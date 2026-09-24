package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.BusinessProjectProposalMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.support.BusinessPersonnelCost;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;

/** Planned budgets, never payroll or actual project costs. Only aggregate amounts leave this service. */
@Service
public class BusinessProjectBudgetService
{
    private static final String MONTHLY_FORECAST_VERSION="CALENDAR_MONTH_V2";
    private static final String HISTORICAL_PERSONNEL_ISSUE="暂无法还原原计划的分月人员成本，请重新测算计划";
    @Autowired private BusinessProjectProposalMapper proposals;
    @Autowired private BusinessProjectWorkMapper mapper;
    @Autowired private BusinessProjectWorkService work;

    public void ensureOwner(BusinessProjectProposal proposal){
        if(proposal.getEffectiveOwnerUserId()==null||proposal.getTemplateVersion()==null||"LEGACY_V1".equals(proposal.getTemplateVersion()))return;
        List<Map<String,Object>> staff=new ArrayList<>(rows(proposal.getStaffingLines()));
        if(staff.stream().anyMatch(r->String.valueOf(proposal.getEffectiveOwnerUserId()).equals(String.valueOf(r.get("userId")))))return;
        Map<String,Object> owner=new LinkedHashMap<>();owner.put("userId",proposal.getEffectiveOwnerUserId());owner.put("userName",proposal.getEffectiveOwnerName());owner.put("roleName","项目负责人");owner.put("participationMode","FOLLOW_PROJECT");
        owner.put("planStartDate",proposal.getPlanStartDate());owner.put("planEndDate",proposal.getPlanEndDate());owner.put("inputUnit","PERCENTAGE");owner.put("inputQuantity",100);
        List<Map<String,Object>> calendars=mapper.selectCalendars();
        for(Map<String,Object> c:calendars)if((proposal.getPlanStartDate()==null||date(c.get("effectiveFrom"))==null||!date(c.get("effectiveFrom")).isAfter(date(proposal.getPlanStartDate())))&&(proposal.getPlanEndDate()==null||date(c.get("effectiveTo"))==null||!date(c.get("effectiveTo")).isBefore(date(proposal.getPlanEndDate())))){owner.put("calendarId",c.get("calendarId"));break;}
        for(Map<String,Object> u:mapper.selectUnitPolicies())if(proposal.getPlanEndDate()==null||date(u.get("effectiveTo"))==null||!date(u.get("effectiveTo")).isBefore(date(proposal.getPlanEndDate()))){owner.put("unitPolicyId",u.get("unitPolicyId"));break;}
        staff.add(0,owner);proposal.setStaffingLines(staff);
    }
    public void apply(BusinessProjectProposal proposal)
    {
        Map<String,Object> budget=estimate(proposal);proposal.setBudget(budget);
        BigDecimal personnel=amountOrNull(budget.get("personnelAmount"));
        BigDecimal external=amountOrNull(budget.get("plannedBusinessAmount"));
        BigDecimal revenue=amountOrNull(budget.get("revenueAmount"));
        proposal.setBudgetMode(String.valueOf(budget.get("mode")));proposal.setBudgetScope(String.valueOf(budget.get("scope")));
        proposal.setDailyBudgetLimit(amountOrNull(budget.get("dailyLimit")));proposal.setStartupBudgetLimit(amountOrNull(budget.get("startupLimit")));
        proposal.setBudgetReason((String)budget.get("reason"));
        proposal.setBudgetLimit("TOTAL".equals(proposal.getBudgetMode())?amountOrNull(budget.get("totalAmount")):null);
        proposal.setNoBudget("NONE".equals(proposal.getBudgetMode())?"1":"0");
        proposal.setEstimatedPersonnelCost(personnel);proposal.setEstimatedExternalCost(external);proposal.setEstimatedRevenue(revenue);
        BigDecimal cost=personnel==null?null:personnel.add(external);
        proposal.setEstimatedTotalCost(cost);proposal.setExpectedProfit(cost==null?null:revenue.subtract(cost));
        proposal.setExpectedMargin(cost==null||revenue.signum()==0?null:revenue.subtract(cost).multiply(new BigDecimal("100")).divide(revenue,4,RoundingMode.HALF_UP));
        proposal.setBreakEvenRevenue(cost);proposal.setPeakCashNeed(proposal.getBudgetLimit());
        Map<String,Object> steady=(Map<String,Object>)budget.get("steadyMonth");
        if(steady!=null){proposal.setRecurringEstimatedRevenue(amountOrNull(steady.get("revenueAmount")));proposal.setRecurringEstimatedExternalCost(amountOrNull(steady.get("plannedBusinessAmount")));proposal.setRecurringEstimatedTotalCost(amountOrNull(steady.get("plannedTotalCost")));proposal.setRecurringExpectedProfit(amountOrNull(steady.get("profit")));}
    }

