package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.ai.capability.project.ProjectPlanCapabilitySupport;

/** Read-only review of a submitted project plan. */
public class ProjectPlanReviewCapability implements AiCapability
{
    private final ProjectPlanCapabilitySupport support;

    @Autowired
    public ProjectPlanReviewCapability(ProjectPlanCapabilitySupport support)
    {
        this.support = support;
    }

    @Override public String code() { return "project.plan.review"; }

    @Override
    public String description()
    {
        return "读取一个已提交、等待老板审核的项目计划，返回项目目标、持续工作、一次性任务、成员、KPI、投入和风险。"
            + "必须先从项目目录取得稳定 projectId。";
    }

    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }

    @Override
    public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定 projectId");
        return AiSchemas.required(schema, "projectId");
    }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return support.review(invocation, AiCapabilityInputs.number(input.get("projectId")));
    }
}
