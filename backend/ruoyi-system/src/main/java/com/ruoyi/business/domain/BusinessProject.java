package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 公司经营项目。 */
public class BusinessProject extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private BigDecimal progressWeight;
    public BigDecimal getProgressWeight() { return progressWeight; }
    public void setProgressWeight(BigDecimal value) { progressWeight = value; }
    private Integer subprojectCount;
    public Integer getSubprojectCount() { return subprojectCount; }
    public void setSubprojectCount(Integer value) { subprojectCount = value; }

    private Long projectId;
    private String projectNo;
    private Long sourceProposalId;
    private Long parentId;
    private String parentName;
    private Long companyDeptId;
    private String companyName;
    private String projectName;
    private String templateVersion;
    private String templateSnapshotJson;
    private String projectType;
    private String accountingMode;
    private String managementMode;
    private String closeMethod;
    private String managementReason;
    private String acceptanceCriteria;
    /** TOTAL=计入总目标；NO_TOTAL=持续经营、不设置项目总完成百分比。 */
    private String goalMode;
    /** 仅用于已运行项目切换目标模式时提交变更原因。 */
    private String goalModeChangeReason;
    /** 仅用于治理模式变更时提交原因，不作为项目主档字段持久化。 */
    private String governanceChangeReason;
    private String objective;
    private String status;
    private String deliveryPolicyVersion;
    private String accountingState;
    private String settlementPolicyVersion;
    private String costPolicyVersion;
    private String baselineStatus;
    private Long applicantUserId;
    private String applicantName;
    private Long sponsorOwnerUserId;
    private String sponsorOwnerName;
    private Long initiatorUserId;
    private String initiatorName;
    private Long mainOwnerUserId;
    private String mainOwnerName;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date planStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date planEndDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date actualStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date actualEndDate;
    private String priority;
    private String baseCurrency;
    private BigDecimal budgetLimit;
    private String budgetMode;
    private BigDecimal dailyBudgetLimit;
    private String budgetScope;
    private BigDecimal startupBudgetLimit;
    private String budgetReason;
    private Integer baselineVersion;
    private Integer version;
    private String delFlag;
    private Integer memberCount;
    private Integer taskCount;
    private Integer completedTaskCount;
    /** 项目负责人最新填报的项目完成百分比；已关闭项目固定为 100。 */
    private Integer progressPercent;
    private Long progressReportId;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date progressBizDate;
    private String progressSummary;
    private String progressEvidenceUrls;
    private String progressReporterName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date progressReportTime;
    private Integer openRiskCount;
    private Long matchedChildId;
    public Long getMatchedChildId() { return matchedChildId; }
    public void setMatchedChildId(Long value) { matchedChildId = value; }
    private boolean contextOnly;
    private boolean manageable;
    public boolean isContextOnly() { return contextOnly; }
    public void setContextOnly(boolean value) { contextOnly = value; }
    public boolean isManageable() { return manageable; }
    public void setManageable(boolean value) { manageable = value; }
    /** 关联的执行数据源；当前一期仅支持 LIVE。 */
    private String executionSource;
    private List<BusinessProjectMember> members;
    private List<BusinessProjectMilestone> milestones;
    private List<BusinessProjectTask> tasks;
    private List<BusinessProjectTask> inactiveTasks;
    private List<BusinessProjectRoutine> routines;
    private List<BusinessProjectRoutine> retiredRoutines;
    private List<BusinessProjectRisk> risks;
    private List<Map<String, Object>> ownerHistory;
    private List<BusinessProjectAcceptance> acceptances;
    private List<BusinessProjectStageAcceptance> stageAcceptances;
    private Map<String, Object> governanceProfile;
    private List<Map<String, Object>> events;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectNo() { return projectNo; }
    public void setProjectNo(String projectNo) { this.projectNo = projectNo; }
    public Long getSourceProposalId() { return sourceProposalId; }
    public void setSourceProposalId(Long sourceProposalId) { this.sourceProposalId = sourceProposalId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public Long getCompanyDeptId() { return companyDeptId; }
    public void setCompanyDeptId(Long companyDeptId) { this.companyDeptId = companyDeptId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String value) { templateVersion = value; }
    public String getTemplateSnapshotJson() { return templateSnapshotJson; }
    public Map<String,Object> getBudget() { return com.ruoyi.business.support.BusinessBudgetSnapshot.read(templateSnapshotJson); }
    public void setTemplateSnapshotJson(String value) { templateSnapshotJson = value; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
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
    public String getGoalMode() { return goalMode; }
    public void setGoalMode(String goalMode) { this.goalMode = goalMode; }
    public String getGoalModeChangeReason() { return goalModeChangeReason; }
    public void setGoalModeChangeReason(String goalModeChangeReason) { this.goalModeChangeReason = goalModeChangeReason; }
    public String getGovernanceChangeReason() { return governanceChangeReason; }
    public void setGovernanceChangeReason(String governanceChangeReason) { this.governanceChangeReason = governanceChangeReason; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeliveryPolicyVersion() { return deliveryPolicyVersion; }
    public void setDeliveryPolicyVersion(String deliveryPolicyVersion) { this.deliveryPolicyVersion = deliveryPolicyVersion; }
    public String getAccountingState() { return accountingState; }
    public void setAccountingState(String accountingState) { this.accountingState = accountingState; }
    public String getSettlementPolicyVersion() { return settlementPolicyVersion; }
    public void setSettlementPolicyVersion(String settlementPolicyVersion) { this.settlementPolicyVersion = settlementPolicyVersion; }
    public String getCostPolicyVersion() { return costPolicyVersion; }
    public void setCostPolicyVersion(String costPolicyVersion) { this.costPolicyVersion = costPolicyVersion; }
    public String getBaselineStatus() { return baselineStatus; }
    public void setBaselineStatus(String baselineStatus) { this.baselineStatus = baselineStatus; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getSponsorOwnerUserId() { return sponsorOwnerUserId; }
    public void setSponsorOwnerUserId(Long sponsorOwnerUserId) { this.sponsorOwnerUserId = sponsorOwnerUserId; }
    public String getSponsorOwnerName() { return sponsorOwnerName; }
    public void setSponsorOwnerName(String sponsorOwnerName) { this.sponsorOwnerName = sponsorOwnerName; }
    public Long getInitiatorUserId() { return initiatorUserId; }
    public void setInitiatorUserId(Long initiatorUserId) { this.initiatorUserId = initiatorUserId; }
    public String getInitiatorName() { return initiatorName; }
    public void setInitiatorName(String initiatorName) { this.initiatorName = initiatorName; }
    public Long getMainOwnerUserId() { return mainOwnerUserId; }
    public void setMainOwnerUserId(Long mainOwnerUserId) { this.mainOwnerUserId = mainOwnerUserId; }
    public String getMainOwnerName() { return mainOwnerName; }
    public void setMainOwnerName(String mainOwnerName) { this.mainOwnerName = mainOwnerName; }
    public Date getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(Date planStartDate) { this.planStartDate = planStartDate; }
    public Date getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(Date planEndDate) { this.planEndDate = planEndDate; }
    public Date getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(Date actualStartDate) { this.actualStartDate = actualStartDate; }
    public Date getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(Date actualEndDate) { this.actualEndDate = actualEndDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public BigDecimal getBudgetLimit() { return budgetLimit; }
    public void setBudgetLimit(BigDecimal budgetLimit) { this.budgetLimit = budgetLimit; }
    public String getBudgetMode() { return budgetMode; }
    public void setBudgetMode(String budgetMode) { this.budgetMode = budgetMode; }
    public BigDecimal getDailyBudgetLimit() { return dailyBudgetLimit; }
    public void setDailyBudgetLimit(BigDecimal dailyBudgetLimit) { this.dailyBudgetLimit = dailyBudgetLimit; }
    public String getBudgetScope() { return budgetScope; }
    public void setBudgetScope(String budgetScope) { this.budgetScope = budgetScope; }
    public BigDecimal getStartupBudgetLimit() { return startupBudgetLimit; }
    public void setStartupBudgetLimit(BigDecimal startupBudgetLimit) { this.startupBudgetLimit = startupBudgetLimit; }
    public String getBudgetReason() { return budgetReason; }
    public void setBudgetReason(String budgetReason) { this.budgetReason = budgetReason; }
    public Integer getBaselineVersion() { return baselineVersion; }
    public void setBaselineVersion(Integer baselineVersion) { this.baselineVersion = baselineVersion; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getTaskCount() { return taskCount; }
    public void setTaskCount(Integer taskCount) { this.taskCount = taskCount; }
    public Integer getCompletedTaskCount() { return completedTaskCount; }
    public void setCompletedTaskCount(Integer completedTaskCount) { this.completedTaskCount = completedTaskCount; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public Long getProgressReportId() { return progressReportId; }
    public void setProgressReportId(Long progressReportId) { this.progressReportId = progressReportId; }
    public Date getProgressBizDate() { return progressBizDate; }
    public void setProgressBizDate(Date progressBizDate) { this.progressBizDate = progressBizDate; }
    public String getProgressSummary() { return progressSummary; }
    public void setProgressSummary(String progressSummary) { this.progressSummary = progressSummary; }
    public String getProgressEvidenceUrls() { return progressEvidenceUrls; }
    public void setProgressEvidenceUrls(String progressEvidenceUrls) { this.progressEvidenceUrls = progressEvidenceUrls; }
    public String getProgressReporterName() { return progressReporterName; }
    public void setProgressReporterName(String progressReporterName) { this.progressReporterName = progressReporterName; }
    public Date getProgressReportTime() { return progressReportTime; }
    public void setProgressReportTime(Date progressReportTime) { this.progressReportTime = progressReportTime; }
    public Integer getOpenRiskCount() { return openRiskCount; }
    public void setOpenRiskCount(Integer openRiskCount) { this.openRiskCount = openRiskCount; }
    public String getExecutionSource() { return executionSource; }
    public void setExecutionSource(String executionSource) { this.executionSource = executionSource; }
    public List<BusinessProjectMember> getMembers() { return members; }
    public void setMembers(List<BusinessProjectMember> members) { this.members = members; }
    public List<BusinessProjectMilestone> getMilestones() { return milestones; }
    public void setMilestones(List<BusinessProjectMilestone> milestones) { this.milestones = milestones; }
    public List<BusinessProjectTask> getTasks() { return tasks; }
    public void setTasks(List<BusinessProjectTask> tasks) { this.tasks = tasks; }
    public List<BusinessProjectTask> getInactiveTasks() { return inactiveTasks; }
    public void setInactiveTasks(List<BusinessProjectTask> inactiveTasks) { this.inactiveTasks = inactiveTasks; }
    public List<BusinessProjectRoutine> getRoutines() { return routines; }
    public void setRoutines(List<BusinessProjectRoutine> routines) { this.routines = routines; }
    public List<BusinessProjectRoutine> getRetiredRoutines() { return retiredRoutines; }
    public void setRetiredRoutines(List<BusinessProjectRoutine> retiredRoutines) { this.retiredRoutines = retiredRoutines; }
    public List<BusinessProjectRisk> getRisks() { return risks; }
    public void setRisks(List<BusinessProjectRisk> risks) { this.risks = risks; }
    public List<Map<String, Object>> getOwnerHistory() { return ownerHistory; }
    public void setOwnerHistory(List<Map<String, Object>> ownerHistory) { this.ownerHistory = ownerHistory; }
    public List<BusinessProjectAcceptance> getAcceptances() { return acceptances; }
    public void setAcceptances(List<BusinessProjectAcceptance> acceptances) { this.acceptances = acceptances; }
    public List<BusinessProjectStageAcceptance> getStageAcceptances() { return stageAcceptances; }
    public void setStageAcceptances(List<BusinessProjectStageAcceptance> stageAcceptances) { this.stageAcceptances = stageAcceptances; }
    public Map<String, Object> getGovernanceProfile() { return governanceProfile; }
    public void setGovernanceProfile(Map<String, Object> governanceProfile) { this.governanceProfile = governanceProfile; }
    public List<Map<String, Object>> getEvents() { return events; }
    public void setEvents(List<Map<String, Object>> events) { this.events = events; }
}
