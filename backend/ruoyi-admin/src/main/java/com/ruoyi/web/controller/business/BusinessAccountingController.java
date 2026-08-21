package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/accounting")
public class BusinessAccountingController extends BaseController
{
    @Autowired private IBusinessAccountingService service;

    @PreAuthorize("@ss.hasPermi('business:accounting:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard(@RequestParam Map<String,Object> query)
    {return success(service.dashboard(query,SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/boss-overview")
    public AjaxResult bossOverview()
    {return success(service.bossOverview(SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/personnel-cost-overview")
    public AjaxResult personnelCostOverview(@RequestParam Map<String,Object> query)
    {return success(service.personnelCostOverview(query,SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:accounting:add')")
    @Log(title="每日收支草稿",businessType=BusinessType.INSERT)
    @PostMapping("/fact")
    public AjaxResult save(@RequestBody BusinessOperatingFact fact)
    {return success(service.saveFact(fact,SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title="项目今日填报",businessType=BusinessType.INSERT)
    @PostMapping("/project-fact")
    public AjaxResult saveProjectFact(@RequestBody BusinessOperatingFact fact)
    {return success(service.saveProjectFact(fact,SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:project:report')")
    @Log(title="项目今日总花费",businessType=BusinessType.INSERT)
    @PostMapping("/project-daily-spend")
    public AjaxResult saveProjectDailySpend(@RequestBody BusinessOperatingFact fact)
    {return success(service.saveProjectDailySpend(fact,SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:accounting:confirm')")
    @Log(title="确认经营事实",businessType=BusinessType.UPDATE)
    @PutMapping("/fact/{factId}/confirm")
    public AjaxResult confirm(@PathVariable Long factId)
    {return success(service.confirmFact(factId,SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:accounting:confirm')")
    @Log(title="冲销经营事实",businessType=BusinessType.UPDATE)
    @PostMapping("/fact/{factId}/reverse")
    public AjaxResult reverse(@PathVariable Long factId,@RequestBody Map<String,Object> body)
    {return success(service.reverseFact(factId,text(body.get("reason")),SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:accounting:recalculate')")
    @Log(title="重算项目日结果",businessType=BusinessType.UPDATE)
    @PostMapping("/recalculate")
    public AjaxResult recalculate(@RequestBody Map<String,Object> body)
    {return success(service.recalculate(longValue(body.get("projectId")),DateUtils.parseDate(body.get("bizDate")),SecurityUtils.getUserId(),getUsername(),SecurityUtils.isAdmin()));}

    @PreAuthorize("@ss.hasPermi('business:accounting:list')")
    @GetMapping("/result/{resultId}")
    public AjaxResult result(@PathVariable Long resultId)
    {return success(service.resultDetail(resultId,SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}

    private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private String text(Object v){return v==null?null:String.valueOf(v);}
}
