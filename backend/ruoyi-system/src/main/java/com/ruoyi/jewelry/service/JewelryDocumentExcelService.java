package com.ruoyi.jewelry.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@Service
public class JewelryDocumentExcelService
{
    private static final int MAX_ROWS = 500;
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
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = data.createRow(0);
            for (int i = 0; i < headers.length; i++)
            {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                data.setColumnWidth(i, Math.max(14, headers[i].length() + 4) * 256);
            }
            data.createFreezePane(0, 1);

            Sheet guide = workbook.createSheet("填写说明");
            String[] guideRows = guide(docType);
            for (int i = 0; i < guideRows.length; i++)
            {
                guide.createRow(i).createCell(0).setCellValue(guideRows[i]);
            }
            guide.setColumnWidth(0, 90 * 256);
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
            row.put("category", value(source, columns, "分类", formatter, evaluator).trim());
            row.put("specification", value(source, columns, "规格", formatter, evaluator).trim());
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
            return new String[] { "SKU", "商品名称（新商品必填）", "分类", "规格", "单位", "数量", "采购单价" };
        if ("SALES_OUT".equals(docType))
            return new String[] { "SKU", "数量", "成交单价", "包装费/件", "物流费/件", "鉴定费/件" };
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
                "新SKU必须填写商品名称；确认导入时系统会先创建商品档案。", "单次最多500行，禁止重复SKU。" };
        if ("SALES_OUT".equals(docType))
            return new String[] { "一行填写一个SKU，SKU必须已存在。", "销售数量不能超过当前可用库存。",
                "费用均按每件填写，未发生费用时填写0。", "单次最多500行，禁止重复SKU。" };
        return new String[] { "一行填写一个SKU，SKU必须已存在。", "实盘数量必须为大于等于0的整数。",
            "每一行都必须填写调整原因。", "单次最多500行，禁止重复SKU。" };
    }

    private void requireSupported(String docType)
    {
        if (!SUPPORTED_TYPES.contains(docType)) throw new ServiceException("当前单据类型暂不支持Excel导入");
    }

    private String normalizeSku(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultString(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
    private int integer(Object value) { return value == null ? 0 : new BigDecimal(String.valueOf(value)).intValue(); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private String join(List<String> values) { return String.join("；", values); }
}
