package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveProjectAllocationCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveProjectAllocationCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.allocation.save"; }
    @Override public String description() { return "新增或调整项目成员的计划投入。根据 allocationId 更新时会生成新版本；修改前先读取项目经营配置。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "稳定项目ID");
        AiSchemas.property(schema, "allocationId", "number", "更新现有投入时传当前投入ID");
        AiSchemas.property(schema, "staffUserId", "number", "项目成员的稳定人员ID");
        AiSchemas.property(schema, "allocationMode", "string", "PERCENTAGE、HOURS、ATTENDANCE、FIXED_DAILY、PER_TASK");
        AiSchemas.property(schema, "allocationValue", "number", "投入值；比例方式为0到100");
        AiSchemas.property(schema, "effectiveFrom", "string", "生效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "effectiveTo", "string", "可选失效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "costPolicyId", "number", "可选，绑定的人员成本政策ID");
        AiSchemas.property(schema, "exceptionAllowed", "string", "Y 或 N");
        AiSchemas.property(schema, "exceptionReason", "string", "例外说明");
        return AiSchemas.required(schema, "projectId", "staffUserId", "allocationMode", "allocationValue", "effectiveFrom");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return "将人员 " + AiCapabilityInputs.number(input.get("staffUserId")) + " 在项目 "
            + AiCapabilityInputs.number(input.get("projectId")) + " 的计划投入设为 "
            + AiCapabilityInputs.decimal(input.get("allocationValue")) + "（"
            + AiCapabilityInputs.upper(input.get("allocationMode")) + "），自 "
            + AiCapabilityInputs.text(input.get("effectiveFrom")) + " 生效";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setAllocationId(AiCapabilityInputs.number(input.get("allocationId")));
        allocation.setProjectId(AiCapabilityInputs.number(input.get("projectId")));
        allocation.setUserId(AiCapabilityInputs.number(input.get("staffUserId")));
        allocation.setAllocationMode(AiCapabilityInputs.upper(input.get("allocationMode")));
        allocation.setAllocationValue(AiCapabilityInputs.decimal(input.get("allocationValue")));
        allocation.setEffectiveFrom(AiCapabilityInputs.date(input.get("effectiveFrom")));
        allocation.setEffectiveTo(AiCapabilityInputs.date(input.get("effectiveTo")));
        allocation.setCostPolicyId(AiCapabilityInputs.number(input.get("costPolicyId")));
        allocation.setExceptionAllowed(AiCapabilityInputs.upper(input.get("exceptionAllowed")));
        allocation.setExceptionReason(AiCapabilityInputs.text(input.get("exceptionReason")));
        BusinessProjectStaffAllocation saved = service.saveStaffAllocation(allocation,
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("allocationId", saved.getAllocationId()); result.put("projectId", saved.getProjectId());
        result.put("staffUserId", saved.getUserId()); result.put("allocationMode", saved.getAllocationMode());
        result.put("allocationValue", saved.getAllocationValue()); result.put("version", saved.getVersion());
        return result;
    }
}
