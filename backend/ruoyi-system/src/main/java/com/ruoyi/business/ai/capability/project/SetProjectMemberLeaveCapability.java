package com.ruoyi.business.ai.capability.project;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class SetProjectMemberLeaveCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SetProjectMemberLeaveCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.member.leave.set"; }
    @Override public String description()
    { return "登记或取消项目成员某天请假。先读取项目详情和人员目录取得稳定ID；登记必须有原因，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); Map<String, Object> operation = AiSchemas.property(s,
            "operation", "string", "MARK登记请假，CANCEL取消请假");
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
        validate(input); return ("CANCEL".equals(AiCapabilityInputs.upper(input.get("operation"))) ? "取消" : "登记")
            + "“" + AiCapabilityInputs.text(input.get("memberName")) + "”在 "
            + AiCapabilityInputs.text(input.get("leaveDate")) + " 的请假";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); String operation = AiCapabilityInputs.upper(input.get("operation"));
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        Long memberId = AiCapabilityInputs.number(input.get("memberUserId"));
        if ("MARK".equals(operation)) service.markMemberLeave(projectId, memberId,
            AiCapabilityInputs.date(input.get("leaveDate")), AiCapabilityInputs.text(input.get("reason")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        else service.cancelMemberLeave(projectId, memberId, AiCapabilityInputs.date(input.get("leaveDate")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", projectId);
        result.put("memberUserId", memberId); result.put("leaveDate", input.get("leaveDate"));
        result.put("status", "MARK".equals(operation) ? "ACTIVE" : "CANCELED"); return result;
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
