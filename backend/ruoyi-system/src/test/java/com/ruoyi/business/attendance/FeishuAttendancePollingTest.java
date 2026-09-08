package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.mapper.BusinessFeishuMapper;

class FeishuAttendancePollingTest {
    @Test void expiredLeasesRecoverButJdbcLocalDateTimeLiveLeasesAreNotStolen() {
        assertFalse(BusinessFeishuService.expired(LocalDateTime.now().plusMinutes(20)));
        assertFalse(BusinessFeishuService.expired(java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(20))));
        assertFalse(BusinessFeishuService.expired("unrecognized"));
        BusinessFeishuService service=mock(BusinessFeishuService.class);
        BusinessFeishuMapper mapper=mock(BusinessFeishuMapper.class);
        AttendanceProvider provider=mock(AttendanceProvider.class);
        FeishuAttendancePolling polling=new FeishuAttendancePolling(service,mapper,provider);
        ReflectionTestUtils.setField(polling,"enabled",true);when(provider.isConfigured("t")).thenReturn(true);
        when(mapper.connections()).thenReturn(Arrays.asList(
            map("connectionId",1L,"tenantKey","t","timezone","Asia/Shanghai","runningRunId",9L,"leaseUntil",LocalDateTime.now().minusMinutes(1)),
            map("connectionId",2L,"tenantKey","t","timezone","Asia/Shanghai","runningRunId",10L,"leaseUntil",LocalDateTime.now().plusMinutes(1))));
        polling.poll();verify(service).startSync(eq(1L),anyMap(),eq(0L));verifyNoMoreInteractions(service);
    }
    @Test void enabledPollingIncludesTodayAndBoundsReplayToSevenDays() {
        BusinessFeishuService service=mock(BusinessFeishuService.class);
        BusinessFeishuMapper mapper=mock(BusinessFeishuMapper.class);
        AttendanceProvider provider=mock(AttendanceProvider.class);
        FeishuAttendancePolling polling=new FeishuAttendancePolling(service,mapper,provider);
        when(mapper.connections()).thenReturn(Arrays.asList(map("connectionId",1L,"tenantKey","t","timezone","Asia/Shanghai"),map("connectionId",2L,"tenantKey","t","timezone","Asia/Shanghai","runningRunId",3L)));
        when(provider.isConfigured("t")).thenReturn(true);
        polling.poll();verifyNoInteractions(service);
        ReflectionTestUtils.setField(polling,"enabled",true);
        ReflectionTestUtils.setField(polling,"lookbackDays",99);
        polling.poll();
        ArgumentCaptor<Map<String,Object>> args=ArgumentCaptor.forClass(Map.class);
        verify(service).startSync(eq(1L),args.capture(),eq(0L));
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertEquals(today.toString(),args.getValue().get("windowEnd"));
        assertEquals(today.minusDays(6).toString(),args.getValue().get("windowStart"));
        verifyNoMoreInteractions(service);
    }
}
