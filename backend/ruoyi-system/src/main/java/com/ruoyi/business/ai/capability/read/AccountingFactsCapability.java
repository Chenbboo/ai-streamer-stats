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
import com.ruoyi.business.service.IBusinessAccountingService;

@Component
public class AccountingFactsCapability implements AiCapability
{
    private final IBusinessAccountingService service;
    @Autowired public AccountingFactsCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.fact.list"; }
    @Override public String description() { return "查询当前账号有权查看的收支事实及稳定 factId，可按项目、日期、状态或 factId 筛选。确认或冲销前必须先查询并核对。"; }
    @Override public String requiredPermission() { return "business:accounting:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "factId", "number", "可选，精确收支事实ID");
        AiSchemas.property(schema, "projectId", "number", "可选，稳定项目ID");
        AiSchemas.property(schema, "dateFrom", "string", "开始日期 YYYY-MM-DD");
        AiSchemas.property(schema, "dateTo", "string", "结束日期 YYYY-MM-DD");
        AiSchemas.property(schema, "status", "string", "DRAFT、CONFIRMED 或 REVERSED");
        return schema;
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        copy(query, input, "factId"); copy(query, input, "projectId"); copy(query, input, "dateFrom");
        copy(query, input, "dateTo"); copy(query, input, "status");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("facts", service.facts(query, invocation.getActor().getUserId(), invocation.getActor().isAdministrator()));
        return result;
    }
    private void copy(Map<String, Object> target, Map<String, Object> source, String key)
    { if (source.containsKey(key) && !AiCapabilityInputs.text(source.get(key)).isEmpty()) target.put(key, source.get(key)); }
}
