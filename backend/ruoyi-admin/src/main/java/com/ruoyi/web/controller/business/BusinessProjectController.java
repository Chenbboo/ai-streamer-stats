package com.ruoyi.web.controller.business;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectStageAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectTaskReport;
import com.ruoyi.business.domain.BusinessProjectProgressReport;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.DateUtils;

/**
 * 公司经营项目中心。
 *
 * 项目数据的可见范围由服务层按“老板/项目成员”二次校验，不能仅依赖菜单权限。
 */
@RestController
@RequestMapping("/business")
public class BusinessProjectController extends BaseController
{
    @Autowired
    private IBusinessProjectService projectService;

    @PreAuthorize("@ss.hasPermi('business:project:list')")
    @GetMapping("/project/list")
    public TableDataInfo list(@RequestParam Map<String, Object> query)
    {
        startPage();
        List<BusinessProject> list = projectService.listProjects(query, currentUserId(), isAdministrator(), isBoss());
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('business:project:list')")
    @GetMapping("/project/{projectId}")
    public AjaxResult detail(@PathVariable Long projectId)
    {
        return success(projectService.getProject(projectId, currentUserId(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:add,business:project:member,business:project:task')")
    @GetMapping("/project/user-options")
    public AjaxResult userOptions(@RequestParam(required = false) String keyword)
    {
        return success(projectService.userOptions(keyword));
    }

    @PreAuthorize("@ss.hasPermi('business:project:edit')")
    @Log(title = "经营项目", businessType = BusinessType.UPDATE)
    @PutMapping("/project")
    public AjaxResult edit(@RequestBody BusinessProject project)
    {
        return success(projectService.updateProject(project, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:list')")
    @GetMapping("/project/{projectId}/operating-config")
    public AjaxResult operatingConfig(@PathVariable Long projectId)
    {
        return success(projectService.operatingConfig(projectId, currentUserId(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:manage')")
    @Log(title = "项目预算", businessType = BusinessType.UPDATE)
    @PutMapping("/project/{projectId}/budget")
    public AjaxResult budget(@PathVariable Long projectId, @RequestBody Map<String, Object> body)
    {
        Object amount = body.get("budgetLimit");
        java.math.BigDecimal budgetLimit = amount == null ? null : new java.math.BigDecimal(String.valueOf(amount));
        return success(projectService.updateBudget(projectId, budgetLimit, text(body, "currency"),
            text(body, "reason"), currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:manage,business:kpi:manage')")
    @Log(title = "项目KPI", businessType = BusinessType.INSERT)
    @PostMapping("/project/kpi")
    public AjaxResult saveKpi(@RequestBody BusinessProjectKpi kpi)
    {
        return success(projectService.saveKpi(kpi, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:manage,business:kpi:manage')")
    @Log(title = "项目KPI", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/kpi/{kpiId}")
    public AjaxResult retireKpi(@PathVariable Long projectId, @PathVariable Long kpiId)
    {
        projectService.retireKpi(projectId, kpiId, currentUserId(), currentUserName(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:staff:list')")
    @GetMapping("/staff/{staffUserId}/cost-policies")
    public AjaxResult staffCostPolicies(@PathVariable Long staffUserId)
    {
        return success(projectService.staffCostPolicies(staffUserId, currentUserId(), canManageStaffCost()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:staff:manage,business:staff:cost')")
    @Log(title = "人员内部核算成本", businessType = BusinessType.INSERT)
    @PostMapping("/staff/cost-policy")
    public AjaxResult saveStaffCostPolicy(@RequestBody BusinessStaffCostPolicy policy)
    {
        return success(projectService.saveStaffCostPolicy(policy, currentUserId(), currentUserName(), canManageStaffCost()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:staff:manage,business:staff:cost')")
    @Log(title = "批量人员内部核算成本", businessType = BusinessType.INSERT)
    @PostMapping("/staff/cost-policies")
    public AjaxResult saveStaffCostPolicies(@RequestBody List<BusinessStaffCostPolicy> policies)
    {
        return success(projectService.saveStaffCostPolicies(policies, currentUserId(), currentUserName(), canManageStaffCost()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:staff:manage,business:staff:cost')")
    @Log(title = "人员内部核算成本", businessType = BusinessType.DELETE)
    @DeleteMapping("/staff/cost-policy/{policyId}")
    public AjaxResult deleteStaffCostPolicy(@PathVariable Long policyId)
    {
        projectService.deleteStaffCostPolicy(policyId, currentUserId(), currentUserName(), canManageStaffCost());
        return success();
    }

    @PreAuthorize("@ss.hasAnyPermi('business:staff:manage,business:staff:cost')")
    @Log(title = "人员内部核算成本", businessType = BusinessType.UPDATE)
    @PutMapping("/staff/cost-policy/{policyId}/void")
    public AjaxResult voidStaffCostPolicy(@PathVariable Long policyId, @RequestBody Map<String, Object> body)
    {
        projectService.voidStaffCostPolicy(policyId, text(body, "reason"),
            currentUserId(), currentUserName(), canManageStaffCost());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "项目人员成本分摊", businessType = BusinessType.INSERT)
    @PostMapping("/project/staff-allocation")
    public AjaxResult saveStaffAllocation(@RequestBody BusinessProjectStaffAllocation allocation)
    {
        return success(projectService.saveStaffAllocation(allocation, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "项目人员成本分摊", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/staff-allocation/{allocationId}")
    public AjaxResult removeStaffAllocation(@PathVariable Long projectId, @PathVariable Long allocationId)
    {
        projectService.removeStaffAllocation(projectId, allocationId, currentUserId(), currentUserName(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:manage')")
    @Log(title = "项目负责人", businessType = BusinessType.UPDATE)
    @PutMapping("/project/{projectId}/owner")
    public AjaxResult changeOwner(@PathVariable Long projectId, @RequestBody Map<String, Object> body)
    {
        Long ownerUserId = requiredLong(body, "ownerUserId");
        return success(projectService.changeOwner(projectId, ownerUserId, text(body, "reason"),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:submit,business:project:manage')")
    @Log(title = "项目验收资料", businessType = BusinessType.INSERT)
    @PostMapping("/project/{projectId}/acceptance")
    public AjaxResult submitAcceptance(@PathVariable Long projectId, @RequestBody BusinessProjectAcceptance acceptance)
    {
        return success(projectService.submitAcceptance(projectId, acceptance,
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:manage')")
    @Log(title = "项目验收评审", businessType = BusinessType.UPDATE)
    @PutMapping("/project/{projectId}/acceptance/review")
    public AjaxResult reviewAcceptance(@PathVariable Long projectId, @RequestBody Map<String, Object> body)
    {
        return success(projectService.reviewAcceptance(projectId, text(body, "decision"), text(body, "comment"),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:submit,business:project:manage')")
    @Log(title = "项目阶段验收资料", businessType = BusinessType.INSERT)
    @PostMapping("/project/{projectId}/stage-acceptance")
    public AjaxResult submitStageAcceptance(@PathVariable Long projectId,
        @RequestBody BusinessProjectStageAcceptance acceptance)
    {
        return success(projectService.submitStageAcceptance(projectId, acceptance,
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:manage')")
    @Log(title = "项目阶段验收评审", businessType = BusinessType.UPDATE)
    @PutMapping("/project/{projectId}/stage-acceptance/{milestoneId}/review")
    public AjaxResult reviewStageAcceptance(@PathVariable Long projectId, @PathVariable Long milestoneId,
        @RequestBody Map<String, Object> body)
    {
        return success(projectService.reviewStageAcceptance(projectId, milestoneId,
            text(body, "decision"), text(body, "comment"), currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:submit,business:project:manage')")
    @Log(title = "项目状态", businessType = BusinessType.UPDATE)
    @PostMapping("/project/{projectId}/transition")
    public AjaxResult transition(@PathVariable Long projectId, @RequestBody Map<String, Object> body)
    {
        return success(projectService.transition(projectId, text(body, "action"), text(body, "comment"),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:member')")
    @Log(title = "项目成员", businessType = BusinessType.INSERT)
    @PostMapping("/project/member")
    public AjaxResult saveMember(@RequestBody BusinessProjectMember member)
    {
        return success(projectService.saveMember(member, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:member')")
    @Log(title = "项目成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/member/{memberUserId}")
    public AjaxResult removeMember(@PathVariable Long projectId, @PathVariable Long memberUserId,
        @RequestParam(defaultValue = "false") boolean retainTodayCost)
    {
        projectService.removeMember(projectId, memberUserId, retainTodayCost,
            currentUserId(), currentUserName(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目里程碑", businessType = BusinessType.INSERT)
    @PostMapping("/project/milestone")
    public AjaxResult saveMilestone(@RequestBody BusinessProjectMilestone milestone)
    {
        return success(projectService.saveMilestone(milestone, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目里程碑", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/milestone/{milestoneId}")
    public AjaxResult deleteMilestone(@PathVariable Long projectId, @PathVariable Long milestoneId)
    {
        projectService.deleteMilestone(projectId, milestoneId, currentUserId(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目任务", businessType = BusinessType.INSERT)
    @PostMapping("/project/task")
    public AjaxResult saveTask(@RequestBody BusinessProjectTask task)
    {
        return success(projectService.saveTask(task, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title = "一次性任务完成填报", businessType = BusinessType.INSERT)
    @PostMapping("/project/task-report")
    public AjaxResult submitTaskReport(@RequestBody BusinessProjectTaskReport report)
    {
        return success(projectService.submitTaskReport(report, currentUserId(), currentUserName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title = "项目完成进度填报", businessType = BusinessType.INSERT)
    @PostMapping("/project/progress-report")
    public AjaxResult submitProjectProgressReport(@RequestBody BusinessProjectProgressReport report)
    {
        return success(projectService.submitProjectProgressReport(report, currentUserId(), currentUserName(),
            isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/task/{taskId}")
    public AjaxResult deleteTask(@PathVariable Long projectId, @PathVariable Long taskId)
    {
        projectService.deleteTask(projectId, taskId, currentUserId(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目持续工作", businessType = BusinessType.INSERT)
    @PostMapping("/project/routine")
    public AjaxResult saveRoutine(@RequestBody BusinessProjectRoutine routine)
    {
        return success(projectService.saveRoutine(routine, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目持续工作", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/routine/{routineId}")
    public AjaxResult removeRoutine(@PathVariable Long projectId, @PathVariable Long routineId)
    {
        projectService.removeRoutine(projectId, routineId, currentUserId(), currentUserName(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title = "持续工作完成填报", businessType = BusinessType.INSERT)
    @PostMapping("/project/routine-report")
    public AjaxResult submitRoutineReport(@RequestBody BusinessProjectRoutineReport report)
    {
        return success(projectService.submitRoutineReport(report, currentUserId(), currentUserName(), isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目风险", businessType = BusinessType.INSERT)
    @PostMapping("/project/risk")
    public AjaxResult saveRisk(@RequestBody BusinessProjectRisk risk)
    {
        return success(projectService.saveRisk(risk, currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:task')")
    @Log(title = "项目风险", businessType = BusinessType.DELETE)
    @DeleteMapping("/project/{projectId}/risk/{riskId}")
    public AjaxResult deleteRisk(@PathVariable Long projectId, @PathVariable Long riskId)
    {
        projectService.deleteRisk(projectId, riskId, currentUserId(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/boss/dashboard")
    public AjaxResult bossDashboard(@RequestParam Map<String, Object> query)
    {
        return success(projectService.dashboard(query, currentUserId(), isAdministrator(), true));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/boss/pending")
    public AjaxResult bossPending(@RequestParam Map<String, Object> query)
    {
        return success(projectService.bossPending(query, currentUserId(), isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/boss/project-directory")
    public AjaxResult bossProjectDirectory()
    {
        return success(projectService.projectDirectory(currentUserId(), isAdministrator(), true));
    }

    @PreAuthorize("@ss.hasPermi('business:project:list')")
    @GetMapping("/my/dashboard")
    public AjaxResult myDashboard()
    {
        return success(projectService.dashboard(currentUserId(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:owner:view')")
    @GetMapping("/owner/dashboard")
    public AjaxResult ownerDashboard(@RequestParam(required = false) Long projectId)
    {
        return success(projectService.ownerWorkbench(projectId, currentUserId(), isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:work:view')")
    @GetMapping("/work/dashboard")
    public AjaxResult workDashboard(@RequestParam(required = false) String period,
        @RequestParam(required = false) String anchorDate)
    {
        return success(projectService.workDashboard(period, anchorDate, currentUserId()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title = "项目实际投入", businessType = BusinessType.INSERT)
    @PostMapping("/work/effort")
    public AjaxResult saveMyEffort(@RequestBody BusinessProjectEffort effort)
    {
        return success(projectService.saveMyEffort(effort, currentUserId(), currentUserName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "项目投入周确认", businessType = BusinessType.UPDATE)
    @PostMapping("/owner/{projectId}/effort-week/confirm")
    public AjaxResult confirmProjectEffortWeek(@PathVariable Long projectId,
        @RequestParam(required = false) String anchorDate)
    {
        return success(projectService.confirmProjectEffortWeek(projectId, anchorDate,
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "确认成员当日投入", businessType = BusinessType.UPDATE)
    @PostMapping("/owner/{projectId}/member/{memberUserId}/effort/confirm")
    public AjaxResult confirmMemberEffort(@PathVariable Long projectId, @PathVariable Long memberUserId,
        @RequestBody Map<String, Object> body)
    {
        return success(projectService.confirmMemberEffort(projectId, memberUserId,
            DateUtils.parseDate(body == null ? null : body.get("bizDate")),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "退回成员当日投入", businessType = BusinessType.UPDATE)
    @PostMapping("/owner/{projectId}/member/{memberUserId}/effort/return")
    public AjaxResult returnMemberEffort(@PathVariable Long projectId, @PathVariable Long memberUserId,
        @RequestBody Map<String, Object> body)
    {
        return success(projectService.returnMemberEffort(projectId, memberUserId,
            DateUtils.parseDate(body == null ? null : body.get("bizDate")),
            body == null || body.get("reviewComment") == null ? null : String.valueOf(body.get("reviewComment")),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "成员请假申请", businessType = BusinessType.INSERT)
    @PostMapping("/owner/{projectId}/member/{memberUserId}/leave")
    public AjaxResult markMemberLeave(@PathVariable Long projectId, @PathVariable Long memberUserId,
        @RequestBody Map<String, Object> body)
    {
        Object start = body == null ? null : (body.get("startDate") == null ? body.get("leaveDate") : body.get("startDate"));
        Object end = body == null ? null : (body.get("endDate") == null ? start : body.get("endDate"));
        return success(projectService.requestMemberLeave(projectId, memberUserId,
            DateUtils.parseDate(start), DateUtils.parseDate(end), text(body, "leaveType"), text(body, "reason"),
            text(body, "attachmentUrls"),
            currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @Log(title = "审批成员请假", businessType = BusinessType.UPDATE)
    @PutMapping("/boss/leave-request/{requestId}/review")
    public AjaxResult reviewMemberLeave(@PathVariable Long requestId, @RequestBody Map<String, Object> body)
    {
        return success(projectService.reviewMemberLeaveRequest(requestId, text(body, "decision"),
            text(body, "comment"), currentUserId(), currentUserName(), isBoss()));
    }

    @PreAuthorize("@ss.hasAnyPermi('business:project:allocation,business:boss:view')")
    @Log(title = "取消成员请假申请", businessType = BusinessType.UPDATE)
    @PostMapping("/leave-request/{requestId}/cancel")
    public AjaxResult cancelMemberLeaveRequest(@PathVariable Long requestId, @RequestBody Map<String, Object> body)
    {
        projectService.cancelMemberLeaveRequest(requestId, text(body, "reason"),
            currentUserId(), currentUserName(), isBoss());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title = "取消成员请假", businessType = BusinessType.DELETE)
    @DeleteMapping("/owner/{projectId}/member/{memberUserId}/leave")
    public AjaxResult cancelMemberLeave(@PathVariable Long projectId, @PathVariable Long memberUserId,
        @RequestParam(required = false) String leaveDate)
    {
        projectService.cancelMemberLeave(projectId, memberUserId, DateUtils.parseDate(leaveDate),
            currentUserId(), currentUserName(), isBoss());
        return success();
    }

    private Long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    private String currentUserName()
    {
        return SecurityUtils.getUsername();
    }

    private boolean isBoss()
    {
        return SecurityUtils.isAdmin() || SecurityUtils.hasPermi("business:boss:view");
    }

    private boolean isAdministrator()
    {
        return SecurityUtils.isAdmin();
    }

    private boolean canManageStaffCost()
    {
        return SecurityUtils.isAdmin() || SecurityUtils.hasPermi("business:staff:manage")
            || SecurityUtils.hasPermi("business:staff:cost");
    }

    private Long requiredLong(Map<String, Object> body, String key)
    {
        Object value = body.get(key);
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String text(Map<String, Object> body, String key)
    {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
