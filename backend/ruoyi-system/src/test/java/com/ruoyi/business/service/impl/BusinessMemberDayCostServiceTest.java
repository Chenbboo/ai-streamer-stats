package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

class BusinessMemberDayCostServiceTest {
    BusinessMemberDayCostService service=new BusinessMemberDayCostService();
    BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
    BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
    BusinessMemberDayCostMapper costs=mock(BusinessMemberDayCostMapper.class);
    IBusinessAccountingService accounting=mock(IBusinessAccountingService.class);
    BusinessProject project=new BusinessProject();
    Map<String,Object> calendar,member,rate;
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"projects",projects);ReflectionTestUtils.setField(service,"work",work);
        ReflectionTestUtils.setField(service,"mapper",costs);ReflectionTestUtils.setField(service,"json",new ObjectMapper());ReflectionTestUtils.setField(service,"accounting",accounting);
        project.setProjectId(1L);project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);project.setBaseCurrency("CNY");project.setMainOwnerUserId(10L);project.setActualStartDate(Date.valueOf("2026-08-31"));
        member=row("userId",7L,"userName","成员","status","0","memberRole","MEMBER","joinedDate","2026-08-31");
        calendar=row("calendarId",1L,"version",1,"workingWeekdays","1,2,3,4,5","dailyMinutes",480,"exceptionsJson","[]","effectiveFrom","2000-01-01");
        rate=row("policyId",2L,"version",1,"costMode","MONTHLY","unitCost",22000,"standardWorkDays",22,"currency","CNY","effectiveFrom","2000-01-01");
        when(projects.selectProjectById(1L)).thenReturn(project);when(projects.selectProjectByIdForUpdate(1L)).thenReturn(project);
        when(work.selectMembers(1L)).thenReturn(Collections.singletonList(member));when(work.selectCalendars()).thenReturn(Collections.singletonList(calendar));
        when(work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Collections.singletonList(rate));
    }
    List<Map<String,Object>> week(){return service.calculate(project,LocalDate.parse("2026-08-31"),LocalDate.parse("2026-09-06"));}
    @Test void fiveWeekdaysWithoutAnyWorkReportOrPercentage(){
        assertEquals(5,week().size());assertTrue(week().stream().allMatch(c->new BigDecimal("1000.00").equals(c.get("amount"))));
        verify(work,never()).selectWorkCosts(anyLong(),any());
    }
    @Test void holidayAndMakeupDayCountWholeDayEvenWhenCalendarHoursVary(){
        calendar.put("exceptionsJson","[{\"bizDate\":\"2026-09-01\",\"minutes\":0},{\"bizDate\":\"2026-09-05\",\"minutes\":240}]");
        List<Map<String,Object>> rows=week();assertEquals(5,rows.size());
        assertFalse(rows.stream().anyMatch(c->"2026-09-01".equals(c.get("bizDate"))));
        assertEquals(new BigDecimal("1000.00"),rows.get(4).get("amount"));
    }
    @Test void joinLeaveAndParticipationDatesIntersectAndIgnoreStoredPercent(){
        member.put("joinedDate","2026-09-01");member.put("leftDate","2026-09-03");member.put("status","1");
        when(work.selectAssignments(1L)).thenReturn(Collections.singletonList(row("userId",7L,"status","ACTIVE","effectiveFrom","2026-09-02","effectiveTo","2026-09-04","calendarId",1L,"inputQuantity",25)));
        assertEquals(2,week().size());assertEquals(new BigDecimal("1000.00"),week().get(0).get("amount"));
    }
    @Test void retiredParticipationKeepsPriorDays(){
        when(work.selectAssignments(1L)).thenReturn(Collections.singletonList(row("userId",7L,"status","RETIRED","effectiveFrom","2026-08-31","retiredTime","2026-09-03","calendarId",1L)));
        assertEquals(3,week().size());
    }
    @Test void effectiveRateChangesDaily(){
        rate.put("effectiveTo","2026-09-02");Map<String,Object> next=new HashMap<>(rate);next.put("effectiveFrom","2026-09-03");next.remove("effectiveTo");next.put("unitCost",44000);
        when(work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,next));
        assertEquals(new BigDecimal("1000.00"),week().get(2).get("amount"));assertEquals(new BigDecimal("2000.00"),week().get(3).get("amount"));
    }
    @Test void projectWeightSplitsTheFullDailyCost(){
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(
            row("allocationId",31L,"userId",7L,"allocationValue",40,"effectiveFrom","2026-08-31","version",2)));
        List<Map<String,Object>> rows=week();
        assertEquals(5,rows.size());
        assertTrue(rows.stream().allMatch(c->new BigDecimal("400.00").equals(c.get("amount"))));
        assertTrue(rows.stream().allMatch(c->String.valueOf(c.get("basisJson")).contains("\"allocationPercent\":40")));
    }
    @Test void missingEffectiveProjectWeightStaysPending(){
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(
            row("allocationId",31L,"userId",7L,"allocationValue",40,"effectiveFrom","2026-09-03","version",2)));
        List<Map<String,Object>> rows=week();
        assertNull(rows.get(0).get("amount"));
        assertEquals("PENDING",rows.get(0).get("pricingStatus"));
        assertEquals("缺少该日期有效的项目投入权重",rows.get(0).get("issue"));
        assertEquals(new BigDecimal("400.00"),rows.get(3).get("amount"));
    }
    @Test void unconfirmedNewProjectDoesNotPretendPersonnelCostIsZero(){
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(
            row("allocationId",31L,"userId",7L,"allocationValue",0,"effectiveFrom","2026-08-31","confirmationStatus","PENDING")));
        assertTrue(week().stream().allMatch(c->c.get("amount")==null && "PENDING".equals(c.get("pricingStatus"))));
        assertTrue(String.valueOf(week().get(0).get("issue")).contains("人员投入待确认"));
    }
    @Test void missingOrOverlappingRatesStayPendingInsteadOfZero(){
        when(work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Collections.emptyList());
        assertNull(week().get(0).get("amount"));assertEquals("PENDING",week().get(0).get("pricingStatus"));
        when(work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,rate));assertEquals("成本生效日期重叠",week().get(0).get("issue"));
    }
    @Test void observersAndNonmembersCannotReadCosts(){
        member.put("memberRole","OBSERVER");assertTrue(week().isEmpty());
        assertThrows(ServiceException.class,()->service.workspace(1L,Collections.emptyMap(),99L,false));
    }
    @Test void repeatedSynchronizationDoesNotCreateMoreAccountingVersions(){
        project.setActualEndDate(Date.valueOf("2026-09-04"));
        List<Map<String,Object>> rows=week();when(costs.selectCosts(1L)).thenReturn(Collections.emptyList(),rows);
        service.synchronize(1L);service.synchronize(1L);
        verify(costs,times(5)).insertCost(anyMap());verify(accounting,times(5)).recalculatePersonnelCost(eq(1L),any(),eq("member-day-cost"));
    }
    @Test void legacyWeekendSnapshotIsRecalculatedToRemoveOldPercentageCosts(){
        project.setActualEndDate(Date.valueOf("2026-09-06"));
        when(costs.selectLegacyResultDates(1L)).thenReturn(Collections.singletonList("2026-09-06"));
        service.synchronize(1L);
        verify(accounting).recalculatePersonnelCost(1L,Date.valueOf("2026-09-06"),"member-day-cost");
        verify(costs,never()).insertCost(argThat(c->"2026-09-06".equals(c.get("bizDate"))));
    }
    @Test void rejoiningKeepsPreviousPeriodAndDoesNotChargeGap(){
        member.put("joinedDate","2026-09-04");
        when(costs.selectPastMemberships(1L)).thenReturn(Collections.singletonList(row("userId",7L,"userName","成员","memberRole","MEMBER","status","1","joinedDate","2026-08-31","leftDate","2026-09-01")));
        assertEquals(3,week().size());assertFalse(week().stream().anyMatch(c->"2026-09-02".equals(c.get("bizDate"))));
    }
    @Test void closedAccountingUsesStoredAmountsAndDoesNotRegenerate(){
        project.setAccountingState("CLOSED");
        service.synchronize(1L);verify(work,never()).selectCalendars();verify(costs,never()).deleteDay(anyLong(),anyString());
    }
    @Test void projectPlannedStartPreventsEarlyAccrual(){
        project.setPlanStartDate(Date.valueOf("2026-09-03"));assertEquals(2,week().size());
    }
}
