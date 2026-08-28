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
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;

public interface IBusinessProjectService
{
    List<BusinessProject> listProjects(Map<String, Object> query, Long userId, boolean viewAll, boolean boss);
    BusinessProject getProject(Long projectId, Long userId, boolean viewAll, boolean boss);
    BusinessProject createProject(BusinessProject project, Long userId, String userName);
    BusinessProject createApprovedProject(BusinessProjectProposal proposal, Long reviewerUserId, String reviewerUserName);
    BusinessProject updateProject(BusinessProject project, Long userId, String userName, boolean boss);
    Map<String, Object> operatingConfig(Long projectId, Long userId, boolean viewAll, boolean boss);
    BusinessProject updateBudget(Long projectId, java.math.BigDecimal budgetLimit, String currency, String reason,
        Long userId, String userName, boolean boss);
    BusinessProjectKpi saveKpi(BusinessProjectKpi kpi, Long userId, String userName, boolean boss);
    void retireKpi(Long projectId, Long kpiId, Long userId, String userName, boolean boss);
    List<BusinessStaffCostPolicy> staffCostPolicies(Long staffUserId, Long userId, boolean boss);
    BusinessStaffCostPolicy saveStaffCostPolicy(BusinessStaffCostPolicy policy,
        Long userId, String userName, boolean boss);
    List<BusinessStaffCostPolicy> saveStaffCostPolicies(List<BusinessStaffCostPolicy> policies,
        Long userId, String userName, boolean boss);
    void deleteStaffCostPolicy(Long policyId, Long userId, String userName, boolean boss);
    void voidStaffCostPolicy(Long policyId, String reason, Long userId, String userName, boolean boss);
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
    BusinessProjectProgressReport submitProjectProgressReport(BusinessProjectProgressReport report,
        Long userId, String userName, boolean viewAll);
    void deleteTask(Long projectId, Long taskId, Long userId, boolean boss);
    BusinessProjectRoutine saveRoutine(BusinessProjectRoutine routine, Long userId, String userName, boolean boss);
    void removeRoutine(Long projectId, Long routineId, Long userId, String userName, boolean boss);
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
    Map<String, Object> markMemberLeave(Long projectId, Long memberUserId, java.util.Date leaveDate, String reason,
        Long userId, String userName, boolean boss);
    Map<String, Object> requestMemberLeave(Long projectId, Long memberUserId, java.util.Date startDate,
        java.util.Date endDate, String leaveType, String reason, String attachmentUrls,
        Long userId, String userName, boolean boss);
    Map<String, Object> reviewMemberLeaveRequest(Long requestId, String decision, String comment,
        Long userId, String userName, boolean boss);
    void cancelMemberLeaveRequest(Long requestId, String reason, Long userId, String userName, boolean boss);
    void cancelMemberLeave(Long projectId, Long memberUserId, java.util.Date leaveDate,
        Long userId, String userName, boolean boss);
    List<Map<String, Object>> projectDirectory(Long userId, boolean viewAll, boolean boss);
    List<Map<String, Object>> userOptions(String keyword);
}
