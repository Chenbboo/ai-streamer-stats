package com.ruoyi.business.support;

import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;

/** Read-only visibility inherited from the owner of a project's direct parent. */
public final class BusinessProjectReadAccess
{
    private BusinessProjectReadAccess() { }

    public static boolean isParentOwner(BusinessProject project, Long userId, BusinessProjectMapper projects)
    {
        if (project == null || project.getParentId() == null || userId == null) return false;
        BusinessProject parent = projects.selectProjectById(project.getParentId());
        return parent != null && userId.equals(parent.getMainOwnerUserId());
    }
}
