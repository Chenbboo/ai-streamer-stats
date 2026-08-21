package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.ai.capability.AiCapabilityAgentLoop;
import com.ruoyi.business.ai.capability.AiCapabilityToolCatalog;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessAiServiceImplTest
{
    @Mock private BusinessAiMapper mapper;
    @Mock private IBusinessProjectService projectService;
    @Mock private IBusinessAccountingService accountingService;
    @Mock private IBusinessStaffService staffService;
    @Mock private IBusinessAiModelClient modelClient;
    @InjectMocks private BusinessAiServiceImpl service;

    private final AtomicLong ids = new AtomicLong(10L);

    @BeforeEach
    void generatedIds()
    {
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "conversationId")).when(mapper).insertConversation(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "messageId")).when(mapper).insertMessage(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "runId")).when(mapper).insertRun(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "toolCallId")).when(mapper).insertToolCall(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "auditId")).when(mapper).insertAudit(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "actionRequestId")).when(mapper).insertActionRequest(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "workflowId")).when(mapper).insertWorkflow(any());
        lenient().doAnswer(invocation -> generated(invocation.getArgument(0), "workflowEventId")).when(mapper).insertWorkflowEvent(any());
    }

    @Test
    void accountingQuestionUsesCurrentBossScopeAndRecordsReadOnlyTool()
    {
        Map<String, Object> today = new LinkedHashMap<String, Object>();
        today.put("projectCount", 2);
        today.put("revenueAmount", new BigDecimal("3000"));
        today.put("businessCost", new BigDecimal("800"));
        today.put("personnelCost", new BigDecimal("200"));
        today.put("profitAmount", new BigDecimal("2000"));
        Map<String, Object> overview = new LinkedHashMap<String, Object>();
        overview.put("today", today);
        overview.put("alerts", Collections.emptyList());
        when(accountingService.bossOverview(23L, false)).thenReturn(overview);

        Map<String, Object> result = service.chat(null, "今天经营怎么样？", 23L, "jianglan", false);

        assertEquals("当前老板本人立项项目", ((Map<?, ?>) result.get("scope")).get("label"));
        assertEquals(true, String.valueOf(result.get("content")).contains("经营结果 2000.00"));
        verify(accountingService).bossOverview(23L, false);
        ArgumentCaptor<Map<String, Object>> tool = mapCaptor();
        verify(mapper).insertToolCall(tool.capture());
        assertEquals("boss_today_accounting", tool.getValue().get("toolCode"));
        assertEquals("READ_ONLY", tool.getValue().get("riskLevel"));
        verify(mapper).insertAudit(any());
    }

    @Test
    void staffQuestionOnlyReturnsCompanyCountsInsteadOfContactDetails()
    {
        Map<String, Object> shanghai = new LinkedHashMap<String, Object>();
        shanghai.put("companyName", "上海美丸文化公司");
        shanghai.put("userName", "private-account");
        Map<String, Object> vietnam = new LinkedHashMap<String, Object>();
        vietnam.put("companyName", "越南meimaru公司");
        when(staffService.listOptions()).thenReturn(Arrays.asList(shanghai, vietnam));

        Map<String, Object> result = service.chat(null, "人员分布怎么样？", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("上海美丸文化公司 1 人"));
        assertEquals(false, content.contains("private-account"));
        verify(projectService, never()).dashboard(any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void cannotContinueAnotherUsersConversation()
    {
        when(mapper.selectConversation(99L, 23L, "BOSS")).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.chat(99L, "项目情况", 23L, "jianglan", false));

        verify(projectService, never()).dashboard(any(), any(Boolean.class), any(Boolean.class));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
    }

    @Test
    void deepSeekSelectsReadOnlyToolAndWritesTheFinalAnswer()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "call_1");
        call.put("name", "boss_today_accounting");
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        plan.put("content", "");
        plan.put("usage", Collections.singletonMap("totalTokens", 30));
        when(modelClient.plan(eq("今天经营如何，需要我关注什么？"), any())).thenReturn(plan);

        Map<String, Object> overview = new LinkedHashMap<String, Object>();
        overview.put("today", Collections.singletonMap("profitAmount", new BigDecimal("88.00")));
        overview.put("alerts", Collections.emptyList());
        overview.put("ranking", Collections.emptyList());
        when(accountingService.bossOverview(23L, false)).thenReturn(overview);
        Map<String, Object> completion = new LinkedHashMap<String, Object>();
        completion.put("content", "今天经营盈利 88 元，目前没有经营异常。");
        completion.put("usage", Collections.singletonMap("totalTokens", 20));
        when(modelClient.complete(eq("今天经营如何，需要我关注什么？"), eq(plan), any())).thenReturn(completion);

        Map<String, Object> result = service.chat(null, "今天经营如何，需要我关注什么？", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("经营结果 88.00"));
        assertEquals(false, "今天经营盈利 88 元，目前没有经营异常。".equals(result.get("content")));
        assertEquals("LLM_AGENT", result.get("executionMode"));
        assertEquals("DEEPSEEK", result.get("provider"));
        assertEquals("deepseek-v4-flash", result.get("model"));
        verify(accountingService).bossOverview(23L, false);
        verify(modelClient).complete(eq("今天经营如何，需要我关注什么？"), eq(plan), any());
    }

    @Test
    void configuredModelRuntimeNeverFallsBackToLegacyIntentRouter()
    {
        AiCapabilityToolCatalog catalog = mock(AiCapabilityToolCatalog.class);
        AiCapabilityAgentLoop agentLoop = mock(AiCapabilityAgentLoop.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "capabilityToolCatalog", catalog);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "capabilityAgentLoop", agentLoop);
        when(modelClient.isEnabled()).thenReturn(true);

        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", "capability_business_operating_overview");
        Map<String, Object> definition = new LinkedHashMap<String, Object>();
        definition.put("function", function);
        List<Map<String, Object>> definitions = Collections.singletonList(definition);
        when(catalog.definitions(any())).thenReturn(definitions);
        when(catalog.isAllowedToolName(eq("capability_business_operating_overview"), any())).thenReturn(true);

        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "capability_call_1");
        call.put("name", "capability_business_operating_overview");
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("今天经营怎么样？"), any(), eq(definitions))).thenReturn(plan);
        when(agentLoop.canHandle(eq(plan), any())).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.chat(null, "今天经营怎么样？", 23L, "jianglan", true));

        assertEquals(true, error.getMessage().contains("没有生成可安全执行"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(mapper).finishRun(any(), eq(null), eq("FAILED"),
            eq("模型未返回可由通用能力层执行的工具计划"));
    }

    @Test
    void passwordCapabilityRedactsThePersistedUserMessageBeforeAnyExecution()
    {
        AiCapabilityToolCatalog catalog = mock(AiCapabilityToolCatalog.class);
        AiCapabilityAgentLoop agentLoop = mock(AiCapabilityAgentLoop.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "capabilityToolCatalog", catalog);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "capabilityAgentLoop", agentLoop);
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", "capability_staff_password_reset");
        Map<String, Object> definition = new LinkedHashMap<String, Object>(); definition.put("function", function);
        List<Map<String, Object>> definitions = Collections.singletonList(definition);
        when(catalog.definitions(any())).thenReturn(definitions);
        when(catalog.isAllowedToolName(eq("capability_staff_password_reset"), any())).thenReturn(true);
        Map<String, Object> call = new LinkedHashMap<String, Object>(); call.put("toolCallId", "password_1");
        call.put("name", "capability_staff_password_reset");
        Map<String, Object> arguments = new LinkedHashMap<String, Object>(); arguments.put("staffUserId", 66L);
        arguments.put("newPassword", "safe123456"); call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>(); plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("把施柳浩密码改成safe123456"), any(), eq(definitions))).thenReturn(plan);
        when(agentLoop.canHandle(eq(plan), any())).thenReturn(false);

        assertThrows(ServiceException.class,
            () -> service.chat(null, "把施柳浩密码改成safe123456", 23L, "jianglan", true));

        verify(mapper).redactMessageContent(any(), eq("[敏感操作请求已脱敏：重置员工密码]"));
    }

    @Test
    void projectSnapshotExposesWhitelistedProjectDetailsFromDomainObjects()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "call_projects");
        call.put("name", "boss_project_snapshot");
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("草稿的项目我看看"), any())).thenReturn(plan);
        when(modelClient.complete(eq("草稿的项目我看看"), eq(plan), any()))
            .thenReturn(Collections.<String, Object>singletonMap("content", "草稿项目是情趣内衣视频制作。"));
        BusinessProject project = new BusinessProject();
        project.setProjectId(16L);
        project.setProjectNo("XM16");
        project.setProjectName("情趣内衣视频制作");
        project.setStatus("DRAFT");
        project.setMainOwnerName("石头");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("summary", Collections.singletonMap("draftCount", 1));
        dashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        service.chat(null, "草稿的项目我看看", 23L, "jianglan", false);

        ArgumentCaptor<List<Map<String, Object>>> messages = listMapCaptor();
        verify(modelClient).complete(eq("草稿的项目我看看"), eq(plan), messages.capture());
        assertEquals(true, String.valueOf(messages.getValue().get(0).get("content")).contains("情趣内衣视频制作"));
        assertEquals(true, String.valueOf(messages.getValue().get(0).get("content")).contains("石头"));
    }

    @Test
    void ambiguousBudgetFollowUpListsCandidatesInsteadOfUsingLooseConversationText()
    {
        when(mapper.selectConversation(77L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 77L));
        Map<String, Object> history = new LinkedHashMap<String, Object>();
        history.put("messageRole", "USER");
        history.put("content", "正在查看项目“新谷酵素视频剪辑”的经营情况。");
        when(mapper.selectMessages(77L, 23L, 12)).thenReturn(Collections.singletonList(history));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> wrongCall = new LinkedHashMap<String, Object>();
        wrongCall.put("toolCallId", "call_wrong_overview");
        wrongCall.put("name", "boss_today_accounting");
        Map<String, Object> wrongPlan = new LinkedHashMap<String, Object>();
        wrongPlan.put("toolCalls", Collections.singletonList(wrongCall));
        wrongPlan.put("content", "公司经营总览");
        when(modelClient.plan(eq("我设置的预算是多少"), any())).thenReturn(wrongPlan);

        BusinessProject target = new BusinessProject();
        target.setProjectId(16L); target.setProjectName("新谷酵素视频剪辑");
        target.setBudgetLimit(new BigDecimal("1000")); target.setBaseCurrency("CNY");
        BusinessProject other = new BusinessProject();
        other.setProjectId(17L); other.setProjectName("越南直播运营");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Arrays.asList(target, other));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(target);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(77L, "我设置的预算是多少", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("还不能唯一确定"), content);
        assertEquals(true, content.contains("新谷酵素视频剪辑"), content);
        assertEquals(true, content.contains("越南直播运营"), content);
        assertEquals(null, result.get("businessCard"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(projectService, never()).getProject(any(Long.class), eq(23L), eq(false), eq(true));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void currentProjectStatusFollowUpCannotFallBackToCompanyOverview()
    {
        when(mapper.selectConversation(78L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 78L));
        Map<String, Object> history = new LinkedHashMap<String, Object>();
        history.put("messageRole", "USER");
        history.put("content", "现在讨论项目“情趣内衣视频制作”。");
        when(mapper.selectMessages(78L, 23L, 12)).thenReturn(Collections.singletonList(history));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> wrongCall = new LinkedHashMap<String, Object>();
        wrongCall.put("toolCallId", "call_wrong_company"); wrongCall.put("name", "boss_today_accounting");
        Map<String, Object> wrongPlan = new LinkedHashMap<String, Object>();
        wrongPlan.put("toolCalls", Collections.singletonList(wrongCall)); wrongPlan.put("content", "公司总览");
        when(modelClient.plan(eq("这个项目现在什么情况"), any())).thenReturn(wrongPlan);

        BusinessProject project = new BusinessProject();
        project.setProjectId(18L); project.setProjectName("情趣内衣视频制作"); project.setStatus("PLANNING");
        project.setMainOwnerName("石头"); project.setCompanyName("上海美丸文化公司");
        project.setBudgetLimit(new BigDecimal("2000")); project.setBaseCurrency("CNY");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(18L, 23L, false, true)).thenReturn(project);
        when(projectService.operatingConfig(18L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(78L, "这个项目现在什么情况", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("情趣内衣视频制作"));
        assertEquals(true, String.valueOf(result.get("content")).contains("规划阶段"));
        assertEquals(false, String.valueOf(result.get("content")).contains("公司总览"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void currentProjectTodayCostUsesProjectAccountingAndServerDate()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(79L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 79L));
        Map<String, Object> history = new LinkedHashMap<String, Object>();
        history.put("messageRole", "USER"); history.put("content", "当前项目是“新谷酵素视频剪辑”。");
        when(mapper.selectMessages(79L, 23L, 12)).thenReturn(Collections.singletonList(history));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> wrongPlan = new LinkedHashMap<String, Object>();
        wrongPlan.put("toolCalls", Collections.emptyList()); wrongPlan.put("content", "不知道");
        when(modelClient.plan(eq("这个项目今天人员成本是多少"), any())).thenReturn(wrongPlan);

        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectName("新谷酵素视频剪辑");
        project.setBudgetLimit(new BigDecimal("1000")); project.setBaseCurrency("CNY");
        Map<String, Object> projectDashboard = new LinkedHashMap<String, Object>();
        projectDashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(projectDashboard);
        Map<String, Object> daily = new LinkedHashMap<String, Object>();
        daily.put("resultId", 90L); daily.put("projectName", "新谷酵素视频剪辑");
        daily.put("revenueAmount", BigDecimal.ZERO); daily.put("costAmount", BigDecimal.ZERO);
        daily.put("personnelCost", new BigDecimal("367.82")); daily.put("profitAmount", new BigDecimal("-367.82"));
        daily.put("budgetSpent", new BigDecimal("2505.75")); daily.put("currency", "CNY");
        Map<String, Object> accountingDashboard = new LinkedHashMap<String, Object>();
        accountingDashboard.put("results", Collections.singletonList(daily)); accountingDashboard.put("facts", Collections.emptyList());
        when(accountingService.dashboard(any(), eq(23L), eq(false))).thenReturn(accountingDashboard);
        Map<String, Object> person = new LinkedHashMap<String, Object>();
        person.put("componentName", "石头"); person.put("amount", new BigDecimal("91.95"));
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("items", Collections.emptyList()); detail.put("personnelItems", Collections.singletonList(person));
        when(accountingService.resultDetail(90L, 23L, false)).thenReturn(detail);

        Map<String, Object> result = service.chat(79L, "这个项目今天人员成本是多少", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("2026-08-13"));
        assertEquals(true, String.valueOf(result.get("content")).contains("人员成本 367.82"));
        assertEquals(true, String.valueOf(result.get("content")).contains("石头 91.95"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void ambiguousPersonPronounAsksForNameInsteadOfGuessing()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.emptyList()); plan.put("content", "猜测是石头");
        when(modelClient.plan(eq("他的成本是多少"), any())).thenReturn(plan);

        Map<String, Object> result = service.chat(null, "他的成本是多少", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("请直接告诉我姓名"));
        assertEquals(false, String.valueOf(result.get("content")).contains("石头"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(projectService, never()).dashboard(any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void pluralMemberProgressUsesRoutineReportsAndNeverInventsProjectAliases()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(80L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 80L));
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        current.put("messageRole", "USER"); current.put("content", "当前查看项目“新谷酵素视频剪辑”，成员是蒋豪和石头。");
        Map<String, Object> older = new LinkedHashMap<String, Object>();
        older.put("messageRole", "ASSISTANT"); older.put("content", "之前还看过项目“情趣内衣视频制作”。");
        when(mapper.selectMessages(80L, 23L, 12)).thenReturn(Arrays.asList(current, older));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> badPlan = new LinkedHashMap<String, Object>();
        badPlan.put("toolCalls", Collections.emptyList());
        badPlan.put("content", "两个项目是同一个项目的别名，整体完成率为0%。");
        when(modelClient.plan(eq("他们的完成进度怎么样"), any())).thenReturn(badPlan);

        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectName("新谷酵素视频剪辑");
        BusinessProjectMember jiang = new BusinessProjectMember(); jiang.setUserNameSnapshot("蒋豪");
        BusinessProjectMember stone = new BusinessProjectMember(); stone.setUserNameSnapshot("石头");
        project.setMembers(Arrays.asList(jiang, stone)); project.setTasks(Collections.emptyList());
        BusinessProjectRoutine editing = new BusinessProjectRoutine();
        editing.setRoutineId(701L); editing.setRoutineName("剪视频"); editing.setAssigneeName("石头"); editing.setUnit("条");
        editing.setTodayTarget(new BigDecimal("10")); editing.setTodayActual(new BigDecimal("10"));
        editing.setCumulativeActual(new BigDecimal("30")); editing.setTodayReportId(901L);
        BusinessProjectRoutine review = new BusinessProjectRoutine();
        review.setRoutineId(702L); review.setRoutineName("审核视频"); review.setAssigneeName("蒋豪"); review.setUnit("条");
        review.setTodayTarget(new BigDecimal("20")); review.setTodayActual(BigDecimal.ZERO); review.setTodayReportId(null);
        project.setRoutines(Arrays.asList(editing, review));
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(project);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(80L, "他们的完成进度怎么样", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("石头：剪视频：今日完成 10.00 / 10.00 条"));
        assertEquals(true, content.contains("蒋豪：审核视频：今日未填报，目标 20.00 条"));
        assertEquals(true, content.contains("累计 30.00 条"));
        assertEquals(true, content.contains("不强行合并成一个总完成率"));
        assertEquals(false, content.contains("同一个项目"));
        assertEquals(false, content.contains("情趣内衣视频制作"));
        assertEquals("MEMBER_PROGRESS", ((Map<?, ?>) result.get("understanding")).get("queryType"));
        assertEquals("RESOLVED", ((Map<?, ?>) result.get("understanding")).get("status"));
        assertEquals(true, result.get("evidence") instanceof List && !((List<?>) result.get("evidence")).isEmpty());
        assertEquals(true, String.valueOf(result.get("evidence")).contains("未填报"));
        assertEquals(true, String.valueOf(result.get("evidence")).contains("MISSING"));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void semanticQueryUsesBossWordsAndReturnsEvidenceInsteadOfTrustingWrongModelIntentOrId()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("queryType", "PROJECT_PORTFOLIO"); // 模型意图也错了，服务端应从老板原话校正。
        arguments.put("projectId", 17L); // 模型给出了错误ID，服务端必须用老板原话重新核对。
        arguments.put("projectName", "情趣内衣视频制作");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "semantic_1"); call.put("name", "boss_query_business"); call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        plan.put("content", "两个项目是同一个项目，进度为0%。");
        when(modelClient.plan(eq("新谷酵素视频剪辑项目的成员完成进度怎么样"), any())).thenReturn(plan);

        BusinessProject target = new BusinessProject();
        target.setProjectId(16L); target.setProjectNo("XM16"); target.setProjectName("新谷酵素视频剪辑");
        BusinessProjectMember stone = new BusinessProjectMember(); stone.setUserId(51L); stone.setUserNameSnapshot("石头");
        target.setMembers(Collections.singletonList(stone)); target.setTasks(Collections.emptyList());
        BusinessProjectRoutine editing = new BusinessProjectRoutine();
        editing.setRoutineId(701L); editing.setRoutineName("剪视频"); editing.setAssigneeName("石头");
        editing.setUnit("条"); editing.setTodayTarget(new BigDecimal("10"));
        editing.setTodayActual(new BigDecimal("8")); editing.setCumulativeActual(new BigDecimal("28"));
        editing.setTodayReportId(801L); target.setRoutines(Collections.singletonList(editing));
        BusinessProject other = new BusinessProject();
        other.setProjectId(17L); other.setProjectNo("XM17"); other.setProjectName("情趣内衣视频制作");
        when(projectService.listProjects(any(), eq(23L), eq(false), eq(true)))
            .thenReturn(Arrays.asList(target, other));
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(target);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(null, "新谷酵素视频剪辑项目的成员完成进度怎么样",
            23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("新谷酵素视频剪辑"));
        assertEquals(true, content.contains("今日完成 8.00 / 10.00 条"));
        assertEquals(false, content.contains("情趣内衣视频制作"));
        assertEquals(false, content.contains("同一个项目"));
        Map<?, ?> understanding = (Map<?, ?>) result.get("understanding");
        assertEquals("MEMBER_PROGRESS", understanding.get("queryType"));
        assertEquals(16L, ((Map<?, ?>) understanding.get("project")).get("projectId"));
        assertEquals("RESOLVED", understanding.get("status"));
        List<?> evidence = (List<?>) result.get("evidence");
        assertEquals(true, evidence != null && !evidence.isEmpty());
        assertEquals(true, !((List<?>) ((Map<?, ?>) evidence.get(0)).get("facts")).isEmpty());
        verify(projectService, org.mockito.Mockito.atLeastOnce()).listProjects(any(), eq(23L), eq(false), eq(true));
        verify(projectService, never()).getProject(17L, 23L, false, true);
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void semanticQueryStopsAndReturnsCandidatesWhenProjectCannotBeUniquelyResolved()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("queryType", "PROJECT_BUDGET"); arguments.put("projectName", "视频");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "semantic_2"); call.put("name", "boss_query_business"); call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call)); plan.put("content", "我猜是第一个项目。预算1000元。");
        when(modelClient.plan(eq("视频项目的预算是多少"), any())).thenReturn(plan);
        BusinessProject first = new BusinessProject(); first.setProjectId(16L); first.setProjectName("新谷酵素视频剪辑");
        first.setMainOwnerName("蒋豪");
        BusinessProject second = new BusinessProject(); second.setProjectId(17L); second.setProjectName("情趣内衣视频制作");
        second.setMainOwnerName("石头");
        when(projectService.listProjects(any(), eq(23L), eq(false), eq(true)))
            .thenReturn(Arrays.asList(first, second));

        Map<String, Object> result = service.chat(null, "视频项目的预算是多少", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("请选择"));
        assertEquals(true, String.valueOf(result.get("content")).contains("新谷酵素视频剪辑"));
        assertEquals(true, String.valueOf(result.get("content")).contains("情趣内衣视频制作"));
        assertEquals("AMBIGUOUS", ((Map<?, ?>) result.get("understanding")).get("status"));
        assertEquals(null, result.get("evidence"));
        verify(projectService, never()).getProject(any(Long.class), eq(23L), eq(false), eq(true));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void projectPortfolioFollowUpWithoutModelToolExpandsEveryProjectFromPersistedUnderstanding()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(83L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 83L));
        Map<String, Object> previous = portfolioUnderstandingMessage();
        when(mapper.selectMessages(83L, 23L, 12)).thenReturn(Collections.singletonList(previous));
        when(mapper.selectMessages(83L, 23L, 20)).thenReturn(Collections.singletonList(previous));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> noToolPlan = new LinkedHashMap<String, Object>();
        noToolPlan.put("toolCalls", Collections.emptyList());
        noToolPlan.put("content", "仍然只有项目总数，无法展开。 ");
        when(modelClient.plan(eq("分别是什么"), any())).thenReturn(noToolPlan);

        BusinessProject editing = portfolioProject(16L, "新谷酵素视频剪辑", "蒋豪",
            "剪辑并交付1000条新谷酵素视频", "ACTIVE");
        BusinessProject content = portfolioProject(17L, "情趣内衣视频制作", "石头",
            "制作1000条情趣内衣短视频", "ACTIVE");
        when(projectService.dashboard(23L, false, true)).thenReturn(portfolioDashboard(editing, content));

        Map<String, Object> result = service.chat(83L, "分别是什么", 23L, "jianglan", false);

        assertExpandedPortfolio(result);
        assertEquals("PROJECT_PORTFOLIO", ((Map<?, ?>) result.get("understanding")).get("queryType"));
        assertPortfolioEvidence(result, 16L, "新谷酵素视频剪辑", "蒋豪", "剪辑并交付1000条新谷酵素视频");
        assertPortfolioEvidence(result, 17L, "情趣内衣视频制作", "石头", "制作1000条情趣内衣短视频");
        verify(projectService).dashboard(23L, false, true);
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(staffService, never()).listOptions();
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void projectPortfolioFollowUpOverridesWrongModelQueryAndStillReturnsProjectFacts()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(84L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 84L));
        Map<String, Object> previous = portfolioUnderstandingMessage();
        when(mapper.selectMessages(84L, 23L, 12)).thenReturn(Collections.singletonList(previous));
        when(mapper.selectMessages(84L, 23L, 20)).thenReturn(Collections.singletonList(previous));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> wrongArguments = new LinkedHashMap<String, Object>();
        wrongArguments.put("queryType", "STAFF_OVERVIEW");
        Map<String, Object> wrongCall = new LinkedHashMap<String, Object>();
        wrongCall.put("toolCallId", "wrong_follow_up");
        wrongCall.put("name", "boss_query_business");
        wrongCall.put("arguments", wrongArguments);
        Map<String, Object> wrongPlan = new LinkedHashMap<String, Object>();
        wrongPlan.put("toolCalls", Collections.singletonList(wrongCall));
        wrongPlan.put("content", "这是人员概况。 ");
        when(modelClient.plan(eq("是什么内容"), any())).thenReturn(wrongPlan);

        BusinessProject editing = portfolioProject(16L, "新谷酵素视频剪辑", "蒋豪",
            "剪辑并交付1000条新谷酵素视频", "ACTIVE");
        BusinessProject content = portfolioProject(17L, "情趣内衣视频制作", "石头",
            "制作1000条情趣内衣短视频", "ACTIVE");
        when(projectService.dashboard(23L, false, true)).thenReturn(portfolioDashboard(editing, content));

        Map<String, Object> result = service.chat(84L, "是什么内容", 23L, "jianglan", false);

        assertExpandedPortfolio(result);
        assertEquals("PROJECT_PORTFOLIO", ((Map<?, ?>) result.get("understanding")).get("queryType"));
        assertPortfolioEvidence(result, 16L, "新谷酵素视频剪辑", "蒋豪", "剪辑并交付1000条新谷酵素视频");
        assertPortfolioEvidence(result, 17L, "情趣内衣视频制作", "石头", "制作1000条情趣内衣短视频");
        verify(projectService).dashboard(23L, false, true);
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(staffService, never()).listOptions();
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void projectPortfolioUsesCompleteDirectoryBeyondDashboardTopTenForAnswerAndEvidence()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        List<BusinessProject> allProjects = new ArrayList<BusinessProject>();
        for (long id = 1L; id <= 12L; id++)
        {
            String name = id == 11L ? "第十一个完整项目" : id == 12L ? "第十二个完整项目" : "项目" + id;
            allProjects.add(portfolioProject(id, name, "负责人" + id, "项目内容" + id, "ACTIVE"));
        }
        Map<String, Object> dashboard = portfolioDashboard(allProjects.toArray(new BusinessProject[0]));
        dashboard.put("projects", new ArrayList<BusinessProject>(allProjects.subList(0, 10)));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.listProjects(any(), eq(23L), eq(false), eq(true))).thenReturn(allProjects);

        Map<String, Object> result = service.chat(null, "项目态势", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("第十一个完整项目"), content);
        assertEquals(true, content.contains("第十二个完整项目"), content);
        assertEquals(true, content.contains("负责人11"), content);
        assertEquals(true, content.contains("项目内容12"), content);
        assertEquals("PROJECT_PORTFOLIO", ((Map<?, ?>) result.get("understanding")).get("queryType"));
        assertPortfolioEvidence(result, 11L, "第十一个完整项目", "负责人11", "项目内容11");
        assertPortfolioEvidence(result, 12L, "第十二个完整项目", "负责人12", "项目内容12");
        verify(projectService).dashboard(23L, false, true);
        verify(projectService).listProjects(any(), eq(23L), eq(false), eq(true));
    }

    @Test
    void pausedClosedAndCanceledPortfolioStatusesAreRenderedInChinese()
    {
        BusinessProject paused = portfolioProject(31L, "暂停项目", "负责人甲", "暂停项目内容", "PAUSED");
        BusinessProject closed = portfolioProject(32L, "关闭项目", "负责人乙", "关闭项目内容", "CLOSED");
        BusinessProject canceled = portfolioProject(33L, "取消项目", "负责人丙", "取消项目内容", "CANCELED");
        Map<String, Object> dashboard = portfolioDashboard(paused, closed, canceled);
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.listProjects(any(), eq(23L), eq(false), eq(true)))
            .thenReturn(Arrays.asList(paused, closed, canceled));

        Map<String, Object> result = service.chat(null, "全部项目的状态", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("“暂停项目”｜负责人：负责人甲｜状态：已暂停"), content);
        assertEquals(true, content.contains("“关闭项目”｜负责人：负责人乙｜状态：已结项"), content);
        assertEquals(true, content.contains("“取消项目”｜负责人：负责人丙｜状态：已取消"), content);
        assertEquals(false, content.contains("状态：PAUSED"), content);
        assertEquals(false, content.contains("状态：CLOSED"), content);
        assertEquals(false, content.contains("状态：CANCELED"), content);
    }

    @Test
    void portfolioFollowUpDoesNotSkipLatestAssistantWithoutUnderstandingToReuseOlderTopic()
    {
        when(mapper.selectConversation(85L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 85L));
        final Map<String, Object> latest = new LinkedHashMap<String, Object>();
        latest.put("messageRole", "ASSISTANT");
        latest.put("content", "我们已经切换到另一件事情，请告诉我接下来要做什么。 ");
        latest.put("metadataJson", "{\"sources\":[],\"executionMode\":\"LOCAL\"}");
        final Map<String, Object> older = portfolioUnderstandingMessage();
        when(mapper.selectMessages(85L, 23L, 12)).thenAnswer(invocation ->
            new ArrayList<Map<String, Object>>(Arrays.asList(latest, older)));
        when(mapper.selectMessages(85L, 23L, 20)).thenAnswer(invocation ->
            new ArrayList<Map<String, Object>>(Arrays.asList(latest, older)));
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> noToolPlan = new LinkedHashMap<String, Object>();
        noToolPlan.put("toolCalls", Collections.emptyList());
        noToolPlan.put("content", "我准备沿用更早的项目话题。 ");
        when(modelClient.plan(eq("分别是什么"), any())).thenReturn(noToolPlan);

        Map<String, Object> result = service.chat(85L, "分别是什么", 23L, "jianglan", false);

        assertEquals(null, result.get("understanding"));
        assertEquals(null, result.get("evidence"));
        assertEquals(false, String.valueOf(result.get("content")).contains("项目态势"),
            String.valueOf(result.get("content")));
        verify(projectService, never()).dashboard(any(), any(Boolean.class), any(Boolean.class));
        verify(projectService, never()).listProjects(any(), any(), any(Boolean.class), any(Boolean.class));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void assistantProjectNameIsNeverUsedAsAuthoritativeEntityContext()
    {
        when(mapper.selectConversation(82L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 82L));
        Map<String, Object> hallucinatedHistory = new LinkedHashMap<String, Object>();
        hallucinatedHistory.put("messageRole", "ASSISTANT");
        hallucinatedHistory.put("content", "当前项目是“新谷酵素视频剪辑”。");
        when(mapper.selectMessages(82L, 23L, 12)).thenReturn(Collections.singletonList(hallucinatedHistory));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "legacy_detail"); call.put("name", "boss_project_detail");
        call.put("arguments", Collections.emptyMap());
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call)); plan.put("content", "我沿用上一次回答里的项目。 ");
        when(modelClient.plan(eq("这个项目现在什么情况"), any())).thenReturn(plan);
        BusinessProject first = new BusinessProject(); first.setProjectId(16L); first.setProjectName("新谷酵素视频剪辑");
        BusinessProject second = new BusinessProject(); second.setProjectId(17L); second.setProjectName("情趣内衣视频制作");
        when(projectService.listProjects(any(), eq(23L), eq(false), eq(true)))
            .thenReturn(Arrays.asList(first, second));

        Map<String, Object> result = service.chat(82L, "这个项目现在什么情况", 23L, "jianglan", false);

        assertEquals(false, String.valueOf(result.get("content")).contains("沿用上一次回答"));
        verify(projectService, never()).getProject(any(Long.class), eq(23L), eq(false), eq(true));
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void agentCanContinueFromProjectSnapshotToAccountingBreakdownBeforeAnswering()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        when(modelClient.providerCode()).thenReturn("DEEPSEEK");
        when(modelClient.modelName()).thenReturn("deepseek-v4-flash");
        String question = "今天为什么亏损，具体是哪个项目造成的？";
        Map<String, Object> snapshotCall = new LinkedHashMap<String, Object>();
        snapshotCall.put("toolCallId", "call_snapshot");
        snapshotCall.put("name", "boss_project_snapshot");
        snapshotCall.put("arguments", Collections.emptyMap());
        Map<String, Object> firstPlan = new LinkedHashMap<String, Object>();
        firstPlan.put("assistantMessageJson", "{\"role\":\"assistant\",\"tool_calls\":[]}");
        firstPlan.put("toolCalls", Collections.singletonList(snapshotCall));
        when(modelClient.plan(eq(question), any())).thenReturn(firstPlan);

        Map<String, Object> detailCall = new LinkedHashMap<String, Object>();
        detailCall.put("toolCallId", "call_accounting_detail");
        detailCall.put("name", "boss_project_accounting_detail");
        Map<String, Object> detailArguments = new LinkedHashMap<String, Object>();
        detailArguments.put("projectId", 16L);
        detailArguments.put("bizDate", "2026-08-13");
        detailCall.put("arguments", detailArguments);
        Map<String, Object> detailPlan = new LinkedHashMap<String, Object>();
        detailPlan.put("assistantMessageJson", "{\"role\":\"assistant\",\"tool_calls\":[]}");
        detailPlan.put("toolCalls", Collections.singletonList(detailCall));
        Map<String, Object> finalAnswer = new LinkedHashMap<String, Object>();
        finalAnswer.put("assistantMessageJson", "{\"role\":\"assistant\",\"content\":\"亏损来自人员成本\"}");
        finalAnswer.put("toolCalls", Collections.emptyList());
        finalAnswer.put("content", "今天的亏损来自情趣内衣视频制作项目，其中石头人员成本 100 元。");
        when(modelClient.continueWithTools(eq(question), any(), any())).thenReturn(detailPlan, finalAnswer);

        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectName("情趣内衣视频制作"); project.setStatus("ACTIVE");
        project.setBudgetLimit(new BigDecimal("1000"));
        Map<String, Object> projectDashboard = new LinkedHashMap<String, Object>();
        projectDashboard.put("summary", Collections.singletonMap("activeCount", 1));
        projectDashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(projectDashboard);

        Map<String, Object> dailyResult = new LinkedHashMap<String, Object>();
        dailyResult.put("resultId", 90L); dailyResult.put("projectId", 16L);
        dailyResult.put("projectName", "情趣内衣视频制作");
        dailyResult.put("profitAmount", new BigDecimal("-100"));
        dailyResult.put("budgetSpent", new BigDecimal("2500"));
        Map<String, Object> accountingDashboard = new LinkedHashMap<String, Object>();
        accountingDashboard.put("summary", Collections.singletonMap("profitAmount", new BigDecimal("-100")));
        accountingDashboard.put("results", Collections.singletonList(dailyResult));
        accountingDashboard.put("facts", Collections.emptyList());
        when(accountingService.dashboard(any(), eq(23L), eq(false))).thenReturn(accountingDashboard);
        Map<String, Object> personnel = new LinkedHashMap<String, Object>();
        personnel.put("componentCode", "PERSONNEL_COST_PERSON"); personnel.put("componentName", "石头");
        personnel.put("amount", new BigDecimal("100"));
        Map<String, Object> resultDetail = new LinkedHashMap<String, Object>();
        resultDetail.put("items", Collections.emptyList());
        resultDetail.put("personnelItems", Collections.singletonList(personnel));
        when(accountingService.resultDetail(90L, 23L, false)).thenReturn(resultDetail);

        Map<String, Object> result = service.chat(null, question, 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("情趣内衣视频制作"));
        assertEquals(true, String.valueOf(result.get("content")).contains("经营结果 -100.00"));
        assertEquals(true, String.valueOf(result.get("content")).contains("石头 100.00"));
        assertEquals(true, String.valueOf(result.get("content")).contains("累计超预算 1500.00"));
        assertEquals("OPERATING_ANALYSIS", ((Map<?, ?>) result.get("businessCard")).get("type"));
        assertEquals("石头", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) result.get("businessCard")).get("personnelItems")).get(0)).get("componentName"));
        assertEquals(new BigDecimal("1500"), ((Map<?, ?>) ((Map<?, ?>) result.get("businessCard")).get("metrics")).get("overBudgetAmount"));
        verify(accountingService).dashboard(any(), eq(23L), eq(false));
        verify(accountingService).resultDetail(90L, 23L, false);
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void projectFollowUpWithoutContextDoesNotSubstituteCompanyOverview()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        String question = "这个项目为什么已经超预算？";
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("assistantMessageJson", "{\"role\":\"assistant\",\"content\":\"沿用旧数据回答\"}");
        plan.put("toolCalls", Collections.emptyList());
        plan.put("content", "沿用旧数据回答");
        when(modelClient.plan(eq(question), any())).thenReturn(plan);
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("summary", Collections.singletonMap("activeCount", 1));
        dashboard.put("projects", Collections.emptyList());
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> result = service.chat(null, question, 23L, "jianglan", false);

        assertEquals("当前没有可查询的项目。", result.get("content"));
        verify(accountingService, never()).bossOverview(any(), any(Boolean.class));
        verify(projectService, org.mockito.Mockito.atLeastOnce()).dashboard(23L, false, true);
        verify(modelClient, never()).complete(any(), any(), any());
    }

    @Test
    void budgetAdjustmentRequiresConfirmationBeforeChangingProject()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("projectId", 16L); arguments.put("budgetLimit", 3000);
        arguments.put("currency", "CNY"); arguments.put("reason", "现有人员投入已超过原预算");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "call_budget"); call.put("name", "boss_prepare_budget_adjustment");
        call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("把新谷项目预算调整到3000元，因为人员投入已超过原预算"), any())).thenReturn(plan);
        when(modelClient.complete(eq("把新谷项目预算调整到3000元，因为人员投入已超过原预算"), eq(plan), any()))
            .thenReturn(Collections.<String, Object>singletonMap("content", "预算调整确认单已准备。"));
        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectNo("XM16"); project.setProjectName("新谷酵素视频剪辑");
        project.setBudgetLimit(new BigDecimal("1000")); project.setBaseCurrency("CNY");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(project));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> chat = service.chat(null, "把新谷项目预算调整到3000元，因为人员投入已超过原预算", 23L, "jianglan", false);

        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("BUDGET_ADJUSTMENT", request.get("actionCode"));
        assertEquals(new BigDecimal("3000"), ((Map<?, ?>) request.get("project")).get("budgetLimit"));
        verify(projectService, never()).updateBudget(any(), any(), any(), any(), any(), any(), any(Boolean.class));

        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-budget");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        when(mapper.finishActionRequest(eq((Long) request.get("actionRequestId")), any())).thenReturn(1);
        BusinessProject updated = new BusinessProject();
        updated.setProjectId(16L); updated.setProjectNo("XM16"); updated.setProjectName("新谷酵素视频剪辑");
        updated.setBudgetLimit(new BigDecimal("3000")); updated.setBaseCurrency("CNY");
        when(projectService.updateBudget(16L, new BigDecimal("3000"), "CNY", "现有人员投入已超过原预算",
            23L, "jianglan", true)).thenReturn(updated);

        Map<String, Object> executed = service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan");

        assertEquals("EXECUTED", executed.get("status"));
        assertEquals(new BigDecimal("3000"), executed.get("budgetLimit"));
        verify(projectService).updateBudget(16L, new BigDecimal("3000"), "CNY", "现有人员投入已超过原预算",
            23L, "jianglan", true);
    }

    @Test
    void createProjectRequestIsRedirectedToProposalPage()
    {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.chat(null, "帮我创建一个新项目", 23L, "jianglan", false));

        assertEquals(true, error.getMessage().contains("立项申请"));
        verify(mapper, never()).insertWorkflow(any());
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createProjectRequestStartsWithPlainLanguageQuestions()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.emptyList());
        plan.put("content", "**请补充项目名称**");
        when(modelClient.plan(eq("帮我创建一个新项目"), any())).thenReturn(plan);
        when(modelClient.complete(eq("帮我创建一个新项目"), eq(plan), any()))
            .thenReturn(Collections.<String, Object>singletonMap("content", "**请补充项目名称**"));
        when(projectService.userOptions(null)).thenReturn(Collections.emptyList());
        when(staffService.listOptions()).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.chat(null, "帮我创建一个新项目", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("项目叫什么"));
        assertEquals(true, content.contains("想让谁负责"));
        assertEquals(true, content.contains("上海公司"));
        assertEquals(false, content.contains("**"));
        assertEquals(false, content.contains("LIVE"));
        assertEquals(false, content.contains("YYYY"));
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void projectCreationRequiresAnOwnedConfirmationBeforeCallingProjectService()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("projectName", "AI 测试项目");
        arguments.put("ownerName", "石头");
        arguments.put("companyName", "上海");
        arguments.put("objective", "完成系统验收");
        arguments.put("planStartDate", "2026-08-12");
        arguments.put("planEndDate", "2026-08-31");
        arguments.put("accountingMode", "COST");
        arguments.put("budgetLimit", 1000);
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("toolCallId", "call_create");
        call.put("name", "boss_prepare_project_create");
        call.put("arguments", arguments);
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("建立AI测试项目"), any())).thenReturn(plan);

        assertThrows(ServiceException.class,
            () -> service.chat(null, "建立AI测试项目", 23L, "jianglan", false));
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void offlineShortAffirmationNeverCreatesAProjectOrConfirmation()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-12T06:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> history = new LinkedHashMap<String, Object>();
        history.put("messageRole", "USER");
        history.put("content", "现在开始，到9月30日结束吧");
        when(mapper.selectMessages(any(), eq(23L), eq(12))).thenReturn(Collections.singletonList(history));

        service.chat(null, "普通就行，对的", 23L, "jianglan", false);

        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void executedProjectIsNotPreparedAgainForPlanningFollowUp()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("name", "boss_prepare_project_create");
        call.put("toolCallId", "call_duplicate");
        call.put("arguments", Collections.singletonMap("projectName", "情趣内衣视频制作"));
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("这个项目是不是已经创建了"), any())).thenReturn(plan);
        Map<String, Object> latest = new LinkedHashMap<String, Object>();
        latest.put("status", "EXECUTED");
        latest.put("resultJson", "{\"projectId\":16,\"projectNo\":\"XM16\",\"projectName\":\"情趣内衣视频制作\"}");
        when(mapper.selectLatestActionRequest(any(), eq(23L), eq("CREATE_PROJECT"))).thenReturn(latest);

        Map<String, Object> result = service.chat(null, "这个项目是不是已经创建了", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).trim().length() > 0);
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void advanceDraftProjectUsesExistingDataAndRequiresConfirmation()
    {
        when(modelClient.isEnabled()).thenReturn(true);
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("name", "boss_prepare_project_transition");
        call.put("toolCallId", "call_transition");
        call.put("arguments", Collections.singletonMap("projectName", "情趣内衣视频制作"));
        Map<String, Object> plan = new LinkedHashMap<String, Object>();
        plan.put("toolCalls", Collections.singletonList(call));
        when(modelClient.plan(eq("推进一下这个"), any())).thenReturn(plan);
        when(modelClient.complete(eq("推进一下这个"), eq(plan), any()))
            .thenReturn(Collections.<String, Object>singletonMap("content", "已准备推进确认单"));
        BusinessProject draft = new BusinessProject();
        draft.setProjectId(16L); draft.setProjectNo("XM16"); draft.setProjectName("情趣内衣视频制作");
        draft.setStatus("DRAFT"); draft.setMainOwnerName("石头");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(draft));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> chat = service.chat(null, "推进一下这个", 23L, "jianglan", false);

        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("PROJECT_TRANSITION", request.get("actionCode"));
        assertEquals("情趣内衣视频制作", ((Map<?, ?>) request.get("project")).get("projectName"));
        verify(projectService, never()).createProject(any(), any(), any());

        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-transition");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        when(mapper.finishActionRequest(eq((Long) request.get("actionRequestId")), any())).thenReturn(1);
        BusinessProject planning = new BusinessProject();
        planning.setProjectId(16L); planning.setProjectNo("XM16"); planning.setProjectName("情趣内衣视频制作");
        planning.setStatus("PLANNING"); planning.setMainOwnerName("石头");
        when(projectService.transition(16L, "START_PLANNING", "老板通过 AI 确认进入规划", 23L, "jianglan", true))
            .thenReturn(planning);

        Map<String, Object> executed = service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan");

        assertEquals("PLANNING", executed.get("projectStatus"));
        verify(projectService).transition(16L, "START_PLANNING", "老板通过 AI 确认进入规划", 23L, "jianglan", true);
    }

    @Test
    void shortNumberReplyKeepsThePreviousAdvanceChoiceAndExplainsCurrentState()
    {
        Map<String, Object> previous = new LinkedHashMap<String, Object>();
        previous.put("messageRole", "ASSISTANT");
        previous.put("content", "目前有情趣内衣视频制作项目待办。1. 同意推进这个项目进入正式规划阶段；2. 处理其他事项。");
        when(mapper.selectMessages(any(), eq(23L), eq(12))).thenReturn(Collections.singletonList(previous));
        BusinessProject planning = new BusinessProject();
        planning.setProjectId(16L); planning.setProjectName("情趣内衣视频制作");
        planning.setStatus("PLANNING"); planning.setBaselineStatus("DRAFT"); planning.setMainOwnerName("石头");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(planning));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> result = service.chat(null, "1", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("已经处于规划中"), content);
        assertEquals(true, content.contains("负责人完善并提交计划"), content);
        assertEquals(false, content.contains("先告诉我"), content);
        verify(mapper, never()).insertActionRequest(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void shortAffirmativeAfterSubmittedStatusStartsReviewInsteadOfRepeatingAdvance()
    {
        Map<String, Object> previous = new LinkedHashMap<String, Object>();
        previous.put("messageRole", "ASSISTANT");
        previous.put("content", "项目已经完成规划并提交计划，现在等待老板审核，不需要再次推进。");
        when(mapper.selectMessages(any(), eq(23L), eq(12))).thenReturn(Collections.singletonList(previous));
        BusinessProject submitted = submittedPlanProject();
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(submitted));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(submitted);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(null, "可以", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("计划已提交"), content);
        assertEquals(true, content.contains("需要关注"), content);
        assertEquals(false, content.contains("不需要再次推进"), content);
        assertEquals(false, result.containsKey("actionRequest"));
    }

    @Test
    void submittedPlanReviewReadsRealPlanAndExplainsMissingConfiguration()
    {
        BusinessProject submitted = submittedPlanProject();
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(submitted));
        dashboard.put("projects", Collections.singletonList(submitted));
        dashboard.put("summary", Collections.emptyMap());
        dashboard.put("tasks", Collections.emptyList());
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(submitted);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(null, "帮我审核待审批计划", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("情趣内衣视频制作"), content);
        assertEquals(true, content.contains("尚未设置项目 KPI"), content);
        assertEquals(true, content.contains("尚未设置成员计划投入"), content);
        Map<?, ?> planReview = (Map<?, ?>) result.get("planReview");
        assertEquals(true, planReview.get("ready"));
        assertEquals("情趣内衣视频制作", ((Map<?, ?>) planReview.get("project")).get("projectName"));
        assertEquals(0, planReview.get("routineCount"));
        assertEquals(true, ((List<?>) planReview.get("warnings")).contains("尚未设置项目 KPI，可根据项目需要后续补充"));
        assertEquals(false, result.containsKey("actionRequest"));
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void pendingQuestionAutomaticallyOpensTheOnlySubmittedPlanInsteadOfSuggestingPlanningAgain()
    {
        BusinessProject submitted = submittedPlanProject();
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(submitted));
        dashboard.put("tasks", Collections.emptyList());
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(submitted);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.chat(null, "今天有什么事情需要我做", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("计划已提交"), content);
        assertEquals(true, result.containsKey("planReview"));
        assertEquals(false, content.contains("开始规划"), content);
        assertEquals(false, content.contains("推进规划"), content);
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void bossApprovalCreatesConfirmationThenStartsProjectOnlyAfterConfirm()
    {
        BusinessProject submitted = submittedPlanProject();
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(submitted));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(submitted);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> chat = service.chat(null, "批准", 23L, "jianglan", false);
        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("PROJECT_PLAN_DECISION", request.get("actionCode"));
        assertEquals("APPROVE", ((Map<?, ?>) request.get("project")).get("decision"));
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));

        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-plan-approval");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        when(mapper.finishActionRequest(eq((Long) request.get("actionRequestId")), any())).thenReturn(1);
        BusinessProject active = submittedPlanProject();
        active.setStatus("ACTIVE"); active.setBaselineStatus("APPROVED");
        when(projectService.transition(16L, "CONFIRM_BASELINE", "老板通过 AI 审核计划并确认启动", 23L, "jianglan", true))
            .thenReturn(active);

        Map<String, Object> executed = service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan");

        assertEquals("ACTIVE", executed.get("projectStatus"));
        assertEquals("CONFIRM_BASELINE", executed.get("transitionAction"));
        verify(projectService).transition(16L, "CONFIRM_BASELINE", "老板通过 AI 审核计划并确认启动", 23L, "jianglan", true);
    }

    @Test
    void planApprovalButtonPhraseCreatesConfirmationWithoutAskingAgain()
    {
        when(modelClient.isEnabled()).thenReturn(false);
        Map<String, Object> malformedArguments = new LinkedHashMap<String, Object>();
        malformedArguments.put("projectName", "情趣内衣视频制作");
        Map<String, Object> malformedCall = new LinkedHashMap<String, Object>();
        malformedCall.put("toolCallId", "plan_decision_without_decision");
        malformedCall.put("name", "boss_prepare_plan_decision");
        malformedCall.put("arguments", malformedArguments);
        Map<String, Object> malformedPlan = new LinkedHashMap<String, Object>();
        malformedPlan.put("toolCalls", Collections.singletonList(malformedCall));
        malformedPlan.put("content", "请明确是批准启动，还是退回负责人调整。");
        when(modelClient.plan(eq("批准项目“情趣内衣视频制作”的计划并启动"), any())).thenReturn(malformedPlan);
        BusinessProject submitted = submittedPlanProject();
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(submitted));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(16L, 23L, false, true)).thenReturn(submitted);
        when(projectService.operatingConfig(16L, 23L, false, true)).thenReturn(Collections.emptyMap());

        Map<String, Object> chat = service.chat(null, "批准项目“情趣内衣视频制作”的计划并启动", 23L, "jianglan", false);

        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("PROJECT_PLAN_DECISION", request.get("actionCode"));
        assertEquals("APPROVE", ((Map<?, ?>) request.get("project")).get("decision"));
        assertEquals(false, String.valueOf(chat.get("content")).contains("请明确"));
    }

    @Test
    void pendingDecisionQueryReturnsNumberedStableListAndPersistsItInUnderstanding()
    {
        BusinessProject draft = portfolioProject(16L, "情趣内衣视频制作", "石头", "制作1000条视频", "DRAFT");
        BusinessProject paused = portfolioProject(18L, "直播增长", "Mina", "提升直播销售", "PAUSED");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Arrays.asList(draft, paused));
        dashboard.put("tasks", Collections.emptyList());
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> result = service.chat(null, "有哪些事情需要我处理", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("1. 项目“情趣内衣视频制作”"), content);
        assertEquals(true, content.contains("2. 项目“直播增长”"), content);
        assertEquals(true, content.contains("处理第一个"), content);
        Map<?, ?> understanding = (Map<?, ?>) result.get("understanding");
        assertEquals("PENDING_DECISIONS", understanding.get("queryType"));
        List<?> decisions = (List<?>) understanding.get("pendingDecisions");
        assertEquals(2, decisions.size());
        assertEquals(16L, ((Map<?, ?>) decisions.get(0)).get("projectId"));
        assertEquals("START_PLANNING", ((Map<?, ?>) decisions.get(0)).get("decisionType"));
        assertEquals("RESUME_PROJECT", ((Map<?, ?>) decisions.get(1)).get("decisionType"));
    }

    @Test
    void processingFirstPendingItemUsesPersistedProjectIdAndRequiresConfirmation()
    {
        when(mapper.selectConversation(86L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 86L));
        Map<String, Object> previous = pendingUnderstandingMessage();
        when(mapper.selectMessages(86L, 23L, 12)).thenReturn(Collections.singletonList(previous));
        when(mapper.selectMessages(86L, 23L, 20)).thenReturn(Collections.singletonList(previous));
        BusinessProject draft = portfolioProject(16L, "情趣内衣视频制作", "石头", "制作1000条视频", "DRAFT");
        BusinessProject other = portfolioProject(18L, "直播增长", "Mina", "提升直播销售", "PAUSED");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Arrays.asList(draft, other));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> result = service.chat(86L, "处理第一个", 23L, "jianglan", false);

        Map<?, ?> request = (Map<?, ?>) result.get("actionRequest");
        assertEquals("PROJECT_TRANSITION", request.get("actionCode"));
        assertEquals(16L, ((Map<?, ?>) request.get("project")).get("projectId"));
        assertEquals("START_PLANNING", ((Map<?, ?>) request.get("project")).get("transitionAction"));
        assertEquals(false, String.valueOf(result.get("content")).contains("直播增长"));
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void processingOutOfRangePendingItemFailsClosedWithoutPreparingAction()
    {
        when(mapper.selectConversation(87L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 87L));
        Map<String, Object> previous = pendingUnderstandingMessage();
        when(mapper.selectMessages(87L, 23L, 12)).thenReturn(Collections.singletonList(previous));
        when(mapper.selectMessages(87L, 23L, 20)).thenReturn(Collections.singletonList(previous));

        Map<String, Object> result = service.chat(87L, "处理第9个", 23L, "jianglan", false);

        assertEquals(true, String.valueOf(result.get("content")).contains("只有 2 项"));
        assertEquals(false, result.containsKey("actionRequest"));
        verify(projectService, never()).dashboard(any(), any(Boolean.class), any(Boolean.class));
        verify(mapper, never()).insertActionRequest(any());
    }

    @Test
    void pausedPendingItemCanOnlyResumeAfterExplicitConfirmation()
    {
        when(mapper.selectConversation(88L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 88L));
        Map<String, Object> previous = pendingUnderstandingMessage();
        when(mapper.selectMessages(88L, 23L, 12)).thenReturn(Collections.singletonList(previous));
        when(mapper.selectMessages(88L, 23L, 20)).thenReturn(Collections.singletonList(previous));
        BusinessProject paused = portfolioProject(18L, "直播增长", "Mina", "提升直播销售", "PAUSED");
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("projects", Collections.singletonList(paused));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);

        Map<String, Object> chat = service.chat(88L, "处理第2个", 23L, "jianglan", false);
        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("RESUME_PROJECT", ((Map<?, ?>) request.get("project")).get("transitionAction"));
        verify(projectService, never()).transition(any(), any(), any(), any(), any(), any(Boolean.class));

        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-resume");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        when(mapper.finishActionRequest(eq((Long) request.get("actionRequestId")), any())).thenReturn(1);
        BusinessProject active = portfolioProject(18L, "直播增长", "Mina", "提升直播销售", "ACTIVE");
        when(projectService.transition(18L, "RESUME_PROJECT", "老板通过 AI 确认恢复执行", 23L, "jianglan", true))
            .thenReturn(active);

        Map<String, Object> executed = service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan");

        assertEquals("ACTIVE", executed.get("projectStatus"));
        assertEquals("RESUME_PROJECT", executed.get("transitionAction"));
    }

    @Test
    void acceptanceReviewShowsSubmittedResultsAndNeverChangesProject()
    {
        BusinessProject acceptance = pendingAcceptanceProject(31L, "新品视频交付", 501L);
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(acceptance));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(31L, 23L, false, true)).thenReturn(acceptance);

        Map<String, Object> result = service.chat(null, "帮我审核待验收项目", 23L, "jianglan", false);

        Map<?, ?> review = (Map<?, ?>) result.get("acceptanceReview");
        assertEquals(true, review.get("ready"));
        assertEquals(true, review.get("canApprove"));
        assertEquals(501L, ((Map<?, ?>) review.get("acceptance")).get("acceptanceId"));
        assertEquals("已交付1000条视频", ((Map<?, ?>) review.get("acceptance")).get("resultSummary"));
        assertEquals(2, review.get("attachmentCount"));
        assertEquals(false, result.containsKey("actionRequest"));
        verify(projectService, never()).reviewAcceptance(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void acceptanceApprovalRequiresConfirmationAndLocksSubmissionVersion()
    {
        BusinessProject acceptance = pendingAcceptanceProject(31L, "新品视频交付", 501L);
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(acceptance));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        when(projectService.getProject(31L, 23L, false, true)).thenReturn(acceptance);

        Map<String, Object> chat = service.chat(null, "验收通过并结项新品视频交付", 23L, "jianglan", false);
        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        assertEquals("PROJECT_ACCEPTANCE_DECISION", request.get("actionCode"));
        assertEquals(501L, ((Map<?, ?>) request.get("project")).get("acceptanceId"));
        assertEquals("APPROVED", ((Map<?, ?>) request.get("project")).get("decision"));
        verify(projectService, never()).reviewAcceptance(any(), any(), any(), any(), any(), any(Boolean.class));

        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-acceptance");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        when(mapper.finishActionRequest(eq((Long) request.get("actionRequestId")), any())).thenReturn(1);
        BusinessProject closed = pendingAcceptanceProject(31L, "新品视频交付", 501L);
        closed.setStatus("CLOSED");
        when(projectService.reviewAcceptance(31L, "APPROVED", "", 23L, "jianglan", true)).thenReturn(closed);

        Map<String, Object> executed = service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan");

        assertEquals("CLOSED", executed.get("projectStatus"));
        assertEquals("APPROVED", executed.get("decision"));
        verify(projectService).reviewAcceptance(31L, "APPROVED", "", 23L, "jianglan", true);
    }

    @Test
    void changedAcceptanceSubmissionInvalidatesOldConfirmation()
    {
        BusinessProject original = pendingAcceptanceProject(31L, "新品视频交付", 501L);
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("decisions", Collections.singletonList(original));
        when(projectService.dashboard(23L, false, true)).thenReturn(dashboard);
        BusinessProject changed = pendingAcceptanceProject(31L, "新品视频交付", 502L);
        when(projectService.getProject(31L, 23L, false, true)).thenReturn(original, changed);
        Map<String, Object> chat = service.chat(null, "验收通过并结项新品视频交付", 23L, "jianglan", false);
        Map<?, ?> request = (Map<?, ?>) chat.get("actionRequest");
        ArgumentCaptor<Map<String, Object>> actionCaptor = mapCaptor();
        verify(mapper).insertActionRequest(actionCaptor.capture());
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(actionCaptor.getValue());
        persisted.put("status", "PENDING"); persisted.put("traceId", "trace-stale-acceptance");
        when(mapper.selectActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(persisted);
        when(mapper.confirmActionRequest((Long) request.get("actionRequestId"), 23L)).thenReturn(1);
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.confirmAction((Long) request.get("actionRequestId"), 23L, "jianglan"));

        assertEquals(true, error.getMessage().contains("验收资料已经发生变化"));
        verify(projectService, never()).reviewAcceptance(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void activeCreateWorkflowContinuesFromShortAnswerEvenWhenModelCallsNoTool()
    {
        when(mapper.selectConversation(91L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 91L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 801L); workflow.put("conversationId", 91L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "BASIC_INFO"); workflow.put("draftJson", "{}");
        workflow.put("missingFieldsJson", "[\"项目名称\",\"主负责人\",\"归属公司\"]"); workflow.put("versionNo", 1);
        when(mapper.selectActiveWorkflow(91L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(91L, "王老吉视频制作，石头，上海", 23L, "jianglan", false);

        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("做成什么样才算完成"), content);
        assertEquals(true, content.contains("什么时候开始、什么时候结束"), content);
        assertEquals(false, content.contains("没有取得足够的系统证据"), content);
        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        assertEquals("CREATE_PROJECT", workflowView.get("workflowCode"));
        assertEquals("GOAL_AND_PERIOD", workflowView.get("currentStep"));
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("王老吉视频制作", draft.get("projectName"));
        assertEquals("石头", draft.get("ownerName"));
        assertEquals("上海美丸文化公司", draft.get("companyName"));
        verify(mapper).updateWorkflow(any());
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void startingCreateProjectPersistsWorkflowBeforeConfirmation()
    {
        when(projectService.userOptions(null)).thenReturn(Collections.emptyList());
        when(staffService.listOptions()).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.chat(null, "帮我创建一个新项目", 23L, "jianglan", false);

        Map<?, ?> workflow = (Map<?, ?>) result.get("workflow");
        assertEquals("CREATE_PROJECT", workflow.get("workflowCode"));
        assertEquals("COLLECTING", workflow.get("status"));
        assertEquals("BASIC_INFO", workflow.get("currentStep"));
        verify(mapper).insertWorkflow(any());
        verify(mapper).insertWorkflowEvent(any());
        verify(projectService, never()).createProject(any(), any(), any());
    }

    @Test
    void conversationRestoresLatestPersistedWorkflowInsteadOfStaleMessageCard()
    {
        when(mapper.selectConversation(94L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 94L));
        Map<String, Object> assistant = new LinkedHashMap<String, Object>();
        assistant.put("messageRole", "ASSISTANT");
        assistant.put("content", "old prompt");
        assistant.put("metadataJson", "{\"workflow\":{\"currentStep\":\"GOAL_AND_PERIOD\"}}");
        when(mapper.selectMessages(94L, 23L, 50)).thenReturn(new ArrayList<Map<String, Object>>(Collections.singletonList(assistant)));

        Map<String, Object> active = new LinkedHashMap<String, Object>();
        active.put("workflowId", 804L); active.put("conversationId", 94L); active.put("userId", 23L);
        active.put("workflowCode", "CREATE_PROJECT"); active.put("workflowStatus", "COLLECTING");
        active.put("currentStep", "ACCOUNTING_AND_BUDGET"); active.put("versionNo", 5);
        active.put("draftJson", "{\"projectName\":\"video project\",\"objective\":\"finish 300 videos\",\"planStartDate\":\"2026-08-13\",\"planEndDate\":\"2026-09-30\"}");
        active.put("missingFieldsJson", "[\"accounting mode\",\"budget\"]");
        when(mapper.selectActiveWorkflow(94L, 23L)).thenReturn(active);

        List<Map<String, Object>> messages = service.conversation(94L, 23L);

        Map<?, ?> metadata = (Map<?, ?>) messages.get(0).get("metadata");
        Map<?, ?> workflow = (Map<?, ?>) metadata.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflow.get("draft");
        assertEquals("ACCOUNTING_AND_BUDGET", workflow.get("currentStep"));
        assertEquals("finish 300 videos", draft.get("objective"));
        assertEquals("2026-08-13", draft.get("planStartDate"));
        assertEquals("2026-09-30", draft.get("planEndDate"));
    }

    @Test
    void conversationReconcilesStalePendingActionCardWithExecutedDatabaseState()
    {
        when(mapper.selectConversation(96L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 96L));
        Map<String, Object> assistant = new LinkedHashMap<String, Object>();
        assistant.put("messageRole", "ASSISTANT");
        assistant.put("content", "请确认立项");
        assistant.put("metadataJson", "{\"actionRequest\":{\"actionRequestId\":10,\"actionCode\":\"CREATE_PROJECT\",\"status\":\"PENDING\",\"project\":{\"projectName\":\"王老吉视频宣传\"}}}");
        when(mapper.selectMessages(96L, 23L, 50))
            .thenReturn(new ArrayList<Map<String, Object>>(Collections.singletonList(assistant)));
        Map<String, Object> persisted = new LinkedHashMap<String, Object>();
        persisted.put("actionRequestId", 10L);
        persisted.put("actionCode", "CREATE_PROJECT");
        persisted.put("status", "EXECUTED");
        persisted.put("resultJson", "{\"projectId\":17,\"projectNo\":\"XM202608141029244D87\",\"projectName\":\"王老吉视频宣传\"}");
        when(mapper.selectConversationActionRequests(96L, 23L)).thenReturn(Collections.singletonList(persisted));

        List<Map<String, Object>> messages = service.conversation(96L, 23L);

        Map<?, ?> metadata = (Map<?, ?>) messages.get(0).get("metadata");
        Map<?, ?> actionRequest = (Map<?, ?>) metadata.get("actionRequest");
        assertEquals("EXECUTED", actionRequest.get("status"));
        assertEquals(17L, ((Number) actionRequest.get("projectId")).longValue());
        assertEquals("XM202608141029244D87", actionRequest.get("projectNo"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createWorkflowAllowsBossToReviseAnAlreadyCollectedObjective()
    {
        when(mapper.selectConversation(95L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 95L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 805L); workflow.put("conversationId", 95L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "ACCOUNTING_AND_BUDGET"); workflow.put("versionNo", 6);
        workflow.put("draftJson", "{\"projectName\":\"王老吉视频制作\",\"ownerName\":\"石头\",\"companyName\":\"上海美丸文化公司\",\"objective\":\"完成300条视频\",\"planStartDate\":\"2026-08-13\",\"planEndDate\":\"2026-09-30\"}");
        workflow.put("missingFieldsJson", "[\"accounting mode\",\"budget\"]");
        when(mapper.selectActiveWorkflow(95L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        Map<String, Object> modelPlan = new LinkedHashMap<String, Object>();
        Map<String, Object> toolCall = new LinkedHashMap<String, Object>();
        toolCall.put("toolCode", "boss_prepare_project_create");
        toolCall.put("arguments", Collections.<String, Object>singletonMap("objective", "1000条"));
        modelPlan.put("toolCalls", Collections.singletonList(toolCall));
        modelPlan.put("intents", Collections.singletonList("CREATE_PROJECT"));
        when(modelClient.isEnabled()).thenReturn(false);
        when(modelClient.plan(any(), any())).thenReturn(modelPlan);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(95L, "我想要把目标改为1000条", 23L, "jianglan", false);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("完成1000条视频", draft.get("objective"));
        assertEquals("ACCOUNTING_AND_BUDGET", workflowView.get("currentStep"));
        assertEquals(true, String.valueOf(result.get("content")).contains("主要看赚了多少钱"), String.valueOf(result.get("content")));
        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertWorkflowEvent(event.capture());
        assertEquals("FIELDS_UPDATED", event.getValue().get("eventType"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createWorkflowUnderstandsNumberedGoalAndFromNowDateRange()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T06:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(92L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 92L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 802L); workflow.put("conversationId", 92L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "GOAL_AND_PERIOD");
        workflow.put("draftJson", "{\"projectName\":\"王老吉视频制作\",\"ownerName\":\"石头\",\"companyName\":\"上海美丸文化公司\"}");
        workflow.put("missingFieldsJson", "[\"项目目标\",\"计划开始和结束日期\"]"); workflow.put("versionNo", 1);
        when(mapper.selectActiveWorkflow(92L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(92L, "1完成300条视频，2从现在做到9月30日", 23L, "jianglan", false);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("完成300条视频", draft.get("objective"));
        assertEquals("2026-08-13", draft.get("planStartDate"));
        assertEquals("2026-09-30", draft.get("planEndDate"));
        assertEquals("ACCOUNTING_AND_BUDGET", workflowView.get("currentStep"));
        assertEquals(false, String.valueOf(result.get("content")).contains("做成什么样才算完成"), String.valueOf(result.get("content")));
        assertEquals(true, String.valueOf(result.get("content")).contains("主要看赚了多少钱"), String.valueOf(result.get("content")));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createWorkflowUnderstandsPlainDailyDeliverableAndFromNowDateRange()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-13T06:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(93L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 93L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 803L); workflow.put("conversationId", 93L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "GOAL_AND_PERIOD");
        workflow.put("draftJson", "{\"projectName\":\"视频项目\",\"ownerName\":\"石头\",\"companyName\":\"上海美丸文化公司\"}");
        workflow.put("missingFieldsJson", "[\"项目目标\",\"计划开始和结束日期\"]"); workflow.put("versionNo", 1);
        when(mapper.selectActiveWorkflow(93L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(93L, "每天完成1000条视频，从现在做到9月30日", 23L, "jianglan", false);

        Map<?, ?> draft = (Map<?, ?>) ((Map<?, ?>) result.get("workflow")).get("draft");
        assertEquals("每天完成1000条视频", draft.get("objective"));
        assertEquals("2026-08-13", draft.get("planStartDate"));
        assertEquals("2026-09-30", draft.get("planEndDate"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void explicitMonthDayRangeOverridesEarlierFromNowStartDate()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-18T05:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(97L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 97L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 807L); workflow.put("conversationId", 97L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "GOAL_AND_PERIOD"); workflow.put("versionNo", 2);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"石头\","
            + "\"companyName\":\"上海美丸文化公司\",\"objective\":\"GMV达到500万元\","
            + "\"planStartDate\":\"2026-08-18\",\"noBudget\":true}");
        workflow.put("missingFieldsJson", "[\"计划开始和结束日期\",\"核算方式\"]");
        when(mapper.selectActiveWorkflow(97L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(66L, "shitou", "石头", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(97L, "从8月15日做到9月30日", 23L, "jianglan", false);

        Map<?, ?> draft = (Map<?, ?>) ((Map<?, ?>) result.get("workflow")).get("draft");
        assertEquals("2026-08-15", draft.get("planStartDate"));
        assertEquals("2026-09-30", draft.get("planEndDate"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createWorkflowRecoversShorthandGmvAndTodayRangeFromEarlierTurn()
    {
        service.setClock(Clock.fixed(Instant.parse("2026-08-18T05:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectConversation(98L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 98L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 808L); workflow.put("conversationId", 98L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "GOAL_AND_PERIOD"); workflow.put("versionNo", 4);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"施柳浩\","
            + "\"companyName\":\"上海美丸文化公司\",\"planEndDate\":\"2026-09-30\"}");
        workflow.put("missingFieldsJson", "[\"项目目标\",\"计划开始和结束日期\",\"核算方式\",\"预算\"]");
        when(mapper.selectActiveWorkflow(98L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        Map<String, Object> previous = new LinkedHashMap<String, Object>();
        previous.put("messageRole", "USER");
        previous.put("content", "500W的GMV，今天到9月30");
        when(mapper.selectMessages(98L, 23L, 12))
            .thenReturn(new ArrayList<Map<String, Object>>(Collections.singletonList(previous)));
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(67L, "shiliuhao", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(67L, "shiliuhao", "施柳浩", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(98L, "500W的GMV算完成", 23L, "jianglan", false);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals("GMV达到500万元", draft.get("objective"));
        assertEquals("2026-08-18", draft.get("planStartDate"));
        assertEquals("2026-09-30", draft.get("planEndDate"));
        assertEquals("ACCOUNTING_AND_BUDGET", workflowView.get("currentStep"));
        assertEquals(false, String.valueOf(result.get("content")).contains("什么时候开始"), String.valueOf(result.get("content")));
        assertEquals(true, String.valueOf(result.get("content")).contains("主要看赚了多少钱"), String.valueOf(result.get("content")));
    }

    @Test
    @org.junit.jupiter.api.Disabled("老板 AI 直接立项旧流程已停用，改由立项申请页面")
    void createWorkflowAcceptsTemporarilyNoBudgetWording()
    {
        when(mapper.selectConversation(99L, 23L, "BOSS"))
            .thenReturn(Collections.<String, Object>singletonMap("conversationId", 99L));
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("workflowId", 809L); workflow.put("conversationId", 99L); workflow.put("userId", 23L);
        workflow.put("workflowCode", "CREATE_PROJECT"); workflow.put("workflowStatus", "COLLECTING");
        workflow.put("currentStep", "ACCOUNTING_AND_BUDGET"); workflow.put("versionNo", 7);
        workflow.put("draftJson", "{\"projectName\":\"上海电商\",\"ownerName\":\"施柳浩\","
            + "\"companyName\":\"上海美丸文化公司\",\"objective\":\"GMV达到500万元\","
            + "\"planStartDate\":\"2026-08-18\",\"planEndDate\":\"2026-09-30\","
            + "\"accountingMode\":\"HYBRID\"}");
        workflow.put("missingFieldsJson", "[\"预算金额，或明确说明不设预算\"]");
        when(mapper.selectActiveWorkflow(99L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        when(projectService.userOptions(null)).thenReturn(Collections.singletonList(staffOption(67L, "shiliuhao", "施柳浩", null, null)));
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staffOption(67L, "shiliuhao", "施柳浩", 100L, "上海美丸文化公司")));

        Map<String, Object> result = service.chat(99L, "暂时不设置预算", 23L, "jianglan", false);

        Map<?, ?> workflowView = (Map<?, ?>) result.get("workflow");
        Map<?, ?> draft = (Map<?, ?>) workflowView.get("draft");
        assertEquals(true, draft.get("noBudget"));
        assertEquals("WAITING_CONFIRMATION", workflowView.get("status"));
        assertEquals("WAITING_CONFIRMATION", workflowView.get("currentStep"));
        assertEquals(Collections.emptyList(), workflowView.get("missingFields"));
        assertEquals("CREATE_PROJECT", ((Map<?, ?>) result.get("actionRequest")).get("actionCode"));
        verify(projectService, never()).createProject(any(), any(), any());
    }

    private Map<String, Object> staffOption(Long userId, String userName, String nickName,
        Long companyDeptId, String companyName)
    {
        Map<String, Object> option = new LinkedHashMap<String, Object>();
        option.put("userId", userId); option.put("userName", userName); option.put("nickName", nickName);
        option.put("companyDeptId", companyDeptId); option.put("companyName", companyName);
        return option;
    }

    private BusinessProject pendingAcceptanceProject(Long projectId, String projectName, Long acceptanceId)
    {
        BusinessProject project = portfolioProject(projectId, projectName, "石头", "交付1000条可验收视频", "ACCEPTANCE");
        project.setCompanyName("上海美丸文化公司"); project.setManagementMode("DELIVERY");
        BusinessProjectTask task = new BusinessProjectTask();
        task.setTaskId(701L); task.setTaskName("完成全部视频"); task.setStatus("DONE"); task.setProgress(100);
        project.setTasks(Collections.singletonList(task));
        BusinessProjectMilestone milestone = new BusinessProjectMilestone();
        milestone.setMilestoneId(801L); milestone.setMilestoneName("最终交付"); milestone.setStatus("DONE");
        project.setMilestones(Collections.singletonList(milestone));
        project.setRisks(Collections.emptyList());
        BusinessProjectAcceptance submission = new BusinessProjectAcceptance();
        submission.setAcceptanceId(acceptanceId); submission.setProjectId(projectId); submission.setSubmissionVersion(2);
        submission.setResultSummary("已交付1000条视频"); submission.setDeliverables("成片和源文件");
        submission.setAttachmentUrls("/profile/a.png,/profile/b.png"); submission.setSubmittedUserName("石头");
        submission.setSubmittedTime(new Date()); submission.setReviewStatus("PENDING");
        project.setAcceptances(Collections.singletonList(submission));
        return project;
    }

    private BusinessProject submittedPlanProject()
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(16L); project.setProjectNo("XM16"); project.setProjectName("情趣内衣视频制作");
        project.setStatus("PLANNING"); project.setBaselineStatus("SUBMITTED"); project.setMainOwnerName("石头");
        project.setObjective("制作1000条视频"); project.setMembers(Collections.emptyList());
        project.setTasks(Collections.emptyList()); project.setRoutines(Collections.emptyList());
        project.setMilestones(Collections.emptyList()); project.setRisks(Collections.emptyList());
        return project;
    }

    private Map<String, Object> portfolioUnderstandingMessage()
    {
        Map<String, Object> previous = new LinkedHashMap<String, Object>();
        previous.put("messageRole", "ASSISTANT");
        previous.put("content", "项目态势：本人范围内共2个项目，执行中2个。 ");
        previous.put("metadataJson", "{\"understanding\":{\"status\":\"RESOLVED\","
            + "\"queryType\":\"PROJECT_PORTFOLIO\",\"queryLabel\":\"项目整体态势\","
            + "\"resolvedTime\":\"2026-08-13 10:00:00\"}}");
        return previous;
    }

    private Map<String, Object> pendingUnderstandingMessage()
    {
        Map<String, Object> previous = new LinkedHashMap<String, Object>();
        previous.put("messageRole", "ASSISTANT");
        previous.put("content", "待处理：共有2项需要你决定。 ");
        previous.put("metadataJson", "{\"understanding\":{\"status\":\"RESOLVED\","
            + "\"queryType\":\"PENDING_DECISIONS\",\"queryLabel\":\"待老板处理事项\","
            + "\"resolvedTime\":\"2026-08-13 13:00:00\",\"pendingDecisions\":["
            + "{\"projectId\":16,\"projectName\":\"情趣内衣视频制作\",\"decisionType\":\"START_PLANNING\"},"
            + "{\"projectId\":18,\"projectName\":\"直播增长\",\"decisionType\":\"RESUME_PROJECT\"}]}}" );
        return previous;
    }

    private BusinessProject portfolioProject(Long projectId, String projectName, String ownerName,
        String objective, String status)
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(projectId);
        project.setProjectNo("XM" + projectId);
        project.setProjectName(projectName);
        project.setMainOwnerName(ownerName);
        project.setObjective(objective);
        project.setStatus(status);
        return project;
    }

    private Map<String, Object> portfolioDashboard(BusinessProject... projects)
    {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("totalCount", projects.length);
        summary.put("activeCount", projects.length);
        summary.put("pendingDecisionCount", 0);
        summary.put("overdueProjectCount", 0);
        summary.put("highRiskProjectCount", 0);
        Map<String, Object> dashboard = new LinkedHashMap<String, Object>();
        dashboard.put("summary", summary);
        dashboard.put("projects", Arrays.asList(projects));
        return dashboard;
    }

    private void assertExpandedPortfolio(Map<String, Object> result)
    {
        String content = String.valueOf(result.get("content"));
        assertEquals(true, content.contains("新谷酵素视频剪辑"), content);
        assertEquals(true, content.contains("蒋豪"), content);
        assertEquals(true, content.contains("剪辑并交付1000条新谷酵素视频"), content);
        assertEquals(true, content.contains("情趣内衣视频制作"), content);
        assertEquals(true, content.contains("石头"), content);
        assertEquals(true, content.contains("制作1000条情趣内衣短视频"), content);
        assertEquals(false, content.contains("仍然只有项目总数"), content);
        assertEquals(false, content.contains("这是人员概况"), content);
    }

    private void assertPortfolioEvidence(Map<String, Object> result, Long projectId, String projectName,
        String ownerName, String objective)
    {
        List<?> evidence = (List<?>) result.get("evidence");
        assertEquals(true, evidence != null && !evidence.isEmpty());
        List<?> facts = (List<?>) ((Map<?, ?>) evidence.get(0)).get("facts");
        assertEquals(true, facts != null && !facts.isEmpty());
        boolean hasName = false;
        boolean hasOwner = false;
        boolean hasObjective = false;
        for (Object value : facts)
        {
            if (!(value instanceof Map)) continue;
            Map<?, ?> fact = (Map<?, ?>) value;
            if (!String.valueOf(projectId).equals(String.valueOf(fact.get("recordId")))) continue;
            assertEquals("PROJECT", fact.get("recordType"));
            assertEquals(projectName, fact.get("entityName"));
            if ("project_name".equals(fact.get("metricCode")))
                hasName = projectName.equals(fact.get("value"));
            if ("main_owner".equals(fact.get("metricCode")))
                hasOwner = ownerName.equals(fact.get("value"));
            if ("project_objective".equals(fact.get("metricCode")))
                hasObjective = objective.equals(fact.get("value"));
        }
        assertEquals(true, hasName, "缺少项目名称证据：" + projectName);
        assertEquals(true, hasOwner, "缺少项目负责人证据：" + projectName);
        assertEquals(true, hasObjective, "缺少项目目标/内容证据：" + projectName);
    }

    private int generated(Map<String, Object> row, String key)
    {
        row.put(key, ids.incrementAndGet());
        return 1;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<List<Map<String, Object>>> listMapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
