package com.ruoyi.business.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessStaffMenuPermission;

public interface BusinessStaffMenuPermissionMapper
{
    List<BusinessStaffMenuPermission> selectByUserId(Long userId);
    int deleteByUserId(Long userId);
    int insertBatch(@Param("items") List<BusinessStaffMenuPermission> items);
}
