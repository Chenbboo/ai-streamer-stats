package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class BusinessAccountingControllerPermissionTest
{
    @Test
    void everyAccountingEndpointDeclaresAuthorization()
    {
        long endpoints=0;
        for(Method method:BusinessAccountingController.class.getDeclaredMethods())
        {
            if(!isEndpoint(method))continue;
            endpoints++;
            assertNotNull(method.getAnnotation(PreAuthorize.class),
                ()->method.getName()+" 缺少权限保护");
        }
        assertTrue(endpoints>=9,"每日收支控制器端点数量异常，请同步更新权限测试");
    }

    @Test
    void personnelCostOverviewRemainsBossOnly()
    {
        Method method=Arrays.stream(BusinessAccountingController.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals("personnelCostOverview"))
            .findFirst().orElseThrow(()->new AssertionError("找不到人员成本核算端点"));
        assertEquals("@ss.hasPermi('business:boss:view')",method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void projectRevenueAndSpendAreSubmitOnlyAndAccountingActionsRemainBossOnly()
    {
        assertPermission("saveProjectFact","@ss.hasPermi('business:project:report')");
        assertPermission("saveProjectDailySpend","@ss.hasPermi('business:project:report')");
        assertPermission("confirm","@ss.hasPermi('business:accounting:confirm')");
        assertPermission("reverse","@ss.hasPermi('business:accounting:confirm')");
        assertPermission("recalculate","@ss.hasPermi('business:accounting:recalculate')");
    }

    private void assertPermission(String methodName,String permission)
    {
        Method method=Arrays.stream(BusinessAccountingController.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals(methodName))
            .findFirst().orElseThrow(()->new AssertionError("找不到端点："+methodName));
        assertEquals(permission,method.getAnnotation(PreAuthorize.class).value());
    }

    private boolean isEndpoint(Method method)
    {
        return method.getAnnotation(GetMapping.class)!=null
            || method.getAnnotation(PostMapping.class)!=null
            || method.getAnnotation(PutMapping.class)!=null;
    }
}
