package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.domain.BusinessIncentiveTier;

public interface BusinessIncentiveMapper
{
    int countDistributionReservations(Long awardId);
    List<Map<String,Object>> selectProjects(@Param("userId") Long userId, @Param("viewAll") boolean viewAll);
    Map<String,Object> selectBonusSetting(Long projectId);
    int saveBonusSetting(Map<String,Object> setting);
    int insertBonusSettingEvent(Map<String,Object> event);
    List<BusinessIncentiveRule> selectRules(Long projectId);
    BusinessIncentiveRule selectRule(Long ruleId);
    Integer nextRuleVersion(Long projectId);
    int insertRule(BusinessIncentiveRule rule);
    int retireProfitRules(@Param("projectId") Long projectId, @Param("userName") String userName);
    List<BusinessIncentiveTier> selectRuleTiers(Long ruleId);
    int insertTier(BusinessIncentiveTier tier);
    int retirePlanRules(@Param("projectId") Long projectId, @Param("kpiPlanId") Long kpiPlanId, @Param("userName") String userName);
    int countExistingScoreAward(@Param("projectId") Long projectId, @Param("settlementId") Long settlementId);
    int countExistingMonthlyAward(@Param("projectId") Long projectId, @Param("settlementMonth") String settlementMonth);
    int retireRule(@Param("ruleId") Long ruleId, @Param("userName") String userName);
    List<BusinessIncentiveAward> selectAwards(Long projectId);
    BusinessIncentiveAward selectAward(Long awardId);
    BusinessIncentiveAward selectAwardForUpdate(Long awardId);
    BusinessIncentiveAward selectAwardByRequest(@Param("projectId") Long projectId, @Param("requestKey") String requestKey);
    int insertAward(BusinessIncentiveAward award);
    int updateAwardDraft(@Param("awardId") Long awardId, @Param("version") Integer version,
        @Param("bizDate") java.util.Date bizDate, @Param("reason") String reason,
        @Param("allocationProposalJson") String allocationProposalJson, @Param("userName") String userName);
    int transitionAward(@Param("awardId") Long awardId, @Param("fromStatus") String fromStatus,
        @Param("toStatus") String toStatus, @Param("version") Integer version, @Param("userId") Long userId,
        @Param("userName") String userName, @Param("reason") String reason, @Param("factId") Long factId);
    int countPendingAwards(Long projectId);
    int countExistingEvidenceAward(@Param("projectId") Long projectId, @Param("ruleId") Long ruleId, @Param("settlementId") Long settlementId);
    List<Map<String,Object>> selectConfirmedKpis(Long projectId);
    List<Map<String,Object>> selectLegacyBonuses(Long projectId);
    List<Map<String,Object>> selectEvents(Long projectId);
    int insertEvent(Map<String,Object> event);
    int voidSourceFact(@Param("factId") Long factId, @Param("version") Integer version, @Param("userName") String userName);
    int resubmitSourceFact(@Param("factId") Long factId, @Param("version") Integer version, @Param("userName") String userName);
}
