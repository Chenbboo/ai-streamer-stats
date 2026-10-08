package com.ruoyi.business.ai.capability.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;

@ExtendWith(MockitoExtension.class)
class BossReadCapabilitiesTest
{
    @Mock private IBusinessProjectService projectService;
    @Mock private IBusinessStaffService staffService;
    @Mock private IBusinessAccountingService accountingService;

    @Test
    @SuppressWarnings("unchecked")
    void pendingDecisionsExposeStableIdsAndSafeDecisionMeaning()
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(17L); project.setProjectName("王老吉视频宣传");
        project.setStatus("PLANNING"); project.setBaselineStatus("SUBMITTED");
        Map<String, Object> task = row("taskId", 91L, "taskName", "整理交付清单",
            "projectId", 17L, "privateColumn", "must-not-leak");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(project));
        dashboard.put("tasks", Collections.singletonList(task));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> result = new PendingDecisionsCapability(projectService, new ObjectMapper())
            .execute(invocation(), Collections.<String, Object>emptyMap());

        List<Map<String, Object>> decisions = (List<Map<String, Object>>) result.get("decisions");
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.get("tasks");
        assertEquals(17L, decisions.get(0).get("projectId"));
        assertEquals("PLAN_APPROVAL", decisions.get(0).get("decisionType"));
        assertEquals(91L, tasks.get(0).get("taskId"));
        assertEquals(false, tasks.get(0).containsKey("privateColumn"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffOverviewIsCalculatedByTheServerInsteadOfTheModel()
    {
        when(staffService.listOptions()).thenReturn(Arrays.asList(
            row("companyName", "上海美丸文化公司", "deptName", "内容部"),
            row("companyName", "上海美丸文化公司", "deptName", "内容部"),
            row("companyName", "越南meimaru公司", "deptName", "直播部")));

        Map<String, Object> result = new StaffOverviewCapability(staffService)
            .execute(invocation(), Collections.<String, Object>emptyMap());

        assertEquals(3, result.get("staffCount"));
        assertEquals(2, ((Map<String, Integer>) result.get("companyCounts")).get("上海美丸文化公司"));
        assertEquals(1, ((Map<String, Integer>) result.get("departmentCounts")).get("直播部"));
    }

    @Test
    void accountingResultsAcceptProjectAndDateFiltersAndReturnResultDirectory()
    {
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("summary", row("profitAmount", "88.00"));
        dashboard.put("results", Collections.singletonList(row("resultId", 501L, "projectId", 17L)));
        when(accountingService.dashboard(any(), eq(23L), eq(false))).thenReturn(dashboard);
        Map<String, Object> input = row("projectId", 17L, "dateFrom", "2026-08-01", "dateTo", "2026-08-14");

        Map<String, Object> result = new AccountingResultsCapability(accountingService).execute(invocation(), input);

        assertEquals(dashboard.get("results"), result.get("results"));
        verify(accountingService).dashboard(eq(input), eq(23L), eq(false));
    }

    private AiCapabilityInvocation invocation()
    { return new AiCapabilityInvocation(AiExecutionContext.legacy(23L, "jianglan", false), 1L, 2L, 3L); }

    private Map<String, Object> row(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
}
