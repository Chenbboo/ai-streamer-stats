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
public class ProjectOperatingConfigCapability implements AiCapability
{
    private final IBusinessProjectService service;
    @Autowired public ProjectOperatingConfigCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.operating-config.get"; }
    @Override public String description() { return "读取项目经营配置，包括预算、KPI版本、成员计划投入和内部核算配置。修改这些资料前先调用本能力取得稳定ID和当前版本。"; }
    @Override public String requiredPermission() { return "business:project:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定项目ID");
        return AiSchemas.required(schema, "projectId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = AiCapabilityInputs.number(input.get("projectId"));
        if (projectId == null) throw new ServiceException("请先确定要查看的项目");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId);
        result.put("operatingConfig", service.operatingConfig(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), invocation.getActor().hasPermission("business:boss:view")));
        return result;
    }
}
