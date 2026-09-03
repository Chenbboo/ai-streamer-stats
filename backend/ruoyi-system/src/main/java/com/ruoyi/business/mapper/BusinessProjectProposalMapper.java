package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessProjectProposal;

public interface BusinessProjectProposalMapper
{
    List<BusinessProjectProposal> selectOwnList(Map<String, Object> query);
    List<BusinessProjectProposal> selectReviewList(Map<String, Object> query);
    List<BusinessProjectProposal> selectDirectory(Map<String, Object> query);
    BusinessProjectProposal selectById(Long proposalId);
    int insertProposal(BusinessProjectProposal proposal);
    int updateDraft(BusinessProjectProposal proposal);
    int updateComputedPlan(BusinessProjectProposal proposal);
    int deleteRevenueLines(Long proposalId);
    int deleteExpenseLines(Long proposalId);
    int deleteStaffingLines(Long proposalId);
    int deleteTargetLines(Long proposalId);
    int insertRevenueLine(Map<String, Object> line);
    int insertExpenseLine(Map<String, Object> line);
    int insertStaffingLine(Map<String, Object> line);
    int insertTargetLine(Map<String, Object> line);
    List<Map<String, Object>> selectRevenueLines(Long proposalId);
    List<Map<String, Object>> selectExpenseLines(Long proposalId);
    List<Map<String, Object>> selectStaffingLines(Long proposalId);
    List<Map<String, Object>> selectTargetLines(Long proposalId);
    int softDelete(@Param("proposalId") Long proposalId, @Param("applicantUserId") Long applicantUserId,
        @Param("version") Integer version, @Param("userName") String userName);
    int submit(@Param("proposalId") Long proposalId, @Param("applicantUserId") Long applicantUserId,
        @Param("version") Integer version, @Param("userName") String userName);
    int activate(@Param("proposalId") Long proposalId, @Param("applicantUserId") Long applicantUserId,
        @Param("version") Integer version, @Param("createdProjectId") Long createdProjectId,
        @Param("operatorName") String operatorName, @Param("userName") String userName);
    int withdraw(@Param("proposalId") Long proposalId, @Param("applicantUserId") Long applicantUserId,
        @Param("version") Integer version, @Param("comment") String comment, @Param("userName") String userName);
    int review(@Param("proposalId") Long proposalId, @Param("sponsorOwnerUserId") Long sponsorOwnerUserId,
        @Param("version") Integer version, @Param("decision") String decision,
        @Param("reviewedUserId") Long reviewedUserId, @Param("reviewedUserName") String reviewedUserName,
        @Param("reviewComment") String reviewComment, @Param("createdProjectId") Long createdProjectId,
        @Param("userName") String userName);
    int insertEvent(Map<String, Object> event);
    List<Map<String, Object>> selectEvents(Long proposalId);
    Map<String, Object> selectActiveUser(Long userId);
    Map<String, Object> selectActiveBoss(Long userId);
    Map<String, Object> selectCompany(Long deptId);
    Map<String, Object> selectParentProject(Long projectId);
    Map<String, Object> selectProposalStaff(@Param("userId") Long userId,
        @Param("effectiveDate") java.util.Date effectiveDate);
    List<Map<String, Object>> selectStaffOptions(@Param("companyDeptId") Long companyDeptId,
        @Param("effectiveDate") java.util.Date effectiveDate);
    List<Map<String, Object>> selectBossOptions(Long excludeUserId);
    List<Map<String, Object>> selectCompanyOptions();
}
