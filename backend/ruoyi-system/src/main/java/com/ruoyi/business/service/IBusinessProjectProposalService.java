package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.business.domain.BusinessProjectProposal;

public interface IBusinessProjectProposalService
{
    List<BusinessProjectProposal> listOwn(Map<String, Object> query, Long userId, boolean viewAll);
    List<BusinessProjectProposal> listForReview(Map<String, Object> query, Long userId, boolean boss);
    List<BusinessProjectProposal> directory(Map<String, Object> query, boolean boss, boolean viewAll);
    BusinessProjectProposal get(Long proposalId, Long userId, boolean boss, boolean viewAll);
    BusinessProjectProposal create(BusinessProjectProposal proposal, Long userId, String userName);
    BusinessProjectProposal update(BusinessProjectProposal proposal, Long userId, String userName);
    void delete(Long proposalId, Long userId, String userName);
    BusinessProjectProposal submit(Long proposalId, Long userId, String userName);
    BusinessProjectProposal withdraw(Long proposalId, String comment, Long userId, String userName);
    BusinessProjectProposal review(Long proposalId, String decision, String comment,
        Long userId, String userName, boolean boss);
    Map<String, Object> options(Long userId);
    List<Map<String, Object>> staffOptions(Long companyDeptId, String effectiveDate, Long userId);
}
