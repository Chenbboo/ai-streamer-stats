package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectStageAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectTaskReport;
import com.ruoyi.business.domain.BusinessProjectWorkPeriod;
import com.ruoyi.business.domain.BusinessProjectProgressReport;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectRoutineDailyTarget;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.attendance.BusinessFeishuService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.service.OnlineUserPermissionService;

@Service
public class BusinessProjectServiceImpl implements IBusinessProjectService
{
    private static final List<String> ACCOUNTING_MODES = Arrays.asList("PROFIT", "COST", "VALUE", "HYBRID");
    private static final List<String> MANAGEMENT_MODES = Arrays.asList("LIGHT", "STANDARD", "KEY_CONTROL");
    private static final List<String> CLOSE_METHODS = Arrays.asList("DIRECT", "RESULT_ACCEPTANCE", "STAGED_ACCEPTANCE");
    private static final List<String> PROJECT_GOAL_MODES = Arrays.asList("TOTAL", "NO_TOTAL");
    private static final List<String> ROUTINE_TARGET_MODES = Arrays.asList("FIXED", "AUTO_TOTAL", "DAILY_DYNAMIC", "NONE");
    private static final List<String> PRIORITIES = Arrays.asList("LOW", "MEDIUM", "HIGH");
    private static final List<String> MEMBER_ROLES = Arrays.asList("DEPUTY", "MEMBER", "OBSERVER");
    private static final List<String> TASK_STATUSES = Arrays.asList("TODO", "DOING", "BLOCKED", "DONE", "CANCELED");
    private static final List<String> RISK_SEVERITIES = Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> KPI_METRIC_TYPES = Arrays.asList("COUNT", "AMOUNT", "PERCENT", "DURATION", "SCORE", "MILESTONE");
    private static final List<String> KPI_PERIOD_TYPES = Arrays.asList("MONTH", "QUARTER", "PROJECT");
    private static final List<String> KPI_SOURCE_TYPES = Arrays.asList("MANUAL", "REVENUE", "BUSINESS_COST",
        "PERSONNEL_COST", "PROFIT", "ROUTINE", "TASK", "MILESTONE");
    private static final List<String> MANUAL_EXPENSE_CATEGORY_CODES = Arrays.asList("PURCHASE_COST", "PLATFORM_FEE",
        "MARKETING_COST", "LOGISTICS_COST", "ADMIN_ALLOCATION", "OTHER_EXPENSE");
    private static final List<String> LEAVE_TYPES = Arrays.asList("SICK", "PERSONAL", "ANNUAL", "COMPENSATORY", "OTHER");
    private static final BigDecimal CHINA_STANDARD_WORK_DAYS = new BigDecimal("21.75");
    private static final BigDecimal VIETNAM_STANDARD_WORK_DAYS = new BigDecimal("26");

    @Autowired
    private BusinessProjectMapper mapper;

    @Autowired
    private BusinessProjectKpiMapper kpiMapper;

    @Autowired
    private BusinessAccountingMapper accountingMapper;

    @Autowired
    private IBusinessAccountingService accountingService;

    @Autowired
    private BusinessProjectManagementFeeService managementFeeService;

    @Autowired private com.ruoyi.business.mapper.BusinessProjectProgressMapper progressMapper;
    @Autowired private BusinessProjectWorkMapper workMapper;
    @Autowired private BusinessMemberDayCostService memberDays;
    @Autowired private BusinessProjectWorkService workService;
    @Autowired private BusinessIncentiveMapper incentiveMapper;
    @Autowired private BusinessFeishuService feishuService;

    @Autowired
    private BusinessFileService businessFileService;

    @Autowired
    private OnlineUserPermissionService onlineUserPermissionService;

    @Override
    public List<BusinessProject> listProjects(Map<String, Object> query, Long userId, boolean viewAll, boolean boss)
    {
        Map<String, Object> scoped = query == null ? new HashMap<String, Object>() : new HashMap<String, Object>(query);
        scoped.put("userId", userId);
        scoped.put("viewAll", viewAll);
        scoped.put("boss", boss);
        return mapper.selectProjectList(scoped);
    }

    @Override
    public BusinessProject getProject(Long projectId, Long userId, boolean viewAll, boolean boss)
    {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = mapper.selectProjectById(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        requireAccess(project, userId, viewAll, boss);
        List<BusinessProjectRoutine> storedRoutines = mapper.selectRoutines(projectId, new Date());
        List<BusinessProjectRoutine> routines = storedRoutines == null
            ? new ArrayList<BusinessProjectRoutine>()
            : new ArrayList<BusinessProjectRoutine>(storedRoutines);
        List<BusinessProjectRoutine> retiredRoutines = mapper.selectRetiredRoutines(projectId, new Date());
        if (retiredRoutines == null) retiredRoutines = new ArrayList<BusinessProjectRoutine>();
        List<BusinessProjectTask> tasks = mapper.selectTasks(projectId);
        if (tasks == null) tasks = new ArrayList<BusinessProjectTask>();
        List<BusinessProjectTask> inactiveTasks = mapper.selectInactiveTasks(projectId);
        if (inactiveTasks == null) inactiveTasks = new ArrayList<BusinessProjectTask>();
        attachWorkPeriods(projectId, tasks, inactiveTasks, routines, retiredRoutines);
        project.setMembers(mapper.selectMembers(projectId));
        project.setMilestones(mapper.selectMilestones(projectId));
        project.setTasks(tasks);
        project.setInactiveTasks(inactiveTasks);
        project.setRisks(mapper.selectRisks(projectId));
        project.setOwnerHistory(mapper.selectOwnerHistory(projectId));
        project.setAcceptances(mapper.selectAcceptances(projectId));
        project.setStageAcceptances(mapper.selectStageAcceptances(projectId));
        project.setEvents(mapper.selectEvents(projectId));
        project.setGovernanceProfile(buildGovernanceProfile(project));
        Map<String, Object> executionRelation = mapper.selectActiveExecutionRelation(projectId);
        if (executionRelation != null && executionRelation.get("sourceDomain") != null)
        {
            project.setExecutionSource(String.valueOf(executionRelation.get("sourceDomain")));
            if ("LIVE".equals(project.getExecutionSource()))
            {
                List<BusinessProjectRoutine> sourceRoutines = mapper.selectLiveStreamerRoutines(executionRelation);
                if (sourceRoutines != null) routines.addAll(sourceRoutines);
            }
        }
        project.setRoutines(routines);
        project.setRetiredRoutines(retiredRoutines);
        return project;
    }

    @Override
    @Transactional
    public BusinessProject createProject(BusinessProject project, Long userId, String userName)
    {
        throw new ServiceException("正式项目不能从此入口创建，请先完成项目测算并由负责人启动");
    }

    @Override
    public List<BusinessProject> projectHierarchy(Map<String, Object> query, Long userId, boolean viewAll, boolean boss)
    {
        Map<String, Object> scoped = query == null ? new HashMap<>() : new HashMap<>(query);
        scoped.put("userId", userId);
        scoped.put("viewAll", viewAll);
        scoped.put("boss", boss);
        List<BusinessProject> rows = mapper.selectProjectRoots(scoped);
        // Preserve PageHelper's Page instance and its root-only total when masking context rows.
        for (int index = 0; index < rows.size(); index++)
        {
            BusinessProject project = rows.get(index);
            if (project.isContextOnly())
            {
                BusinessProject context = new BusinessProject();
                context.setProjectId(project.getProjectId());
                context.setProjectName("主项目（仅展示层级）");
                context.setContextOnly(true);
                context.setMatchedChildId(project.getMatchedChildId());
                rows.set(index, context);
            }
            else decorateHierarchyProject(project, userId, boss);
        }
        return rows;
    }

    @Override
    public List<BusinessProject> projectChildren(Long parentId, Long userId, boolean viewAll, boolean boss)
    {
        BusinessProject parent = requireProject(parentId);
        if (parent.getParentId() != null) throw new ServiceException("仅支持主项目与子项目两级结构");
        Map<String, Object> query = new HashMap<>();
        query.put("parentId", parentId);
        List<BusinessProject> rows = listProjects(query, userId, viewAll, boss);
        for (BusinessProject project : rows) decorateHierarchyProject(project, userId, boss);
        return rows;
    }

    private void decorateHierarchyProject(BusinessProject project, Long userId, boolean boss)
    {
        String role = mapper.selectMemberRole(project.getProjectId(), userId);
        project.setManageable(SecurityUtils.isAdmin(userId)
            || (boss ? userId.equals(projectSponsorUserId(project)) : Arrays.asList("OWNER", "DEPUTY").contains(role)));
    }

    @Override
    public List<Map<String, Object>> projectCompanyOptions()
    {
        return mapper.selectProjectCompanyOptions();
    }

    @Override
    public void validateSubprojectParent(Long parentId, Long sponsorId, Long applicantId)
    {
        BusinessProject parent = requireProjectForUpdate(parentId);
        requireManage(parent, applicantId, applicantId.equals(projectSponsorUserId(parent)));
        ensureMutable(parent);
        validateParent(parentId, null, sponsorId);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireManage(project, userId, boss);
        if (mapper.countSubprojects(projectId) > 0)
            throw new ServiceException("该项目包含子项目，请先删除所有子项目，再删除主项目");
        if (mapper.softDeleteProject(projectId, project.getVersion(), userName) != 1) throw changed();
        addEvent(projectId, "DELETE", project.getStatus(), project.getStatus(), userId, userName, "删除项目，保留历史记录");
    }

    @Override
    public Map<String, Object> settlementStatus(Long projectId, Long userId, boolean viewAll, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireAccess(project, userId, viewAll, boss);
        return buildSettlementStatus(project, userId, boss, viewAll, false);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> closeAccounting(Long projectId, Integer version, String reason,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        // A technical administrator's visibility is not a business sign-off responsibility.
        if (!boss || userId == null || !userId.equals(projectSponsorUserId(project)))
            throw new ServiceException("只有项目归属老板可以确认关闭核算");
        if (version == null || !version.equals(project.getVersion())) throw changed();
        if (StringUtils.isBlank(reason) || reason.trim().length() > 2000)
            throw new ServiceException("请填写核算关闭说明，且不超过2000个字符");
        if (!BusinessProjectLifecycle.isTerminal(project.getStatus()))
        {
            String readinessIssue = unifiedCloseReadinessIssue(project);
            if (readinessIssue != null) throw new ServiceException(readinessIssue);
            BusinessProject closed = finalizeSeparatedProject(project, reason.trim(), userId, userName, boss);
            return buildSettlementStatus(closed, userId, boss, false, true);
        }
        Map<String, Object> status = buildSettlementStatus(project, userId, boss, false, false);
        if (!Boolean.TRUE.equals(status.get("canClose")))
            throw new ServiceException("项目尚不满足核算关闭条件，请刷新查看待处理清单");
        // Unit-level legacy callers construct this service without the Spring-added collaborator.
        // Production always injects it; keeping the null guard preserves those isolated lifecycle tests.
        if (managementFeeService != null)
            managementFeeService.settle(projectId, project.getActualEndDate(), userId, userName);
        accountingService.closeProjectAccounting(projectId, project.getActualEndDate(), userName);
        if (mapper.closeAccounting(projectId, version, userName) != 1) throw changed();
        addEvent(projectId, "ACCOUNTING_CLOSE", "OPEN", "CLOSED", userId, userName, reason.trim());
        project.setAccountingState("CLOSED");
        project.setVersion(version + 1);
        return buildSettlementStatus(project, userId, boss, false, false);
    }

    private Map<String, Object> buildSettlementStatus(BusinessProject project, Long userId, boolean boss,
        boolean viewAll, boolean deliveryValidated)
    {
        Long projectId = project.getProjectId();
        if(BusinessMemberDayCostService.enabled(project))memberDays.synchronize(projectId);
        int kpiCount = mapper.countPendingProjectKpi(projectId);
        int effortCount = BusinessMemberDayCostService.enabled(project)?0:"ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()) ? workMapper.countPendingWork(projectId) : mapper.countPendingProjectEfforts(projectId);
        int pendingCostCount = BusinessMemberDayCostService.enabled(project)?memberDays.pending(projectId):"ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()) ? workMapper.countPendingCosts(projectId) : 0;
        int factCount = accountingMapper.countProjectUnsettledFacts(projectId);
        int awardCount = incentiveMapper.countPendingAwards(projectId);
        Map<String, Object> managementFee;
        if (managementFeeService == null)
        {
            managementFee = new LinkedHashMap<String, Object>();
            managementFee.put("configured", true);
        }
        else managementFee = managementFeeService.settlementSnapshot(project, userId,
            hasPermission("business:incentive:pay"));
        List<Map<String, Object>> blockers = new ArrayList<Map<String, Object>>();
        addSettlementBlocker(blockers, "LEGACY_POLICY", "旧版项目沿用原结项规则", BusinessProjectLifecycle.isSeparated(project) ? 0 : 1);
        String deliveryIssue = deliveryValidated || BusinessProjectLifecycle.isTerminal(project.getStatus())
            ? null : unifiedCloseReadinessIssue(project);
        addSettlementBlocker(blockers, "DELIVERY_OPEN",
            deliveryIssue == null ? "项目交付尚未结束" : deliveryIssue, deliveryIssue == null ? 0 : 1);
        addSettlementBlocker(blockers, "ACCOUNTING_CLOSED", "项目核算已关闭", BusinessProjectLifecycle.isAccountingClosed(project) ? 1 : 0);
        addSettlementBlocker(blockers, "NOT_SPONSOR", "需由项目归属老板确认", boss && userId != null && userId.equals(projectSponsorUserId(project)) ? 0 : 1);
        addSettlementBlocker(blockers, "MISSING_END_DATE", "缺少实际交付结束日期",
            BusinessProjectLifecycle.isTerminal(project.getStatus()) && project.getActualEndDate() == null ? 1 : 0);
        addSettlementBlocker(blockers, "PENDING_KPI", "KPI方案尚未完成结算或作废", kpiCount);
        addSettlementBlocker(blockers, "PENDING_EFFORT", "人员投入尚待确认", effortCount);
        addSettlementBlocker(blockers, "PENDING_FACT", "财务事实尚待处理", factCount);
        addSettlementBlocker(blockers, "PENDING_COST", BusinessMemberDayCostService.enabled(project)?"成员工作日成本尚未计算完整":"已确认工作尚待计价或核算", pendingCostCount);
        addSettlementBlocker(blockers, "PENDING_AWARD", "奖金奖励单尚待处理或取消", awardCount);
        addSettlementBlocker(blockers, "MANAGEMENT_FEE_PENDING", "项目管理费尚未设置或明确免除",
            Boolean.TRUE.equals(managementFee.get("configurationRequired")) ? 1 : 0);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId);
        result.put("status", project.getStatus());
        result.put("deliveryPolicyVersion", project.getDeliveryPolicyVersion());
        result.put("accountingState", BusinessProjectLifecycle.isAccountingClosed(project) ? "CLOSED" : "OPEN");
        result.put("version", project.getVersion());
        result.put("canClose", blockers.isEmpty());
        result.put("blockers", blockers);
        result.put("pendingKpiCount", kpiCount);
        result.put("pendingEffortCount", effortCount);
        result.put("pendingFactCount", factCount);
        result.put("pendingCostCount", pendingCostCount);
        result.put("pendingAwardCount", awardCount);
        result.put("costPolicyVersion", project.getCostPolicyVersion());
        result.put("managementFee", managementFee);
        return result;
    }

    private void addSettlementBlocker(List<Map<String, Object>> blockers, String code, String label, int count)
    {
        if (count <= 0) return;
        Map<String, Object> blocker = new LinkedHashMap<String, Object>();
        blocker.put("code", code); blocker.put("label", label); blocker.put("count", count);
        blockers.add(blocker);
    }

    /**
     * Returns the first delivery-side reason that prevents the boss from completing the one-step close.
     * Financial queues are reported separately by buildSettlementStatus.
     */
    private String unifiedCloseReadinessIssue(BusinessProject project)
    {
        if (!BusinessProjectLifecycle.isSeparated(project)) return "旧版项目沿用原结项规则";
        if (BusinessProjectLifecycle.isTerminal(project.getStatus())) return null;
        String closeMethod = effectiveCloseMethod(project);
        if ("RESULT_ACCEPTANCE".equals(closeMethod))
            return "请在成果验收中确认通过，系统将同时结项、核算并冻结数据";
        try
        {
            if ("STAGED_ACCEPTANCE".equals(closeMethod))
            {
                if (!"ACCEPTANCE".equals(project.getStatus())) return "负责人尚未提交结项申请";
                ensureStagesReadyForClose(project.getProjectId());
            }
            else
            {
                if (!"ACTIVE".equals(project.getStatus()) && !"ACCEPTANCE".equals(project.getStatus()))
                    return "当前项目状态不能办理结项";
                if ("KEY_CONTROL".equals(normalizeManagementMode(project.getManagementMode())))
                    ensureKeyMilestonesReady(project.getProjectId());
            }
            if (!"LIGHT".equals(normalizeManagementMode(project.getManagementMode())))
                ensureHighRisksClosed(project.getProjectId());
            ensureKpiReadyForClose(project.getProjectId());
            ensureReadyForAcceptance(project.getProjectId());
            return null;
        }
        catch (ServiceException ex)
        {
            return ex.getMessage();
        }
    }

    /** Complete delivery, final accounting and the project data freeze in the same transaction. */
    private BusinessProject finalizeSeparatedProject(BusinessProject project, String reason,
        Long userId, String userName, boolean boss)
    {
        if (!BusinessProjectLifecycle.isSeparated(project))
            throw new ServiceException("当前项目不使用结项与核算合并流程");
        Date closeDate = normalizeLeaveDate(DateUtils.getNowDate(), "结项日期不能为空");
        project.setActualEndDate(closeDate);
        prepareTerminalState(project, "CLOSED", userName);
        Map<String, Object> status = buildSettlementStatus(project, userId, boss, false, true);
        if (!Boolean.TRUE.equals(status.get("canClose")))
            throw new ServiceException("项目尚不满足结项条件，请刷新查看待处理清单");
        if (managementFeeService != null)
            managementFeeService.settle(project.getProjectId(), closeDate, userId, userName);
        accountingService.closeProjectAccounting(project.getProjectId(), closeDate, userName);
        Integer version = project.getVersion();
        String from = project.getStatus();
        if (mapper.updateProjectStatus(project.getProjectId(), from, "CLOSED", null, false, userName, version) != 1)
            throw changed();
        if (mapper.closeAccounting(project.getProjectId(), version + 1, userName) != 1) throw changed();
        addEvent(project.getProjectId(), "CLOSE_AND_FREEZE", from, "CLOSED", userId, userName, reason);
        project.setStatus("CLOSED");
        project.setAccountingState("CLOSED");
        project.setVersion(version + 2);
        return project;
    }

    @Override
    @Transactional
    public BusinessProject createApprovedProject(BusinessProjectProposal proposal, Long reviewerUserId, String reviewerUserName)
    {
        if (proposal == null || proposal.getProposalId() == null) throw new ServiceException("立项申请不能为空");
        if (proposal.getParentProjectId() != null) {
            validateSubprojectParent(proposal.getParentProjectId(), proposal.getSponsorOwnerUserId(), proposal.getApplicantUserId());
            if (proposal.getAssignedOwnerUserId() == null) throw new ServiceException("请选择子项目负责人");
        }
        Map<String, Object> owner = requireActiveUser(proposal.getEffectiveOwnerUserId());
        Map<String, Object> sponsor = requireActiveUser(proposal.getSponsorOwnerUserId());
        if (!reviewerUserId.equals(proposal.getApplicantUserId())
            && !reviewerUserId.equals(proposal.getSponsorOwnerUserId()))
            throw new ServiceException("只有项目负责人或归属老板可以启动项目");

        BusinessProject project = new BusinessProject();
        project.setSourceProposalId(proposal.getProposalId());
        project.setParentId(proposal.getParentProjectId());
        project.setCompanyDeptId(proposal.getCompanyDeptId());
        project.setProjectName(proposal.getProjectName());
        project.setProjectType(proposal.getProjectType());
        project.setAccountingMode(proposal.getAccountingMode());
        project.setManagementMode(proposal.getManagementMode());
        project.setCloseMethod(proposal.getCloseMethod());
        project.setManagementReason(proposal.getManagementReason());
        project.setAcceptanceCriteria(proposal.getAcceptanceCriteria());
        project.setTemplateVersion(proposal.getTemplateVersion());
        project.setTemplateSnapshotJson(proposal.getTemplateSnapshotJson());
        project.setGoalMode(proposal.getGoalMode());
        project.setObjective(proposal.getObjective());
        project.setPlanStartDate(proposal.getPlanStartDate());
        project.setPlanEndDate(proposal.getPlanEndDate());
        project.setPriority(proposal.getPriority());
        project.setBaseCurrency(proposal.getBaseCurrency());
        project.setBudgetLimit(proposal.getBudgetLimit());
        project.setBudgetMode(proposal.getBudgetMode());
        project.setDailyBudgetLimit(proposal.getDailyBudgetLimit());
        project.setBudgetScope(proposal.getBudgetScope());
        project.setStartupBudgetLimit(proposal.getStartupBudgetLimit());
        project.setBudgetReason(proposal.getBudgetReason());
        project.setExecutionSource(proposal.getExecutionSource());
        project.setRemark(proposal.getApplicationReason());
        project.setMainOwnerUserId(proposal.getEffectiveOwnerUserId());
        validateProject(project);
        validateParent(project.getParentId(), null, proposal.getSponsorOwnerUserId());

        project.setProjectNo("XM" + DateUtils.dateTimeNow("yyyyMMddHHmmss")
            + IdUtils.fastSimpleUUID().substring(0, 4).toUpperCase());
        project.setMainOwnerName(displayName(owner));
        project.setApplicantUserId(proposal.getApplicantUserId());
        project.setApplicantName(proposal.getParentProjectId() == null ? displayName(owner) : proposal.getApplicantName());
        project.setSponsorOwnerUserId(proposal.getSponsorOwnerUserId());
        project.setSponsorOwnerName(displayName(sponsor));
        // 兼容旧字段；新权限和页面语义以 sponsorOwner 为准。
        project.setInitiatorUserId(proposal.getSponsorOwnerUserId());
        project.setInitiatorName(displayName(sponsor));
        project.setStatus("ACTIVE");
        project.setDeliveryPolicyVersion(BusinessProjectLifecycle.SEPARATED);
        project.setAccountingState("OPEN");
        project.setSettlementPolicyVersion("OWNER_CONFIRM_V1");
        boolean standardTemplate = proposal.getTemplateVersion()!=null && !"LEGACY_V1".equals(proposal.getTemplateVersion());
        project.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);
        project.setBaselineVersion(standardTemplate ? 1 : 0);
        project.setBaselineStatus("APPROVED");
        project.setActualStartDate(new Date());
        project.setCreateBy(reviewerUserName);
        mapper.insertProject(project);

        BusinessProjectMember ownerMember = new BusinessProjectMember();
        ownerMember.setProjectId(project.getProjectId());
        ownerMember.setUserId(project.getMainOwnerUserId());
        ownerMember.setUserNameSnapshot(project.getMainOwnerName());
        ownerMember.setMemberRole("OWNER");
        ownerMember.setStatus("0");
        ownerMember.setJoinedDate(standardTemplate ? proposal.getPlanStartDate() : new Date());
        ownerMember.setCreateBy(reviewerUserName);
        mapper.upsertMember(ownerMember);
        memberDays.saveRole(project.getProjectId(),ownerMember.getUserId(),ownerMember.getJoinedDate(),"OWNER",reviewerUserName);
        grantProjectUser(project.getMainOwnerUserId(), true);

        Set<Long> selectedMemberIds = new HashSet<Long>();
        selectedMemberIds.add(project.getMainOwnerUserId());
        for (Map<String, Object> line : proposal.getStaffingLines() == null
            ? Collections.<Map<String, Object>>emptyList() : proposal.getStaffingLines())
        {
            Object selectedValue = line.get("userId");
            if (selectedValue == null) continue;
            Long selectedUserId = selectedValue instanceof Number
                ? ((Number)selectedValue).longValue() : Long.valueOf(String.valueOf(selectedValue));
            if (!selectedMemberIds.add(selectedUserId)) continue;
            Map<String, Object> selectedUser = requireActiveUser(selectedUserId);
            BusinessProjectMember selectedMember = new BusinessProjectMember();
            selectedMember.setProjectId(project.getProjectId());
            selectedMember.setUserId(selectedUserId);
            selectedMember.setUserNameSnapshot(displayName(selectedUser));
            selectedMember.setMemberRole("MEMBER");
            selectedMember.setStatus("0");
            selectedMember.setJoinedDate(standardTemplate ? memberJoinedDate(proposal,line) : new Date());
            selectedMember.setCreateBy(reviewerUserName);
            selectedMember.setRemark("立项申请选择");
            mapper.upsertMember(selectedMember);
            memberDays.saveRole(project.getProjectId(),selectedMember.getUserId(),selectedMember.getJoinedDate(),"MEMBER",reviewerUserName);
            grantProjectUser(selectedUserId, false);
        }

        if (standardTemplate)
        {
            Map<String,Object> baseline=new LinkedHashMap<String,Object>();baseline.put("projectId",project.getProjectId());baseline.put("templateVersion",project.getTemplateVersion());baseline.put("baselineVersion",project.getBaselineVersion());
            baseline.put("authorizationSource",reviewerUserId.equals(proposal.getApplicantUserId())?"SELF_AUTHORIZED":"MANUAL_APPROVAL");
            baseline.put("userId",reviewerUserId);baseline.put("userName",reviewerUserName);
            try { baseline.put("snapshotJson",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(proposal)); }
            catch(Exception ex){throw new ServiceException("项目计划基线无法保存");}
            workMapper.insertBaseline(baseline);
            for(Map<String,Object> line:proposal.getStaffingLines()==null?Collections.<Map<String,Object>>emptyList():proposal.getStaffingLines())
            {
                Map<String,Object> assignment=new LinkedHashMap<String,Object>(line);assignment.put("effectiveFrom",line.get("planStartDate"));assignment.put("effectiveTo",line.get("planEndDate"));assignment.put("reason",line.get("note"));assignment.put("participationOnly",true);
                if (project.getParentId() == null) workService.saveAssignment(project.getProjectId(),assignment,reviewerUserId,reviewerUserName);
                else workService.saveInitialAssignment(project,assignment,reviewerUserId,reviewerUserName);
            }
        }

        Map<String, Object> history = new HashMap<String, Object>();
        history.put("projectId", project.getProjectId());
        history.put("toUserId", project.getMainOwnerUserId());
        history.put("toUserName", project.getMainOwnerName());
        history.put("reason", "负责人确认项目测算并自主启动项目");
        history.put("operatorUserId", reviewerUserId);
        history.put("operatorName", reviewerUserName);
        mapper.insertOwnerHistory(history);
        BigDecimal effectiveBudget = "DAILY".equals(project.getBudgetMode())
            ? project.getDailyBudgetLimit() : project.getBudgetLimit();
        if (effectiveBudget != null)
        {
            Map<String, Object> budgetHistory = new HashMap<String, Object>();
            budgetHistory.put("projectId", project.getProjectId());
            budgetHistory.put("toAmount", effectiveBudget);
            budgetHistory.put("currency", project.getBaseCurrency());
            budgetHistory.put("budgetMode", project.getBudgetMode());
            budgetHistory.put("budgetVersion", 1);
            budgetHistory.put("reason", "项目预算基线");
            budgetHistory.put("operatorUserId", reviewerUserId);
            budgetHistory.put("operatorName", reviewerUserName);
            mapper.insertBudgetHistory(budgetHistory);
        }
        addEvent(project.getProjectId(), "CREATE_FROM_PROPOSAL", null, "ACTIVE", reviewerUserId,
            reviewerUserName, "负责人确认项目测算并直接进入执行");
        syncExecutionSource(project, reviewerUserId, reviewerUserName);
        boolean operatorIsSponsor = reviewerUserId.equals(proposal.getSponsorOwnerUserId());
        return getProject(project.getProjectId(), reviewerUserId, SecurityUtils.isAdmin(reviewerUserId), operatorIsSponsor);
    }

