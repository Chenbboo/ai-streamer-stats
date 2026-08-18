package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectStaffAllocation extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long allocationId;
    private Long projectId;
    private Long userId;
    private String userName;
    private String allocationMode;
    private BigDecimal allocationValue;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveFrom;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveTo;
    private Long costPolicyId;
    private String exceptionAllowed;
    private String exceptionReason;
    private String status;
    private Integer version;

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAllocationMode() { return allocationMode; }
    public void setAllocationMode(String allocationMode) { this.allocationMode = allocationMode; }
    public BigDecimal getAllocationValue() { return allocationValue; }
    public void setAllocationValue(BigDecimal allocationValue) { this.allocationValue = allocationValue; }
    public Date getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Date effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Date getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Date effectiveTo) { this.effectiveTo = effectiveTo; }
    public Long getCostPolicyId() { return costPolicyId; }
    public void setCostPolicyId(Long costPolicyId) { this.costPolicyId = costPolicyId; }
    public String getExceptionAllowed() { return exceptionAllowed; }
    public void setExceptionAllowed(String exceptionAllowed) { this.exceptionAllowed = exceptionAllowed; }
    public String getExceptionReason() { return exceptionReason; }
    public void setExceptionReason(String exceptionReason) { this.exceptionReason = exceptionReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
