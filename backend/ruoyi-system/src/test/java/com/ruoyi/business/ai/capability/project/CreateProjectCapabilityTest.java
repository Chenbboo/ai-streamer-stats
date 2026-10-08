package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;

class CreateProjectCapabilityTest
{
    private IBusinessProjectService projectService;
    private IBusinessStaffService staffService;
    private CreateProjectCapability capability;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setUp()
    {
        projectService = mock(IBusinessProjectService.class);
        staffService = mock(IBusinessStaffService.class);
        capability = new CreateProjectCapability(projectService, staffService);
        invocation = new AiCapabilityInvocation(AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
        Map<String, Object> owner = new LinkedHashMap<String, Object>();
        owner.put("userId", 81L); owner.put("nickName", "Shitou");
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(owner));
        Map<String, Object> company = new LinkedHashMap<String, Object>();
        company.put("companyDeptId", 100L); company.put("companyName", "Shanghai Company");
        when(staffService.listOptions()).thenReturn(Collections.singletonList(company));
    }

    @Test
    void confirmationValidatesStableIdsWithoutWriting()
    {
        Map<String, Object> details = capability.confirmationDetails(invocation, input());
        assertEquals("Shitou", details.get("mainOwnerName"));
        assertEquals("Shanghai Company", details.get("companyName"));
        assertTrue(capability.confirmationSummary(invocation, input()).contains("Video Project"));
        verify(projectService, never()).createProject(any(BusinessProject.class), any(Long.class), any(String.class));
    }

    @Test
    void finalConfirmationCreatesTheProject()
    {
        BusinessProject created = new BusinessProject();
        created.setProjectId(17L); created.setProjectNo("XM17"); created.setProjectName("Video Project");
        created.setStatus("DRAFT"); created.setMainOwnerUserId(81L); created.setCompanyDeptId(100L);
        when(projectService.createProject(any(BusinessProject.class), any(Long.class), any(String.class))).thenReturn(created);

        Map<String, Object> result = capability.executeConfirmed(invocation, input());

        assertEquals(17L, result.get("projectId"));
        ArgumentCaptor<BusinessProject> captor = ArgumentCaptor.forClass(BusinessProject.class);
        verify(projectService).createProject(captor.capture(), org.mockito.ArgumentMatchers.eq(23L),
            org.mockito.ArgumentMatchers.eq("jianglan"));
        assertEquals(81L, captor.getValue().getMainOwnerUserId());
        assertEquals("PROFIT", captor.getValue().getAccountingMode());
    }

    @Test
    void unknownDirectoryIdsAreRejected()
    {
        Map<String, Object> input = input(); input.put("mainOwnerUserId", 999L);
        assertThrows(ServiceException.class, () -> capability.confirmationDetails(invocation, input));
        verify(projectService, never()).createProject(any(BusinessProject.class), any(Long.class), any(String.class));
    }

    private Map<String, Object> input()
    {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("projectName", "Video Project"); input.put("mainOwnerUserId", 81L);
        input.put("companyDeptId", 100L); input.put("objective", "Deliver 1000 approved videos");
        input.put("planStartDate", "2026-08-14"); input.put("planEndDate", "2026-09-30");
        input.put("accountingMode", "PROFIT"); input.put("noBudget", true);
        return input;
    }
}
