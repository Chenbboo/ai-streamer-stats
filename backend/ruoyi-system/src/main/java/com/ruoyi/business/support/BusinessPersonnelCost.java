package com.ruoyi.business.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;

/** Regional daily personnel costs, independent of the requested report window. */
public final class BusinessPersonnelCost
{
    public static final String MONTHLY_RULE = "CALENDAR_MONTH_V1";
    public static final String PROJECT_MONTHLY_RULE = "NATURAL_MONTH_AND_REGIONAL_DAYS_V5";
    public static final String PROJECT_FORMULA = "完整自然月按月成本及当日投入比例分摊；不足整月按月成本 ÷ 地区标准天数（国内21.75天、越南26天） × 当日投入比例，按计费工作日累计（尾差到分）";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<Map<String,Object>, Map<YearMonth,List<LocalDate>>> months = new IdentityHashMap<>();

    public BigDecimal amount(Map<String,Object> rate, Map<String,Object> calendar, LocalDate date,
        BigDecimal allocationPercent)
    {
        if (allocationPercent == null || allocationPercent.signum() < 0 || allocationPercent.compareTo(HUNDRED) > 0)
            throw new ServiceException("项目投入权重必须在0%至100%之间");
        BigDecimal unit;
        try { unit = new BigDecimal(String.valueOf(rate.get("unitCost"))); }
        catch (RuntimeException ex) { throw new ServiceException("缺少有效用人成本"); }
        if (unit.signum() < 0) throw new ServiceException("用人成本不能为负数");
        String mode = String.valueOf(rate.get("costMode"));
        if ("MONTHLY".equals(mode))
        {
            if (!workingDay(calendar, date)) return BigDecimal.ZERO.setScale(2);
            return BusinessHrDailyCost.allocated(BusinessHrDailyCost.dailyRate(rate), allocationPercent);
        }
        if ("HOURLY".equals(mode)) unit = unit.multiply(new BigDecimal("8"));
        else if (!"DAILY".equals(mode)) throw new ServiceException("缺少可折算的有效日成本");
        return unit.setScale(2, RoundingMode.HALF_UP).multiply(allocationPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public int monthWorkingDays(Map<String,Object> calendar, LocalDate date) { return month(calendar, date).size(); }
    public void useMonthDays(Map<String,Object> calendar,LocalDate date,List<LocalDate> dates) {
        months.computeIfAbsent(calendar,key->new HashMap<>()).put(YearMonth.from(date),new ArrayList<>(new TreeSet<>(dates)));
    }

    public static boolean fullMonth(LocalDate date, LocalDate from, LocalDate to) {
        YearMonth period=YearMonth.from(date);
        return from!=null&&!from.isAfter(period.atDay(1))&&to!=null&&!to.isBefore(period.atEndOfMonth());
    }

    /** Cumulative cents keep a complete calendar month equal to the configured monthly cost. */
    public BigDecimal amount(Map<String,Object> rate, Map<String,Object> calendar, LocalDate date,
        BigDecimal percent, boolean fullMonth) {
        if(!fullMonth||!"MONTHLY".equals(rate.get("costMode")))return amount(rate,calendar,date,percent);
        if(percent==null||percent.signum()<0||percent.compareTo(HUNDRED)>0)throw new ServiceException("项目投入权重必须在0%至100%之间");
        List<LocalDate> days=month(calendar,date);int index=Collections.binarySearch(days,date);
        if(index<0)return BigDecimal.ZERO.setScale(2);
        BigDecimal salary;
        try{salary=new BigDecimal(String.valueOf(rate.get("unitCost")));if(salary.signum()<0)throw new NumberFormatException();}
        catch(RuntimeException ex){throw new ServiceException("缺少有效用人成本");}
        BigDecimal numerator=salary.multiply(percent),denominator=HUNDRED.multiply(BigDecimal.valueOf(days.size()));
        return numerator.multiply(BigDecimal.valueOf(index+1)).divide(denominator,2,RoundingMode.HALF_UP)
            .subtract(numerator.multiply(BigDecimal.valueOf(index)).divide(denominator,2,RoundingMode.HALF_UP));
    }

    public BigDecimal projectAmount(Map<String,Object> rate, Map<String,Object> calendar, LocalDate date,
        BigDecimal percent, BigDecimal shareFrom, boolean fullMonth) {
        if(shareFrom==null||!"MONTHLY".equals(rate.get("costMode")))return amount(rate,calendar,date,percent,fullMonth);
        return amount(rate,calendar,date,shareFrom.add(percent),fullMonth).subtract(amount(rate,calendar,date,shareFrom,fullMonth));
    }

    /** Cumulative project shares assign rounding cents once across projects. */
    public BigDecimal projectAmount(Map<String,Object> rate, Map<String,Object> calendar, LocalDate date,
        BigDecimal percent, BigDecimal shareFrom)
    {
        if (!"MONTHLY".equals(rate.get("costMode")) || shareFrom == null)
            return amount(rate, calendar, date, percent);
        return amount(rate, calendar, date, shareFrom.add(percent)).subtract(amount(rate, calendar, date, shareFrom));
    }

    public static BigDecimal shareFrom(Map<Long,Map<String,Object>> weights, Long projectId)
    {
        BigDecimal before = BigDecimal.ZERO;
        for (Map.Entry<Long,Map<String,Object>> entry : new TreeMap<>(weights).entrySet()) {
            if (entry.getKey().equals(projectId)) return before;
            before = before.add(new BigDecimal(String.valueOf(entry.getValue().get("allocationValue"))));
        }
        return null;
    }

    private List<LocalDate> month(Map<String,Object> calendar, LocalDate date)
    {
        Map<YearMonth,List<LocalDate>> byMonth = months.computeIfAbsent(calendar, key -> new HashMap<>());
        return byMonth.computeIfAbsent(YearMonth.from(date), key -> {
            List<LocalDate> days = new ArrayList<>();
            for (LocalDate d = key.atDay(1); !d.isAfter(key.atEndOfMonth()); d = d.plusDays(1))
                if (workingDay(calendar, d)) days.add(d);
            return days;
        });
    }

    public static boolean workingDay(Map<String,Object> calendar, LocalDate date)
    {
        Object raw = calendar.get("exceptionsJson");
        if (raw != null) try {
            for (Map<String,Object> exception : JSON.readValue(String.valueOf(raw), new TypeReference<List<Map<String,Object>>>() {}))
                if (date.toString().equals(String.valueOf(exception.get("bizDate"))))
                    return new BigDecimal(String.valueOf(exception.get("minutes"))).signum() > 0;
        } catch (Exception ex) { throw new ServiceException("工作日历例外格式不正确"); }
        return Arrays.asList(String.valueOf(calendar.get("workingWeekdays")).split(",")).contains(String.valueOf(date.getDayOfWeek().getValue()))
            && new BigDecimal(String.valueOf(calendar.get("dailyMinutes"))).signum() > 0;
    }
}
