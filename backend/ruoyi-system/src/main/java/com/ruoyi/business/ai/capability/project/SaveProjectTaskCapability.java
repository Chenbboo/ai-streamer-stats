package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessProjectService;

@Component
public class SaveProjectTaskCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public SaveProjectTaskCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.task.save"; }
    @Override public String description() { return "新增一次性任务或修改已有任务。修改时必须先读取项目详情并携带当前 taskId 和 version；持续重复工作应使用持续工作工具。"; }
    @Override public String requiredPermission() { return "business:project:task"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); AiSchemas.property(s, "projectId", "number", "项目ID");
        AiSchemas.property(s, "taskId", "number", "修改时的任务ID"); AiSchemas.property(s, "version", "number", "修改时的当前版本");
        AiSchemas.property(s, "taskName", "string", "任务名称"); AiSchemas.property(s, "assigneeUserId", "number", "项目成员用户ID");
        AiSchemas.property(s, "status", "string", "TODO、DOING、BLOCKED、DONE"); AiSchemas.property(s, "progress", "number", "进度0到100");
        AiSchemas.property(s, "priority", "string", "LOW、MEDIUM、HIGH"); AiSchemas.property(s, "planStartDate", "string", "开始日期 YYYY-MM-DD");
        AiSchemas.property(s, "dueDate", "string", "截止日期 YYYY-MM-DD"); AiSchemas.property(s, "parentTaskId", "number", "父任务ID");
        AiSchemas.property(s, "milestoneId", "number", "里程碑ID"); return AiSchemas.required(s, "projectId", "taskName");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return (AiCapabilityInputs.number(input.get("taskId")) == null ? "新增" : "修改") + "一次性任务“"
            + AiCapabilityInputs.text(input.get("taskName")) + "”" + (AiCapabilityInputs.number(input.get("assigneeUserId")) == null
                ? "" : "，分配给人员ID " + AiCapabilityInputs.number(input.get("assigneeUserId")));
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProjectTask task = new BusinessProjectTask(); task.setProjectId(AiCapabilityInputs.number(input.get("projectId")));
        task.setTaskId(AiCapabilityInputs.number(input.get("taskId"))); task.setVersion(AiCapabilityInputs.integer(input.get("version")));
        task.setTaskName(AiCapabilityInputs.text(input.get("taskName"))); task.setAssigneeUserId(AiCapabilityInputs.number(input.get("assigneeUserId")));
        task.setStatus(AiCapabilityInputs.upper(input.get("status"))); task.setProgress(AiCapabilityInputs.integer(input.get("progress")));
        task.setPriority(AiCapabilityInputs.upper(input.get("priority"))); task.setPlanStartDate(AiCapabilityInputs.date(input.get("planStartDate")));
        task.setDueDate(AiCapabilityInputs.date(input.get("dueDate"))); task.setParentTaskId(AiCapabilityInputs.number(input.get("parentTaskId")));
        task.setMilestoneId(AiCapabilityInputs.number(input.get("milestoneId")));
        BusinessProjectTask saved = service.saveTask(task, invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", saved.getProjectId());
        result.put("taskId", saved.getTaskId()); result.put("taskName", saved.getTaskName()); result.put("assigneeName", saved.getAssigneeName());
        result.put("status", saved.getStatus()); result.put("version", saved.getVersion()); return result;
    }
}
