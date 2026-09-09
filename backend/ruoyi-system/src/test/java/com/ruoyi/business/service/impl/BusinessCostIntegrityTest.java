package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;

class BusinessCostIntegrityTest {
    private BusinessMemberDayCostServiceTest fixture(){BusinessMemberDayCostServiceTest f=new BusinessMemberDayCostServiceTest();f.setup();return f;}

    @Test void voidRateCannotReplacePricedHistoryWithMissingCost(){
        BusinessMemberDayCostServiceTest f=fixture();f.project.setActualEndDate(Date.valueOf("2026-09-04"));
        List<Map<String,Object>> stored=f.week();when(f.costs.selectCosts(1L)).thenReturn(stored);
        when(f.work.selectBudgetRates(eq(7L),anyString(),anyString())).thenReturn(Collections.emptyList());
        f.service.synchronize(1L);verify(f.costs,never()).deleteDay(anyLong(),anyString());verifyNoInteractions(f.accounting);
        assertEquals(new BigDecimal("1000.00"),f.service.overview(1L,"2026-09-01").get(0).get("dailyCost"));
    }
    @Test void roleChangeDoesNotDeletePricedHistory(){
        BusinessMemberDayCostServiceTest f=fixture();f.project.setActualEndDate(Date.valueOf("2026-09-04"));
        List<Map<String,Object>> stored=f.week();when(f.costs.selectCosts(1L)).thenReturn(stored);f.member.put("memberRole","OBSERVER");
        f.service.synchronize(1L);verify(f.costs,never()).deleteDay(anyLong(),anyString());
        assertEquals(5,f.week().size());
    }
    @Test void rolePeriodsPreservePreviouslyUnpricedParticipationBeforeChange(){
        BusinessMemberDayCostServiceTest f=fixture();f.member.put("memberRole","OBSERVER");
        when(f.costs.selectRolePeriods(1L)).thenReturn(Arrays.asList(row("userId",7L,"effectiveFrom","2026-08-31","memberRole","MEMBER"),row("userId",7L,"effectiveFrom","2026-09-03","memberRole","OBSERVER")));
        assertEquals(3,f.week().size());
    }
    @Test void stillParticipatingAfterPlannedEndContinuesAccruing(){
        BusinessMemberDayCostServiceTest f=fixture();f.project.setPlanEndDate(Date.valueOf("2026-09-04"));
        assertEquals(2,f.service.calculate(f.project,LocalDate.parse("2026-09-07"),LocalDate.parse("2026-09-08")).size());
        f.project.setActualEndDate(Date.valueOf("2026-09-07"));
        assertEquals(1,f.service.calculate(f.project,LocalDate.parse("2026-09-07"),LocalDate.parse("2026-09-08")).size());
    }
    @Test void followingProjectSurvivesExtensionButCustomEndIsRespected(){
        BusinessMemberDayCostServiceTest f=fixture();f.project.setPlanEndDate(Date.valueOf("2026-09-11"));
        Map<String,Object> a=row("userId",7L,"status","ACTIVE","effectiveFrom","2026-08-31","effectiveTo","2026-09-04","participationMode","FOLLOW_PROJECT","calendarId",1L);
        when(f.work.selectAssignments(1L)).thenReturn(Collections.singletonList(a));
        assertEquals(5,f.service.calculate(f.project,LocalDate.parse("2026-09-07"),LocalDate.parse("2026-09-11")).size());
        a.put("participationMode","CUSTOM");assertEquals(0,f.service.calculate(f.project,LocalDate.parse("2026-09-07"),LocalDate.parse("2026-09-11")).size());
    }
    @Test void incompleteDayCanBePricedWithoutReplacingOtherPeoplesFacts(){
        BusinessMemberDayCostServiceTest f=fixture();f.project.setActualEndDate(Date.valueOf("2026-09-04"));
        List<Map<String,Object>> stored=f.week();Map<String,Object> first=stored.get(0);first.put("amount",null);first.put("pricingStatus","PENDING");first.put("basisJson","{\"issue\":\"missing\"}");
        when(f.costs.selectCosts(1L)).thenReturn(stored);f.service.synchronize(1L);
        verify(f.costs,times(1)).deleteDay(1L,"2026-08-31");verify(f.costs,times(1)).insertCost(argThat(c->"PRICED".equals(c.get("pricingStatus"))));
    }
    @Test void financialReadModelIncludesReferencedMonthlyRateAndStaffMetadata(){
        BusinessMemberDayCostServiceTest f=fixture();
        when(f.costs.selectStaffMetadata(7L,2L)).thenReturn(row("monthlyCost",new BigDecimal("22000"),"costMode","MONTHLY","countryRegion","CN","companyName","测试公司"));
        Map<String,Object> result=f.service.overview(1L,"2026-09-01").get(0);
        assertEquals("CN",result.get("countryRegion"));assertEquals("测试公司",result.get("companyName"));assertEquals(new BigDecimal("22000"),new BigDecimal(String.valueOf(result.get("monthlyCost"))));assertEquals("READY",result.get("costStatus"));
    }
    @Test void ownerIsAddedOnceToTheBudgetAndMissingOwnerRateBlocksReadiness(){
        BusinessProjectBudgetServiceTest f=new BusinessProjectBudgetServiceTest();f.setup();f.p.setApplicantUserId(10L);
        when(f.mapper.selectCalendars()).thenReturn(Collections.singletonList(f.calendar));
        when(f.mapper.selectUnitPolicies()).thenReturn(Collections.singletonList(row("unitPolicyId",1L)));
        when(f.proposals.selectProposalStaff(eq(10L),any())).thenReturn(row("userId",10L,"companyDeptId",10L,"nickName","负责人"));
        Map<String,Object> budget=f.service.estimate(f.p);assertEquals("PENDING",budget.get("status"));assertNull(budget.get("personnelAmount"));assertEquals(2,f.p.getStaffingLines().size());
        when(f.mapper.selectBudgetRates(eq(10L),anyString(),anyString())).thenReturn(Collections.singletonList(f.rate));
        budget=f.service.estimate(f.p);assertEquals("READY",budget.get("status"));assertEquals(new BigDecimal("44000.00"),budget.get("personnelAmount"));assertEquals(2,f.p.getStaffingLines().size());
    }
}
