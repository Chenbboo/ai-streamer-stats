package com.ruoyi.jewelry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.jewelry.mapper.JewelryErpMapper;
import com.ruoyi.common.config.RuoYiConfig;

@ExtendWith(MockitoExtension.class)
class JewelryDocumentExcelServiceTest
{
    private static final String[] PURCHASE_HEADERS = new String[] { "SKU", "商品名称（新商品必填）",
        "商品类型（新商品必填）", "单位", "数量", "采购单价", "商品图片" };
    private static final String[] SALES_HEADERS = new String[] { "SKU", "数量", "成交单价", "包装费/件",
        "物流费/件", "鉴定费/件", "其他1/件", "其他2/件", "其他3/件" };
    private static final String[] SAMPLE_HEADERS = new String[] { "SKU", "商品", "业务日期",
        "供应商", "实物图片", "数量" };
    private static final String[] LEGACY_SAMPLE_HEADERS = new String[] { "货号", "SKU", "业务日期",
        "供应商编码或名称", "数量", "商品图片" };
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Mock
    private JewelryErpMapper mapper;

    @InjectMocks
    private JewelryDocumentExcelService service;

    @TempDir
    Path tempDir;

    @Test
    void bindingImportStoresPictureInsertedInImageColumn() throws Exception
    {
        new RuoYiConfig().setProfile(tempDir.toString());
        try (XSSFWorkbook workbook = new XSSFWorkbook())
        {
            XSSFSheet sheet = workbook.createSheet("达人商品绑定");
            sheet.createRow(0).createCell(14).setCellValue("图片");
            sheet.createRow(1).createCell(0).setCellValue("SKU-1");
            int pictureId = workbook.addPicture(PNG, Workbook.PICTURE_TYPE_PNG);
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(14);
            anchor.setRow1(1);
            anchor.setCol2(15);
            anchor.setRow2(2);
            sheet.createDrawingPatriarch().createPicture(anchor, pictureId);

            Map<Integer, Map<String, String>> images = service.importProductImages(sheet, 14);

            assertTrue(images.get(1).get("imageUrls").startsWith("/profile/jewelry/import/"));
        }
    }

