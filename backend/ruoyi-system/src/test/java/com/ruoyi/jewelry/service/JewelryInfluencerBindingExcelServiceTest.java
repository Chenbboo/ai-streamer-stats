package com.ruoyi.jewelry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@ExtendWith(MockitoExtension.class)
class JewelryInfluencerBindingExcelServiceTest
{
    @Mock private JewelryErpMapper mapper;
    @InjectMocks private JewelryInfluencerBindingExcelService service;

    @Test
    void sampleBindingRowIsRetainedWithAnErrorForCorrection() throws Exception
    {
        when(mapper.selectProductBySkuAndType("0001", "FINISHED"))
            .thenReturn(product(1L, "成品"));
        when(mapper.selectProductBySkuAndType("0001", "SAMPLE"))
            .thenReturn(product(2L, "样品"));
        when(mapper.selectProductById(1L)).thenReturn(product(1L, "成品"));
        when(mapper.selectProductById(2L)).thenReturn(product(2L, "样品"));
        List<Map<String, Object>> rows = service.preview(workbook(false), null);
        assertEquals(2, rows.size());
        assertEquals(1L, rows.get(0).get("productId"));
        assertEquals(2L, rows.get(1).get("productId"));
        assertTrue(errors(rows.get(0)).isEmpty());
        assertTrue(errors(rows.get(1)).stream().anyMatch(error -> error.contains("只支持成品商品")));
    }

    @Test
    void rejectsCombinedRatesAtOrAboveOneHundredPercent() throws Exception
    {
        when(mapper.selectProductBySkuAndType("0001", "FINISHED"))
            .thenReturn(product(1L, "成品"));
        when(mapper.selectProductById(1L)).thenReturn(product(1L, "成品"));
        assertThrows(ServiceException.class, () -> service.parse(workbook(true)));
    }

    @Test
    void previewRetainsEveryInvalidRowAndRechecksCorrections() throws Exception
    {
        when(mapper.selectProductBySkuAndType("0001", "FINISHED"))
            .thenReturn(product(1L, "项链"));
        when(mapper.selectProductBySkuAndType("0002", "FINISHED"))
            .thenReturn(product(2L, "戒指"));
        when(mapper.selectProductById(2L)).thenReturn(product(2L, "戒指"));
        List<Map<String, Object>> rows = service.preview(workbookWithErrors(), 9L);
        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).get("excelRow"));
        assertTrue(errors(rows.get(0)).stream().anyMatch(error -> error.contains("商品类型")));
        assertTrue(errors(rows.get(1)).stream().anyMatch(error -> error.contains("直播成交价")));
        rows.get(0).put("productType", "FINISHED");
        rows.get(0).put("fixedUnitPrice", "100");
        rows.get(1).put("fixedUnitPrice", "80");
        service.validateRows(9L, rows);
        assertTrue(errors(rows.get(0)).isEmpty());
        assertTrue(errors(rows.get(1)).isEmpty());
    }

    @Test
    void editedSkuCannotKeepAnOldSelectedProduct()
    {
        Map<String, Object> selectedProduct = product(1L, "项链");
        selectedProduct.put("sku", "0001");
        selectedProduct.put("productType", "FINISHED");
        when(mapper.selectProductById(1L)).thenReturn(selectedProduct);
        Map<String, Object> row = new HashMap<>();
        row.put("productId", 1L);
        row.put("sku", "0002");
        row.put("productType", "FINISHED");
        row.put("fixedUnitPrice", "100");
        row.put("bindingStatus", "0");

        service.validateRows(null, List.of(row));
        assertTrue(errors(row).stream().anyMatch(error -> error.contains("不一致")));
    }

    @SuppressWarnings("unchecked")
    private List<String> errors(Map<String, Object> row)
    {
        return (List<String>) row.get("errors");
    }

    private MockMultipartFile workbookWithErrors() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("绑定");
            Row header = sheet.createRow(0);
            String[] names = { "SKU", "商品类型", "直播成交价", "达人佣金率(%)", "平台扣点率(%)",
                "税率(%)", "包装费", "物流费", "鉴定费", "备注" };
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("0001");
            first.createCell(1).setCellValue("错误类型");
            first.createCell(2).setCellValue(100);
            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("0002");
            second.createCell(1).setCellValue("成品商品");
            second.createCell(2).setCellValue("错误价格");
            workbook.write(out);
            return new MockMultipartFile("file", "bindings.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private MockMultipartFile workbook(boolean excessiveRate) throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("绑定");
            Row header = sheet.createRow(0);
            String[] names = { "SKU", "商品类型", "直播成交价", "达人佣金率(%)", "平台扣点率(%)",
                "税率(%)", "包装费", "物流费", "鉴定费", "备注" };
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            Row finished = sheet.createRow(1);
            finished.createCell(0).setCellValue("0001");
            finished.createCell(1).setCellValue("成品商品");
            finished.createCell(2).setCellValue(100);
            finished.createCell(3).setCellValue(excessiveRate ? 70 : 20);
            finished.createCell(4).setCellValue(excessiveRate ? 30 : 5);
            if (!excessiveRate)
            {
                Row sample = sheet.createRow(2);
                sample.createCell(0).setCellValue("0001");
                sample.createCell(1).setCellValue("样品商品");
                sample.createCell(2).setCellValue(80);
            }
            workbook.write(out);
            return new MockMultipartFile("file", "bindings.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private Map<String, Object> product(Long id, String name)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("productId", id);
        result.put("productName", name);
        result.put("status", "0");
        return result;
    }
}
