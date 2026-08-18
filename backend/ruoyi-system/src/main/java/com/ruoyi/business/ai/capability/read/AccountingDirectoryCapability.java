package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessAccountingService;

/** Safe lookup data required before the model prepares an accounting fact. */
@Component
public class AccountingDirectoryCapability implements AiCapability
{
    private final IBusinessAccountingService service;

    @Autowired
    public AccountingDirectoryCapability(IBusinessAccountingService service) { this.service = service; }

    @Override public String code() { return "accounting.directory.get"; }
    @Override public String description()
    { return "读取当前登录账号有权使用的经营收支项目、公司和收支分类目录；录入收入或支出前应先调用此工具取得真实ID。"; }
    @Override public String requiredPermission() { return "business:accounting:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> dashboard = service.dashboard(new LinkedHashMap<String, Object>(),
            invocation.getActor().getUserId(), invocation.getActor().isAdministrator());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("companies", dashboard.get("companies"));
        result.put("projects", dashboard.get("projects"));
        result.put("categories", dashboard.get("categories"));
        return result;
    }
}
