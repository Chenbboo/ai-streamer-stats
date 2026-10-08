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
    @Test void memberPauseSkipsOnlyItsProjectDaysAndStartDateRestoresCost(){
        when(projects.selectMemberWorkPauses(1L)).thenReturn(Collections.singletonList(
            row("userId",7L,"effectiveFrom","2026-09-01","effectiveTo","2026-09-03")));
        List<Map<String,Object>> rows=service.calculateCurrent(project,LocalDate.parse("2026-08-31"),LocalDate.parse("2026-09-04"));
        assertEquals(Arrays.asList("2026-08-31","2026-09-03","2026-09-04"),
            rows.stream().map(r->r.get("bizDate")).collect(java.util.stream.Collectors.toList()));
        assertTrue(rows.stream().allMatch(r->"PRICED".equals(r.get("pricingStatus"))));
    }
    @Test void activePauseAlsoRemovesPreservedCostForPausedDate(){
        when(projects.selectMemberWorkPauses(1L)).thenReturn(Collections.singletonList(
            row("userId",7L,"effectiveFrom","2026-09-01")));
        when(costs.selectCosts(1L)).thenReturn(Collections.singletonList(
            row("userId",7L,"bizDate","2026-09-01","pricingStatus","PRICED","amount",new BigDecimal("1000"))));
        assertTrue(service.calculate(project,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-01")).isEmpty());
    }
    @Test void fullMonthlyPayrollAlsoExcludesMemberPausePeriods(){
        directPayrollFixture();
        when(projects.selectMemberWorkPauses(1L)).thenReturn(Collections.singletonList(
            row("userId",7L,"effectiveFrom","2026-09-24","effectiveTo","2026-09-29")));
        List<Map<String,Object>> rows=service.calculateCurrent(project,LocalDate.parse("2026-09-23"),LocalDate.parse("2026-09-30"));
        assertEquals(Arrays.asList("2026-09-23","2026-09-29","2026-09-30"),
            rows.stream().map(r->r.get("bizDate")).collect(java.util.stream.Collectors.toList()));
        assertTrue(rows.stream().allMatch(r->"PRICED".equals(r.get("pricingStatus"))));
    }
    void directPayrollFixture(){
        project.setActualStartDate(Date.valueOf("2026-09-23"));member.put("joinedDate","2026-09-23");rate.put("unitCost",11250);
        when(costs.selectStaffMetadata(eq(7L),any())).thenReturn(row("departmentCostSource","DIRECT_PROJECT"));
        Map<String,Object> allocation=row("projectId",1L,"userId",7L,"allocationValue",20,"confirmationStatus","CONFIRMED","effectiveFrom","2026-09-23","projectStartDate","2026-09-23","projectCurrency","CNY");
        when(projects.selectUserAllocationTimeline(7L)).thenReturn(Collections.singletonList(allocation));
        when(costs.selectAllocationPeriods(1L)).thenReturn(Collections.singletonList(allocation));
    }
    @Test void directDepartmentProjectsReceiveEntireMonthlySalaryAndReportWindowsDoNotChangeDailyPricing(){
        directPayrollFixture();
        List<Map<String,Object>> month=service.calculateCurrent(project,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30"));
        assertEquals(6,month.size());assertTrue(month.stream().allMatch(r->"PRICED".equals(r.get("pricingStatus"))));
        assertEquals(new BigDecimal("11250.00"),month.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        Map<String,Object> day=service.calculateCurrent(project,LocalDate.parse("2026-09-29"),LocalDate.parse("2026-09-29")).get(0);
        assertEquals(new BigDecimal("1875.00"),day.get("amount"));assertTrue(day.get("basisJson").toString().contains("DIRECT_PROJECT_FULL_MONTH_V2"));
    }
    @Test void preservedDeletedProjectCostsKeepTheirNameAndAmountInMonthlyDetails(){
        directPayrollFixture();
        when(costs.selectUserMonthCosts(7L,"2026-09")).thenReturn(Collections.singletonList(row("projectId",17L,"bizDate","2026-09-21","amount",new BigDecimal("170.46"),"currency","CNY","projectName","唐勃珠宝","projectNo","OLD17","projectDelFlag","2")));
        List<Map<String,Object>> details=service.fullMonthlyPayroll(7L,java.time.YearMonth.of(2026,9),"CNY");
        Map<String,Object> deleted=details.stream().filter(r->Long.valueOf(17L).equals(r.get("projectId"))).findFirst().get();
        assertEquals("唐勃珠宝",deleted.get("projectName"));assertEquals(true,deleted.get("projectDeleted"));assertEquals(new BigDecimal("170.46"),deleted.get("amount"));
        assertEquals(new BigDecimal("11250.00"),details.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void supportMonthlyPreviewAndProjectDayCostUseTheSamePartialBudget(){
        directPayrollFixture();rate.put("unitCost",7500);
        when(costs.selectStaffMetadata(eq(7L),any())).thenReturn(row("departmentCostSource","STAFF_REMAINDER"));
        BigDecimal preview=service.fullMonthlyPayroll(7L,java.time.YearMonth.of(2026,9),"CNY").stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<Map<String,Object>> rows=service.calculateCurrent(project,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30"));
        assertEquals(new BigDecimal("1500.00"),preview);assertEquals(preview,rows.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertTrue(rows.stream().allMatch(r->r.get("basisJson").toString().contains("MONTH_PROJECT_SHARE_V1")));
    }
    @Test void foreignHistoricalCostBlocksMonthlyPreviewAndDailyPricing(){
        directPayrollFixture();
        when(costs.selectUserMonthCosts(7L,"2026-09")).thenReturn(Collections.singletonList(row("projectId",17L,"bizDate","2026-09-21","amount",new BigDecimal("100.00"),"currency","VND")));
        assertThrows(ServiceException.class,()->service.fullMonthlyPayroll(7L,java.time.YearMonth.of(2026,9),"CNY"));
        Map<String,Object> day=service.calculateCurrent(project,LocalDate.parse("2026-09-29"),LocalDate.parse("2026-09-29")).get(0);
        assertEquals("PENDING",day.get("pricingStatus"));assertNull(day.get("amount"));assertEquals("成本币种与项目不一致",day.get("issue"));
    }
    @Test void supportMonthRepricingReplacesPartialCostsOnceAndPreservesOtherMonths(){
        directPayrollFixture();rate.put("unitCost",7500);
        when(costs.selectStaffMetadata(eq(7L),any())).thenReturn(row("departmentCostSource","STAFF_REMAINDER"));
        LocalDate from=LocalDate.parse("2026-09-23"),to=LocalDate.parse("2026-09-29");project.setActualEndDate(Date.valueOf(to));
        List<Map<String,Object>> desired=service.calculateCurrent(project,from,to);
        List<Map<String,Object>> original=new ArrayList<>();
        for(Map<String,Object> d:desired){Map<String,Object> r=new LinkedHashMap<>(d);r.put("amount",new BigDecimal("68.18"));r.put("basisJson","old-priced-basis");original.add(r);}
        when(costs.selectCosts(1L)).thenReturn(original,desired);
        if(java.time.YearMonth.now().equals(java.time.YearMonth.of(2026,9))){
            service.synchronize(1L);service.synchronize(1L);
            verify(costs,times(desired.size())).insertCost(anyMap());verify(accounting,times(desired.size())).recalculatePersonnelCost(eq(1L),any(),eq("member-day-cost"));
        }
        Map<String,Object> old=row("userId",7L,"bizDate","2026-08-31","amount",new BigDecimal("68.18"),"pricingStatus","PRICED","basisJson","historical");
        when(costs.selectCosts(1L)).thenReturn(Collections.singletonList(old));
        assertEquals(new BigDecimal("68.18"),service.calculate(project,LocalDate.parse("2026-08-31"),LocalDate.parse("2026-08-31")).get(0).get("amount"));
    }
    @Test void directDepartmentPendingWeightsCannotTurnIntoPartialOrZeroPayroll(){
        directPayrollFixture();when(projects.selectUserAllocationTimeline(7L)).thenReturn(Collections.singletonList(row("projectId",1L,"allocationValue",20,"confirmationStatus","PENDING","effectiveFrom","2026-09-23")));
        Map<String,Object> day=service.calculateCurrent(project,LocalDate.parse("2026-09-29"),LocalDate.parse("2026-09-29")).get(0);
        assertEquals("PENDING",day.get("pricingStatus"));assertNull(day.get("amount"));
    }
    @Test void currentMonthDirectPayrollReplacesExistingPartialPricingButKeepsClosedAccounting(){
        directPayrollFixture();
        when(costs.selectCosts(1L)).thenReturn(Collections.singletonList(row("userId",7L,"bizDate","2026-09-29","amount",new BigDecimal("102.27"),"pricingStatus","PRICED","basisJson","{}")));
        Map<String,Object> day=service.calculate(project,LocalDate.parse("2026-09-29"),LocalDate.parse("2026-09-29")).get(0);
        if(java.time.YearMonth.now().equals(java.time.YearMonth.of(2026,9)))assertEquals(new BigDecimal("1875.00"),day.get("amount"));
        project.setAccountingState("CLOSED");clearInvocations(costs,accounting);service.synchronize(1L);
        verify(costs,never()).deleteDay(anyLong(),anyString());verifyNoInteractions(accounting);
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
    private List<Map<String,Object>> todayAttendance(String leaveStatus){
        return Arrays.asList(
            row("userId",7L,"observationId",610L,"businessDate","2026-09-28","kind","LEAVE","normalizedStatus",leaveStatus,"quality","KNOWN","intervalsJson","[[1790557200,1790762400]]"),
            row("userId",7L,"observationId",626L,"businessDate","2026-09-28","kind","SHIFT","normalizedStatus","CONFIRMED","quality","KNOWN","intervalsJson","[[1790557200,1790589600]]"));
    }
    @Test void lateSyncedLeaveOverridesSavedDayWithoutReplacingOriginalRate() throws Exception {
        LocalDate today=LocalDate.parse("2026-09-28");
        rate.put("unitCost",11250);
        List<Map<String,Object>> original=service.calculate(project,today,today);
        assertEquals(new BigDecimal("511.36"),original.get(0).get("amount"));
        when(costs.selectCosts(1L)).thenReturn(original);
        when(costs.selectCostAttendance(1L,"2026-09-28","2026-09-28")).thenReturn(todayAttendance("CONFIRMED"));
        rate.put("unitCost",99999);
        List<Map<String,Object>> deducted=service.calculate(project,today,today);
        assertEquals(new BigDecimal("0.00"),deducted.get(0).get("amount"));
        when(costs.selectCosts(1L)).thenReturn(deducted);
        when(costs.selectCostAttendance(1L,"2026-09-28","2026-09-28")).thenReturn(todayAttendance("CANCELED"));
        assertEquals(new BigDecimal("511.36"),service.calculate(project,today,today).get(0).get("amount"));
    }
    @Test void synchronizationUpdatesDailyResultAndRepeatedSyncIsIdempotent(){
        project.setActualStartDate(Date.valueOf("2026-09-28"));project.setActualEndDate(Date.valueOf("2026-09-28"));
        List<Map<String,Object>> original=service.calculate(project,LocalDate.parse("2026-09-28"),LocalDate.parse("2026-09-28"));
        when(costs.selectCosts(1L)).thenReturn(original);
        when(costs.selectCostAttendance(1L,"2026-09-28","2026-09-28")).thenReturn(todayAttendance("CONFIRMED"));
        List<Map<String,Object>> deducted=service.calculate(project,LocalDate.parse("2026-09-28"),LocalDate.parse("2026-09-28"));
        when(costs.selectCosts(1L)).thenReturn(original,deducted);
        service.synchronize(1L);service.synchronize(1L);
        verify(costs,times(1)).insertCost(argThat(c->new BigDecimal("0.00").equals(c.get("amount"))));
        verify(accounting,times(1)).recalculatePersonnelCost(1L,Date.valueOf("2026-09-28"),"member-day-cost");
    }
}
