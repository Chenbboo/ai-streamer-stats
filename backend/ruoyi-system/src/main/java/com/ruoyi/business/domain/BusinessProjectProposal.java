package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 项目立项申请；申请人同时是正式项目的初始主负责人。 */
public class BusinessProjectProposal extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long proposalId;
    private String proposalNo;
    private Integer submissionVersion;
    private String projectName;
    private Long applicantUserId;
    private String applicantName;
    private Long sponsorOwnerUserId;
    private String sponsorOwnerName;
    private Long companyDeptId;
    private String companyName;
    private Long parentProjectId;
    private String parentProjectName;
    private String projectType;
    private String accountingMode;
    private String managementMode;
    private String closeMethod;
    private String managementReason;
    private String acceptanceCriteria;
    private String objective;
    private String applicationReason;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date planStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date planEndDate;
    private String priority;
    private String baseCurrency;
    private BigDecimal budgetLimit;
    private String noBudget;
    private String revenueModel;
    private BigDecimal estimatedRevenue;
    private BigDecimal estimatedExternalCost;
    private BigDecimal estimatedPersonnelCost;
    private BigDecimal estimatedBonusCost;
    private BigDecimal estimatedTaxCost;
    private BigDecimal contingencyCost;
    private BigDecimal estimatedTotalCost;
    private BigDecimal expectedProfit;
    private BigDecimal expectedMargin;
    private BigDecimal breakEvenRevenue;
    private BigDecimal peakCashNeed;
    private Integer plannedHeadcount;
    private String fundingPlan;
    private String keyAssumptions;
    private String riskSummary;
    private String stopLossRule;
    private List<Map<String, Object>> revenueLines;
    private List<Map<String, Object>> expenseLines;
    private List<Map<String, Object>> staffingLines;
    private List<Map<String, Object>> targetLines;
    private String executionSource;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date submittedTime;
    private Long reviewedUserId;
    private String reviewedUserName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date reviewedTime;
    private String reviewComment;
    private Long createdProjectId;
    private Integer version;
    private String delFlag;
    private Boolean canOpen;
    private Boolean canEdit;
    private Boolean canReview;
    private List<Map<String, Object>> events;

    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public String getProposalNo() { return proposalNo; }
    public void setProposalNo(String proposalNo) { this.proposalNo = proposalNo; }
    public Integer getSubmissionVersion() { return submissionVersion; }
    public void setSubmissionVersion(Integer submissionVersion) { this.submissionVersion = submissionVersion; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getSponsorOwnerUserId() { return sponsorOwnerUserId; }
    public void setSponsorOwnerUserId(Long sponsorOwnerUserId) { this.sponsorOwnerUserId = sponsorOwnerUserId; }
    public String getSponsorOwnerName() { return sponsorOwnerName; }
    public void setSponsorOwnerName(String sponsorOwnerName) { this.sponsorOwnerName = sponsorOwnerName; }
    public Long getCompanyDeptId() { return companyDeptId; }
    public void setCompanyDeptId(Long companyDeptId) { this.companyDeptId = companyDeptId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Long getParentProjectId() { return parentProjectId; }
    public void setParentProjectId(Long parentProjectId) { this.parentProjectId = parentProjectId; }
    public String getParentProjectName() { return parentProjectName; }
    public void setParentProjectName(String parentProjectName) { this.parentProjectName = parentProjectName; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getAccountingMode() { return accountingMode; }
    public void setAccountingMode(String accountingMode) { this.accountingMode = accountingMode; }
    public String getManagementMode() { return managementMode; }
    public void setManagementMode(String managementMode) { this.managementMode = managementMode; }
    public String getCloseMethod() { return closeMethod; }
    public void setCloseMethod(String closeMethod) { this.closeMethod = closeMethod; }
    public String getManagementReason() { return managementReason; }
    public void setManagementReason(String managementReason) { this.managementReason = managementReason; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getApplicationReason() { return applicationReason; }
    public void setApplicationReason(String applicationReason) { this.applicationReason = applicationReason; }
    public Date getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(Date planStartDate) { this.planStartDate = planStartDate; }
    public Date getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(Date planEndDate) { this.planEndDate = planEndDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public BigDecimal getBudgetLimit() { return budgetLimit; }
    public void setBudgetLimit(BigDecimal budgetLimit) { this.budgetLimit = budgetLimit; }
    public String getNoBudget() { return noBudget; }
    public void setNoBudget(String noBudget) { this.noBudget = noBudget; }
    public String getRevenueModel() { return revenueModel; }
    public void setRevenueModel(String revenueModel) { this.revenueModel = revenueModel; }
    public BigDecimal getEstimatedRevenue() { return estimatedRevenue; }
    public void setEstimatedRevenue(BigDecimal estimatedRevenue) { this.estimatedRevenue = estimatedRevenue; }
    public BigDecimal getEstimatedExternalCost() { return estimatedExternalCost; }
    public void setEstimatedExternalCost(BigDecimal estimatedExternalCost) { this.estimatedExternalCost = estimatedExternalCost; }
    public BigDecimal getEstimatedPersonnelCost() { return estimatedPersonnelCost; }
    public void setEstimatedPersonnelCost(BigDecimal estimatedPersonnelCost) { this.estimatedPersonnelCost = estimatedPersonnelCost; }
    public BigDecimal getEstimatedBonusCost() { return estimatedBonusCost; }
    public void setEstimatedBonusCost(BigDecimal estimatedBonusCost) { this.estimatedBonusCost = estimatedBonusCost; }
    public BigDecimal getEstimatedTaxCost() { return estimatedTaxCost; }
    public void setEstimatedTaxCost(BigDecimal estimatedTaxCost) { this.estimatedTaxCost = estimatedTaxCost; }
    public BigDecimal getContingencyCost() { return contingencyCost; }
    public void setContingencyCost(BigDecimal contingencyCost) { this.contingencyCost = contingencyCost; }
    public BigDecimal getEstimatedTotalCost() { return estimatedTotalCost; }
    public void setEstimatedTotalCost(BigDecimal estimatedTotalCost) { this.estimatedTotalCost = estimatedTotalCost; }
    public BigDecimal getExpectedProfit() { return expectedProfit; }
    public void setExpectedProfit(BigDecimal expectedProfit) { this.expectedProfit = expectedProfit; }
    public BigDecimal getExpectedMargin() { return expectedMargin; }
    public void setExpectedMargin(BigDecimal expectedMargin) { this.expectedMargin = expectedMargin; }
    public BigDecimal getBreakEvenRevenue() { return breakEvenRevenue; }
    public void setBreakEvenRevenue(BigDecimal breakEvenRevenue) { this.breakEvenRevenue = breakEvenRevenue; }
    public BigDecimal getPeakCashNeed() { return peakCashNeed; }
    public void setPeakCashNeed(BigDecimal peakCashNeed) { this.peakCashNeed = peakCashNeed; }
    public Integer getPlannedHeadcount() { return plannedHeadcount; }
    public void setPlannedHeadcount(Integer plannedHeadcount) { this.plannedHeadcount = plannedHeadcount; }
    public String getFundingPlan() { return fundingPlan; }
    public void setFundingPlan(String fundingPlan) { this.fundingPlan = fundingPlan; }
    public String getKeyAssumptions() { return keyAssumptions; }
    public void setKeyAssumptions(String keyAssumptions) { this.keyAssumptions = keyAssumptions; }
    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }
    public String getStopLossRule() { return stopLossRule; }
    public void setStopLossRule(String stopLossRule) { this.stopLossRule = stopLossRule; }
    public List<Map<String, Object>> getRevenueLines() { return revenueLines; }
    public void setRevenueLines(List<Map<String, Object>> revenueLines) { this.revenueLines = revenueLines; }
    public List<Map<String, Object>> getExpenseLines() { return expenseLines; }
    public void setExpenseLines(List<Map<String, Object>> expenseLines) { this.expenseLines = expenseLines; }
    public List<Map<String, Object>> getStaffingLines() { return staffingLines; }
    public void setStaffingLines(List<Map<String, Object>> staffingLines) { this.staffingLines = staffingLines; }
    public List<Map<String, Object>> getTargetLines() { return targetLines; }
    public void setTargetLines(List<Map<String, Object>> targetLines) { this.targetLines = targetLines; }
    public String getExecutionSource() { return executionSource; }
    public void setExecutionSource(String executionSource) { this.executionSource = executionSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public Long getCreatedProjectId() { return createdProjectId; }
    public void setCreatedProjectId(Long createdProjectId) { this.createdProjectId = createdProjectId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Boolean getCanOpen() { return canOpen; }
    public void setCanOpen(Boolean canOpen) { this.canOpen = canOpen; }
    public Boolean getCanEdit() { return canEdit; }
    public void setCanEdit(Boolean canEdit) { this.canEdit = canEdit; }
    public Boolean getCanReview() { return canReview; }
    public void setCanReview(Boolean canReview) { this.canReview = canReview; }
    public List<Map<String, Object>> getEvents() { return events; }
    public void setEvents(List<Map<String, Object>> events) { this.events = events; }
}
