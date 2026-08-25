package com.ruoyi.jewelry.domain;

import java.math.BigDecimal;
import java.util.Map;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;

/**
 * 商品档案导出行。
 */
public class JewelryProductExportRow
{
    @Excel(name = "SKU", sort = 1, width = 22)
    private String sku;

    @Excel(name = "商品名称", sort = 2, width = 32)
    private String productName;

    @Excel(name = "商品类型", sort = 3, width = 14)
    private String productType;

    @Excel(name = "分类", sort = 4, width = 16)
    private String category;

    @Excel(name = "规格类型", sort = 5, width = 12)
    private String specification;

    @Excel(name = "单位", sort = 6, width = 10)
    private String unit;

    @Excel(name = "库存", sort = 7, cellType = ColumnType.NUMERIC)
    private Integer onHandQty;

    @Excel(name = "平均成本", sort = 8, cellType = ColumnType.NUMERIC, scale = 4)
    private BigDecimal avgCost;

    @Excel(name = "预警值", sort = 9, cellType = ColumnType.NUMERIC)
    private Integer warningQty;

    @Excel(name = "状态", sort = 10, width = 10)
    private String status;

    public static JewelryProductExportRow from(Map<String, Object> source)
    {
        JewelryProductExportRow row = new JewelryProductExportRow();
        row.sku = text(source.get("sku"));
        row.productName = text(source.get("productName"));
        row.productType = productTypeLabel(text(source.get("productType")));
        row.category = text(source.get("category"));
        row.specification = text(source.get("specification"));
        row.unit = text(source.get("unit"));
        row.onHandQty = integer(source.get("onHandQty"));
        row.avgCost = decimal(source.get("avgCost"));
        row.warningQty = integer(source.get("warningQty"));
        row.status = "0".equals(text(source.get("status"))) ? "启用" : "停用";
        return row;
    }

    private static String productTypeLabel(String value)
    {
        if ("FINISHED".equals(value)) return "成品商品";
        if ("PART".equals(value)) return "散件商品";
        if ("ACCESSORY".equals(value)) return "配件商品";
        if ("WELFARE".equals(value)) return "福利商品";
        return value;
    }

    private static String text(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Object value)
    {
        if (value == null || "".equals(String.valueOf(value))) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        return new BigDecimal(String.valueOf(value)).intValue();
    }

    private static BigDecimal decimal(Object value)
    {
        if (value == null || "".equals(String.valueOf(value))) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(String.valueOf(value));
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getOnHandQty() { return onHandQty; }
    public void setOnHandQty(Integer onHandQty) { this.onHandQty = onHandQty; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
    public Integer getWarningQty() { return warningQty; }
    public void setWarningQty(Integer warningQty) { this.warningQty = warningQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
