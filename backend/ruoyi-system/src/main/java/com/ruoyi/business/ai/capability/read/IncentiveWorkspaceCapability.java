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
import com.ruoyi.business.service.IBusinessIncentiveService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class IncentiveWorkspaceCapability implements AiCapability
{
    private final IBusinessIncentiveService service;
    @Autowired public IncentiveWorkspaceCapability(IBusinessIncentiveService service) { this.service = service; }
    @Override public String code() { return "incentive.workspace.get"; }
    @Override public String description()
    {
        return "只读查询本人有权查看的项目奖金规则、奖励申请、核准、成本状态及审计；可省略项目ID读取授权目录。"
            + "项目指标确认、奖励核准、成本入账分别记录。历史奖金池不是个人工资，未记录支付不能描述为已支付。"
            + "本能力不执行核准、成本确认、分配或付款。";
    }
    @Override public String requiredPermission() { return "business:incentive:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String,Object> inputSchema()
    {
        Map<String,Object> schema = AiSchemas.object();
        AiSchemas.property(schema,"projectId","number","授权项目目录中的稳定项目ID，可选");
        return schema;
    }
    @Override public Map<String,Object> execute(AiCapabilityInvocation invocation, Map<String,Object> input)
    {
        if (!invocation.getActor().hasPermission(requiredPermission())) throw new ServiceException("无权查询奖金激励");
        Long projectId = input == null ? null : AiCapabilityInputs.number(input.get("projectId"));
        if (input != null && input.get("projectId") != null && (projectId == null || projectId <= 0))
            throw new ServiceException("项目ID不正确");
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("workspace",service.workspace(projectId,invocation.getActor().getUserId(),invocation.getActor().isAdministrator()));
        result.put("meaning","奖励核准后仅生成待确认成本；个人分配和支付未记录。历史联动项目奖金不重复生成独立奖励。");
        return result;
    }
}
