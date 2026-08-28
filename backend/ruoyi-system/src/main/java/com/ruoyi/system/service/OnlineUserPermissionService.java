package com.ruoyi.system.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;

/** Keeps already signed-in users in sync when a business workflow grants a new role. */
@Service
public class OnlineUserPermissionService
{
    private static final Logger log = LoggerFactory.getLogger(OnlineUserPermissionService.class);

    @Autowired private RedisCache redisCache;
    @Autowired private ISysUserService userService;
    @Autowired private ISysMenuService menuService;

    public void refreshAfterCommit(final Long userId)
    {
        if (userId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    refreshOnlineSessions(userId);
                }
            });
        }
        else
        {
            refreshOnlineSessions(userId);
        }
    }

    public void refreshOnlineSessions(Long userId)
    {
        SysUser freshUser = userService.selectUserById(userId);
        if (freshUser == null) return;
        Set<String> permissions = permissionsFor(freshUser);
        Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys)
        {
            LoginUser loginUser = redisCache.getCacheObject(key);
            if (loginUser == null || !userId.equals(loginUser.getUserId())) continue;
            long remainingSeconds = redisCache.getExpire(key);
            if (remainingSeconds == -2L) continue;
            loginUser.setUser(freshUser);
            loginUser.setDeptId(freshUser.getDeptId());
            loginUser.setPermissions(new HashSet<String>(permissions));
            if (remainingSeconds > 0L)
            {
                redisCache.setCacheObject(key, loginUser,
                    (int)Math.min(remainingSeconds, Integer.MAX_VALUE), TimeUnit.SECONDS);
            }
            else
            {
                redisCache.setCacheObject(key, loginUser);
            }
            log.info("已刷新在线用户[{}]的角色与菜单权限缓存", freshUser.getUserName());
        }
    }

    private Set<String> permissionsFor(SysUser user)
    {
        Set<String> permissions = new HashSet<String>();
        if (user.isAdmin())
        {
            permissions.add(Constants.ALL_PERMISSION);
            return permissions;
        }
        List<SysRole> roles = user.getRoles();
        if (roles == null || roles.isEmpty())
        {
            permissions.addAll(menuService.selectMenuPermsByUserId(user.getUserId()));
            return permissions;
        }
        for (SysRole role : roles)
        {
            if (StringUtils.equals(role.getStatus(), UserConstants.ROLE_NORMAL) && !role.isAdmin())
            {
                Set<String> rolePermissions = menuService.selectMenuPermsByRoleId(role.getRoleId());
                role.setPermissions(rolePermissions);
                permissions.addAll(rolePermissions);
            }
        }
        return permissions;
    }
}
