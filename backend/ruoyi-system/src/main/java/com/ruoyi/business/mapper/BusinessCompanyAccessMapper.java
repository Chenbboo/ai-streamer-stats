package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessCompanyAccessMapper
{
    int allowed(@Param("userId") Long userId, @Param("companyId") Long companyId, @Param("capability") String capability);
    Long departmentCompany(Long deptId);
    Long staffCompany(Long userId);
    Long projectCompany(Long projectId);
    List<Map<String,Object>> companies(@Param("userId") Long userId, @Param("admin") boolean admin);
    List<Map<String,Object>> grants(Long companyId);
    List<Map<String,Object>> bosses();
    Long lockCompany(Long companyId);
    int activeBoss(Long userId);
    int version(@Param("companyId") Long companyId, @Param("userId") Long userId);
    int replace(Map<String,Object> row);
    int audit(Map<String,Object> row);
}
