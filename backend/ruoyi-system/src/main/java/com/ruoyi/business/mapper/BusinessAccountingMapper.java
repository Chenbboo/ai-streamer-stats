package com.ruoyi.business.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessOperatingFact;

public interface BusinessAccountingMapper
{
    @org.apache.ibatis.annotations.Select("select confirmed_user_name confirmedUserName,confirmed_at confirmedAt from biz_project_spend_confirmation where project_id=#{projectId} and biz_date=#{bizDate}")
    Map<String,Object> selectSpendConfirmation(@Param("projectId") Long projectId,@Param("bizDate") Date date);
    @org.apache.ibatis.annotations.Insert("insert into biz_project_spend_confirmation(project_id,biz_date,confirmed_user_id,confirmed_user_name,confirmed_at) values(#{projectId},#{bizDate},#{userId},#{userName},now()) on duplicate key update id=id")
    int confirmNoSpend(@Param("projectId") Long projectId,@Param("bizDate") Date date,@Param("userId") Long userId,@Param("userName") String userName);
    List<Map<String,Object>> selectCompanies();
    List<Map<String,Object>> selectCategories();
    default List<Map<String,Object>> selectProjectOptions(Long userId,boolean viewAll)
    { return selectProjectOptions(userId,viewAll,false); }
    List<Map<String,Object>> selectProjectOptions(@Param("userId") Long userId,@Param("viewAll") boolean viewAll,
        @Param("includeClosed") boolean includeClosed);
    List<Map<String,Object>> selectFacts(Map<String,Object> query);
    BusinessOperatingFact selectFactById(Long factId);
    BusinessOperatingFact selectFactByIdForUpdate(Long factId);
    BusinessOperatingFact selectFactByIdempotencyKey(String idempotencyKey);
    Map<String,Object> selectCategoryById(Long categoryId);
    Map<String,Object> selectCategoryByCode(String categoryCode);
    BusinessOperatingFact selectCurrentProjectDailySpend(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    BusinessOperatingFact selectConfirmedProjectDailySpend(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    List<BusinessOperatingFact> selectProjectDailySpendItems(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    Map<String,Object> selectProjectRevenueSummary(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    Map<String,Object> selectProjectForAccounting(Long projectId);
    Map<String,Object> selectProjectForAccountingForUpdate(Long projectId);
    Map<String,Object> selectProjectBonusSettlement(Long settlementId);
    int countProjectSettlementDate(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    Long lockProjectAccounting(Long projectId);
    String selectAccountingMemberRole(@Param("projectId") Long projectId,@Param("userId") Long userId);
    int insertFact(BusinessOperatingFact fact);
    int updateDraftFact(BusinessOperatingFact fact);
    int confirmFact(@Param("factId") Long factId,@Param("userId") Long userId,@Param("userName") String userName,@Param("version") Integer version);
    int returnFact(@Param("factId") Long factId,@Param("reason") String reason,@Param("userId") Long userId,
        @Param("userName") String userName,@Param("version") Integer version);
    int markFactReversed(@Param("factId") Long factId,@Param("userName") String userName,@Param("version") Integer version);
    Map<String,Object> sumProjectFacts(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    java.math.BigDecimal sumProjectPersonnelCost(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    List<Map<String,Object>> selectProjectPersonnelCostDetails(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    java.math.BigDecimal sumProjectCostToDate(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    String selectProjectBudgetSnapshot(@Param("projectId") Long projectId);
    java.math.BigDecimal sumProjectCostInPeriod(@Param("projectId") Long projectId,@Param("from") Date from,@Param("to") Date to);
    List<Map<String,Object>> selectCurrentResultsAfter(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int updateDailyResultBudgetSpent(@Param("resultId") Long resultId,
        @Param("budgetSpent") java.math.BigDecimal budgetSpent);
    Integer selectNextResultVersion(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int retireCurrentResult(@Param("projectId") Long projectId,@Param("bizDate") Date bizDate);
    int insertDailyResult(Map<String,Object> result);
    int insertDailyResultItem(Map<String,Object> item);
    List<Map<String,Object>> selectDailyResults(Map<String,Object> query);
    List<Map<String,Object>> selectDailyResultItems(Long resultId);
    Map<String,Object> selectDailySummary(Map<String,Object> query);
    List<Map<String,Object>> selectDailySummaryByCurrency(Map<String,Object> query);
    int countDraftFacts(Map<String,Object> query);
    List<Map<String,Object>> selectAccountingAlerts(Map<String,Object> query);
    List<Map<String,Object>> selectProjectProfitRanking(Map<String,Object> query);
    List<Map<String,Object>> selectCompanyAccountingSummary(Map<String,Object> query);
    List<Map<String,Object>> selectPersonnelCostOverview(Map<String,Object> query);
    List<Map<String,Object>> selectCompanyPersonnelCostReadiness(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("bizDate") Date bizDate);
    int countProjectsMissingDailyResult(@Param("userId") Long userId,@Param("viewAll") boolean viewAll,
        @Param("bizDate") Date bizDate);
    int countProjectUnsettledFacts(@Param("projectId") Long projectId);
    int closeProjectDailyResults(@Param("projectId") Long projectId,@Param("userName") String userName);
}
