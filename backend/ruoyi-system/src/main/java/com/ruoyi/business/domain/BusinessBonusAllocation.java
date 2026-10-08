package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class BusinessBonusAllocation
{
    private Long allocationId;
    private Long awardId;
    private Long projectId;
    private String status;
    private String mode;
    private String reason;
    private String requestKey;
    private Long createdUserId;
    private String createdUserName;
    private Long approvedUserId;
    private String approvedUserName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approvedTime;
    private Integer version;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private List<BusinessBonusAllocationLine> lines;

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getAwardId() { return awardId; }
    public void setAwardId(Long value) { awardId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public Long getCreatedUserId() { return createdUserId; }
    public void setCreatedUserId(Long value) { createdUserId = value; }
    public String getCreatedUserName() { return createdUserName; }
    public void setCreatedUserName(String value) { createdUserName = value; }
    public Long getApprovedUserId() { return approvedUserId; }
    public void setApprovedUserId(Long value) { approvedUserId = value; }
    public String getApprovedUserName() { return approvedUserName; }
    public void setApprovedUserName(String value) { approvedUserName = value; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date value) { approvedTime = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { version = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal value) { paidAmount = value; }
    public List<BusinessBonusAllocationLine> getLines() { return lines; }
    public void setLines(List<BusinessBonusAllocationLine> value) { lines = value; }
}
