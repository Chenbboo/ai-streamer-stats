package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectRisk extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long riskId;
    private Long projectId;
    private String riskType;
    private String riskTitle;
    private String severity;
    private String probability;
    private Long ownerUserId;
    private String ownerName;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date dueDate;
    private String status;
    private String responsePlan;

    public Long getRiskId() { return riskId; }
    public void setRiskId(Long riskId) { this.riskId = riskId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRiskType() { return riskType; }
    public void setRiskType(String riskType) { this.riskType = riskType; }
    public String getRiskTitle() { return riskTitle; }
    public void setRiskTitle(String riskTitle) { this.riskTitle = riskTitle; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getProbability() { return probability; }
    public void setProbability(String probability) { this.probability = probability; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponsePlan() { return responsePlan; }
    public void setResponsePlan(String responsePlan) { this.responsePlan = responsePlan; }
}
