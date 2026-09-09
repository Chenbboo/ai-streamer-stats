package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import com.ruoyi.common.exception.ServiceException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessStaffMenuPermission;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessStaffMenuPermissionMapper;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;
import com.ruoyi.system.service.PersonalMenuPermissionService;

@ExtendWith(MockitoExtension.class)
class BusinessStaffMenuPermissionServiceImplTest
{
    @Mock private ISysUserService userService;
    @Mock private BusinessProjectMapper projectMapper;
    @Mock private BusinessStaffMenuPermissionMapper permissionMapper;
    @Mock private PersonalMenuPermissionService permissionResolver;
    @Mock private OnlineUserPermissionService onlineUserPermissionService;
    @InjectMocks private BusinessStaffMenuPermissionServiceImpl service;

    @SuppressWarnings("unchecked")
    @Test
    void companyOwnerCanDelegateEveryModuleOutsideTargetRole()
    {
        SysUser target = new SysUser();
        target.setUserId(200L);
        target.setUserName("employee");
        target.setNickName("员工");
        target.setDelFlag("0");
        List<SysMenu> menus = Arrays.asList(
            menu(1L, 0L, "M", "system", ""),
            menu(100L, 1L, "C", "user", "system:user:list"),
            menu(101L, 100L, "F", "#", "system:user:add"),
            menu(2L, 0L, "M", "monitor", ""),
            menu(200L, 2L, "C", "online", "monitor:online:list"),
            menu(201L, 200L, "F", "#", "monitor:online:forceLogout"),
            menu(3000L, 0L, "M", "jewelry", ""),
            menu(3001L, 3000L, "C", "product", "jewelry:product:list"),
            menu(3002L, 3001L, "F", "#", "jewelry:product:add"),
            menu(3003L, 3000L, "C", "erp", "jewelry:erp:view"));

        when(userService.selectUserById(200L)).thenReturn(target);
        when(projectMapper.countUserRoleByKey(120L, "company_owner")).thenReturn(1);
        when(permissionResolver.selectAllActiveMenus()).thenReturn(menus);
        when(permissionResolver.selectRoleMenuIds(200L)).thenReturn(Collections.emptySet());
        when(permissionResolver.selectEffectiveMenuIds(200L, false)).thenReturn(Collections.emptySet());
        when(permissionResolver.hasExplicitPolicy(200L)).thenReturn(false);

        Map<String, Object> result = service.getMenuPermissions(200L, 120L, false);
        List<Map<String, Object>> roots = (List<Map<String, Object>>) result.get("menus");
        assertEquals(3, roots.size());
        for (Map<String, Object> root : roots)
        {
            List<Map<String, Object>> children = (List<Map<String, Object>>) root.get("children");
            assertEquals("MAINTAIN", root.get("maxLevel"));
            for (Map<String, Object> child : children)
                assertEquals("MAINTAIN", child.get("maxLevel"));
        }
    }

    @Test
    void saveRequiresPagePermissionsButDerivesDirectoryPermissions()
    {
        SysUser target = new SysUser();
        target.setUserId(200L);
        target.setUserName("employee");
        target.setNickName("员工");
        target.setDelFlag("0");
        List<SysMenu> menus = Arrays.asList(
            menu(3000L, 0L, "M", "jewelry", ""),
            menu(3001L, 3000L, "C", "product", "jewelry:product:list"));
        Map<String, Object> pagePermission = new HashMap<String, Object>();
        pagePermission.put("menuId", 3001L);
        pagePermission.put("accessLevel", "READ");

        when(userService.selectUserById(200L)).thenReturn(target);
        when(projectMapper.countUserRoleByKey(120L, "company_owner")).thenReturn(1);
        when(permissionResolver.selectAllActiveMenus()).thenReturn(menus);
        when(permissionResolver.selectEffectiveMenuIds(200L, false)).thenReturn(Collections.emptySet());

        assertDoesNotThrow(() -> service.saveMenuPermissions(200L,
            Collections.singletonList(pagePermission), 120L, false, "owner"));
        verify(permissionMapper).insertBatch(anyList());
    }

