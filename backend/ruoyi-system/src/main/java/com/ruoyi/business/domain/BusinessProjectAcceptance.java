package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 项目验收提交与老板评审记录。 */
public class BusinessProjectAcceptance extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long acceptanceId;
    private Long projectId;
    private Integer submissionVersion;
    private String resultSummary;
    private String deliverables;
    private String attachmentUrls;
    private Long submittedUserId;
    private String submittedUserName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date submittedTime;
    private String reviewStatus;
    private Long reviewedUserId;
    private String reviewedUserName;
    private String reviewComment;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date reviewedTime;

    public Long getAcceptanceId() { return acceptanceId; }
    public void setAcceptanceId(Long acceptanceId) { this.acceptanceId = acceptanceId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getSubmissionVersion() { return submissionVersion; }
    public void setSubmissionVersion(Integer submissionVersion) { this.submissionVersion = submissionVersion; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getDeliverables() { return deliverables; }
    public void setDeliverables(String deliverables) { this.deliverables = deliverables; }
    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String attachmentUrls) { this.attachmentUrls = attachmentUrls; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long submittedUserId) { this.submittedUserId = submittedUserId; }
    public String getSubmittedUserName() { return submittedUserName; }
    public void setSubmittedUserName(String submittedUserName) { this.submittedUserName = submittedUserName; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Long getReviewedUserId() { return reviewedUserId; }
    public void setReviewedUserId(Long reviewedUserId) { this.reviewedUserId = reviewedUserId; }
    public String getReviewedUserName() { return reviewedUserName; }
    public void setReviewedUserName(String reviewedUserName) { this.reviewedUserName = reviewedUserName; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Date getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(Date reviewedTime) { this.reviewedTime = reviewedTime; }
}
