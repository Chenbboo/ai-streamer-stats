package com.ruoyi.web.controller.business;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;

@RestController
@RequestMapping("/business/company-access")
@PreAuthorize("@ss.hasPermi('business:boss:view')")
public class BusinessCompanyAccessController extends BaseController
{
    @Autowired private BusinessCompanyAccessService service;
    @GetMapping public AjaxResult workspace() { return success(service.workspace(getUserId())); }
    @GetMapping("/{companyId}") public AjaxResult grants(@PathVariable Long companyId) { return success(service.grants(companyId,getUserId())); }
    @PutMapping("/{companyId}/{userId}")
    @Log(title="公司管理授权",businessType=BusinessType.GRANT)
    public AjaxResult save(@PathVariable Long companyId,@PathVariable Long userId,@Valid @RequestBody Grant input)
    { service.save(companyId,userId,input.capabilities,input.version,input.reason,getUserId(),getUsername()); return success(); }
    public static class Grant
    {
        @NotNull public List<String> capabilities;
        @NotNull public Integer version;
        @NotNull @Size(min=1,max=500) public String reason;
    }
}
