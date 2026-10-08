package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;

public interface IBusinessStaffMenuPermissionService
{
    Map<String, Object> getMenuPermissions(Long userId, Long operatorUserId, boolean administrator);
    void saveMenuPermissions(Long userId, List<Map<String, Object>> permissions,
        Long operatorUserId, boolean administrator, String operatorName);
    void resetMenuPermissions(Long userId, Long operatorUserId, boolean administrator);
}
