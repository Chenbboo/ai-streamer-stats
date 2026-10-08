package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class ProjectDirectoryCapability implements AiCapability
{
    private final IBusinessProjectService service;
    @Autowired public ProjectDirectoryCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.directory.get"; }
    @Override public String description() { return "列出当前账号可见的项目名称、立项老板和是否可打开详情，用于先定位项目，结果不受看板前十条限制。"; }
    @Override public String requiredPermission() { return "business:project:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projects", service.projectDirectory(invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), invocation.getActor().hasPermission("business:boss:view")));
        return result;
    }
}
