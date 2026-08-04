package com.ruoyi.web.controller.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class LiveControllerPermissionTest
{
    private static final Class<?>[] CONTROLLERS = {
        LiveUploadController.class,
        LiveReviewController.class,
        LiveStatsController.class,
        LiveCustomerController.class,
        LiveKpiConfigController.class,
        LiveStreamerController.class
    };

    @Test
    void everyLiveEndpointDeclaresAuthorization()
    {
        long endpointCount = 0;
        for (Class<?> controller : CONTROLLERS)
        {
            for (Method method : controller.getDeclaredMethods())
            {
                if (!isEndpoint(method))
                {
                    continue;
                }
                endpointCount++;
                assertNotNull(method.getAnnotation(PreAuthorize.class),
                    () -> controller.getSimpleName() + "." + method.getName()
                        + " 缺少 @PreAuthorize 权限保护");
            }
        }
        assertTrue(endpointCount >= 30, "直播模块端点数量异常，请检查测试是否遗漏新接口");
    }

    @Test
    void criticalWorkflowEndpointsKeepTheirDedicatedPermissions()
    {
        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put(key(LiveUploadController.class, "uploadImg"), "@ss.hasPermi('live:upload:add')");
        expected.put(key(LiveUploadController.class, "remove"), "@ss.hasPermi('live:upload:remove')");
        expected.put(key(LiveReviewController.class, "saveResult"), "@ss.hasPermi('live:review:edit')");
        expected.put(key(LiveReviewController.class, "confirm"), "@ss.hasPermi('live:review:confirm')");
        expected.put(key(LiveCustomerController.class, "merge"), "@ss.hasPermi('live:review:edit')");
        expected.put(key(LiveKpiConfigController.class, "add"), "@ss.hasPermi('live:stats:add')");
        expected.put(key(LiveStreamerController.class, "add"), "@ss.hasPermi('live:streamer:add')");
        expected.put(key(LiveStreamerController.class, "edit"), "@ss.hasPermi('live:streamer:edit')");

        for (Map.Entry<String, String> entry : expected.entrySet())
        {
            String[] parts = entry.getKey().split("#", 2);
            Class<?> controller = Arrays.stream(CONTROLLERS)
                .filter(type -> type.getSimpleName().equals(parts[0]))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到控制器：" + parts[0]));
            Method method = findMethod(controller, parts[1]);
            assertEquals(entry.getValue(), method.getAnnotation(PreAuthorize.class).value(),
                entry.getKey() + " 的权限标识发生了非预期变化");
        }
    }

    private String key(Class<?> controller, String methodName)
    {
        return controller.getSimpleName() + "#" + methodName;
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

    private Method findMethod(Class<?> controller, String name)
    {
        return Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到控制器方法：" + controller.getSimpleName() + "." + name));
    }
}
