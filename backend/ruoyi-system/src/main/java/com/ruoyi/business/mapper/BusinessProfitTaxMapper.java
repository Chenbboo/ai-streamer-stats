package com.ruoyi.business.mapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
public interface BusinessProfitTaxMapper {
    List<Map<String,Object>> selectCompanies(Long userId);
    Long lockCompany(Long companyDeptId);
    Map<String,Object> selectPolicy(Long companyDeptId);
    int savePolicy(Map<String,Object> values);
    int insertEvent(Map<String,Object> values);
    List<Map<String,Object>> selectSeries(Map<String,Object> query);
    Map<String,Object> selectProjectBasis(Long projectId);
    int insertSnapshot(Map<String,Object> values);
}
