package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessProjectResourceMapper
{
    int unrecognizedDays(@Param("projectId") Long projectId,@Param("from") String from,@Param("to") String to);
    List<Map<String,Object>> personnel(@Param("projectId") Long projectId,@Param("from") String from,@Param("to") String to);
    Map<String,Object> totals(@Param("projectId") Long projectId,@Param("from") String from,@Param("to") String to);
    java.math.BigDecimal externalCost(@Param("projectId") Long projectId,@Param("from") String from,@Param("to") String to);
}
