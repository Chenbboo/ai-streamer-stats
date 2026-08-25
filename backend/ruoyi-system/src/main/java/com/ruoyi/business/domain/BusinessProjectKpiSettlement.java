package com.ruoyi.business.domain;

import java.util.Date;
import java.util.List;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectKpiSettlement extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long settlementId;
    private Long planId;
    private Long projectId;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date periodStart;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date periodEnd;
    private String status;
    private BigDecimal totalScore;
    private BigDecimal bonusAmount;
    private String currency;
    private Long submittedUserId;
    private String submittedUserName;
    private Date submittedTime;
    private Long reviewedUserId;
    private String reviewedUserName;
    private Date reviewedTime;
    private String reviewComment;
    private Long accountingFactId;
    private Long voidedUserId;
    private String voidedUserName;
    private Date voidedTime;
    private Integer version;
    private List<BusinessProjectKpiResult> results;

    public Long getSettlementId() { return settlementId; }
    public void setSettlementId(Long settlementId) { this.settlementId = settlementId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Date getPeriodStart() { return periodStart; }
    public void setPeriodStart(Date periodStart) { this.periodStart = periodStart; }
    public Date getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Date periodEnd) { this.periodEnd = periodEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public BigDecimal getBonusAmount() { return bonusAmount; }
    public void setBonusAmount(BigDecimal bonusAmount) { this.bonusAmount = bonusAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long submittedUserId) { this.submittedUserId = submittedUserId; }
    public String getSubmittedUserName() { return submittedUserName; }
    public void setSubmittedUserName(String submittedUserName) { this.submittedUserName = submittedUserName; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public Long getReviewedUserId() { return reviewedUserId; }
    public void setReviewedUserId(Long reviewedUserId) { this.reviewedUserId = reviewedUserId; }
    public String getReviewedUserName() { return reviewedUserName; }
    public void setReviewedUserName(String reviewedUserName) { this.reviewedUserName = reviewedUserName; }
    public Date getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(Date reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getAccountingFactId() { return accountingFactId; }
    public void setAccountingFactId(Long accountingFactId) { this.accountingFactId = accountingFactId; }
    public Long getVoidedUserId() { return voidedUserId; }
    public void setVoidedUserId(Long voidedUserId) { this.voidedUserId = voidedUserId; }
    public String getVoidedUserName() { return voidedUserName; }
    public void setVoidedUserName(String voidedUserName) { this.voidedUserName = voidedUserName; }
    public Date getVoidedTime() { return voidedTime; }
    public void setVoidedTime(Date voidedTime) { this.voidedTime = voidedTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<BusinessProjectKpiResult> getResults() { return results; }
    public void setResults(List<BusinessProjectKpiResult> results) { this.results = results; }
}
