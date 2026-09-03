package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;

@ExtendWith(MockitoExtension.class)
class OnlineUserPermissionServiceTest
{
    @Mock private RedisCache redisCache;
    @Mock private ISysUserService userService;
    @Mock private ISysMenuService menuService;
    @InjectMocks private OnlineUserPermissionService service;

    @Test
    void refreshesAnOnlineApplicantAfterProjectRolesAreGranted()
    {
        SysRole staff = role(31L, "company_staff");
        SysRole owner = role(32L, "project_owner");
        SysRole member = role(33L, "project_user");
        SysUser freshUser = new SysUser();
        freshUser.setUserId(129L);
        freshUser.setUserName("jingtian");
        freshUser.setDeptId(110L);
        freshUser.setRoles(Arrays.asList(staff, owner, member));
        LoginUser cached = new LoginUser(129L, 110L, freshUser,
            new HashSet<String>(Collections.singleton("business:project:proposal:list")));
        when(userService.selectUserById(129L)).thenReturn(freshUser);
        when(menuService.selectMenuPermsByRoleId(31L)).thenReturn(
            Collections.singleton("business:project:proposal:list"));
        when(menuService.selectMenuPermsByRoleId(32L)).thenReturn(
            new HashSet<String>(Arrays.asList("business:project:list", "business:project:owner:view")));
        when(menuService.selectMenuPermsByRoleId(33L)).thenReturn(
            Collections.singleton("business:project:work:view"));
        when(menuService.selectMenuPermsByUserId(129L)).thenReturn(
            new HashSet<String>(Arrays.asList("business:project:proposal:list", "business:project:list",
                "business:project:owner:view", "business:project:work:view")));
        when(redisCache.keys("login_tokens:*")).thenReturn(Collections.singleton("login_tokens:abc"));
        when(redisCache.getCacheObject("login_tokens:abc")).thenReturn(cached);
        when(redisCache.getExpire("login_tokens:abc")).thenReturn(900L);

        service.refreshOnlineSessions(129L);

        assertSame(freshUser, cached.getUser());
        assertEquals(3, cached.getUser().getRoles().size());
        assertTrue(cached.getPermissions().contains("business:project:list"));
        assertTrue(cached.getPermissions().contains("business:project:owner:view"));
        verify(redisCache).setCacheObject(eq("login_tokens:abc"), eq(cached), eq(900), eq(TimeUnit.SECONDS));
    }

    @Test
    void menuPermissionChangeForcesOnlyTargetUserToRelogin()
    {
        SysUser targetUser = new SysUser();
        targetUser.setUserId(135L);
        targetUser.setUserName("lisi");
        SysUser otherUser = new SysUser();
        otherUser.setUserId(134L);
        otherUser.setUserName("zhangsan");
        LoginUser target = new LoginUser(135L, 100L, targetUser, Collections.emptySet());
        LoginUser other = new LoginUser(134L, 100L, otherUser, Collections.emptySet());
        when(redisCache.keys("login_tokens:*")).thenReturn(
            new HashSet<String>(Arrays.asList("login_tokens:target", "login_tokens:other")));
        when(redisCache.getCacheObject("login_tokens:target")).thenReturn(target);
        when(redisCache.getCacheObject("login_tokens:other")).thenReturn(other);

        service.forceRelogin(135L);

        verify(redisCache).deleteObject("login_tokens:target");
        verify(redisCache, never()).deleteObject("login_tokens:other");
    }

    private SysRole role(Long roleId, String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        role.setStatus("0");
        return role;
    }
}
