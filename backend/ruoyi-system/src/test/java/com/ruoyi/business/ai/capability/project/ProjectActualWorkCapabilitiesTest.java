package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityActionService;
import com.ruoyi.business.ai.capability.AiCapabilityExecutor;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRegistry;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ProjectActualWorkCapabilitiesTest
{
    @Mock IBusinessProjectService service;
    @Mock BusinessAiMapper mapper;
    private AiCapabilityInvocation invocation;
    private BusinessProject project;
    private List<AiConfirmableCapability> oldCapabilities;
    private Map<String,Object> input;

    @BeforeEach void setup()
    {
        invocation = new AiCapabilityInvocation(AiExecutionContext.legacy(8L, "boss", true), 1L, 2L, 3L);
        project = new BusinessProject(); project.setProjectId(17L); project.setProjectName("标准项目");
        project.setStatus("ACTIVE"); project.setCostPolicyVersion("ACTUAL_WORK_V1");
        project.setDeliveryPolicyVersion("SEPARATED_V1"); project.setAccountingState("OPEN");
        lenient().when(service.getProject(17L, 8L, true, true)).thenReturn(project);
        oldCapabilities = Arrays.asList(new UpdateProjectBudgetCapability(service),
            new SaveProjectAllocationCapability(service), new RetireProjectAllocationCapability(service),
            new ConfirmProjectEffortCapability(service), new ReviewProjectMemberEffortCapability(service));
        input = new LinkedHashMap<String,Object>(); input.put("projectId", 17L); input.put("staffUserId", 9L);
        input.put("memberUserId", 9L); input.put("memberName", "执行人"); input.put("projectName", "标准项目");
        input.put("allocationId", 31L); input.put("allocationValue", 50); input.put("effectiveFrom", "2026-08-01");
        input.put("anchorDate", "2026-08-01"); input.put("bizDate", "2026-08-01"); input.put("decision", "CONFIRM");
        input.put("budgetLimit", 5000); input.put("currency", "CNY"); input.put("reason", "申请调整");
    }

    @Test void newProjectCannotPrepareAnyOldBudgetAllocationOrEffortCard()
    {
        for (AiConfirmableCapability capability : oldCapabilities)
        {
            AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.<AiCapability>singletonList(capability));
            AiCapabilityActionService actions = new AiCapabilityActionService(registry, mapper, new ObjectMapper());
            ServiceException error = assertThrows(ServiceException.class,
                () -> new AiCapabilityExecutor(registry, actions).execute(capability.code(), invocation, input), capability.code());
            assertTrue(error.getMessage().contains(capability instanceof UpdateProjectBudgetCapability ? "计划变更" : "资源与实际工作"));
        }
        verifyNoInteractions(mapper); verifyNoDomainWrites();
    }

    @Test void existingCardsRecheckActualPolicyAtConfirmationTime() throws Exception
    {
        for (AiConfirmableCapability capability : oldCapabilities)
        {
            project.setCostPolicyVersion("PERCENTAGE_V1");
            assertNotNull(capability.confirmationSummary(invocation, input));
            Map<String,Object> persisted = capability.persistedInput(invocation, input);
            project.setCostPolicyVersion("ACTUAL_WORK_V1");
            Map<String,Object> action = new LinkedHashMap<String,Object>();
            action.put("actionCode", "CAPABILITY:" + capability.code()); action.put("conversationId", 1L); action.put("runId", 2L);
            action.put("actionPayloadJson", new ObjectMapper().writeValueAsString(Collections.singletonMap("input", persisted)));
            AiCapabilityActionService actions = new AiCapabilityActionService(
                new AiCapabilityRegistry(Collections.<AiCapability>singletonList(capability)), mapper, new ObjectMapper());
            assertThrows(ServiceException.class, () -> actions.executeConfirmed(action, invocation.getActor()), capability.code());
        }
        verifyNoDomainWrites(); verifyNoInteractions(mapper);
    }

    @Test void arbitraryOrMissingProjectCannotCreateConfirmationCard()
    {
        when(service.getProject(17L, 8L, true, true)).thenThrow(new ServiceException("其他公司的项目不可访问"));
        for (AiConfirmableCapability capability : oldCapabilities)
            assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, input));
        input.remove("projectId");
        for (AiConfirmableCapability capability : oldCapabilities)
            assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, input));
        verifyNoDomainWrites();
    }

    @Test void unknownCostPolicyAndClosedAccountingFailBeforeOldWrites()
    {
        project.setCostPolicyVersion("FUTURE_V9");
        for (AiConfirmableCapability capability : oldCapabilities)
            assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, input));
        project.setCostPolicyVersion("PERCENTAGE_V1"); project.setAccountingState("CLOSED");
        for (AiConfirmableCapability capability : oldCapabilities)
            assertThrows(ServiceException.class, () -> capability.executeConfirmed(invocation, input));
        verifyNoDomainWrites();
    }

    @Test void legacyWeekCardStillDelegatesToDomainAndDoesNotHideDomainRejection()
    {
        project.setCostPolicyVersion("PERCENTAGE_V1");
        when(service.confirmProjectEffortWeek(17L, "2026-08-01", 8L, "boss", true))
            .thenThrow(new ServiceException("锁定项目后发现核算已关闭"));
        ConfirmProjectEffortCapability capability = new ConfirmProjectEffortCapability(service);
        assertNotNull(capability.confirmationSummary(invocation, input));
        ServiceException error = assertThrows(ServiceException.class, () -> capability.executeConfirmed(invocation, input));
        assertTrue(error.getMessage().contains("锁定项目后"));
        verify(service).confirmProjectEffortWeek(17L, "2026-08-01", 8L, "boss", true);
    }

    @Test void genericProjectUpdateCannotBypassPlanBaselineButAllowsRename()
    {
        UpdateProjectCapability capability = new UpdateProjectCapability(service);
        for (String field : Arrays.asList("objective", "acceptanceCriteria", "planStartDate", "planEndDate", "budgetLimit"))
        {
            Map<String,Object> change = new LinkedHashMap<String,Object>(); change.put("projectId", 17L); change.put(field, "changed");
            assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation, change), field);
            assertThrows(ServiceException.class, () -> capability.executeConfirmed(invocation, change), field);
        }
        Map<String,Object> rename = new LinkedHashMap<String,Object>(); rename.put("projectId", 17L); rename.put("projectName", "新名称");
        assertTrue(capability.confirmationSummary(invocation, rename).contains("新名称"));
        verify(service, never()).updateProject(any(), anyLong(), anyString(), anyBoolean());
    }

    @Test void actualProjectPlanReviewDoesNotSuggestPercentageBasedCost()
    {
        project.setStatus("PLANNING"); project.setBaselineStatus("SUBMITTED");
        when(service.operatingConfig(17L, 8L, true, true)).thenReturn(Collections.emptyMap());
        Map<String,Object> result = new ProjectPlanCapabilitySupport(service).review(invocation, 17L);
        assertTrue(String.valueOf(result.get("checks")).contains("资源计划不生成实际成本"));
        assertFalse(String.valueOf(result.get("warnings")).contains("按计划分摊"));
    }

    private void verifyNoDomainWrites()
    {
        verify(service, never()).updateBudget(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(service, never()).saveStaffAllocation(any(), any(), any(), anyBoolean());
        verify(service, never()).removeStaffAllocation(any(), any(), any(), any(), anyBoolean());
        verify(service, never()).confirmProjectEffortWeek(any(), any(), any(), any(), anyBoolean());
        verify(service, never()).confirmMemberEffort(any(), any(), any(), any(), any(), anyBoolean());
        verify(service, never()).returnMemberEffort(any(), any(), any(), any(), any(), any(), anyBoolean());
    }
}
