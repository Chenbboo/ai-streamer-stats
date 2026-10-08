package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.system.service.ISysMenuService;

class AdminPersonalMenuPermissionTest
{
    @Test
    void adminLoginUsesResolvedPersonalPolicyInsteadOfUnconditionalWildcard()
    {
        ISysMenuService menus=mock(ISysMenuService.class);
        SysPermissionService permissions=new SysPermissionService();
        ReflectionTestUtils.setField(permissions,"menuService",menus);
        when(menus.selectMenuPermsByUserId(1L)).thenReturn(Collections.singleton("system:user:list"));
        assertEquals(Collections.singleton("system:user:list"),permissions.getMenuPermission(new SysUser(1L)));
        when(menus.selectMenuPermsByUserId(1L)).thenReturn(Collections.singleton("*:*:*"));
        assertEquals(Collections.singleton("*:*:*"),permissions.getMenuPermission(new SysUser(1L)));
    }
}
