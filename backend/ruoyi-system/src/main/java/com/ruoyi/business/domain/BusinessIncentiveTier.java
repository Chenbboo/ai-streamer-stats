package com.ruoyi.business.domain;

import java.math.BigDecimal;

/** A half-open score interval; a null upper bound denotes the final interval. */
public class BusinessIncentiveTier
{
    private Long ruleId;
    private Integer sortOrder;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private BigDecimal amount;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long value) { ruleId = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public BigDecimal getMinScore() { return minScore; }
    public void setMinScore(BigDecimal value) { minScore = value; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal value) { maxScore = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
}
