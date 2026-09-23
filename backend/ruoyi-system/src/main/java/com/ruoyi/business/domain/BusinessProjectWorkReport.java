package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 持续工作的文字或附件汇报，不参与每日完成量计算。 */
public class BusinessProjectWorkReport
{
    private Long reportId;
    private Long routineId;
    private Long projectId;
    private String routineName;
    private String frequency;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date periodStart;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date periodEnd;
    private String content;
    private String attachmentUrls;
    private Long submittedUserId;
    private String submittedUserName;
    private String status;
    private Long reviewedUserId;
    private String reviewedUserName;
    private String reviewComment;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date reviewedTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date createTime;

    public Long getReportId() { return reportId; }
    public void setReportId(Long value) { reportId = value; }
    public Long getRoutineId() { return routineId; }
    public void setRoutineId(Long value) { routineId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getRoutineName() { return routineName; }
    public void setRoutineName(String value) { routineName = value; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String value) { frequency = value; }
    public Date getPeriodStart() { return periodStart; }
    public void setPeriodStart(Date value) { periodStart = value; }
    public Date getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Date value) { periodEnd = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String value) { attachmentUrls = value; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long value) { submittedUserId = value; }
    public String getSubmittedUserName() { return submittedUserName; }
    public void setSubmittedUserName(String value) { submittedUserName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getReviewedUserId() { return reviewedUserId; }
    public void setReviewedUserId(Long value) { reviewedUserId = value; }
    public String getReviewedUserName() { return reviewedUserName; }
    public void setReviewedUserName(String value) { reviewedUserName = value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment = value; }
    public Date getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(Date value) { reviewedTime = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
