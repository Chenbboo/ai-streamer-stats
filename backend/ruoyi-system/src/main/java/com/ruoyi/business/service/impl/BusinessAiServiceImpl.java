package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.BusinessAiEvidence;
import com.ruoyi.business.ai.BusinessAiEvidenceBundle;
import com.ruoyi.business.ai.BusinessAiEvidenceCoverage;
import com.ruoyi.business.ai.BusinessAiEvidenceEntityType;
import com.ruoyi.business.ai.BusinessAiEvidenceStatus;
import com.ruoyi.business.ai.BusinessAiQueryType;
import com.ruoyi.business.ai.BusinessAiSemanticQuery;
import com.ruoyi.business.ai.BusinessAiSemanticQueryParseException;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.AiCapabilityAgentLoop;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityToolCatalog;
import com.ruoyi.business.ai.capability.AiCapabilityActionService;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessAiModelClient;
import com.ruoyi.business.service.IBusinessAiService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

/**
 * 老板 AI 的安全执行层。
 *
 * DeepSeek 只负责选择这里登记的白名单工具并组织回答，不能直接访问数据库或调用任意接口。
 * 模型不可用或返回异常工具计划时，自动降级为确定性意图路由。
 */
@Service
public class BusinessAiServiceImpl implements IBusinessAiService
{
    private static final String ROLE_CODE = "BOSS";
    private static final String SAFE_ROUTER = "SAFE_ROUTER";
    private static final String LLM_AGENT = "LLM_AGENT";
    private static final String SAFE_ROUTER_FALLBACK = "SAFE_ROUTER_FALLBACK";

