package com.ruoyi.business.ai.capability.accounting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ReverseAccountingFactCapability implements AiConfirmableCapability
{
    private final IBusinessAccountingService service;
    @Autowired public ReverseAccountingFactCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.fact.reverse"; }
    @Override public String description() { return "冲销一条已确认收支事实。必须先按 factId 查询原事实并提供明确原因；属于高影响经营操作，确认后才执行。"; }
    @Override public String requiredPermission() { return "business:accounting:confirm"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object(); AiSchemas.property(schema, "factId", "number", "已确认收支事实ID");
        AiSchemas.property(schema, "reason", "string", "冲销原因，不能为空"); return AiSchemas.required(schema, "factId", "reason");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> fact = fact(invocation, input);
        return "冲销项目“" + fact.get("projectName") + "”的已确认收支 " + fact.get("amount") + " "
            + fact.get("currency") + "；原因：" + AiCapabilityInputs.text(input.get("reason"));
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    { Map<String, Object> result = fact(invocation, input); result.put("reason", AiCapabilityInputs.text(input.get("reason"))); return result; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessOperatingFact saved = service.reverseFact(AiCapabilityInputs.number(input.get("factId")),
            AiCapabilityInputs.text(input.get("reason")), invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), invocation.getActor().isAdministrator());
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("factId", saved.getFactId());
        result.put("reversalFactId", saved.getReversalFactId()); result.put("status", saved.getStatus()); return result;
    }
    private Map<String, Object> fact(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        if (AiCapabilityInputs.text(input.get("reason")).isEmpty()) throw new ServiceException("冲销原因不能为空");
        Map<String, Object> query = new LinkedHashMap<String, Object>(); query.put("factId", AiCapabilityInputs.number(input.get("factId")));
        List<Map<String, Object>> rows = service.facts(query, invocation.getActor().getUserId(), invocation.getActor().isAdministrator());
        if (rows == null || rows.size() != 1 || !"CONFIRMED".equals(String.valueOf(rows.get(0).get("status"))))
            throw new ServiceException("已确认收支不存在、无权访问或状态已变化");
        return new LinkedHashMap<String, Object>(rows.get(0));
    }
}
