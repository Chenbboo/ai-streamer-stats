package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BusinessProjectProposalControllerPermissionTest
{
    @Test
    void proposalEndpointsUseApplicantAndReviewerPermissions()
    {
        Map<String,String> expected = new LinkedHashMap<String,String>();
        expected.put("list","@ss.hasPermi('business:project:proposal:list')");
        expected.put("detail","@ss.hasPermi('business:project:proposal:list')");
        expected.put("options","@ss.hasPermi('business:project:proposal:list')");
        expected.put("add","@ss.hasPermi('business:project:proposal:add')");
        expected.put("edit","@ss.hasPermi('business:project:proposal:edit')");
        expected.put("delete","@ss.hasPermi('business:project:proposal:edit')");
        expected.put("submit","@ss.hasPermi('business:project:proposal:submit')");
        expected.put("withdraw","@ss.hasPermi('business:project:proposal:submit')");
        expected.put("reviewList","@ss.hasPermi('business:project:proposal:review')");
        expected.put("directory","@ss.hasPermi('business:project:proposal:review')");
        expected.put("review","@ss.hasPermi('business:project:proposal:review')");

        for (Map.Entry<String,String> item : expected.entrySet())
        {
            Method method = Arrays.stream(BusinessProjectProposalController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(item.getKey())).findFirst()
                .orElseThrow(() -> new AssertionError("找不到端点：" + item.getKey()));
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertNotNull(authorization,item.getKey()+" 缺少权限保护");
            assertEquals(item.getValue(),authorization.value());
        }
    }
}
