package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.support.BusinessAllocationWeights;
import com.ruoyi.business.support.BusinessHrDailyCost;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;

/** Read-only views: neither synchronize costs nor alter budget/allocation policy. */
@Service
@Transactional(readOnly=true)
public class BusinessProjectResourceService
{
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessAccountingMapper accounting;
    @Autowired private BusinessProjectResourceMapper resources;
    @Autowired private IBusinessProjectService staffCosts;
    @Autowired private BusinessCompanyAccessService access;
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    private BigDecimal number(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(value));}
    private String day(java.util.Date value){return value==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",value);}
    private LocalDate date(Object value){return value==null?null:LocalDate.parse(String.valueOf(value).substring(0,10));}
    private LocalDate requested(String value,LocalDate fallback){
        if(value==null||value.isEmpty())return fallback;
        try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();return LocalDate.parse(value);}
        catch(RuntimeException ex){throw new ServiceException("请选择有效的日期");}
    }
    private BusinessProject require(Long id,Long actor,boolean admin){
        BusinessProject p=projects.selectProjectById(id);
        if(p==null||p.getDelFlag()!=null&&!"0".equals(p.getDelFlag()))throw new ServiceException("项目不存在");
        if(!admin&&!actor.equals(p.getMainOwnerUserId())&&!access.project(p,actor))throw new ServiceException("无权查看该项目人员成本和预算");
        return p;
    }
    private Map<String,Object> query(Long id,LocalDate from,LocalDate to){
        Map<String,Object> q=new HashMap<>();q.put("projectId",id);q.put("viewAll",true);q.put("dateFrom",from.toString());q.put("dateTo",to.toString());return q;
    }
    public Map<String,Object> personnel(Long id,String dateFrom,String dateTo,boolean entireProject,Long actor,boolean admin){
        BusinessProject p=require(id,actor,admin);LocalDate now=today();
        LocalDate from=requested(dateFrom,now.withDayOfMonth(1)),to=requested(dateTo,now);
        if(entireProject){from=LocalDate.of(1900,1,1);to=now;}
        if(to.isAfter(now))to=now;
        if(to.isBefore(from))throw new ServiceException("请选择截至今天的有效日期区间");
        boolean staffCostManager=admin;
        if(!staffCostManager)try{staffCostManager=SecurityUtils.hasPermi("business:staff:cost");}catch(ServiceException ex){staffCostManager=false;}
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Map<String,Object> snapshot:resources.personnel(id,from.toString(),to.toString())){
            Map<String,Object> row=new LinkedHashMap<>(snapshot);
            rows.add(row);
        }
        Map<String,Object> totals=resources.totals(id,from.toString(),to.toString());
        BigDecimal total=number(totals.get("personnelCost")),allocated=BigDecimal.ZERO;
        for(Map<String,Object> row:rows)allocated=allocated.add(number(row.get("amount")));
        BigDecimal difference=total.subtract(allocated);
        if(difference.signum()!=0){Map<String,Object> row=new LinkedHashMap<>();row.put("userName","未归属人员的核算差额");row.put("amount",difference);row.put("unattributed",true);rows.add(row);}
        boolean canAdjust="MEMBER_DAYS_V1".equals(p.getCostPolicyVersion())&&!Arrays.asList("CLOSED","CANCELED","ACCEPTANCE").contains(p.getStatus())&&!"CLOSED".equals(p.getAccountingState());
        List<com.ruoyi.business.domain.BusinessProjectMember> members=projects.selectMembers(id);
        for(com.ruoyi.business.domain.BusinessProjectMember member:members){
            if(!"0".equals(member.getStatus())||"OBSERVER".equals(member.getMemberRole()))continue;
            Map<String,Object> row=null;
            for(Map<String,Object> candidate:rows)if(String.valueOf(member.getUserId()).equals(String.valueOf(candidate.get("userId")))){row=candidate;break;}
            if(row==null){row=new LinkedHashMap<>();row.put("userId",member.getUserId());row.put("userName",member.getUserNameSnapshot());row.put("amount",BigDecimal.ZERO);rows.add(row);}
            row.put("canAdjust",canAdjust);
            row.put("currentMember",true);
            Map<Long,Map<String,Object>> effective=BusinessAllocationWeights.at(projects.selectUserAllocationTimeline(member.getUserId()),now);
            Map<String,Object> allocation=effective.get(id);
            row.put("allocationPercent",allocation==null?null:allocation.get("allocationValue"));
        }
        for(Map<String,Object> row:rows)attachStaffDailySalary(row,now,actor,staffCostManager);
        int pending=accounting.countPendingCostsInRange(query(id,from,to))+resources.unrecognizedDays(id,from.toString(),to.toString());
        Map<String,Object> result=new LinkedHashMap<>();result.put("projectId",id);result.put("projectName",p.getProjectName());result.put("currency",p.getBaseCurrency());
        result.put("rows",rows);result.put("members",members);result.put("totalAmount",total);result.put("pendingCount",pending);result.put("resultCount",totals.get("resultCount"));
        result.put("entireProject",entireProject);
        result.put("rawCostVisible",rows.stream().anyMatch(row->Boolean.TRUE.equals(row.get("rawCostVisible"))));
        result.put("dateFrom",from.toString());result.put("dateTo",to.toString());result.put("allocationDate",now.toString());result.put("canAdjust",canAdjust);return result;
    }
    /** Use the same effective HR cost policy and standard divisor as the staff cost screen. */
    private void attachStaffDailySalary(Map<String,Object> row,LocalDate asOf,Long actor,boolean staffCostManager){
        row.put("rawCostVisible",false);
        if(row.get("userId")==null)return;
        List<BusinessStaffCostPolicy> policies;
        // Reuse HR authorization: project owners, company grants and finance scopes are checked per person.
        try{policies=staffCosts.staffCostPolicies(number(row.get("userId")).longValueExact(),actor,staffCostManager);}
        catch(ServiceException denied){return;}
        row.put("rawCostVisible",true);
        List<BusinessStaffCostPolicy> rates=new ArrayList<>();
        for(BusinessStaffCostPolicy policy:policies)
            if("ACTIVE".equals(policy.getStatus())&&(policy.getEffectiveFrom()==null||!asOf.isBefore(date(day(policy.getEffectiveFrom()))))
                &&(policy.getEffectiveTo()==null||!asOf.isAfter(date(day(policy.getEffectiveTo())))))rates.add(policy);
        if(rates.size()!=1)return;
        BusinessStaffCostPolicy rate=rates.get(0);
        if(!"MONTHLY".equals(rate.getCostMode())||rate.getUnitCost()==null)return;
        BigDecimal monthly=rate.getUnitCost(),days=BusinessHrDailyCost.standardWorkDays(rate.getCountryRegion(),rate.getStandardWorkDays());
        if(monthly.signum()<0||days.signum()<=0)return;
        row.put("dailySalary",BusinessHrDailyCost.dailyRate(rate.getCostMode(),monthly,days));
        row.put("salaryMonthlyCost",monthly);row.put("salaryStandardWorkDays",days);
        row.put("salaryCurrency",rate.getCurrency());row.put("salaryDate",asOf.toString());
    }
    public List<Map<String,Object>> portfolio(Long companyId,String asOf,Long actor,boolean admin){
        if(companyId==null||!admin&&!access.allowed(actor,companyId,"BUSINESS"))throw new ServiceException("无权查看该公司项目预算");
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> option:accounting.selectProjectOptions(actor,admin,true))if(String.valueOf(companyId).equals(String.valueOf(option.get("companyDeptId"))))
            result.add(budget(((Number)option.get("projectId")).longValue(),asOf,actor,admin));
        return result;
    }
    public Map<String,Object> budget(Long id,String asOf,Long actor,boolean admin){
        BusinessProject p=require(id,actor,admin);LocalDate until=requested(asOf,today());if(until.isAfter(today()))throw new ServiceException("预算统计日期不能晚于今天");
        if(p.getActualEndDate()!=null&&date(day(p.getActualEndDate())).isBefore(until))until=date(day(p.getActualEndDate()));
        Map<String,Object> snapshot=p.getBudget();String mode=p.getBudgetMode()==null?"TOTAL":p.getBudgetMode();
        if(snapshot!=null&&snapshot.get("mode")!=null)mode=String.valueOf(snapshot.get("mode"));
        String scope="DAILY".equals(mode)?(p.getBudgetScope()==null?"FULL_COST":p.getBudgetScope()):"FULL_COST";
        LocalDate from=date(day(p.getActualStartDate()==null?p.getPlanStartDate():p.getActualStartDate())),end=until;
        if(from==null)from=until;
        if(snapshot!=null){LocalDate start=date(snapshot.get("startDate")),finish=date(snapshot.get("endDate"));if(start!=null)from=start;if(finish!=null&&finish.isBefore(end))end=finish;}
        String periodStart=from.toString(),periodEnd=snapshot!=null&&snapshot.get("endDate")!=null?String.valueOf(snapshot.get("endDate")):day(p.getPlanEndDate());
        if("DAILY".equals(mode)){from=until;end=until;periodStart=until.toString();periodEnd=until.toString();}
        BigDecimal limit="NONE".equals(mode)?null:"DAILY".equals(mode)?p.getDailyBudgetLimit():p.getBudgetLimit();
        boolean cumulative=!"DAILY".equals(mode)&&(snapshot==null||"PROJECT".equals(snapshot.get("cycle")));
        if(cumulative){from=LocalDate.of(1900,1,1);end=until;}
        BigDecimal used=BigDecimal.ZERO;int pending=0;
        if(!end.isBefore(from)){
            used="CASH_EXPENSE".equals(scope)?resources.externalCost(id,from.toString(),end.toString()):cumulative?accounting.sumProjectCostToDate(id,java.sql.Date.valueOf(end)):accounting.sumProjectCostInPeriod(id,java.sql.Date.valueOf(from),java.sql.Date.valueOf(end));
            used=number(used);
            if(!"CASH_EXPENSE".equals(scope))pending=accounting.countPendingCostsInRange(query(id,from,end))+resources.unrecognizedDays(id,from.toString(),end.toString());
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("projectId",id);result.put("projectName",p.getProjectName());result.put("currency",p.getBaseCurrency());
        result.put("mode",mode);result.put("scope",scope);result.put("limit",limit);result.put("used",used);result.put("pendingCount",pending);
        // Actual facts do not distinguish recurring expenditure from startup expenditure.
        boolean comparable=!"DAILY".equals(mode);
        result.put("comparable",comparable);
        result.put("remaining",limit==null||pending>0||!comparable?null:limit.subtract(used));result.put("periodStart",periodStart);result.put("periodEnd",periodEnd);
        result.put("asOf",until.toString());result.put("cycle",snapshot==null?"PROJECT":snapshot.get("cycle"));result.put("expired",periodEnd!=null&&date(periodEnd).isBefore(today()));
        result.put("startupLimit",p.getStartupBudgetLimit());result.put("planTab","MEMBER_DAYS_V1".equals(p.getCostPolicyVersion())?"plan":"overview");return result;
    }
}
