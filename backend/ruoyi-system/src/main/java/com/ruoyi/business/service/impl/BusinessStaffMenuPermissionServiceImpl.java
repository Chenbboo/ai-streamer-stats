package com.ruoyi.business.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessStaffMenuPermission;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessStaffMenuPermissionMapper;
import com.ruoyi.business.mapper.BusinessStaffProfileMapper;
import com.ruoyi.business.service.IBusinessStaffMenuPermissionService;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;
import com.ruoyi.system.service.PersonalMenuPermissionService;

@Service
public class BusinessStaffMenuPermissionServiceImpl implements IBusinessStaffMenuPermissionService
{
    private static final String HIDDEN = PersonalMenuPermissionService.HIDDEN;
    private static final String READ = PersonalMenuPermissionService.READ;
    private static final String MAINTAIN = PersonalMenuPermissionService.MAINTAIN;

    @Autowired private ISysUserService userService;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessStaffProfileMapper profileMapper;
    @Autowired private BusinessStaffMenuPermissionMapper permissionMapper;
    @Autowired private PersonalMenuPermissionService permissionResolver;
    @Autowired private OnlineUserPermissionService onlineUserPermissionService;

    @Override
    public Map<String, Object> getMenuPermissions(Long userId, Long operatorUserId, boolean administrator)
    {
        SysUser target = requireScope(userId, operatorUserId, administrator);
        List<SysMenu> allMenus = permissionResolver.selectAllActiveMenus();
        Set<Long> inheritedIds = permissionResolver.selectRoleMenuIds(userId);
        Set<Long> currentIds = permissionResolver.selectEffectiveMenuIds(userId, false);
        Set<Long> ceilingIds = permissionCeiling(operatorUserId, administrator, currentIds, allMenus);
        Map<Long, List<SysMenu>> ownedActions = actionsByController(allMenus);

        Map<Long, String> inheritedLevels = navigationLevels(allMenus, inheritedIds, ownedActions);
        Map<Long, String> currentLevels = navigationLevels(allMenus, currentIds, ownedActions);
        Map<Long, String> maximumLevels = navigationLevels(allMenus, ceilingIds, ownedActions);
        allowOwnerToSetEveryNavigationLevel(administrator, allMenus, maximumLevels);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("userId", target.getUserId());
        result.put("userName", target.getUserName());
        result.put("nickName", target.getNickName());
        result.put("inherited", !permissionResolver.hasExplicitPolicy(userId));
        result.put("menus", buildVisibleTree(allMenus, currentLevels, inheritedLevels, maximumLevels));
        return result;
    }

