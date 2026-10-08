package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ProjectDetailCapability implements AiCapability
{
    private final IBusinessProjectService service;
    @Autowired public ProjectDetailCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.detail.get"; }
    @Override public String description() { return "按稳定项目ID读取当前账号有权查看的项目完整详情，包括目标、计划、成员、任务、持续工作、KPI、风险、验收和事件。应先用项目目录取得ID。"; }
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
        Long projectId = number(input.get("projectId"));
        if (projectId == null) throw new ServiceException("请先确定要查看的项目");
        Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("project", service.getProject(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), invocation.getActor().hasPermission("business:boss:view")));
        return result;
    }
    private Long number(Object value)
    {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (Exception ex) { return null; }
    }
}
