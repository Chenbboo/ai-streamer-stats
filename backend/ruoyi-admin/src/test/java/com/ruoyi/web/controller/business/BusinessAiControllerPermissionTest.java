package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessAiControllerPermissionTest
{
    @Test
    void everyAiEndpointRequiresBossPermission()
    {
        int endpoints = 0;
        for (Method method : BusinessAiController.class.getDeclaredMethods())
        {
            if (method.getAnnotation(GetMapping.class) == null && method.getAnnotation(PostMapping.class) == null
                && method.getAnnotation(PutMapping.class) == null) continue;
            endpoints++;
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 缺少权限保护");
            assertEquals("@ss.hasPermi('business:boss:view')", permission.value());
        }
        assertEquals(4, endpoints);
    }
}
