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
public class ConfirmProjectEffortCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public ConfirmProjectEffortCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.effort.week.confirm"; }
    @Override public String description()
    { return "确认项目指定周的人员实际投入。先查询并确定项目ID和周锚点日期，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); AiSchemas.property(s, "projectId", "number", "项目ID");
        AiSchemas.property(s, "projectName", "string", "确认卡展示的项目名称");
        AiSchemas.property(s, "anchorDate", "string", "目标周内任一天 YYYY-MM-DD");
        return AiSchemas.required(s, "projectId", "anchorDate");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "确认项目“" + AiCapabilityInputs.text(input.get("projectName")) + "”在 "
        + AiCapabilityInputs.text(input.get("anchorDate")) + " 所在周的人员投入"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return service.confirmProjectEffortWeek(AiCapabilityInputs.number(input.get("projectId")),
            AiCapabilityInputs.text(input.get("anchorDate")), invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
    }
}
