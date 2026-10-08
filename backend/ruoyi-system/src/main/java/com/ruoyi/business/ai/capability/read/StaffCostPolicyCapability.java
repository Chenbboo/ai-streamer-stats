package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class StaffCostPolicyCapability implements AiCapability
{
    private final IBusinessProjectService service;

    @Autowired
    public StaffCostPolicyCapability(IBusinessProjectService service) { this.service = service; }

    @Override public String code() { return "staff.cost-policy.get"; }
    @Override public String description() { return "读取指定人员当前及历史内部成本政策。应先查询人员目录取得稳定人员ID。金额仅对具备人员成本权限的负责人开放。"; }
    @Override public String requiredPermission() { return "business:staff:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定人员ID");
        return AiSchemas.required(schema, "staffUserId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long staffUserId = AiCapabilityInputs.number(input.get("staffUserId"));
        if (staffUserId == null) throw new ServiceException("请先确定要查看的人员");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffUserId", staffUserId);
        result.put("policies", service.staffCostPolicies(staffUserId, invocation.getActor().getUserId(), true));
        return result;
    }
}
