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
class BusinessBossReviewServiceTest {
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Mock BusinessAccountingMapper mapper;
    @InjectMocks BusinessAccountingServiceImpl service;
    @BeforeEach void clock() { service.setOverviewClock(Clock.fixed(Instant.parse("2026-09-13T16:10:00Z"),ZoneOffset.UTC));
        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}
    Map<String,Object> row(Object... values) {
        Map<String,Object> result=new HashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;
    }
    @Test void yesterdayUsesBusinessTimezoneAndKeepsCurrentPersonnelDateAndOwnerScope() {
        when(mapper.selectOverviewReadiness(anyMap())).thenReturn(row("resultCount",2,"pendingCostCount",13));
        Map<String,Object> result=service.bossOverview("yesterday",126L,false);
        assertEquals("2026-09-13",result.get("bizDate"));assertEquals("2026-09-14",result.get("currentBizDate"));
        assertEquals("INCOMPLETE",result.get("dataStatus"));
        verify(mapper).selectCompanyPersonnelCostReadiness(126L,false,java.sql.Date.valueOf("2026-09-14"));
        verify(mapper).selectDailySummary(argThat(q->"2026-09-13".equals(q.get("dateFrom"))&&q.get("dateFrom").equals(q.get("dateTo"))&&Long.valueOf(126).equals(q.get("userId"))&&Boolean.FALSE.equals(q.get("viewAll"))));
        verify(mapper,never()).insertDailyResult(any());verify(mapper,never()).sumProjectFacts(any(),any());
    }
    @Test void currentAndPeriodAlertsReceiveIndependentDates() {
        List<Map<String,Object>> calls=new ArrayList<>();
        when(mapper.selectAccountingAlerts(anyMap())).thenAnswer(c->{calls.add(new HashMap<>(c.getArgument(0)));return Collections.emptyList();});
        service.bossOverview("2026-09-10",126L,false);
        assertEquals("2026-09-10",calls.get(0).get("bizDate"));assertEquals("PERIOD",calls.get(0).get("alertScope"));
        assertEquals("2026-09-14",calls.get(1).get("bizDate"));assertEquals("CURRENT",calls.get(1).get("alertScope"));
    }
    @Test void existingAiCallStillMeansToday() {
        Map<String,Object> result=service.bossOverview(126L,false);
        assertEquals("2026-09-14",result.get("bizDate"));
        verify(mapper).selectDailySummary(argThat(q->"2026-09-14".equals(q.get("dateFrom"))));
        verify(mapper,never()).selectOverviewReadiness(anyMap());
        verify(mapper).selectAccountingAlerts(argThat(q->!q.containsKey("alertScope")));
    }
    @Test void emptyResultsAreNotReportedAsCompleteZero() {
        when(mapper.selectOverviewReadiness(anyMap())).thenReturn(row("resultCount",0));
        assertEquals("NO_DATA",service.bossOverview("yesterday",1L,true).get("dataStatus"));
    }
    @Test void generatedZeroResultsAreAvailableButAnyIncompleteSourceBlocksThatStatus() {
        when(mapper.selectOverviewReadiness(anyMap())).thenReturn(row("resultCount",1));
        assertEquals("AVAILABLE",service.bossOverview("today",1L,true).get("dataStatus"));
        for(String key:Arrays.asList("pendingCostCount","unfinishedFactCount","unfinishedWorkCount")) {
            when(mapper.selectOverviewReadiness(anyMap())).thenReturn(row("resultCount",1,key,1));
            assertEquals("INCOMPLETE",service.bossOverview("today",1L,true).get("dataStatus"),key);
        }
        when(mapper.selectOverviewReadiness(anyMap())).thenReturn(row("resultCount",0));
        when(mapper.countProjectsMissingDailyResult(anyLong(),anyBoolean(),any(),isNull())).thenReturn(1);
        assertEquals("INCOMPLETE",service.bossOverview("today",1L,true).get("dataStatus"));
    }
    @Test void validatesDatesBeforeReadingData() {
        for(String date:Arrays.asList("2026-02-30","2026-9-01","2026-09-15",""))
            assertThrows(ServiceException.class,()->service.bossOverview(date,126L,false));
        verifyNoInteractions(mapper);
        assertEquals("2026-09-14",service.bossOverview(null,126L,false).get("bizDate"));
    }
    @Test void yesterdayCrossesYearAndLeapMonthBoundaries() {
        service.setOverviewClock(Clock.fixed(Instant.parse("2025-12-31T16:00:00Z"),ZoneOffset.UTC));
        assertEquals("2025-12-31",service.bossOverview("yesterday",1L,true).get("bizDate"));
        service.setOverviewClock(Clock.fixed(Instant.parse("2024-02-29T16:00:00Z"),ZoneOffset.UTC));
        assertEquals("2024-02-29",service.bossOverview("yesterday",1L,true).get("bizDate"));
    }
}
