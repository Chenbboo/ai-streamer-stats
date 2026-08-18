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
import com.ruoyi.common.exception.ServiceException;

@Component
public class AccountingResultDetailCapability implements AiCapability
{
    private final IBusinessAccountingService service;
    @Autowired public AccountingResultDetailCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.result.detail"; }
    @Override public String description() { return "按稳定 resultId 读取项目某日经营结果、收支组成及逐人人员成本明细。"; }
    @Override public String requiredPermission() { return "business:accounting:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object(); AiSchemas.property(schema, "resultId", "number", "经营看板返回的稳定结果ID");
        return AiSchemas.required(schema, "resultId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long resultId = AiCapabilityInputs.number(input.get("resultId")); if (resultId == null) throw new ServiceException("请先确定经营结果ID");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("result", service.resultDetail(resultId, invocation.getActor().getUserId(), invocation.getActor().isAdministrator()));
        return result;
    }
}
