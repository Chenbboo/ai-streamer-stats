package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class DeepSeekBusinessAiClientTest
{
    @Test
    void planningRequiresAnExplicitToolDecisionWhenCapabilitiesAreAvailable() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] request = readAll(exchange.getRequestBody());
            requestBody.set(new String(request, StandardCharsets.UTF_8));
            byte[] response = ("{\"id\":\"test\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"capability_conversation_safe_respond\","
                + "\"arguments\":\"{\\\"responseType\\\":\\\"GREETING\\\"}\"}}]}}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try
        {
            DeepSeekBusinessAiClient client = new DeepSeekBusinessAiClient();
            set(client, "enabled", true); set(client, "apiKey", "test-key");
            set(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            set(client, "model", "deepseek-v4-flash"); set(client, "connectTimeoutMs", 2000);
            set(client, "readTimeoutMs", 2000); set(client, "objectMapper", new ObjectMapper());
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", "capability_conversation_safe_respond");
            function.put("description", "safe exit"); function.put("parameters", Collections.singletonMap("type", "object"));
            Map<String, Object> tool = new LinkedHashMap<String, Object>(); tool.put("type", "function"); tool.put("function", function);

            Map<String, Object> plan = client.plan("你好", Collections.emptyList(), Collections.singletonList(tool));

            JsonNode body = new ObjectMapper().readTree(requestBody.get());
            assertEquals("required", body.path("tool_choice").asText());
            assertEquals(1, body.path("tools").size());
            assertFalse(((java.util.List<?>) plan.get("toolCalls")).isEmpty());
        }
        finally { server.stop(0); }
    }

    @Test
    void rejectedAnswerIsRewrittenFromAuthorizedCapabilityResultsOnly() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            byte[] response = ("{\"id\":\"rewrite\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"项目负责人是石头。\"}}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try
        {
            DeepSeekBusinessAiClient client = client(server);
            Map<String, Object> project = new LinkedHashMap<String, Object>();
            project.put("projectName", "王老吉视频宣传"); project.put("mainOwnerName", "石头");
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("toolCode", "project.detail.get"); result.put("data", project);

            String rewritten = client.rewriteGroundedAnswer("查看项目详情", "负责人是李四",
                Collections.singletonList(result), Collections.singletonList("名称不在结果中"));

            assertEquals("项目负责人是石头。", rewritten);
            JsonNode body = new ObjectMapper().readTree(requestBody.get());
            assertEquals("none", body.path("tool_choice").asText("none"));
            assertTrue(body.path("messages").path(1).path("content").asText().contains("石头"));
            assertTrue(body.path("messages").path(0).path("content").asText().contains("不得新增"));
        }
        finally { server.stop(0); }
    }

    private DeepSeekBusinessAiClient client(HttpServer server) throws Exception
    {
        DeepSeekBusinessAiClient client = new DeepSeekBusinessAiClient();
        set(client, "enabled", true); set(client, "apiKey", "test-key");
        set(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        set(client, "model", "deepseek-v4-flash"); set(client, "connectTimeoutMs", 2000);
        set(client, "readTimeoutMs", 2000); set(client, "objectMapper", new ObjectMapper());
        return client;
    }

    private byte[] readAll(java.io.InputStream input) throws java.io.IOException
    {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
