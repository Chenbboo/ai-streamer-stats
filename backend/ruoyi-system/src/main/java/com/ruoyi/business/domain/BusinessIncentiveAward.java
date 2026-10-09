package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String attachmentUrls;
    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String value) { attachmentUrls = value; }
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
    private BusinessBonusAllocation applicationAllocation;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String settlementMonth;
    private String applicationMonth;
    public String getApplicationMonth() { return applicationMonth; }
    public void setApplicationMonth(String value) { applicationMonth = value; }
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String bonusBlockReason;

    public String getSettlementMonth() { return applicationMonth == null ? settlementMonth : applicationMonth; }
    public void setSettlementMonth(String value) { settlementMonth = value; }
    public String getBonusBlockReason() { return bonusBlockReason; }
    public void setBonusBlockReason(String value) { bonusBlockReason = value; }

    public BusinessBonusAllocation getApplicationAllocation() { return applicationAllocation; }
    public void setApplicationAllocation(BusinessBonusAllocation value) { applicationAllocation = value; }
    @JsonIgnore
    public String getAllocationProposalJson()
    { return applicationAllocation == null ? null : com.alibaba.fastjson2.JSON.toJSONString(applicationAllocation); }
    @JsonIgnore
    public void setAllocationProposalJson(String value)
    { applicationAllocation = value == null || value.trim().isEmpty() ? null : com.alibaba.fastjson2.JSON.parseObject(value, BusinessBonusAllocation.class); }
    // Read-only details of the exact immutable rule version referenced by this award.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal ruleAfterTaxProfit;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal ruleMainOwnerBonusRate;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal ruleSponsorOwnerBonusRate;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String ruleReason;

    public BigDecimal getRuleAfterTaxProfit() { return ruleAfterTaxProfit; }
    public void setRuleAfterTaxProfit(BigDecimal value) { ruleAfterTaxProfit = value; }
    public BigDecimal getRuleMainOwnerBonusRate() { return ruleMainOwnerBonusRate; }
    public void setRuleMainOwnerBonusRate(BigDecimal value) { ruleMainOwnerBonusRate = value; }
    public BigDecimal getRuleSponsorOwnerBonusRate() { return ruleSponsorOwnerBonusRate; }
    public void setRuleSponsorOwnerBonusRate(BigDecimal value) { ruleSponsorOwnerBonusRate = value; }
    public String getRuleReason() { return ruleReason; }
    public void setRuleReason(String value) { ruleReason = value; }
    public BigDecimal getRuleMainOwnerBonusAmount() { return ruleShareAmount(ruleMainOwnerBonusRate); }
    public BigDecimal getRuleSponsorOwnerBonusAmount() { return ruleShareAmount(ruleSponsorOwnerBonusRate); }
    private BigDecimal ruleShareAmount(BigDecimal rate)
    {
        if (!"PROFIT_SHARE_V1".equals(policyVersion) || ruleAfterTaxProfit == null || rate == null
            || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) return null;
        return ruleAfterTaxProfit.max(BigDecimal.ZERO).multiply(rate)
            .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

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
