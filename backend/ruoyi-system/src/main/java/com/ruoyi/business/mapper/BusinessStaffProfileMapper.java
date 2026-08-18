package com.ruoyi.business.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessStaffProfile;

public interface BusinessStaffProfileMapper
{
    BusinessStaffProfile selectByUserId(Long userId);
    List<BusinessStaffProfile> selectByUserIds(@Param("userIds") List<Long> userIds);
    int upsert(BusinessStaffProfile profile);
    int countEmployeeNo(@Param("employeeNo") String employeeNo, @Param("userId") Long userId);
    int syncDepartmentLeader(Long userId);
}
