package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectServiceImplTest
{
    @Mock
    private BusinessProjectMapper mapper;

    @Mock
    private BusinessProjectKpiMapper kpiMapper;

    @Mock
    private BusinessAccountingMapper accountingMapper;

    @Mock
    private IBusinessAccountingService accountingService;

    @InjectMocks
    private BusinessProjectServiceImpl service;

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
    void adjustedKpiVersionKeepsOriginalSystemCode()
    {
        BusinessProject project = project(15L, 9L, "ACTIVE", "APPROVED");
        project.setSponsorOwnerUserId(8L);
        BusinessProjectKpi previous = new BusinessProjectKpi();
        previous.setKpiId(70L);
        previous.setProjectId(15L);
        previous.setKpiCode("KPI_P15_A1B2C3D4E5F6");
        previous.setStatus("CURRENT");
        when(mapper.selectProjectById(15L)).thenReturn(project);
        when(mapper.selectProjectKpiById(70L)).thenReturn(previous);
        when(mapper.retireProjectKpi(70L, "boss8")).thenReturn(1);
        when(mapper.selectNextKpiVersion(15L, "KPI_P15_A1B2C3D4E5F6")).thenReturn(2);
        BusinessProjectKpi input = new BusinessProjectKpi();
        input.setKpiId(70L);
        input.setProjectId(15L);
        input.setKpiCode("ATTEMPT_CHANGE");
        input.setKpiName("有效播放量");
        input.setTargetValue(new BigDecimal("600000"));

        BusinessProjectKpi saved = service.saveKpi(input, 8L, "boss8", true);

        assertEquals("KPI_P15_A1B2C3D4E5F6", saved.getKpiCode());
        assertEquals(2, saved.getTargetVersion());
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
        when(mapper.selectDashboardProjectPage(23L,false,true,20,10))
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
    void bossPendingUsesOneServerPageAndCategoryCounts()
    {
        Map<String,Object> counts=new HashMap<String,Object>();
        counts.put("proposalCount",2L);counts.put("kpiMissingCount",7L);
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
        verify(mapper).selectOwnerPersonnelCostReadiness(eq(23L),any(Date.class),eq(false));
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

        Map<String,Object> result=service.ownerWorkbench(81L,23L,false);

        assertEquals(Collections.singletonList(alert),result.get("allocationAlerts"));
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

    @Test
    void approvedProposalCreatesActiveProjectAndRegistersApplicantAsOwner()
    {
        BusinessProjectProposal proposal = new BusinessProjectProposal();
        proposal.setProposalId(66L);
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
        Map<String, Object> owner = new HashMap<String, Object>();
        owner.put("userName", "owner9");
        owner.put("nickName", "负责人九");
        Map<String, Object> sponsor = new HashMap<String, Object>();
        sponsor.put("userName", "boss23");
        sponsor.put("nickName", "审批老板");
        when(mapper.selectActiveUserById(9L)).thenReturn(owner);
        when(mapper.selectActiveUserById(23L)).thenReturn(sponsor);
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
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("APPROVED", created.getBaselineStatus());
        assertEquals(66L, created.getSourceProposalId());
        assertEquals(9L, created.getApplicantUserId());
        assertEquals(23L, created.getSponsorOwnerUserId());
        assertEquals("审批老板", created.getSponsorOwnerName());
        assertEquals("负责人九", created.getMainOwnerName());
        assertEquals("LIVE", created.getExecutionSource());
        assertEquals("SIMPLE", created.getManagementMode());
        assertTrue(created.getProjectNo().startsWith("XM"));
        ArgumentCaptor<BusinessProjectMember> member = ArgumentCaptor.forClass(BusinessProjectMember.class);
        verify(mapper).upsertMember(member.capture());
        assertEquals(9L, member.getValue().getUserId());
        assertEquals("OWNER", member.getValue().getMemberRole());
        verify(mapper).insertUserRole(9L, 18L);
        verify(mapper).insertUserRole(9L, 19L);
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

        assertTrue(error.getMessage().contains("其他老板"));
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

        assertTrue(error.getMessage().contains("其他老板"));
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
    void completedProjectCanSubmitVersionedAcceptanceEvidence()
    {
        BusinessProject active = project(71L, 9L, "ACTIVE", "APPROVED");
        active.setManagementMode("DELIVERY");
        active.setInitiatorUserId(8L);
        BusinessProject acceptanceState = project(71L, 9L, "ACCEPTANCE", "APPROVED");
        acceptanceState.setManagementMode("DELIVERY");
        acceptanceState.setInitiatorUserId(8L);
        BusinessProjectTask done = new BusinessProjectTask();
        done.setStatus("DONE");
        Map<String, Object> submitter = new HashMap<String, Object>();
        submitter.put("nickName", "负责人九");
        when(mapper.selectProjectById(71L)).thenReturn(active, acceptanceState);
        when(mapper.selectMemberRole(71L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(71L)).thenReturn(Collections.singletonList(done));
        when(mapper.selectMilestones(71L)).thenReturn(Collections.emptyList());
        when(mapper.selectRisks(71L)).thenReturn(Collections.emptyList());
        when(mapper.selectActiveUserById(9L)).thenReturn(submitter);
        when(mapper.selectNextAcceptanceVersion(71L)).thenReturn(2);
        when(mapper.insertAcceptance(any())).thenReturn(1);
        when(mapper.updateProjectStatus(71L, "ACTIVE", "ACCEPTANCE", null, false, "owner9", 0)).thenReturn(1);

        BusinessProjectAcceptance evidence = new BusinessProjectAcceptance();
        evidence.setResultSummary("全部目标已完成");
        evidence.setDeliverables("成片和复盘报告");
        evidence.setAttachmentUrls("/profile/upload/report.pdf");
        BusinessProject result = service.submitAcceptance(71L, evidence, 9L, "owner9", false);

        assertEquals("ACCEPTANCE", result.getStatus());
        assertEquals(2, evidence.getSubmissionVersion());
        assertEquals("负责人九", evidence.getSubmittedUserName());
        verify(mapper).insertEvent(any());
    }

    @Test
    void recurringWorkCanBeTheOnlySubmittedBaselineItem()
    {
        BusinessProject project = project(73L, 9L, "PLANNING", "DRAFT");
        project.setObjective("每天稳定产出视频");
        project.setPlanStartDate(new Date());
        project.setPlanEndDate(new Date());
        BusinessProject submitted = project(73L, 9L, "PLANNING", "SUBMITTED");
        submitted.setObjective(project.getObjective());
        submitted.setPlanStartDate(project.getPlanStartDate());
        submitted.setPlanEndDate(project.getPlanEndDate());
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(1L);
        when(mapper.selectProjectById(73L)).thenReturn(project, submitted);
        when(mapper.selectMemberRole(73L, 9L)).thenReturn("OWNER");
        when(mapper.selectTasks(73L)).thenReturn(Collections.emptyList());
        when(mapper.selectRoutines(org.mockito.ArgumentMatchers.eq(73L), any())).thenReturn(Collections.singletonList(routine));
        when(mapper.updateProjectStatus(73L, "PLANNING", "PLANNING", "SUBMITTED", false, "owner9", 0)).thenReturn(1);

        BusinessProject result = service.transition(73L, "SUBMIT_BASELINE", null, 9L, "owner9", false);

        assertEquals("SUBMITTED", result.getBaselineStatus());
        verify(mapper).updateProjectStatus(73L, "PLANNING", "PLANNING", "SUBMITTED", false, "owner9", 0);
        verify(mapper).insertEvent(any());
    }

    @Test
    void sourceManagedLiveRoutineCanBeTheOnlySubmittedBaselineItem()
    {
        BusinessProject project = project(86L, 9L, "PLANNING", "DRAFT");
        project.setObjective("每日完成主播日报");
        project.setPlanStartDate(new Date());
        project.setPlanEndDate(new Date());
        BusinessProject submitted = project(86L, 9L, "PLANNING", "SUBMITTED");
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
        when(mapper.updateProjectStatus(86L, "PLANNING", "PLANNING", "SUBMITTED", false, "owner9", 0)).thenReturn(1);

        BusinessProject result = service.transition(86L, "SUBMIT_BASELINE", null, 9L, "owner9", false);

        assertEquals("SUBMITTED", result.getBaselineStatus());
        verify(mapper).updateProjectStatus(86L, "PLANNING", "PLANNING", "SUBMITTED", false, "owner9", 0);
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
    void bossCanCloseSimpleProjectWithConclusion()
    {
        BusinessProject project = project(76L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
        BusinessProject closed = project(76L, 9L, "CLOSED", "APPROVED");
        closed.setInitiatorUserId(8L);
        when(mapper.selectProjectById(76L)).thenReturn(project, closed);
        when(kpiMapper.selectPlanSummaries(76L))
            .thenReturn(Collections.singletonList(publishedKpiPlan("CONFIRMED")));
        when(mapper.updateProjectStatus(76L, "ACTIVE", "CLOSED", null, false, "boss8", 0)).thenReturn(1);

        BusinessProject result = service.transition(76L, "CLOSE", "目标已完成", 8L, "boss8", true);

        assertEquals("CLOSED", result.getStatus());
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
    void unfinishedFutureKpiPeriodCannotClose()
    {
        BusinessProject project = project(80L, 9L, "ACTIVE", "APPROVED");
        project.setInitiatorUserId(8L);
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
    void initiatingBossCanApprovePendingAcceptance()
    {
        BusinessProject pendingProject = project(72L, 9L, "ACCEPTANCE", "APPROVED");
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
        when(mapper.selectStaffCompanyLeaderUserId(147L, true)).thenReturn(8L);
        when(mapper.selectStaffCountryRegion(147L)).thenReturn("CN");
        when(mapper.selectNextStaffCostVersion(147L)).thenReturn(1);

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L); input.setCostMode("DAILY"); input.setUnitCost(new BigDecimal("10000"));
        input.setCurrency("USD"); input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 8L, "boss8", true);

        assertEquals("MONTHLY", saved.getCostMode());
        assertEquals("CNY", saved.getCurrency());
        assertEquals("CN", saved.getCountryRegion());
        assertEquals(new BigDecimal("21.75"), saved.getStandardWorkDays());
        verify(mapper).insertStaffCostPolicy(saved);
    }

    @Test
    void vietnamStaffMonthlyCostUsesTwentySixDays()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "越南员工");
        when(mapper.selectActiveUserById(148L)).thenReturn(staff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyLeaderUserId(148L, true)).thenReturn(8L);
        when(mapper.selectStaffCountryRegion(148L)).thenReturn("VN");
        when(mapper.selectNextStaffCostVersion(148L)).thenReturn(3);

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(148L); input.setUnitCost(new BigDecimal("13000"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        BusinessStaffCostPolicy saved = service.saveStaffCostPolicy(input, 8L, "boss8", true);

        assertEquals("CNY", saved.getCurrency());
        assertEquals("VN", saved.getCountryRegion());
        assertEquals(new BigDecimal("26"), saved.getStandardWorkDays());
        assertEquals(Integer.valueOf(3), saved.getPolicyVersion());
    }

    @Test
    void unsupportedStaffRegionCannotCreateMonthlyCost()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "其他地区员工");
        when(mapper.selectActiveUserById(149L)).thenReturn(staff);
        when(mapper.countUserRoleByKey(8L, "company_owner")).thenReturn(1);
        when(mapper.selectStaffCompanyLeaderUserId(149L, true)).thenReturn(8L);
        when(mapper.selectStaffCountryRegion(149L)).thenReturn("OTHER");
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(149L); input.setUnitCost(new BigDecimal("9000"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-19"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 8L, "boss8", true));

        assertTrue(error.getMessage().contains("中国或越南"));
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
        when(mapper.selectStaffCompanyLeaderUserId(147L, false)).thenReturn(120L);
        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.singletonList(policy));

        List<BusinessStaffCostPolicy> result = service.staffCostPolicies(147L, 120L, true);

        assertEquals(1, result.size());
        assertEquals(Long.valueOf(21L), result.get(0).getPolicyId());
    }

    @Test
    void companyOwnerCannotReadForeignCompanyStaffCostPolicies()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.countUserRoleByKey(143L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyLeaderUserId(147L, false)).thenReturn(120L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.staffCostPolicies(147L, 143L, true));

        assertTrue(error.getMessage().contains("本人负责公司"));
        verify(mapper, never()).selectStaffCostPolicies(147L);
    }

    @Test
    void companyOwnerCannotWriteForeignCompanyStaffCostPolicy()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.countUserRoleByKey(143L, "company_owner")).thenReturn(1);
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCompanyLeaderUserId(147L, true)).thenReturn(120L);
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L); input.setUnitCost(new BigDecimal("10000"));
        input.setEffectiveFrom(java.sql.Date.valueOf("2026-08-20"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 143L, "wangfuzhang", true));

        assertTrue(error.getMessage().contains("本人负责公司"));
        verify(mapper, never()).closeOpenEndedStaffCostPolicy(any(), any());
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void hardcodedBossFlagCannotBypassCompanyOwnerRole()
    {
        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 88L, "staff88", true));

        assertTrue(error.getMessage().contains("公司负责人"));
        verify(mapper, never()).selectActiveUserById(any());
        verify(mapper, never()).insertStaffCostPolicy(any());
    }

    @Test
    void administratorMayAuditButCannotWriteStaffCostPolicy()
    {
        Map<String, Object> staff = new HashMap<String, Object>();
        staff.put("nickName", "上海员工");
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectStaffCostPolicies(147L)).thenReturn(Collections.emptyList());

        assertEquals(0, service.staffCostPolicies(147L, 1L, true).size());

        BusinessStaffCostPolicy input = new BusinessStaffCostPolicy();
        input.setUserId(147L);
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveStaffCostPolicy(input, 1L, "admin", true));
        assertTrue(error.getMessage().contains("只能审计"));
        verify(mapper, never()).insertStaffCostPolicy(any());
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
    void bossCannotRetireProjectOwnersAllocation()
    {
        BusinessProject project=project(86L,9L,"ACTIVE","APPROVED");
        project.setInitiatorUserId(8L);
        when(mapper.selectProjectById(86L)).thenReturn(project);

        ServiceException error=assertThrows(ServiceException.class,
            ()->service.removeStaffAllocation(86L,501L,8L,"boss8",true));

        assertTrue(error.getMessage().contains("只有项目主负责人"));
        verify(mapper,never()).voidProjectStaffAllocation(any(),any(),any());
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
    void projectOwnerMarksTodayLeaveAndRecalculatesEveryAllocatedProject()
    {
        BusinessProject project = project(90L,9L,"ACTIVE","APPROVED");
        when(mapper.selectProjectById(90L)).thenReturn(project);
        when(mapper.selectMemberRole(90L,9L)).thenReturn("OWNER");
        when(mapper.selectMemberRole(90L,147L)).thenReturn("MEMBER");
        Map<String,Object> staff = new HashMap<String,Object>(); staff.put("nickName","石头");
        Map<String,Object> owner = new HashMap<String,Object>(); owner.put("nickName","蒋豪");
        when(mapper.selectActiveUserById(147L)).thenReturn(staff);
        when(mapper.selectActiveUserById(9L)).thenReturn(owner);
        when(mapper.selectAllocatedProjectIdsForUserDate(org.mockito.ArgumentMatchers.eq(147L),any()))
            .thenReturn(Arrays.asList(90L,91L));
        Map<String,Object> stored = new HashMap<String,Object>(); stored.put("status","ACTIVE");
        when(mapper.selectStaffLeave(org.mockito.ArgumentMatchers.eq(147L),any())).thenReturn(stored);

        Map<String,Object> result = service.markMemberLeave(90L,147L,new Date(),"病假",
            9L,"owner9",false);

        assertEquals(stored,result);
        verify(mapper).upsertStaffLeave(any());
        verify(mapper,never()).countEffectiveProjectAllocation(any(),any(),any());
        verify(accountingService).recalculatePersonnelCost(org.mockito.ArgumentMatchers.eq(90L),any(),
            org.mockito.ArgumentMatchers.eq("owner9"));
        verify(accountingService).recalculatePersonnelCost(org.mockito.ArgumentMatchers.eq(91L),any(),
            org.mockito.ArgumentMatchers.eq("owner9"));
        verify(mapper).insertEvent(any());
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

    private BusinessProject project(Long id, Long ownerId, String status, String baselineStatus)
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(id);
        project.setMainOwnerUserId(ownerId);
        project.setMainOwnerName("owner");
        project.setStatus(status);
        project.setBaselineStatus(baselineStatus);
        project.setManagementMode("SIMPLE");
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
