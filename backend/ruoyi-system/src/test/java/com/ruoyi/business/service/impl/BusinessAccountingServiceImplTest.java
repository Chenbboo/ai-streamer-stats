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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessAccountingServiceImplTest
{
    @Test void periodicBudgetNeverUsesLifetimeCosts()
    {
        when(mapper.selectProjectBudgetSnapshot(11L)).thenReturn("{\"budget\":{\"cycle\":\"MONTH\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}}");
        java.sql.Date day=java.sql.Date.valueOf("2026-09-15"),from=java.sql.Date.valueOf("2026-09-01");
        when(mapper.sumProjectCostInPeriod(11L,from,day)).thenReturn(new BigDecimal("1500"));
        assertEquals(new BigDecimal("1500"),service.budgetSpent(11L,day));
        assertEquals(BigDecimal.ZERO,service.budgetSpent(11L,java.sql.Date.valueOf("2026-10-01")));
        verify(mapper,never()).sumProjectCostToDate(any(),any());
    }
    @Mock BusinessAccountingMapper mapper;
    @Mock BusinessFileService businessFileService;
    @Mock com.ruoyi.business.mapper.BusinessProjectWorkMapper workMapper;
    @InjectMocks BusinessAccountingServiceImpl service;

    @BeforeEach void provideLockedProjectRead()
    {
        lenient().when(mapper.selectProjectForAccountingForUpdate(any()))
            .thenAnswer(call -> mapper.selectProjectForAccounting(call.getArgument(0)));
    }

    @Test void projectOwnerCanReadProjectCockpitWithoutBeingSponsor()
    {
        Map<String,Object> project=project(11L,8L);project.put("mainOwnerUserId",9L);
        when(mapper.selectProjectForAccounting(11L)).thenReturn(project);
        service.projectDashboard(11L,Collections.singletonMap("dateFrom","2026-09-01"),9L,false);
        ArgumentCaptor<Map<String,Object>> query=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectDailySummary(query.capture());
        assertEquals(11L,query.getValue().get("projectId"));
        assertEquals(true,query.getValue().get("viewAll"));
    }

    @Test void unrelatedUserCannotReadProjectCockpit()
    {
        Map<String,Object> project=project(11L,8L);project.put("mainOwnerUserId",9L);
        when(mapper.selectProjectForAccounting(11L)).thenReturn(project);
        assertThrows(ServiceException.class,()->service.projectDashboard(11L,Collections.emptyMap(),10L,false));
        verify(mapper,never()).selectDailySummary(any());
    }

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
        assertEquals(new BigDecimal("500"),result.get("budgetSpent"));
        org.mockito.InOrder order=inOrder(mapper);
        order.verify(mapper).insertDailyResult(any());
        order.verify(mapper).sumProjectCostToDate(21L,day);
        order.verify(mapper).updateDailyResultBudgetSpent(99L,new BigDecimal("500"));
        verify(mapper,times(5)).insertDailyResultItem(any());
    }

    @Test void backdatedRecalculationRefreshesEveryLaterCumulativeSnapshot()
    {
        Date changed=java.sql.Date.valueOf("2026-08-20");
        Date laterDate=java.sql.Date.valueOf("2026-08-21");
        when(mapper.selectProjectForAccounting(24L)).thenReturn(project(24L,8L));
        when(mapper.sumProjectFacts(24L,changed)).thenReturn(Collections.emptyMap());
        when(mapper.sumProjectPersonnelCost(24L,changed)).thenReturn(new BigDecimal("100"));
        when(mapper.sumProjectCostToDate(24L,changed)).thenReturn(new BigDecimal("500"));
        when(mapper.sumProjectCostToDate(24L,laterDate)).thenReturn(new BigDecimal("650"));
        when(mapper.selectNextResultVersion(24L,changed)).thenReturn(2);
        Map<String,Object> later=new HashMap<String,Object>();later.put("resultId",202L);later.put("bizDate",laterDate);
        when(mapper.selectCurrentResultsAfter(24L,changed)).thenReturn(Collections.singletonList(later));
        doAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("resultId",201L);return 1;})
            .when(mapper).insertDailyResult(any());

        service.recalculate(24L,changed,8L,"boss8",false);

        verify(mapper).updateDailyResultBudgetSpent(201L,new BigDecimal("500"));
        verify(mapper).updateDailyResultBudgetSpent(202L,new BigDecimal("650"));
    }

    @Test void terminalProjectRejectsNewFinancialFacts()
    {
        Map<String,Object> closed=project(25L,8L);closed.put("status","CLOSED");
        when(mapper.selectProjectForAccounting(25L)).thenReturn(closed);
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(25L);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveFact(fact,8L,"boss8",false));

        assertTrue(error.getMessage().contains("核算已关闭"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void returnedOrDraftAccountingFactsBlockProjectClosure()
    {
        when(mapper.countProjectUnsettledFacts(25L)).thenReturn(1);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.ensureProjectCanClose(25L));

        assertTrue(error.getMessage().contains("已退回未修改"));
    }

    @Test void settledAccountingFactsAllowProjectClosure()
    {
        when(mapper.countProjectUnsettledFacts(25L)).thenReturn(0);

        assertDoesNotThrow(()->service.ensureProjectCanClose(25L));
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

    @Test void oldResultKeepsStoredPersonnelSnapshotWithoutRehydratingCurrentPolicies()
    {
        Map<String,Object> result=new HashMap<String,Object>();result.put("resultId",9L);result.put("projectId",22L);
        result.put("bizDate",java.sql.Date.valueOf("2026-08-11"));
        when(mapper.selectDailyResults(any())).thenReturn(Collections.singletonList(result));
        Map<String,Object> total=new HashMap<String,Object>();total.put("componentCode","PERSONNEL_COST");
        when(mapper.selectDailyResultItems(9L)).thenReturn(Collections.singletonList(total));
        Map<String,Object> detail=service.resultDetail(9L,8L,false);

        List<?> personnel=(List<?>)detail.get("personnelItems");
        assertEquals(0,personnel.size());
        verify(mapper,never()).selectProjectPersonnelCostDetails(any(),any());
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

    @Test void actualCostOverviewPreservesUnknownAndHidesRawRatesWithoutIndependentPermission()
    {
        Map<String,Object> old=BusinessProjectWorkServiceTest.row("costStatus","READY","personnelCost",new BigDecimal("10"),"monthlyCost",6000,"dailyCost",200,"policyId",6L,"standardWorkDays",30);
        Map<String,Object> unknown=BusinessProjectWorkServiceTest.row("costPolicyVersion","ACTUAL_WORK_V1","costStatus","MISSING_ACTUAL","workMinutes",null,"personnelCost",null);
        when(mapper.selectPersonnelCostOverview(any())).thenReturn(Collections.singletonList(old));when(workMapper.selectPersonnelCostOverview(any())).thenReturn(Collections.singletonList(unknown));
        Map<String,Object> result=service.personnelCostOverview(Collections.emptyMap(),20L,false);
        assertEquals(false,result.get("rawCostVisible"));assertEquals(true,result.get("hasUnpricedOrMissingWork"));assertEquals(null,unknown.get("personnelCost"));assertTrue(!old.containsKey("monthlyCost"));assertTrue(!old.containsKey("policyId"));
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

    @Test void projectOwnerRevenueIsConfirmedAndCalculatedImmediately()
    {
        Map<String,Object> project=project(30L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryCode","SALES_REVENUE");
        category.put("categoryName","销售收入");category.put("factKind","REVENUE");
        when(mapper.selectProjectForAccounting(30L)).thenReturn(project);
        when(mapper.selectCategoryById(1L)).thenReturn(category);
        doAnswer(invocation->{((BusinessOperatingFact)invocation.getArgument(0)).setFactId(300L);return 1;})
            .when(mapper).insertFact(any());
        BusinessOperatingFact draft=new BusinessOperatingFact();draft.setFactId(300L);draft.setStatus("DRAFT");
        draft.setProjectId(30L);draft.setCreateUserId(9L);draft.setVersion(0);draft.setBizDate(new Date());
        BusinessOperatingFact confirmed=new BusinessOperatingFact();confirmed.setFactId(300L);confirmed.setStatus("CONFIRMED");
        confirmed.setProjectId(30L);confirmed.setCreateUserId(9L);confirmed.setVersion(1);confirmed.setBizDate(draft.getBizDate());
        when(mapper.selectFactById(300L)).thenReturn(draft,confirmed);
        when(mapper.confirmFact(300L,9L,"owner9",0)).thenReturn(1);
        when(mapper.sumProjectFacts(eq(30L),any())).thenReturn(Collections.emptyMap());
        when(mapper.selectNextResultVersion(eq(30L),any())).thenReturn(1);
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(30L);fact.setCategoryId(1L);
        fact.setBizDate(new Date());fact.setAmount(new BigDecimal("1200"));fact.setDescription("今日销售");

        BusinessOperatingFact saved=service.saveProjectFact(fact,9L,"owner9",false);

        assertEquals("CONFIRMED",saved.getStatus());
        verify(mapper).insertFact(any());
        verify(mapper).confirmFact(300L,9L,"owner9",0);
    }

    @Test void projectOwnerExpenseItemIsConfirmedImmediately()
    {
        Map<String,Object> project=project(32L,8L);
        project.put("mainOwnerUserId",9L);project.put("status","ACTIVE");
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryId",5L);
        category.put("categoryCode","DIRECT_EXPENSE");
        when(mapper.selectProjectForAccounting(32L)).thenReturn(project);
        when(mapper.selectCategoryByCode("DIRECT_EXPENSE")).thenReturn(category);
        doAnswer(invocation->{((BusinessOperatingFact)invocation.getArgument(0)).setFactId(320L);return 1;})
            .when(mapper).insertFact(any());
        BusinessOperatingFact draft=new BusinessOperatingFact();draft.setFactId(320L);draft.setStatus("DRAFT");
        draft.setProjectId(32L);draft.setBizDate(new Date());draft.setVersion(0);
        draft.setSourceType("DAILY_ITEM");draft.setCategoryCode("DIRECT_EXPENSE");
        BusinessOperatingFact confirmed=new BusinessOperatingFact();confirmed.setFactId(320L);confirmed.setStatus("CONFIRMED");
        confirmed.setProjectId(32L);confirmed.setBizDate(draft.getBizDate());confirmed.setVersion(1);
        confirmed.setSourceType("DAILY_ITEM");confirmed.setCategoryCode("DIRECT_EXPENSE");
        when(mapper.selectFactById(320L)).thenReturn(draft,confirmed);
        when(mapper.confirmFact(320L,9L,"owner9",0)).thenReturn(1);
        when(mapper.sumProjectFacts(eq(32L),any())).thenReturn(Collections.emptyMap());
        when(mapper.selectNextResultVersion(eq(32L),any())).thenReturn(1);
        BusinessOperatingFact spend=new BusinessOperatingFact();spend.setProjectId(32L);
        spend.setBizDate(new Date());spend.setAmount(new BigDecimal("500"));spend.setDescription("投流与物流合计");

        BusinessOperatingFact saved=service.saveProjectDailySpend(spend,9L,"owner9",false);

        assertEquals("CONFIRMED",saved.getStatus());
        assertEquals("DAILY_ITEM",saved.getSourceType());
        assertEquals("DIRECT_EXPENSE",saved.getCategoryCode());
        verify(mapper).insertDailyResult(any());
        verify(mapper).confirmFact(320L,9L,"owner9",0);
    }

    @Test void bossConfirmationReplacesPreviousDailySpendAndThenRecalculates()
    {
        Date day=java.sql.Date.valueOf("2026-08-26");
        BusinessOperatingFact draft=new BusinessOperatingFact();draft.setFactId(321L);draft.setProjectId(32L);
        draft.setBizDate(day);draft.setStatus("DRAFT");draft.setSourceType("DAILY_TOTAL");draft.setVersion(1);
        BusinessOperatingFact previous=new BusinessOperatingFact();previous.setFactId(300L);previous.setProjectId(32L);
        previous.setBizDate(day);previous.setStatus("CONFIRMED");previous.setVersion(2);previous.setAmount(new BigDecimal("400"));
        previous.setDescription("原今日项目总花费");previous.setFactKind("COST");
        when(mapper.selectFactById(321L)).thenReturn(draft);
        when(mapper.selectFactByIdForUpdate(321L)).thenReturn(draft);
        when(mapper.selectProjectForAccounting(32L)).thenReturn(project(32L,8L));
        when(mapper.selectConfirmedProjectDailySpend(32L,day)).thenReturn(previous);
        when(mapper.markFactReversed(300L,"boss8",2)).thenReturn(1);
        when(mapper.confirmFact(321L,8L,"boss8",1)).thenReturn(1);
        when(mapper.sumProjectFacts(32L,day)).thenReturn(Collections.emptyMap());
        when(mapper.sumProjectPersonnelCost(32L,day)).thenReturn(BigDecimal.ZERO);
        when(mapper.sumProjectCostToDate(32L,day)).thenReturn(BigDecimal.ZERO);
        when(mapper.selectNextResultVersion(32L,day)).thenReturn(2);

        service.confirmFact(321L,8L,"boss8",false);

        ArgumentCaptor<BusinessOperatingFact> inserted=ArgumentCaptor.forClass(BusinessOperatingFact.class);
        verify(mapper).insertFact(inserted.capture());
        assertEquals(new BigDecimal("-400"),inserted.getValue().getAmount());
        assertEquals("REVERSAL",inserted.getValue().getSourceDomain());
        verify(mapper).confirmFact(321L,8L,"boss8",1);
        verify(mapper).insertDailyResult(any());
    }

    @Test void bossCanReturnDraftWithReasonWithoutRecalculating()
    {
        BusinessOperatingFact draft=new BusinessOperatingFact();draft.setFactId(330L);draft.setProjectId(32L);
        draft.setStatus("DRAFT");draft.setVersion(4);
        BusinessOperatingFact returned=new BusinessOperatingFact();returned.setFactId(330L);returned.setProjectId(32L);
        returned.setStatus("RETURNED");returned.setReturnReason("凭证金额与填报不一致");
        when(mapper.selectFactById(330L)).thenReturn(draft,returned);
        when(mapper.selectFactByIdForUpdate(330L)).thenReturn(draft);
        when(mapper.selectProjectForAccounting(32L)).thenReturn(project(32L,8L));
        when(mapper.returnFact(330L,"凭证金额与填报不一致",8L,"boss8",4)).thenReturn(1);

        BusinessOperatingFact result=service.returnFact(330L,"  凭证金额与填报不一致  ",8L,"boss8",false);

        assertEquals("RETURNED",result.getStatus());
        assertEquals("凭证金额与填报不一致",result.getReturnReason());
        verify(mapper).returnFact(330L,"凭证金额与填报不一致",8L,"boss8",4);
        verify(mapper,never()).insertDailyResult(any());
    }

    @Test void returningDraftRequiresReason()
    {
        ServiceException error=assertThrows(ServiceException.class,
            ()->service.returnFact(330L,"  ",8L,"boss8",false));

        assertTrue(error.getMessage().contains("退回原因"));
        verify(mapper,never()).selectFactById(any());
        verify(mapper,never()).returnFact(any(),any(),any(),any(),any());
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
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(31L);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveProjectFact(fact,77L,"outsider",false));

        assertTrue(error.getMessage().contains("主负责人"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void deliveredProjectAcceptsHistoricalOwnerRevenueWhileAccountingIsOpen()
    {
        Map<String,Object> p=separatedProject("CLOSED","OPEN");
        when(mapper.selectProjectForAccounting(41L)).thenReturn(p);
        Map<String,Object> category=new HashMap<String,Object>();
        category.put("categoryCode","SALES_REVENUE");category.put("categoryName","收入");category.put("factKind","REVENUE");
        when(mapper.selectCategoryById(1L)).thenReturn(category);
        BusinessOperatingFact fact=lateFact();fact.setCategoryId(1L);fact.setVersion(0);
        doAnswer(call->{call.<BusinessOperatingFact>getArgument(0).setFactId(410L);return 1;}).when(mapper).insertFact(any());
        when(mapper.selectFactById(410L)).thenReturn(fact);
        when(mapper.confirmFact(410L,9L,"owner9",0)).thenReturn(1);
        when(mapper.sumProjectFacts(41L,fact.getBizDate())).thenReturn(Collections.emptyMap());

        service.saveProjectFact(fact,9L,"owner9",false);

        assertEquals(java.sql.Date.valueOf("2026-07-19"),fact.getBizDate());
        verify(mapper).confirmFact(410L,9L,"owner9",0);
        verify(mapper).insertDailyResult(any());
    }

    @Test void canceledProjectAcceptsHistoricalDailySpendWhileAccountingIsOpen()
    {
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("CANCELED","OPEN"));
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryId",5L);category.put("categoryCode","DIRECT_EXPENSE");
        when(mapper.selectCategoryByCode("DIRECT_EXPENSE")).thenReturn(category);
        BusinessOperatingFact fact=lateFact();fact.setVersion(0);
        doAnswer(call->{call.<BusinessOperatingFact>getArgument(0).setFactId(410L);return 1;}).when(mapper).insertFact(any());
        when(mapper.selectFactById(410L)).thenReturn(fact);
        when(mapper.confirmFact(410L,9L,"owner9",0)).thenReturn(1);
        when(mapper.sumProjectFacts(41L,fact.getBizDate())).thenReturn(Collections.emptyMap());

        service.saveProjectDailySpend(fact,9L,"owner9",false);

        assertEquals("DAILY_ITEM",fact.getSourceType());
        assertNotNull(fact.getSourceId());
        verify(mapper).insertDailyResult(any());
    }

    @Test void deliveredProjectRejectsExpenseAfterActualDeliveryDate()
    {
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("CLOSED","OPEN"));
        BusinessOperatingFact fact=lateFact();fact.setBizDate(java.sql.Date.valueOf("2026-07-21"));

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveProjectDailySpend(fact,9L,"owner9",false));

        assertTrue(error.getMessage().contains("不能晚于项目实际结束日期"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void futureAccountingDateIsRejectedBeforeAnyResultWrite()
    {
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("ACTIVE","OPEN"));
        java.util.Calendar tomorrow=java.util.Calendar.getInstance();tomorrow.add(java.util.Calendar.DATE,1);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.recalculate(41L,tomorrow.getTime(),8L,"boss8",false));

        assertTrue(error.getMessage().contains("不能晚于今天"));
        verify(mapper,never()).insertDailyResult(any());
    }

    @Test void currentLockedAccountingStateRejectsOwnerWriteAfterConcurrentClosure()
    {
        doReturn(separatedProject("CLOSED","CLOSED")).when(mapper).selectProjectForAccountingForUpdate(41L);

        assertThrows(ServiceException.class,()->service.saveProjectDailySpend(lateFact(),9L,"owner9",false));

        verify(mapper,never()).selectProjectForAccounting(any());
        verify(mapper,never()).insertFact(any());
        verify(mapper,never()).insertDailyResult(any());
    }

    @Test void factStatusIsRecheckedAfterProjectLock()
    {
        BusinessOperatingFact before=lateFact();before.setFactId(410L);before.setStatus("DRAFT");before.setVersion(0);
        BusinessOperatingFact after=lateFact();after.setFactId(410L);after.setStatus("CONFIRMED");after.setVersion(1);
        when(mapper.selectFactById(410L)).thenReturn(before);
        when(mapper.selectFactByIdForUpdate(410L)).thenReturn(after);
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("CLOSED","OPEN"));

        assertThrows(ServiceException.class,()->service.confirmFact(410L,8L,"boss8",false));

        verify(mapper,never()).confirmFact(any(),any(),any(),any());
    }

    @Test void bonusKeepsExistingCycleDateAfterDeliveryAndRetryDoesNotDoublePost()
    {
        Date periodEnd=java.sql.Date.valueOf("2026-07-31");
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("CLOSED","OPEN"));
        Map<String,Object> settlement=new HashMap<String,Object>();settlement.put("projectId",41L);
        settlement.put("periodEnd",periodEnd);settlement.put("status","SUBMITTED");
        when(mapper.selectProjectBonusSettlement(51L)).thenReturn(settlement);
        Map<String,Object> category=new HashMap<String,Object>();category.put("categoryId",17L);category.put("categoryName","项目奖金");
        when(mapper.selectCategoryByCode("PROJECT_BONUS_COST")).thenReturn(category);
        when(mapper.sumProjectFacts(41L,periodEnd)).thenReturn(Collections.emptyMap());
        BusinessOperatingFact existing=new BusinessOperatingFact();existing.setProjectId(41L);existing.setFactId(411L);
        when(mapper.selectFactByIdempotencyKey("KPI-BONUS-SETTLEMENT-51")).thenReturn(null,existing);

        service.recordProjectBonus(41L,periodEnd,BigDecimal.TEN,51L,8L,"boss8");
        assertEquals(existing,service.recordProjectBonus(41L,periodEnd,BigDecimal.TEN,51L,8L,"boss8"));

        verify(mapper,times(1)).insertFact(any());
        verify(mapper,times(1)).insertDailyResult(any());
    }

    @Test void bonusCannotInventSettlementForAnotherProject()
    {
        when(mapper.selectProjectForAccounting(41L)).thenReturn(separatedProject("CLOSED","OPEN"));
        Map<String,Object> settlement=new HashMap<String,Object>();settlement.put("projectId",99L);
        when(mapper.selectProjectBonusSettlement(51L)).thenReturn(settlement);

        assertThrows(ServiceException.class,()->service.recordProjectBonus(41L,java.sql.Date.valueOf("2026-07-31"),
            BigDecimal.TEN,51L,8L,"boss8"));

        verify(mapper,never()).insertFact(any());
    }

    private Map<String,Object> separatedProject(String status,String accountingState)
    {
        Map<String,Object> p=project(41L,8L);p.put("mainOwnerUserId",9L);p.put("status",status);
        p.put("deliveryPolicyVersion","SEPARATED_V1");p.put("accountingState",accountingState);
        p.put("actualEndDate",java.sql.Date.valueOf("2026-07-20"));return p;
    }

    private BusinessOperatingFact lateFact()
    {
        BusinessOperatingFact f=new BusinessOperatingFact();f.setProjectId(41L);f.setBizDate(java.sql.Date.valueOf("2026-07-19"));
        f.setAmount(BigDecimal.TEN);f.setDescription("执行期间费用补报");return f;
    }

    private Map<String,Object> project(Long id,Long initiator)
    {Map<String,Object> p=new HashMap<String,Object>();p.put("projectId",id);p.put("companyDeptId",110L);p.put("initiatorUserId",initiator);p.put("accountingMode","PROFIT");p.put("currency","CNY");return p;}
}
