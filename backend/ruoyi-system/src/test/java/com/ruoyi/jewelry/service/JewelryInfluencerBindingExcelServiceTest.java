package com.ruoyi.jewelry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@ExtendWith(MockitoExtension.class)
class JewelryInfluencerBindingExcelServiceTest
{
    @Mock private JewelryErpMapper mapper;
    @Mock private JewelryDocumentExcelService documentExcelService;
    @InjectMocks private JewelryInfluencerBindingExcelService service;

    @Test
    void templateIncludesRequiredUnitCostColumn() throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeTemplate(response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
            new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            Row header = workbook.getSheet("达人商品绑定").getRow(0);
            assertEquals("商品SKU", header.getCell(0).getStringCellValue());
            assertEquals("商品名称", header.getCell(1).getStringCellValue());
            assertEquals(Arrays.asList("成品商品", "赠品商品"),
                Arrays.asList(workbook.getSheet("达人商品绑定").getDataValidations().get(0)
                    .getValidationConstraint().getExplicitListValues()));
            String[] names = { "商品SKU", "商品名称", "商品类型", "供应商名称", "直播成交价", "商品成本价",
                "采购单价", "达人佣金率(%)", "平台扣点率(%)", "税率(%)", "包装费", "物流费",
                "鉴定费", "单位", "图片", "备注" };
            for (int i = 0; i < names.length; i++) assertEquals(names[i], header.getCell(i).getStringCellValue());
            assertEquals(2, workbook.getSheet("达人商品绑定").getDataValidations().get(0)
                .getRegions().getCellRangeAddresses()[0].getFirstColumn());
        }
    }

    @Test
    void previousSupplierAndPurchasePriceHeadersRemainImportable() throws Exception
    {
        when(mapper.selectProductBySkuAndType("NEW-SKU", "FINISHED")).thenReturn(null);
        when(mapper.selectSupplierById(7L)).thenReturn(supplier(7L, "天吉珠宝", "0"));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
            ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("达人商品绑定");
            Row header = sheet.createRow(0);
            String[] names = { "SKU", "商品类型", "直播成交价", "商品成本价", "达人佣金率(%)",
                "平台扣点率(%)", "税率(%)", "包装费", "物流费", "鉴定费", "备注",
                "商品名称", "单位", "图片地址", "常用供应商ID", "参考采购单价" };
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("NEW-SKU");
            row.createCell(1).setCellValue("成品商品");
            row.createCell(2).setCellValue(100);
            row.createCell(3).setCellValue(50);
            row.createCell(11).setCellValue("新商品");
            row.createCell(12).setCellValue("件");
            row.createCell(14).setCellValue(7);
            row.createCell(15).setCellValue(30);
            workbook.write(out);

            MockMultipartFile file = new MockMultipartFile("file", "bindings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            List<Map<String, Object>> rows = service.preview(file, null);
            assertEquals(1, rows.size());
            assertEquals("30", rows.get(0).get("referencePurchasePrice"));
            assertEquals("天吉珠宝", rows.get(0).get("preferredSupplierName"));
            assertTrue(errors(rows.get(0)).isEmpty(), errors(rows.get(0)).toString());
        }
    }

    @Test
    void previewReturnsEveryRowAndMarksUnknownSupplierByName() throws Exception
    {
        when(mapper.selectProductBySkuAndType("SKU-1", "FINISHED")).thenReturn(null);
        when(mapper.selectProductBySkuAndType("SKU-2", "FINISHED")).thenReturn(null);
        when(mapper.selectSuppliersByName("天吉珠宝")).thenReturn(Collections.singletonList(supplier(7L, "天吉珠宝", "0")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.writeTemplate(response);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
            ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.getSheet("达人商品绑定");
            for (int i = 1; i <= 2; i++)
            {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue("SKU-" + i);
                row.createCell(1).setCellValue("商品" + i);
                row.createCell(2).setCellValue("成品商品");
                row.createCell(3).setCellValue(i == 1 ? "天吉珠宝" : "不存在的供应商");
                row.createCell(4).setCellValue(100);
                row.createCell(5).setCellValue(50);
                row.createCell(13).setCellValue("件");
            }
            workbook.write(out);
            List<Map<String, Object>> rows = service.preview(new MockMultipartFile("file", "bindings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()), null);
            assertEquals(2, rows.size());
            assertEquals(7L, rows.get(0).get("preferredSupplierId"));
            assertTrue(errors(rows.get(0)).isEmpty(), errors(rows.get(0)).toString());
            assertEquals("不存在的供应商", rows.get(1).get("excelSupplierName"));
            assertTrue(errors(rows.get(1)).stream().anyMatch(error -> error.contains("供应商名称不存在")));
        }
    }

    @Test
    void newTemplateMapsEmbeddedImageAndRejectsTypedImageAddress() throws Exception
    {
        when(mapper.selectProductBySkuAndType("NEW-IMAGE", "FINISHED")).thenReturn(null);
        when(documentExcelService.importProductImages(any(XSSFSheet.class), eq(14)))
            .thenReturn(Collections.singletonMap(1, Collections.singletonMap("imageUrls", "/profile/jewelry/import/test.png")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.writeTemplate(response);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
            ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Row row = workbook.getSheet("达人商品绑定").createRow(1);
            row.createCell(0).setCellValue("NEW-IMAGE");
            row.createCell(1).setCellValue("图片商品");
            row.createCell(2).setCellValue("成品商品");
            row.createCell(4).setCellValue(100);
            row.createCell(5).setCellValue(30);
            row.createCell(13).setCellValue("件");
            row.createCell(14).setCellValue("https://example.com/photo.png");
            workbook.write(out);
            List<Map<String, Object>> rows = service.preview(new MockMultipartFile("file", "bindings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()), null);
            assertEquals("/profile/jewelry/import/test.png", rows.get(0).get("imageUrls"));
            assertTrue(errors(rows.get(0)).stream().anyMatch(error -> error.contains("不要填写图片地址")));
            rows.get(0).remove("imageError");
            service.validateRows(null, rows);
            assertTrue(errors(rows.get(0)).isEmpty(), errors(rows.get(0)).toString());
        }
    }

    @Test
    void duplicateSupplierNameRequiresSpecificSelection()
    {
        when(mapper.selectProductBySkuAndType("FINISHED-1", "FINISHED")).thenReturn(null);
        when(mapper.selectSuppliersByName("同名供应商")).thenReturn(Arrays.asList(
            supplier(7L, "同名供应商", "0"), supplier(8L, "同名供应商", "0")));
        Map<String, Object> row = new HashMap<>();
        row.put("sku", "FINISHED-1");
        row.put("productType", "FINISHED");
        row.put("productName", "成品");
        row.put("unit", "件");
        row.put("fixedUnitPrice", "1");
        row.put("unitCost", "0");
        row.put("preferredSupplierName", "同名供应商");
        row.put("bindingStatus", "0");

        service.validateRows(null, Collections.singletonList(row));

        assertTrue(errors(row).stream().anyMatch(error -> error.contains("供应商名称重复")));
    }

    @Test
    void newSupplierIsValidatedAndCanBeSharedAcrossImportedRows()
    {
        when(mapper.selectProductBySkuAndType("NEW-1", "FINISHED")).thenReturn(null);
        when(mapper.selectProductBySkuAndType("NEW-2", "FINISHED")).thenReturn(null);
        when(mapper.selectSupplierByCode("NEW-01")).thenReturn(null);
        Map<String, Object> supplier = supplierRequest("NEW-01", "新供应商");
        Map<String, Object> first = new HashMap<>();
        first.put("sku", "NEW-1");
        first.put("productType", "FINISHED");
        first.put("productName", "成品");
        first.put("unit", "件");
        first.put("fixedUnitPrice", "100");
        first.put("unitCost", "50");
        first.put("bindingStatus", "0");
        first.put("preferredSupplierName", "原Excel中不存在的供应商");
        first.put("newSupplier", supplier);
        Map<String, Object> second = new HashMap<>(first);
        second.put("sku", "NEW-2");
        second.put("productName", "成品2");
        second.put("productType", "FINISHED");

        service.validateRows(null, Arrays.asList(first, second));

        assertTrue(errors(first).isEmpty(), errors(first).toString());
        assertTrue(errors(second).isEmpty(), errors(second).toString());
        assertEquals("新供应商", first.get("preferredSupplierName"));
        assertEquals("新供应商", second.get("preferredSupplierName"));

        second.put("newSupplier", supplierRequest("NEW-01", "另一供应商"));
        service.validateRows(null, Arrays.asList(first, second));
        assertTrue(errors(second).stream().anyMatch(error -> error.contains("信息不一致")));
    }

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
        assertTrue(errors(rows.get(1)).stream().anyMatch(error -> error.contains("只支持成品商品或赠品商品")));
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
        rows.get(0).put("unitCost", "50");
        rows.get(1).put("fixedUnitPrice", "80");
        rows.get(1).put("unitCost", "40");
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
        row.put("unitCost", "50");
        row.put("bindingStatus", "0");

        service.validateRows(null, Collections.singletonList(row));
        assertTrue(errors(row).stream().anyMatch(error -> error.contains("不一致")));
    }

    @Test
    void existingProductImageChangeSurvivesRevalidationAndCanClearTheImage()
    {
        Map<String, Object> existing = product(1L, "项链");
        existing.put("sku", "0001");
        existing.put("productType", "FINISHED");
        existing.put("unit", "件");
        existing.put("imageUrls", "/profile/jewelry/original.png");
        when(mapper.selectProductById(1L)).thenReturn(existing);
        Map<String, Object> row = new HashMap<>();
        row.put("productId", 1L);
        row.put("sku", "0001");
        row.put("productType", "FINISHED");
        row.put("fixedUnitPrice", "100");
        row.put("unitCost", "50");
        row.put("bindingStatus", "0");
        row.put("imageChanged", true);
        row.put("imageUrls", "");

        service.validateRows(null, Collections.singletonList(row));

        assertTrue(errors(row).isEmpty(), errors(row).toString());
        assertEquals("", row.get("imageUrls"));
        assertEquals(true, row.get("imageChanged"));
    }

    @Test
    void newFinishedSkuCanBeValidatedForAtomicArchiveCreation()
    {
        when(mapper.selectProductBySkuAndType("NEW-42", "FINISHED")).thenReturn(null);
        Map<String, Object> row = new HashMap<>();
        row.put("sku", "NEW-42");
        row.put("productType", "FINISHED");
        row.put("productName", "新成品");
        row.put("specification", "普通");
        row.put("unit", "件");
        row.put("fixedUnitPrice", "100.00");
        row.put("unitCost", "40.00");
        row.put("referencePurchasePrice", "35.00");
        row.put("bindingStatus", "0");

        service.validateRows(null, Collections.singletonList(row));

        assertTrue(errors(row).isEmpty());
        assertTrue(!row.containsKey("productId"));
    }

    @Test
    void newGiftSkuCanBeValidatedForInfluencerBindingImport()
    {
        when(mapper.selectProductBySkuAndType("SHARED-1", "GIFT")).thenReturn(null);
        Map<String, Object> row = new HashMap<>();
        row.put("sku", "SHARED-1");
        row.put("productType", "GIFT");
        row.put("productName", "随单赠品");
        row.put("unit", "件");
        row.put("fixedUnitPrice", "0.00");
        row.put("unitCost", "0.50");
        row.put("referencePurchasePrice", "0.25");
        row.put("bindingStatus", "0");

        service.validateRows(null, Collections.singletonList(row));

        assertTrue(errors(row).isEmpty(), errors(row).toString());
        assertTrue(!row.containsKey("productId"));
    }

    @SuppressWarnings("unchecked")
    private List<String> errors(Map<String, Object> row)
    {
        return (List<String>) row.get("errors");
    }

    private Map<String, Object> supplierRequest(String code, String name)
    {
        Map<String, Object> supplier = new HashMap<>();
        supplier.put("supplierCode", code);
        supplier.put("supplierName", name);
        return supplier;
    }

    private MockMultipartFile workbookWithErrors() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("绑定");
            Row header = sheet.createRow(0);
            String[] names = { "SKU", "商品类型", "直播成交价", "商品成本价", "达人佣金率(%)", "平台扣点率(%)",
                "税率(%)", "包装费", "物流费", "鉴定费", "备注" };
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("0001");
            first.createCell(1).setCellValue("错误类型");
            first.createCell(2).setCellValue(100);
            first.createCell(3).setCellValue(50);
            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("0002");
            second.createCell(1).setCellValue("成品商品");
            second.createCell(2).setCellValue("错误价格");
            second.createCell(3).setCellValue(40);
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
            String[] names = { "SKU", "商品类型", "直播成交价", "商品成本价", "达人佣金率(%)", "平台扣点率(%)",
                "税率(%)", "包装费", "物流费", "鉴定费", "备注" };
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            Row finished = sheet.createRow(1);
            finished.createCell(0).setCellValue("0001");
            finished.createCell(1).setCellValue("成品商品");
            finished.createCell(2).setCellValue(100);
            finished.createCell(3).setCellValue(50);
            finished.createCell(4).setCellValue(excessiveRate ? 70 : 20);
            finished.createCell(5).setCellValue(excessiveRate ? 30 : 5);
            if (!excessiveRate)
            {
                Row sample = sheet.createRow(2);
                sample.createCell(0).setCellValue("0001");
                sample.createCell(1).setCellValue("样品商品");
                sample.createCell(2).setCellValue(80);
                sample.createCell(3).setCellValue(40);
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

    private Map<String, Object> supplier(Long id, String name, String status)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("supplierId", id);
        result.put("supplierName", name);
        result.put("status", status);
        return result;
    }
}
