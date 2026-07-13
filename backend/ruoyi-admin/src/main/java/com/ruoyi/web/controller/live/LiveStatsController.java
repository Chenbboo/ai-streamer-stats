package com.ruoyi.web.controller.live;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.live.domain.LiveStreamer;
import com.ruoyi.live.service.ILiveStatsService;
import com.ruoyi.live.service.ILiveStreamerService;
import com.ruoyi.system.service.ISysConfigService;

@RestController
@RequestMapping("/live/stats")
public class LiveStatsController extends BaseController
{
    @Autowired
    private ILiveStatsService statsService;

    @Autowired
    private ILiveStreamerService streamerService;

    @Autowired
    private ISysConfigService configService;

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/weekly")
    public AjaxResult weekly(String beginDate, String endDate, Long streamerId)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.minusDays(6) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long effectiveStreamerId = own == null ? streamerId : own.getStreamerId();
        return AjaxResult.success(statsService.getWeeklyStats(begin.toString(), end.toString(), effectiveStreamerId));
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/streamer-card")
    public AjaxResult streamerCard(Long streamerId, String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long effectiveStreamerId = own == null ? streamerId : own.getStreamerId();
        return AjaxResult.success(statsService.getStreamerCardDetail(effectiveStreamerId, begin.toString(), end.toString()));
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/high-value-users")
    public AjaxResult highValueUsers(Long streamerId, String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long effectiveStreamerId = own == null ? streamerId : own.getStreamerId();
        return AjaxResult.success(statsService.getHighValueUsers(effectiveStreamerId, begin.toString(), end.toString()));
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/new-tippers")
    public AjaxResult newTippers(Long streamerId, String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long effectiveStreamerId = own == null ? streamerId : own.getStreamerId();
        return AjaxResult.success(statsService.getNewTippers(effectiveStreamerId, begin.toString(), end.toString()));
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/weiji")
    public AjaxResult weiji(String date)
    {
        if (StringUtils.isEmpty(date))
        {
            date = LocalDate.now().minusDays(1).toString();
        }
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long streamerId = own == null ? null : own.getStreamerId();
        List<Map<String, Object>> data = statsService.getWeijiStats(date);
        if (streamerId != null)
        {
            data = data.stream().filter(d -> streamerId.equals(d.get("streamerId"))).collect(java.util.stream.Collectors.toList());
        }
        return AjaxResult.success(data);
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/weiji-month")
    public AjaxResult weijiMonth(String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long streamerId = own == null ? null : own.getStreamerId();
        List<Map<String, Object>> data = statsService.getWeijiMonthStats(begin.toString(), end.toString());
        if (streamerId != null)
        {
            data = data.stream().filter(d -> streamerId.equals(d.get("streamerId"))).collect(java.util.stream.Collectors.toList());
        }
        return AjaxResult.success(data);
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/weiji-detail")
    public AjaxResult weijiDetail(Long streamerId, String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        if (own != null) streamerId = own.getStreamerId();
        return AjaxResult.success(statsService.getWeijiDetail(streamerId, begin.toString(), end.toString()));
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/advice")
    public AjaxResult advice(String beginDate, String endDate)
    {
        LocalDate end = StringUtils.isEmpty(endDate) ? LocalDate.now().minusDays(1) : LocalDate.parse(endDate);
        LocalDate begin = StringUtils.isEmpty(beginDate) ? end.withDayOfMonth(1) : LocalDate.parse(beginDate);
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long streamerId = own == null ? null : own.getStreamerId();
        List<Map<String, Object>> data = statsService.getAdviceData(begin.toString(), end.toString());
        if (streamerId != null)
        {
            data = data.stream().filter(d -> streamerId.equals(d.get("streamerId"))).collect(java.util.stream.Collectors.toList());
        }
        return AjaxResult.success(data);
    }

    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @PostMapping("/chat")
    public void chat(@RequestBody Map<String, Object> body, HttpServletResponse response) throws Exception
    {
        if (body.get("streamerId") == null || body.get("message") == null)
        {
            throw new ServiceException("streamerId and message are required");
        }
        LiveStreamer own = getOwnStreamerIfRestricted();
        Long streamerId = own == null ? Long.valueOf(body.get("streamerId").toString()) : own.getStreamerId();
        String message = body.get("message").toString();
        List<Map<String, Object>> history = (List<Map<String, Object>>) body.getOrDefault("history", new java.util.ArrayList<>());

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        String basicContext = buildBasicContext(streamerId);

        // 鏋勫缓娑堟伅鍒楄〃
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个直播运营数据分析师。你可以通过调用工具查询主播的详细数据。\n\n"
            + "基础数据：\n" + basicContext + "\n\n"
            + "当用户询问具体粉丝、聊天记录、打赏明细等信息时，请调用相应工具获取数据。");
        messages.add(systemMsg);
        for (Map<String, Object> h : history)
        {
            Map<String, Object> histMsg = new HashMap<>();
            histMsg.put("role", h.get("role").toString());
            histMsg.put("content", h.get("content").toString());
            messages.add(histMsg);
        }
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        messages.add(userMsg);

        // 瀹氫箟宸ュ叿
        List<Map<String, Object>> tools = buildTools(streamerId);

        // 璋冪敤 AI API锛堜娇鐢ㄨ亰澶╀笓鐢ㄩ厤缃級
        String apiKey = configService.selectConfigByKey("live.ai.chat.apiKey");
        String baseUrl = configService.selectConfigByKey("live.ai.chat.baseUrl");
        String model = configService.selectConfigByKey("live.ai.chat.model");

        if (StringUtils.isEmpty(apiKey) || StringUtils.isEmpty(baseUrl))
        {
            response.getWriter().write("data: AI鏈厤缃甛n\n");
            response.getWriter().flush();
            return;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("tools", tools);
        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.7);

        String firstResponse = callAI(baseUrl, apiKey, requestBody);
        if (firstResponse == null)
        {
            response.getWriter().write("data: AI璇锋眰澶辫触\n\n");
            response.getWriter().flush();
            return;
        }

        // 瑙ｆ瀽鍝嶅簲锛屾鏌ユ槸鍚︽湁宸ュ叿璋冪敤
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(firstResponse);
        com.fasterxml.jackson.databind.JsonNode choices = responseNode.path("choices");
        if (choices.isArray() && choices.size() > 0)
        {
            com.fasterxml.jackson.databind.JsonNode messageNode = choices.get(0).path("message");
            com.fasterxml.jackson.databind.JsonNode toolCalls = messageNode.path("tool_calls");

            if (toolCalls.isArray() && toolCalls.size() > 0)
            {
                messages.add(mapper.convertValue(messageNode, Map.class));

                for (com.fasterxml.jackson.databind.JsonNode toolCall : toolCalls)
                {
                    String functionName = toolCall.path("function").path("name").asText();
                    String args = toolCall.path("function").path("arguments").asText();
                    String toolCallId = toolCall.path("id").asText();

                    // 鎵ц宸ュ叿
                    String toolResult = executeTool(functionName, args, streamerId);

                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }

                requestBody.put("messages", messages);
                requestBody.remove("tools");
                requestBody.put("stream", true);

                streamAIResponse(baseUrl, apiKey, requestBody, response);
                return;
            }
        }

        requestBody.put("stream", true);
        streamAIResponse(baseUrl, apiKey, requestBody, response);
    }

    private String buildBasicContext(Long streamerId)
    {
        StringBuilder sb = new StringBuilder();
        LocalDate end = LocalDate.now().minusDays(1);
        List<Map<String, Object>> details = statsService.getStreamerCardDetail(streamerId, end.withDayOfMonth(1).toString(), end.toString());
        if (!details.isEmpty())
        {
            Map<String, Object> d = details.get(0);
            sb.append("涓绘挱: ").append(d.get("stageName")).append("\n");
            sb.append("鏈湀娴佹按: ").append(d.get("monthlyXu")).append("\n");
            sb.append("鏈懆娴佹按: ").append(d.get("weeklyXu")).append("\n");
            sb.append("鏄ㄦ棩娴佹按: ").append(d.get("dailyXu")).append("\n");
            sb.append("鏈湀鎵撹祻瀹㈡埛鏁? ").append(d.get("monthlyCustomers")).append("\n");
            sb.append("鏈湀鑱婂ぉ瀹㈡埛鏁? ").append(d.get("chatMonthly")).append("\n");
            sb.append("涓珮绾х敤鎴锋暟: ").append(d.get("highValueCustomers")).append("\n");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> buildTools(Long streamerId)
    {
        List<Map<String, Object>> tools = new java.util.ArrayList<>();

        // 鏌ヨ鎵撹祻瀹㈡埛宸ュ叿
        Map<String, Object> tipTool = new HashMap<>();
        tipTool.put("type", "function");
        Map<String, Object> tipFunc = new HashMap<>();
        tipFunc.put("name", "query_tip_customers");
        tipFunc.put("description", "查询主播的打赏客户列表，包括客户昵称和打赏金额");
        Map<String, Object> tipParams = new HashMap<>();
        tipParams.put("type", "object");
        tipParams.put("properties", new HashMap<>());
        tipFunc.put("parameters", tipParams);
        tipTool.put("function", tipFunc);
        tools.add(tipTool);

        // 鏌ヨ鑱婂ぉ浜掑姩绮変笣宸ュ叿
        Map<String, Object> chatTool = new HashMap<>();
        chatTool.put("type", "function");
        Map<String, Object> chatFunc = new HashMap<>();
        chatFunc.put("name", "query_chat_fans");
        chatFunc.put("description", "鏌ヨ涓绘挱鏈€杩戣亰澶╀簰鍔ㄧ殑绮変笣鍒楄〃");
        Map<String, Object> chatParams = new HashMap<>();
        chatParams.put("type", "object");
        chatParams.put("properties", new HashMap<>());
        chatFunc.put("parameters", chatParams);
        chatTool.put("function", chatFunc);
        tools.add(chatTool);

        // 鏌ヨ鑱婂ぉ鍐呭宸ュ叿
        Map<String, Object> chatContentTool = new HashMap<>();
        chatContentTool.put("type", "function");
        Map<String, Object> chatContentFunc = new HashMap<>();
        chatContentFunc.put("name", "query_chat_content");
        chatContentFunc.put("description", "鏌ヨ涓绘挱涓庣矇涓濈殑鍏蜂綋鑱婂ぉ鍐呭锛屽寘鎷彂閫佹柟銆佹秷鎭被鍨嬪拰娑堟伅鍐呭");
        Map<String, Object> chatContentParams = new HashMap<>();
        chatContentParams.put("type", "object");
        chatContentParams.put("properties", new HashMap<>());
        chatContentFunc.put("parameters", chatContentParams);
        chatContentTool.put("function", chatContentFunc);
        tools.add(chatContentTool);

        // 鏌ヨ姣忔棩娴佹按宸ュ叿
        Map<String, Object> trendTool = new HashMap<>();
        trendTool.put("type", "function");
        Map<String, Object> trendFunc = new HashMap<>();
        trendFunc.put("name", "query_daily_trend");
        trendFunc.put("description", "鏌ヨ涓绘挱鏈€杩戠殑姣忔棩娴佹按璧板娍鏁版嵁");
        Map<String, Object> trendParams = new HashMap<>();
        trendParams.put("type", "object");
        trendParams.put("properties", new HashMap<>());
        trendFunc.put("parameters", trendParams);
        trendTool.put("function", trendFunc);
        tools.add(trendTool);

        return tools;
    }

    private String executeTool(String functionName, String args, Long streamerId)
    {
        try
        {
            if ("query_tip_customers".equals(functionName))
            {
                List<Map<String, Object>> data = statsService.getRecentTipRecords(streamerId, 20);
                StringBuilder sb = new StringBuilder("鎵撹祻瀹㈡埛鍒楄〃锛歕n");
                for (Map<String, Object> row : data)
                {
                    sb.append("- ").append(row.get("nickname")).append(": ").append(row.get("xu")).append("xu\n");
                }
                return sb.toString();
            }
            else if ("query_chat_fans".equals(functionName))
            {
                List<Map<String, Object>> data = statsService.getRecentChatRecords(streamerId, 20);
                StringBuilder sb = new StringBuilder("鑱婂ぉ浜掑姩绮変笣锛歕n");
                java.util.Set<String> names = new java.util.LinkedHashSet<>();
                for (Map<String, Object> row : data)
                {
                    names.add(String.valueOf(row.get("nickname")));
                }
                sb.append(String.join(", ", names));
                return sb.toString();
            }
            else if ("query_chat_content".equals(functionName))
            {
                List<Map<String, Object>> data = statsService.getChatContent(streamerId, 20);
                StringBuilder sb = new StringBuilder("鑱婂ぉ璁板綍璇︽儏锛歕n");
                for (Map<String, Object> row : data)
                {
                    String aiResult = String.valueOf(row.get("aiResult"));
                    if (aiResult != null && !aiResult.equals("null"))
                    {
                        try
                        {
                            com.fasterxml.jackson.databind.JsonNode resultNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(aiResult);
                            com.fasterxml.jackson.databind.JsonNode items = resultNode.path("items");
                            if (items.isArray())
                            {
                                for (com.fasterxml.jackson.databind.JsonNode item : items)
                                {
                                    String nickname = item.path("nickname").asText("");
                                    com.fasterxml.jackson.databind.JsonNode messages = item.path("messages");
                                    if (messages.isArray())
                                    {
                                        sb.append("\n绮変笣 ").append(nickname).append(" 鐨勮亰澶╋細\n");
                                        for (com.fasterxml.jackson.databind.JsonNode msg : messages)
                                        {
                                            String sender = msg.path("sender").asText("");
                                            String content = msg.path("content").asText("");
                                            String type = msg.path("messageType").asText("text");
                                            sb.append("  ").append(sender).append(": ").append(content);
                                            if (!"text".equals(type))
                                            {
                                                sb.append(" [").append(type).append("]");
                                            }
                                            sb.append("\n");
                                        }
                                    }
                                }
                            }
                        }
                        catch (Exception ignore) {}
                    }
                }
                return sb.toString();
            }
            else if ("query_daily_trend".equals(functionName))
            {
                LocalDate end = LocalDate.now().minusDays(1);
                List<Map<String, Object>> data = statsService.getStreamerCardDetail(streamerId, end.withDayOfMonth(1).toString(), end.toString());
                if (!data.isEmpty())
                {
                    Map<String, Object> d = data.get(0);
                    return "鏈湀娴佹按: " + d.get("monthlyXu") + ", 鏈懆: " + d.get("weeklyXu") + ", 鏄ㄦ棩: " + d.get("dailyXu");
                }
                return "无数据";
            }
            return "鏈煡宸ュ叿: " + functionName;
        }
        catch (Exception e)
        {
            return "宸ュ叿鎵ц澶辫触: " + e.getMessage();
        }
    }

    private String callAI(String baseUrl, String apiKey, Map<String, Object> requestBody)
    {
        try
        {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(baseUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody);
            try (java.io.OutputStream os = conn.getOutputStream())
            {
                os.write(body);
            }

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
            {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")))
                {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private void streamAIResponse(String baseUrl, String apiKey, Map<String, Object> requestBody, HttpServletResponse response) throws Exception
    {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(baseUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        byte[] body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody);
        try (java.io.OutputStream os = conn.getOutputStream())
        {
            os.write(body);
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
        {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith("data: "))
                    {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data))
                        {
                            response.getWriter().write("data: [DONE]\n\n");
                            response.getWriter().flush();
                            break;
                        }
                        try
                        {
                            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
                            String content = node.path("choices").path(0).path("delta").path("content").asText("");
                            if (!content.isEmpty())
                            {
                                Map<String, Object> event = new HashMap<>();
                                event.put("delta", content);
                                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
                                response.getWriter().write("data: " + json + "\n\n");
                                response.getWriter().flush();
                            }
                        }
                        catch (Exception ignore) {}
                    }
                }
            }
        }
        else
        {
            response.getWriter().write("data: AI璇锋眰澶辫触\n\n");
            response.getWriter().flush();
        }
    }

    private LiveStreamer getOwnStreamerIfRestricted()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user.isAdmin())
        {
            return null;
        }
        boolean hasElevated = false;
        boolean isStreamer = false;
        for (SysRole role : user.getRoles())
        {
            String key = role.getRoleKey();
            if ("operator".equals(key) || "live_admin".equals(key))
            {
                hasElevated = true;
            }
            if ("streamer".equals(key))
            {
                isStreamer = true;
            }
        }
        if (hasElevated || !isStreamer)
        {
            return null;
        }
        LiveStreamer own = streamerService.selectLiveStreamerByUserId(user.getUserId());
        if (own == null)
        {
            throw new ServiceException("褰撳墠璐﹀彿鏈粦瀹氫富鎾俊鎭紝璇疯仈绯荤鐞嗗憳");
        }
        return own;
    }
}
