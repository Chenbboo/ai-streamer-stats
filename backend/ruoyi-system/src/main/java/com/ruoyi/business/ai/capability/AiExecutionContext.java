package com.ruoyi.business.ai.capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;

/** Immutable identity and permission snapshot for one AI request. */
public final class AiExecutionContext
{
    private final Long userId;
    private final Long deptId;
    private final String userName;
    private final Set<String> permissions;
    private final Set<String> roles;
    private final boolean administrator;

    private AiExecutionContext(Long userId, Long deptId, String userName, Collection<String> permissions,
        Collection<String> roles, boolean administrator)
    {
        this.userId = userId;
        this.deptId = deptId;
        this.userName = userName;
        this.permissions = immutableSet(permissions);
        this.roles = immutableSet(roles);
        this.administrator = administrator;
    }

    public static AiExecutionContext from(LoginUser loginUser)
    {
        if (loginUser == null) throw new IllegalArgumentException("loginUser must not be null");
        List<String> roleKeys = new ArrayList<String>();
        if (loginUser.getUser() != null && loginUser.getUser().getRoles() != null)
            for (SysRole role : loginUser.getUser().getRoles())
                if (role != null && StringUtils.isNotBlank(role.getRoleKey())) roleKeys.add(role.getRoleKey());
        return new AiExecutionContext(loginUser.getUserId(), loginUser.getDeptId(), loginUser.getUsername(),
            loginUser.getPermissions(), roleKeys, SecurityUtils.isAdmin(loginUser.getUserId()));
    }

    /** Compatibility bridge while legacy callers are migrated to the authenticated context. */
    public static AiExecutionContext legacy(Long userId, String userName, boolean administrator)
    {
        Set<String> permissions = administrator
            ? Collections.singleton("*:*:*") : Collections.<String>emptySet();
        return new AiExecutionContext(userId, null, userName, permissions,
            Collections.<String>emptySet(), administrator);
    }

    private static Set<String> immutableSet(Collection<String> values)
    {
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        if (values != null)
            for (String value : values) if (StringUtils.isNotBlank(value)) copy.add(value);
        return Collections.unmodifiableSet(copy);
    }

    public boolean hasPermission(String permission)
    {
        return administrator || StringUtils.isBlank(permission) || SecurityUtils.hasPermi(permissions, permission);
    }

    public Long getUserId() { return userId; }
    public Long getDeptId() { return deptId; }
    public String getUserName() { return userName; }
    public Set<String> getPermissions() { return permissions; }
    public Set<String> getRoles() { return roles; }
    public boolean isAdministrator() { return administrator; }
}
