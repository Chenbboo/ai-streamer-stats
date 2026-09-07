package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessIncentiveAward extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long awardId;
    private Long projectId;
    private Long companyDeptId;
    private Long ruleId;
    private Integer ruleVersion;
    private String ruleName;
    private String policyVersion;
    private Long settlementId;
    private BigDecimal scoreSnapshot;
    private BigDecimal amount;
    private String currency;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date bizDate;
    private String reason;
    private String requestKey;
    private String status;
    private Long applicantUserId;
    private String applicantUserName;
    private Long approvedUserId;
    private String approvedUserName;
    private Date approvedTime;
    private Long accountingFactId;
    private String costStatus;
    private String reviewComment;
    private Integer version;
    private Boolean canSubmit;
    private Boolean canReview;
    private Boolean canCancel;
    private Boolean canResubmitCost;
    private List<Map<String,Object>> events;

    public Long getAwardId() { return awardId; }
    public void setAwardId(Long value) { awardId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getCompanyDeptId() { return companyDeptId; }
    public void setCompanyDeptId(Long value) { companyDeptId = value; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long value) { ruleId = value; }
    public Integer getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(Integer value) { ruleVersion = value; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String value) { ruleName = value; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String value) { policyVersion = value; }
    public Long getSettlementId() { return settlementId; }
    public void setSettlementId(Long value) { settlementId = value; }
    public BigDecimal getScoreSnapshot() { return scoreSnapshot; }
    public void setScoreSnapshot(BigDecimal value) { scoreSnapshot = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public Date getBizDate() { return bizDate; }
    public void setBizDate(Date value) { bizDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long value) { applicantUserId = value; }
    public String getApplicantUserName() { return applicantUserName; }
    public void setApplicantUserName(String value) { applicantUserName = value; }
    public Long getApprovedUserId() { return approvedUserId; }
    public void setApprovedUserId(Long value) { approvedUserId = value; }
    public String getApprovedUserName() { return approvedUserName; }
    public void setApprovedUserName(String value) { approvedUserName = value; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date value) { approvedTime = value; }
    public Long getAccountingFactId() { return accountingFactId; }
    public void setAccountingFactId(Long value) { accountingFactId = value; }
    public String getCostStatus() { return costStatus; }
    public void setCostStatus(String value) { costStatus = value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { version = value; }
    public Boolean getCanSubmit() { return canSubmit; }
    public void setCanSubmit(Boolean value) { canSubmit = value; }
    public Boolean getCanReview() { return canReview; }
    public void setCanReview(Boolean value) { canReview = value; }
    public Boolean getCanCancel() { return canCancel; }
    public void setCanCancel(Boolean value) { canCancel = value; }
    public Boolean getCanResubmitCost() { return canResubmitCost; }
    public void setCanResubmitCost(Boolean value) { canResubmitCost = value; }
    public String getPaymentStatus() { return "NOT_RECORDED"; }
    public String getAllocationStatus() { return "NOT_RECORDED"; }
    public List<Map<String,Object>> getEvents() { return events; }
    public void setEvents(List<Map<String,Object>> value) { events = value; }
}
