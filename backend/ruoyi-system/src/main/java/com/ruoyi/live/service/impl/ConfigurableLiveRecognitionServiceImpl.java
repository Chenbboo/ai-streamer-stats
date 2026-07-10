package com.ruoyi.live.service.impl;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.service.ILiveRecognitionService;
import com.ruoyi.system.service.ISysConfigService;

@Primary
@Service
public class ConfigurableLiveRecognitionServiceImpl implements ILiveRecognitionService
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROVIDER_MOCK = "mock";
    private static final String PROVIDER_OPENAI_RESPONSES = "openai-responses";
    private static final String PROVIDER_OPENAI_COMPATIBLE_CHAT = "openai-compatible-chat";

    @Autowired
    private ISysConfigService configService;

    @Override
    public String recognize(LiveUpload upload)
    {
        if (!isEnabled())
        {
            return mockRecognize(upload);
        }

        String provider = configValue("live.ai.provider", PROVIDER_OPENAI_COMPATIBLE_CHAT);
        if (PROVIDER_MOCK.equalsIgnoreCase(provider))
        {
            return mockRecognize(upload);
        }

        String apiKey = configValue("live.ai.apiKey", "");
        if (StringUtils.isEmpty(apiKey))
        {
            return mockRecognize(upload);
        }

        if (PROVIDER_OPENAI_RESPONSES.equalsIgnoreCase(provider) || "openai".equalsIgnoreCase(provider))
        {
            return recognizeWithResponsesApi(upload, apiKey);
        }
        if (PROVIDER_OPENAI_COMPATIBLE_CHAT.equalsIgnoreCase(provider)
                || "compatible".equalsIgnoreCase(provider)
                || "chat-completions".equalsIgnoreCase(provider))
        {
            return recognizeWithChatCompletions(upload, apiKey);
        }

        throw new ServiceException("Unsupported AI provider: " + provider);
    }

    private boolean isEnabled()
    {
        String value = configValue("live.ai.enabled", "false");
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String recognizeWithResponsesApi(LiveUpload upload, String apiKey)
    {
        String endpoint = configValue("live.ai.baseUrl", "https://api.openai.com/v1/responses");
        Map<String, Object> request = new HashMap<>();
        request.put("model", configValue("live.ai.model", "gpt-5.5"));
        request.put("input", buildResponsesInput(upload));
        request.put("max_output_tokens", 1200);
        return parseAndNormalizeJson(callModel(endpoint, apiKey, request), "responses");
    }

    private String recognizeWithChatCompletions(LiveUpload upload, String apiKey)
    {
        String endpoint = configValue("live.ai.baseUrl", "https://api.openai.com/v1/chat/completions");
        Map<String, Object> request = new HashMap<>();
        request.put("model", configValue("live.ai.model", "gpt-4o-mini"));
        request.put("messages", buildChatMessages(upload));
        request.put("max_tokens", 8000);
        request.put("temperature", 0);
        return parseAndNormalizeJson(callModel(endpoint, apiKey, request), "chat");
    }

    private String callModel(String endpoint, String apiKey, Map<String, Object> request)
    {
        try
        {
            int timeout = Integer.parseInt(configValue("live.ai.timeout", "60")) * 1000;
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] body = OBJECT_MAPPER.writeValueAsBytes(request);
            try (OutputStream os = connection.getOutputStream())
            {
                os.write(body);
            }

            int status = connection.getResponseCode();
            String responseText = readResponse(connection, status);
            if (status < 200 || status >= 300)
            {
                throw new ServiceException("AI provider failed: " + responseText);
            }
            return responseText;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("AI provider request error: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildResponsesInput(LiveUpload upload)
    {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> text = new HashMap<>();
        text.put("type", "input_text");
        text.put("text", buildPrompt(upload));
        content.add(text);

        addResponsesImage(upload, content);
        message.put("content", content);
        input.add(message);
        return input;
    }

    private List<Map<String, Object>> buildChatMessages(LiveUpload upload)
    {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", buildPrompt(upload));
        content.add(text);

        addChatImage(upload, content);
        message.put("content", content);
        messages.add(message);
        return messages;
    }

    private void addResponsesImage(LiveUpload upload, List<Map<String, Object>> content)
    {
        if (StringUtils.isEmpty(upload.getFilePath()))
        {
            return;
        }
        Map<String, Object> image = new HashMap<>();
        image.put("type", "input_image");
        image.put("image_url", toDataUrl(upload.getFilePath()));
        content.add(image);
    }

    private void addChatImage(LiveUpload upload, List<Map<String, Object>> content)
    {
        if (StringUtils.isEmpty(upload.getFilePath()))
        {
            return;
        }
        Map<String, Object> image = new HashMap<>();
        image.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", toDataUrl(upload.getFilePath()));
        image.put("image_url", imageUrl);
        content.add(image);
    }

    private String buildPrompt(LiveUpload upload)
    {
        String schema;
        if (LiveUpload.TYPE_GIFT.equals(upload.getUploadType()))
        {
            schema = "{\"type\":\"gift\",\"provider\":\"model\",\"items\":[{\"rankNo\":1,\"nickname\":\"name\",\"xu\":1234,\"badge\":\"\",\"confidence\":\"normal\"}]}";
        }
        else if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            schema = "{\"type\":\"chat\",\"provider\":\"model\",\"items\":[{\"nickname\":\"name\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"message text\"}],\"confidence\":\"normal\"}]}";
        }
        else
        {
            schema = "{\"type\":\"report\",\"provider\":\"model\",\"totalXu\":1234,\"rawText\":\"original text\"}";
        }
        String instruction = "You are a live-stream operations OCR assistant. Extract structured data from the submitted screenshot or report text. "
                + "Return valid JSON only, no markdown and no explanation. Target schema: " + schema;
        if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            instruction += ". For chat screenshots: extract each visible message with its sender (customer or streamer). "
                    + "Preserve the original language (Vietnamese/Korean/Chinese/emoji). "
                    + "Group messages by customer nickname. Include ALL visible messages, do not summarize. "
                    + "For text messages: set messageType='text', put the text in content. "
                    + "For video/image/audio messages: look at the thumbnail, describe what you see in content (e.g. '[视频:主播在跳舞]', '[图片:自拍]'). "
                    + "Set messageType to 'video'/'image'/'audio' accordingly.";
        }
        instruction += ". If a field is unclear, use empty string or 0 and set confidence to low. "
                + "Report text: " + (upload.getRawText() == null ? "" : upload.getRawText());
        return instruction;
    }

    private String parseAndNormalizeJson(String responseText, String apiType)
    {
        String output;
        try
        {
            output = "responses".equals(apiType) ? extractResponsesOutputText(responseText) : extractChatOutputText(responseText);
        }
        catch (Exception e)
        {
            throw new ServiceException("Failed to extract AI output: " + e.getMessage());
        }
        output = cleanJson(output);
        try
        {
            JsonNode parsed = OBJECT_MAPPER.readTree(output);
            return OBJECT_MAPPER.writeValueAsString(parsed);
        }
        catch (Exception e)
        {
            // JSON may be truncated due to max_tokens, try to auto-close brackets
            String fixed = autoCloseJson(output);
            try
            {
                JsonNode parsed = OBJECT_MAPPER.readTree(fixed);
                return OBJECT_MAPPER.writeValueAsString(parsed);
            }
            catch (Exception e2)
            {
                throw new ServiceException("AI response is not valid JSON: " + e.getMessage());
            }
        }
    }

    /** Try to auto-close truncated JSON by adding missing closing brackets */
    private String autoCloseJson(String json)
    {
        if (json == null)
        {
            return "{}";
        }
        String s = json.trim();
        // Track open brackets
        int curly = 0, square = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (escaped)
            {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString)
            {
                escaped = true;
                continue;
            }
            if (c == '"')
            {
                inString = !inString;
                continue;
            }
            if (inString)
            {
                continue;
            }
            if (c == '{') curly++;
            else if (c == '}') curly--;
            else if (c == '[') square++;
            else if (c == ']') square--;
        }
        // If we're inside a string, close it first
        if (inString)
        {
            s += "\"";
        }
        // Close any incomplete key-value pair by removing trailing partial
        // Then add missing closing brackets
        StringBuilder sb = new StringBuilder(s);
        // Remove trailing comma or partial value if any
        String trimmed = sb.toString().trim();
        if (trimmed.endsWith(",") || trimmed.endsWith(":"))
        {
            sb = new StringBuilder(trimmed.substring(0, trimmed.length() - 1).trim());
        }
        while (square-- > 0) sb.append(']');
        while (curly-- > 0) sb.append('}');
        return sb.toString();
    }

    private String extractResponsesOutputText(String responseText) throws Exception
    {
        JsonNode root = OBJECT_MAPPER.readTree(responseText);
        if (root.hasNonNull("output_text"))
        {
            return root.get("output_text").asText();
        }
        JsonNode output = root.path("output");
        if (output.isArray())
        {
            for (JsonNode item : output)
            {
                JsonNode content = item.path("content");
                if (content.isArray())
                {
                    for (JsonNode part : content)
                    {
                        if (part.hasNonNull("text"))
                        {
                            return part.get("text").asText();
                        }
                    }
                }
            }
        }
        throw new ServiceException("No text found in Responses API result");
    }

    private String extractChatOutputText(String responseText) throws Exception
    {
        JsonNode root = OBJECT_MAPPER.readTree(responseText);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0)
        {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual())
            {
                return content.asText();
            }
        }
        throw new ServiceException("No text found in Chat Completions result");
    }

    private String cleanJson(String text)
    {
        String value = text.trim();
        if (value.startsWith("```"))
        {
            value = value.replaceFirst("^```json", "").replaceFirst("^```", "");
            int end = value.lastIndexOf("```");
            if (end >= 0)
            {
                value = value.substring(0, end);
            }
        }
        return value.trim();
    }

    private String toDataUrl(String filePath)
    {
        try
        {
            if (!filePath.startsWith(Constants.RESOURCE_PREFIX))
            {
                throw new ServiceException("Image path is outside upload directory");
            }
            File file = new File(RuoYiConfig.getProfile() + StringUtils.substringAfter(filePath, Constants.RESOURCE_PREFIX));
            if (!file.exists())
            {
                throw new ServiceException("Image file does not exist: " + filePath);
            }
            String mime = mimeType(file.getName());
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
            return "data:" + mime + ";base64," + base64;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("Unable to read image file: " + e.getMessage());
        }
    }

    private String mimeType(String fileName)
    {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))
        {
            return "image/png";
        }
        if (lower.endsWith(".webp"))
        {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String readResponse(HttpURLConnection connection, int status) throws Exception
    {
        try (Scanner scanner = new Scanner(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream(), "UTF-8"))
        {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private String mockRecognize(LiveUpload upload)
    {
        if (LiveUpload.TYPE_GIFT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"gift\",\"provider\":\"mock\",\"items\":[{\"rankNo\":1,\"nickname\":\"MockTopFan\",\"xu\":8888,\"badge\":\"MWRM\",\"confidence\":\"normal\"},{\"rankNo\":2,\"nickname\":\"DemoBuyer\",\"xu\":6666,\"badge\":\"\",\"confidence\":\"normal\"}]}";
        }
        if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"chat\",\"provider\":\"mock\",\"items\":[{\"nickname\":\"MockTopFan\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"mock reply\"}],\"confidence\":\"normal\"},{\"nickname\":\"DemoBuyer\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"demo reply\"}],\"confidence\":\"normal\"}]}";
        }
        return "{\"type\":\"report\",\"provider\":\"mock\",\"totalXu\":" + parseTotalXu(upload.getRawText()) + ",\"rawText\":\"" + escapeJson(upload.getRawText()) + "\"}";
    }

    private Integer parseTotalXu(String rawText)
    {
        if (StringUtils.isEmpty(rawText))
        {
            return 0;
        }
        String text = rawText.replace(",", "");
        Matcher keyword = Pattern.compile("(?i)(?:tổng|tong|总计|总)\\D{0,5}(\\d+)").matcher(text);
        if (keyword.find())
        {
            return toIntSafely(keyword.group(1));
        }
        Matcher matcher = Pattern.compile("(\\d{2,})").matcher(text);
        int max = 0;
        while (matcher.find())
        {
            max = Math.max(max, toIntSafely(matcher.group(1)));
        }
        return max;
    }

    private int toIntSafely(String s)
    {
        try
        {
            long val = Long.parseLong(s);
            return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private String escapeJson(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String configValue(String key, String defaultValue)
    {
        String value = configService.selectConfigByKey(key);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }
}
