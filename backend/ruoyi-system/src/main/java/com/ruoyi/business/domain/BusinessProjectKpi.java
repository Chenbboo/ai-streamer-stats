package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectKpi extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long kpiId;
    private Long projectId;
    private String kpiCode;
    private String kpiName;
    private String metricType;
    private String unit;
    private Integer precisionScale;
    private String periodType;
    private BigDecimal targetValue;
    private BigDecimal minimumValue;
    private BigDecimal warningValue;
    private BigDecimal challengeValue;
    private BigDecimal actualValue;
    private BigDecimal weight;
    private String direction;
    private String aggregateType;
    private String sourceType;
    private Long ownerUserId;
    private String ownerName;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveFrom;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date effectiveTo;
    private Integer targetVersion;
    private String status;

    public Long getKpiId() { return kpiId; }
    public void setKpiId(Long kpiId) { this.kpiId = kpiId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getKpiCode() { return kpiCode; }
    public void setKpiCode(String kpiCode) { this.kpiCode = kpiCode; }
    public String getKpiName() { return kpiName; }
    public void setKpiName(String kpiName) { this.kpiName = kpiName; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getPrecisionScale() { return precisionScale; }
    public void setPrecisionScale(Integer precisionScale) { this.precisionScale = precisionScale; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getMinimumValue() { return minimumValue; }
    public void setMinimumValue(BigDecimal minimumValue) { this.minimumValue = minimumValue; }
    public BigDecimal getWarningValue() { return warningValue; }
    public void setWarningValue(BigDecimal warningValue) { this.warningValue = warningValue; }
    public BigDecimal getChallengeValue() { return challengeValue; }
    public void setChallengeValue(BigDecimal challengeValue) { this.challengeValue = challengeValue; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Date getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Date effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Date getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Date effectiveTo) { this.effectiveTo = effectiveTo; }
    public Integer getTargetVersion() { return targetVersion; }
    public void setTargetVersion(Integer targetVersion) { this.targetVersion = targetVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
