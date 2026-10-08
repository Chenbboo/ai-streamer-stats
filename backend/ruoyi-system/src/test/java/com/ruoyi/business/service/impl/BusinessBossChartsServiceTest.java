package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessBossChartsServiceTest {
    @Mock BusinessAccountingMapper mapper;
    @InjectMocks BusinessAccountingServiceImpl service;
    @BeforeEach void clock() { service.setOverviewClock(Clock.fixed(Instant.parse("2026-01-01T16:10:00Z"), ZoneOffset.UTC)); }

    @Test void currentMonthUsesShanghaiDateAndBothQueriesHaveSameCompanyAndOwnerScope() {
        Map<String,Object> result=service.bossCharts("2026-01",110L,126L,false);
        assertEquals("2026-01-01",result.get("dateFrom"));assertEquals("2026-01-02",result.get("dateTo"));
        ArgumentCaptor<Map<String,Object>> queries=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectBossChartTrend(queries.capture());
        verify(mapper).selectBossChartProjects(queries.capture());
        assertEquals(queries.getAllValues().get(0),queries.getAllValues().get(1));
        Map<String,Object> query=queries.getValue();
        assertEquals(110L,query.get("companyDeptId"));assertEquals(126L,query.get("userId"));assertEquals(false,query.get("viewAll"));
        verifyNoMoreInteractions(mapper);
    }
    @Test void historicalMonthsUseTheirFullCalendarRangeIncludingLeapFebruaryAndYearBoundary() {
        Map<String,Object> result=service.bossCharts("2025-12",110L,1L,true);
        assertEquals("2025-12-01",result.get("dateFrom"));assertEquals("2025-12-31",result.get("dateTo"));
        result=service.bossCharts("2024-02",110L,1L,true);
        assertEquals("2024-02-01",result.get("dateFrom"));assertEquals("2024-02-29",result.get("dateTo"));
        result=service.bossCharts("2025-02",110L,1L,true);
        assertEquals("2025-02-28",result.get("dateTo"));
    }
    @Test void emptyResultsStayEmptyAndNegativeCostsArePreserved() {
        Map<String,Object> row=new HashMap<>();row.put("currency","CNY");row.put("costAmount",new java.math.BigDecimal("-12.3400"));
        when(mapper.selectBossChartProjects(anyMap())).thenReturn(Collections.singletonList(row));
        Map<String,Object> result=service.bossCharts("2026-01",110L,1L,true);
        assertEquals(Collections.emptyList(),result.get("trend"));
        assertEquals(Collections.singletonList(row),result.get("projects"));
        verify(mapper).selectBossChartTrend(anyMap());verify(mapper).selectBossChartProjects(anyMap());
        verifyNoMoreInteractions(mapper);
    }
    @Test void invalidOrFutureMonthsAndMissingCompanyDoNotQuery() {
        for(String month:Arrays.asList("2026-02","2025-13","2026-1","2026-01-01","","0000-01"))
            assertThrows(ServiceException.class,()->service.bossCharts(month,110L,1L,true));
        assertThrows(ServiceException.class,()->service.bossCharts(null,110L,1L,true));
        assertThrows(ServiceException.class,()->service.bossCharts("2026-01",null,1L,true));
        verifyNoInteractions(mapper);
    }
    @Test void multiMonthRangeCrossesYearAndStopsCurrentMonthAtToday() {
        Map<String,Object> result=service.bossCharts("2025-11","2026-01",110L,126L,false);
        assertEquals("2025-11-01",result.get("dateFrom"));assertEquals("2026-01-02",result.get("dateTo"));
        assertEquals(true,result.get("monthly"));
        verify(mapper).selectBossChartTrend(argThat(query->Boolean.TRUE.equals(query.get("monthly"))&&Long.valueOf(110L).equals(query.get("companyDeptId"))));
        result=service.bossCharts("2024-01","2024-02",110L,1L,true);
        assertEquals("2024-02-29",result.get("dateTo"));
        assertEquals(false,service.bossCharts("2025-12","2025-12",110L,1L,true).get("monthly"));
    }
    @Test void missingReversedAndFutureRangeEndsAreRejectedBeforeQuerying() {
        assertThrows(ServiceException.class,()->service.bossCharts("2025-11",null,110L,1L,true));
        assertThrows(ServiceException.class,()->service.bossCharts("2026-01","2025-12",110L,1L,true));
        assertThrows(ServiceException.class,()->service.bossCharts("2025-12","2026-02",110L,1L,true));
        verifyNoInteractions(mapper);
    }
}