    @Test
    void purchaseTemplateRestrictsProductTypeToDropdownValues() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.createTemplate("PURCHASE_IN"))))
        {
            XSSFSheet sheet = workbook.getSheet("导入数据");
            assertEquals(1, sheet.getDataValidations().size());
            assertEquals("ProductTypeOptions",
                sheet.getDataValidations().get(0).getValidationConstraint().getFormula1());
            assertTrue(sheet.getDataValidations().get(0).getSuppressDropDownArrow());
            assertFalse(sheet.getDataValidations().get(0).getShowPromptBox());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("模板选项")));
            assertEquals("赠品商品", workbook.getSheet("模板选项").getRow(4).getCell(0).getStringCellValue());
            assertEquals(IndexedColors.DARK_BLUE.getIndex(),
                sheet.getRow(0).getCell(0).getCellStyle().getFillForegroundColor());
            assertEquals(IndexedColors.WHITE.getIndex(),
                workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).getColor());
            assertEquals(34f, sheet.getRow(0).getHeightInPoints(), 0.1f);
            assertEquals(36f, sheet.getRow(1).getHeightInPoints(), 0.1f);
            assertEquals(24 * 256, sheet.getColumnWidth(2));
            assertEquals(FillPatternType.NO_FILL, sheet.getRow(1).getCell(2).getCellStyle().getFillPattern());
            assertEquals(HorizontalAlignment.CENTER, sheet.getRow(1).getCell(2).getCellStyle().getAlignment());
            assertEquals("#,##0.0000", sheet.getRow(1).getCell(5).getCellStyle().getDataFormatString());
            assertFalse(sheet.isDisplayGridlines());
            assertFalse(((XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter());
            assertEquals("采购入库模板填写说明", workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue());
            assertTrue(workbook.getSheet("填写说明").getRow(1).getCell(0).getStringCellValue()
                .contains("先在单据选择达人，再选择其绑定的供应商"));
            assertEquals(44f, workbook.getSheet("填写说明").getRow(3).getHeightInPoints(), 0.1f);
            Sheet options = workbook.getSheet("模板选项");
            assertEquals("成品商品", options.getRow(0).getCell(0).getStringCellValue());
            assertEquals("福利商品", options.getRow(3).getCell(0).getStringCellValue());
            assertEquals("赠品商品", options.getRow(4).getCell(0).getStringCellValue());
            assertEquals("'模板选项'!$A$1:$A$5", workbook.getName("ProductTypeOptions").getRefersToFormula());
        }
    }

    @Test
    void purchasePreviewRejectsSampleTypeLabelsAndCode() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());
        for (String type : new String[] { "样品商品", "样品", "SAMPLE", "sample" })
        {
            Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
                new Object[] { "NEW-SAMPLE", "样品项链", type, "件", 2, 10, "" }), true);
            assertEquals(1, result.get("errorCount"));
            assertEquals(0, result.get("newProductCount"));
            assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("样品商品请使用样品入库单据"));
        }
    }

    @Test
    void purchasePreviewRejectsExistingSampleProductWithoutTypeColumn() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-ONLY", 3, 0);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));

        Map<String, Object> result = service.preview("PURCHASE_IN", workbook(PURCHASE_HEADERS,
            new Object[] { "SAMPLE-ONLY", "", "", "", 1, 10, "" }), true);

        assertEquals(1, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("样品商品请使用样品入库单据"));
    }

    @Test
    void purchasePreviewRoundsUnitPriceToFourDecimals() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(product("SKU-1", 5, 0)));

        Map<String, Object> result = service.preview("PURCHASE_IN", workbook(PURCHASE_HEADERS,
            new Object[] { "SKU-1", "", "", "", 3, new BigDecimal("0.12345"), "" }), true);

        assertEquals(0, result.get("errorCount"));
        BigDecimal unitPrice = (BigDecimal) rows(result).get(0).get("unitPrice");
        assertEquals(new BigDecimal("0.1235"), unitPrice);
        assertEquals(4, unitPrice.scale());
    }

    @Test
    void salesPreviewRejectsQuantityAboveAvailableStock() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(product("SKU-1", 5, 1)));

        Map<String, Object> result = service.preview("SALES_OUT", workbook(SALES_HEADERS,
            new Object[] { "SKU-1", 5, 1000, 0, 0, 0, 1, 2, 3 }), false);

        assertEquals(1, result.get("errorCount"));
        Map<String, Object> row = rows(result).get(0);
        assertEquals(4, row.get("availableQty"));
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) row.get("otherFee1")));
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) row.get("otherFee2")));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) row.get("otherFee3")));
        assertTrue(String.valueOf(row.get("errorMessage")).contains("超过可用库存"));
    }

    @Test
    void salesTemplateOnlyRequiresSkuTypeAndQuantity() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.createTemplate("SALES_OUT"))))
        {
            XSSFSheet sheet = workbook.getSheet("导入数据");
            String[] expected = { "SKU", "商品类型", "数量" };
            for (int i = 0; i < expected.length; i++)
                assertEquals(expected[i], sheet.getRow(0).getCell(i).getStringCellValue());
            assertEquals(expected.length, sheet.getRow(0).getLastCellNum());
            assertEquals(HorizontalAlignment.RIGHT, sheet.getRow(1).getCell(2).getCellStyle().getAlignment());
            assertEquals(1, sheet.getDataValidations().size());
            assertEquals("SalesProductTypeOptions",
                sheet.getDataValidations().get(0).getValidationConstraint().getFormula1());
            assertEquals("B2:B501", sheet.getDataValidations().get(0).getRegions()
                .getCellRangeAddresses()[0].formatAsString());
            assertEquals("成品商品", workbook.getSheet("模板选项").getRow(0).getCell(0).getStringCellValue());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("模板选项")));
        }
    }

    @Test
    void sampleTemplateHasManualSkuAndPerLineFields() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.createTemplate("SAMPLE_IN"))))
        {
            XSSFSheet sheet = workbook.getSheet("导入数据");
            for (int i = 0; i < SAMPLE_HEADERS.length; i++)
                assertEquals(SAMPLE_HEADERS[i], sheet.getRow(0).getCell(i).getStringCellValue());
            assertEquals("@", sheet.getRow(1).getCell(0).getCellStyle().getDataFormatString());
            assertEquals("@", sheet.getRow(1).getCell(1).getCellStyle().getDataFormatString());
            assertEquals("yyyy-mm-dd", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
            assertEquals(HorizontalAlignment.CENTER, sheet.getRow(1).getCell(4).getCellStyle().getAlignment());
            assertEquals(HorizontalAlignment.RIGHT, sheet.getRow(1).getCell(5).getCellStyle().getAlignment());
            assertEquals(36f, sheet.getRow(1).getHeightInPoints(), 0.1f);
            assertEquals("样品入库模板填写说明",
                workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void samplePreviewMapsSkuDateSupplierAndZeroCostWithoutImage() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "测试商品", "2026-09-20", "SUP-1", "", 2 },
            new Object[] { "SAMPLE-1", "测试商品", "2026/9/21", "测试供应商", "", 3 }), false);

        assertEquals(2, result.get("validCount"));
        assertEquals(0, result.get("errorCount"));
        assertEquals("SAMPLE-1", rows(result).get(0).get("sku"));
        assertEquals("2026-09-20", rows(result).get(0).get("bizDate"));
        assertEquals("2026-09-21", rows(result).get(1).get("bizDate"));
        assertEquals(9L, rows(result).get(0).get("supplierId"));
        assertEquals("测试供应商", rows(result).get(1).get("supplierNameSnapshot"));
        assertEquals(BigDecimal.ZERO, rows(result).get(0).get("unitPrice"));
        assertFalse(rows(result).get(0).containsKey("imageUrl"));
    }

    @Test
    void samplePreviewMatchesExistingProductByManualSku() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productName", "苹果");
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "苹果", "2026-06-05", "SUP-1", "", 10 }), true);

        assertEquals(1, result.get("validCount"));
        assertEquals(0, result.get("newProductCount"));
        assertEquals("苹果", rows(result).get(0).get("productInput"));
        assertEquals("SAMPLE-1", rows(result).get(0).get("sku"));
        assertEquals(1L, rows(result).get(0).get("productId"));
    }

    @Test
    void samplePreviewCreatesSeparateProductWhenNonSampleHasSameSku() throws Exception
    {
        Map<String, Object> accessory = product("SHARED-1", 8, 0);
        accessory.put("productType", "ACCESSORY");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(accessory));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SHARED-1", "新样品", "2026-06-05", "SUP-1", "", 2 }), true);

        assertEquals(1, result.get("validCount"));
        assertEquals(1, result.get("newProductCount"));
        assertEquals("SAMPLE", rows(result).get(0).get("productType"));
        assertFalse(rows(result).get(0).containsKey("productId"));
    }

    @Test
    void samplePreviewUsesSampleStockWhenSkuIsShared() throws Exception
    {
        Map<String, Object> accessory = product("SHARED-1", 8, 0);
        accessory.put("productType", "ACCESSORY");
        Map<String, Object> sample = product("SHARED-1", 3, 0);
        sample.put("productId", 2L);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(java.util.Arrays.asList(accessory, sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SHARED-1", "测试商品", "2026-06-05", "SUP-1", "", 2 }), false);

        assertEquals(0, result.get("errorCount"));
        assertEquals(2L, rows(result).get(0).get("productId"));
        assertEquals(3, rows(result).get(0).get("systemQty"));
    }

    @Test
    void salesPreviewRequiresTypeForSharedSkuAndUsesSelectedStock() throws Exception
    {
        Map<String, Object> finished = product("SHARED-1", 9, 0);
        Map<String, Object> sample = product("SHARED-1", 2, 0);
        sample.put("productId", 2L);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(java.util.Arrays.asList(finished, sample));

        Map<String, Object> ambiguous = service.preview("SALES_OUT", workbook(SALES_HEADERS,
            new Object[] { "SHARED-1", 1, 100, 0, 0, 0, 0, 0, 0 }), false);
        assertEquals(1, ambiguous.get("errorCount"));
        assertTrue(String.valueOf(rows(ambiguous).get(0).get("errorMessage")).contains("填写商品类型"));

        String[] typedHeaders = java.util.Arrays.copyOf(SALES_HEADERS, SALES_HEADERS.length + 1);
        typedHeaders[SALES_HEADERS.length] = "商品类型";
        Map<String, Object> typed = service.preview("SALES_OUT", workbook(typedHeaders,
            new Object[] { "SHARED-1", 1, 100, 0, 0, 0, 0, 0, 0, "成品商品" }), false);
        assertEquals(0, typed.get("errorCount"));
        assertEquals(1L, rows(typed).get(0).get("productId"));
        assertEquals(9, rows(typed).get(0).get("systemQty"));
    }

    @Test
    void samplePreviewCreatesOneNewProductForRepeatedSku() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "APPLE-1", "苹果", "2026-06-05", "SUP-1", "", 10 },
            new Object[] { "APPLE-1", "苹果", "2026-06-06", "SUP-1", "", 5 }), true);

        assertEquals(2, result.get("validCount"));
        assertEquals(1, result.get("newProductCount"));
        String sku = String.valueOf(rows(result).get(0).get("sku"));
        assertEquals("APPLE-1", sku);
        assertEquals(sku, rows(result).get(1).get("sku"));
        assertEquals("苹果", rows(result).get(0).get("productName"));
        assertEquals("SAMPLE", rows(result).get(0).get("productType"));
        assertEquals("NEW", rows(result).get(0).get("status"));
    }

    @Test
    void samplePreviewRejectsDuplicateNewProductLine() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "APPLE-1", "苹果", "2026-06-05", "SUP-1", "", 10 },
            new Object[] { "APPLE-1", "苹果", "2026-06-05", "SUP-1", "", 5 }), true);

        assertEquals(2, result.get("errorCount"));
        assertEquals(0, result.get("newProductCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("重复"));
    }

    @Test
    void samplePreviewRequiresProductPermissionAndMatchingName() throws Exception
    {
        Map<String, Object> first = product("SAMPLE-1", 2, 0);
        first.put("productName", "已有样品");
        first.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(first));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "错误名称", "2026-06-05", "SUP-1", "", 1 },
            new Object[] { "NEW-1", "新样品", "2026-06-05", "SUP-1", "", 1 }), false);

        assertEquals(2, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("SKU与商品名称不一致"));
        assertTrue(String.valueOf(rows(result).get(1).get("errorMessage")).contains("无权新增商品"));
    }

    @Test
    void samplePreviewDoesNotRecreateDisabledSku() throws Exception
    {
        Map<String, Object> disabled = product("OLD-SKU", 0, 0);
        disabled.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenAnswer(call -> {
            Map<String, Object> query = call.getArgument(0);
            return "1".equals(query.get("status"))
                ? Collections.singletonList(disabled) : Collections.emptyList();
        });
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "OLD-SKU", "测试商品", "2026-09-20", "SUP-1", "", 1 }), true);

        assertEquals(1, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("已停用"));
    }

    @Test
    void samplePreviewAcceptsPreviouslyDownloadedTemplate() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(LEGACY_SAMPLE_HEADERS,
            new Object[] { "G-1", "SAMPLE-1", "2026-09-20", "SUP-1", 1, "" }), false);

        assertEquals(1, result.get("validCount"));
        assertEquals("SAMPLE-1", rows(result).get(0).get("sku"));
    }

    @Test
    void legacySampleSkuColumnDoesNotCreateProductFromUnknownCode() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(LEGACY_SAMPLE_HEADERS,
            new Object[] { "G-1", "UNKNOWN-SKU", "2026-09-20", "SUP-1", 1, "" }), true);

        assertEquals(1, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("SKU不存在"));
    }

    @Test
    void samplePreviewRejectsDuplicateCompositeAndWrongProductType() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productType", "SAMPLE");
        Map<String, Object> finished = product("FINISHED-1", 2, 0);
        finished.put("productId", 2L);
        when(mapper.selectProductList(any())).thenReturn(java.util.Arrays.asList(sample, finished));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "测试商品", "2026-09-20", "SUP-1", "", 1 },
            new Object[] { "SAMPLE-1", "测试商品", "2026-09-20", "测试供应商", "", 2 },
            new Object[] { "FINISHED-1", "测试商品", "2026-09-20", "SUP-1", "", 1 }), false);

        assertEquals(3, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("重复"));
        assertTrue(String.valueOf(rows(result).get(1).get("errorMessage")).contains("重复"));
        assertTrue(String.valueOf(rows(result).get(2).get("errorMessage")).contains("无权新增商品"));
    }

    @Test
    void samplePreviewRejectsUnknownSupplierAndInvalidDate() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));

        Map<String, Object> result = service.preview("SAMPLE_IN", workbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "测试商品", "2026-13-40", "不存在", "", 0 }), false);

        assertEquals(1, result.get("errorCount"));
        String errors = String.valueOf(rows(result).get(0).get("errorMessage"));
        assertTrue(errors.contains("业务日期格式"));
        assertTrue(errors.contains("供应商不存在"));
        assertTrue(errors.contains("数量必须是正整数"));
    }

    @Test
    void samplePreviewStoresOptionalEmbeddedImage() throws Exception
    {
        Map<String, Object> sample = product("SAMPLE-1", 2, 0);
        sample.put("productType", "SAMPLE");
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(sample));
        when(mapper.selectSupplierList(any())).thenReturn(Collections.singletonList(supplier()));
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("SAMPLE_IN", imageWorkbook(SAMPLE_HEADERS,
            new Object[] { "SAMPLE-1", "测试商品", "2026-09-20", "SUP-1", "", 1 }, 4, PNG), false);

        assertEquals(0, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("imageUrl")).startsWith("/profile/jewelry/import/"));
    }

    @Test
    void purchasePreviewAllowsNewProductForAuthorizedMaker() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-001", "测试戒指", "散件商品", "件", 2, 6800, "" }), true);

        assertEquals(0, result.get("errorCount"));
        assertEquals(1, result.get("newProductCount"));
        Map<String, Object> row = rows(result).get(0);
        assertEquals("NEW", row.get("status"));
        assertEquals("PART", row.get("productType"));
        assertFalse(row.containsKey("specification"));
        assertTrue(String.valueOf(row.get("imageUrl")).startsWith("/profile/jewelry/import/"));
        assertTrue(Files.exists(tempDir.resolve(String.valueOf(row.get("imageUrl"))
            .substring("/profile/".length()).replace("/", java.io.File.separator))));
    }

    @Test
    void purchasePreviewRejectsNewFinishedProductEvenWithEmbeddedImage() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-002", "测试项链", "成品商品", "件", 1, 2000, "" }), true);

        assertEquals(1, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("新成品或赠品请先在达人档案建档并绑定"));
    }

    @Test
    void purchasePreviewNormalizesLargeEmbeddedImageToWebp() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-WEBP", "测试吊坠", "散件商品", "件", 1, 3000, "" },
            largePng()), true);

        assertEquals(0, result.get("errorCount"));
        String imageUrl = String.valueOf(rows(result).get(0).get("imageUrl"));
        assertTrue(imageUrl.endsWith(".webp"));
        Path stored = tempDir.resolve(imageUrl.substring("/profile/".length())
            .replace("/", java.io.File.separator));
        byte[] bytes = Files.readAllBytes(stored);
        assertEquals("RIFF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("WEBP", new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void purchasePreviewSupportsAccessoryAndWelfareProductTypes() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> accessory = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-ACC", "测试配件", "配件商品", "件", 1, 10, "" }), true);
        Map<String, Object> welfare = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-GIFT", "测试福利", "福利商品", "件", 1, 1, "" }), true);

        assertEquals("ACCESSORY", rows(accessory).get(0).get("productType"));
        assertEquals("WELFARE", rows(welfare).get(0).get("productType"));
    }

    @Test
    void purchasePreviewCreatesAndSelectsGiftWithSharedSkuAndName() throws Exception
    {
        Map<String, Object> finished = product("SHARED-GIFT", 5, 0);
        Map<String, Object> sample = product("SHARED-GIFT", 2, 0);
        sample.put("productId", 2L);
        sample.put("productType", "SAMPLE");
        Map<String, Object> gift = product("SHARED-GIFT", 3, 0);
        gift.put("productId", 3L);
        gift.put("productType", "GIFT");
        when(mapper.selectProductList(any())).thenReturn(java.util.Arrays.asList(finished, sample),
            java.util.Arrays.asList(finished, sample, gift));
        new RuoYiConfig().setProfile(tempDir.toString());
        Object[] input = { "SHARED-GIFT", "测试商品", "赠品商品", "件", 1, 0, "" };

        Map<String, Object> created = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(input), true);
        assertEquals(1, created.get("errorCount"));
        assertEquals(0, created.get("newProductCount"));
        assertEquals("赠品商品", rows(created).get(0).get("productType"));
        assertFalse(rows(created).get(0).containsKey("productId"));

        Map<String, Object> existing = service.preview("PURCHASE_IN", workbook(PURCHASE_HEADERS,
            new Object[] { "SHARED-GIFT", "测试商品", "赠品商品", "件", 1, 0, "" }), true);
        assertEquals(0, existing.get("errorCount"));
        assertEquals(0, existing.get("newProductCount"));
        assertEquals(3L, rows(existing).get(0).get("productId"));
    }

    @Test
    void purchasePreviewDoesNotRequireLegacyClassification() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-SPEC", "测试商品", "散件商品", "件", 1, 100, "" }), true);

        assertEquals(0, result.get("errorCount"));
        assertFalse(rows(result).get(0).containsKey("specification"));
    }

    @Test
    void purchaseReviewAcceptsCorrectedImageAndRechecksEditedValues() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        Map<String, Object> preview = service.preview("PURCHASE_IN", workbook(PURCHASE_HEADERS,
            new Object[] { "NEW-1", "测试商品", "散件商品", "件", 2, 10, "" }), true);
        assertEquals(1, preview.get("errorCount"));
        Map<String, Object> edited = rows(preview).get(0);
        edited.put("imageUrls", "/profile/upload/corrected.png");
        edited.put("qty", 3);

        Map<String, Object> checked = service.review("PURCHASE_IN", Collections.singletonList(edited), true);
        assertEquals(0, checked.get("errorCount"));
        assertEquals(1, checked.get("newProductCount"));
        assertEquals(3, rows(checked).get(0).get("qty"));
        assertEquals("/profile/upload/corrected.png", rows(checked).get(0).get("imageUrl"));
        assertEquals(2, rows(checked).get(0).get("rowNumber"));

        edited.put("qty", 0);
        Map<String, Object> invalid = service.review("PURCHASE_IN", Collections.singletonList(edited), true);
        assertEquals(1, invalid.get("errorCount"));
        assertTrue(String.valueOf(rows(invalid).get(0).get("errorMessage")).contains("数量必须是正整数"));
    }

    @Test
    void previewRejectsDuplicateSkuRows() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.singletonList(product("SKU-1", 10, 0)));

        Map<String, Object> result = service.preview("SALES_OUT", workbook(SALES_HEADERS,
            new Object[] { "SKU-1", 1, 1000, 0, 0, 0, 0, 0, 0 },
            new Object[] { "sku-1", 1, 1000, 0, 0, 0, 0, 0, 0 }), false);

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

    private ByteArrayInputStream purchaseWorkbookWithImage(Object[] values) throws Exception
    {
        return purchaseWorkbookWithImage(values, PNG);
    }

    private ByteArrayInputStream purchaseWorkbookWithImage(Object[] values, byte[] image) throws Exception
    {
        return imageWorkbook(PURCHASE_HEADERS, values, 6, image);
    }

    private ByteArrayInputStream imageWorkbook(String[] headers, Object[] values, int imageColumn,
        byte[] image) throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            XSSFSheet sheet = workbook.createSheet("导入数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row row = sheet.createRow(1);
            for (int column = 0; column < values.length; column++)
            {
                Object value = values[column];
                if (value instanceof Number) row.createCell(column).setCellValue(((Number) value).doubleValue());
                else row.createCell(column).setCellValue(String.valueOf(value));
            }
            int pictureId = workbook.addPicture(image, Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(imageColumn);
            anchor.setRow1(1);
            anchor.setCol2(imageColumn + 1);
            anchor.setRow2(2);
            drawing.createPicture(anchor, pictureId);
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private byte[] largePng() throws Exception
    {
        BufferedImage image = new BufferedImage(2001, 10, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private Map<String, Object> product(String sku, int onHandQty, int reservedOutQty)
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 1L);
        product.put("sku", sku);
        product.put("productName", "测试商品");
        product.put("productType", "FINISHED");
        product.put("imageUrl", "/profile/existing.jpg");
        product.put("imageUrls", "/profile/existing.jpg");
        product.put("onHandQty", onHandQty);
        product.put("reservedOutQty", reservedOutQty);
        product.put("avgCost", new BigDecimal("100.00"));
        return product;
    }

    private Map<String, Object> supplier()
    {
        Map<String, Object> supplier = new HashMap<String, Object>();
        supplier.put("supplierId", 9L);
        supplier.put("supplierCode", "SUP-1");
        supplier.put("supplierName", "测试供应商");
        return supplier;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> result)
    {
        return (List<Map<String, Object>>) result.get("rows");
    }
}
