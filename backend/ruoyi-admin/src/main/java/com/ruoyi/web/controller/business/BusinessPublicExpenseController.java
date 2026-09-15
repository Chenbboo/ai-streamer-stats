package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.impl.BusinessPublicExpenseService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/public-expenses")
public class BusinessPublicExpenseController extends BaseController
{
    @Autowired private BusinessPublicExpenseService service;
    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @GetMapping("/workspace")
    public AjaxResult workspace(@RequestParam(required=false) Long companyDeptId,@RequestParam(required=false) String month,@RequestParam(required=false) String currency)
    {return success(service.workspace(companyDeptId,month,currency,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="公司公共费用规则",businessType=BusinessType.UPDATE)
    @PostMapping("/policy")
    public AjaxResult policy(@RequestBody Map<String,Object> body){return success(service.savePolicy(body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="生成公司月费用",businessType=BusinessType.INSERT)
    @PostMapping("/month")
    public AjaxResult generate(@RequestBody Map<String,Object> body){return success(service.generateMonth(body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="核实公司月费用",businessType=BusinessType.UPDATE)
    @PutMapping("/month/{billId}/entries")
    public AjaxResult entries(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.saveEntries(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="公司费用负责人分配",businessType=BusinessType.UPDATE)
    @PutMapping("/month/{billId}/owners")
    public AjaxResult owners(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.saveOwners(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="复制上月负责人分配",businessType=BusinessType.UPDATE)
    @PostMapping("/month/{billId}/copy-owners")
    public AjaxResult copyOwners(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.copyOwners(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="下发公司月费用",businessType=BusinessType.UPDATE)
    @PostMapping("/month/{billId}/publish")
    public AjaxResult publish(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.publish(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="退回公司月费用",businessType=BusinessType.UPDATE)
    @PostMapping("/month/{billId}/recall")
    public AjaxResult recall(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.recall(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="公司公共费用月结",businessType=BusinessType.UPDATE)
    @PostMapping("/month/{billId}/settle")
    public AjaxResult settle(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.settle(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:public-expense:list')")
    @Log(title="公司公共费用历史调整",businessType=BusinessType.INSERT)
    @PostMapping("/month/{billId}/adjust")
    public AjaxResult adjust(@PathVariable Long billId,@RequestBody Map<String,Object> body){return success(service.adjust(billId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasAnyPermi('business:project:owner:view,business:project:list,business:boss:view')")
    @GetMapping("/owner-workspace")
    public AjaxResult ownerWorkspace(@RequestParam(required=false) String month){return success(service.ownerWorkspace(month,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasAnyPermi('business:project:owner:view,business:project:list,business:boss:view')")
    @Log(title="负责人公共费用项目分摊",businessType=BusinessType.UPDATE)
    @PutMapping("/owner/{allocationId}/projects")
    public AjaxResult projects(@PathVariable Long allocationId,@RequestBody Map<String,Object> body){return success(service.saveProjects(allocationId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasAnyPermi('business:project:owner:view,business:project:list,business:boss:view')")
    @Log(title="复制上月项目分摊",businessType=BusinessType.UPDATE)
    @PostMapping("/owner/{allocationId}/copy-projects")
    public AjaxResult copyProjects(@PathVariable Long allocationId,@RequestBody Map<String,Object> body){return success(service.copyProjects(allocationId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasAnyPermi('business:project:owner:view,business:project:list,business:boss:view')")
    @Log(title="提交公共费用项目分摊",businessType=BusinessType.UPDATE)
    @PostMapping("/owner/{allocationId}/submit")
    public AjaxResult submit(@PathVariable Long allocationId,@RequestBody Map<String,Object> body){return success(service.submit(allocationId,body,SecurityUtils.getUserId(),getUsername()));}

    @PreAuthorize("@ss.hasPermi('business:project:list')")
    @GetMapping("/project/{projectId}")
    public AjaxResult project(@PathVariable Long projectId,@RequestParam(required=false) String month){return success(service.projectWorkspace(projectId,month,SecurityUtils.getUserId(),SecurityUtils.isAdmin()));}
}
