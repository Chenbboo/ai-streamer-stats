package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.impl.BusinessProjectWorkService;
import com.ruoyi.business.service.impl.BusinessProjectWorkPricingService;
import com.ruoyi.business.service.impl.BusinessProjectPlanService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/project-work")
public class BusinessProjectWorkController extends BaseController
{
    @Autowired private BusinessProjectWorkService service;
    @Autowired private BusinessProjectWorkPricingService pricing;
    @Autowired private BusinessProjectPlanService plans;

    @GetMapping("/options")
    @PreAuthorize("@ss.hasAnyPermi('business:project:list,business:project:owner:view,business:work:report')")
    public AjaxResult options(){return success(service.options(getUserId(),SecurityUtils.isAdmin()));}

    @GetMapping("/{projectId}/plan")
    @PreAuthorize("@ss.hasAnyPermi('business:project:list,business:project:owner:view,business:work:report')")
    public AjaxResult plan(@PathVariable Long projectId){return success(plans.plan(projectId,getUserId(),SecurityUtils.isAdmin()));}

    @PostMapping("/{projectId}/plan-changes")
    @PreAuthorize("@ss.hasPermi('business:project:edit')")
    @Log(title="项目计划基线变更",businessType=BusinessType.INSERT)
    public AjaxResult change(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(plans.request(projectId,body,getUserId(),getUsername()));}

    @PostMapping("/plan-changes/{changeId}/review")
    @PreAuthorize("@ss.hasPermi('business:project:manage')")
    @Log(title="项目计划变更复核",businessType=BusinessType.UPDATE)
    public AjaxResult reviewChange(@PathVariable Long changeId,@RequestBody Map<String,Object> body){return success(plans.review(changeId,body,getUserId(),getUsername()));}

    @PostMapping("/{projectId}/forecast")
    @PreAuthorize("@ss.hasPermi('business:project:edit')")
    @Log(title="项目预测更新",businessType=BusinessType.INSERT)
    public AjaxResult forecast(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(plans.forecast(projectId,body,getUserId(),getUsername()));}

    @GetMapping("/{projectId}/workspace")
    @PreAuthorize("@ss.hasAnyPermi('business:project:list,business:project:owner:view,business:work:report')")
    public AjaxResult workspace(@PathVariable Long projectId,@RequestParam Map<String,Object> query){return success(service.workspace(projectId,query,getUserId(),SecurityUtils.isAdmin()));}

    @PostMapping("/{projectId}/assignments")
    @PreAuthorize("@ss.hasPermi('business:resource:edit')")
    @Log(title="项目资源计划",businessType=BusinessType.INSERT)
    public AjaxResult assignment(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.saveAssignment(projectId,body,getUserId(),getUsername()));}

    @PostMapping("/assignments/{assignmentId}/retire")
    @PreAuthorize("@ss.hasPermi('business:resource:edit')")
    @Log(title="停用项目资源计划",businessType=BusinessType.UPDATE)
    public AjaxResult retire(@PathVariable Long assignmentId,@RequestBody Map<String,Object> body){service.retireAssignment(assignmentId,body,getUserId(),getUsername());return success();}

    @PostMapping("/{projectId}/entries")
    @PreAuthorize("@ss.hasPermi('business:work:report')")
    @Log(title="实际工作记录",businessType=BusinessType.INSERT)
    public AjaxResult save(@PathVariable Long projectId,@RequestBody Map<String,Object> body){return success(service.saveEntry(projectId,body,getUserId(),getUsername()));}

    @PostMapping("/entries/{entryId}/corrections")
    @PreAuthorize("@ss.hasPermi('business:work:report')")
    @Log(title="实际工作更正",businessType=BusinessType.INSERT)
    public AjaxResult correct(@PathVariable Long entryId,@RequestBody Map<String,Object> body){return success(service.correct(entryId,body,getUserId(),getUsername()));}

    @PostMapping("/entries/{entryId}/{action:submit|withdraw|discard}")
    @PreAuthorize("@ss.hasPermi('business:work:report')")
    @Log(title="实际工作提交撤回",businessType=BusinessType.UPDATE)
    public AjaxResult reportAction(@PathVariable Long entryId,@PathVariable String action,@RequestBody Map<String,Object> body){return success(service.transition(entryId,action,body,getUserId(),getUsername()));}

    @PostMapping("/entries/{entryId}/{action:confirm|return}")
    @PreAuthorize("@ss.hasPermi('business:work:confirm')")
    @Log(title="实际工作确认",businessType=BusinessType.UPDATE)
    public AjaxResult reviewAction(@PathVariable Long entryId,@PathVariable String action,@RequestBody Map<String,Object> body){return success(service.transition(entryId,action,body,getUserId(),getUsername()));}

    @GetMapping("/entries/{entryId}/history")
    @PreAuthorize("@ss.hasAnyPermi('business:project:list,business:project:owner:view,business:work:report')")
    public AjaxResult history(@PathVariable Long entryId){return success(service.history(entryId,getUserId(),SecurityUtils.isAdmin()));}

    @PostMapping("/calendars")
    @PreAuthorize("@ss.hasPermi('business:resource:config')")
    @Log(title="发布工作日历版本",businessType=BusinessType.INSERT)
    public AjaxResult calendar(@RequestBody Map<String,Object> body){return success(service.addCalendar(body,getUsername(),SecurityUtils.isAdmin()));}

    @PostMapping("/unit-policies")
    @PreAuthorize("@ss.hasPermi('business:resource:config')")
    @Log(title="发布工作量单位政策",businessType=BusinessType.INSERT)
    public AjaxResult unit(@RequestBody Map<String,Object> body){return success(service.addUnitPolicy(body,getUsername(),SecurityUtils.isAdmin()));}

    @PostMapping("/{projectId}/reprice")
    @PreAuthorize("@ss.hasPermi('business:accounting:recalculate')")
    @Log(title="重试工作成本计价",businessType=BusinessType.UPDATE)
    public AjaxResult reprice(@PathVariable Long projectId){return success(pricing.retryProject(projectId,getUserId(),getUsername(),SecurityUtils.isAdmin()));}
}
