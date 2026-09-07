package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Versioned resource plans and actual work. Legacy percentage records are deliberately separate. */
public interface BusinessProjectWorkMapper
{
    List<Map<String,Object>> selectCalendars();
    List<Map<String,Object>> selectUnitPolicies();
    Map<String,Object> selectCalendar(Long id);
    Map<String,Object> selectUnitPolicy(Long id);
    int insertCalendar(Map<String,Object> row);
    int insertUnitPolicy(Map<String,Object> row);
    Map<String,Object> selectTemplate(String templateVersion);
    int insertBaseline(Map<String,Object> row);
    List<Map<String,Object>> selectBaselines(Long projectId);
    List<Map<String,Object>> selectPlanChanges(Long projectId);
    Map<String,Object> selectPlanChange(Long changeId);
    int insertPlanChange(Map<String,Object> row);
    int reviewPlanChange(Map<String,Object> row);
    int applyPlanChange(Map<String,Object> row);
    int countAssignmentsOutside(Map<String,Object> row);
    int touchProject(Map<String,Object> row);
    int insertForecast(Map<String,Object> row);
    Map<String,Object> selectForecast(Long projectId);
    List<Map<String,Object>> selectMembers(Long projectId);
    int countMembership(@Param("projectId") Long projectId,@Param("userId") Long userId,@Param("bizDate") String bizDate);
    int insertAssignment(Map<String,Object> row);
    Map<String,Object> selectAssignment(Long assignmentId);
    int retireAssignment(Map<String,Object> row);
    int insertAllocationDay(Map<String,Object> row);
    List<Map<String,Object>> selectAssignments(Long projectId);
    int sumPlannedMinutes(@Param("userId") Long userId,@Param("bizDate") String bizDate);
    int countOverlappingAssignments(Map<String,Object> row);
    int countPlannedWork(@Param("projectId") Long projectId,@Param("userId") Long userId,@Param("bizDate") String bizDate);
    List<Map<String,Object>> selectEntries(Map<String,Object> query);
    Map<String,Object> selectEntry(Long entryId);
    Map<String,Object> selectEntryForUpdate(Long entryId);
    Map<String,Object> selectEntryBySource(@Param("projectId") Long projectId,@Param("sourceKey") String sourceKey);
    int insertEntry(Map<String,Object> row);
    int updateDraftEntry(Map<String,Object> row);
    int insertAudit(Map<String,Object> row);
    List<Map<String,Object>> selectAudit(Long entryId);
    int transitionEntry(Map<String,Object> row);
    int supersedeEntry(@Param("entryId") Long entryId,@Param("version") Integer version,@Param("userName") String userName);
    int countOpenCorrections(Long logicalEntryId);
    int ensurePersonDayLock(@Param("userId") Long userId,@Param("bizDate") String bizDate);
    Long lockPersonDay(@Param("userId") Long userId,@Param("bizDate") String bizDate);
    int sumReservedMinutes(@Param("userId") Long userId,@Param("bizDate") String bizDate,@Param("logicalEntryId") Long logicalEntryId);
    int countPendingWork(Long projectId);
    int countPendingCosts(Long projectId);
    int insertEvent(Map<String,Object> row);
    List<Map<String,Object>> selectPendingEvents();
    List<Map<String,Object>> selectPendingProjectEvents(Long projectId);
    Map<String,Object> selectEvent(Long eventId);
    Map<String,Object> selectEventForUpdate(Long eventId);
    int finishEvent(Map<String,Object> row);
    int recordEventFailure(Map<String,Object> row);
    List<Map<String,Object>> selectApplicableRates(@Param("userId") Long userId,@Param("bizDate") String bizDate);
    int upsertWorkCost(Map<String,Object> row);
    List<Map<String,Object>> selectWorkCosts(@Param("projectId") Long projectId,@Param("bizDate") java.util.Date bizDate);
    List<Map<String,Object>> selectPersonnelCostOverview(Map<String,Object> query);
    List<String> selectConfirmedWorkDates(Long projectId);
}
