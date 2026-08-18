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
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveProjectRiskCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveProjectRiskCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.risk.save"; }
    @Override public String description()
    { return "新增或修改项目风险及应对计划。先读取项目详情和人员目录取得真实ID；修改时携带riskId，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:task"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object();
        AiSchemas.property(s, "projectId", "number", "项目ID"); AiSchemas.property(s, "riskId", "number", "修改时的风险ID");
        AiSchemas.property(s, "riskType", "string", "风险类型，未说明可填GENERAL");
        AiSchemas.property(s, "riskTitle", "string", "风险标题");
        Map<String, Object> severity = AiSchemas.property(s, "severity", "string", "LOW、MEDIUM、HIGH或CRITICAL");
        severity.put("enum", Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL"));
        Map<String, Object> probability = AiSchemas.property(s, "probability", "string", "LOW、MEDIUM或HIGH");
        probability.put("enum", Arrays.asList("LOW", "MEDIUM", "HIGH"));
        AiSchemas.property(s, "ownerUserId", "number", "项目成员中的风险负责人ID");
        AiSchemas.property(s, "dueDate", "string", "应对截止日期 YYYY-MM-DD");
        Map<String, Object> status = AiSchemas.property(s, "status", "string", "OPEN、MITIGATED或CLOSED");
        status.put("enum", Arrays.asList("OPEN", "MITIGATED", "CLOSED"));
        AiSchemas.property(s, "responsePlan", "string", "风险应对方案");
        return AiSchemas.required(s, "projectId", "riskTitle");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return (AiCapabilityInputs.number(input.get("riskId")) == null ? "新增" : "修改") + "项目风险“"
        + AiCapabilityInputs.text(input.get("riskTitle")) + "”"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProjectRisk value = new BusinessProjectRisk();
        value.setProjectId(AiCapabilityInputs.number(input.get("projectId"))); value.setRiskId(AiCapabilityInputs.number(input.get("riskId")));
        value.setRiskType(AiCapabilityInputs.upper(input.get("riskType"))); value.setRiskTitle(AiCapabilityInputs.text(input.get("riskTitle")));
        value.setSeverity(AiCapabilityInputs.upper(input.get("severity"))); value.setProbability(AiCapabilityInputs.upper(input.get("probability")));
        value.setOwnerUserId(AiCapabilityInputs.number(input.get("ownerUserId"))); value.setDueDate(AiCapabilityInputs.date(input.get("dueDate")));
        value.setStatus(AiCapabilityInputs.upper(input.get("status"))); value.setResponsePlan(AiCapabilityInputs.text(input.get("responsePlan")));
        BusinessProjectRisk saved = service.saveRisk(value, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", saved.getProjectId()); result.put("riskId", saved.getRiskId());
        result.put("riskTitle", saved.getRiskTitle()); result.put("status", saved.getStatus());
        return result;
    }
}
