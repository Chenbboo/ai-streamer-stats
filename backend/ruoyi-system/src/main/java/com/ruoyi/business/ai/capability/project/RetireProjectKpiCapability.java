package com.ruoyi.business.ai.capability.project;

import static com.ruoyi.business.ai.capability.AiCapabilityInputs.number;
import static com.ruoyi.business.ai.capability.AiCapabilityInputs.text;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class RetireProjectKpiCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;

    @Autowired
    public RetireProjectKpiCapability(IBusinessProjectService service)
    {
        this.service = service;
    }

    @Override public String code() { return "project.kpi.retire"; }
    @Override public String description()
    { return "停用项目的当前KPI版本，历史数据保留。必须先读取项目详情取得真实projectId和kpiId。"; }
    @Override public String requiredPermission() { return "business:kpi:manage"; }

    @Override
    public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目ID");
        AiSchemas.property(schema, "kpiId", "number", "当前KPI ID");
        AiSchemas.property(schema, "kpiName", "string", "用于确认展示的KPI名称");
        return AiSchemas.required(schema, "projectId", "kpiId");
    }

    @Override
    public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        if (number(input.get("projectId")) == null || number(input.get("kpiId")) == null)
            throw new ServiceException("请先确定项目和KPI");
        return "停用KPI“" + (text(input.get("kpiName")).isEmpty()
            ? "ID " + number(input.get("kpiId")) : text(input.get("kpiName"))) + "”，历史版本继续保留";
    }

    @Override
    public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        service.retireKpi(number(input.get("projectId")), number(input.get("kpiId")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", number(input.get("projectId")));
        result.put("kpiId", number(input.get("kpiId")));
        result.put("status", "RETIRED");
        return result;
    }
}
