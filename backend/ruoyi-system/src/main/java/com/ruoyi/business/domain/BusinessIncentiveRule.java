package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessIncentiveRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long ruleId;
    private Long projectId;
    private Integer ruleVersion;
    private String ruleName;
    private String policyVersion;
    private BigDecimal amount;
    private String currency;
    private BigDecimal minScore;
    private String status;
    private Long createdUserId;
    private String createdUserName;
    private String reason;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long value) { ruleId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Integer getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(Integer value) { ruleVersion = value; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String value) { ruleName = value; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String value) { policyVersion = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public BigDecimal getMinScore() { return minScore; }
    public void setMinScore(BigDecimal value) { minScore = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getCreatedUserId() { return createdUserId; }
    public void setCreatedUserId(Long value) { createdUserId = value; }
    public String getCreatedUserName() { return createdUserName; }
    public void setCreatedUserName(String value) { createdUserName = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
