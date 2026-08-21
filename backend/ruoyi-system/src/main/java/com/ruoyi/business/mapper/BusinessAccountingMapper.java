package com.ruoyi.business.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessOperatingFact;

public interface BusinessAccountingMapper
{
    List<Map<String,Object>> selectCompanies();
    List<Map<String,Object>> selectCategories();
    List<Map<String,Object>> selectProjectOptions(@Param("userId") Long userId,@Param("viewAll") boolean viewAll);
    List<Map<String,Object>> selectFacts(Map<String,Object> query);
    BusinessOperatingFact selectFactById(Long factId);
    BusinessOperatingFact selectFactByIdempotencyKey(String idempotencyKey);
    Map<String,Object> selectCategoryById(Long categoryId);
    Map<String,Object> selectCategoryByCode(String categoryCode);
    BusinessOperatingFact selectCurrentProjectDailySpend(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    Map<String,Object> selectProjectForAccounting(Long projectId);
    String selectAccountingMemberRole(@Param("projectId") Long projectId,@Param("userId") Long userId);
    int insertFact(BusinessOperatingFact fact);
    int updateDraftFact(BusinessOperatingFact fact);
    int confirmFact(@Param("factId") Long factId,@Param("userId") Long userId,@Param("userName") String userName,@Param("version") Integer version);
    int markFactReversed(@Param("factId") Long factId,@Param("userName") String userName,@Param("version") Integer version);
    Map<String,Object> sumProjectFacts(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    java.math.BigDecimal sumProjectPersonnelCost(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    List<Map<String,Object>> selectProjectPersonnelCostDetails(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    java.math.BigDecimal sumProjectCostToDate(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    Integer selectNextResultVersion(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int retireCurrentResult(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int insertDailyResult(Map<String,Object> result);
    int insertDailyResultItem(Map<String,Object> item);
    List<Map<String,Object>> selectDailyResults(Map<String,Object> query);
    List<Map<String,Object>> selectDailyResultItems(Long resultId);
    Map<String,Object> selectDailySummary(Map<String,Object> query);
    int countDraftFacts(Map<String,Object> query);
    List<Map<String,Object>> selectAccountingAlerts(Map<String,Object> query);
    List<Map<String,Object>> selectProjectProfitRanking(Map<String,Object> query);
    List<Map<String,Object>> selectCompanyAccountingSummary(Map<String,Object> query);
    List<Map<String,Object>> selectPersonnelCostOverview(Map<String,Object> query);
    List<Map<String,Object>> selectCompanyPersonnelCostReadiness(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("bizDate") Date bizDate);
    int countProjectsMissingDailyResult(@Param("userId") Long userId,@Param("viewAll") boolean viewAll,
        @Param("bizDate") Date bizDate);
}
