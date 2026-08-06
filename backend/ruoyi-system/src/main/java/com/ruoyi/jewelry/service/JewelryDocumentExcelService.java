package com.ruoyi.jewelry.service;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import com.luciad.imageio.webp.WebPWriteParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@Service
public class JewelryDocumentExcelService
{
    private static final int MAX_ROWS = 500;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SOURCE_IMAGE_BYTES = 50 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final String IMAGE_HEADER = "商品图片";
    private static final Pattern CELL_IMAGE_FORMULA =
        Pattern.compile("DISPIMG\\(\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final String CELL_IMAGES_PART = "/xl/cellimages.xml";
    private static final String RELATIONSHIP_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("PURCHASE_IN", "SALES_OUT", "STOCK_ADJUST")));

    @Autowired
    private JewelryErpMapper mapper;

    public byte[] createTemplate(String docType)
    {
        requireSupported(docType);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet data = workbook.createSheet("导入数据");
            String[] headers = headers(docType);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = data.createRow(0);
            header.setHeightInPoints(34);
            for (int i = 0; i < headers.length; i++)
            {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            setTemplateColumnWidths(data, docType, headers);
            styleTemplateInputRows(workbook, data, docType, headers);
            if ("PURCHASE_IN".equals(docType))
            {
                Sheet options = workbook.createSheet("模板选项");
                String[] productTypes = { "成品商品", "散件商品", "配件商品", "福利商品" };
                for (int i = 0; i < productTypes.length; i++) options.createRow(i).createCell(0).setCellValue(productTypes[i]);
                options.getRow(0).createCell(1).setCellValue("精品");
                options.getRow(1).createCell(1).setCellValue("普通");
                addDropdownValidation(workbook, data, headers, "商品类型（新商品必填）",
                    "ProductTypeOptions", "'模板选项'!$A$1:$A$4", "商品类型",
                    "请选择成品商品、散件商品、配件商品或福利商品");
                addDropdownValidation(workbook, data, headers, "规格类型（新商品必填）",
                    "SpecificationOptions", "'模板选项'!$B$1:$B$2", "规格类型",
                    "请选择精品或普通");
                workbook.setSheetHidden(workbook.getSheetIndex(options), true);
            }
            data.createFreezePane(0, 1);
            data.setDisplayGridlines(false);

            Sheet guide = workbook.createSheet("填写说明");
            styleGuideSheet(workbook, guide, docType);
            workbook.write(output);
            return output.toByteArray();
        }
        catch (Exception e)
        {
            throw new ServiceException("生成Excel模板失败：" + e.getMessage());
        }
    }

    public Map<String, Object> preview(String docType, InputStream input, boolean allowNewProduct)
    {
        requireSupported(docType);
        try (Workbook workbook = WorkbookFactory.create(input))
        {
            if ("PURCHASE_IN".equals(docType) && !(workbook instanceof XSSFWorkbook))
                throw new ServiceException("采购入库含商品图片时仅支持xlsx格式");
            if (workbook.getNumberOfSheets() == 0) throw new ServiceException("Excel中没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new ServiceException("Excel表头不能为空");

            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> columns = readHeaders(headerRow, formatter, evaluator);
            for (String required : requiredHeaders(docType))
            {
                if (!columns.containsKey(required)) throw new ServiceException("Excel缺少必填列：" + required);
            }
            Map<Integer, EmbeddedImage> embeddedImages = "PURCHASE_IN".equals(docType)
                ? extractEmbeddedImages((XSSFSheet) sheet, columns.get(IMAGE_HEADER))
                : Collections.<Integer, EmbeddedImage>emptyMap();

            Map<String, Map<String, Object>> products = loadProducts();
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            Map<String, Integer> skuCounts = new HashMap<String, Integer>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++)
            {
                Row source = sheet.getRow(rowIndex);
                if (source == null || rowIsEmpty(source, formatter, evaluator)) continue;
                if (rows.size() >= MAX_ROWS) throw new ServiceException("单次最多导入" + MAX_ROWS + "行");
                Map<String, Object> row = parseRow(docType, source, rowIndex + 1, columns, formatter, evaluator);
                String skuKey = normalizeSku(string(row.get("sku")));
                skuCounts.put(skuKey, skuCounts.containsKey(skuKey) ? skuCounts.get(skuKey) + 1 : 1);
                rows.add(row);
            }
            if (rows.isEmpty()) throw new ServiceException("Excel中没有可导入的数据");

            int validCount = 0;
            int errorCount = 0;
            int newProductCount = 0;
            for (Map<String, Object> row : rows)
            {
                List<String> errors = new ArrayList<String>();
                String skuKey = normalizeSku(string(row.get("sku")));
                if (skuKey.isEmpty()) errors.add("SKU不能为空");
                if (!skuKey.isEmpty() && skuCounts.get(skuKey) > 1) errors.add("Excel中SKU重复");
                Map<String, Object> product = products.get(skuKey);
                boolean newProduct = product == null;
                if (newProduct && !"PURCHASE_IN".equals(docType)) errors.add("SKU不存在");
                if (newProduct && "PURCHASE_IN".equals(docType))
                {
                    if (!allowNewProduct) errors.add("当前账号无权新增商品档案");
                    if (string(row.get("productName")).isEmpty()) errors.add("新商品必须填写商品名称");
                    String productType = normalizeProductType(string(row.get("productType")));
                    if (productType == null) errors.add("新商品类型必须选择成品商品、散件商品、配件商品或福利商品");
                    else row.put("productType", productType);
                    String specification = normalizeSpecification(string(row.get("specification")));
                    if (specification == null) errors.add("新商品规格类型必须选择精品或普通");
                    else row.put("specification", specification);
                }
                if (!newProduct && "PURCHASE_IN".equals(docType))
                {
                    String inputType = string(row.get("productType"));
                    String currentType = string(product.get("productType"));
                    if (!inputType.isEmpty())
                    {
                        String normalized = normalizeProductType(inputType);
                        if (normalized == null) errors.add("商品类型只能选择成品商品、散件商品、配件商品或福利商品");
                        else if (!normalized.equals(currentType)) errors.add("已有SKU的商品类型与商品档案不一致");
                    }
                    row.put("productType", currentType);
                    String inputSpecification = string(row.get("specification"));
                    String currentSpecification = string(product.get("specification"));
                    if (!inputSpecification.isEmpty())
                    {
                        String normalized = normalizeSpecification(inputSpecification);
                        if (normalized == null) errors.add("规格类型只能选择精品或普通");
                        else if (!normalized.equals(currentSpecification)) errors.add("已有SKU的规格类型与商品档案不一致");
                    }
                    row.put("specification", currentSpecification);
                }
                if ("PURCHASE_IN".equals(docType))
                {
                    EmbeddedImage embedded = embeddedImages.get(integer(row.get("rowNumber")) - 1);
                    if (embedded != null && embedded.error != null) errors.add(embedded.error);
                    String existingImage = product == null ? "" :
                        defaultString(string(product.get("imageUrl")), firstImage(string(product.get("imageUrls"))));
                    if (embedded == null && existingImage.isEmpty()) errors.add("商品图片不能为空");
                    if (embedded != null && embedded.error == null)
                    {
                        String imageUrl = storeEmbeddedImage(embedded);
                        row.put("imageUrl", imageUrl);
                        row.put("imageUrls", imageUrl);
                    }
                    else if (!existingImage.isEmpty())
                    {
                        row.put("imageUrl", existingImage);
                        row.put("imageUrls", existingImage);
                    }
                }
                validateNumbers(docType, row, product, errors);
                if (product != null)
                {
                    row.put("productId", product.get("productId"));
                    row.put("productName", product.get("productName"));
                    row.put("unitCost", decimal(product.get("avgCost")));
                    row.put("systemQty", integer(product.get("onHandQty")));
                    row.put("availableQty", integer(product.get("onHandQty")) - integer(product.get("reservedOutQty")));
                }
                row.put("newProduct", newProduct);
                row.put("valid", errors.isEmpty());
                row.put("errorMessage", join(errors));
                row.put("status", errors.isEmpty() ? (newProduct ? "NEW" : "VALID") : "ERROR");
                if (errors.isEmpty())
                {
                    validCount++;
                    if (newProduct) newProductCount++;
                }
                else errorCount++;
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("docType", docType);
            result.put("rows", rows);
            result.put("validCount", validCount);
            result.put("errorCount", errorCount);
            result.put("newProductCount", newProductCount);
            return result;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("Excel解析失败，请检查文件格式：" + e.getMessage());
        }
    }

    private Map<String, Object> parseRow(String docType, Row source, int rowNumber,
        Map<String, Integer> columns, DataFormatter formatter, FormulaEvaluator evaluator)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("rowNumber", rowNumber);
        row.put("sku", value(source, columns, "SKU", formatter, evaluator).trim());
        if ("PURCHASE_IN".equals(docType))
        {
            row.put("productName", value(source, columns, "商品名称（新商品必填）", formatter, evaluator).trim());
            row.put("productType", value(source, columns, "商品类型（新商品必填）", formatter, evaluator).trim());
            row.put("category", value(source, columns, "分类", formatter, evaluator).trim());
            row.put("specification", value(source, columns, "规格类型（新商品必填）", formatter, evaluator).trim());
            row.put("unit", defaultString(value(source, columns, "单位", formatter, evaluator).trim(), "件"));
            row.put("qty", integerValue(value(source, columns, "数量", formatter, evaluator)));
            row.put("unitPrice", decimalValue(value(source, columns, "采购单价", formatter, evaluator)));
        }
        else if ("SALES_OUT".equals(docType))
        {
            row.put("qty", integerValue(value(source, columns, "数量", formatter, evaluator)));
            row.put("unitPrice", decimalValue(value(source, columns, "成交单价", formatter, evaluator)));
            row.put("packFee", decimalValue(value(source, columns, "包装费/件", formatter, evaluator)));
            row.put("shipFee", decimalValue(value(source, columns, "物流费/件", formatter, evaluator)));
            row.put("certFee", decimalValue(value(source, columns, "鉴定费/件", formatter, evaluator)));
            row.put("otherFee1", decimalValue(value(source, columns, "其他1/件", formatter, evaluator)));
            row.put("otherFee2", decimalValue(value(source, columns, "其他2/件", formatter, evaluator)));
            row.put("otherFee3", decimalValue(value(source, columns, "其他3/件", formatter, evaluator)));
        }
        else
        {
            row.put("countedQty", integerValue(value(source, columns, "实盘数量", formatter, evaluator)));
            row.put("lineReason", value(source, columns, "调整原因", formatter, evaluator).trim());
        }
        return row;
    }

    private void validateNumbers(String docType, Map<String, Object> row,
        Map<String, Object> product, List<String> errors)
    {
        if ("STOCK_ADJUST".equals(docType))
        {
            if (row.get("countedQty") == null || integer(row.get("countedQty")) < 0)
                errors.add("实盘数量必须是大于等于0的整数");
            if (string(row.get("lineReason")).isEmpty()) errors.add("调整原因不能为空");
            return;
        }
        if (row.get("qty") == null || integer(row.get("qty")) <= 0) errors.add("数量必须是正整数");
        if (row.get("unitPrice") == null || decimal(row.get("unitPrice")).compareTo(BigDecimal.ZERO) < 0)
            errors.add("单价必须是大于等于0的数字");
        if ("SALES_OUT".equals(docType))
        {
            validateNonNegative(row, "packFee", "包装费", errors);
            validateNonNegative(row, "shipFee", "物流费", errors);
            validateNonNegative(row, "certFee", "鉴定费", errors);
            validateNonNegative(row, "otherFee1", "其他1", errors);
            validateNonNegative(row, "otherFee2", "其他2", errors);
            validateNonNegative(row, "otherFee3", "其他3", errors);
            if (product != null && row.get("qty") != null
                && integer(row.get("qty")) > integer(product.get("onHandQty")) - integer(product.get("reservedOutQty")))
                errors.add("销售数量超过可用库存");
        }
    }

    private void validateNonNegative(Map<String, Object> row, String key, String label, List<String> errors)
    {
        if (row.get(key) == null || decimal(row.get(key)).compareTo(BigDecimal.ZERO) < 0)
            errors.add(label + "必须是大于等于0的数字");
    }

    private Map<String, Map<String, Object>> loadProducts()
    {
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("status", "0");
        Map<String, Map<String, Object>> result = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> product : mapper.selectProductList(query))
        {
            result.put(normalizeSku(string(product.get("sku"))), product);
        }
        return result;
    }

    private Map<String, Integer> readHeaders(Row row, DataFormatter formatter, FormulaEvaluator evaluator)
    {
        Map<String, Integer> headers = new HashMap<String, Integer>();
        for (int i = 0; i < row.getLastCellNum(); i++)
        {
            String value = cellValue(row.getCell(i), formatter, evaluator).trim();
            if (!value.isEmpty()) headers.put(value, i);
        }
        return headers;
    }

    private boolean rowIsEmpty(Row row, DataFormatter formatter, FormulaEvaluator evaluator)
    {
        for (int i = 0; i < row.getLastCellNum(); i++)
        {
            if (!cellValue(row.getCell(i), formatter, evaluator).trim().isEmpty()) return false;
        }
        return true;
    }

    private String value(Row row, Map<String, Integer> columns, String header,
        DataFormatter formatter, FormulaEvaluator evaluator)
    {
        Integer index = columns.get(header);
        return index == null ? "" : cellValue(row.getCell(index), formatter, evaluator);
    }

    private String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator)
    {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private Integer integerValue(String value)
    {
        try
        {
            if (value == null || value.trim().isEmpty()) return null;
            return new BigDecimal(value.replace(",", "").trim()).intValueExact();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private BigDecimal decimalValue(String value)
    {
        try
        {
            if (value == null || value.trim().isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(value.replace(",", "").trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String[] headers(String docType)
    {
        if ("PURCHASE_IN".equals(docType))
            return new String[] { "SKU", "商品名称（新商品必填）", "商品类型（新商品必填）",
                "分类", "规格类型（新商品必填）", "单位", "数量", "采购单价", IMAGE_HEADER };
        if ("SALES_OUT".equals(docType))
            return new String[] { "SKU", "数量", "成交单价", "包装费/件", "物流费/件", "鉴定费/件",
                "其他1/件", "其他2/件", "其他3/件" };
        return new String[] { "SKU", "实盘数量", "调整原因" };
    }

    private List<String> requiredHeaders(String docType)
    {
        return Arrays.asList(headers(docType));
    }

    private String[] guide(String docType)
    {
        if ("PURCHASE_IN".equals(docType))
            return new String[] { "一行填写一个SKU，数量必须为正整数。", "已有SKU只需填写SKU、数量和采购单价。",
                "新SKU必须填写商品名称、商品类型和规格类型；商品类型从四种固定选项中选择，规格类型只能选择“精品”或“普通”。",
                "每行只能在“商品图片”列插入一张JPG或PNG图片；已有档案图片的SKU可不重复插图。",
                "图片应完整放在对应单元格内，并设置为随单元格移动和调整大小。",
                "确认导入时系统会先创建商品档案。单次最多500行，禁止重复SKU。" };
        if ("SALES_OUT".equals(docType))
            return new String[] { "一行填写一个SKU，SKU必须已存在。", "销售数量不能超过当前可用库存。",
                "费用均按每件填写，未发生费用时填写0。", "单次最多500行，禁止重复SKU。" };
        return new String[] { "一行填写一个SKU，SKU必须已存在。", "实盘数量必须为大于等于0的整数。",
            "每一行都必须填写调整原因。", "单次最多500行，禁止重复SKU。" };
    }

    private CellStyle createHeaderStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBottomBorderColor(IndexedColors.DARK_BLUE.getIndex());
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private void setTemplateColumnWidths(Sheet data, String docType, String[] headers)
    {
        int[] widths;
        if ("PURCHASE_IN".equals(docType))
            widths = new int[] { 18, 28, 24, 18, 24, 12, 12, 16, 20 };
        else if ("SALES_OUT".equals(docType))
            widths = new int[] { 20, 12, 16, 16, 16, 16, 16, 16, 16 };
        else
            widths = new int[] { 20, 14, 36 };
        for (int i = 0; i < headers.length; i++)
            data.setColumnWidth(i, widths[Math.min(i, widths.length - 1)] * 256);
    }

    private void styleTemplateInputRows(Workbook workbook, Sheet data, String docType, String[] headers)
    {
        Font bodyFont = workbook.createFont();
        bodyFont.setFontName("微软雅黑");
        bodyFont.setFontHeightInPoints((short) 10);

        CellStyle textStyle = createInputStyle(workbook, bodyFont, HorizontalAlignment.LEFT);
        CellStyle centerStyle = createInputStyle(workbook, bodyFont, HorizontalAlignment.CENTER);
        CellStyle quantityStyle = createInputStyle(workbook, bodyFont, HorizontalAlignment.RIGHT);
        quantityStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        CellStyle moneyStyle = createInputStyle(workbook, bodyFont, HorizontalAlignment.RIGHT);
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        for (int rowIndex = 1; rowIndex <= 20; rowIndex++)
        {
            Row row = data.createRow(rowIndex);
            row.setHeightInPoints("PURCHASE_IN".equals(docType) ? 36 : 25);
            for (int column = 0; column < headers.length; column++)
            {
                Cell cell = row.createCell(column);
                String header = headers[column];
                if ("数量".equals(header) || "实盘数量".equals(header)) cell.setCellStyle(quantityStyle);
                else if ("采购单价".equals(header) || header.endsWith("/件") || "成交单价".equals(header))
                    cell.setCellStyle(moneyStyle);
                else if ("商品类型（新商品必填）".equals(header) || "规格类型（新商品必填）".equals(header)
                    || "单位".equals(header) || IMAGE_HEADER.equals(header)) cell.setCellStyle(centerStyle);
                else cell.setCellStyle(textStyle);
            }
        }
    }

    private CellStyle createInputStyle(Workbook workbook, Font font, HorizontalAlignment alignment)
    {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private void styleGuideSheet(Workbook workbook, Sheet guide, String docType)
    {
        guide.setDisplayGridlines(false);
        guide.setColumnWidth(0, 90 * 256);
        Row title = guide.createRow(0);
        title.setHeightInPoints(30);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("PURCHASE_IN".equals(docType) ? "采购入库模板填写说明" : "Excel导入模板填写说明");
        titleCell.setCellStyle(createHeaderStyle(workbook));

        Font guideFont = workbook.createFont();
        guideFont.setFontName("微软雅黑");
        guideFont.setFontHeightInPoints((short) 11);
        CellStyle guideStyle = createInputStyle(workbook, guideFont, HorizontalAlignment.LEFT);
        guideStyle.setWrapText(true);
        String[] guideRows = guide(docType);
        for (int i = 0; i < guideRows.length; i++)
        {
            Row row = guide.createRow(i + 1);
            row.setHeightInPoints(guideRows[i].length() > 40 ? 44 : 28);
            Cell cell = row.createCell(0);
            cell.setCellValue((i + 1) + ". " + guideRows[i]);
            cell.setCellStyle(guideStyle);
        }
    }

    private void requireSupported(String docType)
    {
        if (!SUPPORTED_TYPES.contains(docType)) throw new ServiceException("当前单据类型暂不支持Excel导入");
    }

    private Integer findHeader(String[] headers, String target)
    {
        for (int i = 0; i < headers.length; i++) if (target.equals(headers[i])) return i;
        return null;
    }

    private void addDropdownValidation(Workbook workbook, Sheet data, String[] headers, String headerName,
        String rangeName, String rangeFormula, String promptTitle, String promptText)
    {
        Integer column = findHeader(headers, headerName);
        if (column == null) return;
        Name options = workbook.createName();
        options.setNameName(rangeName);
        options.setRefersToFormula(rangeFormula);
        DataValidationHelper helper = data.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        CellRangeAddressList addressList = new CellRangeAddressList(1, MAX_ROWS, column, column);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        validation.setShowPromptBox(false);
        validation.createErrorBox(promptTitle + "不正确", promptText);
        data.addValidationData(validation);

    }

    private String normalizeProductType(String value)
    {
        if ("散件商品".equals(value) || "散件".equals(value) || "PART".equalsIgnoreCase(value)) return "PART";
        if ("成品商品".equals(value) || "成品".equals(value) || "FINISHED".equalsIgnoreCase(value)) return "FINISHED";
        if ("配件商品".equals(value) || "配件".equals(value) || "ACCESSORY".equalsIgnoreCase(value)) return "ACCESSORY";
        if ("福利商品".equals(value) || "福利".equals(value) || "WELFARE".equalsIgnoreCase(value)) return "WELFARE";
        return null;
    }

    private String normalizeSpecification(String value)
    {
        if ("精品".equals(value)) return "精品";
        if ("普通".equals(value)) return "普通";
        return null;
    }

    private String firstImage(String values)
    {
        if (values == null) return "";
        for (String value : values.split(",")) if (!value.trim().isEmpty()) return value.trim();
        return "";
    }

    private Map<Integer, EmbeddedImage> extractEmbeddedImages(XSSFSheet sheet, Integer imageColumn)
    {
        Map<Integer, EmbeddedImage> images = new HashMap<Integer, EmbeddedImage>();
        if (imageColumn == null) return images;
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing != null) for (XSSFShape shape : drawing.getShapes())
        {
            if (!(shape instanceof XSSFPicture)) continue;
            XSSFPicture picture = (XSSFPicture) shape;
            XSSFClientAnchor anchor = picture.getClientAnchor();
            if (anchor == null || anchor.getRow1() < 1) continue;
            int rowIndex = anchor.getRow1();
            if (anchor.getCol1() != imageColumn)
            {
                images.put(rowIndex, EmbeddedImage.error("图片必须放在该行的“商品图片”单元格中"));
                continue;
            }
            if (images.containsKey(rowIndex))
            {
                images.put(rowIndex, EmbeddedImage.error("每行只能插入一张商品图片"));
                continue;
            }
            XSSFPictureData pictureData = picture.getPictureData();
            images.put(rowIndex, validateEmbeddedImage(
                pictureData == null ? null : pictureData.getData(),
                pictureData == null ? "" : pictureData.suggestFileExtension()));
        }
        extractCellImages(sheet, imageColumn, images);
        return images;
    }

    private void extractCellImages(XSSFSheet sheet, int imageColumn, Map<Integer, EmbeddedImage> images)
    {
        try
        {
            PackagePart cellImagesPart = sheet.getWorkbook().getPackage().getPart(
                PackagingURIHelper.createPartName(CELL_IMAGES_PART));
            if (cellImagesPart == null) return;
            Map<String, EmbeddedImage> imagesById = readCellImages(cellImagesPart);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++)
            {
                Row row = sheet.getRow(rowIndex);
                Cell cell = row == null ? null : row.getCell(imageColumn);
                if (cell == null || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.FORMULA) continue;
                Matcher matcher = CELL_IMAGE_FORMULA.matcher(cell.getCellFormula());
                if (!matcher.find()) continue;
                if (images.containsKey(rowIndex))
                {
                    images.put(rowIndex, EmbeddedImage.error("每行只能插入一张商品图片"));
                    continue;
                }
                EmbeddedImage image = imagesById.get(matcher.group(1));
                images.put(rowIndex, image == null
                    ? EmbeddedImage.error("单元格图片数据不存在") : image);
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("解析Excel单元格图片失败：" + e.getMessage());
        }
    }

    private Map<String, EmbeddedImage> readCellImages(PackagePart cellImagesPart) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document;
        try (InputStream input = cellImagesPart.getInputStream())
        {
            document = factory.newDocumentBuilder().parse(input);
        }
        Map<String, EmbeddedImage> result = new HashMap<String, EmbeddedImage>();
        NodeList cellImages = document.getElementsByTagNameNS("*", "cellImage");
        for (int i = 0; i < cellImages.getLength(); i++)
        {
            Element cellImage = (Element) cellImages.item(i);
            Element properties = firstElement(cellImage, "cNvPr");
            Element blip = firstElement(cellImage, "blip");
            if (properties == null || blip == null) continue;
            String imageId = properties.getAttribute("name");
            String relationshipId = blip.getAttributeNS(RELATIONSHIP_NAMESPACE, "embed");
            PackageRelationship relationship = cellImagesPart.getRelationship(relationshipId);
            if (imageId.isEmpty() || relationship == null) continue;
            PackagePart imagePart = cellImagesPart.getRelatedPart(relationship);
            String filename = imagePart.getPartName().getName();
            String extension = filename.substring(filename.lastIndexOf('.') + 1);
            byte[] data;
            try (InputStream input = imagePart.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream())
            {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                data = output.toByteArray();
            }
            result.put(imageId, validateEmbeddedImage(data, extension));
        }
        return result;
    }

    private Element firstElement(Element parent, String localName)
    {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private EmbeddedImage validateEmbeddedImage(byte[] data, String extension)
    {
        if (!"png".equalsIgnoreCase(extension) && !"jpg".equalsIgnoreCase(extension)
            && !"jpeg".equalsIgnoreCase(extension) && !"webp".equalsIgnoreCase(extension))
            return EmbeddedImage.error("商品图片仅支持JPG、JPEG、PNG和WEBP");
        if (data == null || data.length == 0 || data.length > MAX_SOURCE_IMAGE_BYTES)
            return EmbeddedImage.error("商品图片不能为空且源图不能超过50MB");
        try
        {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
            if (source == null) return EmbeddedImage.error("商品图片内容无法识别");
            if (data.length > MAX_IMAGE_BYTES || source.getWidth() > MAX_IMAGE_DIMENSION
                || source.getHeight() > MAX_IMAGE_DIMENSION)
                return normalizeImage(source);
            return new EmbeddedImage(data,
                "jpeg".equalsIgnoreCase(extension) ? "jpg" : extension.toLowerCase(Locale.ROOT), null);
        }
        catch (Exception e)
        {
            return EmbeddedImage.error("商品图片内容无法识别");
        }
    }

    private EmbeddedImage normalizeImage(BufferedImage source) throws Exception
    {
        double scale = Math.min(1D, Math.min((double) MAX_IMAGE_DIMENSION / source.getWidth(),
            (double) MAX_IMAGE_DIMENSION / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        boolean hasAlpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(width, height,
            hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            if (!hasAlpha)
            {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }
        try
        {
            byte[] webp = encodeWebp(target);
            if (webp.length > MAX_IMAGE_BYTES) return EmbeddedImage.error("商品图片压缩后仍超过5MB");
            return new EmbeddedImage(webp, "webp", null);
        }
        catch (RuntimeException | LinkageError e)
        {
            return encodeJpegFallback(target);
        }
    }

    private byte[] encodeWebp(BufferedImage image)
    {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) throw new ServiceException("系统缺少WebP图片编码器");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output))
        {
            writer.setOutput(imageOutput);
            WebPWriteParam parameter = new WebPWriteParam(writer.getLocale());
            parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameter.setCompressionType("Lossy");
            parameter.setCompressionQuality(0.85F);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), parameter);
            imageOutput.flush();
            return output.toByteArray();
        }
        catch (java.io.IOException e)
        {
            throw new ServiceException("WebP图片编码失败：" + e.getMessage());
        }
        finally
        {
            writer.dispose();
        }
    }

    private EmbeddedImage encodeJpegFallback(BufferedImage source) throws Exception
    {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
            graphics.drawImage(source, 0, 0, null);
        }
        finally
        {
            graphics.dispose();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new ServiceException("系统缺少JPG图片编码器");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output))
        {
            writer.setOutput(imageOutput);
            ImageWriteParam parameter = writer.getDefaultWriteParam();
            if (parameter.canWriteCompressed())
            {
                parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameter.setCompressionQuality(0.88F);
            }
            writer.write(null, new javax.imageio.IIOImage(target, null, null), parameter);
            byte[] normalized = output.toByteArray();
            if (normalized.length > MAX_IMAGE_BYTES)
                return EmbeddedImage.error("商品图片压缩后仍超过5MB");
            return new EmbeddedImage(normalized, "jpg", null);
        }
        finally
        {
            writer.dispose();
        }
    }

    private String storeEmbeddedImage(EmbeddedImage image)
    {
        try
        {
            String profile = RuoYiConfig.getProfile();
            if (profile == null || profile.trim().isEmpty()) throw new ServiceException("系统上传目录未配置");
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path profileRoot = Paths.get(profile).toAbsolutePath().normalize();
            Path directory = profileRoot.resolve(Paths.get("jewelry", "import", datePath)).normalize();
            if (!directory.startsWith(profileRoot)) throw new ServiceException("商品图片保存路径不正确");
            Files.createDirectories(directory);
            String filename = sha256(image.data) + "." + image.extension;
            Path target = directory.resolve(filename);
            if (!Files.exists(target)) Files.write(target, image.data);
            return Constants.RESOURCE_PREFIX + "/jewelry/import/" + datePath + "/" + filename;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("保存Excel商品图片失败：" + e.getMessage());
        }
    }

    private String sha256(byte[] data) throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    private static class EmbeddedImage
    {
        private final byte[] data;
        private final String extension;
        private final String error;

        private EmbeddedImage(byte[] data, String extension, String error)
        {
            this.data = data;
            this.extension = extension;
            this.error = error;
        }

        private static EmbeddedImage error(String message)
        {
            return new EmbeddedImage(null, null, message);
        }
    }

    private String normalizeSku(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultString(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
    private int integer(Object value) { return value == null ? 0 : new BigDecimal(String.valueOf(value)).intValue(); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private String join(List<String> values) { return String.join("；", values); }
}
