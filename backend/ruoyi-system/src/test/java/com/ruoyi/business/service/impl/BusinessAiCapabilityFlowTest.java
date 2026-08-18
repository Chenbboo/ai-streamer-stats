package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityActionService;
import com.ruoyi.business.ai.capability.AiCapabilityAgentLoop;
import com.ruoyi.business.ai.capability.AiCapabilityToolCatalog;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

/** Integration boundary tests for model routing, capability execution and confirmation ownership. */
@ExtendWith(MockitoExtension.class)
class BusinessAiCapabilityFlowTest
{
    @Mock private BusinessAiMapper mapper;
    @Mock private IBusinessProjectService projectService;
    @Mock private IBusinessAccountingService accountingService;
    @Mock private IBusinessStaffService staffService;
    @Mock private IBusinessAiModelClient modelClient;
    @Mock private AiCapabilityToolCatalog capabilityToolCatalog;
    @Mock private AiCapabilityAgentLoop capabilityAgentLoop;
    @Mock private AiCapabilityActionService capabilityActionService;
    @InjectMocks private BusinessAiServiceImpl service;

    private final AtomicLong ids = new AtomicLong(100L);

    @BeforeEach
    void setUp()
    {
        lenient().when(mapper.insertConversation(any())).thenAnswer(invocation -> id(invocation.getArgument(0), "conversationId"));
        lenient().when(mapper.insertMessage(any())).thenAnswer(invocation -> id(invocation.getArgument(0), "messageId"));
        lenient().when(mapper.insertRun(any())).thenAnswer(invocation -> id(invocation.getArgument(0), "runId"));
        lenient().when(mapper.insertAudit(any())).thenAnswer(invocation -> id(invocation.getArgument(0), "auditId"));
        lenient().when(mapper.insertActionRequest(any())).thenAnswer(invocation -> id(invocation.getArgument(0), "actionRequestId"));
    }

    @Test
    void allowedCapabilityPlanRunsThroughAgentLoopAndPersistsAuditMetadata()
    {
        AiExecutionContext actor = context("business:boss:view");
        when(modelClient.isEnabled()).thenReturn(true);
        when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
        Map<String, Object> definition = definition("capability_project_portfolio_get");
        when(capabilityToolCatalog.definitions(actor)).thenReturn(Collections.singletonList(definition));

        Map<String, Object> call = call("portfolio_1", "capability_project_portfolio_get");
        Map<String, Object> plan = plan(call);
        when(modelClient.plan(eq("项目整体怎么样"), any(), eq(Collections.singletonList(definition)))).thenReturn(plan);
        when(capabilityToolCatalog.isAllowedToolName("capability_project_portfolio_get", actor)).thenReturn(true);
        when(capabilityAgentLoop.canHandle(plan, actor)).thenReturn(true);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("totalCount", 2);
        Map<String, Object> toolResult = new LinkedHashMap<String, Object>();
        toolResult.put("toolCode", "project.portfolio.get");
        toolResult.put("riskLevel", "READ_ONLY");
        toolResult.put("sourcePath", "ai-capability://project.portfolio.get");
        toolResult.put("data", data);
        Map<String, Object> outcome = new LinkedHashMap<String, Object>();
        outcome.put("content", "当前共有 2 个项目。");
        outcome.put("rounds", 1);
        outcome.put("toolResults", Collections.singletonList(toolResult));
        outcome.put("answerValidation", Collections.singletonMap("status", "PASSED"));
        when(capabilityAgentLoop.run(eq("项目整体怎么样"), any(), eq(plan), any())).thenReturn(outcome);

        Map<String, Object> result = service.chat(null, "项目整体怎么样", actor);

        assertEquals("当前共有 2 个项目。", result.get("content"));
        assertEquals("LLM_AGENT", result.get("executionMode"));
        assertEquals("DEEPSEEK", result.get("provider"));
        assertEquals("PASSED", ((Map<?, ?>) result.get("answerValidation")).get("status"));
        verify(capabilityAgentLoop).run(eq("项目整体怎么样"), any(), eq(plan), any());
        verify(mapper).finishRun(any(), any(), eq("SUCCEEDED"), eq(null));
        verify(mapper).insertAudit(any());
    }

