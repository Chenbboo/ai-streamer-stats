package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveProjectKpiCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveProjectKpiCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.kpi.save"; }
    @Override public String description() { return "新增项目KPI或根据 kpiId 生成新的KPI目标版本。更新前先读取项目经营配置取得当前 kpiId；确认后才执行。"; }
    @Override public String requiredPermission() { return "business:project:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object();
        AiSchemas.property(s, "projectId", "number", "项目ID"); AiSchemas.property(s, "kpiId", "number", "更新现有KPI时传当前 kpiId");
        AiSchemas.property(s, "kpiCode", "string", "新增时必填的KPI编码"); AiSchemas.property(s, "kpiName", "string", "KPI名称");
        AiSchemas.property(s, "metricType", "string", "COUNT、AMOUNT、PERCENT、DURATION、SCORE、MILESTONE");
        AiSchemas.property(s, "periodType", "string", "DAY、WEEK、MONTH、QUARTER、PROJECT");
        AiSchemas.property(s, "targetValue", "number", "目标值"); AiSchemas.property(s, "actualValue", "number", "当前实际值");
        AiSchemas.property(s, "unit", "string", "单位"); AiSchemas.property(s, "weight", "number", "权重0到100");
        AiSchemas.property(s, "ownerUserId", "number", "指标负责人用户ID，必须是项目成员");
        AiSchemas.property(s, "effectiveFrom", "string", "生效日期 YYYY-MM-DD"); AiSchemas.property(s, "effectiveTo", "string", "失效日期 YYYY-MM-DD");
        AiSchemas.property(s, "remark", "string", "调整说明"); return AiSchemas.required(s, "projectId", "kpiName", "targetValue");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return (AiCapabilityInputs.number(input.get("kpiId")) == null ? "新增" : "调整") + "项目KPI“"
            + AiCapabilityInputs.text(input.get("kpiName")) + "”，目标值 " + AiCapabilityInputs.text(input.get("targetValue"))
            + (AiCapabilityInputs.text(input.get("unit")).isEmpty() ? "" : " " + AiCapabilityInputs.text(input.get("unit")));
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProjectKpi k = new BusinessProjectKpi(); k.setProjectId(AiCapabilityInputs.number(input.get("projectId")));
        k.setKpiId(AiCapabilityInputs.number(input.get("kpiId"))); k.setKpiCode(AiCapabilityInputs.upper(input.get("kpiCode")));
        k.setKpiName(AiCapabilityInputs.text(input.get("kpiName"))); k.setMetricType(AiCapabilityInputs.upper(input.get("metricType")));
        k.setPeriodType(AiCapabilityInputs.upper(input.get("periodType"))); k.setTargetValue(AiCapabilityInputs.decimal(input.get("targetValue")));
        k.setActualValue(AiCapabilityInputs.decimal(input.get("actualValue"))); k.setUnit(AiCapabilityInputs.text(input.get("unit")));
        k.setWeight(AiCapabilityInputs.decimal(input.get("weight"))); k.setOwnerUserId(AiCapabilityInputs.number(input.get("ownerUserId")));
        k.setEffectiveFrom(AiCapabilityInputs.date(input.get("effectiveFrom"))); k.setEffectiveTo(AiCapabilityInputs.date(input.get("effectiveTo")));
        k.setRemark(AiCapabilityInputs.text(input.get("remark")));
        BusinessProjectKpi saved = service.saveKpi(k, invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", saved.getProjectId());
        result.put("kpiId", saved.getKpiId()); result.put("kpiCode", saved.getKpiCode()); result.put("kpiName", saved.getKpiName());
        result.put("targetValue", saved.getTargetValue()); result.put("targetVersion", saved.getTargetVersion()); return result;
    }
}
