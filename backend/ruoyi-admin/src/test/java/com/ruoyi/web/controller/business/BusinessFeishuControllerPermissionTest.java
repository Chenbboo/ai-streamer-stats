package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;

class BusinessFeishuControllerPermissionTest
{
    @Test void allEndpointsRequireAuthorizationAndMutationsAvoidSensitiveRequestLogging()
    {
        for(Method method:BusinessFeishuController.class.getDeclaredMethods())
        {
            boolean mutation=method.isAnnotationPresent(PostMapping.class)||method.isAnnotationPresent(PutMapping.class);
            if(!mutation&&!method.isAnnotationPresent(GetMapping.class))continue;
            assertNotNull(method.getAnnotation(PreAuthorize.class),method.getName());
            if(mutation)
            { Log log=method.getAnnotation(Log.class);assertNotNull(log,method.getName());assertFalse(log.isSaveRequestData());assertFalse(log.isSaveResponseData()); }
            if(method.getName().equals("records")||method.getName().equals("availability"))
                assertFalse(method.getAnnotation(PreAuthorize.class).value().contains("integration"));
            if(method.getName().equals("activate")||method.getName().equals("validate"))
                assertEquals("@ss.hasPermi('business:attendance:cutover')",method.getAnnotation(PreAuthorize.class).value());
        }
    }
}
