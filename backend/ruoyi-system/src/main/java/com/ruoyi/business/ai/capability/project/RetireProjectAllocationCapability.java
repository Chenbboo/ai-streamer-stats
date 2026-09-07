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
public class RetireProjectAllocationCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public RetireProjectAllocationCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.allocation.retire"; }
    @Override public String description() { return "仅停用历史比例成本项目的投入配置。ACTUAL_WORK_V1 项目的资源安排须从资源与实际工作流程停用，可先查询 project.work.get。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "稳定项目ID");
        AiSchemas.property(schema, "allocationId", "number", "要停用的投入配置ID");
        AiSchemas.property(schema, "staffName", "string", "可选，仅用于确认单展示");
        return AiSchemas.required(schema, "projectId", "allocationId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        ProjectLegacyPlanningGuard.requireLegacy(service, invocation, input, false);
        String staffName = AiCapabilityInputs.text(input.get("staffName"));
        return "停用项目 " + AiCapabilityInputs.number(input.get("projectId")) + " 的投入配置 "
            + AiCapabilityInputs.number(input.get("allocationId")) + (staffName.isEmpty() ? "" : "（" + staffName + "）");
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        ProjectLegacyPlanningGuard.requireLegacy(service, invocation, input, false);
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        Long allocationId = AiCapabilityInputs.number(input.get("allocationId"));
        service.removeStaffAllocation(projectId, allocationId, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId); result.put("allocationId", allocationId); result.put("status", "RETIRED");
        return result;
    }
}
