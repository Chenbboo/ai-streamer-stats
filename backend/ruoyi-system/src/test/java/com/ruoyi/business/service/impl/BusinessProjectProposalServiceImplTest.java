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
    void createRequiresSelectedBoss()
    {
        proposal.setProposalId(null);
        proposal.setSponsorOwnerUserId(null);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.create(proposal,9L,"applicant9"));

        assertEquals("请选择审批老板",error.getMessage());
        verify(mapper,never()).insertProposal(any());
    }

    @Test
    void applicantCannotSelectSelfAsBoss()
    {
        proposal.setProposalId(null);
        proposal.setSponsorOwnerUserId(9L);
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectCompany(111L)).thenReturn(Collections.<String,Object>singletonMap("deptId",111L));
        when(mapper.selectActiveBoss(9L)).thenReturn(user(9L,"applicant9","申请人九"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.create(proposal,9L,"applicant9"));

        assertEquals("申请人不能选择本人作为审批老板",error.getMessage());
        verify(mapper,never()).insertProposal(any());
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
    void optionsExcludeCurrentApplicantFromBossCandidates()
    {
        when(mapper.selectActiveUser(9L)).thenReturn(user(9L,"applicant9","申请人九"));
        when(mapper.selectBossOptions(9L)).thenReturn(Collections.singletonList(user(23L,"boss23","审批老板")));
        when(mapper.selectCompanyOptions()).thenReturn(Collections.<Map<String,Object>>emptyList());

        Map<String,Object> result = service.options(9L);

        assertEquals(9L,result.get("applicantUserId"));
        assertEquals(1,((java.util.List<?>)result.get("bosses")).size());
        verify(mapper).selectBossOptions(9L);
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
