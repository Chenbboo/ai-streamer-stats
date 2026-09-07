package com.ruoyi.business.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-only external evidence. Implementations must never create work or accounting entries. */
public interface AttendanceProvider
{
    boolean isConfigured(String tenantKey);
    Map<String, Object> configurationStatus();
    List<Map<String, Object>> query(String tenantKey, String timezone, String resource,
        List<String> externalIds, LocalDate date);
}
