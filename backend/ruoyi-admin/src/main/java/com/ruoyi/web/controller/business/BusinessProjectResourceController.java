package com.ruoyi.web.controller.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.impl.BusinessProjectResourceService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/project-resources")
public class BusinessProjectResourceController extends BaseController
{
    @Autowired private BusinessProjectResourceService service;
    @GetMapping("/projects") @PreAuthorize("@ss.hasPermi('business:boss:view')")
    public AjaxResult projects(@RequestParam Long companyDeptId,@RequestParam(required=false) String asOf){return success(service.portfolio(companyDeptId,asOf,getUserId(),SecurityUtils.isAdmin()));}
    @GetMapping("/{projectId}/personnel") @PreAuthorize("@ss.hasAnyPermi('business:boss:view,business:project:owner:view,business:project:list')")
    public AjaxResult personnel(@PathVariable Long projectId,@RequestParam(required=false) String dateFrom,@RequestParam(required=false) String dateTo,@RequestParam(defaultValue="false") boolean entireProject){return success(service.personnel(projectId,dateFrom,dateTo,entireProject,getUserId(),SecurityUtils.isAdmin()));}
    @GetMapping("/{projectId}/budget") @PreAuthorize("@ss.hasAnyPermi('business:boss:view,business:project:owner:view,business:project:list')")
    public AjaxResult budget(@PathVariable Long projectId,@RequestParam(required=false) String asOf){return success(service.budget(projectId,asOf,getUserId(),SecurityUtils.isAdmin()));}
}
