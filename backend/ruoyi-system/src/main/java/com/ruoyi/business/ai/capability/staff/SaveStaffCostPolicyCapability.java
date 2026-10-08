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
    @Override public String description() { return "为人员新增一版人民币月度用人成本。系统按人员国家自动使用中国21.75天或越南26天折算日成本；旧版按生效日期衔接，确认后才写入。"; }
    @Override public String requiredPermission() { return "business:staff:cost"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定人员ID");
        AiSchemas.property(schema, "monthlyCost", "number", "人民币月度用人成本");
        AiSchemas.property(schema, "effectiveFrom", "string", "生效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "effectiveTo", "string", "可选失效日期 YYYY-MM-DD");
        AiSchemas.property(schema, "remark", "string", "调整原因");
        return AiSchemas.required(schema, "staffUserId", "monthlyCost", "effectiveFrom");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return "将人员 " + AiCapabilityInputs.number(input.get("staffUserId")) + " 的内部成本设为 "
            + AiCapabilityInputs.decimal(input.get("monthlyCost")) + " CNY/月，系统按所属国家折算日成本，自 "
            + AiCapabilityInputs.text(input.get("effectiveFrom")) + " 生效";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setUserId(AiCapabilityInputs.number(input.get("staffUserId")));
        policy.setCostMode("MONTHLY");
        policy.setUnitCost(AiCapabilityInputs.decimal(input.get("monthlyCost")));
        policy.setCurrency("CNY");
        policy.setEffectiveFrom(AiCapabilityInputs.date(input.get("effectiveFrom")));
        policy.setEffectiveTo(AiCapabilityInputs.date(input.get("effectiveTo")));
        policy.setRemark(AiCapabilityInputs.text(input.get("remark")));
        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(policy, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("policyId", saved.getPolicyId()); result.put("staffUserId", saved.getUserId());
        result.put("costMode", saved.getCostMode()); result.put("unitCost", saved.getUnitCost());
        result.put("currency", saved.getCurrency()); result.put("countryRegion", saved.getCountryRegion());
        result.put("standardWorkDays", saved.getStandardWorkDays()); result.put("policyVersion", saved.getPolicyVersion());
        return result;
    }
}
