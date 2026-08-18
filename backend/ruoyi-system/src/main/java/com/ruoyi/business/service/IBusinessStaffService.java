package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;

public interface IBusinessStaffService
{
    TableDataInfo listStaff(SysUser query);
    BusinessStaffProfile getStaffProfile(Long userId);
    List<Map<String, Object>> listOptions();
    Map<String, Object> projectResponsibilities(Long staffUserId, Long viewerUserId, boolean viewAll, boolean boss);
    List<TreeSelect> departmentOptions();
    Object createStaff(BusinessStaffProfile input, String operatorName);
    Object updateStaff(BusinessStaffProfile input, String operatorName);
    void changeStatus(Long userId, String status, String operatorName);
    void resetPassword(Long userId, String password, String operatorName);
}