    private Date memberJoinedDate(BusinessProjectProposal proposal, Map<String,Object> line)
    {
        Object value=line==null?null:line.get("planStartDate");
        Date parsed=value instanceof Date?(Date)value:DateUtils.parseDate(value);
        if(parsed!=null)return parsed;
        return proposal.getPlanStartDate()==null?new Date():proposal.getPlanStartDate();
    }

    /** 仅保留供历史代码编译参考；新项目创建必须走 createApprovedProject。 */
    private BusinessProject createLegacyProject(BusinessProject project, Long userId, String userName)
    {
        validateProject(project);
        Map<String, Object> owner = requireActiveUser(project.getMainOwnerUserId());
        Map<String, Object> initiator = requireActiveUser(userId);
        validateParent(project.getParentId(), null, userId);
        project.setProjectNo("XM" + DateUtils.dateTimeNow("yyyyMMddHHmmss")
            + IdUtils.fastSimpleUUID().substring(0, 4).toUpperCase());
        project.setMainOwnerName(displayName(owner));
        project.setInitiatorUserId(userId);
        project.setInitiatorName(displayName(initiator));
        project.setStatus("DRAFT");
        project.setBaselineStatus("DRAFT");
        project.setCreateBy(userName);
        mapper.insertProject(project);

        BusinessProjectMember ownerMember = new BusinessProjectMember();
        ownerMember.setProjectId(project.getProjectId());
        ownerMember.setUserId(project.getMainOwnerUserId());
        ownerMember.setUserNameSnapshot(project.getMainOwnerName());
        ownerMember.setMemberRole("OWNER");
        ownerMember.setStatus("0");
        ownerMember.setJoinedDate(new Date());
        ownerMember.setCreateBy(userName);
        mapper.upsertMember(ownerMember);
        grantProjectUser(project.getMainOwnerUserId(), true);

        Map<String, Object> history = new HashMap<String, Object>();
        history.put("projectId", project.getProjectId());
        history.put("toUserId", project.getMainOwnerUserId());
        history.put("toUserName", project.getMainOwnerName());
        history.put("reason", "项目创建时任命");
        history.put("operatorUserId", userId);
        history.put("operatorName", userName);
        mapper.insertOwnerHistory(history);
        if (project.getBudgetLimit() != null)
        {
            Map<String, Object> budgetHistory = new HashMap<String, Object>();
            budgetHistory.put("projectId", project.getProjectId());
            budgetHistory.put("toAmount", project.getBudgetLimit());
            budgetHistory.put("currency", project.getBaseCurrency());
            budgetHistory.put("budgetVersion", 1);
            budgetHistory.put("reason", "立项预算");
            budgetHistory.put("operatorUserId", userId);
            budgetHistory.put("operatorName", userName);
            mapper.insertBudgetHistory(budgetHistory);
        }
        addEvent(project.getProjectId(), "CREATE", null, "DRAFT", userId, userName, "创建项目并任命负责人");
        syncExecutionSource(project, userId, userName);
        return getProject(project.getProjectId(), userId, SecurityUtils.isAdmin(userId), true);
    }

