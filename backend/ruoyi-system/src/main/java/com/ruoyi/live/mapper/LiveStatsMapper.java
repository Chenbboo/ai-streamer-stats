package com.ruoyi.live.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface LiveStatsMapper
{
    public List<Map<String, Object>> selectStreamerCards(@Param("beginDate") String beginDate,
                                                         @Param("endDate") String endDate,
                                                         @Param("streamerId") Long streamerId);

    public List<Map<String, Object>> selectPreviousTotals(@Param("beginDate") String beginDate,
                                                          @Param("endDate") String endDate,
                                                          @Param("streamerId") Long streamerId);

    public List<Map<String, Object>> selectTrend(@Param("beginDate") String beginDate,
                                                 @Param("endDate") String endDate,
                                                 @Param("streamerId") Long streamerId);

    public List<Map<String, Object>> selectCustomerCards(@Param("beginDate") String beginDate,
                                                         @Param("endDate") String endDate,
                                                         @Param("streamerId") Long streamerId);

    public List<Map<String, Object>> selectStreamerCardDetail(@Param("streamerId") Long streamerId,
                                                               @Param("beginDate") String beginDate,
                                                               @Param("endDate") String endDate);

    public List<Map<String, Object>> selectHighValueUsers(@Param("streamerId") Long streamerId,
                                                           @Param("beginDate") String beginDate,
                                                           @Param("endDate") String endDate);

    public List<Map<String, Object>> selectNewTippers(@Param("streamerId") Long streamerId,
                                                       @Param("beginDate") String beginDate,
                                                       @Param("endDate") String endDate);

    public List<Map<String, Object>> selectWeijiStats(@Param("statDate") String statDate);

    public List<Map<String, Object>> selectWeijiMonthStats(@Param("beginDate") String beginDate,
                                                            @Param("endDate") String endDate);

    public List<Map<String, Object>> selectWeijiDetail(@Param("streamerId") Long streamerId,
                                                        @Param("beginDate") String beginDate,
                                                        @Param("endDate") String endDate);

    public List<Map<String, Object>> selectAdviceData(@Param("beginDate") String beginDate,
                                                       @Param("endDate") String endDate);

    public List<Map<String, Object>> selectCustomerMaintenanceMatrix(@Param("beginDate") String beginDate,
                                                                       @Param("endDate") String endDate,
                                                                       @Param("streamerId") Long streamerId);

    public List<Map<String, Object>> selectRecentChatRecords(@Param("streamerId") Long streamerId, @Param("limit") int limit);

    public List<Map<String, Object>> selectRecentTipRecords(@Param("streamerId") Long streamerId, @Param("limit") int limit);

    public List<Map<String, Object>> selectChatContent(@Param("streamerId") Long streamerId, @Param("limit") int limit);
}
