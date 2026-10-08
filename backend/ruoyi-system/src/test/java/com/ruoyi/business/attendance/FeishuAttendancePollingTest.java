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
        polling.poll();verify(service).startScheduledSync(eq(1L),anyMap());verifyNoMoreInteractions(service);
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
        verify(service).startScheduledSync(eq(1L),args.capture());
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertEquals(today.toString(),args.getValue().get("windowEnd"));
        assertEquals(today.minusDays(6).toString(),args.getValue().get("windowStart"));
        verifyNoMoreInteractions(service);
    }
    @Test void failedStartIsLoggedWithoutSensitiveMessageAndDoesNotBlockOtherConnections() {
        BusinessFeishuService service=mock(BusinessFeishuService.class);
        BusinessFeishuMapper mapper=mock(BusinessFeishuMapper.class);
        AttendanceProvider provider=mock(AttendanceProvider.class);
        FeishuAttendancePolling polling=new FeishuAttendancePolling(service,mapper,provider);
        ReflectionTestUtils.setField(polling,"enabled",true);
        when(mapper.connections()).thenReturn(Arrays.asList(
            map("connectionId",1L,"tenantKey","t","timezone","Asia/Shanghai"),
            map("connectionId",2L,"tenantKey","t","timezone","Asia/Shanghai")));
        when(provider.isConfigured("t")).thenReturn(true);
        when(service.startScheduledSync(eq(1L),anyMap())).thenThrow(new IllegalStateException("private-provider-payload"));
        ch.qos.logback.classic.Logger logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(FeishuAttendancePolling.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs=new ch.qos.logback.core.read.ListAppender<>();
        logs.start();logger.addAppender(logs);
        try {
            polling.poll();
            verify(service).startScheduledSync(eq(2L),anyMap());
            assertEquals(1,logs.list.size());
            ch.qos.logback.classic.spi.ILoggingEvent event=logs.list.get(0);
            assertEquals(ch.qos.logback.classic.Level.WARN,event.getLevel());
            assertTrue(event.getFormattedMessage().contains("FEISHU_POLL_START_FAILED connectionId=1"));
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertFalse(event.getFormattedMessage().contains("private-provider-payload"));
            assertNull(event.getThrowableProxy());
        } finally { logger.detachAppender(logs);logs.stop(); }
    }
}
