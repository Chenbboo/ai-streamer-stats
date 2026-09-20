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
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.business.support.BusinessProjectReadAccess;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;

@Service
public class BusinessProjectPlanService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessProjectWorkMapper mapper;
    @Autowired private ObjectMapper json;
    @Autowired private BusinessProjectBudgetService budgets;

    public Map<String,Object> plan(Long projectId,Long actor,boolean admin)
    {
        BusinessProject p=projectMapper.selectProjectById(projectId);requireProject(p);
        if(!admin&&!manager(p,actor)&&!BusinessProjectReadAccess.isParentOwner(p,actor,projectMapper)
            &&mapper.selectMembers(projectId).stream().noneMatch(m->actor.equals(id(m.get("userId")))))throw new ServiceException("无权查看项目计划");
        List<Map<String,Object>> changes=mapper.selectPlanChanges(projectId);
        for(Map<String,Object> change:changes)change.put("canReview",canReview(p,change,actor));
        List<Map<String,Object>> baselines=mapper.selectBaselines(projectId);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("baselines",baselines);result.put("changes",changes);result.put("forecast",mapper.selectForecast(projectId));
        result.put("currentPlan",currentPlan(p,baselines));
        result.put("canRequestChange",actor.equals(p.getMainOwnerUserId())&&mutable(p));result.put("canForecast",actor.equals(p.getMainOwnerUserId())&&mutable(p));result.put("version",p.getVersion());result.put("baselineVersion",p.getBaselineVersion());return result;
    }

    /** Preview uses the same normalization and calculation as approval, without writing a new version. */
    public Map<String,Object> preview(Long projectId,Map<String,Object> body,Long actor)
    {
        BusinessProject p=projectMapper.selectProjectById(projectId);requireProject(p);requireMutable(p);
        if(!actor.equals(p.getMainOwnerUserId()))throw new ServiceException("只有项目负责人可以测算计划变更");
        return normalize(p,body);
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
        Map<String,Object> row=new LinkedHashMap<String,Object>();
        Map<String,Object> previous=currentPlan(p,mapper.selectBaselines(p.getProjectId()));
        String projectName=text(input.containsKey("projectName")?input.get("projectName"):p.getProjectName()),objective=text(input.get("objective")),criteria=text(input.get("acceptanceCriteria"));
        String applicationReason=text(input.containsKey("applicationReason")?input.get("applicationReason"):p.getRemark());
        String priority=text(input.containsKey("priority")?input.get("priority"):p.getPriority());if(priority==null)priority="MEDIUM";
        String revenueModel=text(input.containsKey("revenueModel")?input.get("revenueModel"):previous.get("revenueModel"));
        if(projectName==null||projectName.isEmpty()||projectName.length()>160)throw new ServiceException("项目名称不能为空或超过160个字符");
        if(objective==null||objective.isEmpty()||objective.length()>1000||criteria==null||criteria.isEmpty()||criteria.length()>2000)throw new ServiceException("范围目标和验收标准不能为空或超出长度");
        if(applicationReason!=null&&applicationReason.length()>2000)throw new ServiceException("立项说明不能超过2000个字符");
        if(revenueModel!=null&&revenueModel.length()>1000)throw new ServiceException("收入模式不能超过1000个字符");
        if(!Arrays.asList("LOW","MEDIUM","HIGH","URGENT").contains(priority))throw new ServiceException("项目优先级不正确");
        Date from=DateUtils.parseDate(input.get("planStartDate")),to=DateUtils.parseDate(input.get("planEndDate"));
        String endText=text(input.get("planEndDate"));
        if(from==null||endText!=null&&!endText.isEmpty()&&to==null||to!=null&&to.before(from))throw new ServiceException("请填写有效的计划起止日期；不限期项目可以不设置结束日期");
        List<Map<String,Object>> revenueLines=planLines(input.containsKey("revenueLines")?input.get("revenueLines"):previous.get("revenueLines"),true,from,to);
        List<Map<String,Object>> expenseLines=planLines(input.containsKey("expenseLines")?input.get("expenseLines"):previous.get("expenseLines"),false,from,to);
        row.put("projectName",projectName);row.put("objective",objective);row.put("applicationReason",applicationReason);row.put("priority",priority);
        row.put("acceptanceCriteria",criteria);row.put("revenueModel",revenueModel);row.put("revenueLines",revenueLines);row.put("expenseLines",expenseLines);
        row.put("planStartDate",DateUtils.parseDateToStr("yyyy-MM-dd",from));row.put("planEndDate",to==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",to));row.put("budgetLimit",money(input.get("budgetLimit")));
        if(p.getBudget()!=null)
        {
            BusinessProjectProposal estimate=new BusinessProjectProposal();
            estimate.setPlanStartDate(from);estimate.setPlanEndDate(to);estimate.setTemplateVersion(p.getTemplateVersion());estimate.setCompanyDeptId(p.getCompanyDeptId());estimate.setBaseCurrency(p.getBaseCurrency());
            Map<String,Object> requested=input.get("budget") instanceof Map?new LinkedHashMap<String,Object>((Map<String,Object>)input.get("budget")):new LinkedHashMap<String,Object>(p.getBudget());
            if(to==null&&"PROJECT".equals(requested.get("cycle")))requested.put("cycle","MONTH");estimate.setBudget(requested);
            estimate.setBudgetMode(text(requested.get("mode")));estimate.setBudgetScope(text(requested.get("scope")));
            estimate.setDailyBudgetLimit(money(requested.get("dailyLimit")));estimate.setStartupBudgetLimit(money(requested.get("startupLimit")));estimate.setBudgetReason(text(requested.get("reason")));
            estimate.setRevenueLines(revenueLines);estimate.setExpenseLines(expenseLines);estimate.setTargetLines(Collections.<Map<String,Object>>emptyList());estimate.setGoalMode("NO_TOTAL");
            List<Map<String,Object>> staff=new ArrayList<Map<String,Object>>();
            for(Map<String,Object> assignment:mapper.selectAssignments(p.getProjectId()))if("ACTIVE".equals(assignment.get("status")))
            {Map<String,Object> person=new LinkedHashMap<String,Object>(assignment);person.put("planStartDate",person.get("effectiveFrom"));person.put("planEndDate",person.get("effectiveTo"));person.put("participationMode",person.getOrDefault("participationMode",person.get("effectiveTo")==null?"UNLIMITED":"CUSTOM"));staff.add(person);}
            if(BusinessMemberDayCostService.enabled(p)) {
                Set<Long> activeMembers=new HashSet<>();
                for(Map<String,Object> member:mapper.selectMembers(p.getProjectId()))
                    if("0".equals(String.valueOf(member.get("status")))&&!"OBSERVER".equals(member.get("memberRole")))activeMembers.add(id(member.get("userId")));
                staff.removeIf(person->!activeMembers.contains(id(person.get("userId"))));
                Set<Long> planned=new HashSet<>();for(Map<String,Object> person:staff)planned.add(id(person.get("userId")));
                for(Long uid:activeMembers)if(!planned.contains(uid)) {Map<String,Object> person=new LinkedHashMap<>();person.put("userId",uid);person.put("participationMode","FOLLOW_PROJECT");person.put("calendarId",1L);staff.add(person);}
            }
            estimate.setStaffingLines(staff);
            Map<String,Object> budget=budgets.estimate(estimate);
            if(!"READY".equals(budget.get("status")))throw new ServiceException("预算尚未计算完整："+budget.get("issues"));
            row.put("budget",budget);row.put("budgetLimit","TOTAL".equals(budget.getOrDefault("mode","TOTAL"))?budget.get("totalAmount"):null);
            row.put("budgetMode",budget.getOrDefault("mode","TOTAL"));row.put("budgetScope",budget.getOrDefault("scope","FULL_COST"));
            row.put("dailyBudgetLimit",budget.get("dailyLimit"));row.put("startupBudgetLimit",budget.get("startupLimit"));row.put("budgetReason",budget.get("reason"));
            try{Map<String,Object> snapshot=json.readValue(p.getTemplateSnapshotJson(),new TypeReference<Map<String,Object>>(){});snapshot.put("budget",budget);row.put("templateSnapshotJson",write(snapshot));}catch(Exception ex){throw new ServiceException("预算快照无法保存");}
        }
        return row;
    }

    private List<Map<String,Object>> planLines(Object value,boolean revenue,Date from,Date to)
    {
        if(value==null)return Collections.emptyList();
        if(!(value instanceof List))throw new ServiceException((revenue?"收入":"支出")+"计划格式不正确");
        List<?> source=(List<?>)value;if(source.size()>100)throw new ServiceException("收入或支出计划每项最多100行");
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();int rowNo=0;
        for(Object item:source)
        {
            rowNo++;if(!(item instanceof Map))throw new ServiceException("第"+rowNo+"行计划格式不正确");
            Map<?,?> input=(Map<?,?>)item;Map<String,Object> line=new LinkedHashMap<String,Object>();
            String itemName=required(input.get("itemName"),160,(revenue?"收入":"支出")+"项目",rowNo);
            String occurrence=text(input.get("occurrenceType"));if(occurrence==null)occurrence="ONE_TIME";
            if(!Arrays.asList("ONE_TIME","DAILY","WEEKLY","MONTHLY").contains(occurrence))throw new ServiceException("第"+rowNo+"行发生方式不正确");
            Object rawDate=input.get(revenue?"expectedDate":"occurDate");Date date=DateUtils.parseDate(rawDate);
            if(date==null)throw new ServiceException("第"+rowNo+"行"+(revenue?"收入":"支出")+"月份不能为空");
            String dateIssue=revenue?com.ruoyi.business.support.BusinessProposalPlanDates.revenueIssue(rawDate,from,to,"收入测算",rowNo):com.ruoyi.business.support.BusinessProposalPlanDates.expenseIssue(rawDate,from,to,"支出计划",rowNo);
            if(dateIssue!=null)throw new ServiceException(dateIssue);
            line.put("itemName",itemName);line.put("occurrenceType",occurrence);
            if(revenue)
            {
                String scenario=text(input.get("scenario"));if(scenario==null)scenario="BASE";
                if(!Arrays.asList("CONSERVATIVE","BASE","OPTIMISTIC").contains(scenario))throw new ServiceException("第"+rowNo+"行收入场景不正确");
                line.put("scenario",scenario);line.put("revenueType",required(input.get("revenueType"),32,"收入方式",rowNo));
                line.put("expectedAmount",lineMoney(input.get("expectedAmount"),"预计收入",rowNo));line.put("expectedDate",DateUtils.parseDateToStr("yyyy-MM-dd",date));
                line.put("assumptionText",optional(input.get("assumptionText"),500,"收入依据",rowNo));
            }
            else
            {
                line.put("expenseCategory",required(input.get("expenseCategory"),32,"支出类别",rowNo));line.put("purpose",required(input.get("purpose"),500,"具体用途",rowNo));
                line.put("counterparty",optional(input.get("counterparty"),160,"收款方",rowNo));line.put("amount",lineMoney(input.get("amount"),"计划支出",rowNo));line.put("occurDate",DateUtils.parseDateToStr("yyyy-MM-dd",date));
                line.put("expenseType","ONE_TIME".equals(occurrence)?"ONE_TIME":"RECURRING");line.put("hasQuotation","1".equals(String.valueOf(input.get("hasQuotation")))?"1":"0");
            }
            result.add(line);
        }
        return result;
    }
    private String required(Object value,int limit,String label,int row){String s=text(value);if(s==null||s.isEmpty()||s.length()>limit)throw new ServiceException("第"+row+"行"+label+"不能为空或超过"+limit+"个字符");return s;}
    private String optional(Object value,int limit,String label,int row){String s=text(value);if(s!=null&&s.length()>limit)throw new ServiceException("第"+row+"行"+label+"不能超过"+limit+"个字符");return s;}
    private BigDecimal lineMoney(Object value,String label,int row){BigDecimal amount=money(value);if(amount==null)throw new ServiceException("第"+row+"行"+label+"不能为空");return amount;}

    private Map<String,Object> currentPlan(BusinessProject p,List<Map<String,Object>> baselines)
    {
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("projectName",p.getProjectName());result.put("objective",p.getObjective());result.put("applicationReason",p.getRemark());result.put("priority",p.getPriority());
        result.put("acceptanceCriteria",p.getAcceptanceCriteria());result.put("planStartDate",p.getPlanStartDate());result.put("planEndDate",p.getPlanEndDate());result.put("budget",p.getBudget());
        for(Map<String,Object> baseline:baselines)try
        {
            Map<String,Object> snapshot=json.readValue(text(baseline.get("snapshotJson")),new TypeReference<Map<String,Object>>(){});
            if(!result.containsKey("revenueModel")&&snapshot.get("revenueModel")!=null)result.put("revenueModel",snapshot.get("revenueModel"));
            if(!result.containsKey("revenueLines")&&snapshot.get("revenueLines") instanceof List)result.put("revenueLines",snapshot.get("revenueLines"));
            if(!result.containsKey("expenseLines")&&snapshot.get("expenseLines") instanceof List)result.put("expenseLines",snapshot.get("expenseLines"));
            if(result.containsKey("revenueLines")&&result.containsKey("expenseLines"))break;
        }catch(Exception ignored){}
        result.putIfAbsent("revenueLines",Collections.emptyList());result.putIfAbsent("expenseLines",Collections.emptyList());return result;
    }
    private boolean canReview(BusinessProject p,Map<String,Object> row,Long actor){return mutable(p)&&"SUBMITTED".equals(row.get("status"))&&companyAccess.project(p,actor)&&!actor.equals(id(row.get("requestUserId")));}
    private boolean mutable(BusinessProject p){return !BusinessProjectLifecycle.isAccountingClosed(p)&&!Arrays.asList("CLOSED","CANCELED","ACCEPTANCE").contains(p.getStatus());}
    private void requireMutable(BusinessProject p){if(!mutable(p))throw new ServiceException("当前项目状态不能调整计划");}
    private void requireProject(BusinessProject p){if(p==null||!BusinessMemberDayCostService.enabled(p)&&!"ACTUAL_WORK_V1".equals(p.getCostPolicyVersion()))throw new ServiceException("该项目未启用版本化计划");}
    private boolean manager(BusinessProject p,Long actor){return actor.equals(p.getMainOwnerUserId())||companyAccess.project(p,actor);}
    private Long sponsor(BusinessProject p){return p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();}
    private String reason(Object v){String s=text(v);if(s==null||s.isEmpty()||s.length()>2000)throw new ServiceException("请填写2000字符内的变更说明");return s;}
    private BigDecimal money(Object v){if(v==null||"".equals(v))return null;try{BigDecimal d=new BigDecimal(String.valueOf(v));if(d.signum()<0||d.scale()>2)throw new Exception();return d;}catch(Exception ex){throw new ServiceException("金额须为非负且最多两位小数");}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new ServiceException("计划快照无法保存");}}
    private String text(Object v){return v==null?null:String.valueOf(v).trim();}
    private Long id(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private int integer(Object v){try{return Integer.parseInt(String.valueOf(v));}catch(Exception ex){throw changed();}}
    private ServiceException changed(){return new ServiceException("项目或变更版本已变化，请刷新后重试");}
}
