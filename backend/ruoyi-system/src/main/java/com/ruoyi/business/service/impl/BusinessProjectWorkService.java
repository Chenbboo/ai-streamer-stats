package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Actual work is an independently approved business fact; attendance and plans never fill missing actuals. */
@Service
public class BusinessProjectWorkService
{
    @Autowired private BusinessProjectWorkMapper mapper;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private ObjectMapper json;

    public List<Map<String,Object>> options(Long actorId,boolean admin)
    {
        Map<String,Object> query=new HashMap<String,Object>();query.put("userId",actorId);query.put("viewAll",admin);query.put("boss",false);
        List<BusinessProject> projects=new ArrayList<BusinessProject>(projectMapper.selectProjectList(query));
        if(!admin){query.put("boss",true);projects.addAll(projectMapper.selectProjectList(query));}
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();Set<Long> seen=new HashSet<Long>();
        for(BusinessProject project:projects)
        {
            if(!"ACTUAL_WORK_V1".equals(project.getCostPolicyVersion())||!seen.add(project.getProjectId()))continue;
            Map<String,Object> option=new LinkedHashMap<String,Object>();option.put("projectId",project.getProjectId());option.put("projectName",project.getProjectName());option.put("costPolicyVersion",project.getCostPolicyVersion());result.add(option);
        }
        return result;
    }

