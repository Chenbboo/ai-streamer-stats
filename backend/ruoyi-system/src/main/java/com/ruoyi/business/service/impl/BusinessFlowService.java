package com.ruoyi.business.service.impl;

import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;

/** Explicit business transitions; original accounting facts and priced days remain immutable. */
@Service
public class BusinessFlowService {
 @Autowired private BusinessFlowMapper flows;
 @Autowired private BusinessProjectMapper projects;
 @Autowired private BusinessAccountingMapper accounting;
 @Autowired private IBusinessProjectService projectService;
 @Autowired private ISysUserService users;
 @Autowired private OnlineUserPermissionService sessions;

 @Transactional
 public BusinessProject transition(Long id,String action,String reason,String pauseMode,Long actor,String name,boolean boss){
   BusinessProject p=projects.selectProjectByIdForUpdate(id);
   if(p==null)throw new ServiceException("项目不存在");
   if("PAUSE".equals(action)&&pauseMode!=null&&!Arrays.asList("KEEP","RELEASE").contains(pauseMode))throw new ServiceException("请选择暂停期间的人员安排");
   if("PAUSE".equals(action)&&"RELEASE".equals(pauseMode)&&!BusinessMemberDayCostService.enabled(p))throw new ServiceException("该历史项目请通过原人员投入流程调整成本");
   if("RESUME".equals(action))flows.resume(id,name);
   BusinessProject result=projectService.transition(id,action,reason,actor,name,boss);
   if("PAUSE".equals(action)&&"RELEASE".equals(pauseMode))flows.pause(id,name,reason);
   return result;
 }
 @Transactional
 public BusinessProject changeOwner(Long id,Long owner,String reason,boolean exitOld,Long actor,String name,boolean boss){
   BusinessProject p=projects.selectProjectByIdForUpdate(id);
   if(p==null)throw new ServiceException("项目不存在");
   Long old=p.getMainOwnerUserId();
   projectService.changeOwner(id,owner,reason,actor,name,boss);
   if(exitOld){
     // Use the existing reassignment services so work periods and audit events stay intact.
     for(Long taskId:flows.assignedTasks(id,old)){
       BusinessProjectTask task=projects.selectTaskById(taskId);task.setAssigneeUserId(owner);
       projectService.saveTask(task,actor,name,boss);
     }
     for(Long routineId:flows.assignedRoutines(id,old)){
       BusinessProjectRoutine routine=projects.selectRoutineById(routineId);routine.setAssigneeUserId(owner);
       projectService.saveRoutine(routine,actor,name,boss);
     }
     projectService.removeMember(id,old,true,actor,name,boss);
   }
   return projectService.getProject(id,actor,SecurityUtils.isAdmin(actor),boss);
 }
 private void staffScope(Long id){
   users.checkUserDataScope(id);
   SysUser u=users.selectUserById(id);
   if(u==null||"2".equals(u.getDelFlag()))throw new ServiceException("人员不存在");
   if(SecurityUtils.isAdmin(id)||projects.countUserRoleByKey(id,"company_owner")>0)throw new ServiceException("管理员和老板账号需通过专门交接办理");
 }
 public Map<String,Object> departureChecklist(Long id){
   staffScope(id);
   Map<String,Object> result=new LinkedHashMap<>();result.put("projects",flows.responsibilities(id));result.put("departure",flows.departure(id));return result;
 }
 private void checkHandover(Long userId){
   for(Map<String,Object> r:flows.responsibilities(userId)){
     if(userId.equals(number(r.get("ownerUserId"))))throw new ServiceException("请先更换项目“"+r.get("projectName")+"”的主负责人");
     if(number(r.get("taskCount"))>0||number(r.get("routineCount"))>0)throw new ServiceException("请先在项目“"+r.get("projectName")+"”指定未完任务及持续工作的接收人");
   }
 }
 @Transactional
 public Map<String,Object> requestDeparture(Long id,Map<String,Object> input,Long actor,String name){
   staffScope(id);flows.lockStaff(id);
   LocalDate date=date(input.get("effectiveDate"));
   if(date.isBefore(LocalDate.now())||date.isAfter(LocalDate.now().plusYears(1)))throw new ServiceException("离职日期需在今天至一年内，历史纠错请走核算调整");
   String reason=required(input.get("reason"),"离职说明");
   Map<String,Object> existing=flows.departure(id);
   if(existing!=null&&("SCHEDULED".equals(existing.get("status"))||"LEFT".equals(flows.employmentStatus(id))))throw new ServiceException("该人员已有离职办理记录，请先核对或取消待生效申请");
   checkHandover(id);
   Map<String,Object> row=new HashMap<>();row.put("userId",id);row.put("date",date.toString());row.put("reason",reason);row.put("handover","交接清单核对通过：无负责项目、无未完任务及持续工作");row.put("actor",name);row.put("actorId",actor);
   flows.insertDeparture(row);
   if(date.equals(LocalDate.now()))completeDeparture(number(row.get("id")));
   return flows.departure(id);
 }
 @Transactional
 public void cancelDeparture(Long userId){
   staffScope(userId);flows.lockStaff(userId);Map<String,Object> latest=flows.departure(userId);
   if(latest==null)throw new ServiceException("没有离职申请");
   Map<String,Object> r=flows.lockDeparture(number(latest.get("id")));
   if(!Arrays.asList("SCHEDULED","FAILED").contains(r.get("status")))throw new ServiceException("该离职申请已办理，不能取消");
   flows.departureStatus(number(r.get("id")),"CANCELED",null);
 }
 @Transactional
 public void completeDeparture(Long id){
   Map<String,Object> snapshot=flows.selectDeparture(id);
   if(snapshot==null)return;
   Long userId=number(snapshot.get("user_id"));flows.lockStaff(userId);
   Map<String,Object> r=flows.lockDeparture(id);
   if(r==null||!"SCHEDULED".equals(r.get("status"))||date(r.get("effective_date")).isAfter(LocalDate.now()))return;
   checkHandover(userId);
   // Lock projects in ascending order before closing membership boundaries.
   for(Map<String,Object> p:flows.responsibilities(userId))projects.selectProjectByIdForUpdate(number(p.get("projectId")));
   checkHandover(userId);
   Map<String,Object> row=new HashMap<>();row.put("userId",userId);row.put("date",date(r.get("effective_date")).toString());row.put("actor",r.get("requested_by"));
   flows.endMemberships(row);flows.endAllocations(row);flows.endWorkPeriods(row);
   flows.markDeparted(userId,String.valueOf(r.get("requested_by")));
   SysUser patch=new SysUser(userId);patch.setStatus("1");patch.setUpdateBy(String.valueOf(r.get("requested_by")));users.updateUserStatus(patch);
   sessions.forceReloginAfterCommit(userId);flows.departureStatus(id,"COMPLETED",null);
 }
 public List<Long> dueDepartures(){return flows.dueDepartures();}
 public void recordDepartureFailure(Long id,String message){flows.departureStatus(id,"FAILED",message==null?"办理失败，请核对交接清单":message.substring(0,Math.min(1900,message.length())));}

