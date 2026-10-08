package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.ruoyi.business.domain.BusinessIncentiveRule;

class BusinessIncentiveControllerPermissionTest
{
    @Test void allEndpointsHaveExplicitAuthorizationAndApprovalsHaveSeparatePermission() throws Exception
    {
        for (Method method : BusinessIncentiveController.class.getDeclaredMethods())
            if (method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class))
                assertNotNull(method.getAnnotation(PreAuthorize.class), method.getName());
        assertEquals("@ss.hasPermi('business:incentive:approve')", BusinessIncentiveController.class
            .getMethod("review",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('business:incentive:approve')", BusinessIncentiveController.class
            .getMethod("resubmitCost",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
    }

    @Test void kpiManagersCanConfigureBonusesWithoutGainingAwardApprovalPermission() throws Exception
    {
        String config="@ss.hasAnyPermi('business:incentive:rule,business:kpi:manage')";
        assertEquals(config,BusinessIncentiveController.class.getMethod("publishRule",BusinessIncentiveRule.class).getAnnotation(PreAuthorize.class).value());
        assertEquals(config,BusinessIncentiveController.class.getMethod("retireRule",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('business:incentive:approve')",BusinessIncentiveController.class.getMethod("review",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
    }
}
