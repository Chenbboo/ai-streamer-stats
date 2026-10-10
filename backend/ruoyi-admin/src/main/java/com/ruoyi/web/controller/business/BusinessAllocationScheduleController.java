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
@RequestMapping("/business/project/project/{projectId}/allocation-schedule")
public class BusinessAllocationScheduleController extends BaseController {
    @Autowired private BusinessHistoricalAllocationService service;
    @GetMapping("/members") @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    public AjaxResult members(@PathVariable Long projectId){return success(service.scheduleMembers(projectId,getUserId()));}
    @GetMapping("/state") @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    public AjaxResult state(@PathVariable Long projectId,@RequestParam Long userId){return success(service.scheduleState(projectId,userId,getUserId()));}
    @GetMapping @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    public AjaxResult workspace(@PathVariable Long projectId,@RequestParam Map<String,Object> params){return success(service.scheduleWorkspace(projectId,params,getUserId()));}
    @PostMapping("/preview") @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    public AjaxResult preview(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.previewSchedule(projectId,body,getUserId()));}
    @PostMapping @PreAuthorize("@ss.hasPermi('business:project:allocation')")
    @Log(title="多段投入调整",businessType=BusinessType.UPDATE)
    public AjaxResult save(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.saveSchedule(projectId,body,getUserId(),getUsername()));}
}
