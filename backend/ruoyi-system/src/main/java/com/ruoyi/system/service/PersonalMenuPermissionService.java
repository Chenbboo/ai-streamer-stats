package com.ruoyi.system.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.business.domain.BusinessStaffMenuPermission;
import com.ruoyi.business.mapper.BusinessStaffMenuPermissionMapper;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.SysMenuMapper;

/** Applies an optional per-user menu snapshot on top of role permissions. */
@Service
public class PersonalMenuPermissionService
{
    public static final String HIDDEN = "HIDDEN";
    public static final String READ = "READ";
    public static final String MAINTAIN = "MAINTAIN";

    @Autowired private BusinessStaffMenuPermissionMapper permissionMapper;
    @Autowired private SysMenuMapper menuMapper;

    public boolean hasExplicitPolicy(Long userId)
    {
        return !permissionMapper.selectByUserId(userId).isEmpty();
    }

    public Map<Long, String> selectExplicitLevels(Long userId)
    {
        Map<Long, String> result = new HashMap<Long, String>();
        for (BusinessStaffMenuPermission item : permissionMapper.selectByUserId(userId))
        {
            result.put(item.getMenuId(), item.getAccessLevel());
        }
        return result;
    }

    public List<SysMenu> selectAllActiveMenus()
    {
        return menuMapper.selectActiveMenuList();
    }

    public Set<Long> selectRoleMenuIds(Long userId)
    {
        return menuIds(menuMapper.selectActiveMenuListByUserId(userId));
    }

    public Set<Long> selectEffectiveMenuIds(Long userId, boolean administrator)
    {
        if (administrator) return menuIds(menuMapper.selectActiveMenuList());
        List<BusinessStaffMenuPermission> explicit = permissionMapper.selectByUserId(userId);
        if (explicit.isEmpty()) return selectRoleMenuIds(userId);
        Set<Long> allowed = new HashSet<Long>();
        for (BusinessStaffMenuPermission item : explicit)
        {
            if (!HIDDEN.equals(item.getAccessLevel())) allowed.add(item.getMenuId());
        }
        return allowed;
    }

    public Set<String> applyPermissions(Long userId, List<String> rolePermissions)
    {
        List<BusinessStaffMenuPermission> explicit = permissionMapper.selectByUserId(userId);
        if (explicit.isEmpty()) return splitPermissions(rolePermissions);

        Map<Long, String> allowed = new HashMap<Long, String>();
        for (BusinessStaffMenuPermission item : explicit)
        {
            if (!HIDDEN.equals(item.getAccessLevel())) allowed.put(item.getMenuId(), item.getAccessLevel());
        }
        Set<String> result = new HashSet<String>();
        for (SysMenu menu : menuMapper.selectActiveMenuList())
        {
            String level = allowed.get(menu.getMenuId());
            if (MAINTAIN.equals(level) || (READ.equals(level) && isReadPermission(menu.getPerms())))
                addPermission(result, menu.getPerms());
        }
        return result;
    }

    public List<SysMenu> applyRoutes(Long userId, List<SysMenu> roleRoutes)
    {
        List<BusinessStaffMenuPermission> explicit = permissionMapper.selectByUserId(userId);
        if (explicit.isEmpty()) return roleRoutes;

        List<SysMenu> all = menuMapper.selectActiveMenuList();
        Map<Long, SysMenu> byId = new HashMap<Long, SysMenu>();
        for (SysMenu menu : all) byId.put(menu.getMenuId(), menu);
        Set<Long> included = new HashSet<Long>();
        for (BusinessStaffMenuPermission item : explicit)
        {
            SysMenu menu = byId.get(item.getMenuId());
            // A directory is structural: it is visible only as an ancestor of a visible page.
            // This also repairs old snapshots that accidentally retained READ on an empty parent.
            if (menu != null && "C".equals(menu.getMenuType())
                && !HIDDEN.equals(item.getAccessLevel())) included.add(item.getMenuId());
        }
        for (Long menuId : new HashSet<Long>(included))
        {
            SysMenu current = byId.get(menuId);
            while (current != null && current.getParentId() != null && current.getParentId() != 0L)
            {
                included.add(current.getParentId());
                current = byId.get(current.getParentId());
            }
        }
        List<SysMenu> result = new ArrayList<SysMenu>();
        for (SysMenu menu : all)
        {
            if (("M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))
                && included.contains(menu.getMenuId())) result.add(menu);
        }
        return result;
    }

    private Set<Long> menuIds(List<SysMenu> menus)
    {
        Set<Long> result = new HashSet<Long>();
        if (menus != null) for (SysMenu menu : menus) result.add(menu.getMenuId());
        return result;
    }

    private Set<String> splitPermissions(List<String> permissions)
    {
        Set<String> result = new HashSet<String>();
        if (permissions != null) for (String permission : permissions) addPermission(result, permission);
        return result;
    }

    private void addPermission(Set<String> target, String permission)
    {
        if (StringUtils.isEmpty(permission)) return;
        for (String value : permission.trim().split(","))
            if (StringUtils.isNotEmpty(value.trim())) target.add(value.trim());
    }

    private boolean isReadPermission(String permission)
    {
        if (StringUtils.isBlank(permission)) return true;
        for (String value : permission.split(","))
        {
            String token = value.trim().toLowerCase();
            int separator = token.lastIndexOf(':');
            String action = separator < 0 ? token : token.substring(separator + 1);
            if (!("list".equals(action) || "query".equals(action) || "detail".equals(action)
                || "view".equals(action) || "read".equals(action) || "get".equals(action)
                || "option".equals(action) || "options".equals(action) || "select".equals(action))) return false;
        }
        return true;
    }
}
