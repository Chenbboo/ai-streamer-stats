package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class RemoveProjectRiskCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public RemoveProjectRiskCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.risk.remove"; }
    @Override public String description()
    { return "删除一条项目风险记录。必须先读取项目详情取得真实projectId和riskId，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:task"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); AiSchemas.property(s, "projectId", "number", "项目ID");
        AiSchemas.property(s, "riskId", "number", "风险ID"); AiSchemas.property(s, "riskTitle", "string", "确认卡展示的风险标题");
        return AiSchemas.required(s, "projectId", "riskId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "删除项目风险“" + AiCapabilityInputs.text(input.get("riskTitle")) + "”"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId")); Long riskId = AiCapabilityInputs.number(input.get("riskId"));
        service.deleteRisk(projectId, riskId, invocation.getActor().getUserId(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", projectId);
        result.put("riskId", riskId); result.put("status", "REMOVED"); return result;
    }
}
