package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

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
}
