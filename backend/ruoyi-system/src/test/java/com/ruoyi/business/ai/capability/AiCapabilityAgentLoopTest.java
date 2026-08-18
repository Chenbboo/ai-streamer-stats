package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.business.ai.capability.read.SafeConversationCapability;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

@ExtendWith(MockitoExtension.class)
class AiCapabilityAgentLoopTest
{
    @Mock private IBusinessAiModelClient modelClient;
    @Mock private BusinessAiMapper mapper;
    private AiCapabilityAgentLoop loop;
    private AiExecutionContext actor;

    @BeforeEach
    void setup()
    {
        AiCapability get = capability("project.draft.get", AiCapabilityRisk.READ_ONLY,
            Collections.<String, Object>singletonMap("objective", "完成1000条视频"));
        AiCapability update = capability("project.draft.update", AiCapabilityRisk.DRAFT_WRITE,
            Collections.<String, Object>singletonMap("objective", "完成600条视频"));
        AiCapability directory = capability("project.directory.get", AiCapabilityRisk.READ_ONLY,
            Collections.<String, Object>singletonMap("projects", Collections.singletonList(
                map("projectId", 17L, "projectName", "王老吉视频宣传"))));
        AiCapability detail = capability("project.detail.get", AiCapabilityRisk.READ_ONLY,
            Collections.<String, Object>singletonMap("project", map("projectId", 17L,
                "projectName", "王老吉视频宣传")));
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Arrays.asList(get, update, directory, detail,
            new SafeConversationCapability()));
        AiCapabilityToolCatalog catalog = new AiCapabilityToolCatalog(registry);
        loop = new AiCapabilityAgentLoop(catalog, new AiCapabilityExecutor(registry), modelClient, mapper,
            new ObjectMapper());
        SysUser user = new SysUser(); user.setUserId(23L); user.setUserName("jianglan");
        actor = AiExecutionContext.from(new LoginUser(23L, 100L, user,
            new java.util.LinkedHashSet<String>(Arrays.asList("business:project:add", "business:boss:view"))));
    }

    @Test
    void modelCanReadThenUpdateDraftAcrossMultipleTurns()
    {
        Map<String, Object> first = plan("call-1", "capability_project_draft_get",
            Collections.<String, Object>emptyMap());
        Map<String, Object> second = plan("call-2", "capability_project_draft_update",
            Collections.<String, Object>singletonMap("changes",
                Collections.<String, Object>singletonMap("objective", "完成600条视频")));
        Map<String, Object> done = new LinkedHashMap<String, Object>();
        done.put("content", "项目目标已经更新为完成600条视频。");
        done.put("toolCalls", Collections.emptyList());
        when(modelClient.continueWithTools(eq("项目目标改为600条"), any(), any(), any()))
            .thenReturn(second, done);

        Map<String, Object> result = loop.run("项目目标改为600条", Collections.emptyList(), first,
            new AiCapabilityInvocation(actor, 67L, 90L, 375L));

        assertEquals("项目目标已经更新为完成600条视频。", result.get("content"));
        assertEquals(2, ((List<?>) result.get("toolResults")).size());
        ArgumentCaptor<Map<String, Object>> calls = mapCaptor();
        verify(mapper, org.mockito.Mockito.times(2)).insertToolCall(calls.capture());
        assertEquals("project.draft.get", calls.getAllValues().get(0).get("toolCode"));
        assertEquals("project.draft.update", calls.getAllValues().get(1).get("toolCode"));
    }

    @Test
    void scopedDraftLoopRejectsModelDriftIntoUnrelatedCapability()
    {
        Map<String, Object> first = plan("call-1", "capability_project_draft_get",
            Collections.<String, Object>emptyMap());
        Map<String, Object> drift = plan("call-2", "capability_project_directory_get",
            Collections.<String, Object>emptyMap());
        when(modelClient.continueWithTools(eq("暂时不设置预算"), any(), any(), any())).thenReturn(drift);
        List<Map<String, Object>> scope = Arrays.asList(
            definition("capability_project_draft_get"), definition("capability_project_draft_update"));

        assertThrows(com.ruoyi.common.exception.ServiceException.class,
            () -> loop.run("暂时不设置预算", Collections.emptyList(), first,
                new AiCapabilityInvocation(actor, 67L, 90L, 375L), scope));

        ArgumentCaptor<Map<String, Object>> calls = mapCaptor();
        verify(mapper).insertToolCall(calls.capture());
        assertEquals("project.draft.get", calls.getValue().get("toolCode"));
    }

    @Test
    void safeConversationToolTerminatesWithoutAnotherModelRound()
    {
        Map<String, Object> initial = plan("call-safe", "capability_conversation_safe_respond",
            Collections.<String, Object>singletonMap("responseType", "GREETING"));

        Map<String, Object> result = loop.run("你好", Collections.emptyList(), initial,
            new AiCapabilityInvocation(actor, 67L, 90L, 375L));

        assertEquals(true, result.get("terminal"));
        assertEquals(true, String.valueOf(result.get("content")).contains("老板 AI 助理"));
        verifyNoInteractions(modelClient);
    }

    @Test
    void inventedIdIsReturnedToTheModelAndAutomaticallyCorrectedWithoutFailingTheRequest()
    {
        Map<String, Object> initial = plan("directory", "capability_project_directory_get",
            Collections.<String, Object>emptyMap());
        Map<String, Object> wrong = plan("wrong-detail", "capability_project_detail_get",
            Collections.<String, Object>singletonMap("projectId", 2L));
        Map<String, Object> corrected = plan("correct-detail", "capability_project_detail_get",
            Collections.<String, Object>singletonMap("projectId", 17L));
        Map<String, Object> done = new LinkedHashMap<String, Object>();
        done.put("content", "王老吉视频宣传项目详情已读取。");
        done.put("toolCalls", Collections.emptyList());
        when(modelClient.continueWithTools(eq("王老吉项目给我看详情"), any(), any(), any()))
            .thenReturn(wrong, corrected, done);

        Map<String, Object> result = loop.run("王老吉项目给我看详情", Collections.emptyList(), initial,
            new AiCapabilityInvocation(actor, 71L, 218L, 436L));

        assertEquals("王老吉视频宣传项目详情已读取。", result.get("content"));
        assertEquals(2, ((List<?>) result.get("toolResults")).size());
        ArgumentCaptor<Map<String, Object>> calls = mapCaptor();
        verify(mapper, org.mockito.Mockito.times(3)).insertToolCall(calls.capture());
        assertEquals("FAILED", calls.getAllValues().get(1).get("status"));
        assertEquals(true, String.valueOf(calls.getAllValues().get(1).get("outputJson"))
            .contains("INVALID_REFERENCE"));
        assertEquals("SUCCEEDED", calls.getAllValues().get(2).get("status"));
    }

    @Test
    void unsupportedFinalFactsAreRewrittenAndVerifiedBeforeReturning()
    {
        Map<String, Object> initial = plan("detail", "capability_project_detail_get",
            Collections.<String, Object>singletonMap("projectId", 17L));
        Map<String, Object> wrong = new LinkedHashMap<String, Object>();
        wrong.put("content", "项目名称：王老吉视频宣传，负责人：李四，预算2,000元。");
        wrong.put("toolCalls", Collections.emptyList());
        when(modelClient.continueWithTools(eq("查看项目详情"), any(), any(), any())).thenReturn(wrong);
        when(modelClient.rewriteGroundedAnswer(eq("查看项目详情"), any(), any(), any()))
            .thenReturn("项目名称：王老吉视频宣传。");

        Map<String, Object> result = loop.run("查看项目详情", Collections.emptyList(), initial,
            new AiCapabilityInvocation(actor, 72L, 220L, 438L));

        assertEquals("项目名称：王老吉视频宣传。", result.get("content"));
        assertEquals("REWRITTEN", ((Map<?, ?>) result.get("answerValidation")).get("status"));
    }

    @Test
    void failedRewriteFallsBackInsteadOfReturningInventedFacts()
    {
        Map<String, Object> initial = plan("detail", "capability_project_detail_get",
            Collections.<String, Object>singletonMap("projectId", 17L));
        Map<String, Object> wrong = new LinkedHashMap<String, Object>();
        wrong.put("content", "负责人：李四，预算2,000元。");
        wrong.put("toolCalls", Collections.emptyList());
        when(modelClient.continueWithTools(eq("查看项目详情"), any(), any(), any())).thenReturn(wrong);
        when(modelClient.rewriteGroundedAnswer(eq("查看项目详情"), any(), any(), any()))
            .thenReturn("负责人：王五，预算3,000元。");

        Map<String, Object> result = loop.run("查看项目详情", Collections.emptyList(), initial,
            new AiCapabilityInvocation(actor, 72L, 221L, 439L));

        assertTrue(String.valueOf(result.get("content")).contains("未通过事实核验"));
        assertFalse(String.valueOf(result.get("content")).contains("李四"));
        assertEquals("SAFE_FALLBACK", ((Map<?, ?>) result.get("answerValidation")).get("status"));
    }

    private AiCapability capability(String code, AiCapabilityRisk risk, Map<String, Object> result)
    {
        return new AiCapability()
        {
            @Override public String code() { return code; }
            @Override public String description() { return code; }
            @Override public String requiredPermission() { return "business:project:add"; }
            @Override public AiCapabilityRisk risk() { return risk; }
            @Override public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
            @Override public Map<String, Object> execute(AiCapabilityInvocation invocation,
                Map<String, Object> input) { return result; }
        };
    }

    private Map<String, Object> plan(String id, String name, Map<String, Object> arguments)
    {
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", id); call.put("name", name); call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("assistantMessageJson", "{\"role\":\"assistant\",\"tool_calls\":[]}");
        plan.put("toolCalls", Collections.singletonList(call));
        return plan;
    }

    private Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }

    private Map<String, Object> definition(String name)
    {
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", name);
        Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put("type", "function"); wrapper.put("function", function);
        return wrapper;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
