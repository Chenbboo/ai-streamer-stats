package com.ruoyi.web.controller.business;

import com.ruoyi.business.service.impl.BusinessHistoricalAllocationService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/project/project/{projectId}/allocation-history")
public class BusinessHistoricalAllocationController extends BaseController {
    @Autowired private BusinessHistoricalAllocationService service;
    @GetMapping("/members") @PreAuthorize("@ss.hasAnyPermi('business:project:allocation,business:project:owner:view')")
    public AjaxResult members(@PathVariable Long projectId){return success(service.members(projectId,getUserId()));}
    @GetMapping @PreAuthorize("@ss.hasAnyPermi('business:project:allocation,business:project:owner:view')")
    public AjaxResult workspace(@PathVariable Long projectId,@RequestParam Map<String,Object> params){return success(service.workspace(projectId,params,getUserId()));}
    @PostMapping("/preview") @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    public AjaxResult preview(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.preview(projectId,body,getUserId()));}
    @PostMapping @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title="历史投入补录",businessType=BusinessType.UPDATE)
    public AjaxResult save(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.save(projectId,body,getUserId(),getUsername()));}
}
