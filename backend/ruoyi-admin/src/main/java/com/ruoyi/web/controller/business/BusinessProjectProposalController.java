package com.ruoyi.web.controller.business;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.service.IBusinessProjectProposalService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

/** 负责人维护项目测算并自主启动；历史待审批数据仍可处理。 */
@RestController
@RequestMapping("/business/project-proposal")
public class BusinessProjectProposalController extends BaseController
{
    @Autowired private IBusinessProjectProposalService proposalService;

    @PreAuthorize("@ss.hasPermi('business:project:proposal:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, Object> query)
    {
        startPage();
        List<BusinessProjectProposal> rows = proposalService.listOwn(query, userId(), isAdmin());
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:review')")
    @GetMapping("/review-list")
    public TableDataInfo reviewList(@RequestParam Map<String, Object> query)
    {
        startPage();
        List<BusinessProjectProposal> rows = proposalService.listForReview(query, userId(), isBoss());
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:review')")
    @GetMapping("/directory")
    public TableDataInfo directory(@RequestParam Map<String, Object> query)
    {
        startPage();
        return getDataTable(proposalService.directory(query, isBoss(), isAdmin()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(proposalService.options(userId()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:list')")
    @GetMapping("/staff-options")
    public AjaxResult staffOptions(@RequestParam(required = false) Long companyDeptId,
        @RequestParam(required = false) String effectiveDate)
    {
        return success(proposalService.staffOptions(companyDeptId, effectiveDate, userId()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:list')")
    @GetMapping("/{proposalId}")
    public AjaxResult detail(@PathVariable("proposalId") Long proposalId)
    {
        return success(proposalService.get(proposalId, userId(), isBoss(), isAdmin()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:add')")
    @Log(title = "立项申请", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BusinessProjectProposal proposal)
    {
        return success(proposalService.create(proposal, userId(), userName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:edit')")
    @Log(title = "立项申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BusinessProjectProposal proposal)
    {
        return success(proposalService.update(proposal, userId(), userName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:edit')")
    @Log(title = "立项申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/{proposalId}")
    public AjaxResult delete(@PathVariable("proposalId") Long proposalId)
    {
        proposalService.delete(proposalId, userId(), userName());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:submit')")
    @Log(title = "负责人启动项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{proposalId}/submit")
    public AjaxResult submit(@PathVariable("proposalId") Long proposalId)
    {
        return success(proposalService.submit(proposalId, userId(), userName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:submit')")
    @Log(title = "撤回立项申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{proposalId}/withdraw")
    public AjaxResult withdraw(@PathVariable("proposalId") Long proposalId,
        @RequestBody(required = false) Map<String, Object> body)
    {
        return success(proposalService.withdraw(proposalId, text(body, "comment"), userId(), userName()));
    }

    @PreAuthorize("@ss.hasPermi('business:project:proposal:review')")
    @Log(title = "处理历史立项申请", businessType = BusinessType.UPDATE)
    @PutMapping("/{proposalId}/review")
    public AjaxResult review(@PathVariable("proposalId") Long proposalId, @RequestBody Map<String, Object> body)
    {
        return success(proposalService.review(proposalId, text(body, "decision"), text(body, "comment"),
            userId(), userName(), isBoss()));
    }

    private Long userId() { return SecurityUtils.getUserId(); }
    private String userName() { return SecurityUtils.getUsername(); }
    private boolean isBoss() { return SecurityUtils.isAdmin() || SecurityUtils.hasPermi("business:boss:view"); }
    private boolean isAdmin() { return SecurityUtils.isAdmin(); }
    private String text(Map<String, Object> body, String key)
    {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
