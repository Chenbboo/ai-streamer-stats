package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProposal;
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
import com.ruoyi.business.domain.BusinessProjectRoutineDailyTarget;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;

public interface IBusinessProjectService
{
    List<Map<String,Object>> staffCostOptions(Long userId,boolean staffCostManager);
    List<BusinessProject> listProjects(Map<String, Object> query, Long userId, boolean viewAll, boolean boss);
    BusinessProject getProject(Long projectId, Long userId, boolean viewAll, boolean boss);
    Map<String, Object> settlementStatus(Long projectId, Long userId, boolean viewAll, boolean boss);
    Map<String, Object> closeAccounting(Long projectId, Integer version, String reason,
        Long userId, String userName, boolean boss);
    BusinessProject createProject(BusinessProject project, Long userId, String userName);
    List<BusinessProject> projectHierarchy(Map<String, Object> query, Long userId, boolean viewAll, boolean boss);
    List<BusinessProject> projectChildren(Long parentId, Long userId, boolean viewAll, boolean boss);
    List<Map<String, Object>> projectCompanyOptions();
    void validateSubprojectParent(Long parentId, Long sponsorId, Long applicantId);
    void deleteProject(Long projectId, Long userId, String userName, boolean boss);
    BusinessProject createApprovedProject(BusinessProjectProposal proposal, Long reviewerUserId, String reviewerUserName);
    BusinessProject updateProject(BusinessProject project, Long userId, String userName, boolean boss);
    Map<String, Object> operatingConfig(Long projectId, Long userId, boolean viewAll, boolean boss);
    BusinessProject updateBudget(Long projectId, java.math.BigDecimal budgetLimit, String currency, String reason,
        Long userId, String userName, boolean boss);
    BusinessProjectKpi saveKpi(BusinessProjectKpi kpi, Long userId, String userName, boolean boss);
    void retireKpi(Long projectId, Long kpiId, Long userId, String userName, boolean boss);
    List<BusinessStaffCostPolicy> staffCostPolicies(Long staffUserId, Long userId, boolean staffCostManager);
    BusinessStaffCostPolicy saveStaffCostPolicy(BusinessStaffCostPolicy policy,
        Long userId, String userName, boolean staffCostManager);
    List<BusinessStaffCostPolicy> saveStaffCostPolicies(List<BusinessStaffCostPolicy> policies,
        Long userId, String userName, boolean staffCostManager);
    void deleteStaffCostPolicy(Long policyId, Long userId, String userName, boolean staffCostManager);
    void voidStaffCostPolicy(Long policyId, String reason, Long userId, String userName, boolean staffCostManager);
    BusinessProjectStaffAllocation saveStaffAllocation(BusinessProjectStaffAllocation allocation,
        Long userId, String userName, boolean boss);
    void removeStaffAllocation(Long projectId, Long allocationId, Long userId, String userName, boolean boss);
    BusinessProject changeOwner(Long projectId, Long newOwnerUserId, String reason,
        Long userId, String userName, boolean boss);
    BusinessProject submitAcceptance(Long projectId, BusinessProjectAcceptance acceptance,
        Long userId, String userName, boolean boss);
    BusinessProject reviewAcceptance(Long projectId, String decision, String comment,
        Long userId, String userName, boolean boss);
    BusinessProject submitStageAcceptance(Long projectId, BusinessProjectStageAcceptance acceptance,
        Long userId, String userName, boolean boss);
    BusinessProject reviewStageAcceptance(Long projectId, Long milestoneId, String decision, String comment,
        Long userId, String userName, boolean boss);
    BusinessProject transition(Long projectId, String action, String comment,
        Long userId, String userName, boolean boss);
    BusinessProjectMember saveMember(BusinessProjectMember member, Long userId, String userName, boolean boss);
    void removeMember(Long projectId, Long memberUserId, Long userId, String userName, boolean boss);
    void removeMember(Long projectId, Long memberUserId, boolean retainTodayCost,
        Long userId, String userName, boolean boss);
    BusinessProjectMilestone saveMilestone(BusinessProjectMilestone milestone,
        Long userId, String userName, boolean boss);
    void deleteMilestone(Long projectId, Long milestoneId, Long userId, boolean boss);
    BusinessProjectTask saveTask(BusinessProjectTask task, Long userId, String userName, boolean boss);
    BusinessProjectTaskReport submitTaskReport(BusinessProjectTaskReport report,
        Long userId, String userName);
    Map<String,Object> progressWorkspace(Long projectId, Long userId, boolean viewAll, boolean boss);
    void setProgressWeight(Long parentId, Long projectId, java.math.BigDecimal weight, Long userId, String userName);
    BusinessProjectProgressReport submitProjectProgressReport(BusinessProjectProgressReport report,
        Long userId, String userName, boolean viewAll);
    void deleteTask(Long projectId, Long taskId, Long userId, String userName, boolean boss);
    BusinessProjectTask enableTask(Long projectId, Long taskId, Long userId, String userName, boolean boss);
    BusinessProjectRoutine saveRoutine(BusinessProjectRoutine routine, Long userId, String userName, boolean boss);
    void removeRoutine(Long projectId, Long routineId, Long userId, String userName, boolean boss);
    BusinessProjectRoutine enableRoutine(Long projectId, Long routineId, java.util.Date endDate,
        Long userId, String userName, boolean boss);
    BusinessProjectRoutineDailyTarget saveRoutineDailyTarget(BusinessProjectRoutineDailyTarget target,
        Long userId, String userName, boolean boss);
    BusinessProjectRoutineReport submitRoutineReport(BusinessProjectRoutineReport report,
        Long userId, String userName, boolean viewAll);
    BusinessProjectRisk saveRisk(BusinessProjectRisk risk, Long userId, String userName, boolean boss);
    void deleteRisk(Long projectId, Long riskId, Long userId, boolean boss);
    Map<String, Object> dashboard(Long userId, boolean viewAll, boolean boss);
    Map<String, Object> dashboard(Map<String, Object> query, Long userId, boolean viewAll, boolean boss);
    Map<String, Object> bossPending(Map<String, Object> query, Long userId, boolean viewAll);
    Map<String, Object> ownerWorkbench(Long projectId, Long userId, boolean viewAll);
    Map<String, Object> workDashboard(String period, String anchorDate, Long userId);
    BusinessProjectEffort saveMyEffort(BusinessProjectEffort effort, Long userId, String userName);
    Map<String, Object> confirmProjectEffortWeek(Long projectId, String anchorDate,
        Long userId, String userName, boolean boss);
    BusinessProjectEffort confirmMemberEffort(Long projectId, Long memberUserId, java.util.Date bizDate,
        Long userId, String userName, boolean boss);
    BusinessProjectEffort returnMemberEffort(Long projectId, Long memberUserId, java.util.Date bizDate,
        String reviewComment, Long userId, String userName, boolean boss);
    List<Map<String, Object>> projectDirectory(Long userId, boolean viewAll, boolean boss);
    List<Map<String, Object>> userOptions(String keyword);
}
