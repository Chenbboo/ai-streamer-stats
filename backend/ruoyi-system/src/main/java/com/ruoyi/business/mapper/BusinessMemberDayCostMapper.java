package com.ruoyi.business.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
public interface BusinessMemberDayCostMapper {
    List<Long> selectOpenProjects();
    List<Map<String,Object>> selectCosts(@Param("projectId") Long projectId);
    List<Map<String,Object>> selectDayCosts(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int deleteDay(@Param("projectId") Long projectId,@Param("bizDate") String bizDate);
    int insertCost(Map<String,Object> row);
    int countPending(@Param("projectId") Long projectId);
    List<String> selectLegacyResultDates(@Param("projectId") Long projectId);
    int archiveMembership(@Param("projectId") Long projectId,@Param("userId") Long userId);
    List<Map<String,Object>> selectPastMemberships(@Param("projectId") Long projectId);
}
