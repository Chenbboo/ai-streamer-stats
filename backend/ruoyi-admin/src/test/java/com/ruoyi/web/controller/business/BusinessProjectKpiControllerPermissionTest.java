package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessProjectKpiControllerPermissionTest
{
    @Test
    void everyEndpointHasExpectedPermission()
    {
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("overview", "@ss.hasPermi('business:kpi:list')");
        expected.put("workspace", "@ss.hasPermi('business:kpi:list')");
        expected.put("publish", "@ss.hasPermi('business:kpi:manage')");
        expected.put("saveResults", "@ss.hasPermi('business:kpi:settle')");
        expected.put("submit", "@ss.hasPermi('business:kpi:settle')");
        expected.put("review", "@ss.hasPermi('business:kpi:manage')");
        for (Method method : BusinessProjectKpiController.class.getDeclaredMethods())
        {
            if (method.getAnnotation(GetMapping.class) == null && method.getAnnotation(PostMapping.class) == null
                && method.getAnnotation(PutMapping.class) == null) continue;
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 缺少权限保护");
            assertEquals(expected.get(method.getName()), permission.value());
        }
    }
}
