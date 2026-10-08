package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class BusinessBonusAllocationLine
{
    private Long lineId;
    private Long allocationId;
    private Long userId;
    private String userName;
    private BigDecimal amount;
    private BigDecimal percentage;
    private String reason;
    private BigDecimal paidAmount;
    private String paymentStatus;

    public Long getLineId() { return lineId; }
    public void setLineId(Long value) { lineId = value; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getUserName() { return userName; }
    public void setUserName(String value) { userName = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal value) { percentage = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal value) { paidAmount = value; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String value) { paymentStatus = value; }
}
