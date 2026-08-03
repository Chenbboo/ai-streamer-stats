package com.ruoyi.jewelry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        "商品类型（新商品必填）", "分类", "规格", "单位", "数量", "采购单价", "商品图片" };
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Mock
    private JewelryErpMapper mapper;

    @InjectMocks
    private JewelryDocumentExcelService service;

    @TempDir
    Path tempDir;

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
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("模板选项")));
            assertEquals(IndexedColors.LIGHT_YELLOW.getIndex(),
                sheet.getRow(1).getCell(2).getCellStyle().getFillForegroundColor());
        }
    }

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
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-001", "测试戒指", "散件", "戒指", "18K", "件", 2, 6800, "" }), true);

        assertEquals(0, result.get("errorCount"));
        assertEquals(1, result.get("newProductCount"));
        Map<String, Object> row = rows(result).get(0);
        assertEquals("NEW", row.get("status"));
        assertEquals("PART", row.get("productType"));
        assertTrue(String.valueOf(row.get("imageUrl")).startsWith("/profile/jewelry/import/"));
        assertTrue(Files.exists(tempDir.resolve(String.valueOf(row.get("imageUrl"))
            .substring("/profile/".length()).replace("/", java.io.File.separator))));
    }

    @Test
    void purchasePreviewRejectsNewProductWithoutEmbeddedImage() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.preview("PURCHASE_IN", workbook(PURCHASE_HEADERS,
            new Object[] { "NEW-002", "测试项链", "成品", "项链", "足金", "件", 1, 2000, "" }), true);

        assertEquals(1, result.get("errorCount"));
        assertTrue(String.valueOf(rows(result).get(0).get("errorMessage")).contains("商品图片不能为空"));
    }

    @Test
    void purchasePreviewNormalizesLargeEmbeddedImageToWebp() throws Exception
    {
        when(mapper.selectProductList(any())).thenReturn(Collections.emptyList());
        new RuoYiConfig().setProfile(tempDir.toString());

        Map<String, Object> result = service.preview("PURCHASE_IN", purchaseWorkbookWithImage(
            new Object[] { "NEW-WEBP", "测试吊坠", "成品", "吊坠", "18K", "件", 1, 3000, "" },
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

    private ByteArrayInputStream purchaseWorkbookWithImage(Object[] values) throws Exception
    {
        return purchaseWorkbookWithImage(values, PNG);
    }

    private ByteArrayInputStream purchaseWorkbookWithImage(Object[] values, byte[] image) throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            XSSFSheet sheet = workbook.createSheet("导入数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < PURCHASE_HEADERS.length; i++) header.createCell(i).setCellValue(PURCHASE_HEADERS[i]);
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
            anchor.setCol1(8);
            anchor.setRow1(1);
            anchor.setCol2(9);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> result)
    {
        return (List<Map<String, Object>>) result.get("rows");
    }
}
