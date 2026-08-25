package com.ruoyi.business.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessProjectBonusTier;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiPlanItem;
import com.ruoyi.business.domain.BusinessProjectKpiResult;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;

public interface BusinessProjectKpiMapper
{
    List<Map<String, Object>> selectProjectOverviews(@Param("userId") Long userId,
        @Param("viewAll") boolean viewAll, @Param("boss") boolean boss,
        @Param("projectIds") List<Long> projectIds);
    Integer selectNextPlanVersion(Long projectId);
    int countOverlappingPlans(@Param("projectId") Long projectId,
        @Param("cycleStart") Date cycleStart, @Param("cycleEnd") Date cycleEnd);
    int insertPlan(BusinessProjectKpiPlan plan);
    int insertPlanItem(BusinessProjectKpiPlanItem item);
    int insertBonusTier(BusinessProjectBonusTier tier);
    int insertSettlement(BusinessProjectKpiSettlement settlement);
    List<Map<String, Object>> selectPlanSummaries(Long projectId);
    Long selectLatestPlanId(Long projectId);
    BusinessProjectKpiPlan selectPlanById(Long planId);
    List<BusinessProjectKpiPlanItem> selectPlanItems(Long planId);
    BusinessProjectKpiPlanItem selectPlanItemById(Long itemId);
    List<BusinessProjectBonusTier> selectBonusTiers(Long planId);
    BusinessProjectKpiSettlement selectSettlementById(Long settlementId);
    BusinessProjectKpiSettlement selectSettlementByPlanId(Long planId);
    List<BusinessProjectKpiResult> selectSettlementResults(Long settlementId);
    int voidDraftSettlement(@Param("planId") Long planId, @Param("userId") Long userId,
        @Param("userName") String userName);
    int voidPublishedPlan(@Param("planId") Long planId, @Param("userId") Long userId,
        @Param("userName") String userName);
    int upsertSettlementResult(BusinessProjectKpiResult result);
    int updateSettlementPreview(@Param("settlementId") Long settlementId,
        @Param("totalScore") java.math.BigDecimal totalScore,
        @Param("bonusAmount") java.math.BigDecimal bonusAmount,
        @Param("userName") String userName, @Param("version") Integer version);
    int submitSettlement(@Param("settlementId") Long settlementId,
        @Param("totalScore") java.math.BigDecimal totalScore,
        @Param("bonusAmount") java.math.BigDecimal bonusAmount,
        @Param("userId") Long userId, @Param("userName") String userName,
        @Param("version") Integer version);
    int returnSettlement(@Param("settlementId") Long settlementId,
        @Param("comment") String comment, @Param("userId") Long userId,
        @Param("userName") String userName, @Param("version") Integer version);
    int confirmSettlement(@Param("settlementId") Long settlementId,
        @Param("totalScore") java.math.BigDecimal totalScore,
        @Param("bonusAmount") java.math.BigDecimal bonusAmount,
        @Param("accountingFactId") Long accountingFactId,
        @Param("comment") String comment, @Param("userId") Long userId,
        @Param("userName") String userName, @Param("version") Integer version);
    int closePlan(@Param("planId") Long planId);
}
