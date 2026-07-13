package com.ruoyi.live.service;

import java.util.List;
import java.util.Map;

public interface ILiveStatsService
{
    public Map<String, Object> getWeeklyStats(String beginDate, String endDate, Long streamerId);

    public List<Map<String, Object>> getStreamerCardDetail(Long streamerId, String beginDate, String endDate);

    public List<Map<String, Object>> getHighValueUsers(Long streamerId, String beginDate, String endDate);

    public List<Map<String, Object>> getNewTippers(Long streamerId, String beginDate, String endDate);

    public List<Map<String, Object>> getWeijiStats(String statDate);

    public List<Map<String, Object>> getWeijiMonthStats(String beginDate, String endDate);

    public List<Map<String, Object>> getWeijiDetail(Long streamerId, String beginDate, String endDate);

    public List<Map<String, Object>> getAdviceData(String beginDate, String endDate);

    public List<Map<String, Object>> getRecentChatRecords(Long streamerId, int limit);

    public List<Map<String, Object>> getRecentTipRecords(Long streamerId, int limit);

    public List<Map<String, Object>> getChatContent(Long streamerId, int limit);
}
