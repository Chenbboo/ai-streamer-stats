package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;

public interface BusinessProjectMapper
{
    List<BusinessProject> selectProjectList(Map<String, Object> query);
    BusinessProject selectProjectById(Long projectId);
    int insertProject(BusinessProject project);
    int updateProject(BusinessProject project);
    int updateProjectBudget(@Param("projectId") Long projectId, @Param("budgetLimit") java.math.BigDecimal budgetLimit,
        @Param("baseCurrency") String baseCurrency, @Param("userName") String userName,
        @Param("version") Integer version);
    int insertBudgetHistory(Map<String, Object> history);
    List<Map<String, Object>> selectBudgetHistory(Long projectId);
    Map<String, Object> selectActiveExecutionRelation(Long projectId);
    Map<String, Object> selectLiveExecutionSummary(Map<String, Object> relation);
    List<BusinessProjectRoutine> selectLiveStreamerRoutines(Map<String, Object> relation);
    int insertExecutionRelation(@Param("projectId") Long projectId, @Param("effectiveFrom") java.util.Date effectiveFrom,
        @Param("activeKey") String activeKey, @Param("userName") String userName);
    int retireExecutionRelation(@Param("relationId") Long relationId, @Param("effectiveTo") java.util.Date effectiveTo,
        @Param("userName") String userName);

    List<BusinessProjectKpi> selectProjectKpis(Long projectId);
    BusinessProjectKpi selectProjectKpiById(Long kpiId);
    BusinessProjectKpi selectCurrentProjectKpi(@Param("projectId") Long projectId, @Param("kpiCode") String kpiCode);
    Integer selectNextKpiVersion(@Param("projectId") Long projectId, @Param("kpiCode") String kpiCode);
    int retireProjectKpi(@Param("kpiId") Long kpiId, @Param("userName") String userName);
    int insertProjectKpi(BusinessProjectKpi kpi);

    List<BusinessStaffCostPolicy> selectStaffCostPolicies(Long userId);
    Long selectStaffCompanyLeaderUserId(@Param("userId") Long userId,
        @Param("lockForUpdate") boolean lockForUpdate);
    BusinessStaffCostPolicy selectStaffCostPolicyById(Long policyId);
    BusinessStaffCostPolicy selectEffectiveStaffCostPolicy(@Param("userId") Long userId,
        @Param("effectiveDate") java.util.Date effectiveDate);
    Integer selectNextStaffCostVersion(Long userId);
    int countOverlappingStaffCostPolicy(@Param("userId") Long userId,
        @Param("effectiveFrom") java.util.Date effectiveFrom, @Param("effectiveTo") java.util.Date effectiveTo);
    int closeOpenEndedStaffCostPolicy(@Param("userId") Long userId,
        @Param("effectiveFrom") java.util.Date effectiveFrom);
    int insertStaffCostPolicy(BusinessStaffCostPolicy policy);

    List<Map<String, Object>> selectProjectStaffAllocations(Long projectId);
    List<Map<String, Object>> selectOwnerPersonnelCostReadiness(@Param("userId") Long userId,
        @Param("bizDate") java.util.Date bizDate, @Param("viewAll") boolean viewAll);
    BusinessProjectStaffAllocation selectProjectStaffAllocationById(Long allocationId);
    java.math.BigDecimal sumOverlappingAllocationPercent(@Param("userId") Long userId,
        @Param("effectiveFrom") java.util.Date effectiveFrom, @Param("effectiveTo") java.util.Date effectiveTo,
        @Param("excludeAllocationId") Long excludeAllocationId);
    int insertProjectStaffAllocation(BusinessProjectStaffAllocation allocation);
    int updateProjectStaffAllocation(BusinessProjectStaffAllocation allocation);
    int voidProjectStaffAllocation(@Param("projectId") Long projectId, @Param("allocationId") Long allocationId,
        @Param("userName") String userName);
    int countOverlappingProjectAllocation(@Param("projectId") Long projectId, @Param("userId") Long userId,
        @Param("effectiveFrom") java.util.Date effectiveFrom, @Param("effectiveTo") java.util.Date effectiveTo,
        @Param("excludeAllocationId") Long excludeAllocationId);
    int updateProjectStatus(@Param("projectId") Long projectId, @Param("expectedStatus") String expectedStatus,
        @Param("status") String status, @Param("baselineStatus") String baselineStatus,
        @Param("baselineIncrement") boolean baselineIncrement, @Param("userName") String userName,
        @Param("version") Integer version);
    int updateProjectOwner(@Param("projectId") Long projectId, @Param("ownerUserId") Long ownerUserId,
        @Param("ownerName") String ownerName, @Param("userName") String userName,
        @Param("version") Integer version);
    String selectMemberRole(@Param("projectId") Long projectId, @Param("userId") Long userId);
    List<BusinessProjectMember> selectMembers(Long projectId);
    int upsertMember(BusinessProjectMember member);
    int leaveMember(@Param("projectId") Long projectId, @Param("userId") Long userId,
        @Param("userName") String userName);
    int insertOwnerHistory(Map<String, Object> history);
    List<Map<String, Object>> selectOwnerHistory(Long projectId);

    List<BusinessProjectAcceptance> selectAcceptances(Long projectId);
    BusinessProjectAcceptance selectLatestPendingAcceptance(Long projectId);
    Integer selectNextAcceptanceVersion(Long projectId);
    int insertAcceptance(BusinessProjectAcceptance acceptance);
    int reviewAcceptance(@Param("acceptanceId") Long acceptanceId, @Param("reviewStatus") String reviewStatus,
        @Param("reviewedUserId") Long reviewedUserId, @Param("reviewedUserName") String reviewedUserName,
        @Param("reviewComment") String reviewComment, @Param("userName") String userName);

