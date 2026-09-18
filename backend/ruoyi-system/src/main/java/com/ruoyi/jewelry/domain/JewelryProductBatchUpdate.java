package com.ruoyi.jewelry.domain;

import java.util.List;
import java.util.Map;

/** Explicitly selected products and fields; omitted fields must remain unchanged. */
public class JewelryProductBatchUpdate
{
    private List<Long> productIds;
    private Map<String, Object> changes;

    public List<Long> getProductIds() { return productIds; }
    public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
    public Map<String, Object> getChanges() { return changes; }
    public void setChanges(Map<String, Object> changes) { this.changes = changes; }
}
