package com.ruoyi.business.ai.capability.project;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

/** Deterministic facts shared by project plan review and decision capabilities. */
@Component
public class ProjectPlanCapabilitySupport
{
    private final IBusinessProjectService projectService;

    @Autowired
    public ProjectPlanCapabilitySupport(IBusinessProjectService projectService)
    {
        this.projectService = projectService;
    }

    public Map<String, Object> review(AiCapabilityInvocation invocation, Long projectId)
    {
        if (projectId == null) throw new ServiceException("请先确定要审核的项目");
        BusinessProject detail = projectService.getProject(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        if (!"PLANNING".equals(detail.getStatus()) || !"SUBMITTED".equals(detail.getBaselineStatus()))
            throw new ServiceException("项目当前没有等待老板审核的已提交计划");

        Map<String, Object> operating = projectService.operatingConfig(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        List<Map<String, Object>> members = members(detail.getMembers());
        List<Map<String, Object>> tasks = tasks(detail.getTasks());
        List<Map<String, Object>> routines = routines(detail.getRoutines());
        List<Map<String, Object>> milestones = milestones(detail.getMilestones());
        List<Map<String, Object>> risks = risks(detail.getRisks());
        List<Map<String, Object>> kpis = whitelistedMaps(operating.get("kpis"), "kpiId", "kpiName", "targetValue",
            "unit", "periodType", "ownerName", "weight");
        List<Map<String, Object>> allocations = whitelistedMaps(operating.get("staffAllocations"), "allocationId",
            "userId", "userName", "allocationMode", "allocationValue", "effectiveFrom", "effectiveTo");

        List<String> checks = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        checks.add("项目目标和计划周期已填写");
        checks.add("负责人已提交项目计划基线");
        checks.add("已安排 " + routines.size() + " 项持续工作、" + tasks.size() + " 项一次性任务、"
            + members.size() + " 名参项人员");
        if (tasks.isEmpty() && routines.isEmpty()) warnings.add("没有可执行的持续工作或一次性任务");
        if (detail.getTasks() != null) for (BusinessProjectTask task : detail.getTasks())
        {
            if (task.getAssigneeUserId() == null) warnings.add("一次性任务“" + task.getTaskName() + "”尚未指定执行人");
            if (task.getDueDate() == null) warnings.add("一次性任务“" + task.getTaskName() + "”尚未设置完成日期");
        }
        if (detail.getRoutines() != null) for (BusinessProjectRoutine routine : detail.getRoutines())
            if (routine.getAssigneeUserId() == null) warnings.add("持续工作“" + routine.getRoutineName() + "”尚未指定执行人");
        if (kpis.isEmpty()) warnings.add("尚未设置项目 KPI，可根据项目需要后续补充");
        if (allocations.isEmpty()) warnings.add("尚未设置成员计划投入，人员成本暂时无法按计划分摊");
        if (detail.getRisks() != null) for (BusinessProjectRisk risk : detail.getRisks())
            if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                warnings.add("存在未关闭的高风险：“" + risk.getRiskTitle() + "”");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ready", true);
        result.put("project", project(detail));
        result.put("members", members);
        result.put("tasks", tasks);
        result.put("routines", routines);
        result.put("milestones", milestones);
        result.put("risks", risks);
        result.put("kpis", kpis);
        result.put("staffAllocations", allocations);
        result.put("taskCount", tasks.size());
        result.put("routineCount", routines.size());
        result.put("memberCount", members.size());
        result.put("kpiCount", kpis.size());
        result.put("allocationCount", allocations.size());
        result.put("checks", checks);
        result.put("warnings", warnings);
        result.put("recommendation", warnings.isEmpty()
            ? "计划要素完整，可以考虑批准启动"
            : "计划可以审核，但请先判断这些提示是否影响启动");
        return result;
    }

    public BusinessProject decide(AiCapabilityInvocation invocation, Long projectId, String decision, String comment)
    {
        review(invocation, projectId);
        String action = "APPROVE".equals(decision) ? "CONFIRM_BASELINE" : "RETURN_PLAN";
        return projectService.transition(projectId, action, comment, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
    }

    private Map<String, Object> project(BusinessProject value)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("projectId", value.getProjectId());
        row.put("projectNo", value.getProjectNo());
        row.put("projectName", value.getProjectName());
        row.put("companyName", value.getCompanyName());
        row.put("mainOwnerName", value.getMainOwnerName());
        row.put("objective", value.getObjective());
        row.put("status", value.getStatus());
        row.put("baselineStatus", value.getBaselineStatus());
        row.put("planStartDate", date(value.getPlanStartDate()));
        row.put("planEndDate", date(value.getPlanEndDate()));
        row.put("budgetLimit", value.getBudgetLimit());
        row.put("baseCurrency", value.getBaseCurrency());
        row.put("accountingMode", value.getAccountingMode());
        row.put("managementMode", value.getManagementMode());
        return row;
    }

    private List<Map<String, Object>> members(List<BusinessProjectMember> values)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (values == null) return rows;
        for (BusinessProjectMember value : values)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("userId", value.getUserId()); row.put("userName", value.getUserNameSnapshot());
            row.put("memberRole", value.getMemberRole()); row.put("joinedDate", date(value.getJoinedDate()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> tasks(List<BusinessProjectTask> values)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (values == null) return rows;
        for (BusinessProjectTask value : values)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("taskId", value.getTaskId()); row.put("taskName", value.getTaskName());
            row.put("assigneeName", value.getAssigneeName()); row.put("priority", value.getPriority());
            row.put("planStartDate", date(value.getPlanStartDate())); row.put("dueDate", date(value.getDueDate()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> routines(List<BusinessProjectRoutine> values)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (values == null) return rows;
        for (BusinessProjectRoutine value : values)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("routineId", value.getRoutineId()); row.put("routineName", value.getRoutineName());
            row.put("frequency", value.getFrequency()); row.put("targetValue", value.getTargetValue());
            row.put("unit", value.getUnit()); row.put("assigneeName", value.getAssigneeName());
            row.put("startDate", date(value.getStartDate())); row.put("endDate", date(value.getEndDate()));
            row.put("evidenceRequired", value.getEvidenceRequired());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> milestones(List<BusinessProjectMilestone> values)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (values == null) return rows;
        for (BusinessProjectMilestone value : values)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("milestoneId", value.getMilestoneId()); row.put("milestoneName", value.getMilestoneName());
            row.put("planDate", date(value.getPlanDate())); row.put("weight", value.getWeight());
            row.put("status", value.getStatus()); rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> risks(List<BusinessProjectRisk> values)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (values == null) return rows;
        for (BusinessProjectRisk value : values)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("riskId", value.getRiskId()); row.put("riskTitle", value.getRiskTitle());
            row.put("severity", value.getSeverity()); row.put("probability", value.getProbability());
            row.put("ownerName", value.getOwnerName()); row.put("dueDate", date(value.getDueDate()));
            row.put("status", value.getStatus()); row.put("responsePlan", value.getResponsePlan());
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> whitelistedMaps(Object value, String... fields)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (!(value instanceof List)) return rows;
        for (Object item : (List<Object>) value)
        {
            if (!(item instanceof Map)) continue;
            Map<String, Object> source = (Map<String, Object>) item;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (String field : fields) row.put(field, source.get(field));
            rows.add(row);
        }
        return rows;
    }

    private String date(java.util.Date value)
    {
        return value == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(value);
    }
}
