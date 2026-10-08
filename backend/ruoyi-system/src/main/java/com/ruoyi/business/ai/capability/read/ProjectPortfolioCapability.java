package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class ProjectPortfolioCapability implements AiCapability
{
    private final IBusinessProjectService service;
    @Autowired public ProjectPortfolioCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.portfolio.get"; }
    @Override public String description() { return "读取当前登录老板可管理项目的组合看板、状态、待办和风险概况。"; }
    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return service.dashboard(invocation.getActor().getUserId(), invocation.getActor().isAdministrator(), true); }
}
