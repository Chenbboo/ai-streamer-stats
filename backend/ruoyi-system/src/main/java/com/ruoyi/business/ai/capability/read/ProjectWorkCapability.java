package com.ruoyi.business.ai.capability.read;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.impl.BusinessProjectWorkService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ProjectWorkCapability implements AiCapability
{
    private final BusinessProjectWorkService service;
    @Autowired public ProjectWorkCapability(BusinessProjectWorkService service) { this.service = service; }
    @Override public String code() { return "project.work.get"; }
    @Override public String description()
    { return "只读查询有权查看的标准项目资源计划、实际小时或人天、工作审批及待计价状态。普通成员只读取服务允许的本人记录。计划、飞书考勤都不能代填实际工作，缺失实际是未知而非零。本能力不提交、确认、退回工作或调整资源。"; }
    @Override public String requiredPermission() { return "business:project:list"; }
    @Override public boolean isAllowed(AiExecutionContext actor)
    {
        return actor != null && (actor.hasPermission(requiredPermission())
            || actor.hasPermission("business:project:owner:view") || actor.hasPermission("business:work:report"));
    }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String,Object> inputSchema()
    {
        Map<String,Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "授权目录中的稳定项目ID");
        AiSchemas.property(schema, "dateFrom", "string", "可选开始日期 YYYY-MM-DD");
        AiSchemas.property(schema, "dateTo", "string", "可选结束日期 YYYY-MM-DD");
        return AiSchemas.required(schema, "projectId");
    }
    @Override public Map<String,Object> execute(AiCapabilityInvocation invocation, Map<String,Object> input)
    {
        if (!isAllowed(invocation.getActor())) throw new ServiceException("无权查询项目工作记录");
        Long projectId = projectId(input);
        Map<String,Object> query = new LinkedHashMap<String,Object>();
        String from = date(input.get("dateFrom")), to = date(input.get("dateTo"));
        if (from != null) query.put("dateFrom", from);
        if (to != null) query.put("dateTo", to);
        if (from != null && to != null && from.compareTo(to) > 0) throw new ServiceException("工作查询开始日期不能晚于结束日期");
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("projectId", projectId);
        result.put("workspace", service.workspace(projectId, query, invocation.getActor().getUserId(), invocation.getActor().isAdministrator()));
        result.put("meaning", "资源计划、已确认工作和已计价成本分别记录；未填工作或待计价不能当作零实际成本。");
        return result;
    }
    static Long projectId(Map<String,Object> input)
    {
        Long value = input == null ? null : AiCapabilityInputs.number(input.get("projectId"));
        if (value == null || value <= 0) throw new ServiceException("请提供有效项目ID");
        return value;
    }
    private String date(Object value)
    {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        String text = String.valueOf(value).trim();
        try { return LocalDate.parse(text).toString(); }
        catch (RuntimeException ex) { throw new ServiceException("工作查询日期须为有效的 YYYY-MM-DD 日期"); }
    }
}
