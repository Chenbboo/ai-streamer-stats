package com.ruoyi.business.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class BusinessProjectKpiResult implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long resultId;
    private Long settlementId;
    private Long planItemId;
    private BigDecimal actualValue;
    private BigDecimal completionRate;
    private BigDecimal weightedScore;
    private String resultNote;
    private String attachmentUrls;
    private Long inputUserId;
    private String inputUserName;
    private java.util.Date inputTime;
    private String kpiCode;
    private String kpiName;
    private String unit;
    private BigDecimal targetValue;
    private BigDecimal weight;
    private String direction;
    private String sourceType;
    private Boolean automatic;

    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Long getSettlementId() { return settlementId; }
    public void setSettlementId(Long settlementId) { this.settlementId = settlementId; }
    public Long getPlanItemId() { return planItemId; }
    public void setPlanItemId(Long planItemId) { this.planItemId = planItemId; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
    public BigDecimal getWeightedScore() { return weightedScore; }
    public void setWeightedScore(BigDecimal weightedScore) { this.weightedScore = weightedScore; }
    public String getResultNote() { return resultNote; }
    public void setResultNote(String resultNote) { this.resultNote = resultNote; }
    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String attachmentUrls) { this.attachmentUrls = attachmentUrls; }
    public Long getInputUserId() { return inputUserId; }
    public void setInputUserId(Long inputUserId) { this.inputUserId = inputUserId; }
    public String getInputUserName() { return inputUserName; }
    public void setInputUserName(String inputUserName) { this.inputUserName = inputUserName; }
    public java.util.Date getInputTime() { return inputTime; }
    public void setInputTime(java.util.Date inputTime) { this.inputTime = inputTime; }
    public String getKpiCode() { return kpiCode; }
    public void setKpiCode(String kpiCode) { this.kpiCode = kpiCode; }
    public String getKpiName() { return kpiName; }
    public void setKpiName(String kpiName) { this.kpiName = kpiName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Boolean getAutomatic() { return automatic; }
    public void setAutomatic(Boolean automatic) { this.automatic = automatic; }
}
