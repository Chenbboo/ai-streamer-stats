package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessAccountingService;

@Component
public class OperatingOverviewCapability implements AiCapability
{
    private final IBusinessAccountingService service;
    @Autowired public OperatingOverviewCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "business.operating.overview"; }
    @Override public String description() { return "读取当前登录老板今天的公司经营总览，包括收入、业务成本、人员成本、利润和异常。"; }
    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return service.bossOverview(invocation.getActor().getUserId(), invocation.getActor().isAdministrator()); }
}
