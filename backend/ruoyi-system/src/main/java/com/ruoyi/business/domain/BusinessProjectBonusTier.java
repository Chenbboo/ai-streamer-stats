package com.ruoyi.business.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class BusinessProjectBonusTier implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long tierId;
    private Long planId;
    private String tierName;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private BigDecimal bonusAmount;
    private Integer sortOrder;

    public Long getTierId() { return tierId; }
    public void setTierId(Long tierId) { this.tierId = tierId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }
    public BigDecimal getMinScore() { return minScore; }
    public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public BigDecimal getBonusAmount() { return bonusAmount; }
    public void setBonusAmount(BigDecimal bonusAmount) { this.bonusAmount = bonusAmount; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