    @Test
    void activeProjectWorkflowUsesModelDraftUnderstandingWithinScopedTools()
    {
        AiExecutionContext actor = context("business:project:add");
        when(modelClient.isEnabled()).thenReturn(true);
        when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
        when(mapper.selectConversation(91L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 91L));
        Map<String, Object> active = workflow(false);
        Map<String, Object> updated = workflow(true);
        when(mapper.selectActiveWorkflow(91L, 23L)).thenReturn(active, updated);
        when(mapper.updateWorkflow(any())).thenReturn(1);

        Map<String, Object> get = definition("capability_project_draft_get");
        Map<String, Object> update = definition("capability_project_draft_update");
        Map<String, Object> unrelated = definition("capability_project_portfolio_get");
        when(capabilityToolCatalog.definitions(actor)).thenReturn(Arrays.asList(get, update, unrelated));
        Map<String, Object> plan = plan(call("draft-update", "capability_project_draft_update"));
        when(modelClient.plan(eq("暂时不设置预算"), any(), eq(Arrays.asList(get, update)))).thenReturn(plan);
        when(capabilityToolCatalog.isAllowedToolName("capability_project_draft_update", actor)).thenReturn(true);
        when(capabilityAgentLoop.canHandle(plan, actor)).thenReturn(true);

        Map<String, Object> toolResult = new LinkedHashMap<String, Object>();
        toolResult.put("toolCode", "project.draft.update");
        toolResult.put("riskLevel", "DRAFT_WRITE");
        toolResult.put("sourcePath", "ai-capability://project.draft.update");
        toolResult.put("data", Collections.singletonMap("draft", Collections.singletonMap("noBudget", true)));
        Map<String, Object> outcome = new LinkedHashMap<String, Object>();
        outcome.put("content", "已记录为暂时不设置预算，请确认立项信息。");
        outcome.put("rounds", 1);
        outcome.put("toolResults", Collections.singletonList(toolResult));
        when(capabilityAgentLoop.run(eq("暂时不设置预算"), any(), eq(plan), any(), eq(Arrays.asList(get, update))))
            .thenReturn(outcome);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(
            option(118L, "ZDY-slh", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(
            option(118L, "ZDY-slh", "施柳浩", 110L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(91L, "暂时不设置预算", actor);

        assertEquals("LLM_AGENT", result.get("executionMode"));
        assertEquals(true, ((Map<?, ?>) ((Map<?, ?>) result.get("workflow")).get("draft")).get("noBudget"));
        assertEquals("WAITING_CONFIRMATION", ((Map<?, ?>) result.get("workflow")).get("status"));
        assertEquals("CREATE_PROJECT", ((Map<?, ?>) result.get("actionRequest")).get("actionCode"));
        verify(capabilityAgentLoop).run(eq("暂时不设置预算"), any(), eq(plan), any(), eq(Arrays.asList(get, update)));
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void waitingConfirmationProjectCanReturnToModelDraftEditing()
    {
        AiExecutionContext actor = context("business:project:add");
        when(modelClient.isEnabled()).thenReturn(true);
        when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
        when(mapper.selectConversation(91L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 91L));
        Map<String, Object> waiting = workflow(true);
        waiting.put("workflowStatus", "WAITING_CONFIRMATION");
        waiting.put("currentStep", "WAITING_CONFIRMATION");
        waiting.put("actionRequestId", 88L);
        when(mapper.selectActiveWorkflow(91L, 23L)).thenReturn(waiting);

        Map<String, Object> get = definition("capability_project_draft_get");
        Map<String, Object> update = definition("capability_project_draft_update");
        when(capabilityToolCatalog.definitions(actor)).thenReturn(Arrays.asList(get, update));
        Map<String, Object> plan = plan(call("draft-get", "capability_project_draft_get"));
        when(modelClient.plan(eq("我想把结束时间改到10月15日"), any(), eq(Arrays.asList(get, update))))
            .thenReturn(plan);
        when(capabilityToolCatalog.isAllowedToolName("capability_project_draft_get", actor)).thenReturn(true);
        when(capabilityAgentLoop.canHandle(plan, actor)).thenReturn(true);
        Map<String, Object> outcome = new LinkedHashMap<String, Object>();
        outcome.put("content", "我先读取当前立项资料再修改结束日期。");
        outcome.put("rounds", 1);
        outcome.put("toolResults", Collections.emptyList());
        when(capabilityAgentLoop.run(eq("我想把结束时间改到10月15日"), any(), eq(plan), any(),
            eq(Arrays.asList(get, update)))).thenReturn(outcome);

        Map<String, Object> result = service.chat(91L, "我想把结束时间改到10月15日", actor);

        assertEquals("LLM_AGENT", result.get("executionMode"));
        assertEquals("project.draft.get", ((Map<?, ?>) result.get("decisionTrace")).get("finalRoute"));
        verify(capabilityAgentLoop).run(eq("我想把结束时间改到10月15日"), any(), eq(plan), any(),
            eq(Arrays.asList(get, update)));
    }

    @Test
    void modelRequestForUnauthorizedCapabilityFailsBeforeAgentExecutionOrBusinessWrite()
    {
        AiExecutionContext actor = context("business:boss:view");
        when(modelClient.isEnabled()).thenReturn(true);
        when(capabilityToolCatalog.definitions(actor)).thenReturn(Collections.emptyList());
        Map<String, Object> plan = plan(call("budget_1", "capability_project_budget_update"));
        when(modelClient.plan(eq("把预算改成十万"), any())).thenReturn(plan);
        when(capabilityToolCatalog.isAllowedToolName("capability_project_budget_update", actor)).thenReturn(false);

        assertThrows(ServiceException.class, () -> service.chat(null, "把预算改成十万", actor));

        verify(capabilityAgentLoop, never()).run(any(), any(), any(), any());
        verify(capabilityActionService, never()).executeConfirmed(any(), any());
        verify(projectService, never()).updateBudget(any(), any(), any(), any(), any(), any(), any(Boolean.class));
        verify(mapper).finishRun(any(), eq(null), eq("FAILED"), any());
    }

    @Test
    void confirmationRechecksCurrentPermissionBeforeClaimingPendingAction()
    {
        AiExecutionContext actor = context("business:boss:view");
        when(mapper.selectActionRequest(88L, 23L)).thenReturn(pendingCapabilityAction());
        when(capabilityActionService.requiredPermission("CAPABILITY:project.budget.update"))
            .thenReturn("business:project:manage");

        assertThrows(ServiceException.class, () -> service.confirmAction(88L, actor));

        verify(mapper, never()).confirmActionRequest(any(), any());
        verify(capabilityActionService, never()).executeConfirmed(any(), any());
    }

    @Test
    void confirmedCapabilityIsClaimedExecutedPersistedMessagedAndAuditedExactlyOnce()
    {
        AiExecutionContext actor = context("business:project:manage");
        Map<String, Object> action = pendingCapabilityAction();
        when(mapper.selectActionRequest(88L, 23L)).thenReturn(action);
        when(capabilityActionService.requiredPermission("CAPABILITY:project.budget.update"))
            .thenReturn("business:project:manage");
        when(mapper.confirmActionRequest(88L, 23L)).thenReturn(1);
        Map<String, Object> completed = new LinkedHashMap<String, Object>(); completed.put("projectId", 16L);
        completed.put("status", "EXECUTED");
        when(capabilityActionService.completeConfirmedAction(action, actor)).thenReturn(completed);

        Map<String, Object> result = service.confirmAction(88L, actor);

        assertEquals("EXECUTED", result.get("status"));
        assertEquals(16L, result.get("projectId"));
        verify(mapper).confirmActionRequest(88L, 23L);
        verify(capabilityActionService).completeConfirmedAction(action, actor);
    }

    @Test
    void alreadyExecutedConfirmationReturnsStoredResultWithoutRunningCapabilityAgain()
    {
        AiExecutionContext actor = context("business:boss:view");
        Map<String, Object> action = pendingCapabilityAction();
        action.put("status", "EXECUTED");
        action.put("resultJson", "{\"status\":\"EXECUTED\",\"projectId\":16}");
        when(mapper.selectActionRequest(88L, 23L)).thenReturn(action);

        Map<String, Object> result = service.confirmAction(88L, actor);

        assertEquals(16L, ((Number) result.get("projectId")).longValue());
        verify(mapper, never()).confirmActionRequest(any(), any());
        verify(capabilityActionService, never()).executeConfirmed(any(), any());
    }

    @Test
    void rejectionAlsoRechecksCapabilityPermissionAndNeverExecutesBusinessAction()
    {
        AiExecutionContext actor = context("business:project:manage");
        when(mapper.selectActionRequest(88L, 23L)).thenReturn(pendingCapabilityAction());
        when(capabilityActionService.requiredPermission("CAPABILITY:project.budget.update"))
            .thenReturn("business:project:manage");
        when(mapper.rejectActionRequest(88L, 23L)).thenReturn(1);

        Map<String, Object> result = service.rejectAction(88L, actor);

        assertEquals("REJECTED", result.get("status"));
        verify(mapper).rejectActionRequest(88L, 23L);
        verify(capabilityActionService, never()).executeConfirmed(any(), any());
        verify(projectService, never()).updateBudget(any(), any(), any(), any(), any(), any(), any(Boolean.class));
        verify(mapper).insertAudit(any());
    }

    private Map<String, Object> pendingCapabilityAction()
    {
        Map<String, Object> action = new LinkedHashMap<String, Object>();
        action.put("actionRequestId", 88L);
        action.put("conversationId", 7L);
        action.put("runId", 8L);
        action.put("traceId", "trace-88");
        action.put("status", "PENDING");
        action.put("actionCode", "CAPABILITY:project.budget.update");
        action.put("confirmationSummary", "将项目预算调整为 100000 CNY");
        action.put("actionPayloadJson", "{\"capabilityCode\":\"project.budget.update\",\"input\":{\"projectId\":16}}");
        return action;
    }

    private AiExecutionContext context(String permission)
    {
        SysUser user = new SysUser();
        user.setUserId(23L);
        user.setUserName("jianglan");
        LoginUser loginUser = new LoginUser(23L, 100L, user, Collections.singleton(permission));
        return AiExecutionContext.from(loginUser);
    }

    private Map<String, Object> definition(String name)
    {
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", name);
        Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    private Map<String, Object> workflow(boolean noBudget)
    {
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 801L); workflow.put("conversationId", 91L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT");
        workflow.put("workflowStatus", noBudget ? "REVALIDATE" : "COLLECTING");
        workflow.put("currentStep", noBudget ? "REVALIDATE" : "ACCOUNTING_AND_BUDGET");
        workflow.put("versionNo", noBudget ? 8 : 7);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"施柳浩\","
            + "\"companyName\":\"上海美丸文化公司\",\"objective\":\"GMV达到500万元\","
            + "\"planStartDate\":\"2026-08-18\",\"planEndDate\":\"2026-09-30\","
            + "\"accountingMode\":\"HYBRID\"" + (noBudget ? ",\"noBudget\":true" : "") + "}");
        workflow.put("missingFieldsJson", noBudget
            ? "[\"修改后的立项资料需要重新校验\"]" : "[\"预算金额，或明确说明不设预算\"]");
        return workflow;
    }

    private Map<String, Object> option(Long userId, String userName, String nickName,
        Long companyDeptId, String companyName)
    {
        Map<String, Object> option = new LinkedHashMap<String, Object>();
        option.put("userId", userId); option.put("userName", userName); option.put("nickName", nickName);
        option.put("companyDeptId", companyDeptId); option.put("companyName", companyName);
        return option;
    }

    private Map<String, Object> call(String id, String name)
    {
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", id);
        call.put("name", name);
        call.put("arguments", Collections.emptyMap());
        return call;
    }

    private Map<String, Object> plan(Map<String, Object> call)
    {
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        plan.put("content", "");
        return plan;
    }

    private int id(Map<String, Object> row, String key)
    {
        row.put(key, ids.incrementAndGet());
        return 1;
    }
}
