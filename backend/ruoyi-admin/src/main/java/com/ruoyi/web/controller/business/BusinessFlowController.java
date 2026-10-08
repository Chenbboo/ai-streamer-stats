package com.ruoyi.web.controller.business;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.impl.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
@RestController
@RequestMapping("/business/flow")
public class BusinessFlowController {
 @Autowired private BusinessFlowService flows;
 @Autowired private BusinessAccountingServiceImpl accounting;
 @PostMapping("/project/{id}/no-spend")
 @PreAuthorize("@ss.hasPermi('business:project:report')")
 @Log(title="今日无支出确认",businessType=BusinessType.INSERT)
 public AjaxResult noSpend(@PathVariable Long id){return AjaxResult.success(accounting.confirmNoSpend(id,new Date(),SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.isAdmin()));}
 @GetMapping("/staff/{id}/departure")
 @PreAuthorize("@ss.hasPermi('business:staff:manage')")
 public AjaxResult checklist(@PathVariable Long id){return AjaxResult.success(flows.departureChecklist(id));}
 @PostMapping("/staff/{id}/departure")
 @PreAuthorize("@ss.hasPermi('business:staff:manage')")
 @Log(title="人员离职办理",businessType=BusinessType.UPDATE)
 public AjaxResult depart(@PathVariable Long id,@RequestBody Map<String,Object> input){return AjaxResult.success(flows.requestDeparture(id,input,SecurityUtils.getUserId(),SecurityUtils.getUsername()));}
 @PostMapping("/staff/{id}/departure/cancel")
 @PreAuthorize("@ss.hasPermi('business:staff:manage')")
 @Log(title="取消离职办理",businessType=BusinessType.UPDATE)
 public AjaxResult cancel(@PathVariable Long id){flows.cancelDeparture(id);return AjaxResult.success();}
 @GetMapping("/project/{id}/adjustments")
 @PreAuthorize("@ss.hasAnyPermi('business:accounting:list,business:project:list,business:project:owner:view,business:boss:view')")
 public AjaxResult adjustments(@PathVariable Long id){return AjaxResult.success(flows.adjustments(id,SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}
 @PostMapping("/project/{id}/adjustments")
 @PreAuthorize("@ss.hasAnyPermi('business:accounting:add,business:project:report')")
 @Log(title="关账后调整申请",businessType=BusinessType.INSERT)
 public AjaxResult adjust(@PathVariable Long id,@RequestBody Map<String,Object> input){return AjaxResult.success(flows.requestAdjustment(id,input,SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.isAdmin()));}
 @PostMapping("/adjustments/{id}/review")
 @PreAuthorize("@ss.hasPermi('business:accounting:close')")
 @Log(title="关账后调整审核",businessType=BusinessType.UPDATE)
 public AjaxResult review(@PathVariable Long id,@RequestBody Map<String,Object> input){flows.reviewAdjustment(id,input,SecurityUtils.getUserId(),SecurityUtils.getUsername());return AjaxResult.success();}
}
