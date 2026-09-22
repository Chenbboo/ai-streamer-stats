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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@Service
public class JewelryInfluencerBindingExcelService
{
    private static final String[] HEADERS = { "SKU", "商品类型", "直播成交价", "达人佣金率(%)",
        "平台扣点率(%)", "税率(%)", "包装费", "物流费", "鉴定费", "备注" };
    private static final String[] FIELDS = { "fixedUnitPrice", "commissionPercent", "platformPercent",
        "taxPercent", "packFee", "shipFee", "certFee" };
    @Autowired private JewelryErpMapper mapper;

    public void writeTemplate(HttpServletResponse response) throws IOException
    {
        try (Workbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("达人商品绑定");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++)
            {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, i == 9 ? 6500 : 4300);
            }
            Sheet help = workbook.createSheet("填写说明");
            help.setColumnWidth(0, 20000);
            String[] notes = { "从「达人商品绑定」工作表第2行开始填写商品，每行一个商品。",
                "商品类型只填写：成品商品。配件商品请在搭售配置中选择。",
                "同一SKU可对应不同商品类型，导入时通过SKU和商品类型共同定位商品。",
                "直播成交价必须大于0；费率填写百分数，例如20表示20%，三项费率合计须小于100%。",
                "包装费、物流费、鉴定费不填写时按0处理；导入同一达人已绑定商品会覆盖其当前配置。" };
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
        if (file.getSize() > 5 * 1024 * 1024) throw new ServiceException("Excel文件不能超过5MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new ServiceException("仅支持.xls或.xlsx文件");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() > 2000) throw new ServiceException("最多导入2000行");
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            for (int i = 0; i < HEADERS.length; i++)
                if (header == null || !HEADERS[i].equals(value(header.getCell(i), formatter)))
                    throw new ServiceException("Excel表头不匹配，请下载最新模板");
            List<Map<String, Object>> result = new ArrayList<>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++)
            {
                Row row = sheet.getRow(n);
                if (row == null) continue;
                boolean empty = true;
                for (int i = 0; i < HEADERS.length; i++)
                    if (!value(row.getCell(i), formatter).isEmpty()) { empty = false; break; }
                if (empty) continue;
                Map<String, Object> item = new HashMap<>();
                String sku = value(row.getCell(0), formatter);
                String typeName = value(row.getCell(1), formatter);
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
                for (int i = 0; i < FIELDS.length; i++)
                    item.put(FIELDS[i], value(row.getCell(i + 2), formatter).replace(",", ""));
                item.put("bindingStatus", "0");
                item.put("bindingRemark", value(row.getCell(9), formatter));
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
        for (int index = 0; index < rows.size(); index++)
        {
            Map<String, Object> item = rows.get(index);
            List<String> errors = new ArrayList<>();
            item.put("errors", errors);
            String sku = string(item.get("sku"));
            String type = productType(string(item.get("productType")));
            if (sku.isEmpty()) errors.add("SKU不能为空");
            if (type.isEmpty()) errors.add("商品类型不正确，请选择商品类型");
            else if (!"FINISHED".equals(type)) errors.add("达人商品绑定只支持成品商品，配件商品请在搭售配置中选择");
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
                if (!sku.isEmpty() && !type.isEmpty() && errors.isEmpty())
                    errors.add("SKU与商品类型未匹配到商品档案，请检查后重试");
                item.remove("productId");
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
                if (!seen.add(productId)) errors.add("同一商品在本次导入中重复");
                if (influencerId != null)
                {
                    Map<String, Object> binding = mapper.selectInfluencerProductPrice(influencerId, productId);
                    if (binding != null && "PENDING".equals(string(binding.get("priceStatus"))))
                        errors.add("该商品价格正在销售草稿中待生效");
                }
            }
            BigDecimal price = number(item.get("fixedUnitPrice"), "直播成交价", errors);
            if (price != null && price.signum() <= 0) errors.add("直播成交价必须大于0");
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
            if (string(item.get("bindingRemark")).length() > 500) errors.add("备注不能超过500字");
            String status = string(item.get("bindingStatus"));
            if (!"0".equals(status) && !"1".equals(status)) errors.add("绑定状态不正确");
            item.putIfAbsent("excelRow", index + 2);
        }
        return rows;
    }

    private String string(Object value) { return value == null ? "" : value.toString().trim(); }

    private BigDecimal number(Object value, String label, List<String> errors)
    {
        String raw = string(value);
        if (raw.isEmpty())
        {
            if ("直播成交价".equals(label)) errors.add(label + "不能为空");
            return "直播成交价".equals(label) ? null : BigDecimal.ZERO;
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
            case "ACCESSORY": case "配件商品": case "配件": return "ACCESSORY";
            case "PART": case "散件商品": case "散件": return "PART";
            case "WELFARE": case "福利商品": case "福利": return "WELFARE";
            default: return "";
        }
    }
}
