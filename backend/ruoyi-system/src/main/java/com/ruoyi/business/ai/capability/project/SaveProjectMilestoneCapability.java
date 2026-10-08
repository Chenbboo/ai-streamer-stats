package com.ruoyi.business.ai.capability.project;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveProjectMilestoneCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveProjectMilestoneCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.milestone.save"; }
    @Override public String description()
    { return "新增或修改项目里程碑。先读取项目详情取得真实项目、里程碑ID；修改现有里程碑时携带milestoneId，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:task"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object();
        AiSchemas.property(s, "projectId", "number", "项目目录或详情返回的项目ID");
        AiSchemas.property(s, "milestoneId", "number", "修改时的里程碑ID");
        AiSchemas.property(s, "milestoneName", "string", "里程碑名称");
        AiSchemas.property(s, "planDate", "string", "计划日期 YYYY-MM-DD");
        Map<String, Object> status = AiSchemas.property(s, "status", "string", "负责人可维护PENDING或DOING；验收状态由系统流程写入");
        status.put("enum", Arrays.asList("PENDING", "DOING"));
        AiSchemas.property(s, "sortOrder", "number", "显示顺序");
        return AiSchemas.required(s, "projectId", "milestoneName");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return (AiCapabilityInputs.number(input.get("milestoneId")) == null ? "新增" : "修改")
            + "项目里程碑“" + AiCapabilityInputs.text(input.get("milestoneName")) + "”";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProjectMilestone value = new BusinessProjectMilestone();
        value.setProjectId(AiCapabilityInputs.number(input.get("projectId")));
        value.setMilestoneId(AiCapabilityInputs.number(input.get("milestoneId")));
        value.setMilestoneName(AiCapabilityInputs.text(input.get("milestoneName")));
        value.setPlanDate(AiCapabilityInputs.date(input.get("planDate")));
        value.setStatus(AiCapabilityInputs.upper(input.get("status")));
        value.setSortOrder(AiCapabilityInputs.integer(input.get("sortOrder")));
        BusinessProjectMilestone saved = service.saveMilestone(value, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", saved.getProjectId()); result.put("milestoneId", saved.getMilestoneId());
        result.put("milestoneName", saved.getMilestoneName()); result.put("status", saved.getStatus());
        return result;
    }
}
