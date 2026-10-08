package com.ruoyi.business.support;

import java.math.*;
import java.time.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;

/** Full monthly payroll of a profit department, divided only among participating projects. */
public final class BusinessFullProjectPayroll {
    public static final String RULE="DIRECT_PROJECT_FULL_MONTH_V2";
    public static final String SUPPORT_RULE="MONTH_PROJECT_SHARE_V1";
    public static final LocalDate EFFECTIVE_FROM=LocalDate.of(2026,9,1);
    private static final BigDecimal HUNDRED=new BigDecimal("100.00");
    private final List<Map<String,Object>> timeline,calendars;
    private final boolean normalizeToFull;
    private final Map<Long,Map<String,Object>> scopes;
    private final Map<YearMonth,Month> months=new HashMap<>();
    private static final class Month {
        BigDecimal budget,frozen;
        Map<Long,BigDecimal> frozenByProject=new TreeMap<>(),projectTargets=new TreeMap<>();
        Map<Long,Ratio> projectWeights=new TreeMap<>();
        List<LocalDate> dates=new ArrayList<>();
        Map<LocalDate,Map<Long,Map<String,Object>>> weights=new TreeMap<>();
        Map<LocalDate,Map<Long,Ratio>> monetaryWeights=new TreeMap<>();
    }
    /** Exact fractions prevent a recurring percentage from moving a half-cent across rounding boundaries. */
    private static final class Ratio {
        static final Ratio ZERO=of(BigDecimal.ZERO);
        final BigInteger numerator,denominator;
        Ratio(BigInteger numerator,BigInteger denominator){
            BigInteger gcd=numerator.gcd(denominator);this.numerator=numerator.divide(gcd);this.denominator=denominator.divide(gcd);
        }
        static Ratio of(BigDecimal value){BigDecimal decimal=value.setScale(Math.max(0,value.scale()));return new Ratio(decimal.unscaledValue(),BigInteger.TEN.pow(decimal.scale()));}
        Ratio add(Ratio other){return new Ratio(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),denominator.multiply(other.denominator));}
        Ratio subtract(Ratio other){return new Ratio(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),denominator.multiply(other.denominator));}
        Ratio multiply(Ratio other){return new Ratio(numerator.multiply(other.numerator),denominator.multiply(other.denominator));}
        Ratio divide(Ratio other){return new Ratio(numerator.multiply(other.denominator),denominator.multiply(other.numerator));}
        BigDecimal money(BigDecimal budget){return budget.multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator),2,RoundingMode.HALF_UP);}
    }
    public BusinessFullProjectPayroll(List<Map<String,Object>> timeline,List<Map<String,Object>> calendars,Map<Long,Map<String,Object>> scopes) {
        this(timeline,calendars,scopes,true);
    }
    public BusinessFullProjectPayroll(List<Map<String,Object>> timeline,List<Map<String,Object>> calendars,Map<Long,Map<String,Object>> scopes,boolean normalizeToFull) {
        this.timeline=timeline;this.calendars=calendars;this.scopes=scopes;this.normalizeToFull=normalizeToFull;
    }
    private Map<Long,Map<String,Object>> shares(Map<Long,Map<String,Object>> input) {
        if(normalizeToFull)return normalize(input);
        Map<Long,Map<String,Object>> result=new TreeMap<>();BigDecimal through=BigDecimal.ZERO;
        for(Map.Entry<Long,Map<String,Object>> entry:input.entrySet()) {
            Map<String,Object> row=new LinkedHashMap<>(entry.getValue());BigDecimal value=number(row.get("allocationValue"));
            if("PENDING".equals(row.get("confirmationStatus")))throw new ServiceException("项目投入比例待负责人确认");
            if(value.signum()<0||value.compareTo(HUNDRED)>0)throw new ServiceException("项目投入权重必须在0%至100%之间");
            row.put("originalAllocationValue",value);row.put("costShareFrom",through);through=through.add(value);row.put("costShareTo",through);result.put(entry.getKey(),row);
        }
        if(through.compareTo(HUNDRED)>0)throw new ServiceException("项目投入比例合计超过100%，请先调整");
        return result;
    }
    public static Map<Long,Map<String,Object>> normalize(Map<Long,Map<String,Object>> input) {
        if(input.isEmpty())throw new ServiceException("盈利部门人员必须设置参与项目及有效投入比例");
        BigDecimal sum=BigDecimal.ZERO;
        for(Map<String,Object> row:input.values()) {
            if("PENDING".equals(row.get("confirmationStatus")))throw new ServiceException("盈利部门人员项目投入比例待确认，不能分摊人员成本");
            BigDecimal value=number(row.get("allocationValue"));
            if(value.signum()<0||value.compareTo(HUNDRED)>0)throw new ServiceException("项目投入权重必须在0%至100%之间");
            sum=sum.add(value);
        }
        if(sum.signum()==0)throw new ServiceException("盈利部门人员项目投入比例合计必须大于0");
        if(sum.compareTo(HUNDRED)>0)throw new ServiceException("项目投入比例合计超过100%，请先调整");
        Map<Long,Map<String,Object>> result=new TreeMap<>();BigDecimal through=BigDecimal.ZERO,allocated=BigDecimal.ZERO;
        for(Map.Entry<Long,Map<String,Object>> entry:new TreeMap<>(input).entrySet()) {
            BigDecimal raw=number(entry.getValue().get("allocationValue"));through=through.add(raw);
            BigDecimal cumulative=through.multiply(HUNDRED).divide(sum,2,RoundingMode.HALF_UP);
            Map<String,Object> row=new LinkedHashMap<>(entry.getValue());
            row.put("originalAllocationValue",raw);row.put("costShareFrom",allocated);row.put("costShareTo",cumulative);
            row.put("allocationValue",cumulative.subtract(allocated));row.put("fullCostAllocated",true);
            result.put(entry.getKey(),row);allocated=cumulative;
        }
        return result;
    }
    /** Raw dated weights restricted by the same membership, pause, role and assignment rules as day costing. */
    @SuppressWarnings("unchecked")
    private Map<Long,Map<String,Object>> eligible(LocalDate date,Map<String,Object> calendar) {
        Map<Long,Map<String,Object>> result=new TreeMap<>();
        for(Map.Entry<Long,Map<String,Object>> entry:BusinessAllocationWeights.at(timeline,date).entrySet()) {
            Map<String,Object> weight=entry.getValue(),scope=scopes.get(entry.getKey());
            if(scope==null||"CLOSED".equals(scope.get("accountingState"))||"2".equals(scope.get("delFlag")))continue;
            boolean paused=false;
            for(Map<String,Object> pause:(List<Map<String,Object>>)scope.get("pauses"))
                if(!date.isBefore(day(pause.get("effectiveFrom")))&&(pause.get("effectiveTo")==null||date.isBefore(day(pause.get("effectiveTo")))))paused=true;
            if(paused)continue;
            for(Map<String,Object> member:(List<Map<String,Object>>)scope.get("members")) {
                LocalDate joined=day(member.get("joinedDate")),left=day(member.get("leftDate"));
                if(!"0".equals(String.valueOf(member.get("status")))&&left==null||joined!=null&&date.isBefore(joined)||left!=null&&date.isAfter(left))continue;
                String role=String.valueOf(member.get("memberRole"));
                for(Map<String,Object> period:(List<Map<String,Object>>)scope.get("roles"))
                    if(!date.isBefore(day(period.get("effectiveFrom"))))role=String.valueOf(period.get("memberRole"));
                if("OBSERVER".equals(role))continue;
                List<Map<String,Object>> applicable=new ArrayList<>();Map<String,Object> plan=null;
                for(Map<String,Object> candidate:(List<Map<String,Object>>)scope.get("plans")) {
                    if(!"FOLLOW_PROJECT".equals(candidate.get("participationMode"))&&joined!=null&&day(candidate.get("effectiveTo"))!=null&&day(candidate.get("effectiveTo")).isBefore(joined))continue;
                    applicable.add(candidate);
                    LocalDate start=day(candidate.get("effectiveFrom")),end=day(candidate.get("effectiveTo")),retired=day(candidate.get("retiredTime"));
                    if(plan==null&&(start==null||!date.isBefore(start))&&("FOLLOW_PROJECT".equals(candidate.get("participationMode"))||end==null||!date.isAfter(end))
                        &&("ACTIVE".equals(candidate.get("status"))||"RETIRED".equals(candidate.get("status"))&&retired!=null&&date.isBefore(retired)))plan=candidate;
                }
                if(!applicable.isEmpty()&&plan==null)continue;
                Object calendarId=plan==null?null:plan.get("calendarId");
                Map<String,Object> chosen=null;
                for(Map<String,Object> c:calendars)
                    if(calendarId!=null&&String.valueOf(calendarId).equals(String.valueOf(c.get("calendarId")))){chosen=c;break;}
                    else if(calendarId==null&&covers(c,date)&&(chosen==null||number(c.get("calendarId")).compareTo(number(chosen.get("calendarId")))<0))chosen=c;
                if(chosen==null)throw new ServiceException("盈利部门人员缺少有效工作日历");
                if(!String.valueOf(chosen.get("calendarId")).equals(String.valueOf(calendar.get("calendarId"))))
                    throw new ServiceException("盈利部门人员跨项目工作日历不一致，请先核对");
                result.put(entry.getKey(),weight);break;
            }
        }
        return result;
    }
    public Map<String,Object> amount(Long projectId,LocalDate date,Map<String,Object> calendar,List<Map<String,Object>> rates,
        List<Map<String,Object>> stored,String currency,LocalDate hireDate) {
        YearMonth period=YearMonth.from(date);Month month=months.get(period);
        if(month==null){month=build(period,calendar,rates,stored,currency,hireDate);months.put(period,month);}
        int index=Collections.binarySearch(month.dates,date);
        if(index<0)throw new ServiceException("盈利部门人员缺少该日期有效的项目投入比例");
        Map<Long,Map<String,Object>> shares=shares(month.weights.get(date));Map<String,Object> share=shares.get(projectId);
        if(share==null)throw new ServiceException("盈利部门人员缺少该日期有效的项目投入比例");
        BigDecimal remaining=month.budget.subtract(month.frozen),count=BigDecimal.valueOf(month.dates.size());
        BigDecimal today=remaining.multiply(BigDecimal.valueOf(index+1)).divide(count,8,RoundingMode.HALF_UP);
        BigDecimal before=remaining.multiply(BigDecimal.valueOf(index)).divide(count,8,RoundingMode.HALF_UP);
        BigDecimal daily=today.setScale(2,RoundingMode.HALF_UP).subtract(before.setScale(2,RoundingMode.HALF_UP));
        BigDecimal amount=projectDayAmount(month,projectId,date);
        // Project monthly targets are authoritative; their cent remainders can move between days.
        if(normalizeToFull){daily=BigDecimal.ZERO.setScale(2);for(Long id:month.weights.get(date).keySet())daily=daily.add(projectDayAmount(month,id,date));}
        Map<String,Object> result=new LinkedHashMap<>(share);result.put("amount",amount);
        result.put("fullDailyCost",daily);
        result.put("fullMonthlyCost",month.budget);result.put("frozenMonthlyCost",month.frozen);result.put("allocationWorkingDays",month.dates.size());
        return result;
    }
    private BigDecimal projectDayAmount(Month month,Long projectId,LocalDate date) {
        Ratio projectWeight=month.projectWeights.get(projectId),through=Ratio.ZERO;
        for(LocalDate d:month.dates){if(d.isAfter(date))break;through=through.add(month.monetaryWeights.get(d).getOrDefault(projectId,Ratio.ZERO));}
        Ratio weight=month.monetaryWeights.get(date).get(projectId);BigDecimal target=month.projectTargets.get(projectId);
        return projectWeight.numerator.signum()==0?BigDecimal.ZERO.setScale(2):through.divide(projectWeight).money(target)
            .subtract(through.subtract(weight).divide(projectWeight).money(target));
    }
    /** Retain precision for money; the two-decimal normalized percentages are display metadata only. */
    private Map<Long,Ratio> monetaryWeights(Map<Long,Map<String,Object>> weights) {
        Map<Long,Map<String,Object>> validated=shares(weights);Map<Long,Ratio> result=new TreeMap<>();
        BigDecimal sum=BigDecimal.ZERO;
        for(Map<String,Object> weight:weights.values())sum=sum.add(number(weight.get("allocationValue")));
        for(Map.Entry<Long,Map<String,Object>> entry:validated.entrySet()){
            BigDecimal raw=number(entry.getValue().get("originalAllocationValue"));
            result.put(entry.getKey(),normalizeToFull?Ratio.of(raw).multiply(Ratio.of(HUNDRED)).divide(Ratio.of(sum)):Ratio.of(raw));
        }
        return result;
    }
    /** Whole-month monetary breakdown, including preserved costs, using the same dated rules as day costing. */
    public List<Map<String,Object>> allocations(YearMonth period,Map<String,Object> calendar,List<Map<String,Object>> rates,
        List<Map<String,Object>> stored,String currency,LocalDate hireDate) {
        Month month=build(period,calendar,rates,stored,currency,hireDate);months.put(period,month);
        Map<Long,BigDecimal> totals=new TreeMap<>(month.frozenByProject);
        for(LocalDate date:month.dates)for(Long projectId:month.weights.get(date).keySet())
            totals.merge(projectId,(BigDecimal)amount(projectId,date,calendar,rates,stored,currency,hireDate).get("amount"),BigDecimal::add);
        List<Map<String,Object>> result=new ArrayList<>();BigDecimal through=BigDecimal.ZERO,percent=BigDecimal.ZERO;
        for(Map.Entry<Long,BigDecimal> entry:totals.entrySet()) {
            through=through.add(entry.getValue());
            BigDecimal cumulative=month.budget.signum()==0?BigDecimal.ZERO:through.multiply(HUNDRED).divide(month.budget,2,RoundingMode.HALF_UP);
            Map<String,Object> row=new LinkedHashMap<>();row.put("projectId",entry.getKey());row.put("amount",entry.getValue());
            row.put("allocationPercent",cumulative.subtract(percent));percent=cumulative;result.add(row);
        }
        return result;
    }
    private Month build(YearMonth period,Map<String,Object> calendar,List<Map<String,Object>> rates,List<Map<String,Object>> stored,String currency,LocalDate hireDate) {
        Month month=new Month();month.budget=BigDecimal.ZERO;month.frozen=BigDecimal.ZERO;
        BusinessPersonnelCost pricing=new BusinessPersonnelCost();boolean priced=false;
        for(LocalDate date=period.atDay(1);!date.isAfter(period.atEndOfMonth());date=date.plusDays(1)) {
            if(!BusinessPersonnelCost.workingDay(calendar,date))continue;
            if(hireDate==null||!date.isBefore(hireDate)) {
                Map<String,Object> rate=null;
                for(Map<String,Object> candidate:rates)if(covers(candidate,date)) {
                    if(rate!=null)throw new ServiceException("成本生效日期重叠");rate=candidate;
                }
                if(rate==null)throw new ServiceException("盈利部门人员缺少有效月成本");
                if(!currency.equals(rate.get("currency")))throw new ServiceException("成本币种与项目不一致");
                month.budget=month.budget.add(pricing.amount(rate,calendar,date,HUNDRED));priced=true;
            }
            Map<Long,Map<String,Object>> weights=eligible(date,calendar);
            for(Map<String,Object> weight:weights.values())if(weight.get("projectCurrency")!=null&&!currency.equals(weight.get("projectCurrency")))
                throw new ServiceException("成本币种与项目不一致");
            if(!weights.isEmpty()){month.dates.add(date);month.weights.put(date,weights);}
        }
        if(priced&&rates.size()==1&&"MONTHLY".equals(rates.get(0).get("costMode")))month.budget=number(rates.get(0).get("unitCost")).setScale(2,RoundingMode.HALF_UP);
        for(Map<String,Object> row:stored) {
            LocalDate date=day(row.get("bizDate"));
            if(!period.equals(YearMonth.from(date)))continue;
            Map<Long,Map<String,Object>> weights=month.weights.get(date);
            if(weights!=null&&weights.containsKey(Long.valueOf(String.valueOf(row.get("projectId")))))continue;
            if(!currency.equals(row.get("currency")))throw new ServiceException("成本币种与项目不一致");
            if(row.get("amount")==null)throw new ServiceException("盈利部门人员已有项目成本待计价，请先核对");
            BigDecimal value=number(row.get("amount"));
            try {Object base=JSON.parseObject(String.valueOf(row.get("basisJson"))).get("attendanceBaseAmount");if(base!=null)value=number(base);}catch(RuntimeException ignored){}
            month.frozen=month.frozen.add(value);
            month.frozenByProject.merge(Long.valueOf(String.valueOf(row.get("projectId"))),value,BigDecimal::add);
        }
        if(month.frozen.compareTo(month.budget)>0)throw new ServiceException("盈利部门人员已计入项目的成本超过月成本，请先核对");
        if(normalizeToFull&&month.dates.isEmpty()&&month.budget.compareTo(month.frozen)>0)throw new ServiceException("盈利部门人员必须设置参与项目及有效投入比例");
        if(!month.dates.isEmpty()) {
            for(LocalDate date:month.dates){
                Map<Long,Ratio> weights=monetaryWeights(month.weights.get(date));month.monetaryWeights.put(date,weights);
                for(Map.Entry<Long,Ratio> entry:weights.entrySet())month.projectWeights.merge(entry.getKey(),entry.getValue(),Ratio::add);
            }
            Ratio cumulative=Ratio.ZERO,denominator=Ratio.of(BigDecimal.valueOf(month.dates.size()).multiply(HUNDRED));BigDecimal assigned=BigDecimal.ZERO;
            for(Map.Entry<Long,Ratio> entry:month.projectWeights.entrySet()) {
                cumulative=cumulative.add(entry.getValue());BigDecimal target=cumulative.divide(denominator).money(month.budget.subtract(month.frozen));
                month.projectTargets.put(entry.getKey(),target.subtract(assigned));assigned=target;
            }
        }
        return month;
    }
    private static BigDecimal number(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(value));}
    private static LocalDate day(Object value){return value==null?null:LocalDate.parse(String.valueOf(value).substring(0,10));}
    private static boolean covers(Map<String,Object> row,LocalDate date){LocalDate from=day(row.get("effectiveFrom")),to=day(row.get("effectiveTo"));return (from==null||!date.isBefore(from))&&(to==null||!date.isAfter(to));}
}
