package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessAiModelFirstRoutingTest
{
    @Mock private BusinessAiMapper mapper;
    @Mock private IBusinessProjectService projectService;
    @Mock private IBusinessAccountingService accountingService;
    @Mock private IBusinessStaffService staffService;
    @Mock private IBusinessAiModelClient modelClient;
    @InjectMocks private BusinessAiServiceImpl service;
    private final AtomicLong ids = new AtomicLong(100L);

    @BeforeEach
    void setup()
    {
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "conversationId")).when(mapper).insertConversation(any());
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "messageId")).when(mapper).insertMessage(any());
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "runId")).when(mapper).insertRun(any());
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "auditId")).when(mapper).insertAudit(any());
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "workflowId")).when(mapper).insertWorkflow(any());
        lenient().doAnswer(invocation -> id(invocation.getArgument(0), "workflowEventId")).when(mapper).insertWorkflowEvent(any());
        when(modelClient.isEnabled()).thenReturn(true);
        lenient().when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        lenient().when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
    }

    @Test
    void explicitProjectCreateStartsSafeWorkflowEvenWhenModelOnlyAnswersInText()
    {
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.emptyList());
        plan.put("content", "可以，我们先聊清楚这个项目想解决的问题。");
        when(modelClient.plan(eq("我要创建一个项目"), any())).thenReturn(plan);

        Map<String, Object> result = service.chat(null, "我要创建一个项目", 23L, "jianglan", false);

        assertCreateWorkflow(result);
        assertEquals("CORRECTED", decisionTrace(result).get("validationStatus"));
        assertEquals("CREATE_PROJECT_WORKFLOW", decisionTrace(result).get("finalRoute"));
        verify(mapper).insertWorkflow(any());
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void removedAggregateQueryToolIsRejectedInsteadOfFallingBackToKeywords()
    {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("queryType", "STAFF_OVERVIEW");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "staff_1");
        call.put("name", "boss_query_business");
        call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        plan.put("content", "");
        when(modelClient.plan(eq("今天经营怎么样"), any())).thenReturn(plan);
        assertThrows(ServiceException.class,
            () -> service.chat(null, "今天经营怎么样", 23L, "jianglan", false));

        verify(staffService, never()).listOptions();
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
    }

    @Test
    void modelPlanningFailureCanOnlyStartNonWritingProjectWorkflow()
    {
        when(modelClient.plan(eq("帮我创建项目"), any())).thenReturn(null);

        Map<String, Object> result = service.chat(null, "帮我创建项目", 23L, "jianglan", false);

        assertCreateWorkflow(result);
        assertEquals(Collections.emptyList(), decisionTrace(result).get("modelSelection"));
        verify(mapper).insertWorkflow(any());
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void productionPhraseRejectsUnclearSafeReplyAndStartsProjectWorkflow()
    {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("responseType", "UNCLEAR");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "safe_1");
        call.put("name", "capability_conversation_safe_respond");
        call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        plan.put("content", "我还没能确定你希望查询还是办理什么。");
        when(modelClient.plan(eq("帮我创建一个新项目"), any())).thenReturn(plan);

        Map<String, Object> result = service.chat(null, "帮我创建一个新项目", 23L, "jianglan", false);

        assertCreateWorkflow(result);
        Map<String, Object> trace = decisionTrace(result);
        assertEquals(Collections.singletonList("conversation.safe.respond"), trace.get("modelSelection"));
        assertEquals("CORRECTED", trace.get("validationStatus"));
        assertEquals("CREATE_PROJECT_WORKFLOW", trace.get("finalRoute"));
        verify(mapper).insertWorkflow(any());
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void negatedProjectCreateRequestIsNotForcedIntoWorkflow()
    {
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.emptyList());
        plan.put("content", "好的，不会创建项目。");
        when(modelClient.plan(eq("先不要创建项目"), any())).thenReturn(plan);

        Map<String, Object> result = service.chat(null, "先不要创建项目", 23L, "jianglan", false);

        assertEquals("好的，不会创建项目。", result.get("content"));
        verify(mapper, never()).insertWorkflow(any());
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private void assertCreateWorkflow(Map<String, Object> result)
    {
        Map<String, Object> workflow = (Map<String, Object>) result.get("workflow");
        assertEquals("CREATE_PROJECT", workflow.get("workflowCode"));
        assertEquals("COLLECTING", workflow.get("status"));
        assertEquals("BASIC_INFO", workflow.get("currentStep"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decisionTrace(Map<String, Object> result)
    {
        return (Map<String, Object>) result.get("decisionTrace");
    }

    private int id(Map<String, Object> row, String key)
    {
        row.put(key, ids.incrementAndGet());
        return 1;
    }
}