    @Override
    @Transactional
    public void saveMenuPermissions(Long userId, List<Map<String, Object>> permissions,
        Long operatorUserId, boolean administrator, String operatorName)
    {
        requireScope(userId, operatorUserId, administrator);
        if (permissions == null) throw new ServiceException("目录权限不能为空");

        List<SysMenu> allMenus = permissionResolver.selectAllActiveMenus();
        Map<Long, SysMenu> byId = byId(allMenus);
        Map<Long, List<SysMenu>> ownedActions = actionsByController(allMenus);
        Set<Long> currentIds = permissionResolver.selectEffectiveMenuIds(userId, false);
        Set<Long> ceilingIds = permissionCeiling(operatorUserId, administrator, currentIds, allMenus);
        Map<Long, String> maximumLevels = navigationLevels(allMenus, ceilingIds, ownedActions);
        allowOwnerToSetEveryNavigationLevel(administrator, allMenus, maximumLevels);
        Map<Long, String> currentLevels = navigationLevels(allMenus, currentIds, ownedActions);
        Map<Long, String> requested = parseRequestedLevels(permissions);

        for (SysMenu menu : allMenus)
        {
            // Directory (M) levels are derived from their child pages. Only concrete pages (C)
            // are part of the editable payload and therefore need completeness validation.
            if ("C".equals(menu.getMenuType()) && isEditableNavigation(menu)
                && !requested.containsKey(menu.getMenuId()))
                throw new ServiceException("请完整提交全部页面权限");
        }

        Map<Long, String> itemLevels = new HashMap<Long, String>();
        for (SysMenu menu : allMenus) itemLevels.put(menu.getMenuId(), HIDDEN);

        Map<Long, String> navigation = new HashMap<Long, String>();
        for (SysMenu menu : allMenus)
        {
            if (!"C".equals(menu.getMenuType())) continue;
            String level = isEditableNavigation(menu) ? requested.get(menu.getMenuId())
                : currentLevels.getOrDefault(menu.getMenuId(), HIDDEN);
            String maximum = maximumLevels.getOrDefault(menu.getMenuId(), HIDDEN);
            if (rank(level) > rank(maximum))
                throw new ServiceException("不能把“" + menu.getMenuName() + "”设置为超过本人权限的级别");
            navigation.put(menu.getMenuId(), level);
            if (rank(level) >= rank(READ)) itemLevels.put(menu.getMenuId(), level);

            List<SysMenu> actions = ownedActions.get(menu.getMenuId());
            if (actions == null) continue;
            for (SysMenu action : actions)
            {
                if (!ceilingIds.contains(action.getMenuId())) continue;
                if (MAINTAIN.equals(level))
                    itemLevels.put(action.getMenuId(), isReadAction(action) ? READ : MAINTAIN);
                else if (READ.equals(level) && isReadAction(action))
                    itemLevels.put(action.getMenuId(), READ);
            }
        }

        // Preserve enabled action records that are not attached to a concrete page.
        for (SysMenu menu : allMenus)
        {
            if ("F".equals(menu.getMenuType()) && controllerFor(menu, byId) == null
                && currentIds.contains(menu.getMenuId())) itemLevels.put(menu.getMenuId(), READ);
        }
        // A directory is visible only when at least one saved child page remains visible.
        // Falling back to the previous role-derived directory level leaves an empty parent
        // route behind after every child is explicitly hidden.
        deriveDirectoryLevels(allMenus, navigation, Collections.<Long, String>emptyMap());
        itemLevels.putAll(navigation);

        List<BusinessStaffMenuPermission> items = new ArrayList<BusinessStaffMenuPermission>();
        for (SysMenu menu : allMenus)
        {
            BusinessStaffMenuPermission item = new BusinessStaffMenuPermission();
            item.setUserId(userId);
            item.setMenuId(menu.getMenuId());
            item.setAccessLevel(itemLevels.getOrDefault(menu.getMenuId(), HIDDEN));
            item.setCreateBy(operatorName);
            item.setUpdateBy(operatorName);
            items.add(item);
        }
        permissionMapper.deleteByUserId(userId);
        if (!items.isEmpty()) permissionMapper.insertBatch(items);
        onlineUserPermissionService.forceReloginAfterCommit(userId);
    }

    @Override
    @Transactional
    public void resetMenuPermissions(Long userId, Long operatorUserId, boolean administrator)
    {
        requireScope(userId, operatorUserId, administrator);
        permissionMapper.deleteByUserId(userId);
        onlineUserPermissionService.forceReloginAfterCommit(userId);
    }

    private SysUser requireScope(Long userId, Long operatorUserId, boolean administrator)
    {
        if (userId == null || operatorUserId == null) throw new ServiceException("人员ID不能为空");
        SysUser target = userService.selectUserById(userId);
        if (target == null || "2".equals(target.getDelFlag())) throw new ServiceException("人员不存在");
        if (SecurityUtils.isAdmin(target.getUserId())
            || projectMapper.countUserRoleByKey(target.getUserId(), "company_owner") > 0)
            throw new ServiceException("系统管理员和老板账号为受保护账号，不能设置个人目录权限");
        if (!administrator)
        {
            if (projectMapper.countUserRoleByKey(operatorUserId, "company_owner") == 0)
                throw new ServiceException("只有老板可以设置员工目录权限");
            BusinessStaffProfile profile = profileMapper.selectByUserId(userId);
            if (profile == null || !operatorUserId.equals(profile.getCompanyLeaderUserId()))
                throw new ServiceException("只能设置本人负责公司的员工目录权限");
        }
        return target;
    }

    private Set<Long> permissionCeiling(Long operatorUserId, boolean administrator,
        Set<Long> currentTargetIds, List<SysMenu> allMenus)
    {
        Set<Long> result = new HashSet<Long>(currentTargetIds);
        result.addAll(permissionResolver.selectEffectiveMenuIds(operatorUserId, administrator));
        if (!administrator)
        {
            // Company owners may delegate every active menu and action to employees in their own
            // company, even when the owner's navigation role does not contain that menu.
            for (SysMenu menu : allMenus) result.add(menu.getMenuId());
        }
        return result;
    }

