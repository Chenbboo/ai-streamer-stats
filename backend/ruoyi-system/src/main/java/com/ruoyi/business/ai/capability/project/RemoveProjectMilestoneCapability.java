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
public class RemoveProjectMilestoneCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public RemoveProjectMilestoneCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.milestone.remove"; }
    @Override public String description()
    { return "删除项目里程碑。必须先读取项目详情取得真实projectId和milestoneId，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:task"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object();
        AiSchemas.property(s, "projectId", "number", "项目ID");
        AiSchemas.property(s, "milestoneId", "number", "里程碑ID");
        AiSchemas.property(s, "milestoneName", "string", "确认卡展示的里程碑名称");
        return AiSchemas.required(s, "projectId", "milestoneId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "删除项目里程碑“" + AiCapabilityInputs.text(input.get("milestoneName")) + "”"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        Long milestoneId = AiCapabilityInputs.number(input.get("milestoneId"));
        service.deleteMilestone(projectId, milestoneId, invocation.getActor().getUserId(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId); result.put("milestoneId", milestoneId); result.put("status", "REMOVED");
        return result;
    }
}
