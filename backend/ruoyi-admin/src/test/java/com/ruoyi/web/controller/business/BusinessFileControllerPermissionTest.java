package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BusinessFileControllerPermissionTest
{
    @Test
    void uploadAllowsEveryLoggedInProjectParticipantToReachProjectScopeValidation() throws Exception
    {
        Method upload = BusinessFileController.class.getDeclaredMethod("upload",
            org.springframework.web.multipart.MultipartFile.class, Long.class);
        PreAuthorize authorization = upload.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("isAuthenticated()", authorization.value());
    }
}
