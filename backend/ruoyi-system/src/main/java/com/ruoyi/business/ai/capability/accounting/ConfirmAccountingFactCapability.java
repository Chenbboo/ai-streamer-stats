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
public class ConfirmAccountingFactCapability implements AiConfirmableCapability
{
    private final IBusinessAccountingService service;
    @Autowired public ConfirmAccountingFactCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.fact.confirm"; }
    @Override public String description() { return "确认一条收支草稿，使其进入经营核算。先按 factId 查询并核对事实，老板确认后才执行。"; }
    @Override public String requiredPermission() { return "business:accounting:confirm"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "factId", "number", "收支事实列表返回的稳定 factId");
        return AiSchemas.required(schema, "factId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> fact = fact(invocation, input);
        return "确认项目“" + value(fact, "projectName") + "”在 " + value(fact, "bizDate") + " 的“"
            + value(fact, "categoryName") + "” " + value(fact, "amount") + " " + value(fact, "currency");
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return fact(invocation, input); }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long factId = AiCapabilityInputs.number(input.get("factId"));
        BusinessOperatingFact saved = service.confirmFact(factId, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), invocation.getActor().isAdministrator());
        return saved(saved);
    }
    private Map<String, Object> fact(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> query = new LinkedHashMap<String, Object>(); query.put("factId", AiCapabilityInputs.number(input.get("factId")));
        List<Map<String, Object>> rows = service.facts(query, invocation.getActor().getUserId(), invocation.getActor().isAdministrator());
        if (rows == null || rows.size() != 1 || !"DRAFT".equals(String.valueOf(rows.get(0).get("status"))))
            throw new ServiceException("收支草稿不存在、无权访问或状态已变化");
        return new LinkedHashMap<String, Object>(rows.get(0));
    }
    private Map<String, Object> saved(BusinessOperatingFact fact)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("factId", fact.getFactId());
        result.put("projectId", fact.getProjectId()); result.put("status", fact.getStatus());
        result.put("amount", fact.getAmount()); result.put("currency", fact.getCurrency()); return result;
    }
    private String value(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
}
