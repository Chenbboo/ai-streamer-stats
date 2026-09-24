package com.ruoyi.jewelry.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@Service
public class JewelryInfluencerBindingExcelService
{
    private static final String[] HEADERS = { "商品SKU", "商品名称", "商品类型", "供应商名称", "直播成交价", "商品成本价",
        "采购单价", "达人佣金率(%)", "平台扣点率(%)", "税率(%)", "包装费", "物流费", "鉴定费", "单位", "图片", "备注" };
    private static final String[] LEGACY_HEADERS = { "商品SKU", "商品类型", "直播成交价", "商品成本价", "达人佣金率(%)",
        "平台扣点率(%)", "税率(%)", "包装费", "物流费", "鉴定费", "备注",
        "商品名称", "单位", "图片地址", "供应商名称", "采购单价" };
    private static final String[] FIELDS = { "fixedUnitPrice", "unitCost", "commissionPercent", "platformPercent",
        "taxPercent", "packFee", "shipFee", "certFee" };
    @Autowired private JewelryErpMapper mapper;
    @Autowired private JewelryDocumentExcelService documentExcelService;

    public void writeTemplate(HttpServletResponse response) throws IOException
    {
        try (Workbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("达人商品绑定");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++)
            {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, i == 1 || i == 14 || i == 15 ? 6500 : 4300);
            }
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            DataValidationConstraint productTypes = validationHelper.createExplicitListConstraint(
                new String[] { "成品商品", "赠品商品" });
            DataValidation typeValidation = validationHelper.createValidation(productTypes,
                new CellRangeAddressList(1, 2000, 2, 2));
            typeValidation.setShowErrorBox(true);
            sheet.addValidationData(typeValidation);
            Sheet help = workbook.createSheet("填写说明");
            help.setColumnWidth(0, 20000);
            String[] notes = { "从「达人商品绑定」工作表第2行开始填写商品，每行一个商品。",
                "商品类型可填写成品商品或赠品商品；其他类型不能在达人档案绑定。",
                "同一SKU可对应不同商品类型，导入时通过SKU和商品类型共同定位商品。",
                "成品直播成交价必须大于0，赠品可为0；商品成本价必填且不能小于0。",
                "费率填写百分数，例如20表示20%，三项费率合计须小于100%。",
                "包装费、物流费、鉴定费不填写时按0处理；导入同一达人已绑定商品会覆盖其当前配置。",
                "新SKU填写商品名称和单位后，将按所填类型自动新建商品档案。已有SKU默认只更新该达人的绑定；在确认表修改图片时会同步更新共用商品档案。",
                "供应商名称填写供应商档案中的完整名称；名称不存在、重名或停用时可在导入预览中修改。",
                "图片列请直接插入或粘贴一张图片；也可在导入确认表中上传图片，无需填写图片地址。",
                "供应商和采购单价会在采购入库时带入；实际采购价以单据填写为准。" };
            for (int i = 0; i < notes.length; i++) help.createRow(i).createCell(0).setCellValue(notes[i]);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String name = URLEncoder.encode("达人商品绑定模板.xlsx", StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + name);
            workbook.write(response.getOutputStream());
        }
    }

    public List<Map<String, Object>> parse(MultipartFile file) throws IOException
    {
        List<Map<String, Object>> rows = preview(file, null);
        for (Map<String, Object> row : rows)
        {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) row.get("errors");
            if (!errors.isEmpty())
                throw new ServiceException("Excel第" + row.get("excelRow") + "行：" + String.join("；", errors));
        }
        return rows;
    }

    public List<Map<String, Object>> preview(MultipartFile file, Long influencerId) throws IOException
    {
        if (file == null || file.isEmpty()) throw new ServiceException("请选择Excel文件");
        if (file.getSize() > 20 * 1024 * 1024) throw new ServiceException("Excel文件不能超过20MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new ServiceException("仅支持.xls或.xlsx文件");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() > 2000) throw new ServiceException("最多导入2000行");
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            boolean current = header != null && "商品名称".equals(value(header.getCell(1), formatter));
            if (current)
            {
                for (int i = 0; i < HEADERS.length; i++)
                    if (!HEADERS[i].equals(value(header.getCell(i), formatter)))
                        throw new ServiceException("Excel表头不匹配，请下载最新模板");
            }
            else
            {
                for (int i = 0; i < 11; i++)
                    if (header == null || !(LEGACY_HEADERS[i].equals(value(header.getCell(i), formatter))
                        || (i == 0 && "SKU".equals(value(header.getCell(i), formatter)))))
                        throw new ServiceException("Excel表头不匹配，请下载最新模板");
            }
            boolean extended = !current && header != null && LEGACY_HEADERS[11].equals(value(header.getCell(11), formatter));
            boolean legacyExtended = extended && "商品分类".equals(value(header.getCell(12), formatter))
                && "规格类型".equals(value(header.getCell(13), formatter));
            String supplierHeader = extended ? value(header.getCell(14 + (legacyExtended ? 2 : 0)), formatter) : "";
            boolean legacySupplierId = "供应商ID".equals(supplierHeader) || "常用供应商ID".equals(supplierHeader);
            if (extended)
                for (int i = 11; i < LEGACY_HEADERS.length; i++)
                {
                    String name = value(header.getCell(i + (legacyExtended && i >= 12 ? 2 : 0)), formatter);
                    boolean previousName = (i == 14 && ("供应商ID".equals(name) || "常用供应商ID".equals(name)))
                        || (i == 15 && "参考采购单价".equals(name));
                    if (!LEGACY_HEADERS[i].equals(name) && !previousName)
                        throw new ServiceException("Excel表头不匹配，请下载最新模板");
                }
            Map<Integer, Map<String, String>> pictures = current && sheet instanceof XSSFSheet
                ? documentExcelService.importProductImages((XSSFSheet) sheet, 14) : null;
            if (pictures == null) pictures = java.util.Collections.emptyMap();
            List<Map<String, Object>> result = new ArrayList<>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++)
            {
                Row row = sheet.getRow(n);
                if (row == null) continue;
                boolean empty = true;
                for (int i = 0; i < row.getLastCellNum(); i++)
                {
                    if (current && i == 14) continue;
                    if (!value(row.getCell(i), formatter).isEmpty()) { empty = false; break; }
                }
                if (empty && !pictures.containsKey(n)) continue;
                Map<String, Object> item = new HashMap<>();
                String sku = value(row.getCell(0), formatter);
                String typeName = value(row.getCell(current ? 2 : 1), formatter);
                String type = productType(typeName);
                item.put("excelRow", n + 1);
                item.put("excelSku", sku);
                item.put("excelProductTypeName", typeName);
                item.put("sku", sku);
                item.put("productTypeName", typeName);
                item.put("productType", type);
                if (!sku.isEmpty() && !type.isEmpty())
                {
                    Map<String, Object> product = mapper.selectProductBySkuAndType(sku, type);
                    if (product != null)
                    {
                        item.put("productId", product.get("productId"));
                        item.put("productName", product.get("productName"));
                    }
                }
                item.put("bindingStatus", "0");
                if (current)
                {
                    item.put("productName", value(row.getCell(1), formatter));
                    item.put("preferredSupplierName", value(row.getCell(3), formatter));
                    item.put("excelSupplierName", item.get("preferredSupplierName"));
                    String[] moneyFields = { "fixedUnitPrice", "unitCost", "referencePurchasePrice",
                        "commissionPercent", "platformPercent", "taxPercent", "packFee", "shipFee", "certFee" };
                    for (int i = 0; i < moneyFields.length; i++)
                        item.put(moneyFields[i], value(row.getCell(i + 4), formatter).replace(",", ""));
                    item.put("unit", value(row.getCell(13), formatter));
                    item.put("bindingRemark", value(row.getCell(15), formatter));
                    Map<String, String> picture = pictures.get(n);
                    if (picture != null)
                    {
                        item.putAll(picture);
                        item.put("imageChanged", true);
                    }
                    Cell imageCell = row.getCell(14);
                    if (imageCell != null && imageCell.getCellType() != org.apache.poi.ss.usermodel.CellType.FORMULA
                        && !value(imageCell, formatter).isEmpty())
                        item.put("imageError", "图片列请直接插入图片，不要填写图片地址");
                }
                else
                {
                    for (int i = 0; i < FIELDS.length; i++)
                        item.put(FIELDS[i], value(row.getCell(i + 2), formatter).replace(",", ""));
                    item.put("bindingRemark", value(row.getCell(10), formatter));
                    if (extended)
                    {
                        String[] extraFields = {"productName", "unit", "imageUrls",
                            "preferredSupplierName", "referencePurchasePrice"};
                        for (int i = 0; i < extraFields.length; i++)
                        {
                            String field = i == 3 && legacySupplierId ? "preferredSupplierId" : extraFields[i];
                            String cellValue = value(row.getCell(11 + i + (legacyExtended && i >= 1 ? 2 : 0)), formatter);
                            item.put(field, cellValue);
                            if ("imageUrls".equals(field) && !cellValue.isEmpty()) item.put("imageChanged", true);
                            if (i == 3 && !legacySupplierId) item.put("excelSupplierName", cellValue);
                        }
                    }
                }
                result.add(item);
            }
            if (result.isEmpty()) throw new ServiceException("Excel中没有可导入的商品");
            return validateRows(influencerId, result);
        }
        catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw new ServiceException("Excel解析失败，请检查文件格式"); }
    }

    public List<Map<String, Object>> validateRows(Long influencerId, List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty()) throw new ServiceException("没有可导入的商品");
        if (rows.size() > 2000) throw new ServiceException("最多导入2000行");
        Set<Long> seen = new HashSet<>();
        Set<String> seenSkus = new HashSet<>();
        Map<String, List<Map<String, Object>>> suppliersByName = new HashMap<>();
        Map<String, Boolean> supplierCodesExist = new HashMap<>();
        Map<String, String> newSupplierSignatures = new HashMap<>();
        Map<String, String> newSupplierNameCodes = new HashMap<>();
        for (int index = 0; index < rows.size(); index++)
        {
            Map<String, Object> item = rows.get(index);
            List<String> errors = new ArrayList<>();
            item.put("errors", errors);
            if (!string(item.get("imageError")).isEmpty()) errors.add(string(item.get("imageError")));
            boolean imageChanged = Boolean.TRUE.equals(item.get("imageChanged"))
                || "true".equalsIgnoreCase(string(item.get("imageChanged")));
            String imageUrls = string(item.get("imageUrls"));
            if (imageChanged && imageUrls.length() > 500) errors.add("商品图片信息过长");
            if (imageChanged && imageUrls.contains(",")) errors.add("每个商品只支持一张实物图片");
            item.put("imageChanged", imageChanged);
            String sku = string(item.get("sku"));
            String type = productType(string(item.get("productType")));
            if (sku.isEmpty()) errors.add("SKU不能为空");
            if (sku.length() > 64) errors.add("商品SKU不能超过64个字符");
            if (type.isEmpty()) errors.add("商品类型不正确，请选择商品类型");
            else if (!"FINISHED".equals(type) && !"GIFT".equals(type))
                errors.add("达人商品绑定只支持成品商品或赠品商品");
            Object selectedId = item.get("productId");
            Map<String, Object> product = null;
            if (selectedId != null && !selectedId.toString().trim().isEmpty())
            {
                try { product = mapper.selectProductById(Long.parseLong(selectedId.toString())); }
                catch (NumberFormatException ex) { errors.add("商品选择不正确"); }
            }
            else if (!sku.isEmpty() && !type.isEmpty())
                product = mapper.selectProductBySkuAndType(sku, type);
            if (product == null)
            {
                item.remove("productId");
                if (!sku.isEmpty() && ("FINISHED".equals(type) || "GIFT".equals(type)))
                {
                    if (string(item.get("productName")).isEmpty()) errors.add("新商品须填写商品名称");
                    if (string(item.get("productName")).length() > 128) errors.add("商品名称不能超过128个字符");
                    if (string(item.get("unit")).isEmpty()) errors.add("新商品须填写单位");
                    if (string(item.get("unit")).length() > 16) errors.add("单位不能超过16个字符");
                    if (string(item.get("imageUrls")).length() > 500) errors.add("商品图片信息过长");
                    if (!seenSkus.add(type + ":" + sku.toUpperCase(java.util.Locale.ROOT)))
                        errors.add("本次导入中商品SKU和类型重复");
                }
            }
            else if (!"0".equals(string(product.get("status"))))
                errors.add("商品已停用，请选择启用的商品");
            else if ((product.get("sku") != null && !sku.equalsIgnoreCase(string(product.get("sku"))))
                || (product.get("productType") != null && !type.equals(string(product.get("productType")))))
                errors.add("所选商品与SKU或商品类型不一致");
            else
            {
                Long productId = ((Number) product.get("productId")).longValue();
                item.put("productId", productId);
                item.put("sku", product.get("sku") == null ? item.get("sku") : product.get("sku"));
                item.put("productName", product.get("productName"));
                item.put("productType", product.get("productType") == null ? item.get("productType") : product.get("productType"));
                if (product.get("unit") != null) item.put("unit", product.get("unit"));
                if (!imageChanged)
                    item.put("imageUrls", product.get("imageUrls") == null ? "" : product.get("imageUrls"));
                if (!seen.add(productId)) errors.add("同一商品在本次导入中重复");
                if (influencerId != null)
                {
                    Map<String, Object> binding = mapper.selectInfluencerProductPrice(influencerId, productId);
                    if (binding != null && "PENDING".equals(string(binding.get("priceStatus"))))
                        errors.add("该商品价格正在销售草稿中待生效");
                }
            }
            BigDecimal price = number(item.get("fixedUnitPrice"), "直播成交价", errors);
            if (price != null && (price.signum() < 0 || ("FINISHED".equals(type) && price.signum() == 0)))
                errors.add("成品直播成交价必须大于0，赠品不能小于0");
            BigDecimal unitCost = number(item.get("unitCost"), "商品成本价", errors);
            if (unitCost != null && unitCost.signum() < 0) errors.add("商品成本价不能小于0");
            BigDecimal total = BigDecimal.ZERO;
            for (String[] field : new String[][] { {"commissionPercent", "达人佣金率"}, {"platformPercent", "平台扣点率"}, {"taxPercent", "税率"} })
            {
                BigDecimal rate = number(item.get(field[0]), field[1], errors);
                if (rate != null)
                {
                    if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0)
                        errors.add(field[1] + "必须在0%到100%之间");
                    total = total.add(rate);
                }
            }
            if (total.compareTo(new BigDecimal("100")) >= 0) errors.add("佣金、平台扣点和税率合计必须小于100%");
            for (String[] field : new String[][] { {"packFee", "包装费"}, {"shipFee", "物流费"}, {"certFee", "鉴定费"} })
            {
                BigDecimal fee = number(item.get(field[0]), field[1], errors);
                if (fee != null && fee.signum() < 0) errors.add(field[1] + "不能小于0");
            }
            BigDecimal purchasePrice = number(item.get("referencePurchasePrice"), "采购单价", errors);
            if (purchasePrice != null && purchasePrice.signum() < 0) errors.add("采购单价不能小于0");
            String supplierId = string(item.get("preferredSupplierId"));
            String supplierName = string(item.get("preferredSupplierName"));
            if (item.get("newSupplier") != null)
                validateNewSupplier(item, errors, suppliersByName, supplierCodesExist,
                    newSupplierSignatures, newSupplierNameCodes);
            else if (!supplierId.isEmpty())
            {
                try
                {
                    Map<String, Object> supplier = mapper.selectSupplierById(Long.parseLong(supplierId));
                    if (supplier == null || !"0".equals(string(supplier.get("status"))))
                        errors.add("供应商不存在或已停用");
                    else if (!supplierName.isEmpty() && !supplierName.equals(string(supplier.get("supplierName"))))
                        errors.add("供应商名称与所选供应商不一致");
                    else item.put("preferredSupplierName", supplier.get("supplierName"));
                }
                catch (NumberFormatException ex) { errors.add("供应商ID必须是数字"); }
            }
            else if (!supplierName.isEmpty())
            {
                List<Map<String, Object>> matches = suppliersByName.computeIfAbsent(supplierName, name -> {
                    List<Map<String, Object>> found = mapper.selectSuppliersByName(name);
                    return found == null ? java.util.Collections.emptyList() : found;
                });
                int activeCount = 0;
                Map<String, Object> matched = null;
                for (Map<String, Object> supplier : matches)
                    if ("0".equals(string(supplier.get("status")))) { activeCount++; matched = supplier; }
                if (matches.isEmpty()) errors.add("供应商名称不存在：" + supplierName);
                else if (activeCount == 0) errors.add("供应商已停用：" + supplierName);
                else if (activeCount > 1) errors.add("供应商名称重复，请在表格中选择具体供应商：" + supplierName);
                else
                {
                    item.put("preferredSupplierId", matched.get("supplierId"));
                    item.put("preferredSupplierName", matched.get("supplierName"));
                }
            }
            if (string(item.get("bindingRemark")).length() > 500) errors.add("备注不能超过500字");
            String status = string(item.get("bindingStatus"));
            if (!"0".equals(status) && !"1".equals(status)) errors.add("绑定状态不正确");
            item.putIfAbsent("excelRow", index + 2);
        }
        return rows;
    }

    private void validateNewSupplier(Map<String, Object> item, List<String> errors,
        Map<String, List<Map<String, Object>>> suppliersByName, Map<String, Boolean> supplierCodesExist,
        Map<String, String> newSupplierSignatures, Map<String, String> newSupplierNameCodes)
    {
        if (!(item.get("newSupplier") instanceof Map))
        {
            errors.add("新增供应商信息不正确");
            return;
        }
        Map<?, ?> draft = (Map<?, ?>) item.get("newSupplier");
        String code = string(draft.get("supplierCode"));
        String name = string(draft.get("supplierName"));
        String contactName = string(draft.get("contactName"));
        String contactPhone = string(draft.get("contactPhone"));
        String settlementType = string(draft.get("settlementType"));
        String address = string(draft.get("address"));
        if (!string(item.get("preferredSupplierId")).isEmpty())
            errors.add("请选择已有供应商或新增供应商，不能同时填写");
        if (code.isEmpty() || name.isEmpty()) errors.add("新增供应商须填写编码和名称");
        if (code.length() > 32 || name.length() > 128 || contactName.length() > 64
            || contactPhone.length() > 32 || settlementType.length() > 64 || address.length() > 255)
            errors.add("新增供应商字段超过允许长度");
        if (code.isEmpty() || name.isEmpty()) return;
        item.put("preferredSupplierName", name);
        String codeKey = code.toUpperCase(java.util.Locale.ROOT);
        String nameKey = name.toUpperCase(java.util.Locale.ROOT);
        if (supplierCodesExist.computeIfAbsent(codeKey, key -> mapper.selectSupplierByCode(code) != null))
            errors.add("供应商编码已存在，请选择已有供应商：" + code);
        List<Map<String, Object>> matches = suppliersByName.computeIfAbsent(name, key -> {
            List<Map<String, Object>> found = mapper.selectSuppliersByName(key);
            return found == null ? java.util.Collections.emptyList() : found;
        });
        if (matches.stream().anyMatch(supplier -> "0".equals(string(supplier.get("status")))))
            errors.add("供应商名称已存在，请选择已有供应商：" + name);
        String signature = String.join("\u0000", name, contactName, contactPhone, settlementType, address);
        String previous = newSupplierSignatures.putIfAbsent(codeKey, signature);
        if (previous != null && !previous.equals(signature))
            errors.add("同一供应商编码在本次导入中的信息不一致：" + code);
        String previousCode = newSupplierNameCodes.putIfAbsent(nameKey, codeKey);
        if (previousCode != null && !previousCode.equals(codeKey))
            errors.add("同一供应商名称在本次导入中使用了不同编码：" + name);
    }

    private String string(Object value) { return value == null ? "" : value.toString().trim(); }

    private BigDecimal number(Object value, String label, List<String> errors)
    {
        String raw = string(value);
        if (raw.isEmpty())
        {
            boolean required = "直播成交价".equals(label) || "商品成本价".equals(label);
            if (required) errors.add(label + "不能为空");
            return required ? null : BigDecimal.ZERO;
        }
        try { return new BigDecimal(raw); }
        catch (NumberFormatException ex) { errors.add(label + "必须是数字"); return null; }
    }

    private String value(Cell cell, DataFormatter formatter)
    {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String productType(String type)
    {
        switch (type)
        {
            case "FINISHED": case "成品商品": case "成品": return "FINISHED";
            case "SAMPLE": case "样品商品": case "样品": return "SAMPLE";
            case "GIFT": case "赠品商品": case "赠品": return "GIFT";
            case "ACCESSORY": case "配件商品": case "配件": return "ACCESSORY";
            case "PART": case "散件商品": case "散件": return "PART";
            case "WELFARE": case "福利商品": case "福利": return "WELFARE";
            default: return "";
        }
    }
}
