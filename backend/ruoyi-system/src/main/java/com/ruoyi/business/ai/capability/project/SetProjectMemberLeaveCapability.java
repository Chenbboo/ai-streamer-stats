package com.ruoyi.business.ai.capability.project;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.attendance.BusinessFeishuService;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.common.exception.ServiceException;

@Component
public class SetProjectMemberLeaveCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    private final BusinessFeishuService attendance;
    @Autowired public SetProjectMemberLeaveCapability(IBusinessProjectService service,BusinessFeishuService attendance)
    { this.service = service; this.attendance=attendance; }
    @Override public String code() { return "project.member.leave.set"; }
    @Override public String description()
    { return "在尚未切换飞书的公司和日期范围内，提交或撤回历史本地项目请假申请。提交必须有原因并走既有审批。已切换范围在飞书办理；飞书结果不自动生成项目投入或成本，历史核算按原版本处理。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); Map<String, Object> operation = AiSchemas.property(s,
            "operation", "string", "MARK提交请假申请，CANCEL撤回待审批申请");
        operation.put("enum", Arrays.asList("MARK", "CANCEL"));
        AiSchemas.property(s, "projectId", "number", "项目ID");
        AiSchemas.property(s, "memberUserId", "number", "项目成员用户ID");
        AiSchemas.property(s, "memberName", "string", "确认卡展示的成员姓名");
        AiSchemas.property(s, "leaveDate", "string", "请假日期 YYYY-MM-DD");
        AiSchemas.property(s, "reason", "string", "登记请假时的原因");
        return AiSchemas.required(s, "operation", "projectId", "memberUserId", "leaveDate");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); BusinessProjectMember member=authorizedMember(invocation,input);
        return ("CANCEL".equals(AiCapabilityInputs.upper(input.get("operation"))) ? "撤回历史本地" : "提交本地")
            + "请假：“" + member.getUserNameSnapshot() + "”在 " + AiCapabilityInputs.text(input.get("leaveDate"))
            + "；提交后按既有流程审批，实际投入与成本依各自政策处理";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); authorizedMember(invocation,input); String operation = AiCapabilityInputs.upper(input.get("operation"));
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        Long memberId = AiCapabilityInputs.number(input.get("memberUserId"));
        if ("MARK".equals(operation)) service.markMemberLeave(projectId, memberId,
            AiCapabilityInputs.date(input.get("leaveDate")), AiCapabilityInputs.text(input.get("reason")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        else service.cancelMemberLeave(projectId, memberId, AiCapabilityInputs.date(input.get("leaveDate")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", projectId);
        result.put("memberUserId", memberId); result.put("leaveDate", input.get("leaveDate"));
        result.put("status", "MARK".equals(operation) ? "PENDING" : "CANCELED"); return result;
    }
    private BusinessProjectMember authorizedMember(AiCapabilityInvocation invocation,Map<String,Object> input)
    {
        Long projectId=AiCapabilityInputs.number(input.get("projectId")),memberId=AiCapabilityInputs.number(input.get("memberUserId"));
        if(projectId==null||memberId==null)throw new ServiceException("请先确认项目和成员标识");
        BusinessProject project=service.getProject(projectId,invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(),invocation.getActor().hasPermission("business:boss:view"));
        if(project==null)throw new ServiceException("项目不存在或无权访问");
        BusinessProjectMember found=null;
        if(project.getMembers()!=null)for(BusinessProjectMember member:project.getMembers())if(Objects.equals(memberId,member.getUserId()))found=member;
        if(found==null)throw new ServiceException("该人员不属于当前项目成员范围");
        Map<String,Object> authority=attendance.getPersonAuthority(memberId,project.getCompanyDeptId(),AiCapabilityInputs.date(input.get("leaveDate")));
        if(!Boolean.TRUE.equals(authority.get("localLeaveAllowed")))throw new ServiceException("该人员在此日期已使用飞书假勤，请在飞书办理；本地仅保留历史查询");
        return found;
    }
    private void validate(Map<String, Object> input)
    {
        String operation = AiCapabilityInputs.upper(input.get("operation"));
        if (!"MARK".equals(operation) && !"CANCEL".equals(operation)) throw new ServiceException("请假操作类型不正确");
        if (AiCapabilityInputs.date(input.get("leaveDate")) == null) throw new ServiceException("请假日期不正确");
        if ("MARK".equals(operation) && AiCapabilityInputs.text(input.get("reason")).isEmpty())
            throw new ServiceException("登记请假必须填写原因");
    }
}
