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

import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    void activeProjectCreateWorkflowCannotDriftIntoUnrelatedModelTools()
    {
        when(mapper.selectConversation(91L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 91L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 801L); workflow.put("conversationId", 91L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "GOAL_AND_PERIOD"); workflow.put("versionNo", 1);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"施柳浩\","
            + "\"companyName\":\"上海美丸文化公司\",\"planStartDate\":\"2026-08-18\","
            + "\"projectType\":\"ECOMMERCE\"}");
        workflow.put("missingFieldsJson", "[\"项目目标\",\"计划结束日期\",\"核算方式\",\"预算\"]");
        when(mapper.selectActiveWorkflow(91L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", 110L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(91L, "500万的GMV，持续，没有预算", 23L, "admin", true);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("GMV达到500万元", draft.get("objective"));
        assertEquals(true, draft.get("noBudget"));
        assertEquals("GOAL_AND_PERIOD", workflowView.get("currentStep"));
        assertEquals(true, String.valueOf(result.get("content")).contains("什么时候结束"));
        verify(modelClient, never()).plan(any(), any(), any());
    }

    @Test
    void emptyActiveDraftRecoversProjectFieldsFromEarlierUserTurns()
    {
        service.setClock(java.time.Clock.fixed(java.time.Instant.parse("2026-08-18T05:00:00Z"),
            java.time.ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(92L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 92L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 802L); workflow.put("conversationId", 92L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "BASIC_INFO"); workflow.put("versionNo", 1);
        workflow.put("draftJson", "{}"); workflow.put("missingFieldsJson", "[\"项目名称\"]");
        when(mapper.selectActiveWorkflow(92L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(mapper.selectMessages(92L, 23L, 12)).thenReturn(new ArrayList<Map<String, Object>>(Arrays.asList(
            historyMessage("500万的GMV，持续，没有预算"),
            historyMessage("施柳浩吧，名称就叫上海电商，从现在到持续，电商类型"),
            historyMessage("上海电商，让蒋豪负责，属于上海"),
            historyMessage("帮我创建一个新项目"))));
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", 110L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(92L, "继续创建", 23L, "admin", true);

        Map<?, ?> draft = (Map<?, ?>) ((Map<?, ?>) result.get("workflow")).get("draft");
        assertEquals("上海电商", draft.get("projectName"));
        assertEquals("施柳浩", draft.get("ownerName"));
        assertEquals("上海美丸文化公司", draft.get("companyName"));
        assertEquals("GMV达到500万元", draft.get("objective"));
        assertEquals("2026-08-18", draft.get("planStartDate"));
        assertEquals("ECOMMERCE", draft.get("projectType"));
        assertEquals(true, draft.get("noBudget"));
    }

    @Test
    void shortTogetherAnswerSelectsHybridAccountingInAccountingStep()
    {
        when(mapper.selectConversation(93L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 93L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 803L); workflow.put("conversationId", 93L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "ACCOUNTING_AND_BUDGET"); workflow.put("versionNo", 8);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"施柳浩\","
            + "\"companyName\":\"上海美丸文化公司\",\"objective\":\"GMV达到500万元\","
            + "\"planStartDate\":\"2026-08-15\",\"planEndDate\":\"2026-09-30\","
            + "\"projectType\":\"ECOMMERCE\",\"noBudget\":true}");
        workflow.put("missingFieldsJson", "[\"核算方式（利润、成本、价值或混合）\"]");
        when(mapper.selectActiveWorkflow(93L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(
            staffOption(118L, "ZDY-slh", "施柳浩", 110L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(93L, "一起看", 23L, "admin", true);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("HYBRID", draft.get("accountingMode"));
        assertEquals("WAITING_CONFIRMATION", workflowView.get("status"));
        assertEquals(Collections.emptyList(), workflowView.get("missingFields"));
        verify(mapper).insertActionRequest(any());
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

    private Map<String, Object> staffOption(Long userId, String userName, String nickName,
        Long companyDeptId, String companyName)
    {
        Map<String, Object> option = new LinkedHashMap<String, Object>();
        option.put("userId", userId); option.put("userName", userName); option.put("nickName", nickName);
        option.put("companyDeptId", companyDeptId); option.put("companyName", companyName);
        return option;
    }

    private Map<String, Object> historyMessage(String content)
    {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("messageRole", "USER"); message.put("content", content);
        return message;
    }

    private int id(Map<String, Object> row, String key)
    {
        row.put(key, ids.incrementAndGet());
        return 1;
    }
}