    private void allowOwnerToSetEveryNavigationLevel(boolean administrator, List<SysMenu> allMenus,
        Map<Long, String> maximumLevels)
    {
        if (administrator) return;
        for (SysMenu menu : allMenus)
        {
            if (isEditableNavigation(menu)) maximumLevels.put(menu.getMenuId(), MAINTAIN);
        }
    }

    private Map<Long, String> parseRequestedLevels(List<Map<String, Object>> permissions)
    {
        Map<Long, String> result = new HashMap<Long, String>();
        for (Map<String, Object> entry : permissions)
        {
            if (entry == null || entry.get("menuId") == null) throw new ServiceException("目录权限格式不正确");
            Long menuId;
            try { menuId = Long.valueOf(String.valueOf(entry.get("menuId"))); }
            catch (NumberFormatException e) { throw new ServiceException("目录ID不正确"); }
            String level = normalizeLevel(entry.get("accessLevel"));
            if (result.put(menuId, level) != null) throw new ServiceException("目录权限不能重复");
        }
        return result;
    }

    private String normalizeLevel(Object value)
    {
        String level = value == null ? "" : String.valueOf(value).trim().toUpperCase();
        if (!HIDDEN.equals(level) && !READ.equals(level) && !MAINTAIN.equals(level))
            throw new ServiceException("目录权限级别不正确");
        return level;
    }

