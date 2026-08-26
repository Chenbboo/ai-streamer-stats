package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessProjectControllerPermissionTest
{
    @Test
    void everyProjectEndpointDeclaresAuthorization()
    {
        long endpoints = 0;
        for (Method method : BusinessProjectController.class.getDeclaredMethods())
        {
            if (!isEndpoint(method)) continue;
            endpoints++;
            assertNotNull(method.getAnnotation(PreAuthorize.class),
                () -> method.getName() + " 缺少权限保护");
        }
        assertTrue(endpoints >= 17, "项目控制器端点数量异常，请同步更新权限测试");
    }

    @Test
    void bossDecisionsAndProjectWritesKeepDedicatedPermissions()
    {
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("list", "@ss.hasPermi('business:project:list')");
        expected.put("edit", "@ss.hasPermi('business:project:edit')");
        expected.put("changeOwner", "@ss.hasPermi('business:project:manage')");
        expected.put("submitAcceptance", "@ss.hasAnyPermi('business:project:submit,business:project:manage')");
        expected.put("reviewAcceptance", "@ss.hasPermi('business:project:manage')");
        expected.put("transition", "@ss.hasAnyPermi('business:project:submit,business:project:manage')");
        expected.put("bossDashboard", "@ss.hasPermi('business:boss:view')");
        expected.put("bossProjectDirectory", "@ss.hasPermi('business:boss:view')");
        expected.put("myDashboard", "@ss.hasPermi('business:project:list')");
        expected.put("ownerDashboard", "@ss.hasPermi('business:project:owner:view')");
        expected.put("staffCostPolicies", "@ss.hasPermi('business:staff:list')");
        expected.put("saveStaffCostPolicy", "@ss.hasPermi('business:staff:manage')");
        expected.put("saveStaffCostPolicies", "@ss.hasPermi('business:staff:manage')");
        expected.put("deleteStaffCostPolicy", "@ss.hasPermi('business:staff:manage')");
        expected.put("voidStaffCostPolicy", "@ss.hasPermi('business:staff:manage')");
        expected.put("saveStaffAllocation", "@ss.hasPermi('business:project:allocation')");
        expected.put("removeStaffAllocation", "@ss.hasPermi('business:project:allocation')");
        expected.put("confirmProjectEffortWeek", "@ss.hasPermi('business:project:allocation')");
        expected.put("confirmMemberEffort", "@ss.hasPermi('business:project:allocation')");
        expected.put("returnMemberEffort", "@ss.hasPermi('business:project:allocation')");
        expected.put("markMemberLeave", "@ss.hasPermi('business:project:allocation')");
        expected.put("cancelMemberLeave", "@ss.hasPermi('business:project:allocation')");

        for (Map.Entry<String, String> item : expected.entrySet())
        {
            Method method = Arrays.stream(BusinessProjectController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(item.getKey()))
                .findFirst().orElseThrow(() -> new AssertionError("找不到端点：" + item.getKey()));
            assertEquals(item.getValue(), method.getAnnotation(PreAuthorize.class).value());
        }
    }

    private boolean isEndpoint(Method method)
    {
        return method.getAnnotation(GetMapping.class) != null
            || method.getAnnotation(PostMapping.class) != null
            || method.getAnnotation(PutMapping.class) != null
            || method.getAnnotation(DeleteMapping.class) != null;
    }
}
