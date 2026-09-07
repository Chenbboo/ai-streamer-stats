package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BusinessProjectWorkControllerPermissionTest
{
    @Test void readOnlyWorkspaceEntrypointsAllowOwnerAndReporterWithoutProjectListPermission()
    {
        for(Method method:BusinessProjectWorkController.class.getDeclaredMethods())
        {
            if(!java.util.Arrays.asList("options","workspace","history","plan").contains(method.getName()))continue;
            String rule=method.getAnnotation(PreAuthorize.class).value();
            assertTrue(rule.contains("hasAnyPermi"),method.getName());assertTrue(rule.contains("business:work:report"),method.getName());assertTrue(rule.contains("business:project:owner:view"),method.getName());
        }
    }
}
