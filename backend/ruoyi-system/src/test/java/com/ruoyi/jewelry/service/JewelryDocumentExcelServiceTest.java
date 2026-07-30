package com.ruoyi.jewelry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@ExtendWith(MockitoExtension.class)
class JewelryDocumentExcelServiceTest
{
    @Mock
    private JewelryErpMapper mapper;

    @InjectMocks
    private JewelryDocumentExcelService service;

    @Test
    void salesPreviewRejectsQuantityAboveAvailableStock() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(product("SKU-1", 5, 1)));

        Map<String, Object> result = service.preview("SALES_OUT", workbook(
            new String[] { "SKU", "数量", "成交单价", "包装费/件", "物流费/件", "鉴定费/件" },
            new Object[] { "SKU-1", 5, 1000, 0, 0, 0 }), false);

        assertEquals(1, result.get("errorCount"));
        Map<String, Object> row = rows(result).get(0);
        assertEquals(4, row.get("availableQty"));
        assertTrue(String.valueOf(row.get("errorMessage")).contains("超过可用库存"));
    }

    @Test
    void purchasePreviewAllowsNewProductForAuthorizedMaker() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.preview("PURCHASE_IN", workbook(
            new String[] { "SKU", "商品名称（新商品必填）", "分类", "规格", "单位", "数量", "采购单价" },
            new Object[] { "NEW-001", "测试戒指", "戒指", "18K", "件", 2, 6800 }), true);

        assertEquals(0, result.get("errorCount"));
        assertEquals(1, result.get("newProductCount"));
        assertEquals("NEW", rows(result).get(0).get("status"));
    }

    @Test
    void previewRejectsDuplicateSkuRows() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(product("SKU-1", 10, 0)));

        Map<String, Object> result = service.preview("SALES_OUT", workbook(
            new String[] { "SKU", "数量", "成交单价", "包装费/件", "物流费/件", "鉴定费/件" },
            new Object[] { "SKU-1", 1, 1000, 0, 0, 0 },
            new Object[] { "sku-1", 1, 1000, 0, 0, 0 }), false);

        assertEquals(2, result.get("errorCount"));
        assertTrue(rows(result).stream()
            .allMatch(row -> String.valueOf(row.get("errorMessage")).contains("SKU重复")));
    }

    private ByteArrayInputStream workbook(String[] headers, Object[]... values) throws Exception
    {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("导入数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++)
            {
                Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < values[rowIndex].length; column++)
                {
                    Object value = values[rowIndex][column];
                    if (value instanceof Number) row.createCell(column).setCellValue(((Number) value).doubleValue());
                    else row.createCell(column).setCellValue(String.valueOf(value));
                }
            }
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private Map<String, Object> product(String sku, int onHandQty, int reservedOutQty)
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 1L);
        product.put("sku", sku);
        product.put("productName", "测试商品");
        product.put("onHandQty", onHandQty);
        product.put("reservedOutQty", reservedOutQty);
        product.put("avgCost", new BigDecimal("100.00"));
        return product;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> result)
    {
        return (List<Map<String, Object>>) result.get("rows");
    }
}
