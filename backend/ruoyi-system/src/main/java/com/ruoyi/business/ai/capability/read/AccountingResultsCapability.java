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

/** Finds daily project results before a detail query is made by stable resultId. */
@Component
public class AccountingResultsCapability implements AiCapability
{
    private final IBusinessAccountingService service;

    @Autowired public AccountingResultsCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.result.list"; }
    @Override public String description()
    {
        return "按项目和日期查询经营结果清单与汇总，返回稳定 resultId。"
            + "需要查看逐项收支和逐人人员成本时，再调用经营结果明细能力。";
    }
    @Override public String requiredPermission() { return "business:accounting:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "可选，项目目录返回的稳定项目ID");
        AiSchemas.property(schema, "dateFrom", "string", "可选，开始日期 YYYY-MM-DD");
        AiSchemas.property(schema, "dateTo", "string", "可选，结束日期 YYYY-MM-DD");
        return schema;
    }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        copy(query, input, "projectId");
        copy(query, input, "dateFrom");
        copy(query, input, "dateTo");
        Map<String, Object> dashboard = service.dashboard(query, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("summary", dashboard.get("summary"));
        result.put("results", dashboard.get("results"));
        return result;
    }

    private void copy(Map<String, Object> target, Map<String, Object> source, String key)
    {
        if (source != null && source.containsKey(key)
            && !AiCapabilityInputs.text(source.get(key)).isEmpty()) target.put(key, source.get(key));
    }
}
