package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessAccountingServiceImplTest
{
    @Mock BusinessAccountingMapper mapper;
    @InjectMocks BusinessAccountingServiceImpl service;

    @Test void otherBossCannotCreateFactForForeignProject()
    {
        Map<String,Object> project=project(20L,8L);
        when(mapper.selectProjectForAccounting(20L)).thenReturn(project);
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(20L);
        ServiceException error=assertThrows(ServiceException.class,()->service.saveFact(fact,9L,"boss9",false));
        assertTrue(error.getMessage().contains("其他老板"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void dailyProfitIncludesManualAndPersonnelCostAndCreatesItems()
    {
        Date day=new Date();Map<String,Object> project=project(21L,8L);
        when(mapper.selectProjectForAccounting(21L)).thenReturn(project);
        Map<String,Object> sums=new HashMap<String,Object>();sums.put("revenueAmount",new BigDecimal("1000"));
        sums.put("costAmount",new BigDecimal("200"));sums.put("adjustmentAmount",new BigDecimal("50"));sums.put("valueScore",BigDecimal.ZERO);
        when(mapper.sumProjectFacts(21L,day)).thenReturn(sums);
        when(mapper.sumProjectPersonnelCost(21L,day)).thenReturn(new BigDecimal("300"));
        when(mapper.sumProjectCostToDate(21L,day)).thenReturn(new BigDecimal("500"));
        when(mapper.selectNextResultVersion(21L,day)).thenReturn(2);
        doAnswer(invocation->{((Map<String,Object>)invocation.getArgument(0)).put("resultId",99L);return 1;}).when(mapper).insertDailyResult(any());

        Map<String,Object> result=service.recalculate(21L,day,8L,"boss8",false);

        assertEquals(new BigDecimal("550"),result.get("profitAmount"));
        assertEquals(2,result.get("resultVersion"));
        verify(mapper,times(5)).insertDailyResultItem(any());
    }

    @Test void recalculationStoresOnePersonnelSnapshotItemPerPerson()
    {
        Date day=new Date();Map<String,Object> project=project(22L,8L);
        when(mapper.selectProjectForAccounting(22L)).thenReturn(project);
        when(mapper.sumProjectFacts(22L,day)).thenReturn(Collections.emptyMap());
        when(mapper.sumProjectPersonnelCost(22L,day)).thenReturn(new BigDecimal("137.931"));
        when(mapper.sumProjectCostToDate(22L,day)).thenReturn(new BigDecimal("137.931"));
        when(mapper.selectNextResultVersion(22L,day)).thenReturn(1);
        Map<String,Object> person=new HashMap<String,Object>();person.put("componentName","石头");
        person.put("amount",new BigDecimal("137.931"));person.put("calculationDetail","计划投入 30%；月成本 10000 / 21.75 天");
        when(mapper.selectProjectPersonnelCostDetails(22L,day)).thenReturn(Collections.singletonList(person));
        doAnswer(invocation->{((Map<String,Object>)invocation.getArgument(0)).put("resultId",100L);return 1;})
            .when(mapper).insertDailyResult(any());

        service.recalculate(22L,day,8L,"boss8",false);

        @SuppressWarnings({"rawtypes","unchecked"})
        ArgumentCaptor<Map<String,Object>> itemCaptor=(ArgumentCaptor)ArgumentCaptor.forClass(Map.class);
        verify(mapper,times(6)).insertDailyResultItem(itemCaptor.capture());
        Map<String,Object> personnelItem=itemCaptor.getAllValues().get(3);
        assertEquals("PERSONNEL_COST_PERSON",personnelItem.get("componentCode"));
        assertEquals("石头",personnelItem.get("componentName"));
        assertEquals(new BigDecimal("137.931"),personnelItem.get("amount"));
    }

    @Test void projectBonusIsIdempotentAndImmediatelyRecalculates()
    {
        Date day=java.sql.Date.valueOf("2026-08-19");Map<String,Object> project=project(23L,8L);
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryId",17L);
        category.put("categoryName","项目绩效奖金");
        when(mapper.selectProjectForAccounting(23L)).thenReturn(project);
        when(mapper.selectCategoryByCode("PROJECT_BONUS_COST")).thenReturn(category);
        when(mapper.sumProjectFacts(23L,day)).thenReturn(Collections.emptyMap());
        when(mapper.sumProjectPersonnelCost(23L,day)).thenReturn(BigDecimal.ZERO);
        when(mapper.sumProjectCostToDate(23L,day)).thenReturn(new BigDecimal("30000"));
        when(mapper.selectNextResultVersion(23L,day)).thenReturn(1);
        doAnswer(invocation->{BusinessOperatingFact fact=invocation.getArgument(0);fact.setFactId(71L);return 1;})
            .when(mapper).insertFact(any());
        doAnswer(invocation->{((Map<String,Object>)invocation.getArgument(0)).put("resultId",101L);return 1;})
            .when(mapper).insertDailyResult(any());

        BusinessOperatingFact fact=service.recordProjectBonus(23L,day,new BigDecimal("30000"),51L,8L,"boss8");

        assertEquals(71L,fact.getFactId());
        assertEquals("KPI-BONUS-SETTLEMENT-51",fact.getIdempotencyKey());
        assertEquals("PROJECT_BONUS_COST",fact.getCategoryCode());
        verify(mapper).insertDailyResult(any());
    }

    @Test void oldResultGetsPersonnelBreakdownWithoutChangingTheSnapshot()
    {
        Map<String,Object> result=new HashMap<String,Object>();result.put("resultId",9L);result.put("projectId",22L);
        result.put("bizDate",java.sql.Date.valueOf("2026-08-11"));
        when(mapper.selectDailyResults(any())).thenReturn(Collections.singletonList(result));
        Map<String,Object> total=new HashMap<String,Object>();total.put("componentCode","PERSONNEL_COST");
        when(mapper.selectDailyResultItems(9L)).thenReturn(Collections.singletonList(total));
        Map<String,Object> person=new HashMap<String,Object>();person.put("componentName","石头");person.put("amount",new BigDecimal("137.931"));
        when(mapper.selectProjectPersonnelCostDetails(eq(22L),any())).thenReturn(Collections.singletonList(person));

        Map<String,Object> detail=service.resultDetail(9L,8L,false);

        List<?> personnel=(List<?>)detail.get("personnelItems");
        assertEquals(1,personnel.size());
        assertEquals("石头",((Map<?,?>)personnel.get(0)).get("componentName"));
        verify(mapper,never()).insertDailyResult(any());
    }

    @Test void bossOverviewAlwaysCarriesInitiatorScope()
    {
        when(mapper.countProjectsMissingDailyResult(eq(142L),eq(false),any())).thenReturn(3);
        service.bossOverview(142L,false);

        @SuppressWarnings({"rawtypes","unchecked"})
        ArgumentCaptor<Map<String,Object>> captor=(ArgumentCaptor)ArgumentCaptor.forClass(Map.class);
        verify(mapper).countDraftFacts(captor.capture());
        assertEquals(142L,captor.getValue().get("userId"));
        assertEquals(false,captor.getValue().get("viewAll"));
    }

    @Test void personnelCostOverviewSummarizesReadinessAndKeepsBossScope()
    {
        Map<String,Object> ready=new HashMap<String,Object>();ready.put("costStatus","READY");
        ready.put("personnelCost",new BigDecimal("129.5000"));
        Map<String,Object> missing=new HashMap<String,Object>();missing.put("costStatus","MISSING_COST");
        missing.put("personnelCost",BigDecimal.ZERO);
        Map<String,Object> over=new HashMap<String,Object>();over.put("costStatus","OVER_ALLOCATED");
        over.put("personnelCost",new BigDecimal("80.2500"));
        when(mapper.selectPersonnelCostOverview(any())).thenReturn(java.util.Arrays.asList(ready,missing,over));
        Map<String,Object> query=new HashMap<String,Object>();query.put("bizDate","2026-08-19");

        Map<String,Object> result=service.personnelCostOverview(query,142L,false);

        assertEquals(1,result.get("readyCount"));
        assertEquals(2,result.get("issueCount"));
        assertEquals(1,result.get("overAllocatedCount"));
        assertEquals(new BigDecimal("209.7500"),result.get("personnelCost"));
        @SuppressWarnings({"rawtypes","unchecked"})
        ArgumentCaptor<Map<String,Object>> captor=(ArgumentCaptor)ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectPersonnelCostOverview(captor.capture());
        assertEquals(142L,captor.getValue().get("userId"));
        assertEquals(false,captor.getValue().get("viewAll"));
        assertEquals("2026-08-19",captor.getValue().get("bizDate"));
    }

    @Test void bossOverviewIsReadOnlyAndReportsMissingDailyResults()
    {
        when(mapper.countProjectsMissingDailyResult(eq(142L),eq(false),any())).thenReturn(27);

        Map<String,Object> result=service.bossOverview(142L,false);

        assertEquals(27,result.get("missingDailyResultCount"));
        verify(mapper,never()).selectProjectForAccounting(any());
        verify(mapper,never()).insertDailyResult(any());
    }

    @Test void bossOverviewGroupsPersonnelSetupIssuesByStaffMember()
    {
        Map<String,Object> firstProject=new HashMap<String,Object>();
        firstProject.put("userId",9L);firstProject.put("userName","石头");
        firstProject.put("projectId",21L);firstProject.put("projectName","王老吉视频宣传");
        firstProject.put("costStatus","MISSING_REGION");
        Map<String,Object> secondProject=new HashMap<String,Object>();
        secondProject.put("userId",9L);secondProject.put("userName","石头");
        secondProject.put("projectId",22L);secondProject.put("projectName","情趣内衣视频制作");
        secondProject.put("costStatus","MISSING_REGION");
        Map<String,Object> missingCost=new HashMap<String,Object>();
        missingCost.put("userId",10L);missingCost.put("userName","蒋豪");
        missingCost.put("projectId",23L);missingCost.put("projectName","新谷酵素视频剪辑");
        missingCost.put("profileCountryRegion","CN");missingCost.put("costStatus","MISSING_COST");
        when(mapper.selectCompanyPersonnelCostReadiness(eq(142L),eq(false),any()))
            .thenReturn(java.util.Arrays.asList(firstProject,secondProject,missingCost));

        Map<String,Object> result=service.bossOverview(142L,false);

        @SuppressWarnings("unchecked") Map<String,Object> readiness=(Map<String,Object>)result.get("personnelReadiness");
        assertEquals(2,readiness.get("issueCount"));
        assertEquals(1,readiness.get("missingRegionCount"));
        assertEquals(1,readiness.get("missingCostCount"));
        @SuppressWarnings("unchecked") List<Map<String,Object>> issues=(List<Map<String,Object>>)readiness.get("issues");
        assertEquals(2,issues.get(0).get("projectCount"));
    }

    @Test void projectOwnerCanSubmitTodayDraftButCannotConfirmIt()
    {
        Map<String,Object> project=project(30L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryCode","SALES_REVENUE");
        category.put("categoryName","销售收入");category.put("factKind","REVENUE");
        when(mapper.selectProjectForAccounting(30L)).thenReturn(project);
        when(mapper.selectAccountingMemberRole(30L,9L)).thenReturn("OWNER");
        when(mapper.selectCategoryById(1L)).thenReturn(category);
        doAnswer(invocation->{((BusinessOperatingFact)invocation.getArgument(0)).setFactId(300L);return 1;})
            .when(mapper).insertFact(any());
        when(mapper.selectFactById(300L)).thenAnswer(invocation->{
            BusinessOperatingFact saved=new BusinessOperatingFact();saved.setFactId(300L);saved.setStatus("DRAFT");
            saved.setProjectId(30L);saved.setCreateUserId(9L);return saved;
        });
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(30L);fact.setCategoryId(1L);
        fact.setBizDate(new Date());fact.setAmount(new BigDecimal("1200"));fact.setDescription("今日销售");

        BusinessOperatingFact saved=service.saveProjectFact(fact,9L,"owner9",false);

        assertEquals("DRAFT",saved.getStatus());
        verify(mapper).insertFact(any());
        verify(mapper,never()).confirmFact(any(),any(),any(),any());
    }

    @Test void projectOwnerDailyTotalSpendIsConfirmedAndRecalculatedImmediately()
    {
        Map<String,Object> project=project(32L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryId",5L);
        category.put("categoryCode","DIRECT_EXPENSE");
        when(mapper.selectProjectForAccounting(32L)).thenReturn(project);
        when(mapper.selectCategoryByCode("DIRECT_EXPENSE")).thenReturn(category);
        when(mapper.sumProjectFacts(eq(32L),any())).thenAnswer(invocation->{
            Map<String,Object> sums=new HashMap<String,Object>();sums.put("revenueAmount",BigDecimal.ZERO);
            sums.put("costAmount",new BigDecimal("500"));sums.put("adjustmentAmount",BigDecimal.ZERO);
            sums.put("valueScore",BigDecimal.ZERO);return sums;
        });
        when(mapper.sumProjectPersonnelCost(eq(32L),any())).thenReturn(new BigDecimal("91.9540"));
        when(mapper.sumProjectCostToDate(eq(32L),any())).thenReturn(new BigDecimal("591.9540"));
        when(mapper.selectNextResultVersion(eq(32L),any())).thenReturn(2);
        doAnswer(invocation->{((BusinessOperatingFact)invocation.getArgument(0)).setFactId(320L);return 1;})
            .when(mapper).insertFact(any());
        doAnswer(invocation->{((Map<String,Object>)invocation.getArgument(0)).put("resultId",321L);return 1;})
            .when(mapper).insertDailyResult(any());
        BusinessOperatingFact spend=new BusinessOperatingFact();spend.setProjectId(32L);
        spend.setBizDate(new Date());spend.setAmount(new BigDecimal("500"));spend.setDescription("投流与物流合计");

        BusinessOperatingFact saved=service.saveProjectDailySpend(spend,9L,"owner9",false);

        assertEquals("CONFIRMED",saved.getStatus());
        assertEquals("DAILY_TOTAL",saved.getSourceType());
        assertEquals("DIRECT_EXPENSE",saved.getCategoryCode());
        verify(mapper).insertDailyResult(any());
    }

    @Test void ordinaryMemberCannotSubmitProjectDailyTotalSpend()
    {
        Map<String,Object> project=project(33L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        when(mapper.selectProjectForAccounting(33L)).thenReturn(project);
        BusinessOperatingFact spend=new BusinessOperatingFact();spend.setProjectId(33L);
        spend.setBizDate(new Date());spend.setAmount(new BigDecimal("100"));

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveProjectDailySpend(spend,77L,"member77",false));

        assertTrue(error.getMessage().contains("主负责人"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void unrelatedUserCannotSubmitProjectFact()
    {
        Map<String,Object> project=project(31L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        when(mapper.selectProjectForAccounting(31L)).thenReturn(project);
        when(mapper.selectAccountingMemberRole(31L,77L)).thenReturn(null);
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(31L);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveProjectFact(fact,77L,"outsider",false));

        assertTrue(error.getMessage().contains("自己负责或参与"));
        verify(mapper,never()).insertFact(any());
    }

    private Map<String,Object> project(Long id,Long initiator)
    {Map<String,Object> p=new HashMap<String,Object>();p.put("projectId",id);p.put("companyDeptId",110L);p.put("initiatorUserId",initiator);p.put("accountingMode","PROFIT");p.put("currency","CNY");return p;}
}
