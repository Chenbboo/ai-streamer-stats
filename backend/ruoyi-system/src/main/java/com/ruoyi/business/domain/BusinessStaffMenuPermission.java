package com.ruoyi.business.domain;

import java.io.Serializable;

/** A user's explicit menu permission snapshot. */
public class BusinessStaffMenuPermission implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long menuId;
    private String accessLevel;
    private String createBy;
    private String updateBy;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
}
