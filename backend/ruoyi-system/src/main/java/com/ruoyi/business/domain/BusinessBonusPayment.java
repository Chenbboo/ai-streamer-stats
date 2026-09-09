package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class BusinessBonusPayment
{
    private Long paymentId;
    private Long lineId;
    private Long projectId;
    private BigDecimal amount;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date paidDate;
    private String method;
    private String referenceNo;
    private String voucher;
    private String reason;
    private String requestKey;
    private String status;
    private Long recordedUserId;
    private String recordedUserName;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long value) { paymentId = value; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long value) { lineId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public Date getPaidDate() { return paidDate; }
    public void setPaidDate(Date value) { paidDate = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String value) { referenceNo = value; }
    public String getVoucher() { return voucher; }
    public void setVoucher(String value) { voucher = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getRecordedUserId() { return recordedUserId; }
    public void setRecordedUserId(Long value) { recordedUserId = value; }
    public String getRecordedUserName() { return recordedUserName; }
    public void setRecordedUserName(String value) { recordedUserName = value; }
}
