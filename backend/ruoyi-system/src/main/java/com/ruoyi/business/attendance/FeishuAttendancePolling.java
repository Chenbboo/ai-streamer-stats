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
    @Value("${FEISHU_ATTENDANCE_POLL_LOOKBACK_DAYS:3}") private int lookbackDays = 3;
    private final BusinessFeishuService service;
    private final BusinessFeishuMapper mapper;
    private final AttendanceProvider provider;
    public FeishuAttendancePolling(BusinessFeishuService service,BusinessFeishuMapper mapper,AttendanceProvider provider)
    { this.service=service;this.mapper=mapper;this.provider=provider; }

    @Scheduled(fixedDelayString="${FEISHU_ATTENDANCE_POLL_DELAY_MS:900000}",initialDelayString="${FEISHU_ATTENDANCE_POLL_INITIAL_DELAY_MS:10000}")
    public void poll()
    {
        if(!enabled)return;
        for(Map<String,Object> c:mapper.connections())
        {
            if(!provider.isConfigured(String.valueOf(c.get("tenantKey")))||(c.get("runningRunId")!=null&&(c.get("leaseUntil")==null||!BusinessFeishuService.expired(c.get("leaseUntil")))))continue;
            LocalDate today=LocalDate.now(ZoneId.of(String.valueOf(c.get("timezone"))));
            int days=Math.max(1,Math.min(7,lookbackDays));
            try { service.startSync(((Number)c.get("connectionId")).longValue(),map("windowStart",today.minusDays(days-1).toString(),"windowEnd",today.toString()),0L); }
            catch(Exception ignored) { /* Detailed run outcome is retained by the service; never log provider payloads. */ }
        }
    }
}
