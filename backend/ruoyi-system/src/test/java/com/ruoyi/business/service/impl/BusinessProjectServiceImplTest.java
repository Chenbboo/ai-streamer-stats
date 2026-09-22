package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.lenient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectStageAcceptance;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectTaskReport;
import com.ruoyi.business.domain.BusinessProjectProgressReport;
import com.ruoyi.business.domain.BusinessProjectWorkPeriod;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectRoutineDailyTarget;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.OnlineUserPermissionService;

@ExtendWith(MockitoExtension.class)
class BusinessProjectServiceImplTest
{
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Mock
    private BusinessProjectMapper mapper;
    @Mock private com.ruoyi.business.mapper.BusinessProjectProposalMapper proposalMapper;
    @Mock private com.ruoyi.business.mapper.BusinessAllocationRequestMapper allocationRequests;

    @Mock
    private BusinessProjectKpiMapper kpiMapper;

    @Mock
    private BusinessAccountingMapper accountingMapper;
    @Mock private com.ruoyi.business.mapper.BusinessPublicExpenseMapper publicExpenses;
    @Mock private BusinessMemberDayCostService memberDays;
    @Mock private com.ruoyi.business.mapper.BusinessProjectWorkMapper workMapper;
    @Mock private com.ruoyi.business.mapper.BusinessIncentiveMapper incentiveMapper;
    @Mock private com.ruoyi.business.attendance.BusinessFeishuService feishuService;

    @Mock
    private IBusinessAccountingService accountingService;

    @Mock
    private BusinessFileService businessFileService;

    @Mock
    private OnlineUserPermissionService onlineUserPermissionService;

    @InjectMocks
    private BusinessProjectServiceImpl service;

    @BeforeEach
    void currentProjectLockUsesTestFixture()
    {
        lenient().when(allocationRequests.selectPending(anyLong())).thenReturn(null);
        lenient().when(mapper.selectRoutineByIdForUpdate(anyLong())).thenAnswer(call->mapper.selectRoutineById(call.getArgument(0)));
        lenient().when(feishuService.getAuthority(any(),any())).thenReturn(Collections.emptyMap());
        lenient().when(mapper.selectProjectByIdForUpdate(anyLong()))
            .thenAnswer(invocation -> mapper.selectProjectById(invocation.getArgument(0)));

        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        lenient().when(companyAccess.staff(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> mapper.selectStaffCompanyId(call.getArgument(1))!=null);
        lenient().when(companyAccess.allowed(eq(23L),org.mockito.ArgumentMatchers.any(),eq("BUSINESS"))).thenReturn(true);
        lenient().when(companyAccess.project(1L,7L)).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(feishuService,"companyAccess",companyAccess);
        org.springframework.test.util.ReflectionTestUtils.setField(businessFileService,"companyAccess",companyAccess);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}

    @Test
    void ownerHandoffRequiresExistingPublicExpenseAllocationSubmission()
    {
        BusinessProject project=project(15L,9L,"ACTIVE","APPROVED");project.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(publicExpenses.countProjectUnsubmitted(15L)).thenReturn(1);
        ServiceException error=assertThrows(ServiceException.class,()->service.changeOwner(15L,10L,"项目交接",8L,"boss",true));
        assertTrue(error.getMessage().contains("公共费用"));
        verify(mapper,never()).updateProjectOwner(any(),any(),any(),any(),any());
    }

    @Test
    void newKpiUsesSystemGeneratedCodeAndIgnoresClientCode()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectNextKpiVersion(eq(15L), any(String.class))).thenReturn(1);
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setProjectId(15L);
        input.setKpiCode("MANUAL_CODE");
        input.setKpiName("有效播放量");
        input.setTargetValue(new BigDecimal("500000"));

        BusinessProjectKpi saved = service.saveKpi(input, 8L, "boss8", true);