    @Autowired private BusinessAiMapper mapper;
    @Autowired private IBusinessProjectService projectService;
    @Autowired private IBusinessAccountingService accountingService;
    @Autowired private IBusinessStaffService staffService;
    @Autowired private IBusinessAiModelClient modelClient;
    @Autowired(required = false) private AiCapabilityToolCatalog capabilityToolCatalog;
    @Autowired(required = false) private AiCapabilityAgentLoop capabilityAgentLoop;
    @Autowired(required = false) private AiCapabilityActionService capabilityActionService;
    @Autowired(required = false) private RedisCache redisCache;
    @Autowired private ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) { this.clock = clock == null ? Clock.systemDefaultZone() : clock; }

    @Override
    @Transactional
    public Map<String, Object> chat(Long conversationId, String message, Long userId, String userName, boolean viewAll)
    {
        return chat(conversationId, message, AiExecutionContext.legacy(userId, userName, viewAll));
    }

    @Override
    @Transactional
    public Map<String, Object> chat(Long conversationId, String message, AiExecutionContext context)
    {
        if (context == null) throw new ServiceException("AI执行上下文不存在");
        Long userId = context.getUserId();
        String userName = context.getUserName();
        boolean viewAll = context.isAdministrator();
        String question = StringUtils.trim(message);
        if (StringUtils.isBlank(question)) throw new ServiceException("请告诉 AI 你想了解什么");
        if (question.length() > 1000) throw new ServiceException("单次问题不能超过1000字");

        Long activeConversationId = requireOrCreateConversation(conversationId, question, userId);
        Map<String, Object> activeWorkflow = mapper.selectActiveWorkflow(activeConversationId, userId);
        boolean continuingProjectCreate = isWorkflow(activeWorkflow, "CREATE_PROJECT", "COLLECTING", "READY");
        List<Map<String, Object>> modelHistory = recentModelHistory(activeConversationId, userId);
        Map<String, Object> conversationState = conversationState(activeConversationId, userId);
        boolean modelEnabled = modelClient.isEnabled();
        Map<String, Object> pendingSelection = modelEnabled ? Collections.<String, Object>emptyMap()
            : pendingDecisionSelection(question, conversationState);
        addConversationContext(modelHistory, conversationState);
        // New messages are routed by model-selected, permission-filtered capabilities.
        // Local phrase lists must not reinterpret the boss's natural language.
        String guardedProjectQuery = modelEnabled ? null : guardedProjectQuery(question);
        boolean directProjectQuery = !modelEnabled && StringUtils.isNotBlank(guardedProjectQuery);
        boolean directBudgetQuery = !modelEnabled && "BUDGET".equals(guardedProjectQuery);
        boolean ambiguousPersonReference = !modelEnabled && isAmbiguousPersonReference(question);
        Map<String, Object> userMessage = message(activeConversationId, userId, "USER", question, null);
        mapper.insertMessage(userMessage);

        String traceId = IdUtils.fastSimpleUUID();
        Map<String, Object> run = new LinkedHashMap<String, Object>();
        run.put("traceId", traceId);
        run.put("conversationId", activeConversationId);
        run.put("userId", userId);
        run.put("roleCode", ROLE_CODE);
        run.put("requestMessageId", longValue(userMessage.get("messageId")));
        String executionMode = modelEnabled ? LLM_AGENT : SAFE_ROUTER;
        run.put("executionMode", executionMode);
        mapper.insertRun(run);
        Long runId = longValue(run.get("runId"));

        Map<String, Object> modelPlan = null;
        Map<String, Object> modelCompletion = null;
        Map<String, Object> decisionTrace = new LinkedHashMap<String, Object>();
        String fallbackReason = null;
        boolean guardedProjectCreate = false;
        List<String> intents;
        if (modelEnabled)
        {
            try
            {
                List<Map<String, Object>> modelTools = capabilityToolCatalog == null
                    ? Collections.<Map<String, Object>>emptyList() : capabilityToolCatalog.definitions(context);
                modelTools = continuingProjectCreate ? modelTools : withoutProjectDraftTools(modelTools);
                modelPlan = modelTools.isEmpty() ? modelClient.plan(question, modelHistory)
                    : modelClient.plan(question, modelHistory, modelTools);
                List<String> modelSelections = selectedCapabilityCodes(modelPlan, context);
                decisionTrace.put("modelSelection", modelSelections);
                if (!plannedToolsAllowed(modelPlan, context))
                {
                    modelPlan = null;
                    fallbackReason = "模型返回了未登记工具";
                }
            }
            catch (Exception ex)
            {
                modelPlan = null;
                fallbackReason = "模型规划暂不可用";
            }
        }
        if (modelEnabled && !continuingProjectCreate && isExplicitNewProjectRequest(question))
        {
            guardedProjectCreate = true;
            decisionTrace.put("detectedIntent", "CREATE_PROJECT");
            decisionTrace.put("candidateCapabilities", Arrays.asList(
                "project.create", "project.draft.update", "conversation.safe.respond"));
            decisionTrace.put("validationStatus", "CORRECTED");
            decisionTrace.put("validationMessage", safeConversationSelected(decisionTrace)
                ? "模型将明确的创建项目请求误判为不清楚，系统已拒绝安全回复"
                : "系统已按确定性规则启动项目创建资料收集，避免资料不足时直接执行");
            decisionTrace.put("finalRoute", "CREATE_PROJECT_WORKFLOW");
            modelPlan = null;
            fallbackReason = "明确创建项目意图已由系统工作流接管";
            executionMode = SAFE_ROUTER_FALLBACK;
            mapper.updateRunMode(runId, executionMode);
        }
        else if (modelEnabled)
        {
            decisionTrace.put("detectedIntent", firstSelection(decisionTrace));
            decisionTrace.put("validationStatus", modelPlan == null ? "REJECTED" : "PASSED");
            decisionTrace.put("validationMessage", modelPlan == null
                ? StringUtils.defaultIfBlank(fallbackReason, "模型没有返回可执行路由")
                : "模型选择已通过权限与能力白名单校验");
            decisionTrace.put("finalRoute", firstSelection(decisionTrace));
        }
        if (modelEnabled && modelPlan == null && !guardedProjectCreate)
        {
            mapper.finishRun(runId, null, "FAILED", StringUtils.defaultIfBlank(fallbackReason, "AI model unavailable"));
            throw new ServiceException("AI 模型当前暂不可用，请稍后重试。本次没有执行任何系统操作。");
        }
        if (capabilityAgentLoop != null && capabilityAgentLoop.canHandle(modelPlan, context))
        {
            try
            {
                AiCapabilityInvocation invocation = new AiCapabilityInvocation(context, activeConversationId,
                    runId, longValue(userMessage.get("messageId")));
                Map<String, Object> outcome = capabilityAgentLoop.run(question, modelHistory, modelPlan, invocation);
                return completeCapabilityRun(question, outcome, activeConversationId, runId, traceId, userId,
                    userName, viewAll, context, modelEnabled, longValue(userMessage.get("messageId")), decisionTrace);
            }
            catch (Exception ex)
            {
                mapper.finishRun(runId, null, "FAILED", StringUtils.substring(ex.getMessage(), 0, 500));
                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
                throw new ServiceException("AI系统能力执行失败");
            }
        }
        intents = modelPlan == null ? detectIntents(question) : intentsFromPlan(modelPlan);

        BusinessAiSemanticQuery semanticQuery = null;
        Map<String, Object> semanticResolution = null;
        String semanticError = null;
        BusinessAiSemanticQuery locallyRecognizedQuery = modelPlan == null
            ? localSemanticQuery(question, intents, conversationState) : null;
        if (!hasToolCalls(modelPlan) && !ambiguousPersonReference)
        {
            // 仅保留模型关闭时的离线兼容路由；线上模型不会经过关键词重解释。
            semanticQuery = locallyRecognizedQuery;
            if (semanticQuery != null)
                semanticResolution = resolveSemanticQuery(semanticQuery, question, conversationState, userId, viewAll);
        }

        Map<String, Object> projectDetailArguments = firstToolArguments(modelPlan, "boss_project_detail");
        Map<String, Object> accountingDetailArguments = firstToolArguments(modelPlan, "boss_project_accounting_detail");
        if (semanticQuery != null || semanticError != null)
        {
            intents = new ArrayList<String>();
            directProjectQuery = false;
            directBudgetQuery = false;
        }
        else if (directProjectQuery)
        {
            intents = new ArrayList<String>();
            if ("ACCOUNTING".equals(guardedProjectQuery))
            {
                intents.add("ACCOUNTING_DETAIL");
                accountingDetailArguments = resolveProjectReference(accountingDetailArguments, modelHistory,
                    question, userId, viewAll);
            }
            else
            {
                intents.add("PROJECT_DETAIL");
                projectDetailArguments = resolveProjectReference(projectDetailArguments, modelHistory, question,
                    userId, viewAll);
            }
        }
        if (ambiguousPersonReference)
        {
            intents = new ArrayList<String>();
        }
        if (continuingProjectCreate)
        {
            semanticQuery = null;
            semanticResolution = null;
            semanticError = null;
            intents = new ArrayList<String>();
            directProjectQuery = false;
            directBudgetQuery = false;
            ambiguousPersonReference = false;
        }
        if (modelPlan == null && (isAcceptanceReviewRequest(question) || isAcceptanceApproveRequest(question)
            || isAcceptanceReturnRequest(question)))
        {
            intents = new ArrayList<String>();
            semanticQuery = null;
            semanticResolution = null;
            semanticError = null;
            directProjectQuery = false;
            directBudgetQuery = false;
            ambiguousPersonReference = false;
        }
        if (Boolean.TRUE.equals(pendingSelection.get("requested")))
        {
            // “处理第一个”只能引用上一轮结构化待办列表，不能继续相信模型或聊天正文猜项目。
            semanticQuery = null;
            semanticResolution = null;
            semanticError = null;
            intents = new ArrayList<String>();
            directProjectQuery = false;
            directBudgetQuery = false;
            ambiguousPersonReference = false;
        }

        List<Map<String, Object>> toolResults = new ArrayList<Map<String, Object>>();
        if (semanticQuery != null && Boolean.TRUE.equals(semanticResolution.get("ready")))
            toolResults.add(executeSemanticQuery(semanticQuery, semanticResolution, runId, activeConversationId,
                userId, viewAll));
        for (String intent : intents)
        {
            if ("ACCOUNTING".equals(intent)) toolResults.add(accountingTool(runId, activeConversationId, userId, viewAll));
            if ("PROJECTS".equals(intent)) toolResults.add(projectTool(runId, activeConversationId, userId, viewAll));
            if ("PENDING".equals(intent)) toolResults.add(pendingTool(runId, activeConversationId, userId, viewAll));
            if ("STAFF".equals(intent)) toolResults.add(staffTool(runId, activeConversationId, userId));
            if ("PROJECT_DETAIL".equals(intent)) toolResults.add(projectDetailTool(runId, activeConversationId,
                userId, viewAll, projectDetailArguments));
            if ("ACCOUNTING_DETAIL".equals(intent)) toolResults.add(projectAccountingDetailTool(runId,
                activeConversationId, userId, viewAll, accountingDetailArguments));
        }
        Map<String, Object> actionRequest = null;
        Map<String, Object> preparedAction = null;
        Map<String, Object> completedAction = null;
        Map<String, Object> reviewedPlan = null;
        Map<String, Object> reviewedAcceptance = null;
        Map<String, Object> projectArguments = null;
        Map<String, Object> transitionArguments = firstToolArguments(modelPlan, "boss_prepare_project_transition");
        Map<String, Object> planReviewArguments = null;
        Map<String, Object> planDecisionArguments = null;
        Map<String, Object> acceptanceReviewArguments = firstToolArguments(modelPlan, "boss_project_acceptance_review");
        Map<String, Object> acceptanceDecisionArguments = firstToolArguments(modelPlan, "boss_prepare_acceptance_decision");
        Map<String, Object> budgetArguments = firstToolArguments(modelPlan, "boss_prepare_budget_adjustment");
        if (projectArguments != null || (modelPlan == null && (continuingProjectCreate || isProjectCreateRequest(question))))
        {
            Map<String, Object> storedDraft = workflowDraft(activeWorkflow);
            Map<String, Object> merged = new LinkedHashMap<String, Object>(storedDraft);
            if (projectArguments != null) mergeNonBlank(merged, projectArguments);
            if (modelPlan == null) mergeProjectCreateMessage(merged, storedDraft, question);
            projectArguments = merged;
        }
        if (Boolean.TRUE.equals(pendingSelection.get("requested")))
        {
            projectArguments = null;
            transitionArguments = null;
            planReviewArguments = null;
            planDecisionArguments = null;
            acceptanceReviewArguments = null;
            acceptanceDecisionArguments = null;
            budgetArguments = null;
            @SuppressWarnings("unchecked") Map<String, Object> selectedDecision = pendingSelection.get("decision") instanceof Map
                ? (Map<String, Object>) pendingSelection.get("decision") : null;
            if (selectedDecision != null)
            {
                String decisionType = stringValue(selectedDecision.get("decisionType"));
                if ("START_PLANNING".equals(decisionType) || "RESUME_PROJECT".equals(decisionType))
                {
                    transitionArguments = new LinkedHashMap<String, Object>();
                    transitionArguments.put("projectId", selectedDecision.get("projectId"));
                    transitionArguments.put("projectName", selectedDecision.get("projectName"));
                    transitionArguments.put("transitionAction", decisionType);
                }
                else if ("PLAN_APPROVAL".equals(decisionType))
                {
                    planReviewArguments = new LinkedHashMap<String, Object>();
                    planReviewArguments.put("projectId", selectedDecision.get("projectId"));
                    planReviewArguments.put("projectName", selectedDecision.get("projectName"));
                }
                else if ("PROJECT_ACCEPTANCE".equals(decisionType))
                {
                    acceptanceReviewArguments = new LinkedHashMap<String, Object>();
                    acceptanceReviewArguments.put("projectId", selectedDecision.get("projectId"));
                    acceptanceReviewArguments.put("projectName", selectedDecision.get("projectName"));
                }
            }
        }
        if (modelPlan == null)
        {
            boolean approval = isAcceptanceApproveRequest(question)
                || (isShortAffirmative(question) && historyContains(modelHistory, "验收审核卡", "验收资料已经核对", "可以验收通过"));
            boolean returned = isAcceptanceReturnRequest(question);
            if (acceptanceDecisionArguments == null && (approval || returned))
            {
                acceptanceDecisionArguments = new LinkedHashMap<String, Object>();
                acceptanceDecisionArguments.put("decision", approval ? "APPROVE" : "RETURN");
            }
            if (acceptanceReviewArguments == null && acceptanceDecisionArguments == null
                && isAcceptanceReviewRequest(question)) acceptanceReviewArguments = new LinkedHashMap<String, Object>();
        }
        if (acceptanceDecisionArguments != null && longValue(acceptanceDecisionArguments.get("projectId")) == null
            && conversationState.get("activeProjectId") != null)
        {
            acceptanceDecisionArguments.put("projectId", conversationState.get("activeProjectId"));
            acceptanceDecisionArguments.put("projectName", conversationState.get("activeProjectName"));
        }
        if (modelPlan == null && acceptanceDecisionArguments != null
            && "RETURN".equals(upper(acceptanceDecisionArguments.get("decision")))
            && StringUtils.isBlank(text(acceptanceDecisionArguments, "returnReason")))
        {
            String reason = acceptanceReturnReason(question);
            if (StringUtils.isNotBlank(reason)) acceptanceDecisionArguments.put("returnReason", reason);
        }
        if (acceptanceReviewArguments != null || acceptanceDecisionArguments != null)
        {
            projectArguments = null;
            transitionArguments = null;
            planReviewArguments = null;
            planDecisionArguments = null;
            budgetArguments = null;
        }
        boolean contextualAdvance = transitionArguments != null || (modelPlan == null && (isAdvanceExistingRequest(question)
            || (isShortAffirmative(question) && historyContains(modelHistory, "推进这个项目", "进入规划", "正式规划", "让负责人开始规划"))));
        if (contextualAdvance)
        {
            if (transitionArguments == null) transitionArguments = new LinkedHashMap<String, Object>();
            if (StringUtils.isBlank(text(transitionArguments, "projectName")))
                transitionArguments.put("contextText", historyText(modelHistory));
            projectArguments = null;
        }
        if (modelPlan == null && projectArguments == null && isProjectCreateRequest(question))
            projectArguments = new LinkedHashMap<String, Object>();
        if (modelPlan == null && (isPlanApproveRequest(question)
            || (isShortAffirmative(question) && historyContains(modelHistory, "批准并启动", "批准计划", "计划已经审核", "可以考虑批准启动"))))
        {
            if (planDecisionArguments == null) planDecisionArguments = new LinkedHashMap<String, Object>();
            planDecisionArguments.put("decision", "APPROVE");
        }
        if (planDecisionArguments != null && longValue(planDecisionArguments.get("projectId")) == null
            && conversationState.get("activeProjectId") != null)
        {
            planDecisionArguments.put("projectId", conversationState.get("activeProjectId"));
            if (conversationState.get("activeProjectName") != null)
                planDecisionArguments.put("projectName", conversationState.get("activeProjectName"));
        }
        if (modelPlan == null && isPlanReturnRequest(question))
        {
            if (planDecisionArguments == null) planDecisionArguments = new LinkedHashMap<String, Object>();
            planDecisionArguments.put("decision", "RETURN");
        }
        if (modelPlan == null && planDecisionArguments != null
            && "RETURN".equals(upper(planDecisionArguments.get("decision")))
            && StringUtils.isBlank(text(planDecisionArguments, "returnReason")))
        {
            String reason = planReturnReason(question);
            if (StringUtils.isNotBlank(reason)) planDecisionArguments.put("returnReason", reason);
        }
        if (modelPlan == null && budgetArguments == null && isBudgetAdjustmentRequest(question))
            budgetArguments = new LinkedHashMap<String, Object>();
        if (budgetArguments != null && longValue(budgetArguments.get("projectId")) == null
            && StringUtils.isBlank(text(budgetArguments, "projectName")))
            budgetArguments.put("contextText", historyText(modelHistory) + " " + question);
        if (modelPlan == null)
        {
            Map<String, Object> submittedPendingPlan = submittedPendingPlan(question, modelHistory, toolResults);
            if (planDecisionArguments == null && submittedPendingPlan != null)
            {
                planReviewArguments = new LinkedHashMap<String, Object>();
                planReviewArguments.put("projectId", submittedPendingPlan.get("projectId"));
                planReviewArguments.put("projectName", submittedPendingPlan.get("projectName"));
                transitionArguments = null;
                projectArguments = null;
            }
            if (planReviewArguments == null && planDecisionArguments == null && (isPlanReviewRequest(question)
                || (isShortAffirmative(question) && historyContains(modelHistory, "等待老板审核", "开始审核计划", "审核这份计划"))))
                planReviewArguments = new LinkedHashMap<String, Object>();
            Map<String, Object> latestAction = mapper.selectLatestActionRequest(activeConversationId, userId, "CREATE_PROJECT");
            if (projectArguments != null && latestAction != null
                && "EXECUTED".equals(stringValue(latestAction.get("status"))) && !isExplicitNewProjectRequest(question))
            {
                completedAction = resultMap(latestAction.get("resultJson"));
                projectArguments = null;
            }
        }
        if (projectArguments != null)
        {
            normalizeProjectDates(projectArguments, modelHistory, question);
            preparedAction = prepareProjectAction(runId, activeConversationId, userId, projectArguments);
            activeWorkflow = persistProjectWorkflow(activeWorkflow, activeConversationId, userId,
                longValue(userMessage.get("messageId")), projectArguments, preparedAction);
            toolResults.add(recordActionTool(runId, activeConversationId, userId, preparedAction));
            if (Boolean.TRUE.equals(preparedAction.get("ready"))) actionRequest = actionView(preparedAction);
        }
        if (transitionArguments != null)
        {
            preparedAction = prepareProjectTransition(runId, activeConversationId, userId, viewAll, transitionArguments);
            toolResults.add(recordTransitionTool(runId, activeConversationId, userId, preparedAction));
            if (Boolean.TRUE.equals(preparedAction.get("ready"))) actionRequest = actionView(preparedAction);
        }
        if (planReviewArguments != null)
        {
            Map<String, Object> reviewTool = planReviewTool(runId, activeConversationId, userId, viewAll, planReviewArguments);
            toolResults.add(reviewTool);
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) reviewTool.get("data");
            reviewedPlan = data;
        }
        if (planDecisionArguments != null)
        {
            preparedAction = preparePlanDecision(runId, activeConversationId, userId, viewAll, planDecisionArguments);
            toolResults.add(recordPlanDecisionTool(runId, activeConversationId, userId, preparedAction));
            if (Boolean.TRUE.equals(preparedAction.get("ready"))) actionRequest = actionView(preparedAction);
        }
        if (acceptanceReviewArguments != null)
        {
            Map<String, Object> reviewTool = acceptanceReviewTool(runId, activeConversationId, userId, viewAll,
                acceptanceReviewArguments);
            toolResults.add(reviewTool);
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) reviewTool.get("data");
            reviewedAcceptance = data;
        }
        if (acceptanceDecisionArguments != null)
        {
            preparedAction = prepareAcceptanceDecision(runId, activeConversationId, userId, viewAll,
                acceptanceDecisionArguments);
            toolResults.add(recordAcceptanceDecisionTool(runId, activeConversationId, userId, preparedAction));
            if (Boolean.TRUE.equals(preparedAction.get("ready"))) actionRequest = actionView(preparedAction);
        }
        if (budgetArguments != null)
        {
            preparedAction = prepareBudgetAdjustment(runId, activeConversationId, userId, viewAll, budgetArguments);
            toolResults.add(recordBudgetAdjustmentTool(runId, activeConversationId, userId, preparedAction));
            if (Boolean.TRUE.equals(preparedAction.get("ready"))) actionRequest = actionView(preparedAction);
        }

        saveConversationContext(activeConversationId, userId, toolResults, actionRequest,
            reviewedAcceptance == null ? reviewedPlan : reviewedAcceptance);
        String answer = modelPlan == null ? buildAnswer(toolResults) : stringValue(modelPlan.get("content"));
        Map<String, Object> agentOutcome = null;
        if (modelPlan != null && !toolResults.isEmpty() && preparedAction == null && reviewedPlan == null
            && reviewedAcceptance == null
            && semanticQuery == null && !directProjectQuery && !ambiguousPersonReference
            && hasToolCalls(modelPlan) && onlyReadOnlyToolCalls(modelPlan))
        {
            try
            {
                agentOutcome = continueReadOnlyAgent(question, modelHistory, modelPlan, toolResults, runId,
                    activeConversationId, userId, viewAll);
                if (agentOutcome != null && StringUtils.isNotBlank(stringValue(agentOutcome.get("content"))))
                {
                    answer = stringValue(agentOutcome.get("content"));
                    modelCompletion = agentOutcome;
                }
            }
            catch (Exception ex)
            {
                fallbackReason = "模型多步查询暂不可用";
            }
        }
        if (semanticQuery == null && !directProjectQuery && !ambiguousPersonReference
            && modelPlan != null && !toolResults.isEmpty()
            && (agentOutcome == null || StringUtils.isBlank(stringValue(agentOutcome.get("content")))))
        {
            try
            {
                modelCompletion = modelClient.complete(question, modelPlan, modelToolMessages(modelPlan, toolResults));
                answer = stringValue(modelCompletion.get("content"));
                if (StringUtils.isBlank(answer)) throw new ServiceException("模型未返回回答");
            }
            catch (Exception ex)
            {
                answer = buildAnswer(toolResults);
                fallbackReason = "模型回答暂不可用";
                executionMode = SAFE_ROUTER_FALLBACK;
                mapper.updateRunMode(runId, executionMode);
            }
        }
        saveConversationContext(activeConversationId, userId, toolResults, actionRequest,
            reviewedAcceptance == null ? reviewedPlan : reviewedAcceptance);
        if (StringUtils.isBlank(answer)) answer = buildAnswer(toolResults);
        if (completedAction != null) answer = completedProjectAnswer(completedAction);
        else if (preparedAction != null) answer = projectActionAnswer(preparedAction);
        else if (reviewedPlan != null) answer = planReviewText(reviewedPlan);
        else if (reviewedAcceptance != null) answer = acceptanceReviewText(reviewedAcceptance);
        else if (Boolean.TRUE.equals(pendingSelection.get("requested")))
            answer = stringValue(pendingSelection.get("message"));
        else if (semanticError != null) answer = semanticError;
        else if (semanticQuery != null && !Boolean.TRUE.equals(semanticResolution.get("ready")))
            answer = semanticResolutionAnswer(semanticResolution);
        else if (semanticQuery != null) answer = semanticAnswer(semanticQuery.getQueryType(), toolResults);
        else if (ambiguousPersonReference) answer = "我还不能确定你说的“这个人”是谁。请直接告诉我姓名，我再查询，避免把其他人的数据算进来。";
        else if (directBudgetQuery) answer = projectBudgetAnswer(toolResults);
        else if ("ACCOUNTING".equals(guardedProjectQuery)) answer = projectAccountingAnswer(toolResults);
        else if ("MEMBER_PROGRESS".equals(guardedProjectQuery)) answer = projectMemberProgressAnswer(toolResults);
        else if ("DETAIL".equals(guardedProjectQuery)) answer = projectSummaryAnswer(toolResults);
        else if (!toolResults.isEmpty() && modelPlan != null && onlyReadOnlyToolCalls(modelPlan))
            answer = verifiedReadAnswer(toolResults);
        else if (toolResults.isEmpty() && modelPlan != null)
            answer = cleanModelText(stringValue(modelPlan.get("content")));
        else answer = cleanModelText(answer);
        Map<String, Object> businessCard = directBudgetQuery ? null : businessCard(toolResults);
        Map<String, Object> scope = scope(userId, viewAll);
        List<Map<String, Object>> sources = sources(toolResults);
        List<Map<String, Object>> toolReferences = toolReferences(toolResults);
        Map<String, Object> understanding = semanticResolution == null ? null
            : semanticUnderstanding(semanticResolution, toolResults);
        List<Map<String, Object>> evidenceBundle = semanticQuery == null || !Boolean.TRUE.equals(semanticResolution.get("ready"))
            ? null : semanticEvidence(semanticQuery.getQueryType(), toolResults, scope);
        if (semanticQuery != null && Boolean.TRUE.equals(semanticResolution.get("ready"))
            && !hasEvidenceFacts(evidenceBundle))
            answer = "我已经理解你的查询，但系统没有返回可追溯的业务依据，因此这次不生成具体数字或结论。";
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("scope", scope);
        metadata.put("sources", sources);
        metadata.put("toolCalls", toolReferences);
        metadata.put("executionMode", executionMode);
        metadata.put("provider", modelEnabled ? modelClient.providerCode() : "LOCAL");
        metadata.put("model", modelEnabled ? modelClient.modelName() : SAFE_ROUTER);
        if (understanding != null) metadata.put("understanding", understanding);
        if (evidenceBundle != null) metadata.put("evidence", evidenceBundle);
        if (modelPlan != null) metadata.put("planUsage", modelPlan.get("usage"));
        if (modelCompletion != null) metadata.put("completionUsage", modelCompletion.get("usage"));
        if (fallbackReason != null) metadata.put("fallbackReason", fallbackReason);
        if (reviewedPlan != null && Boolean.TRUE.equals(reviewedPlan.get("ready")))
            metadata.put("planReview", reviewedPlan);
        if (reviewedAcceptance != null && Boolean.TRUE.equals(reviewedAcceptance.get("ready")))
            metadata.put("acceptanceReview", reviewedAcceptance);
        if (businessCard != null) metadata.put("businessCard", businessCard);
        if (actionRequest != null) metadata.put("actionRequest", actionRequest);
        if (activeWorkflow != null) metadata.put("workflow", workflowView(activeWorkflow));
        Map<String, Object> completedDecisionTrace = completeDecisionTrace(decisionTrace, activeWorkflow,
            runId, traceId, executionMode, modelEnabled);
        if (!completedDecisionTrace.isEmpty()) metadata.put("decisionTrace", completedDecisionTrace);

        Map<String, Object> assistantMessage = message(activeConversationId, userId, "ASSISTANT", answer, toJson(metadata));
        mapper.insertMessage(assistantMessage);
        Long responseMessageId = longValue(assistantMessage.get("messageId"));
        mapper.finishRun(runId, responseMessageId, "SUCCEEDED", null);
        mapper.touchConversation(activeConversationId);
        audit(traceId, activeConversationId, runId, userId, userName,
            actionRequest == null ? "AI_READ_COMPLETED" : "AI_ACTION_PREPARED",
            actionRequest == null ? "老板 AI 完成经营查询，共调用 " + toolResults.size() + " 个工具"
                : "老板 AI 已生成项目立项确认单，等待本人确认", metadata);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("conversationId", activeConversationId);
        result.put("runId", runId);
        result.put("traceId", traceId);
        result.put("role", "assistant");
        result.put("content", answer);
        result.put("scope", scope);
        result.put("sources", sources);
        result.put("toolCalls", toolReferences);
        result.put("executionMode", executionMode);
        result.put("provider", modelEnabled ? modelClient.providerCode() : "LOCAL");
        result.put("model", modelEnabled ? modelClient.modelName() : SAFE_ROUTER);
        if (understanding != null) result.put("understanding", understanding);
        if (evidenceBundle != null) result.put("evidence", evidenceBundle);
        result.put("suggestions", suggestions());
        result.put("createdTime", timestamp());
        if (reviewedPlan != null && Boolean.TRUE.equals(reviewedPlan.get("ready")))
            result.put("planReview", reviewedPlan);
        if (reviewedAcceptance != null && Boolean.TRUE.equals(reviewedAcceptance.get("ready")))
            result.put("acceptanceReview", reviewedAcceptance);
        if (businessCard != null) result.put("businessCard", businessCard);
        if (actionRequest != null) result.put("actionRequest", actionRequest);
        if (activeWorkflow != null) result.put("workflow", workflowView(activeWorkflow));
        if (!completedDecisionTrace.isEmpty()) result.put("decisionTrace", completedDecisionTrace);
        return result;
    }

    @Override
    public List<Map<String, Object>> conversation(Long conversationId, Long userId)
    {
        if (conversationId == null || mapper.selectConversation(conversationId, userId, ROLE_CODE) == null)
            throw new ServiceException("AI 会话不存在或无权访问");
        List<Map<String, Object>> messages = mapper.selectMessages(conversationId, userId, 50);
        Collections.reverse(messages);
        Map<Long, Map<String, Object>> actionStates = new LinkedHashMap<Long, Map<String, Object>>();
        List<Map<String, Object>> persistedActions = mapper.selectConversationActionRequests(conversationId, userId);
        if (persistedActions != null)
        {
            for (Map<String, Object> action : persistedActions)
            {
                Long actionRequestId = longValue(action.get("actionRequestId"));
                if (actionRequestId != null) actionStates.put(actionRequestId, action);
            }
        }
        for (Map<String, Object> item : messages)
        {
            item.put("role", String.valueOf(item.remove("messageRole")).toLowerCase());
            Object metadata = item.remove("metadataJson");
            if (metadata != null && StringUtils.isNotBlank(String.valueOf(metadata)))
            {
                Object parsed = fromJson(String.valueOf(metadata));
                if (parsed instanceof Map)
                {
                    @SuppressWarnings("unchecked") Map<String, Object> parsedMetadata =
                        new LinkedHashMap<String, Object>((Map<String, Object>) parsed);
                    reconcileActionRequest(parsedMetadata, actionStates);
                    item.put("metadata", parsedMetadata);
                }
            }
        }
        Map<String, Object> activeWorkflow = mapper.selectActiveWorkflow(conversationId, userId);
        if (activeWorkflow != null)
        {
            for (int index = messages.size() - 1; index >= 0; index--)
            {
                Map<String, Object> item = messages.get(index);
                if (!"assistant".equals(stringValue(item.get("role")))) continue;
                @SuppressWarnings("unchecked") Map<String, Object> metadata = item.get("metadata") instanceof Map
                    ? new LinkedHashMap<String, Object>((Map<String, Object>) item.get("metadata"))
                    : new LinkedHashMap<String, Object>();
                metadata.put("workflow", workflowView(activeWorkflow));
                item.put("metadata", metadata);
                break;
            }
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private void reconcileActionRequest(Map<String, Object> metadata,
        Map<Long, Map<String, Object>> actionStates)
    {
        if (metadata == null || actionStates == null || actionStates.isEmpty()) return;
        Object value = metadata.get("actionRequest");
        if (!(value instanceof Map)) return;
        Map<String, Object> actionRequest = new LinkedHashMap<String, Object>((Map<String, Object>) value);
        Long actionRequestId = longValue(actionRequest.get("actionRequestId"));
        Map<String, Object> persisted = actionStates.get(actionRequestId);
        if (persisted == null) return;
        actionRequest.put("status", persisted.get("status"));
        Map<String, Object> result = resultMap(persisted.get("resultJson"));
        for (String field : new String[] { "projectId", "projectNo", "projectName", "projectStatus",
            "transitionAction", "decision", "budgetLimit", "currency" })
            if (result.containsKey(field)) actionRequest.put(field, result.get(field));
        metadata.put("actionRequest", actionRequest);
    }

    @Override
    @Transactional
    public Map<String, Object> confirmAction(Long actionRequestId, Long userId, String userName)
    {
        return confirmAction(actionRequestId, AiExecutionContext.legacy(userId, userName, true));
    }

    @Override
    @Transactional
    public Map<String, Object> confirmAction(Long actionRequestId, AiExecutionContext context)
    {
        if (context == null) throw new ServiceException("AI执行上下文不存在");
        Long userId = context.getUserId();
        String userName = context.getUserName();
        Map<String, Object> action = requireAction(actionRequestId, userId);
        if ("EXECUTED".equals(String.valueOf(action.get("status"))))
            return resultMap(action.get("resultJson"));
        if (!"PENDING".equals(String.valueOf(action.get("status"))))
            throw new ServiceException("该确认单已处理，不能重复执行");
        String actionCode = stringValue(action.get("actionCode"));
        requireActionPermission(actionCode, context);
        if (mapper.confirmActionRequest(actionRequestId, userId) != 1)
            throw new ServiceException("确认单已过期或已被处理，请重新让 AI 生成");
        if (actionCode.startsWith(AiCapabilityActionService.ACTION_PREFIX))
        {
            if (capabilityActionService == null) throw new ServiceException("AI 系统能力执行器尚未就绪");
            Map<String, Object> executed = capabilityActionService.executeConfirmed(action, context);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            if (executed != null) result.putAll(executed);
            result.put("actionRequestId", actionRequestId); result.put("status", "EXECUTED");
            result.put("actionCode", actionCode);
            if (mapper.finishActionRequest(actionRequestId, toJson(result)) != 1)
                throw new ServiceException("AI 操作状态更新失败");
            writeCapabilityExecutionMessage(action, userId, result);
            audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")),
                longValue(action.get("runId")), userId, userName, "AI_ACTION_EXECUTED",
                "老板确认 AI 系统能力 " + actionCode, result);
            return result;
        }
        if ("BUDGET_ADJUSTMENT".equals(actionCode))
        {
            Map<String, Object> payload = resultMap(action.get("actionPayloadJson"));
            Long projectId = longValue(payload.get("projectId"));
            BigDecimal budgetLimit = decimal(payload.get("budgetLimit"));
            String currency = upper(payload.get("currency"));
            String reason = text(payload, "reason");
            if (projectId == null || budgetLimit == null || budgetLimit.compareTo(BigDecimal.ZERO) < 0
                || currency.length() != 3 || StringUtils.isBlank(reason))
                throw new ServiceException("预算调整确认单数据不完整，请重新生成");
            BusinessProject project = projectService.updateBudget(projectId, budgetLimit, currency, reason,
                userId, userName, true);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("actionRequestId", actionRequestId); result.put("status", "EXECUTED");
            result.put("actionCode", actionCode); result.put("projectId", project.getProjectId());
            result.put("projectNo", project.getProjectNo()); result.put("projectName", project.getProjectName());
            result.put("oldBudgetLimit", payload.get("oldBudgetLimit")); result.put("budgetLimit", budgetLimit);
            result.put("currency", currency); result.put("reason", reason);
            if (mapper.finishActionRequest(actionRequestId, toJson(result)) != 1)
                throw new ServiceException("AI 操作状态更新失败");
            writeExecutionMessage(action, userId, result);
            audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")), longValue(action.get("runId")),
                userId, userName, "AI_ACTION_EXECUTED", "老板确认 AI 调整项目预算 " + project.getProjectName(), result);
            return result;
        }
        if ("PROJECT_ACCEPTANCE_DECISION".equals(actionCode))
        {
            Map<String, Object> payload = resultMap(action.get("actionPayloadJson"));
            Long projectId = longValue(payload.get("projectId"));
            Long acceptanceId = longValue(payload.get("acceptanceId"));
            String decision = upper(payload.get("decision"));
            String comment = text(payload, "comment");
            if (projectId == null || acceptanceId == null
                || (!"APPROVED".equals(decision) && !"RETURNED".equals(decision)))
                throw new ServiceException("验收确认单数据不完整，请重新审核验收资料");
            if ("RETURNED".equals(decision) && StringUtils.isBlank(comment))
                throw new ServiceException("退回验收必须说明原因和修改要求");
            BusinessProject current = projectService.getProject(projectId, userId, SecurityUtils.isAdmin(userId), true);
            BusinessProjectAcceptance latest = latestPendingAcceptance(current);
            if (latest == null || !acceptanceId.equals(latest.getAcceptanceId()))
                throw new ServiceException("验收资料已经发生变化，请重新让 AI 审核后再确认");
            BusinessProject project = projectService.reviewAcceptance(projectId, decision, comment,
                userId, userName, true);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("actionRequestId", actionRequestId); result.put("status", "EXECUTED");
            result.put("actionCode", actionCode); result.put("projectId", project.getProjectId());
            result.put("projectNo", project.getProjectNo()); result.put("projectName", project.getProjectName());
            result.put("projectStatus", project.getStatus()); result.put("acceptanceId", acceptanceId);
            result.put("decision", decision); result.put("comment", comment);
            if (mapper.finishActionRequest(actionRequestId, toJson(result)) != 1)
                throw new ServiceException("AI 操作状态更新失败");
            writeExecutionMessage(action, userId, result);
            audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")), longValue(action.get("runId")),
                userId, userName, "AI_ACTION_EXECUTED", "老板确认 AI 项目验收决定 " + project.getProjectName(), result);
            return result;
        }
        if ("PROJECT_TRANSITION".equals(actionCode) || "PROJECT_PLAN_DECISION".equals(actionCode))
        {
            Map<String, Object> payload = resultMap(action.get("actionPayloadJson"));
            Long projectId = longValue(payload.get("projectId"));
            String transitionAction = stringValue(payload.get("transitionAction"));
            boolean enteringPlanning = "START_PLANNING".equals(transitionAction);
            boolean resumingProject = "RESUME_PROJECT".equals(transitionAction);
            boolean approvingPlan = "CONFIRM_BASELINE".equals(transitionAction);
            boolean returningPlan = "RETURN_PLAN".equals(transitionAction);
            if (projectId == null || (!enteringPlanning && !resumingProject && !approvingPlan && !returningPlan))
                throw new ServiceException("项目确认单数据不完整，请重新生成");
            String comment = enteringPlanning ? "老板通过 AI 确认进入规划"
                : resumingProject ? "老板通过 AI 确认恢复执行"
                : approvingPlan ? "老板通过 AI 审核计划并确认启动" : stringValue(payload.get("returnReason"));
            if (returningPlan && StringUtils.isBlank(comment)) throw new ServiceException("退回计划必须说明调整原因");
            BusinessProject project = projectService.transition(projectId, transitionAction, comment,
                userId, userName, true);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("actionRequestId", actionRequestId); result.put("status", "EXECUTED");
            result.put("actionCode", actionCode); result.put("projectId", project.getProjectId());
            result.put("projectNo", project.getProjectNo()); result.put("projectName", project.getProjectName());
            result.put("projectStatus", project.getStatus()); result.put("mainOwnerName", project.getMainOwnerName());
            result.put("transitionAction", transitionAction);
            if (mapper.finishActionRequest(actionRequestId, toJson(result)) != 1)
                throw new ServiceException("AI 操作状态更新失败");
            writeExecutionMessage(action, userId, result);
            audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")), longValue(action.get("runId")),
                userId, userName, "AI_ACTION_EXECUTED", "老板确认 AI 项目操作 " + transitionAction + " " + project.getProjectName(), result);
            return result;
        }
        if (!"CREATE_PROJECT".equals(actionCode)) throw new ServiceException("暂不支持执行该类 AI 操作");
        BusinessProject input;
        try
        {
            Map<String, Object> payload = resultMap(action.get("actionPayloadJson"));
            input = objectMapper.convertValue(payload, BusinessProject.class);
            input.setPlanStartDate(date(payload.get("planStartDate")));
            input.setPlanEndDate(date(payload.get("planEndDate")));
        }
        catch (Exception ex) { throw new ServiceException("立项确认单数据不完整，请重新生成"); }
        BusinessProject project = projectService.createProject(input, userId, userName);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("actionRequestId", actionRequestId);
        result.put("status", "EXECUTED");
        result.put("actionCode", "CREATE_PROJECT");
        result.put("projectId", project.getProjectId());
        result.put("projectNo", project.getProjectNo());
        result.put("projectName", project.getProjectName());
        result.put("projectStatus", project.getStatus());
        result.put("planStartDate", formatDate(project.getPlanStartDate()));
        result.put("planEndDate", formatDate(project.getPlanEndDate()));
        result.put("mainOwnerName", project.getMainOwnerName());
        if (mapper.finishActionRequest(actionRequestId, toJson(result)) != 1)
            throw new ServiceException("AI 操作状态更新失败");
        mapper.finishWorkflow(longValue(action.get("conversationId")), userId, "CREATE_PROJECT", "COMPLETED", actionRequestId);
        writeExecutionMessage(action, userId, result);
        audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")),
            longValue(action.get("runId")), userId, userName, "AI_ACTION_EXECUTED",
            "老板确认 AI 立项并成功创建项目 " + project.getProjectName(), result);
        return result;
    }

    private void writeExecutionMessage(Map<String, Object> action, Long userId, Map<String, Object> result)
    {
        Long conversationId = longValue(action.get("conversationId"));
        String actionCode = stringValue(result.get("actionCode"));
        String transitionAction = stringValue(result.get("transitionAction"));
        String content;
        if ("BUDGET_ADJUSTMENT".equals(actionCode))
            content = "项目“" + result.get("projectName") + "”的预算已调整为 " + money(result.get("budgetLimit"))
                + " " + result.get("currency") + "。";
        else if ("PROJECT_TRANSITION".equals(actionCode))
            content = "RESUME_PROJECT".equals(transitionAction)
                ? "项目“" + result.get("projectName") + "”已经恢复执行。负责人“" + result.get("mainOwnerName") + "”可以继续推进工作。"
                : "项目“" + result.get("projectName") + "”已经进入规划。负责人“" + result.get("mainOwnerName")
                    + "”现在可以开始拆解工作计划。";
        else if ("PROJECT_PLAN_DECISION".equals(actionCode) && "CONFIRM_BASELINE".equals(transitionAction))
            content = "项目“" + result.get("projectName") + "”的计划已经批准，项目现已进入执行。";
        else if ("PROJECT_PLAN_DECISION".equals(actionCode))
            content = "项目“" + result.get("projectName") + "”的计划已退回负责人调整，项目仍处于规划中。";
        else content = completedProjectAnswer(result);
        Map<String, Object> executionMessage = message(conversationId, userId, "ASSISTANT", content,
            toJson(Collections.<String, Object>singletonMap("executedAction", result)));
        mapper.insertMessage(executionMessage);
        mapper.touchConversation(conversationId);
    }

    private void writeCapabilityExecutionMessage(Map<String, Object> action, Long userId,
        Map<String, Object> result)
    {
        Long conversationId = longValue(action.get("conversationId"));
        String content = "操作已确认并执行：" + stringValue(action.get("confirmationSummary"));
        Map<String, Object> executionMessage = message(conversationId, userId, "ASSISTANT", content,
            toJson(Collections.<String, Object>singletonMap("executedAction", result)));
        mapper.insertMessage(executionMessage); mapper.touchConversation(conversationId);
    }

    @Override
    @Transactional
    public Map<String, Object> rejectAction(Long actionRequestId, Long userId, String userName)
    {
        return rejectAction(actionRequestId, AiExecutionContext.legacy(userId, userName, true));
    }

    @Override
    @Transactional
    public Map<String, Object> rejectAction(Long actionRequestId, AiExecutionContext context)
    {
        if (context == null) throw new ServiceException("AI执行上下文不存在");
        Long userId = context.getUserId();
        String userName = context.getUserName();
        Map<String, Object> action = requireAction(actionRequestId, userId);
        requireActionPermission(stringValue(action.get("actionCode")), context);
        if (!"PENDING".equals(String.valueOf(action.get("status"))))
            throw new ServiceException("该确认单已处理");
        if (mapper.rejectActionRequest(actionRequestId, userId) != 1)
            throw new ServiceException("确认单已过期或已被处理");
        if ("CREATE_PROJECT".equals(stringValue(action.get("actionCode"))))
            mapper.finishWorkflow(longValue(action.get("conversationId")), userId, "CREATE_PROJECT", "CANCELED", actionRequestId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("actionRequestId", actionRequestId);
        result.put("status", "REJECTED");
        audit(stringValue(action.get("traceId")), longValue(action.get("conversationId")),
            longValue(action.get("runId")), userId, userName, "AI_ACTION_REJECTED",
            "老板取消 AI 生成的项目立项确认单", result);
        return result;
    }

    private void requireActionPermission(String actionCode, AiExecutionContext context)
    {
        String permission;
        if (actionCode != null && actionCode.startsWith(AiCapabilityActionService.ACTION_PREFIX))
        {
            if (capabilityActionService == null) throw new ServiceException("AI 系统能力执行器尚未就绪");
            permission = capabilityActionService.requiredPermission(actionCode);
        }
        else if ("CREATE_PROJECT".equals(actionCode)) permission = "business:project:add";
        else if ("BUDGET_ADJUSTMENT".equals(actionCode) || "PROJECT_TRANSITION".equals(actionCode)
            || "PROJECT_PLAN_DECISION".equals(actionCode) || "PROJECT_ACCEPTANCE_DECISION".equals(actionCode))
            permission = "business:project:manage";
        else throw new ServiceException("暂不支持执行该类 AI 操作");
        if (!context.hasPermission(permission)) throw new ServiceException("当前账号没有确认该操作的权限");
    }

    private Map<String, Object> requireAction(Long actionRequestId, Long userId)
    {
        if (actionRequestId == null) throw new ServiceException("确认单不存在");
        Map<String, Object> action = mapper.selectActionRequest(actionRequestId, userId);
        if (action == null) throw new ServiceException("确认单不存在或无权访问");
        return action;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultMap(Object json)
    {
        Object value = json == null ? null : fromJson(String.valueOf(json));
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private List<Map<String, Object>> recentModelHistory(Long conversationId, Long userId)
    {
        List<Map<String, Object>> source = mapper.selectMessages(conversationId, userId, 12);
        if (source == null) source = new ArrayList<Map<String, Object>>();
        Collections.reverse(source);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : source)
        {
            String role = String.valueOf(item.get("messageRole")).toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            Map<String, Object> history = new LinkedHashMap<String, Object>();
            history.put("role", role);
            history.put("content", item.get("content"));
            result.add(history);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conversationState(Long conversationId, Long userId)
    {
        List<Map<String, Object>> messages = mapper.selectMessages(conversationId, userId, 20);
        if (messages != null)
            for (Map<String, Object> message : messages)
            {
                // 只允许紧邻的上一条助手回答提供追问上下文。若它没有结构化 understanding，
                // 就不能越过它去拾取更早的话题，否则很久以后的“分别是什么”也可能接到旧项目上。
                if (!"ASSISTANT".equalsIgnoreCase(stringValue(message.get("messageRole")))) continue;
                Object parsed = fromJson(stringValue(message.get("metadataJson")));
                if (!(parsed instanceof Map)) return new LinkedHashMap<String, Object>();
                Object understanding = ((Map<String, Object>) parsed).get("understanding");
                if (!(understanding instanceof Map)) return new LinkedHashMap<String, Object>();
                Map<String, Object> state = stateFromUnderstanding((Map<String, Object>) understanding);
                if (!state.isEmpty()) return state;
                return new LinkedHashMap<String, Object>();
            }
        if (redisCache == null) return new LinkedHashMap<String, Object>();
        try
        {
            Object value = redisCache.getCacheObject(contextKey(conversationId, userId));
            return value instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) value)
                : new LinkedHashMap<String, Object>();
        }
        catch (Exception ignored) { return new LinkedHashMap<String, Object>(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stateFromUnderstanding(Map<String, Object> understanding)
    {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        if (!"RESOLVED".equals(stringValue(understanding.get("status")))) return state;
        Object projectValue = understanding.get("project");
        if (projectValue instanceof Map)
        {
            Map<String, Object> project = (Map<String, Object>) projectValue;
            if (project.get("projectId") != null) state.put("activeProjectId", project.get("projectId"));
            if (project.get("projectName") != null) state.put("activeProjectName", project.get("projectName"));
            if (project.get("projectNo") != null) state.put("activeProjectNo", project.get("projectNo"));
        }
        state.put("lastQueryType", understanding.get("queryType"));
        state.put("updatedTime", understanding.get("resolvedTime"));
        if (BusinessAiQueryType.PENDING_DECISIONS.name().equals(stringValue(understanding.get("queryType"))))
        {
            List<Map<String, Object>> decisions = copyFields(understanding.get("pendingDecisions"), "projectId",
                "projectNo", "projectName", "companyName", "mainOwnerName", "status", "baselineStatus",
                "decisionType", "decisionLabel", "nextAction");
            if (!decisions.isEmpty()) state.put("pendingDecisions", decisions);
        }
        return state;
    }

    private Map<String, Object> pendingDecisionSelection(String question, Map<String, Object> state)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Integer index = pendingDecisionIndex(question);
        if (index == null) return result;
        result.put("requested", true);
        if (!BusinessAiQueryType.PENDING_DECISIONS.name().equals(stringValue(state.get("lastQueryType"))))
        {
            result.put("message", "我还没有一份可引用的待办清单。请先问我“有哪些事情需要处理”，我会列出编号后再按编号处理。");
            return result;
        }
        List<Map<String, Object>> decisions = mapList(state.get("pendingDecisions"));
        if (decisions.isEmpty())
        {
            result.put("message", "上一份待办清单里没有需要老板决策的项目，请重新查询最新待办。");
            return result;
        }
        if (index < 1 || index > decisions.size())
        {
            result.put("message", "这份待办清单只有 " + decisions.size() + " 项，请选择 1 到 " + decisions.size() + "。");
            return result;
        }
        Map<String, Object> decision = new LinkedHashMap<String, Object>(decisions.get(index - 1));
        result.put("pendingDecisions", new ArrayList<Map<String, Object>>(decisions));
        result.put("index", index);
        result.put("decision", decision);
        String type = stringValue(decision.get("decisionType"));
        if ("PROJECT_ACCEPTANCE".equals(type))
            result.put("message", "你选中的是项目“" + decision.get("projectName")
                + "”的验收事项。我会先读取负责人提交的成果说明、交付物和凭证，生成验收审核卡；当前不会直接替你通过验收。");
        else if (!"START_PLANNING".equals(type) && !"RESUME_PROJECT".equals(type) && !"PLAN_APPROVAL".equals(type))
            result.put("message", "你选中的是项目“" + decision.get("projectName")
                + "”。这类决策还没有安全执行器，系统没有进行任何修改。");
        else result.put("message", "已锁定第 " + index + " 项：项目“" + decision.get("projectName") + "”。");
        return result;
    }

    private Integer pendingDecisionIndex(String question)
    {
        String value = StringUtils.trim(question);
        if (StringUtils.isBlank(value)) return null;
        Matcher arabic = Pattern.compile("^(?:请|帮我|先)?(?:处理|办理|打开|查看|选择)?(?:第)?\\s*(\\d{1,2})\\s*(?:个|项)?(?:待办|事项|项目)?$").matcher(value);
        if (arabic.matches()) return Integer.valueOf(arabic.group(1));
        Matcher chinese = Pattern.compile("^(?:请|帮我|先)?(?:处理|办理|打开|查看|选择)?第([一二三四五六七八九十])(?:个|项)?(?:待办|事项|项目)?$").matcher(value);
        if (!chinese.matches()) return null;
        String number = chinese.group(1);
        if ("十".equals(number)) return 10;
        return "一二三四五六七八九".indexOf(number) + 1;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingSelectionUnderstanding(Map<String, Object> selection)
    {
        Map<String, Object> decision = (Map<String, Object>) selection.get("decision");
        Map<String, Object> understanding = new LinkedHashMap<String, Object>();
        understanding.put("queryType", BusinessAiQueryType.PENDING_DECISIONS.name());
        understanding.put("queryLabel", "待老板处理事项");
        understanding.put("status", "RESOLVED");
        understanding.put("resolvedTime", timestamp());
        understanding.put("project", mapFields(decision, "projectId", "projectNo", "projectName"));
        understanding.put("pendingDecisions", selection.get("pendingDecisions"));
        understanding.put("pendingDecisionCount", mapList(selection.get("pendingDecisions")).size());
        understanding.put("summary", "已选择第 " + selection.get("index") + " 项 · " + decision.get("projectName"));
        return understanding;
    }

    private void addConversationContext(List<Map<String, Object>> history, Map<String, Object> state)
    {
        if (state == null || state.isEmpty()) return;
        Map<String, Object> contextMessage = new LinkedHashMap<String, Object>();
        contextMessage.put("role", "system");
        contextMessage.put("content", "系统保存的权威业务上下文（只用于解析‘这个、它、他们、下一步’，优先于历史回答）："
            + toJson(state));
        history.add(contextMessage);
    }

    @SuppressWarnings("unchecked")
    private void saveConversationContext(Long conversationId, Long userId, List<Map<String, Object>> toolResults,
        Map<String, Object> actionRequest, Map<String, Object> reviewedPlan)
    {
        if (redisCache == null) return;
        try
        {
            String key = contextKey(conversationId, userId);
            Object cached = redisCache.getCacheObject(key);
            Map<String, Object> context = cached instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) cached)
                : new LinkedHashMap<String, Object>();
            if (toolResults != null)
                for (Map<String, Object> tool : toolResults)
                {
                    context.put("lastToolCode", tool.get("toolCode"));
                    Object data = tool.get("data");
                    if (!(data instanceof Map)) continue;
                    Map<String, Object> values = (Map<String, Object>) data;
                    rememberProjectContext(context, values.get("project"));
                    Object decisions = values.get("decisions");
                    if (decisions instanceof List && ((List<?>) decisions).size() == 1)
                    {
                        Object decision = ((List<?>) decisions).get(0);
                        rememberProjectContext(context, decision);
                        Map<String, Object> view = mapFields(decision, "decisionType", "decisionLabel");
                        context.putAll(view);
                    }
                }
            if (reviewedPlan != null) rememberProjectContext(context, reviewedPlan.get("project"));
            if (actionRequest != null)
            {
                context.put("pendingActionRequestId", actionRequest.get("actionRequestId"));
                context.put("pendingActionCode", actionRequest.get("actionCode"));
                rememberProjectContext(context, actionRequest.get("project"));
            }
            context.put("updatedTime", timestamp());
            redisCache.setCacheObject(key, context, 12, TimeUnit.HOURS);
        }
        catch (Exception ignored) { }
    }

    private void rememberProjectContext(Map<String, Object> context, Object value)
    {
        Map<String, Object> project = mapFields(value, "projectId", "projectNo", "projectName", "status",
            "baselineStatus", "mainOwnerName", "companyName");
        if (project.isEmpty()) return;
        if (project.containsKey("projectId")) context.put("activeProjectId", project.get("projectId"));
        if (project.containsKey("projectNo")) context.put("activeProjectNo", project.get("projectNo"));
        if (project.containsKey("projectName")) context.put("activeProjectName", project.get("projectName"));
        if (project.containsKey("status")) context.put("activeProjectStatus", project.get("status"));
        if (project.containsKey("baselineStatus")) context.put("activeBaselineStatus", project.get("baselineStatus"));
        if (project.containsKey("mainOwnerName")) context.put("activeProjectOwner", project.get("mainOwnerName"));
        if (project.containsKey("companyName")) context.put("activeCompanyName", project.get("companyName"));
    }

    private String contextKey(Long conversationId, Long userId)
    {
        return "business:boss-ai:context:" + userId + ":" + conversationId;
    }

    private boolean isWorkflow(Map<String, Object> workflow, String code, String... statuses)
    {
        if (workflow == null || !code.equals(stringValue(workflow.get("workflowCode")))) return false;
        String status = stringValue(workflow.get("workflowStatus"));
        for (String candidate : statuses) if (candidate.equals(status)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> workflowDraft(Map<String, Object> workflow)
    {
        if (workflow == null) return new LinkedHashMap<String, Object>();
        Object value = fromJson(stringValue(workflow.get("draftJson")));
        return value instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) value)
            : new LinkedHashMap<String, Object>();
    }

    private void mergeNonBlank(Map<String, Object> target, Map<String, Object> incoming)
    {
        if (incoming == null) return;
        for (Map.Entry<String, Object> entry : incoming.entrySet())
        {
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof String && StringUtils.isBlank((String) value)) continue;
            target.put(entry.getKey(), value);
        }
    }

    /**
     * 当前工作流优先于模型路由。这里仅做可确定的字段提取；不唯一的人员、公司或日期仍交给后续校验追问。
     */
    private void mergeProjectCreateMessage(Map<String, Object> draft, Map<String, Object> storedDraft,
        String question)
    {
        String value = StringUtils.trim(question);
        if (StringUtils.isBlank(value)) return;
        List<Map<String, Object>> users = projectService.userOptions(null);
        List<Map<String, Object>> staff = staffService.listOptions();

        // Explicit revisions must win over both the stored draft and model-extracted fields.
        // A boss may revise any collected value without restarting the workflow.
        applyExplicitProjectDraftRevisions(draft, storedDraft, value);

        List<String> mentionedOwners = new ArrayList<String>();
        for (Map<String, Object> user : users)
        {
            String nick = stringValue(user.get("nickName"));
            String account = stringValue(user.get("userName"));
            if (StringUtils.isNotBlank(nick) && value.contains(nick)) mentionedOwners.add(nick);
            else if (StringUtils.isNotBlank(account) && value.toLowerCase().contains(account.toLowerCase()))
                mentionedOwners.add(account);
        }
        if (mentionedOwners.size() == 1) draft.put("ownerName", mentionedOwners.get(0));

        boolean shanghai = value.contains("上海");
        boolean vietnam = value.contains("越南") || value.toLowerCase().contains("meimaru");
        if (shanghai ^ vietnam) draft.put("companyName", shanghai ? "上海美丸文化公司" : "越南meimaru公司");

        Matcher named = Pattern.compile("(?:项目(?:叫|名称是|名为)|新建|创建)[“\"']?([^，,。；;“”\"']{2,60})[”\"']?").matcher(value);
        if (named.find())
        {
            String candidate = StringUtils.trim(named.group(1));
            candidate = candidate.replaceFirst("^(一个|新)?项目[:：\\s]*", "");
            if (!containsAny(candidate, "一个新项目", "一个项目")) draft.put("projectName", candidate);
        }
        if (StringUtils.isBlank(text(draft, "projectName")) && value.matches(".*[，,].*"))
        {
            String first = StringUtils.trim(value.split("[，,]", 2)[0]);
            first = first.replaceFirst("^(项目叫|项目名称是|项目名为)[:：\\s]*", "");
            if (first.length() >= 2 && !isProjectCreateRequest(first)) draft.put("projectName", first);
        }

        Matcher objective = Pattern.compile("(?:目标是|目标为|要做到|完成标准是|做成)([^。；;]{3,300})").matcher(value);
        if (objective.find()) draft.put("objective", StringUtils.trim(objective.group(1)));
        if (StringUtils.isBlank(text(draft, "objective")))
        {
            Matcher deliverable = Pattern.compile("(?:^|[，,；;\\s])(?:1\\s*[.、:：]?\\s*)?((?:完成|制作|做)\\s*\\d+(?:\\.\\d+)?\\s*(?:条|个|份|套|场|篇|小时|天)[^，,。；;]*)")
                .matcher(value);
            if (deliverable.find()) draft.put("objective", cleanObjective(deliverable.group(1)));
            else
            {
                Matcher daily = Pattern.compile("((?:每天|每日)\\s*(?:完成|制作|做)?\\s*\\d+(?:\\.\\d+)?\\s*(?:条|个|份|套|场|篇)[^，,。；;]*)")
                    .matcher(value);
                if (daily.find()) draft.put("objective", cleanObjective(daily.group(1)));
            }
        }
        if (containsAny(value, "利润项目", "看利润", "赚多少钱")) draft.put("accountingMode", "PROFIT");
        else if (containsAny(value, "成本项目", "只看成本", "控制成本")) draft.put("accountingMode", "COST");
        else if (containsAny(value, "价值项目", "不算利润")) draft.put("accountingMode", "VALUE");
        else if (containsAny(value, "混合核算", "利润和成本一起")) draft.put("accountingMode", "HYBRID");
        if (containsAny(value, "不设预算", "没有预算", "暂不设预算", "预算暂不设置")) draft.put("noBudget", true);
        Matcher budget = Pattern.compile("(?:预算(?:是|为|上限|设为|设置为)?[:：\\s]*)?(\\d+(?:\\.\\d+)?)\\s*(万|元)?").matcher(value);
        if (value.contains("预算") && budget.find())
        {
            BigDecimal amount = new BigDecimal(budget.group(1));
            if ("万".equals(budget.group(2))) amount = amount.multiply(new BigDecimal("10000"));
            draft.put("budgetLimit", amount); draft.put("noBudget", false);
        }
        if (value.toUpperCase().contains("VND") || value.contains("越南盾")) draft.put("baseCurrency", "VND");
        else if (value.toUpperCase().contains("CNY") || value.contains("人民币")) draft.put("baseCurrency", "CNY");
    }

    private String cleanObjective(String value)
    {
        String result = StringUtils.trim(value);
        result = result.replaceFirst("(?:吧|就行|即可)$", "");
        return StringUtils.trim(result);
    }

    private void applyExplicitProjectDraftRevisions(Map<String, Object> draft, Map<String, Object> storedDraft,
        String value)
    {
        Matcher projectName = Pattern.compile(
            "(?:把|将)?(?:这个)?(?:项目名称|项目名字|项目名|名称|名字)\\s*(?:修改|调整|变更|改|换)\\s*(?:成|为|到)?\\s*[“\"']?([^，,。；;\\n”\"']{2,60})")
            .matcher(value);
        if (projectName.find())
            draft.put("projectName", cleanRevisionValue(projectName.group(1)));

        Matcher objective = Pattern.compile(
            "(?:把|将)?(?:这个)?(?:项目)?目标\\s*(?:修改|调整|变更|改|换)\\s*(?:成|为|到)?\\s*([^，,。；;\\n]{1,300})")
            .matcher(value);
        if (objective.find())
        {
            String revised = cleanRevisionValue(objective.group(1));
            if (StringUtils.isNotBlank(revised))
                draft.put("objective", revisedObjective(text(storedDraft, "objective"), revised));
        }
    }

    private String cleanRevisionValue(String value)
    {
        String result = StringUtils.trim(value);
        result = result.replaceFirst("(?:吧|就行|即可|可以了|就可以了)$", "");
        return StringUtils.trim(result);
    }

    private String revisedObjective(String existing, String revised)
    {
        String candidate = cleanObjective(revised);
        if (candidate.matches("\\d+(?:\\.\\d+)?\\s*(?:条|个|份|套|场|篇|小时|天)"))
        {
            Matcher existingQuantity = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:条|个|份|套|场|篇|小时|天)")
                .matcher(defaultValue(existing, ""));
            if (existingQuantity.find())
                return existingQuantity.replaceFirst(Matcher.quoteReplacement(candidate));
            return "完成" + candidate;
        }
        return candidate;
    }

    private Map<String, Object> persistProjectWorkflow(Map<String, Object> workflow, Long conversationId,
        Long userId, Long messageId, Map<String, Object> draft, Map<String, Object> prepared)
    {
        List<String> missing = stringList(prepared.get("missingFields"));
        String status = Boolean.TRUE.equals(prepared.get("ready")) ? "WAITING_CONFIRMATION" : "COLLECTING";
        String step = projectWorkflowStep(missing);
        Map<String, Object> bound = new LinkedHashMap<String, Object>();
        Map<String, Object> owner = findUser(projectService.userOptions(null), text(draft, "ownerName"));
        Map<String, Object> company = findCompany(staffService.listOptions(), text(draft, "companyName"));
        if (owner != null) bound.put("ownerUserId", owner.get("userId"));
        if (company != null) bound.put("companyDeptId", company.get("companyDeptId"));
        boolean newWorkflow = workflow == null || workflow.get("workflowId") == null;
        Map<String, Object> before = newWorkflow ? Collections.<String, Object>emptyMap() : workflowDraft(workflow);
        Map<String, Object> row = newWorkflow ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(workflow);
        row.put("conversationId", conversationId); row.put("userId", userId); row.put("roleCode", ROLE_CODE);
        row.put("workflowCode", "CREATE_PROJECT"); row.put("workflowStatus", status); row.put("currentStep", step);
        row.put("draftJson", toJson(draft)); row.put("missingFieldsJson", toJson(missing));
        row.put("boundEntitiesJson", toJson(bound)); row.put("actionRequestId", prepared.get("actionRequestId"));
        row.put("expireTime", new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L));
        String eventType;
        if (newWorkflow)
        {
            mapper.insertWorkflow(row);
            row.put("versionNo", 1);
            eventType = "STARTED";
        }
        else
        {
            int changed = mapper.updateWorkflow(row);
            if (row.get("workflowId") != null && row.get("versionNo") != null && changed != 1)
                throw new ServiceException("立项草稿刚刚被其他操作更新，请重新发送本条信息");
            Number version = (Number) row.get("versionNo");
            if (version != null) row.put("versionNo", version.longValue() + 1L);
            if (replacedExistingWorkflowField(before, draft)) eventType = "FIELDS_UPDATED";
            else eventType = Boolean.TRUE.equals(prepared.get("ready")) ? "WAITING_CONFIRMATION" : "FIELDS_MERGED";
        }
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("workflowId", row.get("workflowId")); event.put("conversationId", conversationId);
        event.put("userId", userId); event.put("eventType", eventType); event.put("beforeJson", toJson(before));
        event.put("afterJson", toJson(draft)); event.put("messageId", messageId);
        mapper.insertWorkflowEvent(event);
        return row;
    }

    private boolean replacedExistingWorkflowField(Map<String, Object> before, Map<String, Object> after)
    {
        if (before == null || before.isEmpty() || after == null) return false;
        for (Map.Entry<String, Object> entry : before.entrySet())
        {
            String previous = stringValue(entry.getValue());
            if (StringUtils.isBlank(previous)) continue;
            if (!previous.equals(stringValue(after.get(entry.getKey())))) return true;
        }
        return false;
    }

    private String projectWorkflowStep(List<String> missing)
    {
        if (missing.isEmpty()) return "WAITING_CONFIRMATION";
        if (hasMissing(missing, "项目名称") || hasMissing(missing, "主负责人") || hasMissing(missing, "归属公司"))
            return "BASIC_INFO";
        if (hasMissing(missing, "项目目标") || hasMissing(missing, "计划开始") || hasMissing(missing, "正确的计划周期"))
            return "GOAL_AND_PERIOD";
        return "ACCOUNTING_AND_BUDGET";
    }

    private Map<String, Object> workflowView(Map<String, Object> workflow)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("workflowId", workflow.get("workflowId"));
        result.put("workflowCode", workflow.get("workflowCode"));
        result.put("status", workflow.get("workflowStatus"));
        result.put("currentStep", workflow.get("currentStep"));
        result.put("version", workflow.get("versionNo"));
        result.put("draft", workflowDraft(workflow));
        Object missing = fromJson(stringValue(workflow.get("missingFieldsJson")));
        result.put("missingFields", missing instanceof List ? missing : Collections.emptyList());
        result.put("actionRequestId", workflow.get("actionRequestId"));
        return result;
    }

    private Long requireOrCreateConversation(Long conversationId, String question, Long userId)
    {
        if (conversationId != null)
        {
            if (mapper.selectConversation(conversationId, userId, ROLE_CODE) == null)
                throw new ServiceException("AI 会话不存在或无权访问");
            return conversationId;
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("userId", userId);
        row.put("roleCode", ROLE_CODE);
        row.put("title", question.length() > 36 ? question.substring(0, 36) + "…" : question);
        mapper.insertConversation(row);
        return longValue(row.get("conversationId"));
    }

    private Map<String, Object> accountingTool(Long runId, Long conversationId, Long userId, boolean viewAll)
    {
        Map<String, Object> data = accountingService.bossOverview(userId, viewAll);
        return recordTool(runId, conversationId, userId, "boss_today_accounting", "今日经营结果", data,
            "/business/accounting/boss-overview");
    }

    private Map<String, Object> projectTool(Long runId, Long conversationId, Long userId, boolean viewAll)
    {
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        // 工作台只展示前10项，但 AI 的项目总览必须使用完整权限范围内的项目集合，
        // 否则老板追问“分别是什么”时会漏掉第11项以后的项目。
        List<BusinessProject> projects = projectService.listProjects(Collections.<String, Object>emptyMap(),
            userId, viewAll, true);
        Object projectSource = projects == null || projects.isEmpty() ? dashboard.get("projects") : projects;
        // 工具结果只保留这次回答真正需要的字段。预算、内部核算版本等敏感字段不会进入模型上下文。
        List<Map<String, Object>> safeProjects = copyFields(projectSource, "projectId", "projectNo",
            "projectName", "companyName", "initiatorName", "mainOwnerName", "status", "planStartDate",
            "planEndDate", "objective");
        Map<String, Object> summary = mapFields(dashboard.get("summary"), "totalCount", "activeCount",
            "pendingDecisionCount", "overdueProjectCount", "highRiskProjectCount");
        if (summary.get("totalCount") == null) summary.put("totalCount", safeProjects.size());
        data.put("summary", summary);
        data.put("projects", safeProjects);
        data.put("bizDate", LocalDate.now(clock).toString());
        data.put("summarySourcePath", "/business/boss/dashboard");
        data.put("projectsSourcePath", "/business/project/list");
        return recordTool(runId, conversationId, userId, "boss_project_snapshot", "项目态势", data,
            "/business/project/portfolio");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingTool(Long runId, Long conversationId, Long userId, boolean viewAll)
    {
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        List<Map<String, Object>> decisions = pendingDecisionViews(dashboard.get("decisions"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("decisions", decisions);
        data.put("tasks", dashboard.get("tasks"));
        data.put("decisionCount", decisions.size());
        data.put("taskCount", dashboard.get("tasks") instanceof List ? ((List<Object>) dashboard.get("tasks")).size() : 0);
        return recordTool(runId, conversationId, userId, "boss_pending_decisions", "待老板处理", data,
            "/business/boss/dashboard");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pendingDecisionViews(Object value)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<Object>) value)
        {
            Map<String, Object> source;
            if (item instanceof Map) source = (Map<String, Object>) item;
            else
            {
                try { source = objectMapper.convertValue(item, Map.class); }
                catch (Exception ignored) { continue; }
            }
            Map<String, Object> decision = new LinkedHashMap<String, Object>();
            String status = stringValue(source.get("status"));
            String baselineStatus = stringValue(source.get("baselineStatus"));
            for (String field : new String[] { "projectId", "projectNo", "projectName", "companyName", "mainOwnerName",
                "objective", "status", "baselineStatus", "planStartDate", "planEndDate" })
                if (source.containsKey(field)) decision.put(field, source.get(field));
            if ("PLANNING".equals(status) && "SUBMITTED".equals(baselineStatus))
            {
                decision.put("decisionType", "PLAN_APPROVAL");
                decision.put("decisionLabel", "审核已提交的项目计划");
                decision.put("nextAction", "查看完整计划后，批准启动或退回负责人修改");
            }
            else if ("DRAFT".equals(status))
            {
                decision.put("decisionType", "START_PLANNING");
                decision.put("decisionLabel", "确认是否进入规划");
                decision.put("nextAction", "确认后由负责人开始制定计划");
            }
            else if ("ACCEPTANCE".equals(status))
            {
                decision.put("decisionType", "PROJECT_ACCEPTANCE");
                decision.put("decisionLabel", "项目验收决策");
                decision.put("nextAction", "检查交付结果后确认验收或退回整改");
            }
            else if ("PAUSED".equals(status))
            {
                decision.put("decisionType", "RESUME_PROJECT");
                decision.put("decisionLabel", "确认是否恢复执行");
                decision.put("nextAction", "确认后项目恢复执行，负责人和成员继续工作");
            }
            else
            {
                decision.put("decisionType", "PROJECT_DECISION");
                decision.put("decisionLabel", "项目待老板决策");
                decision.put("nextAction", "查看项目详情后作出决定");
            }
            result.add(decision);
        }
        return result;
    }

    private Map<String, Object> staffTool(Long runId, Long conversationId, Long userId)
    {
        List<Map<String, Object>> staff = staffService.listOptions();
        Map<String, Integer> companyCounts = new LinkedHashMap<String, Integer>();
        for (Map<String, Object> row : staff)
        {
            String company = row.get("companyName") == null ? "未归属公司" : String.valueOf(row.get("companyName"));
            companyCounts.put(company, companyCounts.containsKey(company) ? companyCounts.get(company) + 1 : 1);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("staffCount", staff.size());
        data.put("companyCounts", companyCounts);
        return recordTool(runId, conversationId, userId, "boss_staff_snapshot", "人员概况", data,
            "/business/staff/options");
    }

    private Map<String, Object> projectDetailTool(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> data = loadProjectDetail(userId, viewAll, arguments);
        return recordTool(runId, conversationId, userId, "boss_project_detail", "项目完整详情", data,
            "/business/project/detail");
    }

    private Map<String, Object> projectAccountingDetailTool(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> safeArguments = arguments == null
            ? new LinkedHashMap<String, Object>() : arguments;
        List<BusinessProject> projects = accessibleProjects(userId, viewAll);
        BusinessProject project = matchProject(projects, safeArguments);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (project == null)
        {
            data.put("ready", false);
            data.put("candidateProjects", projectNames(projects));
            data.put("missingFields", Collections.singletonList(projects.isEmpty()
                ? "当前没有可查询的项目" : "请说明要查看哪个项目的经营明细"));
            return recordTool(runId, conversationId, userId, "boss_project_accounting_detail", "项目经营明细",
                data, "/business/accounting/dashboard");
        }
        String bizDate = text(safeArguments, "bizDate");
        if (!bizDate.matches("\\d{4}-\\d{2}-\\d{2}")) bizDate = LocalDate.now(clock).toString();
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        query.put("projectId", project.getProjectId());
        query.put("dateFrom", bizDate);
        query.put("dateTo", bizDate);
        Map<String, Object> dashboard = accountingService.dashboard(query, userId, viewAll);
        data.put("ready", true);
        data.put("project", projectIdentity(project));
        data.put("bizDate", bizDate);
        data.put("summary", dashboard.get("summary"));
        data.put("results", copyFields(dashboard.get("results"), "resultId", "projectId", "projectName", "bizDate",
            "revenueAmount", "costAmount", "personnelCost", "adjustmentAmount", "profitAmount", "budgetSpent",
            "valueScore", "dataCutoffTime", "closeStatus", "resultVersion", "calculationDetail", "currency"));
        data.put("facts", copyFields(dashboard.get("facts"), "factId", "bizDate", "categoryName", "factKind",
            "amount", "quantity", "currency", "description", "status", "sourceDomain", "sourceType"));
        Object rows = dashboard.get("results");
        if (rows instanceof List && !((List<?>) rows).isEmpty())
        {
            Map<String, Object> row = mapFields(((List<?>) rows).get(0), "resultId", "budgetSpent");
            BigDecimal budgetSpent = decimal(row.get("budgetSpent"));
            BigDecimal budgetLimit = project.getBudgetLimit();
            BigDecimal overBudgetAmount = budgetSpent != null && budgetLimit != null
                && budgetSpent.compareTo(budgetLimit) > 0 ? budgetSpent.subtract(budgetLimit) : BigDecimal.ZERO;
            Map<String, Object> budgetMetrics = new LinkedHashMap<String, Object>();
            budgetMetrics.put("budgetSpent", budgetSpent);
            budgetMetrics.put("budgetLimit", budgetLimit);
            budgetMetrics.put("overBudgetAmount", overBudgetAmount);
            budgetMetrics.put("calculationRule", "超预算金额 = 累计成本 - 预算上限；不得把累计成本当作超预算金额");
            data.put("budgetMetrics", budgetMetrics);
            Long resultId = longValue(row.get("resultId"));
            if (resultId != null)
            {
                Map<String, Object> detail = accountingService.resultDetail(resultId, userId, viewAll);
                data.put("calculationItems", copyFields(detail.get("items"), "componentCode", "componentName",
                    "amount", "quantity", "calculationDetail"));
                data.put("personnelItems", copyFields(detail.get("personnelItems"), "userId", "componentCode",
                    "componentName", "amount", "quantity", "calculationDetail"));
            }
        }
        return recordTool(runId, conversationId, userId, "boss_project_accounting_detail", "项目经营明细", data,
            "/business/accounting/dashboard");
    }

    private Map<String, Object> loadProjectDetail(Long userId, boolean viewAll, Map<String, Object> arguments)
    {
        List<BusinessProject> projects = accessibleProjects(userId, viewAll);
        BusinessProject selected = matchProject(projects, arguments == null
            ? new LinkedHashMap<String, Object>() : arguments);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (selected == null)
        {
            result.put("ready", false);
            result.put("candidateProjects", projectNames(projects));
            result.put("missingFields", Collections.singletonList(projects.isEmpty()
                ? "当前没有可查询的项目" : "请说明要查看哪个项目"));
            return result;
        }
        BusinessProject detail = projectService.getProject(selected.getProjectId(), userId, viewAll, true);
        Map<String, Object> operating = projectService.operatingConfig(detail.getProjectId(), userId, viewAll, true);
        result.put("ready", true);
        result.put("project", projectIdentity(detail));
        result.put("members", copyFields(detail.getMembers(), "memberId", "userId", "userNameSnapshot", "memberRole", "joinedDate"));
        result.put("routines", copyFields(detail.getRoutines(), "routineId", "routineName", "frequency", "targetValue",
            "unit", "assigneeUserId", "assigneeName", "startDate", "endDate", "evidenceRequired", "todayTarget", "todayActual",
            "cumulativeActual", "todayReportId", "todaySummary", "todayIssueReason", "remark"));
        result.put("tasks", copyFields(detail.getTasks(), "taskId", "taskName", "assigneeUserId", "assigneeName", "status", "progress",
            "priority", "planStartDate", "dueDate", "actualFinishTime", "remark"));
        result.put("milestones", copyFields(detail.getMilestones(), "milestoneId", "milestoneName", "planDate",
            "actualDate", "weight", "status", "remark"));
        result.put("risks", copyFields(detail.getRisks(), "riskId", "riskTitle", "severity", "probability",
            "ownerName", "dueDate", "status", "responsePlan"));
        result.put("events", copyFields(detail.getEvents(), "eventType", "fromStatus", "toStatus", "operatorName",
            "eventTime", "detail"));
        result.put("kpis", copyFields(operating.get("kpis"), "kpiId", "kpiName", "targetValue", "actualValue",
            "unit", "periodType", "ownerName", "weight"));
        result.put("staffAllocations", copyFields(operating.get("staffAllocations"), "userId", "userName",
            "allocationMode", "allocationValue", "effectiveFrom", "effectiveTo", "approvalStatus"));
        result.put("bizDate", LocalDate.now(clock).toString());
        return result;
    }

    private Map<String, Object> resolveSemanticQuery(BusinessAiSemanticQuery query, String question,
        Map<String, Object> conversationState, Long userId, boolean viewAll)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("queryType", query.getQueryType().name());
        result.put("queryLabel", semanticQueryLabel(query.getQueryType()));
        result.put("bizDate", query.getBizDate() == null ? LocalDate.now(clock).toString()
            : query.getBizDate().toString());
        result.put("personName", query.getPersonName());
        result.put("resolvedTime", timestamp());
        if (!query.getQueryType().requiresProjectReference())
        {
            result.put("ready", true);
            result.put("status", "RESOLVED");
            return result;
        }

        List<BusinessProject> projects = accessibleProjects(userId, viewAll);
        BusinessProject selected = uniqueMentionedProject(projects, question);
        if (selected == null) selected = uniqueQuestionProjectMatch(projects, question);
        Long activeProjectId = longValue(conversationState == null ? null : conversationState.get("activeProjectId"));
        if (selected == null && activeProjectId != null)
            selected = projectById(projects, activeProjectId);
        // 模型给出的项目名称和ID都不是事实。只有ID与已确认上下文一致，或老板原话明确包含项目编号时才采用。
        if (selected == null && query.getProjectId() != null)
        {
            BusinessProject candidate = projectById(projects, query.getProjectId());
            if (candidate != null && (query.getProjectId().equals(activeProjectId)
                || questionContainsProject(question, candidate))) selected = candidate;
        }
        if (selected == null && projects.size() == 1) selected = projects.get(0);
        if (selected == null)
        {
            result.put("ready", false);
            result.put("status", projects.isEmpty() ? "UNRESOLVED" : "AMBIGUOUS");
            result.put("candidateProjects", projectCandidates(projects));
            result.put("summary", projects.isEmpty() ? "当前没有可查询的项目"
                : "还不能唯一确定你要查询的项目，请从候选项目中选择");
            return result;
        }
        result.put("ready", true);
        result.put("status", "RESOLVED");
        result.put("project", projectIdentity(selected));
        return result;
    }

    private BusinessAiSemanticQuery localSemanticQuery(String question, List<String> intents,
        Map<String, Object> conversationState)
    {
        if (isProjectCreateRequest(question) || isAdvanceExistingRequest(question) || isPlanReviewRequest(question)
            || isPlanApproveRequest(question) || isPlanReturnRequest(question) || isBudgetAdjustmentRequest(question)
            || isAcceptanceReviewRequest(question) || isAcceptanceApproveRequest(question)
            || isAcceptanceReturnRequest(question))
            return null;
        BusinessAiQueryType type = null;
        String guarded = guardedProjectQuery(question);
        if ("BUDGET".equals(guarded)) type = BusinessAiQueryType.PROJECT_BUDGET;
        else if ("ACCOUNTING".equals(guarded)) type = BusinessAiQueryType.PROJECT_ACCOUNTING;
        else if ("MEMBER_PROGRESS".equals(guarded)) type = BusinessAiQueryType.MEMBER_PROGRESS;
        else if ("DETAIL".equals(guarded)) type = BusinessAiQueryType.PROJECT_DETAIL;
        else if (containsAny(question, "今天经营", "今日经营", "今天收入", "今日收入", "今天成本", "今日成本",
            "今天盈亏", "今日盈亏", "今天亏损", "今日亏损")) type = BusinessAiQueryType.TODAY_ACCOUNTING;
        else if (containsAny(question, "所有项目", "全部项目", "项目整体", "项目态势", "项目总览", "项目概况"))
            type = BusinessAiQueryType.PROJECT_PORTFOLIO;
        else if (isProjectPortfolioFollowUp(question, conversationState))
            type = BusinessAiQueryType.PROJECT_PORTFOLIO;
        else if (containsAny(question, "待处理事项", "待办事项", "需要我处理", "要我处理", "等我审批",
            "待我审批", "待老板处理", "待老板决策")) type = BusinessAiQueryType.PENDING_DECISIONS;
        else if (containsAny(question, "人员分布", "人员概况", "员工分布", "员工概况", "团队分布", "公司有多少人"))
            type = BusinessAiQueryType.STAFF_OVERVIEW;
        else if (intents != null && intents.size() == 1 && intents.contains("ACCOUNTING"))
            type = BusinessAiQueryType.TODAY_ACCOUNTING;
        else if (intents != null && intents.size() == 1 && intents.contains("PROJECTS"))
            type = BusinessAiQueryType.PROJECT_PORTFOLIO;
        else if (intents != null && intents.size() == 1 && intents.contains("PENDING"))
            type = BusinessAiQueryType.PENDING_DECISIONS;
        else if (intents != null && intents.size() == 1 && intents.contains("STAFF"))
            type = BusinessAiQueryType.STAFF_OVERVIEW;
        if (type == null) return null;
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("queryType", type.name());
        try { return BusinessAiSemanticQuery.fromMap(values); }
        catch (BusinessAiSemanticQueryParseException ignored) { return null; }
    }

    /**
     * “分别是什么 / 是什么内容 / 具体有哪些”没有独立业务实体，必须继承上一轮已经核验的查询类型。
     * 这里只继承结构化 understanding 中的 PROJECT_PORTFOLIO，不扫描助手自然语言，避免旧回答反向污染事实。
     */
    private boolean isProjectPortfolioFollowUp(String question, Map<String, Object> conversationState)
    {
        if (conversationState == null
            || !BusinessAiQueryType.PROJECT_PORTFOLIO.name().equals(stringValue(conversationState.get("lastQueryType"))))
            return false;
        String normalized = StringUtils.trim(question);
        return containsAny(normalized, "分别是什么", "分别有哪些", "具体是什么", "具体有哪些", "都是什么",
            "都有哪些", "是哪几个", "哪几个项目", "项目名称", "列出来", "展开看看", "展开说说",
            "是什么内容", "项目内容", "分别说说", "详细说说");
    }

    private Map<String, Object> executeSemanticQuery(BusinessAiSemanticQuery query,
        Map<String, Object> resolution, Long runId, Long conversationId, Long userId, boolean viewAll)
    {
        BusinessAiQueryType type = query.getQueryType();
        if (type == BusinessAiQueryType.TODAY_ACCOUNTING)
            return accountingTool(runId, conversationId, userId, viewAll);
        if (type == BusinessAiQueryType.PROJECT_PORTFOLIO)
            return projectTool(runId, conversationId, userId, viewAll);
        if (type == BusinessAiQueryType.PENDING_DECISIONS)
            return pendingTool(runId, conversationId, userId, viewAll);
        if (type == BusinessAiQueryType.STAFF_OVERVIEW)
            return staffTool(runId, conversationId, userId);
        @SuppressWarnings("unchecked") Map<String, Object> project = resolution.get("project") instanceof Map
            ? (Map<String, Object>) resolution.get("project") : Collections.<String, Object>emptyMap();
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("projectId", project.get("projectId"));
        arguments.put("projectName", project.get("projectName"));
        if (type == BusinessAiQueryType.PROJECT_ACCOUNTING)
        {
            arguments.put("bizDate", resolution.get("bizDate"));
            return projectAccountingDetailTool(runId, conversationId, userId, viewAll, arguments);
        }
        return projectDetailTool(runId, conversationId, userId, viewAll, arguments);
    }

    private String semanticAnswer(BusinessAiQueryType type, List<Map<String, Object>> tools)
    {
        if (type == BusinessAiQueryType.PROJECT_BUDGET) return projectBudgetAnswer(tools);
        if (type == BusinessAiQueryType.PROJECT_ACCOUNTING) return projectAccountingAnswer(tools);
        if (type == BusinessAiQueryType.MEMBER_PROGRESS) return projectMemberProgressAnswer(tools);
        if (type == BusinessAiQueryType.PROJECT_DETAIL) return projectSummaryAnswer(tools);
        Map<String, Object> tool = tools.isEmpty() ? null : tools.get(0);
        if (tool == null || !(tool.get("data") instanceof Map)) return "系统暂时没有返回可核验的数据。";
        @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) tool.get("data");
        if (type == BusinessAiQueryType.TODAY_ACCOUNTING) return accountingText(data);
        if (type == BusinessAiQueryType.PROJECT_PORTFOLIO) return projectText(data);
        if (type == BusinessAiQueryType.PENDING_DECISIONS) return pendingText(data);
        if (type == BusinessAiQueryType.STAFF_OVERVIEW) return staffText(data);
        return "系统已经完成查询。";
    }

    private String semanticResolutionAnswer(Map<String, Object> resolution)
    {
        List<Map<String, Object>> candidates = mapList(resolution.get("candidateProjects"));
        if (candidates.isEmpty()) return stringValue(resolution.get("summary")) + "。";
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> candidate : candidates)
            names.add(stringValue(candidate.get("projectName")) + "（" + stringValue(candidate.get("mainOwnerName")) + "负责）");
        return "我还不能唯一确定你指的是哪个项目。请选择：" + join(names, "、") + "。";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> semanticUnderstanding(Map<String, Object> resolution,
        List<Map<String, Object>> toolResults)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String field : new String[] { "queryType", "queryLabel", "status", "project", "personName",
            "candidateProjects", "resolvedTime" })
            if (resolution.containsKey(field) && resolution.get(field) != null) result.put(field, resolution.get(field));
        String date = stringValue(resolution.get("bizDate"));
        if (StringUtils.isNotBlank(date))
        {
            Map<String, Object> dateRange = new LinkedHashMap<String, Object>();
            dateRange.put("start", date); dateRange.put("end", date); dateRange.put("label", date);
            result.put("dateRange", dateRange);
        }
        if (BusinessAiQueryType.PENDING_DECISIONS.name().equals(stringValue(resolution.get("queryType"))))
        {
            for (Map<String, Object> tool : toolResults)
            {
                if (!"boss_pending_decisions".equals(stringValue(tool.get("toolCode")))
                    || !(tool.get("data") instanceof Map)) continue;
                Map<String, Object> data = (Map<String, Object>) tool.get("data");
                List<Map<String, Object>> decisions = copyFields(data.get("decisions"), "projectId", "projectNo",
                    "projectName", "companyName", "mainOwnerName", "status", "baselineStatus", "decisionType",
                    "decisionLabel", "nextAction");
                result.put("pendingDecisions", decisions);
                result.put("pendingDecisionCount", decisions.size());
                break;
            }
        }
        result.put("summary", semanticUnderstandingSummary(resolution));
        return result;
    }

    @SuppressWarnings("unchecked")
    private String semanticUnderstandingSummary(Map<String, Object> resolution)
    {
        if (!Boolean.TRUE.equals(resolution.get("ready"))) return stringValue(resolution.get("summary"));
        String value = stringValue(resolution.get("queryLabel"));
        Object project = resolution.get("project");
        if (project instanceof Map) value += " · " + stringValue(((Map<String, Object>) project).get("projectName"));
        if (resolution.get("personName") != null) value += " · " + resolution.get("personName");
        if (resolution.get("bizDate") != null) value += " · " + resolution.get("bizDate");
        return value;
    }

    private String semanticFieldLabel(String field)
    {
        if ("queryType".equals(field)) return "查询目的";
        if ("projectId".equals(field) || "projectName".equals(field)) return "项目";
        if ("personName".equals(field)) return "人员";
        if ("bizDate".equals(field)) return "日期";
        return "问题";
    }

    private String semanticQueryLabel(BusinessAiQueryType type)
    {
        if (type == BusinessAiQueryType.TODAY_ACCOUNTING) return "今日经营";
        if (type == BusinessAiQueryType.PROJECT_PORTFOLIO) return "项目整体态势";
        if (type == BusinessAiQueryType.PENDING_DECISIONS) return "待老板处理事项";
        if (type == BusinessAiQueryType.STAFF_OVERVIEW) return "人员概况";
        if (type == BusinessAiQueryType.PROJECT_DETAIL) return "项目详情";
        if (type == BusinessAiQueryType.PROJECT_ACCOUNTING) return "项目经营核算";
        if (type == BusinessAiQueryType.PROJECT_BUDGET) return "项目预算";
        if (type == BusinessAiQueryType.MEMBER_PROGRESS) return "成员完成进度";
        return type.name();
    }

    private BusinessProject projectById(List<BusinessProject> projects, Long projectId)
    {
        if (projectId == null) return null;
        for (BusinessProject project : projects) if (projectId.equals(project.getProjectId())) return project;
        return null;
    }

    /**
     * 直接从老板原话做候选匹配，不信任模型补出来的项目名称。
     * 使用最长公共连续片段并要求唯一最高分：例如“新谷项目”可以命中“新谷酵素视频剪辑”，
     * 而只有“视频项目”时两个候选同分，必须让老板选择，不能猜第一个。
     */
    private BusinessProject uniqueQuestionProjectMatch(List<BusinessProject> projects, String question)
    {
        if (StringUtils.isBlank(question)) return null;
        BusinessProject selected = null;
        int bestScore = 1;
        boolean tied = false;
        for (BusinessProject project : projects)
        {
            int score = longestCommonSubstringLength(normalizeEntityText(question),
                normalizeEntityText(project.getProjectName()));
            if (score > bestScore)
            {
                selected = project;
                bestScore = score;
                tied = false;
            }
            else if (score == bestScore && score >= 2 && selected != null
                && !selected.getProjectId().equals(project.getProjectId())) tied = true;
        }
        return bestScore >= 2 && !tied ? selected : null;
    }

    private String normalizeEntityText(String value)
    {
        return value == null ? "" : value.toLowerCase().replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private int longestCommonSubstringLength(String left, String right)
    {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) return 0;
        int[] previous = new int[right.length() + 1];
        int best = 0;
        for (int i = 1; i <= left.length(); i++)
        {
            int[] current = new int[right.length() + 1];
            for (int j = 1; j <= right.length(); j++)
                if (left.charAt(i - 1) == right.charAt(j - 1))
                {
                    current[j] = previous[j - 1] + 1;
                    if (current[j] > best) best = current[j];
                }
            previous = current;
        }
        return best;
    }

    private boolean questionContainsProject(String question, BusinessProject project)
    {
        return StringUtils.isNotBlank(question) && project != null
            && ((StringUtils.isNotBlank(project.getProjectName()) && question.contains(project.getProjectName()))
                || (StringUtils.isNotBlank(project.getProjectNo()) && question.contains(project.getProjectNo())));
    }

    private List<Map<String, Object>> projectCandidates(List<BusinessProject> projects)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (BusinessProject project : projects)
            result.add(mapFields(projectIdentity(project), "projectId", "projectNo", "projectName", "companyName",
                "mainOwnerName", "status"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> semanticEvidence(BusinessAiQueryType type,
        List<Map<String, Object>> tools, Map<String, Object> scope)
    {
        if (tools == null || tools.isEmpty() || !(tools.get(0).get("data") instanceof Map))
            return Collections.emptyList();
        Map<String, Object> tool = tools.get(0);
        Map<String, Object> data = (Map<String, Object>) tool.get("data");
        String sourcePath = defaultValue(stringValue(tool.get("sourcePath")), "/business");
        Instant asOf = Instant.now(clock);
        List<BusinessAiEvidence> facts = new ArrayList<BusinessAiEvidence>();
        List<String> warnings = new ArrayList<String>();
        String period = defaultValue(stringValue(data.get("bizDate")), LocalDate.now(clock).toString());

        if (type == BusinessAiQueryType.TODAY_ACCOUNTING)
        {
            Map<String, Object> today = data.get("today") instanceof Map
                ? (Map<String, Object>) data.get("today") : Collections.<String, Object>emptyMap();
            addEvidence(facts, BusinessAiEvidenceEntityType.ACCOUNTING, period, "今日经营", "project_count",
                "已核算项目", today.get("projectCount"), "个", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            addEvidence(facts, BusinessAiEvidenceEntityType.ACCOUNTING, period, "今日经营", "revenue_amount",
                "收入", today.get("revenueAmount"), "CNY", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            addEvidence(facts, BusinessAiEvidenceEntityType.ACCOUNTING, period, "今日经营", "business_cost",
                "业务成本", today.get("businessCost"), "CNY", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            addEvidence(facts, BusinessAiEvidenceEntityType.ACCOUNTING, period, "今日经营", "personnel_cost",
                "人员成本", today.get("personnelCost"), "CNY", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            addEvidence(facts, BusinessAiEvidenceEntityType.ACCOUNTING, period, "今日经营", "profit_amount",
                "经营结果", today.get("profitAmount"), "CNY", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
        }
        else if (type == BusinessAiQueryType.PROJECT_PORTFOLIO)
        {
            String summarySourcePath = defaultValue(stringValue(data.get("summarySourcePath")), sourcePath);
            String projectsSourcePath = defaultValue(stringValue(data.get("projectsSourcePath")), sourcePath);
            Map<String, Object> summary = data.get("summary") instanceof Map
                ? (Map<String, Object>) data.get("summary") : Collections.<String, Object>emptyMap();
            for (String[] metric : new String[][] { { "totalCount", "project_total", "项目总量" },
                { "activeCount", "project_active", "执行中项目" }, { "pendingDecisionCount", "project_pending", "待决策项目" },
                { "overdueProjectCount", "project_overdue", "逾期项目" }, { "highRiskProjectCount", "project_high_risk", "高风险项目" } })
                addEvidence(facts, BusinessAiEvidenceEntityType.COMPANY, "portfolio", "项目组合", metric[1], metric[2],
                    summary.get(metric[0]), "个", period, summarySourcePath, asOf, summary.get(metric[0]) == null
                        ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED);
            List<Map<String, Object>> projects = copyFields(data.get("projects"), "projectId", "projectNo",
                "projectName", "companyName", "mainOwnerName", "status", "planStartDate", "planEndDate", "objective");
            for (Map<String, Object> project : projects)
            {
                String projectId = stringValue(project.get("projectId"));
                if (StringUtils.isBlank(projectId))
                {
                    addWarningOnce(warnings, "部分项目缺少稳定编号，已跳过无法追溯的项目明细");
                    continue;
                }
                String projectName = defaultValue(stringValue(project.get("projectName")), "未命名项目");
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                    "project_name", projectName + " · 项目名称", projectName, "", period, projectsSourcePath,
                    asOf, BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                    "main_owner", projectName + " · 负责人", project.get("mainOwnerName"), "", period,
                    projectsSourcePath, asOf, StringUtils.isBlank(stringValue(project.get("mainOwnerName")))
                        ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                    "project_status", projectName + " · 状态", project.get("status"), "", period, projectsSourcePath,
                    asOf, BusinessAiEvidenceStatus.CONFIRMED);
                String planPeriod = projectPeriodText(project);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                    "plan_period", projectName + " · 计划周期", planPeriod, "", period, projectsSourcePath,
                    asOf, "尚未设置".equals(planPeriod)
                        ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                    "project_objective", projectName + " · 项目内容", project.get("objective"), "", period,
                    projectsSourcePath, asOf, StringUtils.isBlank(stringValue(project.get("objective")))
                        ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED);
            }
        }
        else if (type == BusinessAiQueryType.PENDING_DECISIONS)
        {
            addEvidence(facts, BusinessAiEvidenceEntityType.COMPANY, "pending", "待处理事项", "decision_count",
                "待老板决策", data.get("decisionCount"), "项", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            addEvidence(facts, BusinessAiEvidenceEntityType.COMPANY, "pending", "待处理事项", "task_count",
                "本人未完成任务", data.get("taskCount"), "项", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
        }
        else if (type == BusinessAiQueryType.STAFF_OVERVIEW)
        {
            addEvidence(facts, BusinessAiEvidenceEntityType.COMPANY, "all", "公司人员", "staff_count",
                "有效人员账号", data.get("staffCount"), "人", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            if (data.get("companyCounts") instanceof Map)
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) data.get("companyCounts")).entrySet())
                    addEvidence(facts, BusinessAiEvidenceEntityType.COMPANY, stringValue(entry.getKey()),
                        stringValue(entry.getKey()), "company_staff_count", "公司人员数", entry.getValue(), "人",
                        period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
        }
        else
        {
            Map<String, Object> project = data.get("project") instanceof Map
                ? (Map<String, Object>) data.get("project") : Collections.<String, Object>emptyMap();
            String projectId = stringValue(project.get("projectId"));
            String projectName = defaultValue(stringValue(project.get("projectName")), "项目");
            if (type == BusinessAiQueryType.PROJECT_BUDGET || type == BusinessAiQueryType.PROJECT_DETAIL)
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "budget_limit", "预算上限",
                    project.get("budgetLimit"), stringValue(project.get("baseCurrency")), period, sourcePath, asOf,
                    project.get("budgetLimit") == null ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED);
            if (type == BusinessAiQueryType.PROJECT_DETAIL)
            {
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "project_status", "项目状态",
                    project.get("status"), "", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "main_owner", "主负责人",
                    project.get("mainOwnerName"), "", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "member_count", "参项人员",
                    project.get("memberCount"), "人", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "task_count", "一次性任务",
                    project.get("taskCount"), "项", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, "open_risk_count", "未关闭风险",
                    project.get("openRiskCount"), "项", period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
            }
            if (type == BusinessAiQueryType.PROJECT_ACCOUNTING)
            {
                List<Map<String, Object>> results = mapList(data.get("results"));
                if (results.isEmpty())
                {
                    addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                        "accounting_result", "经营核算结果", "未生成", "", period, sourcePath, asOf,
                        BusinessAiEvidenceStatus.MISSING);
                    warnings.add("该日期尚未生成项目经营核算结果");
                }
                else
                {
                    Map<String, Object> row = results.get(0);
                    String currency = defaultValue(stringValue(row.get("currency")), stringValue(project.get("baseCurrency")));
                    for (String[] metric : new String[][] { { "revenueAmount", "revenue_amount", "收入" },
                        { "costAmount", "business_cost", "业务成本" }, { "personnelCost", "personnel_cost", "人员成本" },
                        { "profitAmount", "profit_amount", "经营结果" }, { "budgetSpent", "budget_spent", "累计成本" } })
                        addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName, metric[1], metric[2],
                            row.get(metric[0]), currency, period, sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                    for (Map<String, Object> person : mapList(data.get("personnelItems")))
                    {
                        String userId = stringValue(person.get("userId"));
                        if (StringUtils.isBlank(userId))
                        {
                            addWarningOnce(warnings, "部分人员成本明细缺少人员ID，已跳过无法追溯的记录");
                            continue;
                        }
                        addEvidence(facts, BusinessAiEvidenceEntityType.PERSON, userId,
                            stringValue(person.get("componentName")), "project_personnel_cost:" + projectId,
                            "人员成本", person.get("amount"), currency, period, sourcePath, asOf,
                            BusinessAiEvidenceStatus.CONFIRMED);
                    }
                }
            }
            if (type == BusinessAiQueryType.MEMBER_PROGRESS)
            {
                for (Map<String, Object> routine : mapList(data.get("routines")))
                {
                    String routineId = stringValue(routine.get("routineId"));
                    if (StringUtils.isBlank(routineId))
                    {
                        addWarningOnce(warnings, "部分持续工作缺少稳定编号，已跳过无法追溯的记录");
                        continue;
                    }
                    String routineName = defaultValue(stringValue(routine.get("routineName")), "持续工作");
                    String unit = stringValue(routine.get("unit"));
                    BusinessAiEvidenceStatus reportStatus = routine.get("todayReportId") == null
                        ? BusinessAiEvidenceStatus.MISSING : BusinessAiEvidenceStatus.CONFIRMED;
                    addEvidence(facts, BusinessAiEvidenceEntityType.ROUTINE, routineId, routineName, "routine_today_actual",
                        routineName + " · " + stringValue(routine.get("assigneeName")) + "今日完成",
                        reportStatus == BusinessAiEvidenceStatus.MISSING ? "未填报" : routine.get("todayActual"), unit, period,
                        sourcePath, asOf, reportStatus);
                    addEvidence(facts, BusinessAiEvidenceEntityType.ROUTINE, routineId, routineName, "routine_today_target",
                        routineName + " · " + stringValue(routine.get("assigneeName")) + "今日目标", routine.get("todayTarget"), unit, period,
                        sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                    addEvidence(facts, BusinessAiEvidenceEntityType.ROUTINE, routineId, routineName, "routine_cumulative_actual",
                        routineName + " · " + stringValue(routine.get("assigneeName")) + "累计完成", routine.get("cumulativeActual"), unit, period,
                        sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                }
                for (Map<String, Object> task : mapList(data.get("tasks")))
                {
                    String taskId = stringValue(task.get("taskId"));
                    if (StringUtils.isBlank(taskId))
                    {
                        addWarningOnce(warnings, "部分一次性任务缺少稳定编号，已跳过无法追溯的记录");
                        continue;
                    }
                    addEvidence(facts, BusinessAiEvidenceEntityType.TASK, taskId,
                        defaultValue(stringValue(task.get("taskName")), "一次性任务"), "task_progress",
                        defaultValue(stringValue(task.get("taskName")), "一次性任务") + " · "
                            + stringValue(task.get("assigneeName")) + "任务进度", task.get("progress"), "%", period,
                        sourcePath, asOf, BusinessAiEvidenceStatus.CONFIRMED);
                }
                if (facts.isEmpty())
                {
                    addEvidence(facts, BusinessAiEvidenceEntityType.PROJECT, projectId, projectName,
                        "member_work_arrangement", "成员工作安排", "未设置", "", period, sourcePath, asOf,
                        BusinessAiEvidenceStatus.MISSING);
                    addWarningOnce(warnings, "当前没有可统计的成员工作安排");
                }
            }
        }

        facts = deduplicateEvidence(facts, warnings);

        boolean hasIncompleteEvidence = false;
        for (BusinessAiEvidence fact : facts)
            if (fact.getStatus() != BusinessAiEvidenceStatus.CONFIRMED) hasIncompleteEvidence = true;
        BusinessAiEvidenceCoverage coverage = facts.isEmpty() ? BusinessAiEvidenceCoverage.NONE
            : warnings.isEmpty() && !hasIncompleteEvidence ? BusinessAiEvidenceCoverage.FULL
                : BusinessAiEvidenceCoverage.PARTIAL;
        BusinessAiEvidenceBundle.Builder bundleBuilder = BusinessAiEvidenceBundle.builder()
            .scope(stringValue(scope.get("label"))).asOf(asOf).coverage(coverage).evidence(facts);
        for (String warning : warnings) bundleBuilder.addWarning(warning);
        BusinessAiEvidenceBundle bundle = bundleBuilder.build();
        Map<String, Object> group = new LinkedHashMap<String, Object>();
        group.put("label", "系统已核验业务事实");
        group.put("cutoffTime", timestamp());
        group.put("coverage", bundle.getCoverage().name());
        group.put("warnings", bundle.getWarnings());
        List<Map<String, Object>> factViews = new ArrayList<Map<String, Object>>();
        for (BusinessAiEvidence evidence : bundle.getEvidence())
        {
            Map<String, Object> fact = new LinkedHashMap<String, Object>();
            fact.put("factId", evidence.getEvidenceId()); fact.put("label", evidence.getMetricLabel());
            fact.put("value", evidence.getValue()); fact.put("unit", evidence.getUnit());
            fact.put("bizDate", evidence.getPeriod()); fact.put("recordType", evidence.getEntityType().name());
            fact.put("recordId", evidence.getEntityId()); fact.put("entityName", evidence.getEntityName());
            fact.put("metricCode", evidence.getMetricCode()); fact.put("status", evidence.getStatus().name());
            factViews.add(fact);
        }
        group.put("facts", factViews);
        return Collections.singletonList(group);
    }

    private boolean addEvidence(List<BusinessAiEvidence> result, BusinessAiEvidenceEntityType entityType,
        String entityId, String entityName, String metricCode, String metricLabel, Object value, String unit,
        String period, String sourcePath, Instant cutoffTime, BusinessAiEvidenceStatus status)
    {
        if (StringUtils.isBlank(entityId)) return false;
        String normalizedEntityId = entityId;
        String normalizedEntityName = defaultValue(entityName, "未命名对象");
        String normalizedValue = value == null ? "未设置" : String.valueOf(value);
        result.add(BusinessAiEvidence.builder().entityType(entityType).entityId(normalizedEntityId)
            .entityName(normalizedEntityName).metricCode(metricCode).metricLabel(metricLabel)
            .value(normalizedValue).unit(unit).period(period).sourcePath(sourcePath)
            .cutoffTime(cutoffTime).status(status).build());
        return true;
    }

    private List<BusinessAiEvidence> deduplicateEvidence(List<BusinessAiEvidence> source, List<String> warnings)
    {
        Map<String, BusinessAiEvidence> unique = new LinkedHashMap<String, BusinessAiEvidence>();
        Set<String> conflicted = new HashSet<String>();
        for (BusinessAiEvidence evidence : source)
        {
            String evidenceId = evidence.getEvidenceId();
            if (conflicted.contains(evidenceId)) continue;
            BusinessAiEvidence existing = unique.get(evidenceId);
            if (existing == null)
            {
                unique.put(evidenceId, evidence);
                continue;
            }
            if (sameEvidenceValue(existing, evidence)) continue;
            unique.remove(evidenceId);
            conflicted.add(evidenceId);
            addWarningOnce(warnings, "发现相同业务记录的冲突值，已从回答依据中排除");
        }
        return new ArrayList<BusinessAiEvidence>(unique.values());
    }

    private boolean sameEvidenceValue(BusinessAiEvidence left, BusinessAiEvidence right)
    {
        return left.getValue().equals(right.getValue()) && left.getStatus() == right.getStatus()
            && left.getMetricLabel().equals(right.getMetricLabel()) && left.getUnit().equals(right.getUnit());
    }

    private void addWarningOnce(List<String> warnings, String warning)
    {
        if (!warnings.contains(warning)) warnings.add(warning);
    }

    @SuppressWarnings("unchecked")
    private boolean hasEvidenceFacts(List<Map<String, Object>> evidence)
    {
        if (evidence == null) return false;
        for (Map<String, Object> group : evidence)
            if (group.get("facts") instanceof List && !((List<Object>) group.get("facts")).isEmpty()) return true;
        return false;
    }

    private List<BusinessProject> accessibleProjects(Long userId, boolean viewAll)
    {
        List<BusinessProject> result = new ArrayList<BusinessProject>();
        // AI 实体检索必须使用完整项目目录，不能复用老板工作台“仅展示前10项”的视图数据。
        List<BusinessProject> projects = projectService.listProjects(Collections.<String, Object>emptyMap(),
            userId, viewAll, true);
        if (projects != null) result.addAll(projects);
        // 兼容尚未实现完整目录的测试替身和旧部署；正式服务优先使用上面的全量列表。
        if (!result.isEmpty()) return result;
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        Object value = dashboard.get("projects");
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value)
        {
            try { result.add(item instanceof BusinessProject ? (BusinessProject) item
                : objectMapper.convertValue(item, BusinessProject.class)); }
            catch (Exception ignored) { }
        }
        return result;
    }

    private BusinessProject matchProject(List<BusinessProject> projects, Map<String, Object> arguments)
    {
        Long requestedId = longValue(arguments.get("projectId"));
        String requestedName = text(arguments, "projectName");
        List<BusinessProject> matches = new ArrayList<BusinessProject>();
        for (BusinessProject project : projects)
        {
            if (requestedId != null && requestedId.equals(project.getProjectId())) matches.add(project);
            else if (requestedId == null && StringUtils.isNotBlank(requestedName)
                && (project.getProjectName().equalsIgnoreCase(requestedName)
                    || project.getProjectName().contains(requestedName)
                    || requestedName.contains(project.getProjectName()))) matches.add(project);
        }
        if (matches.size() == 1) return matches.get(0);
        return requestedId == null && StringUtils.isBlank(requestedName) && projects.size() == 1 ? projects.get(0) : null;
    }

    private Map<String, Object> resolveProjectReference(Map<String, Object> arguments,
        List<Map<String, Object>> history, String question, Long userId, boolean viewAll)
    {
        Map<String, Object> result = arguments == null ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(arguments);
        if (longValue(result.get("projectId")) != null || StringUtils.isNotBlank(text(result, "projectName")))
            return result;

        List<BusinessProject> projects = accessibleProjects(userId, viewAll);
        BusinessProject explicit = uniqueMentionedProject(projects, question);
        if (explicit != null) return projectReference(result, explicit);

        if (history != null)
        {
            Pattern activeIdPattern = Pattern.compile("\\\"activeProjectId\\\"\\s*:\\s*(\\d+)");
            for (Map<String, Object> item : history)
            {
                if (!"system".equalsIgnoreCase(stringValue(item.get("role")))) continue;
                Matcher matcher = activeIdPattern.matcher(stringValue(item.get("content")));
                if (!matcher.find()) continue;
                Long activeId = longValue(matcher.group(1));
                for (BusinessProject project : projects)
                    if (activeId != null && activeId.equals(project.getProjectId()))
                        return projectReference(result, project);
            }
            for (int index = history.size() - 1; index >= 0; index--)
            {
                Map<String, Object> item = history.get(index);
                String role = stringValue(item.get("role"));
                if (!"user".equalsIgnoreCase(role) && !"system".equalsIgnoreCase(role)) continue;
                BusinessProject mentioned = uniqueMentionedProject(projects, stringValue(item.get("content")));
                if (mentioned != null) return projectReference(result, mentioned);
            }
        }
        return result;
    }

    private BusinessProject uniqueMentionedProject(List<BusinessProject> projects, String content)
    {
        if (StringUtils.isBlank(content)) return null;
        BusinessProject match = null;
        for (BusinessProject project : projects)
        {
            if (StringUtils.isBlank(project.getProjectName()) || !content.contains(project.getProjectName())) continue;
            if (match != null && !match.getProjectId().equals(project.getProjectId())) return null;
            match = project;
        }
        return match;
    }

    private Map<String, Object> projectReference(Map<String, Object> result, BusinessProject project)
    {
        result.put("projectId", project.getProjectId());
        result.put("projectName", project.getProjectName());
        return result;
    }

    private List<String> projectNames(List<BusinessProject> projects)
    {
        List<String> result = new ArrayList<String>();
        for (BusinessProject project : projects) result.add(project.getProjectName());
        return result;
    }

    private Map<String, Object> projectIdentity(BusinessProject project)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", project.getProjectId()); result.put("projectNo", project.getProjectNo());
        result.put("projectName", project.getProjectName()); result.put("companyName", project.getCompanyName());
        result.put("initiatorName", project.getInitiatorName()); result.put("mainOwnerName", project.getMainOwnerName());
        result.put("objective", project.getObjective()); result.put("status", project.getStatus());
        result.put("baselineStatus", project.getBaselineStatus()); result.put("projectType", project.getProjectType());
        result.put("accountingMode", project.getAccountingMode()); result.put("managementMode", project.getManagementMode());
        result.put("priority", project.getPriority()); result.put("planStartDate", formatDate(project.getPlanStartDate()));
        result.put("planEndDate", formatDate(project.getPlanEndDate())); result.put("budgetLimit", project.getBudgetLimit());
        result.put("baseCurrency", project.getBaseCurrency()); result.put("memberCount", project.getMemberCount());
        result.put("taskCount", project.getTaskCount()); result.put("completedTaskCount", project.getCompletedTaskCount());
        result.put("openRiskCount", project.getOpenRiskCount());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> businessCard(List<Map<String, Object>> toolResults)
    {
        if (toolResults == null) return null;
        for (int index = toolResults.size() - 1; index >= 0; index--)
        {
            Map<String, Object> tool = toolResults.get(index);
            if (!"boss_project_accounting_detail".equals(stringValue(tool.get("toolCode")))) continue;
            if (!(tool.get("data") instanceof Map)) continue;
            Map<String, Object> data = (Map<String, Object>) tool.get("data");
            if (!Boolean.TRUE.equals(data.get("ready"))) continue;
            List<Map<String, Object>> results = mapList(data.get("results"));
            if (results.isEmpty()) continue;
            Map<String, Object> daily = results.get(0);
            Map<String, Object> project = data.get("project") instanceof Map
                ? (Map<String, Object>) data.get("project") : new LinkedHashMap<String, Object>();
            Map<String, Object> metrics = new LinkedHashMap<String, Object>();
            metrics.put("revenueAmount", daily.get("revenueAmount"));
            metrics.put("businessCost", daily.get("costAmount"));
            metrics.put("personnelCost", daily.get("personnelCost"));
            metrics.put("adjustmentAmount", daily.get("adjustmentAmount"));
            metrics.put("profitAmount", daily.get("profitAmount"));
            metrics.put("budgetSpent", daily.get("budgetSpent"));
            metrics.put("budgetLimit", project.get("budgetLimit"));
            BigDecimal profit = decimal(daily.get("profitAmount"));
            BigDecimal spent = decimal(daily.get("budgetSpent"));
            BigDecimal limit = decimal(project.get("budgetLimit"));
            BigDecimal overBudget = spent != null && limit != null && spent.compareTo(limit) > 0
                ? spent.subtract(limit) : BigDecimal.ZERO;
            metrics.put("overBudgetAmount", overBudget);
            Map<String, Object> card = new LinkedHashMap<String, Object>();
            card.put("type", "OPERATING_ANALYSIS");
            card.put("title", stringValue(project.get("projectName")) + "经营分析");
            card.put("status", overBudget.compareTo(BigDecimal.ZERO) > 0 ? "OVER_BUDGET"
                : profit != null && profit.compareTo(BigDecimal.ZERO) < 0 ? "LOSS" : "NORMAL");
            card.put("bizDate", data.get("bizDate"));
            card.put("project", project);
            card.put("metrics", metrics);
            card.put("personnelItems", data.get("personnelItems"));
            card.put("calculationItems", data.get("calculationItems"));
            List<String> warnings = new ArrayList<String>();
            if (profit != null && profit.compareTo(BigDecimal.ZERO) < 0)
                warnings.add("当日经营结果为亏损 " + money(profit.abs()) + " 元");
            if (overBudget.compareTo(BigDecimal.ZERO) > 0)
                warnings.add("累计成本已超过预算 " + money(overBudget) + " 元");
            card.put("warnings", warnings);
            List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
            actions.add(cardAction("VIEW_PROJECT", "查看完整项目", "查看项目“" + project.get("projectName") + "”的完整详情"));
            actions.add(cardAction("REVIEW_ALLOCATION", "检查人员投入", "分析项目“" + project.get("projectName") + "”的人员投入是否合理"));
            if (overBudget.compareTo(BigDecimal.ZERO) > 0)
                actions.add(cardAction("ADJUST_BUDGET", "调整项目预算", "帮我调整项目“" + project.get("projectName") + "”的预算"));
            card.put("actions", actions);
            return card;
        }
        for (int index = toolResults.size() - 1; index >= 0; index--)
        {
            Map<String, Object> tool = toolResults.get(index);
            if (!"boss_project_detail".equals(stringValue(tool.get("toolCode"))) || !(tool.get("data") instanceof Map)) continue;
            Map<String, Object> data = (Map<String, Object>) tool.get("data");
            if (!Boolean.TRUE.equals(data.get("ready"))) continue;
            Map<String, Object> card = new LinkedHashMap<String, Object>();
            card.put("type", "PROJECT_OVERVIEW"); card.put("title", "项目完整情况");
            card.put("project", data.get("project")); card.put("members", data.get("members"));
            card.put("routines", data.get("routines")); card.put("tasks", data.get("tasks"));
            card.put("kpis", data.get("kpis")); card.put("risks", data.get("risks"));
            card.put("staffAllocations", data.get("staffAllocations"));
            return card;
        }
        return null;
    }

    private Map<String, Object> cardAction(String code, String label, String prompt)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("code", code); result.put("label", label); result.put("prompt", prompt);
        return result;
    }

    private Map<String, Object> recordTool(Long runId, Long conversationId, Long userId, String code,
        String label, Map<String, Object> data, String sourcePath)
    {
        Date now = new Date();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId);
        row.put("conversationId", conversationId);
        row.put("userId", userId);
        row.put("toolCode", code);
        row.put("riskLevel", "READ_ONLY");
        row.put("inputJson", "{}");
        row.put("outputJson", toJson(auditToolData(code, data)));
        row.put("status", "SUCCEEDED");
        row.put("startedTime", now);
        row.put("finishedTime", new Date());
        mapper.insertToolCall(row);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", code);
        result.put("label", label);
        result.put("riskLevel", "READ_ONLY");
        result.put("sourcePath", sourcePath);
        result.put("data", data);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstToolArguments(Map<String, Object> plan, String toolCode)
    {
        if (plan == null || !(plan.get("toolCalls") instanceof List)) return null;
        for (Object item : (List<Object>) plan.get("toolCalls"))
        {
            if (!(item instanceof Map)) continue;
            Map<String, Object> call = (Map<String, Object>) item;
            if (toolCode.equals(String.valueOf(call.get("name"))))
                return call.get("arguments") instanceof Map
                    ? (Map<String, Object>) call.get("arguments") : new LinkedHashMap<String, Object>();
        }
        return null;
    }

    private Map<String, Object> prepareProjectAction(Long runId, Long conversationId, Long userId,
        Map<String, Object> arguments)
    {
        List<Map<String, Object>> users = projectService.userOptions(null);
        List<Map<String, Object>> staff = staffService.listOptions();
        Map<String, Object> owner = findUser(users, text(arguments, "ownerName"));
        Map<String, Object> company = findCompany(staff, text(arguments, "companyName"));
        List<String> missing = new ArrayList<String>();
        String projectName = text(arguments, "projectName");
        String objective = text(arguments, "objective");
        String accountingMode = upper(arguments.get("accountingMode"));
        Date startDate = date(arguments.get("planStartDate"));
        Date endDate = date(arguments.get("planEndDate"));
        boolean noBudget = booleanValue(arguments.get("noBudget"));
        BigDecimal budget = decimal(arguments.get("budgetLimit"));
        String baseCurrency = defaultValue(upper(arguments.get("baseCurrency")), "CNY");
        if (StringUtils.isBlank(projectName)) missing.add("项目名称");
        if (owner == null) missing.add("主负责人（请使用系统中的姓名或账号）");
        if (company == null) missing.add("归属公司（上海美丸文化公司或越南meimaru公司）");
        if (StringUtils.isBlank(objective)) missing.add("项目目标");
        if (startDate == null || endDate == null) missing.add("计划开始和结束日期");
        else if (startDate.after(endDate)) missing.add("正确的计划周期（结束日期不能早于开始日期）");
        if (!contains(new String[] { "PROFIT", "COST", "VALUE", "HYBRID" }, accountingMode))
            missing.add("核算方式（利润、成本、价值或混合）");
        if (budget == null && !noBudget) missing.add("预算金额，或明确说明不设预算");
        if (budget != null && budget.compareTo(BigDecimal.ZERO) < 0) missing.add("不小于0的预算金额");
        if (baseCurrency.length() != 3) missing.add("三位币种代码（例如 CNY 或 VND）");

        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("ready", missing.isEmpty());
        prepared.put("missingFields", missing);
        prepared.put("ownerCandidates", optionNames(users, "nickName", "userName"));
        prepared.put("companyCandidates", optionNames(staff, "companyName", null));
        if (!missing.isEmpty()) return prepared;

        BusinessProject project = new BusinessProject();
        project.setProjectName(projectName);
        project.setMainOwnerUserId(longValue(owner.get("userId")));
        project.setCompanyDeptId(longValue(company.get("companyDeptId")));
        project.setObjective(objective);
        project.setPlanStartDate(startDate);
        project.setPlanEndDate(endDate);
        project.setProjectType(defaultValue(upper(arguments.get("projectType")), "GENERAL"));
        project.setAccountingMode(accountingMode);
        project.setManagementMode(allowed(defaultValue(upper(arguments.get("managementMode")), "SIMPLE"),
            new String[] { "SIMPLE", "STANDARD", "DELIVERY" }, "SIMPLE"));
        project.setPriority(allowed(defaultValue(upper(arguments.get("priority")), "MEDIUM"),
            new String[] { "LOW", "MEDIUM", "HIGH" }, "MEDIUM"));
        project.setBaseCurrency(baseCurrency);
        project.setBudgetLimit(noBudget ? null : budget);
        if ("LIVE".equals(upper(arguments.get("executionSource")))) project.setExecutionSource("LIVE");

        String ownerName = displayName(owner);
        String companyName = stringValue(company.get("companyName"));
        String summary = "项目“" + projectName + "”，归属" + companyName + "，主负责人" + ownerName
            + "，周期" + formatDate(startDate) + "至" + formatDate(endDate) + "，"
            + accountingLabel(accountingMode) + "，预算" + (noBudget ? "暂不设置" : money(budget) + " " + project.getBaseCurrency());
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId);
        row.put("conversationId", conversationId);
        row.put("userId", userId);
        row.put("actionCode", "CREATE_PROJECT");
        row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("actionPayloadJson", toJson(projectActionPayload(project)));
        row.put("confirmationSummary", summary);
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);

        Map<String, Object> projectView = new LinkedHashMap<String, Object>();
        projectView.put("projectName", projectName);
        projectView.put("companyName", companyName);
        projectView.put("mainOwnerName", ownerName);
        projectView.put("objective", objective);
        projectView.put("planStartDate", formatDate(startDate));
        projectView.put("planEndDate", formatDate(endDate));
        projectView.put("projectType", project.getProjectType());
        projectView.put("accountingMode", accountingMode);
        projectView.put("managementMode", project.getManagementMode());
        projectView.put("priority", project.getPriority());
        projectView.put("budgetLimit", project.getBudgetLimit());
        projectView.put("baseCurrency", project.getBaseCurrency());
        projectView.put("executionSource", project.getExecutionSource());
        prepared.put("actionRequestId", row.get("actionRequestId"));
        prepared.put("status", "PENDING");
        prepared.put("actionCode", "CREATE_PROJECT");
        prepared.put("riskLevel", "CONFIRM_REQUIRED");
        prepared.put("confirmationSummary", summary);
        prepared.put("project", projectView);
        return prepared;
    }

    private Map<String, Object> projectActionPayload(BusinessProject project)
    {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("projectName", project.getProjectName());
        payload.put("companyDeptId", project.getCompanyDeptId());
        payload.put("mainOwnerUserId", project.getMainOwnerUserId());
        payload.put("objective", project.getObjective());
        payload.put("planStartDate", formatDate(project.getPlanStartDate()));
        payload.put("planEndDate", formatDate(project.getPlanEndDate()));
        payload.put("projectType", project.getProjectType());
        payload.put("accountingMode", project.getAccountingMode());
        payload.put("managementMode", project.getManagementMode());
        payload.put("priority", project.getPriority());
        payload.put("baseCurrency", project.getBaseCurrency());
        payload.put("budgetLimit", project.getBudgetLimit());
        payload.put("executionSource", project.getExecutionSource());
        return payload;
    }

    private Map<String, Object> recordActionTool(Long runId, Long conversationId, Long userId,
        Map<String, Object> prepared)
    {
        Map<String, Object> auditData = new LinkedHashMap<String, Object>();
        auditData.put("ready", prepared.get("ready"));
        auditData.put("missingFields", prepared.get("missingFields"));
        auditData.put("actionRequestId", prepared.get("actionRequestId"));
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId);
        row.put("conversationId", conversationId);
        row.put("userId", userId);
        row.put("toolCode", "boss_prepare_project_create");
        row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("inputJson", "{}");
        row.put("outputJson", toJson(auditData));
        row.put("status", "SUCCEEDED");
        row.put("startedTime", new Date());
        row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", "boss_prepare_project_create");
        result.put("label", "项目立项确认单");
        result.put("riskLevel", "CONFIRM_REQUIRED");
        result.put("data", prepared);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> prepareProjectTransition(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        List<Object> projects = dashboard.get("projects") instanceof List
            ? (List<Object>) dashboard.get("projects") : Collections.emptyList();
        Long requestedId = longValue(arguments.get("projectId"));
        String requestedName = text(arguments, "projectName");
        String contextText = text(arguments, "contextText");
        String transitionAction = upper(arguments.get("transitionAction"));
        if (!"RESUME_PROJECT".equals(transitionAction)) transitionAction = "START_PLANNING";
        String requiredStatus = "RESUME_PROJECT".equals(transitionAction) ? "PAUSED" : "DRAFT";
        String toStatus = "RESUME_PROJECT".equals(transitionAction) ? "ACTIVE" : "PLANNING";
        List<BusinessProject> available = new ArrayList<BusinessProject>();
        for (Object item : projects)
        {
            try { available.add(item instanceof BusinessProject ? (BusinessProject) item : objectMapper.convertValue(item, BusinessProject.class)); }
            catch (Exception ignored) { }
        }
        if (requestedId == null && StringUtils.isBlank(requestedName) && StringUtils.isNotBlank(contextText))
            for (BusinessProject project : available)
                if (contextText.contains(project.getProjectName())) requestedName = project.getProjectName();
        List<BusinessProject> matches = new ArrayList<BusinessProject>();
        List<BusinessProject> namedProjects = new ArrayList<BusinessProject>();
        for (BusinessProject project : available)
        {
            boolean selected = requestedId != null && requestedId.equals(project.getProjectId());
            if (!selected && requestedId == null && StringUtils.isNotBlank(requestedName))
                selected = project.getProjectName().equalsIgnoreCase(requestedName)
                    || project.getProjectName().contains(requestedName) || requestedName.contains(project.getProjectName());
            if (selected) namedProjects.add(project);
            if (!requiredStatus.equals(project.getStatus())) continue;
            if (selected || (requestedId == null && StringUtils.isBlank(requestedName))) matches.add(project);
        }
        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("actionCode", "PROJECT_TRANSITION");
        prepared.put("ready", matches.size() == 1);
        if (matches.size() != 1)
        {
            List<String> names = new ArrayList<String>();
            for (BusinessProject project : matches) names.add(project.getProjectName());
            String message;
            if (matches.isEmpty() && namedProjects.size() == 1)
            {
                BusinessProject current = namedProjects.get(0);
                if ("PLANNING".equals(current.getStatus()) && "SUBMITTED".equals(current.getBaselineStatus()))
                    message = "项目“" + current.getProjectName() + "”已经完成规划并提交计划，现在等待老板审核，不需要再次推进";
                else if ("PLANNING".equals(current.getStatus()))
                    message = "项目“" + current.getProjectName() + "”已经处于规划中，下一步由负责人完善并提交计划，不需要再次推进";
                else if ("ACTIVE".equals(current.getStatus()))
                    message = "项目“" + current.getProjectName() + "”已经进入执行，不需要再次推进规划";
                else message = "项目“" + current.getProjectName() + "”当前状态不能进入规划";
            }
            else if ("RESUME_PROJECT".equals(transitionAction))
                message = matches.isEmpty() ? "没有找到可恢复执行的暂停项目" : "请说明要恢复哪个暂停项目：" + join(names, "、");
            else message = matches.isEmpty() ? "没有找到可进入规划的草稿项目" : "请说明要推进哪个草稿项目：" + join(names, "、");
            prepared.put("missingFields", Collections.singletonList(message));
            return prepared;
        }
        BusinessProject project = matches.get(0);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("projectId", project.getProjectId());
        payload.put("projectNo", project.getProjectNo());
        payload.put("projectName", project.getProjectName());
        payload.put("mainOwnerName", project.getMainOwnerName());
        payload.put("fromStatus", requiredStatus);
        payload.put("toStatus", toStatus);
        payload.put("transitionAction", transitionAction);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId);
        row.put("conversationId", conversationId);
        row.put("userId", userId);
        row.put("actionCode", "PROJECT_TRANSITION");
        row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("actionPayloadJson", toJson(payload));
        row.put("confirmationSummary", "RESUME_PROJECT".equals(transitionAction)
            ? "项目“" + project.getProjectName() + "”恢复执行，由负责人“" + project.getMainOwnerName() + "”继续推进"
            : "项目“" + project.getProjectName() + "”进入规划，由负责人“" + project.getMainOwnerName() + "”开始拆解工作计划");
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);
        prepared.put("actionRequestId", row.get("actionRequestId"));
        prepared.put("status", "PENDING");
        prepared.put("actionCode", "PROJECT_TRANSITION");
        prepared.put("riskLevel", "CONFIRM_REQUIRED");
        prepared.put("confirmationSummary", row.get("confirmationSummary"));
        prepared.put("project", payload);
        return prepared;
    }

    private Map<String, Object> recordTransitionTool(Long runId, Long conversationId, Long userId,
        Map<String, Object> prepared)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("toolCode", "boss_prepare_project_transition"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("inputJson", "{}"); row.put("outputJson", toJson(prepared)); row.put("status", "SUCCEEDED");
        row.put("startedTime", new Date()); row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", "boss_prepare_project_transition");
        result.put("label", "项目进入规划确认单");
        result.put("riskLevel", "CONFIRM_REQUIRED");
        result.put("data", prepared);
        return result;
    }

    private Map<String, Object> planReviewTool(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> data = loadPlanReview(userId, viewAll, arguments);
        return recordTool(runId, conversationId, userId, "boss_project_plan_review", "待审批计划审核", data,
            "/business/project/detail");
    }

    private Map<String, Object> preparePlanDecision(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> review = loadPlanReview(userId, viewAll, arguments);
        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("actionCode", "PROJECT_PLAN_DECISION");
        if (!Boolean.TRUE.equals(review.get("ready")))
        {
            prepared.putAll(review);
            return prepared;
        }
        String decision = upper(arguments.get("decision"));
        String returnReason = text(arguments, "returnReason");
        List<String> missing = new ArrayList<String>();
        if (!"APPROVE".equals(decision) && !"RETURN".equals(decision))
            missing.add("请明确是批准启动，还是退回负责人调整");
        if ("RETURN".equals(decision) && StringUtils.isBlank(returnReason))
            missing.add("退回原因和需要负责人修改的内容");
        prepared.put("ready", missing.isEmpty());
        prepared.put("missingFields", missing);
        prepared.put("planReview", review);
        if (!missing.isEmpty()) return prepared;

        @SuppressWarnings("unchecked") Map<String, Object> project = (Map<String, Object>) review.get("project");
        String transitionAction = "APPROVE".equals(decision) ? "CONFIRM_BASELINE" : "RETURN_PLAN";
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("projectId", project.get("projectId"));
        payload.put("projectNo", project.get("projectNo"));
        payload.put("projectName", project.get("projectName"));
        payload.put("mainOwnerName", project.get("mainOwnerName"));
        payload.put("fromStatus", "PLANNING");
        payload.put("toStatus", "APPROVE".equals(decision) ? "ACTIVE" : "PLANNING");
        payload.put("transitionAction", transitionAction);
        payload.put("decision", decision);
        payload.put("returnReason", returnReason);
        payload.put("planReview", review);

        String summary = "APPROVE".equals(decision)
            ? "批准项目“" + project.get("projectName") + "”的负责人计划并进入执行"
            : "将项目“" + project.get("projectName") + "”的计划退回负责人调整：" + returnReason;
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("actionCode", "PROJECT_PLAN_DECISION"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("actionPayloadJson", toJson(payload)); row.put("confirmationSummary", summary);
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);

        prepared.put("actionRequestId", row.get("actionRequestId"));
        prepared.put("status", "PENDING"); prepared.put("actionCode", "PROJECT_PLAN_DECISION");
        prepared.put("riskLevel", "CONFIRM_REQUIRED"); prepared.put("confirmationSummary", summary);
        prepared.put("project", payload);
        return prepared;
    }

    private Map<String, Object> recordPlanDecisionTool(Long runId, Long conversationId, Long userId,
        Map<String, Object> prepared)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("toolCode", "boss_prepare_plan_decision"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("inputJson", "{}"); row.put("outputJson", toJson(prepared)); row.put("status", "SUCCEEDED");
        row.put("startedTime", new Date()); row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", "boss_prepare_plan_decision"); result.put("label", "项目计划审批确认单");
        result.put("riskLevel", "CONFIRM_REQUIRED"); result.put("data", prepared);
        return result;
    }

    private Map<String, Object> acceptanceReviewTool(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> data = loadAcceptanceReview(userId, viewAll, arguments);
        return recordTool(runId, conversationId, userId, "boss_project_acceptance_review", "待验收资料审核", data,
            "/business/project/detail/acceptance");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadAcceptanceReview(Long userId, boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        List<Object> decisions = dashboard.get("decisions") instanceof List
            ? (List<Object>) dashboard.get("decisions") : Collections.emptyList();
        Long requestedId = longValue(arguments.get("projectId"));
        String requestedName = text(arguments, "projectName");
        List<BusinessProject> matches = new ArrayList<BusinessProject>();
        for (Object item : decisions)
        {
            BusinessProject project;
            try { project = item instanceof BusinessProject ? (BusinessProject) item : objectMapper.convertValue(item, BusinessProject.class); }
            catch (Exception ignored) { continue; }
            if (!"ACCEPTANCE".equals(project.getStatus())) continue;
            if (requestedId != null && requestedId.equals(project.getProjectId())) matches.add(project);
            else if (requestedId == null && StringUtils.isNotBlank(requestedName)
                && (project.getProjectName().equalsIgnoreCase(requestedName) || project.getProjectName().contains(requestedName)
                    || requestedName.contains(project.getProjectName()))) matches.add(project);
            else if (requestedId == null && StringUtils.isBlank(requestedName)) matches.add(project);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ready", matches.size() == 1);
        if (matches.size() != 1)
        {
            List<String> names = new ArrayList<String>();
            for (BusinessProject project : matches) names.add(project.getProjectName());
            result.put("missingFields", Collections.singletonList(matches.isEmpty()
                ? "当前没有负责人已提交、等待老板验收的项目"
                : "请说明要审核哪个项目的验收资料：" + join(names, "、")));
            result.put("candidateProjects", names);
            return result;
        }

        BusinessProject detail = projectService.getProject(matches.get(0).getProjectId(), userId, viewAll, true);
        BusinessProjectAcceptance acceptance = latestPendingAcceptance(detail);
        if (acceptance == null)
        {
            result.put("ready", false);
            result.put("missingFields", Collections.singletonList("项目当前没有待审核的验收提交，请刷新待办后重试"));
            return result;
        }
        Map<String, Object> project = new LinkedHashMap<String, Object>();
        project.put("projectId", detail.getProjectId()); project.put("projectNo", detail.getProjectNo());
        project.put("projectName", detail.getProjectName()); project.put("companyName", detail.getCompanyName());
        project.put("mainOwnerName", detail.getMainOwnerName()); project.put("objective", detail.getObjective());
        project.put("status", detail.getStatus()); project.put("managementMode", detail.getManagementMode());
        result.put("project", project);

        Map<String, Object> submission = new LinkedHashMap<String, Object>();
        submission.put("acceptanceId", acceptance.getAcceptanceId());
        submission.put("submissionVersion", acceptance.getSubmissionVersion());
        submission.put("resultSummary", acceptance.getResultSummary());
        submission.put("deliverables", acceptance.getDeliverables());
        submission.put("attachmentUrls", acceptance.getAttachmentUrls());
        submission.put("submittedUserName", acceptance.getSubmittedUserName());
        submission.put("submittedTime", acceptance.getSubmittedTime());
        submission.put("reviewStatus", acceptance.getReviewStatus());
        result.put("acceptance", submission);

        int taskCount = detail.getTasks() == null ? 0 : detail.getTasks().size();
        int completedTaskCount = 0;
        if (detail.getTasks() != null) for (BusinessProjectTask task : detail.getTasks())
            if ("DONE".equals(task.getStatus())) completedTaskCount++;
        int milestoneCount = detail.getMilestones() == null ? 0 : detail.getMilestones().size();
        int completedMilestoneCount = 0;
        if (detail.getMilestones() != null) for (BusinessProjectMilestone milestone : detail.getMilestones())
            if ("DONE".equals(milestone.getStatus())) completedMilestoneCount++;
        int openHighRiskCount = 0;
        if (detail.getRisks() != null) for (BusinessProjectRisk risk : detail.getRisks())
            if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                openHighRiskCount++;
        int attachmentCount = splitAttachments(acceptance.getAttachmentUrls()).size();
        boolean canApprove = taskCount > 0 && completedTaskCount == taskCount
            && completedMilestoneCount == milestoneCount && openHighRiskCount == 0;
        List<String> checks = new ArrayList<String>();
        checks.add("已提交第 " + acceptance.getSubmissionVersion() + " 版验收资料");
        checks.add("一次性任务已完成 " + completedTaskCount + "/" + taskCount + " 项");
        if (milestoneCount > 0) checks.add("里程碑已完成 " + completedMilestoneCount + "/" + milestoneCount + " 项");
        checks.add("交付凭证 " + attachmentCount + " 份");
        List<String> warnings = new ArrayList<String>();
        if (taskCount == 0) warnings.add("项目没有可核验的一次性任务，暂不满足通过条件");
        else if (completedTaskCount < taskCount) warnings.add("仍有 " + (taskCount - completedTaskCount) + " 项一次性任务未完成");
        if (completedMilestoneCount < milestoneCount) warnings.add("仍有 " + (milestoneCount - completedMilestoneCount) + " 个里程碑未完成");
        if (openHighRiskCount > 0) warnings.add("仍有 " + openHighRiskCount + " 项未关闭的高风险或严重风险");
        if (attachmentCount == 0) warnings.add("负责人没有上传交付凭证，建议先核对结果说明和交付物");
        result.put("taskCount", taskCount); result.put("completedTaskCount", completedTaskCount);
        result.put("milestoneCount", milestoneCount); result.put("completedMilestoneCount", completedMilestoneCount);
        result.put("openHighRiskCount", openHighRiskCount); result.put("attachmentCount", attachmentCount);
        result.put("attachmentList", splitAttachments(acceptance.getAttachmentUrls()));
        result.put("canApprove", canApprove); result.put("checks", checks); result.put("warnings", warnings);
        result.put("recommendation", canApprove ? "系统前置条件已通过，请老板核对成果内容与凭证后决定是否验收"
            : "当前不满足验收通过条件，可以退回负责人补充或完成剩余事项");
        return result;
    }

    private Map<String, Object> prepareAcceptanceDecision(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> review = loadAcceptanceReview(userId, viewAll, arguments);
        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("actionCode", "PROJECT_ACCEPTANCE_DECISION");
        if (!Boolean.TRUE.equals(review.get("ready")))
        {
            prepared.putAll(review);
            return prepared;
        }
        String rawDecision = upper(arguments.get("decision"));
        String decision = "APPROVE".equals(rawDecision) || "APPROVED".equals(rawDecision) ? "APPROVED"
            : "RETURN".equals(rawDecision) || "RETURNED".equals(rawDecision) ? "RETURNED" : "";
        String returnReason = text(arguments, "returnReason");
        List<String> missing = new ArrayList<String>();
        if (StringUtils.isBlank(decision)) missing.add("请明确是验收通过，还是退回负责人补充");
        if ("APPROVED".equals(decision) && !Boolean.TRUE.equals(review.get("canApprove")))
            missing.add("项目当前不满足验收通过条件，请先处理审核卡中的提示");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(returnReason))
            missing.add("退回原因和需要负责人补充的内容");
        prepared.put("ready", missing.isEmpty()); prepared.put("missingFields", missing);
        prepared.put("acceptanceReview", review);
        if (!missing.isEmpty()) return prepared;

        Map<String, Object> project = mapFields(review.get("project"), "projectId", "projectNo", "projectName",
            "mainOwnerName", "companyName");
        Map<String, Object> acceptance = mapFields(review.get("acceptance"), "acceptanceId", "submissionVersion");
        Map<String, Object> payload = new LinkedHashMap<String, Object>(project);
        payload.putAll(acceptance); payload.put("fromStatus", "ACCEPTANCE");
        payload.put("toStatus", "APPROVED".equals(decision) ? "CLOSED" : "ACTIVE");
        payload.put("decision", decision); payload.put("comment", returnReason);
        payload.put("acceptanceReview", review);
        String summary = "APPROVED".equals(decision)
            ? "通过项目“" + project.get("projectName") + "”第 " + acceptance.get("submissionVersion") + " 版验收并结项"
            : "退回项目“" + project.get("projectName") + "”的验收资料：" + returnReason;
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("actionCode", "PROJECT_ACCEPTANCE_DECISION"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("actionPayloadJson", toJson(payload)); row.put("confirmationSummary", summary);
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);
        prepared.put("actionRequestId", row.get("actionRequestId")); prepared.put("status", "PENDING");
        prepared.put("riskLevel", "CONFIRM_REQUIRED"); prepared.put("confirmationSummary", summary);
        prepared.put("project", payload);
        return prepared;
    }

    private Map<String, Object> recordAcceptanceDecisionTool(Long runId, Long conversationId, Long userId,
        Map<String, Object> prepared)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("toolCode", "boss_prepare_acceptance_decision"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("inputJson", "{}"); row.put("outputJson", toJson(prepared)); row.put("status", "SUCCEEDED");
        row.put("startedTime", new Date()); row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", "boss_prepare_acceptance_decision"); result.put("label", "项目验收决定确认单");
        result.put("riskLevel", "CONFIRM_REQUIRED"); result.put("data", prepared);
        return result;
    }

    private BusinessProjectAcceptance latestPendingAcceptance(BusinessProject project)
    {
        BusinessProjectAcceptance latest = null;
        if (project == null || project.getAcceptances() == null) return null;
        for (BusinessProjectAcceptance item : project.getAcceptances())
        {
            if (item == null || item.getAcceptanceId() == null || !"PENDING".equals(item.getReviewStatus())) continue;
            if (latest == null || integer(item.getSubmissionVersion()) > integer(latest.getSubmissionVersion())
                || (integer(item.getSubmissionVersion()) == integer(latest.getSubmissionVersion())
                    && item.getAcceptanceId().longValue() > latest.getAcceptanceId().longValue())) latest = item;
        }
        return latest;
    }

    private List<String> splitAttachments(String value)
    {
        if (StringUtils.isBlank(value)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String item : value.split("[,;\\n\\r]+"))
            if (StringUtils.isNotBlank(StringUtils.trim(item))) result.add(StringUtils.trim(item));
        return result;
    }

    private Map<String, Object> prepareBudgetAdjustment(Long runId, Long conversationId, Long userId,
        boolean viewAll, Map<String, Object> arguments)
    {
        List<BusinessProject> projects = accessibleProjects(userId, viewAll);
        BusinessProject project = matchProject(projects, arguments);
        if (project == null && StringUtils.isNotBlank(text(arguments, "contextText")))
        {
            String context = text(arguments, "contextText");
            for (BusinessProject candidate : projects)
                if (context.contains(candidate.getProjectName()))
                {
                    if (project != null) { project = null; break; }
                    project = candidate;
                }
        }
        BigDecimal newBudget = decimal(arguments.get("budgetLimit"));
        String reason = text(arguments, "reason");
        List<String> missing = new ArrayList<String>();
        if (project == null) missing.add(projects.isEmpty() ? "当前没有可调整预算的项目"
            : "要调整预算的项目名称");
        if (newBudget == null || newBudget.compareTo(BigDecimal.ZERO) < 0) missing.add("新的预算上限");
        if (StringUtils.isBlank(reason)) missing.add("调整预算的原因");
        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("ready", missing.isEmpty());
        prepared.put("actionCode", "BUDGET_ADJUSTMENT");
        prepared.put("missingFields", missing);
        prepared.put("candidateProjects", projectNames(projects));
        if (!missing.isEmpty()) return prepared;

        String currency = defaultValue(upper(arguments.get("currency")), project.getBaseCurrency());
        if (StringUtils.isBlank(currency)) currency = "CNY";
        if (currency.length() != 3)
        {
            prepared.put("ready", false);
            prepared.put("missingFields", Collections.singletonList("三位币种代码，例如 CNY 或 VND"));
            return prepared;
        }
        Map<String, Object> payload = projectIdentity(project);
        payload.put("oldBudgetLimit", project.getBudgetLimit());
        payload.put("budgetLimit", newBudget);
        payload.put("currency", currency);
        payload.put("reason", reason);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("actionCode", "BUDGET_ADJUSTMENT"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("actionPayloadJson", toJson(payload));
        row.put("confirmationSummary", "将项目“" + project.getProjectName() + "”的预算由 "
            + money(project.getBudgetLimit()) + " 调整为 " + money(newBudget) + " " + currency + "，原因：" + reason);
        row.put("expireTime", new Date(System.currentTimeMillis() + 30L * 60L * 1000L));
        mapper.insertActionRequest(row);
        prepared.put("actionRequestId", row.get("actionRequestId"));
        prepared.put("status", "PENDING"); prepared.put("riskLevel", "CONFIRM_REQUIRED");
        prepared.put("confirmationSummary", row.get("confirmationSummary")); prepared.put("project", payload);
        return prepared;
    }

    private Map<String, Object> recordBudgetAdjustmentTool(Long runId, Long conversationId, Long userId,
        Map<String, Object> prepared)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId); row.put("conversationId", conversationId); row.put("userId", userId);
        row.put("toolCode", "boss_prepare_budget_adjustment"); row.put("riskLevel", "CONFIRM_REQUIRED");
        row.put("inputJson", "{}"); row.put("outputJson", toJson(prepared)); row.put("status", "SUCCEEDED");
        row.put("startedTime", new Date()); row.put("finishedTime", new Date());
        mapper.insertToolCall(row);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("toolCode", "boss_prepare_budget_adjustment"); result.put("label", "项目预算调整确认单");
        result.put("riskLevel", "CONFIRM_REQUIRED"); result.put("data", prepared);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPlanReview(Long userId, boolean viewAll, Map<String, Object> arguments)
    {
        Map<String, Object> dashboard = projectService.dashboard(userId, viewAll, true);
        List<Object> decisions = dashboard.get("decisions") instanceof List
            ? (List<Object>) dashboard.get("decisions") : Collections.emptyList();
        Long requestedId = longValue(arguments.get("projectId"));
        String requestedName = text(arguments, "projectName");
        List<BusinessProject> matches = new ArrayList<BusinessProject>();
        for (Object item : decisions)
        {
            BusinessProject project;
            try { project = item instanceof BusinessProject ? (BusinessProject) item : objectMapper.convertValue(item, BusinessProject.class); }
            catch (Exception ignored) { continue; }
            if (!"PLANNING".equals(project.getStatus()) || !"SUBMITTED".equals(project.getBaselineStatus())) continue;
            if (requestedId != null && requestedId.equals(project.getProjectId())) matches.add(project);
            else if (requestedId == null && StringUtils.isNotBlank(requestedName)
                && (project.getProjectName().equalsIgnoreCase(requestedName) || project.getProjectName().contains(requestedName)
                    || requestedName.contains(project.getProjectName()))) matches.add(project);
            else if (requestedId == null && StringUtils.isBlank(requestedName)) matches.add(project);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ready", matches.size() == 1);
        if (matches.size() != 1)
        {
            List<String> names = new ArrayList<String>();
            for (BusinessProject project : matches) names.add(project.getProjectName());
            result.put("missingFields", Collections.singletonList(matches.isEmpty()
                ? "当前没有已提交、等待老板审批的项目计划"
                : "请说明要审核哪个项目计划：" + join(names, "、")));
            result.put("candidateProjects", names);
            return result;
        }

        BusinessProject detail = projectService.getProject(matches.get(0).getProjectId(), userId, viewAll, true);
        Map<String, Object> operating = projectService.operatingConfig(detail.getProjectId(), userId, viewAll, true);
        Map<String, Object> project = new LinkedHashMap<String, Object>();
        project.put("projectId", detail.getProjectId()); project.put("projectNo", detail.getProjectNo());
        project.put("projectName", detail.getProjectName()); project.put("companyName", detail.getCompanyName());
        project.put("mainOwnerName", detail.getMainOwnerName()); project.put("objective", detail.getObjective());
        project.put("planStartDate", formatDate(detail.getPlanStartDate())); project.put("planEndDate", formatDate(detail.getPlanEndDate()));
        project.put("budgetLimit", detail.getBudgetLimit()); project.put("baseCurrency", detail.getBaseCurrency());
        project.put("accountingMode", detail.getAccountingMode()); project.put("managementMode", detail.getManagementMode());
        result.put("project", project);
        result.put("members", copyFields(detail.getMembers(), "userId", "userNameSnapshot", "memberRole", "joinedDate"));
        result.put("tasks", copyFields(detail.getTasks(), "taskId", "taskName", "assigneeName", "priority", "planStartDate", "dueDate", "remark"));
        result.put("routines", copyFields(detail.getRoutines(), "routineId", "routineName", "frequency", "targetValue", "unit",
            "assigneeName", "startDate", "endDate", "evidenceRequired", "remark"));
        result.put("milestones", copyFields(detail.getMilestones(), "milestoneId", "milestoneName", "planDate", "weight", "remark"));
        result.put("risks", copyFields(detail.getRisks(), "riskId", "riskTitle", "severity", "probability", "ownerName", "dueDate", "status", "responsePlan"));
        result.put("kpis", copyFields(operating.get("kpis"), "kpiId", "kpiName", "targetValue", "unit", "periodType", "ownerName", "weight"));
        result.put("staffAllocations", copyFields(operating.get("staffAllocations"), "userId", "userName", "allocationMode",
            "allocationValue", "effectiveFrom", "effectiveTo"));

        List<String> checks = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        int taskCount = detail.getTasks() == null ? 0 : detail.getTasks().size();
        int routineCount = detail.getRoutines() == null ? 0 : detail.getRoutines().size();
        int memberCount = detail.getMembers() == null ? 0 : detail.getMembers().size();
        int kpiCount = operating.get("kpis") instanceof List ? ((List<Object>) operating.get("kpis")).size() : 0;
        int allocationCount = operating.get("staffAllocations") instanceof List ? ((List<Object>) operating.get("staffAllocations")).size() : 0;
        checks.add("项目目标和计划周期已填写");
        checks.add("项目计划基线已提交");
        checks.add("已安排 " + routineCount + " 项持续工作、" + taskCount + " 项一次性任务、" + memberCount + " 名参项人员");
        if (taskCount == 0 && routineCount == 0) warnings.add("没有可执行的持续工作或一次性任务");
        if (detail.getTasks() != null) for (BusinessProjectTask task : detail.getTasks())
        {
            if (task.getAssigneeUserId() == null) warnings.add("一次性任务“" + task.getTaskName() + "”尚未指定执行人");
            if (task.getDueDate() == null) warnings.add("一次性任务“" + task.getTaskName() + "”尚未设置完成日期");
        }
        if (detail.getRoutines() != null) for (BusinessProjectRoutine routine : detail.getRoutines())
            if (routine.getAssigneeUserId() == null) warnings.add("持续工作“" + routine.getRoutineName() + "”尚未指定执行人");
        if (kpiCount == 0) warnings.add("尚未设置项目 KPI，可根据项目需要后续补充");
        if (allocationCount == 0) warnings.add("尚未设置成员计划投入，人员成本暂时无法按计划分摊");
        if (detail.getRisks() != null) for (BusinessProjectRisk risk : detail.getRisks())
            if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                warnings.add("存在未关闭的高风险：“" + risk.getRiskTitle() + "”");
        result.put("checks", checks); result.put("warnings", warnings);
        result.put("taskCount", taskCount); result.put("routineCount", routineCount); result.put("memberCount", memberCount);
        result.put("kpiCount", kpiCount); result.put("allocationCount", allocationCount);
        result.put("recommendation", warnings.isEmpty() ? "计划要素完整，可以考虑批准启动" : "计划可以审批，但建议先确认提示项是否可接受");
        return result;
    }

    private Map<String, Object> actionView(Map<String, Object> prepared)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("actionRequestId", prepared.get("actionRequestId"));
        result.put("actionCode", prepared.get("actionCode"));
        result.put("riskLevel", prepared.get("riskLevel"));
        result.put("status", prepared.get("status"));
        result.put("confirmationSummary", prepared.get("confirmationSummary"));
        result.put("project", prepared.get("project"));
        return result;
    }

    private List<String> detectIntents(String question)
    {
        List<String> result = new ArrayList<String>();
        boolean broad = containsAny(question, "今天怎么样", "今日怎么样", "公司情况", "经营概况", "整体情况", "晨报");
        if (broad || containsAny(question, "经营", "收入", "成本", "利润", "亏损", "盈亏", "收支", "赚钱", "赚了",
            "预算", "超支", "花费", "支出")) result.add("ACCOUNTING");
        if (broad || containsAny(question, "项目", "进度", "风险", "逾期")) result.add("PROJECTS");
        if (broad || containsAny(question, "待办", "待处理", "要我", "审批", "确认", "决策")) result.add("PENDING");
        if (containsAny(question, "人员", "员工", "人力", "团队", "主播")) result.add("STAFF");
        return result;
    }

    private boolean plannedToolsAllowed(Map<String, Object> plan)
    {
        return plannedToolsAllowed(plan, null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> withoutProjectDraftTools(List<Map<String, Object>> definitions)
    {
        if (definitions == null || definitions.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> wrapper : definitions)
        {
            Object function = wrapper == null ? null : wrapper.get("function");
            String name = function instanceof Map
                ? stringValue(((Map<String, Object>) function).get("name")) : "";
            if (!name.startsWith("capability_project_draft_")) result.add(wrapper);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> withoutTool(List<Map<String, Object>> definitions, String excludedName)
    {
        if (definitions == null || definitions.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> wrapper : definitions)
        {
            Object function = wrapper == null ? null : wrapper.get("function");
            String name = function instanceof Map
                ? stringValue(((Map<String, Object>) function).get("name")) : "";
            if (!excludedName.equals(name)) result.add(wrapper);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> selectedCapabilityCodes(Map<String, Object> plan, AiExecutionContext context)
    {
        Object calls = plan == null ? null : plan.get("toolCalls");
        if (!(calls instanceof List)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (Object item : (List<Object>) calls)
        {
            if (!(item instanceof Map)) continue;
            String toolName = stringValue(((Map<String, Object>) item).get("name"));
            String code = toolName;
            if (capabilityToolCatalog != null && context != null
                && capabilityToolCatalog.findAllowedByToolName(toolName, context) != null)
                code = capabilityToolCatalog.findAllowedByToolName(toolName, context).code();
            else if (toolName.startsWith("capability_"))
                code = toolName.substring("capability_".length()).replace('_', '.');
            if (StringUtils.isNotBlank(code) && !result.contains(code)) result.add(code);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String firstSelection(Map<String, Object> decisionTrace)
    {
        Object value = decisionTrace == null ? null : decisionTrace.get("modelSelection");
        if (!(value instanceof List) || ((List<Object>) value).isEmpty()) return "NO_MODEL_ROUTE";
        return stringValue(((List<Object>) value).get(0));
    }

    @SuppressWarnings("unchecked")
    private boolean safeConversationSelected(Map<String, Object> decisionTrace)
    {
        Object value = decisionTrace == null ? null : decisionTrace.get("modelSelection");
        if (!(value instanceof List)) return false;
        for (Object item : (List<Object>) value)
            if ("conversation.safe.respond".equals(stringValue(item))) return true;
        return false;
    }

    private Map<String, Object> completeDecisionTrace(Map<String, Object> source,
        Map<String, Object> workflow, Long runId, String traceId, String executionMode, boolean modelEnabled)
    {
        if ((source == null || source.isEmpty()) && !modelEnabled) return Collections.emptyMap();
        Map<String, Object> result = source == null
            ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
        result.put("runId", runId);
        result.put("traceId", traceId);
        result.put("executionMode", executionMode);
        result.put("provider", modelEnabled ? modelClient.providerCode() : "LOCAL");
        result.put("model", modelEnabled ? modelClient.modelName() : SAFE_ROUTER);
        if (workflow != null && "CREATE_PROJECT".equals(stringValue(workflow.get("workflowCode"))))
        {
            Map<String, Object> view = workflowView(workflow);
            result.put("missingFields", view.get("missingFields"));
            if (StringUtils.isBlank(stringValue(result.get("finalRoute"))))
                result.put("finalRoute", "CREATE_PROJECT_WORKFLOW");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean plannedToolsAllowed(Map<String, Object> plan, AiExecutionContext context)
    {
        Object calls = plan == null ? null : plan.get("toolCalls");
        if (!(calls instanceof List)) return true;
        for (Object item : (List<Object>) calls)
        {
            if (!(item instanceof Map)) return false;
            String name = stringValue(((Map<String, Object>) item).get("name"));
            if (capabilityToolCatalog != null && context != null)
            {
                if (capabilityToolCatalog.isAllowedToolName(name, context)) continue;
                return false;
            }
            if (toolIntent(name) == null
                && !"boss_prepare_project_transition".equals(name) && !"boss_prepare_budget_adjustment".equals(name)
                && !"boss_project_acceptance_review".equals(name)
                && !"boss_prepare_acceptance_decision".equals(name)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> completeCapabilityRun(String question, Map<String, Object> outcome,
        Long conversationId, Long runId, String traceId, Long userId, String userName, boolean viewAll,
        AiExecutionContext context, boolean modelEnabled, Long requestMessageId,
        Map<String, Object> decisionTrace)
    {
        String answer = cleanModelText(stringValue(outcome.get("content")));
        if (StringUtils.isBlank(answer)) answer = "已按你的要求更新系统草稿。";
        List<Map<String, Object>> toolResults = outcome.get("toolResults") instanceof List
            ? (List<Map<String, Object>>) outcome.get("toolResults")
            : new ArrayList<Map<String, Object>>();
        Map<String, Object> latestWorkflow = mapper.selectActiveWorkflow(conversationId, userId);
        Map<String, Object> actionRequest = null;
        if (latestWorkflow != null && hasCapabilityResult(toolResults, "project.draft.update"))
        {
            Map<String, Object> draft = workflowDraft(latestWorkflow);
            Map<String, Object> prepared = prepareProjectAction(runId, conversationId, userId, draft);
            latestWorkflow = persistProjectWorkflow(latestWorkflow, conversationId, userId,
                requestMessageId, draft, prepared);
            toolResults.add(recordActionTool(runId, conversationId, userId, prepared));
            if (Boolean.TRUE.equals(prepared.get("ready"))) actionRequest = actionView(prepared);
        }
        Map<String, Object> scope = scope(userId, viewAll);
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("scope", scope);
        metadata.put("sources", sources(toolResults));
        metadata.put("toolCalls", toolReferences(toolResults));
        metadata.put("executionMode", LLM_AGENT);
        metadata.put("provider", modelEnabled ? modelClient.providerCode() : "LOCAL");
        metadata.put("model", modelEnabled ? modelClient.modelName() : SAFE_ROUTER);
        metadata.put("capabilityRounds", outcome.get("rounds"));
        if (outcome.get("answerValidation") != null)
            metadata.put("answerValidation", outcome.get("answerValidation"));
        if (latestWorkflow != null) metadata.put("workflow", workflowView(latestWorkflow));
        Map<String, Object> capabilityAcceptanceReview = capabilityResultData(toolResults,
            "project.acceptance.review");
        if (capabilityAcceptanceReview != null && Boolean.TRUE.equals(capabilityAcceptanceReview.get("ready")))
            metadata.put("acceptanceReview", capabilityAcceptanceReview);
        Map<String, Object> capabilityPlanReview = capabilityResultData(toolResults, "project.plan.review");
        if (capabilityPlanReview != null && Boolean.TRUE.equals(capabilityPlanReview.get("ready")))
            metadata.put("planReview", capabilityPlanReview);
        if (actionRequest == null) actionRequest = capabilityActionRequest(toolResults);
        if (actionRequest != null) metadata.put("actionRequest", actionRequest);
        Map<String, Object> completedDecisionTrace = completeDecisionTrace(decisionTrace, latestWorkflow,
            runId, traceId, LLM_AGENT, modelEnabled);
        if (!completedDecisionTrace.isEmpty()) metadata.put("decisionTrace", completedDecisionTrace);

        Map<String, Object> assistantMessage = message(conversationId, userId, "ASSISTANT", answer,
            toJson(metadata));
        mapper.insertMessage(assistantMessage);
        mapper.finishRun(runId, longValue(assistantMessage.get("messageId")), "SUCCEEDED", null);
        mapper.touchConversation(conversationId);
        audit(traceId, conversationId, runId, userId, userName, "AI_CAPABILITY_COMPLETED",
            "老板 AI 已通过当前账号授权的系统能力完成操作，共调用 " + toolResults.size() + " 个能力", metadata);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("conversationId", conversationId);
        result.put("runId", runId);
        result.put("traceId", traceId);
        result.put("role", "assistant");
        result.put("content", answer);
        result.put("scope", scope);
        result.put("sources", metadata.get("sources"));
        result.put("toolCalls", metadata.get("toolCalls"));
        result.put("executionMode", LLM_AGENT);
        result.put("provider", metadata.get("provider"));
        result.put("model", metadata.get("model"));
        if (metadata.get("answerValidation") != null)
            result.put("answerValidation", metadata.get("answerValidation"));
        if (latestWorkflow != null) result.put("workflow", workflowView(latestWorkflow));
        if (capabilityAcceptanceReview != null && Boolean.TRUE.equals(capabilityAcceptanceReview.get("ready")))
            result.put("acceptanceReview", capabilityAcceptanceReview);
        if (capabilityPlanReview != null && Boolean.TRUE.equals(capabilityPlanReview.get("ready")))
            result.put("planReview", capabilityPlanReview);
        if (actionRequest != null) result.put("actionRequest", actionRequest);
        if (!completedDecisionTrace.isEmpty()) result.put("decisionTrace", completedDecisionTrace);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capabilityResultData(List<Map<String, Object>> toolResults, String code)
    {
        if (toolResults == null) return null;
        for (int i = toolResults.size() - 1; i >= 0; i--)
        {
            Map<String, Object> item = toolResults.get(i);
            if (code.equals(stringValue(item.get("toolCode"))) && item.get("data") instanceof Map)
                return (Map<String, Object>) item.get("data");
        }
        return null;
    }

    private boolean hasCapabilityResult(List<Map<String, Object>> toolResults, String code)
    {
        if (toolResults == null) return false;
        for (Map<String, Object> result : toolResults)
            if (code.equals(stringValue(result.get("toolCode")))) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capabilityActionRequest(List<Map<String, Object>> toolResults)
    {
        if (toolResults == null) return null;
        for (int i = toolResults.size() - 1; i >= 0; i--)
        {
            Object value = toolResults.get(i).get("data");
            if (!(value instanceof Map)) continue;
            Map<String, Object> data = (Map<String, Object>) value;
            if (!Boolean.TRUE.equals(data.get("ready")) || data.get("actionRequestId") == null) continue;
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("actionRequestId", data.get("actionRequestId")); result.put("actionCode", data.get("actionCode"));
            result.put("riskLevel", data.get("riskLevel")); result.put("status", data.get("status"));
            result.put("confirmationSummary", data.get("confirmationSummary")); result.put("details", data.get("details"));
            return result;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> intentsFromPlan(Map<String, Object> plan)
    {
        List<String> result = new ArrayList<String>();
        Object calls = plan.get("toolCalls");
        if (!(calls instanceof List)) return result;
        for (Object item : (List<Object>) calls)
        {
            if (!(item instanceof Map)) continue;
            String intent = toolIntent(stringValue(((Map<String, Object>) item).get("name")));
            if (intent != null && !result.contains(intent)) result.add(intent);
        }
        return result;
    }

    private String toolIntent(String toolCode)
    {
        if ("boss_today_accounting".equals(toolCode)) return "ACCOUNTING";
        if ("boss_project_snapshot".equals(toolCode)) return "PROJECTS";
        if ("boss_pending_decisions".equals(toolCode)) return "PENDING";
        if ("boss_staff_snapshot".equals(toolCode)) return "STAFF";
        if ("boss_project_detail".equals(toolCode)) return "PROJECT_DETAIL";
        if ("boss_project_accounting_detail".equals(toolCode)) return "ACCOUNTING_DETAIL";
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean hasToolCalls(Map<String, Object> plan)
    {
        Object calls = plan == null ? null : plan.get("toolCalls");
        return calls instanceof List && !((List<Object>) calls).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private boolean onlyReadOnlyToolCalls(Map<String, Object> plan)
    {
        Object calls = plan == null ? null : plan.get("toolCalls");
        if (!(calls instanceof List)) return false;
        for (Object item : (List<Object>) calls)
        {
            if (!(item instanceof Map)) return false;
            String name = stringValue(((Map<String, Object>) item).get("name"));
            if (toolIntent(name) == null) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> continueReadOnlyAgent(String question, List<Map<String, Object>> history,
        Map<String, Object> initialPlan, List<Map<String, Object>> allToolResults, Long runId,
        Long conversationId, Long userId, boolean viewAll)
    {
        List<Map<String, Object>> turns = new ArrayList<Map<String, Object>>();
        turns.add(agentTurn(initialPlan, modelToolMessages(initialPlan, allToolResults)));
        Set<String> executed = new HashSet<String>();
        rememberToolSignatures(initialPlan, executed);
        for (int round = 0; round < 2; round++)
        {
            Map<String, Object> next = modelClient.continueWithTools(question, history, turns);
            if (next == null) return null;
            if (!plannedToolsAllowed(next)) return null;
            if (!hasToolCalls(next)) return next;
            if (!onlyReadOnlyToolCalls(next)) return null;
            Object value = next.get("toolCalls");
            List<Map<String, Object>> stepResults = new ArrayList<Map<String, Object>>();
            boolean added = false;
            for (Object item : (List<Object>) value)
            {
                if (!(item instanceof Map)) continue;
                Map<String, Object> call = (Map<String, Object>) item;
                String signature = toolSignature(call);
                String name = stringValue(call.get("name"));
                if (executed.contains(signature))
                {
                    Map<String, Object> previous = findToolResult(allToolResults, name);
                    if (previous != null) stepResults.add(previous);
                    continue;
                }
                Map<String, Object> result = executeReadToolCall(call, runId, conversationId, userId, viewAll);
                if (result != null)
                {
                    executed.add(signature);
                    stepResults.add(result);
                    allToolResults.add(result);
                    added = true;
                }
            }
            if (stepResults.isEmpty() || !added) return null;
            turns.add(agentTurn(next, modelToolMessages(next, stepResults)));
        }
        return null;
    }

    private Map<String, Object> executeReadToolCall(Map<String, Object> call, Long runId, Long conversationId,
        Long userId, boolean viewAll)
    {
        String name = stringValue(call.get("name"));
        @SuppressWarnings("unchecked") Map<String, Object> arguments = call.get("arguments") instanceof Map
            ? (Map<String, Object>) call.get("arguments") : new LinkedHashMap<String, Object>();
        if ("boss_today_accounting".equals(name)) return accountingTool(runId, conversationId, userId, viewAll);
        if ("boss_project_snapshot".equals(name)) return projectTool(runId, conversationId, userId, viewAll);
        if ("boss_pending_decisions".equals(name)) return pendingTool(runId, conversationId, userId, viewAll);
        if ("boss_staff_snapshot".equals(name)) return staffTool(runId, conversationId, userId);
        if ("boss_project_detail".equals(name))
            return projectDetailTool(runId, conversationId, userId, viewAll, arguments);
        if ("boss_project_accounting_detail".equals(name))
            return projectAccountingDetailTool(runId, conversationId, userId, viewAll, arguments);
        return null;
    }

    private Map<String, Object> agentTurn(Map<String, Object> plan, List<Map<String, Object>> toolMessages)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("assistantMessageJson", plan.get("assistantMessageJson"));
        result.put("toolMessages", toolMessages);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void rememberToolSignatures(Map<String, Object> plan, Set<String> signatures)
    {
        Object calls = plan == null ? null : plan.get("toolCalls");
        if (!(calls instanceof List)) return;
        for (Object item : (List<Object>) calls)
            if (item instanceof Map) signatures.add(toolSignature((Map<String, Object>) item));
    }

    private String toolSignature(Map<String, Object> call)
    {
        Object arguments = call.get("arguments");
        return stringValue(call.get("name")) + ":" + toJson(arguments == null
            ? Collections.emptyMap() : arguments);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> modelToolMessages(Map<String, Object> plan,
        List<Map<String, Object>> toolResults)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Object calls = plan.get("toolCalls");
        if (!(calls instanceof List) || ((List<Object>) calls).isEmpty())
        {
            for (Map<String, Object> toolResult : toolResults)
            {
                @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) toolResult.get("data");
                Map<String, Object> message = new LinkedHashMap<String, Object>();
                message.put("toolCallId", "");
                message.put("content", toJson(modelToolData(stringValue(toolResult.get("toolCode")), data)));
                result.add(message);
            }
            return result;
        }
        for (Object item : (List<Object>) calls)
        {
            if (!(item instanceof Map)) continue;
            Map<String, Object> call = (Map<String, Object>) item;
            String name = stringValue(call.get("name"));
            Map<String, Object> toolResult = findToolResult(toolResults, name);
            if (toolResult == null) continue;
            Map<String, Object> message = new LinkedHashMap<String, Object>();
            message.put("toolCallId", call.get("toolCallId"));
            @SuppressWarnings("unchecked") Map<String, Object> data =
                (Map<String, Object>) toolResult.get("data");
            message.put("content", toJson(modelToolData(name, data)));
            result.add(message);
        }
        return result;
    }

    private Map<String, Object> findToolResult(List<Map<String, Object>> toolResults, String code)
    {
        for (Map<String, Object> result : toolResults)
            if (code.equals(String.valueOf(result.get("toolCode")))) return result;
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> modelToolData(String code, Map<String, Object> data)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if ("boss_today_accounting".equals(code))
        {
            result.put("bizDate", data.get("bizDate"));
            result.put("today", data.get("today"));
            result.put("draftFactCount", data.get("draftFactCount"));
            result.put("alerts", copyFields(data.get("alerts"), "alertType", "projectName", "amount", "message"));
            result.put("ranking", copyFields(data.get("ranking"), "projectName", "companyName", "revenueAmount",
                "businessCost", "personnelCost", "profitAmount"));
        }
        else if ("boss_project_snapshot".equals(code))
        {
            result.put("summary", data.get("summary"));
            result.put("projects", copyFields(data.get("projects"), "projectId", "projectNo", "projectName", "status",
                "baselineStatus", "companyName", "mainOwnerName", "objective", "accountingMode", "managementMode", "taskCount",
                "completedTaskCount", "openRiskCount", "planStartDate", "planEndDate"));
        }
        else if ("boss_pending_decisions".equals(code))
        {
            result.put("decisionCount", data.get("decisionCount"));
            result.put("taskCount", data.get("taskCount"));
            result.put("decisions", copyFields(data.get("decisions"), "projectId", "projectName", "status", "baselineStatus",
                "companyName", "mainOwnerName", "objective", "planStartDate", "planEndDate", "decisionType",
                "decisionLabel", "nextAction"));
            result.put("tasks", copyFields(data.get("tasks"), "projectId", "projectName", "taskId", "taskName",
                "status", "priority", "dueDate"));
        }
        else if ("boss_staff_snapshot".equals(code))
        {
            result.put("staffCount", data.get("staffCount"));
            result.put("companyCounts", data.get("companyCounts"));
        }
        else if ("boss_project_detail".equals(code) || "boss_project_accounting_detail".equals(code))
            result.putAll(data);
        else if ("boss_prepare_project_create".equals(code) || "boss_prepare_project_transition".equals(code)
            || "boss_project_plan_review".equals(code) || "boss_prepare_plan_decision".equals(code)
            || "boss_prepare_budget_adjustment".equals(code) || "boss_project_acceptance_review".equals(code)
            || "boss_prepare_acceptance_decision".equals(code)) result.putAll(data);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapFields(Object value, String... fields)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (value == null) return result;
        Map<String, Object> source;
        if (value instanceof Map) source = (Map<String, Object>) value;
        else
        {
            try { source = objectMapper.convertValue(value, Map.class); }
            catch (Exception ignored) { return result; }
        }
        for (String field : fields) if (source.containsKey(field)) result.put(field, source.get(field));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyFields(Object value, String... fields)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<Object>) value)
        {
            Map<String, Object> source;
            if (item instanceof Map) source = (Map<String, Object>) item;
            else
            {
                try { source = objectMapper.convertValue(item, Map.class); }
                catch (Exception ignored) { continue; }
            }
            Map<String, Object> target = new LinkedHashMap<String, Object>();
            for (String field : fields) if (source.containsKey(field)) target.put(field, source.get(field));
            result.add(target);
        }
        return result;
    }

    private String projectActionAnswer(Map<String, Object> prepared)
    {
        if (Boolean.TRUE.equals(prepared.get("ready")))
        {
            if ("PROJECT_TRANSITION".equals(stringValue(prepared.get("actionCode"))))
            {
                @SuppressWarnings("unchecked") Map<String, Object> project = (Map<String, Object>) prepared.get("project");
                return "RESUME_PROJECT".equals(stringValue(project.get("transitionAction")))
                    ? "已经按待办清单锁定这个暂停项目。我准备好了“恢复执行”确认单；点击确认后项目才会恢复。"
                    : "这个项目已有完整的公司、负责人和目标，不需要重复填写。我已经准备好“进入规划”确认单；确认后，负责人就可以开始拆解计划。";
            }
            if ("PROJECT_PLAN_DECISION".equals(stringValue(prepared.get("actionCode"))))
            {
                @SuppressWarnings("unchecked") Map<String, Object> project = (Map<String, Object>) prepared.get("project");
                return "APPROVE".equals(stringValue(project.get("decision")))
                    ? "负责人提交的计划已经审核完毕。我已准备好“批准并启动”确认单；点击确认后项目才会正式进入执行。"
                    : "退回要求已经整理好。我已准备好“退回负责人调整”确认单；点击确认后才会正式退回。";
            }
            if ("PROJECT_ACCEPTANCE_DECISION".equals(stringValue(prepared.get("actionCode"))))
            {
                @SuppressWarnings("unchecked") Map<String, Object> project = (Map<String, Object>) prepared.get("project");
                return "APPROVED".equals(stringValue(project.get("decision")))
                    ? "验收资料已经核对完毕。我已准备好“验收通过并结项”确认单；点击确认后项目才会正式结项。"
                    : "退回要求已经整理好。我已准备好“退回验收”确认单；点击确认后才会正式退回负责人补充。";
            }
            if ("BUDGET_ADJUSTMENT".equals(stringValue(prepared.get("actionCode"))))
                return "预算调整信息已经整理好。请检查下面的确认单；只有点击确认后，项目预算才会真正改变。";
            return "资料我已经整理好了。请看下面的确认单：没有问题就点“确认立项”；想修改，直接告诉我要改哪一项。确认前不会创建项目。";
        }

        List<String> missing = stringList(prepared.get("missingFields"));
        if ("PROJECT_TRANSITION".equals(stringValue(prepared.get("actionCode"))))
            return missing.isEmpty() ? "当前没有可推进的草稿项目。" : join(missing, "、") + "。";
        if ("PROJECT_PLAN_DECISION".equals(stringValue(prepared.get("actionCode"))))
            return missing.isEmpty() ? "当前没有可以审批的负责人计划。" : join(missing, "、") + "。";
        if ("PROJECT_ACCEPTANCE_DECISION".equals(stringValue(prepared.get("actionCode"))))
            return missing.isEmpty() ? "当前没有可以审核的项目验收资料。" : join(missing, "、") + "。";
        if ("BUDGET_ADJUSTMENT".equals(stringValue(prepared.get("actionCode"))))
        {
            List<String> questions = new ArrayList<String>();
            if (hasMissing(missing, "项目")) questions.add("要调整哪个项目的预算？");
            if (hasMissing(missing, "预算")) questions.add("新的预算上限是多少？");
            if (hasMissing(missing, "原因")) questions.add("为什么要调整？简单说一句原因就可以。");
            return questions.isEmpty() ? "还需要补充：" + join(missing, "、") + "。" : join(questions, " ");
        }
        List<String> questions = new ArrayList<String>();
        boolean basicStage = hasMissing(missing, "项目名称") || hasMissing(missing, "主负责人")
            || hasMissing(missing, "归属公司");
        boolean workStage = hasMissing(missing, "项目目标") || hasMissing(missing, "计划开始");
        if (basicStage)
        {
            if (hasMissing(missing, "项目名称")) questions.add("项目叫什么？");
            if (hasMissing(missing, "主负责人")) questions.add("想让谁负责？直接说姓名就行。");
            if (hasMissing(missing, "归属公司")) questions.add("属于上海公司，还是越南公司？");
        }
        else if (workStage)
        {
            if (hasMissing(missing, "项目目标")) questions.add("做成什么样才算完成？");
            if (hasMissing(missing, "计划开始")) questions.add("准备什么时候开始、什么时候结束？直接说日期就行。");
        }
        else
        {
            if (hasMissing(missing, "核算方式")) questions.add("这个项目主要看赚了多少钱、花了多少钱，还是两者一起看？");
            if (hasMissing(missing, "预算")) questions.add("最多准备花多少钱？还没定就说“暂时不设预算”。");
        }
        if (questions.isEmpty()) return "还需要补充：" + join(missing, "、") + "。你直接用平时说话的方式告诉我就可以。";
        StringBuilder answer = new StringBuilder("好的，我们一步一步来。先告诉我：\n");
        for (int index = 0; index < questions.size(); index++)
            answer.append(index + 1).append(". ").append(questions.get(index)).append("\n");
        if (basicStage)
            answer.append("\n你可以直接这样说：项目叫“越南直播增长”，让 Mina 负责，属于越南公司。");
        else if (workStage)
            answer.append("\n比如：每天完成10条视频，从8月15日做到9月30日。");
        else answer.append("\n不用填写表格，像平时说话一样告诉我就可以。");
        return answer.toString().trim();
    }

    private String completedProjectAnswer(Map<String, Object> result)
    {
        String projectName = stringValue(result.get("projectName"));
        String projectNo = stringValue(result.get("projectNo"));
        return "项目“" + projectName + "”已经创建完成" + (StringUtils.isBlank(projectNo) ? "" : "，编号是 " + projectNo)
            + "。这次立项已经结束，不需要再次确认。现在可以让负责人进入项目规划；如果要创建另一个项目，请明确告诉我“再创建一个新项目”。";
    }

    @SuppressWarnings("unchecked")
    private String projectBudgetAnswer(List<Map<String, Object>> tools)
    {
        Map<String, Object> tool = findToolResult(tools, "boss_project_detail");
        if (tool == null || !(tool.get("data") instanceof Map))
            return "我暂时没有查到你说的项目预算，请直接告诉我项目名称。";
        Map<String, Object> data = (Map<String, Object>) tool.get("data");
        if (!Boolean.TRUE.equals(data.get("ready")))
        {
            List<String> candidates = stringList(data.get("candidateProjects"));
            return candidates.isEmpty() ? "当前没有可查询的项目。"
                : "我还不能确定你指的是哪个项目。请直接说项目名称，可选项目有：" + join(candidates, "、") + "。";
        }
        Map<String, Object> project = data.get("project") instanceof Map
            ? (Map<String, Object>) data.get("project") : Collections.<String, Object>emptyMap();
        Object budget = project.get("budgetLimit");
        String name = stringValue(project.get("projectName"));
        String currency = stringValue(project.get("baseCurrency"));
        if (budget == null)
            return "项目“" + name + "”当前没有设置预算上限。";
        return "项目“" + name + "”当前设置的预算上限是 " + money(budget)
            + (StringUtils.isBlank(currency) ? "。" : " " + currency + "。");
    }

    @SuppressWarnings("unchecked")
    private String projectSummaryAnswer(List<Map<String, Object>> tools)
    {
        Map<String, Object> tool = findToolResult(tools, "boss_project_detail");
        if (tool == null || !(tool.get("data") instanceof Map))
            return "我暂时没有查到你说的项目，请直接告诉我项目名称。";
        Map<String, Object> data = (Map<String, Object>) tool.get("data");
        String unresolved = unresolvedProjectAnswer(data);
        if (unresolved != null) return unresolved;
        Map<String, Object> project = (Map<String, Object>) data.get("project");
        String currency = stringValue(project.get("baseCurrency"));
        Object budget = project.get("budgetLimit");
        return "项目“" + project.get("projectName") + "”目前处于" + projectStatusLabel(project.get("status"))
            + "，负责人是" + stringValue(project.get("mainOwnerName")) + "，归属"
            + stringValue(project.get("companyName")) + "。计划周期是" + stringValue(project.get("planStartDate"))
            + "至" + stringValue(project.get("planEndDate")) + "，当前有" + integer(project.get("memberCount"))
            + "名成员、" + integer(project.get("taskCount")) + "项任务、" + integer(project.get("openRiskCount"))
            + "项未关闭风险，预算上限" + (budget == null ? "尚未设置" : money(budget)
                + (StringUtils.isBlank(currency) ? "" : " " + currency)) + "。";
    }

    @SuppressWarnings("unchecked")
    private String projectAccountingAnswer(List<Map<String, Object>> tools)
    {
        Map<String, Object> tool = findToolResult(tools, "boss_project_accounting_detail");
        if (tool == null || !(tool.get("data") instanceof Map))
            return "我暂时没有查到你说的项目经营数据，请直接告诉我项目名称。";
        Map<String, Object> data = (Map<String, Object>) tool.get("data");
        String unresolved = unresolvedProjectAnswer(data);
        if (unresolved != null) return unresolved;
        Map<String, Object> project = (Map<String, Object>) data.get("project");
        List<Map<String, Object>> results = mapList(data.get("results"));
        String bizDate = stringValue(data.get("bizDate"));
        if (results.isEmpty())
            return "项目“" + project.get("projectName") + "”在 " + bizDate + " 暂时没有生成经营核算结果。";
        Map<String, Object> row = results.get(0);
        String currency = stringValue(row.get("currency"));
        if (StringUtils.isBlank(currency)) currency = stringValue(project.get("baseCurrency"));
        List<String> people = new ArrayList<String>();
        for (Map<String, Object> person : mapList(data.get("personnelItems")))
            people.add(stringValue(person.get("componentName")) + " " + money(person.get("amount")));
        Map<String, Object> budget = data.get("budgetMetrics") instanceof Map
            ? (Map<String, Object>) data.get("budgetMetrics") : Collections.<String, Object>emptyMap();
        BigDecimal overBudget = decimal(budget.get("overBudgetAmount"));
        return "项目“" + project.get("projectName") + "”在 " + bizDate + " 的收入是 "
            + money(row.get("revenueAmount")) + "，业务成本 " + money(row.get("costAmount")) + "，人员成本 "
            + money(row.get("personnelCost")) + "，经营结果 " + money(row.get("profitAmount"))
            + (StringUtils.isBlank(currency) ? "" : " " + currency)
            + (people.isEmpty() ? "" : "。人员成本分别是：" + join(people, "、"))
            + (overBudget != null && overBudget.compareTo(BigDecimal.ZERO) > 0
                ? "。当前累计超预算 " + money(overBudget) + (StringUtils.isBlank(currency) ? "" : " " + currency) : "")
            + "。";
    }

    @SuppressWarnings("unchecked")
    private String projectMemberProgressAnswer(List<Map<String, Object>> tools)
    {
        Map<String, Object> tool = findToolResult(tools, "boss_project_detail");
        if (tool == null || !(tool.get("data") instanceof Map))
            return "我暂时没有查到参项人员的完成记录，请先告诉我项目名称。";
        Map<String, Object> data = (Map<String, Object>) tool.get("data");
        String unresolved = unresolvedProjectAnswer(data);
        if (unresolved != null) return unresolved;
        Map<String, Object> project = (Map<String, Object>) data.get("project");
        Map<String, List<String>> progress = new LinkedHashMap<String, List<String>>();
        for (Map<String, Object> member : mapList(data.get("members")))
        {
            String name = stringValue(member.get("userNameSnapshot"));
            if (StringUtils.isNotBlank(name)) progress.put(name, new ArrayList<String>());
        }
        for (Map<String, Object> routine : mapList(data.get("routines")))
        {
            String name = stringValue(routine.get("assigneeName"));
            if (StringUtils.isBlank(name)) continue;
            if (!progress.containsKey(name)) progress.put(name, new ArrayList<String>());
            String unit = stringValue(routine.get("unit"));
            String item = stringValue(routine.get("routineName")) + "：";
            if (routine.get("todayReportId") == null)
                item += "今日未填报，目标 " + money(routine.get("todayTarget")) + (StringUtils.isBlank(unit) ? "" : " " + unit);
            else
                item += "今日完成 " + money(routine.get("todayActual")) + " / " + money(routine.get("todayTarget"))
                    + (StringUtils.isBlank(unit) ? "" : " " + unit);
            if (routine.get("cumulativeActual") != null)
                item += "，累计 " + money(routine.get("cumulativeActual")) + (StringUtils.isBlank(unit) ? "" : " " + unit);
            progress.get(name).add(item);
        }
        for (Map<String, Object> task : mapList(data.get("tasks")))
        {
            String name = stringValue(task.get("assigneeName"));
            if (StringUtils.isBlank(name)) continue;
            if (!progress.containsKey(name)) progress.put(name, new ArrayList<String>());
            progress.get(name).add(stringValue(task.get("taskName")) + "：" + integer(task.get("progress")) + "%（"
                + taskStatusLabel(task.get("status")) + "）");
        }
        List<String> people = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : progress.entrySet())
            people.add(entry.getKey() + "：" + (entry.getValue().isEmpty() ? "暂未安排可统计的工作"
                : join(entry.getValue(), "；")));
        return "项目“" + project.get("projectName") + "”在 " + stringValue(data.get("bizDate"))
            + " 的逐人完成情况如下：" + (people.isEmpty() ? "当前没有参项人员或可统计的工作。" : join(people, "。") + "。")
            + "为避免把不同工作内容混为一体，系统不强行合并成一个总完成率。";
    }

    @SuppressWarnings("unchecked")
    private String unresolvedProjectAnswer(Map<String, Object> data)
    {
        if (Boolean.TRUE.equals(data.get("ready")) && data.get("project") instanceof Map) return null;
        List<String> candidates = stringList(data.get("candidateProjects"));
        return candidates.isEmpty() ? "当前没有可查询的项目。"
            : "我还不能确定你指的是哪个项目。请直接说项目名称，可选项目有：" + join(candidates, "、") + "。";
    }

    private String projectStatusLabel(Object value)
    {
        String status = stringValue(value);
        if ("DRAFT".equals(status)) return "草稿阶段";
        if ("PLANNING".equals(status)) return "规划阶段";
        if ("ACTIVE".equals(status)) return "执行阶段";
        if ("ACCEPTANCE".equals(status)) return "待验收阶段";
        if ("CLOSED".equals(status) || "COMPLETED".equals(status)) return "已结项";
        if ("PAUSED".equals(status) || "SUSPENDED".equals(status)) return "已暂停";
        if ("CANCELED".equals(status) || "CANCELLED".equals(status)) return "已取消";
        return StringUtils.isBlank(status) ? "未知状态" : status;
    }

    private String taskStatusLabel(Object value)
    {
        String status = stringValue(value);
        if ("DONE".equals(status)) return "已完成";
        if ("IN_PROGRESS".equals(status)) return "进行中";
        if ("TODO".equals(status)) return "待开始";
        if ("BLOCKED".equals(status)) return "受阻";
        return StringUtils.isBlank(status) ? "未设置状态" : status;
    }

    private boolean hasMissing(List<String> missing, String prefix)
    {
        for (String value : missing) if (value.startsWith(prefix)) return true;
        return false;
    }

    private String buildAnswer(List<Map<String, Object>> tools)
    {
        if (tools.isEmpty())
            return "我已经可以替你查询今日经营、项目态势、人员概况和待处理事项。你可以直接问：‘今天经营怎么样？’或‘现在有哪些事情需要我处理？’。涉及立项、预算和审批的操作，我会先整理成确认单，未经你确认不会执行。";
        List<String> parts = new ArrayList<String>();
        for (Map<String, Object> tool : tools)
        {
            String code = String.valueOf(tool.get("toolCode"));
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) tool.get("data");
            if ("boss_today_accounting".equals(code)) parts.add(accountingText(data));
            if ("boss_project_snapshot".equals(code)) parts.add(projectText(data));
            if ("boss_pending_decisions".equals(code)) parts.add(pendingText(data));
            if ("boss_staff_snapshot".equals(code)) parts.add(staffText(data));
            if ("boss_prepare_project_create".equals(code))
            {
                if (Boolean.TRUE.equals(data.get("ready")))
                    parts.add("项目立项确认单已经准备好。请核对负责人、公司、周期、目标和预算后点击确认立项。");
                else parts.add("还需要补充以下立项信息：" + join(stringList(data.get("missingFields")), "、") + "。");
            }
            if ("boss_prepare_project_transition".equals(code))
            {
                if (Boolean.TRUE.equals(data.get("ready")))
                    parts.add("项目进入规划确认单已经准备好。请核对项目和负责人后确认推进。");
                else parts.add(join(stringList(data.get("missingFields")), "、") + "。");
            }
            if ("boss_project_plan_review".equals(code)) parts.add(planReviewText(data));
            if ("boss_project_acceptance_review".equals(code)) parts.add(acceptanceReviewText(data));
            if ("boss_prepare_plan_decision".equals(code))
            {
                if (Boolean.TRUE.equals(data.get("ready"))) parts.add("项目计划审批确认单已经准备好，等待老板最后确认。");
                else parts.add(join(stringList(data.get("missingFields")), "、") + "。");
            }
            if ("boss_prepare_acceptance_decision".equals(code))
            {
                if (Boolean.TRUE.equals(data.get("ready"))) parts.add("项目验收决定确认单已经准备好，等待老板最后确认。");
                else parts.add(join(stringList(data.get("missingFields")), "、") + "。");
            }
        }
        return join(parts, "\n");
    }

    /**
     * 只读查询的最终事实由服务端按工具结果生成。模型可以选择查询，但不能把自由文本直接当成业务事实。
     */
    private String verifiedReadAnswer(List<Map<String, Object>> tools)
    {
        if (findToolResult(tools, "boss_project_accounting_detail") != null)
            return projectAccountingAnswer(tools);
        if (findToolResult(tools, "boss_project_detail") != null)
            return projectSummaryAnswer(tools);
        return buildAnswer(tools);
    }

    private String noEvidenceAnswer(String question)
    {
        if (containsAny(question, "你好", "在吗", "你是谁", "能做什么"))
            return "我是老板 AI 助理，可以查询经营、项目、人员和待办，也可以把立项、预算与审批整理成确认单。涉及业务事实时，我会先读取系统数据再回答。";
        return "这次我还没有取得足够的系统证据，因此不会根据聊天内容猜测。请把要查询的项目、人员或日期说得更具体一点。";
    }

    @SuppressWarnings("unchecked")
    private String planReviewText(Map<String, Object> data)
    {
        if (!Boolean.TRUE.equals(data.get("ready"))) return join(stringList(data.get("missingFields")), "、") + "。";
        Map<String, Object> project = data.get("project") instanceof Map
            ? (Map<String, Object>) data.get("project") : Collections.<String,Object>emptyMap();
        List<String> warnings = stringList(data.get("warnings"));
        String result = "项目“" + project.get("projectName") + "”的计划已提交：负责人" + project.get("mainOwnerName")
            + "，周期" + project.get("planStartDate") + "至" + project.get("planEndDate") + "，包含"
            + integer(data.get("routineCount")) + "项持续工作、" + integer(data.get("taskCount")) + "项一次性任务、"
            + integer(data.get("memberCount")) + "名参项人员。";
        return result + (warnings.isEmpty() ? "系统检查未发现明显缺项，可以考虑批准启动。"
            : "需要关注：" + join(warnings, "；") + "。你可以让我批准，也可以说明原因后退回负责人调整。");
    }

    @SuppressWarnings("unchecked")
    private String acceptanceReviewText(Map<String, Object> data)
    {
        if (!Boolean.TRUE.equals(data.get("ready"))) return join(stringList(data.get("missingFields")), "、") + "。";
        Map<String, Object> project = data.get("project") instanceof Map
            ? (Map<String, Object>) data.get("project") : Collections.<String,Object>emptyMap();
        Map<String, Object> acceptance = data.get("acceptance") instanceof Map
            ? (Map<String, Object>) data.get("acceptance") : Collections.<String,Object>emptyMap();
        String result = "项目“" + project.get("projectName") + "”已提交第 " + acceptance.get("submissionVersion")
            + " 版验收资料：一次性任务完成 " + integer(data.get("completedTaskCount")) + "/"
            + integer(data.get("taskCount")) + " 项，交付凭证 " + integer(data.get("attachmentCount")) + " 份。";
        return result + (Boolean.TRUE.equals(data.get("canApprove"))
            ? "系统前置条件已通过。请在下面的验收审核卡中核对成果说明、交付物和凭证，再选择通过或退回；未经最终确认不会改变项目状态。"
            : "当前还不满足验收通过条件，请查看审核卡中的提示，可以退回负责人补充。未经最终确认不会改变项目状态。");
    }

    @SuppressWarnings("unchecked")
    private String accountingText(Map<String, Object> data)
    {
        Map<String, Object> today = data.get("today") instanceof Map ? (Map<String, Object>) data.get("today") : Collections.<String,Object>emptyMap();
        int alerts = data.get("alerts") instanceof List ? ((List<Object>) data.get("alerts")).size() : 0;
        return "今日经营：已核算 " + integer(today.get("projectCount")) + " 个项目，收入 " + money(today.get("revenueAmount"))
            + "，业务成本 " + money(today.get("businessCost")) + "，人员成本 " + money(today.get("personnelCost"))
            + "，经营结果 " + money(today.get("profitAmount")) + "。当前有 " + alerts + " 项经营异常。";
    }

    @SuppressWarnings("unchecked")
    private String projectText(Map<String, Object> data)
    {
        Map<String, Object> summary = data.get("summary") instanceof Map ? (Map<String, Object>) data.get("summary") : Collections.<String,Object>emptyMap();
        StringBuilder answer = new StringBuilder("项目态势：当前权限范围内共 " + integer(summary.get("totalCount")) + " 个项目，执行中 "
            + integer(summary.get("activeCount")) + " 个，待决策 " + integer(summary.get("pendingDecisionCount"))
            + " 个，存在逾期 " + integer(summary.get("overdueProjectCount")) + " 个，高风险 "
            + integer(summary.get("highRiskProjectCount")) + " 个。");
        List<Map<String, Object>> projects = copyFields(data.get("projects"), "projectId", "projectNo",
            "projectName", "companyName", "mainOwnerName", "status", "planStartDate", "planEndDate", "objective");
        if (projects.isEmpty()) return answer.toString();
        answer.append("\n\n分别是：");
        int index = 1;
        for (Map<String, Object> project : projects)
        {
            answer.append("\n").append(index++).append(". “")
                .append(defaultValue(stringValue(project.get("projectName")), "未命名项目")).append("”")
                .append("｜负责人：").append(defaultValue(stringValue(project.get("mainOwnerName")), "未指定"))
                .append("｜状态：").append(projectStatusLabel(project.get("status")))
                .append("｜周期：").append(projectPeriodText(project))
                .append("\n   项目内容：").append(defaultValue(stringValue(project.get("objective")), "尚未填写"));
        }
        return answer.toString();
    }

    private String pendingText(Map<String, Object> data)
    {
        List<Map<String, Object>> decisions = mapList(data.get("decisions"));
        if (decisions.isEmpty())
            return "当前没有需要老板决策的项目。另有 " + integer(data.get("taskCount")) + " 项分配给你的未完成任务。";
        StringBuilder answer = new StringBuilder("待处理：共有 " + decisions.size() + " 项需要你决定：");
        int index = 1;
        for (Map<String, Object> item : decisions)
            answer.append("\n").append(index++).append(". 项目“").append(item.get("projectName")).append("”：")
                .append(item.get("decisionLabel")).append("。下一步：").append(item.get("nextAction"));
        answer.append("\n\n你可以直接说“处理第一个”或“处理第 2 个”，我会按这份清单中的项目 ID 锁定事项，确认前不会修改数据。");
        if (integer(data.get("taskCount")) > 0)
            answer.append(" 另外还有 ").append(integer(data.get("taskCount"))).append(" 项分配给你的未完成任务。");
        return answer.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value)
    {
        if (!(value instanceof List)) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<Object>) value) if (item instanceof Map) result.add((Map<String, Object>) item);
        return result;
    }

    @SuppressWarnings("unchecked")
    private String staffText(Map<String, Object> data)
    {
        Map<String, Integer> counts = data.get("companyCounts") instanceof Map
            ? (Map<String, Integer>) data.get("companyCounts") : Collections.<String,Integer>emptyMap();
        List<String> parts = new ArrayList<String>();
        for (Map.Entry<String, Integer> item : counts.entrySet()) parts.add(item.getKey() + " " + item.getValue() + " 人");
        return "人员概况：当前共有 " + integer(data.get("staffCount")) + " 个有效账号"
            + (parts.isEmpty() ? "。" : "，其中" + join(parts, "、") + "。");
    }

    private List<Map<String, Object>> sources(List<Map<String, Object>> tools)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> tool : tools)
        {
            if (!"READ_ONLY".equals(String.valueOf(tool.get("riskLevel")))) continue;
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("label", tool.get("label"));
            source.put("toolCode", tool.get("toolCode"));
            source.put("sourcePath", tool.get("sourcePath"));
            source.put("cutoffTime", timestamp());
            result.add(source);
        }
        return result;
    }

    private List<Map<String, Object>> toolReferences(List<Map<String, Object>> tools)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> tool : tools)
        {
            Map<String, Object> reference = new LinkedHashMap<String, Object>();
            reference.put("toolCode", tool.get("toolCode"));
            reference.put("label", tool.get("label"));
            reference.put("riskLevel", tool.get("riskLevel"));
            reference.put("sourcePath", tool.get("sourcePath"));
            result.add(reference);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> auditToolData(String code, Map<String, Object> data)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if ("boss_today_accounting".equals(code))
        {
            result.put("bizDate", data.get("bizDate"));
            result.put("today", data.get("today"));
            result.put("draftFactCount", data.get("draftFactCount"));
            result.put("alertCount", data.get("alerts") instanceof List ? ((List<Object>) data.get("alerts")).size() : 0);
            result.put("rankingCount", data.get("ranking") instanceof List ? ((List<Object>) data.get("ranking")).size() : 0);
        }
        else if ("boss_project_snapshot".equals(code))
        {
            result.put("summary", data.get("summary"));
            result.put("projectCount", data.get("projects") instanceof List ? ((List<Object>) data.get("projects")).size() : 0);
        }
        else if ("boss_pending_decisions".equals(code))
        {
            result.put("decisionCount", data.get("decisionCount"));
            result.put("taskCount", data.get("taskCount"));
        }
        else if ("boss_staff_snapshot".equals(code))
        {
            result.put("staffCount", data.get("staffCount"));
            result.put("companyCounts", data.get("companyCounts"));
        }
        else if ("boss_project_plan_review".equals(code))
        {
            result.put("ready", data.get("ready"));
            result.put("project", data.get("project"));
            result.put("taskCount", data.get("taskCount"));
            result.put("routineCount", data.get("routineCount"));
            result.put("memberCount", data.get("memberCount"));
            result.put("warningCount", data.get("warnings") instanceof List ? ((List<Object>) data.get("warnings")).size() : 0);
        }
        else if ("boss_project_acceptance_review".equals(code))
        {
            result.put("ready", data.get("ready")); result.put("project", data.get("project"));
            result.put("acceptance", data.get("acceptance")); result.put("canApprove", data.get("canApprove"));
            result.put("taskCount", data.get("taskCount")); result.put("completedTaskCount", data.get("completedTaskCount"));
            result.put("attachmentCount", data.get("attachmentCount"));
            result.put("warningCount", data.get("warnings") instanceof List ? ((List<Object>) data.get("warnings")).size() : 0);
        }
        return result;
    }

    private Map<String, Object> scope(Long userId, boolean viewAll)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("userId", userId);
        result.put("label", viewAll ? "管理员全量经营范围" : "当前老板本人立项项目");
        result.put("dataDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        result.put("cutoffTime", timestamp());
        return result;
    }

    private List<String> suggestions()
    {
        List<String> result = new ArrayList<String>();
        result.add("今天经营怎么样？");
        result.add("有哪些项目需要我处理？");
        result.add("帮我审核待审批计划");
        result.add("帮我审核待验收项目");
        result.add("现在的人员分布怎么样？");
        return result;
    }

    private Map<String, Object> message(Long conversationId, Long userId, String role, String content, String metadata)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("conversationId", conversationId);
        row.put("userId", userId);
        row.put("messageRole", role);
        row.put("content", content);
        row.put("metadataJson", metadata);
        return row;
    }

    private void audit(String traceId, Long conversationId, Long runId, Long userId, String userName,
        String eventType, String summary, Object detail)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("traceId", traceId);
        row.put("conversationId", conversationId);
        row.put("runId", runId);
        row.put("userId", userId);
        row.put("userName", userName);
        row.put("roleCode", ROLE_CODE);
        row.put("eventType", eventType);
        row.put("eventSummary", summary);
        row.put("detailJson", toJson(detail));
        mapper.insertAudit(row);
    }

    private boolean containsAny(String text, String... candidates)
    {
        for (String candidate : candidates) if (text.contains(candidate)) return true;
        return false;
    }

    private String text(Map<String, Object> values, String key)
    {
        return values == null ? "" : StringUtils.trim(stringValue(values.get(key)));
    }

    private String upper(Object value) { return stringValue(value).trim().toUpperCase(); }

    private boolean booleanValue(Object value)
    {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(stringValue(value)) || "1".equals(stringValue(value));
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception ex) { return null; }
    }

    private Date date(Object value)
    {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) return null;
        try
        {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            format.setLenient(false);
            return format.parse(String.valueOf(value));
        }
        catch (Exception ex) { return null; }
    }

    private void normalizeProjectDates(Map<String, Object> arguments, List<Map<String, Object>> history,
        String question)
    {
        StringBuilder userText = new StringBuilder();
        if (history != null)
            for (Map<String, Object> item : history)
                if ("user".equals(stringValue(item.get("role"))))
                    userText.append(stringValue(item.get("content"))).append('\n');
        userText.append(question == null ? "" : question);
        String text = userText.toString();
        LocalDate today = LocalDate.now(clock);
        boolean userSpecifiedYear = Pattern.compile("(?:19|20)\\d{2}\\s*(?:年|[-/.])").matcher(text).find();

        if (!userSpecifiedYear)
        {
            normalizeArgumentYear(arguments, "planStartDate", today.getYear());
            normalizeArgumentYear(arguments, "planEndDate", today.getYear());
        }
        if (containsAny(text, "现在开始", "今天开始", "从今天", "从现在", "现在起", "今天起", "即日起", "当天开始"))
            arguments.put("planStartDate", today.toString());

        Matcher end = Pattern.compile("(?:到|至)\\s*(\\d{1,2})月(\\d{1,2})日?").matcher(text);
        while (end.find()) putMonthDay(arguments, "planEndDate", today.getYear(), end.group(1), end.group(2));
        Matcher start = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?\\s*(?:开始|开工|启动)").matcher(text);
        while (start.find()) putMonthDay(arguments, "planStartDate", today.getYear(), start.group(1), start.group(2));
    }

    private void normalizeArgumentYear(Map<String, Object> arguments, String key, int year)
    {
        String value = text(arguments, key);
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) return;
        try
        {
            LocalDate original = LocalDate.parse(value);
            arguments.put(key, LocalDate.of(year, original.getMonthValue(), original.getDayOfMonth()).toString());
        }
        catch (Exception ignored) { }
    }

    private void putMonthDay(Map<String, Object> arguments, String key, int year, String month, String day)
    {
        try { arguments.put(key, LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day)).toString()); }
        catch (Exception ignored) { }
    }

    private boolean contains(String[] values, String expected)
    {
        for (String value : values) if (value.equals(expected)) return true;
        return false;
    }

    private boolean isProjectCreateRequest(String question)
    {
        return containsAny(question, "创建项目", "创建一个项目", "创建一个新项目", "新建项目", "建立项目", "开启项目", "开一个项目", "立项");
    }

    private boolean isBudgetAdjustmentRequest(String question)
    {
        return containsAny(question, "调整预算", "增加预算", "提高预算", "降低预算", "修改预算", "预算改成",
            "预算设为", "预算调整到", "预算提高到", "预算降低到");
    }

    private boolean isProjectBudgetQuery(String question)
    {
        return !isBudgetAdjustmentRequest(question) && containsAny(question, "预算是多少", "预算多少", "多少预算",
            "预算上限是多少", "设置的预算", "当前预算", "原预算");
    }

    private String guardedProjectQuery(String question)
    {
        if (isProjectBudgetQuery(question)) return "BUDGET";
        if (containsAny(question, "他们的完成进度", "他们完成了多少", "他们做了多少", "成员完成进度",
            "人员完成进度", "大家完成进度", "参项人员进度", "每个人完成情况")) return "MEMBER_PROGRESS";
        boolean projectReference = containsAny(question, "这个项目", "那个项目", "该项目", "当前项目",
            "这个立项", "这个计划", "它的项目", "它现在", "它今天", "它怎么样");
        if (!projectReference) return null;
        if (containsAny(question, "今天收入", "今天成本", "今日收入", "今日成本", "今天亏", "今日亏",
            "经营结果", "人员成本", "业务成本", "利润", "花了多少", "花费", "支出", "累计成本",
            "超预算", "超支")) return "ACCOUNTING";
        if (containsAny(question, "怎么样", "什么情况", "负责人", "成员", "计划", "进度", "状态", "目标",
            "KPI", "风险", "什么时候", "周期", "哪家公司", "详情")) return "DETAIL";
        return null;
    }

    private boolean isAmbiguousPersonReference(String question)
    {
        return containsAny(question, "这个人", "那个人", "他今天", "她今天", "他的成本", "她的成本",
            "他的任务", "她的任务", "让他处理", "让她处理", "他负责", "她负责");
    }

    private boolean isExplicitNewProjectRequest(String question)
    {
        if (StringUtils.isBlank(question) || containsAny(question, "不要创建", "不用创建", "别创建", "暂不创建",
            "不创建项目", "取消创建", "不需要创建")) return false;
        return containsAny(question, "创建项目", "创建一个项目", "创建一个新项目", "新建项目", "建立项目", "开启项目",
            "开一个项目", "再建一个", "再创建一个", "另一个项目", "新的项目");
    }

    private boolean isAdvanceExistingRequest(String question)
    {
        return containsAny(question, "推进一下", "推进这个", "推进项目", "进入规划", "开始规划", "负责人去规划",
            "负责人开始规划", "让负责人规划", "启动规划");
    }

    private boolean isShortAffirmative(String question)
    {
        String value = StringUtils.trim(question);
        return "1".equals(value) || "第一个".equals(value) || "可以".equals(value) || "好的".equals(value)
            || "好".equals(value) || "同意".equals(value) || "确认".equals(value);
    }

    private boolean historyContains(List<Map<String, Object>> history, String... candidates)
    {
        if (history == null) return false;
        for (int index = history.size() - 1; index >= 0; index--)
        {
            Map<String, Object> item = history.get(index);
            if (!"assistant".equals(stringValue(item.get("role")))) continue;
            return containsAny(stringValue(item.get("content")), candidates);
        }
        return false;
    }

    private Map<String, Object> submittedPendingPlan(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> toolResults)
    {
        Map<String, Object> pending = findToolResult(toolResults, "boss_pending_decisions");
        if (pending == null || !(pending.get("data") instanceof Map)) return null;
        @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) pending.get("data");
        List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> decision : mapList(data.get("decisions")))
            if ("PLAN_APPROVAL".equals(stringValue(decision.get("decisionType")))) candidates.add(decision);
        if (candidates.isEmpty()) return null;

        String context = question + "\n" + historyText(history);
        for (Map<String, Object> candidate : candidates)
        {
            String name = stringValue(candidate.get("projectName"));
            if (StringUtils.isNotBlank(name) && context.contains(name)) return candidate;
            String keyword = pendingProjectKeyword(question);
            if (StringUtils.isNotBlank(keyword) && name.contains(keyword)) return candidate;
        }
        return candidates.size() == 1 && containsAny(question, "需要我做", "有什么事情", "待处理", "待办", "处理", "下一步",
            "什么情况", "查看", "看看", "审核") ? candidates.get(0) : null;
    }

    private String pendingProjectKeyword(String question)
    {
        String value = StringUtils.trim(question);
        for (String word : new String[] { "帮我", "你去", "现在", "这个", "项目", "处理", "查看", "看看", "看一下",
            "是什么情况", "什么情况", "为什么", "下一步", "一下", "呀", "啊", "呢", "的" }) value = value.replace(word, "");
        return value.length() >= 2 ? value : "";
    }

    private String historyText(List<Map<String, Object>> history)
    {
        StringBuilder value = new StringBuilder();
        if (history != null) for (Map<String, Object> item : history)
            value.append(stringValue(item.get("content"))).append('\n');
        return value.toString();
    }

    private boolean isPlanReviewRequest(String question)
    {
        return containsAny(question, "审核计划", "待审批计划", "待确认计划", "负责人提交的计划", "看看计划",
            "看一下计划", "计划怎么样", "计划是否合理");
    }

    private boolean isPlanApproveRequest(String question)
    {
        String normalized = StringUtils.trim(question);
        return "批准".equals(normalized) || "同意".equals(normalized) || "可以启动".equals(normalized)
            || containsAny(question, "批准计划", "批准这个计划", "同意这个计划", "同意负责人计划", "确认并启动",
            "计划没问题，启动", "按这个计划执行")
            || (question.contains("批准") && question.contains("计划") && containsAny(question, "启动", "执行", "通过"));
    }

    private boolean isPlanReturnRequest(String question)
    {
        String normalized = StringUtils.trim(question);
        return "退回".equals(normalized) || containsAny(question, "退回计划", "退回负责人", "让负责人调整", "让负责人修改", "计划需要修改",
            "这个计划要调整");
    }

    private String planReturnReason(String question)
    {
        String value = StringUtils.trim(question);
        if ("退回".equals(value) || "退回计划".equals(value)) return "";
        value = value.replaceFirst("^(请)?(把)?(这个)?(计划)?(退回|让负责人调整|让负责人修改)[，,：:\\s]*", "");
        return StringUtils.trim(value);
    }

    private boolean isAcceptanceReviewRequest(String question)
    {
        return containsAny(question, "审核验收", "验收资料", "待验收项目", "验收项目", "看看验收", "查看验收",
            "交付验收", "成果验收");
    }

    private boolean isAcceptanceApproveRequest(String question)
    {
        return containsAny(question, "验收通过", "通过验收", "批准验收", "确认验收", "验收并结项",
            "验收没问题", "通过并关闭", "验收合格");
    }

    private boolean isAcceptanceReturnRequest(String question)
    {
        return containsAny(question, "退回验收", "验收退回", "验收不通过", "退回交付", "验收资料退回");
    }

    private String acceptanceReturnReason(String question)
    {
        String value = StringUtils.trim(question);
        value = value.replaceFirst("^(请)?(把)?(这个)?(项目)?(的)?(验收资料)?(退回验收|验收退回|验收不通过|退回交付|验收资料退回)[，,：:\\s]*", "");
        return StringUtils.trim(value);
    }

    private String defaultValue(String value, String fallback)
    {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private String allowed(String value, String[] allowed, String fallback)
    {
        return contains(allowed, value) ? value : fallback;
    }

    private Map<String, Object> findUser(List<Map<String, Object>> users, String input)
    {
        if (StringUtils.isBlank(input)) return null;
        List<Map<String, Object>> partial = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> user : users)
        {
            String nick = stringValue(user.get("nickName"));
            String account = stringValue(user.get("userName"));
            if (input.equalsIgnoreCase(nick) || input.equalsIgnoreCase(account)) return user;
            if (nick.contains(input) || account.toLowerCase().contains(input.toLowerCase())) partial.add(user);
        }
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private Map<String, Object> findCompany(List<Map<String, Object>> staff, String input)
    {
        if (StringUtils.isBlank(input)) return null;
        Map<String, Map<String, Object>> companies = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> option : staff)
        {
            if (option.get("companyDeptId") == null || option.get("companyName") == null) continue;
            String key = String.valueOf(option.get("companyDeptId"));
            Map<String, Object> company = new LinkedHashMap<String, Object>();
            company.put("companyDeptId", option.get("companyDeptId"));
            company.put("companyName", option.get("companyName"));
            companies.put(key, company);
        }
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> company : companies.values())
        {
            String name = String.valueOf(company.get("companyName"));
            if (name.equalsIgnoreCase(input)) return company;
            if (name.contains(input) || input.contains(name)) matches.add(company);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<String> optionNames(List<Map<String, Object>> rows, String primary, String secondary)
    {
        List<String> result = new ArrayList<String>();
        for (Map<String, Object> row : rows)
        {
            String name = stringValue(row.get(primary));
            if (StringUtils.isBlank(name) && secondary != null) name = stringValue(row.get(secondary));
            if (StringUtils.isNotBlank(name) && !result.contains(name)) result.add(name);
        }
        return result;
    }

    private String displayName(Map<String, Object> user)
    {
        String nick = stringValue(user.get("nickName"));
        return StringUtils.isNotBlank(nick) ? nick : stringValue(user.get("userName"));
    }

    private String formatDate(Date value) { return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(value); }

    private String projectDateText(Object value)
    {
        if (value == null) return "";
        if (value instanceof Date) return formatDate((Date) value);
        String text = StringUtils.trim(String.valueOf(value));
        if (text.length() >= 10 && text.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}"))
            return text.substring(0, 10);
        return text;
    }

    private String projectPeriodText(Map<String, Object> project)
    {
        String start = projectDateText(project.get("planStartDate"));
        String end = projectDateText(project.get("planEndDate"));
        if (StringUtils.isBlank(start) && StringUtils.isBlank(end)) return "尚未设置";
        if (StringUtils.isBlank(start)) return "截至 " + end;
        if (StringUtils.isBlank(end)) return start + " 起";
        return start + " 至 " + end;
    }

    private String accountingLabel(String mode)
    {
        if ("COST".equals(mode)) return "成本项目";
        if ("VALUE".equals(mode)) return "价值项目";
        if ("HYBRID".equals(mode)) return "混合核算项目";
        return "利润项目";
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value)
    {
        if (!(value instanceof List)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (Object item : (List<Object>) value) result.add(String.valueOf(item));
        return result;
    }

    private String money(Object value)
    {
        try { return new BigDecimal(value == null ? "0" : String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
        catch (Exception ex) { return "0.00"; }
    }

    private int integer(Object value)
    {
        try { return value == null ? 0 : new BigDecimal(String.valueOf(value)).intValue(); }
        catch (Exception ex) { return 0; }
    }

    private Long longValue(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String cleanModelText(String value)
    {
        if (value == null) return "";
        return value.replace("**", "").replace("__", "").replace("`", "")
            .replaceAll("(?m)^#{1,6}\\s*", "").trim();
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }

    private String timestamp() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()); }

    private String join(List<String> values, String separator)
    {
        StringBuilder result = new StringBuilder();
        for (String value : values)
        {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private String toJson(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex)
        {
            // 审计序列化异常不能阻断老板的只读查询；保留可检索的安全文本作为降级记录。
            return "{\"serializationFallback\":true,\"value\":\"" + escapeJson(String.valueOf(value)) + "\"}";
        }
    }

    private Object fromJson(String value)
    {
        try { return objectMapper.readValue(value, Object.class); }
        catch (Exception ex) { return null; }
    }

    private String escapeJson(String value)
    {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }
}
