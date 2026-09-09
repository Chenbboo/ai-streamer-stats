package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectBonusTier;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiPlanItem;
import com.ruoyi.business.domain.BusinessProjectKpiResult;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectKpiServiceImplTest
{
    @Mock BusinessProjectKpiMapper mapper;
    @Mock BusinessProjectMapper projectMapper;
    @Mock IBusinessAccountingService accountingService;
    @Mock BusinessFileService businessFileService;
    @InjectMocks BusinessProjectKpiServiceImpl service;

    @BeforeEach void provideLockedReads()
    {
        lenient().when(projectMapper.selectProjectByIdForUpdate(any()))
            .thenAnswer(call -> projectMapper.selectProjectById(call.getArgument(0)));
        lenient().when(mapper.selectSettlementByIdForUpdate(any()))
            .thenAnswer(call -> mapper.selectSettlementById(call.getArgument(0)));
    }

    @Test void bossOverviewUsesOwnerScopedProjectQuery()
    {
        List<Map<String,Object>> rows=Collections.singletonList(Collections.<String,Object>singletonMap("projectId",1L));
        when(mapper.selectProjectOverviews(8L,false,true,null)).thenReturn(rows);

        assertEquals(rows,service.overview(8L,false,true));
        verify(mapper).selectProjectOverviews(8L,false,true,null);
    }

    @Test void bossCannotPublishWhenProjectWeightsDoNotEqualOneHundred()
    {
        BusinessProject project=project();
        BusinessProjectKpi target=target(1L,new BigDecimal("60"));
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(projectMapper.selectProjectKpis(1L)).thenReturn(Collections.singletonList(target));
        BusinessProjectKpiPlan plan=plan();plan.setTiers(tiers());

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.publishPlan(plan,8L,"boss8",false,true));

        assertTrue(error.getMessage().contains("权重合计必须等于100%"));
        verify(mapper,never()).insertPlan(any());
    }

    @Test void administratorCanManageButCannotFillForProjectOwner()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(mapper.selectLatestPlanId(1L)).thenReturn(null);

        Map<String,Object> workspace=service.workspace(1L,null,1L,true,false);

        assertEquals(Boolean.TRUE,workspace.get("canManage"));
        assertEquals(Boolean.FALSE,workspace.get("canSettle"));
    }

    @Test void projectOwnerSavesManualResultAndGetsRmbPreview()
    {
        BusinessProject project=project();
        BusinessProjectKpiSettlement settlement=settlement("DRAFT",0);
        BusinessProjectKpiPlanItem item=item();
        BusinessProjectKpiResult inputResult=new BusinessProjectKpiResult();inputResult.setPlanItemId(101L);
        inputResult.setActualValue(new BigDecimal("120"));inputResult.setResultNote("系统销售报表与合同回款记录");
        BusinessProjectKpiSettlement input=new BusinessProjectKpiSettlement();
        input.setResults(Collections.singletonList(inputResult));
        when(mapper.selectSettlementById(20L)).thenReturn(settlement);
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(inputResult));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(mapper.updateSettlementPreview(eq(20L),eq(new BigDecimal("120.00")),
            eq(new BigDecimal("30000.00")),eq("owner9"),eq(0))).thenReturn(1);

        BusinessProjectKpiSettlement saved=service.saveResults(20L,input,9L,"owner9",false);

        ArgumentCaptor<BusinessProjectKpiResult> resultCaptor=ArgumentCaptor.forClass(BusinessProjectKpiResult.class);
        verify(mapper).upsertSettlementResult(resultCaptor.capture());
        assertEquals(new BigDecimal("120.00"),resultCaptor.getValue().getCompletionRate());
        assertEquals(new BigDecimal("120.00"),resultCaptor.getValue().getWeightedScore());
        assertEquals(1,saved.getResults().size());
    }

    @Test void workspaceCalculatesRoutineKpiFromSubmittedReports()
    {
        BusinessProjectKpiPlan currentPlan=plan();currentPlan.setPlanId(10L);currentPlan.setPlanVersion(1);
        BusinessProjectKpiPlanItem automatic=item();automatic.setSourceType("ROUTINE");automatic.setSourceRefId(301L);
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);
        draft.setPeriodStart(java.sql.Date.valueOf("2026-01-01"));draft.setPeriodEnd(java.sql.Date.valueOf("2026-12-31"));
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(projectMapper.selectProjectKpis(1L)).thenReturn(Collections.emptyList());
        when(mapper.selectLatestPlanId(1L)).thenReturn(10L);
        when(mapper.selectPlanById(10L)).thenReturn(currentPlan);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(automatic));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(mapper.selectSettlementByPlanId(10L)).thenReturn(draft);
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.emptyList());
        when(mapper.sumRoutineActual(eq(1L),eq(301L),any(),any())).thenReturn(new BigDecimal("42"));

        Map<String,Object> workspace=service.workspace(1L,null,9L,false,false);

        BusinessProjectKpiPlan selected=(BusinessProjectKpiPlan)workspace.get("selectedPlan");
        assertEquals(new BigDecimal("42"),selected.getSettlement().getResults().get(0).getActualValue());
        assertEquals(Boolean.TRUE,selected.getSettlement().getResults().get(0).getAutomatic());
    }

    @Test void workspacePreviewsEveryAutomaticSourceWithinThePublishedPeriod()
    {
        BusinessProjectKpiPlan currentPlan=plan();currentPlan.setPlanId(10L);currentPlan.setPlanVersion(1);
        currentPlan.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);draft.setRewardPolicyVersion("INDEPENDENT_V1");
        List<BusinessProjectKpiPlanItem> items=new java.util.ArrayList<BusinessProjectKpiPlanItem>();
        String[] sources={"REVENUE","BUSINESS_COST","PERSONNEL_COST","PROFIT","ROUTINE","TASK","MILESTONE"};
        for(int i=0;i<sources.length;i++)
        {
            BusinessProjectKpiPlanItem source=item();source.setItemId(101L+i);source.setSourceType(sources[i]);
            if("ROUTINE".equals(sources[i]))source.setSourceRefId(301L);
            if("TASK".equals(sources[i]))source.setSourceRefId(401L);
            if("MILESTONE".equals(sources[i]))source.setSourceRefId(501L);
            items.add(source);
        }
        Map<String,Object> summary=new java.util.LinkedHashMap<String,Object>();
        summary.put("revenueAmount",new BigDecimal("500"));summary.put("businessCost",new BigDecimal("120"));
        summary.put("personnelCost",new BigDecimal("80"));summary.put("profitAmount",new BigDecimal("300"));
        Map<String,Object> dashboard=new java.util.LinkedHashMap<String,Object>();dashboard.put("summary",summary);
        dashboard.put("costPolicyVersion","MEMBER_DAYS_V1");dashboard.put("pendingCostCount",0);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(projectMapper.selectProjectKpis(1L)).thenReturn(Collections.emptyList());
        when(mapper.selectLatestPlanId(1L)).thenReturn(10L);when(mapper.selectPlanById(10L)).thenReturn(currentPlan);
        when(mapper.selectPlanItems(10L)).thenReturn(items);when(mapper.selectSettlementByPlanId(10L)).thenReturn(draft);
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.emptyList());
        when(accountingService.projectDashboard(eq(1L),any(),eq(9L),eq(true))).thenReturn(dashboard);
        when(mapper.sumRoutineActual(1L,301L,draft.getPeriodStart(),draft.getPeriodEnd())).thenReturn(new BigDecimal("42"));
        when(mapper.countCompletedTasks(1L,401L,draft.getPeriodStart(),draft.getPeriodEnd())).thenReturn(new BigDecimal("3"));
        when(mapper.countCompletedMilestones(1L,501L,draft.getPeriodStart(),draft.getPeriodEnd())).thenReturn(new BigDecimal("2"));

        Map<String,Object> workspace=service.workspace(1L,null,9L,false,false);
        List<BusinessProjectKpiResult> results=((BusinessProjectKpiPlan)workspace.get("selectedPlan")).getSettlement().getResults();
        BigDecimal[] expected={new BigDecimal("500"),new BigDecimal("120"),new BigDecimal("80"),
            new BigDecimal("300"),new BigDecimal("42"),new BigDecimal("3"),new BigDecimal("2")};
        assertEquals(sources.length,results.size());
        for(int i=0;i<sources.length;i++)
        {
            assertEquals(sources[i],results.get(i).getSourceType());assertEquals(expected[i],results.get(i).getActualValue());
            assertEquals(Boolean.TRUE,results.get(i).getAutomatic());assertEquals("READY",results.get(i).getDataStatus());
        }
    }

    @Test void automaticResultCannotBeOverwrittenManually()
    {
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);
        BusinessProjectKpiPlanItem automatic=item();automatic.setSourceType("REVENUE");
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(BigDecimal.TEN);result.setResultNote("尝试覆盖");
        BusinessProjectKpiSettlement input=new BusinessProjectKpiSettlement();input.setResults(Collections.singletonList(result));
        when(mapper.selectSettlementById(20L)).thenReturn(draft);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(automatic));

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.saveResults(20L,input,9L,"owner9",false));

        assertTrue(error.getMessage().contains("自动取数KPI不能手工覆盖"));
        verify(mapper,never()).upsertSettlementResult(any());
    }

    @Test void settlementCannotBeConfirmedOnItsEndDate()
    {
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);
        draft.setPeriodEnd(java.sql.Date.valueOf(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date())));
        when(mapper.selectSettlementById(20L)).thenReturn(draft);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.submit(20L,9L,"owner9",false));

        assertTrue(error.getMessage().contains("截止日期次日"));
        verify(mapper,never()).submitSettlement(any(),any(),any(),any(),any(),any());
    }

    @Test void ordinaryMemberCannotSubmitProjectSettlement()
    {
        when(mapper.selectSettlementById(20L)).thenReturn(settlement("DRAFT",0));
        when(projectMapper.selectProjectById(1L)).thenReturn(project());

        ServiceException error=assertThrows(ServiceException.class,()->service.submit(20L,12L,"member12",false));

        assertTrue(error.getMessage().contains("只有项目主负责人"));
        verify(mapper,never()).submitSettlement(any(),any(),any(),any(),any(),any());
    }

    @Test void bossConfirmationCreatesOneProjectBonusCostAndClosesPlan()
    {
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",1);
        BusinessProjectKpiSettlement confirmed=settlement("CONFIRMED",2);
        confirmed.setTotalScore(new BigDecimal("100.00"));confirmed.setBonusAmount(new BigDecimal("30000.00"));
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(new BigDecimal("100"));result.setResultNote("已核对项目经营数据");
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setFactId(71L);
        when(mapper.selectSettlementById(20L)).thenReturn(submitted,confirmed);
        doReturn(submitted).when(mapper).selectSettlementByIdForUpdate(20L);
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item()));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(result));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(accountingService.recordProjectBonus(eq(1L),any(),eq(new BigDecimal("30000.00")),eq(20L),eq(8L),eq("boss8")))
            .thenReturn(fact);
        when(mapper.confirmSettlement(eq(20L),eq(new BigDecimal("100.00")),eq(new BigDecimal("30000.00")),
            eq(71L),any(),eq(8L),eq("boss8"),eq(1))).thenReturn(1);
        when(mapper.closePlan(10L)).thenReturn(1);

        BusinessProjectKpiSettlement resultValue=service.review(20L,"CONFIRMED","同意",8L,"boss8",false,true);

        assertEquals("CONFIRMED",resultValue.getStatus());
        verify(accountingService).recordProjectBonus(eq(1L),any(),eq(new BigDecimal("30000.00")),eq(20L),eq(8L),eq("boss8"));
        verify(mapper).closePlan(10L);
    }

    @Test void bossCanVoidUnsubmittedPlanWithoutDeletingAuditData()
    {
        BusinessProjectKpiPlan plan=plan();plan.setPlanId(10L);plan.setPlanVersion(1);
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);
        when(mapper.selectPlanById(10L)).thenReturn(plan);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(mapper.selectSettlementByPlanId(10L)).thenReturn(draft);
        when(mapper.selectSettlementByIdForUpdate(20L)).thenReturn(draft);
        when(mapper.voidDraftSettlement(10L,8L,"boss8")).thenReturn(1);
        when(mapper.voidPublishedPlan(10L,8L,"boss8")).thenReturn(1);

        service.voidPlan(10L,8L,"boss8",false,true);

        verify(mapper).voidDraftSettlement(10L,8L,"boss8");
        verify(mapper).voidPublishedPlan(10L,8L,"boss8");
    }

    @Test void confirmedPlanCannotBeDeleted()
    {
        BusinessProjectKpiPlan plan=plan();plan.setPlanId(10L);plan.setPlanVersion(1);
        BusinessProjectKpiSettlement confirmed=settlement("CONFIRMED",1);confirmed.setAccountingFactId(71L);
        when(mapper.selectPlanById(10L)).thenReturn(plan);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
        when(mapper.selectSettlementByPlanId(10L)).thenReturn(confirmed);
        when(mapper.selectSettlementByIdForUpdate(20L)).thenReturn(confirmed);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.voidPlan(10L,8L,"boss8",false,true));

        assertTrue(error.getMessage().contains("仅可作废未提交、未入账"));
        verify(mapper,never()).voidDraftSettlement(any(),any(),any());
        verify(mapper,never()).voidPublishedPlan(any(),any(),any());
    }

    @Test void deliveredProjectOwnerCanContinueExistingDraftResults()
    {
        assertClosedProjectResultCanBeSaved("DRAFT");
    }

    @Test void canceledProjectOwnerCanContinueReturnedResults()
    {
        assertClosedProjectResultCanBeSaved("RETURNED");
    }

    private void assertClosedProjectResultCanBeSaved(String status)
    {
        BusinessProject closed=separatedProject("RETURNED".equals(status)?"CANCELED":"CLOSED","OPEN");
        BusinessProjectKpiSettlement draft=settlement(status,0);
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(new BigDecimal("100"));result.setResultNote("交付后的既有周期核对");
        BusinessProjectKpiSettlement input=new BusinessProjectKpiSettlement();input.setResults(Collections.singletonList(result));
        when(projectMapper.selectProjectById(1L)).thenReturn(closed);
        when(mapper.selectSettlementById(20L)).thenReturn(draft);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item()));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(result));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(mapper.updateSettlementPreview(eq(20L),eq(new BigDecimal("100.00")),
            eq(new BigDecimal("30000.00")),eq("owner9"),eq(0))).thenReturn(1);

        service.saveResults(20L,input,9L,"owner9",false);

        verify(mapper).upsertSettlementResult(any());
        verify(accountingService,never()).recordProjectBonus(any(),any(),any(),any(),any(),any());
    }

    @Test void oldClosedProjectStillCannotSubmit()
    {
        BusinessProject closed=project();closed.setStatus("CLOSED");
        when(projectMapper.selectProjectById(1L)).thenReturn(closed);
        when(mapper.selectSettlementById(20L)).thenReturn(settlement("DRAFT",0));

        assertThrows(ServiceException.class,()->service.submit(20L,9L,"owner9",false));

        verify(mapper,never()).submitSettlement(any(),any(),any(),any(),any(),any());
    }

    @Test void independentlyClosedAccountingRejectsEvenZeroBonusReview()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","CLOSED"));
        when(mapper.selectSettlementById(20L)).thenReturn(settlement("SUBMITTED",1));

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.review(20L,"CONFIRMED","同意",8L,"boss8",false,true));

        assertTrue(error.getMessage().contains("核算已关闭"));
        verify(mapper,never()).confirmSettlement(any(),any(),any(),any(),any(),any(),any(),any());
        verify(accountingService,never()).recordProjectBonus(any(),any(),any(),any(),any(),any());
    }

    @Test void projectOwnerCannotTakeOverLegacyBossReview()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        when(mapper.selectSettlementById(20L)).thenReturn(settlement("SUBMITTED",1));

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.review(20L,"CONFIRMED","同意",9L,"owner9",false,false));

        assertTrue(error.getMessage().contains("归属老板"));
        verify(mapper,never()).confirmSettlement(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void legacyAdministratorReviewExceptionRemainsAvailable()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",1);
        when(mapper.selectSettlementById(20L)).thenReturn(submitted);
        when(mapper.returnSettlement(20L,"核对凭证",1L,"admin",1)).thenReturn(1);

        service.review(20L,"RETURNED","核对凭证",1L,"admin",true,false);

        verify(mapper).returnSettlement(20L,"核对凭证",1L,"admin",1);
    }

    @Test void ownerCanConfirmExistingCycleAfterDelivery()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",1);
        BusinessProjectKpiSettlement confirmed=settlement("CONFIRMED",2);
        when(mapper.selectSettlementById(20L)).thenReturn(draft,submitted,confirmed);
        doReturn(draft).when(mapper).selectSettlementByIdForUpdate(20L);
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(new BigDecimal("100"));result.setResultNote("按既有周期核对");
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item()));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(result));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(mapper.submitSettlement(eq(20L),any(),any(),eq(9L),eq("owner9"),eq(0))).thenReturn(1);
        when(mapper.confirmSettlement(eq(20L),any(),any(),any(),any(),eq(9L),eq("owner9"),eq(1))).thenReturn(1);
        when(mapper.closePlan(10L)).thenReturn(1);

        assertEquals("CONFIRMED",service.submit(20L,9L,"owner9",false).getStatus());

        verify(accountingService).recordProjectBonus(eq(1L),eq(java.sql.Date.valueOf("2026-07-31")),
            eq(new BigDecimal("30000.00")),eq(20L),eq(9L),eq("owner9"));
    }

    @Test void legacyReturnedResubmissionKeepsBossReviewInsteadOfOwnerConfirmation()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        BusinessProjectKpiSettlement returned=settlement("RETURNED",2);returned.setReviewedUserId(8L);
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",3);
        when(mapper.selectSettlementById(20L)).thenReturn(returned,submitted);
        doReturn(returned).when(mapper).selectSettlementByIdForUpdate(20L);
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(new BigDecimal("100"));result.setResultNote("按审核意见补正");
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item()));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(result));
        when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(mapper.submitSettlement(eq(20L),any(),any(),eq(9L),eq("owner9"),eq(2))).thenReturn(1);

        assertEquals("SUBMITTED",service.submit(20L,9L,"owner9",false).getStatus());

        verify(accountingService,never()).recordProjectBonus(any(),any(),any(),any(),any(),any());
        verify(mapper,never()).confirmSettlement(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void closedProjectWorkspaceSeparatesManageSettleAndLegacyReviewPermissions()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        when(mapper.selectLatestPlanId(1L)).thenReturn(null);

        Map<String,Object> owner=service.workspace(1L,null,9L,false,false);
        Map<String,Object> boss=service.workspace(1L,null,8L,false,true);

        assertEquals(false,owner.get("canManage"));
        assertEquals(true,owner.get("canSettle"));
        assertEquals(false,owner.get("canReview"));
        assertEquals(true,boss.get("canReview"));
        assertEquals(false,boss.get("canSettle"));
    }

    @Test void publishedPlanCannotBeAddedAfterDelivery()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));

        assertThrows(ServiceException.class,()->service.publishPlan(plan(),9L,"owner9",false,false));

        verify(mapper,never()).insertPlan(any());
    }

    @Test void independentProjectIndicatorConfirmsWithoutBonusOrAccountingFact()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(separatedProject("CLOSED","OPEN"));
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);draft.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",1);submitted.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiSettlement confirmed=settlement("CONFIRMED",2);confirmed.setRewardPolicyVersion("INDEPENDENT_V1");
        when(mapper.selectSettlementById(20L)).thenReturn(draft,submitted,confirmed);
        doReturn(draft).when(mapper).selectSettlementByIdForUpdate(20L);
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(101L);
        result.setActualValue(new BigDecimal("100"));result.setResultNote("交付指标独立确认");
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(item()));
        when(mapper.selectSettlementResults(20L)).thenReturn(Collections.singletonList(result));
        when(mapper.submitSettlement(eq(20L),any(),isNull(),eq(9L),eq("owner9"),eq(0))).thenReturn(1);
        when(mapper.confirmSettlement(eq(20L),any(),isNull(),isNull(),any(),eq(9L),eq("owner9"),eq(1))).thenReturn(1);
        when(mapper.closePlan(10L)).thenReturn(1);

        assertEquals("CONFIRMED",service.submit(20L,9L,"owner9",false).getStatus());
        verify(accountingService,never()).recordProjectBonus(any(),any(),any(),any(),any(),any());
        verify(mapper,never()).selectBonusTiers(any());
    }

    @Test void newPlanInProjectCurrencyIgnoresClientAttemptToPublishLegacyBonus()
    {
        BusinessProject project=project();project.setBaseCurrency("VND");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(projectMapper.selectProjectKpis(1L)).thenReturn(Collections.singletonList(target(1L,new BigDecimal("100"))));
        when(mapper.selectNextPlanVersion(1L)).thenReturn(2);
        when(mapper.selectLatestPlanId(1L)).thenReturn(null);
        BusinessProjectKpiPlan plan=plan();plan.setRewardPolicyVersion("LEGACY_LINKED");plan.setTiers(tiers());

        service.publishPlan(plan,9L,"owner9",false,false);

        ArgumentCaptor<BusinessProjectKpiPlan> captured=ArgumentCaptor.forClass(BusinessProjectKpiPlan.class);
        verify(mapper).insertPlan(captured.capture());
        assertEquals("INDEPENDENT_V1",captured.getValue().getRewardPolicyVersion());
        assertEquals("NONE",captured.getValue().getBonusMode());
        assertEquals("VND",captured.getValue().getCurrency());
        verify(mapper,never()).insertBonusTier(any());
    }

    @Test void projectCycleUsesProjectDatesForOverlapCheckPlanAndSettlement()
    {
        BusinessProject project=project();
        project.setPlanStartDate(java.sql.Date.valueOf("2026-07-15"));
        project.setPlanEndDate(java.sql.Date.valueOf("2026-09-30"));
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(projectMapper.selectProjectKpis(1L)).thenReturn(Collections.singletonList(target(1L,new BigDecimal("100"))));
        when(mapper.selectLatestPlanId(1L)).thenReturn(null);
        BusinessProjectKpiPlan plan=plan();plan.setCycleType("PROJECT");

        service.publishPlan(plan,9L,"owner9",false,false);

        verify(mapper).countOverlappingPlans(1L,project.getPlanStartDate(),project.getPlanEndDate());
        ArgumentCaptor<BusinessProjectKpiPlan> published=ArgumentCaptor.forClass(BusinessProjectKpiPlan.class);
        verify(mapper).insertPlan(published.capture());
        assertEquals(project.getPlanStartDate(),published.getValue().getCycleStart());
        assertEquals(project.getPlanEndDate(),published.getValue().getCycleEnd());
        ArgumentCaptor<BusinessProjectKpiSettlement> draft=ArgumentCaptor.forClass(BusinessProjectKpiSettlement.class);
        verify(mapper).insertSettlement(draft.capture());
        assertEquals(project.getPlanStartDate(),draft.getValue().getPeriodStart());
        assertEquals(project.getPlanEndDate(),draft.getValue().getPeriodEnd());
    }

    @Test void projectCycleCannotUseClientDatesWhenProjectDatesAreMissing()
    {
        BusinessProject project=project();
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        BusinessProjectKpiPlan plan=plan();plan.setCycleType("PROJECT");
        for (boolean missingStart : new boolean[]{true,false})
        {
            project.setPlanStartDate(missingStart?null:java.sql.Date.valueOf("2026-07-15"));
            project.setPlanEndDate(missingStart?java.sql.Date.valueOf("2026-09-30"):null);
            ServiceException error=assertThrows(ServiceException.class,
                ()->service.publishPlan(plan,9L,"owner9",false,false));
            assertTrue(error.getMessage().contains("完善计划起止日期"));
        }
        verify(mapper,never()).insertPlan(any());
        verify(mapper,never()).insertSettlement(any());
    }

    @Test void projectCycleRejectsReversedProjectDates()
    {
        BusinessProject project=project();
        project.setPlanStartDate(java.sql.Date.valueOf("2026-09-30"));
        project.setPlanEndDate(java.sql.Date.valueOf("2026-07-15"));
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        BusinessProjectKpiPlan plan=plan();plan.setCycleType("PROJECT");

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.publishPlan(plan,9L,"owner9",false,false));

        assertTrue(error.getMessage().contains("结束日期不能早于开始日期"));
        verify(mapper,never()).insertPlan(any());
    }

    @Test void independentPlanCannotEnterLegacyBossBonusReview()
    {
        BusinessProjectKpiSettlement submitted=settlement("SUBMITTED",1);submitted.setRewardPolicyVersion("INDEPENDENT_V1");
        when(mapper.selectSettlementById(20L)).thenReturn(submitted);
        when(projectMapper.selectProjectById(1L)).thenReturn(project());

        assertThrows(ServiceException.class,()->service.review(20L,"CONFIRMED","核准",8L,"boss8",false,true));
        verify(accountingService,never()).recordProjectBonus(any(),any(),any(),any(),any(),any());
    }

    @Test void independentCostIndicatorPreviewShowsUnknownWhilePersonnelCostIsPending()
    {
        BusinessProjectKpiPlan plan=plan();plan.setPlanId(10L);plan.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);draft.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiPlanItem cost=item();cost.setSourceType("PERSONNEL_COST");
        when(projectMapper.selectProjectById(1L)).thenReturn(project());when(mapper.selectLatestPlanId(1L)).thenReturn(10L);
        when(mapper.selectPlanById(10L)).thenReturn(plan);when(mapper.selectSettlementByPlanId(10L)).thenReturn(draft);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(cost));
        when(accountingService.projectDashboard(eq(1L),any(),eq(9L),eq(true))).thenReturn(pendingCosts());
        Map<String,Object> workspace=service.workspace(1L,null,9L,false,false);
        BusinessProjectKpiSettlement shown=((BusinessProjectKpiPlan)workspace.get("selectedPlan")).getSettlement();
        assertEquals("PENDING_COST",shown.getDataStatus());
        assertEquals(null,shown.getTotalScore());assertEquals(null,shown.getResults().get(0).getActualValue());
        assertEquals("PENDING_COST",shown.getResults().get(0).getDataStatus());
        assertEquals(true,workspace.get("canSettle"));assertEquals(false,workspace.get("canConfirm"));
    }

    @Test void independentProfitConfirmationIsBlockedBeforePersistingPartialResults()
    {
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);draft.setRewardPolicyVersion("INDEPENDENT_V1");
        BusinessProjectKpiPlanItem profit=item();profit.setSourceType("PROFIT");
        when(projectMapper.selectProjectById(1L)).thenReturn(project());when(mapper.selectSettlementById(20L)).thenReturn(draft);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(profit));
        when(accountingService.projectDashboard(eq(1L),any(),eq(9L),eq(true))).thenReturn(pendingCosts());
        assertThrows(ServiceException.class,()->service.submit(20L,9L,"owner9",false));
        verify(mapper,never()).upsertSettlementResult(any());verify(mapper,never()).submitSettlement(any(),any(),any(),any(),any(),any());
    }

    @Test void legacyCostPreviewKeepsFrozenCompatibilityMethod()
    {
        BusinessProjectKpiPlan plan=plan();plan.setPlanId(10L);plan.setRewardPolicyVersion("LEGACY_LINKED");
        BusinessProjectKpiSettlement draft=settlement("DRAFT",0);draft.setRewardPolicyVersion("LEGACY_LINKED");
        BusinessProjectKpiPlanItem cost=item();cost.setSourceType("PERSONNEL_COST");
        when(projectMapper.selectProjectById(1L)).thenReturn(project());when(mapper.selectLatestPlanId(1L)).thenReturn(10L);
        when(mapper.selectPlanById(10L)).thenReturn(plan);when(mapper.selectSettlementByPlanId(10L)).thenReturn(draft);
        when(mapper.selectPlanItems(10L)).thenReturn(Collections.singletonList(cost));when(mapper.selectBonusTiers(10L)).thenReturn(tiers());
        when(accountingService.projectDashboard(eq(1L),any(),eq(9L),eq(true))).thenReturn(pendingCosts());
        BusinessProjectKpiSettlement shown=((BusinessProjectKpiPlan)service.workspace(1L,null,9L,false,false).get("selectedPlan")).getSettlement();
        assertEquals(new BigDecimal("10"),shown.getResults().get(0).getActualValue());
        assertEquals("READY",shown.getDataStatus());
    }

    private Map<String,Object> pendingCosts()
    {
        Map<String,Object> result=new java.util.LinkedHashMap<String,Object>();result.put("costPolicyVersion","ACTUAL_WORK_V1");
        result.put("pendingCostCount",2);result.put("summary",Collections.<String,Object>singletonMap("personnelCost",new BigDecimal("10")));return result;
    }

    private BusinessProject separatedProject(String status,String accountingState)
    {
        BusinessProject p=project();p.setStatus(status);p.setDeliveryPolicyVersion("SEPARATED_V1");
        p.setAccountingState(accountingState);p.setActualEndDate(java.sql.Date.valueOf("2026-07-20"));return p;
    }

    private BusinessProject project()
    {
        BusinessProject project=new BusinessProject();project.setProjectId(1L);project.setProjectName("直播增长项目");
        project.setMainOwnerUserId(9L);project.setSponsorOwnerUserId(8L);project.setStatus("ACTIVE");project.setBaseCurrency("CNY");
        return project;
    }

    private BusinessProjectKpi target(Long id,BigDecimal weight)
    {
        BusinessProjectKpi target=new BusinessProjectKpi();target.setKpiId(id);target.setProjectId(1L);
        target.setKpiCode("SALES");target.setKpiName("销售额");target.setMetricType("AMOUNT");target.setUnit("元");
        target.setTargetValue(new BigDecimal("100"));target.setWeight(weight);target.setDirection("HIGHER_BETTER");
        target.setAggregateType("SUM");target.setSourceType("MANUAL");target.setStatus("CURRENT");return target;
    }

    private BusinessProjectKpiPlan plan()
    {
        BusinessProjectKpiPlan plan=new BusinessProjectKpiPlan();plan.setProjectId(1L);plan.setCycleType("MONTH");
        plan.setCycleStart(java.sql.Date.valueOf("2026-08-01"));plan.setCycleEnd(java.sql.Date.valueOf("2026-08-31"));return plan;
    }

    private BusinessProjectKpiPlanItem item()
    {
        BusinessProjectKpiPlanItem item=new BusinessProjectKpiPlanItem();item.setItemId(101L);item.setPlanId(10L);
        item.setKpiId(1L);item.setKpiCode("SALES");item.setKpiName("销售额");item.setTargetValue(new BigDecimal("100"));
        item.setWeight(new BigDecimal("100"));item.setDirection("HIGHER_BETTER");return item;
    }

    private BusinessProjectKpiSettlement settlement(String status,int version)
    {
        BusinessProjectKpiSettlement settlement=new BusinessProjectKpiSettlement();settlement.setSettlementId(20L);
        settlement.setPlanId(10L);settlement.setProjectId(1L);settlement.setPeriodStart(java.sql.Date.valueOf("2026-07-01"));
        settlement.setPeriodEnd(java.sql.Date.valueOf("2026-07-31"));settlement.setStatus(status);settlement.setVersion(version);
        return settlement;
    }

    private java.util.List<BusinessProjectBonusTier> tiers()
    {
        BusinessProjectBonusTier low=new BusinessProjectBonusTier();low.setTierName("未达标");low.setMinScore(BigDecimal.ZERO);
        low.setMaxScore(new BigDecimal("100"));low.setBonusAmount(BigDecimal.ZERO);
        BusinessProjectBonusTier high=new BusinessProjectBonusTier();high.setTierName("达标");high.setMinScore(new BigDecimal("100"));
        high.setMaxScore(null);high.setBonusAmount(new BigDecimal("30000"));
        return Arrays.asList(low,high);
    }
}
