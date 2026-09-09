package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.service.IBusinessIncentiveService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/incentive")
public class BusinessIncentiveController extends BaseController
{
    @Autowired private IBusinessIncentiveService service;

    @PreAuthorize("@ss.hasPermi('business:incentive:list')")
    @GetMapping("/workspace")
    public AjaxResult workspace(@RequestParam(required = false) Long projectId)
    { return success(service.workspace(projectId, SecurityUtils.getUserId(), SecurityUtils.isAdmin())); }

    @PreAuthorize("@ss.hasAnyPermi('business:incentive:rule,business:kpi:manage')")
    @Log(title = "发布独立奖金规则", businessType = BusinessType.INSERT)
    @PostMapping("/rule")
    public AjaxResult publishRule(@RequestBody BusinessIncentiveRule rule)
    { return success(service.publishRule(rule, SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin())); }

    @PreAuthorize("@ss.hasAnyPermi('business:incentive:rule,business:kpi:manage')")
    @Log(title = "停用奖金规则", businessType = BusinessType.UPDATE)
    @PostMapping("/rule/{ruleId}/retire")
    public AjaxResult retireRule(@PathVariable Long ruleId, @RequestBody Map<String,Object> body)
    {
        service.retireRule(ruleId, text(body,"reason"), SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:incentive:list')")
    @PostMapping("/estimate")
    public AjaxResult estimate(@RequestBody BusinessIncentiveAward input)
    { return success(service.estimate(input.getProjectId(), input.getRuleId(), input.getSettlementId(), SecurityUtils.getUserId(), SecurityUtils.isAdmin())); }

    @PreAuthorize("@ss.hasPermi('business:incentive:apply')")
    @Log(title = "创建独立奖励申请", businessType = BusinessType.INSERT)
    @PostMapping("/award")
    public AjaxResult create(@RequestBody BusinessIncentiveAward input)
    { return success(service.createAward(input, SecurityUtils.getUserId(), SecurityUtils.getUsername())); }

    @PreAuthorize("@ss.hasPermi('business:incentive:apply')")
    @Log(title = "提交奖励核准", businessType = BusinessType.UPDATE)
    @PostMapping("/award/{awardId}/submit")
    public AjaxResult submit(@PathVariable Long awardId, @RequestBody Map<String,Object> body)
    { return success(service.submit(awardId, version(body), text(body,"reason"), SecurityUtils.getUserId(), SecurityUtils.getUsername())); }

    @PreAuthorize("@ss.hasPermi('business:incentive:approve')")
    @Log(title = "核准独立奖励", businessType = BusinessType.UPDATE)
    @PostMapping("/award/{awardId}/review")
    public AjaxResult review(@PathVariable Long awardId, @RequestBody Map<String,Object> body)
    { return success(service.review(awardId, version(body), text(body,"decision"), text(body,"reason"), SecurityUtils.getUserId(), SecurityUtils.getUsername())); }

    @PreAuthorize("@ss.hasAnyPermi('business:incentive:apply,business:incentive:approve')")
    @Log(title = "撤销独立奖励", businessType = BusinessType.UPDATE)
    @PostMapping("/award/{awardId}/cancel")
    public AjaxResult cancel(@PathVariable Long awardId, @RequestBody Map<String,Object> body)
    { return success(service.cancel(awardId, version(body), text(body,"reason"), SecurityUtils.getUserId(), SecurityUtils.getUsername())); }

    @PreAuthorize("@ss.hasPermi('business:incentive:approve')")
    @Log(title = "重新提交核准奖励成本", businessType = BusinessType.UPDATE)
    @PostMapping("/award/{awardId}/resubmit-cost")
    public AjaxResult resubmitCost(@PathVariable Long awardId, @RequestBody Map<String,Object> body)
    { return success(service.resubmitCost(awardId, version(body), text(body,"reason"), SecurityUtils.getUserId(), SecurityUtils.getUsername())); }

    private Integer version(Map<String,Object> body)
    {
        try { return body == null || body.get("version") == null ? null : Integer.valueOf(String.valueOf(body.get("version"))); }
        catch (NumberFormatException error) { throw new ServiceException("奖励版本号不正确"); }
    }
    private String text(Map<String,Object> body, String key)
    { Object value = body == null ? null : body.get(key); return value == null ? null : String.valueOf(value); }
}