    private List<Map<String, Object>> buildVisibleTree(List<SysMenu> allMenus, Map<Long, String> current,
        Map<Long, String> inherited, Map<Long, String> maximum)
    {
        List<SysMenu> visible = new ArrayList<SysMenu>();
        Set<Long> visibleIds = new HashSet<Long>();
        for (SysMenu menu : allMenus)
        {
            if (isEditableNavigation(menu)) { visible.add(menu); visibleIds.add(menu.getMenuId()); }
        }
        Collections.sort(visible, menuComparator());
        Map<Long, List<SysMenu>> children = new HashMap<Long, List<SysMenu>>();
        List<SysMenu> roots = new ArrayList<SysMenu>();
        for (SysMenu menu : visible)
        {
            if (!visibleIds.contains(menu.getParentId())) roots.add(menu);
            else children.computeIfAbsent(menu.getParentId(), key -> new ArrayList<SysMenu>()).add(menu);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (SysMenu root : roots) result.add(toNode(root, children, current, inherited, maximum));
        return result;
    }

    private Map<String, Object> toNode(SysMenu menu, Map<Long, List<SysMenu>> children,
        Map<Long, String> current, Map<Long, String> inherited, Map<Long, String> maximum)
    {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("menuId", menu.getMenuId());
        node.put("menuName", menu.getMenuName());
        node.put("menuType", menu.getMenuType());
        node.put("accessLevel", current.getOrDefault(menu.getMenuId(), HIDDEN));
        node.put("inheritedLevel", inherited.getOrDefault(menu.getMenuId(), HIDDEN));
        node.put("maxLevel", maximum.getOrDefault(menu.getMenuId(), HIDDEN));
        List<Map<String, Object>> childNodes = new ArrayList<Map<String, Object>>();
        List<SysMenu> childMenus = children.get(menu.getMenuId());
        if (childMenus != null) for (SysMenu child : childMenus)
            childNodes.add(toNode(child, children, current, inherited, maximum));
        node.put("children", childNodes);
        return node;
    }

    private Map<Long, String> navigationLevels(List<SysMenu> menus, Set<Long> allowed,
        Map<Long, List<SysMenu>> ownedActions)
    {
        Map<Long, String> levels = new HashMap<Long, String>();
        for (SysMenu menu : menus)
        {
            if ("M".equals(menu.getMenuType()) && allowed.contains(menu.getMenuId()))
                levels.put(menu.getMenuId(), READ);
            if (!"C".equals(menu.getMenuType())) continue;
            boolean any = allowed.contains(menu.getMenuId());
            boolean write = any && !isReadPermission(menu.getPerms());
            List<SysMenu> actions = ownedActions.get(menu.getMenuId());
            if (actions != null) for (SysMenu action : actions)
            {
                if (!allowed.contains(action.getMenuId())) continue;
                any = true;
                if (!isReadAction(action)) write = true;
            }
            levels.put(menu.getMenuId(), !any ? HIDDEN : write ? MAINTAIN : READ);
        }
        deriveDirectoryLevels(menus, levels, levels);
        return levels;
    }

    private void deriveDirectoryLevels(List<SysMenu> menus, Map<Long, String> levels,
        Map<Long, String> fallback)
    {
        Map<Long, List<SysMenu>> children = new HashMap<Long, List<SysMenu>>();
        Map<Long, SysMenu> directories = new HashMap<Long, SysMenu>();
        for (SysMenu menu : menus)
        {
            if ("M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))
                children.computeIfAbsent(menu.getParentId(), key -> new ArrayList<SysMenu>()).add(menu);
            if ("M".equals(menu.getMenuType())) directories.put(menu.getMenuId(), menu);
        }
        Set<Long> visiting = new HashSet<Long>();
        for (Long directoryId : directories.keySet())
            deriveDirectoryLevel(directoryId, children, levels, fallback, visiting);
    }

    private String deriveDirectoryLevel(Long menuId, Map<Long, List<SysMenu>> children,
        Map<Long, String> levels, Map<Long, String> fallback, Set<Long> visiting)
    {
        if (!visiting.add(menuId)) return levels.getOrDefault(menuId, HIDDEN);
        int highest = 0;
        List<SysMenu> childMenus = children.get(menuId);
        boolean hasNavigationChild = childMenus != null && !childMenus.isEmpty();
        if (childMenus != null) for (SysMenu child : childMenus)
        {
            String childLevel = "M".equals(child.getMenuType())
                ? deriveDirectoryLevel(child.getMenuId(), children, levels, fallback, visiting)
                : levels.getOrDefault(child.getMenuId(), HIDDEN);
            highest = Math.max(highest, rank(childLevel));
        }
        if (!hasNavigationChild) highest = rank(fallback.getOrDefault(menuId, HIDDEN));
        String result = level(highest);
        levels.put(menuId, result);
        visiting.remove(menuId);
        return result;
    }

    private Map<Long, List<SysMenu>> actionsByController(List<SysMenu> menus)
    {
        Map<Long, SysMenu> byId = byId(menus);
        Map<Long, List<SysMenu>> result = new HashMap<Long, List<SysMenu>>();
        for (SysMenu menu : menus)
        {
            if (!"F".equals(menu.getMenuType())) continue;
            Long controller = controllerFor(menu, byId);
            if (controller != null) result.computeIfAbsent(controller, key -> new ArrayList<SysMenu>()).add(menu);
        }
        return result;
    }

    private Long controllerFor(SysMenu menu, Map<Long, SysMenu> byId)
    {
        SysMenu parent = byId.get(menu.getParentId());
        while (parent != null)
        {
            if ("C".equals(parent.getMenuType())) return parent.getMenuId();
            parent = byId.get(parent.getParentId());
        }
        return null;
    }

    private Map<Long, SysMenu> byId(List<SysMenu> menus)
    {
        Map<Long, SysMenu> result = new HashMap<Long, SysMenu>();
        for (SysMenu menu : menus) result.put(menu.getMenuId(), menu);
        return result;
    }

    private boolean isEditableNavigation(SysMenu menu)
    {
        return ("M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))
            && "0".equals(menu.getVisible());
    }

    private boolean isReadAction(SysMenu menu)
    {
        return isReadPermission(menu.getPerms());
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

    private Comparator<SysMenu> menuComparator()
    {
        return (left, right) -> {
            int order = Integer.compare(left.getOrderNum() == null ? 0 : left.getOrderNum(),
                right.getOrderNum() == null ? 0 : right.getOrderNum());
            return order != 0 ? order : Long.compare(left.getMenuId(), right.getMenuId());
        };
    }

    private int rank(String level)
    {
        return MAINTAIN.equals(level) ? 2 : READ.equals(level) ? 1 : 0;
    }

    private String level(int rank)
    {
        return rank >= 2 ? MAINTAIN : rank == 1 ? READ : HIDDEN;
    }
}
