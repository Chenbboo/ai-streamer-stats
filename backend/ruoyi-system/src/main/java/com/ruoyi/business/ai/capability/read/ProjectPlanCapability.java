package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.impl.BusinessProjectPlanService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ProjectPlanCapability implements AiCapability
{
    private final BusinessProjectPlanService service;
    @Autowired public ProjectPlanCapability(BusinessProjectPlanService service) { this.service = service; }
    @Override public String code() { return "project.plan.get"; }
    @Override public String description()
    { return "只读查询有权查看的标准项目计划基线版本、变更单及负责人预测。基线、预测、实际分别记录；预算和计划日期修改须走计划变更流程。本能力不申请、不核准变更，也不改写预算或实际成本。"; }
    @Override public String requiredPermission() { return "business:project:list"; }
    @Override public boolean isAllowed(AiExecutionContext actor)
    { return actor != null && (actor.hasPermission(requiredPermission()) || actor.hasPermission("business:project:owner:view")); }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String,Object> inputSchema()
    {
        Map<String,Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "授权目录中的稳定项目ID");
        return AiSchemas.required(schema, "projectId");
    }
    @Override public Map<String,Object> execute(AiCapabilityInvocation invocation, Map<String,Object> input)
    {
        if (!isAllowed(invocation.getActor())) throw new ServiceException("无权查询项目计划");
        Long projectId = ProjectWorkCapability.projectId(input);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("projectId", projectId);
        result.put("plan", service.plan(projectId, invocation.getActor().getUserId(), invocation.getActor().isAdministrator()));
        result.put("meaning", "基线保存已授权计划，预测表达当前判断，二者均不代表已发生成本；修改须进入计划变更流程。");
        return result;
    }
}
