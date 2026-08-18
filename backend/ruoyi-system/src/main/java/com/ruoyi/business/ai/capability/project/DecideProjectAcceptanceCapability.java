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
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.common.exception.ServiceException;

/** Confirmed boss decision for a pending project acceptance. */
@Component
public class DecideProjectAcceptanceCapability implements AiConfirmableCapability
{
    private final ProjectAcceptanceCapabilitySupport support;
    @Autowired public DecideProjectAcceptanceCapability(ProjectAcceptanceCapabilitySupport support) { this.support = support; }
    @Override public String code() { return "project.acceptance.decide"; }
    @Override public String description()
    { return "批准待验收项目并结项，或退回负责人继续执行。必须使用稳定项目ID；退回必须说明原因；老板最终确认后才执行。"; }
    @Override public String requiredPermission() { return "business:project:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定项目ID");
        Map<String, Object> decision = AiSchemas.property(schema, "decision", "string", "APPROVED 验收通过；RETURNED 退回负责人");
        decision.put("enum", Arrays.asList("APPROVED", "RETURNED"));
        AiSchemas.property(schema, "comment", "string", "验收意见；退回时必须填写具体原因");
        return AiSchemas.required(schema, "projectId", "decision");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> details = details(invocation, input);
        String decision = String.valueOf(details.get("decision"));
        String projectName = String.valueOf(details.get("projectName"));
        String version = String.valueOf(details.get("submissionVersion"));
        return "APPROVED".equals(decision)
            ? "通过项目“" + projectName + "”第 " + version + " 版验收并正式结项"
            : "退回项目“" + projectName + "”第 " + version + " 版验收：" + details.get("comment");
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return details(invocation, input); }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        String decision = decision(input.get("decision"));
        String comment = AiCapabilityInputs.text(input.get("comment"));
        BusinessProject saved = support.decide(invocation, projectId, decision, comment);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", saved.getProjectId()); result.put("projectName", saved.getProjectName());
        result.put("projectNo", saved.getProjectNo()); result.put("decision", decision); result.put("status", saved.getStatus());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        String decision = decision(input.get("decision"));
        String comment = AiCapabilityInputs.text(input.get("comment"));
        if ("RETURNED".equals(decision) && comment.isEmpty()) throw new ServiceException("退回验收时必须说明原因");
        Map<String, Object> review = support.review(invocation, projectId);
        if ("APPROVED".equals(decision) && !Boolean.TRUE.equals(review.get("canApprove")))
            throw new ServiceException("项目当前不满足验收通过条件");
        Map<String, Object> project = (Map<String, Object>) review.get("project");
        Map<String, Object> acceptance = (Map<String, Object>) review.get("acceptance");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId); result.put("projectName", project.get("projectName"));
        result.put("mainOwnerName", project.get("mainOwnerName")); result.put("companyName", project.get("companyName"));
        result.put("acceptanceId", acceptance.get("acceptanceId")); result.put("submissionVersion", acceptance.get("submissionVersion"));
        result.put("decision", decision); result.put("comment", comment); result.put("acceptanceReview", review);
        return result;
    }

    private String decision(Object value)
    {
        String decision = AiCapabilityInputs.upper(value);
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision)) throw new ServiceException("验收决定不正确");
        return decision;
    }
}
