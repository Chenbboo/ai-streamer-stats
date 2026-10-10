package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessHistoricalAllocationMapper {
    List<Map<String,Object>> selectMemberships(@Param("userId") Long userId,@Param("projectId") Long projectId,
        @Param("dateFrom") String dateFrom,@Param("dateTo") String dateTo);
    int voidVersion(@Param("allocationId") Long allocationId,@Param("version") Integer version,@Param("operator") String operator);
    int restoreStart(@Param("projectId") Long projectId,@Param("version") Integer version,
        @Param("startDate") String startDate,@Param("operator") String operator);
    int countFrozenResults(@Param("projectId") Long projectId,@Param("dateFrom") String dateFrom,@Param("dateTo") String dateTo);
}
