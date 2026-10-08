package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessStaffControllerPermissionTest
{
    @Test
    void everyStaffEndpointDeclaresDedicatedPermission()
    {
        int endpoints = 0;
        for (Method method : BusinessStaffController.class.getDeclaredMethods())
        {
            if (method.getAnnotation(GetMapping.class) == null && method.getAnnotation(PostMapping.class) == null
                && method.getAnnotation(PutMapping.class) == null && method.getAnnotation(DeleteMapping.class) == null) continue;
            endpoints++;
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 缺少权限保护");
            String expected = method.getAnnotation(GetMapping.class) == null || "menuPermissions".equals(method.getName())
                ? "@ss.hasPermi('business:staff:manage')" : "@ss.hasPermi('business:staff:list')";
            assertEquals(expected, permission.value());
        }
        assertEquals(11, endpoints);
    }
}
