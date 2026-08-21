package com.ruoyi.business.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class BusinessProjectKpiPlanItem implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long itemId;
    private Long planId;
    private Long kpiId;
    private String kpiCode;
    private String kpiName;
    private String metricType;
    private String unit;
    private BigDecimal targetValue;
    private BigDecimal minimumValue;
    private BigDecimal warningValue;
    private BigDecimal challengeValue;
    private BigDecimal weight;
    private String direction;
    private String aggregateType;
    private String sourceType;
    private Integer sortOrder;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getKpiId() { return kpiId; }
    public void setKpiId(Long kpiId) { this.kpiId = kpiId; }
    public String getKpiCode() { return kpiCode; }
    public void setKpiCode(String kpiCode) { this.kpiCode = kpiCode; }
    public String getKpiName() { return kpiName; }
    public void setKpiName(String kpiName) { this.kpiName = kpiName; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getMinimumValue() { return minimumValue; }
    public void setMinimumValue(BigDecimal minimumValue) { this.minimumValue = minimumValue; }
    public BigDecimal getWarningValue() { return warningValue; }
    public void setWarningValue(BigDecimal warningValue) { this.warningValue = warningValue; }
    public BigDecimal getChallengeValue() { return challengeValue; }
    public void setChallengeValue(BigDecimal challengeValue) { this.challengeValue = challengeValue; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
