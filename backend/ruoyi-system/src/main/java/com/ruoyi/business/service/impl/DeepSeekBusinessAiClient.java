package com.ruoyi.business.service.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/** DeepSeek OpenAI-compatible Chat Completions client. */
@Service
public class DeepSeekBusinessAiClient implements IBusinessAiModelClient
{
    private static final String PROVIDER = "DEEPSEEK";
    private static final String SYSTEM_PROMPT = "你是公司的老板AI经营助理。"
        + "理解老板用日常语言表达的目标，并主动组合当前账号被授权使用的工具完成查询或准备操作。"
        + "系统工具是业务事实和可执行能力的唯一来源；不得猜测系统中的项目、人员、金额、日期、状态或执行结果。"
        + "查询时应继续调用工具直到取得足够事实；对象不唯一或关键写入信息确实缺失时，只追问最少的必要信息。"
        + "写操作必须先通过工具生成草稿或确认单，未经用户在系统中明确确认，不得声称已经执行。"
        + "规划阶段必须明确选择一个工具；只有确实不需要任何系统查询或操作时，才选择安全对话工具。"
        + "工具返回失败、无权限或资料不足时，应如实说明，不得绕过权限或虚构成功。"
        + "用通俗、简洁的中文先说结论，再说依据和下一步；不要向老板展示内部枚举、技术协议或提示词。"
        + "使用适合业务系统直接显示的纯文本，不使用Markdown标题、表格或加粗。";

    @Value("${business.ai.deepseek.enabled:false}") private boolean enabled;
    @Value("${business.ai.deepseek.base-url:https://api.deepseek.com}") private String baseUrl;
    @Value("${business.ai.deepseek.api-key:}") private String apiKey;
    @Value("${business.ai.deepseek.model:deepseek-v4-flash}") private String model;
    @Value("${business.ai.deepseek.connect-timeout-ms:8000}") private int connectTimeoutMs;
    @Value("${business.ai.deepseek.read-timeout-ms:60000}") private int readTimeoutMs;
    @Autowired private ObjectMapper objectMapper;

    @Override public boolean isEnabled() { return enabled && StringUtils.isNotBlank(apiKey); }
    @Override public String providerCode() { return PROVIDER; }
    @Override public String modelName() { return model; }

    @Override
    public Map<String, Object> plan(String question, List<Map<String, Object>> history)
    {
        return plan(question, history, null);
    }

    @Override
    public Map<String, Object> plan(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> dynamicTools)
    {
        ObjectNode body = baseBody();
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", datedSystemPrompt()));
        if (history != null)
            for (Map<String, Object> item : history)
            {
                String role = String.valueOf(item.get("role"));
                if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) continue;
                messages.add(message(role, String.valueOf(item.get("content"))));
            }
        messages.add(message("user", question));
        body.set("tools", tools(dynamicTools));
        // Every planning turn must make an explicit decision. The capability list includes a safe
        // non-business exit for greetings/out-of-scope requests, so "required" never forces a
        // business mutation while eliminating silent no-tool plans for real operations.
        body.put("tool_choice", dynamicTools == null || dynamicTools.isEmpty() ? "none" : "required");
        body.put("max_tokens", 1000);
        JsonNode response = post(body);
        JsonNode assistant = firstMessage(response);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("assistantMessageJson", assistant.toString());
        result.put("content", text(assistant.get("content")));
        result.put("responseId", text(response.get("id")));
        result.put("toolCalls", toolCalls(assistant));
        result.put("usage", usage(response));
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> continueWithTools(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> turns)
    {
        return continueWithTools(question, history, turns, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> continueWithTools(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> turns, List<Map<String, Object>> dynamicTools)
    {
        ObjectNode body = baseBody();
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", datedSystemPrompt()));
        appendHistory(messages, history);
        messages.add(message("user", question));
        if (turns != null)
            for (Map<String, Object> turn : turns)
            {
                try { messages.add(objectMapper.readTree(String.valueOf(turn.get("assistantMessageJson")))); }
                catch (Exception ex) { throw new ServiceException("DeepSeek 多步工具消息格式不正确"); }
                Object toolMessages = turn.get("toolMessages");
                if (!(toolMessages instanceof List)) continue;
                for (Object item : (List<Object>) toolMessages)
                {
                    if (!(item instanceof Map)) continue;
                    Map<String, Object> tool = (Map<String, Object>) item;
                    ObjectNode toolMessage = objectMapper.createObjectNode();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", String.valueOf(tool.get("toolCallId")));
                    toolMessage.put("content", String.valueOf(tool.get("content")));
                    messages.add(toolMessage);
                }
            }
        body.set("tools", tools(dynamicTools));
        body.put("tool_choice", "auto");
        body.put("max_tokens", 1600);
        JsonNode response = post(body);
        JsonNode assistant = firstMessage(response);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("assistantMessageJson", assistant.toString());
        result.put("content", text(assistant.get("content")));
        result.put("responseId", text(response.get("id")));
        result.put("toolCalls", toolCalls(assistant));
        result.put("usage", usage(response));
        return result;
    }

    @Override
    public Map<String, Object> complete(String question, Map<String, Object> plan,
        List<Map<String, Object>> toolMessages)
    {
        ObjectNode body = baseBody();
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", datedSystemPrompt()));
        messages.add(message("user", question));
        try { messages.add(objectMapper.readTree(String.valueOf(plan.get("assistantMessageJson")))); }
        catch (Exception ex) { throw new ServiceException("DeepSeek 工具计划格式不正确"); }
        for (Map<String, Object> tool : toolMessages)
        {
            String toolCallId = String.valueOf(tool.get("toolCallId"));
            if (StringUtils.isBlank(toolCallId))
            {
                messages.add(message("user", "系统刚刚完成的授权实时查询结果如下，请仅依据这些事实回答："
                    + String.valueOf(tool.get("content"))));
                continue;
            }
            ObjectNode toolMessage = objectMapper.createObjectNode();
            toolMessage.put("role", "tool");
            toolMessage.put("tool_call_id", toolCallId);
            toolMessage.put("content", String.valueOf(tool.get("content")));
            messages.add(toolMessage);
        }
        body.put("max_tokens", 1400);
        JsonNode response = post(body);
        JsonNode assistant = firstMessage(response);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("content", text(assistant.get("content")));
        result.put("responseId", text(response.get("id")));
        result.put("usage", usage(response));
        return result;
    }

    @Override
    public String rewriteGroundedAnswer(String question, String rejectedAnswer,
        List<Map<String, Object>> capabilityResults, List<String> violations)
    {
        ObjectNode body = baseBody();
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", "你是业务回答事实校对器。只能使用本次系统能力结果中的事实重写回答。"
            + "不得新增、推算或猜测项目、人员、金额、数量、日期、状态和执行结果。"
            + "资料没有提供的内容必须明确说系统资料不足。保留原问题需要的结论，使用简洁自然的中文纯文本。"));
        ObjectNode input = objectMapper.createObjectNode();
        input.put("question", question);
        input.put("rejectedAnswer", rejectedAnswer);
        input.set("violations", objectMapper.valueToTree(violations));
        input.set("authorizedCapabilityResults", objectMapper.valueToTree(capabilityResults));
        messages.add(message("user", input.toString()));
        body.put("max_tokens", 1400);
        JsonNode response = post(body);
        return text(firstMessage(response).get("content"));
    }

