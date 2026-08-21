package com.ruoyi.business.domain;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectKpiPlan extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long planId;
    private Long projectId;
    private Integer planVersion;
    private String cycleType;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date cycleStart;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date cycleEnd;
    private String bonusMode;
    private String currency;
    private String status;
    private Long publishedUserId;
    private String publishedUserName;
    private Date publishedTime;
    private Date closedTime;
    private List<BusinessProjectKpiPlanItem> items;
    private List<BusinessProjectBonusTier> tiers;
    private BusinessProjectKpiSettlement settlement;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getCycleType() { return cycleType; }
    public void setCycleType(String cycleType) { this.cycleType = cycleType; }
    public Date getCycleStart() { return cycleStart; }
    public void setCycleStart(Date cycleStart) { this.cycleStart = cycleStart; }
    public Date getCycleEnd() { return cycleEnd; }
    public void setCycleEnd(Date cycleEnd) { this.cycleEnd = cycleEnd; }
    public String getBonusMode() { return bonusMode; }
    public void setBonusMode(String bonusMode) { this.bonusMode = bonusMode; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPublishedUserId() { return publishedUserId; }
    public void setPublishedUserId(Long publishedUserId) { this.publishedUserId = publishedUserId; }
    public String getPublishedUserName() { return publishedUserName; }
    public void setPublishedUserName(String publishedUserName) { this.publishedUserName = publishedUserName; }
    public Date getPublishedTime() { return publishedTime; }
    public void setPublishedTime(Date publishedTime) { this.publishedTime = publishedTime; }
    public Date getClosedTime() { return closedTime; }
    public void setClosedTime(Date closedTime) { this.closedTime = closedTime; }
    public List<BusinessProjectKpiPlanItem> getItems() { return items; }
    public void setItems(List<BusinessProjectKpiPlanItem> items) { this.items = items; }
    public List<BusinessProjectBonusTier> getTiers() { return tiers; }
    public void setTiers(List<BusinessProjectBonusTier> tiers) { this.tiers = tiers; }
    public BusinessProjectKpiSettlement getSettlement() { return settlement; }
    public void setSettlement(BusinessProjectKpiSettlement settlement) { this.settlement = settlement; }
}
