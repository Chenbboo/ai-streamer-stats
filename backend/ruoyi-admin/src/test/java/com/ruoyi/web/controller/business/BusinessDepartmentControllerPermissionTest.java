package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessDepartmentControllerPermissionTest
{
    @Test
    void everyDepartmentEndpointDeclaresDedicatedPermission()
    {
        int endpoints = 0;
        for (Method method : BusinessDepartmentController.class.getDeclaredMethods())
        {
            boolean read = method.getAnnotation(GetMapping.class) != null;
            boolean write = method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null || method.getAnnotation(DeleteMapping.class) != null;
            if (!read && !write) continue;
            endpoints++;
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 缺少权限保护");
            assertEquals(read ? "@ss.hasPermi('business:department:list')"
                : "@ss.hasPermi('business:department:manage')", permission.value());
        }
        assertEquals(6, endpoints);
    }
}
