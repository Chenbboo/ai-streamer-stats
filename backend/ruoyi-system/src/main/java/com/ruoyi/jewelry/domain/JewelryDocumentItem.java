package com.ruoyi.jewelry.domain;

import java.math.BigDecimal;

public class JewelryDocumentItem
{
    private Long itemId;
    private Long documentId;
    private Long productId;
    private Long sourceItemId;
    private String skuSnapshot;
    private String productNameSnapshot;
    private Integer qty;
    private Integer goodQty;
    private Integer defectQty;
    private Integer systemQty;
    private Integer countedQty;
    private Integer adjustmentQty;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private BigDecimal packFee;
    private BigDecimal shipFee;
    private BigDecimal certFee;
    private BigDecimal amount;
    private BigDecimal costAmount;
    private BigDecimal profitAmount;
    private BigDecimal profitRate;
    private String lineReason;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public void setSkuSnapshot(String skuSnapshot) { this.skuSnapshot = skuSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Integer getGoodQty() { return goodQty; }
    public void setGoodQty(Integer goodQty) { this.goodQty = goodQty; }
    public Integer getDefectQty() { return defectQty; }
    public void setDefectQty(Integer defectQty) { this.defectQty = defectQty; }
    public Integer getSystemQty() { return systemQty; }
    public void setSystemQty(Integer systemQty) { this.systemQty = systemQty; }
    public Integer getCountedQty() { return countedQty; }
    public void setCountedQty(Integer countedQty) { this.countedQty = countedQty; }
    public Integer getAdjustmentQty() { return adjustmentQty; }
    public void setAdjustmentQty(Integer adjustmentQty) { this.adjustmentQty = adjustmentQty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getPackFee() { return packFee; }
    public void setPackFee(BigDecimal packFee) { this.packFee = packFee; }
    public BigDecimal getShipFee() { return shipFee; }
    public void setShipFee(BigDecimal shipFee) { this.shipFee = shipFee; }
    public BigDecimal getCertFee() { return certFee; }
    public void setCertFee(BigDecimal certFee) { this.certFee = certFee; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    public BigDecimal getProfitAmount() { return profitAmount; }
    public void setProfitAmount(BigDecimal profitAmount) { this.profitAmount = profitAmount; }
    public BigDecimal getProfitRate() { return profitRate; }
    public void setProfitRate(BigDecimal profitRate) { this.profitRate = profitRate; }
    public String getLineReason() { return lineReason; }
    public void setLineReason(String lineReason) { this.lineReason = lineReason; }
}
