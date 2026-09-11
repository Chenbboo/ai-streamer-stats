package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.mapper.BusinessProjectProgressMapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/project/progress")
@PreAuthorize("isAuthenticated()")
public class BusinessProjectProgressController extends BaseController {
    @Autowired private IBusinessProjectService projects;
    @Autowired private BusinessProjectProgressMapper reports;

    @GetMapping("/{projectId}")
    public AjaxResult workspace(@PathVariable Long projectId) {
        return success(projects.progressWorkspace(projectId,getUserId(),SecurityUtils.isAdmin(getUserId()),
            SecurityUtils.hasPermi("business:boss:view")));
    }
    @GetMapping("/notifications")
    public AjaxResult notifications() { return success(reports.notifications(getUserId())); }

    @PutMapping("/{parentId}/weights/{projectId}")
    public AjaxResult weight(@PathVariable Long parentId,@PathVariable Long projectId,@RequestBody Map<String,java.math.BigDecimal> body) {
        projects.setProgressWeight(parentId,projectId,body.get("weight"),getUserId(),getUsername());
        return success();
    }

    @PutMapping("/notifications/{id}/read")
    public AjaxResult read(@PathVariable Long id) { return toAjax(reports.readNotification(id,getUserId())); }
}
