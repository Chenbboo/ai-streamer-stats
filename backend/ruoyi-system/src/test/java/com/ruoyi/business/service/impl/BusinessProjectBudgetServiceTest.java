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
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

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

        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        org.springframework.test.util.ReflectionTestUtils.setField(work,"companyAccess",companyAccess);
}
    @Test void childBudgetIncludesSelectedOwnerInsteadOfApplicant() {
        p.setParentProjectId(1L);p.setApplicantUserId(9L);p.setAssignedOwnerUserId(7L);p.setAssignedOwnerName("子负责人");
        p.setStaffingLines(Collections.emptyList());
        service.ensureOwner(p);
        assertEquals(1,p.getStaffingLines().size());assertEquals(7L,p.getStaffingLines().get(0).get("userId"));
        assertEquals("子负责人",p.getStaffingLines().get(0).get("userName"));
        service.ensureOwner(p);assertEquals(1,p.getStaffingLines().size());
    }
    @Test void childFundingIsOneTimeForecastRevenueAndDoesNotCapBudget()
    {
        p.setParentProjectId(1L);p.setParentFundingAmount(new BigDecimal("2000"));
        p.setPlanEndDate(Date.valueOf("2026-10-31"));p.setStaffingLines(Collections.emptyList());
        p.getBudget().put("businessAmount",new BigDecimal("2600"));
        p.setRevenueLines(Collections.emptyList());

        Map<String,Object> budget=service.estimate(p);

        assertEquals(new BigDecimal("2000.00"),budget.get("revenueAmount"));
        assertEquals(new BigDecimal("0.00"),budget.get("externalRevenueAmount"));
        assertEquals(new BigDecimal("2000.00"),budget.get("parentFundingRevenue"));
        assertEquals(new BigDecimal("2600.00"),budget.get("totalAmount"));
        List<Map<String,Object>> months=(List<Map<String,Object>>)budget.get("monthlyForecasts");
        assertEquals(new BigDecimal("2000.00"),months.get(0).get("revenueAmount"));
        assertEquals(new BigDecimal("0.00"),months.get(1).get("revenueAmount"));
    }
    @Test void smallRevenueRetainsLargeNegativeMarginInsteadOfCappingLoss()
    {
        p.setPlanStartDate(Date.valueOf("2026-09-15"));p.setPlanEndDate(Date.valueOf("2026-09-24"));
        staff.put("planStartDate","2026-09-15");staff.put("planEndDate","2026-09-24");
        rate.put("unitCost",new BigDecimal("12300"));
        p.getBudget().put("businessAmount",3);
        p.setExpenseLines(Collections.singletonList(row("amount",3,"occurrenceType","ONE_TIME")));
        p.setRevenueLines(Collections.singletonList(row("scenario","BASE","expectedAmount",3,"occurrenceType","ONE_TIME")));
        service.apply(p);
        assertEquals(new BigDecimal("4472.73"),p.getEstimatedPersonnelCost());
        assertEquals(new BigDecimal("4475.73"),p.getEstimatedTotalCost());
        assertEquals(new BigDecimal("-4472.73"),p.getExpectedProfit());
        assertEquals(new BigDecimal("-149091.0000"),p.getExpectedMargin());
        p.setRevenueLines(Collections.singletonList(row("expectedAmount",new BigDecimal("0.01"))));
        service.apply(p);
        assertEquals(new BigDecimal("-44757200.0000"),p.getExpectedMargin());
        p.setRevenueLines(Collections.emptyList());service.apply(p);
        assertNull(p.getExpectedMargin());
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
    @Test void weeklyOneTimeIncomeAndExpenseUseExactDates()
    {
        p.setPlanStartDate(Date.valueOf("2026-08-01"));staff.put("planStartDate","2026-08-01");
        p.getBudget().put("cycle","WEEK");p.getBudget().put("anchorDate","2026-09-14");
        p.getBudget().put("businessAmount",6800);
        p.setRevenueLines(Arrays.asList(row("scenario","BASE","expectedAmount",1000,"occurrenceType","ONE_TIME","expectedDate","2026-09-15"),
            row("scenario","BASE","expectedAmount",7000,"occurrenceType","ONE_TIME","expectedDate","2026-09-01")));
        p.setExpenseLines(Arrays.asList(row("amount",800,"occurrenceType","ONE_TIME","occurDate","2026-09-15"),
            row("amount",6000,"occurrenceType","ONE_TIME","occurDate","2026-09-21")));
        Map<String,Object> budget=service.estimate(p);
        assertEquals(new BigDecimal("1000.00"),budget.get("revenueAmount"));
        assertEquals(new BigDecimal("800.00"),budget.get("plannedBusinessAmount"));
        assertEquals("READY",budget.get("status"));
    }
    @Test void quarterlyBudgetUsesSelectedThreeMonthsAcrossYearBoundary()
    {
        p.getBudget().put("cycle","QUARTER");p.getBudget().put("anchorDate","2026-11-15");
        staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2026-11-01",b.get("anchorDate"));assertEquals("2026-11-01",b.get("startDate"));assertEquals("2027-01-31",b.get("endDate"));
        assertEquals(new BigDecimal("66000.00"),b.get("personnelAmount"));
    }
    @Test void rollingQuarterIncludesLeapDayAndClipsOnlyProjectStart()
    {
        p.setStaffingLines(Collections.emptyList());p.setPlanStartDate(Date.valueOf("2027-12-15"));
        p.getBudget().put("cycle","QUARTER");p.getBudget().put("anchorDate","2027-12-01");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2027-12-01",b.get("anchorDate"));assertEquals("2027-12-15",b.get("startDate"));assertEquals("2028-02-29",b.get("endDate"));
    }
    @Test void annualBudgetUsesSelectedCalendarYear()
    {
        p.setStaffingLines(Collections.emptyList());p.getBudget().put("cycle","YEAR");p.getBudget().put("anchorDate","2027-08-15");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2027-01-01",b.get("anchorDate"));assertEquals("2027-01-01",b.get("startDate"));assertEquals("2027-12-31",b.get("endDate"));
    }
    @Test void annualFirstPeriodIsClippedToProjectStart()
    {
        p.setStaffingLines(Collections.emptyList());p.setPlanStartDate(Date.valueOf("2026-09-15"));p.getBudget().put("cycle","YEAR");p.getBudget().put("anchorDate","2026-01-01");
        Map<String,Object> b=service.estimate(p);
        assertEquals("2026-09-15",b.get("startDate"));assertEquals("2026-12-31",b.get("endDate"));
    }
    @Test void finiteProjectAlwaysUsesWholeProject()
    {p.setPlanEndDate(Date.valueOf("2026-10-31"));Map<String,Object> b=service.estimate(p);assertEquals("PROJECT",b.get("cycle"));assertEquals("2026-10-31",b.get("endDate"));}
    @Test void proposalInputRatioControlsPersonnelBudget()
    {staff.put("planStartDate","2026-09-30");staff.put("planEndDate","2026-10-01");staff.put("inputUnit","HOUR");staff.put("inputQuantity",8);Map<String,Object> b=service.estimate(p);assertEquals(new BigDecimal("80.00"),b.get("personnelAmount"),b.toString());Map<String,Object> status=(Map<String,Object>)((List<?>)b.get("staffingStatus")).get(0);assertEquals(new BigDecimal("80.00"),status.get("amount"));assertEquals("CNY",status.get("currency"));}
    @Test void holidayDoesNotReduceFullMonthMonthlyCost()
    {calendar.put("exceptionsJson","[{\"bizDate\":\"2026-09-01\",\"minutes\":0}]");assertEquals(new BigDecimal("22000.00"),service.estimate(p).get("personnelAmount"));}
    @Test void changesOfRateDuringMonthArePricedByDay()
    {rate.put("effectiveTo","2026-09-15");Map<String,Object> next=new HashMap<>(rate);next.remove("effectiveTo");next.put("effectiveFrom","2026-09-16");next.put("unitCost",new BigDecimal("44000"));when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,next));assertEquals(new BigDecimal("33000.00"),service.estimate(p).get("personnelAmount"));}
    @Test void hourlyRatesConvertToStandardDayAndDailyRatesIgnoreCalendarHours()
    {staff.put("inputUnit","HOUR");staff.put("inputQuantity",8);staff.put("planEndDate","2026-09-01");rate.put("costMode","HOURLY");rate.put("unitCost",100);assertEquals(new BigDecimal("64.00"),service.estimate(p).get("personnelAmount"));rate.put("costMode","DAILY");rate.put("unitCost",600);rate.put("rateMinutesPerDay",360);assertEquals(new BigDecimal("48.00"),service.estimate(p).get("personnelAmount"));}
    @Test void missingOrOverlappingRateCannotBecomeZeroBudget()
    {when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Collections.emptyList());service.apply(p);assertNull(p.getBudgetLimit());assertNull(p.getEstimatedPersonnelCost());assertEquals("PENDING",p.getBudget().get("status"));when(mapper.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Arrays.asList(rate,rate));assertNull(service.estimate(p).get("personnelAmount"));}
    @Test void currencyMismatchAndDuplicateMembersAreNotSilentlyAccepted()
    {rate.put("currency","USD");assertEquals("PENDING",service.estimate(p).get("status"));rate.put("currency","CNY");p.setStaffingLines(Arrays.asList(staff,new HashMap<>(staff)));assertEquals("PENDING",service.estimate(p).get("status"));}
    @Test void crossCompanyStaffCanBeIncludedInProposalBudget()
    {p.setCompanyDeptId(99L);Map<String,Object> budget=service.estimate(p);assertEquals("READY",budget.get("status"));assertEquals(new BigDecimal("22000.00"),budget.get("personnelAmount"));}
    @Test void datedExpensesOutsidePeriodAreExcludedUndatedExpensesCount()
    {p.setExpenseLines(Arrays.asList(row("amount",100,"occurDate","2026-10-01"),row("amount",300)));Map<String,Object> b=service.estimate(p);assertEquals(new BigDecimal("300.00"),b.get("plannedBusinessAmount"));p.getBudget().put("businessAmount",200);assertEquals("PENDING",service.estimate(p).get("status"));}
    @Test void emptyPersonnelPlanMeansZeroAndInvalidMoneyIsRejected()
    {p.setStaffingLines(Collections.emptyList());assertEquals(new BigDecimal("500.00"),service.estimate(p).get("totalAmount"));p.getBudget().put("businessAmount",-1);assertThrows(ServiceException.class,()->service.estimate(p));p.getBudget().put("businessAmount","0.001");assertThrows(ServiceException.class,()->service.estimate(p));}

    @Test void totalBudgetCannotBeReducedBelowExpensesOutsideCurrentPeriod()
    {
        p.setStaffingLines(Collections.emptyList());
        p.setExpenseLines(Arrays.asList(row("amount",100,"occurDate","2026-10-01"),row("amount",300)));
        p.getBudget().put("businessAmount",350);
        Map<String,Object> budget=service.estimate(p);
        assertEquals(new BigDecimal("300.00"),budget.get("plannedBusinessAmount"));
        assertEquals("PENDING",budget.get("status"));
        assertTrue(((List<?>)budget.get("issues")).contains("业务预算不能低于全部支出计划金额合计 400.00 CNY"));
        p.getBudget().put("businessAmount",450);
        budget=service.estimate(p);
        assertEquals("READY",budget.get("status"));
        assertEquals(new BigDecimal("450.00"),budget.get("businessAmount"));
        assertEquals(new BigDecimal("450.00"),budget.get("totalAmount"));
    }

    @Test void unspecifiedBusinessBudgetDefaultsToAllExpenseRows()
    {
        p.setStaffingLines(Collections.emptyList());
        p.getBudget().remove("businessAmount");
        p.setExpenseLines(Arrays.asList(row("amount",100,"occurDate","2026-10-01"),row("amount",300)));
        Map<String,Object> budget=service.estimate(p);
        assertEquals(new BigDecimal("400.00"),budget.get("businessAmount"));
        assertEquals("READY",budget.get("status"));
        p.setBudget(null);
        assertEquals(new BigDecimal("400.00"),service.estimate(p).get("businessAmount"));
    }
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
    @Test void totalBudgetIncludesOneTimeExpensesAndDiscardsStartupLimit()
    {
        p.setStaffingLines(Collections.emptyList());p.setBudgetMode("TOTAL");
        p.setStartupBudgetLimit(new BigDecimal("100"));p.getBudget().put("startupLimit",100);
        p.getBudget().put("businessAmount",20000);
        p.setExpenseLines(Collections.singletonList(row("amount",15000,"occurrenceType","ONE_TIME")));
        service.apply(p);
        assertEquals("READY",p.getBudget().get("status"));
        assertEquals(new BigDecimal("20000.00"),p.getBudgetLimit());
        assertNull(p.getStartupBudgetLimit());assertNull(p.getBudget().get("startupLimit"));
        assertFalse(p.getBudget().get("issues").toString().contains("启动预算"));
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
    @Test void previewAndSaveUseTheSameExtendedExpenseDateBoundary()
    {
        p.setStaffingLines(Collections.emptyList());p.setPlanStartDate(Date.valueOf("2026-09-08"));p.setBudgetMode("DAILY");p.setDailyBudgetLimit(new BigDecimal("500"));
        p.setExpenseLines(Collections.singletonList(row("expenseCategory","PROMOTION","itemName","推广","purpose","获客","amount",500,"occurrenceType","DAILY","occurDate","2026-02-28")));
        Map<String,Object> b=service.estimate(p);
        ServiceException error=assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(new BusinessProjectProposalServiceImpl(),"validatePlanDetails",p));
        assertEquals("PENDING",b.get("status"));assertTrue(((List<?>)b.get("issues")).contains(error.getMessage()));
        p.getExpenseLines().get(0).put("occurDate","2026-03-01");assertEquals("READY",service.estimate(p).get("status"));
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
    @Test void fullMonthsUseMonthlyFieldRegardlessOfStandardDaysAndMonthLength()
    {
        rate.put("unitCost",new BigDecimal("8000"));rate.put("standardWorkDays",new BigDecimal("21.75"));
        p.setPlanStartDate(Date.valueOf("2026-01-01"));staff.put("participationMode","FOLLOW_PROJECT");
        for(String month:Arrays.asList("2026-02-01","2026-07-01","2026-10-01")){
            p.getBudget().put("anchorDate",month);
            assertEquals(new BigDecimal("8000.00"),service.estimate(p).get("personnelAmount"),month);
        }
    }
    @Test void partialMonthUsesThatMonthsWorkdaysAndDailyFeesRemainDaily()
    {
        p.setPlanStartDate(Date.valueOf("2026-10-01"));p.getBudget().put("anchorDate","2026-10-01");
        staff.put("planStartDate","2026-10-01");staff.put("planEndDate","2026-10-14");
        rate.put("unitCost",new BigDecimal("8000"));rate.remove("standardWorkDays");
        assertEquals(new BigDecimal("3636.36"),service.estimate(p).get("personnelAmount"));
        rate.put("costMode","DAILY");rate.put("unitCost",100);
        assertEquals(new BigDecimal("1000.00"),service.estimate(p).get("personnelAmount"));
    }
    @Test void refreshedNextMonthDoesNotReplaceApprovedBudgetOrStaffPlan()
    {
        rate.put("unitCost",8000);rate.put("standardWorkDays",21.75);staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> saved=row("cycle","MONTH","businessAmount",500,"totalAmount",123,"personnelAmount",100);
        Map<String,Object> oldForecast=row("plannedTotalCost",9999);saved.put("steadyMonth",oldForecast);p.setBudget(saved);
        service.refreshMonthlyForecast(p);
        assertEquals(123,p.getBudget().get("totalAmount"));assertEquals(100,p.getBudget().get("personnelAmount"));
        assertSame(oldForecast,saved.get("steadyMonth"));
        assertEquals(new BigDecimal("8000.00"),p.getRecurringEstimatedTotalCost());
        assertEquals("2026-10-01",((Map<?,?>)p.getBudget().get("steadyMonth")).get("startDate"));
        assertSame(staff,p.getStaffingLines().get(0));
    }
    @Test void finiteDewiForecastUsesEachMonthAndKeepsApprovedWholeProjectAmount()
    {
        p.setPlanStartDate(Date.valueOf("2026-09-12"));p.setPlanEndDate(Date.valueOf("2026-12-11"));
        p.setStaffingLines(Collections.emptyList());
        p.setRevenueLines(Arrays.asList(row("expectedAmount",47700,"occurrenceType","ONE_TIME","expectedDate","2026-09-12"),
            row("expectedAmount",6000,"occurrenceType","MONTHLY","expectedDate","2026-09-12"),
            row("expectedAmount",2000,"occurrenceType","MONTHLY","expectedDate","2026-09-12")));
        Map<String,Object> budget=service.estimate(p);List<Map<String,Object>> months=months(budget);
        assertEquals("PROJECT",budget.get("cycle"));assertEquals(new BigDecimal("71966.67"),budget.get("revenueAmount"));
        assertEquals(Arrays.asList("52766.67","8266.67","8000.00","2933.33"),
            months.stream().map(m->m.get("revenueAmount").toString()).collect(java.util.stream.Collectors.toList()));
        assertEquals("2026-09-12",months.get(0).get("startDate"));assertEquals("2026-09-30",months.get(0).get("endDate"));
        assertEquals("2026-12-01",months.get(3).get("startDate"));assertEquals("2026-12-11",months.get(3).get("endDate"));
        assertEquals(budget.get("revenueAmount"),sum(months,"revenueAmount"));
        assertEquals("CALENDAR_MONTH_V2",budget.get("monthlyForecastVersion"));
    }
    @Test void unlimitedRefreshChangesOnlySteadyMonthAndKeepsSavedMonthlyHistory()
    {
        staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> saved=service.estimate(p);p.setBudget(saved);
        Map<String,Object> first=(Map<String,Object>)saved.get("firstMonth");
        List<Map<String,Object>> history=months(saved);
        BigDecimal originalFirstCost=(BigDecimal)first.get("personnelAmount");
        rate.put("unitCost",new BigDecimal("33000"));

        service.refreshMonthlyForecast(p);

        assertSame(first,p.getBudget().get("firstMonth"));assertSame(history,p.getBudget().get("monthlyForecasts"));
        assertEquals(originalFirstCost,first.get("personnelAmount"));
        assertEquals(saved.get("personnelAmount"),p.getBudget().get("personnelAmount"));
        assertEquals(saved.get("totalAmount"),p.getBudget().get("totalAmount"));
        assertEquals(new BigDecimal("33000.00"),((Map<?,?>)p.getBudget().get("steadyMonth")).get("personnelAmount"));
        assertEquals(new BigDecimal("33000.00"),p.getRecurringEstimatedTotalCost());
    }
    @Test void finiteForecastClipsStaffParticipationAcrossYearsWithoutChangingPlanDates()
    {
        p.setPlanStartDate(Date.valueOf("2026-12-15"));p.setPlanEndDate(Date.valueOf("2027-02-10"));
        staff.put("planStartDate","2026-12-21");staff.put("planEndDate","2027-01-15");
        rate.put("costMode","DAILY");rate.put("unitCost",100);p.getBudget().put("businessAmount",500);
        p.setExpenseLines(Collections.singletonList(row("amount",200,"occurrenceType","ONE_TIME","occurDate","2027-01-01")));
        Map<String,Object> budget=service.estimate(p);List<Map<String,Object>> months=months(budget);
        assertEquals(Arrays.asList("2026-12","2027-01","2027-02"),
            months.stream().map(m->m.get("month").toString()).collect(java.util.stream.Collectors.toList()));
        assertEquals(new BigDecimal("900.00"),months.get(0).get("personnelAmount"));
        assertEquals(new BigDecimal("1100.00"),months.get(1).get("personnelAmount"));
        assertEquals(new BigDecimal("0.00"),months.get(2).get("personnelAmount"));
        assertEquals(new BigDecimal("0.00"),months.get(0).get("plannedBusinessAmount"));
        assertEquals(new BigDecimal("200.00"),months.get(1).get("plannedBusinessAmount"));
        assertEquals(budget.get("personnelAmount"),sum(months,"personnelAmount"));
        assertEquals(budget.get("plannedTotalCost"),sum(months,"plannedTotalCost"));
        assertEquals("2026-12-21",staff.get("planStartDate"));assertEquals("2027-01-15",staff.get("planEndDate"));
    }
    @Test void finiteForecastMonthlyPersonnelRoundingAndRateGapsRemainLocalToEachMonth()
    {
        p.setPlanStartDate(Date.valueOf("2026-09-12"));p.setPlanEndDate(Date.valueOf("2026-12-11"));
        staff.put("participationMode","FOLLOW_PROJECT");rate.put("unitCost",new BigDecimal("12345.67"));
        Map<String,Object> budget=service.estimate(p);
        assertEquals(budget.get("personnelAmount"),sum(months(budget),"personnelAmount"));
        assertEquals(new BigDecimal("12345.67"),months(budget).get(1).get("personnelAmount"));
        rate.put("effectiveTo","2026-10-31");budget=service.estimate(p);
        assertNull(budget.get("personnelAmount"));assertEquals("READY",months(budget).get(1).get("status"));
        assertNull(months(budget).get(2).get("personnelAmount"));assertNull(months(budget).get(2).get("profit"));
        assertEquals("PENDING",months(budget).get(2).get("status"));
        assertTrue(months(budget).get(2).get("issues").toString().contains("缺少有效成本费率"));
    }
    @Test void undatedOneTimeAmountsAndFundingOccurOnlyInFirstForecastMonth()
    {
        p.setPlanEndDate(Date.valueOf("2026-11-30"));p.setStaffingLines(Collections.emptyList());
        p.setParentProjectId(1L);p.setParentFundingAmount(new BigDecimal("2000"));
        p.setRevenueLines(Collections.singletonList(row("expectedAmount",1000,"occurrenceType","ONE_TIME")));
        p.setExpenseLines(Collections.singletonList(row("amount",200,"occurrenceType","ONE_TIME")));
        Map<String,Object> budget=service.estimate(p);List<Map<String,Object>> months=months(budget);
        assertEquals(new BigDecimal("3000.00"),months.get(0).get("revenueAmount"));
        assertEquals(new BigDecimal("200.00"),months.get(0).get("plannedBusinessAmount"));
        for(int i=1;i<months.size();i++){
            assertEquals(new BigDecimal("0.00"),months.get(i).get("revenueAmount"));
            assertEquals(new BigDecimal("0.00"),months.get(i).get("plannedBusinessAmount"));
        }
        assertEquals(budget.get("revenueAmount"),sum(months,"revenueAmount"));
    }
    @Test void finiteMonthCentRemaindersReconcileRevenueCostsAndProfitToWholeProject()
    {
        p.setPlanEndDate(Date.valueOf("2026-12-16"));p.setStaffingLines(Collections.emptyList());
        p.setRevenueLines(Collections.singletonList(row("expectedAmount",new BigDecimal("0.01"),"occurrenceType","WEEKLY")));
        p.setExpenseLines(Collections.singletonList(row("amount",new BigDecimal("0.01"),"occurrenceType","MONTHLY")));
        Map<String,Object> budget=service.estimate(p);List<Map<String,Object>> months=months(budget);
        assertEquals(new BigDecimal("0.15"),budget.get("revenueAmount"));
        assertEquals(new BigDecimal("0.04"),budget.get("plannedBusinessAmount"));
        for(String key:Arrays.asList("revenueAmount","plannedBusinessAmount","plannedTotalCost","profit"))
            assertEquals(budget.get(key),sum(months,key),key);
    }
    @Test void oldFiniteSnapshotRefreshRequiresSamePersonnelBasisAndPreservesFrozenTotals()
    {
        p.setPlanEndDate(Date.valueOf("2026-10-31"));staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> original=service.estimate(p);original.remove("monthlyForecastVersion");
        // Old snapshots may omit per-person totals while retaining complete daily rate references.
        ((List<Map<String,Object>>)original.get("staffingStatus")).forEach(s->s.remove("amount"));
        p.setBudget(original);p.setBudgetLimit(new BigDecimal("77777"));p.setEstimatedPersonnelCost(new BigDecimal("88888"));
        service.refreshMonthlyForecast(p);
        assertEquals(original.get("personnelAmount"),sum(months(p.getBudget()),"personnelAmount"));
        assertEquals(original.get("totalAmount"),p.getBudget().get("totalAmount"));
        assertEquals(new BigDecimal("77777"),p.getBudgetLimit());assertEquals(new BigDecimal("88888"),p.getEstimatedPersonnelCost());
        p.setBudget(original);rate.put("version",2);service.refreshMonthlyForecast(p);
        for(Map<String,Object> month:months(p.getBudget())){
            assertNull(month.get("personnelAmount"));assertNull(month.get("plannedTotalCost"));assertNull(month.get("profit"));
            assertEquals("PENDING",month.get("status"));assertTrue(month.get("issues").toString().contains("暂无法还原原计划的分月人员成本"));
        }
        assertEquals(original.get("personnelAmount"),p.getBudget().get("personnelAmount"));
        assertEquals(original.get("totalAmount"),p.getBudget().get("totalAmount"));
        p.setBudget(original);rate.put("version",1);rate.put("unitCost",33000);service.refreshMonthlyForecast(p);
        assertNull(months(p.getBudget()).get(0).get("personnelAmount"));
        assertEquals(original.get("personnelAmount"),p.getBudget().get("personnelAmount"));
    }
    @Test void oldFiniteSnapshotWithoutBasisKeepsIncomeButDoesNotInventPersonnelAmounts()
    {
        p.setPlanEndDate(Date.valueOf("2026-10-31"));staff.put("participationMode","FOLLOW_PROJECT");
        p.setRevenueLines(Collections.singletonList(row("expectedAmount",1000,"expectedDate","2026-10-01")));
        Map<String,Object> saved=row("businessAmount",500,"personnelAmount",44000,"totalAmount",44500);p.setBudget(saved);
        service.refreshMonthlyForecast(p);
        assertEquals(new BigDecimal("1000.00"),months(p.getBudget()).get(1).get("revenueAmount"));
        assertNull(months(p.getBudget()).get(1).get("personnelAmount"));
        assertEquals(44500,p.getBudget().get("totalAmount"));assertFalse(saved.containsKey("monthlyForecasts"));
    }
    @Test void historicalPeriodAndKnownZeroPersonnelOverrideShiftedRootDates()
    {
        p.setPlanStartDate(Date.valueOf("2026-08-31"));p.setPlanEndDate(Date.valueOf("2026-10-30"));
        p.setStaffingLines(Collections.emptyList());
        Map<String,Object> saved=row("startDate","2026-09-01","endDate","2026-10-31","businessAmount",500,"personnelAmount",0,"totalAmount",500);
        p.setBudget(saved);service.refreshMonthlyForecast(p);
        assertEquals("2026-09",months(p.getBudget()).get(0).get("month"));
        assertEquals("2026-09-01",months(p.getBudget()).get(0).get("startDate"));
        assertEquals("2026-10-31",months(p.getBudget()).get(1).get("endDate"));
        assertEquals(new BigDecimal("0.00"),months(p.getBudget()).get(0).get("personnelAmount"));
        assertEquals(Date.valueOf("2026-08-31"),p.getPlanStartDate());
    }
    @Test void correctedFiniteSnapshotDoesNotRepriceWhenReadAgain()
    {
        p.setPlanEndDate(Date.valueOf("2026-10-31"));staff.put("participationMode","FOLLOW_PROJECT");
        Map<String,Object> saved=service.estimate(p);p.setBudget(saved);
        clearInvocations(mapper,proposals);rate.put("unitCost",999999);service.refreshMonthlyForecast(p);
        assertSame(saved,p.getBudget());verifyNoInteractions(mapper,proposals);
    }
    @Test void longForecastCentRemainderNeverCreatesNegativeRevenueOrExpense()
    {
        p.setPlanStartDate(Date.valueOf("2026-01-01"));p.setPlanEndDate(Date.valueOf("2030-12-31"));
        p.setStaffingLines(Collections.emptyList());
        p.setRevenueLines(Collections.singletonList(row("expectedAmount",new BigDecimal("0.02"),"occurrenceType","WEEKLY")));
        p.setExpenseLines(Collections.singletonList(row("amount",new BigDecimal("0.02"),"occurrenceType","WEEKLY")));
        Map<String,Object> budget=service.estimate(p);List<Map<String,Object>> months=months(budget);
        assertEquals(60,months.size());
        for(String key:Arrays.asList("revenueAmount","plannedBusinessAmount")){
            assertEquals(budget.get(key),sum(months,key));
            assertTrue(months.stream().allMatch(m->((BigDecimal)m.get(key)).signum()>=0),key);
        }
    }
    private List<Map<String,Object>> months(Map<String,Object> budget){return (List<Map<String,Object>>)budget.get("monthlyForecasts");}
    private BigDecimal sum(List<Map<String,Object>> rows,String key){return rows.stream().map(r->(BigDecimal)r.get(key)).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);}
}
