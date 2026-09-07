package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.business.mapper.BusinessFeishuMapper;

/** Optional bounded historical replay; configuration alone never enables authority cutover. */
@Component
@EnableScheduling
public class FeishuAttendancePolling
{
    @Value("${FEISHU_ATTENDANCE_POLL_ENABLED:false}") private boolean enabled;
    private final BusinessFeishuService service;
    private final BusinessFeishuMapper mapper;
    private final AttendanceProvider provider;
    public FeishuAttendancePolling(BusinessFeishuService service,BusinessFeishuMapper mapper,AttendanceProvider provider)
    { this.service=service;this.mapper=mapper;this.provider=provider; }

    @Scheduled(fixedDelayString="${FEISHU_ATTENDANCE_POLL_DELAY_MS:900000}",initialDelayString="${FEISHU_ATTENDANCE_POLL_DELAY_MS:900000}")
    public void poll()
    {
        if(!enabled)return;
        for(Map<String,Object> c:mapper.connections())
        {
            if(!provider.isConfigured(String.valueOf(c.get("tenantKey")))||c.get("runningRunId")!=null)continue;
            LocalDate yesterday=LocalDate.now(ZoneId.of(String.valueOf(c.get("timezone")))).minusDays(1);
            try { service.startSync(((Number)c.get("connectionId")).longValue(),map("windowStart",yesterday.minusDays(1).toString(),"windowEnd",yesterday.toString()),0L); }
            catch(Exception ignored) { /* Detailed run outcome is retained by the service; never log provider payloads. */ }
        }
    }
}
