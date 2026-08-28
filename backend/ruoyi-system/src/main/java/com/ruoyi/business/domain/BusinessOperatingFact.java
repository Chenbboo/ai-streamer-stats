package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessOperatingFact extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long factId;
    private Long projectId;
    private Long companyDeptId;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date bizDate;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String factKind;
    private BigDecimal amount;
    private BigDecimal quantity;
    private String currency;
    private String unit;
    private String description;
    private String counterparty;
    private String attachmentUrls;
    private String sourceDomain;
    private String sourceType;
    private String sourceId;
    private String sourceLineKey;
    private String status;
    private Long reversalFactId;
    private String idempotencyKey;
    private Integer version;
    private Long confirmedUserId;
    private String confirmedUserName;
    private Date confirmedTime;
    private Long returnedUserId;
    private String returnedUserName;
    private Date returnedTime;
    private String returnReason;
    private Long createUserId;

    public Long getFactId(){return factId;} public void setFactId(Long v){factId=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public Long getCompanyDeptId(){return companyDeptId;} public void setCompanyDeptId(Long v){companyDeptId=v;}
    public Date getBizDate(){return bizDate;} public void setBizDate(Date v){bizDate=v;}
    public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
    public String getCategoryCode(){return categoryCode;} public void setCategoryCode(String v){categoryCode=v;}
    public String getCategoryName(){return categoryName;} public void setCategoryName(String v){categoryName=v;}
    public String getFactKind(){return factKind;} public void setFactKind(String v){factKind=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getCounterparty(){return counterparty;} public void setCounterparty(String v){counterparty=v;}
    public String getAttachmentUrls(){return attachmentUrls;} public void setAttachmentUrls(String v){attachmentUrls=v;}
    public String getSourceDomain(){return sourceDomain;} public void setSourceDomain(String v){sourceDomain=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getSourceId(){return sourceId;} public void setSourceId(String v){sourceId=v;}
    public String getSourceLineKey(){return sourceLineKey;} public void setSourceLineKey(String v){sourceLineKey=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Long getReversalFactId(){return reversalFactId;} public void setReversalFactId(Long v){reversalFactId=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Long getConfirmedUserId(){return confirmedUserId;} public void setConfirmedUserId(Long v){confirmedUserId=v;}
    public String getConfirmedUserName(){return confirmedUserName;} public void setConfirmedUserName(String v){confirmedUserName=v;}
    public Date getConfirmedTime(){return confirmedTime;} public void setConfirmedTime(Date v){confirmedTime=v;}
    public Long getReturnedUserId(){return returnedUserId;} public void setReturnedUserId(Long v){returnedUserId=v;}
    public String getReturnedUserName(){return returnedUserName;} public void setReturnedUserName(String v){returnedUserName=v;}
    public Date getReturnedTime(){return returnedTime;} public void setReturnedTime(Date v){returnedTime=v;}
    public String getReturnReason(){return returnReason;} public void setReturnReason(String v){returnReason=v;}
    public Long getCreateUserId(){return createUserId;} public void setCreateUserId(Long v){createUserId=v;}
}
