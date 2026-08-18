package com.ruoyi.business.ai.capability;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/** Model-driven execution loop. The model chooses; the server authorizes, executes and audits. */
@Service
public class AiCapabilityAgentLoop
{
    private static final int MAX_ROUNDS = 6;
    private final AiCapabilityToolCatalog catalog;
    private final AiCapabilityExecutor executor;
    private final IBusinessAiModelClient modelClient;
    private final BusinessAiMapper mapper;
    private final ObjectMapper objectMapper;
    private final AiCapabilityReferenceGuard referenceGuard = new AiCapabilityReferenceGuard();
    private final AiCapabilityAnswerGuard answerGuard;

    @Autowired
    public AiCapabilityAgentLoop(AiCapabilityToolCatalog catalog, AiCapabilityExecutor executor,
        IBusinessAiModelClient modelClient, BusinessAiMapper mapper, ObjectMapper objectMapper)
    {
        this.catalog = catalog;
        this.executor = executor;
        this.modelClient = modelClient;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.answerGuard = new AiCapabilityAnswerGuard(objectMapper);
    }

    public boolean canHandle(Map<String, Object> plan, AiExecutionContext context)
    {
        List<Map<String, Object>> calls = calls(plan);
        if (calls.isEmpty()) return false;
        for (Map<String, Object> call : calls)
            if (catalog.findAllowedByToolName(text(call.get("name")), context) == null) return false;
        return true;
    }

    public Map<String, Object> run(String question, List<Map<String, Object>> history,
        Map<String, Object> initialPlan, AiCapabilityInvocation invocation)
    {
        return run(question, history, initialPlan, invocation, null);
    }

