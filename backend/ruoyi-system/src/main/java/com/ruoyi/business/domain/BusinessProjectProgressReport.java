package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 项目负责人按日填报的项目整体完成情况。 */
public class BusinessProjectProgressReport extends BaseEntity
{
    private static final long serialVersionUID = 1L;
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
