package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 项目负责人提交的不可覆盖进度版本；关联数据由服务器归档。 */
public class BusinessProjectProgressReport extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private String issuesRisks;
    public String getIssuesRisks() { return issuesRisks; }
    public void setIssuesRisks(String value) { issuesRisks = value; }
    private String nextPlan;
    public String getNextPlan() { return nextPlan; }
    public void setNextPlan(String value) { nextPlan = value; }
    private Boolean syncTasks;
    public Boolean getSyncTasks() { return syncTasks; }
    public void setSyncTasks(Boolean value) { syncTasks = value; }
    private Boolean syncRoutines;
    public Boolean getSyncRoutines() { return syncRoutines; }
    public void setSyncRoutines(Boolean value) { syncRoutines = value; }
    private String snapshotJson;
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { snapshotJson = value; }
    private Long parentProjectId;
    public Long getParentProjectId() { return parentProjectId; }
    public void setParentProjectId(Long value) { parentProjectId = value; }
    private String projectNameSnapshot;
    public String getProjectNameSnapshot() { return projectNameSnapshot; }
    public void setProjectNameSnapshot(String value) { projectNameSnapshot = value; }
    private Long reportId;
    private Long projectId;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date bizDate;
    private Integer progress;
    private String completionSummary;
    private String evidenceUrls;
    private Long submittedUserId;
    private String submittedUserName;
    private Integer version;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Date getBizDate() { return bizDate; }
    public void setBizDate(Date bizDate) { this.bizDate = bizDate; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getCompletionSummary() { return completionSummary; }
    public void setCompletionSummary(String completionSummary) { this.completionSummary = completionSummary; }
    public String getEvidenceUrls() { return evidenceUrls; }
    public void setEvidenceUrls(String evidenceUrls) { this.evidenceUrls = evidenceUrls; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long submittedUserId) { this.submittedUserId = submittedUserId; }
    public String getSubmittedUserName() { return submittedUserName; }
    public void setSubmittedUserName(String submittedUserName) { this.submittedUserName = submittedUserName; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