    public Map<String, Object> run(String question, List<Map<String, Object>> history,
        Map<String, Object> initialPlan, AiCapabilityInvocation invocation,
        List<Map<String, Object>> scopedDefinitions)
    {
        List<Map<String, Object>> definitions = scopedDefinitions == null
            ? catalog.capabilityDefinitions(invocation.getActor()) : scopedDefinitions;
        List<Map<String, Object>> turns = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        Map<String, Map<String, Object>> executed = new LinkedHashMap<String, Map<String, Object>>();
        Map<String, Object> current = initialPlan;
        for (int round = 0; round < MAX_ROUNDS; round++)
        {
            List<Map<String, Object>> calls = calls(current);
            if (calls.isEmpty()) return verifiedOutcome(question, current, results, round);
            List<Map<String, Object>> toolMessages = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> call : calls)
            {
                String toolName = text(call.get("name"));
                if (!toolDefined(toolName, definitions))
                    throw new ServiceException("模型请求了当前工作流范围外的系统能力");
                AiCapability capability = catalog.findAllowedByToolName(toolName, invocation.getActor());
                if (capability == null) throw new ServiceException("模型请求了当前账号不可用的系统能力");
                Map<String, Object> input = map(call.get("arguments"));
                String signature = toolName + ":" + json(input);
                Map<String, Object> data = executed.get(signature);
                if (data == null)
                {
                    data = referenceGuard.validate(input, results);
                    if (data == null) data = executeAndAudit(capability, invocation, input);
                    else auditRejected(capability, invocation, input, data);
                    executed.put(signature, data);
                    if (!Boolean.TRUE.equals(data.get("_retryableToolError")))
                    {
                        Map<String, Object> visible = new LinkedHashMap<String, Object>();
                        visible.put("toolCode", capability.code());
                        visible.put("label", capability.code());
                        visible.put("riskLevel", capability.risk().name());
                        visible.put("sourcePath", "ai-capability://" + capability.code());
                        visible.put("data", data);
                        results.add(visible);
                    }
                }
                if (Boolean.TRUE.equals(data.get("_terminal")))
                    return terminalOutcome(data, results, round + 1);
                Map<String, Object> message = new LinkedHashMap<String, Object>();
                message.put("toolCallId", text(call.get("toolCallId")));
                message.put("content", json(data));
                toolMessages.add(message);
            }
            Map<String, Object> turn = new LinkedHashMap<String, Object>();
            turn.put("assistantMessageJson", current.get("assistantMessageJson"));
            turn.put("toolMessages", toolMessages);
            turns.add(turn);
            current = modelClient.continueWithTools(question, history, turns, definitions);
            if (current == null) throw new ServiceException("模型未返回能力执行结果");
            if (!calls(current).isEmpty() && (!canHandle(current, invocation.getActor())
                || !allToolsDefined(current, definitions)))
                throw new ServiceException("模型在能力执行过程中请求了未授权工具");
        }
        throw new ServiceException("模型连续调用系统能力次数过多，请缩小本次操作范围");
    }

    private Map<String, Object> executeAndAudit(AiCapability capability, AiCapabilityInvocation invocation,
        Map<String, Object> input)
    {
        Date started = new Date();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", invocation.getRunId());
        row.put("conversationId", invocation.getConversationId());
        row.put("userId", invocation.getActor().getUserId());
        row.put("toolCode", capability.code());
        row.put("riskLevel", capability.risk().name());
        row.put("inputJson", json(input));
        row.put("startedTime", started);
        try
        {
            Map<String, Object> output = executor.execute(capability.code(), invocation, input);
            row.put("outputJson", json(output));
            row.put("status", "SUCCEEDED");
            row.put("finishedTime", new Date());
            mapper.insertToolCall(row);
            return output;
        }
        catch (RuntimeException ex)
        {
            row.put("status", "FAILED");
            row.put("errorMessage", StringUtils.substring(ex.getMessage(), 0, 500));
            row.put("finishedTime", new Date());
            mapper.insertToolCall(row);
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean toolDefined(String toolName, List<Map<String, Object>> definitions)
    {
        if (definitions == null) return false;
        for (Map<String, Object> wrapper : definitions)
        {
            Object function = wrapper == null ? null : wrapper.get("function");
            if (function instanceof Map && toolName.equals(text(((Map<String, Object>) function).get("name"))))
                return true;
        }
        return false;
    }

    private boolean allToolsDefined(Map<String, Object> plan, List<Map<String, Object>> definitions)
    {
        for (Map<String, Object> call : calls(plan))
            if (!toolDefined(text(call.get("name")), definitions)) return false;
        return true;
    }

    private void auditRejected(AiCapability capability, AiCapabilityInvocation invocation,
        Map<String, Object> input, Map<String, Object> error)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", invocation.getRunId());
        row.put("conversationId", invocation.getConversationId());
        row.put("userId", invocation.getActor().getUserId());
        row.put("toolCode", capability.code());
        row.put("riskLevel", capability.risk().name());
        row.put("inputJson", json(input));
        row.put("outputJson", json(error));
        row.put("status", "FAILED");
        row.put("errorMessage", StringUtils.substring(text(error.get("message")), 0, 500));
        row.put("startedTime", new Date());
        row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> calls(Map<String, Object> plan)
    {
        Object value = plan == null ? null : plan.get("toolCalls");
        if (!(value instanceof List)) return new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<Object>) value) if (item instanceof Map) result.add((Map<String, Object>) item);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> outcome(Map<String, Object> model, List<Map<String, Object>> results, int rounds)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("content", model.get("content"));
        result.put("usage", model.get("usage"));
        result.put("responseId", model.get("responseId"));
        result.put("toolResults", results);
        result.put("rounds", rounds);
        return result;
    }

    private Map<String, Object> verifiedOutcome(String question, Map<String, Object> model,
        List<Map<String, Object>> results, int rounds)
    {
        Map<String, Object> result = outcome(model, results, rounds);
        String answer = text(result.get("content"));
        AiCapabilityAnswerGuard.Validation validation = answerGuard.validate(answer, results);
        if (validation.isValid())
        {
            result.put("answerValidation", validation.toMap("PASSED"));
            return result;
        }

        String rewritten = null;
        try
        {
            rewritten = modelClient.rewriteGroundedAnswer(question, answer, results, validation.getViolations());
        }
        catch (RuntimeException ignored)
        {
            // A failed correction must never make the business request fail or expose an unverified answer.
        }
        AiCapabilityAnswerGuard.Validation rewriteValidation = answerGuard.validate(rewritten, results);
        if (rewriteValidation.isValid())
        {
            result.put("content", rewritten);
            result.put("answerValidation", validation.toMap("REWRITTEN"));
            return result;
        }

        result.put("content", safeFallback(results));
        Map<String, Object> metadata = validation.toMap("SAFE_FALLBACK");
        metadata.put("rewriteViolations", rewriteValidation.getViolations());
        result.put("answerValidation", metadata);
        return result;
    }

    private String safeFallback(List<Map<String, Object>> results)
    {
        boolean changed = false;
        for (Map<String, Object> result : results)
            if (!"READ_ONLY".equals(text(result.get("riskLevel")))) changed = true;
        return changed
            ? "系统已处理本次操作，但模型生成的说明未通过事实核验。请以页面确认卡和“查看依据”中的系统结果为准。"
            : "系统已读取相关数据，但模型生成的回答未通过事实核验。为避免提供错误信息，请展开“查看依据”查看系统结果。";
    }

    private Map<String, Object> terminalOutcome(Map<String, Object> data,
        List<Map<String, Object>> results, int rounds)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("content", text(data.get("_content")));
        result.put("toolResults", results);
        result.put("rounds", rounds);
        result.put("terminal", true);
        return result;
    }

    private String json(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new ServiceException("AI能力数据序列化失败"); }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
