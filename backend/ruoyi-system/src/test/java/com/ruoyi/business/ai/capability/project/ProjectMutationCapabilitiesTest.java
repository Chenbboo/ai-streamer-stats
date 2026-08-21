package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ProjectMutationCapabilitiesTest
{
    @Mock private IBusinessProjectService projectService;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setUp()
    {
        SysUser user = new SysUser(); user.setUserId(23L); user.setUserName("jianglan");
        AiExecutionContext actor = AiExecutionContext.from(
            new LoginUser(23L, 100L, user, Collections.singleton("*:*:*")));
        invocation = new AiCapabilityInvocation(actor, 7L, 8L, 9L);
    }

    @Test
    void budgetCapabilityValidatesConfirmationAndUsesExactConfirmedValues()
    {
        UpdateProjectBudgetCapability capability = new UpdateProjectBudgetCapability(projectService);
        BusinessProject current = project("旧项目", "ACTIVE"); current.setBudgetLimit(new BigDecimal("5000"));
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(current);
        Map<String, Object> input = map("projectId", 16L, "budgetLimit", "8000.50", "currency", "cny",
            "reason", "新增拍摄批次");

        String summary = capability.confirmationSummary(invocation, input);
        assertEquals(true, summary.contains("8000.50 CNY"), summary);
        verify(projectService, never()).updateBudget(any(), any(), any(), any(), any(), any(), any(Boolean.class));

        BusinessProject saved = project("旧项目", "ACTIVE"); saved.setProjectId(16L); saved.setProjectNo("XM16");
        when(projectService.updateBudget(16L, new BigDecimal("8000.50"), "CNY", "新增拍摄批次",
            23L, "jianglan", true)).thenReturn(saved);
        Map<String, Object> result = capability.executeConfirmed(invocation, input);
        assertEquals(new BigDecimal("8000.50"), result.get("budgetLimit"));
        verify(projectService).updateBudget(16L, new BigDecimal("8000.50"), "CNY", "新增拍摄批次",
            23L, "jianglan", true);
    }

    @Test
    void budgetCapabilityRejectsNegativeAmountBlankReasonAndInvalidCurrency()
    {
        UpdateProjectBudgetCapability capability = new UpdateProjectBudgetCapability(projectService);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(project("项目A", "ACTIVE"));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("projectId", 16L, "budgetLimit", -1, "currency", "CNY", "reason", "调整")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("projectId", 16L, "budgetLimit", 1, "currency", "CN", "reason", "调整")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("projectId", 16L, "budgetLimit", 1, "currency", "CNY", "reason", "")));
        verify(projectService, never()).updateBudget(any(), any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void destructiveProjectTransitionsRequireExplanationBeforeConfirmation()
    {
        TransitionProjectCapability capability = new TransitionProjectCapability(projectService);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(project("项目A", "ACTIVE"));

        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("projectId", 16L, "action", "PAUSE", "comment", "")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("projectId", 16L, "action", "DROP_DATABASE", "comment", "x")));
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void ownerChangeUsesStableDirectoryIdAndCannotExecuteDuringSummary()
    {
        ChangeProjectOwnerCapability capability = new ChangeProjectOwnerCapability(projectService);
        BusinessProject current = project("项目A", "ACTIVE"); current.setMainOwnerName("旧负责人");
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(current);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(
            map("userId", 66L, "nickName", "新负责人")));
        Map<String, Object> input = map("projectId", 16L, "newOwnerUserId", 66L, "reason", "职责调整");

        assertEquals(true, capability.confirmationSummary(invocation, input).contains("新负责人"));
        verify(projectService, never()).changeOwner(any(), any(), any(), any(), any(), any(Boolean.class));

        BusinessProject saved = project("项目A", "ACTIVE"); saved.setMainOwnerName("新负责人");
        when(projectService.changeOwner(16L, 66L, "职责调整", 23L, "jianglan", true)).thenReturn(saved);
        assertEquals("新负责人", capability.executeConfirmed(invocation, input).get("mainOwnerName"));
    }

    @Test
    void projectUpdateOverlaysOnlyExplicitFieldsAndKeepsSensitiveFieldsUntouched()
    {
        UpdateProjectCapability capability = new UpdateProjectCapability(projectService);
        BusinessProject current = project("旧名称", "ACTIVE");
        current.setMainOwnerUserId(55L); current.setBudgetLimit(new BigDecimal("9000")); current.setObjective("旧目标");
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(current);
        when(projectService.updateProject(any(BusinessProject.class), eq(23L), eq("jianglan"), eq(true)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> input = map("projectId", 16L, "projectName", "新名称", "priority", "high");

        capability.executeConfirmed(invocation, input);

        ArgumentCaptor<BusinessProject> captor = ArgumentCaptor.forClass(BusinessProject.class);
        verify(projectService).updateProject(captor.capture(), eq(23L), eq("jianglan"), eq(true));
        assertEquals("新名称", captor.getValue().getProjectName());
        assertEquals("HIGH", captor.getValue().getPriority());
        assertEquals(55L, captor.getValue().getMainOwnerUserId());
        assertEquals(new BigDecimal("9000"), captor.getValue().getBudgetLimit());
        assertEquals("旧目标", captor.getValue().getObjective());
    }

    @Test
    void memberSaveAndRemoveCarryStableIdsToDomainService()
    {
        SaveProjectMemberCapability save = new SaveProjectMemberCapability(projectService);
        RemoveProjectMemberCapability remove = new RemoveProjectMemberCapability(projectService);
        Map<String, Object> input = map("projectId", 16L, "staffUserId", 66L, "staffName", "石头",
            "memberRole", "member", "joinedDate", "2026-08-18");
        BusinessProjectMember saved = new BusinessProjectMember(); saved.setProjectId(16L); saved.setUserId(66L);
        saved.setUserNameSnapshot("石头"); saved.setMemberRole("MEMBER");
        when(projectService.saveMember(any(BusinessProjectMember.class), eq(23L), eq("jianglan"), eq(true)))
            .thenReturn(saved);

        save.executeConfirmed(invocation, input);
        ArgumentCaptor<BusinessProjectMember> captor = ArgumentCaptor.forClass(BusinessProjectMember.class);
        verify(projectService).saveMember(captor.capture(), eq(23L), eq("jianglan"), eq(true));
        assertEquals(16L, captor.getValue().getProjectId());
        assertEquals(66L, captor.getValue().getUserId());
        assertEquals("MEMBER", captor.getValue().getMemberRole());

        assertEquals("REMOVED", remove.executeConfirmed(invocation, input).get("status"));
        verify(projectService).removeMember(16L, 66L, 23L, "jianglan", true);
    }

    @Test
    void kpiSaveAndRetirePreserveVersionedHistoryContract()
    {
        SaveProjectKpiCapability save = new SaveProjectKpiCapability(projectService);
        RetireProjectKpiCapability retire = new RetireProjectKpiCapability(projectService);
        Map<String, Object> input = map("projectId", 16L, "kpiId", 70L, "kpiName", "交付量",
            "targetValue", 1000, "unit", "条", "metricType", "count", "periodType", "project");
        BusinessProjectKpi saved = new BusinessProjectKpi(); saved.setProjectId(16L); saved.setKpiId(71L);
        saved.setKpiName("交付量"); saved.setTargetValue(new BigDecimal("1000")); saved.setTargetVersion(2);
        when(projectService.saveKpi(any(BusinessProjectKpi.class), eq(23L), eq("jianglan"), eq(true)))
            .thenReturn(saved);

        assertEquals(2, save.executeConfirmed(invocation, input).get("targetVersion"));
        assertEquals(true, retire.confirmationSummary(invocation, input).contains("历史版本继续保留"));
        assertEquals("RETIRED", retire.executeConfirmed(invocation, input).get("status"));
        verify(projectService).retireKpi(16L, 70L, 23L, "jianglan", true);
    }

    @Test
    void allocationSaveAndRetireUseTheConfirmedPersonProjectAndEffectiveDate()
    {
        SaveProjectAllocationCapability save = new SaveProjectAllocationCapability(projectService);
        RetireProjectAllocationCapability retire = new RetireProjectAllocationCapability(projectService);
        Map<String, Object> input = map("projectId", 16L, "allocationId", 90L, "staffUserId", 66L,
            "allocationValue", 30, "effectiveFrom", "2026-08-18");
        BusinessProjectStaffAllocation saved = new BusinessProjectStaffAllocation(); saved.setAllocationId(91L);
        saved.setProjectId(16L); saved.setUserId(66L); saved.setAllocationMode("PERCENTAGE");
        saved.setAllocationValue(new BigDecimal("30")); saved.setVersion(2);
        when(projectService.saveStaffAllocation(any(BusinessProjectStaffAllocation.class), eq(23L), eq("jianglan"), eq(true)))
            .thenReturn(saved);

        assertEquals(91L, save.executeConfirmed(invocation, input).get("allocationId"));
        ArgumentCaptor<BusinessProjectStaffAllocation> captor = ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);
        verify(projectService).saveStaffAllocation(captor.capture(), eq(23L), eq("jianglan"), eq(true));
        assertEquals("PERCENTAGE", captor.getValue().getAllocationMode());
        assertEquals(new BigDecimal("30"), captor.getValue().getAllocationValue());

        assertEquals("RETIRED", retire.executeConfirmed(invocation, input).get("status"));
        verify(projectService).removeStaffAllocation(16L, 90L, 23L, "jianglan", true);
    }

    @Test
    void taskRemovalDoesNotRunUntilConfirmedAndUsesExactTaskId()
    {
        RemoveProjectTaskCapability capability = new RemoveProjectTaskCapability(projectService);
        Map<String, Object> input = map("projectId", 16L, "taskId", 701L, "taskName", "最终交付");
        assertEquals(true, capability.confirmationSummary(invocation, input).contains("最终交付"));
        verify(projectService, never()).deleteTask(any(), any(), any(), any(Boolean.class));
        assertEquals("REMOVED", capability.executeConfirmed(invocation, input).get("status"));
        verify(projectService).deleteTask(16L, 701L, 23L, true);
    }

    private BusinessProject project(String name, String status)
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectNo("XM16"); project.setProjectName(name); project.setStatus(status);
        return project;
    }

    private Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
}
