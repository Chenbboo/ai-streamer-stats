package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.business.ai.capability.AiExecutionContext;

public interface IBusinessAiService
{
    Map<String, Object> chat(Long conversationId, String message, Long userId, String userName, boolean viewAll);
    Map<String, Object> chat(Long conversationId, String message, AiExecutionContext context);
    List<Map<String, Object>> conversation(Long conversationId, Long userId);
    Map<String, Object> confirmAction(Long actionRequestId, Long userId, String userName);
    default Map<String, Object> confirmAction(Long actionRequestId, AiExecutionContext context)
    {
        return confirmAction(actionRequestId, context.getUserId(), context.getUserName());
    }
    Map<String, Object> rejectAction(Long actionRequestId, Long userId, String userName);
    default Map<String, Object> rejectAction(Long actionRequestId, AiExecutionContext context)
    {
        return rejectAction(actionRequestId, context.getUserId(), context.getUserName());
    }
}
