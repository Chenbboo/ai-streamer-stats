package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.read.ProjectAcceptanceReviewCapability;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessProjectKpiService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

class ProjectAcceptanceCapabilitiesTest
{
    private IBusinessProjectService service;
    private IBusinessProjectKpiService kpiService;
    private ProjectAcceptanceCapabilitySupport support;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setUp()
    {
        service = mock(IBusinessProjectService.class);
        kpiService = mock(IBusinessProjectKpiService.class);
        support = new ProjectAcceptanceCapabilitySupport(service, kpiService);
        invocation = new AiCapabilityInvocation(AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
        when(kpiService.workspace(17L, null, 23L, true, true)).thenReturn(kpiWorkspace("CONFIRMED"));
    }

    @Test
    void reviewCapabilityReturnsDeterministicAcceptanceFacts()
    {
        when(service.getProject(17L, 23L, true, true)).thenReturn(project(true, false));
        ProjectAcceptanceReviewCapability capability = new ProjectAcceptanceReviewCapability(support);
        Map<String, Object> input = Collections.<String, Object>singletonMap("projectId", 17L);

        Map<String, Object> result = capability.execute(invocation, input);

        assertEquals("project.acceptance.review", capability.code());
        assertTrue(Boolean.TRUE.equals(result.get("canApprove")));
        assertEquals(1, result.get("completedTaskCount"));
        assertEquals(2, result.get("attachmentCount"));
        assertEquals("交付1000条合格视频", map(result.get("acceptance")).get("resultSummary"));
    }

    @Test
    void approvalIsPreparedThenRevalidatedOnFinalConfirmation()
    {
        BusinessProject detail = project(true, false);
        BusinessProject closed = project(true, false); closed.setStatus("CLOSED");
        when(service.getProject(17L, 23L, true, true)).thenReturn(detail);
        when(service.reviewAcceptance(17L, "APPROVED", "验收通过", 23L, "jianglan", true)).thenReturn(closed);
        DecideProjectAcceptanceCapability capability = new DecideProjectAcceptanceCapability(support);
        Map<String, Object> input = input("APPROVED", "验收通过");

        assertTrue(capability.confirmationSummary(invocation, input).contains("正式结项"));
        Map<String, Object> result = capability.executeConfirmed(invocation, input);

        assertEquals("CLOSED", result.get("status"));
        assertEquals("APPROVED", result.get("decision"));
        verify(service).reviewAcceptance(17L, "APPROVED", "验收通过", 23L, "jianglan", true);
    }

    @Test
    void returnRequiresAConcreteReason()
    {
        when(service.getProject(17L, 23L, true, true)).thenReturn(project(true, false));
        DecideProjectAcceptanceCapability capability = new DecideProjectAcceptanceCapability(support);

        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, input("RETURNED", "")));
        verify(service, never()).reviewAcceptance(17L, "RETURNED", "", 23L, "jianglan", true);
    }

    @Test
    void approvalIsBlockedWhenHighRiskRemainsOpen()
    {
        when(service.getProject(17L, 23L, true, true)).thenReturn(project(true, true));
        DecideProjectAcceptanceCapability capability = new DecideProjectAcceptanceCapability(support);

        Map<String, Object> review = support.review(invocation, 17L);
        assertFalse(Boolean.TRUE.equals(review.get("canApprove")));
        assertThrows(ServiceException.class,
            () -> capability.confirmationSummary(invocation, input("APPROVED", "验收通过")));
        verify(service, never()).reviewAcceptance(17L, "APPROVED", "验收通过", 23L, "jianglan", true);
    }

    @Test
    void approvalIsBlockedUntilKpiSettlementIsConfirmed()
    {
        when(service.getProject(17L, 23L, true, true)).thenReturn(project(true, false));
        when(kpiService.workspace(17L, null, 23L, true, true)).thenReturn(kpiWorkspace("SUBMITTED"));
        DecideProjectAcceptanceCapability capability = new DecideProjectAcceptanceCapability(support);

        Map<String, Object> review = support.review(invocation, 17L);

        assertFalse(Boolean.TRUE.equals(review.get("canApprove")));
        assertFalse(Boolean.TRUE.equals(review.get("kpiReadyForClose")));
        assertTrue(String.valueOf(review.get("warnings")).contains("KPI周期"));
        assertThrows(ServiceException.class,
            () -> capability.confirmationSummary(invocation, input("APPROVED", "验收通过")));
        verify(service, never()).reviewAcceptance(17L, "APPROVED", "验收通过", 23L, "jianglan", true);
    }

    private Map<String, Object> input(String decision, String comment)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", 17L); result.put("decision", decision); result.put("comment", comment);
        return result;
    }

    private BusinessProject project(boolean taskDone, boolean openHighRisk)
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(17L); project.setProjectNo("XM17"); project.setProjectName("王老吉视频宣传");
        project.setCompanyName("上海美丸文化公司"); project.setMainOwnerName("石头");
        project.setObjective("交付1000条合格视频"); project.setStatus("ACCEPTANCE"); project.setManagementMode("DELIVERY");
        BusinessProjectTask task = new BusinessProjectTask(); task.setTaskId(91L); task.setStatus(taskDone ? "DONE" : "IN_PROGRESS");
        project.setTasks(Collections.singletonList(task)); project.setMilestones(Collections.emptyList());
        if (openHighRisk)
        {
            BusinessProjectRisk risk = new BusinessProjectRisk(); risk.setRiskId(71L); risk.setStatus("OPEN"); risk.setSeverity("HIGH");
            project.setRisks(Collections.singletonList(risk));
        }
        else project.setRisks(Collections.emptyList());
        BusinessProjectAcceptance acceptance = new BusinessProjectAcceptance();
        acceptance.setAcceptanceId(33L); acceptance.setSubmissionVersion(2); acceptance.setReviewStatus("PENDING");
        acceptance.setResultSummary("交付1000条合格视频"); acceptance.setDeliverables("视频文件和交付清单");
        acceptance.setAttachmentUrls("/upload/a.png,/upload/b.pdf"); acceptance.setSubmittedUserName("石头");
        project.setAcceptances(Arrays.asList(acceptance));
        return project;
    }

    private Map<String, Object> kpiWorkspace(String settlementStatus)
    {
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("status", "PUBLISHED");
        plan.put("settlementStatus", settlementStatus);
        Map<String, Object> workspace = new LinkedHashMap<String, Object>();
        workspace.put("plans", Collections.singletonList(plan));
        return workspace;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
