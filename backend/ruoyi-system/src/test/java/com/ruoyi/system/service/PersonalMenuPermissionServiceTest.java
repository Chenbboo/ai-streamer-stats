package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessStaffMenuPermission;
import com.ruoyi.business.mapper.BusinessStaffMenuPermissionMapper;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.system.mapper.SysMenuMapper;

@ExtendWith(MockitoExtension.class)
class PersonalMenuPermissionServiceTest
{
    @Mock private BusinessStaffMenuPermissionMapper permissionMapper;
    @Mock private SysMenuMapper menuMapper;
    @InjectMocks private PersonalMenuPermissionService service;

    @Test
    void emptyPolicyPreservesRolePermissionsExactly()
    {
        when(permissionMapper.selectByUserId(9L)).thenReturn(Collections.emptyList());
        Set<String> result = service.applyPermissions(9L, Arrays.asList("demo:item:list", "demo:item:add"));
        assertEquals(2, result.size());
        assertTrue(result.contains("demo:item:list"));
        assertTrue(result.contains("demo:item:add"));
    }

    @Test
    void explicitReadSnapshotRemovesWritePermissionAndHiddenRoute()
    {
        List<BusinessStaffMenuPermission> policy = Arrays.asList(
            policy(10L, PersonalMenuPermissionService.READ),
            policy(11L, PersonalMenuPermissionService.READ),
            policy(12L, PersonalMenuPermissionService.HIDDEN));
        when(permissionMapper.selectByUserId(9L)).thenReturn(policy);
        when(menuMapper.selectActiveMenuList()).thenReturn(Arrays.asList(
            menu(10L, 0L, "M", ""), menu(11L, 10L, "C", "demo:item:list"),
            menu(12L, 11L, "F", "demo:item:add")));

        Set<String> permissions = service.applyPermissions(9L,
            Arrays.asList("demo:item:list", "demo:item:add"));
        List<SysMenu> routes = service.applyRoutes(9L, Collections.emptyList());

        assertTrue(permissions.contains("demo:item:list"));
        assertFalse(permissions.contains("demo:item:add"));
        assertEquals(2, routes.size());
    }

    @Test
    void oldVisibleDirectoryRecordCannotLeaveAnEmptyMenuBehind()
    {
        List<BusinessStaffMenuPermission> policy = Arrays.asList(
            policy(10L, PersonalMenuPermissionService.READ),
            policy(11L, PersonalMenuPermissionService.HIDDEN));
        when(permissionMapper.selectByUserId(9L)).thenReturn(policy);
        when(menuMapper.selectActiveMenuList()).thenReturn(Arrays.asList(
            menu(10L, 0L, "M", ""),menu(11L, 10L, "C", "demo:item:list")));

        assertTrue(service.applyRoutes(9L, Collections.emptyList()).isEmpty());
    }

    private BusinessStaffMenuPermission policy(Long menuId, String level)
    {
        BusinessStaffMenuPermission item = new BusinessStaffMenuPermission();
        item.setUserId(9L);
        item.setMenuId(menuId);
        item.setAccessLevel(level);
        return item;
    }

    private SysMenu menu(Long id, Long parentId, String type, String permission)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setPerms(permission);
        return menu;
    }
}
