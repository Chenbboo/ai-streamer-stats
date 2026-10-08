package com.ruoyi.web.controller.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.domain.BusinessAiChatRequest;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.service.IBusinessAiService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

/** 老板 AI 入口；权限与业务服务的数据范围校验同时生效。 */
@RestController
@RequestMapping("/business/ai/boss")
public class BusinessAiController extends BaseController
{
    @Autowired private IBusinessAiService service;

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody BusinessAiChatRequest request)
    {
        return success(service.chat(request == null ? null : request.getConversationId(),
            request == null ? null : request.getMessage(), AiExecutionContext.from(SecurityUtils.getLoginUser())));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @GetMapping("/conversation/{conversationId}")
    public AjaxResult conversation(@PathVariable Long conversationId)
    {
        return success(service.conversation(conversationId, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @PutMapping("/action/{actionRequestId}/confirm")
    public AjaxResult confirmAction(@PathVariable Long actionRequestId)
    {
        return success(service.confirmAction(actionRequestId,
            AiExecutionContext.from(SecurityUtils.getLoginUser())));
    }

    @PreAuthorize("@ss.hasPermi('business:boss:view')")
    @PutMapping("/action/{actionRequestId}/reject")
    public AjaxResult rejectAction(@PathVariable Long actionRequestId)
    {
        return success(service.rejectAction(actionRequestId,
            AiExecutionContext.from(SecurityUtils.getLoginUser())));
    }
}
