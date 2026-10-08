package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;

class AiCapabilityRegistryTest
{
    @Test
    void onlyCapabilitiesAllowedByTheLoggedInAccountAreVisible()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Arrays.asList(
            capability("project.draft.get", "business:project:add", AiCapabilityRisk.READ_ONLY),
            capability("staff.manage", "business:staff:manage", AiCapabilityRisk.DRAFT_WRITE)));
        AiExecutionContext actor = context("business:project:add");

        assertEquals(1, registry.allowed(actor).size());
        assertEquals("project.draft.get", registry.allowed(actor).get(0).code());
    }

    @Test
    void executorChecksPermissionAgainInsteadOfTrustingTheModelToolList()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.singletonList(
            capability("staff.manage", "business:staff:manage", AiCapabilityRisk.DRAFT_WRITE)));
        AiCapabilityExecutor executor = new AiCapabilityExecutor(registry);
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(context("business:project:add"), 1L, 2L, 3L);

        assertThrows(ServiceException.class,
            () -> executor.execute("staff.manage", invocation, Collections.<String, Object>emptyMap()));
    }

    @Test
    void confirmationRequiredCapabilityCannotExecuteDirectly()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.singletonList(
            capability("project.create", "business:project:add", AiCapabilityRisk.CONFIRM_REQUIRED)));
        AiCapabilityExecutor executor = new AiCapabilityExecutor(registry);
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(context("business:project:add"), 1L, 2L, 3L);

        assertThrows(ServiceException.class,
            () -> executor.execute("project.create", invocation, Collections.<String, Object>emptyMap()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelToolDefinitionsAreGeneratedFromAllowedCapabilities()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Arrays.asList(
            capability("project.draft.get", "business:project:add", AiCapabilityRisk.READ_ONLY),
            capability("staff.manage", "business:staff:manage", AiCapabilityRisk.DRAFT_WRITE)));
        AiCapabilityToolCatalog catalog = new AiCapabilityToolCatalog(registry);

        Map<String, Object> wrapper = catalog.definitions(context("business:project:add")).get(0);
        Map<String, Object> function = (Map<String, Object>) wrapper.get("function");
        assertEquals("capability_project_draft_get", function.get("name"));
        assertEquals("project.draft.get", function.get("description"));
        assertEquals(null, catalog.findAllowedByToolName("capability_staff_manage",
            context("business:project:add")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void catalogContainsOnlyRegisteredCapabilitiesAndNoLegacyBossTools()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.singletonList(
            capability("business.pending-decisions.get", "business:boss:view", AiCapabilityRisk.READ_ONLY)));
        AiCapabilityToolCatalog catalog = new AiCapabilityToolCatalog(registry);

        Map<String, Object> wrapper = catalog.definitions(context("business:boss:view")).get(0);
        String name = String.valueOf(((Map<String, Object>) wrapper.get("function")).get("name"));
        assertEquals("capability_business_pending-decisions_get", name);
        assertTrue(catalog.definitions(context("business:boss:view")).stream()
            .noneMatch(item -> String.valueOf(((Map<String, Object>) item.get("function")).get("name"))
                .startsWith("boss_")));
    }

    @Test
    void registryRejectsDuplicateAndBlankCapabilityCodesAtStartup()
    {
        assertThrows(IllegalStateException.class, () -> new AiCapabilityRegistry(Arrays.asList(
            capability("project.detail.get", "business:project:list", AiCapabilityRisk.READ_ONLY),
            capability("project.detail.get", "business:project:list", AiCapabilityRisk.READ_ONLY))));
        assertThrows(IllegalStateException.class, () -> new AiCapabilityRegistry(Collections.singletonList(
            capability(" ", "business:project:list", AiCapabilityRisk.READ_ONLY))));
    }

    @Test
    void registryViewsCannotBeMutatedAndNullActorGetsNoCapabilities()
    {
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.singletonList(
            capability("project.detail.get", "business:project:list", AiCapabilityRisk.READ_ONLY)));
        assertEquals(0, registry.allowed(null).size());
        assertThrows(UnsupportedOperationException.class,
            () -> registry.allowed(context("business:project:list")).clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }

    private AiExecutionContext context(String permission)
    {
        com.ruoyi.common.core.domain.entity.SysUser user = new com.ruoyi.common.core.domain.entity.SysUser();
        user.setUserId(23L); user.setUserName("jianglan");
        com.ruoyi.common.core.domain.model.LoginUser login = new com.ruoyi.common.core.domain.model.LoginUser(
            23L, 100L, user, Collections.singleton(permission));
        return AiExecutionContext.from(login);
    }

    private AiCapability capability(String code, String permission, AiCapabilityRisk risk)
    {
        return new AiCapability()
        {
            @Override public String code() { return code; }
            @Override public String description() { return code; }
            @Override public String requiredPermission() { return permission; }
            @Override public AiCapabilityRisk risk() { return risk; }
            @Override public Map<String, Object> inputSchema() { return new LinkedHashMap<String, Object>(); }
            @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
            { return Collections.<String, Object>singletonMap("ok", true); }
        };
    }
}