    @Test
    void hidingEveryChildAlsoHidesPreviouslyVisibleParentDirectory()
    {
        SysUser target = new SysUser();target.setUserId(200L);target.setUserName("employee");target.setDelFlag("0");
        List<SysMenu> menus = Arrays.asList(
            menu(3000L, 0L, "M", "jewelry", ""),
            menu(3001L, 3000L, "C", "product", "jewelry:product:list"));
        Map<String,Object> hidden=new HashMap<String,Object>();hidden.put("menuId",3001L);hidden.put("accessLevel","HIDDEN");
        when(userService.selectUserById(200L)).thenReturn(target);
        when(projectMapper.countUserRoleByKey(120L,"company_owner")).thenReturn(1);
        when(permissionResolver.selectAllActiveMenus()).thenReturn(menus);
        when(permissionResolver.selectEffectiveMenuIds(200L,false)).thenReturn(new java.util.HashSet<Long>(Arrays.asList(3000L,3001L)));

        service.saveMenuPermissions(200L,Collections.singletonList(hidden),120L,false,"owner");

        ArgumentCaptor<List<BusinessStaffMenuPermission>> saved=ArgumentCaptor.forClass(List.class);
        verify(permissionMapper).insertBatch(saved.capture());
        for(BusinessStaffMenuPermission item:saved.getValue())assertEquals("HIDDEN",item.getAccessLevel());
    }

    @Test
    void ownerCanGrantAdminAndOtherOwnersWritePermissionsWithoutCompanyProfile()
    {
        for(Long targetId:Arrays.asList(1L,120L,121L)){
            SysUser target=new SysUser();target.setUserId(targetId);target.setDelFlag("0");
            when(userService.selectUserById(targetId)).thenReturn(target);
        }
        when(projectMapper.countUserRoleByKey(120L,"company_owner")).thenReturn(1);
        when(permissionResolver.selectAllActiveMenus()).thenReturn(Arrays.asList(
            menu(1L,0L,"M","system",""),
            menu(100L,1L,"C","user","system:user:list"),
            menu(101L,100L,"F","#","system:user:add"),
            menu(102L,100L,"F","#","system:user:query")));
        Map<String,Object> setting=new HashMap<>();setting.put("menuId",100L);setting.put("accessLevel","MAINTAIN");
        for(Long targetId:Arrays.asList(1L,120L,121L)){
            service.saveMenuPermissions(targetId,Collections.singletonList(setting),120L,false,"owner");
            verify(onlineUserPermissionService).forceReloginAfterCommit(targetId);
        }
        ArgumentCaptor<List<BusinessStaffMenuPermission>> saved=ArgumentCaptor.forClass(List.class);
        verify(permissionMapper,org.mockito.Mockito.times(3)).insertBatch(saved.capture());
        for(List<BusinessStaffMenuPermission> rows:saved.getAllValues()){
            assertEquals("MAINTAIN",rows.stream().filter(row->row.getMenuId()==101L).findFirst().get().getAccessLevel());
            assertEquals("READ",rows.stream().filter(row->row.getMenuId()==102L).findFirst().get().getAccessLevel());
        }
        service.resetMenuPermissions(1L,120L,false);
        verify(permissionMapper,org.mockito.Mockito.times(2)).deleteByUserId(1L);
    }

    @Test
    void nonOwnerCannotReadSaveOrResetAnyAccountPolicy()
    {
        SysUser target=new SysUser();target.setUserId(1L);target.setDelFlag("0");
        when(userService.selectUserById(1L)).thenReturn(target);
        assertThrows(ServiceException.class,()->service.getMenuPermissions(1L,200L,false));
        assertThrows(ServiceException.class,()->service.saveMenuPermissions(1L,Collections.emptyList(),200L,false,"staff"));
        assertThrows(ServiceException.class,()->service.resetMenuPermissions(1L,200L,false));
        verify(permissionMapper,never()).deleteByUserId(any());
    }

    private SysMenu menu(Long id, Long parentId, String type, String path, String permission)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuName(path);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setPerms(permission);
        menu.setVisible("0");
        menu.setOrderNum(1);
        return menu;
    }
}
