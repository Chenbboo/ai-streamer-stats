package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;

@Service
public class BusinessProjectPlanService
{
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessProjectWorkMapper mapper;
    @Autowired private ObjectMapper json;
    @Autowired private BusinessProjectBudgetService budgets;

    public Map<String,Object> plan(Long projectId,Long actor,boolean admin)
    {
        BusinessProject p=projectMapper.selectProjectById(projectId);requireProject(p);
        if(!admin&&!manager(p,actor)&&mapper.selectMembers(projectId).stream().noneMatch(m->actor.equals(id(m.get("userId")))))throw new ServiceException("无权查看项目计划");
        List<Map<String,Object>> changes=mapper.selectPlanChanges(projectId);
        for(Map<String,Object> change:changes)change.put("canReview",canReview(p,change,actor));
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("baselines",mapper.selectBaselines(projectId));result.put("changes",changes);result.put("forecast",mapper.selectForecast(projectId));
        result.put("canRequestChange",actor.equals(p.getMainOwnerUserId())&&mutable(p));result.put("canForecast",actor.equals(p.getMainOwnerUserId())&&mutable(p));result.put("version",p.getVersion());result.put("baselineVersion",p.getBaselineVersion());return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> request(Long projectId,Map<String,Object> body,Long actor,String userName)
    {
        BusinessProject p=projectMapper.selectProjectByIdForUpdate(projectId);requireProject(p);requireMutable(p);
        if(!actor.equals(p.getMainOwnerUserId()))throw new ServiceException("只有项目负责人可以申请调整计划基线");
        if(body.get("version")==null||integer(body.get("version"))!=p.getVersion())throw changed();
        Map<String,Object> proposed=normalize(p,body);Map<String,Object> change=new LinkedHashMap<String,Object>();change.put("projectId",projectId);change.put("baseVersion",p.getBaselineVersion());change.put("snapshotJson",write(proposed));change.put("reason",reason(body.get("reason")));change.put("actorId",actor);change.put("userName",userName);
        boolean self="LIGHT_V1".equals(p.getTemplateVersion());change.put("status",self?"APPROVED":"SUBMITTED");mapper.insertPlanChange(change);
        if(self)apply(p,proposed,actor,userName,"OWNER_AUTHORIZED_CHANGE");
        return mapper.selectPlanChange(id(change.get("changeId")));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> review(Long changeId,Map<String,Object> body,Long actor,String userName)
    {
        Map<String,Object> change=mapper.selectPlanChange(changeId);if(change==null)throw new ServiceException("计划变更不存在");
        BusinessProject p=projectMapper.selectProjectByIdForUpdate(id(change.get("projectId")));requireProject(p);requireMutable(p);change=mapper.selectPlanChange(changeId);
        if(!canReview(p,change,actor))throw new ServiceException("只有独立的项目归属老板可以复核计划变更");
        if(body.get("version")==null||integer(body.get("version"))!=integer(change.get("version")))throw changed();
        String decision=text(body.get("decision"));if(!Arrays.asList("APPROVED","RETURNED").contains(decision))throw new ServiceException("变更复核决定不正确");
        if("APPROVED".equals(decision)){
            if(integer(change.get("baseVersion"))!=p.getBaselineVersion())throw new ServiceException("原计划基线已变化，请重新申请变更");
            Map<String,Object> proposed;try{proposed=json.readValue(text(change.get("snapshotJson")),new TypeReference<Map<String,Object>>(){});}catch(Exception ex){throw new ServiceException("计划快照无效");}
            proposed=normalize(p,proposed);apply(p,proposed,actor,userName,"SPONSOR_APPROVED_CHANGE");
        }
        change.put("decision",decision);change.put("actorId",actor);change.put("userName",userName);change.put("reason",reason(body.get("reason")));if(mapper.reviewPlanChange(change)!=1)throw changed();return mapper.selectPlanChange(changeId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> forecast(Long projectId,Map<String,Object> body,Long actor,String userName)
    {
        BusinessProject p=projectMapper.selectProjectByIdForUpdate(projectId);requireProject(p);requireMutable(p);if(!actor.equals(p.getMainOwnerUserId()))throw new ServiceException("只有项目负责人可以维护预测");
        if(body.get("version")==null||integer(body.get("version"))!=p.getVersion())throw changed();
        Date end=DateUtils.parseDate(body.get("forecastEndDate"));if(end==null||p.getPlanStartDate()!=null&&end.before(p.getPlanStartDate()))throw new ServiceException("预测结束日期不正确");
        Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("projectId",projectId);row.put("projectVersion",p.getVersion());row.put("forecastEndDate",end);row.put("forecastCost",money(body.get("forecastCost")));row.put("reason",reason(body.get("reason")));row.put("actorId",actor);row.put("userName",userName);
        if(mapper.touchProject(row)!=1)throw changed();mapper.insertForecast(row);return mapper.selectForecast(projectId);
    }

    private void apply(BusinessProject p,Map<String,Object> proposed,Long actor,String userName,String source)
    {
        proposed.put("projectId",p.getProjectId());proposed.put("baseVersion",p.getBaselineVersion());proposed.put("projectVersion",p.getVersion());proposed.put("userName",userName);
        if(!BusinessMemberDayCostService.enabled(p)&&mapper.countAssignmentsOutside(proposed)>0)throw new ServiceException("变更日期不覆盖当前资源计划，请先停用并调整受影响安排");
        if(mapper.applyPlanChange(proposed)!=1)throw changed();Map<String,Object> baseline=new LinkedHashMap<String,Object>();baseline.put("projectId",p.getProjectId());baseline.put("baselineVersion",p.getBaselineVersion()+1);baseline.put("templateVersion",p.getTemplateVersion());
        Map<String,Object> snapshot=new LinkedHashMap<String,Object>(proposed);snapshot.put("assignments",mapper.selectAssignments(p.getProjectId()));snapshot.put("tasks",projectMapper.selectTasks(p.getProjectId()));snapshot.put("templateSnapshotJson",proposed.getOrDefault("templateSnapshotJson",p.getTemplateSnapshotJson()));
        baseline.put("snapshotJson",write(snapshot));baseline.put("authorizationSource",source);baseline.put("userId",actor);baseline.put("userName",userName);mapper.insertBaseline(baseline);
    }
    private Map<String,Object> normalize(BusinessProject p,Map<String,Object> input)
    {
        Map<String,Object> row=new LinkedHashMap<String,Object>();String objective=text(input.get("objective")),criteria=text(input.get("acceptanceCriteria"));
        if(objective==null||objective.isEmpty()||objective.length()>1000||criteria==null||criteria.isEmpty()||criteria.length()>2000)throw new ServiceException("范围目标和验收标准不能为空或超出长度");
        Date from=DateUtils.parseDate(input.get("planStartDate")),to=DateUtils.parseDate(input.get("planEndDate"));
        String endText=text(input.get("planEndDate"));
        if(from==null||endText!=null&&!endText.isEmpty()&&to==null||to!=null&&to.before(from))throw new ServiceException("请填写有效的计划起止日期；不限期项目可以不设置结束日期");
        row.put("objective",objective);row.put("acceptanceCriteria",criteria);row.put("planStartDate",DateUtils.parseDateToStr("yyyy-MM-dd",from));row.put("planEndDate",to==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",to));row.put("budgetLimit",money(input.get("budgetLimit")));
        if(p.getBudget()!=null)
        {
            com.ruoyi.business.domain.BusinessProjectProposal estimate=new com.ruoyi.business.domain.BusinessProjectProposal();
            estimate.setPlanStartDate(from);estimate.setPlanEndDate(to);estimate.setTemplateVersion(p.getTemplateVersion());estimate.setCompanyDeptId(p.getCompanyDeptId());estimate.setBaseCurrency(p.getBaseCurrency());
            Map<String,Object> requested=input.get("budget") instanceof Map?new LinkedHashMap<String,Object>((Map<String,Object>)input.get("budget")):new LinkedHashMap<String,Object>(p.getBudget());
            if(to==null&&"PROJECT".equals(requested.get("cycle")))requested.put("cycle","MONTH");estimate.setBudget(requested);
            estimate.setRevenueLines((List<Map<String,Object>>)requested.get("revenueLines"));
            estimate.setExpenseLines((List<Map<String,Object>>)requested.get("expenseLines"));
            List<Map<String,Object>> staff=new ArrayList<Map<String,Object>>();
            for(Map<String,Object> assignment:mapper.selectAssignments(p.getProjectId()))if("ACTIVE".equals(assignment.get("status")))
            {Map<String,Object> person=new LinkedHashMap<String,Object>(assignment);person.put("planStartDate",person.get("effectiveFrom"));person.put("planEndDate",person.get("effectiveTo"));person.put("participationMode",person.get("effectiveTo")==null?"UNLIMITED":"CUSTOM");staff.add(person);}
            if(BusinessMemberDayCostService.enabled(p)) {
                Set<Long> activeMembers=new HashSet<>();
                for(Map<String,Object> member:mapper.selectMembers(p.getProjectId()))
                    if("0".equals(String.valueOf(member.get("status")))&&!"OBSERVER".equals(member.get("memberRole")))activeMembers.add(id(member.get("userId")));
                staff.removeIf(person->!activeMembers.contains(id(person.get("userId"))));
                Set<Long> planned=new HashSet<>();for(Map<String,Object> person:staff)planned.add(id(person.get("userId")));
                for(Long uid:activeMembers)if(!planned.contains(uid)) {Map<String,Object> person=new LinkedHashMap<>();person.put("userId",uid);person.put("participationMode","FOLLOW_PROJECT");person.put("calendarId",1L);staff.add(person);}
            }
            estimate.setStaffingLines(staff);
            Map<String,Object> budget=budgets.estimateResourcePlan(estimate);
            if(!"READY".equals(budget.get("status")))throw new ServiceException("预算尚未计算完整："+budget.get("issues"));
            row.put("budget",budget);row.put("budgetLimit","TOTAL".equals(budget.getOrDefault("mode","TOTAL"))?budget.get("totalAmount"):null);
            row.put("budgetMode",budget.getOrDefault("mode","TOTAL"));row.put("budgetScope",budget.getOrDefault("scope","FULL_COST"));
            row.put("dailyBudgetLimit",budget.get("dailyLimit"));row.put("startupBudgetLimit",budget.get("startupLimit"));row.put("budgetReason",budget.get("reason"));
            try{Map<String,Object> snapshot=json.readValue(p.getTemplateSnapshotJson(),new TypeReference<Map<String,Object>>(){});snapshot.put("budget",budget);row.put("templateSnapshotJson",write(snapshot));}catch(Exception ex){throw new ServiceException("预算快照无法保存");}
        }
        return row;
    }
    private boolean canReview(BusinessProject p,Map<String,Object> row,Long actor){return mutable(p)&&"SUBMITTED".equals(row.get("status"))&&actor.equals(sponsor(p))&&!actor.equals(id(row.get("requestUserId")));}
    private boolean mutable(BusinessProject p){return !BusinessProjectLifecycle.isAccountingClosed(p)&&!Arrays.asList("CLOSED","CANCELED","ACCEPTANCE").contains(p.getStatus());}
    private void requireMutable(BusinessProject p){if(!mutable(p))throw new ServiceException("当前项目状态不能调整计划");}
    private void requireProject(BusinessProject p){if(p==null||!BusinessMemberDayCostService.enabled(p)&&!"ACTUAL_WORK_V1".equals(p.getCostPolicyVersion()))throw new ServiceException("该项目未启用版本化计划");}
    private boolean manager(BusinessProject p,Long actor){return actor.equals(p.getMainOwnerUserId())||actor.equals(sponsor(p));}
    private Long sponsor(BusinessProject p){return p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();}
    private String reason(Object v){String s=text(v);if(s==null||s.isEmpty()||s.length()>2000)throw new ServiceException("请填写2000字符内的变更说明");return s;}
    private BigDecimal money(Object v){if(v==null||"".equals(v))return null;try{BigDecimal d=new BigDecimal(String.valueOf(v));if(d.signum()<0||d.scale()>2)throw new Exception();return d;}catch(Exception ex){throw new ServiceException("金额须为非负且最多两位小数");}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new ServiceException("计划快照无法保存");}}
    private String text(Object v){return v==null?null:String.valueOf(v).trim();}
    private Long id(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private int integer(Object v){try{return Integer.parseInt(String.valueOf(v));}catch(Exception ex){throw changed();}}
    private ServiceException changed(){return new ServiceException("项目或变更版本已变化，请刷新后重试");}
}
