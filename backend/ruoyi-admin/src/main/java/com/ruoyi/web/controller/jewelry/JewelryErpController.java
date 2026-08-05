package com.ruoyi.web.controller.jewelry;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;
import com.ruoyi.jewelry.service.JewelryDocumentExcelService;
import com.ruoyi.jewelry.service.IJewelryErpService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/jewelry")
public class JewelryErpController extends BaseController
{
    @Autowired private IJewelryErpService service;
    @Autowired private JewelryErpMapper mapper;
    @Autowired private ISysUserService userService;
    @Autowired private JewelryDocumentExcelService documentExcelService;

    @PreAuthorize("@ss.hasPermi('jewelry:overview:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        Map<String, Object> data = service.dashboard();
        if (isMakerOnly())
        {
            data.remove("stockAmount");
            data.remove("monthPurchase");
            data.remove("monthSales");
            data.remove("monthProfit");
        }
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:staff:list')")
    @GetMapping("/staff/list")
    public TableDataInfo staffList(@RequestParam Map<String, Object> query)
    {
        startPage();
        return getDataTable(mapper.selectStaffList(query));
    }

    @PreAuthorize("@ss.hasPermi('jewelry:staff:add')")
    @PostMapping("/staff")
    @Transactional
    public AjaxResult addStaff(@RequestBody Map<String, Object> body)
    {
        String userName = string(body.get("userName"));
        String realName = string(body.get("realName"));
        String password = string(body.get("password"));
        String roleKey = string(body.get("roleKey"));
        if (userName.isEmpty() || realName.isEmpty()) return error("登录账号和姓名不能为空");
        if (password.length() < 6) return error("初始密码至少6位");
        if (!validRole(roleKey)) return error("ERP角色不正确");
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setNickName(realName);
        user.setPhonenumber(string(body.get("phone")));
        user.setPassword(SecurityUtils.encryptPassword(password));
        user.setStatus("0");
        user.setCreateBy(SecurityUtils.getUsername());
        if (!userService.checkUserNameUnique(user)) return error("登录账号已存在");
        userService.insertUser(user);
        Long roleId = mapper.selectRoleIdByKey(roleKey);
        if (roleId == null) return error("ERP角色尚未初始化");
        userService.insertUserAuth(user.getUserId(), new Long[] { roleId });
        body.put("userId", user.getUserId());
        body.put("status", defaultString(body.get("status"), "0"));
        body.put("createBy", SecurityUtils.getUsername());
        mapper.insertStaff(body);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:staff:edit')")
    @PutMapping("/staff")
    @Transactional
    public AjaxResult editStaff(@RequestBody Map<String, Object> body)
    {
        String roleKey = string(body.get("roleKey"));
        if (!validRole(roleKey)) return error("ERP角色不正确");
        Long staffId = number(body.get("staffId"));
        if (staffId == null) return error("ERP人员ID不能为空");
        Map<String, Object> existing = mapper.selectStaffById(staffId);
        if (existing == null) return error("ERP人员不存在");
        Long userId = number(existing.get("userId"));
        Long requestedUserId = number(body.get("userId"));
        if (requestedUserId != null && !requestedUserId.equals(userId))
            return error("ERP人员与登录账号绑定关系不一致");
        Long roleId = mapper.selectRoleIdByKey(roleKey);
        if (roleId == null) return error("ERP角色尚未初始化");
        body.put("updateBy", SecurityUtils.getUsername());
        int rows = mapper.updateStaff(body);
        if (rows != 1) return error("ERP人员信息已变化，请刷新后重试");
        mapper.deleteJewelryRolesByUserId(userId);
        mapper.insertUserRole(userId, roleId);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setStatus(defaultString(body.get("status"), "0"));
        userService.updateUserStatus(user);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:product:list')")
    @GetMapping("/product/list")
    public TableDataInfo productList(@RequestParam Map<String, Object> query)
    {
        startPage();
        List<Map<String, Object>> rows = service.listProducts(query);
        if (isMakerOnly()) removeKeys(rows, "avgCost");
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:product:list')")
    @GetMapping("/product/options")
    public AjaxResult productOptions(@RequestParam Map<String, Object> query)
    {
        List<Map<String, Object>> rows = service.listProducts(query);
        if (isMakerOnly()) removeKeys(rows, "avgCost");
        return success(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('jewelry:product:add,jewelry:product:edit')")
    @PostMapping("/product")
    public AjaxResult saveProduct(@RequestBody Map<String, Object> body)
    {
        if (isMakerOnly()) return error("制单员只能在组装单内新建目标成品");
        boolean editing = body.get("productId") != null;
        if (editing && !hasPermission("jewelry:product:edit")) return error("无权修改已有商品档案");
        if (!editing && !hasPermission("jewelry:product:add")) return error("无权新增商品档案");
        if (string(body.get("sku")).isEmpty() || string(body.get("productName")).isEmpty())
            return error("SKU和商品名称不能为空");
        String productType = defaultString(body.get("productType"), "FINISHED");
        if (!Arrays.asList("PART", "FINISHED").contains(productType)) return error("商品类型不正确");
        body.put("productType", productType);
        body.put("imageUrl", string(body.get("imageUrl")));
        body.put("imageUrls", string(body.get("imageUrls")));
        body.put(editing ? "updateBy" : "createBy", SecurityUtils.getUsername());
        body.put("status", defaultString(body.get("status"), "0"));
        return toAjax(service.saveProduct(body));
    }

    @PreAuthorize("@ss.hasPermi('jewelry:supplier:list')")
    @GetMapping("/supplier/list")
    public TableDataInfo supplierList(@RequestParam Map<String, Object> query)
    {
        startPage();
        return getDataTable(service.listSuppliers(query));
    }

    @PreAuthorize("@ss.hasAnyPermi('jewelry:supplier:add,jewelry:supplier:edit')")
    @PostMapping("/supplier")
    public AjaxResult saveSupplier(@RequestBody Map<String, Object> body)
    {
        boolean editing = body.get("supplierId") != null;
        if (editing && !hasPermission("jewelry:supplier:edit")) return error("无权修改已有供应商档案");
        if (!editing && !hasPermission("jewelry:supplier:add")) return error("无权新增供应商档案");
        if (string(body.get("supplierCode")).isEmpty() || string(body.get("supplierName")).isEmpty())
            return error("供应商编码和名称不能为空");
        body.put(editing ? "updateBy" : "createBy", SecurityUtils.getUsername());
        body.put("status", defaultString(body.get("status"), "0"));
        return toAjax(service.saveSupplier(body));
    }

    @PreAuthorize("@ss.hasPermi('jewelry:stock:list')")
    @GetMapping("/stock/list")
    public TableDataInfo stockList(@RequestParam Map<String, Object> query)
    {
        if (!"true".equalsIgnoreCase(string(query.get("warningOnly"))))
        {
            query.remove("warningOnly");
            query.remove("warningType");
        }
        else if (!Arrays.asList("quantity", "age").contains(string(query.get("warningType"))))
        {
            query.remove("warningType");
        }
        startPage();
        List<Map<String, Object>> rows = service.listStock(query);
        if (isMakerOnly()) removeKeys(rows, "avgCost", "stockAmount");
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:stock:list')")
    @GetMapping("/stock/transactions")
    public TableDataInfo transactions(@RequestParam Map<String, Object> query)
    {
        startPage();
        List<Map<String, Object>> rows = service.listTransactions(query);
        if (isMakerOnly()) removeKeys(rows, "beforeAvgCost", "afterAvgCost");
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:stock:list')")
    @GetMapping("/stock/warning-days")
    public AjaxResult stockWarningDays()
    {
        return success(service.getStockWarningDays());
    }

    @PreAuthorize("@ss.hasPermi('jewelry:stock:config')")
    @PutMapping("/stock/warning-days")
    public AjaxResult updateStockWarningDays(@RequestBody Map<String, Object> body)
    {
        Long days = number(body.get("days"));
        if (days == null) return error("请输入库存时间预警天数");
        service.setStockWarningDays(days.intValue(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:list')")
    @GetMapping("/document/list")
    public TableDataInfo documentList(JewelryDocument query)
    {
        if (isMakerOnly())
        {
            query.setCreatorUserId(SecurityUtils.getUserId());
        }
        startPage();
        List<JewelryDocument> rows = service.listDocuments(query);
        if (isMakerOnly()) redactDocumentFinance(rows);
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:add')")
    @GetMapping("/document/import-template")
    public void documentImportTemplate(@RequestParam String docType, HttpServletResponse response) throws Exception
    {
        byte[] content = documentExcelService.createTemplate(docType);
        String fileName = URLEncoder.encode("珠宝单据导入模板-" + docType + ".xlsx", "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:add')")
    @PostMapping("/document/import-preview")
    public AjaxResult documentImportPreview(@RequestParam String docType,
        @RequestParam("file") MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty()) return error("请选择Excel文件");
        if (file.getSize() > 200L * 1024 * 1024) return error("Excel文件不能超过200MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) return error("仅支持xls和xlsx文件");
        if ("PURCHASE_IN".equals(docType) && !name.endsWith(".xlsx"))
            return error("采购入库模板包含商品图片，仅支持xlsx文件");
        return success(documentExcelService.preview(docType, file.getInputStream(),
            hasPermission("jewelry:product:add")));
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:list')")
    @GetMapping("/document/return-inspection-source/{id}")
    public AjaxResult returnInspectionSource(@PathVariable Long id,
        @RequestParam(required = false) Long excludeDocumentId)
    {
        JewelryDocument source = service.getReturnInspectionSource(id, excludeDocumentId);
        if (isMakerOnly() && !SecurityUtils.getUserId().equals(source.getCreatorUserId()))
        {
            return error("无权使用其他制单员创建的客户退货单");
        }
        if (isMakerOnly()) redactDocumentFinance(source);
        return success(source);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:list')")
    @GetMapping("/document/{id}")
    public AjaxResult document(@PathVariable Long id)
    {
        JewelryDocument document = service.getDocumentForDisplay(id);
        if (isMakerOnly() && !SecurityUtils.getUserId().equals(document.getCreatorUserId()))
        {
            return error("无权查看其他制单员的单据");
        }
        if (isMakerOnly()) redactDocumentFinance(document);
        return success(document);
    }

    @PreAuthorize("@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')")
    @PostMapping("/document/risk-check")
    public AjaxResult assessDocumentRisk(@RequestBody JewelryDocument document)
    {
        return success(service.assessDocumentRisk(document));
    }

    @PreAuthorize("@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')")
    @PostMapping("/document")
    public AjaxResult saveDocument(@RequestBody JewelryDocument document)
    {
        boolean editing = document.getDocumentId() != null;
        if (editing && !hasPermission("jewelry:document:edit"))
            return error("无权修改已有单据");
        if (!editing && !hasPermission("jewelry:document:add"))
            return error("无权新建单据");
        boolean assembly = "ASSEMBLY".equals(document.getDocType());
        if (editing)
        {
            JewelryDocument current = service.getDocument(document.getDocumentId());
            assembly = assembly || "ASSEMBLY".equals(current.getDocType());
        }
        if (assembly && !hasPermission("jewelry:assembly:add"))
            return error("无权新建或修改组装单");
        JewelryDocument saved = service.saveDocument(document, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        if (isMakerOnly()) redactDocumentFinance(saved);
        return success(saved);
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:submit')")
    @PostMapping("/document/{id}/submit")
    public AjaxResult submit(@PathVariable Long id)
    {
        service.submit(id, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:withdraw')")
    @PostMapping("/document/{id}/withdraw")
    public AjaxResult withdraw(@PathVariable Long id)
    {
        service.withdraw(id, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:document:reverse')")
    @PostMapping("/document/{id}/reverse")
    public AjaxResult reverse(@PathVariable Long id)
    {
        return success(service.createReversal(id, SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('jewelry:approval:approve')")
    @PostMapping("/approval/{id}/approve")
    public AjaxResult approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body)
    {
        Object expectedCost = body == null ? null : body.get("expectedTotalCost");
        service.approve(id, body == null ? "" : string(body.get("comment")),
            expectedCost == null ? null : decimal(expectedCost),
            SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:approval:reject')")
    @PostMapping("/approval/{id}/reject")
    public AjaxResult reject(@PathVariable Long id, @RequestBody Map<String, Object> body)
    {
        service.reject(id, string(body.get("comment")), SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('jewelry:calculator:list')")
    @PostMapping("/calculator")
    public AjaxResult calculate(@RequestBody Map<String, Object> body)
    {
        if (isMakerOnly()) return error("毛利试算仅对审核员和管理员开放");
        return success(service.calculateProfit(body));
    }

    private boolean validRole(String key)
    {
        return "jewelry_maker".equals(key) || "jewelry_reviewer".equals(key) || "jewelry_admin".equals(key);
    }
    private boolean isMakerOnly()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user.isAdmin()) return false;
        boolean maker = false;
        for (com.ruoyi.common.core.domain.entity.SysRole role : user.getRoles())
        {
            if ("jewelry_admin".equals(role.getRoleKey()) || "jewelry_reviewer".equals(role.getRoleKey())) return false;
            if ("jewelry_maker".equals(role.getRoleKey())) maker = true;
        }
        return maker;
    }
    private void removeKeys(List<Map<String, Object>> rows, String... keys)
    {
        for (Map<String, Object> row : rows)
        {
            for (String key : keys) row.remove(key);
        }
    }
    private void redactDocumentFinance(List<JewelryDocument> documents)
    {
        for (JewelryDocument document : documents) redactDocumentFinance(document);
    }
    private void redactDocumentFinance(JewelryDocument document)
    {
        document.setTotalCost(null);
        document.setTotalProfit(null);
        if (document.getItems() == null) return;
        for (com.ruoyi.jewelry.domain.JewelryDocumentItem item : document.getItems())
        {
            item.setUnitCost(null);
            item.setCostAmount(null);
            item.setProfitAmount(null);
            item.setProfitRate(null);
        }
    }
    private boolean hasPermission(String permission)
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        return user.isAdmin() || SecurityUtils.getLoginUser().getPermissions().contains(permission);
    }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultString(Object value, String fallback)
    {
        String result = string(value);
        return result.isEmpty() ? fallback : result;
    }
    private Long number(Object value)
    {
        if (value == null || string(value).isEmpty()) return null;
        return Long.valueOf(string(value));
    }
    private java.math.BigDecimal decimal(Object value)
    {
        return value == null || string(value).isEmpty() ? java.math.BigDecimal.ZERO :
            new java.math.BigDecimal(string(value));
    }
}
