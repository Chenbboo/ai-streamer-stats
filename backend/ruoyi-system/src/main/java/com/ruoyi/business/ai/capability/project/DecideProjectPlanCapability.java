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
import com.ruoyi.common.utils.StringUtils;

/** Boss approval or return of a submitted project plan. */
public class DecideProjectPlanCapability implements AiConfirmableCapability
{
    private final ProjectPlanCapabilitySupport support;

    @Autowired
    public DecideProjectPlanCapability(ProjectPlanCapabilitySupport support)
    {
        this.support = support;
    }

    @Override public String code() { return "project.plan.decide"; }

    @Override
    public String description()
    {
        return "批准已提交的项目计划并启动项目，或退回负责人调整。必须使用稳定 projectId；退回时必须填写原因；"
            + "用户最终确认后才执行。";
    }

    @Override public String requiredPermission() { return "business:project:manage"; }

    @Override
    public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定 projectId");
        Map<String, Object> decision = AiSchemas.property(schema, "decision", "string", "APPROVE 批准启动；RETURN 退回调整");
        decision.put("enum", Arrays.asList("APPROVE", "RETURN"));
        AiSchemas.property(schema, "comment", "string", "审核意见；退回时必须填写具体原因");
        return AiSchemas.required(schema, "projectId", "decision");
    }

    @Override
    public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> details = details(invocation, input);
        String name = String.valueOf(details.get("projectName"));
        return "APPROVE".equals(details.get("decision"))
            ? "批准项目“" + name + "”的计划并启动"
            : "退回项目“" + name + "”的计划给负责人调整：" + details.get("comment");
    }

    @Override
    public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return details(invocation, input);
    }

    @Override
    public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        String decision = decision(input.get("decision"));
        String comment = AiCapabilityInputs.text(input.get("comment"));
        if ("RETURN".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回计划时必须说明原因");
        BusinessProject saved = support.decide(invocation, projectId, decision, comment);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", saved.getProjectId()); result.put("projectNo", saved.getProjectNo());
        result.put("projectName", saved.getProjectName()); result.put("decision", decision);
        result.put("status", saved.getStatus()); result.put("baselineStatus", saved.getBaselineStatus());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        String decision = decision(input.get("decision"));
        String comment = AiCapabilityInputs.text(input.get("comment"));
        if ("RETURN".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回计划时必须说明原因");
        Map<String, Object> review = support.review(invocation, projectId);
        Map<String, Object> project = (Map<String, Object>) review.get("project");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId); result.put("projectName", project.get("projectName"));
        result.put("mainOwnerName", project.get("mainOwnerName")); result.put("companyName", project.get("companyName"));
        result.put("decision", decision); result.put("comment", comment); result.put("planReview", review);
        return result;
    }

    private String decision(Object value)
    {
        String decision = AiCapabilityInputs.upper(value);
        if (!"APPROVE".equals(decision) && !"RETURN".equals(decision)) throw new ServiceException("计划审核决定不正确");
        return decision;
    }
}
