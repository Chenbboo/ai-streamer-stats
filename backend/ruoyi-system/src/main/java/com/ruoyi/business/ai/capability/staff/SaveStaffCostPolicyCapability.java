package com.ruoyi.business.ai.capability.staff;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveStaffCostPolicyCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveStaffCostPolicyCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "staff.cost-policy.save"; }
    @Override public String description() { return "为人员新增一版内部成本政策。旧版会按生效日期衔接，确认后才写入。修改前先读取人员成本政策。"; }
    @Override public String requiredPermission() { return "business:staff:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定人员ID");
        AiSchemas.property(schema, "costMode", "string", "DAILY、HOURLY、MONTHLY、FIXED_PROJECT、FIXED_TASK、VARIABLE");
        AiSchemas.property(schema, "unitCost", "number", "单位成本");
        AiSchemas.property(schema, "currency", "string", "三位币种代码");
        AiSchemas.property(schema, "effectiveFrom", "string", "生效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "effectiveTo", "string", "可选失效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "remark", "string", "调整原因");
        return AiSchemas.required(schema, "staffUserId", "costMode", "unitCost", "currency", "effectiveFrom");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return "将人员 " + AiCapabilityInputs.number(input.get("staffUserId")) + " 的内部成本设为 "
            + AiCapabilityInputs.decimal(input.get("unitCost")) + " " + AiCapabilityInputs.upper(input.get("currency"))
            + "（" + AiCapabilityInputs.upper(input.get("costMode")) + "），自 "
            + AiCapabilityInputs.text(input.get("effectiveFrom")) + " 生效";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setUserId(AiCapabilityInputs.number(input.get("staffUserId")));
        policy.setCostMode(AiCapabilityInputs.upper(input.get("costMode")));
        policy.setUnitCost(AiCapabilityInputs.decimal(input.get("unitCost")));
        policy.setCurrency(AiCapabilityInputs.upper(input.get("currency")));
        policy.setEffectiveFrom(AiCapabilityInputs.date(input.get("effectiveFrom")));
        policy.setEffectiveTo(AiCapabilityInputs.date(input.get("effectiveTo")));
        policy.setRemark(AiCapabilityInputs.text(input.get("remark")));
        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(policy, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("policyId", saved.getPolicyId()); result.put("staffUserId", saved.getUserId());
        result.put("costMode", saved.getCostMode()); result.put("unitCost", saved.getUnitCost());
        result.put("currency", saved.getCurrency()); result.put("policyVersion", saved.getPolicyVersion());
        return result;
    }
}