    @Override
    @Transactional
    public BusinessProject updateProject(BusinessProject input, Long userId, String userName, boolean boss)
    {
        BusinessProject current = requireProjectForUpdate(input.getProjectId());
        requireManage(current, userId, boss);
        ensureMutable(current);
        if (!java.util.Objects.equals(input.getParentId(), current.getParentId()))
            throw new ServiceException("归属主项目不可修改");
        input.setBaseCurrency(current.getBaseCurrency());
        input.setBudgetLimit(current.getBudgetLimit());
        if (StringUtils.isBlank(input.getManagementMode())) input.setManagementMode(current.getManagementMode());
        if (StringUtils.isBlank(input.getCloseMethod())) input.setCloseMethod(effectiveCloseMethod(current));
        if (input.getManagementReason() == null) input.setManagementReason(current.getManagementReason());
        if (input.getAcceptanceCriteria() == null) input.setAcceptanceCriteria(current.getAcceptanceCriteria());
        if ((BusinessMemberDayCostService.enabled(current)||"ACTUAL_WORK_V1".equals(current.getCostPolicyVersion()))
            && (!java.util.Objects.equals(input.getObjective(),current.getObjective())
                || !java.util.Objects.equals(input.getPlanStartDate(),current.getPlanStartDate())
                || !java.util.Objects.equals(input.getPlanEndDate(),current.getPlanEndDate())
                || !java.util.Objects.equals(input.getAcceptanceCriteria(),current.getAcceptanceCriteria())
                || !java.util.Objects.equals(input.getManagementMode(),current.getManagementMode())
                || !java.util.Objects.equals(input.getCloseMethod(),current.getCloseMethod())))
            throw new ServiceException("范围、日期和验收基线须通过计划变更办理，模板治理方式保持版本冻结");
        if (StringUtils.isBlank(input.getGoalMode())) input.setGoalMode(current.getGoalMode());
        boolean governanceChanged = !normalizeManagementMode(current.getManagementMode()).equals(normalizeManagementMode(input.getManagementMode()))
            || !effectiveCloseMethod(current).equals(input.getCloseMethod());
        boolean goalModeChanged = !effectiveGoalMode(current).equals(input.getGoalMode());
        if (goalModeChanged && Arrays.asList("ACTIVE", "PAUSED", "ACCEPTANCE").contains(current.getStatus()))
        {
            if (StringUtils.isBlank(input.getGoalModeChangeReason()))
                throw new ServiceException("执行中的项目调整目标模式时必须填写变更原因");
            if (input.getGoalModeChangeReason().length() > 500)
                throw new ServiceException("目标模式变更原因不能超过500个字符");
            if ("ACCEPTANCE".equals(current.getStatus())) throw new ServiceException("验收中的项目不能调整目标模式");
        }
        if (governanceChanged && Arrays.asList("ACTIVE", "PAUSED", "ACCEPTANCE").contains(current.getStatus()))
        {
            if (StringUtils.isBlank(input.getGovernanceChangeReason())) throw new ServiceException("执行中的项目调整管理模式或结项方式时必须填写变更原因");
            if (input.getGovernanceChangeReason().length() > 500) throw new ServiceException("治理方式变更原因不能超过500个字符");
            if ("ACCEPTANCE".equals(current.getStatus())) throw new ServiceException("验收中的项目不能调整管理模式或结项方式");
            if (closeMethodRank(input.getCloseMethod()) < closeMethodRank(effectiveCloseMethod(current))
                && hasAcceptanceRecords(current.getProjectId()))
                throw new ServiceException("项目已有验收记录，不能降低结项管控要求");
        }
        if (!boss)
        {
            if (Arrays.asList("ACCEPTANCE", "CLOSED", "CANCELED").contains(current.getStatus()))
                throw new ServiceException("验收中或已结束的项目不能由负责人修改资料");
            input.setParentId(current.getParentId());
            input.setProjectType(current.getProjectType());
            input.setAccountingMode(current.getAccountingMode());
            input.setCompanyDeptId(current.getCompanyDeptId());
        }
        validateProject(input);
        validateParent(input.getParentId(), current.getProjectId(), projectSponsorUserId(current));
        input.setVersion(current.getVersion());
        input.setUpdateBy(userName);
        if (mapper.updateProject(input) != 1) throw changed();
        addEvent(current.getProjectId(), governanceChanged ? "GOVERNANCE_CHANGE" : goalModeChanged ? "GOAL_MODE_CHANGE" : "EDIT",
            current.getStatus(), current.getStatus(), userId, userName,
            governanceChanged ? input.getGovernanceChangeReason()
                : goalModeChanged ? input.getGoalModeChangeReason() : "更新项目资料");
        if (boss && input.getExecutionSource() != null) syncExecutionSource(input, userId, userName);
        return getProject(current.getProjectId(), userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    public Map<String, Object> operatingConfig(Long projectId, Long userId, boolean viewAll, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireAccess(project, userId, viewAll, boss);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", projectId);
        result.put("budgetLimit", project.getBudgetLimit());
        result.put("budgetMode", project.getBudgetMode());
        result.put("dailyBudgetLimit", project.getDailyBudgetLimit());
        result.put("budgetScope", project.getBudgetScope());
        result.put("startupBudgetLimit", project.getStartupBudgetLimit());
        result.put("budgetReason", project.getBudgetReason());
        result.put("currency", project.getBaseCurrency());
        result.put("budgetHistory", mapper.selectBudgetHistory(projectId));
        result.put("kpis", mapper.selectProjectKpis(projectId));
        List<Map<String, Object>> allocations = mapper.selectProjectStaffAllocations(projectId);
        if (!boss && !viewAll)
        {
            for (Map<String, Object> row : allocations)
            {
                row.remove("unitCost");
                row.remove("costMode");
                row.remove("costPolicyId");
                row.remove("policyVersion");
                row.remove("exceptionReason");
            }
        }
        result.put("staffAllocations", allocations);
        result.put("rawCostVisible", boss || viewAll);
        Map<String, Object> relation = mapper.selectActiveExecutionRelation(projectId);
        if (relation != null && "LIVE".equals(String.valueOf(relation.get("sourceDomain"))))
        {
            Map<String, Object> summary = mapper.selectLiveExecutionSummary(relation);
            if (summary == null) summary = new LinkedHashMap<String, Object>();
            summary.put("sourceDomain", "LIVE");
            summary.put("sourceName", "直播数据管理");
            summary.put("readOnly", true);
            result.put("executionSummary", summary);
        }
        return result;
    }

    private void syncExecutionSource(BusinessProject project, Long userId, String userName)
    {
        Map<String, Object> current = mapper.selectActiveExecutionRelation(project.getProjectId());
        boolean hasCurrent = current != null && current.get("relationId") != null;
        boolean wantsLive = "LIVE".equals(project.getExecutionSource());
        if (wantsLive && !hasCurrent)
        {
            Date effectiveFrom = project.getPlanStartDate() == null ? new Date() : project.getPlanStartDate();
            mapper.insertExecutionRelation(project.getProjectId(), effectiveFrom,
                "LIVE:BUSINESS_SCOPE:ALL:EXECUTION_SOURCE", userName);
            addEvent(project.getProjectId(), "SOURCE_LINK", null, "LIVE", userId, userName,
                "关联直播执行系统（只读数据源）");
        }
        else if (!wantsLive && hasCurrent)
        {
            mapper.retireExecutionRelation(Long.valueOf(String.valueOf(current.get("relationId"))), new Date(), userName);
            addEvent(project.getProjectId(), "SOURCE_UNLINK", "LIVE", null, userId, userName,
                "解除直播执行系统关联");
        }
    }

    @Override
    @Transactional
    public BusinessProject updateBudget(Long projectId, BigDecimal budgetLimit, String currency, String reason,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        if(BusinessMemberDayCostService.enabled(project)||"ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()))throw new ServiceException("本项目预算基线请通过计划变更办理");
        requireMainOwnerOrBoss(project, userId, boss);
        ensureMutable(project);
        if (budgetLimit == null || budgetLimit.compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算金额不能为空或为负数");
        if (StringUtils.isBlank(reason)) throw new ServiceException("请填写预算调整原因");
        if (StringUtils.isBlank(currency)) currency = project.getBaseCurrency();
        currency = currency.trim().toUpperCase();
        validateCurrency(currency);
        if (!currency.equals(project.getBaseCurrency()))
            throw new ServiceException("项目产生后不能通过预算调整修改基础币种");
        List<Map<String, Object>> historyRows = mapper.selectBudgetHistory(projectId);
        int budgetVersion = historyRows == null ? 1 : historyRows.size() + 1;
        if (mapper.updateProjectBudget(projectId, budgetLimit, currency, userName, project.getVersion()) != 1)
            throw changed();
        BigDecimal previousAmount = "DAILY".equals(project.getBudgetMode())
            ? project.getDailyBudgetLimit() : project.getBudgetLimit();
        String resultingMode = "NONE".equals(project.getBudgetMode()) ? "TOTAL" : project.getBudgetMode();
        Map<String, Object> history = new HashMap<String, Object>();
        history.put("projectId", projectId); history.put("fromAmount", previousAmount);
        history.put("toAmount", budgetLimit); history.put("currency", currency);
        history.put("budgetMode", resultingMode);
        history.put("budgetVersion", budgetVersion); history.put("reason", reason.trim());
        history.put("operatorUserId", userId); history.put("operatorName", userName);
        mapper.insertBudgetHistory(history);
        addEvent(projectId, "BUDGET_CHANGE", project.getStatus(), project.getStatus(), userId, userName,
            ("DAILY".equals(resultingMode) ? "每日预算上限调整为 " : "项目总额预算调整为 ")
                + budgetLimit.toPlainString() + " " + currency + "：" + reason.trim());
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProjectKpi saveKpi(BusinessProjectKpi kpi, Long userId, String userName, boolean boss)
    {
        if (kpi == null || kpi.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProjectForUpdate(kpi.getProjectId());
        requireMainOwnerOrBoss(project, userId, boss); ensureMutable(project);
        BusinessProjectKpi previous = null;
        if (kpi.getKpiId() != null)
        {
            previous = mapper.selectProjectKpiById(kpi.getKpiId());
            if (previous == null || !project.getProjectId().equals(previous.getProjectId()) || !"CURRENT".equals(previous.getStatus()))
                throw new ServiceException("KPI当前版本不存在，请刷新后重试");
            kpi.setKpiCode(previous.getKpiCode());
        }
        else
        {
            // 编码是系统内部稳定标识，禁止页面、接口或AI自行命名。
            kpi.setKpiCode(generateKpiCode(project.getProjectId()));
        }
        if (StringUtils.isBlank(kpi.getKpiName())) throw new ServiceException("请填写KPI名称");
        kpi.setKpiCode(kpi.getKpiCode().trim().toUpperCase());
        if (!kpi.getKpiCode().matches("[A-Z0-9_\\-]{2,64}")) throw new ServiceException("KPI编码只能使用字母、数字、下划线或短横线");
        if (StringUtils.isBlank(kpi.getMetricType())) kpi.setMetricType("COUNT");
        if (!KPI_METRIC_TYPES.contains(kpi.getMetricType())) throw new ServiceException("KPI指标类型不正确");
        if (StringUtils.isBlank(kpi.getPeriodType())) kpi.setPeriodType("PROJECT");
        if (!KPI_PERIOD_TYPES.contains(kpi.getPeriodType())) throw new ServiceException("KPI统计周期不正确");
        if (kpi.getTargetValue() == null || kpi.getTargetValue().compareTo(BigDecimal.ZERO) <= 0)
            throw new ServiceException("KPI目标值必须大于0");
        if (kpi.getWeight() == null) kpi.setWeight(BigDecimal.ZERO);
        if (kpi.getWeight().compareTo(BigDecimal.ZERO) < 0 || kpi.getWeight().compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException("KPI权重必须在0到100之间");
        // 第一阶段KPI只考核项目，不设置个人考核对象或个人奖金领取人。
        kpi.setOwnerUserId(null);
        kpi.setOwnerName(null);
        // 实际结果通过负责人填报并形成结算快照，不能在目标定义中直接写入。
        kpi.setActualValue(null);
        if (kpi.getEffectiveFrom() == null) kpi.setEffectiveFrom(new Date());
        if (kpi.getEffectiveTo() != null && kpi.getEffectiveTo().before(kpi.getEffectiveFrom()))
            throw new ServiceException("KPI失效日期不能早于生效日期");
        if (StringUtils.isBlank(kpi.getDirection())) kpi.setDirection("HIGHER_BETTER");
        if (StringUtils.isBlank(kpi.getAggregateType())) kpi.setAggregateType("SUM");
        if (StringUtils.isBlank(kpi.getSourceType())) kpi.setSourceType("MANUAL");
        kpi.setSourceType(kpi.getSourceType().trim().toUpperCase());
        if (!KPI_SOURCE_TYPES.contains(kpi.getSourceType())) throw new ServiceException("KPI数据来源不正确");
        validateKpiSource(project.getProjectId(), kpi);
        if (kpi.getPrecisionScale() == null) kpi.setPrecisionScale(2);
        if (previous != null && mapper.retireProjectKpi(previous.getKpiId(), userName) != 1) throw changed();
        kpi.setKpiId(null);
        kpi.setTargetVersion(mapper.selectNextKpiVersion(project.getProjectId(), kpi.getKpiCode()));
        kpi.setStatus("CURRENT"); kpi.setCreateBy(userName);
        mapper.insertProjectKpi(kpi);
        addEvent(project.getProjectId(), "KPI_CHANGE", project.getStatus(), project.getStatus(), userId, userName,
            kpi.getKpiName() + " v" + kpi.getTargetVersion());
        return kpi;
    }

    private void validateKpiSource(Long projectId, BusinessProjectKpi kpi)
    {
        if ("MANUAL".equals(kpi.getSourceType()) || Arrays.asList("REVENUE", "BUSINESS_COST",
            "PERSONNEL_COST", "PROFIT").contains(kpi.getSourceType()))
        {
            kpi.setSourceRefId(null);
            return;
        }
        if ("ROUTINE".equals(kpi.getSourceType()))
        {
            if (kpi.getSourceRefId() == null) throw new ServiceException("请选择要自动汇总的持续工作");
            BusinessProjectRoutine routine = mapper.selectRoutineById(kpi.getSourceRefId());
            if (routine == null || !projectId.equals(routine.getProjectId())) throw new ServiceException("持续工作不属于当前项目");
            return;
        }
        if (kpi.getSourceRefId() == null) return;
        if ("TASK".equals(kpi.getSourceType()))
        {
            BusinessProjectTask task = mapper.selectTaskById(kpi.getSourceRefId());
            if (task == null || !projectId.equals(task.getProjectId())) throw new ServiceException("任务不属于当前项目");
            return;
        }
        BusinessProjectMilestone milestone = mapper.selectMilestoneById(kpi.getSourceRefId());
        if (milestone == null || !projectId.equals(milestone.getProjectId())) throw new ServiceException("里程碑不属于当前项目");
    }

    private String generateKpiCode(Long projectId)
    {
        for (int attempt = 0; attempt < 5; attempt++)
        {
            String code = "KPI_P" + projectId + "_"
                + IdUtils.fastSimpleUUID().substring(0, 12).toUpperCase();
            if (mapper.selectCurrentProjectKpi(projectId, code) == null) return code;
        }
        throw new ServiceException("KPI编码生成失败，请重试");
    }

    @Override
    @Transactional
    public void retireKpi(Long projectId, Long kpiId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireMainOwnerOrBoss(project, userId, boss); ensureMutable(project);
        BusinessProjectKpi kpi = mapper.selectProjectKpiById(kpiId);
        if (kpi == null || !projectId.equals(kpi.getProjectId()) || mapper.retireProjectKpi(kpiId, userName) != 1)
            throw new ServiceException("KPI当前版本不存在");
        addEvent(projectId, "KPI_RETIRE", project.getStatus(), project.getStatus(), userId, userName, kpi.getKpiName());
    }

    @Override
    public List<BusinessStaffCostPolicy> staffCostPolicies(Long staffUserId, Long userId, boolean staffCostManager)
    {
        boolean administrator = SecurityUtils.isAdmin(userId);
        boolean companyOwner = requireStaffCostManager(userId, staffCostManager);
        if (administrator) requireCostEligibleUser(staffUserId);
        else requireActiveUser(staffUserId);
        if (!administrator) requireStaffCostScope(staffUserId, userId, companyOwner, staffCostManager, false);
        return mapper.selectStaffCostPolicies(staffUserId);
    }

    @Override
    public List<Map<String,Object>> staffCostOptions(Long userId,boolean staffCostManager)
    {
        boolean companyOwner=requireStaffCostManager(userId,staffCostManager);
        Map<String,Object> query=new HashMap<String,Object>();query.put("userId",userId);query.put("administrator",SecurityUtils.isAdmin(userId));query.put("companyOwner",companyOwner);
        query.put("financeManager",staffCostManager&&mapper.countUserRoleByKey(userId,"finance_cost_manager")>0);query.put("companyDeptId",mapper.selectStaffCompanyId(userId));
        List<Map<String,Object>> rows=mapper.selectStaffCostOptions(query);for(Map<String,Object> row:rows){row.put("rawCostVisible",true);row.put("canManageCost",true);}return rows;
    }

    @Override
    @Transactional
    public BusinessStaffCostPolicy saveStaffCostPolicy(BusinessStaffCostPolicy policy,
        Long userId, String userName, boolean staffCostManager)
    {
        boolean administrator = SecurityUtils.isAdmin(userId);
        boolean companyOwner = requireStaffCostManager(userId, staffCostManager);
        if (policy == null || policy.getUserId() == null) throw new ServiceException("请选择人员");
        mapper.lockStaffCostPerson(policy.getUserId());
        if (administrator) requireCostEligibleUser(policy.getUserId());
        else requireActiveUser(policy.getUserId());
        if (!administrator) requireStaffCostScope(policy.getUserId(), userId, companyOwner, staffCostManager, true);
        if (policy.getUnitCost() == null || policy.getUnitCost().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("内部成本单价不能为空或为负数");
        if (policy.getUnitCost().stripTrailingZeros().scale() > 4 || policy.getUnitCost().compareTo(new BigDecimal("10000000000")) >= 0)
            throw new ServiceException("内部费率最多保留4位小数，且须小于100亿元");
        String countryRegion = mapper.selectStaffCountryRegion(policy.getUserId());
        String mode=StringUtils.defaultIfEmpty(policy.getCostMode(),"MONTHLY").toUpperCase(java.util.Locale.ROOT);
        if(!Arrays.asList("MONTHLY","DAILY","HOURLY").contains(mode))throw new ServiceException("内部费率单位只支持月、日或小时");
        if("MONTHLY".equals(mode))
        {
            if("CN".equals(countryRegion))policy.setStandardWorkDays(CHINA_STANDARD_WORK_DAYS);
            else if("VN".equals(countryRegion))policy.setStandardWorkDays(VIETNAM_STANDARD_WORK_DAYS);
            else throw new ServiceException("该人员的国家/地区未配置月度成本折算规则，请先在人员管理中设置为中国或越南");
        }
        else policy.setStandardWorkDays(null);
        policy.setCountryRegion(countryRegion);
        policy.setCostMode(mode);
        // New versions use the fixed eight-hour basis; historical snapshots retain their stored basis.
        policy.setRateMinutesPerDay(480);
        policy.setCurrency(StringUtils.defaultIfEmpty(policy.getCurrency(),"CNY").toUpperCase(java.util.Locale.ROOT));
        validateCurrency(policy.getCurrency());
        if (policy.getEffectiveFrom() == null) throw new ServiceException("请选择生效日期");
        if (policy.getEffectiveTo() != null && policy.getEffectiveTo().before(policy.getEffectiveFrom()))
            throw new ServiceException("失效日期不能早于生效日期");
        mapper.closeOpenEndedStaffCostPolicy(policy.getUserId(), policy.getEffectiveFrom());
        if (mapper.countOverlappingStaffCostPolicy(policy.getUserId(), policy.getEffectiveFrom(), policy.getEffectiveTo()) > 0)
            throw new ServiceException("该人员在所选日期已有内部成本政策，请使用不重叠的生效区间");
        policy.setPolicyVersion(mapper.selectNextStaffCostVersion(policy.getUserId()));
        policy.setStatus("ACTIVE"); policy.setCreateBy(userName);
        mapper.insertStaffCostPolicy(policy);
        return policy;
    }

    @Override
    @Transactional
    public List<BusinessStaffCostPolicy> saveStaffCostPolicies(List<BusinessStaffCostPolicy> policies,
        Long userId, String userName, boolean staffCostManager)
    {
        if (policies == null || policies.isEmpty()) throw new ServiceException("请选择要设置成本的人员");
        if (policies.size() > 200) throw new ServiceException("单次最多设置200名人员的成本");
        Map<Long, Boolean> userIds = new HashMap<Long, Boolean>();
        for (BusinessStaffCostPolicy policy : policies)
        {
            if (policy == null || policy.getUserId() == null) throw new ServiceException("请选择人员");
            if (userIds.put(policy.getUserId(), Boolean.TRUE) != null)
                throw new ServiceException("批量设置中存在重复人员");
        }
        List<BusinessStaffCostPolicy> saved = new ArrayList<BusinessStaffCostPolicy>();
        for (BusinessStaffCostPolicy policy : policies)
            saved.add(saveStaffCostPolicy(policy, userId, userName, staffCostManager));
        return saved;
    }

    @Override
    @Transactional
    public void deleteStaffCostPolicy(Long policyId, Long userId, String userName, boolean staffCostManager)
    {
        BusinessStaffCostPolicy policy = requireManageableStaffCostPolicy(policyId, userId, staffCostManager);
        if (!"ACTIVE".equals(policy.getStatus())) throw new ServiceException("成本版本已作废，不能删除");
        if (policy.getEffectiveFrom() == null || !policy.getEffectiveFrom().after(DateUtils.getNowDate()))
            throw new ServiceException("已生效的成本版本不能删除，请改为作废");
        if (policy.getReferenceCount() != null && policy.getReferenceCount() > 0)
            throw new ServiceException("成本版本已被项目投入引用，不能删除，请改为作废");
        if (mapper.deleteUnusedFutureStaffCostPolicy(policyId) != 1)
            throw new ServiceException("成本版本已生效、已被引用或状态已变化，请刷新后重试");
        mapper.restorePrecedingStaffCostPolicy(policy.getUserId(), policy.getEffectiveFrom());
    }

    @Override
    @Transactional
    public void voidStaffCostPolicy(Long policyId, String reason,
        Long userId, String userName, boolean staffCostManager)
    {
        if (StringUtils.isBlank(reason)) throw new ServiceException("请填写作废原因");
        reason = reason.trim();
        if (reason.length() > 500) throw new ServiceException("作废原因不能超过500个字符");
        BusinessStaffCostPolicy policy = requireManageableStaffCostPolicy(policyId, userId, staffCostManager);
        if (!"ACTIVE".equals(policy.getStatus())) throw new ServiceException("成本版本已作废，请勿重复操作");
        if (mapper.voidStaffCostPolicy(policyId, reason, userId, userName) != 1)
            throw new ServiceException("成本版本状态已变化，请刷新后重试");
    }

    private BusinessStaffCostPolicy requireManageableStaffCostPolicy(Long policyId, Long userId,
        boolean staffCostManager)
    {
        if (policyId == null) throw new ServiceException("成本版本ID不能为空");
        boolean administrator = SecurityUtils.isAdmin(userId);
        boolean companyOwner = requireStaffCostManager(userId, staffCostManager);
        BusinessStaffCostPolicy policy = mapper.selectStaffCostPolicyById(policyId);
        if (policy == null) throw new ServiceException("成本版本不存在");
        if (administrator) requireCostEligibleUser(policy.getUserId());
        else
        {
            requireActiveUser(policy.getUserId());
            requireStaffCostScope(policy.getUserId(), userId, companyOwner, staffCostManager, false);
        }
        return policy;
    }

    private boolean requireStaffCostManager(Long userId, boolean staffCostManager)
    {
        if (SecurityUtils.isAdmin(userId)) return false;
        boolean companyOwner = staffCostManager && mapper.countUserRoleByKey(userId, "company_owner") > 0;
        boolean projectOwner = !mapper.selectManagedProjectMemberUserIds(userId).isEmpty();
        boolean financeManager=staffCostManager&&mapper.countUserRoleByKey(userId,"finance_cost_manager")>0;
        if (!companyOwner && !projectOwner && !financeManager)
            throw new ServiceException("只有项目负责人或具备成本权限的公司、财务负责人可以维护用人成本");
        // 公司负责人继续受本人公司范围限制；纯项目负责人只获得成本维护能力。
        return companyOwner;
    }

    private void requireStaffCostCompanyOwner(Long staffUserId, Long operatorUserId, boolean lockForUpdate)
    {
        Long companyLeaderUserId = mapper.selectStaffCompanyLeaderUserId(staffUserId, lockForUpdate);
        if (companyLeaderUserId == null)
            throw new ServiceException("该人员所属公司尚未配置负责人，暂时无法维护人员成本");
        if (!companyLeaderUserId.equals(operatorUserId))
            throw new ServiceException("只能查看和设置本人负责公司的人员内部核算成本");
    }

    @Override
    @Transactional
    public BusinessProjectStaffAllocation saveStaffAllocation(BusinessProjectStaffAllocation allocation,
        Long userId, String userName, boolean boss)
    {
        if (allocation == null || allocation.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProjectForUpdate(allocation.getProjectId());
        requireLegacyEffortPolicy(project);
        boolean administrator = SecurityUtils.isAdmin(userId);
        boolean bossExceptionApproval = !administrator && boss;
        if (bossExceptionApproval)
        {
            requireBoss(project, userId, true);
            if (!"1".equals(allocation.getExceptionAllowed()) || StringUtils.isBlank(allocation.getExceptionReason()))
                throw new ServiceException("老板不能代替项目主负责人设置正常投入，只能审批超过100%的例外申请");
        }
        else requireAllocationOwner(project, userId, boss);
        ensureMutable(project);
        String allocationRole = allocation.getUserId() == null ? null
            : mapper.selectMemberRole(project.getProjectId(), allocation.getUserId());
        if (allocationRole == null || "OBSERVER".equals(allocationRole))
            throw new ServiceException("成本分摊人员必须是当前项目成员");
        if (!"PERCENTAGE".equals(allocation.getAllocationMode()))
            throw new ServiceException("人员成本只支持按项目投入比例分摊");
        if (allocation.getAllocationValue() == null || allocation.getAllocationValue().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("成本分摊参数不能为空或为负数");
        if (allocation.getAllocationValue().compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException("单个项目投入比例不能超过100%");
        if (allocation.getEffectiveFrom() == null) throw new ServiceException("请选择分摊生效日期");
        if (allocation.getEffectiveTo() != null && allocation.getEffectiveTo().before(allocation.getEffectiveFrom()))
            throw new ServiceException("分摊失效日期不能早于生效日期");
        if (mapper.countOverlappingProjectAllocation(project.getProjectId(), allocation.getUserId(),
            allocation.getEffectiveFrom(), allocation.getEffectiveTo(), allocation.getAllocationId()) > 0)
            throw new ServiceException("该人员在本项目所选日期已有计划投入，请编辑原记录或使用不重叠日期");
        BusinessStaffCostPolicy policy = mapper.selectEffectiveStaffCostPolicy(allocation.getUserId(), allocation.getEffectiveFrom());
        if (policy == null || !allocation.getUserId().equals(policy.getUserId()))
            throw new ServiceException("该人员在分摊生效日没有可用的内部成本政策");
        allocation.setCostPolicyId(policy.getPolicyId());
        if ("PERCENTAGE".equals(allocation.getAllocationMode()))
        {
            BigDecimal used = mapper.sumOverlappingAllocationPercent(allocation.getUserId(), allocation.getEffectiveFrom(),
                allocation.getEffectiveTo(), allocation.getAllocationId());
            boolean exceeds = used.add(allocation.getAllocationValue()).compareTo(new BigDecimal("100")) > 0;
            if (exceeds)
            {
                if (!bossExceptionApproval && !administrator)
                    throw new ServiceException("该人员同期跨项目计划投入超过100%，请调整后再保存或提交老板例外审批");
            }
            else if (bossExceptionApproval)
                throw new ServiceException("当前投入合计未超过100%，正常投入必须由项目主负责人设置");
        }
        if (!bossExceptionApproval && !administrator)
        {
            allocation.setExceptionAllowed("0");
            allocation.setExceptionReason(null);
        }
        allocation.setUserName(displayName(requireActiveUser(allocation.getUserId())));
        BusinessProjectStaffAllocation previous = null;
        if (allocation.getAllocationId() == null)
        {
            allocation.setStatus("ACTIVE"); allocation.setVersion(0); allocation.setCreateBy(userName);
            mapper.insertProjectStaffAllocation(allocation);
        }
        else
        {
            previous = mapper.selectProjectStaffAllocationById(allocation.getAllocationId());
            if (previous == null || !project.getProjectId().equals(previous.getProjectId())) throw new ServiceException("成本分摊记录不存在");
            allocation.setUpdateBy(userName);
            if (mapper.updateProjectStaffAllocation(allocation) != 1) throw changed();
        }
        recalculateTodayWhenAllocationAffected(project.getProjectId(), previous, allocation, userName);
        addEvent(project.getProjectId(), "COST_ALLOCATION", project.getStatus(), project.getStatus(), userId, userName,
            allocation.getUserName() + " / " + allocation.getAllocationMode() + " / " + allocation.getAllocationValue());
        return allocation;
    }

    @Override
    @Transactional
    public void removeStaffAllocation(Long projectId, Long allocationId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireLegacyEffortPolicy(project); requireAllocationOwner(project, userId, boss); ensureMutable(project);
        BusinessProjectStaffAllocation current = mapper.selectProjectStaffAllocationById(allocationId);
        if (current == null || !projectId.equals(current.getProjectId())) throw new ServiceException("成本分摊记录不存在");
        if (mapper.voidProjectStaffAllocation(projectId, allocationId, userName) != 1)
            throw new ServiceException("成本分摊记录不存在");
        recalculateTodayWhenAllocationAffected(projectId, current, null, userName);
        addEvent(projectId, "COST_ALLOCATION_VOID", project.getStatus(), project.getStatus(), userId, userName,
            "停用分摊记录 " + allocationId);
    }

    @Override
    @Transactional
    public BusinessProject changeOwner(Long projectId, Long newOwnerUserId, String reason,
        Long userId, String userName, boolean boss)
    {
        if (!boss) throw new ServiceException("只有老板可以更换项目主负责人");
        if (StringUtils.isBlank(reason)) throw new ServiceException("负责人变更原因不能为空");
        BusinessProject project = requireProjectForUpdate(projectId);
        requireBoss(project, userId, boss);
        ensureMutable(project);
        if (project.getMainOwnerUserId().equals(newOwnerUserId)) throw new ServiceException("新负责人不能与当前负责人相同");
        Map<String, Object> newOwner = requireActiveUser(newOwnerUserId);
        String newOwnerPreviousRole = mapper.selectMemberRole(projectId, newOwnerUserId);
        String newOwnerName = displayName(newOwner);
        if(BusinessMemberDayCostService.enabled(project)){
            Date effective=DateUtils.parseDate(DateUtils.getDate());
            memberDays.archiveMembership(projectId,newOwnerUserId);
            memberDays.saveRole(projectId,newOwnerUserId,effective,"OWNER",userName);
            memberDays.saveRole(projectId,project.getMainOwnerUserId(),effective,"MEMBER",userName);
        }
        if (mapper.updateProjectOwner(projectId, newOwnerUserId, newOwnerName, userName, project.getVersion()) != 1)
            throw changed();

        BusinessProjectMember oldOwner = new BusinessProjectMember();
        oldOwner.setProjectId(projectId);
        oldOwner.setUserId(project.getMainOwnerUserId());
        oldOwner.setUserNameSnapshot(project.getMainOwnerName());
        oldOwner.setMemberRole("MEMBER");
        oldOwner.setJoinedDate(new Date());
        oldOwner.setCreateBy(userName);
        mapper.upsertMember(oldOwner);

        BusinessProjectMember ownerMember = new BusinessProjectMember();
        ownerMember.setProjectId(projectId);
        ownerMember.setUserId(newOwnerUserId);
        ownerMember.setUserNameSnapshot(newOwnerName);
        ownerMember.setMemberRole("OWNER");
        ownerMember.setJoinedDate(new Date());
        ownerMember.setCreateBy(userName);
        mapper.upsertMember(ownerMember);
        grantProjectUser(newOwnerUserId, true);
        if ("DEPUTY".equals(newOwnerPreviousRole)) syncProjectDeputyRole(newOwnerUserId);

        Map<String, Object> history = new HashMap<String, Object>();
        history.put("projectId", projectId);
        history.put("fromUserId", project.getMainOwnerUserId());
        history.put("fromUserName", project.getMainOwnerName());
        history.put("toUserId", newOwnerUserId);
        history.put("toUserName", newOwnerName);
        history.put("reason", reason);
        history.put("operatorUserId", userId);
        history.put("operatorName", userName);
        mapper.insertOwnerHistory(history);
        addEvent(projectId, "OWNER_CHANGE", project.getStatus(), project.getStatus(), userId, userName, reason);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject submitAcceptance(Long projectId, BusinessProjectAcceptance acceptance,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireAccess(project, userId, SecurityUtils.isAdmin(userId), boss);
        requireOwnerOrBoss(mapper.selectMemberRole(projectId, userId), project, userId, boss);
        requireStatus(project, "ACTIVE");
        if (!"RESULT_ACCEPTANCE".equals(effectiveCloseMethod(project)))
            throw new ServiceException("只有选择成果验收的项目需要提交整体验收资料");
        if ("KEY_CONTROL".equals(normalizeManagementMode(project.getManagementMode()))) ensureKeyMilestonesReady(projectId);
        ensureReadyForAcceptance(projectId);
        ensureKpiReadyForClose(projectId);
        if (acceptance == null || StringUtils.isBlank(acceptance.getResultSummary()))
            throw new ServiceException("请填写项目结果摘要");
        if (StringUtils.isBlank(acceptance.getDeliverables())) throw new ServiceException("请填写交付成果说明");
        if (acceptance.getResultSummary().length() > 2000) throw new ServiceException("项目结果摘要不能超过2000个字符");
        if (acceptance.getDeliverables().length() > 4000) throw new ServiceException("交付成果说明不能超过4000个字符");
        if (StringUtils.isNotEmpty(acceptance.getAttachmentUrls()) && acceptance.getAttachmentUrls().length() > 4000)
            throw new ServiceException("验收附件数量或地址长度超出限制");
        businessFileService.validateReferences(acceptance.getAttachmentUrls(), projectId, userId, boss, SecurityUtils.isAdmin(userId));
        Map<String, Object> submitter = requireActiveUser(userId);
        acceptance.setProjectId(projectId);
        acceptance.setSubmissionVersion(mapper.selectNextAcceptanceVersion(projectId));
        acceptance.setSubmittedUserId(userId);
        acceptance.setSubmittedUserName(displayName(submitter));
        acceptance.setReviewStatus("PENDING");
        acceptance.setCreateBy(userName);
        if (mapper.insertAcceptance(acceptance) != 1) throw new ServiceException("提交验收资料失败");
        if (mapper.updateProjectStatus(projectId, "ACTIVE", "ACCEPTANCE", null, false, userName, project.getVersion()) != 1)
            throw changed();
        addEvent(projectId, "REQUEST_ACCEPTANCE", "ACTIVE", "ACCEPTANCE", userId, userName,
            "负责人提交成果验收，等待老板检验：" + acceptance.getResultSummary());
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject reviewAcceptance(Long projectId, String decision, String comment,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireBoss(project, userId, boss);
        requireStatus(project, "ACCEPTANCE");
        if (!"RESULT_ACCEPTANCE".equals(effectiveCloseMethod(project)))
            throw new ServiceException("当前项目不使用成果验收结项");
        BusinessProjectAcceptance pending = mapper.selectLatestPendingAcceptance(projectId);
        if (pending == null) throw new ServiceException("没有待评审的验收资料");
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision))
            throw new ServiceException("验收决定不正确");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
        if (StringUtils.isNotEmpty(comment) && comment.length() > 2000) throw new ServiceException("验收意见不能超过2000个字符");
        if ("APPROVED".equals(decision))
        {
            if ("KEY_CONTROL".equals(normalizeManagementMode(project.getManagementMode()))) ensureKeyMilestonesReady(projectId);
            ensureReadyForAcceptance(projectId);
            ensureKpiReadyForClose(projectId);
            if (!BusinessProjectLifecycle.isSeparated(project)) prepareTerminalState(project, "CLOSED", userName);
        }
        String reviewerName = displayName(requireActiveUser(userId));
        if (mapper.reviewAcceptance(pending.getAcceptanceId(), decision, userId, reviewerName, comment, userName) != 1)
            throw new ServiceException("验收资料已被其他人处理，请刷新后重试");
        String to = "APPROVED".equals(decision) ? "CLOSED" : "ACTIVE";
        if ("APPROVED".equals(decision) && BusinessProjectLifecycle.isSeparated(project))
            return finalizeSeparatedProject(project,
                StringUtils.isBlank(comment) ? "成果验收通过并完成结项核算" : comment,
                userId, userName, boss);
        if (mapper.updateProjectStatus(projectId, "ACCEPTANCE", to, null, false, userName, project.getVersion()) != 1)
            throw changed();
        addEvent(projectId, "APPROVED".equals(decision) ? "CLOSE" : "RETURN_ACTIVE",
            "ACCEPTANCE", to, userId, userName, StringUtils.isBlank(comment) ? "验收通过" : comment);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    private void requireStaffCostScope(Long staffUserId, Long operatorUserId, boolean companyOwner, boolean staffCostManager,
        boolean lockForUpdate)
    {
        if (mapper.countManagedProjectMember(operatorUserId, staffUserId) > 0) return;
        if (companyOwner)
        {
            requireStaffCostCompanyOwner(staffUserId, operatorUserId, lockForUpdate);
            return;
        }
        if(staffCostManager && mapper.countUserRoleByKey(operatorUserId,"finance_cost_manager")>0)
        {
            Long operatorCompany=mapper.selectStaffCompanyId(operatorUserId),staffCompany=mapper.selectStaffCompanyId(staffUserId);
            if(operatorCompany==null||!operatorCompany.equals(staffCompany))throw new ServiceException("财务费率管理仅限本人所属公司");
            return;
        }
        throw new ServiceException("只能查看和设置本人负责项目中成员的内部核算成本");
    }

    @Override
    @Transactional
    public BusinessProject submitStageAcceptance(Long projectId, BusinessProjectStageAcceptance acceptance,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireAccess(project, userId, SecurityUtils.isAdmin(userId), boss);
        requireOwnerOrBoss(mapper.selectMemberRole(projectId, userId), project, userId, boss);
        requireStatus(project, "ACTIVE");
        if (acceptance == null || acceptance.getMilestoneId() == null) throw new ServiceException("请选择需要验收的里程碑");
        BusinessProjectMilestone milestone = mapper.selectMilestoneById(acceptance.getMilestoneId());
        if (milestone == null || !projectId.equals(milestone.getProjectId())) throw new ServiceException("里程碑不属于当前项目");
        if ("DONE".equals(milestone.getStatus())) throw new ServiceException("该里程碑已经验收通过");
        if (mapper.selectLatestPendingStageAcceptance(projectId, milestone.getMilestoneId()) != null)
            throw new ServiceException("该里程碑已有待评审的验收资料");
        ensureMilestoneTasksReady(projectId, milestone.getMilestoneId());
        validateStageAcceptance(acceptance);
        businessFileService.validateReferences(acceptance.getAttachmentUrls(), projectId, userId, boss, SecurityUtils.isAdmin(userId));
        acceptance.setProjectId(projectId);
        acceptance.setMilestoneName(milestone.getMilestoneName());
        acceptance.setSubmissionVersion(mapper.selectNextStageAcceptanceVersion(projectId, milestone.getMilestoneId()));
        acceptance.setSubmittedUserId(userId);
        acceptance.setSubmittedUserName(displayName(requireActiveUser(userId)));
        acceptance.setReviewStatus("PENDING");
        acceptance.setCreateBy(userName);
        if (mapper.insertStageAcceptance(acceptance) != 1) throw new ServiceException("提交阶段验收失败");
        mapper.updateMilestoneStatus(projectId, milestone.getMilestoneId(), "REVIEWING", userName);
        addEvent(projectId, "REQUEST_STAGE_ACCEPTANCE", "ACTIVE", "ACTIVE", userId, userName,
            "负责人提交里程碑“" + milestone.getMilestoneName() + "”验收，等待老板检验");
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject reviewStageAcceptance(Long projectId, Long milestoneId, String decision, String comment,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireBoss(project, userId, boss);
        requireStatus(project, "ACTIVE");
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision)) throw new ServiceException("验收决定不正确");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
        if (StringUtils.isNotEmpty(comment) && comment.length() > 2000) throw new ServiceException("验收意见不能超过2000个字符");
        BusinessProjectStageAcceptance pending = mapper.selectLatestPendingStageAcceptance(projectId, milestoneId);
        if (pending == null) throw new ServiceException("该里程碑没有待评审的验收资料");
        if (mapper.reviewStageAcceptance(pending.getStageAcceptanceId(), decision, userId,
            displayName(requireActiveUser(userId)), comment, userName) != 1)
            throw new ServiceException("阶段验收资料已被其他人处理，请刷新后重试");
        mapper.updateMilestoneStatus(projectId, milestoneId, "APPROVED".equals(decision) ? "DONE" : "DOING", userName);
        addEvent(projectId, "APPROVED".equals(decision) ? "APPROVE_STAGE" : "RETURN_STAGE",
            "ACTIVE", "ACTIVE", userId, userName,
            StringUtils.isBlank(comment) ? "阶段验收通过" : comment);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject transition(Long projectId, String action, String comment,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireAccess(project, userId, SecurityUtils.isAdmin(userId), boss);
        String memberRole = mapper.selectMemberRole(projectId, userId);
        String from = project.getStatus();
        String to = from;
        String baseline = null;
        boolean increment = false;

        if ("START_PLANNING".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "DRAFT"); to = "PLANNING";
        }
        else if ("SUBMIT_BASELINE".equals(action))
        {
            requireOwnerOrBoss(memberRole, project, userId, boss); requireStatus(project, "PLANNING");
            if (!"DRAFT".equals(project.getBaselineStatus())) throw new ServiceException("项目计划已经提交");
            if (StringUtils.isBlank(project.getObjective()) || project.getPlanStartDate() == null)
                throw new ServiceException("提交前请完善项目目标和计划开始日期");
            List<BusinessProjectRoutine> manualRoutines = mapper.selectRoutines(projectId, new Date());
            Map<String, Object> executionRelation = mapper.selectActiveExecutionRelation(projectId);
            List<BusinessProjectRoutine> sourceRoutines = executionRelation == null
                ? Collections.<BusinessProjectRoutine>emptyList()
                : mapper.selectLiveStreamerRoutines(executionRelation);
            if (mapper.selectTasks(projectId).isEmpty()
                && (manualRoutines == null || manualRoutines.isEmpty())
                && (sourceRoutines == null || sourceRoutines.isEmpty()))
                throw new ServiceException("提交前至少添加一项任务或持续工作计划");
            to = "ACTIVE"; baseline = "APPROVED"; increment = true;
            if (StringUtils.isBlank(comment)) comment = "负责人确认项目计划并启动执行";
        }
        else if ("RETURN_PLAN".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "PLANNING");
            if (!"SUBMITTED".equals(project.getBaselineStatus())) throw new ServiceException("当前没有待确认计划");
            if (StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
            baseline = "DRAFT";
        }
        else if ("CONFIRM_BASELINE".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "PLANNING");
            if (!"SUBMITTED".equals(project.getBaselineStatus())) throw new ServiceException("负责人尚未提交项目计划");
            to = "ACTIVE"; baseline = "APPROVED"; increment = true;
        }
        else if ("PAUSE".equals(action))
        {
            requireOwnerOrBoss(memberRole, project, userId, boss); requireStatus(project, "ACTIVE");
            if (StringUtils.isBlank(comment)) throw new ServiceException("暂停原因不能为空");
            to = "PAUSED";
        }
        else if ("RESUME".equals(action))
        {
            requireOwnerOrBoss(memberRole, project, userId, boss); requireStatus(project, "PAUSED"); to = "ACTIVE";
        }
        else if ("REQUEST_ACCEPTANCE".equals(action))
        {
            throw new ServiceException("请填写验收资料后提交验收");
        }
        else if ("REQUEST_CLOSE".equals(action))
        {
            if (!"OWNER".equals(memberRole)) throw new ServiceException("只有项目主负责人可以发起结项");
            requireStatus(project, "ACTIVE");
            String closeMethod = effectiveCloseMethod(project);
            if ("RESULT_ACCEPTANCE".equals(closeMethod))
                throw new ServiceException("成果验收项目请填写并提交成果验收资料");
            if ("STAGED_ACCEPTANCE".equals(closeMethod)) ensureStagesReadyForClose(projectId);
            else if ("KEY_CONTROL".equals(normalizeManagementMode(project.getManagementMode()))) ensureKeyMilestonesReady(projectId);
            if (!"LIGHT".equals(normalizeManagementMode(project.getManagementMode()))) ensureHighRisksClosed(projectId);
            ensureKpiReadyForClose(projectId);
            ensureReadyForAcceptance(projectId);
            if (StringUtils.isBlank(comment)) throw new ServiceException("请填写结项申请说明");
            to = "ACCEPTANCE";
        }
        else if ("RETURN_ACTIVE".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "ACCEPTANCE");
            if ("RESULT_ACCEPTANCE".equals(effectiveCloseMethod(project)))
                throw new ServiceException("请在验收资料中填写意见并退回执行");
            if (StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
            to = "ACTIVE";
        }
        else if ("CLOSE".equals(action))
        {
            requireBoss(project, userId, boss);
            String closeMethod = effectiveCloseMethod(project);
            if ("RESULT_ACCEPTANCE".equals(closeMethod))
                throw new ServiceException("该项目需提交成果验收资料并评审通过后结项");
            if ("STAGED_ACCEPTANCE".equals(closeMethod))
            {
                requireStatus(project, "ACCEPTANCE");
                ensureStagesReadyForClose(projectId);
            }
            else
            {
                if (!"ACTIVE".equals(project.getStatus()) && !"ACCEPTANCE".equals(project.getStatus()))
                    throw new ServiceException("当前项目状态不允许执行此操作");
                if ("KEY_CONTROL".equals(normalizeManagementMode(project.getManagementMode()))) ensureKeyMilestonesReady(projectId);
            }
            if (!"LIGHT".equals(normalizeManagementMode(project.getManagementMode()))) ensureHighRisksClosed(projectId);
            if (StringUtils.isBlank(comment)) throw new ServiceException("请填写项目完成结论");
            ensureKpiReadyForClose(projectId);
            ensureReadyForAcceptance(projectId);
            if (BusinessProjectLifecycle.isSeparated(project))
                return finalizeSeparatedProject(project, comment, userId, userName, boss);
            to = "CLOSED";
        }
        else if ("CANCEL".equals(action))
        {
            requireOwnerOrBoss(memberRole, project, userId, boss); ensureMutable(project);
            if (StringUtils.isBlank(comment)) throw new ServiceException("取消原因不能为空");
            to = "CANCELED";
        }
        else throw new ServiceException("不支持的项目操作");

        if ("CLOSED".equals(to) || "CANCELED".equals(to)) prepareTerminalState(project, to, userName);
        if (mapper.updateProjectStatus(projectId, from, to, baseline, increment, userName, project.getVersion()) != 1)
            throw changed();
        addEvent(projectId, action, from, to, userId, userName, comment);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProjectMember saveMember(BusinessProjectMember member, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(member.getProjectId());
        requireManage(project, userId, boss);
        ensureMutable(project);
        if (member.getUserId() == null) throw new ServiceException("请选择项目成员");
        if (project.getMainOwnerUserId().equals(member.getUserId())) throw new ServiceException("主负责人身份不能在成员列表修改");
        if (!MEMBER_ROLES.contains(member.getMemberRole())) throw new ServiceException("项目成员身份不正确");
        Map<String, Object> user = requireActiveUser(member.getUserId());
        member.setUserNameSnapshot(displayName(user));
        member.setStatus("0");
        member.setCreateBy(userName);
        member.setJoinedDate(member.getJoinedDate() == null ? new Date() : member.getJoinedDate());
        String currentRole = mapper.selectMemberRole(project.getProjectId(), member.getUserId());
        requireDeputyAssignmentAuthority(project, userId, boss, currentRole, member.getMemberRole());
        if(BusinessMemberDayCostService.enabled(project)){
            memberDays.archiveMembership(project.getProjectId(),member.getUserId());
            Date roleDate=member.getRoleEffectiveDate()==null?DateUtils.parseDate(DateUtils.getDate()):member.getRoleEffectiveDate();
            if(currentRole!=null&&!currentRole.equals(member.getMemberRole())&&!roleDate.equals(DateUtils.parseDate(DateUtils.getDate())))throw new ServiceException("角色变更从今天起生效，历史成本请通过核算调整处理");
            memberDays.saveRole(project.getProjectId(),member.getUserId(),currentRole==null?member.getJoinedDate():roleDate,member.getMemberRole(),userName);
        }
        mapper.upsertMember(member);
        grantProjectUser(member.getUserId(), false);
        if ("DEPUTY".equals(currentRole) || "DEPUTY".equals(member.getMemberRole()))
            syncProjectDeputyRole(member.getUserId());
        addEvent(project.getProjectId(), "MEMBER_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            member.getUserNameSnapshot() + " / " + member.getMemberRole());
        return member;
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long memberUserId, Long userId, String userName, boolean boss)
    {
        removeMember(projectId, memberUserId, false, userId, userName, boss);
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long memberUserId, boolean retainTodayCost,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId);
        requireManage(project, userId, boss);
        ensureMutable(project);
        if (project.getMainOwnerUserId().equals(memberUserId)) throw new ServiceException("不能移除项目主负责人");
        String currentRole = mapper.selectMemberRole(projectId, memberUserId);
        requireDeputyAssignmentAuthority(project, userId, boss, currentRole, null);
        if (mapper.leaveMember(projectId, memberUserId, userName) != 1) throw new ServiceException("项目成员不存在或已经退出");
        if ("DEPUTY".equals(currentRole)) syncProjectDeputyRole(memberUserId);
        mapper.closeMemberWorkPeriods(projectId, memberUserId, userName);
        int taskCount = mapper.unassignOpenMemberTasks(projectId, memberUserId, userName);
        int routineCount = mapper.unassignActiveMemberRoutines(projectId, memberUserId, userName);
        int allocationCount = mapper.closeMemberAllocations(projectId, memberUserId, retainTodayCost, userName);
        if(BusinessMemberDayCostService.enabled(project))memberDays.synchronize(projectId);
        if (allocationCount > 0&&!BusinessMemberDayCostService.enabled(project))
        {
            Date today = normalizeLeaveDate(DateUtils.getNowDate(), "日期不正确");
            accountingService.recalculatePersonnelCost(projectId, today, userName);
        }
        addEvent(projectId, "MEMBER_REMOVE", project.getStatus(), project.getStatus(), userId, userName,
            "移除账号ID " + memberUserId + "；解除未完成任务 " + taskCount + " 项，解除持续工作负责人 "
                + routineCount + " 项；" + (BusinessMemberDayCostService.enabled(project)?"人员成本按参与起止日期内的工作日计算，含退出当日":
                (retainTodayCost ? "保留移除当日人员成本" : "移除当日不再计人员成本")));
    }

    @Override
    @Transactional
    public BusinessProjectMilestone saveMilestone(BusinessProjectMilestone milestone,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(milestone.getProjectId());
        requireManage(project, userId, boss); ensureMutable(project);
        if (StringUtils.isBlank(milestone.getMilestoneName())) throw new ServiceException("里程碑名称不能为空");
        BusinessProjectMilestone currentMilestone = null;
        if (milestone.getMilestoneId() != null)
        {
            currentMilestone = mapper.selectMilestoneById(milestone.getMilestoneId());
            if (currentMilestone == null || !project.getProjectId().equals(currentMilestone.getProjectId()))
                throw new ServiceException("里程碑不存在");
            if ("REVIEWING".equals(currentMilestone.getStatus()) || "DONE".equals(currentMilestone.getStatus()))
                throw new ServiceException("待验收或已完成的里程碑已锁定，不能直接修改");
        }
        milestone.setWeight(currentMilestone == null || currentMilestone.getWeight() == null
            ? BigDecimal.ZERO : currentMilestone.getWeight());
        if (StringUtils.isBlank(milestone.getStatus()))
            milestone.setStatus(currentMilestone == null ? "PENDING" : currentMilestone.getStatus());
        if (!Arrays.asList("PENDING", "DOING", "REVIEWING", "DONE").contains(milestone.getStatus()))
            throw new ServiceException("里程碑状态不正确");
        if (("REVIEWING".equals(milestone.getStatus()) || "DONE".equals(milestone.getStatus()))
            && (currentMilestone == null || !milestone.getStatus().equals(currentMilestone.getStatus())))
            throw new ServiceException("里程碑必须由负责人提交验收，并由老板确认后才能完成");
        milestone.setActualDate(null);
        if (milestone.getSortOrder() == null) milestone.setSortOrder(0);
        if (milestone.getMilestoneId() == null)
        {
            milestone.setCreateBy(userName); mapper.insertMilestone(milestone);
        }
        else
        {
            milestone.setUpdateBy(userName);
            if (mapper.updateMilestone(milestone) != 1) throw new ServiceException("里程碑不存在");
        }
        addEvent(project.getProjectId(), "MILESTONE_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            milestone.getMilestoneName());
        return milestone;
    }

    @Override
    @Transactional
    public void deleteMilestone(Long projectId, Long milestoneId, Long userId, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        List<BusinessProjectStageAcceptance> records = mapper.selectStageAcceptances(projectId);
        if (records != null)
            for (BusinessProjectStageAcceptance record : records)
                if (milestoneId.equals(record.getMilestoneId())) throw new ServiceException("已有阶段验收记录的里程碑不能删除");
        if (mapper.deleteMilestone(projectId, milestoneId) != 1)
            throw new ServiceException("里程碑不存在或仍有关联任务");
    }

    @Override
    @Transactional
    public BusinessProjectTask saveTask(BusinessProjectTask task, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(task.getProjectId());
        String callerRole = mapper.selectMemberRole(project.getProjectId(), userId);
        if ("OBSERVER".equals(callerRole)) throw new ServiceException("观察者仅可查看项目，不能新建或修改任务");
        boolean manager = SecurityUtils.isAdmin(userId) || "OWNER".equals(callerRole) || "DEPUTY".equals(callerRole);
        if (boss)
        {
            requireManage(project, userId, true);
            manager = true;
        }
        ensureMutable(project);
        BusinessProjectTask currentTask = task.getTaskId() == null ? null : mapper.selectTaskById(task.getTaskId());
        if (task.getTaskId() != null && (currentTask == null || !project.getProjectId().equals(currentTask.getProjectId())))
            throw new ServiceException("任务不存在");
        if (currentTask != null && "VOID".equals(currentTask.getActiveStatus()))
            throw new ServiceException("任务已停用，请先启用后再编辑");
        if (!manager)
        {
            if (task.getTaskId() == null) throw new ServiceException("只有项目负责人可以新增任务");
            BusinessProjectTask current = currentTask;
            if (!userId.equals(current.getAssigneeUserId())) throw new ServiceException("只能更新分配给自己的任务");
            if (task.getVersion() == null || !task.getVersion().equals(current.getVersion())) throw changed();
            if (StringUtils.isBlank(task.getStatus()) || !TASK_STATUSES.contains(task.getStatus()))
                throw new ServiceException("任务状态不正确");
            int progress = task.getProgress() == null ? 0 : task.getProgress();
            if (progress < 0 || progress > 100) throw new ServiceException("任务进度必须在0到100之间");
            current.setStatus(progress == 100 ? "DONE" : task.getStatus());
            current.setProgress("DONE".equals(current.getStatus()) ? 100 : progress);
            current.setUpdateBy(userName);
            if (mapper.updateTask(current) != 1) throw changed();
            addEvent(project.getProjectId(), "TASK_PROGRESS", project.getStatus(), project.getStatus(), userId, userName,
                current.getTaskName() + " / " + current.getProgress() + "%");
            return current;
        }
        if (StringUtils.isBlank(task.getTaskName())) throw new ServiceException("任务名称不能为空");
        Map<String, Object> assigneeUser = null;
        if (task.getAssigneeUserId() != null)
        {
            String role = mapper.selectMemberRole(task.getProjectId(), task.getAssigneeUserId());
            if (role == null || "OBSERVER".equals(role)) throw new ServiceException("任务负责人必须是可执行工作的有效项目成员");
            assigneeUser = requireActiveUser(task.getAssigneeUserId());
            task.setAssigneeName(displayName(assigneeUser));
        }
        if (task.getMilestoneId() != null)
        {
            BusinessProjectMilestone milestone = mapper.selectMilestoneById(task.getMilestoneId());
            if (milestone == null || !project.getProjectId().equals(milestone.getProjectId()))
                throw new ServiceException("任务里程碑必须属于当前项目");
        }
        if (task.getTaskId() != null)
        {
            // 项目负责人只安排任务，执行进度只能由任务负责人的完成填报产生。
            task.setStatus(currentTask.getStatus());
            task.setProgress(currentTask.getProgress());
        }
        else
        {
            task.setStatus("TODO");
            task.setProgress(0);
        }
        if (task.getProgress() == null) task.setProgress(0);
        if (task.getProgress() < 0 || task.getProgress() > 100) throw new ServiceException("任务进度必须在0到100之间");
        if (StringUtils.isBlank(task.getStatus())) task.setStatus("TODO");
        if (!TASK_STATUSES.contains(task.getStatus())) throw new ServiceException("任务状态不正确");
        if (task.getProgress() == 100) task.setStatus("DONE");
        if ("DONE".equals(task.getStatus())) task.setProgress(100);
        if (StringUtils.isBlank(task.getPriority())) task.setPriority("MEDIUM");
        if (!PRIORITIES.contains(task.getPriority())) throw new ServiceException("任务优先级不正确");
        if (task.getParentTaskId() != null && task.getParentTaskId().equals(task.getTaskId()))
            throw new ServiceException("任务不能成为自己的父任务");
        validateTaskParent(project.getProjectId(), task.getTaskId(), task.getParentTaskId());
        if (task.getTaskId() == null)
        {
            task.setActiveStatus("ACTIVE");
            task.setCreateBy(userName); mapper.insertTask(task);
            openWorkPeriod(project.getProjectId(), "TASK", task.getTaskId(), task.getAssigneeUserId(),
                task.getAssigneeName(), task.getPlanStartDate(), userName);
        }
        else
        {
            if (task.getVersion() == null) throw new ServiceException("缺少任务版本，请刷新后重试");
            task.setUpdateBy(userName);
            if (mapper.updateTask(task) != 1) throw changed();
            task.setActiveStatus("ACTIVE");
            if (!Objects.equals(currentTask.getAssigneeUserId(), task.getAssigneeUserId()))
            {
                mapper.closeWorkPeriod(project.getProjectId(), "TASK", task.getTaskId(), userName);
                openWorkPeriod(project.getProjectId(), "TASK", task.getTaskId(), task.getAssigneeUserId(),
                    task.getAssigneeName(), DateUtils.getNowDate(), userName);
            }
        }
        addEvent(project.getProjectId(), "TASK_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            task.getTaskName(), assigneeUser);
        return task;
    }

    @Override
    @Transactional
    public BusinessProjectTaskReport submitTaskReport(BusinessProjectTaskReport report,
        Long userId, String userName)
    {
        if (report == null || report.getTaskId() == null) throw new ServiceException("请选择一次性任务");
        BusinessProjectTask task = mapper.selectTaskById(report.getTaskId());
        if (task == null) throw new ServiceException("任务不存在");
        if ("VOID".equals(task.getActiveStatus())) throw new ServiceException("任务已停用，不能继续填报");
        if (!userId.equals(task.getAssigneeUserId())) throw new ServiceException("只能由任务负责人本人填报");
        BusinessProject project = requireProjectForUpdate(task.getProjectId());
        requireActiveExecutor(project.getProjectId(), userId);
        if (!"ACTIVE".equals(project.getStatus())) throw new ServiceException("项目执行中才能填报任务完成情况");
        Date today = DateUtils.getNowDate();
        if (report.getBizDate() == null) report.setBizDate(today);
        if (!new SimpleDateFormat("yyyy-MM-dd").format(today)
            .equals(new SimpleDateFormat("yyyy-MM-dd").format(report.getBizDate())))
            throw new ServiceException("只能填报今日任务完成情况");
        requireNotOnLeave(project, userId, report.getBizDate());
        if (report.getProgress() == null || report.getProgress() < 0 || report.getProgress() > 100)
            throw new ServiceException("任务进度必须在0到100之间");
        if (report.getProgress() < (task.getProgress() == null ? 0 : task.getProgress()))
            throw new ServiceException("任务进度只能增加，不能低于当前进度");
        if (StringUtils.isBlank(report.getCompletionSummary())) throw new ServiceException("请填写实际完成情况");
        if (StringUtils.isBlank(report.getEvidenceUrls())) throw new ServiceException("请上传成果凭证");
        if (report.getCompletionSummary().length() > 2000) throw new ServiceException("实际完成情况不能超过2000字");
        if (report.getEvidenceUrls().length() > 4000) throw new ServiceException("成果凭证文件过多");
        businessFileService.validateReferences(report.getEvidenceUrls(), project.getProjectId(), userId, false, SecurityUtils.isAdmin(userId));
        report.setCompletionSummary(report.getCompletionSummary().trim());

        task.setProgress(report.getProgress());
        task.setStatus(report.getProgress() >= 100 ? "DONE" : report.getProgress() > 0 ? "DOING" : "TODO");
        task.setUpdateBy(userName);
        if (mapper.updateTask(task) != 1) throw changed();

        report.setProjectId(project.getProjectId());
        report.setSubmittedUserId(userId);
        report.setSubmittedUserName(displayName(requireActiveUser(userId)));
        report.setCreateBy(userName);
        mapper.upsertTaskReport(report);
        addEvent(project.getProjectId(), "TASK_PROGRESS", project.getStatus(), project.getStatus(), userId, userName,
            task.getTaskName() + " / " + task.getProgress() + "%");
        return mapper.selectTaskReport(task.getTaskId(), report.getBizDate());
    }

    @Override
    @Transactional
    public BusinessProjectProgressReport submitProjectProgressReport(BusinessProjectProgressReport report,
        Long userId, String userName, boolean viewAll)
    {
        if (report == null || report.getProjectId() == null) throw new ServiceException("请选择项目");
        BusinessProject project = requireProjectForUpdate(report.getProjectId());
        if (!Objects.equals(userId, project.getMainOwnerUserId()))
            throw new ServiceException("只能由项目主负责人本人汇报进度");
        if (!"ACTIVE".equals(project.getStatus()) && !"PAUSED".equals(project.getStatus()))
            throw new ServiceException("项目执行中或暂停时才能汇报进度");
        if (project.getParentId() == null && mapper.countSubprojects(project.getProjectId()) > 0)
            throw new ServiceException("总项目进度由子项目自动汇总，请在子项目汇报");
        if (project.getParentId() == null && "NO_TOTAL".equals(effectiveGoalMode(project)))
            throw new ServiceException("不计入总目标的持续经营项目无需填写项目完成百分比");
        if (report.getProgress() == null || report.getProgress() < 0 || report.getProgress() > 100)
            throw new ServiceException("项目进度必须在0到100之间");
        validateProgressText(report.getCompletionSummary(), "阶段成果", true);
        validateProgressText(report.getIssuesRisks(), "问题风险", project.getParentId() != null);
        validateProgressText(report.getNextPlan(), "下一步计划", project.getParentId() != null);
        if (report.getEvidenceUrls() == null) report.setEvidenceUrls("");
        if (report.getEvidenceUrls().length() > 4000) throw new ServiceException("成果凭证文件过多");
        if (!report.getEvidenceUrls().isEmpty()) businessFileService.validateReferences(report.getEvidenceUrls(), project.getProjectId(), userId, false, SecurityUtils.isAdmin(userId));
        BusinessProjectProgressReport latest = mapper.selectLatestProjectProgressReport(project.getProjectId());
        Date now = DateUtils.getNowDate();
        report.setReportId(null);
        report.setBizDate(now);
        report.setCreateTime(now);
        report.setUpdateTime(null);
        report.setUpdateBy(null);
        report.setVersion(latest == null ? 1 : (latest.getVersion() == null ? 0 : latest.getVersion()) + 1);
        report.setCompletionSummary(report.getCompletionSummary().trim());
        report.setSubmittedUserId(userId);
        report.setSubmittedUserName(displayName(requireActiveUser(userId)));
        report.setCreateBy(userName);
        report.setParentProjectId(project.getParentId());
        report.setProjectNameSnapshot(project.getProjectName());
        report.setSyncTasks(Boolean.TRUE.equals(report.getSyncTasks()));
        report.setSyncRoutines(Boolean.TRUE.equals(report.getSyncRoutines()));
        // Capture authoritative data at submission; never trust a snapshot supplied by the client.
        report.setSnapshotJson(com.alibaba.fastjson2.JSON.toJSONString(progressSnapshot(project, now)));
        mapper.insertProjectProgressReport(report);
        String comment = "汇报 v" + report.getVersion() + " / " + report.getProgress() + "% / "
            + report.getCompletionSummary().substring(0, Math.min(700, report.getCompletionSummary().length()));
        addEvent(project.getProjectId(), "PROJECT_PROGRESS", project.getStatus(), project.getStatus(), userId, report.getSubmittedUserName(), "[子项目:" + project.getProjectId() + "][汇报:" + report.getReportId() + "] " + comment);
        if (project.getParentId() != null) {
            BusinessProject parent = requireProject(project.getParentId());
            addEvent(parent.getProjectId(), "SUBPROJECT_PROGRESS", parent.getStatus(), parent.getStatus(), userId,
                report.getSubmittedUserName(), "[子项目:" + project.getProjectId() + "][汇报:" + report.getReportId() + "] " + project.getProjectName() + " / " + comment);
            progressMapper.notifyOwner(report.getReportId(), parent.getMainOwnerUserId());
        }
        return report;
    }

    private void validateProgressText(String value, String label, boolean required) {
        if (required && StringUtils.isBlank(value)) throw new ServiceException("请填写" + label + "（无内容可填写“无”）");
        if (value != null && value.length() > 2000) throw new ServiceException(label + "不能超过2000字");
    }

    @Override
    @Transactional
    public void setProgressWeight(Long parentId, Long projectId, BigDecimal weight, Long userId, String userName) {
        BusinessProject parent = requireProjectForUpdate(parentId);
        if (parent.getParentId() != null || !Objects.equals(userId,parent.getMainOwnerUserId()))
            throw new ServiceException("仅总项目负责人可配置子项目权重");
        ensureMutable(parent);
        if (weight != null && (weight.signum() <= 0 || weight.compareTo(new BigDecimal("99999999")) > 0 || weight.scale() > 4))
            throw new ServiceException("权重必须大于0、不超过99999999，最多4位小数；留空按1计算");
        if (progressMapper.setWeight(projectId,parentId,weight,userName) != 1) throw new ServiceException("子项目不存在或不属于该总项目");
        addEvent(parentId,"PROGRESS_WEIGHT",parent.getStatus(),parent.getStatus(),userId,userName,
            "子项目 " + projectId + " 的进度权重调整为 " + (weight == null ? "默认等权1" : weight.toPlainString()));
    }

    private Map<String,Object> progressSnapshot(BusinessProject project, Date now) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capturedAt", now);
        snapshot.put("projectId", project.getProjectId());
        snapshot.put("projectName", project.getProjectName());
        snapshot.put("parentProjectId", project.getParentId());
        snapshot.put("progressWeight", project.getProgressWeight());
        snapshot.put("tasks", mapper.selectTasks(project.getProjectId()));
        snapshot.put("taskReports", progressMapper.taskReports(project.getProjectId()));
        List<BusinessProjectRoutine> routines = new ArrayList<>(mapper.selectRoutines(project.getProjectId(), now));
        Map<String,Object> relation = mapper.selectActiveExecutionRelation(project.getProjectId());
        if (relation != null && "LIVE".equals(relation.get("sourceDomain"))) {
            List<BusinessProjectRoutine> linked = mapper.selectLiveStreamerRoutines(relation);
            if (linked != null) routines.addAll(linked);
        }
        snapshot.put("routines", routines);
        snapshot.put("executionPeriods", mapper.selectWorkPeriods(project.getProjectId()));
        return snapshot;
    }

    @Override
    public Map<String,Object> progressWorkspace(Long projectId, Long userId, boolean viewAll, boolean boss) {
        BusinessProject project = mapper.selectProjectById(projectId);
        if (project == null) project = progressMapper.archiveProject(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        boolean parentOwner = false;
        if (project.getParentId() != null) {
            BusinessProject parent = progressMapper.archiveProject(project.getParentId());
            parentOwner = parent != null && Objects.equals(parent.getMainOwnerUserId(), userId);
        }
        if (!parentOwner) {
            try { requireAccess(project,userId,viewAll,boss); }
            catch (ServiceException denied) {
                // Former parent owners retain only the versions addressed to them, never future or live data.
                List<BusinessProjectProgressReport> received = progressMapper.recipientHistory(projectId,userId);
                if (received.isEmpty()) throw denied;
                Map<String,Object> archive = new LinkedHashMap<>();
                archive.put("projectId",projectId);archive.put("parentId",project.getParentId());
                archive.put("projectName",received.get(0).getProjectNameSnapshot());
                archive.put("progressPercent",received.get(0).getProgress());archive.put("reports",received);
                archive.put("canSubmit",false);archive.put("canConfigureWeights",false);
                return archive;
            }
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId); result.put("projectName", project.getProjectName());
        result.put("parentId", project.getParentId()); result.put("progressPercent", project.getProgressPercent());
        result.put("canConfigureWeights", project.getParentId() == null && Objects.equals(userId,project.getMainOwnerUserId())
            && !"2".equals(project.getDelFlag()) && !Arrays.asList("CLOSED","CANCELED").contains(project.getStatus()));
        result.put("canSubmit", !"2".equals(project.getDelFlag()) && Objects.equals(userId,project.getMainOwnerUserId()) && ("ACTIVE".equals(project.getStatus()) || "PAUSED".equals(project.getStatus())) && (project.getParentId() != null || mapper.countSubprojects(projectId) == 0));
        result.put("reporterName", Objects.equals(userId,project.getMainOwnerUserId()) ? displayName(requireActiveUser(userId)) : null);
        result.put("serverTime", DateUtils.getNowDate());
        result.put("snapshot", progressSnapshot(project, DateUtils.getNowDate()));
        result.put("reports", progressMapper.history(projectId));
        if (project.getParentId() == null) {
            boolean full = viewAll || Objects.equals(userId, project.getMainOwnerUserId())
                || (boss && Objects.equals(userId, projectSponsorUserId(project)));
            Map<String,Object> query = new HashMap<>();
            query.put("parentId",projectId); query.put("viewAll",full); query.put("boss",boss); query.put("userId",userId);
            List<BusinessProject> children = mapper.selectProjectList(query);
            List<Map<String,Object>> summaries = new ArrayList<>();
            Set<Long> visibleIds = new HashSet<>();
            for (BusinessProject child : children) {
                visibleIds.add(child.getProjectId());
                Map<String,Object> summary = new LinkedHashMap<>();
                summary.put("projectId",child.getProjectId()); summary.put("projectName",child.getProjectName());
                summary.put("progressPercent",child.getProgressPercent()); summary.put("progressWeight",child.getProgressWeight());
                summary.put("progressSummary",child.getProgressSummary()); summary.put("progressReportTime",child.getProgressReportTime());
                summaries.add(summary);
            }
            result.put("children",summaries);
            List<BusinessProjectProgressReport> childReports = new ArrayList<>();
            for (BusinessProjectProgressReport item : progressMapper.childHistory(projectId))
                if (full || visibleIds.contains(item.getProjectId())) childReports.add(item);
            result.put("childReports",childReports);
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteTask(Long projectId, Long taskId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        BusinessProjectTask task = mapper.selectTaskById(taskId);
        if (task == null || !projectId.equals(task.getProjectId()) || "VOID".equals(task.getActiveStatus()))
            throw new ServiceException("任务不存在或已停用");
        if (mapper.countTaskChildren(projectId, taskId) > 0) throw new ServiceException("请先处理子任务");
        if (mapper.voidTask(projectId, taskId, userName) != 1) throw new ServiceException("任务不存在或已停用");
        mapper.closeWorkPeriod(projectId, "TASK", taskId, userName);
        addEvent(projectId, "TASK_VOID", project.getStatus(), project.getStatus(), userId, userName,
            "停用一次性任务 " + task.getTaskName(), workAssigneeSnapshot(task.getAssigneeUserId(), task.getAssigneeName()));
    }

    @Override
    @Transactional
    public BusinessProjectTask enableTask(Long projectId, Long taskId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        BusinessProjectTask task = mapper.selectTaskById(taskId);
        if (task == null || !projectId.equals(task.getProjectId()) || !"VOID".equals(task.getActiveStatus()))
            throw new ServiceException("任务不存在或已经启用");
        requireRunnableAssignee(projectId, task.getAssigneeUserId(), "任务");
        if (mapper.activateTask(projectId, taskId, userName) != 1) throw changed();
        openWorkPeriod(projectId, "TASK", taskId, task.getAssigneeUserId(), task.getAssigneeName(),
            DateUtils.getNowDate(), userName);
        addEvent(projectId, "TASK_ENABLE", project.getStatus(), project.getStatus(), userId, userName,
            "重新启用一次性任务 " + task.getTaskName(), workAssigneeSnapshot(task.getAssigneeUserId(), task.getAssigneeName()));
        return mapper.selectTaskById(taskId);
    }

    @Override
    @Transactional
    public BusinessProjectRoutine saveRoutine(BusinessProjectRoutine routine, Long userId, String userName, boolean boss)
    {
        if (routine == null || routine.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProjectForUpdate(routine.getProjectId());
        requireManage(project, userId, boss); ensureMutable(project);
        BusinessProjectRoutine currentRoutine = routine.getRoutineId() == null
            ? null : mapper.selectRoutineById(routine.getRoutineId());
        if (routine.getRoutineId() != null
            && (currentRoutine == null || !project.getProjectId().equals(currentRoutine.getProjectId())))
            throw new ServiceException("持续工作计划不存在");
        if (currentRoutine != null && "VOID".equals(currentRoutine.getStatus()))
            throw new ServiceException("持续工作已停用，请先启用后再编辑");
        if (StringUtils.isBlank(routine.getRoutineName())) throw new ServiceException("持续工作名称不能为空");
        if (StringUtils.isBlank(routine.getTargetMode())) routine.setTargetMode("FIXED");
        if (!ROUTINE_TARGET_MODES.contains(routine.getTargetMode())) throw new ServiceException("目标模式不正确");
        // 当前持续工作统一按日填报和判定目标；不再接受会被误解为周期累计的周/月频率。
        routine.setFrequency("DAILY");
        if ("FIXED".equals(routine.getTargetMode()) || "AUTO_TOTAL".equals(routine.getTargetMode()))
        {
            if (routine.getTargetValue() == null || routine.getTargetValue().compareTo(BigDecimal.ZERO) <= 0)
                throw new ServiceException("目标数量必须大于0");
        }
        else routine.setTargetValue(BigDecimal.ZERO);
        if ("AUTO_TOTAL".equals(routine.getTargetMode()))
        {
            if (!"TOTAL".equals(effectiveGoalMode(project))) throw new ServiceException("只有计入总目标的项目可以使用总目标自动分配");
            if (routine.getEndDate() == null) throw new ServiceException("总目标自动分配必须设置执行结束日期");
        }
        if (StringUtils.isBlank(routine.getUnit()))
        {
            if ("NONE".equals(routine.getTargetMode())) routine.setUnit("项");
            else throw new ServiceException("请填写成果单位");
        }
        String routineRole = routine.getAssigneeUserId() == null ? null
            : mapper.selectMemberRole(project.getProjectId(), routine.getAssigneeUserId());
        if (routineRole == null || "OBSERVER".equals(routineRole))
            throw new ServiceException("持续工作负责人必须是有效项目成员");
        Map<String, Object> assigneeUser = requireActiveUser(routine.getAssigneeUserId());
        routine.setAssigneeName(displayName(assigneeUser));
        if (routine.getStartDate() == null) routine.setStartDate(project.getPlanStartDate());
        if (routine.getStartDate() == null) throw new ServiceException("请选择开始日期");
        if (routine.getEndDate() != null && routine.getEndDate().before(routine.getStartDate()))
            throw new ServiceException("结束日期不能早于开始日期");
        if (!"1".equals(routine.getEvidenceRequired())) routine.setEvidenceRequired("0");
        if (routine.getRoutineId() == null)
        {
            routine.setStatus("ACTIVE"); routine.setVersion(0); routine.setCreateBy(userName);
            mapper.insertRoutine(routine);
            openWorkPeriod(project.getProjectId(), "ROUTINE", routine.getRoutineId(), routine.getAssigneeUserId(),
                routine.getAssigneeName(), routine.getStartDate(), userName);
        }
        else
        {
            if (routine.getVersion() == null) routine.setVersion(currentRoutine.getVersion());
            routine.setUpdateBy(userName);
            if (mapper.updateRoutine(routine) != 1) throw changed();
            routine.setStatus("ACTIVE");
            if (!Objects.equals(currentRoutine.getAssigneeUserId(), routine.getAssigneeUserId()))
            {
                mapper.closeWorkPeriod(project.getProjectId(), "ROUTINE", routine.getRoutineId(), userName);
                openWorkPeriod(project.getProjectId(), "ROUTINE", routine.getRoutineId(), routine.getAssigneeUserId(),
                    routine.getAssigneeName(), DateUtils.getNowDate(), userName);
            }
        }
        addEvent(project.getProjectId(), "ROUTINE_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            routine.getRoutineName() + " / " + routine.getFrequency() + " / " + routine.getTargetValue() + routine.getUnit(),
            assigneeUser);
        return mapper.selectRoutineById(routine.getRoutineId());
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BusinessProjectRoutineDailyTarget saveRoutineDailyTarget(BusinessProjectRoutineDailyTarget target,
        Long userId, String userName, boolean boss)
    {
        if (target == null || target.getRoutineId() == null) throw new ServiceException("请选择持续工作");
        BusinessProjectRoutine routine = mapper.selectRoutineById(target.getRoutineId());
        if (routine == null || !"ACTIVE".equals(routine.getStatus())) throw new ServiceException("持续工作不存在或已停用");
        BusinessProject project = requireProjectForUpdate(routine.getProjectId());
        routine = mapper.selectRoutineByIdForUpdate(routine.getRoutineId());
        if(routine==null||!"ACTIVE".equals(routine.getStatus()))throw new ServiceException("持续工作计划已变更或停用，请刷新");
        requireManage(project, userId, boss); ensureMutable(project);
        if (!"ACTIVE".equals(project.getStatus())) throw new ServiceException("项目执行中才能下达今日目标");
        if (!"DAILY_DYNAMIC".equals(routine.getTargetMode())) throw new ServiceException("只有客户动态日目标类型需要负责人下达今日目标");
        Date today = DateUtils.getNowDate();
        if (target.getBizDate() == null) target.setBizDate(today);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        if (!dateFormat.format(today).equals(dateFormat.format(target.getBizDate())))
            throw new ServiceException("负责人工作台只能下达今日目标");
        String targetDate = dateFormat.format(target.getBizDate());
        if (targetDate.compareTo(dateFormat.format(routine.getStartDate())) < 0
            || (routine.getEndDate() != null && targetDate.compareTo(dateFormat.format(routine.getEndDate())) > 0))
            throw new ServiceException("今日不在持续工作的执行区间内");
        if (target.getTargetValue() == null || target.getTargetValue().compareTo(BigDecimal.ZERO) <= 0)
            throw new ServiceException("今日目标必须大于0");
        if (StringUtils.isNotEmpty(target.getCustomerRequirement()) && target.getCustomerRequirement().length() > 1000)
            throw new ServiceException("客户要求不能超过1000个字符");
        if (mapper.selectRoutineReport(routine.getRoutineId(), target.getBizDate()) != null)
            throw new ServiceException("执行人已提交今日完成情况，不能再修改今日目标");
        BusinessProjectRoutineDailyTarget current = mapper.selectCurrentRoutineDailyTarget(routine.getRoutineId(), target.getBizDate());
        if (current != null)
        {
            if (StringUtils.isBlank(target.getChangeReason())) throw new ServiceException("修改今日目标时必须填写变更原因");
            if (target.getChangeReason().length() > 500) throw new ServiceException("变更原因不能超过500个字符");
            if (mapper.supersedeRoutineDailyTarget(current.getDailyTargetId(), userName) != 1) throw changed();
        }
        target.setProjectId(project.getProjectId());
        target.setUnit(routine.getUnit());
        target.setAssigneeUserId(routine.getAssigneeUserId());
        target.setAssigneeName(routine.getAssigneeName());
        target.setTargetVersion(current == null ? 1 : current.getTargetVersion() + 1);
        target.setStatus("CURRENT"); target.setCreateBy(userName);
        mapper.insertRoutineDailyTarget(target);
        addEvent(project.getProjectId(), "ROUTINE_TARGET_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            (current == null ? "下达" : "修改") + "今日目标 " + routine.getRoutineName() + "："
                + target.getTargetValue() + routine.getUnit(),
            workAssigneeSnapshot(routine.getAssigneeUserId(), routine.getAssigneeName()));
        return mapper.selectCurrentRoutineDailyTarget(routine.getRoutineId(), target.getBizDate());
    }

    @Override
    @Transactional
    public void removeRoutine(Long projectId, Long routineId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        BusinessProjectRoutine routine = mapper.selectRoutineById(routineId);
        if (routine == null || !projectId.equals(routine.getProjectId()) || !"ACTIVE".equals(routine.getStatus()))
            throw new ServiceException("持续工作计划不存在或已停用");
        if (mapper.voidRoutine(projectId, routineId, userName) != 1) throw new ServiceException("持续工作计划不存在");
        mapper.closeWorkPeriod(projectId, "ROUTINE", routineId, userName);
        addEvent(projectId, "ROUTINE_VOID", project.getStatus(), project.getStatus(), userId, userName,
            "停用持续工作 " + routine.getRoutineName(),
            workAssigneeSnapshot(routine.getAssigneeUserId(), routine.getAssigneeName()));
    }

    @Override
    @Transactional
    public BusinessProjectRoutine enableRoutine(Long projectId, Long routineId, Date requestedEndDate,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        BusinessProjectRoutine routine = mapper.selectRoutineById(routineId);
        if (routine == null || !projectId.equals(routine.getProjectId()) || !"VOID".equals(routine.getStatus()))
            throw new ServiceException("持续工作不存在或已经启用");
        requireRunnableAssignee(projectId, routine.getAssigneeUserId(), "持续工作");
        Date activationDate = dateOnly(DateUtils.getNowDate());
        Date activationEndDate = null;
        if ("AUTO_TOTAL".equals(routine.getTargetMode()))
        {
            activationEndDate = requestedEndDate == null ? routine.getEndDate() : dateOnly(requestedEndDate);
            if (activationEndDate == null || activationEndDate.before(activationDate))
                throw new ServiceException("总目标自动分配已超过原执行区间，请选择新的结束日期后再启用");
            Calendar afterToday = Calendar.getInstance();
            afterToday.setTime(activationDate); afterToday.add(Calendar.DAY_OF_MONTH, 1);
            BigDecimal cumulativeActual = mapper.sumRoutineActualBefore(routineId, afterToday.getTime());
            if (cumulativeActual == null) cumulativeActual = BigDecimal.ZERO;
            if (routine.getTargetValue() == null || cumulativeActual.compareTo(routine.getTargetValue()) >= 0)
                throw new ServiceException("该持续工作的总目标已经完成，无需重新启用");
        }
        if (mapper.activateRoutine(projectId, routineId, activationDate, activationEndDate,
            routine.getVersion(), userName) != 1) throw changed();
        openWorkPeriod(projectId, "ROUTINE", routineId, routine.getAssigneeUserId(), routine.getAssigneeName(),
            activationDate, userName);
        addEvent(projectId, "ROUTINE_ENABLE", project.getStatus(), project.getStatus(), userId, userName,
            "重新启用持续工作 " + routine.getRoutineName()
                + (activationEndDate == null ? "" : "，执行至 " + new SimpleDateFormat("yyyy-MM-dd").format(activationEndDate)),
            workAssigneeSnapshot(routine.getAssigneeUserId(), routine.getAssigneeName()));
        return mapper.selectRoutineById(routineId);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BusinessProjectRoutineReport submitRoutineReport(BusinessProjectRoutineReport report,
        Long userId, String userName, boolean viewAll)
    {
        if (report == null || report.getRoutineId() == null) throw new ServiceException("请选择持续工作");
        BusinessProjectRoutine routine = mapper.selectRoutineById(report.getRoutineId());
        if (routine == null || !"ACTIVE".equals(routine.getStatus())) throw new ServiceException("持续工作计划不存在或已停用");
        BusinessProject project = requireProjectForUpdate(routine.getProjectId());
        routine = mapper.selectRoutineByIdForUpdate(routine.getRoutineId());
        if(routine==null||!"ACTIVE".equals(routine.getStatus()))throw new ServiceException("持续工作计划已变更或停用，请刷新");
        if (!userId.equals(routine.getAssigneeUserId()))
            throw new ServiceException("只能由实际执行人本人填报完成量");
        requireActiveExecutor(project.getProjectId(), userId);
        if (!"ACTIVE".equals(project.getStatus()) && !"ACCEPTANCE".equals(project.getStatus()))
            throw new ServiceException("项目进入执行中后才能填写完成情况");
        Date today = DateUtils.getNowDate();
        if (report.getBizDate() == null) report.setBizDate(today);
        if (!new SimpleDateFormat("yyyy-MM-dd").format(today)
            .equals(new SimpleDateFormat("yyyy-MM-dd").format(report.getBizDate())))
            throw new ServiceException("负责人工作台只能填报今日完成情况");
        requireNotOnLeave(project, userId, report.getBizDate());
        BigDecimal effectiveTarget = effectiveRoutineTarget(routine, report.getBizDate());
        if ("NONE".equals(routine.getTargetMode())) report.setActualValue(BigDecimal.ZERO);
        if (report.getActualValue() == null || report.getActualValue().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("实际完成数量不能为空或为负数");
        if ("NONE".equals(routine.getTargetMode()) && StringUtils.isBlank(report.getSummary()))
            throw new ServiceException("请填写今日完成说明");
        boolean belowDailyTarget = !"NONE".equals(routine.getTargetMode())
            && report.getActualValue().compareTo(effectiveTarget) < 0;
        if (belowDailyTarget && StringUtils.isBlank(report.getIssueReason()))
            throw new ServiceException("未达到周期目标时请填写原因");
        if (belowDailyTarget)
        {
            report.setIssueReason(report.getIssueReason().trim());
            if (report.getIssueReason().length() > 500) throw new ServiceException("未达原因不能超过500个字符");
        }
        else report.setIssueReason(null);
        if ("1".equals(routine.getEvidenceRequired()) && StringUtils.isBlank(report.getEvidenceUrls()))
            throw new ServiceException("该工作要求上传成果凭证");
        businessFileService.validateReferences(report.getEvidenceUrls(), project.getProjectId(), userId, false, SecurityUtils.isAdmin(userId));
        report.setProjectId(project.getProjectId()); report.setTargetSnapshot(effectiveTarget);
        report.setUnit(routine.getUnit()); report.setSubmittedUserId(userId);
        report.setSubmittedUserName(displayName(requireActiveUser(userId))); report.setStatus("SUBMITTED");
        report.setCreateBy(userName); mapper.upsertRoutineReport(report);
        addEvent(project.getProjectId(), "ROUTINE_REPORT", project.getStatus(), project.getStatus(), userId, userName,
            routine.getRoutineName() + "：" + report.getActualValue() + routine.getUnit());
        return mapper.selectRoutineReport(routine.getRoutineId(), report.getBizDate());
    }

    private BigDecimal effectiveRoutineTarget(BusinessProjectRoutine routine, Date bizDate)
    {
        String mode = StringUtils.isBlank(routine.getTargetMode()) ? "FIXED" : routine.getTargetMode();
        if ("NONE".equals(mode)) return BigDecimal.ZERO;
        if ("DAILY_DYNAMIC".equals(mode))
        {
            BusinessProjectRoutineDailyTarget daily = mapper.selectCurrentRoutineDailyTarget(routine.getRoutineId(), bizDate);
            if (daily == null) throw new ServiceException("负责人尚未下达今日目标，请联系负责人后再填报");
            return daily.getTargetValue();
        }
        if (!"AUTO_TOTAL".equals(mode)) return routine.getTargetValue();
        if (routine.getEndDate() == null) throw new ServiceException("总目标自动分配缺少执行结束日期");
        Calendar from = Calendar.getInstance(); from.setTime(bizDate);
        Calendar to = Calendar.getInstance(); to.setTime(routine.getEndDate());
        zeroTime(from); zeroTime(to);
        long days = ((to.getTimeInMillis() - from.getTimeInMillis()) / 86400000L) + 1L;
        if (days < 1) days = 1;
        BigDecimal actual = mapper.sumRoutineActualBefore(routine.getRoutineId(), bizDate);
        if (actual == null) actual = BigDecimal.ZERO;
        BigDecimal remaining = routine.getTargetValue().subtract(actual).max(BigDecimal.ZERO);
        return remaining.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    private void zeroTime(Calendar calendar)
    {
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
    }

    private Date dateOnly(Date value)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value); zeroTime(calendar);
        return calendar.getTime();
    }

    @Override
    @Transactional
    public BusinessProjectRisk saveRisk(BusinessProjectRisk risk, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(risk.getProjectId()); requireManage(project, userId, boss); ensureMutable(project);
        if (StringUtils.isBlank(risk.getRiskTitle())) throw new ServiceException("风险标题不能为空");
        if (StringUtils.isBlank(risk.getRiskType())) risk.setRiskType("GENERAL");
        if (StringUtils.isBlank(risk.getSeverity())) risk.setSeverity("MEDIUM");
        if (!RISK_SEVERITIES.contains(risk.getSeverity())) throw new ServiceException("风险等级不正确");
        if (StringUtils.isBlank(risk.getProbability())) risk.setProbability("MEDIUM");
        if (!RISK_SEVERITIES.subList(0, 3).contains(risk.getProbability())) throw new ServiceException("风险概率不正确");
        if (StringUtils.isBlank(risk.getStatus())) risk.setStatus("OPEN");
        if (!Arrays.asList("OPEN", "MITIGATED", "CLOSED").contains(risk.getStatus()))
            throw new ServiceException("风险状态不正确");
        if (risk.getOwnerUserId() != null)
        {
            if (mapper.selectMemberRole(risk.getProjectId(), risk.getOwnerUserId()) == null)
                throw new ServiceException("风险负责人必须是有效项目成员");
            risk.setOwnerName(displayName(requireActiveUser(risk.getOwnerUserId())));
        }
        if (risk.getRiskId() == null)
        {
            risk.setCreateBy(userName); mapper.insertRisk(risk);
        }
        else
        {
            risk.setUpdateBy(userName);
            if (mapper.updateRisk(risk) != 1) throw new ServiceException("风险记录不存在");
        }
        addEvent(project.getProjectId(), "RISK_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            risk.getRiskTitle());
        return risk;
    }

    @Override
    @Transactional
    public void deleteRisk(Long projectId, Long riskId, Long userId, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireManage(project, userId, boss); ensureMutable(project);
        if (mapper.deleteRisk(projectId, riskId) != 1) throw new ServiceException("风险记录不存在");
    }

    @Override
    public Map<String, Object> dashboard(Long userId, boolean viewAll, boolean boss)
    {
        return dashboard(Collections.<String, Object>emptyMap(), userId, viewAll, boss);
    }

    @Override
    public Map<String, Object> dashboard(Map<String, Object> query, Long userId, boolean viewAll, boolean boss)
    {
        int projectPageNum = positiveInt(query, "projectPageNum", 1, 100000);
        int projectPageSize = positiveInt(query, "projectPageSize", 10, 50);
        int decisionPageNum = positiveInt(query, "decisionPageNum", 1, 100000);
        int decisionPageSize = positiveInt(query, "decisionPageSize", 20, 50);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> summary = mapper.selectDashboardSummary(userId, viewAll, boss);
        Map<String, Object> safeSummary = summary == null ? new HashMap<String, Object>() : summary;
        result.put("summary", safeSummary);
        String projectKeyword = query == null || query.get("projectKeyword") == null ? "" : String.valueOf(query.get("projectKeyword")).trim();
        String projectStatus = query == null || query.get("projectStatus") == null ? "" : String.valueOf(query.get("projectStatus")).trim();
        long projectTotal = projectKeyword.isEmpty() && projectStatus.isEmpty()
            ? longValue(safeSummary.get("totalCount"))
            : mapper.countDashboardProjects(userId, viewAll, boss, projectKeyword, projectStatus);
        projectPageNum = (int) Math.min(projectPageNum, Math.max(1L, (projectTotal + projectPageSize - 1) / projectPageSize));
        List<BusinessProject> projects = mapper.selectDashboardProjectPage(userId, viewAll, boss,
            (projectPageNum - 1) * projectPageSize, projectPageSize, projectKeyword, projectStatus);
        List<BusinessProject> decisions = mapper.selectDashboardDecisionPage(userId, viewAll, boss,
            (decisionPageNum - 1) * decisionPageSize, decisionPageSize);
        if (projects == null) projects = Collections.<BusinessProject>emptyList();
        if (decisions == null) decisions = Collections.<BusinessProject>emptyList();
        long decisionTotal = longValue(safeSummary.get("pendingDecisionCount"));
        result.put("projectPage", page(projects, projectTotal, projectPageNum, projectPageSize));
        result.put("decisionPage", page(decisions, decisionTotal, decisionPageNum, decisionPageSize));
        // 保留旧字段，避免老板 AI 和既有调用方在升级期间失效。
        result.put("projects", projects);
        result.put("decisions", decisions);
        result.put("tasks", mapper.selectMyDueTasks(userId, viewAll, boss));
        if (boss) result.put("ownerLoads", ownerLoads(mapper.selectBossOwnerActiveProjects(userId, viewAll, null)));
        return result;
    }

    private List<Map<String, Object>> ownerLoads(List<Map<String, Object>> projectRows)
    {
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<Long, Map<String, Object>>();
        if (projectRows != null) for (Map<String, Object> row : projectRows)
        {
            Long ownerId = longValue(row.get("ownerUserId"));
            Map<String, Object> owner = grouped.get(ownerId);
            if (owner == null)
            {
                owner = new LinkedHashMap<String, Object>();
                owner.put("ownerUserId", ownerId); owner.put("ownerName", row.get("ownerName"));
                owner.put("projects", new ArrayList<Map<String, Object>>()); grouped.put(ownerId, owner);
            }
            Map<String, Object> project = new LinkedHashMap<String, Object>();
            project.put("projectId", row.get("projectId")); project.put("projectNo", row.get("projectNo"));
            project.put("projectName", row.get("projectName")); project.put("status", row.get("status"));
            project.put("companyName", row.get("companyName"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) owner.get("projects");
            rows.add(project);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(grouped.values());
        for (Map<String, Object> owner : result)
        {
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) owner.get("projects");
            owner.put("projectCount", rows.size()); owner.put("eligibilityThreshold", 3);
            owner.put("managementFeeEligible", rows.size() >= 3);
        }
        Collections.sort(result, (left, right) -> {
            int count = Integer.compare(((Number) right.get("projectCount")).intValue(), ((Number) left.get("projectCount")).intValue());
            return count != 0 ? count : String.valueOf(left.get("ownerName")).compareTo(String.valueOf(right.get("ownerName")));
        });
        return result;
    }

    private int positiveInt(Map<String, Object> query, String key, int defaultValue, int maxValue)
    {
        if (query == null || query.get(key) == null) return defaultValue;
        try
        {
            int value = Integer.parseInt(String.valueOf(query.get(key)));
            return Math.min(Math.max(value, 1), maxValue);
        }
        catch (NumberFormatException ignored)
        {
            return defaultValue;
        }
    }

    private long longValue(Object value)
    {
        if (value instanceof Number) return ((Number)value).longValue();
        if (value == null) return 0L;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private Map<String, Object> page(List<BusinessProject> rows, long total, int pageNum, int pageSize)
    {
        Map<String, Object> page = new LinkedHashMap<String, Object>();
        page.put("rows", rows == null ? Collections.<BusinessProject>emptyList() : rows);
        page.put("total", total);
        page.put("pageNum", pageNum);
        page.put("pageSize", pageSize);
        return page;
    }

    @Override
    public Map<String, Object> bossPending(Map<String, Object> query, Long userId, boolean viewAll)
    {
        int pageNum = positiveInt(query, "pageNum", 1, 100000);
        int pageSize = positiveInt(query, "pageSize", 5, 50);
        String category = query == null || query.get("category") == null
            ? "ALL" : String.valueOf(query.get("category")).trim().toUpperCase();
        if (!Arrays.asList("ALL", "PROPOSAL", "MANAGEMENT_FEE", "ACCOUNTING", "INCENTIVE_REVIEW", "STAGE_ACCEPTANCE", "KPI_MISSING", "KPI_REVIEW", "PERSONNEL_COST", "PROJECT")
            .contains(category)) category = "ALL";
        Date bizDate = new Date();
        Map<String, Object> counts = mapper.selectBossPendingCounts(userId, viewAll, bizDate);
        if (counts == null) counts = new LinkedHashMap<String, Object>();
        long total = pendingTotal(counts, category);
        List<Map<String, Object>> rows = mapper.selectBossPendingPage(userId, viewAll, bizDate, category,
            (pageNum - 1) * pageSize, pageSize);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("rows", rows == null ? Collections.<Map<String, Object>>emptyList() : rows);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("category", category);
        result.put("counts", counts);
        return result;
    }

    private long pendingTotal(Map<String, Object> counts, String category)
    {
        Map<String, String> keys = new HashMap<String, String>();
        keys.put("PROPOSAL", "proposalCount");
        keys.put("MANAGEMENT_FEE", "managementFeeCount");
        keys.put("ACCOUNTING", "accountingCount");
        keys.put("INCENTIVE_REVIEW", "incentiveReviewCount");
        keys.put("STAGE_ACCEPTANCE", "stageAcceptanceCount");
        keys.put("KPI_MISSING", "kpiMissingCount");
        keys.put("KPI_REVIEW", "kpiReviewCount");
        keys.put("PERSONNEL_COST", "personnelCostCount");
        keys.put("PROJECT", "projectCount");
        if (!"ALL".equals(category)) return longValue(counts.get(keys.get(category)));
        long total = 0L;
        for (String key : keys.values()) total += longValue(counts.get(key));
        counts.put("totalCount", total);
        return total;
    }

    @Override
    public Map<String, Object> ownerWorkbench(Long projectId, Long userId, boolean viewAll)
    {
        Map<String, Object> projectQuery = new HashMap<String, Object>();
        projectQuery.put("userId", userId);
        projectQuery.put("viewAll", viewAll);
        projectQuery.put("boss", false);
        projectQuery.put("ownerOnly", !viewAll);
        List<BusinessProject> projects = mapper.selectProjectList(projectQuery);

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        int activeCount = 0;
        int overdueCount = 0;
        int highRiskCount = 0;
        for (BusinessProject project : projects)
        {
            if ("ACTIVE".equals(project.getStatus())) activeCount++;
            if (project.getTaskCount() != null && project.getCompletedTaskCount() != null
                && project.getCompletedTaskCount() < project.getTaskCount()
                && project.getPlanEndDate() != null && project.getPlanEndDate().before(DateUtils.getNowDate())) overdueCount++;
            if (project.getOpenRiskCount() != null && project.getOpenRiskCount() > 0) highRiskCount++;
        }
        summary.put("projectCount", projects.size());
        summary.put("activeCount", activeCount);
        summary.put("overdueCount", overdueCount);
        summary.put("riskProjectCount", highRiskCount);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("summary", summary);
        result.put("projects", projects);
        List<Map<String, Object>> allocationAlerts = mapper.selectOwnerPersonnelCostReadiness(userId,
            DateUtils.getNowDate(), viewAll);
        result.put("allocationAlerts", allocationAlerts == null
            ? Collections.<Map<String, Object>>emptyList() : allocationAlerts);
        List<Map<String, Object>> pendingEffortRequests = mapper.selectOwnerPendingEffortRequests(userId, viewAll);
        result.put("pendingEffortRequests", pendingEffortRequests == null
            ? Collections.<Map<String, Object>>emptyList() : pendingEffortRequests);
        if (projects.isEmpty()) return result;

        Long selectedId = projectId == null ? projects.get(0).getProjectId() : projectId;
        BusinessProject selected = null;
        for (BusinessProject project : projects)
        {
            if (project.getProjectId().equals(selectedId))
            {
                selected = project;
                break;
            }
        }
        if (selected == null) throw new ServiceException("只能进入自己负责的项目工作台");

        BusinessProject detail = getProject(selectedId, userId, viewAll, false);
        Map<String, Object> operating = operatingConfig(selectedId, userId, viewAll, false);
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<BusinessProjectTask> openTasks = new ArrayList<BusinessProjectTask>();
        for (BusinessProjectTask task : detail.getTasks())
            if (!"DONE".equals(task.getStatus())) openTasks.add(task);

        Map<String, Object> accounting = new LinkedHashMap<String, Object>();
        accounting.put("bizDate", today);
        List<BusinessOperatingFact> dailySpendItems=accountingMapper.selectProjectDailySpendItems(selectedId,
            java.sql.Date.valueOf(today));
        if(dailySpendItems==null)dailySpendItems=Collections.emptyList();
        BigDecimal dailySpendTotal=BigDecimal.ZERO;
        boolean hasConfirmedSpend=false;
        for(BusinessOperatingFact item:dailySpendItems)if("CONFIRMED".equals(item.getStatus()))
        {
            dailySpendTotal=dailySpendTotal.add(item.getAmount()==null?BigDecimal.ZERO:item.getAmount());
            hasConfirmedSpend=true;
        }
        Map<String,Object> dailySpend=null;
        Map<String,Object> noSpend=accountingMapper.selectSpendConfirmation(selectedId,java.sql.Date.valueOf(today));
        accounting.put("spendConfirmation",noSpend);
        if(hasConfirmedSpend)
        {
            dailySpend=new LinkedHashMap<String,Object>();dailySpend.put("amount",dailySpendTotal);
            dailySpend.put("currency",detail.getBaseCurrency());dailySpend.put("status","CONFIRMED");
            dailySpend.put("itemCount",dailySpendItems.size());
        }
        accounting.put("dailySpend",dailySpend);
        if(!hasConfirmedSpend&&noSpend!=null){
            Map<String,Object> zero=new LinkedHashMap<>();zero.put("amount",BigDecimal.ZERO);zero.put("currency",detail.getBaseCurrency());zero.put("status","NO_SPEND");zero.put("itemCount",0);
            accounting.put("dailySpend",zero);
        }
        accounting.put("dailySpendItems",dailySpendItems);
        accounting.put("dailyRevenue", accountingMapper.selectProjectRevenueSummary(selectedId,
            java.sql.Date.valueOf(today)));
        List<Map<String, Object>> revenueCategories = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> expenseCategories = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> categories = accountingMapper.selectCategories();
        if (categories != null) for (Map<String, Object> category : categories)
        {
            if ("REVENUE".equals(String.valueOf(category.get("factKind")))) revenueCategories.add(category);
            if ("COST".equals(String.valueOf(category.get("factKind")))
                && MANUAL_EXPENSE_CATEGORY_CODES.contains(String.valueOf(category.get("categoryCode")))) expenseCategories.add(category);
        }
        accounting.put("revenueCategories", revenueCategories);
        accounting.put("expenseCategories", expenseCategories);

        result.put("project", detail);
        result.put("attendanceAuthority", feishuService.getAuthority(detail.getCompanyDeptId(), java.sql.Date.valueOf(today)));
        Map<String,Object> memberAuthorities = new LinkedHashMap<String,Object>();
        for (BusinessProjectMember member : detail.getMembers())
            memberAuthorities.put(String.valueOf(member.getUserId()), feishuService.getPersonAuthority(
                member.getUserId(), detail.getCompanyDeptId(), java.sql.Date.valueOf(today)));
        result.put("memberAttendanceAuthorities", memberAuthorities);
        result.put("operating", operating);
        result.put("openTasks", openTasks);
        List<BusinessProjectTaskReport> taskReports = mapper.selectTaskReports(selectedId);
        result.put("taskReports", taskReports == null
            ? Collections.<BusinessProjectTaskReport>emptyList() : taskReports);
        result.put("todayRoutines", detail.getRoutines());
        result.put("accounting", accounting);
        result.put("todayProjectProgress", mapper.selectProjectProgressReport(selectedId,
            java.sql.Date.valueOf(today)));
        String[] week = weekRange(today);
        result.put("effortWeek", mapper.selectProjectEffortWeek(selectedId, week[0], week[1]));
        result.put("effortWeekFrom", week[0]);
        result.put("effortWeekTo", week[1]);
        result.put("todayLeaves", mapper.selectProjectMemberLeaves(selectedId, java.sql.Date.valueOf(today)));
        return result;
    }

    @Override
    public Map<String, Object> workDashboard(String period, String anchorDate, Long userId)
    {
        String normalized = StringUtils.isBlank(period) ? "DAY" : period.toUpperCase();
        if (!Arrays.asList("DAY", "WEEK", "MONTH").contains(normalized))
            throw new ServiceException("安排周期不正确");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        Date anchor = DateUtils.getNowDate();
        if (StringUtils.isNotBlank(anchorDate))
        {
            try { anchor = format.parse(anchorDate); }
            catch (Exception ex) { throw new ServiceException("查询日期格式不正确"); }
        }
        Calendar from = Calendar.getInstance(); from.setTime(anchor);
        Calendar to = Calendar.getInstance(); to.setTime(anchor);
        if ("WEEK".equals(normalized))
        {
            int day = from.get(Calendar.DAY_OF_WEEK);
            int offset = day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day;
            from.add(Calendar.DAY_OF_MONTH, offset);
            to.setTime(from.getTime()); to.add(Calendar.DAY_OF_MONTH, 6);
        }
        else if ("MONTH".equals(normalized))
        {
            from.set(Calendar.DAY_OF_MONTH, 1);
            to.setTime(from.getTime());
            to.set(Calendar.DAY_OF_MONTH, to.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        String dateFrom = format.format(from.getTime()), dateTo = format.format(to.getTime());
        String today = format.format(DateUtils.getNowDate());
        List<Map<String, Object>> tasks = mapper.selectMyWorkTasks(userId, dateFrom, dateTo);
        List<Map<String, Object>> routines = mapper.selectMyWorkRoutines(userId, dateFrom, dateTo, today);
        List<Map<String, Object>> efforts = mapper.selectMyEfforts(userId, format.format(anchor));
        decorateWorkAbsence(tasks, userId, java.sql.Date.valueOf(today));
        decorateWorkAbsence(routines, userId, java.sql.Date.valueOf(today));
        List<Map<String, Object>> projectBonuses = kpiMapper.selectMemberProjectBonusTotals(userId);
        if (projectBonuses == null) projectBonuses = Collections.<Map<String, Object>>emptyList();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("taskCount", tasks.size());
        summary.put("routineCount", routines.size());
        int reported = 0;
        for (Map<String, Object> routine : routines)
            if (routine.get("todayReportId") != null || routine.get("todayLeaveId") != null) reported++;
        summary.put("reportedRoutineCount", reported);
        BigDecimal plannedEffort = BigDecimal.ZERO, actualEffort = BigDecimal.ZERO;
        int submittedEffort = 0;
        for (Map<String, Object> effort : efforts)
        {
            plannedEffort = plannedEffort.add(decimal(effort.get("plannedPercent")));
            actualEffort = actualEffort.add(decimal(effort.get("actualPercent")));
            if (!"UNSUBMITTED".equals(String.valueOf(effort.get("reportStatus")))) submittedEffort++;
        }
        summary.put("plannedEffortPercent", plannedEffort);
        summary.put("actualEffortPercent", actualEffort);
        summary.put("submittedEffortCount", submittedEffort);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("period", normalized); result.put("anchorDate", format.format(anchor));
        result.put("dateFrom", dateFrom); result.put("dateTo", dateTo);
        result.put("today", today); result.put("summary", summary);
        result.put("projectBonuses", projectBonuses);
        result.put("tasks", tasks); result.put("routines", routines); result.put("efforts", efforts);
        return result;
    }

    @Override
    @Transactional
    public BusinessProjectEffort saveMyEffort(BusinessProjectEffort effort, Long userId, String userName)
    {
        if (effort == null || effort.getProjectId() == null || effort.getBizDate() == null)
            throw new ServiceException("请选择项目和投入日期");
        requireLegacyEffortPolicy(requireProjectForUpdate(effort.getProjectId()));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String bizDate = format.format(effort.getBizDate());
        if (bizDate.compareTo(format.format(DateUtils.getNowDate())) > 0)
            throw new ServiceException("不能提前填报未来日期的实际投入");
        Map<String, Object> plan = null;
        for (Map<String, Object> row : mapper.selectMyEfforts(userId, bizDate))
            if (sameLong(row.get("projectId"), effort.getProjectId())) { plan = row; break; }
        if (plan == null) throw new ServiceException("项目负责人尚未为你设置该日计划投入");
        if ("LEAVE".equals(String.valueOf(plan.get("reportStatus"))))
            throw new ServiceException("当天已登记请假，无需填报投入");
        BusinessProjectEffort current = mapper.selectEffortReport(effort.getProjectId(), userId, effort.getBizDate());
        if (current != null && "CONFIRMED".equals(current.getReportStatus()))
            throw new ServiceException("该日投入已由项目负责人确认，不能再修改");
        BigDecimal planned = decimal(plan.get("plannedPercent"));
        BigDecimal actual = effort.getActualPercent();
        if (actual == null || actual.compareTo(BigDecimal.ZERO) < 0 || actual.compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException("实际投入比例必须在0到100之间");
        BigDecimal other = mapper.sumUserEffectiveEffortExcludingProject(userId, effort.getBizDate(), effort.getProjectId());
        if (decimal(other).add(actual).compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException("当天跨项目实际投入合计不能超过100%，请先调整其他项目");
        if (actual.compareTo(planned) != 0 && StringUtils.isBlank(effort.getDeviationReason()))
            throw new ServiceException("实际投入与计划不一致时请填写偏差原因");
        effort.setUserId(userId);
        effort.setUserName(displayName(requireActiveUser(userId)));
        effort.setPlannedPercent(planned);
        effort.setSourceType("EMPLOYEE");
        effort.setReportStatus("SUBMITTED");
        effort.setCreateBy(userName);
        mapper.upsertEffortReport(effort);
        return mapper.selectEffortReport(effort.getProjectId(), userId, effort.getBizDate());
    }

    @Override
    @Transactional
    public Map<String, Object> confirmProjectEffortWeek(Long projectId, String anchorDate,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireLegacyEffortPolicy(project);
        requireManage(project, userId, boss);
        ensureMutable(project);
        String[] week = weekRange(anchorDate);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String today = format.format(DateUtils.getNowDate());
        Calendar cursor = Calendar.getInstance();
        try { cursor.setTime(format.parse(week[0])); }
        catch (Exception ex) { throw new ServiceException("查询日期格式不正确"); }
        int confirmedDays = 0;
        while (format.format(cursor.getTime()).compareTo(week[1]) <= 0
            && format.format(cursor.getTime()).compareTo(today) <= 0)
        {
            Date bizDate = cursor.getTime();
            int affected = mapper.confirmProjectEffortDay(projectId, bizDate, userId, userName);
            if (affected > 0)
            {
                accountingService.recalculatePersonnelCost(projectId, bizDate, userName);
                confirmedDays++;
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        addEvent(projectId, "EFFORT_WEEK_CONFIRMED", project.getStatus(), project.getStatus(), userId, userName,
            "确认投入周期 " + week[0] + " 至 " + week[1]);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("dateFrom", week[0]); result.put("dateTo", week[1]); result.put("confirmedDays", confirmedDays);
        result.put("rows", mapper.selectProjectEffortWeek(projectId, week[0], week[1]));
        return result;
    }

    @Override
    @Transactional
    public BusinessProjectEffort confirmMemberEffort(Long projectId, Long memberUserId, Date bizDate,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireLegacyEffortPolicy(project);
        requireManage(project, userId, boss);
        ensureMutable(project);
        Date day = normalizeToday(bizDate);
        BusinessProjectEffort current = mapper.selectEffortReport(projectId, memberUserId, day);
        if (current == null || !"SUBMITTED".equals(current.getReportStatus()))
            throw new ServiceException("该成员当天没有待确认的投入申报");
        if (mapper.confirmProjectMemberEffort(projectId, memberUserId, day, userId, userName) != 1)
            throw new ServiceException("投入申报已发生变化，请刷新后重试");
        accountingService.recalculatePersonnelCost(projectId, day, userName);
        addEvent(projectId, "EFFORT_DAY_CONFIRMED", project.getStatus(), project.getStatus(), userId, userName,
            current.getUserName() + " / " + new SimpleDateFormat("yyyy-MM-dd").format(day)
                + " / " + current.getActualPercent() + "%");
        return mapper.selectEffortReport(projectId, memberUserId, day);
    }

    @Override
    @Transactional
    public BusinessProjectEffort returnMemberEffort(Long projectId, Long memberUserId, Date bizDate,
        String reviewComment, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProjectForUpdate(projectId); requireLegacyEffortPolicy(project);
        requireManage(project, userId, boss);
        ensureMutable(project);
        Date day = normalizeToday(bizDate);
        if (StringUtils.isBlank(reviewComment)) throw new ServiceException("请填写退回原因");
        BusinessProjectEffort current = mapper.selectEffortReport(projectId, memberUserId, day);
        if (current == null || !"SUBMITTED".equals(current.getReportStatus()))
            throw new ServiceException("该成员当天没有待确认的投入申报");
        if (mapper.returnProjectMemberEffort(projectId, memberUserId, day, reviewComment.trim(), userName) != 1)
            throw new ServiceException("投入申报已发生变化，请刷新后重试");
        addEvent(projectId, "EFFORT_DAY_RETURNED", project.getStatus(), project.getStatus(), userId, userName,
            current.getUserName() + " / " + new SimpleDateFormat("yyyy-MM-dd").format(day)
                + " / 退回原因：" + reviewComment.trim());
        return mapper.selectEffortReport(projectId, memberUserId, day);
    }

    @Override
    public List<Map<String, Object>> projectDirectory(Long userId, boolean viewAll, boolean boss)
    {
        if (!boss && !viewAll) throw new ServiceException("只有老板可以查看公司项目目录");
        List<Map<String, Object>> rows = mapper.selectProjectDirectory();
        for (Map<String, Object> row : rows)
        {
            boolean canOpen = viewAll || sameLong(row.get("initiatorUserId"), userId);
            row.put("canOpen", canOpen);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> userOptions(String keyword)
    {
        return mapper.selectUserOptions(keyword);
    }

    private String[] weekRange(String anchorDate)
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        Date anchor = DateUtils.getNowDate();
        if (StringUtils.isNotBlank(anchorDate))
        {
            try { anchor = format.parse(anchorDate); }
            catch (Exception ex) { throw new ServiceException("查询日期格式不正确"); }
        }
        Calendar from = Calendar.getInstance(); from.setTime(anchor);
        int day = from.get(Calendar.DAY_OF_WEEK);
        from.add(Calendar.DAY_OF_MONTH, day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day);
        Calendar to = Calendar.getInstance(); to.setTime(from.getTime()); to.add(Calendar.DAY_OF_MONTH, 6);
        return new String[] { format.format(from.getTime()), format.format(to.getTime()) };
    }

    private Date normalizeToday(Date requested)
    {
        Date day = requested == null ? DateUtils.getNowDate() : requested;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        if (!format.format(DateUtils.getNowDate()).equals(format.format(day)))
            throw new ServiceException("负责人工作台目前只允许登记或取消今日请假");
        try { return format.parse(format.format(day)); }
        catch (Exception ex) { throw new ServiceException("请假日期格式不正确"); }
    }

    private void recalculateAllocatedProjects(Long memberUserId, Date bizDate, String userName)
    {
        for (Long affectedProjectId : mapper.selectAllocatedProjectIdsForUserDate(memberUserId, bizDate))
            accountingService.recalculatePersonnelCost(affectedProjectId, bizDate, userName);
    }

    private void recalculateTodayWhenAllocationAffected(Long projectId, BusinessProjectStaffAllocation previous,
        BusinessProjectStaffAllocation current, String userName)
    {
        Date today = DateUtils.getNowDate();
        if (!isEffectiveOn(previous, today) && !isEffectiveOn(current, today)) return;
        try
        {
            Date bizDate = new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(today));
            accountingService.recalculatePersonnelCost(projectId, bizDate, userName);
        }
        catch (Exception ex)
        {
            throw new ServiceException("当天人员成本核算失败");
        }
    }

    private boolean isEffectiveOn(BusinessProjectStaffAllocation allocation, Date bizDate)
    {
        if (allocation == null || allocation.getEffectiveFrom() == null) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String day = format.format(bizDate);
        return format.format(allocation.getEffectiveFrom()).compareTo(day) <= 0
            && (allocation.getEffectiveTo() == null || format.format(allocation.getEffectiveTo()).compareTo(day) >= 0);
    }

    private BigDecimal decimal(Object value)
    {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private BusinessProject requireProjectForUpdate(Long projectId)
    {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = mapper.selectProjectByIdForUpdate(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        return project;
    }

    private BusinessProject requireProject(Long projectId)
    {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = mapper.selectProjectById(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        return project;
    }

    private void requireAccess(BusinessProject project, Long userId, boolean viewAll, boolean boss)
    {
        if (viewAll) return;
        if (project.getParentId() != null && userId.equals(project.getApplicantUserId())) {
            BusinessProject parent = mapper.selectProjectById(project.getParentId());
            if (parent != null && (userId.equals(parent.getMainOwnerUserId())
                || Arrays.asList("OWNER", "DEPUTY").contains(mapper.selectMemberRole(parent.getProjectId(), userId)))) return;
        }
        if (boss)
        {
            if (userId.equals(projectSponsorUserId(project))) return;
            throw new ServiceException("无权查看其他老板归属的项目");
        }
        if (project.getMainOwnerUserId().equals(userId)) return;
        if (mapper.selectMemberRole(project.getProjectId(), userId) == null) throw new ServiceException("无权查看该项目");
    }

    private void requireManage(BusinessProject project, Long userId, boolean boss)
    {
        if (SecurityUtils.isAdmin(userId)) return;
        if (boss)
        {
            if (userId.equals(projectSponsorUserId(project))) return;
            throw new ServiceException("无权管理其他老板归属的项目");
        }
        String role = mapper.selectMemberRole(project.getProjectId(), userId);
        if (!"OWNER".equals(role) && !"DEPUTY".equals(role)) throw new ServiceException("无权管理该项目");
    }

    private void requireDeputyAssignmentAuthority(BusinessProject project, Long userId, boolean boss,
        String currentRole, String requestedRole)
    {
        if (SecurityUtils.isAdmin(userId) || boss || project.getMainOwnerUserId().equals(userId)) return;
        if ("DEPUTY".equals(currentRole) || "DEPUTY".equals(requestedRole))
            throw new ServiceException("只有项目主负责人或老板可以任命、调整或移除副负责人");
    }

    private void requireAllocationOwner(BusinessProject project, Long userId, boolean boss)
    {
        if (SecurityUtils.isAdmin(userId)) return;
        if (boss)
        {
            requireBoss(project, userId, true);
            return;
        }
        if (!userId.equals(project.getMainOwnerUserId()))
            throw new ServiceException("只有项目主负责人或归属老板可以设置、停用成员计划投入");
    }

    private void requireMainOwnerOrBoss(BusinessProject project, Long userId, boolean boss)
    {
        if (SecurityUtils.isAdmin(userId)) return;
        if (boss)
        {
            requireBoss(project, userId, true);
            return;
        }
        if (!userId.equals(project.getMainOwnerUserId()))
            throw new ServiceException("只有项目主负责人或归属老板可以执行此操作");
    }

    private void requireOwnerOrBoss(String role, BusinessProject project, Long userId, boolean boss)
    {
        if (boss)
        {
            requireBoss(project, userId, true);
            return;
        }
        if (!"OWNER".equals(role)) throw new ServiceException("只有项目主负责人可以提交此操作");
    }

    private void requireBoss(BusinessProject project, Long userId, boolean boss)
    {
        if (!boss) throw new ServiceException("只有老板可以执行此操作");
        if (!SecurityUtils.isAdmin(userId) && !userId.equals(projectSponsorUserId(project)))
            throw new ServiceException("无权操作其他老板归属的项目");
    }

    private void requireStatus(BusinessProject project, String expected)
    {
        if (!expected.equals(project.getStatus())) throw new ServiceException("当前项目状态不允许执行此操作");
    }

    private void requireActiveExecutor(Long projectId, Long userId)
    {
        BusinessProject project = mapper.selectProjectById(projectId);
        if (project != null && userId.equals(project.getMainOwnerUserId())) return;
        String role = mapper.selectMemberRole(projectId, userId);
        if (role == null || "OBSERVER".equals(role)) throw new ServiceException("已不再是该项目的有效执行成员");
    }

    private void validateTaskParent(Long projectId, Long taskId, Long parentTaskId)
    {
        if (parentTaskId == null) return;
        Set<Long> visited = new HashSet<Long>();
        if (taskId != null) visited.add(taskId);
        Long currentId = parentTaskId;
        while (currentId != null)
        {
            if (!visited.add(currentId)) throw new ServiceException("任务上下级关系不能形成循环");
            BusinessProjectTask parent = mapper.selectTaskById(currentId);
            if (parent == null || !projectId.equals(parent.getProjectId()))
                throw new ServiceException("父任务必须属于当前项目");
            currentId = parent.getParentTaskId();
        }
    }

    private void validateCurrency(String currency)
    {
        if (StringUtils.isBlank(currency) || !currency.matches("^[A-Z]{3}$"))
            throw new ServiceException("币种必须使用三位英文字母代码，例如CNY、VND或USD");
    }

    private void ensureMutable(BusinessProject project)
    {
        if ("CLOSED".equals(project.getStatus()) || "CANCELED".equals(project.getStatus()))
            throw new ServiceException("已结项或已取消项目不能修改");
    }

    private void prepareTerminalState(BusinessProject project, String terminalStatus, String userName)
    {
        Long projectId = project.getProjectId();
        if (!BusinessMemberDayCostService.enabled(project)&&!"ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()) && mapper.countPendingProjectEfforts(projectId) > 0)
            throw new ServiceException("项目仍有待负责人确认的成员投入，请先确认或退回后再结项");
        if (!BusinessProjectLifecycle.isSeparated(project)) accountingService.ensureProjectCanClose(projectId);

        Date closeDate = normalizeLeaveDate(DateUtils.getNowDate(), "结项日期不能为空");
        BusinessProjectLifecycle.requireAccountingOpen(project);
        if (!BusinessProjectLifecycle.isSeparated(project))
            accountingService.closeProjectAccounting(projectId, closeDate, userName);
        else
            accountingService.recalculatePersonnelCost(projectId, closeDate, userName);
        mapper.closeProjectRoutines(projectId, userName);
        mapper.closeProjectWorkPeriods(projectId, userName);
        mapper.closeProjectAllocations(projectId, closeDate, userName);
        if ("CANCELED".equals(terminalStatus)) mapper.cancelOpenProjectTasks(projectId, userName);
    }

    private void requireLegacyEffortPolicy(BusinessProject project)
    { if(BusinessMemberDayCostService.enabled(project))throw new ServiceException("已按成员工作日自动计成本，无需设置或确认投入");if("ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()))throw new ServiceException("历史工作记录仅保留追溯"); }

    private void ensureReadyForAcceptance(Long projectId)
    {
        List<BusinessProjectTask> tasks = mapper.selectTasks(projectId);
        if(tasks==null)tasks=Collections.emptyList();
        if(tasks.isEmpty()){
            List<BusinessProjectRoutine> routines=mapper.selectRoutines(projectId,new Date());
            List<BusinessProjectRoutine> retired=mapper.selectRetiredRoutines(projectId,new Date());
            Map<String,Object> relation=mapper.selectActiveExecutionRelation(projectId);
            List<BusinessProjectRoutine> source=relation==null?Collections.emptyList():mapper.selectLiveStreamerRoutines(relation);
            if((routines==null||routines.isEmpty())&&(retired==null||retired.isEmpty())&&(source==null||source.isEmpty()))
                throw new ServiceException("项目至少需要一项任务或持续工作记录才能验收");
            if(workMapper.countPendingWork(projectId)>0)throw new ServiceException("持续工作仍有待处理记录，请先处理后结项");
        }
        List<String> blockers = new ArrayList<String>();
        List<String> unfinishedTasks = new ArrayList<String>();
        for (BusinessProjectTask task : tasks)
        {
            if (!isTaskComplete(task)) unfinishedTasks.add(StringUtils.defaultIfEmpty(task.getTaskName(), "未命名任务"));
        }
        if (!unfinishedTasks.isEmpty()) blockers.add("存在未完成任务：" + String.join("、", unfinishedTasks));
        List<BusinessProjectMilestone> milestones = mapper.selectMilestones(projectId);
        List<String> unfinishedMilestones = new ArrayList<String>();
        if (milestones != null)
        {
            for (BusinessProjectMilestone milestone : milestones)
            {
                if (!"DONE".equals(milestone.getStatus()))
                    unfinishedMilestones.add(StringUtils.defaultIfEmpty(milestone.getMilestoneName(), "未命名里程碑"));
            }
        }
        if (!unfinishedMilestones.isEmpty()) blockers.add("存在未完成里程碑：" + String.join("、", unfinishedMilestones));
        List<BusinessProjectRisk> risks = mapper.selectRisks(projectId);
        List<String> openHighRisks = new ArrayList<String>();
        if (risks != null)
        {
            for (BusinessProjectRisk risk : risks)
            {
                if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                    openHighRisks.add(StringUtils.defaultIfEmpty(risk.getRiskTitle(), "未命名风险"));
            }
        }
        if (!openHighRisks.isEmpty()) blockers.add("存在高风险或严重风险未处理：" + String.join("、", openHighRisks));
        if (!blockers.isEmpty()) throw new ServiceException(String.join("；", blockers) + "，暂不能申请或通过验收");
    }

    private Date normalizeLeaveDate(Date requested, String emptyMessage)
    {
        if (requested == null) throw new ServiceException(emptyMessage);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        try { return format.parse(format.format(requested)); }
        catch (Exception ex) { throw new ServiceException("请假日期格式不正确"); }
    }

    private void decorateWorkAbsence(List<Map<String,Object>> rows, Long userId, Date bizDate)
    {
        Map<Long,BusinessProject> projects = new HashMap<Long,BusinessProject>();
        for (Map<String,Object> row : rows)
        {
            Long id = longValue(row.get("projectId"));
            if (id == null) continue;
            if (!projects.containsKey(id)) projects.put(id, mapper.selectProjectById(id));
            BusinessProject p = projects.get(id);
            if (p == null) continue;
            Map<String,Object> authority = feishuService.getPersonAuthority(userId, p.getCompanyDeptId(), bizDate);
            boolean independent = "ACTUAL_WORK_V1".equals(p.getCostPolicyVersion())
                || authority != null && "FEISHU".equals(authority.get("source"));
            row.put("costPolicyVersion", p.getCostPolicyVersion());
            row.put("allowWorkReport", independent || row.get("todayLeaveId") == null);
            if (independent) { row.remove("todayLeaveId"); row.remove("todayLeaveReason"); }
        }
    }

    private void requireNotOnLeave(BusinessProject project, Long userId, Date bizDate)
    {
        // Work outcomes have their own evidence; absence never fabricates or removes actual work.
        if ("ACTUAL_WORK_V1".equals(project.getCostPolicyVersion())
            || "FEISHU".equals(feishuService.getPersonAuthority(userId, project.getCompanyDeptId(), bizDate).get("source"))) return;
        mapper.lockUserForLeave(userId);
        Map<String, Object> leave = mapper.selectStaffLeave(userId, bizDate);
        if (leave != null && "ACTIVE".equals(String.valueOf(leave.get("status"))))
            throw new ServiceException("今日已登记请假，无需填报完成量");
    }

    private Date leaveDateValue(Object value)
    {
        Date parsed = value instanceof Date ? (Date)value : DateUtils.parseDate(value);
        return normalizeLeaveDate(parsed, "请假日期不正确");
    }

    private List<Date> leaveDays(Date from, Date to)
    {
        List<Date> days = new ArrayList<Date>();
        Calendar cursor = Calendar.getInstance(); cursor.setTime(from);
        while (!cursor.getTime().after(to))
        {
            days.add(cursor.getTime());
            if (days.size() > 31) break;
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return days;
    }

    private String dateRange(Date from, Date to)
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.format(from) + (from.equals(to) ? "" : " 至 " + format.format(to));
    }

    private boolean isTaskComplete(BusinessProjectTask task)
    {
        return "DONE".equals(task.getStatus()) || (task.getProgress() != null && task.getProgress() >= 100);
    }

    private void ensureMilestoneTasksReady(Long projectId, Long milestoneId)
    {
        List<BusinessProjectTask> tasks = mapper.selectTasks(projectId);
        boolean linkedTaskFound = false;
        if (tasks != null)
        {
            for (BusinessProjectTask task : tasks)
            {
                if (milestoneId.equals(task.getMilestoneId()))
                {
                    linkedTaskFound = true;
                    if (!isTaskComplete(task))
                        throw new ServiceException("该里程碑仍有未完成任务，暂不能提交阶段验收");
                }
            }
        }
        if (!linkedTaskFound) throw new ServiceException("该里程碑至少需要关联一项任务才能提交阶段验收");
    }

    private void ensureStagesReadyForClose(Long projectId)
    {
        List<BusinessProjectMilestone> milestones = mapper.selectMilestones(projectId);
        if (milestones == null || milestones.isEmpty()) throw new ServiceException("阶段验收项目至少需要一个里程碑");
        for (BusinessProjectMilestone milestone : milestones)
        {
            if (!"DONE".equals(milestone.getStatus())) throw new ServiceException("仍有里程碑未通过阶段验收，暂不能结项");
        }
        ensureHighRisksClosed(projectId);
    }

    private void ensureKeyMilestonesReady(Long projectId)
    {
        List<BusinessProjectMilestone> milestones = mapper.selectMilestones(projectId);
        if (milestones == null || milestones.isEmpty()) throw new ServiceException("重点监管项目至少需要一个里程碑");
        for (BusinessProjectMilestone milestone : milestones)
            if (!"DONE".equals(milestone.getStatus())) throw new ServiceException("重点监管项目仍有未完成里程碑，暂不能结项或验收");
    }

    private void ensureHighRisksClosed(Long projectId)
    {
        List<BusinessProjectRisk> risks = mapper.selectRisks(projectId);
        if (risks == null) return;
        for (BusinessProjectRisk risk : risks)
        {
            if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                throw new ServiceException("仍有高风险或严重风险未处理，暂不能结项");
        }
    }

    private void validateStageAcceptance(BusinessProjectStageAcceptance acceptance)
    {
        if (StringUtils.isBlank(acceptance.getResultSummary())) throw new ServiceException("请填写阶段结果摘要");
        if (StringUtils.isBlank(acceptance.getDeliverables())) throw new ServiceException("请填写阶段交付成果");
        if (acceptance.getResultSummary().length() > 2000) throw new ServiceException("阶段结果摘要不能超过2000个字符");
        if (acceptance.getDeliverables().length() > 4000) throw new ServiceException("阶段交付成果不能超过4000个字符");
        if (StringUtils.isNotEmpty(acceptance.getAttachmentUrls()) && acceptance.getAttachmentUrls().length() > 4000)
            throw new ServiceException("验收附件数量或地址长度超出限制");
    }

    private void ensureKpiReadyForClose(Long projectId)
    {
        List<Map<String, Object>> plans = kpiMapper.selectPlanSummaries(projectId);
        if (plans == null) plans = Collections.emptyList();

        int publishedPlanCount = 0;
        int pendingInputCount = 0;
        int returnedCount = 0;
        int pendingReviewCount = 0;
        int otherUnconfirmedCount = 0;
        String nextCycleEnd = null;
        String today = DateUtils.getDate();

        for (Map<String, Object> plan : plans)
        {
            if (plan == null) continue;
            String planStatus = value(plan.get("status"));
            if (!"PUBLISHED".equals(planStatus) && !"CLOSED".equals(planStatus)) continue;
            publishedPlanCount++;

            String settlementStatus = value(plan.get("settlementStatus"));
            if ("CONFIRMED".equals(settlementStatus)) continue;

            Date cycleEnd = plan.get("cycleEnd") instanceof Date
                ? (Date) plan.get("cycleEnd") : DateUtils.parseDate(plan.get("cycleEnd"));
            if (cycleEnd == null)
                throw new ServiceException("KPI方案的考核结束日期异常，请检查后再结项");
            String cycleEndDate = DateUtils.dateTime(cycleEnd);
            if (cycleEndDate.compareTo(today) > 0)
            {
                if (nextCycleEnd == null || cycleEndDate.compareTo(nextCycleEnd) < 0) nextCycleEnd = cycleEndDate;
                continue;
            }

            if (StringUtils.isBlank(settlementStatus) || "DRAFT".equals(settlementStatus)) pendingInputCount++;
            else if ("RETURNED".equals(settlementStatus)) returnedCount++;
            else if ("SUBMITTED".equals(settlementStatus)) pendingReviewCount++;
            else otherUnconfirmedCount++;
        }

        if (publishedPlanCount == 0)
            throw new ServiceException("项目尚未发布KPI方案，请先设置并发布KPI及奖金方案后再结项");
        if (nextCycleEnd != null)
            throw new ServiceException("尚有KPI考核周期未结束（最近结束日期：" + nextCycleEnd + "），暂不能结项");

        int incompleteCount = pendingInputCount + returnedCount + pendingReviewCount + otherUnconfirmedCount;
        if (incompleteCount == 0) return;
        int incompleteKinds = (pendingInputCount > 0 ? 1 : 0) + (returnedCount > 0 ? 1 : 0)
            + (pendingReviewCount > 0 ? 1 : 0) + (otherUnconfirmedCount > 0 ? 1 : 0);
        if (incompleteKinds > 1)
            throw new ServiceException("存在多项已到期但尚未完成确认的KPI结算，请先全部完成负责人填报和老板确认后再结项");
        if (pendingInputCount > 0)
            throw new ServiceException("存在已到期但负责人尚未提交的KPI结算，请先完成结果填报并提交后再结项");
        if (returnedCount > 0)
            throw new ServiceException("存在已到期且被退回的KPI结算，请负责人修改并重新提交后再结项");
        if (pendingReviewCount > 0)
            throw new ServiceException("存在已到期且待老板确认的KPI结算，请先确认KPI及奖金后再结项");
        throw new ServiceException("存在已到期但尚未确认的KPI结算，请先完成结算后再结项");
    }

    private String value(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private boolean sameLong(Object value, Long expected)
    {
        return value != null && expected != null && String.valueOf(value).equals(String.valueOf(expected));
    }

    private void validateProject(BusinessProject project)
    {
        if (project == null || StringUtils.isBlank(project.getProjectName())) throw new ServiceException("项目名称不能为空");
        if (project.getProjectName().length() > 160) throw new ServiceException("项目名称不能超过160个字符");
        if (project.getObjective() != null && project.getObjective().length() > 1000)
            throw new ServiceException("项目目标不能超过1000个字符");
        if (project.getMainOwnerUserId() == null) throw new ServiceException("请选择项目主负责人");
        if (StringUtils.isBlank(project.getProjectType())) project.setProjectType("GENERAL");
        if (StringUtils.isNotBlank(project.getExecutionSource()) && !"LIVE".equals(project.getExecutionSource()))
            throw new ServiceException("项目执行系统类型不正确");
        if (project.getCompanyDeptId() != null && mapper.selectCompanyById(project.getCompanyDeptId()) == null)
            throw new ServiceException("项目归属公司不正确");
        if (StringUtils.isBlank(project.getAccountingMode())) project.setAccountingMode("PROFIT");
        if (!ACCOUNTING_MODES.contains(project.getAccountingMode())) throw new ServiceException("项目核算模式不正确");
        if (StringUtils.isBlank(project.getGoalMode())) project.setGoalMode("TOTAL");
        if (!PROJECT_GOAL_MODES.contains(project.getGoalMode())) throw new ServiceException("项目目标模式不正确");
        if(StringUtils.isBlank(project.getBudgetMode()))project.setBudgetMode(project.getBudgetLimit()==null?"NONE":"TOTAL");
        if(StringUtils.isBlank(project.getBudgetScope()))project.setBudgetScope("FULL_COST");
        if(!Arrays.asList("TOTAL","DAILY","NONE").contains(project.getBudgetMode())||!Arrays.asList("FULL_COST","CASH_EXPENSE").contains(project.getBudgetScope()))throw new ServiceException("预算控制方式或统计口径不正确");
        project.setManagementMode(normalizeManagementMode(project.getManagementMode()));
        if (!MANAGEMENT_MODES.contains(project.getManagementMode())) throw new ServiceException("项目管理模式不正确");
        if (StringUtils.isBlank(project.getCloseMethod())) project.setCloseMethod("DIRECT");
        if (!CLOSE_METHODS.contains(project.getCloseMethod())) throw new ServiceException("项目结项方式不正确");
        if ("KEY_CONTROL".equals(project.getManagementMode()) && StringUtils.isBlank(project.getManagementReason()))
            throw new ServiceException("重点监管项目必须填写选择该模式的理由");
        if (StringUtils.isNotEmpty(project.getManagementReason()) && project.getManagementReason().length() > 1000)
            throw new ServiceException("管理模式选择理由不能超过1000个字符");
        if (!"DIRECT".equals(project.getCloseMethod()) && StringUtils.isBlank(project.getAcceptanceCriteria()))
            throw new ServiceException("成果验收或阶段验收项目必须填写验收标准");
        if (StringUtils.isNotEmpty(project.getAcceptanceCriteria()) && project.getAcceptanceCriteria().length() > 2000)
            throw new ServiceException("验收标准不能超过2000个字符");
        if (StringUtils.isBlank(project.getPriority())) project.setPriority("MEDIUM");
        if (!PRIORITIES.contains(project.getPriority())) throw new ServiceException("项目优先级不正确");
        if (StringUtils.isBlank(project.getBaseCurrency())) project.setBaseCurrency("CNY");
        project.setBaseCurrency(project.getBaseCurrency().trim().toUpperCase());
        validateCurrency(project.getBaseCurrency());
        if (project.getBudgetLimit() != null && project.getBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算上限不能为负数");
        if (project.getDailyBudgetLimit() != null && project.getDailyBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("每日预算上限不能为负数");
        if (project.getStartupBudgetLimit() != null && project.getStartupBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("一次性启动预算不能为负数");
        if (project.getPlanStartDate() != null && project.getPlanEndDate() != null
            && project.getPlanStartDate().after(project.getPlanEndDate()))
            throw new ServiceException("计划结束日期不能早于开始日期");
    }

    private String normalizeManagementMode(String mode)
    {
        if (StringUtils.isBlank(mode)) return "STANDARD";
        if ("SIMPLE".equals(mode)) return "LIGHT";
        if ("DELIVERY".equals(mode)) return "STANDARD";
        return mode;
    }

    private String effectiveGoalMode(BusinessProject project)
    {
        return StringUtils.isBlank(project.getGoalMode()) ? "TOTAL" : project.getGoalMode();
    }

    private String effectiveCloseMethod(BusinessProject project)
    {
        if (StringUtils.isNotBlank(project.getCloseMethod())) return project.getCloseMethod();
        return "DELIVERY".equals(project.getManagementMode()) ? "RESULT_ACCEPTANCE" : "DIRECT";
    }

    private int closeMethodRank(String closeMethod)
    {
        if ("STAGED_ACCEPTANCE".equals(closeMethod)) return 3;
        if ("RESULT_ACCEPTANCE".equals(closeMethod)) return 2;
        return 1;
    }

    private boolean hasAcceptanceRecords(Long projectId)
    {
        List<BusinessProjectAcceptance> acceptances = mapper.selectAcceptances(projectId);
        if (acceptances != null && !acceptances.isEmpty()) return true;
        List<BusinessProjectStageAcceptance> stageAcceptances = mapper.selectStageAcceptances(projectId);
        return stageAcceptances != null && !stageAcceptances.isEmpty();
    }

    private Map<String, Object> buildGovernanceProfile(BusinessProject project)
    {
        String mode = normalizeManagementMode(project.getManagementMode());
        String closeMethod = effectiveCloseMethod(project);
        Map<String, Object> profile = new LinkedHashMap<String, Object>();
        profile.put("managementMode", mode);
        profile.put("closeMethod", closeMethod);
        profile.put("riskRequired", !"LIGHT".equals(mode));
        profile.put("milestoneRequired", "KEY_CONTROL".equals(mode) || "STAGED_ACCEPTANCE".equals(closeMethod));
        profile.put("changeApprovalRequired", "KEY_CONTROL".equals(mode));
        profile.put("reportCycle", "LIGHT".equals(mode) ? "EXCEPTION" : ("KEY_CONTROL".equals(mode) ? "WEEKLY_AND_EVENT" : "WEEKLY"));
        profile.put("budgetAlertThresholds", "LIGHT".equals(mode)
            ? Arrays.asList(100) : ("KEY_CONTROL".equals(mode) ? Arrays.asList(70, 90, 100) : Arrays.asList(80, 100)));
        List<String> modules = new ArrayList<String>(Arrays.asList("OVERVIEW", "MEMBER", "TASK", "EFFORT", "COST", "KPI"));
        if (!"LIGHT".equals(mode)) modules.add("RISK");
        if (!"LIGHT".equals(mode) || "STAGED_ACCEPTANCE".equals(closeMethod)) modules.add("MILESTONE");
        if ("RESULT_ACCEPTANCE".equals(closeMethod)) modules.add("RESULT_ACCEPTANCE");
        if ("STAGED_ACCEPTANCE".equals(closeMethod)) modules.add("STAGE_ACCEPTANCE");
        profile.put("enabledModules", modules);
        return profile;
    }

    private void validateParent(Long parentId, Long currentProjectId, Long sponsorOwnerUserId)
    {
        if (parentId == null) return;
        if (parentId.equals(currentProjectId)) throw new ServiceException("项目不能成为自己的父项目");
        BusinessProject cursor = requireProjectForUpdate(parentId);
        if (!sponsorOwnerUserId.equals(projectSponsorUserId(cursor)))
            throw new ServiceException("上级项目必须属于同一位归属老板");
        if (cursor.getParentId() != null) throw new ServiceException("仅支持主项目与子项目两级结构，子项目不能再创建下级项目");
    }

    private Map<String, Object> requireActiveUser(Long userId)
    {
        if (userId == null) throw new ServiceException("请选择有效账号");
        Map<String, Object> user = mapper.selectActiveUserById(userId);
        if (user == null) throw new ServiceException("所选账号不存在或已停用");
        return user;
    }

    private Map<String, Object> requireCostEligibleUser(Long userId)
    {
        if (userId == null) throw new ServiceException("请选择有效人员");
        Map<String, Object> user = mapper.selectCostEligibleUserById(userId);
        if (user == null) throw new ServiceException("所选人员不存在或已离职");
        return user;
    }

    private Long projectSponsorUserId(BusinessProject project)
    {
        return project.getSponsorOwnerUserId() == null ? project.getInitiatorUserId() : project.getSponsorOwnerUserId();
    }

    private boolean hasPermission(String permission)
    {
        try { return SecurityUtils.hasPermi(permission); }
        catch (RuntimeException ex) { return false; }
    }

    private String displayName(Map<String, Object> user)
    {
        Object nick = user.get("nickName");
        if (nick != null && StringUtils.isNotBlank(String.valueOf(nick))) return String.valueOf(nick);
        return String.valueOf(user.get("userName"));
    }

    private void grantProjectUser(Long userId, boolean projectOwner)
    {
        if (mapper.countUserRoleByKey(userId, "company_owner") > 0) return;
        Long roleId = mapper.selectRoleIdByKey("project_user");
        if (roleId == null) throw new ServiceException("项目参与人员角色尚未初始化");
        mapper.insertUserRole(userId, roleId);
        if (projectOwner)
        {
            Long ownerRoleId = mapper.selectRoleIdByKey("project_owner");
            if (ownerRoleId == null) throw new ServiceException("项目负责人角色尚未初始化");
            mapper.insertUserRole(userId, ownerRoleId);
        }
        onlineUserPermissionService.refreshAfterCommit(userId);
    }

    private void syncProjectDeputyRole(Long userId)
    {
        Long roleId = mapper.selectRoleIdByKey("project_deputy");
        if (roleId == null) throw new ServiceException("项目副负责人角色尚未初始化");
        boolean eligible = mapper.countUserRoleByKey(userId, "company_owner") < 1
            && mapper.countActiveProjectMembershipByRole(userId, "DEPUTY") > 0;
        int changed = eligible ? mapper.insertUserRole(userId, roleId) : mapper.deleteUserRole(userId, roleId);
        if (changed > 0) onlineUserPermissionService.refreshAfterCommit(userId);
    }

    private void attachWorkPeriods(Long projectId, List<BusinessProjectTask> tasks,
        List<BusinessProjectTask> inactiveTasks, List<BusinessProjectRoutine> routines,
        List<BusinessProjectRoutine> retiredRoutines)
    {
        List<BusinessProjectWorkPeriod> periods = mapper.selectWorkPeriods(projectId);
        if (periods == null) periods = Collections.emptyList();
        Map<String, List<BusinessProjectWorkPeriod>> grouped = new HashMap<String, List<BusinessProjectWorkPeriod>>();
        for (BusinessProjectWorkPeriod period : periods)
        {
            String key = period.getWorkType() + ":" + period.getWorkId();
            if (!grouped.containsKey(key)) grouped.put(key, new ArrayList<BusinessProjectWorkPeriod>());
            grouped.get(key).add(period);
        }
        for (BusinessProjectTask task : tasks)
            task.setExecutionPeriods(grouped.getOrDefault("TASK:" + task.getTaskId(), Collections.<BusinessProjectWorkPeriod>emptyList()));
        for (BusinessProjectTask task : inactiveTasks)
            task.setExecutionPeriods(grouped.getOrDefault("TASK:" + task.getTaskId(), Collections.<BusinessProjectWorkPeriod>emptyList()));
        for (BusinessProjectRoutine routine : routines)
            routine.setExecutionPeriods(grouped.getOrDefault("ROUTINE:" + routine.getRoutineId(), Collections.<BusinessProjectWorkPeriod>emptyList()));
        for (BusinessProjectRoutine routine : retiredRoutines)
            routine.setExecutionPeriods(grouped.getOrDefault("ROUTINE:" + routine.getRoutineId(), Collections.<BusinessProjectWorkPeriod>emptyList()));
    }

    private void openWorkPeriod(Long projectId, String workType, Long workId, Long assigneeUserId,
        String assigneeName, Date startDate, String userName)
    {
        if (assigneeUserId == null || workId == null) return;
        BusinessProjectWorkPeriod period = new BusinessProjectWorkPeriod();
        period.setProjectId(projectId); period.setWorkType(workType); period.setWorkId(workId);
        period.setAssigneeUserId(assigneeUserId); period.setAssigneeName(assigneeName);
        period.setStartDate(startDate == null ? DateUtils.getNowDate() : startDate);
        period.setStatus("ACTIVE"); period.setVersion(0); period.setCreateBy(userName);
        mapper.insertWorkPeriod(period);
    }

    private void requireRunnableAssignee(Long projectId, Long assigneeUserId, String workLabel)
    {
        if (assigneeUserId == null) throw new ServiceException(workLabel + "没有负责人，请重新创建安排");
        String role = mapper.selectMemberRole(projectId, assigneeUserId);
        if (role == null || "OBSERVER".equals(role))
            throw new ServiceException(workLabel + "负责人已不是有效项目成员，不能启用");
        requireActiveUser(assigneeUserId);
    }

    private Map<String, Object> workAssigneeSnapshot(Long assigneeUserId, String savedAssigneeName)
    {
        if (assigneeUserId == null && StringUtils.isBlank(savedAssigneeName)) return null;
        Map<String, Object> user = assigneeUserId == null ? null : mapper.selectUserAuditSnapshotById(assigneeUserId);
        Map<String, Object> snapshot = user == null
            ? new HashMap<String, Object>() : new HashMap<String, Object>(user);
        snapshot.put("userId", assigneeUserId);
        if (StringUtils.isNotBlank(savedAssigneeName)) snapshot.put("nickName", savedAssigneeName);
        return snapshot;
    }

    private void addEvent(Long projectId, String eventType, String fromStatus, String toStatus,
        Long userId, String userName, String comment)
    {
        addEvent(projectId, eventType, fromStatus, toStatus, userId, userName, comment, null);
    }

    private void addEvent(Long projectId, String eventType, String fromStatus, String toStatus,
        Long userId, String userName, String comment, Map<String, Object> subjectUser)
    {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("projectId", projectId); event.put("eventType", eventType);
        event.put("fromStatus", fromStatus); event.put("toStatus", toStatus);
        event.put("operatorUserId", userId); event.put("operatorName", userName);
        if (subjectUser != null)
        {
            event.put("subjectUserId", subjectUser.get("userId"));
            event.put("subjectName", displayName(subjectUser));
            event.put("subjectAccount", subjectUser.get("userName"));
        }
        event.put("comment", comment);
        mapper.insertEvent(event);
    }

    private ServiceException changed()
    {
        return new ServiceException("数据已被其他人修改，请刷新后重试");
    }
}
