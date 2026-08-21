package com.ruoyi.web.controller.business;
import java.util.ArrayList;
import java.util.List;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;
import com.ruoyi.business.service.IBusinessProjectKpiService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/kpi")
public class BusinessProjectKpiController extends BaseController
{
    @Autowired private IBusinessProjectKpiService service;

    @PreAuthorize("@ss.hasPermi('business:kpi:list')")
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(required = false) String projectIds)
    {
        if (projectIds == null || projectIds.trim().isEmpty())
            return success(service.overview(userId(), isAdministrator(), isBoss()));
        List<Long> ids = new ArrayList<Long>();
        for (String value : projectIds.split(","))
            try { ids.add(Long.valueOf(value.trim())); } catch (NumberFormatException ignored) { }
        return success(service.overview(ids, userId(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:kpi:list')")
    @GetMapping("/workspace")
    public AjaxResult workspace(@RequestParam Long projectId, @RequestParam(required = false) Long planId)
    {
        return success(service.workspace(projectId, planId, userId(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:kpi:manage')")
    @Log(title = "项目KPI奖金方案", businessType = BusinessType.INSERT)
    @PostMapping("/plan/publish")
    public AjaxResult publish(@RequestBody BusinessProjectKpiPlan plan)
    {
        return success(service.publishPlan(plan, userId(), userName(), isAdministrator(), isBoss()));
    }

    @PreAuthorize("@ss.hasPermi('business:kpi:settle')")
    @Log(title = "项目KPI结果", businessType = BusinessType.UPDATE)
    @PutMapping("/settlement/{settlementId}/results")
    public AjaxResult saveResults(@PathVariable Long settlementId, @RequestBody BusinessProjectKpiSettlement input)
    {
        return success(service.saveResults(settlementId, input, userId(), userName(), isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:kpi:settle')")
    @Log(title = "提交项目KPI结算", businessType = BusinessType.UPDATE)
    @PostMapping("/settlement/{settlementId}/submit")
    public AjaxResult submit(@PathVariable Long settlementId)
    {
        return success(service.submit(settlementId, userId(), userName(), isAdministrator()));
    }

    @PreAuthorize("@ss.hasPermi('business:kpi:manage')")
    @Log(title = "确认项目KPI奖金", businessType = BusinessType.UPDATE)
    @PostMapping("/settlement/{settlementId}/review")
    public AjaxResult review(@PathVariable Long settlementId, @RequestBody Map<String, Object> body)
    {
        return success(service.review(settlementId, text(body, "decision"), text(body, "comment"),
            userId(), userName(), isAdministrator(), isBoss()));
    }

    private Long userId() { return SecurityUtils.getUserId(); }
    private String userName() { return SecurityUtils.getUsername(); }
    private boolean isAdministrator() { return SecurityUtils.isAdmin(); }
    private boolean isBoss() { return SecurityUtils.isAdmin() || SecurityUtils.hasPermi("business:boss:view"); }
    private String text(Map<String, Object> body, String key)
    { Object value = body == null ? null : body.get(key); return value == null ? null : String.valueOf(value); }
}
