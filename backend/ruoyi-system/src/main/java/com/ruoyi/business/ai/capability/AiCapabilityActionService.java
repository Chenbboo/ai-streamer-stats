package com.ruoyi.business.ai.capability;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.common.exception.ServiceException;

/** Persists and executes capability confirmations using the same permission gate as normal calls. */
@Service
public class AiCapabilityActionService
{
    public static final String ACTION_PREFIX = "CAPABILITY:";
    private final AiCapabilityRegistry registry;
    private final BusinessAiMapper mapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiCapabilityActionService(AiCapabilityRegistry registry, BusinessAiMapper mapper, ObjectMapper objectMapper)
    {
        this.registry = registry; this.mapper = mapper; this.objectMapper = objectMapper;
    }

    public Map<String, Object> prepare(AiConfirmableCapability capability, AiCapabilityInvocation invocation,
        Map<String, Object> input)
    {
        Map<String, Object> safeInput = input == null ? Collections.<String, Object>emptyMap()
            : new LinkedHashMap<String, Object>(input);
        String summary = capability.confirmationSummary(invocation, safeInput);
        if (summary == null || summary.trim().isEmpty()) throw new ServiceException("确认内容不能为空");
        Map<String, Object> details = capability.confirmationDetails(invocation, safeInput);
        Map<String, Object> persistedInput = capability.persistedInput(invocation, safeInput);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("capabilityCode", capability.code()); payload.put("input", persistedInput);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", invocation.getRunId()); row.put("conversationId", invocation.getConversationId());
        row.put("userId", invocation.getActor().getUserId()); row.put("actionCode", ACTION_PREFIX + capability.code());
        row.put("riskLevel", AiCapabilityRisk.CONFIRM_REQUIRED.name());
        row.put("actionPayloadJson", json(payload)); row.put("confirmationSummary", summary);
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ready", true); result.put("actionRequestId", row.get("actionRequestId"));
        result.put("actionCode", row.get("actionCode")); result.put("riskLevel", row.get("riskLevel"));
        result.put("status", "PENDING"); result.put("confirmationSummary", summary);
        result.put("details", details);
        return result;
    }

    public String requiredPermission(String actionCode)
    {
        return requireCapability(actionCode).requiredPermission();
    }

    public Map<String, Object> executeConfirmed(Map<String, Object> action, AiExecutionContext context)
    {
        String actionCode = String.valueOf(action.get("actionCode"));
        AiConfirmableCapability capability = requireCapability(actionCode);
        if (!context.hasPermission(capability.requiredPermission()))
            throw new ServiceException("当前账号没有执行该操作的权限");
        Map<String, Object> payload = map(read(String.valueOf(action.get("actionPayloadJson"))));
        Map<String, Object> input = map(payload.get("input"));
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(context, number(action.get("conversationId")),
            number(action.get("runId")), null);
        return capability.executeConfirmed(invocation, input);
    }

    /** Completes a confirmed generic capability and owns its execution message and audit record. */
    public Map<String, Object> completeConfirmedAction(Map<String, Object> action, AiExecutionContext context)
    {
        Long actionRequestId = number(action.get("actionRequestId"));
        Map<String, Object> executed = executeConfirmed(action, context);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (executed != null) result.putAll(executed);
        result.put("actionRequestId", actionRequestId); result.put("status", "EXECUTED");
        result.put("actionCode", String.valueOf(action.get("actionCode")));
        if (mapper.finishActionRequest(actionRequestId, json(result)) != 1)
            throw new ServiceException("AI 操作状态更新失败");

        Long conversationId = number(action.get("conversationId"));
        Map<String, Object> metadata = new LinkedHashMap<String, Object>(); metadata.put("executedAction", result);
        Map<String, Object> message = new LinkedHashMap<String, Object>(); message.put("conversationId", conversationId);
        message.put("userId", context.getUserId()); message.put("messageRole", "ASSISTANT");
        message.put("content", "操作已确认并执行：" + String.valueOf(action.get("confirmationSummary")));
        message.put("metadataJson", json(metadata)); mapper.insertMessage(message); mapper.touchConversation(conversationId);

        Map<String, Object> audit = new LinkedHashMap<String, Object>(); audit.put("traceId", action.get("traceId"));
        audit.put("conversationId", conversationId); audit.put("runId", action.get("runId"));
        audit.put("userId", context.getUserId()); audit.put("userName", context.getUserName());
        audit.put("eventType", "AI_ACTION_EXECUTED");
        audit.put("summary", "老板确认 AI 系统能力 " + String.valueOf(action.get("actionCode")));
        audit.put("detailJson", json(result)); mapper.insertAudit(audit);
        return result;
    }

    private AiConfirmableCapability requireCapability(String actionCode)
    {
        if (actionCode == null || !actionCode.startsWith(ACTION_PREFIX))
            throw new ServiceException("不是已注册的 AI 能力确认单");
        AiCapability capability;
        try { capability = registry.require(actionCode.substring(ACTION_PREFIX.length())); }
        catch (Exception ex) { throw new ServiceException("确认单对应的系统能力已不存在"); }
        if (!(capability instanceof AiConfirmableCapability)) throw new ServiceException("确认单对应的系统能力不可执行");
        return (AiConfirmableCapability) capability;
    }

    private Object read(String value)
    {
        try { return objectMapper.readValue(value, Object.class); }
        catch (Exception ex) { throw new ServiceException("确认单数据格式异常"); }
    }
    private String json(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new ServiceException("确认单数据序列化失败"); }
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value)
    { return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>(); }
    private Long number(Object value)
    { return value instanceof Number ? ((Number) value).longValue() : value == null ? null : Long.valueOf(String.valueOf(value)); }
}
