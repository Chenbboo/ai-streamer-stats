package com.ruoyi.business.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
public interface BusinessMemberDayCostMapper {
    @org.apache.ibatis.annotations.Select("select effective_from effectiveFrom,effective_to effectiveTo from biz_project_cost_pause where project_id=#{projectId}")
    List<Map<String,Object>> selectCostPauses(Long projectId);
    Map<String,Object> selectStaffMetadata(@Param("userId") Long userId,@Param("policyId") Long policyId);
    List<Long> selectOpenProjects();
    List<Map<String,Object>> selectRolePeriods(@Param("projectId") Long projectId);
    int saveRolePeriod(@Param("projectId") Long projectId,@Param("userId") Long userId,@Param("effectiveFrom") Date effectiveFrom,@Param("role") String role,@Param("operator") String operator);
    List<Map<String,Object>> selectCosts(@Param("projectId") Long projectId);
    List<Map<String,Object>> selectDayCosts(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int deleteDay(@Param("projectId") Long projectId,@Param("bizDate") String bizDate);
    int insertCost(Map<String,Object> row);
    int countPending(@Param("projectId") Long projectId);
    List<String> selectLegacyResultDates(@Param("projectId") Long projectId);
    int archiveMembership(@Param("projectId") Long projectId,@Param("userId") Long userId);
    List<Map<String,Object>> selectPastMemberships(@Param("projectId") Long projectId);
}
