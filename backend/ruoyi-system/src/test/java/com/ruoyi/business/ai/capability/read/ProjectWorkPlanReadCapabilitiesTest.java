package com.ruoyi.business.ai.capability.read;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityExecutor;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRegistry;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.service.impl.BusinessProjectPlanService;
import com.ruoyi.business.service.impl.BusinessProjectWorkService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ProjectWorkPlanReadCapabilitiesTest
{
    @Mock BusinessProjectWorkService work;
    @Mock BusinessProjectPlanService plans;

    @Test void ordinaryWorkerCanReadAuthorizedWorkThroughRegistryAndExecutor()
    {
        ProjectWorkCapability capability = new ProjectWorkCapability(work);
        AiCapabilityRegistry registry = registry();
        AiCapabilityInvocation invocation = invocation(9L, "business:work:report");
        assertEquals(1, registry.allowed(invocation.getActor()).size());
        Map<String,Object> workspace = Collections.<String,Object>singletonMap("missingActualMeaning", "UNKNOWN");
        when(work.workspace(eq(17L), anyMap(), eq(9L), eq(false))).thenReturn(workspace);
        Map<String,Object> input = input(); input.put("dateFrom", "2026-08-01"); input.put("dateTo", "2026-08-07");
        input.put("userId", 1L); input.put("admin", true); input.put("viewAll", true);
        Map<String,Object> result = new AiCapabilityExecutor(registry).execute(capability.code(), invocation, input);
        assertSame(workspace, result.get("workspace")); assertEquals(AiCapabilityRisk.READ_ONLY, capability.risk());
        ArgumentCaptor<Map<String,Object>> query = ArgumentCaptor.forClass(Map.class);
        verify(work).workspace(eq(17L), query.capture(), eq(9L), eq(false));
        assertEquals(2, query.getValue().size()); assertEquals("2026-08-01", query.getValue().get("dateFrom"));
        verifyNoMoreInteractions(work); verifyNoInteractions(plans);
    }

    @Test void ownerPortalPermissionCanReadBothPlansAndWorkWithoutManagerElevation()
    {
        AiCapabilityRegistry registry = registry(); AiCapabilityInvocation invocation = invocation(9L, "business:project:owner:view");
        assertEquals(2, registry.allowed(invocation.getActor()).size());
        Map<String,Object> plan = Collections.<String,Object>singletonMap("forecast", Collections.singletonMap("forecastCost", null));
        when(plans.plan(17L, 9L, false)).thenReturn(plan);
        Map<String,Object> result = new AiCapabilityExecutor(registry).execute("project.plan.get", invocation, input());
        assertSame(plan, result.get("plan")); assertEquals(AiCapabilityRisk.READ_ONLY, registry.require("project.plan.get").risk());
        verify(plans).plan(17L, 9L, false); verifyNoMoreInteractions(plans);
    }

    @Test void permissionsAreRecheckedBeforeDirectOrModelSelectedRead()
    {
        AiCapabilityRegistry registry = registry(); AiCapabilityInvocation noAccess = invocation(9L, "business:incentive:list");
        assertTrue(registry.allowed(noAccess.getActor()).isEmpty());
        for (AiCapability capability : registry.all())
        {
            assertThrows(ServiceException.class, () -> capability.execute(noAccess, input()));
            assertThrows(ServiceException.class, () -> new AiCapabilityExecutor(registry).execute(capability.code(), noAccess, input()));
        }
        assertFalse(registry.require("project.plan.get").isAllowed(invocation(9L, "business:work:report").getActor()));
        verifyNoInteractions(work, plans);
    }

    @Test void foreignProjectServiceRejectionCannotBeReplacedWithUnscopedQuery()
    {
        when(work.workspace(eq(17L), anyMap(), eq(12L), eq(false))).thenThrow(new ServiceException("无权查看该项目工作记录"));
        when(plans.plan(17L, 12L, false)).thenThrow(new ServiceException("无权查看项目计划"));
        AiCapabilityInvocation actor = invocation(12L, "business:project:list");
        assertThrows(ServiceException.class, () -> new ProjectWorkCapability(work).execute(actor, input()));
        assertThrows(ServiceException.class, () -> new ProjectPlanCapability(plans).execute(actor, input()));
        verify(work).workspace(eq(17L), anyMap(), eq(12L), eq(false)); verify(plans).plan(17L, 12L, false);
        verifyNoMoreInteractions(work, plans);
    }

    @Test void onlyAuthenticatedTechnicalAdministratorGetsExplicitAdminReadFlag()
    {
        AiCapabilityInvocation admin = invocation(1L, "");
        new ProjectWorkCapability(work).execute(admin, input()); new ProjectPlanCapability(plans).execute(admin, input());
        verify(work).workspace(eq(17L), anyMap(), eq(1L), eq(true)); verify(plans).plan(17L, 1L, true);
    }

    @Test void malformedProjectAndDateRangesNeverReachBusinessQueries()
    {
        AiCapabilityInvocation actor = invocation(9L, "business:project:list");
        ProjectWorkCapability capability = new ProjectWorkCapability(work);
        assertThrows(ServiceException.class, () -> capability.execute(actor, Collections.emptyMap()));
        assertThrows(ServiceException.class, () -> new ProjectPlanCapability(plans).execute(actor, Collections.singletonMap("projectId", -1)));
        Map<String,Object> badDate = input(); badDate.put("dateFrom", "2026-02-30");
        assertThrows(ServiceException.class, () -> capability.execute(actor, badDate));
        badDate.put("dateFrom", "2026-09-07"); badDate.put("dateTo", "2026-09-01");
        assertThrows(ServiceException.class, () -> capability.execute(actor, badDate));
        verifyNoInteractions(work, plans);
    }

    private AiCapabilityRegistry registry()
    { return new AiCapabilityRegistry(Arrays.<AiCapability>asList(new ProjectWorkCapability(work), new ProjectPlanCapability(plans))); }
    private Map<String,Object> input()
    { return new LinkedHashMap<String,Object>(Collections.singletonMap("projectId", 17L)); }
    private AiCapabilityInvocation invocation(Long actorId, String permission)
    {
        SysUser user = new SysUser(); user.setUserId(actorId); user.setUserName("reader");
        return new AiCapabilityInvocation(AiExecutionContext.from(new LoginUser(actorId, 100L, user,
            permission.isEmpty() ? Collections.emptySet() : Collections.singleton(permission))), 1L, 2L, 3L);
    }
}
