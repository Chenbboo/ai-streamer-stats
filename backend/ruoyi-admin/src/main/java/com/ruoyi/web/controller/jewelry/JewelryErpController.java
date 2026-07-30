package com.ruoyi.web.controller.jewelry;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
import com.ruoyi.jewelry.service.IJewelryErpService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/jewelry")
public class JewelryErpController extends BaseController
{
    @Autowired private IJewelryErpService service;
    @Autowired private JewelryErpMapper mapper;
    @Autowired private ISysUserService userService;

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

    @PreAuthorize("@ss.hasAnyPermi('jewelry:product:add,jewelry:product:edit')")
    @PostMapping("/product")
    public AjaxResult saveProduct(@RequestBody Map<String, Object> body)
    {
        boolean editing = body.get("productId") != null;
        if (editing && !hasPermission("jewelry:product:edit")) return error("无权修改已有商品档案");
        if (!editing && !hasPermission("jewelry:product:add")) return error("无权新增商品档案");
        if (string(body.get("sku")).isEmpty() || string(body.get("productName")).isEmpty())
            return error("SKU和商品名称不能为空");
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

    @PreAuthorize("@ss.hasPermi('jewelry:document:list')")
    @GetMapping("/document/{id}")
    public AjaxResult document(@PathVariable Long id)
    {
        JewelryDocument document = service.getDocument(id);
        if (isMakerOnly() && !SecurityUtils.getUserId().equals(document.getCreatorUserId()))
        {
            return error("无权查看其他制单员的单据");
        }
        if (isMakerOnly()) redactDocumentFinance(document);
        return success(document);
    }

    @PreAuthorize("@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')")
    @PostMapping("/document")
    public AjaxResult saveDocument(@RequestBody JewelryDocument document)
    {
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
        service.approve(id, body == null ? "" : string(body.get("comment")),
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
        Long productId = number(body.get("productId"));
        if (productId == null) return error("请选择需要试算的商品");
        Map<String, Object> product = mapper.selectProductById(productId);
        if (product == null || !"0".equals(string(product.get("status")))) return error("商品不存在或已停用");

        java.math.BigDecimal price = decimal(body.get("price"));
        if (price.signum() <= 0) return error("成交价必须大于0");
        Long quantityValue = number(body.get("quantity"));
        int quantity = quantityValue == null ? 1 : quantityValue.intValue();
        int availableQty = decimal(product.get("onHandQty")).subtract(decimal(product.get("reservedOutQty"))).intValue();
        if (quantity <= 0) return error("试算数量必须大于0");
        if (quantity > availableQty) return error("试算数量不能超过当前可用库存" + availableQty + "件");
        java.math.BigDecimal cost = decimal(product.get("avgCost"));
        java.math.BigDecimal fees = decimal(body.get("packFee")).add(decimal(body.get("shipFee")))
            .add(decimal(body.get("certFee")));
        java.math.BigDecimal platformRate = percentage(body.get("platformRate"), "平台扣点率");
        java.math.BigDecimal commissionRate = percentage(body.get("commissionRate"), "达人佣金率");
        java.math.BigDecimal taxRate = percentage(body.get("taxRate"), "税率");
        java.math.BigDecimal rate = platformRate.add(commissionRate).add(taxRate);
        if (rate.compareTo(java.math.BigDecimal.ONE) >= 0) return error("平台、佣金和税率合计必须小于100%");

        java.math.BigDecimal deductions = price.multiply(rate);
        java.math.BigDecimal profit = price.subtract(cost).subtract(fees).subtract(price.multiply(rate));
        java.math.BigDecimal breakEvenPrice = cost.add(fees).divide(java.math.BigDecimal.ONE.subtract(rate), 2,
            java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal maxCommissionRate = java.math.BigDecimal.ONE.subtract(platformRate).subtract(taxRate)
            .subtract(cost.add(fees).divide(price, 8, java.math.RoundingMode.HALF_UP));
        if (maxCommissionRate.signum() < 0) maxCommissionRate = java.math.BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("profit", profit.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("profitRate", price.signum() == 0 ? java.math.BigDecimal.ZERO :
            profit.divide(price, 6, java.math.RoundingMode.HALF_UP));
        result.put("cost", cost.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("deductions", deductions.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("fixedFees", fees.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("breakEvenPrice", breakEvenPrice);
        result.put("maxCommissionRate", maxCommissionRate.setScale(6, java.math.RoundingMode.HALF_UP));
        result.put("quantity", quantity);
        result.put("availableQty", availableQty);
        result.put("remainingQty", availableQty - quantity);
        result.put("totalRevenue", price.multiply(java.math.BigDecimal.valueOf(quantity))
            .setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("totalProfit", profit.multiply(java.math.BigDecimal.valueOf(quantity))
            .setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("totalDeductions", deductions.multiply(java.math.BigDecimal.valueOf(quantity))
            .setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("totalFixedFees", fees.multiply(java.math.BigDecimal.valueOf(quantity))
            .setScale(2, java.math.RoundingMode.HALF_UP));
        return success(result);
    }

    private java.math.BigDecimal percentage(Object value, String label)
    {
        java.math.BigDecimal percent = decimal(value);
        if (percent.signum() < 0 || percent.compareTo(new java.math.BigDecimal("100")) > 0)
            throw new IllegalArgumentException(label + "必须在0%到100%之间");
        return percent.divide(new java.math.BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);
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
        document.setRiskStatus(null);
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