    public Map<String,Object> estimate(BusinessProjectProposal proposal) {
        ensureOwner(proposal);
        Map<String,Object> result=estimate(proposal,false);
        addMonthlyForecasts(proposal,result);
        return result;
    }
    private void addMonthlyForecasts(BusinessProjectProposal proposal,Map<String,Object> result) {
        if(proposal.getPlanStartDate()!=null){
            LocalDate first=date(proposal.getPlanStartDate()).withDayOfMonth(1);
            LocalDate last=proposal.getPlanEndDate()==null?first.plusMonths(11):date(proposal.getPlanEndDate()).withDayOfMonth(1);
            List<Map<String,Object>> forecasts=new ArrayList<Map<String,Object>>();
            for(LocalDate month=first;!month.isAfter(last)&&forecasts.size()<60;month=month.plusMonths(1))
            {
                Map<String,Object> forecast=monthlyForecast(proposal,month,false);
                forecast.put("month",month.toString().substring(0,7));forecasts.add(forecast);
            }
            if(proposal.getPlanEndDate()!=null&&!forecasts.isEmpty()
                &&last.toString().substring(0,7).equals(forecasts.get(forecasts.size()-1).get("month")))
                reconcileMonthlyRounding(proposal,forecasts);
            result.put("monthlyForecasts",forecasts);
            result.put("monthlyForecastVersion",MONTHLY_FORECAST_VERSION);
        }
        if(proposal.getPlanStartDate()!=null&&proposal.getPlanEndDate()==null){
            LocalDate first=date(proposal.getPlanStartDate()).withDayOfMonth(1);
            result.put("firstMonth",monthlyForecast(proposal,first,false));
            result.put("steadyMonth",monthlyForecast(proposal,first.plusMonths(1),true));
        }
    }
    private void reconcileMonthlyRounding(BusinessProjectProposal proposal,List<Map<String,Object>> forecasts){
        LocalDate start=date(proposal.getPlanStartDate()),end=date(proposal.getPlanEndDate());
        List<String> issues=new ArrayList<>();
        BigDecimal revenue=plannedAmount(proposal.getRevenueLines(),"expectedAmount","expectedDate",start,end,true,issues);
        if(proposal.getParentProjectId()!=null&&proposal.getParentFundingAmount()!=null)
            revenue=revenue.add(proposal.getParentFundingAmount().setScale(2,RoundingMode.HALF_UP));
        BigDecimal business=plannedAmount(proposal.getExpenseLines(),"amount","occurDate",start,end,false,issues);
        for(Map<String,Object> forecast:forecasts){
            revenue=revenue.subtract(amountOrNull(forecast.get("revenueAmount")));
            business=business.subtract(amountOrNull(forecast.get("plannedBusinessAmount")));
        }
        // Reconcile independently rounded months to the unchanged whole-project estimate.
        // For very small recurring amounts over many months, spread a negative remainder
        // backwards as needed instead of inventing a negative last month's revenue or cost.
        applyMonthlyRemainder(forecasts,"revenueAmount",revenue);
        applyMonthlyRemainder(forecasts,"plannedBusinessAmount",business);
        for(Map<String,Object> forecast:forecasts){
            BigDecimal personnel=amountOrNull(forecast.get("personnelAmount"));
            BigDecimal cost=personnel==null?null:personnel.add(amountOrNull(forecast.get("plannedBusinessAmount")));
            forecast.put("plannedTotalCost",cost);
            forecast.put("profit",cost==null?null:amountOrNull(forecast.get("revenueAmount")).subtract(cost));
        }
    }
    private void applyMonthlyRemainder(List<Map<String,Object>> forecasts,String field,BigDecimal remainder){
        for(int i=forecasts.size()-1;i>=0&&remainder.signum()!=0;i--){
            Map<String,Object> forecast=forecasts.get(i);BigDecimal amount=amountOrNull(forecast.get(field));
            BigDecimal adjusted=amount.add(remainder).max(BigDecimal.ZERO.setScale(2));
            remainder=remainder.subtract(adjusted.subtract(amount));forecast.put(field,adjusted);
        }
    }
    /** Refresh derived monthly views only; approved totals and persisted snapshots stay intact. */
    public void refreshMonthlyForecast(BusinessProjectProposal proposal) {
        if(proposal.getPlanStartDate()==null||proposal.getBudget()==null
            ||proposal.getTemplateVersion()==null||"LEGACY_V1".equals(proposal.getTemplateVersion()))return;
        boolean finite=proposal.getPlanEndDate()!=null;
        if(finite&&MONTHLY_FORECAST_VERSION.equals(proposal.getBudget().get("monthlyForecastVersion")))return;
        BusinessProjectProposal copy=new BusinessProjectProposal();org.springframework.beans.BeanUtils.copyProperties(proposal,copy);
        if(!finite){
            // Keep the established open-ended behavior: only its forward-looking steady month
            // uses current rates; the saved first month and monthly history remain frozen.
            ensureOwner(copy);
            Map<String,Object> forecast=monthlyForecast(copy,date(copy.getPlanStartDate()).withDayOfMonth(1).plusMonths(1),true);
            Map<String,Object> budget=new LinkedHashMap<>(proposal.getBudget());
            budget.put("steadyMonth",forecast);proposal.setBudget(budget);
            proposal.setRecurringEstimatedRevenue(amountOrNull(forecast.get("revenueAmount")));
            proposal.setRecurringEstimatedExternalCost(amountOrNull(forecast.get("plannedBusinessAmount")));
            proposal.setRecurringEstimatedTotalCost(amountOrNull(forecast.get("plannedTotalCost")));
            proposal.setRecurringExpectedProfit(amountOrNull(forecast.get("profit")));
            return;
        }
        // Old baseline root dates may have passed through a timezone-sensitive serializer.
        // Its approved budget period is the authoritative interval for this read-only projection.
        if(finite){
            if(proposal.getBudget().get("startDate")!=null)copy.setPlanStartDate(DateUtils.parseDate(proposal.getBudget().get("startDate")));
            if(proposal.getBudget().get("endDate")!=null)copy.setPlanEndDate(DateUtils.parseDate(proposal.getBudget().get("endDate")));
        }
        ensureOwner(copy);
        Map<String,Object> budget=new LinkedHashMap<String,Object>(proposal.getBudget());
        Map<String,Object> refreshed=estimate(copy);
        if(finite){
            BigDecimal savedPersonnel=amountOrNull(budget.get("personnelAmount"));
            List<String> savedBasis=personnelBasis(budget.get("basis"));
            boolean knownZero=savedPersonnel!=null&&savedPersonnel.signum()==0&&savedBasis!=null&&savedBasis.isEmpty();
            BigDecimal currentPersonnel=amountOrNull(refreshed.get("personnelAmount"));
            boolean sameBasis=savedPersonnel!=null&&currentPersonnel!=null&&savedPersonnel.compareTo(currentPersonnel)==0
                &&savedBasis!=null&&!savedBasis.isEmpty()&&savedBasis.equals(personnelBasis(refreshed.get("basis")));
            if(knownZero||!sameBasis)
                for(Map<String,Object> forecast:(List<Map<String,Object>>)refreshed.get("monthlyForecasts"))
                    preserveHistoricalPersonnel(forecast,knownZero);
        }
        for(String key:Arrays.asList("monthlyForecasts","monthlyForecastVersion"))
            if(refreshed.containsKey(key))budget.put(key,refreshed.get(key));
        proposal.setBudget(budget);
    }
    private List<String> personnelBasis(Object raw){
        if(raw==null)return Collections.emptyList();
        if(!(raw instanceof List))return null;
        List<String> result=new ArrayList<>();
        for(Object item:(List<?>)raw){
            if(!(item instanceof Map))return null;
            Map<?,?> reference=(Map<?,?>)item;StringBuilder value=new StringBuilder();
            for(String key:Arrays.asList("userId","bizDate","plannedMinutes","ratePolicyId","rateVersion")){
                Object field=reference.get(key);if(field==null)return null;
                try{value.append("bizDate".equals(key)?date(field).toString():new BigDecimal(String.valueOf(field)).stripTrailingZeros().toPlainString()).append('|');}
                catch(RuntimeException ex){return null;}
            }
            result.add(value.toString());
        }
        Collections.sort(result);return result;
    }
    private void preserveHistoricalPersonnel(Map<String,Object> forecast,boolean knownZero){
        BigDecimal business=amountOrNull(forecast.get("plannedBusinessAmount"));
        forecast.put("personnelAmount",knownZero?BigDecimal.ZERO.setScale(2):null);
        forecast.put("plannedTotalCost",knownZero?business:null);
        forecast.put("profit",knownZero?amountOrNull(forecast.get("revenueAmount")).subtract(business):null);
        // Current individual amounts must not leak into a projection of a different saved budget.
        forecast.put("staffingStatus",Collections.emptyList());
        if(!knownZero){
            List<String> issues=new ArrayList<>((List<String>)forecast.get("issues"));
            issues.add(HISTORICAL_PERSONNEL_ISSUE);forecast.put("issues",issues);forecast.put("status","PENDING");
        }
    }
    private Map<String,Object> monthlyForecast(BusinessProjectProposal source,LocalDate month,boolean recurringOnly){
        BusinessProjectProposal copy=new BusinessProjectProposal();org.springframework.beans.BeanUtils.copyProperties(source,copy);
        Map<String,Object> input=new LinkedHashMap<String,Object>();if(source.getBudget()!=null)input.putAll(source.getBudget());
        input.put("cycle","MONTH");input.put("anchorDate",month.toString());copy.setBudget(input);
        if(recurringOnly){copy.setRevenueLines(recurring(source.getRevenueLines()));copy.setExpenseLines(recurring(source.getExpenseLines()));}
        // 主项目拨款是一次性内部收入，只在子项目开始月份计入预测。
        if(recurringOnly||date(source.getPlanStartDate())==null
            ||!month.equals(date(source.getPlanStartDate()).withDayOfMonth(1)))copy.setParentFundingAmount(null);
        Map<String,Object> estimate=estimate(copy,false,month);
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        for(String key:Arrays.asList("startDate","endDate","currency","revenueAmount","plannedBusinessAmount","personnelAmount","plannedTotalCost","profit","status","issues","staffingStatus","personnelCostRule"))result.put(key,estimate.get(key));
        result.put("recurringOnly",recurringOnly);return result;
    }
    private List<Map<String,Object>> recurring(List<Map<String,Object>> rows){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(Map<String,Object> row:rows(rows))if(!"ONE_TIME".equals(occurrence(row)))result.add(row);return result;}
    public Map<String,Object> estimateResourcePlan(BusinessProjectProposal proposal) { return estimate(proposal,true); }
    private Map<String,Object> estimate(BusinessProjectProposal proposal,boolean resourcePlan)
    { return estimate(proposal,resourcePlan,null); }
    private Map<String,Object> estimate(BusinessProjectProposal proposal,boolean resourcePlan,LocalDate forecastMonth)
    {
        Map<String,Object> input=proposal.getBudget()==null?Collections.<String,Object>emptyMap():proposal.getBudget();
        Map<String,Object> result=new LinkedHashMap<String,Object>();List<String> issues=new ArrayList<String>();
        String mode=proposal.getBudgetMode()!=null?proposal.getBudgetMode():String.valueOf(input.getOrDefault("mode","TOTAL"));
        String scope=proposal.getBudgetScope()!=null?proposal.getBudgetScope():String.valueOf(input.getOrDefault("scope","FULL_COST"));
        if(!Arrays.asList("TOTAL","DAILY","NONE").contains(mode)||!Arrays.asList("FULL_COST","CASH_EXPENSE").contains(scope))throw new ServiceException("预算控制方式或统计口径不正确");
        Object dailyInput=proposal.getDailyBudgetLimit()!=null?proposal.getDailyBudgetLimit():input.get("dailyLimit");
        Object startupInput=proposal.getStartupBudgetLimit()!=null?proposal.getStartupBudgetLimit():input.get("startupLimit");
        BigDecimal daily="DAILY".equals(mode)?money(dailyInput,"每日预算上限",issues):null;
        if(daily!=null&&daily.signum()<=0)throw new ServiceException("每日预算上限必须大于0");
        BigDecimal startup="DAILY".equals(mode)&&startupInput!=null?money(startupInput,"启动预算",issues):null;
        String reason=proposal.getBudgetReason()!=null?proposal.getBudgetReason():(String)input.get("reason");
        if(reason!=null&&reason.length()>500)throw new ServiceException("预算说明不能超过500个字符");
        if("NONE".equals(mode)&&(reason==null||reason.trim().isEmpty()))issues.add("暂不设置预算时请填写原因");
        result.put("mode",mode);result.put("scope",scope);result.put("dailyLimit",daily);result.put("startupLimit",startup);result.put("reason",reason);
        List<Map<String,Object>> basis=new ArrayList<Map<String,Object>>();
        LocalDate projectStart=date(proposal.getPlanStartDate()),projectEnd=date(proposal.getPlanEndDate());
        String cycle=forecastMonth!=null?"MONTH":projectEnd!=null?"PROJECT":String.valueOf(input.getOrDefault("cycle","MONTH"));
        if(!Arrays.asList("PROJECT","WEEK","MONTH","QUARTER","YEAR").contains(cycle)||projectEnd==null&&"PROJECT".equals(cycle))
            throw new ServiceException("不限期项目请选择周度、月度、季度或年度预算");
        LocalDate start=projectStart,end=projectEnd;
        if(projectStart==null)issues.add("请先填写项目开始日期");
        if(projectStart!=null&&projectEnd!=null&&projectEnd.isBefore(projectStart))issues.add("计划结束不能早于开始日期");
        if(forecastMonth!=null&&projectStart!=null)
        {
            start=forecastMonth.isBefore(projectStart)?projectStart:forecastMonth;
            LocalDate monthEnd=forecastMonth.plusMonths(1).minusDays(1);
            end=projectEnd!=null&&projectEnd.isBefore(monthEnd)?projectEnd:monthEnd;
            result.put("anchorDate",forecastMonth.toString());
        }
        else if(projectEnd==null&&projectStart!=null)
        {
            LocalDate anchor=date(input.get("anchorDate"));if(anchor==null)anchor=projectStart;
            LocalDate periodStart;
            if("WEEK".equals(cycle))
            {
                periodStart=anchor.minusDays(anchor.getDayOfWeek().getValue()-1L);
                end=periodStart.plusDays(6);
            }
            else
            {
                periodStart="YEAR".equals(cycle)?anchor.withDayOfYear(1):anchor.withDayOfMonth(1);
                int months="YEAR".equals(cycle)?12:"QUARTER".equals(cycle)?3:1;
                end=periodStart.plusMonths(months).minusDays(1);
            }
            start=periodStart.isBefore(projectStart)?projectStart:periodStart;
            if(end.isBefore(start))issues.add("预算期间不能早于项目开始日期");
            result.put("anchorDate",periodStart.toString());
        }
        result.put("policyVersion","PLANNED_BUDGET_V1");result.put("cycle",cycle);
        result.put("startDate",start==null?null:start.toString());result.put("endDate",end==null?null:end.toString());
        String currency=proposal.getBaseCurrency()==null?"CNY":proposal.getBaseCurrency().trim().toUpperCase(Locale.ROOT);
        result.put("currency",currency);
        validateLineDates(proposal,proposal.getRevenueLines(),"expectedDate","收入测算",issues);
        validateLineDates(proposal,proposal.getExpenseLines(),"occurDate","支出计划",issues);
        if(!"NO_TOTAL".equals(proposal.getGoalMode()))validateLineDates(proposal,proposal.getTargetLines(),"dueDate","量化目标",issues);
        LocalDate forecastOrigin=forecastMonth==null?null:projectStart;
        BigDecimal external=plannedAmount(proposal.getExpenseLines(),"amount","occurDate",start,end,false,issues,forecastOrigin);
        BigDecimal expensePlanTotal=BigDecimal.ZERO.setScale(2);
        if ("TOTAL".equals(mode) && !resourcePlan)
            for (Map<String,Object> line : rows(proposal.getExpenseLines()))
                if (line.get("amount") != null && !"".equals(line.get("amount")))
                    expensePlanTotal=expensePlanTotal.add(money(line.get("amount"),"计划支出",issues));
        BigDecimal business=external;
        if ("TOTAL".equals(mode))
        {
            if (!resourcePlan && input.get("businessAmount")==null) business=expensePlanTotal;
            else if (proposal.getBudget()!=null) business=money(input.get("businessAmount"),"业务预算",issues);
        }
        BigDecimal externalRevenue=plannedAmount(proposal.getRevenueLines(),"expectedAmount","expectedDate",start,end,true,issues,forecastOrigin);
        BigDecimal fundingRevenue=proposal.getParentProjectId()==null||proposal.getParentFundingAmount()==null
            ?BigDecimal.ZERO:proposal.getParentFundingAmount().setScale(2,RoundingMode.HALF_UP);
        BigDecimal revenue=externalRevenue.add(fundingRevenue);
        if(business!=null&&external.compareTo(business)>0)issues.add("业务预算不能低于本期支出计划合计 "+external.toPlainString()+" "+currency);
        if("TOTAL".equals(mode)&&!resourcePlan&&business!=null&&expensePlanTotal.compareTo(business)>0)
            issues.add("业务预算不能低于全部支出计划金额合计 "+expensePlanTotal.toPlainString()+" "+currency);
        Map<LocalDate,BigDecimal> dailyPersonnel=new TreeMap<>();
        BusinessPersonnelCost pricing=new BusinessPersonnelCost();
        result.put("personnelCostRule",BusinessPersonnelCost.MONTHLY_RULE);
        List<Map<String,Object>> staffingStatus=new ArrayList<>();
        BigDecimal personnel=BigDecimal.ZERO;boolean missing=false;Set<Long> people=new HashSet<Long>();
        for(Map<String,Object> staff:rows(proposal.getStaffingLines()))
        {
            Map<String,Object> staffStatus=new LinkedHashMap<>();
            List<String> staffIssues=new ArrayList<>();
            staffStatus.put("userId",staff.get("userId"));staffStatus.put("issues",staffIssues);staffStatus.put("status","READY");staffingStatus.add(staffStatus);
            String label=String.valueOf(staff.getOrDefault("userName","所选人员"));
            try
            {
                Long userId=staff.get("userId")==null?null:Long.valueOf(String.valueOf(staff.get("userId")));
                if(userId==null||!resourcePlan&&!people.add(userId))throw new ServiceException("请选择不同的具体人员");
                Map<String,Object> member=proposals.selectProposalStaff(userId,proposal.getPlanStartDate());
                if(member==null||member.get("userId")==null||member.get("companyDeptId")==null)
                    throw new ServiceException("人员不在有效任职范围");
                label=String.valueOf(member.getOrDefault("nickName",member.getOrDefault("accountName",label)));
                if(start==null||end==null)throw new ServiceException("请先确定预算期间");
                if("LEGACY_V1".equals(proposal.getTemplateVersion())||proposal.getTemplateVersion()==null)
                {
                    if(!"MONTHLY".equals(member.get("costMode"))||member.get("monthlyCost")==null)throw new ServiceException("缺少有效的历史人员成本");
                    if(!currency.equals(String.valueOf(member.get("costCurrency"))))throw new ServiceException("成本币种与项目币种不一致");
                    BigDecimal previousPersonnel=personnel;
                    BigDecimal monthly=new BigDecimal(String.valueOf(member.get("monthlyCost")));
                    if(projectEnd==null&&"WEEK".equals(cycle))
                    {
                        if(member.get("dailyCost")==null)throw new ServiceException("缺少日用人成本");
                        long weekdays=0;for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1))if(day.getDayOfWeek().getValue()<=5)weekdays++;
                        personnel=personnel.add(new BigDecimal(String.valueOf(member.get("dailyCost"))).multiply(BigDecimal.valueOf(weekdays)));
                    }
                    else if(projectEnd==null)personnel=personnel.add(monthly.multiply(new BigDecimal("YEAR".equals(cycle)?12:"QUARTER".equals(cycle)?3:1)));
                    else {if(member.get("dailyCost")==null)throw new ServiceException("缺少日用人成本");personnel=personnel.add(new BigDecimal(String.valueOf(member.get("dailyCost"))).multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start,end)+1)));}
                    BigDecimal staffCost=personnel.subtract(previousPersonnel);
                    addLegacyDailyCosts(dailyPersonnel,start,end,staffCost,projectEnd==null&&"WEEK".equals(cycle));
                    staffStatus.put("amount",staffCost.setScale(2,RoundingMode.HALF_UP));staffStatus.put("currency",currency);
                    continue;
                }
                String participationMode=com.ruoyi.business.support.BusinessProposalParticipation.mode(staff,proposal);
                LocalDate from="FOLLOW_PROJECT".equals(participationMode)?projectStart:date(staff.get("planStartDate"));
                LocalDate participationEnd="FOLLOW_PROJECT".equals(participationMode)?projectEnd:"UNLIMITED".equals(participationMode)?null:date(staff.get("planEndDate"));
                if(!Arrays.asList("FOLLOW_PROJECT","CUSTOM","UNLIMITED").contains(participationMode)||from==null
                    ||"CUSTOM".equals(participationMode)&&participationEnd==null||"UNLIMITED".equals(participationMode)&&projectEnd!=null
                    ||participationEnd!=null&&participationEnd.isBefore(from)||from.isBefore(projectStart)||projectEnd!=null&&participationEnd!=null&&participationEnd.isAfter(projectEnd))
                    throw new ServiceException("请填写项目范围内的人员参与方式和日期");
                LocalDate pricedFrom=from.isAfter(start)?from:start;
                LocalDate pricedTo=participationEnd==null||participationEnd.isAfter(end)?end:participationEnd;
                if(pricedTo.isBefore(pricedFrom))continue;
                Map<String,Object> planned=new LinkedHashMap<String,Object>(staff);
                planned.put("planStartDate",pricedFrom.toString());planned.put("planEndDate",pricedTo.toString());
                BigDecimal inputQuantity=staff.get("inputQuantity")==null?new BigDecimal("100"):new BigDecimal(String.valueOf(staff.get("inputQuantity")));
                // 兼容改版前以 0 表示“仅参与”的草稿；新表单只允许录入 0 以上的比例。
                if(inputQuantity.compareTo(BigDecimal.ZERO)==0)inputQuantity=new BigDecimal("100");
                if(inputQuantity.compareTo(BigDecimal.ZERO)<=0||inputQuantity.compareTo(new BigDecimal("100"))>0)
                    throw new ServiceException("人员投入比例必须大于0且不超过100%");
                // 预算按每个有效工作日的完整日成本乘投入比例计算。这里用 100% 只取得工作日，
                // 避免 8.33% 等比例换算成分钟时产生无意义的整数分钟校验误差。
                planned.put("inputUnit","PERCENTAGE");planned.put("inputQuantity",new BigDecimal("100"));planned.put("unitPolicyId",1L);
                List<Map<String,Object>> days=work.plannedWorkDays(planned);
                Map<String,Object> calendar=mapper.selectCalendar(Long.valueOf(String.valueOf(planned.get("calendarId"))));
                List<Map<String,Object>> rates=mapper.selectBudgetRates(userId,start.toString(),end.toString());
                if(rates==null)rates=Collections.emptyList();
                BigDecimal staffCost=BigDecimal.ZERO;
                List<LocalDate> missingDates=new ArrayList<>(),overlapDates=new ArrayList<>(),currencyDates=new ArrayList<>();
                Map<Object,Map<String,Object>> usedPeriods=new LinkedHashMap<>();
                for(Map<String,Object> day:days)
                {
                    LocalDate d=date(day.get("bizDate"));int minutes=((Number)day.get("plannedMinutes")).intValue();
                    if(d.isBefore(start)||d.isAfter(end)||minutes==0)continue;
                    List<Map<String,Object>> matches=new ArrayList<Map<String,Object>>();
                    for(Map<String,Object> rate:rates)if(!d.isBefore(date(rate.get("effectiveFrom")))&&(date(rate.get("effectiveTo"))==null||!d.isAfter(date(rate.get("effectiveTo")))))matches.add(rate);
                    if(matches.size()!=1){(matches.isEmpty()?missingDates:overlapDates).add(d);continue;}
                    Map<String,Object> rate=matches.get(0);
                    if(!currency.equals(String.valueOf(rate.get("currency")))){currencyDates.add(d);continue;}
                    BigDecimal dayCost=pricing.amount(rate,calendar,d,inputQuantity);
                    staffCost=staffCost.add(dayCost);dailyPersonnel.merge(d,dayCost,BigDecimal::add);
                    Map<String,Object> period=new LinkedHashMap<>();
                    period.put("effectiveFrom",date(rate.get("effectiveFrom")).toString());
                    period.put("effectiveTo",date(rate.get("effectiveTo"))==null?null:date(rate.get("effectiveTo")).toString());
                    period.put("version",rate.get("version"));usedPeriods.put(rate.get("policyId"),period);
                    Map<String,Object> reference=new LinkedHashMap<String,Object>();reference.put("userId",userId);reference.put("bizDate",d.toString());reference.put("plannedMinutes",minutes);reference.put("ratePolicyId",rate.get("policyId"));reference.put("rateVersion",rate.get("version"));basis.add(reference);
                }
                addDateIssue(staffIssues,missingDates,"缺少有效成本费率");
                addDateIssue(staffIssues,overlapDates,"存在重叠成本费率");
                addDateIssue(staffIssues,currencyDates,"成本币种与项目币种不一致");
                staffStatus.put("ratePeriods",new ArrayList<>(usedPeriods.values()));
                if(staffIssues.isEmpty()){staffStatus.put("amount",staffCost.setScale(2,RoundingMode.HALF_UP));staffStatus.put("currency",currency);}
                personnel=personnel.add(staffCost);
            }
            catch(ServiceException ex){staffIssues.add(ex.getMessage());}
            catch(RuntimeException ex){staffIssues.add("人员计划或费率资料不完整，请核对");}
            if(!staffIssues.isEmpty()){
                missing=true;staffStatus.put("status","PENDING");
                for(String issue:staffIssues)issues.add(label+"："+issue);
            }
        }
        result.put("businessAmount",business);result.put("personnelAmount",missing?null:personnel.setScale(2,RoundingMode.HALF_UP));
        result.put("totalAmount",missing||business==null?null:personnel.add(business).setScale(2,RoundingMode.HALF_UP));
        if(personnel.compareTo(new BigDecimal("99999999999999.99"))>0||business!=null&&personnel.add(business).compareTo(new BigDecimal("99999999999999.99"))>0)throw new ServiceException("合计预算超出金额上限");
        result.put("plannedBusinessAmount",external);result.put("externalRevenueAmount",externalRevenue);
        result.put("parentFundingRevenue",fundingRevenue);
        result.put("revenueAmount",revenue);result.put("status",issues.isEmpty()?"READY":"PENDING");
        BigDecimal plannedCost=missing?null:personnel.add(external).setScale(2,RoundingMode.HALF_UP);
        result.put("plannedTotalCost",plannedCost);result.put("profit",plannedCost==null?null:revenue.subtract(plannedCost));
        BigDecimal oneTime=external.subtract(plannedAmount(recurring(proposal.getExpenseLines()),"amount","occurDate",start,end,false,issues,forecastOrigin));
        if("DAILY".equals(mode)&&startup!=null&&oneTime.compareTo(startup)>0)issues.add("启动预算不能低于本期一次性支出 "+oneTime);
        if("DAILY".equals(mode)&&oneTime.signum()>0&&startup==null)issues.add("每日预算模式下请单独设置一次性启动预算");
        if("DAILY".equals(mode)&&start!=null&&end!=null&&!end.isBefore(start)&&daily!=null){
            boolean complete=!missing||"CASH_EXPENSE".equals(scope);
            BigDecimal controlled=external.subtract(oneTime).add("FULL_COST".equals(scope)?personnel:BigDecimal.ZERO);
            result.put("expectedDailyCost",complete?controlled.divide(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start,end)+1),2,RoundingMode.HALF_UP):null);
            BigDecimal peak=BigDecimal.ZERO;LocalDate peakDate=start,firstExceeded=null;int exceededDays=0;
            for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)){
                BigDecimal dayCost=recurringDailyCost(proposal.getExpenseLines(),day,issues);
                if("FULL_COST".equals(scope))dayCost=dayCost.add(dailyPersonnel.getOrDefault(day,BigDecimal.ZERO));
                if(dayCost.compareTo(peak)>0){peak=dayCost;peakDate=day;}
                if(dayCost.compareTo(daily)>0){exceededDays++;if(firstExceeded==null)firstExceeded=day;}
            }
            result.put("peakDailyCost",complete?peak.setScale(2,RoundingMode.CEILING):null);
            result.put("knownPeakDailyCost",peak.setScale(2,RoundingMode.CEILING));result.put("peakDailyDate",peakDate.toString());
            if(firstExceeded!=null)issues.add("每日预算上限 "+daily+" "+currency+" 不足：从 "+firstExceeded+" 起共 "+exceededDays+" 天超限，最高日成本"+(complete?" ":"至少 ")+peak.setScale(2,RoundingMode.CEILING)+" "+currency+"（"+peakDate+"）");
        }
        result.put("staffingStatus",staffingStatus);
        result.put("status",issues.isEmpty()?"READY":"PENDING");
        result.put("revenueLines",rows(proposal.getRevenueLines()));result.put("expenseLines",rows(proposal.getExpenseLines()));
        result.put("issues",issues);result.put("basis",basis);return result;
    }

    private void validateLineDates(BusinessProjectProposal proposal,List<Map<String,Object>> lines,String field,String label,List<String> issues){
        int index=0;for(Map<String,Object> line:rows(lines)){
            ++index;
            String issue="收入测算".equals(label)
                ? com.ruoyi.business.support.BusinessProposalPlanDates.revenueIssue(line.get(field),proposal.getPlanStartDate(),proposal.getPlanEndDate(),label,index)
                : "支出计划".equals(label)
                    ? com.ruoyi.business.support.BusinessProposalPlanDates.expenseIssue(line.get(field),proposal.getPlanStartDate(),proposal.getPlanEndDate(),label,index)
                    : com.ruoyi.business.support.BusinessProposalPlanDates.issue(line.get(field),proposal.getPlanStartDate(),proposal.getPlanEndDate(),label,index);
            if(issue!=null)issues.add(issue);
        }
    }
    private void addDateIssue(List<String> issues,List<LocalDate> dates,String reason){
        if(!dates.isEmpty())issues.add(dates.get(0)+(dates.size()==1?"":" 至 "+dates.get(dates.size()-1)+"（期间共 "+dates.size()+" 个工作日）")+" "+reason);
    }
    private void addLegacyDailyCosts(Map<LocalDate,BigDecimal> costs,LocalDate start,LocalDate end,BigDecimal amount,boolean weekdaysOnly){
        List<LocalDate> days=new ArrayList<>();
        for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1))if(!weekdaysOnly||d.getDayOfWeek().getValue()<=5)days.add(d);
        if(!days.isEmpty())for(LocalDate d:days)costs.merge(d,amount.divide(BigDecimal.valueOf(days.size()),8,RoundingMode.HALF_UP),BigDecimal::add);
    }
    private BigDecimal recurringDailyCost(List<Map<String,Object>> lines,LocalDate day,List<String> issues){
        BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> line:rows(lines)){
            String frequency=occurrence(line);if("ONE_TIME".equals(frequency))continue;
            LocalDate from=date(line.get("occurDate"));if(from!=null&&from.isAfter(day))continue;
            // Missing values were already reported by the aggregate calculation.
            if(line.get("amount")==null||"".equals(line.get("amount")))continue;
            BigDecimal amount=money(line.get("amount"),"计划支出",issues);
            total=total.add(amount.divide(new BigDecimal("WEEKLY".equals(frequency)?7:"MONTHLY".equals(frequency)?30:1),8,RoundingMode.HALF_UP));
        }
        return total;
    }
    private BigDecimal plannedAmount(List<Map<String,Object>> lines,String field,String dateField,LocalDate start,LocalDate end,boolean revenue,List<String> issues)
    { return plannedAmount(lines,field,dateField,start,end,revenue,issues,null); }
    private BigDecimal plannedAmount(List<Map<String,Object>> lines,String field,String dateField,LocalDate start,LocalDate end,boolean revenue,List<String> issues,LocalDate forecastOrigin)
    {
        BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> line:rows(lines))
        {
            if(revenue&&!"BASE".equals(String.valueOf(line.getOrDefault("scenario","BASE"))))continue;
            LocalDate d=date(line.get(dateField));String frequency=occurrence(line);
            BigDecimal amount=money(line.get(field),revenue?"预计收入":"计划支出",issues);if(amount==null)continue;
            // An undated one-off belongs to the initial project period, never every forecast month.
            if(d==null&&forecastOrigin!=null&&"ONE_TIME".equals(frequency))d=forecastOrigin;
            if("ONE_TIME".equals(frequency)){if(d!=null&&(start!=null&&d.isBefore(start)||end!=null&&d.isAfter(end)))continue;total=total.add(amount);continue;}
            if(start==null||end==null||end.isBefore(start))continue;
            LocalDate from=d!=null&&d.isAfter(start)?d:start;if(from.isAfter(end))continue;
            BigDecimal days=BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(from,end)+1);
            BigDecimal divisor=new BigDecimal("WEEKLY".equals(frequency)?7:"MONTHLY".equals(frequency)?30:1);
            total=total.add(amount.multiply(days).divide(divisor,2,RoundingMode.HALF_UP));
        }
        return total.setScale(2,RoundingMode.HALF_UP);
    }
    private BigDecimal money(Object value,String label,List<String> issues)
    {
        if(value==null||"".equals(value)){issues.add("请填写"+label);return null;}
        try{BigDecimal amount=new BigDecimal(String.valueOf(value));if(amount.signum()<0||amount.scale()>2||amount.compareTo(new BigDecimal("99999999999999.99"))>0)throw new NumberFormatException();return amount.setScale(2);}
        catch(Exception ex){throw new ServiceException(label+"须为非负金额，最多两位小数且不能超过金额上限");}
    }
    private String occurrence(Map<String,Object> row){String value=String.valueOf(row.getOrDefault("occurrenceType","RECURRING".equals(row.get("expenseType"))?"MONTHLY":"ONE_TIME"));if(!Arrays.asList("ONE_TIME","DAILY","WEEKLY","MONTHLY").contains(value))throw new ServiceException("收支发生方式不正确");return value;}
    private BigDecimal amountOrNull(Object value){return value==null?null:new BigDecimal(String.valueOf(value));}
    private List<Map<String,Object>> rows(List<Map<String,Object>> rows){if(rows==null)return Collections.emptyList();if(rows.size()>100||rows.contains(null))throw new ServiceException("明细不能为空且一次最多100行");return rows;}
    private LocalDate date(Object value)
    {
        if(value==null||"".equals(value))return null;
        try{return value instanceof Date?LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",(Date)value)):LocalDate.parse(String.valueOf(value).substring(0,10));}
        catch(Exception ex){throw new ServiceException("预算或人员计划日期格式不正确");}
    }
}