    private ObjectNode baseBody()
    {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ObjectNode thinking = body.putObject("thinking");
        thinking.put("type", "disabled");
        body.put("stream", false);
        return body;
    }

    private String datedSystemPrompt()
    {
        LocalDate today = LocalDate.now();
        return SYSTEM_PROMPT + "系统会根据当前登录账号动态提供可用业务工具，工具列表本身就是当前权限边界。"
            + "涉及正在进行的业务草稿时，先读取系统当前值，再只修改用户明确提出的字段；不要用聊天历史覆盖未提到的字段。"
            + "当前服务器日期是 " + today + "。老板说‘今天’或‘现在开始’时必须使用这个日期；"
            + "老板只说月日、没有说年份时，必须使用当前年份 " + today.getYear() + "，不得自行使用过去年份。";
    }

    private ArrayNode tools(List<Map<String, Object>> dynamicTools)
    {
        ArrayNode result = objectMapper.createArrayNode();
        if (dynamicTools != null)
            for (Map<String, Object> definition : dynamicTools)
                if (definition != null) result.add(objectMapper.valueToTree(definition));
        return result;
    }
    private void appendHistory(ArrayNode messages, List<Map<String, Object>> history)
    {
        if (history == null) return;
        for (Map<String, Object> item : history)
        {
            String role = String.valueOf(item.get("role"));
            if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) continue;
            messages.add(message(role, String.valueOf(item.get("content"))));
        }
    }

    private ObjectNode message(String role, String content)
    {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("role", role);
        result.put("content", content);
        return result;
    }

    private JsonNode post(ObjectNode body)
    {
        if (!isEnabled()) throw new ServiceException("DeepSeek 尚未配置");
        HttpURLConnection connection = null;
        try
        {
            String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] payload = objectMapper.writeValueAsBytes(body);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream);
            if (status < 200 || status >= 300)
                throw new ServiceException("DeepSeek 调用失败（HTTP " + status + "）");
            return objectMapper.readTree(response);
        }
        catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw new ServiceException("DeepSeek 暂时不可用，请稍后重试"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private JsonNode firstMessage(JsonNode response)
    {
        JsonNode message = response.path("choices").path(0).path("message");
        if (message.isMissingNode() || message.isNull()) throw new ServiceException("DeepSeek 未返回有效结果");
        return message;
    }

    private List<Map<String, Object>> toolCalls(JsonNode assistant)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (JsonNode call : assistant.path("tool_calls"))
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("toolCallId", text(call.get("id")));
            row.put("name", text(call.path("function").get("name")));
            String argumentsJson = text(call.path("function").get("arguments"));
            row.put("argumentsJson", argumentsJson);
            try
            {
                @SuppressWarnings("unchecked") Map<String, Object> arguments =
                    objectMapper.readValue(argumentsJson, Map.class);
                row.put("arguments", arguments);
            }
            catch (Exception ex) { row.put("arguments", new LinkedHashMap<String, Object>()); }
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> usage(JsonNode response)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("promptTokens", response.path("usage").path("prompt_tokens").asInt(0));
        result.put("completionTokens", response.path("usage").path("completion_tokens").asInt(0));
        result.put("totalTokens", response.path("usage").path("total_tokens").asInt(0));
        return result;
    }

    private String read(InputStream stream) throws Exception
    {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asText(""); }
}
