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

    private Long projectId;
    private String projectNo;
    private Long parentId;
    private String parentName;
    private Long companyDeptId;
    private String companyName;
    private String projectName;
    private String projectType;
    private String accountingMode;
    private String managementMode;
    private String objective;
    private String status;
    private String baselineStatus;
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
    private Integer baselineVersion;
    private Integer version;
    private String delFlag;
    private Integer memberCount;
    private Integer taskCount;
    private Integer completedTaskCount;
    private Integer openRiskCount;
    /** 关联的执行数据源；当前一期仅支持 LIVE。 */
    private String executionSource;
    private List<BusinessProjectMember> members;
    private List<BusinessProjectMilestone> milestones;
    private List<BusinessProjectTask> tasks;
    private List<BusinessProjectRoutine> routines;
    private List<BusinessProjectRisk> risks;
    private List<Map<String, Object>> ownerHistory;
    private List<BusinessProjectAcceptance> acceptances;
    private List<Map<String, Object>> events;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectNo() { return projectNo; }
    public void setProjectNo(String projectNo) { this.projectNo = projectNo; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public Long getCompanyDeptId() { return companyDeptId; }
    public void setCompanyDeptId(Long companyDeptId) { this.companyDeptId = companyDeptId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getAccountingMode() { return accountingMode; }
    public void setAccountingMode(String accountingMode) { this.accountingMode = accountingMode; }
    public String getManagementMode() { return managementMode; }
    public void setManagementMode(String managementMode) { this.managementMode = managementMode; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBaselineStatus() { return baselineStatus; }
    public void setBaselineStatus(String baselineStatus) { this.baselineStatus = baselineStatus; }
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
    public List<BusinessProjectRoutine> getRoutines() { return routines; }
    public void setRoutines(List<BusinessProjectRoutine> routines) { this.routines = routines; }
    public List<BusinessProjectRisk> getRisks() { return risks; }
    public void setRisks(List<BusinessProjectRisk> risks) { this.risks = risks; }
    public List<Map<String, Object>> getOwnerHistory() { return ownerHistory; }
    public void setOwnerHistory(List<Map<String, Object>> ownerHistory) { this.ownerHistory = ownerHistory; }
    public List<BusinessProjectAcceptance> getAcceptances() { return acceptances; }
    public void setAcceptances(List<BusinessProjectAcceptance> acceptances) { this.acceptances = acceptances; }
    public List<Map<String, Object>> getEvents() { return events; }
    public void setEvents(List<Map<String, Object>> events) { this.events = events; }
}
