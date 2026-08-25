package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;

public interface IBusinessProjectKpiService
{
    List<Map<String, Object>> overview(Long userId, boolean viewAll, boolean boss);
    List<Map<String, Object>> overview(List<Long> projectIds, Long userId, boolean viewAll, boolean boss);
    Map<String, Object> workspace(Long projectId, Long planId, Long userId, boolean viewAll, boolean boss);
    Map<String, Object> publishPlan(BusinessProjectKpiPlan plan, Long userId, String userName,
        boolean viewAll, boolean boss);
    void voidPlan(Long planId, Long userId, String userName, boolean viewAll, boolean boss);
    BusinessProjectKpiSettlement saveResults(Long settlementId, BusinessProjectKpiSettlement input,
        Long userId, String userName, boolean viewAll);
    BusinessProjectKpiSettlement submit(Long settlementId, Long userId, String userName, boolean viewAll);
    BusinessProjectKpiSettlement review(Long settlementId, String decision, String comment,
        Long userId, String userName, boolean viewAll, boolean boss);
}
