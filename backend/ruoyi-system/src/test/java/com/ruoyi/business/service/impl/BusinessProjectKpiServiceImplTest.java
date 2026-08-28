package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
        when(projectMapper.selectProjectById(1L)).thenReturn(project());
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

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.voidPlan(10L,8L,"boss8",false,true));

        assertTrue(error.getMessage().contains("仅可作废未提交、未入账"));
        verify(mapper,never()).voidDraftSettlement(any(),any(),any());
        verify(mapper,never()).voidPublishedPlan(any(),any(),any());
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
