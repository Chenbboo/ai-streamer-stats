package com.ruoyi.business.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.ruoyi.business.domain.BusinessOperatingFact;

public interface IBusinessAccountingService
{
    Map<String,Object> dashboard(Map<String,Object> query,Long userId,boolean viewAll);
    Map<String,Object> projectDashboard(Long projectId,Map<String,Object> query,Long userId,boolean viewAll);
    Map<String,Object> bossOverview(Long userId,boolean viewAll);
    Map<String,Object> bossOverview(String bizDate,Long userId,boolean viewAll);
    Map<String,Object> bossOverview(String bizDate,Long companyDeptId,Long userId,boolean viewAll);
    Map<String,Object> personnelCostOverview(Map<String,Object> query,Long userId,boolean viewAll);
    List<Map<String,Object>> facts(Map<String,Object> query,Long userId,boolean viewAll);
    BusinessOperatingFact saveFact(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact saveProjectFact(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact saveProjectDailySpend(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact reverseProjectDailySpend(Long factId,String reason,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact confirmFact(Long factId,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact returnFact(Long factId,String reason,Long userId,String userName,boolean viewAll);
    BusinessOperatingFact reverseFact(Long factId,String reason,Long userId,String userName,boolean viewAll);
    Map<String,Object> recalculate(Long projectId,Date bizDate,Long userId,String userName,boolean viewAll);
    Map<String,Object> recalculatePersonnelCost(Long projectId,Date bizDate,String userName);
    Map<String,Object> recalculatePublicExpenseCost(Long projectId,Date bizDate,String userName);
    void ensureProjectCanClose(Long projectId);
    void closeProjectAccounting(Long projectId,Date closeDate,String userName);
    BusinessOperatingFact recordProjectBonus(Long projectId,Date bizDate,java.math.BigDecimal amount,
        Long settlementId,Long userId,String userName);
    void recordSubprojectFundingTransfer(Long parentProjectId,Long childProjectId,Long proposalId,
        BigDecimal amount,String currency,String reason,Long userId,String userName);
    Map<String,Object> resultDetail(Long resultId,Long userId,boolean viewAll);
}
