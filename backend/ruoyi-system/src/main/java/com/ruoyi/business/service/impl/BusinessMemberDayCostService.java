package com.ruoyi.business.service.impl;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;

/** Splits each eligible member's daily rate across projects by effective project weights; no timesheet input. */
@Service
public class BusinessMemberDayCostService {
    public static final String POLICY="MEMBER_DAYS_V1";
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessProjectWorkMapper work;
    @Autowired private BusinessMemberDayCostMapper mapper;
    @Autowired private ObjectMapper json;
    @Autowired @Lazy private IBusinessAccountingService accounting;

    public static boolean enabled(BusinessProject p){return p!=null&&POLICY.equals(p.getCostPolicyVersion());}
    public List<Long> openProjects(){return mapper.selectOpenProjects();}
    public int pending(Long projectId){return mapper.countPending(projectId);}
    public void archiveMembership(Long projectId,Long userId){mapper.archiveMembership(projectId,userId);}
    public List<Map<String,Object>> dayCosts(Long projectId,Date date){return mapper.selectDayCosts(projectId,date);}

    public void saveRole(Long projectId,Long userId,Date date,String role,String operator){mapper.saveRolePeriod(projectId,userId,date,role,operator);}
    // A priced day is an accounting fact. Later policy/role/calendar changes never rewrite it.
    private List<Map<String,Object>> preservePriced(Long projectId,LocalDate from,LocalDate to,List<Map<String,Object>> desired){
        Map<String,Map<String,Object>> merged=new LinkedHashMap<>();
        for(Map<String,Object> row:desired)merged.put(row.get("userId")+":"+day(row.get("bizDate")),row);
        for(Map<String,Object> row:mapper.selectCosts(projectId)){
            LocalDate d=day(row.get("bizDate"));
            if(!d.isBefore(from)&&!d.isAfter(to)&&"PRICED".equals(row.get("pricingStatus"))&&row.get("amount")!=null)
                merged.put(row.get("userId")+":"+d,new LinkedHashMap<>(row));
        }
        List<Map<String,Object>> result=new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(r->day(r.get("bizDate"))));return result;
    }
    public List<Map<String,Object>> overview(Long projectId,String date){
        BusinessProject p=projects.selectProjectById(projectId);
        LocalDate d=day(date),start=projectStart(p);
        if(start==null||d.isBefore(start)||d.isAfter(LocalDate.now())||p.getActualEndDate()!=null&&d.isAfter(day(p.getActualEndDate())))return Collections.emptyList();
        List<Map<String,Object>> rows=BusinessProjectLifecycle.isAccountingClosed(p)?mapper.selectDayCosts(projectId,java.sql.Date.valueOf(d)):calculate(p,d,d);
        for(Map<String,Object> row:rows){Map<String,Object> metadata=mapper.selectStaffMetadata(id(row.get("userId")),id(row.get("ratePolicyId")));if(metadata!=null)row.putAll(metadata);if(row.get("basisJson")!=null)try{Map<String,Object> basis=json.readValue(String.valueOf(row.get("basisJson")),new TypeReference<Map<String,Object>>(){});for(String field:Arrays.asList("companyName","companyDeptId","countryRegion","costMode","monthlyCost","standardWorkDays","allocationPercent","fullDailyCost"))if(basis.get(field)!=null)row.put(field,basis.get(field));}catch(Exception ignored){}row.put("projectName",p.getProjectName());row.put("costPolicyVersion",POLICY);row.put("personnelCost",row.get("amount"));row.put("dailyCost",row.get("amount"));row.put("workingDays",1);row.put("costStatus","PRICED".equals(row.get("pricingStatus"))?"READY":"PENDING_COST");}
        return rows;
    }

    @Transactional
    public void synchronize(Long projectId) {
        BusinessProject p=projects.selectProjectByIdForUpdate(projectId);
        if(!enabled(p)||BusinessProjectLifecycle.isAccountingClosed(p))return;
        LocalDate from=projectStart(p);
        if(from==null||from.isAfter(LocalDate.now()))return;
        LocalDate to=LocalDate.now();
        if(p.getActualEndDate()!=null&&day(p.getActualEndDate()).isBefore(to))to=day(p.getActualEndDate());
        List<Map<String,Object>> desired=calculateCurrent(p,from,to);
        List<Map<String,Object>> stored=mapper.selectCosts(projectId);
        // Also retain priced rows outside a changed project window.
        Map<String,List<Map<String,Object>>> old=group(stored),next=group(desired);
        for(Map<String,Object> row:stored)if("PRICED".equals(row.get("pricingStatus"))&&row.get("amount")!=null){
            String key=day(row.get("bizDate")).toString();List<Map<String,Object>> rows=next.computeIfAbsent(key,k->new ArrayList<>());
            rows.removeIf(r->Objects.equals(id(r.get("userId")),id(row.get("userId"))));rows.add(row);
        }
        Set<String> dates=new TreeSet<>();dates.addAll(old.keySet());dates.addAll(next.keySet());
        Set<String> legacyDates=new HashSet<>(mapper.selectLegacyResultDates(projectId));dates.addAll(legacyDates);
        for(String date:dates) {
            List<Map<String,Object>> before=old.getOrDefault(date,Collections.emptyList()),after=next.getOrDefault(date,Collections.emptyList());
            if(signature(before).equals(signature(after))&&!legacyDates.contains(date))continue;
            mapper.deleteDay(projectId,date);
            for(Map<String,Object> cost:after)mapper.insertCost(cost);
            accounting.recalculatePersonnelCost(projectId,java.sql.Date.valueOf(date),"member-day-cost");
        }
    }
    private Map<String,List<Map<String,Object>>> group(List<Map<String,Object>> rows){
        Map<String,List<Map<String,Object>>> result=new TreeMap<>();
        for(Map<String,Object> row:rows)result.computeIfAbsent(day(row.get("bizDate")).toString(),k->new ArrayList<>()).add(row);
        return result;
    }
    private String signature(List<Map<String,Object>> rows){
        List<String> values=new ArrayList<>();for(Map<String,Object> row:rows)values.add(row.get("userId")+":"+row.get("basisJson"));
        Collections.sort(values);return values.toString();
    }
    public List<Map<String,Object>> calculate(BusinessProject p,LocalDate from,LocalDate to){
        return preservePriced(p.getProjectId(),from,to,calculateCurrent(p,from,to));
    }
    public List<Map<String,Object>> calculateCurrent(BusinessProject p,LocalDate from,LocalDate to){
        List<Map<String,Object>> result=new ArrayList<>();
        List<Map<String,Object>> roles=mapper.selectRolePeriods(p.getProjectId());
        List<Map<String,Object>> pauses=mapper.selectCostPauses(p.getProjectId());
        List<Map<String,Object>> allocations=mapper.selectAllocationPeriods(p.getProjectId());
        List<Map<String,Object>> assignments=work.selectAssignments(p.getProjectId()),calendars=work.selectCalendars();
        List<Map<String,Object>> memberships=new ArrayList<>(work.selectMembers(p.getProjectId()));memberships.addAll(mapper.selectPastMemberships(p.getProjectId()));
        Set<String> calculated=new HashSet<>();Map<String,Map<String,Object>> metadataCache=new HashMap<>();
        for(Map<String,Object> member:memberships) {

            Long userId=id(member.get("userId"));
            List<Map<String,Object>> userAllocations=new ArrayList<>();
            for(Map<String,Object> allocation:allocations)if(userId.equals(id(allocation.get("userId"))))userAllocations.add(allocation);
            LocalDate joined=day(member.get("joinedDate")),left=day(member.get("leftDate"));
            if(!"0".equals(String.valueOf(member.get("status")))&&left==null)continue;
            List<Map<String,Object>> plans=new ArrayList<>();
            for(Map<String,Object> a:assignments)if(userId.equals(id(a.get("userId")))&&("FOLLOW_PROJECT".equals(a.get("participationMode"))||joined==null||day(a.get("effectiveTo"))==null||!day(a.get("effectiveTo")).isBefore(joined)))plans.add(a);
            List<Map<String,Object>> rates=work.selectBudgetRates(userId,from.toString(),to.toString());
            for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)) {
                boolean released=false;
                for(Map<String,Object> pause:pauses)if(!date.isBefore(day(pause.get("effectiveFrom")))&&(pause.get("effectiveTo")==null||date.isBefore(day(pause.get("effectiveTo"))))){released=true;break;}
                if(released)continue;
                if(projectStart(p)!=null&&date.isBefore(projectStart(p))||p.getActualEndDate()!=null&&date.isAfter(day(p.getActualEndDate())))continue;
                if(joined!=null&&date.isBefore(joined)||left!=null&&date.isAfter(left))continue;
                String role=String.valueOf(member.get("memberRole"));
                for(Map<String,Object> period:roles)if(userId.equals(id(period.get("userId")))&&!date.isBefore(day(period.get("effectiveFrom"))))role=String.valueOf(period.get("memberRole"));
                if("OBSERVER".equals(role))continue;
                Map<String,Object> plan=null;
                for(Map<String,Object> a:plans)if(participates(a,date)&&("ACTIVE".equals(a.get("status"))||"RETIRED".equals(a.get("status"))&&day(a.get("retiredTime"))!=null&&date.isBefore(day(a.get("retiredTime"))))){plan=a;break;}
                if(!plans.isEmpty()&&plan==null)continue;
                Map<String,Object> calendar=null;
                if(plan!=null&&plan.get("calendarId")!=null){for(Map<String,Object> c:calendars)if(id(c.get("calendarId")).equals(id(plan.get("calendarId")))){calendar=c;break;}}
                else for(Map<String,Object> c:calendars)if(covers(c,"effectiveFrom","effectiveTo",date)&&(calendar==null||id(c.get("calendarId"))<id(calendar.get("calendarId"))))calendar=c;
                Map<String,Object> cost=new LinkedHashMap<>();cost.put("projectId",p.getProjectId());cost.put("userId",userId);cost.put("userName",member.get("userName"));cost.put("bizDate",date.toString());cost.put("currency",p.getBaseCurrency());
                Map<String,Object> allocation=null;
                for(Map<String,Object> candidate:userAllocations)if(covers(candidate,"effectiveFrom","effectiveTo",date)){allocation=candidate;break;}
                BigDecimal allocationPercent=userAllocations.isEmpty()?new BigDecimal("100"):
                    allocation==null?null:new BigDecimal(String.valueOf(allocation.get("allocationValue")));
                String issue=allocationPercent==null?"缺少该日期有效的项目投入权重":null;BigDecimal amount=null;BigDecimal fullDailyCost=null;Map<String,Object> rate=null;
                if(allocation!=null&&"PENDING".equals(allocation.get("confirmationStatus")))issue="人员投入待确认，请由相关项目负责人确认分配";
                if(issue==null&&(allocationPercent.signum()<0||allocationPercent.compareTo(new BigDecimal("100"))>0))issue="项目投入权重必须在0%至100%之间";
                if(calendar==null||!covers(calendar,"effectiveFrom","effectiveTo",date))issue="缺少该日期有效的工作日历";
                else if(!workingDay(calendar,date))continue;
                if(issue==null){
                    List<Map<String,Object>> matches=new ArrayList<>();for(Map<String,Object> r:rates)if(covers(r,"effectiveFrom","effectiveTo",date))matches.add(r);
                    if(matches.size()!=1)issue=matches.isEmpty()?"缺少有效用人成本":"成本生效日期重叠";
                    else {rate=matches.get(0);if(!p.getBaseCurrency().equals(rate.get("currency")))issue="成本币种与项目不一致";
                        else try{fullDailyCost=dailyRate(rate);amount=fullDailyCost.multiply(allocationPercent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}catch(ServiceException ex){issue=ex.getMessage();}}
                }
                String metadataKey=userId+":"+(rate==null?"":rate.get("policyId"));
                final Long metadataPolicy=rate==null?null:id(rate.get("policyId"));
                Map<String,Object> metadata=metadataCache.computeIfAbsent(metadataKey,k->mapper.selectStaffMetadata(userId,metadataPolicy));
                cost.put("companyName",member.get("companyName"));cost.put("companyDeptId",member.get("companyDeptId"));cost.put("countryRegion",member.get("countryRegion"));
                cost.put("costMode",rate==null?null:rate.get("costMode"));cost.put("monthlyCost",rate!=null&&"MONTHLY".equals(rate.get("costMode"))?rate.get("unitCost"):null);cost.put("standardWorkDays",rate==null?null:rate.get("standardWorkDays"));
                if(metadata!=null)cost.putAll(metadata);
                cost.put("calendarId",calendar==null?null:calendar.get("calendarId"));cost.put("ratePolicyId",rate==null?null:rate.get("policyId"));cost.put("amount",amount);cost.put("issue",issue);cost.put("pricingStatus",issue==null?"PRICED":"PENDING");
                Map<String,Object> basis=new LinkedHashMap<>();basis.put("costPolicyVersion",POLICY);basis.put("formula","工作日数 × 当日有效日成本 × 项目投入权重");basis.put("workingDays",1);basis.put("bizDate",date.toString());basis.put("userName",member.get("userName"));basis.put("calendarId",cost.get("calendarId"));basis.put("calendarVersion",calendar==null?null:calendar.get("version"));basis.put("ratePolicyId",cost.get("ratePolicyId"));basis.put("rateVersion",rate==null?null:rate.get("version"));basis.put("allocationId",allocation==null?null:allocation.get("allocationId"));basis.put("allocationVersion",allocation==null?null:allocation.get("version"));basis.put("allocationPercent",allocationPercent);basis.put("fullDailyCost",fullDailyCost);basis.put("dailyCost",amount);basis.put("currency",p.getBaseCurrency());basis.put("issue",issue);for(String field:Arrays.asList("companyName","companyDeptId","countryRegion","costMode","monthlyCost","standardWorkDays"))basis.put(field,cost.get(field));
                try{cost.put("basisJson",json.writeValueAsString(basis));}catch(Exception ex){throw new ServiceException("工作日成本依据无法保存");}
                if(calculated.add(userId+":"+date))result.add(cost);
            }
        }
        result.sort(Comparator.comparing(c->String.valueOf(c.get("bizDate"))));
        return result;
    }
    @Transactional
    public void synchronizeAllocationChange(Long projectId,Date effectiveDate,String operator){
        BusinessProject p=projects.selectProjectByIdForUpdate(projectId);
        if(!enabled(p)||BusinessProjectLifecycle.isAccountingClosed(p))return;
        LocalDate from=day(effectiveDate),start=projectStart(p),to=LocalDate.now();
        if(start!=null&&start.isAfter(from))from=start;
        if(p.getActualEndDate()!=null&&day(p.getActualEndDate()).isBefore(to))to=day(p.getActualEndDate());
        if(from.isAfter(to))return;
        Map<String,List<Map<String,Object>>> before=group(mapper.selectCosts(projectId));
        Map<String,List<Map<String,Object>>> after=group(calculateCurrent(p,from,to));
        for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)){
            String key=date.toString();
            if(signature(before.getOrDefault(key,Collections.emptyList())).equals(signature(after.getOrDefault(key,Collections.emptyList()))))continue;
            mapper.deleteDay(projectId,key);
            for(Map<String,Object> cost:after.getOrDefault(key,Collections.emptyList()))mapper.insertCost(cost);
            accounting.recalculatePersonnelCost(projectId,java.sql.Date.valueOf(date),operator);
        }
    }
    public boolean workingDay(Map<String,Object> calendar,LocalDate date){
        Object raw=calendar.get("exceptionsJson");
        if(raw!=null)try{for(Map<String,Object> e:json.readValue(String.valueOf(raw),new TypeReference<List<Map<String,Object>>>(){}))if(date.toString().equals(String.valueOf(e.get("bizDate"))))return new BigDecimal(String.valueOf(e.get("minutes"))).signum()>0;}catch(Exception ex){throw new ServiceException("工作日历例外格式不正确");}
        return Arrays.asList(String.valueOf(calendar.get("workingWeekdays")).split(",")).contains(String.valueOf(date.getDayOfWeek().getValue()))&&new BigDecimal(String.valueOf(calendar.get("dailyMinutes"))).signum()>0;
    }
    public static BigDecimal dailyRate(Map<String,Object> rate){
        try {
            BigDecimal unit=new BigDecimal(String.valueOf(rate.get("unitCost")));if(unit.signum()<0)throw new IllegalArgumentException();
            String mode=String.valueOf(rate.get("costMode"));
            if("MONTHLY".equals(mode)){BigDecimal days=new BigDecimal(String.valueOf(rate.get("standardWorkDays")));if(days.signum()<=0)throw new IllegalArgumentException();return unit.divide(days,2,RoundingMode.HALF_UP);}
            if("DAILY".equals(mode))return unit.setScale(2,RoundingMode.HALF_UP);
            if("HOURLY".equals(mode))return unit.multiply(new BigDecimal("8")).setScale(2,RoundingMode.HALF_UP);
        }catch(Exception ignored){}
        throw new ServiceException("缺少可折算的有效日成本");
    }
    public Map<String,Object> workspace(Long projectId,Map<String,Object> query,Long actor,boolean admin){
        BusinessProject p=projects.selectProjectById(projectId);if(p==null)throw new ServiceException("项目不存在");
        if(!enabled(p))throw new ServiceException("该历史项目保留原核算结果，请在项目核算中查看");
        boolean manager=admin||actor.equals(p.getMainOwnerUserId())||actor.equals(p.getSponsorOwnerUserId())||actor.equals(p.getInitiatorUserId());
        if(!manager&&work.selectMembers(projectId).stream().noneMatch(m->actor.equals(id(m.get("userId")))&&"0".equals(String.valueOf(m.get("status")))))throw new ServiceException("无权查看项目人员成本");
        LocalDate to=query.get("dateTo")==null?LocalDate.now():day(query.get("dateTo"));LocalDate from=query.get("dateFrom")==null?to.withDayOfMonth(1):day(query.get("dateFrom"));
        if(from==null||to==null||to.isBefore(from)||to.toEpochDay()-from.toEpochDay()>730)throw new ServiceException("请选择两年以内的日期范围");
        LocalDate start=projectStart(p);if(start!=null&&start.isAfter(from))from=start;
        if(to.isAfter(LocalDate.now()))to=LocalDate.now();if(p.getActualEndDate()!=null&&day(p.getActualEndDate()).isBefore(to))to=day(p.getActualEndDate());
        Map<Long,Map<String,Object>> summary=new LinkedHashMap<>();
        final LocalDate rangeFrom=from,rangeTo=to;
        List<Map<String,Object>> costs=to.isBefore(from)?Collections.emptyList():BusinessProjectLifecycle.isAccountingClosed(p)?new ArrayList<>():calculate(p,from,to);
        if(BusinessProjectLifecycle.isAccountingClosed(p))for(Map<String,Object> c:mapper.selectCosts(projectId))if(!day(c.get("bizDate")).isBefore(rangeFrom)&&!day(c.get("bizDate")).isAfter(rangeTo))costs.add(c);
        BigDecimal total=BigDecimal.ZERO;int pending=0;
        for(Map<String,Object> c:costs){Long uid=id(c.get("userId"));if(!manager&&!actor.equals(uid))continue;
            Map<String,Object> row=summary.computeIfAbsent(uid,k->{Map<String,Object> v=new LinkedHashMap<>();v.put("userId",uid);v.put("userName",c.get("userName"));v.put("workingDays",0);v.put("amount",BigDecimal.ZERO);v.put("issues",new LinkedHashSet<String>());return v;});
            row.put("workingDays",((Integer)row.get("workingDays"))+1);row.putIfAbsent("startDate",c.get("bizDate"));row.put("endDate",c.get("bizDate"));
            if(c.get("amount")==null){pending++;((Set<String>)row.get("issues")).add(String.valueOf(c.get("issue")));}
            else{BigDecimal amount=(BigDecimal)c.get("amount");total=total.add(amount);row.put("amount",((BigDecimal)row.get("amount")).add(amount));}
        }
        for(Map<String,Object> r:summary.values())if(!((Set<?>)r.get("issues")).isEmpty())r.put("amount",null);
        Map<String,Object> result=new LinkedHashMap<>();result.put("rows",summary.values());result.put("totalAmount",pending==0?total:null);result.put("pendingCount",pending);result.put("currency",p.getBaseCurrency());result.put("overdue",p.getPlanEndDate()!=null&&day(p.getPlanEndDate()).isBefore(LocalDate.now())&&p.getActualEndDate()==null);result.put("dateFrom",from.toString());result.put("dateTo",to.toString());return result;
    }
    private boolean participates(Map<String,Object> assignment,LocalDate date){
        LocalDate from=day(assignment.get("effectiveFrom")),to=day(assignment.get("effectiveTo"));
        return (from==null||!date.isBefore(from))&&("FOLLOW_PROJECT".equals(assignment.get("participationMode"))||to==null||!date.isAfter(to));
    }
    private boolean covers(Map<String,Object> row,String from,String to,LocalDate d){LocalDate a=day(row.get(from)),b=day(row.get(to));return (a==null||!d.isBefore(a))&&(b==null||!d.isAfter(b));}
    private static LocalDate projectStart(BusinessProject p){LocalDate planned=day(p.getPlanStartDate()),actual=day(p.getActualStartDate());return planned==null?actual:actual==null||planned.isAfter(actual)?planned:actual;}
    private static Long id(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private static LocalDate day(Object value){if(value==null)return null;if(value instanceof Date)return LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",(Date)value));return LocalDate.parse(String.valueOf(value).substring(0,10));}
}
