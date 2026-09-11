package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.BusinessProjectProposalMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectProposalServiceImplTest
{
    @Mock private BusinessProjectProposalMapper mapper;
    @Mock private BusinessProjectWorkMapper workMapper;
    @Mock private IBusinessProjectService projectService;
    @Mock private BusinessProjectBudgetService budgetService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private BusinessProjectProposalServiceImpl service;

    private BusinessProjectProposal proposal;

    @BeforeEach
    void setUp()
    {
        org.mockito.Mockito.lenient().when(workMapper.selectTemplate("LIGHT_V1")).thenReturn(BusinessProjectWorkServiceTest.row("templateVersion","LIGHT_V1","snapshotJson","{}","managementMode","LIGHT","closeMethod","DIRECT"));
        proposal = new BusinessProjectProposal();
        proposal.setProposalId(77L);
        proposal.setProjectName("越南直播增长");
        proposal.setApplicantUserId(9L);
        proposal.setApplicantName("申请人九");
        proposal.setSponsorOwnerUserId(23L);
        proposal.setSponsorOwnerName("审批老板");
        proposal.setCompanyDeptId(111L);
        proposal.setObjective("提升有效流水并形成可验收结果");
        proposal.setApplicationReason("需要组织人员和预算共同执行");
        proposal.setPlanStartDate(new Date());
        proposal.setPlanEndDate(new Date());
        proposal.setProjectType("LIVE");
        proposal.setAccountingMode("PROFIT");
        proposal.setManagementMode("SIMPLE");
        proposal.setPriority("MEDIUM");
        proposal.setBaseCurrency("CNY");
        proposal.setBudgetLimit(new BigDecimal("1000"));
        proposal.setNoBudget("0");
        proposal.setSubmissionVersion(1);
        proposal.setVersion(2);
    }

    @ParameterizedTest
    @CsvSource({"1,true", "9,true"})
    void staffOptionsExposeBudgetRatesToAdminAndProposalPlanner(Long userId,boolean visible)
    {
        when(mapper.selectActiveUser(userId)).thenReturn(BusinessProjectWorkServiceTest.row("userId",userId));
        when(mapper.selectCompany(111L)).thenReturn(BusinessProjectWorkServiceTest.row("deptId",111L));
        when(mapper.selectStaffOptions(eq(111L),any())).thenReturn(Collections.singletonList(
            BusinessProjectWorkServiceTest.row("userId",7L,"monthlyCost",new BigDecimal("21750"),"dailyCost",new BigDecimal("1000"),"costCurrency","CNY","costMode","MONTHLY")));
        Map<String,Object> staff=service.staffOptions(111L,"2026-09-01",userId).get(0);
        assertEquals(visible,staff.get("rawCostVisible"));
        assertEquals(visible,staff.containsKey("monthlyCost"));assertEquals(visible,staff.containsKey("dailyCost"));
        assertEquals(visible,staff.containsKey("costCurrency"));assertEquals(7L,staff.get("userId"));
        assertEquals(new BigDecimal("21750"),staff.get("monthlyCost"));
        assertEquals(new BigDecimal("1000"),staff.get("dailyCost"));
    }

    @Test
    void staffOptionsRequireSelectedValidCompany()
    {
        when(mapper.selectActiveUser(9L)).thenReturn(BusinessProjectWorkServiceTest.row("userId",9L));
        when(mapper.selectCompany(999L)).thenReturn(null);
        assertThrows(ServiceException.class,()->service.staffOptions(null,"2026-09-01",9L));
        assertThrows(ServiceException.class,()->service.staffOptions(999L,"2026-09-01",9L));
        verify(mapper,never()).selectStaffOptions(any(),any());
    }

    @Test
    void staffOptionsRejectInactivePlanner()
    {
        assertThrows(ServiceException.class,()->service.staffOptions(111L,"2026-09-01",9L));
        verify(mapper,never()).selectStaffOptions(any(),any());
    }

    @Test
    void createUsesCurrentUserAndSelectedBoss()
    {
        proposal.setProposalId(null);
        proposal.setApplicantUserId(999L);
        proposal.setSponsorOwnerUserId(23L);
        proposal.setSponsorOwnerName("不能信任的前端名称");
        Map<String,Object> applicant = user(9L,"applicant9","申请人九");
        when(mapper.selectActiveUser(9L)).thenReturn(applicant);
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        doAnswer(invocation -> {
            BusinessProjectProposal input = invocation.getArgument(0);
            input.setProposalId(77L); input.setStatus("DRAFT"); input.setVersion(0); input.setSubmissionVersion(0);
            return 1;
        }).when(mapper).insertProposal(any(BusinessProjectProposal.class));
        when(mapper.selectById(77L)).thenAnswer(invocation -> proposal);
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal created = service.create(proposal, 9L, "applicant9");

        assertEquals(9L, created.getApplicantUserId());
        assertEquals("申请人九", created.getApplicantName());
        assertEquals(23L, created.getSponsorOwnerUserId());
        assertEquals("审批老板", created.getSponsorOwnerName());
        assertEquals("LIGHT", created.getManagementMode());
        verify(mapper).insertEvent(any());
    }

    @Test
    void createNormalizesManagementModeBeforeValidation()
    {
        proposal.setProposalId(null);
        proposal.setManagementMode(" light ");
        proposal.setCloseMethod(" direct ");
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        doAnswer(invocation -> {
            BusinessProjectProposal input = invocation.getArgument(0);
            input.setProposalId(77L); input.setStatus("DRAFT"); input.setVersion(0); input.setSubmissionVersion(0);
            return 1;
        }).when(mapper).insertProposal(any(BusinessProjectProposal.class));
        when(mapper.selectById(77L)).thenAnswer(invocation -> proposal);
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal created = service.create(proposal, 9L, "applicant9");

        assertEquals("LIGHT", created.getManagementMode());
        assertEquals("DIRECT", created.getCloseMethod());
    }

    @Test
    void newProposalCanBeSavedWithoutProjectEndDate()
    {
        proposal.setProposalId(null);proposal.setPlanEndDate(null);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        doAnswer(invocation -> {
            BusinessProjectProposal input = invocation.getArgument(0);
            input.setProposalId(77L); input.setStatus("DRAFT"); input.setVersion(0); input.setSubmissionVersion(0);
            return 1;
        }).when(mapper).insertProposal(any(BusinessProjectProposal.class));
        when(mapper.selectById(77L)).thenAnswer(invocation -> proposal);
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal created = service.create(proposal,9L,"applicant9");

        assertEquals(null,created.getPlanEndDate());
        assertEquals("MONTH",created.getForecastPeriod());
        assertEquals(30,created.getForecastDays());
        verify(mapper).insertProposal(any(BusinessProjectProposal.class));
    }

    @Test
    void createDiscardsHiddenTargetsForContinuousOperation()
    {
        proposal.setProposalId(null);
        proposal.setGoalMode("NO_TOTAL");
        Map<String,Object> hiddenTarget = new HashMap<String,Object>();
        hiddenTarget.put("targetType","QUANTITY");
        hiddenTarget.put("targetName","切换模式前的旧目标");
        hiddenTarget.put("targetValue",new BigDecimal("100"));
        hiddenTarget.put("unit","项");
        hiddenTarget.put("acceptanceEvidence","旧验收依据");
        proposal.setTargetLines(Collections.singletonList(hiddenTarget));
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        doAnswer(invocation -> {
            BusinessProjectProposal input = invocation.getArgument(0);
            assertEquals(0,input.getTargetLines().size());
            input.setProposalId(77L); input.setStatus("DRAFT"); input.setVersion(0); input.setSubmissionVersion(0);
            return 1;
        }).when(mapper).insertProposal(any(BusinessProjectProposal.class));
        when(mapper.selectById(77L)).thenAnswer(invocation -> proposal);
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal created = service.create(proposal,9L,"applicant9");

        assertEquals("NO_TOTAL",created.getGoalMode());
        assertEquals(0,created.getTargetLines().size());
        verify(mapper,never()).insertTargetLine(any());
    }

    @Test
    void childProposalUsesNormalCreateValidationAndPersistsParentBinding()
    {
        proposal.setProposalId(null); proposal.setParentProjectId(15L);proposal.setAssignedOwnerUserId(10L);
        proposal.setSponsorOwnerUserId(999L);
        when(mapper.selectParentProject(15L)).thenReturn(childParent(9L));
        when(mapper.selectActiveUser(10L)).thenReturn(user(10L,"owner10","子负责人十"));
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        doAnswer(call -> { BusinessProjectProposal input=call.getArgument(0);input.setProposalId(77L);input.setStatus("DRAFT");return 1; })
            .when(mapper).insertProposal(any());
        when(mapper.selectById(77L)).thenReturn(proposal);
        BusinessProjectProposal saved=service.create(proposal,9L,"applicant9");
        assertEquals(15L,saved.getParentProjectId());
        assertEquals(10L,saved.getAssignedOwnerUserId());assertEquals("子负责人十",saved.getAssignedOwnerName());
        assertEquals(9L,saved.getApplicantUserId());assertEquals(23L,saved.getSponsorOwnerUserId());
        verify(projectService).validateSubprojectParent(15L,23L,9L);
        verify(projectService,never()).createApprovedProject(any(),any(),any());
        verify(budgetService).apply(proposal);
    }

    private Map<String,Object> childParent(Long owner) {
        Map<String,Object> row=new java.util.HashMap<>();row.put("mainOwnerUserId",owner);row.put("sponsorOwnerUserId",23L);return row;
    }

    @Test void childOwnerIsRequiredAndExistingParentPermissionIsEnforced() {
        proposal.setProposalId(null);proposal.setParentProjectId(15L);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"owner","负责人"));
        when(mapper.selectParentProject(15L)).thenReturn(childParent(8L));
        org.mockito.Mockito.doThrow(new ServiceException("无权管理主项目")).when(projectService).validateSubprojectParent(15L,23L,9L);
        assertEquals("无权管理主项目",assertThrows(ServiceException.class,()->service.estimateBudget(proposal,9L)).getMessage());
        org.mockito.Mockito.doNothing().when(projectService).validateSubprojectParent(15L,23L,9L);
        proposal.setProposalId(null);
        when(mapper.selectCompany(111L)).thenReturn(Collections.singletonMap("deptId",111L));
        assertEquals("请选择子项目负责人",assertThrows(ServiceException.class,()->service.create(proposal,9L,"owner")).getMessage());
        proposal.setAssignedOwnerUserId(999L);
        assertThrows(ServiceException.class,()->service.create(proposal,9L,"owner"));
        verify(mapper,never()).insertProposal(any());
    }

    @Test void mainProposalBudgetIgnoresForgedChildOwner() {
        proposal.setProposalId(null);proposal.setAssignedOwnerUserId(10L);proposal.setAssignedOwnerName("伪造姓名");
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"owner","负责人"));
        service.estimateBudget(proposal,9L);
        assertEquals(9L,proposal.getEffectiveOwnerUserId());assertEquals(null,proposal.getAssignedOwnerUserId());
        verify(mapper,never()).selectParentProject(any());
    }

    @Test
    void editingProposalCannotDetachOrReassignParent()
    {
        proposal.setStatus("DRAFT");proposal.setParentProjectId(15L);
        when(mapper.selectById(77L)).thenReturn(proposal);
        BusinessProjectProposal input=new BusinessProjectProposal();input.setProposalId(77L);
        assertEquals("归属主项目不可修改",assertThrows(ServiceException.class,()->service.update(input,9L,"applicant9")).getMessage());
        verify(mapper,never()).updateDraft(any());
    }

    @Test
    void createRequiresSelectedBoss()
    {
        proposal.setProposalId(null);
        proposal.setSponsorOwnerUserId(null);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.create(proposal,9L,"applicant9"));

        assertEquals("请选择项目观察老板",error.getMessage());
        verify(mapper,never()).insertProposal(any());
    }

    @Test
    void ownerAccountCanSelectSelfAsProjectObserver()
    {
        proposal.setProposalId(null);
        proposal.setSponsorOwnerUserId(9L);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(9L)).thenReturn(user(9L,"applicant9","申请人九"));

        doAnswer(invocation -> {
            BusinessProjectProposal input = invocation.getArgument(0);
            input.setProposalId(77L); input.setStatus("DRAFT"); input.setVersion(0); input.setSubmissionVersion(0);
            return 1;
        }).when(mapper).insertProposal(any(BusinessProjectProposal.class));
        when(mapper.selectById(77L)).thenAnswer(invocation -> proposal);
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal created = service.create(proposal,9L,"applicant9");

        assertEquals(9L,created.getSponsorOwnerUserId());
        assertEquals("申请人九",created.getSponsorOwnerName());
    }

    @Test
    void createRejectsAccountWithoutActiveBossRole()
    {
        proposal.setProposalId(null);
        proposal.setSponsorOwnerUserId(24L);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.create(proposal,9L,"applicant9"));

        assertEquals("审批老板账号不存在、已停用或没有老板角色",error.getMessage());
        verify(mapper,never()).insertProposal(any());
    }

    @Test
    void submitRechecksSelectedBossRole()
    {
        proposal.setStatus("DRAFT");
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(77L,9L,"applicant9"));

        assertEquals("审批老板账号不存在、已停用或没有老板角色",error.getMessage());
        verify(mapper,never()).submit(any(),any(),any(),any());
    }

    @Test
    void ownerLaunchesProjectAfterCompleteBusinessPlanWithoutBossApproval()
    {
        proposal.setStatus("DRAFT");
        proposal.setKeyAssumptions("基准转化率可持续");
        proposal.setRiskSummary("流量波动可能影响收入");
        proposal.setStopLossRule("连续两周低于目标50%即停止新投入");

        Map<String,Object> revenue = new HashMap<String,Object>();
        revenue.put("scenario","BASE"); revenue.put("revenueType","SERVICE");
        revenue.put("itemName","直播服务收入"); revenue.put("expectedAmount",new BigDecimal("2000"));
        Map<String,Object> expense = new HashMap<String,Object>();
        expense.put("expenseCategory","TRAFFIC"); expense.put("itemName","投流");
        expense.put("purpose","获取新用户"); expense.put("amount",new BigDecimal("300"));
        Map<String,Object> staffing = new HashMap<String,Object>();
        staffing.put("userId",12L); staffing.put("estimatedCost",new BigDecimal("999999"));
        Map<String,Object> target = new HashMap<String,Object>();
        target.put("targetType","FINANCIAL"); target.put("targetName","月收入");
        target.put("targetValue",new BigDecimal("2000")); target.put("unit","元");
        target.put("acceptanceEvidence","已确认收入流水");

        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectRevenueLines(77L)).thenReturn(Collections.singletonList(revenue));
        when(mapper.selectExpenseLines(77L)).thenReturn(Collections.singletonList(expense));
        when(mapper.selectStaffingLines(77L)).thenReturn(Collections.singletonList(staffing));
        Map<String,Object> selectedStaff = user(12L,"anchor12","主播十二");
        selectedStaff.put("accountName","anchor12"); selectedStaff.put("positionName","主播");
        selectedStaff.put("companyDeptId",111L); selectedStaff.put("costMode","MONTHLY");
        selectedStaff.put("costPolicyId",501L); selectedStaff.put("costPolicyVersion",3);
        selectedStaff.put("monthlyCost",new BigDecimal("15000")); selectedStaff.put("standardWorkDays",new BigDecimal("30"));
        selectedStaff.put("dailyCost",new BigDecimal("500")); selectedStaff.put("costCurrency","CNY");
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(selectedStaff);
        when(mapper.updateComputedPlan(proposal)).thenReturn(1);
        when(mapper.selectTargetLines(77L)).thenReturn(Collections.singletonList(target));
        BusinessProject project = new BusinessProject(); project.setProjectId(88L);
        when(projectService.createApprovedProject(proposal,9L,"applicant9")).thenReturn(project);
        doAnswer(invocation -> {
            proposal.setStatus("APPROVED"); proposal.setCreatedProjectId(88L); proposal.setVersion(3);
            return 1;
        }).when(mapper).activate(77L,9L,2,88L,"申请人九","applicant9");
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal launched = service.submit(77L,9L,"applicant9");

        assertEquals("APPROVED",launched.getStatus());
        assertEquals(88L,launched.getCreatedProjectId());
        assertEquals(new BigDecimal("2000.00"),launched.getEstimatedRevenue());
        assertEquals(new BigDecimal("800.00"),launched.getEstimatedTotalCost());
        assertEquals(new BigDecimal("1200.00"),launched.getExpectedProfit());
        assertEquals(1,launched.getPlannedHeadcount());
        verify(projectService).createApprovedProject(proposal,9L,"applicant9");
        verify(mapper,never()).submit(any(),any(),any(),any());
        verify(mapper,never()).review(any(),any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test
    void optionsIncludeOwnerAccountAsObserverCandidate()
    {
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectBossOptions(null)).thenReturn(Collections.singletonList(user(9L,"applicant9","申请人九")));
        when(mapper.selectCompanyOptions()).thenReturn(Collections.<Map<String,Object>>emptyList());

        Map<String,Object> result = service.options(9L);

        assertEquals(9L,result.get("applicantUserId"));
        assertEquals(1,((java.util.List<?>)result.get("bosses")).size());
        verify(mapper).selectBossOptions(null);
    }

    @ParameterizedTest
    @CsvSource({"2026-09-08,FOLLOW_PROJECT", "2026-09-15,UNLIMITED"})
    void savedOpenEndedDraftWithoutModeCanLaunch(String staffStart,String expectedMode)
    {
        proposal.setAccountingMode("COST");
        proposal.setTemplateVersion("LIGHT_V1");proposal.setStatus("DRAFT");proposal.setAcceptanceCriteria("交付文件");
        proposal.setPlanStartDate(java.sql.Date.valueOf("2026-09-08"));proposal.setPlanEndDate(null);
        proposal.setBudgetMode("NONE");proposal.setBudgetReason("持续经营");
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectStaffingLines(77L)).thenReturn(Collections.singletonList(BusinessProjectWorkServiceTest.row(
            "userId",12L,"planStartDate",java.sql.Date.valueOf(staffStart),"calendarId",1L)));
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(BusinessProjectWorkServiceTest.row("userId",12L,"companyDeptId",111L));
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.updateComputedPlan(proposal)).thenReturn(1);
        BusinessProject created=new BusinessProject();created.setProjectId(88L);
        when(projectService.createApprovedProject(proposal,9L,"applicant9")).thenReturn(created);
        when(mapper.activate(77L,9L,2,88L,"申请人九","applicant9")).thenAnswer(call->{proposal.setStatus("APPROVED");return 1;});
        assertEquals("APPROVED",service.submit(77L,9L,"applicant9").getStatus());
        assertEquals(expectedMode,proposal.getStaffingLines().get(0).get("participationMode"));
        assertEquals(null,proposal.getStaffingLines().get(0).get("planEndDate"));
    }

    @Test
    void explicitCustomParticipationStillRequiresEndDate()
    {
        proposal.setForecastDays(30);
        proposal.setTemplateVersion("LIGHT_V1");proposal.setPlanStartDate(java.sql.Date.valueOf("2026-09-08"));proposal.setPlanEndDate(null);
        proposal.setStaffingLines(Collections.singletonList(BusinessProjectWorkServiceTest.row("userId",12L,"participationMode","CUSTOM",
            "planStartDate",java.sql.Date.valueOf("2026-09-08"),"calendarId",1L)));
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(BusinessProjectWorkServiceTest.row("userId",12L,"companyDeptId",111L));
        assertThrows(ServiceException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal));
    }

    @ParameterizedTest
    @CsvSource({"DIRECT", "RESULT_ACCEPTANCE", "STAGED_ACCEPTANCE"})
    void lightTemplateLaunchesWithoutAcceptanceCriteriaBudgetStaffCostRevenueKpiOrBonus(String closeMethod)
    {
        proposal.setAccountingMode("COST");
        proposal.setTemplateVersion("LIGHT_V1");proposal.setStatus("DRAFT");proposal.setBudgetLimit(null);
        proposal.setCloseMethod(closeMethod);proposal.setAcceptanceCriteria(null);
        proposal.setBudgetMode("NONE");proposal.setBudgetReason("本次测试明确不设置预算控制上限");
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.updateComputedPlan(proposal)).thenReturn(1);
        BusinessProject created=new BusinessProject();created.setProjectId(88L);
        when(projectService.createApprovedProject(proposal,9L,"applicant9")).thenReturn(created);
        when(mapper.activate(77L,9L,2,88L,"申请人九","applicant9")).thenAnswer(call->{proposal.setStatus("APPROVED");return 1;});
        BusinessProjectProposal result=service.submit(77L,9L,"applicant9");
        assertEquals("APPROVED",result.getStatus());assertEquals("1",result.getNoBudget());assertEquals(0,result.getPlannedHeadcount());
        assertEquals(null,result.getAcceptanceCriteria());assertEquals(closeMethod,result.getCloseMethod());
        verify(mapper,never()).selectProposalStaff(any(),any());verify(mapper,never()).submit(any(),any(),any(),any());
    }

    @ParameterizedTest
    @CsvSource({"LIGHT_V1,DRAFT", "CONTROLLED_V1,DRAFT", "SERVICE_V1,DRAFT",
        "CONTROLLED_V1,PENDING", "CONTROLLED_V1,RETURNED", "SERVICE_V1,WITHDRAWN"})
    void everyProposalLaunchesDirectlyIncludingPendingApplications(String templateVersion, String status) throws Exception
    {
        proposal.setAccountingMode("COST");
        proposal.setTemplateVersion(templateVersion);proposal.setStatus(status);proposal.setBudgetLimit(null);proposal.setAcceptanceCriteria("交付文件验收");
        proposal.setPlanEndDate(null);
        proposal.setManagementMode("KEY_CONTROL");proposal.setCloseMethod("STAGED_ACCEPTANCE");proposal.setManagementReason("逐阶段检查交付风险");
        when(workMapper.selectTemplate(templateVersion)).thenReturn(BusinessProjectWorkServiceTest.row("snapshotJson","{}","managementMode","STANDARD","closeMethod","RESULT_ACCEPTANCE"));
        when(mapper.selectById(77L)).thenReturn(proposal);when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.updateComputedPlan(proposal)).thenReturn(1);
        BusinessProject created = new BusinessProject();created.setProjectId(88L);
        when(projectService.createApprovedProject(proposal,9L,"applicant9")).thenReturn(created);
        when(mapper.activate(77L,9L,2,88L,"申请人九","applicant9")).thenAnswer(call->{proposal.setStatus("APPROVED");return 1;});
        assertEquals("APPROVED",service.submit(77L,9L,"applicant9").getStatus());
        assertEquals("KEY_CONTROL",proposal.getManagementMode());assertEquals("STAGED_ACCEPTANCE",proposal.getCloseMethod());
        Map<?,?> snapshot=objectMapper.readValue(proposal.getTemplateSnapshotJson(),Map.class);
        assertEquals("KEY_CONTROL",snapshot.get("managementMode"));assertEquals("STAGED_ACCEPTANCE",snapshot.get("closeMethod"));
        assertEquals("SELF_AUTHORIZED",snapshot.get("authorizationMode"));
        assertEquals(null,proposal.getPlanEndDate());assertEquals(false,snapshot.get("finiteReviewWindowRequired"));
        verify(mapper,never()).submit(any(),any(),any(),any());
        verify(mapper,never()).review(any(),any(),any(),any(),any(),any(),any(),any(),any());
        org.mockito.ArgumentCaptor<Map<String,Object>> event=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertEvent(event.capture());assertEquals(status,event.getValue().get("fromStatus"));
        assertThrows(ServiceException.class,()->service.submit(77L,9L,"applicant9"));
        verify(projectService).createApprovedProject(proposal,9L,"applicant9");
    }

    @ParameterizedTest
    @CsvSource({"LIGHT,DIRECT", "LIGHT,RESULT_ACCEPTANCE", "LIGHT,STAGED_ACCEPTANCE",
        "STANDARD,DIRECT", "STANDARD,RESULT_ACCEPTANCE", "STANDARD,STAGED_ACCEPTANCE",
        "KEY_CONTROL,DIRECT", "KEY_CONTROL,RESULT_ACCEPTANCE", "KEY_CONTROL,STAGED_ACCEPTANCE"})
    void editingPreservesIndependentGovernanceChoices(String managementMode, String closeMethod) throws Exception
    {
        proposal.setTemplateVersion("LIGHT_V1");proposal.setStatus("PENDING");
        BusinessProjectProposal input=new BusinessProjectProposal();
        org.springframework.beans.BeanUtils.copyProperties(proposal,input);
        input.setManagementMode(managementMode);input.setCloseMethod(closeMethod);
        input.setManagementReason("项目涉及多阶段风险");input.setAcceptanceCriteria("逐项核对交付成果");
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.updateDraft(input)).thenAnswer(call->{org.springframework.beans.BeanUtils.copyProperties(input,proposal);return 1;});
        BusinessProjectProposal saved=service.update(input,9L,"applicant9");
        assertEquals(managementMode,saved.getManagementMode());assertEquals(closeMethod,saved.getCloseMethod());
        Map<?,?> snapshot=objectMapper.readValue(saved.getTemplateSnapshotJson(),Map.class);
        assertEquals(managementMode,snapshot.get("managementMode"));assertEquals(closeMethod,snapshot.get("closeMethod"));
        assertEquals(Boolean.TRUE,saved.getCanEdit());
    }

    @Test
    void pendingProposalCannotBeStartedByAnotherUser()
    {
        proposal.setStatus("PENDING");when(mapper.selectById(77L)).thenReturn(proposal);
        assertThrows(ServiceException.class,()->service.submit(77L,23L,"boss23"));
        verify(projectService,never()).createApprovedProject(any(),any(),any());
    }

    @Test
    void unlimitedProposalAcceptsIndependentStaffDatesAndQuantityTarget()
    {
        proposal.setStatus("DRAFT");proposal.setTemplateVersion("LIGHT_V1");
        proposal.setPlanStartDate(java.sql.Date.valueOf("2026-01-01"));proposal.setPlanEndDate(null);
        proposal.setAcceptanceCriteria("完成数量目标");
        Map<String,Object> staffing=BusinessProjectWorkServiceTest.row("userId",12L,"planStartDate","2026-02-01",
            "planEndDate","2026-03-31","inputUnit","DAY","inputQuantity",10,"calendarId",1L,"unitPolicyId",1L);
        Map<String,Object> target=BusinessProjectWorkServiceTest.row("targetType","QUANTITY","targetName","交付数量",
            "targetValue",20,"unit","件","acceptanceEvidence","验收清单");
        proposal.setStaffingLines(Collections.singletonList(staffing));proposal.setTargetLines(Collections.singletonList(target));
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(BusinessProjectWorkServiceTest.row("userId",12L,"companyDeptId",111L,"nickName","成员十二"));
        when(mapper.updateDraft(proposal)).thenReturn(1);
        service.update(proposal,9L,"applicant9");
        org.mockito.ArgumentCaptor<Map<String,Object>> staffArg=org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.ArgumentCaptor<Map<String,Object>> targetArg=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStaffingLine(staffArg.capture());verify(mapper).insertTargetLine(targetArg.capture());
        assertEquals(java.sql.Date.valueOf("2026-03-31"),staffArg.getValue().get("planEndDate"));
        assertEquals("QUANTITY",targetArg.getValue().get("targetType"));
        assertEquals(null,proposal.getPlanEndDate());
    }

    @Test
    void staffingParticipationModesReplaceManualWorkload()
    {
        proposal.setStatus("DRAFT");proposal.setTemplateVersion("LIGHT_V1");
        proposal.setPlanStartDate(java.sql.Date.valueOf("2026-01-01"));proposal.setPlanEndDate(null);
        proposal.setAcceptanceCriteria("完成交付");
        Map<String,Object> staffing=BusinessProjectWorkServiceTest.row("userId",12L,"participationMode","UNLIMITED",
            "planStartDate","2026-02-01","inputUnit","HOUR","inputQuantity",2,"calendarId",1L,"unitPolicyId",999L);
        proposal.setStaffingLines(Collections.singletonList(staffing));
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(BusinessProjectWorkServiceTest.row("userId",12L,"companyDeptId",111L,"nickName","成员十二"));
        when(mapper.updateDraft(proposal)).thenReturn(1);
        service.update(proposal,9L,"applicant9");
        org.mockito.ArgumentCaptor<Map<String,Object>> saved=org.mockito.ArgumentCaptor.forClass(Map.class);verify(mapper).insertStaffingLine(saved.capture());
        assertEquals("UNLIMITED",saved.getValue().get("participationMode"));assertEquals(null,saved.getValue().get("planEndDate"));
        assertEquals("PERCENTAGE",saved.getValue().get("inputUnit"));assertEquals(BigDecimal.ZERO,saved.getValue().get("inputQuantity"));assertEquals(1L,saved.getValue().get("unitPolicyId"));
    }

    @Test
    void followProjectParticipationAlwaysInheritsProjectDates()
    {
        proposal.setStatus("DRAFT");proposal.setTemplateVersion("LIGHT_V1");proposal.setAcceptanceCriteria("完成交付");
        Map<String,Object> staffing=BusinessProjectWorkServiceTest.row("userId",12L,"participationMode","FOLLOW_PROJECT",
            "planStartDate","2030-01-01","planEndDate","2030-12-31","calendarId",1L);
        proposal.setStaffingLines(Collections.singletonList(staffing));when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectProposalStaff(eq(12L),any(Date.class))).thenReturn(BusinessProjectWorkServiceTest.row("userId",12L,"companyDeptId",111L));when(mapper.updateDraft(proposal)).thenReturn(1);
        service.update(proposal,9L,"applicant9");org.mockito.ArgumentCaptor<Map<String,Object>> saved=org.mockito.ArgumentCaptor.forClass(Map.class);verify(mapper).insertStaffingLine(saved.capture());
        assertEquals(proposal.getPlanStartDate(),saved.getValue().get("planStartDate"));assertEquals(proposal.getPlanEndDate(),saved.getValue().get("planEndDate"));
    }

    @Test
    void proposalOwnerCanContinueLegacyPlanWithoutReceivingRawRateSnapshots()
    {
        proposal.setStatus("DRAFT");when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectStaffingLines(77L)).thenReturn(Collections.singletonList(BusinessProjectWorkServiceTest.row("userId",12L,"monthlyCostSnapshot",15000,"dailyCostSnapshot",500,"estimatedCost",500)));
        when(mapper.selectEvents(77L)).thenReturn(Collections.singletonList(BusinessProjectWorkServiceTest.row("eventType","CREATE","snapshotJson","{\"monthlyCostSnapshot\":15000}")));
        BusinessProjectProposal result=service.get(77L,9L,false,false);
        assertEquals(12L,result.getStaffingLines().get(0).get("userId"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getStaffingLines().get(0).containsKey("monthlyCostSnapshot"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getStaffingLines().get(0).containsKey("estimatedCost"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getEvents().get(0).containsKey("snapshotJson"));
    }

    @Test
    void totalBudgetDetailRemovesStoredDailyStartupWarning()
    {
        proposal.setStatus("DRAFT");proposal.setBudgetMode("TOTAL");
        proposal.setStartupBudgetLimit(new BigDecimal("100"));
        proposal.setBudget(BusinessProjectWorkServiceTest.row("mode","TOTAL","startupLimit",100,
            "status","PENDING","issues",Collections.singletonList("启动预算不能低于本期一次性支出 15000.00")));
        when(mapper.selectById(77L)).thenReturn(proposal);

        BusinessProjectProposal result=service.get(77L,9L,false,false);

        assertEquals(null,result.getStartupBudgetLimit());
        assertEquals(null,result.getBudget().get("startupLimit"));
        assertEquals("READY",result.getBudget().get("status"));
        assertEquals(Collections.emptyList(),result.getBudget().get("issues"));
    }

    @Test
    void selectedBossApprovalCreatesOneActiveProject()
    {
        proposal.setStatus("PENDING");
        when(mapper.selectById(77L)).thenReturn(proposal);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","审批老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        BusinessProject project = new BusinessProject(); project.setProjectId(88L);
        when(projectService.createApprovedProject(proposal,23L,"boss23")).thenReturn(project);
        doAnswer(invocation -> { proposal.setStatus("APPROVED"); proposal.setCreatedProjectId(88L); return 1; })
            .when(mapper).review(eq(77L),eq(23L),eq(2),eq("APPROVED"),eq(23L),eq("审批老板"),
                eq(null),eq(88L),eq("boss23"));
        when(mapper.selectEvents(77L)).thenReturn(Collections.<Map<String,Object>>emptyList());

        BusinessProjectProposal approved = service.review(77L,"APPROVED",null,23L,"boss23",true);

        assertEquals("APPROVED",approved.getStatus());
        assertEquals(88L,approved.getCreatedProjectId());
        verify(projectService).createApprovedProject(proposal,23L,"boss23");
    }

    @Test
    void otherBossCannotReviewAssignedProposal()
    {
        proposal.setStatus("PENDING");
        when(mapper.selectById(77L)).thenReturn(proposal);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.review(77L,"APPROVED",null,24L,"boss24",true));

        assertEquals("只能审批分配给本人的立项申请",error.getMessage());
        verify(projectService,never()).createApprovedProject(any(),any(),any());
    }

    @Test
    void otherBossCannotOpenAssignedProposalDetail()
    {
        proposal.setStatus("PENDING");
        when(mapper.selectById(77L)).thenReturn(proposal);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.get(77L,24L,true,false));

        assertEquals("无权查看该立项申请",error.getMessage());
        verify(mapper,never()).selectEvents(77L);
    }

    @ParameterizedTest
    @CsvSource({"RESULT,QUANTITY", "VALUE,OTHER", "OTHER,OTHER", "FINANCIAL,FINANCIAL",
        "QUANTITY,QUANTITY", "SCHEDULE,SCHEDULE", "QUALITY,QUALITY", "EFFICIENCY,EFFICIENCY",
        "GROWTH,GROWTH", "CUSTOMER,CUSTOMER", "COMPLIANCE,COMPLIANCE"})
    void targetTypesAndPrecisionSurviveRepeatedNormalization(String inputType, String savedType)
    {
        proposal.setGoalMode("TOTAL");proposal.setForecastDays(30);
        proposal.setTargetLines(Collections.singletonList(BusinessProjectWorkServiceTest.row(
            "targetType",inputType,"targetName","转化目标","targetValue",new BigDecimal("1.2345"),
            "unit","%","acceptanceEvidence","统计报表")));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal);
        assertEquals(savedType,proposal.getTargetLines().get(0).get("targetType"));
        // Editing and launch validate the canonical value read back from storage.
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal);
        assertEquals(savedType,proposal.getTargetLines().get(0).get("targetType"));
        assertEquals(new BigDecimal("1.2345"),proposal.getTargetLines().get(0).get("targetValue"));
    }

    @Test
    void noTotalModeDiscardsIncompleteHiddenTargets()
    {
        proposal.setGoalMode("NO_TOTAL");proposal.setForecastDays(30);
        proposal.setTargetLines(Collections.singletonList(BusinessProjectWorkServiceTest.row("targetType","VALUE")));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal);
        assertEquals(0,proposal.getTargetLines().size());
    }

    @Test
    void totalModeStillRejectsIncompleteOrUnknownTargets()
    {
        proposal.setGoalMode("TOTAL");proposal.setForecastDays(30);
        proposal.setTargetLines(Collections.singletonList(BusinessProjectWorkServiceTest.row("targetType","VALUE")));
        assertThrows(ServiceException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal));
        proposal.setTargetLines(Collections.singletonList(BusinessProjectWorkServiceTest.row(
            "targetType","UNKNOWN","targetName","目标","targetValue",1,"unit","个","acceptanceEvidence","清单")));
        assertThrows(ServiceException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal));
    }

    private Map<String,Object> user(Long id,String userName,String nickName)
    {
        Map<String,Object> row = new HashMap<String,Object>();
        row.put("userId",id); row.put("userName",userName); row.put("nickName",nickName);
        return row;
    }

    @ParameterizedTest
    @CsvSource({"COST,0,false,true", "PROFIT,0,false,false", "PROFIT,100,false,true",
        "VALUE,0,false,false", "VALUE,0,true,true", "HYBRID,100,false,false",
        "HYBRID,0,true,false", "HYBRID,100,true,true"})
    void accountingModeRequirementsAreCheckedAtLaunch(String mode,String revenue,boolean target,boolean allowed)
    {
        proposal.setAccountingMode(mode);proposal.setEstimatedRevenue(new BigDecimal(revenue));
        proposal.setTargetLines(target?Collections.singletonList(BusinessProjectWorkServiceTest.row("targetName","完成上线","acceptanceEvidence","上线验收")):Collections.emptyList());
        if(allowed)org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->service.validateAccountingRequirements(proposal));
        else assertThrows(ServiceException.class,()->service.validateAccountingRequirements(proposal));
    }

    @ParameterizedTest
    @CsvSource({"PROFIT", "VALUE", "HYBRID", "COST"})
    void modeSpecificRequirementsDoNotBlockDraftSave(String mode)
    {
        proposal.setAccountingMode(mode);proposal.setTemplateVersion("LIGHT_V1");proposal.setProposalId(null);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectActiveBoss(23L)).thenReturn(user(23L,"boss23","老板"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.singletonMap("deptId",111L));
        doAnswer(call->{proposal.setProposalId(77L);proposal.setStatus("DRAFT");return 1;}).when(mapper).insertProposal(any());
        when(mapper.selectById(77L)).thenReturn(proposal);
        assertEquals("DRAFT",service.create(proposal,9L,"applicant9").getStatus());
        verify(projectService,never()).createApprovedProject(any(),any(),any());
    }

    @Test
    void continuousValueProjectKeepsQualitativeGoalWithoutNumericEntry()
    {
        proposal.setAccountingMode("VALUE");proposal.setGoalMode("NO_TOTAL");proposal.setTemplateVersion("LIGHT_V1");proposal.setForecastDays(1);
        Map<String,Object> target=BusinessProjectWorkServiceTest.row("targetType","DELIVERY","targetName","系统上线","acceptanceEvidence","验收通过");
        proposal.setTargetLines(Collections.singletonList(target));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal);
        assertEquals(1,proposal.getTargetLines().size());assertEquals(BigDecimal.ONE,target.get("targetValue"));assertEquals("项",target.get("unit"));
        service.validateAccountingRequirements(proposal);
        proposal.getTargetLines().get(0).put("acceptanceEvidence","");
        assertThrows(ServiceException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeBusinessPlan",proposal));
    }
}
