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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.BusinessProjectProposalMapper;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectProposalServiceImplTest
{
    @Mock private BusinessProjectProposalMapper mapper;
    @Mock private IBusinessProjectService projectService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private BusinessProjectProposalServiceImpl service;

    private BusinessProjectProposal proposal;

    @BeforeEach
    void setUp()
    {
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
    void createAllowsPlanWithoutEndDate()
    {
        proposal.setProposalId(null);
        proposal.setPlanEndDate(null);
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
        verify(mapper).insertProposal(any(BusinessProjectProposal.class));
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
        assertEquals(new BigDecimal("2000"),launched.getEstimatedRevenue());
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

    private Map<String,Object> user(Long id,String userName,String nickName)
    {
        Map<String,Object> row = new HashMap<String,Object>();
        row.put("userId",id); row.put("userName",userName); row.put("nickName",nickName);
        return row;
    }
}