    List<BusinessProjectMilestone> selectMilestones(Long projectId);
    int insertMilestone(BusinessProjectMilestone milestone);
    int updateMilestone(BusinessProjectMilestone milestone);
    int deleteMilestone(@Param("projectId") Long projectId, @Param("milestoneId") Long milestoneId);

    List<BusinessProjectTask> selectTasks(Long projectId);
    BusinessProjectTask selectTaskById(Long taskId);
    int insertTask(BusinessProjectTask task);
    int updateTask(BusinessProjectTask task);
    int deleteTask(@Param("projectId") Long projectId, @Param("taskId") Long taskId);
    int countTaskChildren(@Param("projectId") Long projectId, @Param("taskId") Long taskId);

    List<BusinessProjectRoutine> selectRoutines(@Param("projectId") Long projectId,
        @Param("bizDate") java.util.Date bizDate);
    BusinessProjectRoutine selectRoutineById(Long routineId);
    int insertRoutine(BusinessProjectRoutine routine);
    int updateRoutine(BusinessProjectRoutine routine);
    int voidRoutine(@Param("projectId") Long projectId,@Param("routineId") Long routineId,
        @Param("userName") String userName);
    int upsertRoutineReport(BusinessProjectRoutineReport report);
    BusinessProjectRoutineReport selectRoutineReport(@Param("routineId") Long routineId,
        @Param("bizDate") java.util.Date bizDate);

    List<BusinessProjectRisk> selectRisks(Long projectId);
    int insertRisk(BusinessProjectRisk risk);
    int updateRisk(BusinessProjectRisk risk);
    int deleteRisk(@Param("projectId") Long projectId, @Param("riskId") Long riskId);

    int insertEvent(Map<String, Object> event);
    List<Map<String, Object>> selectEvents(Long projectId);
    Map<String, Object> selectDashboardSummary(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
        @Param("boss") boolean boss);
    List<BusinessProject> selectDashboardProjectPage(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("boss") boolean boss,
        @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<BusinessProject> selectDashboardDecisionPage(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("boss") boolean boss,
        @Param("offset") int offset, @Param("pageSize") int pageSize);
    Map<String, Object> selectBossPendingCounts(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("bizDate") java.util.Date bizDate);
    List<Map<String, Object>> selectBossPendingPage(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("bizDate") java.util.Date bizDate,
        @Param("category") String category, @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<Map<String, Object>> selectMyDueTasks(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("boss") boolean boss);
    List<Map<String, Object>> selectMyWorkTasks(@Param("userId") Long userId,
        @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo);
    List<Map<String, Object>> selectMyWorkRoutines(@Param("userId") Long userId,
        @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo,
        @Param("today") String today);
    List<Map<String, Object>> selectMyEfforts(@Param("userId") Long userId, @Param("bizDate") String bizDate);
    BusinessProjectEffort selectEffortReport(@Param("projectId") Long projectId,
        @Param("userId") Long userId, @Param("bizDate") java.util.Date bizDate);
    java.math.BigDecimal sumUserEffectiveEffortExcludingProject(@Param("userId") Long userId,
        @Param("bizDate") java.util.Date bizDate, @Param("excludeProjectId") Long excludeProjectId);
    int upsertEffortReport(BusinessProjectEffort effort);
    List<Map<String, Object>> selectProjectEffortWeek(@Param("projectId") Long projectId,
        @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo);
    int confirmProjectEffortDay(@Param("projectId") Long projectId, @Param("bizDate") java.util.Date bizDate,
        @Param("userId") Long userId, @Param("userName") String userName);
    int confirmProjectMemberEffort(@Param("projectId") Long projectId, @Param("memberUserId") Long memberUserId,
        @Param("bizDate") java.util.Date bizDate, @Param("userId") Long userId,
        @Param("userName") String userName);
    int returnProjectMemberEffort(@Param("projectId") Long projectId, @Param("memberUserId") Long memberUserId,
        @Param("bizDate") java.util.Date bizDate, @Param("reviewComment") String reviewComment,
        @Param("userName") String userName);
    int countEffectiveProjectAllocation(@Param("projectId") Long projectId, @Param("userId") Long userId,
        @Param("bizDate") java.util.Date bizDate);
    Map<String, Object> selectStaffLeave(@Param("userId") Long userId,
        @Param("leaveDate") java.util.Date leaveDate);
    List<Map<String, Object>> selectProjectMemberLeaves(@Param("projectId") Long projectId,
        @Param("leaveDate") java.util.Date leaveDate);
    int upsertStaffLeave(Map<String, Object> leave);
    int cancelStaffLeave(@Param("userId") Long userId, @Param("leaveDate") java.util.Date leaveDate,
        @Param("userName") String userName);
    List<Long> selectAllocatedProjectIdsForUserDate(@Param("userId") Long userId,
        @Param("bizDate") java.util.Date bizDate);
    List<Map<String, Object>> selectProjectDirectory();
    List<Map<String, Object>> selectUserOptions(@Param("keyword") String keyword);
    List<Map<String, Object>> selectStaffResponsibilities(@Param("staffUserId") Long staffUserId,
        @Param("viewerUserId") Long viewerUserId, @Param("viewAll") boolean viewAll,
        @Param("boss") boolean boss);
    Map<String, Object> selectActiveUserById(Long userId);
    Map<String, Object> selectCostEligibleUserById(Long userId);
    String selectStaffCountryRegion(Long userId);
    Map<String, Object> selectCompanyById(Long deptId);
    Long selectRoleIdByKey(String roleKey);
    int countUserRoleByKey(@Param("userId") Long userId, @Param("roleKey") String roleKey);
    String selectUserRoleNames(Long userId);
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
