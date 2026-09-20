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
    BusinessMemberDayCostService service=new BusinessMemberDayCostService();{org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());}
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
        assertEquals(5,week().size());assertEquals(new BigDecimal("1047.62"),week().get(0).get("amount"));
        assertTrue(week().subList(1,5).stream().allMatch(c->new BigDecimal("1000.00").equals(c.get("amount"))));
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
    @Test void monthlyCostsUseSequentialEqualRedistributionAcrossProjectEndings(){
        rate.put("unitCost",3300);member.put("joinedDate","2026-09-14");
        List<Map<String,Object>> timeline=Arrays.asList(
            row("projectId",1L,"allocationId",1L,"userId",7L,"allocationValue",100,"effectiveFrom","2026-09-14","effectiveTo","2026-09-14","confirmationStatus","CONFIRMED"),
            row("projectId",1L,"allocationId",2L,"userId",7L,"allocationValue",10,"effectiveFrom","2026-09-15","confirmationStatus","CONFIRMED"),
            row("projectId",2L,"allocationId",3L,"userId",7L,"allocationValue",20,"effectiveFrom","2026-09-15","projectEndDate","2026-09-16","confirmationStatus","CONFIRMED"),
            row("projectId",3L,"allocationId",4L,"userId",7L,"allocationValue",70,"effectiveFrom","2026-09-15","projectEndDate","2026-09-18","confirmationStatus","CONFIRMED"));
        when(projects.selectUserAllocationTimeline(7L)).thenReturn(timeline);
        BigDecimal total=BigDecimal.ZERO;
        String[] expected={"1440.00","60.00","450.00"};
        for(long projectId=1;projectId<=3;projectId++){
            final long currentId=projectId;
            BusinessProject current=new BusinessProject();current.setProjectId(projectId);current.setBaseCurrency("CNY");
            current.setActualStartDate(Date.valueOf(projectId==1?"2026-09-14":"2026-09-15"));
            if(projectId!=1)current.setPlanEndDate(Date.valueOf(projectId==2?"2026-09-16":"2026-09-18"));
            when(work.selectMembers(projectId)).thenReturn(Collections.singletonList(member));
            when(costs.selectAllocationPeriods(projectId)).thenReturn(timeline.stream().filter(r->Long.valueOf(currentId).equals(r.get("projectId"))).collect(java.util.stream.Collectors.toList()));
            List<Map<String,Object>> calculated=service.calculateCurrent(current,java.time.LocalDate.parse("2026-09-01"),java.time.LocalDate.parse("2026-09-30"));
            assertTrue(calculated.stream().allMatch(r->"PRICED".equals(r.get("pricingStatus"))));
            BigDecimal amount=calculated.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add);
            assertEquals(new BigDecimal(expected[(int)projectId-1]),amount);total=total.add(amount);
            if(projectId==1)assertTrue(calculated.stream().filter(r->"2026-09-21".equals(r.get("bizDate"))).allMatch(r->String.valueOf(r.get("basisJson")).contains("\"autoRedistributed\":true")));
        }
        assertEquals(new BigDecimal("1950.00"),total);
    }

    @Test void projectWeightSplitsTheFullDailyCost(){
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(
            row("allocationId",31L,"userId",7L,"allocationValue",40,"effectiveFrom","2026-08-31","version",2)));
        List<Map<String,Object>> rows=week();
        assertEquals(5,rows.size());
        assertEquals(new BigDecimal("419.05"),rows.get(0).get("amount"));
        assertTrue(rows.subList(1,5).stream().allMatch(c->new BigDecimal("400.00").equals(c.get("amount"))));
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
    @Test void parentOwnerCanReadChildPersonnelCostsButUnrelatedUserCannot(){
        project.setParentId(2L);BusinessProject parent=new BusinessProject();parent.setProjectId(2L);parent.setMainOwnerUserId(99L);
        when(projects.selectProjectById(2L)).thenReturn(parent);
        assertDoesNotThrow(()->service.workspace(1L,Collections.emptyMap(),99L,false));
        assertThrows(ServiceException.class,()->service.workspace(1L,Collections.emptyMap(),88L,false));
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
    private BigDecimal sum(List<Map<String,Object>> rows){return rows.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add);}
    @Test void fullMonthAndProjectShareHaveNoAccumulatedRoundingError(){
        rate.put("unitCost",8000);rate.put("standardWorkDays",21.75);
        LocalDate from=LocalDate.parse("2026-10-01"),to=LocalDate.parse("2026-10-31");
        assertEquals(new BigDecimal("8000.00"),sum(service.calculate(project,from,to)));
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(
            row("allocationId",31L,"userId",7L,"allocationValue",new BigDecimal("33.33"),"effectiveFrom","2026-08-31","confirmationStatus","CONFIRMED")));
        List<Map<String,Object>> rows=service.calculate(project,from,to);
        assertEquals(new BigDecimal("2666.40"),sum(rows));
        assertTrue(String.valueOf(rows.get(0).get("basisJson")).contains("CALENDAR_MONTH_V1"));
        assertEquals(sum(rows),sum(service.calculate(project,from,LocalDate.parse("2026-10-14"))).add(sum(service.calculate(project,LocalDate.parse("2026-10-15"),to))));
    }
    @Test void midMonthRateAndWeightChangesAreProrated(){
        rate.put("unitCost",8000);rate.put("effectiveTo","2026-10-15");
        Map<String,Object> next=new HashMap<>(rate);next.put("policyId",3L);next.put("effectiveFrom","2026-10-16");next.remove("effectiveTo");next.put("unitCost",10000);
        when(work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,next));
        LocalDate from=LocalDate.parse("2026-10-01"),to=LocalDate.parse("2026-10-31");
        assertEquals(new BigDecimal("9000.00"),sum(service.calculate(project,from,to)));
        when(costs.selectAllocationPeriods(1L)).thenReturn(Arrays.asList(
            row("userId",7L,"allocationValue",100,"effectiveFrom","2026-08-31","effectiveTo","2026-10-15"),
            row("userId",7L,"allocationValue",50,"effectiveFrom","2026-10-16")));
        assertEquals(new BigDecimal("6500.00"),sum(service.calculate(project,from,to)));
    }
    @Test void newMonthlyRulePreservesAlreadyPricedHistory(){
        Map<String,Object> stored=row("userId",7L,"bizDate","2026-09-01","amount",new BigDecimal("101.23"),"pricingStatus","PRICED","basisJson","historical snapshot");
        when(costs.selectCosts(1L)).thenReturn(Collections.singletonList(stored));
        List<Map<String,Object>> rows=service.calculate(project,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-01"));
        assertEquals(new BigDecimal("101.23"),rows.get(0).get("amount"));assertEquals("historical snapshot",rows.get(0).get("basisJson"));
    }
}
