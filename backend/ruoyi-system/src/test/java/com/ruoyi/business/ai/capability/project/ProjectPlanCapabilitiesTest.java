package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.read.ProjectPlanReviewCapability;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

class ProjectPlanCapabilitiesTest
{
    private IBusinessProjectService service;
    private ProjectPlanCapabilitySupport support;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setUp()
    {
        service = mock(IBusinessProjectService.class);
        support = new ProjectPlanCapabilitySupport(service);
        invocation = new AiCapabilityInvocation(AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
        when(service.getProject(17L, 23L, true, true)).thenReturn(submittedProject());
        Map<String, Object> operating = new LinkedHashMap<String, Object>();
        operating.put("kpis", Collections.emptyList()); operating.put("staffAllocations", Collections.emptyList());
        when(service.operatingConfig(17L, 23L, true, true)).thenReturn(operating);
    }

    @Test
    void reviewReturnsTheSubmittedPlanFromStableProjectId()
    {
        ProjectPlanReviewCapability capability = new ProjectPlanReviewCapability(support);
        Map<String, Object> result = capability.execute(invocation, Collections.<String, Object>singletonMap("projectId", 17L));
        assertEquals("project.plan.review", capability.code());
        assertEquals(1, result.get("routineCount"));
        assertEquals(1, result.get("memberCount"));
        assertTrue(((java.util.List<?>) result.get("warnings")).size() >= 1);
    }

    @Test
    void approvalIsOnlyExecutedAfterFinalConfirmation()
    {
        BusinessProject active = submittedProject(); active.setStatus("ACTIVE"); active.setBaselineStatus("APPROVED");
        when(service.transition(17L, "CONFIRM_BASELINE", "", 23L, "jianglan", true)).thenReturn(active);
        DecideProjectPlanCapability capability = new DecideProjectPlanCapability(support);
        Map<String, Object> input = input("APPROVE", "");

        assertTrue(capability.confirmationSummary(invocation, input).contains("Video Project"));
        verify(service, never()).transition(17L, "CONFIRM_BASELINE", "", 23L, "jianglan", true);
        Map<String, Object> result = capability.executeConfirmed(invocation, input);
        assertEquals("ACTIVE", result.get("status"));
        assertEquals("APPROVE", result.get("decision"));
    }

    @Test
    void returnRequiresAReason()
    {
        DecideProjectPlanCapability capability = new DecideProjectPlanCapability(support);
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, input("RETURN", "")));
        verify(service, never()).transition(17L, "RETURN_PLAN", "", 23L, "jianglan", true);
    }

    private Map<String, Object> input(String decision, String comment)
    {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("projectId", 17L); input.put("decision", decision); input.put("comment", comment);
        return input;
    }

    private BusinessProject submittedProject()
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(17L); project.setProjectNo("XM17"); project.setProjectName("Video Project");
        project.setCompanyName("Shanghai Company"); project.setMainOwnerName("Shitou");
        project.setObjective("Deliver 1000 approved videos"); project.setStatus("PLANNING");
        project.setBaselineStatus("SUBMITTED"); project.setTasks(Collections.emptyList());
        project.setMilestones(Collections.emptyList()); project.setRisks(Collections.emptyList());
        BusinessProjectRoutine routine = new BusinessProjectRoutine();
        routine.setRoutineId(41L); routine.setRoutineName("Publish videos"); routine.setAssigneeUserId(81L);
        project.setRoutines(Collections.singletonList(routine));
        BusinessProjectMember member = new BusinessProjectMember();
        member.setUserId(81L); member.setUserNameSnapshot("Shitou"); member.setMemberRole("OWNER");
        project.setMembers(Collections.singletonList(member));
        return project;
    }
}
