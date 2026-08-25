package com.ruoyi.jewelry.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JewelryProductExportRowTest
{
    @Test
    void mapsProductArchiveFieldsAndChineseLabels()
    {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("sku", "SKU-001");
        source.put("productName", "平安扣");
        source.put("productType", "ACCESSORY");
        source.put("onHandQty", 12L);
        source.put("avgCost", new BigDecimal("3.0792"));
        source.put("warningQty", 5);
        source.put("status", "0");

        JewelryProductExportRow row = JewelryProductExportRow.from(source);

        assertEquals("SKU-001", row.getSku());
        assertEquals("平安扣", row.getProductName());
        assertEquals("配件商品", row.getProductType());
        assertEquals(12, row.getOnHandQty());
        assertEquals(new BigDecimal("3.0792"), row.getAvgCost());
        assertEquals("启用", row.getStatus());
    }

    @Test
    void keepsAverageCostBlankWhenPermissionLayerRemovedIt()
    {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("productType", "FINISHED");
        source.put("status", "1");

        JewelryProductExportRow row = JewelryProductExportRow.from(source);

        assertEquals("成品商品", row.getProductType());
        assertEquals("停用", row.getStatus());
        assertNull(row.getAvgCost());
    }
}
