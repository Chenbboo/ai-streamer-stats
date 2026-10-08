package com.ruoyi.business.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;

/** Calendar-month payroll shares, independent of the requested report window. */
public final class BusinessPersonnelCost
{
    public static final String MONTHLY_RULE = "CALENDAR_MONTH_V1";
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
            List<LocalDate> days = month(calendar, date);
            if (days.isEmpty()) throw new ServiceException("当月工作日历没有应工作日");
            int index = Collections.binarySearch(days, date);
            if (index < 0) return BigDecimal.ZERO.setScale(2);
            BigDecimal monthlyShare = unit.multiply(allocationPercent).divide(HUNDRED);
            BigDecimal count = BigDecimal.valueOf(days.size());
            // Difference of rounded cumulative shares: a complete month equals the monthly field
            // (including its project weight), without accumulating per-day rounding errors.
            BigDecimal throughToday = monthlyShare.multiply(BigDecimal.valueOf(index + 1)).divide(count, 2, RoundingMode.HALF_UP);
            BigDecimal beforeToday = monthlyShare.multiply(BigDecimal.valueOf(index)).divide(count, 2, RoundingMode.HALF_UP);
            return throughToday.subtract(beforeToday);
        }
        if ("HOURLY".equals(mode)) unit = unit.multiply(new BigDecimal("8"));
        else if (!"DAILY".equals(mode)) throw new ServiceException("缺少可折算的有效日成本");
        return unit.setScale(2, RoundingMode.HALF_UP).multiply(allocationPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public int monthWorkingDays(Map<String,Object> calendar, LocalDate date) { return month(calendar, date).size(); }

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