    public Map<String,Object> workspace(Long projectId, Map<String,Object> query, Long actorId, boolean admin)
    {
        BusinessProject p=projectMapper.selectProjectById(projectId);
        requireProject(p);
        List<Map<String,Object>> members=mapper.selectMembers(projectId);
        boolean manager=isManager(p,actorId), member=hasMember(members,actorId);
        if(!admin&&!manager&&!member)throw new ServiceException("无权查看该项目工作记录");
        Map<String,Object> q=new HashMap<String,Object>();
        if(query!=null){q.put("dateFrom",query.get("dateFrom"));q.put("dateTo",query.get("dateTo"));}
        q.put("projectId",projectId);if(!admin&&!manager)q.put("userId",actorId);
        List<Map<String,Object>> entries=mapper.selectEntries(q);
        for(Map<String,Object> row:entries)decorate(p,row,actorId);
        List<Map<String,Object>> assignments=mapper.selectAssignments(projectId);
        for(Map<String,Object> row:assignments)row.put("canRetire",manager&&"ACTIVE".equals(row.get("status"))&&!BusinessProjectLifecycle.isTerminal(p.getStatus())&&!BusinessProjectLifecycle.isAccountingClosed(p));
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        // Only non-sensitive project fields are returned; internal rates belong to accounting.
        Map<String,Object> project=new LinkedHashMap<String,Object>();
        project.put("projectId",projectId);project.put("projectName",p.getProjectName());project.put("status",p.getStatus());project.put("accountingState",p.getAccountingState());
        project.put("costPolicyVersion",p.getCostPolicyVersion());project.put("templateVersion",p.getTemplateVersion());project.put("actualEndDate",p.getActualEndDate()==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",p.getActualEndDate()));
        project.put("mainOwnerUserId",p.getMainOwnerUserId());project.put("sponsorOwnerUserId",sponsor(p));
        project.put("planStartDate",p.getPlanStartDate()==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",p.getPlanStartDate()));project.put("planEndDate",p.getPlanEndDate()==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",p.getPlanEndDate()));project.put("actualStartDate",p.getActualStartDate()==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",p.getActualStartDate()));
        result.put("project",project);result.put("entries",entries);result.put("assignments",assignments);result.put("members",members);
        result.put("calendars",mapper.selectCalendars());result.put("unitPolicies",mapper.selectUnitPolicies());
        result.put("canManage",manager&&!BusinessProjectLifecycle.isTerminal(p.getStatus())&&!BusinessProjectLifecycle.isAccountingClosed(p));
        result.put("canReport",(manager||member)&&!BusinessProjectLifecycle.isAccountingClosed(p));
        result.put("pendingWorkCount",mapper.countPendingWork(projectId));result.put("pendingCostCount",mapper.countPendingCosts(projectId));
        result.put("missingActualMeaning","UNKNOWN");return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveAssignment(Long projectId, Map<String,Object> body, Long actorId, String userName)
    {
        BusinessProject p=lockProject(projectId);requireOpen(p);
        if(!isManager(p,actorId))throw new ServiceException("只有项目负责人或归属老板可以安排资源");
        if(BusinessProjectLifecycle.isTerminal(p.getStatus()))throw new ServiceException("交付结束后不能新增资源计划");
        Map<String,Object> row=copy(body);Long userId=id(row.get("userId"));
        LocalDate from=date(row.get("effectiveFrom")),to=date(row.get("effectiveTo"));
        if(to.isBefore(from)||Duration.between(from.atStartOfDay(),to.atStartOfDay()).toDays()>730)throw new ServiceException("参与日期范围不正确或超过两年");
        requireMembership(p,userId,from);requireMembership(p,userId,to);
        if(p.getPlanStartDate()!=null&&from.isBefore(date(p.getPlanStartDate()))||p.getPlanEndDate()!=null&&to.isAfter(date(p.getPlanEndDate())))throw new ServiceException("人员参与日期必须在项目计划窗口内");
        Map<String,Object> calendar=calendar(row.get("calendarId"),from,to),unit=unit(row.get("unitPolicyId"),from,to);
        String inputUnit=code(row.get("inputUnit"));BigDecimal quantity=quantity(row.get("inputQuantity"),false);
        List<LocalDate> days=new ArrayList<LocalDate>();List<Integer> capacities=new ArrayList<Integer>();int totalCapacity=0;
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){int c=capacity(calendar,day);if(c>0){days.add(day);capacities.add(c);totalCapacity+=c;}}
        if(totalCapacity==0)throw new ServiceException("参与日期没有可用工作日，请选择适用日历");
        int total;
        if("PERCENTAGE".equals(inputUnit)){if(quantity.compareTo(new BigDecimal("100"))>0)throw new ServiceException("单项计划比例不能超过100%");total=exactMinutes(quantity.multiply(BigDecimal.valueOf(totalCapacity)).divide(new BigDecimal("100")));}
        else total=minutes(quantity,inputUnit,integer(unit.get("minutesPerDay")));
        row.put("projectId",projectId);row.put("userId",userId);row.put("effectiveFrom",from.toString());row.put("effectiveTo",to.toString());row.put("plannedMinutes",total);
        row.put("inputUnit",inputUnit);row.put("inputQuantity",quantity);row.put("calendarId",calendar.get("calendarId"));row.put("unitPolicyId",unit.get("unitPolicyId"));
        row.put("calendarSnapshotJson",write(calendar));row.put("unitSnapshotJson",write(unit));row.put("userName",userName);
        if(mapper.countOverlappingAssignments(row)>0)throw new ServiceException("该人员在本项目已有重叠参与计划，请先停用或调整原安排");
        String reason=optionalReason(row.get("reason"));row.put("reason",reason);
        int[] daily=new int[days.size()];int assigned=0;
        for(int i=0;i<daily.length;i++){daily[i]=(int)((long)total*capacities.get(i)/totalCapacity);assigned+=daily[i];}
        for(int i=0;assigned<total;i=(i+1)%daily.length){daily[i]++;assigned++;}
        for(int i=0;i<days.size();i++){lockPerson(userId,days.get(i));if(mapper.sumPlannedMinutes(userId,days.get(i).toString())+daily[i]>capacities.get(i)&&blank(reason))throw new ServiceException("存在跨项目超配，请填写资源安排例外原因");}
        mapper.insertAssignment(row);
        for(int i=0;i<days.size();i++){Map<String,Object> day=copy(row);day.put("bizDate",days.get(i).toString());day.put("timeZone",calendar.get("timeZone"));day.put("plannedMinutes",daily[i]);day.put("capacityMinutes",capacities.get(i));mapper.insertAllocationDay(day);}
        return row;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveEntry(Long projectId,Map<String,Object> body,Long actorId,String userName)
    {
        BusinessProject p=lockProject(projectId);requireOpen(p);
        if(body.get("entryId")!=null)return editDraft(p,body,actorId,userName);
        Map<String,Object> row=copy(body);Long userId=row.get("userId")==null?actorId:id(row.get("userId"));
        requireReporter(p,userId,actorId);LocalDate day=date(row.get("bizDate"));requireWorkDate(p,day,row.get("reason"));requireMembership(p,userId,day);
        String source=text(row.get("sourceKey"));if(blank(source)||source.length()>100)throw new ServiceException("请提供不超过100字符的唯一提交标识");
        Map<String,Object> existing=mapper.selectEntryBySource(projectId,source);
        if(existing!=null){if(!actorId.equals(id(existing.get("createUserId")))||!userId.equals(id(existing.get("userId"))))throw new ServiceException("提交标识已被其他记录使用");return decorated(p,existing,actorId);}
        if(mapper.countPlannedWork(projectId,userId,day.toString())==0&&blank(row.get("reason")))throw new ServiceException("未计划工作请填写补录原因");
        row.put("projectId",projectId);row.put("userId",userId);row.put("bizDate",day.toString());row.put("revisionNo",1);row.put("logicalEntryId",null);row.put("parentEntryId",null);
        normalizeEntry(row,false);row.put("actorId",actorId);row.put("userName",userName);mapper.insertEntry(row);
        return decorated(p,mapper.selectEntry(id(row.get("entryId"))),actorId);
    }

    private Map<String,Object> editDraft(BusinessProject p,Map<String,Object> body,Long actorId,String userName)
    {
        Map<String,Object> row=mapper.selectEntryForUpdate(id(body.get("entryId")));
        if(row==null||!p.getProjectId().equals(id(row.get("projectId"))))throw new ServiceException("工作记录不属于当前项目");
        requireVersion(row,body);
        if(!actorId.equals(id(row.get("createUserId")))||!Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(row.get("status")))throw new ServiceException("只能修改本人未提交工作记录");
        audit(row,"EDIT",actorId,userName,body.get("reason"));
        row.put("inputUnit",body.get("inputUnit"));row.put("inputQuantity",body.get("inputQuantity"));row.put("unitPolicyId",body.get("unitPolicyId"));
        if(body.get("calendarId")!=null)row.put("calendarId",body.get("calendarId"));if(body.get("activity")!=null)row.put("activity",body.get("activity"));row.put("reason",body.get("reason"));
        requireWorkDate(p,date(row.get("bizDate")),row.get("reason"));normalizeEntry(row,row.get("parentEntryId")!=null);row.put("userName",userName);
        if(mapper.updateDraftEntry(row)!=1)throw changed();return decorated(p,mapper.selectEntry(id(row.get("entryId"))),actorId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void retireAssignment(Long assignmentId,Map<String,Object> body,Long actorId,String userName)
    {
        Map<String,Object> row=mapper.selectAssignment(assignmentId);if(row==null)throw new ServiceException("资源安排不存在");
        BusinessProject p=lockProject(id(row.get("projectId")));requireOpen(p);if(!isManager(p,actorId))throw new ServiceException("只有项目负责人或归属老板可以停用资源安排");
        if(BusinessProjectLifecycle.isTerminal(p.getStatus()))throw new ServiceException("已关闭项目的资源安排保留只读");
        row=mapper.selectAssignment(assignmentId);requireVersion(row,body);row.put("reason",requiredReason(body.get("reason")));row.put("userName",userName);
        if(mapper.retireAssignment(row)!=1)throw changed();
    }

    public List<Map<String,Object>> history(Long entryId,Long actorId,boolean admin)
    {
        Map<String,Object> row=requireEntry(entryId);BusinessProject p=projectMapper.selectProjectById(id(row.get("projectId")));requireProject(p);
        if(!admin&&!isManager(p,actorId)&&!actorId.equals(id(row.get("userId"))))throw new ServiceException("无权查看工作记录历史");return mapper.selectAudit(entryId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> correct(Long entryId,Map<String,Object> body,Long actorId,String userName)
    {
        Map<String,Object> before=requireEntry(entryId);BusinessProject p=lockProject(id(before.get("projectId")));requireOpen(p);
        before=mapper.selectEntryForUpdate(entryId);requireVersion(before,body);requireReporter(p,id(before.get("userId")),actorId);
        if(!"CONFIRMED".equals(before.get("status"))||!"1".equals(before.get("isCurrent")))throw new ServiceException("只能更正当前有效的确认记录");
        String reason=requiredReason(body.get("reason"));LocalDate day=date(before.get("bizDate"));requireWorkDate(p,day,reason);
        Long logical=id(before.get("logicalEntryId"));if(logical==null)logical=entryId;
        if(mapper.countOpenCorrections(logical)>0)throw new ServiceException("该工作记录已有未完成的更正，请先处理");
        Map<String,Object> row=copy(before);row.put("entryId",null);row.put("parentEntryId",entryId);row.put("logicalEntryId",logical);row.put("revisionNo",integer(before.get("revisionNo"))+1);
        row.put("inputUnit",body.get("inputUnit"));row.put("inputQuantity",body.get("inputQuantity"));row.put("unitPolicyId",body.get("unitPolicyId"));
        row.put("sourceKey","CORRECTION-"+entryId+"-"+UUID.randomUUID().toString());row.put("reason",reason);row.put("bizDate",day.toString());
        normalizeEntry(row,true);row.put("actorId",actorId);row.put("userName",userName);mapper.insertEntry(row);
        return decorated(p,mapper.selectEntry(id(row.get("entryId"))),actorId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> transition(Long entryId,String action,Map<String,Object> body,Long actorId,String userName)
    {
        Map<String,Object> row=requireEntry(entryId);BusinessProject p=lockProject(id(row.get("projectId")));requireOpen(p);
        row=mapper.selectEntryForUpdate(entryId);requireVersion(row,body);String from=code(row.get("status")),to;
        boolean creator=actorId.equals(id(row.get("createUserId"))),reviewer=canConfirm(p,row,actorId);
        String reason=optionalReason(body.get("reason"));
        if("submit".equals(action)){
            if(!creator||!Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(from))throw new ServiceException("只有填报人可以提交草稿或退回记录");
            requireReporter(p,id(row.get("userId")),actorId);requireWorkDate(p,date(row.get("bizDate")),row.get("reason"));requireMembership(p,id(row.get("userId")),date(row.get("bizDate")));checkDailyTotal(row);to="SUBMITTED";
        }else if("withdraw".equals(action)){
            if(!creator||!"SUBMITTED".equals(from))throw new ServiceException("只有填报人可以撤回待确认记录");to="WITHDRAWN";reason=requiredReason(body.get("reason"));
        }else if("discard".equals(action)){
            if(!creator||!Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(from))throw new ServiceException("只能作废本人未提交记录");to="VOID";reason=requiredReason(body.get("reason"));
        }else if("confirm".equals(action)||"return".equals(action)){
            if(!reviewer||!"SUBMITTED".equals(from))throw new ServiceException("无权确认该记录；本人或本人代填的工作须由另一业务责任人确认");
            if("return".equals(action)){to="RETURNED";reason=requiredReason(body.get("reason"));}
            else {checkDailyTotal(row);to="CONFIRMED";if(row.get("parentEntryId")!=null){Map<String,Object> parent=mapper.selectEntryForUpdate(id(row.get("parentEntryId")));if(parent==null||!"CONFIRMED".equals(parent.get("status"))||!"1".equals(parent.get("isCurrent")))throw new ServiceException("原确认版本已变化，请重新申请更正");if(mapper.supersedeEntry(id(parent.get("entryId")),integer(parent.get("version")),userName)!=1)throw changed();}}
        }else throw new ServiceException("工作记录操作不正确");
        Map<String,Object> change=copy(row);change.put("fromStatus",from);change.put("toStatus",to);change.put("isCurrent","CONFIRMED".equals(to)?"1":"0");change.put("actorId",actorId);change.put("userName",userName);change.put("reason",reason);
        audit(row,action.toUpperCase(Locale.ROOT),actorId,userName,reason);
        if(mapper.transitionEntry(change)!=1)throw changed();
        if("CONFIRMED".equals(to))mapper.insertEvent(change); // Fact and durable pricing event commit together.
        return decorated(p,mapper.selectEntry(entryId),actorId);
    }

    public Map<String,Object> addCalendar(Map<String,Object> body,String userName,boolean administrator)
    {
        if(!administrator)throw new ServiceException("只有平台配置管理员可以发布日历版本");
        Map<String,Object> row=copy(body);String name=text(row.get("calendarName"));if(blank(name)||name.length()>100)throw new ServiceException("请填写日历名称");
        try{ZoneId.of(text(row.get("timeZone")));}catch(Exception ex){throw new ServiceException("请选择有效时区");}
        int minutes=integer(row.get("dailyMinutes"));if(minutes<0||minutes>1440)throw new ServiceException("每日容量必须为0至1440整数分钟");
        String weekdays=text(row.get("workingWeekdays"));if(weekdays==null||!weekdays.matches("[1-7](,[1-7]){0,6}"))throw new ServiceException("工作周请使用1至7的逗号分隔值");
        validity(row);List<Map<String,Object>> exceptions=new ArrayList<Map<String,Object>>();Set<String> dates=new HashSet<String>();
        Object supplied=row.get("exceptions");if(supplied instanceof List)for(Object value:(List<?>)supplied){if(!(value instanceof Map))throw new ServiceException("日历例外格式不正确");Map<String,Object> e=copy((Map<String,Object>)value);LocalDate d=date(e.get("bizDate"));if(!dates.add(d.toString()))throw new ServiceException("同一天不能重复设置日历例外");int m=integer(e.get("minutes"));if(m<0||m>dayMinutes(d,text(row.get("timeZone"))))throw new ServiceException("日历例外容量超出当地日时长");e.put("bizDate",d.toString());e.put("minutes",m);exceptions.add(e);}
        row.put("exceptionsJson",write(exceptions));row.put("userName",userName);mapper.insertCalendar(row);return mapper.selectCalendar(id(row.get("calendarId")));
    }

    public Map<String,Object> addUnitPolicy(Map<String,Object> body,String userName,boolean administrator)
    {
        if(!administrator)throw new ServiceException("只有平台配置管理员可以发布工作量单位版本");
        Map<String,Object> row=copy(body);String name=text(row.get("policyName"));if(blank(name)||name.length()>100)throw new ServiceException("请填写单位政策名称");
        int minutes=integer(row.get("minutesPerDay"));if(minutes<1||minutes>1440)throw new ServiceException("一人天必须为1至1440整数分钟");validity(row);row.put("userName",userName);mapper.insertUnitPolicy(row);return mapper.selectUnitPolicy(id(row.get("unitPolicyId")));
    }

    private void normalizeEntry(Map<String,Object> row,boolean correction)
    {
        LocalDate day=date(row.get("bizDate"));Map<String,Object> calendar=calendar(row.get("calendarId"),day,day),unit=unit(row.get("unitPolicyId"),day,day);
        BigDecimal quantity=quantity(row.get("inputQuantity"),correction);String inputUnit=code(row.get("inputUnit"));int minutes=minutes(quantity,inputUnit,integer(unit.get("minutesPerDay")));
        if(day.isAfter(LocalDate.now(ZoneId.of(text(calendar.get("timeZone"))))))throw new ServiceException("不能填报未来工作日期");
        if(minutes>dayMinutes(day,text(calendar.get("timeZone")) ))throw new ServiceException("工作量超过当地自然日实际时长");
        String activity=text(row.get("activity"));if(blank(activity)||activity.length()>1000)throw new ServiceException("请填写1000字符内的实际工作活动");
        row.put("workMinutes",minutes);row.put("inputQuantity",quantity);row.put("inputUnit",inputUnit);row.put("calendarId",calendar.get("calendarId"));row.put("unitPolicyId",unit.get("unitPolicyId"));
        row.put("calendarSnapshotJson",write(calendar));row.put("unitSnapshotJson",write(unit));row.put("minutesPerDay",unit.get("minutesPerDay"));row.put("timeZone",calendar.get("timeZone"));row.put("capacityMinutes",capacity(calendar,day));
        row.put("reason",optionalReason(row.get("reason")));
    }

    private void checkDailyTotal(Map<String,Object> row)
    {
        Long user=id(row.get("userId")),logical=row.get("logicalEntryId")==null?id(row.get("entryId")):id(row.get("logicalEntryId"));LocalDate day=date(row.get("bizDate"));lockPerson(user,day);
        int sum=mapper.sumReservedMinutes(user,day.toString(),logical)+integer(row.get("workMinutes"));
        if(sum>dayMinutes(day,text(row.get("timeZone"))))throw new ServiceException("跨项目工作总量超过当地自然日实际时长");
        if(sum>integer(row.get("capacityMinutes"))&&blank(row.get("reason")))throw new ServiceException("跨项目工作量超过日历容量，请填写例外说明");
    }
    private void audit(Map<String,Object> row,String action,Long actor,String userName,Object reason){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("entryId",row.get("entryId"));item.put("action",action);item.put("snapshotJson",write(row));item.put("actorId",actor);item.put("userName",userName);item.put("reason",optionalReason(reason));mapper.insertAudit(item);}
    private void lockPerson(Long user,LocalDate day){mapper.ensurePersonDayLock(user,day.toString());mapper.lockPersonDay(user,day.toString());}
    private void requireMembership(BusinessProject p,Long user,LocalDate day){if(user==null||mapper.countMembership(p.getProjectId(),user,day.toString())==0)throw new ServiceException("人员在工作日期没有有效项目参与授权");}
    private void requireReporter(BusinessProject p,Long person,Long actor){if(!actor.equals(person)&&!actor.equals(p.getMainOwnerUserId()))throw new ServiceException("仅可记录本人工作，项目负责人代填须保留代填人");}
    private void requireWorkDate(BusinessProject p,LocalDate day,Object reason){if(p.getActualStartDate()!=null&&day.isBefore(date(p.getActualStartDate())))throw new ServiceException("工作日期早于实际项目开始日");if(BusinessProjectLifecycle.isTerminal(p.getStatus())){if(p.getActualEndDate()==null||day.isAfter(date(p.getActualEndDate())))throw new ServiceException("只能补录交付结束日前已经发生的工作");requiredReason(reason);}}
    private boolean canConfirm(BusinessProject p,Map<String,Object> row,Long actor){if(actor==null||actor.equals(id(row.get("userId")))||actor.equals(id(row.get("createUserId"))))return false;return actor.equals(p.getMainOwnerUserId())||actor.equals(sponsor(p));}
    private void decorate(BusinessProject p,Map<String,Object> row,Long actor){boolean open=!BusinessProjectLifecycle.isAccountingClosed(p),creator=actor.equals(id(row.get("createUserId")));String s=code(row.get("status"));row.put("canEdit",open&&creator&&Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(s));row.put("canSubmit",open&&creator&&Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(s));row.put("canWithdraw",open&&creator&&"SUBMITTED".equals(s));row.put("canDiscard",open&&creator&&Arrays.asList("DRAFT","RETURNED","WITHDRAWN").contains(s));row.put("canConfirm",open&&"SUBMITTED".equals(s)&&canConfirm(p,row,actor));row.put("canReturn",row.get("canConfirm"));row.put("canCorrect",open&&"CONFIRMED".equals(s)&&"1".equals(row.get("isCurrent"))&&(actor.equals(id(row.get("userId")))||actor.equals(p.getMainOwnerUserId())));row.put("confirmingSelfBlocked",actor.equals(id(row.get("userId")))||actor.equals(id(row.get("createUserId"))));if(row.get("pricingStatus")==null&&"CONFIRMED".equals(s))row.put("pricingStatus","PENDING");}
    private Map<String,Object> decorated(BusinessProject p,Map<String,Object> row,Long actor){decorate(p,row,actor);return row;}
    private boolean isManager(BusinessProject p,Long actor){return actor!=null&&(actor.equals(p.getMainOwnerUserId())||actor.equals(sponsor(p)));}
    private Long sponsor(BusinessProject p){return p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();}
    private boolean hasMember(List<Map<String,Object>> rows,Long actor){for(Map<String,Object> row:rows)if(actor.equals(id(row.get("userId"))))return true;return false;}
    private BusinessProject lockProject(Long id){BusinessProject p=projectMapper.selectProjectByIdForUpdate(id);requireProject(p);return p;}
    private void requireProject(BusinessProject p){if(p==null)throw new ServiceException("项目不存在");if(!"ACTUAL_WORK_V1".equals(p.getCostPolicyVersion()))throw new ServiceException("旧项目继续使用原版投入记录");}
    private void requireOpen(BusinessProject p){BusinessProjectLifecycle.requireAccountingOpen(p);}
    private Map<String,Object> requireEntry(Long id){Map<String,Object> row=mapper.selectEntry(id);if(row==null)throw new ServiceException("工作记录不存在");return row;}
    private void requireVersion(Map<String,Object> row,Map<String,Object> body){if(body.get("version")==null||integer(row.get("version"))!=integer(body.get("version")))throw changed();}
    private ServiceException changed(){return new ServiceException("记录已变化，请刷新后重试");}
    private Map<String,Object> calendar(Object id,LocalDate from,LocalDate to){Map<String,Object> row=mapper.selectCalendar(id(id));if(row==null)throw new ServiceException("请选择日历版本");effective(row,from,to);return row;}
    private Map<String,Object> unit(Object id,LocalDate from,LocalDate to){Map<String,Object> row=mapper.selectUnitPolicy(id(id));if(row==null)throw new ServiceException("请选择人天换算政策版本");effective(row,from,to);return row;}
    private void effective(Map<String,Object> row,LocalDate from,LocalDate to){if(row.get("effectiveFrom")!=null&&from.isBefore(date(row.get("effectiveFrom")))||row.get("effectiveTo")!=null&&to.isAfter(date(row.get("effectiveTo"))))throw new ServiceException("所选版本不覆盖业务日期");}
    private void validity(Map<String,Object> row){LocalDate from=date(row.get("effectiveFrom"));LocalDate to=row.get("effectiveTo")==null||blank(row.get("effectiveTo"))?null:date(row.get("effectiveTo"));if(to!=null&&to.isBefore(from))throw new ServiceException("版本生效日期不正确");row.put("effectiveFrom",from.toString());row.put("effectiveTo",to==null?null:to.toString());}
    private int capacity(Map<String,Object> calendar,LocalDate day){Object raw=calendar.get("exceptionsJson");if(raw!=null)try{for(Map<String,Object> e:json.readValue(String.valueOf(raw),new TypeReference<List<Map<String,Object>>>(){}))if(day.toString().equals(text(e.get("bizDate"))))return integer(e.get("minutes"));}catch(Exception ex){throw new ServiceException("日历例外版本无效");}return Arrays.asList(text(calendar.get("workingWeekdays")).split(",")).contains(String.valueOf(day.getDayOfWeek().getValue()))?integer(calendar.get("dailyMinutes")):0;}
    public static int minutes(BigDecimal quantity,String unit,int minutesPerDay){if(!Arrays.asList("HOUR","DAY").contains(unit))throw new ServiceException("实际投入单位只能为小时或人天");return exactMinutes(quantity.multiply(BigDecimal.valueOf("HOUR".equals(unit)?60:minutesPerDay)));}
    private static int exactMinutes(BigDecimal value){try{return value.setScale(0,RoundingMode.UNNECESSARY).intValueExact();}catch(ArithmeticException ex){throw new ServiceException("工作量必须能精确换算为整数分钟，请修改输入");}}
    private static int dayMinutes(LocalDate day,String zone){ZoneId z=ZoneId.of(zone);return (int)Duration.between(day.atStartOfDay(z),day.plusDays(1).atStartOfDay(z)).toMinutes();}
    private BigDecimal quantity(Object value,boolean allowZero){try{BigDecimal q=new BigDecimal(text(value));if(q.signum()<0||!allowZero&&q.signum()==0)throw new ServiceException("工作量必须大于零，更正撤销可为零");return q;}catch(NumberFormatException ex){throw new ServiceException("工作量格式不正确");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new ServiceException("无法保存业务快照");}}
    private Map<String,Object> copy(Map<String,Object> row){return row==null?new LinkedHashMap<String,Object>():new LinkedHashMap<String,Object>(row);}
    private String requiredReason(Object value){String reason=optionalReason(value);if(blank(reason))throw new ServiceException("请填写办理原因");return reason;}
    private String optionalReason(Object value){String r=text(value);if(r!=null&&r.length()>2000)throw new ServiceException("说明不能超过2000字符");return r;}
    private static LocalDate date(Object value){try{if(value instanceof java.util.Date)return LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",(java.util.Date)value));return LocalDate.parse(String.valueOf(value).substring(0,10));}catch(Exception ex){throw new ServiceException("业务日期格式不正确");}}
    private static String text(Object value){return value==null?null:String.valueOf(value).trim();}
    private static boolean blank(Object value){return value==null||String.valueOf(value).trim().isEmpty();}
    private static String code(Object value){return value==null?"":text(value).toUpperCase(Locale.ROOT);}
    private static Long id(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(Exception ex){throw new ServiceException("标识格式不正确");}}
    private static int integer(Object value){try{return new BigDecimal(String.valueOf(value)).intValueExact();}catch(Exception ex){throw new ServiceException("请输入整数分钟或有效版本");}}
}
