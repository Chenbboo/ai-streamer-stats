package com.ruoyi.business.service;

import java.util.Map;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.domain.BusinessIncentiveAward;

public interface IBusinessIncentiveService
{
    Map<String,Object> workspace(Long projectId, Long userId, boolean viewAll);
    BusinessIncentiveRule publishRule(BusinessIncentiveRule input, Long userId, String userName, boolean viewAll);
    void retireRule(Long ruleId, String reason, Long userId, String userName, boolean viewAll);
    Map<String,Object> estimate(Long projectId, Long ruleId, Long settlementId, Long userId, boolean viewAll);
    BusinessIncentiveAward createAward(BusinessIncentiveAward input, Long userId, String userName);
    BusinessIncentiveAward submit(Long awardId, Integer version, String reason, Long userId, String userName);
    BusinessIncentiveAward review(Long awardId, Integer version, String decision, String reason, Long userId, String userName);
    BusinessIncentiveAward cancel(Long awardId, Integer version, String reason, Long userId, String userName);
    BusinessIncentiveAward resubmitCost(Long awardId, Integer version, String reason, Long userId, String userName);
}
