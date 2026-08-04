package com.ruoyi.web.controller.jewelry;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class JewelryErpControllerPermissionTest
{
    @Test
    void everyErpEndpointDeclaresAuthorization()
    {
        long endpointCount = 0;
        for (Method method : JewelryErpController.class.getDeclaredMethods())
        {
            if (!isEndpoint(method))
            {
                continue;
            }
            endpointCount++;
            assertNotNull(method.getAnnotation(PreAuthorize.class),
                () -> method.getName() + " 缺少 @PreAuthorize 权限保护");
        }
        assertTrue(endpointCount >= 18, "ERP 控制器端点数量异常，请检查测试是否遗漏新接口");
    }

    @Test
    void criticalWorkflowEndpointsKeepTheirDedicatedPermissions()
    {
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("dashboard", "@ss.hasPermi('jewelry:overview:list')");
        expected.put("staffList", "@ss.hasPermi('jewelry:staff:list')");
        expected.put("addStaff", "@ss.hasPermi('jewelry:staff:add')");
        expected.put("editStaff", "@ss.hasPermi('jewelry:staff:edit')");
        expected.put("productList", "@ss.hasPermi('jewelry:product:list')");
        expected.put("supplierList", "@ss.hasPermi('jewelry:supplier:list')");
        expected.put("stockList", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("transactions", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("documentList", "@ss.hasPermi('jewelry:document:list')");
        expected.put("document", "@ss.hasPermi('jewelry:document:list')");
        expected.put("assessDocumentRisk", "@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')");
        expected.put("submit", "@ss.hasPermi('jewelry:document:submit')");
        expected.put("withdraw", "@ss.hasPermi('jewelry:document:withdraw')");
        expected.put("reverse", "@ss.hasPermi('jewelry:document:reverse')");
        expected.put("approve", "@ss.hasPermi('jewelry:approval:approve')");
        expected.put("reject", "@ss.hasPermi('jewelry:approval:reject')");
        expected.put("calculate", "@ss.hasPermi('jewelry:calculator:list')");

        for (Map.Entry<String, String> entry : expected.entrySet())
        {
            Method method = findMethod(entry.getKey());
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertNotNull(authorization, entry.getKey() + " 缺少权限保护");
            assertEquals(entry.getValue(), authorization.value(),
                entry.getKey() + " 的权限标识发生了非预期变化");
        }
    }

    @Test
    void writeEndpointsUseSeparateCreateEditOrApprovalPermissions()
    {
        assertEquals("@ss.hasAnyPermi('jewelry:product:add,jewelry:product:edit')",
            authorization("saveProduct"));
        assertEquals("@ss.hasAnyPermi('jewelry:supplier:add,jewelry:supplier:edit')",
            authorization("saveSupplier"));
        assertEquals("@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')",
            authorization("saveDocument"));
    }

    private boolean isEndpoint(Method method)
    {
        return Arrays.stream(method.getAnnotations()).anyMatch(annotation ->
            annotation.annotationType() == GetMapping.class
                || annotation.annotationType() == PostMapping.class
                || annotation.annotationType() == PutMapping.class
                || annotation.annotationType() == DeleteMapping.class
                || annotation.annotationType() == PatchMapping.class
                || annotation.annotationType() == RequestMapping.class);
    }

    private String authorization(String methodName)
    {
        PreAuthorize annotation = findMethod(methodName).getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少权限保护");
        return annotation.value();
    }

    private Method findMethod(String name)
    {
        return Arrays.stream(JewelryErpController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到控制器方法：" + name));
    }
}
