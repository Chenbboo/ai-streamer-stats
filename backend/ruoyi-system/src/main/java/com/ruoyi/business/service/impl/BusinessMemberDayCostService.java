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

/** One full daily rate per eligible member and working date; no workload input. */
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

    public List<Map<String,Object>> overview(Long projectId,String date){
        BusinessProject p=projects.selectProjectById(projectId);
        LocalDate d=day(date),start=projectStart(p);
        if(start==null||d.isBefore(start)||d.isAfter(LocalDate.now())||p.getActualEndDate()!=null&&d.isAfter(day(p.getActualEndDate())))return Collections.emptyList();
        List<Map<String,Object>> rows=BusinessProjectLifecycle.isAccountingClosed(p)?mapper.selectDayCosts(projectId,java.sql.Date.valueOf(d)):calculate(p,d,d);
        for(Map<String,Object> row:rows){row.put("projectName",p.getProjectName());row.put("costPolicyVersion",POLICY);row.put("personnelCost",row.get("amount"));row.put("dailyCost",row.get("amount"));row.put("workingDays",1);row.put("costStatus","PRICED".equals(row.get("pricingStatus"))?"READY":"PENDING_COST");}
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
        List<Map<String,Object>> desired=calculate(p,from,to);
        Map<String,List<Map<String,Object>>> old=group(mapper.selectCosts(projectId)),next=group(desired);
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
        List<Map<String,Object>> result=new ArrayList<>();
        List<Map<String,Object>> assignments=work.selectAssignments(p.getProjectId()),calendars=work.selectCalendars();
        List<Map<String,Object>> memberships=new ArrayList<>(work.selectMembers(p.getProjectId()));memberships.addAll(mapper.selectPastMemberships(p.getProjectId()));
        Set<String> calculated=new HashSet<>();
        for(Map<String,Object> member:memberships) {
            if("OBSERVER".equals(member.get("memberRole")))continue;
            Long userId=id(member.get("userId"));
            LocalDate joined=day(member.get("joinedDate")),left=day(member.get("leftDate"));
            if(!"0".equals(String.valueOf(member.get("status")))&&left==null)continue;
            List<Map<String,Object>> plans=new ArrayList<>();
            for(Map<String,Object> a:assignments)if(userId.equals(id(a.get("userId")))&&(joined==null||day(a.get("effectiveTo"))==null||!day(a.get("effectiveTo")).isBefore(joined)))plans.add(a);
            List<Map<String,Object>> rates=work.selectBudgetRates(userId,from.toString(),to.toString());
            for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)) {
                if(projectStart(p)!=null&&date.isBefore(projectStart(p)))continue;
                if(joined!=null&&date.isBefore(joined)||left!=null&&date.isAfter(left))continue;
                if(p.getPlanEndDate()!=null&&date.isAfter(day(p.getPlanEndDate())))continue;
                Map<String,Object> plan=null;
                for(Map<String,Object> a:plans)if(covers(a,"effectiveFrom","effectiveTo",date)&&("ACTIVE".equals(a.get("status"))||"RETIRED".equals(a.get("status"))&&day(a.get("retiredTime"))!=null&&date.isBefore(day(a.get("retiredTime"))))){plan=a;break;}
                if(!plans.isEmpty()&&plan==null)continue;
                Map<String,Object> calendar=null;
                if(plan!=null&&plan.get("calendarId")!=null){for(Map<String,Object> c:calendars)if(id(c.get("calendarId")).equals(id(plan.get("calendarId")))){calendar=c;break;}}
                else for(Map<String,Object> c:calendars)if(covers(c,"effectiveFrom","effectiveTo",date)&&(calendar==null||id(c.get("calendarId"))<id(calendar.get("calendarId"))))calendar=c;
                Map<String,Object> cost=new LinkedHashMap<>();cost.put("projectId",p.getProjectId());cost.put("userId",userId);cost.put("userName",member.get("userName"));cost.put("bizDate",date.toString());cost.put("currency",p.getBaseCurrency());
                String issue=null;BigDecimal amount=null;Map<String,Object> rate=null;
                if(calendar==null||!covers(calendar,"effectiveFrom","effectiveTo",date))issue="缺少该日期有效的工作日历";
                else if(!workingDay(calendar,date))continue;
                if(issue==null){
                    List<Map<String,Object>> matches=new ArrayList<>();for(Map<String,Object> r:rates)if(covers(r,"effectiveFrom","effectiveTo",date))matches.add(r);
                    if(matches.size()!=1)issue=matches.isEmpty()?"缺少有效用人成本":"成本生效日期重叠";
                    else {rate=matches.get(0);if(!p.getBaseCurrency().equals(rate.get("currency")))issue="成本币种与项目不一致";
                        else try{amount=dailyRate(rate);}catch(ServiceException ex){issue=ex.getMessage();}}
                }
                cost.put("calendarId",calendar==null?null:calendar.get("calendarId"));cost.put("ratePolicyId",rate==null?null:rate.get("policyId"));cost.put("amount",amount);cost.put("issue",issue);cost.put("pricingStatus",issue==null?"PRICED":"PENDING");
                Map<String,Object> basis=new LinkedHashMap<>();basis.put("costPolicyVersion",POLICY);basis.put("formula","工作日数 × 当日有效日成本");basis.put("workingDays",1);basis.put("bizDate",date.toString());basis.put("userName",member.get("userName"));basis.put("calendarId",cost.get("calendarId"));basis.put("calendarVersion",calendar==null?null:calendar.get("version"));basis.put("ratePolicyId",cost.get("ratePolicyId"));basis.put("rateVersion",rate==null?null:rate.get("version"));basis.put("dailyCost",amount);basis.put("currency",p.getBaseCurrency());basis.put("issue",issue);
                try{cost.put("basisJson",json.writeValueAsString(basis));}catch(Exception ex){throw new ServiceException("工作日成本依据无法保存");}
                if(calculated.add(userId+":"+date))result.add(cost);
            }
        }
        result.sort(Comparator.comparing(c->String.valueOf(c.get("bizDate"))));
        return result;
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
        Map<String,Object> result=new LinkedHashMap<>();result.put("rows",summary.values());result.put("totalAmount",pending==0?total:null);result.put("pendingCount",pending);result.put("currency",p.getBaseCurrency());result.put("dateFrom",from.toString());result.put("dateTo",to.toString());return result;
    }
    private boolean covers(Map<String,Object> row,String from,String to,LocalDate d){LocalDate a=day(row.get(from)),b=day(row.get(to));return (a==null||!d.isBefore(a))&&(b==null||!d.isAfter(b));}
    private static LocalDate projectStart(BusinessProject p){LocalDate planned=day(p.getPlanStartDate()),actual=day(p.getActualStartDate());return planned==null?actual:actual==null||planned.isAfter(actual)?planned:actual;}
    private static Long id(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private static LocalDate day(Object value){if(value==null)return null;if(value instanceof Date)return LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",(Date)value));return LocalDate.parse(String.valueOf(value).substring(0,10));}
}
