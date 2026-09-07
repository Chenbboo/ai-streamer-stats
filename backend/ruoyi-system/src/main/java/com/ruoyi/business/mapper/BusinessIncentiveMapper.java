package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;

public interface BusinessIncentiveMapper
{
    List<Map<String,Object>> selectProjects(@Param("userId") Long userId, @Param("viewAll") boolean viewAll);
    List<BusinessIncentiveRule> selectRules(Long projectId);
    BusinessIncentiveRule selectRule(Long ruleId);
    Integer nextRuleVersion(Long projectId);
    int insertRule(BusinessIncentiveRule rule);
    int retireRule(@Param("ruleId") Long ruleId, @Param("userName") String userName);
    List<BusinessIncentiveAward> selectAwards(Long projectId);
    BusinessIncentiveAward selectAward(Long awardId);
    BusinessIncentiveAward selectAwardForUpdate(Long awardId);
    BusinessIncentiveAward selectAwardByRequest(@Param("projectId") Long projectId, @Param("requestKey") String requestKey);
    int insertAward(BusinessIncentiveAward award);
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
