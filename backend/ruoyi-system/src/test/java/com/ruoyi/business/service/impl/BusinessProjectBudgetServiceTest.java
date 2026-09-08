package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

class BusinessProjectBudgetServiceTest
{
    BusinessProjectBudgetService service=new BusinessProjectBudgetService();
    BusinessProjectWorkMapper mapper=mock(BusinessProjectWorkMapper.class);
    BusinessProjectProposalMapper proposals=mock(BusinessProjectProposalMapper.class);
    BusinessProjectWorkService work=new BusinessProjectWorkService();
    BusinessProjectProposal p;
    Map<String,Object> calendar,rate,staff;
    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"proposals",proposals);ReflectionTestUtils.setField(service,"work",work);
        ReflectionTestUtils.setField(work,"mapper",mapper);ReflectionTestUtils.setField(work,"json",new ObjectMapper());
        calendar=row("calendarId",1L,"timeZone","Asia/Shanghai","workingWeekdays","1,2,3,4,5","dailyMinutes",480,"effectiveFrom",Date.valueOf("2000-01-01"),"exceptionsJson","[]");
        when(mapper.selectCalendar(1L)).thenReturn(calendar);
        when(mapper.selectUnitPolicy(1L)).thenReturn(row("unitPolicyId",1L,"minutesPerDay",480,"effectiveFrom",Date.valueOf("2000-01-01")));
        when(proposals.selectProposalStaff(eq(7L),any())).thenReturn(row("userId",7L,"companyDeptId",10L,"nickName","成员"));
        rate=row("policyId",5L,"version",1,"costMode","MONTHLY","unitCost",new BigDecimal("22000"),"standardWorkDays",22,"rateMinutesPerDay",480,"currency","CNY","effectiveFrom","2000-01-01");
        when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenAnswer(c->Collections.singletonList(rate));
        staff=row("userId",7L,"planStartDate","2026-09-01","planEndDate","2026-09-30","inputUnit","PERCENTAGE","inputQuantity",100,"calendarId",1L,"unitPolicyId",1L);
        p=new BusinessProjectProposal();p.setTemplateVersion("LIGHT_V1");p.setCompanyDeptId(10L);p.setBaseCurrency("CNY");p.setPlanStartDate(Date.valueOf("2026-09-01"));
        p.setBudget(row("cycle","MONTH","anchorDate","2026-09-01","businessAmount",500));p.setStaffingLines(Collections.singletonList(staff));
    }
    @Test void monthlyBudgetUsesCalendarAndRateAndIgnoresClientTotals()
    {
        p.getBudget().put("totalAmount",1);p.getBudget().put("personnelAmount",1);p.setBudgetLimit(BigDecimal.ONE);
        service.apply(p);assertEquals(new BigDecimal("22000.00"),p.getEstimatedPersonnelCost());assertEquals(new BigDecimal("22500.00"),p.getBudgetLimit());assertEquals("READY",p.getBudget().get("status"));
        assertFalse(p.getBudget().toString().contains("unitCost"));assertFalse(p.getBudget().toString().contains("monthlyCost"));
    }
    @Test void firstPeriodIsClippedToProjectStart()
    {p.setPlanStartDate(Date.valueOf("2026-09-15"));staff.put("planStartDate","2026-09-15");Map<String,Object> b=service.estimate(p);assertEquals("2026-09-15",b.get("startDate"));assertEquals("2026-09-30",b.get("endDate"));assertEquals(new BigDecimal("12000.00"),b.get("personnelAmount"));}
    @Test void weeklyBudgetUsesMondayThroughSunday()
    {p.getBudget().put("cycle","WEEK");p.getBudget().put("anchorDate","2026-09-20");Map<String,Object> b=service.estimate(p);assertEquals("2026-09-14",b.get("startDate"));assertEquals("2026-09-20",b.get("endDate"));assertEquals(new BigDecimal("5000.00"),b.get("personnelAmount"));}
    @Test void quarterlyBudgetUsesSelectedThreeMonthsAcrossYearBoundary()
    {
        p.getBudget().put("cycle","QUARTER");p.getBudget().put("anchorDate","2026-11-15");
        staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2026-11-01",b.get("anchorDate"));assertEquals("2026-11-01",b.get("startDate"));assertEquals("2027-01-31",b.get("endDate"));
        assertEquals(new BigDecimal("65000.00"),b.get("personnelAmount"));
    }
    @Test void rollingQuarterIncludesLeapDayAndClipsOnlyProjectStart()
    {
        p.setStaffingLines(Collections.emptyList());p.setPlanStartDate(Date.valueOf("2027-12-15"));
        p.getBudget().put("cycle","QUARTER");p.getBudget().put("anchorDate","2027-12-01");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2027-12-01",b.get("anchorDate"));assertEquals("2027-12-15",b.get("startDate"));assertEquals("2028-02-29",b.get("endDate"));
    }
    @Test void annualBudgetCycleIsRejected()
    {p.getBudget().put("cycle","YEAR");assertThrows(ServiceException.class,()->service.estimate(p));}
    @Test void finiteProjectAlwaysUsesWholeProject()
    {p.setPlanEndDate(Date.valueOf("2026-10-31"));Map<String,Object> b=service.estimate(p);assertEquals("PROJECT",b.get("cycle"));assertEquals("2026-10-31",b.get("endDate"));}
    @Test void proposalIgnoresClientWorkloadAndBudgetsFullParticipationDays()
    {staff.put("planStartDate","2026-09-30");staff.put("planEndDate","2026-10-01");staff.put("inputUnit","HOUR");staff.put("inputQuantity",8);Map<String,Object> b=service.estimate(p);assertEquals(new BigDecimal("1000.00"),b.get("personnelAmount"));}
    @Test void holidayDoesNotConsumePlannedBudget()
    {calendar.put("exceptionsJson","[{\"bizDate\":\"2026-09-01\",\"minutes\":0}]");assertEquals(new BigDecimal("21000.00"),service.estimate(p).get("personnelAmount"));}
    @Test void changesOfRateDuringMonthArePricedByDay()
    {rate.put("effectiveTo","2026-09-15");Map<String,Object> next=new HashMap<>(rate);next.remove("effectiveTo");next.put("effectiveFrom","2026-09-16");next.put("unitCost",new BigDecimal("44000"));when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,next));assertEquals(new BigDecimal("33000.00"),service.estimate(p).get("personnelAmount"));}
    @Test void hourlyRatesConvertToStandardDayAndDailyRatesIgnoreCalendarHours()
    {staff.put("inputUnit","HOUR");staff.put("inputQuantity",8);staff.put("planEndDate","2026-09-01");rate.put("costMode","HOURLY");rate.put("unitCost",100);assertEquals(new BigDecimal("800.00"),service.estimate(p).get("personnelAmount"));rate.put("costMode","DAILY");rate.put("unitCost",600);rate.put("rateMinutesPerDay",360);assertEquals(new BigDecimal("600.00"),service.estimate(p).get("personnelAmount"));}
    @Test void missingOrOverlappingRateCannotBecomeZeroBudget()
    {when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Collections.emptyList());service.apply(p);assertNull(p.getBudgetLimit());assertNull(p.getEstimatedPersonnelCost());assertEquals("PENDING",p.getBudget().get("status"));when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,rate));assertNull(service.estimate(p).get("personnelAmount"));}
    @Test void currencyMismatchAndDuplicateMembersAreNotSilentlyAccepted()
    {rate.put("currency","USD");assertEquals("PENDING",service.estimate(p).get("status"));rate.put("currency","CNY");p.setStaffingLines(Arrays.asList(staff,new HashMap<>(staff)));assertEquals("PENDING",service.estimate(p).get("status"));}
    @Test void unauthorizedCompanyCannotExposeAggregateRates()
    {p.setCompanyDeptId(99L);assertNull(service.estimate(p).get("personnelAmount"));verify(mapper,never()).selectBudgetRates(anyLong(),anyString(),anyString());}
    @Test void datedExpensesOutsidePeriodAreExcludedUndatedExpensesCount()
    {p.setExpenseLines(Arrays.asList(row("amount",100,"occurDate","2026-10-01"),row("amount",300)));Map<String,Object> b=service.estimate(p);assertEquals(new BigDecimal("300.00"),b.get("plannedBusinessAmount"));p.getBudget().put("businessAmount",200);assertEquals("PENDING",service.estimate(p).get("status"));}
    @Test void emptyPersonnelPlanMeansZeroAndInvalidMoneyIsRejected()
    {p.setStaffingLines(Collections.emptyList());assertEquals(new BigDecimal("500.00"),service.estimate(p).get("totalAmount"));p.getBudget().put("businessAmount",-1);assertThrows(ServiceException.class,()->service.estimate(p));p.getBudget().put("businessAmount","0.001");assertThrows(ServiceException.class,()->service.estimate(p));}
    @Test void invalidOrEmptyRowsCannotDisappear()
    {p.setStaffingLines(Arrays.asList((Map<String,Object>)null));assertThrows(ServiceException.class,()->service.estimate(p));}

    @Test void dailyBudgetControlsRecurringCostsSeparatelyFromStartupAndNeverBecomesTotalCap()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("DAILY");p.setBudgetScope("CASH_EXPENSE");
        p.setDailyBudgetLimit(new BigDecimal("100"));p.setStartupBudgetLimit(new BigDecimal("500"));
        p.setExpenseLines(Arrays.asList(row("amount",100,"occurrenceType","DAILY"),row("amount",500,"occurrenceType","ONE_TIME")));
        service.apply(p);assertNull(p.getBudgetLimit());assertEquals("0",p.getNoBudget());
        assertEquals(new BigDecimal("3500.00"),p.getEstimatedExternalCost());assertEquals("READY",p.getBudget().get("status"));
        p.setDailyBudgetLimit(new BigDecimal("99"));assertEquals("PENDING",service.estimate(p).get("status"));
        p.setDailyBudgetLimit(new BigDecimal("100"));p.setStartupBudgetLimit(new BigDecimal("499"));assertEquals("PENDING",service.estimate(p).get("status"));
    }
    @Test void fullCostDailyCapIncludesPersonnelButCashExpenseCapDoesNot()
    {
        p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("100"));p.setBudgetScope("FULL_COST");
        assertEquals("PENDING",service.estimate(p).get("status"));p.setBudgetScope("CASH_EXPENSE");
        assertEquals("READY",service.estimate(p).get("status"));
    }
    @Test void noBudgetNeedsReasonAndKeepsForecastWithoutInventingACap()
    {
        p.setBudgetMode("NONE");assertEquals("PENDING",service.estimate(p).get("status"));
        p.setBudgetReason("持续探索，按月复盘");service.apply(p);
        assertNull(p.getBudgetLimit());assertEquals("1",p.getNoBudget());assertEquals("READY",p.getBudget().get("status"));
        assertEquals(new BigDecimal("22000.00"),p.getEstimatedTotalCost());
    }
    @Test void monthlyForecastSeparatesStartupFromSteadyMonthAndConvertsRecurringFrequencies()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("NONE");p.setBudgetReason("测算");
        p.setPlanStartDate(Date.valueOf("2026-09-16"));
        p.setRevenueLines(Arrays.asList(row("expectedAmount",300,"occurrenceType","MONTHLY"),row("expectedAmount",70,"occurrenceType","WEEKLY"),row("expectedAmount",50)));
        Map<String,Object> b=service.estimate(p),first=(Map<String,Object>)b.get("firstMonth"),steady=(Map<String,Object>)b.get("steadyMonth");
        assertEquals("2026-09-16",first.get("startDate"));assertEquals(new BigDecimal("350.00"),first.get("revenueAmount"));
        assertEquals("2026-10-01",steady.get("startDate"));assertEquals(new BigDecimal("620.00"),steady.get("revenueAmount"));
    }
    @Test void dailyCapRejectsLateStartingExpenseEvenWhenMonthlyAverageIsLow()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("100"));
        p.setExpenseLines(Collections.singletonList(row("amount",500,"occurrenceType","DAILY","occurDate","2026-09-30")));
        Map<String,Object> b=service.estimate(p);
        assertEquals(new BigDecimal("16.67"),b.get("expectedDailyCost"));assertEquals(new BigDecimal("500.00"),b.get("peakDailyCost"));
        assertEquals("PENDING",b.get("status"));assertTrue(b.get("issues").toString().contains("2026-09-30"));
    }
    @Test void fullCostUsesWorkingDayPeakAndStillChecksKnownDaysWithMissingRates()
    {
        p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("900"));
        Map<String,Object> b=service.estimate(p);
        assertEquals("PENDING",b.get("status"));assertEquals(new BigDecimal("1000.00"),b.get("peakDailyCost"));
        rate.put("effectiveTo","2026-09-10");b=service.estimate(p);
        assertNull(b.get("personnelAmount"));assertNull(b.get("peakDailyCost"));assertNull(b.get("expectedDailyCost"));
        assertEquals(new BigDecimal("1000.00"),b.get("knownPeakDailyCost"));
        assertTrue(b.get("issues").toString().contains("缺少有效成本费率"));assertTrue(b.get("issues").toString().contains("最高日成本至少"));
    }
    @Test void weeklyAndMonthlyCostsAreSpreadPerDayAndStartupIsSeparate()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("20"));p.setStartupBudgetLimit(new BigDecimal("1000"));
        p.setExpenseLines(Arrays.asList(row("amount",70,"occurrenceType","WEEKLY"),row("amount",300,"occurrenceType","MONTHLY"),row("amount",1000,"occurrenceType","ONE_TIME")));
        Map<String,Object> b=service.estimate(p);assertEquals("READY",b.get("status"));assertEquals(new BigDecimal("20.00"),b.get("peakDailyCost"));
        p.setDailyBudgetLimit(new BigDecimal("19.99"));assertEquals("PENDING",service.estimate(p).get("status"));
    }
    @Test void noneModeDiscardsHiddenStartupAndDailyLimitsFromBothPayloadLocations()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("NONE");p.setBudgetReason("不设上限");
        p.setStartupBudgetLimit(new BigDecimal("200"));p.setDailyBudgetLimit(new BigDecimal("10"));
        p.getBudget().put("startupLimit",100);p.getBudget().put("dailyLimit",1);
        p.setExpenseLines(Collections.singletonList(row("amount",300,"occurrenceType","ONE_TIME")));
        service.apply(p);assertEquals("READY",p.getBudget().get("status"));assertNull(p.getStartupBudgetLimit());assertNull(p.getDailyBudgetLimit());
        assertNull(p.getBudget().get("startupLimit"));assertNull(p.getBudget().get("dailyLimit"));
    }
    @Test void previewAndSaveUseTheSameDateBoundaryError()
    {
        p.setStaffingLines(Collections.emptyList());p.setPlanStartDate(Date.valueOf("2026-09-08"));p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("500"));
        p.setExpenseLines(Collections.singletonList(row("expenseCategory","PROMOTION","itemName","推广","purpose","获客","amount",500,"occurrenceType","DAILY","occurDate","2026-09-02")));
        Map<String,Object> b=service.estimate(p);
        ServiceException error=assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(new BusinessProjectProposalServiceImpl(),"validatePlanDetails",p));
        assertEquals("PENDING",b.get("status"));assertTrue(((List<?>)b.get("issues")).contains(error.getMessage()));
        p.getExpenseLines().get(0).put("occurDate","2026-09-08");assertEquals("READY",service.estimate(p).get("status"));
    }
    @Test void periodForecastsExposeIndependentStaffGapsWithoutSalaryDetails()
    {
        staff.put("participationMode","FOLLOW_PROJECT");rate.put("effectiveTo","2026-09-30");
        Map<String,Object> b=service.estimate(p),first=(Map<String,Object>)b.get("firstMonth"),steady=(Map<String,Object>)b.get("steadyMonth");
        assertEquals("READY",first.get("status"));assertEquals("PENDING",steady.get("status"));
        assertTrue(steady.get("issues").toString().contains("2026-10-01"));assertNull(steady.get("personnelAmount"));
        Map<String,Object> status=(Map<String,Object>)((List<?>)b.get("staffingStatus")).get(0);
        assertEquals(7L,status.get("userId"));assertTrue(status.get("ratePeriods").toString().contains("2026-09-30"));
        assertFalse(status.toString().contains("unitCost"));assertFalse(status.toString().contains("monthlyCost"));
    }
    @Test void staffGapsPreserveLaterValidVersionsAndReverseDatesAreRejected()
    {
        rate.put("effectiveTo","2026-09-10");Map<String,Object> next=new HashMap<>(rate);
        next.put("policyId",6L);next.put("version",2);next.put("effectiveFrom","2026-09-21");next.put("effectiveTo","2026-09-30");
        when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,next));
        Map<String,Object> b=service.estimate(p),status=(Map<String,Object>)((List<?>)b.get("staffingStatus")).get(0);
        assertNull(b.get("personnelAmount"));assertEquals(2,((List<?>)status.get("ratePeriods")).size());
        assertTrue(status.get("issues").toString().contains("2026-09-11 至 2026-09-18"));
        staff.put("planStartDate","2026-09-20");staff.put("planEndDate","2026-09-10");
        assertEquals("PENDING",service.estimate(p).get("status"));
    }
    @Test void futureRecurringStartIsClippedAndInvalidFrequencyIsRejected()
    {
        p.setExpenseLines(Collections.singletonList(row("amount",10,"occurrenceType","DAILY","occurDate","2026-09-21")));
        assertEquals(new BigDecimal("100.00"),service.estimate(p).get("plannedBusinessAmount"));
        p.getExpenseLines().get(0).put("occurrenceType","YEARLY");assertThrows(ServiceException.class,()->service.estimate(p));
    }
}