        assertTrue(saved.getKpiCode().matches("KPI_P15_[A-F0-9]{12}"));
        assertTrue(!"MANUAL_CODE".equals(saved.getKpiCode()));
        assertEquals(1, saved.getTargetVersion());
        verify(mapper).selectCurrentProjectKpi(15L, saved.getKpiCode());
        verify(mapper).insertProjectKpi(input);
    }

    @Test
    void tenThousandYuanKpiIsAnAmountMetric()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L); project.setBaseCurrency("CNY");
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectNextKpiVersion(eq(15L), any(String.class))).thenReturn(1);
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setProjectId(15L); input.setKpiName("销售额"); input.setMetricType("COUNT");
        input.setUnit("万元"); input.setTargetValue(new BigDecimal("10"));

        BusinessProjectKpi saved = service.saveKpi(input, 8L, "boss8", true);

        assertEquals("AMOUNT", saved.getMetricType());
        assertEquals("万元", saved.getUnit());
        verify(mapper).insertProjectKpi(input);
    }

    @Test
    void adjustedKpiVersionKeepsOriginalSystemCode()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        BusinessProjectKpi previous = new BusinessProjectKpi();
        previous.setKpiId(70L);
        previous.setProjectId(15L);
        previous.setKpiCode("KPI_P15_A1B2C3D4E5F6");
        previous.setStatus("CURRENT");
        previous.setWeight(new BigDecimal("60"));
        BusinessProjectKpi another = new BusinessProjectKpi();
        another.setKpiId(71L);
        another.setProjectId(15L);
        another.setStatus("CURRENT");
        another.setWeight(new BigDecimal("40"));
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectProjectKpiById(70L)).thenReturn(previous);
        when(mapper.selectProjectKpis(15L)).thenReturn(Arrays.asList(previous, another));
        when(mapper.retireProjectKpi(70L, "boss8")).thenReturn(1);
        when(mapper.selectNextKpiVersion(15L, "KPI_P15_A1B2C3D4E5F6")).thenReturn(2);
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setKpiId(70L);
        input.setProjectId(15L);
        input.setKpiCode("ATTEMPT_CHANGE");
        input.setKpiName("有效播放量");
        input.setTargetValue(new BigDecimal("600000"));
        input.setWeight(new BigDecimal("50"));

        BusinessProjectKpi saved = service.saveKpi(input, 8L, "boss8", true);

        assertEquals("KPI_P15_A1B2C3D4E5F6", saved.getKpiCode());
        assertEquals(2, saved.getTargetVersion());
    }

    @Test
    void routineKpiKeepsValidatedAutomaticSourceReference()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(301L);routine.setProjectId(15L);routine.setRoutineName("每日制作视频");routine.setUnit("条");
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectRoutineById(301L)).thenReturn(routine);
        when(mapper.selectNextKpiVersion(eq(15L), any(String.class))).thenReturn(1);
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setProjectId(15L);input.setKpiName("制作视频数量");input.setTargetValue(new BigDecimal("100"));
        input.setSourceType("ROUTINE");input.setSourceRefId(301L);input.setUnit("条");

        BusinessProjectKpi saved = service.saveKpi(input, 8L, "boss8", true);

        assertEquals("ROUTINE", saved.getSourceType());
        assertEquals(301L, saved.getSourceRefId());
        verify(mapper).insertProjectKpi(input);
        input.setUnit("万元");
        assertThrows(ServiceException.class, () -> service.saveKpi(input, 8L, "boss8", true));
    }

    @Test
    void kpiSaveRejectsProjectWeightAboveOneHundred()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        BusinessProjectKpi current = new BusinessProjectKpi();
        current.setKpiId(70L);
        current.setProjectId(15L);
        current.setStatus("CURRENT");
        current.setWeight(new BigDecimal("60"));
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectProjectKpis(15L)).thenReturn(Collections.singletonList(current));
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setProjectId(15L);
        input.setKpiName("新增收入");
        input.setTargetValue(new BigDecimal("20000"));
        input.setWeight(new BigDecimal("50"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveKpi(input, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("KPI权重合计不能超过100%"));
        assertTrue(error.getMessage().contains("本项最多可填40%"));
        verify(mapper, never()).insertProjectKpi(any());
    }

    @Test
    void projectListAlwaysCarriesCurrentUserScope()
    {
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("keyword", "直播");
        when(mapper.selectProjectList(any())).thenReturn(Collections.<BusinessProject>emptyList());

        service.listProjects(query, 23L, false, false);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(mapper).selectProjectList(captor.capture());
        assertEquals(23L, captor.getValue().get("userId"));
        assertEquals(false, captor.getValue().get("viewAll"));
        assertEquals(false, captor.getValue().get("boss"));
        assertEquals("直播", captor.getValue().get("keyword"));
    }

    @Test
    void bossDashboardUsesDatabasePaginationInsteadOfLoadingAllProjects()
    {
        Map<String,Object> summary=new HashMap<String,Object>();
        summary.put("totalCount",126L);summary.put("pendingDecisionCount",31L);
        BusinessProject project=project(91L,23L,"ACTIVE","APPROVED");
        BusinessProject decision=project(88L,23L,"PAUSED","APPROVED");
        when(mapper.selectDashboardSummary(23L,false,true)).thenReturn(summary);
        when(mapper.selectDashboardProjectPage(23L,false,true,20,10,"",""))
            .thenReturn(Collections.singletonList(project));
        when(mapper.selectDashboardDecisionPage(23L,false,true,5,5))
            .thenReturn(Collections.singletonList(decision));
        Map<String,Object> query=new HashMap<String,Object>();
        query.put("projectPageNum",3);query.put("projectPageSize",10);
        query.put("decisionPageNum",2);query.put("decisionPageSize",5);

        Map<String,Object> result=service.dashboard(query,23L,false,true);

        @SuppressWarnings("unchecked") Map<String,Object> projectPage=(Map<String,Object>)result.get("projectPage");
        @SuppressWarnings("unchecked") Map<String,Object> decisionPage=(Map<String,Object>)result.get("decisionPage");
        assertEquals(126L,projectPage.get("total"));
        assertEquals(3,projectPage.get("pageNum"));
        assertEquals(Collections.singletonList(project),projectPage.get("rows"));
        assertEquals(31L,decisionPage.get("total"));
        assertEquals(2,decisionPage.get("pageNum"));
        assertEquals(Collections.singletonList(decision),result.get("decisions"));
        verify(mapper,never()).selectProjectList(any());
    }

    @Test
    void bossDashboardFiltersWithinAuthorizedScopeAndClampsPage()
    {
        Map<String,Object> summary=new HashMap<String,Object>();
        summary.put("totalCount",126L);
        when(mapper.selectDashboardSummary(23L,false,true)).thenReturn(summary);
        when(mapper.countDashboardProjects(23L,false,true,"美团","ACTIVE")).thenReturn(5L);
        Map<String,Object> query=new HashMap<String,Object>();
        query.put("projectPageNum",9);query.put("projectPageSize",4);
        query.put("projectKeyword"," 美团 ");query.put("projectStatus","ACTIVE");
        Map<String,Object> result=service.dashboard(query,23L,false,true);
        @SuppressWarnings("unchecked") Map<String,Object> page=(Map<String,Object>)result.get("projectPage");
        assertEquals(5L,page.get("total"));
        assertEquals(2,page.get("pageNum"));
        assertEquals(4,page.get("pageSize"));
        assertEquals(summary,result.get("summary"));
        verify(mapper).selectDashboardProjectPage(23L,false,true,4,4,"美团","ACTIVE");
        when(mapper.countDashboardProjects(23L,false,true,"美团","ACTIVE")).thenReturn(0L);
        @SuppressWarnings("unchecked") Map<String,Object> emptyPage=(Map<String,Object>)service.dashboard(query,23L,false,true).get("projectPage");
        assertEquals(0L,emptyPage.get("total"));
        assertEquals(1,emptyPage.get("pageNum"));
        verify(mapper).selectDashboardProjectPage(23L,false,true,0,4,"美团","ACTIVE");
    }

    @Test
    void bossPendingIncludesBonusPaymentsAndAcceptsPaymentCategory()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("bonusPaymentCount",3L);counts.put("incentiveReviewCount",2L);
        when(mapper.selectBossPendingCounts(eq(23L),eq(false),any(Date.class))).thenReturn(counts);
        assertEquals(5L,service.bossPending(Collections.emptyMap(),23L,false).get("total"));
        Map<String,Object> query=new HashMap<String,Object>();
        query.put("category","bonus_payment");query.put("pageNum",2);query.put("pageSize",1);
        Map<String,Object> result=service.bossPending(query,23L,false);
        assertEquals(3L,result.get("total"));assertEquals("BONUS_PAYMENT",result.get("category"));
        verify(mapper).selectBossPendingPage(eq(23L),eq(false),any(Date.class),eq("BONUS_PAYMENT"),eq(1),eq(1));
    }

    @Test
    void bossPendingIncludesAwardCountAndAcceptsAwardCategory()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("incentiveReviewCount",2L);counts.put("accountingCount",1L);
        when(mapper.selectBossPendingCounts(eq(23L),eq(false),any(Date.class))).thenReturn(counts);
        Map<String,Object> all=service.bossPending(Collections.emptyMap(),23L,false);
        assertEquals(3L,all.get("total"));
        assertEquals(3L,((Map<?,?>)all.get("counts")).get("totalCount"));
        Map<String,Object> query=new HashMap<String,Object>();query.put("category","incentive_review");
        query.put("pageNum",2);query.put("pageSize",1);
        Map<String,Object> awards=service.bossPending(query,23L,false);
        assertEquals(2L,awards.get("total"));assertEquals("INCENTIVE_REVIEW",awards.get("category"));
        verify(mapper).selectBossPendingPage(eq(23L),eq(false),any(Date.class),eq("INCENTIVE_REVIEW"),eq(1),eq(1));
    }

    @Test
    void bossPendingUsesOneServerPageAndCategoryCounts()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("proposalCount",2L);counts.put("accountingCount",5L);counts.put("stageAcceptanceCount",6L);counts.put("kpiMissingCount",7L);
        counts.put("kpiReviewCount",1L);counts.put("personnelCostCount",3L);counts.put("projectCount",4L);
        Map<String,Object> row=new HashMap<String,Object>();row.put("category","KPI_MISSING");row.put("projectId",17L);
        when(mapper.selectBossPendingCounts(eq(23L),eq(false),any(Date.class))).thenReturn(counts);
        when(mapper.selectBossPendingPage(eq(23L),eq(false),any(Date.class),eq("KPI_MISSING"),eq(5),eq(5)))
            .thenReturn(Collections.singletonList(row));
        Map<String,Object> query=new HashMap<String,Object>();query.put("category","kpi_missing");
        query.put("pageNum",2);query.put("pageSize",5);

        Map<String,Object> result=service.bossPending(query,23L,false);

        assertEquals(7L,result.get("total"));
        assertEquals(2,result.get("pageNum"));
        assertEquals("KPI_MISSING",result.get("category"));
        assertEquals(Collections.singletonList(row),result.get("rows"));
    }

    @Test
    void bossPendingIncludesStageAcceptancesInTheDefaultAllCategory()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("proposalCount",0L);counts.put("accountingCount",0L);counts.put("stageAcceptanceCount",1L);
        counts.put("kpiMissingCount",0L);counts.put("kpiReviewCount",0L);counts.put("personnelCostCount",0L);
        counts.put("projectCount",0L);
        Map<String,Object> row=new HashMap<String,Object>();row.put("category","STAGE_ACCEPTANCE");
        row.put("stageAcceptanceId",701L);row.put("attachmentUrls","/profile/upload/result.pdf");
        when(mapper.selectBossPendingCounts(eq(23L),eq(false),any(Date.class))).thenReturn(counts);
        when(mapper.selectBossPendingPage(eq(23L),eq(false),any(Date.class),eq("ALL"),eq(0),eq(5)))
            .thenReturn(Collections.singletonList(row));

        Map<String,Object> result=service.bossPending(Collections.<String,Object>emptyMap(),23L,false);

        assertEquals(1L,result.get("total"));
        assertEquals("ALL",result.get("category"));
        assertEquals(Collections.singletonList(row),result.get("rows"));
        assertEquals(1L,((Map<?,?>)result.get("counts")).get("totalCount"));
    }

    @Test
    void bossPendingIncludesAccountingDraftsInTheAllCategory()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("proposalCount",0L);counts.put("accountingCount",1L);counts.put("kpiMissingCount",0L);
        counts.put("kpiReviewCount",0L);counts.put("personnelCostCount",0L);counts.put("projectCount",0L);
        Map<String,Object> row=new HashMap<String,Object>();row.put("category","ACCOUNTING");row.put("factId",301L);
        when(mapper.selectBossPendingCounts(eq(23L),eq(false),any(Date.class))).thenReturn(counts);
        when(mapper.selectBossPendingPage(eq(23L),eq(false),any(Date.class),eq("ALL"),eq(0),eq(5)))
            .thenReturn(Collections.singletonList(row));

        Map<String,Object> result=service.bossPending(Collections.<String,Object>emptyMap(),23L,false);

        assertEquals(1L,result.get("total"));
        assertEquals(Collections.singletonList(row),result.get("rows"));
        assertEquals(1L,((Map<?,?>)result.get("counts")).get("totalCount"));
    }

    @Test
    void ownerWorkbenchOnlyQueriesProjectsOwnedByCurrentUser()
    {
        when(mapper.selectProjectList(any())).thenReturn(Collections.<BusinessProject>emptyList());

        Map<String,Object> result=service.ownerWorkbench(null,23L,false);

        ArgumentCaptor<Map<String,Object>> captor=mapCaptor();
        verify(mapper).selectProjectList(captor.capture());
        assertEquals(23L,captor.getValue().get("userId"));
        assertEquals(true,captor.getValue().get("ownerOnly"));
        assertEquals(0,((Map<?,?>)result.get("summary")).get("projectCount"));
        assertEquals(Collections.emptyList(),result.get("allocationAlerts"));
        assertEquals(Collections.emptyList(),result.get("pendingEffortRequests"));
        verify(mapper).selectOwnerPersonnelCostReadiness(eq(23L),any(Date.class),eq(false));
        verify(mapper).selectOwnerPendingEffortRequests(23L,false);
    }

    @Test
    void ownerWorkbenchReturnsProjectsWithMissingMemberAllocation()
    {
        BusinessProject owned = project(81L,23L,"ACTIVE","APPROVED");
        owned.setProjectName("王老吉视频宣传");
        when(mapper.selectProjectList(any())).thenReturn(Collections.singletonList(owned));
        when(mapper.selectProjectById(81L)).thenReturn(owned);
        Map<String,Object> alert = new HashMap<String,Object>();
        alert.put("projectId",81L);
        alert.put("projectName","王老吉视频宣传");
        alert.put("missingAllocationCount",2);
        alert.put("missingMemberNames","石头、蒋豪");
        when(mapper.selectOwnerPersonnelCostReadiness(eq(23L),any(Date.class),eq(false)))
            .thenReturn(Collections.singletonList(alert));
        Map<String,Object> pendingEffort = new HashMap<String,Object>();
        pendingEffort.put("effortId",501L);pendingEffort.put("projectId",81L);
        pendingEffort.put("userName","石头");pendingEffort.put("deviationReason","临时支援");
        when(mapper.selectOwnerPendingEffortRequests(23L,false))
            .thenReturn(Collections.singletonList(pendingEffort));
        Map<String,Object> revenueCategory = new HashMap<String,Object>();
        revenueCategory.put("categoryId",1L);revenueCategory.put("factKind","REVENUE");revenueCategory.put("categoryCode","SALES_REVENUE");
        Map<String,Object> costCategory = new HashMap<String,Object>();
        costCategory.put("categoryId",2L);costCategory.put("factKind","COST");costCategory.put("categoryCode","OTHER_EXPENSE");
        Map<String,Object> legacyDirectCategory = new HashMap<String,Object>();
        legacyDirectCategory.put("categoryId",4L);legacyDirectCategory.put("factKind","COST");legacyDirectCategory.put("categoryCode","DIRECT_EXPENSE");
        Map<String,Object> bonusCategory = new HashMap<String,Object>();
        bonusCategory.put("categoryId",3L);bonusCategory.put("factKind","COST");bonusCategory.put("categoryCode","PROJECT_BONUS_COST");
        when(accountingMapper.selectCategories()).thenReturn(Arrays.asList(revenueCategory,costCategory,legacyDirectCategory,bonusCategory));
        Map<String,Object> dailyRevenue = new HashMap<String,Object>();
        dailyRevenue.put("confirmedAmount",new BigDecimal("120.00"));
        dailyRevenue.put("draftAmount",new BigDecimal("30.00"));
        when(accountingMapper.selectProjectRevenueSummary(eq(81L),any(Date.class))).thenReturn(dailyRevenue);
        BusinessProjectTaskReport taskReport = new BusinessProjectTaskReport();
        taskReport.setReportId(901L);taskReport.setTaskId(301L);taskReport.setProjectId(81L);
        taskReport.setProgress(60);taskReport.setCompletionSummary("完成粗剪");taskReport.setSubmittedUserName("石头");
        when(mapper.selectTaskReports(81L)).thenReturn(Collections.singletonList(taskReport));

        Map<String,Object> result=service.ownerWorkbench(81L,23L,false);

        assertEquals(Collections.singletonList(alert),result.get("allocationAlerts"));
        assertEquals(Collections.singletonList(pendingEffort),result.get("pendingEffortRequests"));
        Map<?,?> accounting=(Map<?,?>)result.get("accounting");
        assertEquals(Collections.singletonList(revenueCategory),accounting.get("revenueCategories"));
        assertEquals(Collections.singletonList(costCategory),accounting.get("expenseCategories"));
        assertEquals(dailyRevenue,accounting.get("dailyRevenue"));
        assertEquals(Collections.singletonList(taskReport),result.get("taskReports"));
    }

    @Test
    void ownerWorkbenchYesterdaySpendUsesYesterdayCostsWithoutChangingTodayEntry()
    {
        java.time.LocalDate yesterday=java.time.LocalDate.now().minusDays(1);
        BusinessProject owned=project(81L,23L,"ACTIVE","APPROVED");
        owned.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectList(any())).thenReturn(Collections.singletonList(owned));
        when(mapper.selectProjectById(81L)).thenReturn(owned);
        BusinessOperatingFact expense=new BusinessOperatingFact();
        expense.setStatus("CONFIRMED");expense.setAmount(new BigDecimal("35.20"));
        when(accountingMapper.selectProjectDailySpendItems(eq(81L),any(Date.class)))
            .thenReturn(Collections.singletonList(expense));
        Map<String,Object> confirmedCosts=new HashMap<>();confirmedCosts.put("costAmount",new BigDecimal("70.20"));
        when(accountingMapper.sumProjectFacts(81L,java.sql.Date.valueOf(yesterday))).thenReturn(confirmedCosts);
        Map<String,Object> priced=new HashMap<>();priced.put("pricingStatus","PRICED");priced.put("amount",new BigDecimal("40.125"));
        Map<String,Object> pending=new HashMap<>();pending.put("pricingStatus","PENDING_COST");
        when(memberDays.calculate(owned,yesterday,yesterday))
            .thenReturn(Arrays.asList(priced,pending));

        Map<?,?> accounting=(Map<?,?>)service.ownerWorkbench(81L,23L,false).get("accounting");
        Map<?,?> yesterdaySpend=(Map<?,?>)accounting.get("yesterdaySpend");

        assertEquals(new BigDecimal("35.20"),((Map<?,?>)accounting.get("dailySpend")).get("amount"));
        assertEquals(yesterday.toString(),yesterdaySpend.get("bizDate"));
        assertEquals(new BigDecimal("40.13"),yesterdaySpend.get("personnelCost"));
        assertEquals(new BigDecimal("70.20"),yesterdaySpend.get("projectCost"));
        assertEquals(new BigDecimal("110.33"),yesterdaySpend.get("amount"));
        assertEquals(1,yesterdaySpend.get("pendingPersonnelCount"));
        verify(accountingMapper).selectProjectDailySpendItems(81L,java.sql.Date.valueOf(yesterday.plusDays(1)));
    }

    @Test
    void bossProjectListUsesInitiatorScopeInsteadOfViewingAll()
    {
        when(mapper.selectProjectList(any())).thenReturn(Collections.<BusinessProject>emptyList());

        service.listProjects(Collections.<String, Object>emptyMap(), 23L, false, true);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(mapper).selectProjectList(captor.capture());
        assertEquals(23L, captor.getValue().get("userId"));
        assertEquals(false, captor.getValue().get("viewAll"));
        assertEquals(true, captor.getValue().get("boss"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "RESULT_ACCEPTANCE", "STAGED_ACCEPTANCE"})
    void approvedProposalCreatesActiveProjectAndRegistersApplicantAsOwner(String closeMethod)
    {
        BusinessProjectProposal proposal = new BusinessProjectProposal();
        proposal.setProposalId(66L);
        proposal.setCloseMethod(closeMethod);
        proposal.setProjectName("东南亚直播增长");
        proposal.setProjectType("LIVE");
        proposal.setExecutionSource("LIVE");
        proposal.setAccountingMode("PROFIT");
        proposal.setManagementMode("SIMPLE");
        proposal.setApplicantUserId(9L);
        proposal.setSponsorOwnerUserId(23L);
        proposal.setCompanyDeptId(110L);
        proposal.setPlanStartDate(new Date());
        proposal.setPlanEndDate(new Date());
        Map<String,Object> selectedLine = new HashMap<String,Object>();
        selectedLine.put("userId",12L);
        proposal.setStaffingLines(Collections.singletonList(selectedLine));
        Map<String, Object> owner = new HashMap<String, Object>();
        owner.put("userName", "owner9");
        owner.put("nickName", "负责人九");
        Map<String, Object> sponsor = new HashMap<String, Object>();
        sponsor.put("userName", "boss23");
        sponsor.put("nickName", "审批老板");
        Map<String,Object> selectedUser = new HashMap<String,Object>();
        selectedUser.put("userName","member12"); selectedUser.put("nickName","成员十二");
        when(mapper.selectActiveUserById(9L)).thenReturn(owner);
        when(mapper.selectActiveUserById(23L)).thenReturn(sponsor);
        when(mapper.selectActiveUserById(12L)).thenReturn(selectedUser);
        when(mapper.selectCompanyById(110L)).thenReturn(Collections.<String,Object>singletonMap("deptId",110L));
        when(mapper.selectRoleIdByKey("project_user")).thenReturn(18L);
        when(mapper.selectRoleIdByKey("project_owner")).thenReturn(19L);
        final BusinessProject[] stored = new BusinessProject[1];
        doAnswer(invocation -> {
            stored[0] = invocation.getArgument(0);
            stored[0].setProjectId(88L);
            return 1;
        }).when(mapper).insertProject(any(BusinessProject.class));
        when(mapper.selectProjectById(88L)).thenAnswer(invocation -> stored[0]);
        when(mapper.selectMembers(88L)).thenReturn(Collections.<BusinessProjectMember>emptyList());
        when(mapper.selectMilestones(88L)).thenReturn(Collections.emptyList());
        when(mapper.selectTasks(88L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(88L)).thenReturn(Collections.emptyList());
        when(mapper.selectEvents(88L)).thenReturn(Collections.emptyList());

        BusinessProject created = service.createApprovedProject(proposal, 23L, "boss23");

        assertEquals(88L, created.getProjectId());
        assertEquals(null, created.getAcceptanceCriteria());
        assertEquals(closeMethod, created.getCloseMethod());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("APPROVED", created.getBaselineStatus());
        assertEquals(Integer.valueOf(0),created.getBaselineVersion());
        assertEquals(66L, created.getSourceProposalId());
        assertEquals(9L, created.getApplicantUserId());
        assertEquals(23L, created.getSponsorOwnerUserId());
        assertEquals("审批老板", created.getSponsorOwnerName());
        assertEquals("负责人九", created.getMainOwnerName());
        assertEquals("LIVE", created.getExecutionSource());
        assertEquals("LIGHT", created.getManagementMode());
        assertTrue(created.getProjectNo().startsWith("XM"));
        ArgumentCaptor<BusinessProjectMember> member = ArgumentCaptor.forClass(BusinessProjectMember.class);
        verify(mapper,times(2)).upsertMember(member.capture());
        assertEquals(9L, member.getAllValues().get(0).getUserId());
        assertEquals("OWNER", member.getAllValues().get(0).getMemberRole());
        assertEquals(12L, member.getAllValues().get(1).getUserId());
        assertEquals("MEMBER", member.getAllValues().get(1).getMemberRole());
        verify(mapper).insertUserRole(9L, 18L);
        verify(mapper).insertUserRole(9L, 19L);
        verify(mapper).insertUserRole(12L,18L);
        verify(onlineUserPermissionService).refreshAfterCommit(9L);
        verify(onlineUserPermissionService).refreshAfterCommit(12L);
        verify(mapper).insertExecutionRelation(eq(88L), any(Date.class),
            eq("LIVE:BUSINESS_SCOPE:ALL:EXECUTION_SOURCE"), eq("boss23"));
    }

    @Test
    void employeeWorkDashboardOnlyUsesCurrentUsersAssignments()
    {
        Map<String,Object> routine = new HashMap<String,Object>();
        routine.put("todayReportId", 20L);
        when(mapper.selectMyWorkTasks(org.mockito.ArgumentMatchers.eq(147L), any(), any()))
            .thenReturn(Collections.<Map<String,Object>>emptyList());
        when(mapper.selectMyWorkRoutines(org.mockito.ArgumentMatchers.eq(147L), any(), any(), any()))
            .thenReturn(Collections.singletonList(routine));

        Map<String,Object> result = service.workDashboard("DAY", "2026-08-11", 147L);

        assertEquals(1, ((List<?>) result.get("routines")).size());
        assertEquals(1, ((Map<?,?>) result.get("summary")).get("reportedRoutineCount"));
        verify(mapper).selectMyWorkTasks(147L, "2026-08-11", "2026-08-11");
        verify(mapper).selectMyWorkRoutines(147L, "2026-08-11", "2026-08-11",
            new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()));
    }

    @Test
    void workDashboardKeepsMultipleProjectsAndTreatsLeaveAsHandled()
    {
        Map<String,Object> first = new HashMap<String,Object>();first.put("projectId", 81L);first.put("todayReportId", 20L);
        Map<String,Object> second = new HashMap<String,Object>();second.put("projectId", 82L);second.put("todayLeaveId", 30L);
        when(mapper.selectMyWorkTasks(eq(147L), any(), any())).thenReturn(Collections.<Map<String,Object>>emptyList());
        when(mapper.selectMyWorkRoutines(eq(147L), any(), any(), any())).thenReturn(Arrays.asList(first, second));

        Map<String,Object> result = service.workDashboard("DAY", "2026-08-11", 147L);

        List<?> routines=(List<?>)result.get("routines");
        assertEquals(2,routines.size());
        assertEquals(81L,((Map<?,?>)routines.get(0)).get("projectId"));
        assertEquals(82L,((Map<?,?>)routines.get(1)).get("projectId"));
        assertEquals(2,((Map<?,?>)result.get("summary")).get("reportedRoutineCount"));
    }

    @Test
    void workDashboardReturnsConfirmedBonusTotalsForParticipatingProjects()
    {
        Map<String,Object> first = new HashMap<String,Object>();
        first.put("projectId", 81L); first.put("projectName", "直播项目");
        first.put("totalBonus", new BigDecimal("3800.00"));
        Map<String,Object> second = new HashMap<String,Object>();
        second.put("projectId", 82L); second.put("projectName", "短视频项目");
        second.put("totalBonus", BigDecimal.ZERO);
        when(kpiMapper.selectMemberProjectBonusTotals(147L)).thenReturn(Arrays.asList(first, second));

        Map<String,Object> result = service.workDashboard("DAY", "2026-08-11", 147L);

        List<?> bonuses = (List<?>) result.get("projectBonuses");
        assertEquals(2, bonuses.size());
        assertEquals(new BigDecimal("3800.00"), ((Map<?,?>) bonuses.get(0)).get("totalBonus"));
        verify(kpiMapper).selectMemberProjectBonusTotals(147L);
    }

    @Test
    void assignedEmployeeCanOnlyUpdateTaskProgress()
    {
        BusinessProject project = project(77L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectTask current = new BusinessProjectTask();
        current.setTaskId(30L); current.setProjectId(77L); current.setTaskName("发布视频");
        current.setAssigneeUserId(147L); current.setStatus("TODO"); current.setProgress(0);
        current.setPriority("MEDIUM"); current.setVersion(2);
        when(mapper.selectProjectById(77L)).thenReturn(project);
        when(mapper.selectMemberRole(77L, 147L)).thenReturn("MEMBER");
        when(mapper.selectTaskById(30L)).thenReturn(current);
        when(mapper.updateTask(current)).thenReturn(1);
        BusinessProjectTask update = new BusinessProjectTask();
        update.setProjectId(77L); update.setTaskId(30L); update.setTaskName("恶意改名");
        update.setStatus("DONE"); update.setProgress(50); update.setVersion(2);

        BusinessProjectTask saved = service.saveTask(update, 147L, "shitou", false);

        assertEquals("发布视频", saved.getTaskName());
        assertEquals("DONE", saved.getStatus());
        assertEquals(100, saved.getProgress());
        verify(mapper).insertEvent(any());
    }

    @Test
    void projectOwnerCannotConfirmBaselineForBoss()
    {
        BusinessProject project = project(31L, 9L, "PLANNING", "SUBMITTED");
        when(mapper.selectProjectById(31L)).thenReturn(project);
        when(mapper.selectMemberRole(31L, 9L)).thenReturn("OWNER");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(31L, "CONFIRM_BASELINE", null, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("老板"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void unrelatedMemberCannotReadProjectDetail()
    {
        BusinessProject project = project(42L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(42L)).thenReturn(project);
        when(mapper.selectMemberRole(42L, 77L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.getProject(42L, 77L, false, false));

        assertTrue(error.getMessage().contains("无权查看"));
        verify(mapper, never()).selectTasks(42L);
    }

    @Test
    void oneBossCannotReadAnotherBossProjectEvenWhenAddedAsMember()
    {
        BusinessProject project = project(43L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(7L);
        when(mapper.selectProjectById(43L)).thenReturn(project);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.getProject(43L, 8L, false, true));

        assertTrue(error.getMessage().contains("未授权公司"));
        verify(mapper, never()).selectMemberRole(43L, 8L);
        verify(mapper, never()).selectTasks(43L);
    }

    @Test
    void oneBossCannotManageAnotherBossProjectByGuessingId()
    {
        BusinessProject project = project(44L, 9L, "DRAFT", "DRAFT");
        project.setInitiatorUserId(7L);
        when(mapper.selectProjectById(44L)).thenReturn(project);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(44L, "START_PLANNING", null, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("未授权公司"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void companyOwnerDoesNotReceiveRedundantProjectUserRole()
    {
        BusinessProject project = project(51L, 1L, "DRAFT", "DRAFT");
        BusinessProjectMember member = new BusinessProjectMember();
        member.setProjectId(51L);
        member.setUserId(9L);
        member.setMemberRole("MEMBER");
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("userName", "boss9");
        user.put("nickName", "老板九");
        when(mapper.selectProjectById(51L)).thenReturn(project);
        when(mapper.selectActiveUserById(9L)).thenReturn(user);
        when(mapper.countUserRoleByKey(9L, "company_owner")).thenReturn(1);

        service.saveMember(member, 1L, "admin", true);

        verify(mapper, never()).selectRoleIdByKey("project_user");
        verify(mapper, never()).insertUserRole(any(), any());
    }

    @Test
    void mainOwnerAssigningDeputyGrantsDedicatedRoleAndRefreshesOnlinePermissions()
    {
        BusinessProject project = project(52L, 9L, "PLANNING", "DRAFT");
        BusinessProjectMember member = new BusinessProjectMember();
        member.setProjectId(52L);
        member.setUserId(10L);
        member.setMemberRole("DEPUTY");
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("userName", "deputy10");
        user.put("nickName", "副负责人十");
        when(mapper.selectProjectById(52L)).thenReturn(project);
        when(mapper.selectMemberRole(52L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(52L, 10L)).thenReturn(null);
        when(mapper.selectActiveUserById(10L)).thenReturn(user);
        when(mapper.selectRoleIdByKey("project_user")).thenReturn(18L);
        when(mapper.selectRoleIdByKey("project_deputy")).thenReturn(20L);
        when(mapper.countActiveProjectMembershipByRole(10L, "DEPUTY")).thenReturn(1);

        service.saveMember(member, 9L, "owner9", false);

        verify(mapper).insertUserRole(10L, 18L);
        verify(mapper).insertUserRole(10L, 20L);
        verify(onlineUserPermissionService).refreshAfterCommit(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "30"})
    void addingMemberSavesSelectedAllocationWithExistingConfirmationRules(String used)
    {
        BusinessProjectMember member = allocationMember("MEMBER");
        member.setAllocationPercent(new BigDecimal("25.50"));
        when(mapper.sumAllocationPercentAtDate(eq(10L), any(Date.class))).thenReturn(new BigDecimal(used));

        service.saveMember(member, 1L, "admin", true);

        ArgumentCaptor<BusinessProjectStaffAllocation> allocation = ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper).upsertMember(member);
        verify(mapper).insertProjectStaffAllocation(allocation.capture());
        assertEquals(new BigDecimal("25.50"), allocation.getValue().getAllocationValue());
        assertEquals(Long.valueOf(52L), allocation.getValue().getProjectId());
        assertEquals(Long.valueOf(10L), allocation.getValue().getUserId());
        assertEquals("0".equals(used) ? "CONFIRMED" : "PENDING", allocation.getValue().getConfirmationStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.01", "80"})
    void addingMemberRejectsInvalidOrOverbookedAllocation(String percent)
    {
        BusinessProjectMember member = allocationMember("MEMBER");
        member.setAllocationPercent(new BigDecimal(percent));
        when(mapper.sumAllocationPercentAtDate(eq(10L), any(Date.class))).thenReturn(new BigDecimal("30"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveMember(member, 1L, "admin", true));

        assertTrue(error.getMessage().contains("80".equals(percent) ? "本项目最多可设置70%" : "必须大于0且不超过100%"));
        verify(mapper, never()).insertProjectStaffAllocation(any());
    }

    @Test
    void addingObserverDoesNotCreateCostAllocation()
    {
        BusinessProjectMember member = allocationMember("OBSERVER");
        member.setAllocationPercent(new BigDecimal("50"));

        service.saveMember(member, 1L, "admin", true);

        verify(mapper).upsertMember(member);
        verify(mapper, never()).insertProjectStaffAllocation(any());
    }

    @Test
    void addingMemberWithoutExplicitAllocationKeepsDefaultBehavior()
    {
        BusinessProjectMember member = allocationMember("MEMBER");
        when(mapper.sumAllocationPercentAtDate(eq(10L), any(Date.class))).thenReturn(new BigDecimal("30"));

        service.saveMember(member, 1L, "admin", true);

        ArgumentCaptor<BusinessProjectStaffAllocation> allocation = ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper).insertProjectStaffAllocation(allocation.capture());
        assertEquals(0, new BigDecimal("70").compareTo(allocation.getValue().getAllocationValue()));
    }

    private BusinessProjectMember allocationMember(String role)
    {
        BusinessProject project = project(52L, 1L, "ACTIVE", "APPROVED");
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectById(52L)).thenReturn(project);
        when(mapper.selectActiveUserById(10L)).thenReturn(Collections.singletonMap("nickName", "成员十"));
        BusinessProjectMember member = new BusinessProjectMember();
        member.setProjectId(52L);
        member.setUserId(10L);
        member.setMemberRole(role);
        return member;
    }

    @Test
    void memberPreviewIncludesEndedAndUpcomingPeriodProjects()
    {
        allocationMember("MEMBER");
        Date today = com.ruoyi.common.utils.DateUtils.parseDate(com.ruoyi.common.utils.DateUtils.getDate());
        when(proposalMapper.selectStaffAllocationPeriodProjects(eq(10L), any(Date.class), any()))
            .thenReturn(Arrays.asList(row("projectId",90L,"projectEndDate","2000-01-01","allocationValue",20),
                row("projectId",91L,"projectStartDate","2099-01-01","allocationValue",30)));
        Map<String,Object> preview = service.memberAllocationPreview(52L,10L,1L,false);
        List<Map<String,Object>> period = (List<Map<String,Object>>)preview.get("periodProjects");
        assertEquals(2,period.size());
        assertEquals("ENDED",period.get(0).get("periodState"));
        assertEquals("UPCOMING",period.get(1).get("periodState"));
        assertEquals(false,period.get(0).get("editable"));
        assertEquals(com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",today),preview.get("effectiveDate"));
    }

    @Test
    void ordinaryMemberCannotPreviewAnotherEmployeesAllocation()
    {
        BusinessProject p=project(52L,1L,"ACTIVE","APPROVED");
        p.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectById(52L)).thenReturn(p);
        when(mapper.selectMemberRole(52L,12L)).thenReturn("MEMBER");
        assertThrows(ServiceException.class,()->service.memberAllocationPreview(52L,10L,12L,false));
        verify(mapper,never()).selectUserAllocationWorkspace(anyLong(),any(Date.class));
    }

    @Test
    void addingMemberSavesAllProjectRatiosTogether()
    {
        BusinessProjectMember member = memberWithAllocationPlan(1L);
        service.saveMember(member,1L,"admin",false);
        ArgumentCaptor<BusinessProjectStaffAllocation> saved=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper,times(2)).insertProjectStaffAllocation(saved.capture());
        assertEquals("APPLIED",member.getAllocationOutcome());
        assertTrue(saved.getAllValues().stream().anyMatch(a->a.getProjectId().equals(52L)&&a.getAllocationValue().compareTo(new BigDecimal("40"))==0));
        assertTrue(saved.getAllValues().stream().anyMatch(a->a.getProjectId().equals(93L)&&a.getAllocationValue().compareTo(new BigDecimal("60"))==0));
        verify(allocationRequests,never()).insertRequest(any());
    }

    @Test
    void returningMemberWithEndedAllocationCanJoinAndReallocate()
    {
        BusinessProjectMember member = memberWithAllocationPlan(1L);
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        when(mapper.selectUserAllocationTimeline(10L)).thenReturn(Collections.singletonList(
            row("projectId",52L,"allocationId",5L,"allocationValue",10,
                "effectiveFrom",yesterday.minusDays(1).toString(),"effectiveTo",yesterday.toString(),
                "confirmationStatus","CONFIRMED")));

        service.saveMember(member,1L,"admin",false);

        ArgumentCaptor<BusinessProjectStaffAllocation> saved=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper,times(2)).insertProjectStaffAllocation(saved.capture());
        assertEquals("APPLIED",member.getAllocationOutcome());
        assertTrue(saved.getAllValues().stream().anyMatch(a->a.getProjectId().equals(52L)
            && a.getAllocationValue().compareTo(new BigDecimal("40"))==0));
    }

    @Test
    void memberWithoutOtherProjectsKeepsSelectedPercentage()
    {
        BusinessProjectMember member=allocationMember("MEMBER");
        Map<String,Object> preview=service.memberAllocationPreview(52L,10L,1L,false);
        member.setAllocationPercent(new BigDecimal("25.50"));
        member.setAllocationPlan(row("effectiveDate",preview.get("effectiveDate"),"versionToken",preview.get("versionToken"),
            "allocations",Collections.emptyList()));
        service.saveMember(member,1L,"admin",false);
        ArgumentCaptor<BusinessProjectStaffAllocation> saved=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper).insertProjectStaffAllocation(saved.capture());
        assertEquals(new BigDecimal("25.50"),saved.getValue().getAllocationValue());
        assertEquals("APPLIED",member.getAllocationOutcome());
    }

    @Test
    void deputyAddingMemberRequestsConfirmationUnderTheirOwnIdentity()
    {
        BusinessProjectMember member=memberWithAllocationPlan(9L);
        when(mapper.selectMemberRole(52L,12L)).thenReturn("DEPUTY");
        when(mapper.selectActiveUserById(12L)).thenReturn(row("nickName","副负责人十二"));
        service.saveMember(member,12L,"deputy12",false);
        ArgumentCaptor<Map<String,Object>> request=ArgumentCaptor.forClass(Map.class);
        verify(allocationRequests).insertRequest(request.capture());
        assertEquals(12L,request.getValue().get("applicantId"));
        assertEquals("PENDING",member.getAllocationOutcome());
        verify(mapper,never()).insertProjectStaffAllocation(any());
        ArgumentCaptor<Map<String,Object>> reviews=ArgumentCaptor.forClass(Map.class);
        verify(allocationRequests,times(2)).insertReview(reviews.capture());
        assertTrue(reviews.getAllValues().stream().allMatch(review->"PENDING".equals(review.get("status"))));
    }

    @Test
    void addingMemberAcrossOwnersCreatesConfirmationWithoutChangingExistingRatios()
    {
        BusinessProjectMember member = memberWithAllocationPlan(9L);
        when(mapper.selectActiveUserById(1L)).thenReturn(row("nickName","管理员"));
        service.saveMember(member,1L,"admin",false);
        assertEquals("PENDING",member.getAllocationOutcome());
        ArgumentCaptor<Map<String,Object>> request=ArgumentCaptor.forClass(Map.class);
        verify(allocationRequests).insertRequest(request.capture());
        assertEquals(1L,request.getValue().get("applicantId"));
        verify(mapper,never()).insertProjectStaffAllocation(any());
        verify(allocationRequests,times(2)).insertReview(any());
    }

    @Test
    void staleMemberAllocationPlanCannotOverwriteOtherProjects()
    {
        BusinessProjectMember member = memberWithAllocationPlan(1L);
        member.getAllocationPlan().put("versionToken","stale");
        ServiceException error=assertThrows(ServiceException.class,()->service.saveMember(member,1L,"admin",false));
        assertTrue(error.getMessage().contains("其他项目投入已变化"));
        verify(mapper,never()).insertProjectStaffAllocation(any());
    }

    @ParameterizedTest
    @ValueSource(strings={"total","reason","projects","date","pending"})
    void invalidMemberAllocationPlansAreRejected(String invalid)
    {
        BusinessProjectMember member = memberWithAllocationPlan(1L);
        if("total".equals(invalid))member.setAllocationPercent(new BigDecimal("50"));
        if("reason".equals(invalid))member.getAllocationPlan().put("reason","");
        if("projects".equals(invalid))member.getAllocationPlan().put("allocations",Collections.emptyList());
        if("date".equals(invalid))member.getAllocationPlan().put("effectiveDate","2000-01-01");
        if("pending".equals(invalid))when(allocationRequests.selectPending(10L)).thenReturn(row("requestId",5L));
        assertThrows(ServiceException.class,()->service.saveMember(member,1L,"admin",false));
        verify(mapper,never()).insertProjectStaffAllocation(any());
        verify(allocationRequests,never()).insertRequest(any());
    }

    private BusinessProjectMember memberWithAllocationPlan(Long otherOwner)
    {
        BusinessProjectMember member=allocationMember("MEMBER");
        Map<String,Object> old=row("projectId",93L,"projectName","既有项目","ownerUserId",otherOwner,"ownerName","原负责人",
            "allocationId",1L,"allocationVersion",0,"allocationValue",100,"confirmationStatus","CONFIRMED","allocationHistoryToken","1:1:0");
        Map<String,Object> current=row("projectId",52L,"projectName","本项目","ownerUserId",1L,"ownerName","管理员",
            "allocationValue",0,"allocationHistoryToken","1:1:0");
        java.util.concurrent.atomic.AtomicBoolean added=new java.util.concurrent.atomic.AtomicBoolean();
        when(mapper.selectUserAllocationWorkspace(eq(10L),any(Date.class)))
            .thenAnswer(call->added.get()?Arrays.asList(current,old):Collections.singletonList(old));
        lenient().doAnswer(call->{added.set(true);return 1;}).when(mapper).upsertMember(member);
        BusinessProject other=project(93L,otherOwner,"ACTIVE","APPROVED");other.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        lenient().when(mapper.selectProjectById(93L)).thenReturn(other);
        Map<String,Object> preview=service.memberAllocationPreview(52L,10L,1L,false);
        member.setAllocationPercent(new BigDecimal("40"));
        member.setAllocationPlan(row("effectiveDate",preview.get("effectiveDate"),"versionToken",preview.get("versionToken"),
            "reason","新增成员分配投入","allocations",Collections.singletonList(row("projectId",93L,"allocationValue",60))));
        return member;
    }

    @Test
    void removingFinalDeputyAssignmentRevokesDedicatedRole()
    {
        BusinessProject project = project(53L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(53L)).thenReturn(project);
        when(mapper.selectMemberRole(53L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(53L, 10L)).thenReturn("DEPUTY");
        when(mapper.leaveMember(53L, 10L, false, "owner9")).thenReturn(1);
        when(mapper.closeMemberAllocations(53L, 10L, false, "owner9")).thenReturn(1);
        when(mapper.selectRoleIdByKey("project_deputy")).thenReturn(20L);
        when(mapper.countActiveProjectMembershipByRole(10L, "DEPUTY")).thenReturn(0);
        when(mapper.deleteUserRole(10L, 20L)).thenReturn(1);

        service.removeMember(53L, 10L, 9L, "owner9", false);

        verify(mapper).unassignOpenMemberTasks(53L, 10L, "owner9");
        verify(mapper).unassignActiveMemberRoutines(53L, 10L, "owner9");
        verify(accountingService).recalculatePersonnelCost(eq(53L), any(Date.class), eq("owner9"));
        verify(mapper).deleteUserRole(10L, 20L);
        verify(onlineUserPermissionService).refreshAfterCommit(10L);
    }

    @Test
    void removingMemberWithoutTodayCostRecalculatesTheRemovalDay()
    {
        BusinessProject project = project(53L, 9L, "ACTIVE", "APPROVED");
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectById(53L)).thenReturn(project);
        when(mapper.selectMemberRole(53L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(53L, 10L)).thenReturn("MEMBER");
        when(mapper.leaveMember(53L, 10L, false, "owner9")).thenReturn(1);
        when(memberDays.deleteRemovalDayCost(eq(53L), eq(10L), any(Date.class))).thenReturn(1);

        service.removeMember(53L, 10L, false, 9L, "owner9", false);

        verify(memberDays).synchronize(53L);
        verify(accountingService).recalculatePersonnelCost(eq(53L), any(Date.class), eq("owner9"));
    }

    @Test
    void removingMemberAfterTodayWorkKeepsTheRemovalDayCost()
    {
        BusinessProject project = project(53L, 9L, "ACTIVE", "APPROVED");
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectById(53L)).thenReturn(project);
        when(mapper.selectMemberRole(53L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(53L, 10L)).thenReturn("MEMBER");
        when(mapper.leaveMember(53L, 10L, true, "owner9")).thenReturn(1);

        service.removeMember(53L, 10L, true, 9L, "owner9", false);

        verify(memberDays, never()).deleteRemovalDayCost(eq(53L), eq(10L), any(Date.class));
        verify(memberDays).synchronize(53L);
        verify(accountingService, never()).recalculatePersonnelCost(eq(53L), any(Date.class), eq("owner9"));
    }

    @Test
    void deputyCannotAppointAnotherDeputy()
    {
        BusinessProject project = project(54L, 9L, "PLANNING", "DRAFT");
        BusinessProjectMember member = new BusinessProjectMember();
        member.setProjectId(54L);
        member.setUserId(11L);
        member.setMemberRole("DEPUTY");
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("userName", "member11");
        when(mapper.selectProjectById(54L)).thenReturn(project);
        when(mapper.selectMemberRole(54L, 10L)).thenReturn("DEPUTY");
        when(mapper.selectMemberRole(54L, 11L)).thenReturn(null);
        when(mapper.selectActiveUserById(11L)).thenReturn(user);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveMember(member, 10L, "deputy10", false));

        assertTrue(error.getMessage().contains("主负责人或老板"));
        verify(mapper, never()).upsertMember(any(BusinessProjectMember.class));
    }

    @Test
    void bossMustExplainWhyAnActiveProjectIsPaused()
    {
        BusinessProject project = project(61L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(61L)).thenReturn(project);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(61L, "PAUSE", "", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("暂停原因"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void unfinishedTasksBlockAcceptanceRequest()
    {
        BusinessProject project = project(62L, 9L, "ACTIVE", "APPROVED");
        project.setManagementMode("DELIVERY");
        project.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setStatus("DOING");
        when(mapper.selectProjectById(62L)).thenReturn(project);
        when(mapper.selectMemberRole(62L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(62L)).thenReturn(Collections.singletonList(task));

        BusinessProjectAcceptance acceptance = new BusinessProjectAcceptance();
        acceptance.setResultSummary("结果摘要");
        acceptance.setDeliverables("交付成果");
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitAcceptance(62L, acceptance, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("未完成任务"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void acceptanceErrorNamesEveryOutstandingBlocker()
    {
        BusinessProject project = project(63L, 9L, "ACTIVE", "APPROVED");
        project.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskName("交付复盘"); task.setStatus("DOING"); task.setProgress(80);
        BusinessProjectMilestone milestone = new BusinessProjectMilestone();
        milestone.setMilestoneName("最终交付"); milestone.setStatus("DOING");
        BusinessProjectRisk risk = new BusinessProjectRisk();
        risk.setRiskTitle("账号封禁风险"); risk.setSeverity("HIGH"); risk.setStatus("OPEN");
        when(mapper.selectProjectById(63L)).thenReturn(project);
        when(mapper.selectMemberRole(63L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(63L)).thenReturn(Collections.singletonList(task));
        when(mapper.selectMilestones(63L)).thenReturn(Collections.singletonList(milestone));
        when(mapper.selectRisks(63L)).thenReturn(Collections.singletonList(risk));
        BusinessProjectAcceptance acceptance = new BusinessProjectAcceptance();
        acceptance.setResultSummary("结果摘要"); acceptance.setDeliverables("交付成果");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitAcceptance(63L, acceptance, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("存在未完成任务：交付复盘"));
        assertTrue(error.getMessage().contains("存在未完成里程碑：最终交付"));
        assertTrue(error.getMessage().contains("存在高风险或严重风险未处理：账号封禁风险"));
    }

    @Test
    void completedProgressLeavesOnlyOpenHighRiskInAcceptanceError()
    {
        BusinessProject project = project(64L, 9L, "ACTIVE", "APPROVED");
        project.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskName("最终成片"); task.setStatus("DOING"); task.setProgress(100);
        BusinessProjectRisk risk = new BusinessProjectRisk();
        risk.setRiskTitle("交付风险"); risk.setSeverity("CRITICAL"); risk.setStatus("OPEN");
        when(mapper.selectProjectById(64L)).thenReturn(project);
        when(mapper.selectMemberRole(64L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(64L)).thenReturn(Collections.singletonList(task));
        when(mapper.selectMilestones(64L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(64L)).thenReturn(Collections.singletonList(risk));
        BusinessProjectAcceptance acceptance = new BusinessProjectAcceptance();
        acceptance.setResultSummary("结果摘要"); acceptance.setDeliverables("交付成果");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitAcceptance(64L, acceptance, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("存在高风险或严重风险未处理：交付风险"));
        assertTrue(!error.getMessage().contains("未完成任务"));
    }

    @Test
    void bossDirectoryShowsAllNamesButOnlyOwnProjectsCanOpen()
    {
        Map<String, Object> own = new HashMap<String, Object>();
        own.put("projectId", 1L); own.put("projectName", "江澜项目"); own.put("initiatorUserId", 7L);
        Map<String, Object> foreign = new HashMap<String, Object>();
        foreign.put("projectId", 2L); foreign.put("projectName", "王赋章项目"); foreign.put("initiatorUserId", 8L);
        when(mapper.selectProjectDirectory()).thenReturn(Arrays.asList(own, foreign));

        List<Map<String, Object>> directory = service.projectDirectory(7L, false, true);

        assertEquals(true, directory.get(0).get("canOpen"));
        assertEquals(false, directory.get(1).get("canOpen"));
    }

    @Test
    void completedProjectCanSubmitVersionedAcceptanceEvidenceForBossReview()
    {
        BusinessProject active = project(71L, 9L, "ACTIVE", "APPROVED");
        active.setManagementMode("DELIVERY");
        active.setCloseMethod("RESULT_ACCEPTANCE");
        active.setInitiatorUserId(8L);
        BusinessProject pendingState = project(71L, 9L, "ACCEPTANCE", "APPROVED");
        pendingState.setManagementMode("DELIVERY");
        pendingState.setCloseMethod("RESULT_ACCEPTANCE");
        pendingState.setInitiatorUserId(8L);
        BusinessProjectTask done = new BusinessProjectTask();
        done.setStatus("DONE");
        Map<String, Object> submitter = new HashMap<String, Object>();
        submitter.put("nickName", "负责人九");
        when(mapper.selectProjectById(71L)).thenReturn(active, pendingState);
        when(mapper.selectMemberRole(71L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(71L)).thenReturn(Collections.singletonList(done));
        when(mapper.selectMilestones(71L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(71L)).thenReturn(Collections.emptyList());
        when(mapper.selectActiveUserById(9L)).thenReturn(submitter);
        when(mapper.selectNextAcceptanceVersion(71L)).thenReturn(2);
        doAnswer(invocation -> { ((BusinessProjectAcceptance)invocation.getArgument(0)).setAcceptanceId(61L); return 1; })
            .when(mapper).insertAcceptance(any());
        when(kpiMapper.selectPlanSummaries(71L)).thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(71L, "ACTIVE", "ACCEPTANCE", null, false, "owner9", 0)).thenReturn(1);

        BusinessProjectAcceptance evidence = new BusinessProjectAcceptance();
        evidence.setResultSummary("全部目标已完成");
        evidence.setDeliverables("成片和复盘报告");
        evidence.setAttachmentUrls("/profile/upload/report.pdf");
        BusinessProject result = service.submitAcceptance(71L, evidence, 9L, "owner9", false);

        assertEquals("ACCEPTANCE", result.getStatus());
        assertEquals(2, evidence.getSubmissionVersion());
        assertEquals("负责人九", evidence.getSubmittedUserName());
        verify(mapper, never()).reviewAcceptance(any(), any(), any(), any(), any(), any());
        verify(mapper).insertEvent(any());
    }

    @Test
    void oneHundredPercentProgressMarksAssignedTaskDone()
    {
        BusinessProject project = project(78L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectTask current = new BusinessProjectTask();
        current.setTaskId(31L); current.setProjectId(78L); current.setTaskName("完成复盘");
        current.setAssigneeUserId(147L); current.setStatus("DOING"); current.setProgress(80);
        current.setPriority("MEDIUM"); current.setVersion(1);
        when(mapper.selectProjectById(78L)).thenReturn(project);
        when(mapper.selectMemberRole(78L, 147L)).thenReturn("MEMBER");
        when(mapper.selectTaskById(31L)).thenReturn(current);
        when(mapper.updateTask(current)).thenReturn(1);
        BusinessProjectTask update = new BusinessProjectTask();
        update.setProjectId(78L); update.setTaskId(31L); update.setStatus("DOING");
        update.setProgress(100); update.setVersion(1);

        BusinessProjectTask saved = service.saveTask(update, 147L, "member147", false);

        assertEquals("DONE", saved.getStatus());
        assertEquals(100, saved.getProgress());
    }

    @Test
    void completedMilestoneTasksCanSubmitStageAcceptance()
    {
        BusinessProject active = project(75L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("STAGED_ACCEPTANCE");
        active.setInitiatorUserId(8L);
        BusinessProjectMilestone milestone = new BusinessProjectMilestone();
        milestone.setMilestoneId(501L); milestone.setProjectId(75L); milestone.setMilestoneName("首播验证");
        milestone.setStatus("DOING");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setMilestoneId(501L); task.setStatus("DONE");
        Map<String, Object> submitter = new HashMap<String, Object>(); submitter.put("nickName", "负责人九");
        when(mapper.selectProjectById(75L)).thenReturn(active);
        when(mapper.selectMemberRole(75L, 9L)).thenReturn("OWNER");
        when(mapper.selectMilestoneById(501L)).thenReturn(milestone);
        when(mapper.selectTasks(75L)).thenReturn(Collections.singletonList(task));
        when(mapper.selectActiveUserById(9L)).thenReturn(submitter);
        when(mapper.selectNextStageAcceptanceVersion(75L, 501L)).thenReturn(1);
        doAnswer(invocation -> { ((BusinessProjectStageAcceptance)invocation.getArgument(0)).setStageAcceptanceId(81L); return 1; })
            .when(mapper).insertStageAcceptance(any());
        BusinessProjectStageAcceptance evidence = new BusinessProjectStageAcceptance();
        evidence.setMilestoneId(501L); evidence.setResultSummary("首播完成"); evidence.setDeliverables("数据复盘报告");
        BusinessProject result = service.submitStageAcceptance(75L, evidence, 9L, "owner9", false);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals("首播验证", evidence.getMilestoneName());
        verify(mapper).updateMilestoneStatus(75L, 501L, "REVIEWING", "owner9");
        verify(mapper, never()).reviewStageAcceptance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resultAcceptanceProjectCanSubmitMilestoneAcceptance()
    {
        BusinessProject active = project(77L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProjectMilestone milestone = new BusinessProjectMilestone();
        milestone.setMilestoneId(502L); milestone.setProjectId(77L); milestone.setMilestoneName("交付版本");
        milestone.setStatus("DOING");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setMilestoneId(502L); task.setStatus("DONE");
        Map<String, Object> submitter = new HashMap<String, Object>(); submitter.put("nickName", "负责人九");
        when(mapper.selectProjectById(77L)).thenReturn(active);
        when(mapper.selectMemberRole(77L, 9L)).thenReturn("OWNER");
        when(mapper.selectMilestoneById(502L)).thenReturn(milestone);
        when(mapper.selectTasks(77L)).thenReturn(Collections.singletonList(task));
        when(mapper.selectActiveUserById(9L)).thenReturn(submitter);
        when(mapper.selectNextStageAcceptanceVersion(77L, 502L)).thenReturn(1);
        doAnswer(invocation -> { ((BusinessProjectStageAcceptance)invocation.getArgument(0)).setStageAcceptanceId(82L); return 1; })
            .when(mapper).insertStageAcceptance(any());
        BusinessProjectStageAcceptance evidence = new BusinessProjectStageAcceptance();
        evidence.setMilestoneId(502L); evidence.setResultSummary("交付完成"); evidence.setDeliverables("产品包和验收报告");
        service.submitStageAcceptance(77L, evidence, 9L, "owner9", false);

        verify(mapper).updateMilestoneStatus(77L, 502L, "REVIEWING", "owner9");
        verify(mapper, never()).reviewStageAcceptance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void milestoneCannotBeMarkedDoneWithoutBossAcceptance()
    {
        BusinessProject active = project(77L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("RESULT_ACCEPTANCE");
        when(mapper.selectProjectById(77L)).thenReturn(active);
        when(mapper.selectMemberRole(77L, 9L)).thenReturn("OWNER");
        BusinessProjectMilestone input = new BusinessProjectMilestone();
        input.setProjectId(77L); input.setMilestoneName("交付版本"); input.setStatus("DONE");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveMilestone(input, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("提交验收"));
        verify(mapper, never()).insertMilestone(any());
    }

    @Test
    void newMilestoneAlwaysUsesZeroWeight()
    {
        BusinessProject active = project(77L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(77L)).thenReturn(active);
        when(mapper.selectMemberRole(77L, 9L)).thenReturn("OWNER");
        BusinessProjectMilestone input = new BusinessProjectMilestone();
        input.setProjectId(77L); input.setMilestoneName("交付版本"); input.setStatus("PENDING");
        input.setWeight(new BigDecimal("75"));

        BusinessProjectMilestone saved = service.saveMilestone(input, 9L, "owner9", false);

        assertEquals(BigDecimal.ZERO, saved.getWeight());
        verify(mapper).insertMilestone(input);
    }

    @Test
    void editingMilestonePreservesHistoricalWeight()
    {
        BusinessProject active = project(77L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectMilestone current = new BusinessProjectMilestone();
        current.setMilestoneId(502L); current.setProjectId(77L); current.setMilestoneName("交付版本");
        current.setStatus("DOING"); current.setWeight(new BigDecimal("35"));
        when(mapper.selectProjectById(77L)).thenReturn(active);
        when(mapper.selectMemberRole(77L, 9L)).thenReturn("OWNER");
        when(mapper.selectMilestoneById(502L)).thenReturn(current);
        when(mapper.updateMilestone(any())).thenReturn(1);
        BusinessProjectMilestone input = new BusinessProjectMilestone();
        input.setMilestoneId(502L); input.setProjectId(77L); input.setMilestoneName("交付版本 v2");
        input.setStatus("DOING"); input.setWeight(new BigDecimal("80"));

        BusinessProjectMilestone saved = service.saveMilestone(input, 9L, "owner9", false);

        assertEquals(new BigDecimal("35"), saved.getWeight());
        verify(mapper).updateMilestone(input);
    }

    @Test
    void stagedProjectCannotRequestCloseBeforeEveryMilestoneIsApproved()
    {
        BusinessProject active = project(76L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("STAGED_ACCEPTANCE"); active.setInitiatorUserId(8L);
        BusinessProjectMilestone milestone = new BusinessProjectMilestone(); milestone.setStatus("REVIEWING");
        when(mapper.selectProjectById(76L)).thenReturn(active);
        when(mapper.selectMemberRole(76L, 9L)).thenReturn("OWNER");
        when(mapper.selectMilestones(76L)).thenReturn(Collections.singletonList(milestone));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(76L, "REQUEST_CLOSE", null, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("未通过阶段验收"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void ownerRequestsCloseAfterEveryStageAndKpiAreConfirmed()
    {
        BusinessProject active = project(79L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("STAGED_ACCEPTANCE"); active.setInitiatorUserId(8L);
        BusinessProject pending = project(79L, 9L, "ACCEPTANCE", "APPROVED");
        pending.setCloseMethod("STAGED_ACCEPTANCE"); pending.setInitiatorUserId(8L);
        BusinessProjectMilestone milestone = new BusinessProjectMilestone(); milestone.setStatus("DONE");
        when(mapper.selectProjectById(79L)).thenReturn(active, pending);
        when(mapper.selectMemberRole(79L, 9L)).thenReturn("OWNER");
        when(mapper.selectMilestones(79L)).thenReturn(Collections.singletonList(milestone));
        when(mapper.selectRisks(79L)).thenReturn(Collections.emptyList());
        when(mapper.selectTasks(79L)).thenReturn(Collections.singletonList(completedTask("阶段交付")));
        when(kpiMapper.selectPlanSummaries(79L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(79L, "ACTIVE", "ACCEPTANCE", null, false, "owner9", 0)).thenReturn(1);

        BusinessProject result = service.transition(79L, "REQUEST_CLOSE", "全部阶段已完成，请老板检验", 9L, "owner9", false);

        assertEquals("ACCEPTANCE", result.getStatus());
        verify(mapper).insertEvent(any());
    }

    @Test
    void projectOwnerCannotApproveDirectClose()
    {
        BusinessProject active = project(82L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("DIRECT"); active.setInitiatorUserId(8L);
        when(mapper.selectProjectById(82L)).thenReturn(active);
        when(mapper.selectMemberRole(82L, 9L)).thenReturn("OWNER");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(82L, "CLOSE", "负责人自行结项", 9L, "owner9", false));

        assertTrue(error.getMessage().contains("只有老板"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void parentProjectOwnerCanApproveChildDirectClose()
    {
        BusinessProject child = project(88L, 19L, "ACCEPTANCE", "APPROVED");
        child.setParentId(880L); child.setCloseMethod("DIRECT");
        child.setDeliveryPolicyVersion("SEPARATED_V1"); child.setAccountingState("OPEN");
        BusinessProject parent = project(880L, 8L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(88L)).thenReturn(child);
        when(mapper.selectProjectById(880L)).thenReturn(parent);
        when(mapper.selectTasks(88L)).thenReturn(Collections.singletonList(completedTask("子项目交付")));
        when(mapper.selectRisks(88L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(88L)).thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(88L, "ACCEPTANCE", "CLOSED", null, false, "parent8", 0)).thenReturn(1);
        when(mapper.closeAccounting(88L, 1, "parent8")).thenReturn(1);

        Map<String, Object> result = service.closeAccounting(88L, 0, "子项目验收通过", 8L, "parent8", false);

        assertEquals("CLOSED", result.get("status"));
        assertEquals("CLOSED", result.get("accountingState"));
        verify(mapper).updateProjectStatus(88L, "ACCEPTANCE", "CLOSED", null, false, "parent8", 0);
    }

    @Test
    void assigningOneOffTaskStoresAssigneeAuditSnapshot()
    {
        BusinessProject project = project(85L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(85L)).thenReturn(project);
        when(mapper.selectMemberRole(85L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(85L, 14L)).thenReturn("MEMBER");
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectActiveUserById(14L)).thenReturn(assignee);

        BusinessProjectTask task = new BusinessProjectTask();
        task.setProjectId(85L); task.setTaskName("完成接口联调"); task.setAssigneeUserId(14L);
        service.saveTask(task, 9L, "zhangsan", false);

        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertEvent(event.capture());
        assertEquals("TASK_SAVE", event.getValue().get("eventType"));
        assertEquals(14L, event.getValue().get("subjectUserId"));
        assertEquals("李四", event.getValue().get("subjectName"));
        assertEquals("lisi", event.getValue().get("subjectAccount"));
    }

    @Test
    void assigningRoutineStoresAssigneeAuditSnapshotAndNormalizesFrequency()
    {
        BusinessProject project = project(86L, 9L, "ACTIVE", "APPROVED");
        project.setPlanStartDate(new Date());
        when(mapper.selectProjectById(86L)).thenReturn(project);
        when(mapper.selectMemberRole(86L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(86L, 14L)).thenReturn("MEMBER");
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectActiveUserById(14L)).thenReturn(assignee);

        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setProjectId(86L); routine.setRoutineName("每日发布视频"); routine.setFrequency("WEEKLY");
        routine.setTargetValue(BigDecimal.TEN); routine.setUnit("条"); routine.setAssigneeUserId(14L);
        routine.setEvidenceRequired("1");
        service.saveRoutine(routine, 9L, "zhangsan", false);

        assertEquals("DAILY", routine.getFrequency());
        assertEquals("0", routine.getEvidenceRequired());
        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertEvent(event.capture());
        assertEquals("ROUTINE_SAVE", event.getValue().get("eventType"));
        assertEquals(14L, event.getValue().get("subjectUserId"));
        assertEquals("李四", event.getValue().get("subjectName"));
        assertEquals("lisi", event.getValue().get("subjectAccount"));
    }

    @Test
    void removingOneOffTaskSoftDisablesItAndClosesCurrentExecutionPeriod()
    {
        BusinessProject project = project(87L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskId(701L); task.setProjectId(87L); task.setTaskName("完成接口联调");
        task.setAssigneeUserId(14L); task.setAssigneeName("李四"); task.setActiveStatus("ACTIVE");
        when(mapper.selectProjectById(87L)).thenReturn(project);
        when(mapper.selectMemberRole(87L, 9L)).thenReturn("OWNER");
        when(mapper.selectTaskById(701L)).thenReturn(task);
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectUserAuditSnapshotById(14L)).thenReturn(assignee);
        when(mapper.voidTask(87L, 701L, "zhangsan")).thenReturn(1);

        service.deleteTask(87L, 701L, 9L, "zhangsan", false);

        verify(mapper).voidTask(87L, 701L, "zhangsan");
        verify(mapper).closeWorkPeriod(87L, "TASK", 701L, "zhangsan");
        verify(mapper, never()).countTaskReports(701L);
        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertEvent(event.capture());
        assertEquals("TASK_VOID", event.getValue().get("eventType"));
        assertEquals(14L, event.getValue().get("subjectUserId"));
        assertEquals("李四", event.getValue().get("subjectName"));
        assertEquals("lisi", event.getValue().get("subjectAccount"));
    }

    @Test
    void enablingRoutineOpensASecondExecutionPeriodFromToday()
    {
        BusinessProject project = project(88L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine retired = new BusinessProjectRoutine();
        retired.setRoutineId(801L); retired.setProjectId(88L); retired.setRoutineName("每日发布视频");
        retired.setAssigneeUserId(14L); retired.setAssigneeName("李四"); retired.setStatus("VOID"); retired.setVersion(3);
        BusinessProjectRoutine enabled = new BusinessProjectRoutine();
        enabled.setRoutineId(801L); enabled.setProjectId(88L); enabled.setStatus("ACTIVE");
        when(mapper.selectProjectById(88L)).thenReturn(project);
        when(mapper.selectRoutineById(801L)).thenReturn(retired, enabled);
        when(mapper.selectMemberRole(88L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(88L, 14L)).thenReturn("MEMBER");
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectActiveUserById(14L)).thenReturn(assignee);
        when(mapper.selectUserAuditSnapshotById(14L)).thenReturn(assignee);
        when(mapper.activateRoutine(eq(88L), eq(801L), any(Date.class), eq((Date) null), eq(3), eq("zhangsan"))).thenReturn(1);

        BusinessProjectRoutine result = service.enableRoutine(88L, 801L, null, 9L, "zhangsan", false);

        assertEquals("ACTIVE", result.getStatus());
        ArgumentCaptor<BusinessProjectWorkPeriod> period = ArgumentCaptor.forClass(BusinessProjectWorkPeriod.class);
        verify(mapper).insertWorkPeriod(period.capture());
        assertEquals("ROUTINE", period.getValue().getWorkType());
        assertEquals(801L, period.getValue().getWorkId());
        assertEquals(14L, period.getValue().getAssigneeUserId());
        assertTrue(period.getValue().getStartDate() != null);
        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertEvent(event.capture());
        assertEquals("ROUTINE_ENABLE", event.getValue().get("eventType"));
        assertEquals(14L, event.getValue().get("subjectUserId"));
        assertEquals("李四", event.getValue().get("subjectName"));
        assertEquals("lisi", event.getValue().get("subjectAccount"));
    }

    @Test
    void enablingAutoTotalRoutineKeepsFutureEndDateAndRemainingTarget()
    {
        BusinessProject project = project(89L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine retired = new BusinessProjectRoutine();
        retired.setRoutineId(802L); retired.setProjectId(89L); retired.setRoutineName("累计发布视频");
        retired.setTargetMode("AUTO_TOTAL"); retired.setTargetValue(new BigDecimal("100"));
        retired.setEndDate(java.sql.Date.valueOf("2099-12-31"));
        retired.setAssigneeUserId(14L); retired.setAssigneeName("李四"); retired.setStatus("VOID"); retired.setVersion(4);
        BusinessProjectRoutine enabled = new BusinessProjectRoutine();
        enabled.setRoutineId(802L); enabled.setProjectId(89L); enabled.setStatus("ACTIVE");
        when(mapper.selectProjectById(89L)).thenReturn(project);
        when(mapper.selectRoutineById(802L)).thenReturn(retired, enabled);
        when(mapper.selectMemberRole(89L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(89L, 14L)).thenReturn("MEMBER");
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectActiveUserById(14L)).thenReturn(assignee);
        when(mapper.selectUserAuditSnapshotById(14L)).thenReturn(assignee);
        when(mapper.sumRoutineActualBefore(eq(802L), any(Date.class))).thenReturn(new BigDecimal("40"));
        when(mapper.activateRoutine(eq(89L), eq(802L), any(Date.class),
            eq(java.sql.Date.valueOf("2099-12-31")), eq(4), eq("zhangsan"))).thenReturn(1);

        BusinessProjectRoutine result = service.enableRoutine(89L, 802L, null, 9L, "zhangsan", false);

        assertEquals("ACTIVE", result.getStatus());
        verify(mapper).activateRoutine(eq(89L), eq(802L), any(Date.class),
            eq(java.sql.Date.valueOf("2099-12-31")), eq(4), eq("zhangsan"));
    }

    @Test
    void expiredAutoTotalRoutineRequiresANewEndDate()
    {
        BusinessProject project = project(90L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine retired = new BusinessProjectRoutine();
        retired.setRoutineId(803L); retired.setProjectId(90L); retired.setTargetMode("AUTO_TOTAL");
        retired.setTargetValue(new BigDecimal("100")); retired.setEndDate(java.sql.Date.valueOf("2020-01-01"));
        retired.setAssigneeUserId(14L); retired.setStatus("VOID"); retired.setVersion(1);
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectRoutineById(803L)).thenReturn(retired);
        when(mapper.selectMemberRole(90L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(90L, 14L)).thenReturn("MEMBER");
        when(mapper.selectActiveUserById(14L)).thenReturn(new HashMap<String, Object>());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.enableRoutine(90L, 803L, null, 9L, "zhangsan", false));

        assertTrue(error.getMessage().contains("选择新的结束日期"));
        verify(mapper, never()).activateRoutine(anyLong(), anyLong(), any(Date.class), any(Date.class), any(), any());
    }

    @Test
    void expiredAutoTotalRoutineCanRestartWithANewEndDate()
    {
        BusinessProject project = project(91L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine retired = new BusinessProjectRoutine();
        retired.setRoutineId(804L); retired.setProjectId(91L); retired.setRoutineName("累计发布视频");
        retired.setTargetMode("AUTO_TOTAL"); retired.setTargetValue(new BigDecimal("100"));
        retired.setEndDate(java.sql.Date.valueOf("2020-01-01"));
        retired.setAssigneeUserId(14L); retired.setAssigneeName("李四"); retired.setStatus("VOID"); retired.setVersion(2);
        BusinessProjectRoutine enabled = new BusinessProjectRoutine();
        enabled.setRoutineId(804L); enabled.setProjectId(91L); enabled.setStatus("ACTIVE");
        when(mapper.selectProjectById(91L)).thenReturn(project);
        when(mapper.selectRoutineById(804L)).thenReturn(retired, enabled);
        when(mapper.selectMemberRole(91L, 9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(91L, 14L)).thenReturn("MEMBER");
        Map<String, Object> assignee = new HashMap<String, Object>();
        assignee.put("userId", 14L); assignee.put("userName", "lisi"); assignee.put("nickName", "李四");
        when(mapper.selectActiveUserById(14L)).thenReturn(assignee);
        when(mapper.selectUserAuditSnapshotById(14L)).thenReturn(assignee);
        when(mapper.sumRoutineActualBefore(eq(804L), any(Date.class))).thenReturn(new BigDecimal("40"));
        Date newEndDate = java.sql.Date.valueOf("2099-12-31");
        when(mapper.activateRoutine(eq(91L), eq(804L), any(Date.class), eq(newEndDate), eq(2), eq("zhangsan")))
            .thenReturn(1);

        BusinessProjectRoutine result = service.enableRoutine(91L, 804L, newEndDate, 9L, "zhangsan", false);

        assertEquals("ACTIVE", result.getStatus());
        verify(mapper).activateRoutine(eq(91L), eq(804L), any(Date.class), eq(newEndDate), eq(2), eq("zhangsan"));
    }

    @Test
    void projectOwnerCannotApproveResultAcceptance()
    {
        BusinessProject pending = project(83L, 9L, "ACCEPTANCE", "APPROVED");
        pending.setCloseMethod("RESULT_ACCEPTANCE"); pending.setInitiatorUserId(8L);
        when(mapper.selectProjectById(83L)).thenReturn(pending);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reviewAcceptance(83L, "APPROVED", "负责人自行验收", 9L, "owner9", false));

        assertTrue(error.getMessage().contains("只有老板"));
        verify(mapper, never()).reviewAcceptance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void projectOwnerCannotApproveMilestoneAcceptance()
    {
        BusinessProject active = project(84L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("STAGED_ACCEPTANCE"); active.setInitiatorUserId(8L);
        when(mapper.selectProjectById(84L)).thenReturn(active);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reviewStageAcceptance(84L, 501L, "APPROVED", "负责人自行验收",
                9L, "owner9", false));

        assertTrue(error.getMessage().contains("只有老板"));
        verify(mapper, never()).reviewStageAcceptance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void parentProjectOwnerCanApproveChildResultAcceptance()
    {
        BusinessProject pending = project(85L, 19L, "ACCEPTANCE", "APPROVED");
        pending.setParentId(850L); pending.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProject closed = project(85L, 19L, "CLOSED", "APPROVED");
        closed.setParentId(850L); closed.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProject parent = project(850L, 8L, "ACTIVE", "APPROVED");
        BusinessProjectAcceptance acceptance = new BusinessProjectAcceptance();
        acceptance.setAcceptanceId(8500L);
        Map<String, Object> reviewer = new HashMap<String, Object>(); reviewer.put("nickName", "主负责人八");
        when(mapper.selectProjectById(85L)).thenReturn(pending, closed);
        when(mapper.selectProjectById(850L)).thenReturn(parent);
        when(mapper.selectLatestPendingAcceptance(85L)).thenReturn(acceptance);
        when(mapper.selectTasks(85L)).thenReturn(Collections.singletonList(completedTask("子项目交付")));
        when(mapper.selectMilestones(85L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(85L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(85L)).thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.selectActiveUserById(8L)).thenReturn(reviewer);
        when(mapper.reviewAcceptance(8500L, "APPROVED", 8L, "主负责人八", "验收通过", "parent8")).thenReturn(1);
        when(mapper.updateProjectStatus(85L, "ACCEPTANCE", "CLOSED", null, false, "parent8", 0)).thenReturn(1);

        BusinessProject result = service.reviewAcceptance(85L, "APPROVED", "验收通过", 8L, "parent8", false);

        assertEquals("CLOSED", result.getStatus());
        verify(mapper).reviewAcceptance(8500L, "APPROVED", 8L, "主负责人八", "验收通过", "parent8");
    }

    @Test
    void companyBossCannotApproveChildAcceptance()
    {
        BusinessProject child = project(86L, 19L, "ACCEPTANCE", "APPROVED");
        child.setParentId(860L); child.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProject parent = project(860L, 8L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(86L)).thenReturn(child);
        when(mapper.selectProjectById(860L)).thenReturn(parent);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reviewAcceptance(86L, "APPROVED", "老板验收", 23L, "boss23", true));

        assertTrue(error.getMessage().contains("主项目主负责人"));
        verify(mapper, never()).reviewAcceptance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void parentProjectOwnerCanApproveChildMilestoneAcceptance()
    {
        BusinessProject active = project(87L, 19L, "ACTIVE", "APPROVED");
        active.setParentId(870L); active.setCloseMethod("STAGED_ACCEPTANCE");
        BusinessProject parent = project(870L, 8L, "ACTIVE", "APPROVED");
        BusinessProjectStageAcceptance acceptance = new BusinessProjectStageAcceptance();
        acceptance.setStageAcceptanceId(8700L);
        Map<String, Object> reviewer = new HashMap<String, Object>(); reviewer.put("nickName", "主负责人八");
        when(mapper.selectProjectById(87L)).thenReturn(active);
        when(mapper.selectProjectById(870L)).thenReturn(parent);
        when(mapper.selectLatestPendingStageAcceptance(87L, 501L)).thenReturn(acceptance);
        when(mapper.selectActiveUserById(8L)).thenReturn(reviewer);
        when(mapper.reviewStageAcceptance(8700L, "APPROVED", 8L, "主负责人八", "阶段通过", "parent8")).thenReturn(1);

        service.reviewStageAcceptance(87L, 501L, "APPROVED", "阶段通过", 8L, "parent8", false);

        verify(mapper).updateMilestoneStatus(87L, 501L, "DONE", "parent8");
    }

    @Test
    void bossConfirmsPendingStagedProjectClose()
    {
        BusinessProject pending = project(80L, 9L, "ACCEPTANCE", "APPROVED");
        pending.setCloseMethod("STAGED_ACCEPTANCE"); pending.setInitiatorUserId(8L);
        BusinessProject closed = project(80L, 9L, "CLOSED", "APPROVED");
        closed.setCloseMethod("STAGED_ACCEPTANCE"); closed.setInitiatorUserId(8L);
        BusinessProjectMilestone milestone = new BusinessProjectMilestone(); milestone.setStatus("DONE");
        when(mapper.selectProjectById(80L)).thenReturn(pending, closed);
        when(mapper.selectMilestones(80L)).thenReturn(Collections.singletonList(milestone));
        when(mapper.selectTasks(80L)).thenReturn(Collections.singletonList(completedTask("阶段交付")));
        when(mapper.selectRisks(80L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(80L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(80L, "ACCEPTANCE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);

        BusinessProject result = service.transition(80L, "CLOSE", "同意结项", 8L, "boss8", true);

        assertEquals("CLOSED", result.getStatus());
        verify(accountingService).ensureProjectCanClose(80L);
        verify(accountingService).closeProjectAccounting(eq(80L), any(Date.class), eq("boss8"));
        verify(mapper).closeProjectRoutines(80L, "boss8");
        verify(mapper).closeProjectAllocations(eq(80L), any(Date.class), eq("boss8"));
        verify(mapper).insertEvent(any());
    }

    @Test
    void pendingMemberEffortBlocksProjectCloseBeforeAnythingIsFrozen()
    {
        BusinessProject project = project(81L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(81L)).thenReturn(project);
        when(kpiMapper.selectPlanSummaries(81L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.selectTasks(81L)).thenReturn(Collections.singletonList(completedTask("项目交付")));
        when(mapper.selectMilestones(81L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(81L)).thenReturn(Collections.emptyList());
        when(mapper.countPendingProjectEfforts(81L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(81L, "CLOSE", "目标已完成", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("待负责人确认"));
        verify(accountingService, never()).closeProjectAccounting(any(), any(), any());
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void bossReturnsPendingStagedProjectForMoreWork()
    {
        BusinessProject pending = project(82L, 9L, "ACCEPTANCE", "APPROVED");
        pending.setCloseMethod("STAGED_ACCEPTANCE"); pending.setInitiatorUserId(8L);
        BusinessProject active = project(82L, 9L, "ACTIVE", "APPROVED");
        active.setCloseMethod("STAGED_ACCEPTANCE"); active.setInitiatorUserId(8L);
        when(mapper.selectProjectById(82L)).thenReturn(pending, active);
        when(mapper.updateProjectStatus(82L, "ACCEPTANCE", "ACTIVE", null, false, "boss8", 0)).thenReturn(1);

        BusinessProject result = service.transition(82L, "RETURN_ACTIVE", "补充结项复盘", 8L, "boss8", true);

        assertEquals("ACTIVE", result.getStatus());
        verify(mapper).insertEvent(any());
    }

    @Test
    void recurringWorkCanBeTheOnlySubmittedBaselineItem()
    {
        BusinessProject project = project(73L, 9L, "PLANNING", "DRAFT");
        project.setObjective("每天稳定产出视频");
        project.setPlanStartDate(new Date());
        project.setPlanEndDate(new Date());
        BusinessProject submitted = project(73L, 9L, "ACTIVE", "APPROVED");
        submitted.setObjective(project.getObjective());
        submitted.setPlanStartDate(project.getPlanStartDate());
        submitted.setPlanEndDate(project.getPlanEndDate());
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(1L);
        when(mapper.selectProjectById(73L)).thenReturn(project, submitted);
        when(mapper.selectMemberRole(73L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(73L)).thenReturn(Collections.emptyList());
        when(mapper.selectRoutines(org.mockito.ArgumentMatchers.eq(73L), any())).thenReturn(Collections.singletonList(routine));
        when(mapper.updateProjectStatus(73L, "PLANNING", "ACTIVE", "APPROVED", true, "owner9", 0)).thenReturn(1);

        BusinessProject result = service.transition(73L, "SUBMIT_BASELINE", null, 9L, "owner9", false);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals("APPROVED", result.getBaselineStatus());
        verify(mapper).updateProjectStatus(73L, "PLANNING", "ACTIVE", "APPROVED", true, "owner9", 0);
        verify(mapper).insertEvent(any());
    }

    @Test
    void openEndedProjectCanSubmitBaseline()
    {
        BusinessProject project = project(88L,9L,"PLANNING","DRAFT");
        project.setObjective("持续运营，不设置固定结束日期");
        project.setPlanStartDate(new Date());
        project.setPlanEndDate(null);
        BusinessProject submitted = project(88L,9L,"ACTIVE","APPROVED");
        submitted.setObjective(project.getObjective());
        submitted.setPlanStartDate(project.getPlanStartDate());
        submitted.setPlanEndDate(null);
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(1L);
        when(mapper.selectProjectById(88L)).thenReturn(project,submitted);
        when(mapper.selectMemberRole(88L,9L)).thenReturn("OWNER");
        when(mapper.selectTasks(88L)).thenReturn(Collections.emptyList());
        when(mapper.selectRoutines(org.mockito.ArgumentMatchers.eq(88L),any())).thenReturn(Collections.singletonList(routine));
        when(mapper.updateProjectStatus(88L,"PLANNING","ACTIVE","APPROVED",true,"owner9",0)).thenReturn(1);

        BusinessProject result=service.transition(88L,"SUBMIT_BASELINE",null,9L,"owner9",false);

        assertEquals("ACTIVE",result.getStatus());
        assertEquals("APPROVED",result.getBaselineStatus());
        verify(mapper).updateProjectStatus(88L,"PLANNING","ACTIVE","APPROVED",true,"owner9",0);
    }

    @Test
    void sourceManagedLiveRoutineCanBeTheOnlySubmittedBaselineItem()
    {
        BusinessProject project = project(86L, 9L, "PLANNING", "DRAFT");
        project.setObjective("每日完成主播日报");
        project.setPlanStartDate(new Date());
        project.setPlanEndDate(new Date());
        BusinessProject submitted = project(86L, 9L, "ACTIVE", "APPROVED");
        submitted.setObjective(project.getObjective());
        submitted.setPlanStartDate(project.getPlanStartDate());
        submitted.setPlanEndDate(project.getPlanEndDate());
        Map<String, Object> relation = new HashMap<String, Object>();
        relation.put("projectId", 86L);
        relation.put("sourceDomain", "LIVE");
        BusinessProjectRoutine sourceRoutine = new BusinessProjectRoutine();
        sourceRoutine.setRoutineId(-1L);
        sourceRoutine.setSourceManaged(true);
        when(mapper.selectProjectById(86L)).thenReturn(project, submitted);
        when(mapper.selectMemberRole(86L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(86L)).thenReturn(Collections.emptyList());
        when(mapper.selectRoutines(eq(86L), any())).thenReturn(Collections.emptyList());
        when(mapper.selectActiveExecutionRelation(86L)).thenReturn(relation);
        when(mapper.selectLiveStreamerRoutines(relation)).thenReturn(Collections.singletonList(sourceRoutine));
        when(mapper.updateProjectStatus(86L, "PLANNING", "ACTIVE", "APPROVED", true, "owner9", 0)).thenReturn(1);

        BusinessProject result = service.transition(86L, "SUBMIT_BASELINE", null, 9L, "owner9", false);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals("APPROVED", result.getBaselineStatus());
        verify(mapper).updateProjectStatus(86L, "PLANNING", "ACTIVE", "APPROVED", true, "owner9", 0);
    }

    @Test
    void dailyRoutineBelowTargetRequiresReason()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(11L); routine.setProjectId(74L); routine.setFrequency("DAILY");
        routine.setTargetValue(new BigDecimal("10")); routine.setUnit("条"); routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(9L);
        when(mapper.selectRoutineById(11L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project);

        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(11L); report.setActualValue(new BigDecimal("8"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitRoutineReport(report, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("未达到周期目标"));
        verify(mapper, never()).upsertRoutineReport(any());
    }

    @Test
    void ownerCanPublishDynamicDailyTargetWithAssigneeSnapshot()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(111L); routine.setProjectId(74L); routine.setTargetMode("DAILY_DYNAMIC");
        routine.setStatus("ACTIVE"); routine.setUnit("条"); routine.setAssigneeUserId(147L);
        routine.setAssigneeName("李四"); routine.setStartDate(new Date(0));
        when(mapper.selectRoutineById(111L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project);
        when(mapper.selectMemberRole(74L, 9L)).thenReturn("OWNER");

        BusinessProjectRoutineDailyTarget target = new BusinessProjectRoutineDailyTarget();
        target.setRoutineId(111L); target.setTargetValue(new BigDecimal("12"));
        target.setCustomerRequirement("客户要求今天交付12条");

        service.saveRoutineDailyTarget(target, 9L, "owner9", false);

        ArgumentCaptor<BusinessProjectRoutineDailyTarget> captor = ArgumentCaptor.forClass(BusinessProjectRoutineDailyTarget.class);
        verify(mapper).insertRoutineDailyTarget(captor.capture());
        assertEquals(147L, captor.getValue().getAssigneeUserId());
        assertEquals("李四", captor.getValue().getAssigneeName());
        assertEquals(1, captor.getValue().getTargetVersion());
    }

    @Test
    void dynamicRoutineCannotBeReportedBeforeOwnerPublishesTarget()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(112L); routine.setProjectId(74L); routine.setTargetMode("DAILY_DYNAMIC");
        routine.setStatus("ACTIVE"); routine.setUnit("条"); routine.setAssigneeUserId(9L);
        when(mapper.selectRoutineById(112L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project);
        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(112L); report.setActualValue(BigDecimal.ONE);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitRoutineReport(report, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("尚未下达今日目标"));
        verify(mapper, never()).upsertRoutineReport(any());
    }

    @Test
    void dailyTargetCannotChangeAfterEmployeeReportAndUsesLockedCurrentRoutine()
    {
        BusinessProjectRoutine routine=new BusinessProjectRoutine();routine.setRoutineId(111L);routine.setProjectId(74L);
        routine.setStatus("ACTIVE");routine.setTargetMode("DAILY_DYNAMIC");routine.setStartDate(new Date(0));
        when(mapper.selectRoutineById(111L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project(74L,9L,"ACTIVE","APPROVED"));
        when(mapper.selectMemberRole(74L,9L)).thenReturn("OWNER");
        when(mapper.selectRoutineReport(eq(111L),any(Date.class))).thenReturn(new BusinessProjectRoutineReport());
        BusinessProjectRoutineDailyTarget target=new BusinessProjectRoutineDailyTarget();target.setRoutineId(111L);target.setTargetValue(BigDecimal.TEN);
        assertTrue(assertThrows(ServiceException.class,()->service.saveRoutineDailyTarget(target,9L,"owner9",false)).getMessage().contains("不能再修改"));
        verify(mapper).selectProjectByIdForUpdate(74L);verify(mapper).selectRoutineByIdForUpdate(111L);
        verify(mapper,never()).insertRoutineDailyTarget(any());verify(mapper,never()).supersedeRoutineDailyTarget(anyLong(),any());
    }

    @Test
    void dailyTargetRevisionRetainsPreviousVersionAndRequiresReason()
    {
        BusinessProjectRoutine routine=new BusinessProjectRoutine();routine.setRoutineId(111L);routine.setProjectId(74L);
        routine.setStatus("ACTIVE");routine.setTargetMode("DAILY_DYNAMIC");routine.setStartDate(new Date(0));routine.setUnit("条");
        when(mapper.selectRoutineById(111L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project(74L,9L,"ACTIVE","APPROVED"));
        when(mapper.selectMemberRole(74L,9L)).thenReturn("OWNER");
        BusinessProjectRoutineDailyTarget previous=new BusinessProjectRoutineDailyTarget();previous.setDailyTargetId(20L);previous.setTargetVersion(2);
        when(mapper.selectCurrentRoutineDailyTarget(eq(111L),any(Date.class))).thenReturn(previous);
        BusinessProjectRoutineDailyTarget target=new BusinessProjectRoutineDailyTarget();target.setRoutineId(111L);target.setTargetValue(BigDecimal.TEN);
        assertThrows(ServiceException.class,()->service.saveRoutineDailyTarget(target,9L,"owner9",false));
        verify(mapper,never()).supersedeRoutineDailyTarget(anyLong(),any());
        target.setChangeReason("客户追加目标");when(mapper.supersedeRoutineDailyTarget(20L,"owner9")).thenReturn(1);
        service.saveRoutineDailyTarget(target,9L,"owner9",false);
        assertEquals(3,target.getTargetVersion());assertEquals(2,previous.getTargetVersion());verify(mapper).insertRoutineDailyTarget(target);
    }

    @Test
    void dynamicRoutineReportStoresPublishedTargetSnapshotWithoutEvidenceDespiteLegacySetting()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(113L); routine.setProjectId(74L); routine.setTargetMode("DAILY_DYNAMIC");
        routine.setStatus("ACTIVE"); routine.setUnit("条"); routine.setAssigneeUserId(9L);
        routine.setEvidenceRequired("1");
        BusinessProjectRoutineDailyTarget daily = new BusinessProjectRoutineDailyTarget();
        daily.setTargetValue(new BigDecimal("12"));
        Map<String, Object> user = new HashMap<String, Object>(); user.put("nickName", "员工九");
        when(mapper.selectRoutineById(113L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project);
        when(mapper.selectCurrentRoutineDailyTarget(eq(113L), any(Date.class))).thenReturn(daily);
        when(mapper.selectActiveUserById(9L)).thenReturn(user);
        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(113L); report.setActualValue(new BigDecimal("12"));

        service.submitRoutineReport(report, 9L, "owner9", false);

        ArgumentCaptor<BusinessProjectRoutineReport> captor = ArgumentCaptor.forClass(BusinessProjectRoutineReport.class);
        verify(mapper).upsertRoutineReport(captor.capture());
        assertEquals(new BigDecimal("12"), captor.getValue().getTargetSnapshot());
    }

    @Test
    void noTotalProjectRejectsPercentageProgressReport()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        project.setGoalMode("NO_TOTAL");
        when(mapper.selectProjectById(74L)).thenReturn(project);
        BusinessProjectProgressReport report = new BusinessProjectProgressReport();
        report.setProjectId(74L); report.setProgress(20);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitProjectProgressReport(report, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("无需填写项目完成百分比"));
        verify(mapper, never()).insertProjectProgressReport(any());
    }

    @Test
    void dailyRoutineMeetingTargetClearsPreviousIssueReason()
    {
        BusinessProject project = project(74L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(11L); routine.setProjectId(74L); routine.setFrequency("DAILY");
        routine.setTargetValue(new BigDecimal("5")); routine.setUnit("条"); routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(9L);
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("nickName", "员工九");
        when(mapper.selectRoutineById(11L)).thenReturn(routine);
        when(mapper.selectProjectById(74L)).thenReturn(project);
        when(mapper.selectActiveUserById(9L)).thenReturn(user);
        when(mapper.upsertRoutineReport(any())).thenReturn(1);

        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(11L); report.setActualValue(new BigDecimal("5"));
        report.setIssueReason("生病");

        service.submitRoutineReport(report, 9L, "employee9", false);

        ArgumentCaptor<BusinessProjectRoutineReport> captor = ArgumentCaptor.forClass(BusinessProjectRoutineReport.class);
        verify(mapper).upsertRoutineReport(captor.capture());
        assertEquals(null, captor.getValue().getIssueReason());
    }

    @Test
    void unrelatedMemberCannotReportAnotherPersonsRoutine()
    {
        BusinessProject project = project(75L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(12L); routine.setProjectId(75L); routine.setFrequency("DAILY");
        routine.setTargetValue(BigDecimal.ONE); routine.setUnit("条"); routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(9L);
        when(mapper.selectRoutineById(12L)).thenReturn(routine);
        when(mapper.selectProjectById(75L)).thenReturn(project);
        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(12L); report.setActualValue(BigDecimal.ONE);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitRoutineReport(report, 77L, "other", false));

        assertTrue(error.getMessage().contains("实际执行人本人"));
        verify(mapper, never()).upsertRoutineReport(any());
    }

    @Test
    void projectOwnerCannotReportAnotherPersonsRoutine()
    {
        BusinessProject project = project(75L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(13L); routine.setProjectId(75L); routine.setFrequency("DAILY");
        routine.setTargetValue(BigDecimal.ONE); routine.setUnit("条"); routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(147L);
        when(mapper.selectRoutineById(13L)).thenReturn(routine);
        when(mapper.selectProjectById(75L)).thenReturn(project);

        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(13L); report.setActualValue(BigDecimal.ONE);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitRoutineReport(report, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("实际执行人本人"));
        verify(mapper, never()).upsertRoutineReport(any());
    }

    @Test
    void employeeOnLeaveCannotSubmitRoutineCompletion()
    {
        BusinessProject project = project(76L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(14L); routine.setProjectId(76L); routine.setFrequency("DAILY");
        routine.setTargetValue(BigDecimal.ONE); routine.setUnit("条"); routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(9L);
        when(mapper.selectRoutineById(14L)).thenReturn(routine);
        when(mapper.selectProjectById(76L)).thenReturn(project);
        Map<String,Object> leave=new HashMap<String,Object>();leave.put("status","ACTIVE");
        when(mapper.selectStaffLeave(eq(9L),any(Date.class))).thenReturn(leave);
        BusinessProjectRoutineReport report = new BusinessProjectRoutineReport();
        report.setRoutineId(14L); report.setActualValue(BigDecimal.ONE);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitRoutineReport(report, 9L, "employee9", false));

        assertTrue(error.getMessage().contains("今日已登记请假"));
        verify(mapper, never()).upsertRoutineReport(any());
    }

    @Test
    void employeeOnLeaveCannotSubmitOneOffTaskCompletion()
    {
        BusinessProject project = project(76L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskId(31L); task.setProjectId(76L); task.setTaskName("每日测试");
        task.setAssigneeUserId(9L); task.setStatus("TODO"); task.setProgress(0); task.setVersion(0);
        when(mapper.selectTaskById(31L)).thenReturn(task);
        when(mapper.selectProjectById(76L)).thenReturn(project);
        Map<String,Object> leave = new HashMap<String,Object>(); leave.put("status", "ACTIVE");
        when(mapper.selectStaffLeave(eq(9L), any(Date.class))).thenReturn(leave);
        BusinessProjectTaskReport report = new BusinessProjectTaskReport();
        report.setTaskId(31L); report.setProgress(10); report.setCompletionSummary("完成部分工作");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submitTaskReport(report, 9L, "employee9"));

        assertTrue(error.getMessage().contains("今日已登记请假"));
        verify(mapper, never()).updateTask(any());
        verify(mapper, never()).upsertTaskReport(any());
    }

    @Test
    void oneOffTaskReportRequiresSummaryAndProgressButAcceptsNoEvidence()
    {
        BusinessProject project = project(76L, 9L, "ACTIVE", "APPROVED");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskId(31L); task.setProjectId(76L); task.setTaskName("今日任务");
        task.setAssigneeUserId(9L); task.setStatus("TODO"); task.setProgress(0); task.setVersion(0);
        when(mapper.selectTaskById(31L)).thenReturn(task);
        when(mapper.selectProjectById(76L)).thenReturn(project);
        BusinessProjectTaskReport report = new BusinessProjectTaskReport();
        report.setTaskId(31L);

        assertThrows(ServiceException.class, () -> service.submitTaskReport(report, 9L, "employee9"));
        report.setProgress(10);
        assertThrows(ServiceException.class, () -> service.submitTaskReport(report, 9L, "employee9"));

        report.setCompletionSummary("完成今日工作");
        when(mapper.updateTask(task)).thenReturn(1);
        when(mapper.selectActiveUserById(9L)).thenReturn(row("userId",9L,"nickName","成员九"));
        when(mapper.selectTaskReport(eq(31L),any(Date.class))).thenReturn(report);
        service.submitTaskReport(report, 9L, "employee9");

        assertEquals("", report.getEvidenceUrls());
        verify(mapper).upsertTaskReport(report);
        verify(businessFileService, never()).validateReferences(any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void bossCanCloseSimpleProjectWithConclusion()
    {
        BusinessProject project = project(76L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        BusinessProject closed = project(76L, 9L, "CLOSED", "APPROVED");
        closed.setInitiatorUserId(8L);
        when(mapper.selectProjectById(76L)).thenReturn(project, closed);
        when(mapper.selectTasks(76L)).thenReturn(Collections.singletonList(completedTask("项目交付")));
        when(mapper.selectMilestones(76L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(76L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(76L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(76L, "ACTIVE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);

        BusinessProject result = service.transition(76L, "CLOSE", "目标已完成", 8L, "boss8", true);

        assertEquals("CLOSED", result.getStatus());
        verify(accountingService).ensureProjectCanClose(76L);
        verify(accountingService).closeProjectAccounting(eq(76L), any(Date.class), eq("boss8"));
        verify(mapper).closeProjectRoutines(76L, "boss8");
        verify(mapper).closeProjectAllocations(eq(76L), any(Date.class), eq("boss8"));
        verify(mapper).insertEvent(any());
    }

    @Test
    void projectWithoutPublishedKpiPlanCannotClose()
    {
        BusinessProject project = project(77L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(77L)).thenReturn(project);
        when(kpiMapper.selectPlanSummaries(77L)).thenReturn(Collections.emptyList());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(77L, "CLOSE", "目标已完成", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("尚未发布KPI方案"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void dueDraftKpiSettlementCannotClose()
    {
        BusinessProject project = project(78L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(78L)).thenReturn(project);
        when(kpiMapper.selectPlanSummaries(78L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("DRAFT")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(78L, "CLOSE", "目标已完成", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("负责人尚未提交"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void dueReturnedKpiSettlementCannotClose()
    {
        BusinessProject project = project(79L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(79L)).thenReturn(project);
        when(kpiMapper.selectPlanSummaries(79L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("RETURNED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(79L, "CLOSE", "目标已完成", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("被退回"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void unfinishedFutureKpiPeriodBlocksSeparatedDeliveryClose()
    {
        BusinessProject project = project(80L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        project.setDeliveryPolicyVersion("SEPARATED_V1");
        project.setAccountingState("OPEN");
        Map<String, Object> futurePlan = publishedKpiPlan("DRAFT");
        futurePlan.put("cycleEnd", new Date(System.currentTimeMillis() + 172800000L));
        when(mapper.selectProjectById(80L)).thenReturn(project);
        when(kpiMapper.selectPlanSummaries(80L)).thenReturn(Collections.singletonList(futurePlan));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.transition(80L, "CLOSE", "目标已完成", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("KPI考核周期未结束"));
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void confirmedEarlyKpiAllowsSeparatedDeliveryClose()
    {
        BusinessProject project = project(801L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);project.setDeliveryPolicyVersion("SEPARATED_V1");project.setAccountingState("OPEN");
        Map<String, Object> confirmedPlan = publishedKpiPlan("CONFIRMED");
        confirmedPlan.put("cycleEnd", new Date(System.currentTimeMillis() + 172800000L));
        when(mapper.selectProjectById(801L)).thenReturn(project);
        when(mapper.selectTasks(801L)).thenReturn(Collections.singletonList(completedTask("项目交付")));
        when(kpiMapper.selectPlanSummaries(801L)).thenReturn(Collections.singletonList(confirmedPlan));
        when(mapper.updateProjectStatus(801L, "ACTIVE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);
        when(mapper.closeAccounting(801L, 1, "boss8")).thenReturn(1);

        service.transition(801L, "CLOSE", "KPI已提前达标", 8L, "boss8", true);

        verify(mapper).updateProjectStatus(801L, "ACTIVE", "CLOSED", null, false, "boss8", 0);
        verify(accountingService).closeProjectAccounting(eq(801L), any(Date.class), eq("boss8"));
        verify(mapper).closeAccounting(801L, 1, "boss8");
    }

    @Test
    void initiatingBossCanApprovePendingAcceptance()
    {
        BusinessProject pendingProject = project(72L, 9L, "ACCEPTANCE", "APPROVED");
        pendingProject.setCloseMethod("RESULT_ACCEPTANCE");
        pendingProject.setInitiatorUserId(8L);
        BusinessProject closedProject = project(72L, 9L, "CLOSED", "APPROVED");
        closedProject.setInitiatorUserId(8L);
        BusinessProjectAcceptance pending = new BusinessProjectAcceptance();
        pending.setAcceptanceId(700L);
        BusinessProjectTask done = new BusinessProjectTask();
        done.setStatus("DONE");
        Map<String, Object> boss = new HashMap<String, Object>();
        boss.put("nickName", "老板八");
        when(mapper.selectProjectById(72L)).thenReturn(pendingProject, closedProject);
        when(mapper.selectLatestPendingAcceptance(72L)).thenReturn(pending);
        when(mapper.selectTasks(72L)).thenReturn(Collections.singletonList(done));
        when(mapper.selectMilestones(72L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(72L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(72L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.selectActiveUserById(8L)).thenReturn(boss);
        when(mapper.reviewAcceptance(700L, "APPROVED", 8L, "老板八", "同意验收", "boss8")).thenReturn(1);
        when(mapper.updateProjectStatus(72L, "ACCEPTANCE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);

        BusinessProject result = service.reviewAcceptance(72L, "APPROVED", "同意验收", 8L, "boss8", true);

        assertEquals("CLOSED", result.getStatus());
        verify(mapper).insertEvent(any());
    }

    @Test
    void dueSubmittedKpiSettlementBlocksAcceptanceApproval()
    {
        BusinessProject pendingProject = project(73L, 9L, "ACCEPTANCE", "APPROVED");
        pendingProject.setCloseMethod("RESULT_ACCEPTANCE");
        pendingProject.setInitiatorUserId(8L);
        BusinessProjectAcceptance pending = new BusinessProjectAcceptance();
        pending.setAcceptanceId(701L);
        BusinessProjectTask done = new BusinessProjectTask();
        done.setStatus("DONE");
        when(mapper.selectProjectById(73L)).thenReturn(pendingProject);
        when(mapper.selectLatestPendingAcceptance(73L)).thenReturn(pending);
        when(mapper.selectTasks(73L)).thenReturn(Collections.singletonList(done));
        when(mapper.selectMilestones(73L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(73L)).thenReturn(Collections.emptyList());
        when(kpiMapper.selectPlanSummaries(73L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("SUBMITTED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reviewAcceptance(73L, "APPROVED", "同意验收", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("待老板确认"));
        verify(mapper, never()).reviewAcceptance(any(), any(), any(), any(), any(), any());
        verify(mapper, never()).updateProjectStatus(any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void projectOwnerSeesAllocatedAmountButNotRawStaffCost()
    {
        BusinessProject project = project(81L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        project.setBudgetLimit(new BigDecimal("100000"));
        Map<String, Object> allocation = new HashMap<String, Object>();
        allocation.put("userId", 9L);
        allocation.put("allocatedCost", new BigDecimal("400"));
        allocation.put("unitCost", new BigDecimal("800"));
        allocation.put("costMode", "DAILY");
        allocation.put("costPolicyId", 18L);
        when(mapper.selectProjectById(81L)).thenReturn(project);
        when(mapper.selectProjectStaffAllocations(81L)).thenReturn(Collections.singletonList(allocation));
        when(mapper.selectProjectKpis(81L)).thenReturn(Collections.emptyList());
        when(mapper.selectBudgetHistory(81L)).thenReturn(Collections.emptyList());

        Map<String, Object> config = service.operatingConfig(81L, 9L, false, false);

        Map<?, ?> visible = ((List<Map<String, Object>>) config.get("staffAllocations")).get(0);
        assertEquals(new BigDecimal("400"), visible.get("allocatedCost"));
        assertEquals(null, visible.get("unitCost"));
        assertEquals(false, config.get("rawCostVisible"));
    }

    @Test
    void parentOwnerCanSeeChildProjectRawStaffCost()
    {
        BusinessProject child = project(82L, 30L, "ACTIVE", "APPROVED");
        child.setParentId(13L);
        BusinessProject parent = project(13L, 9L, "ACTIVE", "APPROVED");
        Map<String, Object> allocation = new HashMap<String, Object>();
        allocation.put("userId", 30L);
        allocation.put("unitCost", new BigDecimal("800"));
        when(mapper.selectProjectById(82L)).thenReturn(child);
        when(mapper.selectProjectById(13L)).thenReturn(parent);
        when(mapper.selectProjectStaffAllocations(82L)).thenReturn(Collections.singletonList(allocation));
        when(mapper.selectProjectKpis(82L)).thenReturn(Collections.emptyList());
        when(mapper.selectBudgetHistory(82L)).thenReturn(Collections.emptyList());

        Map<String, Object> config = service.operatingConfig(82L, 9L, false, false);

        Map<?, ?> visible = ((List<Map<String, Object>>) config.get("staffAllocations")).get(0);
        assertEquals(new BigDecimal("800"), visible.get("unitCost"));
        assertEquals(true, config.get("rawCostVisible"));
    }

    @Test
    void linkedLiveProjectOnlyExposesManagementSummary()
    {
        BusinessProject project = project(84L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        Map<String, Object> relation = new HashMap<String, Object>();
        relation.put("relationId", 31L);
        relation.put("sourceDomain", "LIVE");
        relation.put("effectiveFrom", java.sql.Date.valueOf("2026-08-01"));
        Map<String, Object> liveSummary = new HashMap<String, Object>();
        liveSummary.put("statDate", "2026-08-11");
        liveSummary.put("expectedStreamerCount", 10L);
        liveSummary.put("submittedStreamerCount", 8L);
        when(mapper.selectProjectById(84L)).thenReturn(project);
        when(mapper.selectProjectKpis(84L)).thenReturn(Collections.emptyList());
        when(mapper.selectBudgetHistory(84L)).thenReturn(Collections.emptyList());
        when(mapper.selectProjectStaffAllocations(84L)).thenReturn(Collections.emptyList());
        when(mapper.selectActiveExecutionRelation(84L)).thenReturn(relation);
        when(mapper.selectLiveExecutionSummary(relation)).thenReturn(liveSummary);

        Map<String, Object> config = service.operatingConfig(84L, 9L, false, false);

        Map<?, ?> summary = (Map<?, ?>) config.get("executionSummary");
        assertEquals("直播数据管理", summary.get("sourceName"));
        assertEquals(10L, summary.get("expectedStreamerCount"));
        assertEquals(8L, summary.get("submittedStreamerCount"));
        assertEquals(true, summary.get("readOnly"));
    }

    @Test
    void linkedLiveProjectAddsSourceManagedStreamerRoutines()
    {
        BusinessProject project = project(85L, 9L, "ACTIVE", "APPROVED");
        Map<String, Object> relation = new HashMap<String, Object>();
        relation.put("projectId", 85L);
        relation.put("sourceDomain", "LIVE");
        BusinessProjectRoutine sourceRoutine = new BusinessProjectRoutine();
        sourceRoutine.setRoutineId(-7L);
        sourceRoutine.setRoutineName("主播七 · 直播日报");
        sourceRoutine.setAssigneeName("主播七");
        sourceRoutine.setSourceManaged(true);
        sourceRoutine.setSourceDomain("LIVE");
        when(mapper.selectProjectById(85L)).thenReturn(project);
        when(mapper.selectRoutines(eq(85L), any())).thenReturn(Collections.emptyList());
        when(mapper.selectActiveExecutionRelation(85L)).thenReturn(relation);
        when(mapper.selectLiveStreamerRoutines(relation)).thenReturn(Collections.singletonList(sourceRoutine));

        BusinessProject result = service.getProject(85L, 9L, false, false);

        assertEquals("LIVE", result.getExecutionSource());
        assertEquals(1, result.getRoutines().size());
        assertEquals(true, result.getRoutines().get(0).getSourceManaged());
        assertEquals("主播七", result.getRoutines().get(0).getAssigneeName());
    }

    @Test
    void chinaStaffMonthlyCostUsesCnyAndTwentyOnePointSevenFiveDays()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "中国员工");
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectNextStaffCostVersion(147L)).thenReturn(1);

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L); input.setUnitCost(new BigDecimal("10000"));
        input.setStandardWorkDays(new BigDecimal("30")); input.setRateMinutesPerDay(360);
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 8L, "boss8", true);

        assertEquals("MONTHLY", saved.getCostMode());
        assertEquals("CNY", saved.getCurrency());
        assertEquals("CN", saved.getCountryRegion());
        assertEquals(new BigDecimal("21.75"), saved.getStandardWorkDays());
        assertEquals(Integer.valueOf(480), saved.getRateMinutesPerDay());
        verify(mapper).insertStaffCostPolicy(saved);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void standardProjectStartsWithPlanSnapshotAndNoAutomaticTask(boolean subproject)
    {
        BusinessProjectProposal proposal=new BusinessProjectProposal();proposal.setProposalId(66L);proposal.setProjectName("基础交付项目");proposal.setTemplateVersion("LIGHT_V1");proposal.setApplicantUserId(9L);proposal.setSponsorOwnerUserId(23L);proposal.setManagementMode("LIGHT");proposal.setAcceptanceCriteria("交付文件");
        if (!subproject) when(mapper.selectActiveUserById(9L)).thenReturn(Collections.singletonMap("nickName","负责人"));when(mapper.selectActiveUserById(23L)).thenReturn(Collections.singletonMap("nickName","归属老板"));
        final BusinessProject[] stored=new BusinessProject[1];doAnswer(call->{stored[0]=call.getArgument(0);stored[0].setProjectId(88L);return 1;}).when(mapper).insertProject(any());when(mapper.selectProjectById(88L)).thenAnswer(call->stored[0]);
        if (subproject) {
            BusinessProject parent=project(15L,9L,"ACTIVE","APPROVED");parent.setSponsorOwnerUserId(23L);
            when(mapper.selectProjectById(15L)).thenReturn(parent);proposal.setParentProjectId(15L);proposal.setAssignedOwnerUserId(10L);proposal.setApplicantName("主负责人");
            when(mapper.selectMemberRole(15L,9L)).thenReturn("OWNER");
            when(mapper.selectActiveUserById(10L)).thenReturn(Collections.singletonMap("nickName","子负责人"));
        }
        BusinessProject created=service.createApprovedProject(proposal,9L,"owner");ArgumentCaptor<Map<String,Object>> baseline=mapCaptor();verify(workMapper).insertBaseline(baseline.capture());
        assertEquals(Integer.valueOf(1),created.getBaselineVersion());assertEquals(created.getBaselineVersion(),baseline.getValue().get("baselineVersion"));assertEquals("MEMBER_DAYS_V1",created.getCostPolicyVersion());
        assertEquals(subproject ? Long.valueOf(15) : null,created.getParentId());
        assertEquals(88L,baseline.getValue().get("projectId"));
        assertEquals(subproject?10L:9L,created.getMainOwnerUserId());assertEquals(9L,created.getApplicantUserId());
        assertEquals(23L,created.getSponsorOwnerUserId());
        ArgumentCaptor<BusinessProjectMember> member=ArgumentCaptor.forClass(BusinessProjectMember.class);
        verify(mapper).upsertMember(member.capture());assertEquals(created.getMainOwnerUserId(),member.getValue().getUserId());assertEquals("OWNER",member.getValue().getMemberRole());
        verify(mapper,never()).updateProject(any());
        verify(mapper,never()).insertTask(any());
        verify(mapper,never()).insertWorkPeriod(any());
    }

    @Test
    void standardProjectMemberKeepsDateObjectAsRoleEffectiveDate()
    {
        BusinessProjectProposal proposal=new BusinessProjectProposal();
        Date start=java.sql.Date.valueOf("2026-09-08");proposal.setPlanStartDate(start);
        Map<String,Object> staffing=Collections.<String,Object>singletonMap("planStartDate",start);

        Date joined=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"memberJoinedDate",proposal,staffing);

        assertEquals(start,joined);
    }

    @Test
    void proposalRatioBecomesFormalProjectAllocationAndCannotOverbookPerson()
    {
        BusinessProject project=project(88L,9L,"ACTIVE","APPROVED");
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        BusinessProjectMember member=new BusinessProjectMember();member.setProjectId(88L);member.setUserId(12L);
        member.setUserNameSnapshot("成员十二");member.setMemberRole("MEMBER");member.setJoinedDate(new Date());
        when(mapper.selectUserAllocationWorkspace(eq(12L),any(Date.class))).thenReturn(Collections.emptyList());
        when(mapper.sumAllocationPercentAtDate(eq(12L),any(Date.class))).thenReturn(new BigDecimal("30"));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"ensureDefaultProjectWeight",
            project,member,"owner",new BigDecimal("40"));

        ArgumentCaptor<BusinessProjectStaffAllocation> allocation=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper).insertProjectStaffAllocation(allocation.capture());
        assertEquals(new BigDecimal("40.00"),allocation.getValue().getAllocationValue());
        assertEquals("PENDING",allocation.getValue().getConfirmationStatus());

        ServiceException error=assertThrows(ServiceException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            service,"ensureDefaultProjectWeight",project,member,"owner",new BigDecimal("80")));
        assertTrue(error.getMessage().contains("本项目最多可设置70%"));
    }

    @Test
    void fullyAllocatedPersonCanJoinBeforeRedistributingProjectWeights()
    {
        BusinessProject project=project(89L,9L,"ACTIVE","APPROVED");
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        BusinessProjectMember member=new BusinessProjectMember();member.setProjectId(89L);member.setUserId(12L);
        member.setMemberRole("MEMBER");
        when(mapper.selectProjectById(89L)).thenReturn(project);
        when(mapper.selectMemberRole(89L,9L)).thenReturn("OWNER");
        when(mapper.selectActiveUserById(12L)).thenReturn(row("userName","member12","nickName","成员十二"));
        when(mapper.selectUserAllocationWorkspace(eq(12L),any(Date.class))).thenReturn(Collections.emptyList());
        when(mapper.sumAllocationPercentAtDate(eq(12L),any(Date.class))).thenReturn(new BigDecimal("100"));

        BusinessProjectMember saved=service.saveMember(member,9L,"owner9",false);

        assertEquals("成员十二",saved.getUserNameSnapshot());
        verify(mapper).upsertMember(member);
        verify(mapper,never()).insertProjectStaffAllocation(any(BusinessProjectStaffAllocation.class));
    }

    @Test
    void independentFinanceRoleCanMaintainExplicitDailyRateOnlyInsideOwnCompany()
    {
        when(mapper.countUserRoleByKey(80L,"company_owner")).thenReturn(0);
        when(mapper.countUserRoleByKey(80L,"finance_cost_manager")).thenReturn(1);
        when(mapper.selectStaffCompanyId(80L)).thenReturn(111L);when(mapper.selectStaffCompanyId(147L)).thenReturn(111L);
        when(mapper.selectActiveUserById(147L)).thenReturn(Collections.singletonMap("nickName","成员"));
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("OTHER");
        BusinessStaffCostPolicy input=new BusinessStaffCostPolicy();input.setUserId(147L);input.setCostMode("DAILY");input.setCurrency("USD");input.setUnitCost(new BigDecimal("100"));input.setRateMinutesPerDay(360);input.setEffectiveFrom(java.sql.Date.valueOf("2026-09-01"));
        BusinessStaffCostPolicy saved=service.saveStaffCostPolicy(input,80L,"finance",true);
        assertEquals("DAILY",saved.getCostMode());assertEquals("USD",saved.getCurrency());assertEquals(Integer.valueOf(480),saved.getRateMinutesPerDay());assertEquals(null,saved.getStandardWorkDays());
        when(mapper.selectStaffCompanyId(147L)).thenReturn(222L);assertThrows(ServiceException.class,()->service.saveStaffCostPolicy(input,80L,"finance",true));
    }

    @Test
    void projectResponsibilityCanReadMemberRatesWithoutIndependentRatePermission()
    {
        when(mapper.selectManagedProjectMemberUserIds(10L)).thenReturn(Collections.singletonList(147L));
        when(mapper.selectActiveUserById(147L)).thenReturn(Collections.singletonMap("nickName","项目成员"));
        when(mapper.countManagedProjectMember(10L,147L)).thenReturn(1);
        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.singletonList(new BusinessStaffCostPolicy()));
        assertEquals(1,service.staffCostPolicies(147L,10L,false).size());
    }

    @Test
    void projectOwnerCannotReadOrWriteCostForUnmanagedMember()
    {
        when(mapper.selectManagedProjectMemberUserIds(10L)).thenReturn(Collections.singletonList(147L));
        when(mapper.selectActiveUserById(148L)).thenReturn(Collections.singletonMap("nickName","其他人员"));
        assertThrows(ServiceException.class,()->service.staffCostPolicies(148L,10L,false));
        BusinessStaffCostPolicy input=new BusinessStaffCostPolicy();input.setUserId(148L);
        assertThrows(ServiceException.class,()->service.saveStaffCostPolicy(input,10L,"owner",false));
        verify(mapper,never()).selectStaffCostPolicies(anyLong());
        verify(mapper,never()).insertStaffCostPolicy(any());
    }

    @Test
    void ordinaryStaffCannotUseCostApiWithDirectoryPermissionOnly()
    {
        assertThrows(ServiceException.class,()->service.staffCostPolicies(147L,10L,false));
        assertThrows(ServiceException.class,()->service.staffCostOptions(10L,false));
        verify(mapper,never()).selectStaffCostPolicies(anyLong());
        verify(mapper,never()).selectStaffCostOptions(any());
    }

    @Test
    void vietnamStaffMonthlyCostUsesTwentySixDays()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "越南员工");
        when(mapper.selectActiveUserById(148L)).thenReturn(staff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyId(148L)).thenReturn(110L);
        when(mapper.selectStaffCountryRegion(148L)).thenReturn("VN");
        when(mapper.selectNextStaffCostVersion(148L)).thenReturn(3);

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(148L); input.setUnitCost(new BigDecimal("13000"));
        input.setStandardWorkDays(new BigDecimal("21.75"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 8L, "boss8", true);

        assertEquals("CNY", saved.getCurrency());
        assertEquals("VN", saved.getCountryRegion());
        assertEquals(new BigDecimal("26"), saved.getStandardWorkDays());
        assertEquals(Integer.valueOf(3), saved.getPolicyVersion());
    }

    @Test
    void batchStaffMonthlyCostSavesEveryPersonWithTheirRegionRule()
    {
        Map<String, Object> chinaStaff = new HashMap<String, Object>();
        chinaStaff.put("nickName", "中国员工");
        Map<String, Object> vietnamStaff = new HashMap<String, Object>();
        vietnamStaff.put("nickName", "越南员工");
        when(mapper.selectActiveUserById(147L)).thenReturn(chinaStaff);
        when(mapper.selectActiveUserById(148L)).thenReturn(vietnamStaff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.selectStaffCompanyId(148L)).thenReturn(111L);
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectStaffCountryRegion(148L)).thenReturn("VN");
        when(mapper.selectNextStaffCostVersion(anyLong())).thenReturn(1);
        BusinessStaffCostPolicy china = new BusinessStaffCostPolicy();
        china.setUserId(147L); china.setUnitCost(new BigDecimal("10000"));
        china.setEffectiveFrom(java.sql.Date.valueOf("2026-08-24"));
        BusinessStaffCostPolicy vietnam = new BusinessStaffCostPolicy();
        vietnam.setUserId(148L); vietnam.setUnitCost(new BigDecimal("10000"));
        vietnam.setEffectiveFrom(java.sql.Date.valueOf("2026-08-24"));

        List<BusinessStaffCostPolicy> saved = service.saveStaffCostPolicies(
            Arrays.asList(china, vietnam), 8L, "boss8", true);

        assertEquals(2, saved.size());
        assertEquals(new BigDecimal("21.75"), saved.get(0).getStandardWorkDays());
        assertEquals(new BigDecimal("26"), saved.get(1).getStandardWorkDays());
        verify(mapper, times(2)).insertStaffCostPolicy(any(BusinessStaffCostPolicy.class));
    }

    @Test
    void batchStaffMonthlyCostRejectsDuplicatePeopleBeforeWriting()
    {
        BusinessStaffCostPolicy first = new BusinessStaffCostPolicy(); first.setUserId(147L);
        BusinessStaffCostPolicy duplicate = new BusinessStaffCostPolicy(); duplicate.setUserId(147L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicies(Arrays.asList(first, duplicate), 8L, "boss8", true));

        assertTrue(error.getMessage().contains("重复人员"));
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void otherStaffRegionCannotOverrideMonthlyDayBasis()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "其他地区员工");
        when(mapper.selectActiveUserById(149L)).thenReturn(staff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyId(149L)).thenReturn(110L);
        when(mapper.selectStaffCountryRegion(149L)).thenReturn("OTHER");
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(149L); input.setUnitCost(new BigDecimal("9000")); input.setStandardWorkDays(new BigDecimal("22"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("国家/地区"));
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void companyOwnerCanReadOwnCompanyStaffCostPolicies()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setPolicyId(21L);
        when(mapper.countUserRoleByKey(120L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.singletonList(policy));

        List<BusinessStaffCostPolicy> result = service.staffCostPolicies(147L, 120L, true);

        assertEquals(1, result.size());
        assertEquals(Long.valueOf(21L), result.get(0).getPolicyId());
    }

    @Test
    void projectOwnerCanSetStaffCostWithoutCompanyOwnerScope()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "项目成员");
        when(mapper.selectManagedProjectMemberUserIds(134L)).thenReturn(Arrays.asList(147L));
        when(mapper.countManagedProjectMember(134L, 147L)).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectNextStaffCostVersion(147L)).thenReturn(2);
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L); input.setUnitCost(new BigDecimal("8000"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-09-03"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 134L, "zhangsan", false);

        assertEquals(Integer.valueOf(2), saved.getPolicyVersion());
        verify(mapper, never()).selectStaffCompanyLeaderUserId(anyLong(),
            org.mockito.ArgumentMatchers.anyBoolean());
        verify(mapper).insertStaffCostPolicy(saved);
    }

    @Test
    void companyOwnerCanReadForeignCompanyStaffCostPolicies()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.countUserRoleByKey(143L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);

        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.singletonList(new BusinessStaffCostPolicy()));
        assertEquals(1, service.staffCostPolicies(147L, 143L, true).size());
        verify(mapper).selectStaffCostPolicies(147L);
    }

    @Test
    void companyOwnerCanWriteForeignCompanyStaffCostPolicy()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.countUserRoleByKey(143L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectNextStaffCostVersion(147L)).thenReturn(1);
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L); input.setUnitCost(new BigDecimal("10000"));
        input.setStandardWorkDays(new BigDecimal("30")); input.setRateMinutesPerDay(360);
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-20"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 143L, "wangfuzhang", true);
        assertEquals(new BigDecimal("21.75"), saved.getStandardWorkDays());
        assertEquals(Integer.valueOf(480), saved.getRateMinutesPerDay());
        verify(mapper).insertStaffCostPolicy(saved);
    }

    @Test
    void companyOwnerCannotMaintainStaffWithoutActiveCompany()
    {
        when(mapper.countUserRoleByKey(143L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(Collections.singletonMap("nickName", "未归属公司人员"));
        when(mapper.selectStaffCompanyId(147L)).thenReturn(null);
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L);
        assertThrows(ServiceException.class, () -> service.staffCostPolicies(147L, 143L, true));
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 143L, "wangfuzhang", true));
        assertTrue(error.getMessage().contains("成本权限"));
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void hardcodedBossFlagCannotBypassCompanyOwnerRole()
    {
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 88L, "staff88", true));

        assertTrue(error.getMessage().contains("负责人"));
        verify(mapper, never()).selectActiveUserById(any());
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void administratorCanWriteStaffCostPolicyForAnyCompany()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.selectCostEligibleUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.emptyList());
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectNextStaffCostVersion(147L)).thenReturn(1);

        assertEquals(0, service.staffCostPolicies(147L, 1L, true).size());

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L);
        input.setUnitCost(new BigDecimal("10000"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-24"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 1L, "admin", true);

        assertEquals("MONTHLY", saved.getCostMode());
        assertEquals("CNY", saved.getCurrency());
        assertEquals(new BigDecimal("21.75"), saved.getStandardWorkDays());
        assertEquals(Integer.valueOf(480), saved.getRateMinutesPerDay());
        verify(mapper).insertStaffCostPolicy(saved);
    }

    @Test
    void futureUnusedStaffCostPolicyCanBeDeleted()
    {
        BusinessStaffCostPolicy policy = staffCostPolicy(31L, 147L, "2099-08-30", "ACTIVE", 0);
        Map<String, Object> staff = new HashMap<String, Object>(); staff.put("nickName", "上海员工");
        when(mapper.selectStaffCostPolicyById(31L)).thenReturn(policy);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.deleteUnusedFutureStaffCostPolicy(31L)).thenReturn(1);

        service.deleteStaffCostPolicy(31L, 8L, "boss8", true);

        verify(mapper).deleteUnusedFutureStaffCostPolicy(31L);
        verify(mapper).restorePrecedingStaffCostPolicy(147L, policy.getEffectiveFrom());
    }

    @Test
    void referencedStaffCostPolicyCannotBeDeleted()
    {
        BusinessStaffCostPolicy policy = staffCostPolicy(32L, 147L, "2099-08-30", "ACTIVE", 2);
        Map<String, Object> staff = new HashMap<String, Object>(); staff.put("nickName", "上海员工");
        when(mapper.selectStaffCostPolicyById(32L)).thenReturn(policy);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.deleteStaffCostPolicy(32L, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("已被项目投入引用"));
        verify(mapper, never()).deleteUnusedFutureStaffCostPolicy(anyLong());
    }

    @Test
    void effectiveStaffCostPolicyCannotBeDeleted()
    {
        BusinessStaffCostPolicy policy = staffCostPolicy(33L, 147L, "2020-08-30", "ACTIVE", 0);
        Map<String, Object> staff = new HashMap<String, Object>(); staff.put("nickName", "上海员工");
        when(mapper.selectStaffCostPolicyById(33L)).thenReturn(policy);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.deleteStaffCostPolicy(33L, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("已生效"));
        verify(mapper, never()).deleteUnusedFutureStaffCostPolicy(anyLong());
    }

    @Test
    void staffCostPolicyCanBeVoidedWithAuditReason()
    {
        BusinessStaffCostPolicy policy = staffCostPolicy(34L, 147L, "2020-08-30", "ACTIVE", 3);
        Map<String, Object> staff = new HashMap<String, Object>(); staff.put("nickName", "上海员工");
        when(mapper.selectStaffCostPolicyById(34L)).thenReturn(policy);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyId(147L)).thenReturn(110L);
        when(mapper.voidStaffCostPolicy(34L, "金额录入错误", 8L, "boss8")).thenReturn(1);

        service.voidStaffCostPolicy(34L, "  金额录入错误  ", 8L, "boss8", true);

        verify(mapper).voidStaffCostPolicy(34L, "金额录入错误", 8L, "boss8");
    }

    @Test
    void staffCostPolicyVoidRequiresReason()
    {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.voidStaffCostPolicy(34L, "  ", 8L, "boss8", true));

        assertTrue(error.getMessage().contains("作废原因"));
        verify(mapper, never()).selectStaffCostPolicyById(anyLong());
    }

    @Test
    void percentageAllocationOverOneHundredRequiresRecordedException()
    {
        BusinessProject project = project(82L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setPolicyId(19L); policy.setUserId(9L);
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "负责人九");
        when(mapper.selectProjectById(82L)).thenReturn(project);
        when(mapper.selectMemberRole(82L, 9L)).thenReturn("OWNER");
        when(mapper.selectEffectiveStaffCostPolicy(any(), any())).thenReturn(policy);
        when(mapper.sumOverlappingAllocationPercent(any(), any(), any(), any())).thenReturn(new BigDecimal("50"));

        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setProjectId(82L); allocation.setUserId(9L); allocation.setAllocationMode("PERCENTAGE");
        allocation.setAllocationValue(new BigDecimal("60")); allocation.setEffectiveFrom(new Date());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffAllocation(allocation, 9L, "owner9", false));
        assertTrue(error.getMessage().contains("超过100%"));
        verify(mapper, never()).insertProjectStaffAllocation(any());
    }

    @Test
    void bossCannotSetNormalMemberAllocationForProjectOwner()
    {
        BusinessProject project = project(84L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(84L)).thenReturn(project);
        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setProjectId(84L); allocation.setUserId(9L); allocation.setAllocationMode("PERCENTAGE");
        allocation.setAllocationValue(new BigDecimal("50")); allocation.setEffectiveFrom(new Date());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffAllocation(allocation, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("不能代替项目主负责人"));
        verify(mapper, never()).insertProjectStaffAllocation(any());
    }

    @Test
    void bossCanRetireProjectOwnersAllocationWithAudit()
    {
        BusinessProject project=project(86L,9L,"ACTIVE","APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(86L)).thenReturn(project);
        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setAllocationId(501L);
        allocation.setProjectId(86L);
        allocation.setEffectiveFrom(new Date());
        when(mapper.selectProjectStaffAllocationById(501L)).thenReturn(allocation);
        when(mapper.voidProjectStaffAllocation(86L,501L,"boss8")).thenReturn(1);

        service.removeStaffAllocation(86L,501L,8L,"boss8",true);

        verify(mapper).voidProjectStaffAllocation(86L,501L,"boss8");
        verify(mapper).insertEvent(any());
    }

    @Test
    void bossCanOnlyApproveRecordedOverAllocationException()
    {
        BusinessProject project = project(85L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setPolicyId(21L); policy.setUserId(9L);
        Map<String,Object> staff = new HashMap<String,Object>();staff.put("nickName","负责人九");
        when(mapper.selectProjectById(85L)).thenReturn(project);
        when(mapper.selectMemberRole(85L, 9L)).thenReturn("OWNER");
        when(mapper.selectEffectiveStaffCostPolicy(any(), any())).thenReturn(policy);
        when(mapper.sumOverlappingAllocationPercent(any(), any(), any(), any())).thenReturn(new BigDecimal("50"));
        when(mapper.selectActiveUserById(9L)).thenReturn(staff);
        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setProjectId(85L); allocation.setUserId(9L); allocation.setAllocationMode("PERCENTAGE");
        allocation.setAllocationValue(new BigDecimal("60")); allocation.setEffectiveFrom(new Date());
        allocation.setExceptionAllowed("1");allocation.setExceptionReason("临时跨项目支援");

        BusinessProjectStaffAllocation saved = service.saveStaffAllocation(allocation,8L,"boss8",true);

        assertEquals("1",saved.getExceptionAllowed());
        assertEquals("临时跨项目支援",saved.getExceptionReason());
        verify(mapper).insertProjectStaffAllocation(saved);
    }

    @Test
    void oneProjectAllocationCannotExceedOneHundredPercent()
    {
        BusinessProject project = project(83L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(83L)).thenReturn(project);
        when(mapper.selectMemberRole(83L, 9L)).thenReturn("OWNER");
        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setProjectId(83L); allocation.setUserId(9L); allocation.setAllocationMode("PERCENTAGE");
        allocation.setAllocationValue(new BigDecimal("101")); allocation.setEffectiveFrom(new Date());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffAllocation(allocation, 9L, "owner9", false));

        assertTrue(error.getMessage().contains("不能超过100%"));
        verify(mapper, never()).selectEffectiveStaffCostPolicy(any(), any());
    }

    @Test
    void projectOwnerCanSetMemberPlannedEffortWithoutSeeingRawCost()
    {
        BusinessProject project = project(90L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setPolicyId(31L); policy.setUserId(147L);
        Map<String,Object> staff = new HashMap<String,Object>();
        staff.put("nickName", "石头");
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectMemberRole(90L, 147L)).thenReturn("MEMBER");
        when(mapper.selectEffectiveStaffCostPolicy(any(), any())).thenReturn(policy);
        when(mapper.sumOverlappingAllocationPercent(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);

        BusinessProjectStaffAllocation allocation = new BusinessProjectStaffAllocation();
        allocation.setProjectId(90L); allocation.setUserId(147L); allocation.setAllocationMode("PERCENTAGE");
        allocation.setAllocationValue(new BigDecimal("60")); allocation.setEffectiveFrom(new Date());

        BusinessProjectStaffAllocation saved = service.saveStaffAllocation(allocation, 9L, "owner9", false);

        assertEquals(31L, saved.getCostPolicyId());
        assertEquals("石头", saved.getUserName());
        verify(mapper).insertProjectStaffAllocation(saved);
        verify(accountingService).recalculatePersonnelCost(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq("owner9"));
    }

    @Test
    void employeeOnlyReportsDeviationAgainstOwnEffectivePlan()
    {
        when(mapper.selectProjectById(90L)).thenReturn(project(90L, 9L, "ACTIVE", "APPROVED"));
        Map<String,Object> plan = new HashMap<String,Object>();
        plan.put("projectId", 90L); plan.put("plannedPercent", new BigDecimal("60"));
        when(mapper.selectMyEfforts(147L, new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date())))
            .thenReturn(Collections.singletonList(plan));
        when(mapper.sumUserEffectiveEffortExcludingProject(org.mockito.ArgumentMatchers.eq(147L), any(),
            org.mockito.ArgumentMatchers.eq(90L))).thenReturn(new BigDecimal("20"));
        Map<String,Object> staff = new HashMap<String,Object>(); staff.put("nickName", "石头");
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        BusinessProjectEffort stored = new BusinessProjectEffort();
        stored.setProjectId(90L); stored.setUserId(147L); stored.setActualPercent(new BigDecimal("50"));
        when(mapper.selectEffortReport(org.mockito.ArgumentMatchers.eq(90L),org.mockito.ArgumentMatchers.eq(147L),any()))
            .thenReturn(null,stored);
        BusinessProjectEffort effort = new BusinessProjectEffort();
        effort.setProjectId(90L); effort.setBizDate(new Date()); effort.setActualPercent(new BigDecimal("50"));
        effort.setDeviationReason("临时支援另一个项目");

        BusinessProjectEffort result = service.saveMyEffort(effort,147L,"shitou");

        assertEquals(stored,result);
        assertEquals(new BigDecimal("60"),effort.getPlannedPercent());
        assertEquals("石头",effort.getUserName());
        verify(mapper).upsertEffortReport(effort);
    }

    @Test
    void projectOwnerWeekConfirmationRecalculatesDailyAccounting()
    {
        BusinessProject project = project(90L,9L,"ACTIVE","APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectMemberRole(90L,9L)).thenReturn("OWNER");
        when(mapper.confirmProjectEffortDay(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq(9L),org.mockito.ArgumentMatchers.eq("owner9"))).thenReturn(1);
        when(mapper.selectProjectEffortWeek(org.mockito.ArgumentMatchers.eq(90L),any(),any()))
            .thenReturn(Collections.<Map<String,Object>>emptyList());

        service.confirmProjectEffortWeek(90L,
            new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()),9L,"owner9",false);

        verify(mapper,atLeastOnce()).confirmProjectEffortDay(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq(9L),org.mockito.ArgumentMatchers.eq("owner9"));
        verify(accountingService,atLeastOnce()).recalculatePersonnelCost(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq("owner9"));
    }

    @Test
    void employeeOnLeaveCannotSubmitEffort()
    {
        when(mapper.selectProjectById(90L)).thenReturn(project(90L,9L,"ACTIVE","APPROVED"));
        Map<String,Object> plan = new HashMap<String,Object>();
        plan.put("projectId",90L); plan.put("plannedPercent",new BigDecimal("20"));
        plan.put("reportStatus","LEAVE");
        when(mapper.selectMyEfforts(org.mockito.ArgumentMatchers.eq(147L),any()))
            .thenReturn(Collections.singletonList(plan));
        BusinessProjectEffort effort = new BusinessProjectEffort();
        effort.setProjectId(90L); effort.setBizDate(new Date()); effort.setActualPercent(BigDecimal.ZERO);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveMyEffort(effort,147L,"shitou"));

        assertTrue(error.getMessage().contains("已登记请假"));
        verify(mapper,never()).upsertEffortReport(any());
    }

    @Test
    void projectOwnerConfirmsOneMembersTodayEffort()
    {
        BusinessProject project = project(90L,9L,"ACTIVE","APPROVED");
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectMemberRole(90L,9L)).thenReturn("OWNER");
        BusinessProjectEffort submitted = new BusinessProjectEffort();
        submitted.setProjectId(90L); submitted.setUserId(147L); submitted.setUserName("石头");
        submitted.setActualPercent(new BigDecimal("30")); submitted.setReportStatus("SUBMITTED");
        BusinessProjectEffort confirmed = new BusinessProjectEffort(); confirmed.setReportStatus("CONFIRMED");
        when(mapper.selectEffortReport(org.mockito.ArgumentMatchers.eq(90L),
            org.mockito.ArgumentMatchers.eq(147L),any())).thenReturn(submitted,confirmed);
        when(mapper.confirmProjectMemberEffort(org.mockito.ArgumentMatchers.eq(90L),
            org.mockito.ArgumentMatchers.eq(147L),any(),org.mockito.ArgumentMatchers.eq(9L),
            org.mockito.ArgumentMatchers.eq("owner9"))).thenReturn(1);

        BusinessProjectEffort result = service.confirmMemberEffort(90L,147L,new Date(),9L,"owner9",false);

        assertEquals("CONFIRMED",result.getReportStatus());
        verify(accountingService).recalculatePersonnelCost(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq("owner9"));
        verify(mapper).insertEvent(any());
    }

    @Test
    void projectOwnerReturnsOneMembersTodayEffortWithReason()
    {
        BusinessProject project = project(90L,9L,"ACTIVE","APPROVED");
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectMemberRole(90L,9L)).thenReturn("OWNER");
        BusinessProjectEffort submitted = new BusinessProjectEffort();
        submitted.setProjectId(90L); submitted.setUserId(147L); submitted.setUserName("石头");
        submitted.setActualPercent(new BigDecimal("30")); submitted.setReportStatus("SUBMITTED");
        BusinessProjectEffort returned = new BusinessProjectEffort(); returned.setReportStatus("RETURNED");
        when(mapper.selectEffortReport(org.mockito.ArgumentMatchers.eq(90L),
            org.mockito.ArgumentMatchers.eq(147L),any())).thenReturn(submitted,returned);
        when(mapper.returnProjectMemberEffort(org.mockito.ArgumentMatchers.eq(90L),
            org.mockito.ArgumentMatchers.eq(147L),any(),org.mockito.ArgumentMatchers.eq("请核对投入比例"),
            org.mockito.ArgumentMatchers.eq("owner9"))).thenReturn(1);

        BusinessProjectEffort result = service.returnMemberEffort(90L,147L,new Date(),"请核对投入比例",
            9L,"owner9",false);

        assertEquals("RETURNED",result.getReportStatus());
        verify(accountingService,never()).recalculatePersonnelCost(any(),any(),any());
        verify(mapper).insertEvent(any());
    }

    private BusinessStaffCostPolicy staffCostPolicy(Long policyId, Long userId, String effectiveFrom,
        String status, int referenceCount)
    {
        BusinessStaffCostPolicy policy = new BusinessStaffCostPolicy();
        policy.setPolicyId(policyId);
        policy.setUserId(userId);
        policy.setEffectiveFrom(java.sql.Date.valueOf(effectiveFrom));
        policy.setStatus(status);
        policy.setReferenceCount(referenceCount);
        return policy;
    }

    @Test
    void separatedDeliveryClosesAccountingAndFreezesInTheSameTransaction()
    {
        BusinessProject p = project(900L, 9L, "ACTIVE", "APPROVED");
        p.setSponsorOwnerUserId(8L);
        p.setDeliveryPolicyVersion("SEPARATED_V1"); p.setAccountingState("OPEN");
        when(mapper.selectProjectById(900L)).thenReturn(p);
        when(mapper.selectTasks(900L)).thenReturn(Collections.singletonList(completedTask("交付")));
        when(kpiMapper.selectPlanSummaries(900L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(900L, "ACTIVE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);
        when(mapper.closeAccounting(900L, 1, "boss8")).thenReturn(1);
        service.transition(900L, "CLOSE", "完成交付并冻结", 8L, "boss8", true);
        verify(kpiMapper).selectPlanSummaries(900L);
        verify(accountingService, never()).ensureProjectCanClose(900L);
        verify(accountingService).closeProjectAccounting(eq(900L), any(Date.class), eq("boss8"));
        verify(mapper).closeAccounting(900L, 1, "boss8");
        verify(mapper).closeProjectAllocations(eq(900L), any(Date.class), eq("boss8"));
        verify(mapper).selectProjectByIdForUpdate(900L);
    }

    @Test
    void globalProjectWeightsMustTotalExactlyOneHundredPercent()
    {
        Map<String,Object> staff=new HashMap<String,Object>();staff.put("nickName","成员十一");
        Map<String,Object> first=new HashMap<String,Object>();first.put("projectId",91L);first.put("projectName","项目甲");first.put("ownerUserId",9L);first.put("allocationId",1L);first.put("allocationVersion",0);first.put("allocationValue",new BigDecimal("60"));
        Map<String,Object> second=new HashMap<String,Object>();second.put("projectId",92L);second.put("projectName","项目乙");second.put("ownerUserId",12L);second.put("allocationId",2L);second.put("allocationVersion",0);second.put("allocationValue",new BigDecimal("40"));
        when(mapper.selectActiveUserById(11L)).thenReturn(staff);
        when(mapper.selectUserAllocationWorkspace(eq(11L),any(Date.class))).thenReturn(Arrays.asList(first,second));
        Map<String,Object> current=service.staffAllocationWorkspace(11L,java.sql.Date.valueOf("2026-09-11"),9L,false);
        Map<String,Object> body=new LinkedHashMap<String,Object>();body.put("userId",11L);body.put("effectiveDate","2026-09-11");body.put("reason","调整并行项目投入");body.put("versionToken",current.get("versionToken"));
        body.put("allocations",Arrays.asList(row("projectId",91L,"allocationValue",60),row("projectId",92L,"allocationValue",30)));

        ServiceException error=assertThrows(ServiceException.class,()->service.saveStaffAllocationWorkspace(body,9L,"owner9",false));

        assertTrue(error.getMessage().contains("合计必须等于100%"));
        verify(mapper,never()).insertProjectStaffAllocation(any());
    }

    @Test
    void ownerCanSaveACompleteGlobalWeightDistributionAndRepriceBothProjects()
    {
        Map<String,Object> staff=new HashMap<String,Object>();staff.put("nickName","成员十一");
        Map<String,Object> first=new HashMap<String,Object>();first.put("projectId",93L);first.put("projectName","项目甲");first.put("ownerUserId",9L);first.put("allocationId",1L);first.put("allocationVersion",0);first.put("allocationValue",new BigDecimal("100"));
        Map<String,Object> second=new HashMap<String,Object>();second.put("projectId",94L);second.put("projectName","项目乙");second.put("ownerUserId",12L);second.put("allocationId",2L);second.put("allocationVersion",0);second.put("allocationValue",BigDecimal.ZERO);
        BusinessProject projectA=project(93L,9L,"ACTIVE","APPROVED");projectA.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        second.put("ownerUserId",9L);
        BusinessProject projectB=project(94L,9L,"ACTIVE","APPROVED");projectB.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectActiveUserById(11L)).thenReturn(staff);
        when(mapper.selectUserAllocationWorkspace(eq(11L),any(Date.class))).thenReturn(Arrays.asList(first,second));
        when(mapper.selectProjectById(93L)).thenReturn(projectA);when(mapper.selectProjectById(94L)).thenReturn(projectB);
        Map<String,Object> current=service.staffAllocationWorkspace(11L,java.sql.Date.valueOf("2026-09-11"),9L,false);
        Map<String,Object> body=new LinkedHashMap<String,Object>();body.put("userId",11L);body.put("effectiveDate","2026-09-11");body.put("reason","调整并行项目投入");body.put("versionToken",current.get("versionToken"));
        body.put("allocations",Arrays.asList(row("projectId",93L,"allocationValue",60),row("projectId",94L,"allocationValue",40)));

        service.saveStaffAllocationWorkspace(body,9L,"owner9",false);

        ArgumentCaptor<BusinessProjectStaffAllocation> saved=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper,times(2)).insertProjectStaffAllocation(saved.capture());
        assertEquals(Arrays.asList(new BigDecimal("60"),new BigDecimal("40")),Arrays.asList(saved.getAllValues().get(0).getAllocationValue(),saved.getAllValues().get(1).getAllocationValue()));
        verify(memberDays).synchronizeAllocationChange(93L,java.sql.Date.valueOf("2026-09-11"),"owner9");
        verify(memberDays).synchronizeAllocationChange(94L,java.sql.Date.valueOf("2026-09-11"),"owner9");
    }

    @Test
    void proposalAllocationPlanAddsNewProjectAtRequestedRatio()
    {
        Date effective=java.sql.Date.valueOf("2026-09-11");
        Map<String,Object> previous=row("projectId",93L,"projectNo","XM93","projectName","既有项目",
            "ownerUserId",9L,"ownerName","负责人九","allocationId",1L,"allocationVersion",0,
            "allocationValue",new BigDecimal("60"),"confirmationStatus","CONFIRMED","allocationHistoryToken","1:1:0");
        Map<String,Object> createdRow=row("projectId",94L,"projectNo","XM94","projectName","本次立项",
            "ownerUserId",9L,"ownerName","负责人九","allocationId",null,"allocationVersion",null,
            "allocationValue",BigDecimal.ZERO,"confirmationStatus",null,"allocationHistoryToken","1:1:0");
        when(mapper.selectActiveUserById(11L)).thenReturn(row("nickName","成员十一"));
        when(mapper.selectUserAllocationWorkspace(eq(11L),any(Date.class))).thenReturn(Arrays.asList(previous,createdRow));
        BusinessProject oldProject=project(93L,9L,"ACTIVE","APPROVED");oldProject.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        BusinessProject newProject=project(94L,9L,"ACTIVE","APPROVED");newProject.setProjectName("本次立项");newProject.setMainOwnerName("负责人九");newProject.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        when(mapper.selectProjectById(93L)).thenReturn(oldProject);when(mapper.selectProjectById(94L)).thenReturn(newProject);
        Map<String,Object> plan=row("effectiveDate","2026-09-11","versionToken","93:1:0:9:60:CONFIRMED:1:1:0;",
            "reason","立项时重新安排投入","allocations",Collections.singletonList(row("projectId",93L,"allocationValue",60)));
        Map<String,Object> line=row("userId",11L,"inputQuantity",40,"allocationPlan",plan);
        BusinessProjectProposal proposal=new BusinessProjectProposal();proposal.setStaffingLines(Collections.singletonList(line));
        BusinessProjectMember member=new BusinessProjectMember();member.setUserId(11L);member.setUserNameSnapshot("成员十一");member.setMemberRole("MEMBER");member.setJoinedDate(java.sql.Date.valueOf("2026-09-01"));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"applyProposalProjectWeight",
            newProject,member,proposal,"owner9",true);

        ArgumentCaptor<BusinessProjectStaffAllocation> saved=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(mapper).insertProjectStaffAllocation(saved.capture());
        assertEquals(94L,saved.getValue().getProjectId());assertEquals(new BigDecimal("40"),saved.getValue().getAllocationValue());
        verify(memberDays).synchronizeAllocationChange(94L,effective,"负责人九");
    }

    @Test
    void separatedProjectSettlementPreviewAndConfirmationUseOneStepClose()
    {
        BusinessProject p = project(910L, 9L, "ACTIVE", "APPROVED");
        p.setSponsorOwnerUserId(8L); p.setDeliveryPolicyVersion("SEPARATED_V1"); p.setAccountingState("OPEN");
        when(mapper.selectProjectById(910L)).thenReturn(p);
        when(mapper.selectTasks(910L)).thenReturn(Collections.singletonList(completedTask("最终交付")));
        when(kpiMapper.selectPlanSummaries(910L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(910L, "ACTIVE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);
        when(mapper.closeAccounting(910L, 1, "boss8")).thenReturn(1);

        assertEquals(true, service.settlementStatus(910L, 8L, false, true).get("canClose"));
        Map<String,Object> result = service.closeAccounting(910L, 0, "确认最终金额", 8L, "boss8", true);

        assertEquals("CLOSED", result.get("status"));
        assertEquals("CLOSED", result.get("accountingState"));
        assertEquals(2, result.get("version"));
        verify(accountingService).closeProjectAccounting(eq(910L), any(Date.class), eq("boss8"));
        verify(mapper).closeAccounting(910L, 1, "boss8");
    }

    @Test
    void explicitDeliveryEndKeepsAccountingOpenUntilFinalMonthCostsSettle()
    {
        BusinessProject p=projectAwaitingPublicCosts(920L,"SEPARATED_V1");
        when(mapper.updateProjectStatus(920L,"ACTIVE","CLOSED",null,false,"boss8",0)).thenReturn(1);
        Map<String,Object> before=service.settlementStatus(920L,8L,false,true);
        assertEquals(false,before.get("canClose"));assertEquals(true,before.get("canEndDeliveryAwaitingCosts"));
        Map<String,Object> ended=service.endDeliveryAwaitingCosts(920L,0,"交付完成，等待九月账单",null,false,false,8L,"boss8",true);
        assertEquals("CLOSED",ended.get("status"));assertEquals("OPEN",ended.get("accountingState"));assertEquals(1,ended.get("version"));assertEquals(true,ended.get("deliveryAwaitingCosts"));
        assertEquals(com.ruoyi.common.utils.DateUtils.getDate(),ended.get("actualEndDate"));
        verify(mapper).closeProjectRoutines(920L,"boss8");verify(mapper).closeProjectWorkPeriods(920L,"boss8");
        verify(mapper).closeProjectAllocations(eq(920L),any(Date.class),eq("boss8"));
        verify(accountingService,never()).closeProjectAccounting(any(),any(),any());
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(920L,0,"重复提交",null,false,false,8L,"boss8",true));
        // Simulate final month costs settling after the calendar has advanced: the saved delivery date is reused.
        Date savedEnd=java.sql.Date.valueOf("2025-09-30");p.setActualEndDate(savedEnd);
        when(publicExpenses.countProjectPending(920L)).thenReturn(0);when(mapper.closeAccounting(920L,1,"boss8")).thenReturn(1);
        Map<String,Object> closed=service.closeAccounting(920L,1,"九月费用已月结",8L,"boss8",true);
        assertEquals("CLOSED",closed.get("accountingState"));verify(accountingService).closeProjectAccounting(920L,savedEnd,"boss8");
        verify(mapper,times(1)).updateProjectStatus(920L,"ACTIVE","CLOSED",null,false,"boss8",0);
    }

    @Test
    void legacyDeliverySeparationNeedsExplicitConsentAndPreservesOtherPolicies()
    {
        BusinessProject p=projectAwaitingPublicCosts(921L,"LEGACY_V1");p.setCostPolicyVersion("PERCENTAGE_V1");p.setSettlementPolicyVersion("LEGACY_V1");p.setBudgetMode("DAILY");
        Map<String,Object> before=service.settlementStatus(921L,8L,false,true);
        assertEquals(true,before.get("canEndDeliveryAwaitingCosts"));assertEquals(true,before.get("requiresLegacyDeliverySeparation"));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(921L,0,"结束交付",null,false,false,8L,"boss8",true));
        verify(mapper,never()).separateDeliveryForPublicCosts(any(),any(),any());
        when(mapper.separateDeliveryForPublicCosts(921L,0,"boss8")).thenReturn(1);
        when(mapper.updateProjectStatus(921L,"ACTIVE","CLOSED",null,false,"boss8",1)).thenReturn(1);
        Map<String,Object> ended=service.endDeliveryAwaitingCosts(921L,0,"确认分开办理",null,false,true,8L,"boss8",true);
        assertEquals("SEPARATED_V1",ended.get("deliveryPolicyVersion"));assertEquals("OPEN",ended.get("accountingState"));assertEquals(2,ended.get("version"));
        assertEquals("PERCENTAGE_V1",p.getCostPolicyVersion());assertEquals("LEGACY_V1",p.getSettlementPolicyVersion());assertEquals("DAILY",p.getBudgetMode());
        verify(accountingService,never()).ensureProjectCanClose(921L);verify(accountingService,never()).closeProjectAccounting(any(),any(),any());
    }

    @Test
    void resultAcceptanceNeedsExplicitApprovalOfTheCurrentSubmissionBeforeDeliveryEnds()
    {
        BusinessProject p=projectAwaitingPublicCosts(922L,"SEPARATED_V1");p.setStatus("ACCEPTANCE");p.setCloseMethod("RESULT_ACCEPTANCE");
        BusinessProjectAcceptance pending=new BusinessProjectAcceptance();pending.setAcceptanceId(9220L);
        when(mapper.selectLatestPendingAcceptance(922L)).thenReturn(pending);
        assertEquals(true,service.settlementStatus(922L,8L,false,true).get("canEndDeliveryAwaitingCosts"));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(922L,0,"资料已核对",9220L,false,false,8L,"boss8",true));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(922L,0,"资料已核对",9210L,true,false,8L,"boss8",true));
        verify(mapper,never()).reviewAcceptance(any(),any(),any(),any(),any(),any());
        when(mapper.selectActiveUserById(8L)).thenReturn(row("nickName","老板八"));
        when(mapper.reviewAcceptance(9220L,"APPROVED",8L,"老板八","资料已核对","boss8")).thenReturn(1);
        when(mapper.updateProjectStatus(922L,"ACCEPTANCE","CLOSED",null,false,"boss8",0)).thenReturn(1);
        Map<String,Object> ended=service.endDeliveryAwaitingCosts(922L,0,"资料已核对",9220L,true,false,8L,"boss8",true);
        assertEquals("OPEN",ended.get("accountingState"));verify(mapper).reviewAcceptance(9220L,"APPROVED",8L,"老板八","资料已核对","boss8");
        verify(accountingService,never()).closeProjectAccounting(any(),any(),any());
    }

    @Test
    void endingDeliveryStillRequiresCurrentSponsorAndVersion()
    {
        BusinessProject p=project(923L,9L,"ACTIVE","APPROVED");p.setSponsorOwnerUserId(8L);p.setDeliveryPolicyVersion("SEPARATED_V1");p.setAccountingState("OPEN");
        when(mapper.selectProjectById(923L)).thenReturn(p);
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(923L,0,"结束交付",null,false,false,9L,"owner",false));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(923L,0,"结束交付",null,false,false,1L,"admin",true));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(923L,2,"结束交付",null,false,false,8L,"boss8",true));
        verify(mapper,never()).updateProjectStatus(any(),any(),any(),any(),any(Boolean.class),any(),any());
    }

    @Test
    void deliveryEndCannotBypassUnconfirmedKpiOrUnfinishedTasksOrPendingFacts()
    {
        projectAwaitingPublicCosts(924L,"SEPARATED_V1");
        when(kpiMapper.selectPlanSummaries(924L)).thenReturn(Collections.singletonList(publishedKpiPlan("PENDING")));
        assertEquals(false,service.settlementStatus(924L,8L,false,true).get("canEndDeliveryAwaitingCosts"));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(924L,0,"提前结束",null,false,false,8L,"boss8",true));
        when(kpiMapper.selectPlanSummaries(924L)).thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        BusinessProjectTask unfinished=completedTask("未完成交付");unfinished.setStatus("DOING");unfinished.setProgress(50);when(mapper.selectTasks(924L)).thenReturn(Collections.singletonList(unfinished));
        assertEquals(false,service.settlementStatus(924L,8L,false,true).get("canEndDeliveryAwaitingCosts"));
        when(mapper.selectTasks(924L)).thenReturn(Collections.singletonList(completedTask("已完成")));when(accountingMapper.countProjectUnsettledFacts(924L)).thenReturn(1);
        assertEquals(false,service.settlementStatus(924L,8L,false,true).get("canEndDeliveryAwaitingCosts"));
        verify(accountingService,never()).recalculatePersonnelCost(any(),any(),any());
    }

    @Test
    void stagedDeliveryCannotEndBeforeOwnerRequestsClosure()
    {
        BusinessProject p=projectAwaitingPublicCosts(925L,"SEPARATED_V1");p.setCloseMethod("STAGED_ACCEPTANCE");
        assertEquals(false,service.settlementStatus(925L,8L,false,true).get("canEndDeliveryAwaitingCosts"));
        assertThrows(ServiceException.class,()->service.endDeliveryAwaitingCosts(925L,0,"阶段交付",null,false,false,8L,"boss8",true));
    }

    private BusinessProject projectAwaitingPublicCosts(Long projectId,String policy)
    {
        BusinessProject p=project(projectId,9L,"ACTIVE","APPROVED");p.setSponsorOwnerUserId(8L);p.setDeliveryPolicyVersion(policy);p.setAccountingState("OPEN");
        when(mapper.selectProjectById(projectId)).thenReturn(p);
        lenient().when(mapper.selectTasks(projectId)).thenReturn(Collections.singletonList(completedTask("最终交付")));
        lenient().when(kpiMapper.selectPlanSummaries(projectId)).thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(publicExpenses.countProjectPending(projectId)).thenReturn(1);return p;
    }

    @Test
    void separatedResultAcceptanceApprovalAlsoFinalizesAccounting()
    {
        BusinessProject p = project(911L, 9L, "ACCEPTANCE", "APPROVED");
        p.setCloseMethod("RESULT_ACCEPTANCE"); p.setSponsorOwnerUserId(8L);
        p.setDeliveryPolicyVersion("SEPARATED_V1"); p.setAccountingState("OPEN");
        BusinessProjectAcceptance pending = new BusinessProjectAcceptance(); pending.setAcceptanceId(9110L);
        Map<String,Object> boss = new HashMap<String,Object>(); boss.put("nickName", "老板八");
        when(mapper.selectProjectById(911L)).thenReturn(p);
        when(mapper.selectLatestPendingAcceptance(911L)).thenReturn(pending);
        when(mapper.selectTasks(911L)).thenReturn(Collections.singletonList(completedTask("成果交付")));
        when(kpiMapper.selectPlanSummaries(911L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.selectActiveUserById(8L)).thenReturn(boss);
        when(mapper.reviewAcceptance(9110L, "APPROVED", 8L, "老板八", "验收通过", "boss8")).thenReturn(1);
        when(mapper.updateProjectStatus(911L, "ACCEPTANCE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);
        when(mapper.closeAccounting(911L, 1, "boss8")).thenReturn(1);

        BusinessProject result = service.reviewAcceptance(911L, "APPROVED", "验收通过", 8L, "boss8", true);

        assertEquals("CLOSED", result.getStatus()); assertEquals("CLOSED", result.getAccountingState());
        verify(accountingService).closeProjectAccounting(eq(911L), any(Date.class), eq("boss8"));
        verify(mapper).closeAccounting(911L, 1, "boss8");
    }

    @Test
    void separatedCancellationAlsoLeavesAccountingOpen()
    {
        BusinessProject p = project(900L, 9L, "ACTIVE", "APPROVED");
        p.setSponsorOwnerUserId(8L); p.setDeliveryPolicyVersion("SEPARATED_V1"); p.setAccountingState("OPEN");
        when(mapper.selectProjectById(900L)).thenReturn(p);
        when(mapper.updateProjectStatus(900L, "ACTIVE", "CANCELED", null, false, "boss8", 0)).thenReturn(1);
        service.transition(900L, "CANCEL", "停止交付", 8L, "boss8", true);
        verify(accountingService, never()).closeProjectAccounting(anyLong(), any(Date.class), any(String.class));
        verify(mapper).cancelOpenProjectTasks(900L, "boss8");
    }

    @Test
    void sponsorCanCloseSeparatedAccountingAfterDeliveryWithNoRequiredKpi()
    {
        BusinessProject p = separatedClosedProject();
        when(mapper.selectProjectById(901L)).thenReturn(p);
        when(mapper.closeAccounting(901L, 0, "boss8")).thenReturn(1);
        Map<String, Object> result = service.closeAccounting(901L, 0, "所有成本已核对", 8L, "boss8", true);
        assertEquals("CLOSED", result.get("accountingState"));
        assertEquals(1, result.get("version"));
        verify(accountingService).closeProjectAccounting(901L, p.getActualEndDate(), "boss8");
        verify(mapper).insertEvent(any(Map.class));
    }

    @Test
    void pendingKpiOrFactBlocksIndependentAccountingClose()
    {
        BusinessProject p = separatedClosedProject();
        when(mapper.selectProjectById(901L)).thenReturn(p);
        when(mapper.countPendingProjectKpi(901L)).thenReturn(1);
        when(accountingMapper.countProjectUnsettledFacts(901L)).thenReturn(2);
        Map<String, Object> status = service.settlementStatus(901L, 8L, false, true);
        assertEquals(false, status.get("canClose"));
        assertEquals(1, status.get("pendingKpiCount")); assertEquals(2, status.get("pendingFactCount"));
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "关闭", 8L, "boss8", true));
        verify(accountingService, never()).closeProjectAccounting(anyLong(), any(Date.class), any(String.class));
    }

    @Test
    void ownerOtherBossAndTechnicalAdminCannotSignOffAccounting()
    {
        when(mapper.selectProjectById(901L)).thenReturn(separatedClosedProject());
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "关闭", 9L, "owner", false));
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "关闭", 7L, "other", true));
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "关闭", 1L, "admin", true));
        verify(mapper, never()).closeAccounting(anyLong(), any(Integer.class), any(String.class));
    }

    @Test
    void staleOrRepeatedCloseDoesNotWriteAgain()
    {
        BusinessProject p = separatedClosedProject();
        when(mapper.selectProjectById(901L)).thenReturn(p);
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 2, "关闭", 8L, "boss8", true));
        p.setAccountingState("CLOSED");
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "再次关闭", 8L, "boss8", true));
        verify(accountingService, never()).closeProjectAccounting(anyLong(), any(Date.class), any(String.class));
    }

    @Test
    void legacyTerminalAccountingCannotBeReopenedOrClosedByNewEndpoint()
    {
        BusinessProject p = separatedClosedProject(); p.setDeliveryPolicyVersion("LEGACY_V1");
        p.setAccountingState(null);
        when(mapper.selectProjectById(901L)).thenReturn(p);
        assertEquals("CLOSED", service.settlementStatus(901L, 8L, false, true).get("accountingState"));
        assertThrows(ServiceException.class, () -> service.closeAccounting(901L, 0, "关闭", 8L, "boss8", true));
        verify(mapper, never()).closeAccounting(anyLong(), any(Integer.class), any(String.class));
    }

    @Test
    void settlementNoLongerExposesLocalLeaveApprovalBlockers()
    {
        when(mapper.selectProjectById(901L)).thenReturn(separatedClosedProject());
        Map<String,Object> status=service.settlementStatus(901L,8L,false,true);
        assertEquals(true,status.get("canClose"));
        assertEquals(false,status.containsKey("pendingLeaveCount"));
        assertTrue(((List<?>)status.get("blockers")).isEmpty());
    }

    @Test
    void deletingParentChecksChildrenEvenWhenTheyAreNotVisibleToOperator()
    {
        BusinessProject parent = project(15L, 9L, "ACTIVE", "APPROVED");
        parent.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(15L)).thenReturn(parent);
        when(mapper.countSubprojects(15L)).thenReturn(2);
        assertEquals("该项目包含子项目，请先删除所有子项目，再删除主项目",
            assertThrows(ServiceException.class, () -> service.deleteProject(15L, 1L, "admin", true)).getMessage());
        verify(mapper, never()).softDeleteProject(anyLong(), any(), any());
    }

    @Test
    void deletingChildOnlySoftDeletesRequestedProjectAndChecksVersion()
    {
        BusinessProject child = project(16L, 9L, "DRAFT", "DRAFT");
        child.setParentId(15L); child.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(16L)).thenReturn(child);
        when(mapper.softDeleteProject(16L, 0, "admin")).thenReturn(1);
        service.deleteProject(16L, 1L, "admin", true);
        verify(mapper).softDeleteProject(16L, 0, "admin");
        verify(mapper, never()).softDeleteProject(eq(15L), any(), any());
    }

    @Test
    void unrelatedBossCannotCreateOrDeleteProjects()
    {
        BusinessProject parent = project(15L, 9L, "ACTIVE", "APPROVED");
        parent.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(15L)).thenReturn(parent);
        assertThrows(ServiceException.class, () -> service.validateSubprojectParent(15L, 8L, 7L));
        assertThrows(ServiceException.class, () -> service.deleteProject(15L, 7L, "other", true));
        verify(mapper, never()).insertProject(any());
        verify(mapper, never()).softDeleteProject(anyLong(), any(), any());
    }

    @Test
    void ownerDeletionRequiresAdministratorReviewAndKeepsProjectUntilApproval()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(15L)).thenReturn(project);
        assertThrows(ServiceException.class, () -> service.deleteProject(15L, 9L, "owner", false));
        service.requestProjectDeletion(15L, "项目不再需要", 9L, "owner");
        verify(mapper).insertProjectDeletionRequest(any());
        verify(mapper, never()).softDeleteProject(anyLong(), any(), any());
        assertThrows(ServiceException.class, () -> service.requestProjectDeletion(15L, "原因", 7L, "other"));

        Map<String, Object> pending = new HashMap<>();
        pending.put("requestId", 21L); pending.put("projectId", 15L); pending.put("requestUserId", 9L); pending.put("status", "PENDING");
        when(mapper.selectPendingProjectDeletion(15L)).thenReturn(pending);
        assertThrows(ServiceException.class, () -> service.requestProjectDeletion(15L, "再次申请", 9L, "owner"));
        when(mapper.selectProjectDeletionById(21L)).thenReturn(pending);
        assertThrows(ServiceException.class, () -> service.reviewProjectDeletion(21L, "APPROVED", "", 9L, "owner", false));
        when(mapper.softDeleteProject(15L, 0, "admin")).thenReturn(1);
        when(mapper.reviewProjectDeletionRequest(21L, "APPROVED", "", 1L, "admin")).thenReturn(1);
        when(mapper.insertProjectDeletionNotification(21L, 9L)).thenReturn(1);
        service.reviewProjectDeletion(21L, "APPROVED", "", 1L, "admin", true);
        verify(mapper).softDeleteProject(15L, 0, "admin");
        verify(mapper).reviewProjectDeletionRequest(21L, "APPROVED", "", 1L, "admin");
        verify(mapper).insertProjectDeletionNotification(21L, 9L);
    }

    @Test
    void rejectingDeletionKeepsProjectAndRequiresReason()
    {
        Map<String, Object> pending = new HashMap<>();
        pending.put("requestId", 22L); pending.put("projectId", 15L); pending.put("requestUserId", 9L); pending.put("status", "PENDING");
        when(mapper.selectProjectDeletionById(22L)).thenReturn(pending);
        when(mapper.selectProjectById(15L)).thenReturn(project(15L, 9L, "ACTIVE", "APPROVED"));
        when(mapper.selectPendingProjectDeletion(15L)).thenReturn(pending);
        assertThrows(ServiceException.class, () -> service.reviewProjectDeletion(22L, "REJECTED", "", 1L, "admin", true));
        when(mapper.reviewProjectDeletionRequest(22L, "REJECTED", "保留项目", 1L, "admin")).thenReturn(1);
        when(mapper.insertProjectDeletionNotification(22L, 9L)).thenReturn(1);
        service.reviewProjectDeletion(22L, "REJECTED", "保留项目", 1L, "admin", true);
        verify(mapper, never()).softDeleteProject(anyLong(), any(), any());
        verify(mapper).insertProjectDeletionNotification(22L, 9L);
    }

    @Test
    void oldOwnerRequestCannotDeleteProjectAfterOwnerChanges()
    {
        Map<String, Object> pending = new HashMap<>();
        pending.put("requestId", 23L); pending.put("projectId", 15L);
        pending.put("requestUserId", 8L); pending.put("status", "PENDING");
        when(mapper.selectProjectDeletionById(23L)).thenReturn(pending);
        when(mapper.selectProjectById(15L)).thenReturn(project(15L, 9L, "ACTIVE", "APPROVED"));
        assertThrows(ServiceException.class, () -> service.reviewProjectDeletion(23L, "APPROVED", "", 1L, "admin", true));
        verify(mapper, never()).softDeleteProject(anyLong(), any(), any());
    }

    @Test
    void companyBossCanApproveButUnrelatedBossCannotAndSecondReviewIsRejected()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        Map<String, Object> pending = new HashMap<>();
        pending.put("requestId", 24L); pending.put("projectId", 15L);
        pending.put("requestUserId", 9L); pending.put("status", "PENDING");
        Map<String, Object> reviewed = new HashMap<>(pending);
        reviewed.put("status", "APPROVED");
        when(mapper.selectProjectDeletionById(24L)).thenReturn(pending, pending, reviewed);
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectPendingProjectDeletion(15L)).thenReturn(pending);
        assertThrows(ServiceException.class, () -> service.reviewProjectDeletion(24L, "APPROVED", "", 7L, "otherBoss", true));
        when(mapper.softDeleteProject(15L, 0, "boss8")).thenReturn(1);
        when(mapper.reviewProjectDeletionRequest(24L, "APPROVED", "", 8L, "boss8")).thenReturn(1);
        when(mapper.insertProjectDeletionNotification(24L, 9L)).thenReturn(1);
        service.reviewProjectDeletion(24L, "APPROVED", "", 8L, "boss8", true);
        assertThrows(ServiceException.class, () -> service.reviewProjectDeletion(24L, "APPROVED", "", 1L, "admin", true));
        verify(mapper).softDeleteProject(15L, 0, "boss8");
        verify(mapper, never()).softDeleteProject(15L, 0, "admin");
        verify(mapper).insertProjectDeletionNotification(24L, 9L);
    }

    @Test
    void subprojectRequiresExistingMainProjectAndCannotBeNestedAgain()
    {
        assertThrows(ServiceException.class, () -> service.validateSubprojectParent(null, 8L, 8L));
        BusinessProject child = project(16L, 9L, "ACTIVE", "APPROVED");
        child.setParentId(15L); child.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(16L)).thenReturn(child);
        assertThrows(ServiceException.class, () -> service.validateSubprojectParent(16L, 8L, 8L));
        verify(mapper, never()).insertProject(any());
    }

    @Test
    void editingChildCannotDetachItFromParent()
    {
        BusinessProject child = project(16L, 9L, "DRAFT", "DRAFT");
        child.setParentId(15L); child.setSponsorOwnerUserId(8L);
        when(mapper.selectProjectById(16L)).thenReturn(child);
        BusinessProject input = project(16L, 9L, "DRAFT", "DRAFT");
        assertEquals("归属主项目不可修改", assertThrows(ServiceException.class,
            () -> service.updateProject(input, 8L, "boss8", true)).getMessage());
        verify(mapper, never()).updateProject(any());
    }

    @Test
    void rootPageShowsParentListFieldsWithoutExposingDetailsOrFetchingChildren()
    {
        BusinessProject parent = project(15L, 9L, "ACTIVE", "APPROVED");
        parent.setProjectNo("XM-15"); parent.setCompanyName("公司A");
        parent.setSponsorOwnerName("老板A"); parent.setMainOwnerName("负责人A");
        parent.setManagementMode("STANDARD"); parent.setObjective("项目目标");
        parent.setBudgetLimit(new BigDecimal("1000"));
        parent.setContextOnly(true); parent.setMatchedChildId(16L);
        com.github.pagehelper.Page<BusinessProject> page = new com.github.pagehelper.Page<>(2, 10);
        page.setTotal(25); page.add(parent);
        when(mapper.selectProjectRoots(any())).thenReturn(page);
        List<BusinessProject> hierarchy = service.projectHierarchy(Collections.emptyMap(), 10L, false, false);
        BusinessProject context = hierarchy.get(0);
        assertTrue(context.isContextOnly()); assertEquals("XM-15", context.getProjectNo());
        assertEquals("公司A", context.getCompanyName()); assertEquals("老板A", context.getSponsorOwnerName());
        assertEquals("负责人A", context.getMainOwnerName()); assertEquals("STANDARD", context.getManagementMode());
        assertEquals("项目目标", context.getObjective());
        assertEquals(null, context.getMainOwnerUserId()); assertEquals(null, context.getBudgetLimit());
        assertEquals(16L, context.getMatchedChildId());
        assertTrue(hierarchy == page); assertEquals(25, page.getTotal());
        verify(mapper, never()).selectProjectList(any());
        verify(mapper, never()).selectProjectById(anyLong());
    }

    @Test
    void childOwnerStillCannotOpenParentDetailWithoutParentMembership()
    {
        BusinessProject parent = project(15L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(15L)).thenReturn(parent);
        assertThrows(ServiceException.class, () -> service.getProject(15L, 10L, false, false));
    }

    @Test
    void expandingParentLoadsOnlyItsScopedChildren()
    {
        BusinessProject parent = project(15L, 9L, "ACTIVE", "APPROVED");
        when(mapper.selectProjectById(15L)).thenReturn(parent);
        when(mapper.selectProjectList(any())).thenReturn(Collections.emptyList());
        service.projectChildren(15L, 10L, false, false);
        ArgumentCaptor<Map<String, Object>> query = mapCaptor();
        verify(mapper).selectProjectList(query.capture());
        assertEquals(15L, query.getValue().get("parentId")); assertEquals(10L, query.getValue().get("userId"));
        assertEquals(false, query.getValue().get("viewAll")); verify(mapper, never()).selectProjectRoots(any());
    }

    private BusinessProject separatedClosedProject()
    {
        BusinessProject p = project(901L, 9L, "CLOSED", "APPROVED");
        p.setSponsorOwnerUserId(8L); p.setDeliveryPolicyVersion("SEPARATED_V1"); p.setAccountingState("OPEN");
        p.setActualEndDate(java.sql.Date.valueOf("2026-09-01"));
        return p;
    }

    private Map<String,Object> row(Object... values)
    {
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        for(int i=0;i<values.length;i+=2)result.put(String.valueOf(values[i]),values[i+1]);
        return result;
    }

    private BusinessProject project(Long id, Long ownerId, String status, String baselineStatus)
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(id);
        project.setMainOwnerUserId(ownerId);
        project.setMainOwnerName("owner");
        project.setStatus(status);
        project.setBaselineStatus(baselineStatus);
        project.setManagementMode("LIGHT");
        project.setCloseMethod("DIRECT");
        project.setVersion(0);
        return project;
    }

    private Map<String, Object> publishedKpiPlan(String settlementStatus)
    {
        Map<String, Object> plan = new HashMap<String, Object>();
        plan.put("status", "CONFIRMED".equals(settlementStatus) ? "CLOSED" : "PUBLISHED");
        plan.put("cycleEnd", new Date());
        plan.put("settlementStatus", settlementStatus);
        return plan;
    }

    private BusinessProjectTask completedTask(String name)
    {
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskName(name);
        task.setStatus("DONE");
        task.setProgress(100);
        return task;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
