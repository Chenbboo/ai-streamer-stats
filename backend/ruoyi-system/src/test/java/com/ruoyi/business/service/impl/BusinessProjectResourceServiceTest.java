package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BusinessProjectResourceServiceTest {
    @Mock BusinessProjectMapper projects;
    @Mock BusinessAccountingMapper accounting;
    @Mock BusinessProjectResourceMapper resources;
    @Mock IBusinessProjectService staffCosts;
    @Mock BusinessCompanyAccessService access;
    @InjectMocks BusinessProjectResourceService service;
    BusinessProject project;
    @BeforeEach void setup(){project=new BusinessProject();project.setProjectId(1L);project.setCompanyDeptId(20L);project.setProjectName("项目");project.setMainOwnerUserId(10L);project.setDelFlag("0");project.setStatus("ACTIVE");project.setBaseCurrency("CNY");project.setBudgetMode("TOTAL");project.setBudgetLimit(new BigDecimal("1000"));project.setPlanStartDate(Date.valueOf("2026-01-01"));lenient().when(projects.selectProjectById(1L)).thenReturn(project);lenient().doThrow(new ServiceException("无成员成本查看权限")).when(staffCosts).staffCostPolicies(anyLong(),anyLong(),anyBoolean());}
    @Test void deniesOtherOwnerBeforeReadingCosts(){assertThrows(ServiceException.class,()->service.personnel(1L,null,null,false,11L,false));verifyNoInteractions(resources,accounting);}
    @Test void deniesOtherCompanyBeforeListingPortfolio(){assertThrows(ServiceException.class,()->service.portfolio(21L,null,10L,false));verifyNoInteractions(accounting,resources);}
    private void salarySnapshot(){
        when(resources.personnel(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("userId",32L,"amount",80,"workingDays",2)));
        when(resources.totals(anyLong(),anyString(),anyString())).thenReturn(row("personnelCost",80,"resultCount",2));
    }
    private BusinessStaffCostPolicy salaryPolicy(String monthly,String days,String currency){
        BusinessStaffCostPolicy policy=new BusinessStaffCostPolicy();policy.setCostMode("MONTHLY");policy.setUnitCost(new BigDecimal(monthly));policy.setStandardWorkDays(new BigDecimal(days));policy.setCurrency(currency);policy.setStatus("ACTIVE");policy.setEffectiveFrom(Date.valueOf("2020-01-01"));return policy;
    }
    @Test void projectAccessAloneDoesNotExposeDailySalary(){
        salarySnapshot();
        Map<String,Object> result=service.personnel(1L,"2026-01-01","2026-01-31",false,10L,false);
        Map<String,Object> person=((List<Map<String,Object>>)result.get("rows")).get(0);
        assertEquals(false,result.get("rawCostVisible"));
        for(String field:Arrays.asList("dailySalary","salaryMonthlyCost","salaryStandardWorkDays","salaryCurrency","salaryDate"))assertFalse(person.containsKey(field));
        assertEquals(false,person.get("rawCostVisible"));verify(staffCosts).staffCostPolicies(32L,10L,false);
        assertEquals(80,person.get("amount"));
    }
    @Test void dailySalaryUsesCurrentHrPolicyRatherThanProjectSnapshotOrDayCount(){
        salarySnapshot();LoginUser login=new LoginUser();login.setPermissions(new HashSet<>(Arrays.asList("business:staff:cost")));
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        BusinessStaffCostPolicy future=salaryPolicy("5000","21.75","CNY");future.setEffectiveFrom(Date.valueOf("2099-01-01"));
        doReturn(Arrays.asList(future,salaryPolicy("3300","21.75","CNY"))).when(staffCosts).staffCostPolicies(32L,10L,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null));
        try{
            Map<String,Object> result=service.personnel(1L,"2026-01-01","2026-01-31",false,10L,false);
            Map<String,Object> person=((List<Map<String,Object>>)result.get("rows")).get(0);
            assertEquals(true,result.get("rawCostVisible"));assertEquals(new BigDecimal("151.7241"),person.get("dailySalary"));assertEquals(new BigDecimal("3300"),person.get("salaryMonthlyCost"));assertEquals(new BigDecimal("21.75"),person.get("salaryStandardWorkDays"));assertEquals(today,person.get("salaryDate"));assertEquals(80,person.get("amount"));
        }finally{SecurityContextHolder.clearContext();}
    }
    @Test void vietnamSalaryUsesTwentySixDaysAndOriginalCurrency(){
        salarySnapshot();doReturn(Arrays.asList(salaryPolicy("2600000","26","VND"))).when(staffCosts).staffCostPolicies(32L,10L,true);
        Map<String,Object> person=((List<Map<String,Object>>)service.personnel(1L,"2026-01-01","2026-01-31",false,10L,true).get("rows")).get(0);
        assertEquals(new BigDecimal("100000.0000"),person.get("dailySalary"));assertEquals("VND",person.get("salaryCurrency"));
    }
    @Test void storedHrDivisorCannotOverrideFixedSalaryBasis(){
        salarySnapshot();doReturn(Arrays.asList(salaryPolicy("3300","0","CNY"))).when(staffCosts).staffCostPolicies(32L,10L,true);
        Map<String,Object> person=((List<Map<String,Object>>)service.personnel(1L,"2026-01-01","2026-01-31",false,10L,true).get("rows")).get(0);
        assertEquals(new BigDecimal("151.7241"),person.get("dailySalary"));
    }
    @Test void currentMemberWithNoRecognizedCostsCanStillShowHrSalary(){
        BusinessProjectMember member=new BusinessProjectMember();member.setUserId(33L);member.setUserNameSnapshot("新成员");member.setStatus("0");member.setMemberRole("MEMBER");
        when(projects.selectMembers(1L)).thenReturn(Arrays.asList(member));
        when(resources.totals(anyLong(),anyString(),anyString())).thenReturn(row("personnelCost",0,"resultCount",0));
        doReturn(Arrays.asList(salaryPolicy("3300","21.75","CNY"))).when(staffCosts).staffCostPolicies(33L,10L,true);
        Map<String,Object> person=((List<Map<String,Object>>)service.personnel(1L,"2026-01-01","2026-01-31",false,10L,true).get("rows")).get(0);
        assertEquals(new BigDecimal("151.7241"),person.get("dailySalary"));assertEquals(BigDecimal.ZERO,person.get("amount"));
    }
    @Test void projectOwnerCanReadManagedMemberSalaryWithoutGlobalCostPermission(){
        salarySnapshot();doReturn(Arrays.asList(salaryPolicy("3300","21.75","CNY"))).when(staffCosts).staffCostPolicies(32L,10L,false);
        Map<String,Object> person=((List<Map<String,Object>>)service.personnel(1L,"2026-01-01","2026-01-31",false,10L,false).get("rows")).get(0);
        assertEquals(true,person.get("rawCostVisible"));assertEquals(new BigDecimal("151.7241"),person.get("dailySalary"));
    }
    @Test void salaryVisibilityIsCheckedPerMemberEvenWhenAnotherMemberIsAuthorized(){
        when(resources.personnel(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("userId",32L,"amount",80),row("userId",33L,"amount",20)));
        when(resources.totals(anyLong(),anyString(),anyString())).thenReturn(row("personnelCost",100,"resultCount",2));
        doReturn(Arrays.asList(salaryPolicy("3300","21.75","CNY"))).when(staffCosts).staffCostPolicies(32L,10L,false);
        Map<String,Object> result=service.personnel(1L,"2026-01-01","2026-01-31",false,10L,false);
        List<Map<String,Object>> people=(List<Map<String,Object>>)result.get("rows");
        assertEquals(true,result.get("rawCostVisible"));assertEquals(true,people.get(0).get("rawCostVisible"));assertEquals(false,people.get(1).get("rawCostVisible"));assertFalse(people.get(1).containsKey("dailySalary"));assertFalse(people.get(1).containsKey("salaryMonthlyCost"));
    }
    @Test void formerMembersKeepHistoricalCostsAndActiveZeroMembersRemainVisible(){
        BusinessProjectMember member=new BusinessProjectMember();member.setUserId(33L);member.setUserNameSnapshot("零成本成员");member.setStatus("0");member.setMemberRole("MEMBER");
        when(projects.selectMembers(1L)).thenReturn(Arrays.asList(member));
        when(projects.selectUserAllocationTimeline(33L)).thenReturn(Arrays.asList(row("projectId",1L,"allocationId",3L,"effectiveFrom","2020-01-01","allocationValue",25)));
        when(resources.personnel(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("userId",32L,"userName","退出成员","amount",80)));
        when(resources.totals(anyLong(),anyString(),anyString())).thenReturn(row("personnelCost",100,"resultCount",2));
        Map<String,Object> result=service.personnel(1L,"2026-01-01","2026-01-31",false,10L,false);
        List<Map<String,Object>> rows=(List<Map<String,Object>>)result.get("rows");
        assertEquals(3,rows.size());assertNull(rows.get(0).get("canAdjust"));assertEquals(new BigDecimal("20"),rows.get(1).get("amount"));assertTrue((Boolean)rows.get(1).get("unattributed"));assertEquals(25,rows.get(2).get("allocationPercent"));assertEquals(BigDecimal.ZERO,rows.get(2).get("amount"));
        verify(projects,never()).selectProjectByIdForUpdate(anyLong());
    }
    @Test void incompleteCostSuppressesRemainingBudget(){when(accounting.sumProjectCostToDate(anyLong(),any())).thenReturn(new BigDecimal("200"));when(resources.unrecognizedDays(anyLong(),anyString(),anyString())).thenReturn(1);Map<String,Object> result=service.budget(1L,"2026-01-09",10L,false);assertEquals(1,result.get("pendingCount"));assertNull(result.get("remaining"));assertEquals(new BigDecimal("200"),result.get("used"));}
    @Test void monthlyBudgetNeverIncludesOtherMonthsAndStopsAtItsEnd(){project.setTemplateSnapshotJson("{\"budget\":{\"cycle\":\"MONTH\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}}");when(accounting.sumProjectCostInPeriod(1L,Date.valueOf("2026-01-01"),Date.valueOf("2026-01-31"))).thenReturn(new BigDecimal("1200"));Map<String,Object> result=service.budget(1L,"2026-02-09",10L,false);assertEquals(new BigDecimal("-200"),result.get("remaining"));assertEquals("MONTH",result.get("cycle"));verify(accounting,never()).sumProjectCostToDate(anyLong(),any());}
    @Test void cashDailyReferenceUsesSingleDayAndNeverMisstatesStartupAsBudgetOverrun(){project.setBudgetMode("DAILY");project.setBudgetScope("CASH_EXPENSE");project.setDailyBudgetLimit(new BigDecimal("10"));when(resources.externalCost(1L,"2026-01-09","2026-01-09")).thenReturn(new BigDecimal("80"));Map<String,Object> result=service.budget(1L,"2026-01-09",10L,false);assertFalse((Boolean)result.get("comparable"));assertNull(result.get("remaining"));assertEquals("2026-01-09",result.get("periodStart"));verifyNoInteractions(accounting);}
    @Test void noLimitDoesNotManufacturePercentageOrRemaining(){project.setBudgetMode("NONE");when(accounting.sumProjectCostToDate(anyLong(),any())).thenReturn(BigDecimal.ZERO);Map<String,Object> result=service.budget(1L,"2026-01-09",10L,false);assertNull(result.get("limit"));assertNull(result.get("remaining"));}
    @Test void projectBudgetKeepsExistingCumulativeAccountingEvenAfterPlannedEnd(){project.setPlanEndDate(Date.valueOf("2026-01-02"));when(accounting.sumProjectCostToDate(1L,Date.valueOf("2026-01-09"))).thenReturn(new BigDecimal("100"));assertEquals(new BigDecimal("900"),service.budget(1L,"2026-01-09",10L,false).get("remaining"));verify(accounting,never()).sumProjectCostInPeriod(anyLong(),any(),any());}
    @Test void portfolioRespectsCompanyBoundary(){when(access.allowed(10L,20L,"BUSINESS")).thenReturn(true);when(accounting.selectProjectOptions(10L,false,true)).thenReturn(Arrays.asList(row("projectId",1L,"companyDeptId",20L),row("projectId",2L,"companyDeptId",21L)));when(accounting.sumProjectCostToDate(anyLong(),any())).thenReturn(BigDecimal.ZERO);assertEquals(1,service.portfolio(20L,"2026-01-09",10L,false).size());verify(projects,never()).selectProjectById(2L);}
}
