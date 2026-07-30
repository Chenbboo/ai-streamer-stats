package com.ruoyi.jewelry.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class JewelryDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long documentId;
    private String docNo;
    private String docType;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date bizDate;
    private String status;
    private Long supplierId;
    private String supplierNameSnapshot;
    private String salesChannel;
    private String externalNo;
    private String influencerName;
    private BigDecimal platformRate;
    private BigDecimal commissionRate;
    private BigDecimal taxRate;
    private String returnReason;
    private Long sourceDocumentId;
    private String unlinkedReason;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;
    private String riskStatus;
    private Long creatorUserId;
    private String creatorName;
    private Long firstReviewerUserId;
    private String firstReviewerName;
    private Long secondReviewerUserId;
    private String secondReviewerName;
    private String rejectReason;
    private Integer version;
    private List<JewelryDocumentItem> items;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public Date getBizDate() { return bizDate; }
    public void setBizDate(Date bizDate) { this.bizDate = bizDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplierNameSnapshot() { return supplierNameSnapshot; }
    public void setSupplierNameSnapshot(String supplierNameSnapshot) { this.supplierNameSnapshot = supplierNameSnapshot; }
    public String getSalesChannel() { return salesChannel; }
    public void setSalesChannel(String salesChannel) { this.salesChannel = salesChannel; }
    public String getExternalNo() { return externalNo; }
    public void setExternalNo(String externalNo) { this.externalNo = externalNo; }
    public String getInfluencerName() { return influencerName; }
    public void setInfluencerName(String influencerName) { this.influencerName = influencerName; }
    public BigDecimal getPlatformRate() { return platformRate; }
    public void setPlatformRate(BigDecimal platformRate) { this.platformRate = platformRate; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(Long sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public String getUnlinkedReason() { return unlinkedReason; }
    public void setUnlinkedReason(String unlinkedReason) { this.unlinkedReason = unlinkedReason; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }
    public String getRiskStatus() { return riskStatus; }
    public void setRiskStatus(String riskStatus) { this.riskStatus = riskStatus; }
    public Long getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(Long creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Long getFirstReviewerUserId() { return firstReviewerUserId; }
    public void setFirstReviewerUserId(Long firstReviewerUserId) { this.firstReviewerUserId = firstReviewerUserId; }
    public String getFirstReviewerName() { return firstReviewerName; }
    public void setFirstReviewerName(String firstReviewerName) { this.firstReviewerName = firstReviewerName; }
    public Long getSecondReviewerUserId() { return secondReviewerUserId; }
    public void setSecondReviewerUserId(Long secondReviewerUserId) { this.secondReviewerUserId = secondReviewerUserId; }
    public String getSecondReviewerName() { return secondReviewerName; }
    public void setSecondReviewerName(String secondReviewerName) { this.secondReviewerName = secondReviewerName; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<JewelryDocumentItem> getItems() { return items; }
    public void setItems(List<JewelryDocumentItem> items) { this.items = items; }
}
