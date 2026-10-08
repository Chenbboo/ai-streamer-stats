package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.ai.capability.project.ProjectAcceptanceCapabilitySupport;

/** Read-only boss view of the latest pending project acceptance submission. */
@Component
public class ProjectAcceptanceReviewCapability implements AiCapability
{
    private final ProjectAcceptanceCapabilitySupport support;
    @Autowired public ProjectAcceptanceReviewCapability(ProjectAcceptanceCapabilitySupport support) { this.support = support; }
    @Override public String code() { return "project.acceptance.review"; }
    @Override public String description()
    { return "读取一个待验收项目的最新成果说明、交付内容、附件、任务、里程碑和高风险检查结果。先查询项目目录取得稳定项目ID。"; }
    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定项目ID");
        return AiSchemas.required(schema, "projectId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return support.review(invocation, AiCapabilityInputs.number(input.get("projectId"))); }
}
