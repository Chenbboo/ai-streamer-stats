package com.ruoyi.web.controller.business;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.impl.BusinessProfitTaxService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
@RestController
@RequestMapping("/business/profit-tax")
public class BusinessProfitTaxController extends BaseController {
    @Autowired private BusinessProfitTaxService service;
    @GetMapping("/settings")
    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    public AjaxResult settings(){return success(service.settings(SecurityUtils.getUserId()));}
    @PutMapping("/settings/{companyId}")
    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    public AjaxResult save(@PathVariable Long companyId,@RequestBody Map<String,Object> body){return success(service.save(companyId,body,SecurityUtils.getUserId(),getUsername()));}
}