 private BusinessProject accessibleProject(Long id,Long actor,boolean admin){
   BusinessProject p=projects.selectProjectByIdForUpdate(id);
   if(p==null)throw new ServiceException("项目不存在");
   Long sponsor=p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();
   if(!admin&&!actor.equals(sponsor)&&!actor.equals(p.getMainOwnerUserId()))throw new ServiceException("无权查看或申请该项目调整");
   return p;
 }
 @Transactional
 public List<Map<String,Object>> adjustments(Long id,Long actor,boolean admin){accessibleProject(id,actor,admin);return flows.adjustments(id);}
 @Transactional
 public Map<String,Object> requestAdjustment(Long id,Map<String,Object> input,Long actor,String name,boolean admin){
   BusinessProject p=accessibleProject(id,actor,admin);
   if(!BusinessProjectLifecycle.isAccountingClosed(p))throw new ServiceException("尚未关账，请使用原收支调整流程");
   String request=required(input.get("requestId"),"提交编号");if(!request.matches("[A-Za-z0-9_-]{16,64}"))throw new ServiceException("提交编号无效");
   LocalDate bizDate=date(input.get("businessDate"));
   if(bizDate.isAfter(LocalDate.now())||p.getActualEndDate()==null||bizDate.isAfter(date(p.getActualEndDate())))throw new ServiceException("原业务日期不能晚于实际结束日期或今天");
   BigDecimal delta;try{delta=new BigDecimal(String.valueOf(input.get("profitDelta"))).setScale(2,java.math.RoundingMode.UNNECESSARY);}catch(Exception ex){throw new ServiceException("请输入两位小数以内的调整差额");}
   if(delta.signum()==0||delta.abs().compareTo(new BigDecimal("999999999999.99"))>0)throw new ServiceException("请输入非零且有效的调整差额");
   Long factId=input.get("originalFactId")==null?null:number(input.get("originalFactId"));
   if(factId!=null){BusinessOperatingFact fact=accounting.selectFactById(factId);if(fact==null||!id.equals(fact.getProjectId()))throw new ServiceException("原流水不属于该项目");}
   Map<String,Object> row=new HashMap<>();row.put("projectId",id);row.put("factId",factId);row.put("date",bizDate.toString());row.put("delta",delta);row.put("currency",p.getBaseCurrency());row.put("reason",required(input.get("reason"),"调整原因及依据"));row.put("requestId",request);row.put("actorId",actor);row.put("actor",name);
   Map<String,Object> replay=flows.replayAdjustment(row);
   if(replay!=null){
     if(!bizDate.equals(date(replay.get("business_date")))
         ||delta.compareTo(new BigDecimal(String.valueOf(replay.get("profit_delta"))))!=0
         ||!Objects.equals(row.get("reason"),replay.get("reason"))
         ||!Objects.equals(factId,replay.get("original_fact_id")==null?null:number(replay.get("original_fact_id"))))
       throw new ServiceException("本次调整已提交，内容发生变化，请刷新后重新申请");
     return replay;
   }
   flows.insertAdjustment(row);return flows.lockAdjustment(number(row.get("id")));
 }
 @Transactional
 public void reviewAdjustment(Long id,Map<String,Object> input,Long actor,String name){
   Map<String,Object> snapshot=flows.selectAdjustment(id);if(snapshot==null)throw new ServiceException("调整单不存在");
   BusinessProject p=accessibleProject(number(snapshot.get("project_id")),actor,false);
   Map<String,Object> a=flows.lockAdjustment(id);if(a==null)throw new ServiceException("调整单不存在");
   Long sponsor=p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();
   if(!actor.equals(sponsor))throw new ServiceException("只能由项目归属老板审核");
   String decision=String.valueOf(input.get("decision"));if(!Arrays.asList("APPROVED","REJECTED").contains(decision))throw new ServiceException("审核决定无效");
   if(!"PENDING".equals(a.get("status")))throw new ServiceException("该调整单已处理，请刷新");
   Map<String,Object> row=new HashMap<>();row.put("id",id);row.put("status",decision);row.put("actor",name);row.put("actorId",actor);row.put("comment",required(input.get("comment"),"审核意见"));
   if(flows.reviewAdjustment(row)!=1)throw new ServiceException("该调整单已被处理");
 }
 private static Long number(Object v){if(v==null)return 0L;try{return Long.valueOf(String.valueOf(v));}catch(NumberFormatException ex){throw new ServiceException("记录编号格式无效");}}
 private static LocalDate date(Object v){try{if(v instanceof java.util.Date)return ((java.util.Date)v).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();return LocalDate.parse(String.valueOf(v).substring(0,10));}catch(Exception ex){if(v instanceof java.sql.Date)return ((java.sql.Date)v).toLocalDate();throw new ServiceException("日期格式无效");}}
 private static String required(Object v,String label){String s=v==null?"":String.valueOf(v).trim();if(s.isEmpty()||s.length()>2000)throw new ServiceException(label+"必填且不能超过2000字");return s;}
}
