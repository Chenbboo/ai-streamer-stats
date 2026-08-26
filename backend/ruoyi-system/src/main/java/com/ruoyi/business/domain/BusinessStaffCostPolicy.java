package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessStaffCostPolicy extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long policyId;
    private Long userId;
    private String costMode;
    private BigDecimal unitCost;
    private String currency;
    private String countryRegion;
    private BigDecimal standardWorkDays;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveFrom;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveTo;
    private Integer policyVersion;
    private String status;
    private Integer referenceCount;
    private Long voidedUserId;
    private String voidedUserName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date voidedTime;
    private String voidReason;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCostMode() { return costMode; }
    public void setCostMode(String costMode) { this.costMode = costMode; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountryRegion() { return countryRegion; }
    public void setCountryRegion(String countryRegion) { this.countryRegion = countryRegion; }
    public BigDecimal getStandardWorkDays() { return standardWorkDays; }
    public void setStandardWorkDays(BigDecimal standardWorkDays) { this.standardWorkDays = standardWorkDays; }
    public Date getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Date effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Date getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Date effectiveTo) { this.effectiveTo = effectiveTo; }
    public Integer getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Integer policyVersion) { this.policyVersion = policyVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getReferenceCount() { return referenceCount; }
    public void setReferenceCount(Integer referenceCount) { this.referenceCount = referenceCount; }
    public Long getVoidedUserId() { return voidedUserId; }
    public void setVoidedUserId(Long voidedUserId) { this.voidedUserId = voidedUserId; }
    public String getVoidedUserName() { return voidedUserName; }
    public void setVoidedUserName(String voidedUserName) { this.voidedUserName = voidedUserName; }
    public Date getVoidedTime() { return voidedTime; }
    public void setVoidedTime(Date voidedTime) { this.voidedTime = voidedTime; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
}
