package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

@Service
public class BusinessProjectServiceImpl implements IBusinessProjectService
{
    private static final List<String> ACCOUNTING_MODES = Arrays.asList("PROFIT", "COST", "VALUE", "HYBRID");
    private static final List<String> MANAGEMENT_MODES = Arrays.asList("SIMPLE", "STANDARD", "DELIVERY");
    private static final List<String> ROUTINE_FREQUENCIES = Arrays.asList("DAILY", "WEEKLY", "MONTHLY");
    private static final List<String> PRIORITIES = Arrays.asList("LOW", "MEDIUM", "HIGH");
    private static final List<String> MEMBER_ROLES = Arrays.asList("DEPUTY", "MEMBER", "OBSERVER");
    private static final List<String> TASK_STATUSES = Arrays.asList("TODO", "DOING", "BLOCKED", "DONE");
    private static final List<String> RISK_SEVERITIES = Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> KPI_METRIC_TYPES = Arrays.asList("COUNT", "AMOUNT", "PERCENT", "DURATION", "SCORE", "MILESTONE");
    private static final List<String> KPI_PERIOD_TYPES = Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "PROJECT");
    private static final List<String> COST_MODES = Arrays.asList("DAILY", "HOURLY", "MONTHLY", "FIXED_PROJECT", "FIXED_TASK", "VARIABLE");
    private static final List<String> ALLOCATION_MODES = Arrays.asList("PERCENTAGE", "HOURS", "ATTENDANCE", "FIXED_DAILY", "PER_TASK");

    @Autowired
    private BusinessProjectMapper mapper;

    @Autowired
    private BusinessAccountingMapper accountingMapper;

    @Autowired
    private IBusinessAccountingService accountingService;

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
        BusinessProject project = requireProject(projectId);
        requireAccess(project, userId, viewAll, boss);
        List<BusinessProjectRoutine> storedRoutines = mapper.selectRoutines(projectId, new Date());
        List<BusinessProjectRoutine> routines = storedRoutines == null
            ? new ArrayList<BusinessProjectRoutine>()
            : new ArrayList<BusinessProjectRoutine>(storedRoutines);
        project.setMembers(mapper.selectMembers(projectId));
        project.setMilestones(mapper.selectMilestones(projectId));
        project.setTasks(mapper.selectTasks(projectId));
        project.setRisks(mapper.selectRisks(projectId));
        project.setOwnerHistory(mapper.selectOwnerHistory(projectId));
        project.setAcceptances(mapper.selectAcceptances(projectId));
        project.setEvents(mapper.selectEvents(projectId));
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
        return project;
    }

    @Override
    @Transactional
    public BusinessProject createProject(BusinessProject project, Long userId, String userName)
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
        BusinessProject current = requireProject(input.getProjectId());
        requireManage(current, userId, boss);
        ensureMutable(current);
        input.setBaseCurrency(current.getBaseCurrency());
        input.setBudgetLimit(current.getBudgetLimit());
        if (!boss)
        {
            if (!Arrays.asList("DRAFT", "PLANNING").contains(current.getStatus())
                || "SUBMITTED".equals(current.getBaselineStatus()))
                throw new ServiceException("当前状态不能修改项目规划");
            input.setParentId(current.getParentId());
            input.setProjectType(current.getProjectType());
            input.setAccountingMode(current.getAccountingMode());
            input.setCompanyDeptId(current.getCompanyDeptId());
        }
        validateProject(input);
        validateParent(input.getParentId(), current.getProjectId(), current.getInitiatorUserId());
        input.setVersion(current.getVersion());
        input.setUpdateBy(userName);
        if (mapper.updateProject(input) != 1) throw changed();
        addEvent(current.getProjectId(), "EDIT", current.getStatus(), current.getStatus(), userId, userName, "更新项目资料");
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
        BusinessProject project = requireProject(projectId);
        requireBoss(project, userId, boss);
        ensureMutable(project);
        if (budgetLimit == null || budgetLimit.compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算金额不能为空或为负数");
        if (StringUtils.isBlank(reason)) throw new ServiceException("请填写预算调整原因");
        if (StringUtils.isBlank(currency)) currency = project.getBaseCurrency();
        currency = currency.trim().toUpperCase();
        if (currency.length() != 3) throw new ServiceException("币种代码必须为3位");
        List<Map<String, Object>> historyRows = mapper.selectBudgetHistory(projectId);
        int budgetVersion = historyRows == null ? 1 : historyRows.size() + 1;
        if (mapper.updateProjectBudget(projectId, budgetLimit, currency, userName, project.getVersion()) != 1)
            throw changed();
        Map<String, Object> history = new HashMap<String, Object>();
        history.put("projectId", projectId); history.put("fromAmount", project.getBudgetLimit());
        history.put("toAmount", budgetLimit); history.put("currency", currency);
        history.put("budgetVersion", budgetVersion); history.put("reason", reason.trim());
        history.put("operatorUserId", userId); history.put("operatorName", userName);
        mapper.insertBudgetHistory(history);
        addEvent(projectId, "BUDGET_CHANGE", project.getStatus(), project.getStatus(), userId, userName,
            "预算调整为 " + budgetLimit.toPlainString() + " " + currency + "：" + reason.trim());
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProjectKpi saveKpi(BusinessProjectKpi kpi, Long userId, String userName, boolean boss)
    {
        if (kpi == null || kpi.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProject(kpi.getProjectId());
        requireBoss(project, userId, boss); ensureMutable(project);
        BusinessProjectKpi previous = null;
        if (kpi.getKpiId() != null)
        {
            previous = mapper.selectProjectKpiById(kpi.getKpiId());
            if (previous == null || !project.getProjectId().equals(previous.getProjectId()) || !"CURRENT".equals(previous.getStatus()))
                throw new ServiceException("KPI当前版本不存在，请刷新后重试");
            kpi.setKpiCode(previous.getKpiCode());
        }
        if (StringUtils.isBlank(kpi.getKpiCode())) throw new ServiceException("请填写KPI编码");
        if (StringUtils.isBlank(kpi.getKpiName())) throw new ServiceException("请填写KPI名称");
        kpi.setKpiCode(kpi.getKpiCode().trim().toUpperCase());
        if (!kpi.getKpiCode().matches("[A-Z0-9_\\-]{2,64}")) throw new ServiceException("KPI编码只能使用字母、数字、下划线或短横线");
        if (previous == null && mapper.selectCurrentProjectKpi(project.getProjectId(), kpi.getKpiCode()) != null)
            throw new ServiceException("该KPI编码已存在，请从现有KPI执行目标调整");
        if (StringUtils.isBlank(kpi.getMetricType())) kpi.setMetricType("COUNT");
        if (!KPI_METRIC_TYPES.contains(kpi.getMetricType())) throw new ServiceException("KPI指标类型不正确");
        if (StringUtils.isBlank(kpi.getPeriodType())) kpi.setPeriodType("PROJECT");
        if (!KPI_PERIOD_TYPES.contains(kpi.getPeriodType())) throw new ServiceException("KPI统计周期不正确");
        if (kpi.getTargetValue() == null) throw new ServiceException("请填写KPI目标值");
        if (kpi.getWeight() == null) kpi.setWeight(BigDecimal.ZERO);
        if (kpi.getWeight().compareTo(BigDecimal.ZERO) < 0 || kpi.getWeight().compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException("KPI权重必须在0到100之间");
        if (kpi.getOwnerUserId() != null)
        {
            if (mapper.selectMemberRole(project.getProjectId(), kpi.getOwnerUserId()) == null)
                throw new ServiceException("KPI负责人必须是当前项目成员");
            kpi.setOwnerName(displayName(requireActiveUser(kpi.getOwnerUserId())));
        }
        if (kpi.getEffectiveFrom() == null) kpi.setEffectiveFrom(new Date());
        if (kpi.getEffectiveTo() != null && kpi.getEffectiveTo().before(kpi.getEffectiveFrom()))
            throw new ServiceException("KPI失效日期不能早于生效日期");
        if (StringUtils.isBlank(kpi.getDirection())) kpi.setDirection("HIGHER_BETTER");
        if (StringUtils.isBlank(kpi.getAggregateType())) kpi.setAggregateType("SUM");
        if (StringUtils.isBlank(kpi.getSourceType())) kpi.setSourceType("MANUAL");
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

    @Override
    @Transactional
    public void retireKpi(Long projectId, Long kpiId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId); requireBoss(project, userId, boss); ensureMutable(project);
        BusinessProjectKpi kpi = mapper.selectProjectKpiById(kpiId);
        if (kpi == null || !projectId.equals(kpi.getProjectId()) || mapper.retireProjectKpi(kpiId, userName) != 1)
            throw new ServiceException("KPI当前版本不存在");
        addEvent(projectId, "KPI_RETIRE", project.getStatus(), project.getStatus(), userId, userName, kpi.getKpiName());
    }

    @Override
    public List<BusinessStaffCostPolicy> staffCostPolicies(Long staffUserId, Long userId, boolean boss)
    {
        if (!boss && !SecurityUtils.isAdmin(userId)) throw new ServiceException("只有老板可以查看人员内部核算成本");
        requireActiveUser(staffUserId);
        return mapper.selectStaffCostPolicies(staffUserId);
    }

    @Override
    @Transactional
    public BusinessStaffCostPolicy saveStaffCostPolicy(BusinessStaffCostPolicy policy,
        Long userId, String userName, boolean boss)
    {
        if (!boss && !SecurityUtils.isAdmin(userId)) throw new ServiceException("只有老板可以设置人员内部核算成本");
        if (policy == null || policy.getUserId() == null) throw new ServiceException("请选择人员");
        requireActiveUser(policy.getUserId());
        if (!COST_MODES.contains(policy.getCostMode())) throw new ServiceException("内部成本核算方式不正确");
        if (policy.getUnitCost() == null || policy.getUnitCost().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("内部核算金额不能为空或为负数");
        if (StringUtils.isBlank(policy.getCurrency())) policy.setCurrency("CNY");
        policy.setCurrency(policy.getCurrency().trim().toUpperCase());
        if (policy.getCurrency().length() != 3) throw new ServiceException("币种代码必须为3位");
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
    public BusinessProjectStaffAllocation saveStaffAllocation(BusinessProjectStaffAllocation allocation,
        Long userId, String userName, boolean boss)
    {
        if (allocation == null || allocation.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProject(allocation.getProjectId());
        requireManage(project, userId, boss); ensureMutable(project);
        if (allocation.getUserId() == null || mapper.selectMemberRole(project.getProjectId(), allocation.getUserId()) == null)
            throw new ServiceException("成本分摊人员必须是当前项目成员");
        if (!ALLOCATION_MODES.contains(allocation.getAllocationMode())) throw new ServiceException("成本分摊方式不正确");
        if (allocation.getAllocationValue() == null || allocation.getAllocationValue().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("成本分摊参数不能为空或为负数");
        if (allocation.getEffectiveFrom() == null) throw new ServiceException("请选择分摊生效日期");
        if (allocation.getEffectiveTo() != null && allocation.getEffectiveTo().before(allocation.getEffectiveFrom()))
            throw new ServiceException("分摊失效日期不能早于生效日期");
        if (mapper.countOverlappingProjectAllocation(project.getProjectId(), allocation.getUserId(),
            allocation.getEffectiveFrom(), allocation.getEffectiveTo(), allocation.getAllocationId()) > 0)
            throw new ServiceException("该人员在本项目所选日期已有计划投入，请编辑原记录或使用不重叠日期");
        BusinessStaffCostPolicy policy = !boss || allocation.getCostPolicyId() == null
            ? mapper.selectEffectiveStaffCostPolicy(allocation.getUserId(), allocation.getEffectiveFrom())
            : mapper.selectStaffCostPolicyById(allocation.getCostPolicyId());
        if (policy == null || !allocation.getUserId().equals(policy.getUserId()))
            throw new ServiceException("该人员在分摊生效日没有可用的内部成本政策");
        allocation.setCostPolicyId(policy.getPolicyId());
        if ("PERCENTAGE".equals(allocation.getAllocationMode()))
        {
            BigDecimal used = mapper.sumOverlappingAllocationPercent(allocation.getUserId(), allocation.getEffectiveFrom(),
                allocation.getEffectiveTo(), allocation.getAllocationId());
            if (used.add(allocation.getAllocationValue()).compareTo(new BigDecimal("100")) > 0)
            {
                if (!boss) throw new ServiceException("该人员同期跨项目计划投入超过100%，请调整后再保存");
                if (!"1".equals(allocation.getExceptionAllowed()) || StringUtils.isBlank(allocation.getExceptionReason()))
                    throw new ServiceException("该人员同期跨项目计划投入超过100%，老板批准例外时必须填写原因");
            }
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
        BusinessProject project = requireProject(projectId); requireManage(project, userId, boss); ensureMutable(project);
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
        BusinessProject project = requireProject(projectId);
        requireBoss(project, userId, boss);
        ensureMutable(project);
        if (project.getMainOwnerUserId().equals(newOwnerUserId)) throw new ServiceException("新负责人不能与当前负责人相同");
        Map<String, Object> newOwner = requireActiveUser(newOwnerUserId);
        String newOwnerName = displayName(newOwner);
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
        BusinessProject project = requireProject(projectId);
        requireAccess(project, userId, SecurityUtils.isAdmin(userId), boss);
        requireOwnerOrBoss(mapper.selectMemberRole(projectId, userId), project, userId, boss);
        requireStatus(project, "ACTIVE");
        if (!"DELIVERY".equals(project.getManagementMode()))
            throw new ServiceException("只有交付模式项目需要提交验收资料");
        ensureReadyForAcceptance(projectId);
        if (acceptance == null || StringUtils.isBlank(acceptance.getResultSummary()))
            throw new ServiceException("请填写项目结果摘要");
        if (StringUtils.isBlank(acceptance.getDeliverables())) throw new ServiceException("请填写交付成果说明");
        if (acceptance.getResultSummary().length() > 2000) throw new ServiceException("项目结果摘要不能超过2000个字符");
        if (acceptance.getDeliverables().length() > 4000) throw new ServiceException("交付成果说明不能超过4000个字符");
        if (StringUtils.isNotEmpty(acceptance.getAttachmentUrls()) && acceptance.getAttachmentUrls().length() > 4000)
            throw new ServiceException("验收附件数量或地址长度超出限制");
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
            acceptance.getResultSummary());
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject reviewAcceptance(Long projectId, String decision, String comment,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireBoss(project, userId, boss);
        requireStatus(project, "ACCEPTANCE");
        BusinessProjectAcceptance pending = mapper.selectLatestPendingAcceptance(projectId);
        if (pending == null) throw new ServiceException("没有待评审的验收资料");
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision))
            throw new ServiceException("验收决定不正确");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
        if (StringUtils.isNotEmpty(comment) && comment.length() > 2000) throw new ServiceException("验收意见不能超过2000个字符");
        if ("APPROVED".equals(decision)) ensureReadyForAcceptance(projectId);
        String reviewerName = displayName(requireActiveUser(userId));
        if (mapper.reviewAcceptance(pending.getAcceptanceId(), decision, userId, reviewerName, comment, userName) != 1)
            throw new ServiceException("验收资料已被其他人处理，请刷新后重试");
        String to = "APPROVED".equals(decision) ? "CLOSED" : "ACTIVE";
        if (mapper.updateProjectStatus(projectId, "ACCEPTANCE", to, null, false, userName, project.getVersion()) != 1)
            throw changed();
        addEvent(projectId, "APPROVED".equals(decision) ? "CLOSE" : "RETURN_ACTIVE",
            "ACCEPTANCE", to, userId, userName, StringUtils.isBlank(comment) ? "验收通过" : comment);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProject transition(Long projectId, String action, String comment,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
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
            if (StringUtils.isBlank(project.getObjective()) || project.getPlanStartDate() == null || project.getPlanEndDate() == null)
                throw new ServiceException("提交前请完善项目目标和计划日期");
            List<BusinessProjectRoutine> manualRoutines = mapper.selectRoutines(projectId, new Date());
            Map<String, Object> executionRelation = mapper.selectActiveExecutionRelation(projectId);
            List<BusinessProjectRoutine> sourceRoutines = executionRelation == null
                ? Collections.<BusinessProjectRoutine>emptyList()
                : mapper.selectLiveStreamerRoutines(executionRelation);
            if (mapper.selectTasks(projectId).isEmpty()
                && (manualRoutines == null || manualRoutines.isEmpty())
                && (sourceRoutines == null || sourceRoutines.isEmpty()))
                throw new ServiceException("提交前至少添加一项任务或持续工作计划");
            baseline = "SUBMITTED";
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
            requireBoss(project, userId, boss); requireStatus(project, "ACTIVE");
            if (StringUtils.isBlank(comment)) throw new ServiceException("暂停原因不能为空");
            to = "PAUSED";
        }
        else if ("RESUME".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "PAUSED"); to = "ACTIVE";
        }
        else if ("REQUEST_ACCEPTANCE".equals(action))
        {
            throw new ServiceException("请填写验收资料后提交验收");
        }
        else if ("RETURN_ACTIVE".equals(action))
        {
            throw new ServiceException("请在验收资料中填写意见并退回执行");
        }
        else if ("CLOSE".equals(action))
        {
            requireBoss(project, userId, boss); requireStatus(project, "ACTIVE");
            if ("DELIVERY".equals(project.getManagementMode()))
                throw new ServiceException("交付模式项目请在验收资料中完成验收");
            if (StringUtils.isBlank(comment)) throw new ServiceException("请填写项目完成结论");
            to = "CLOSED";
        }
        else if ("CANCEL".equals(action))
        {
            requireBoss(project, userId, boss); ensureMutable(project);
            if (StringUtils.isBlank(comment)) throw new ServiceException("取消原因不能为空");
            to = "CANCELED";
        }
        else throw new ServiceException("不支持的项目操作");

        if (mapper.updateProjectStatus(projectId, from, to, baseline, increment, userName, project.getVersion()) != 1)
            throw changed();
        addEvent(projectId, action, from, to, userId, userName, comment);
        return getProject(projectId, userId, SecurityUtils.isAdmin(userId), boss);
    }

    @Override
    @Transactional
    public BusinessProjectMember saveMember(BusinessProjectMember member, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(member.getProjectId());
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
        mapper.upsertMember(member);
        grantProjectUser(member.getUserId(), false);
        addEvent(project.getProjectId(), "MEMBER_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            member.getUserNameSnapshot() + " / " + member.getMemberRole());
        return member;
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long memberUserId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireManage(project, userId, boss);
        ensureMutable(project);
        if (project.getMainOwnerUserId().equals(memberUserId)) throw new ServiceException("不能移除项目主负责人");
        if (mapper.leaveMember(projectId, memberUserId, userName) != 1) throw new ServiceException("项目成员不存在或已经退出");
        addEvent(projectId, "MEMBER_REMOVE", project.getStatus(), project.getStatus(), userId, userName,
            "移除账号ID " + memberUserId);
    }

    @Override
    @Transactional
    public BusinessProjectMilestone saveMilestone(BusinessProjectMilestone milestone,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(milestone.getProjectId());
        requireManage(project, userId, boss); ensureMutable(project);
        if (StringUtils.isBlank(milestone.getMilestoneName())) throw new ServiceException("里程碑名称不能为空");
        if (milestone.getWeight() == null) milestone.setWeight(BigDecimal.ZERO);
        if (milestone.getWeight().compareTo(BigDecimal.ZERO) < 0) throw new ServiceException("里程碑权重不能为负数");
        if (StringUtils.isBlank(milestone.getStatus())) milestone.setStatus("PENDING");
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
        BusinessProject project = requireProject(projectId); requireManage(project, userId, boss); ensureMutable(project);
        if (mapper.deleteMilestone(projectId, milestoneId) != 1)
            throw new ServiceException("里程碑不存在或仍有关联任务");
    }

    @Override
    @Transactional
    public BusinessProjectTask saveTask(BusinessProjectTask task, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(task.getProjectId());
        String callerRole = mapper.selectMemberRole(project.getProjectId(), userId);
        boolean manager = SecurityUtils.isAdmin(userId) || "OWNER".equals(callerRole) || "DEPUTY".equals(callerRole);
        if (boss)
        {
            requireManage(project, userId, true);
            manager = true;
        }
        ensureMutable(project);
        if (!manager)
        {
            if (task.getTaskId() == null) throw new ServiceException("只有项目负责人可以新增任务");
            BusinessProjectTask current = mapper.selectTaskById(task.getTaskId());
            if (current == null || !project.getProjectId().equals(current.getProjectId())) throw new ServiceException("任务不存在");
            if (!userId.equals(current.getAssigneeUserId())) throw new ServiceException("只能更新分配给自己的任务");
            if (task.getVersion() == null || !task.getVersion().equals(current.getVersion())) throw changed();
            if (StringUtils.isBlank(task.getStatus()) || !TASK_STATUSES.contains(task.getStatus()))
                throw new ServiceException("任务状态不正确");
            int progress = task.getProgress() == null ? 0 : task.getProgress();
            if (progress < 0 || progress > 100) throw new ServiceException("任务进度必须在0到100之间");
            current.setStatus(task.getStatus());
            current.setProgress("DONE".equals(task.getStatus()) ? 100 : progress);
            current.setUpdateBy(userName);
            if (mapper.updateTask(current) != 1) throw changed();
            addEvent(project.getProjectId(), "TASK_PROGRESS", project.getStatus(), project.getStatus(), userId, userName,
                current.getTaskName() + " / " + current.getProgress() + "%");
            return current;
        }
        if (StringUtils.isBlank(task.getTaskName())) throw new ServiceException("任务名称不能为空");
        if (task.getAssigneeUserId() != null)
        {
            String role = mapper.selectMemberRole(task.getProjectId(), task.getAssigneeUserId());
            if (role == null) throw new ServiceException("任务负责人必须是有效项目成员");
            task.setAssigneeName(displayName(requireActiveUser(task.getAssigneeUserId())));
        }
        if (task.getProgress() == null) task.setProgress(0);
        if (task.getProgress() < 0 || task.getProgress() > 100) throw new ServiceException("任务进度必须在0到100之间");
        if (StringUtils.isBlank(task.getStatus())) task.setStatus("TODO");
        if (!TASK_STATUSES.contains(task.getStatus())) throw new ServiceException("任务状态不正确");
        if ("DONE".equals(task.getStatus())) task.setProgress(100);
        if (StringUtils.isBlank(task.getPriority())) task.setPriority("MEDIUM");
        if (!PRIORITIES.contains(task.getPriority())) throw new ServiceException("任务优先级不正确");
        if (task.getParentTaskId() != null && task.getParentTaskId().equals(task.getTaskId()))
            throw new ServiceException("任务不能成为自己的父任务");
        if (task.getTaskId() == null)
        {
            task.setCreateBy(userName); mapper.insertTask(task);
        }
        else
        {
            if (task.getVersion() == null) throw new ServiceException("缺少任务版本，请刷新后重试");
            task.setUpdateBy(userName);
            if (mapper.updateTask(task) != 1) throw changed();
        }
        addEvent(project.getProjectId(), "TASK_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            task.getTaskName());
        return task;
    }

    @Override
    @Transactional
    public void deleteTask(Long projectId, Long taskId, Long userId, boolean boss)
    {
        BusinessProject project = requireProject(projectId); requireManage(project, userId, boss); ensureMutable(project);
        if (mapper.countTaskChildren(projectId, taskId) > 0) throw new ServiceException("请先处理子任务");
        if (mapper.deleteTask(projectId, taskId) != 1) throw new ServiceException("任务不存在");
    }

    @Override
    @Transactional
    public BusinessProjectRoutine saveRoutine(BusinessProjectRoutine routine, Long userId, String userName, boolean boss)
    {
        if (routine == null || routine.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = requireProject(routine.getProjectId());
        requireManage(project, userId, boss); ensureMutable(project);
        if (StringUtils.isBlank(routine.getRoutineName())) throw new ServiceException("持续工作名称不能为空");
        if (StringUtils.isBlank(routine.getFrequency())) routine.setFrequency("DAILY");
        if (!ROUTINE_FREQUENCIES.contains(routine.getFrequency())) throw new ServiceException("执行频率不正确");
        if (routine.getTargetValue() == null || routine.getTargetValue().compareTo(BigDecimal.ZERO) <= 0)
            throw new ServiceException("周期目标必须大于0");
        if (StringUtils.isBlank(routine.getUnit())) throw new ServiceException("请填写成果单位");
        if (routine.getAssigneeUserId() == null
            || mapper.selectMemberRole(project.getProjectId(), routine.getAssigneeUserId()) == null)
            throw new ServiceException("持续工作负责人必须是有效项目成员");
        routine.setAssigneeName(displayName(requireActiveUser(routine.getAssigneeUserId())));
        if (routine.getStartDate() == null) routine.setStartDate(project.getPlanStartDate());
        if (routine.getStartDate() == null) throw new ServiceException("请选择开始日期");
        if (routine.getEndDate() != null && routine.getEndDate().before(routine.getStartDate()))
            throw new ServiceException("结束日期不能早于开始日期");
        if (!"1".equals(routine.getEvidenceRequired())) routine.setEvidenceRequired("0");
        if (routine.getRoutineId() == null)
        {
            routine.setStatus("ACTIVE"); routine.setVersion(0); routine.setCreateBy(userName);
            mapper.insertRoutine(routine);
        }
        else
        {
            BusinessProjectRoutine current = mapper.selectRoutineById(routine.getRoutineId());
            if (current == null || !project.getProjectId().equals(current.getProjectId()))
                throw new ServiceException("持续工作计划不存在");
            if (routine.getVersion() == null) routine.setVersion(current.getVersion());
            routine.setUpdateBy(userName);
            if (mapper.updateRoutine(routine) != 1) throw changed();
        }
        addEvent(project.getProjectId(), "ROUTINE_SAVE", project.getStatus(), project.getStatus(), userId, userName,
            routine.getRoutineName() + " / " + routine.getFrequency() + " / " + routine.getTargetValue() + routine.getUnit());
        return mapper.selectRoutineById(routine.getRoutineId());
    }

    @Override
    @Transactional
    public void removeRoutine(Long projectId, Long routineId, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId); requireManage(project, userId, boss); ensureMutable(project);
        if (mapper.voidRoutine(projectId, routineId, userName) != 1) throw new ServiceException("持续工作计划不存在");
        addEvent(projectId, "ROUTINE_VOID", project.getStatus(), project.getStatus(), userId, userName,
            "停用持续工作 " + routineId);
    }

    @Override
    @Transactional
    public BusinessProjectRoutineReport submitRoutineReport(BusinessProjectRoutineReport report,
        Long userId, String userName, boolean viewAll)
    {
        if (report == null || report.getRoutineId() == null) throw new ServiceException("请选择持续工作");
        BusinessProjectRoutine routine = mapper.selectRoutineById(report.getRoutineId());
        if (routine == null || !"ACTIVE".equals(routine.getStatus())) throw new ServiceException("持续工作计划不存在或已停用");
        BusinessProject project = requireProject(routine.getProjectId());
        if (!userId.equals(routine.getAssigneeUserId()))
            throw new ServiceException("只能由实际执行人本人填报完成量");
        if (!"ACTIVE".equals(project.getStatus()) && !"ACCEPTANCE".equals(project.getStatus()))
            throw new ServiceException("项目进入执行中后才能填写完成情况");
        Date today = DateUtils.getNowDate();
        if (report.getBizDate() == null) report.setBizDate(today);
        if (!new SimpleDateFormat("yyyy-MM-dd").format(today)
            .equals(new SimpleDateFormat("yyyy-MM-dd").format(report.getBizDate())))
            throw new ServiceException("负责人工作台只能填报今日完成情况");
        Map<String, Object> leave = mapper.selectStaffLeave(userId, report.getBizDate());
        if (leave != null && "ACTIVE".equals(String.valueOf(leave.get("status"))))
            throw new ServiceException("今日已登记请假，无需填报完成量");
        if (report.getActualValue() == null || report.getActualValue().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("实际完成数量不能为空或为负数");
        if ("DAILY".equals(routine.getFrequency())
            && report.getActualValue().compareTo(routine.getTargetValue()) < 0
            && StringUtils.isBlank(report.getIssueReason()))
            throw new ServiceException("未达到周期目标时请填写原因");
        if ("1".equals(routine.getEvidenceRequired()) && StringUtils.isBlank(report.getEvidenceUrls()))
            throw new ServiceException("该工作要求上传成果凭证");
        report.setProjectId(project.getProjectId()); report.setTargetSnapshot(routine.getTargetValue());
        report.setUnit(routine.getUnit()); report.setSubmittedUserId(userId);
        report.setSubmittedUserName(displayName(requireActiveUser(userId))); report.setStatus("SUBMITTED");
        report.setCreateBy(userName); mapper.upsertRoutineReport(report);
        addEvent(project.getProjectId(), "ROUTINE_REPORT", project.getStatus(), project.getStatus(), userId, userName,
            routine.getRoutineName() + "：" + report.getActualValue() + routine.getUnit());
        return mapper.selectRoutineReport(routine.getRoutineId(), report.getBizDate());
    }

    @Override
    @Transactional
    public BusinessProjectRisk saveRisk(BusinessProjectRisk risk, Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(risk.getProjectId()); requireManage(project, userId, boss); ensureMutable(project);
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
        BusinessProject project = requireProject(projectId); requireManage(project, userId, boss); ensureMutable(project);
        if (mapper.deleteRisk(projectId, riskId) != 1) throw new ServiceException("风险记录不存在");
    }

    @Override
    public Map<String, Object> dashboard(Long userId, boolean viewAll, boolean boss)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> summary = mapper.selectDashboardSummary(userId, viewAll, boss);
        result.put("summary", summary == null ? new HashMap<String, Object>() : summary);
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("userId", userId); query.put("viewAll", viewAll); query.put("boss", boss);
        List<BusinessProject> projects = mapper.selectProjectList(query);
        result.put("projects", projects.size() > 10 ? new ArrayList<BusinessProject>(projects.subList(0, 10)) : projects);
        List<BusinessProject> decisions = new ArrayList<BusinessProject>();
        for (BusinessProject project : projects)
        {
            boolean needsDecision = "DRAFT".equals(project.getStatus())
                || ("PLANNING".equals(project.getStatus()) && "SUBMITTED".equals(project.getBaselineStatus()))
                || "PAUSED".equals(project.getStatus()) || "ACCEPTANCE".equals(project.getStatus());
            if (needsDecision && decisions.size() < 20) decisions.add(project);
        }
        result.put("decisions", decisions);
        result.put("tasks", mapper.selectMyDueTasks(userId, viewAll, boss));
        return result;
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
        accounting.put("dailySpend", accountingMapper.selectCurrentProjectDailySpend(selectedId,
            java.sql.Date.valueOf(today)));

        result.put("project", detail);
        result.put("operating", operating);
        result.put("openTasks", openTasks);
        result.put("todayRoutines", detail.getRoutines());
        result.put("accounting", accounting);
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
        result.put("tasks", tasks); result.put("routines", routines); result.put("efforts", efforts);
        return result;
    }

    @Override
    @Transactional
    public BusinessProjectEffort saveMyEffort(BusinessProjectEffort effort, Long userId, String userName)
    {
        if (effort == null || effort.getProjectId() == null || effort.getBizDate() == null)
            throw new ServiceException("请选择项目和投入日期");
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
        BusinessProject project = requireProject(projectId);
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
        BusinessProject project = requireProject(projectId);
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
        BusinessProject project = requireProject(projectId);
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
    @Transactional
    public Map<String, Object> markMemberLeave(Long projectId, Long memberUserId, Date leaveDate, String reason,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireManage(project, userId, boss);
        ensureMutable(project);
        Date day = normalizeToday(leaveDate);
        if (memberUserId == null || mapper.selectMemberRole(projectId, memberUserId) == null)
            throw new ServiceException("只能为本项目参项人员登记请假");
        if (StringUtils.isBlank(reason)) throw new ServiceException("请填写请假原因");
        Map<String, Object> employee = requireActiveUser(memberUserId);
        Map<String, Object> leave = new HashMap<String, Object>();
        leave.put("userId", memberUserId);
        leave.put("userName", displayName(employee));
        leave.put("leaveDate", day);
        leave.put("reason", reason.trim());
        leave.put("recordedProjectId", projectId);
        leave.put("recordedUserId", userId);
        leave.put("recordedUserName", displayName(requireActiveUser(userId)));
        leave.put("createBy", userName);
        mapper.upsertStaffLeave(leave);
        recalculateAllocatedProjects(memberUserId, day, userName);
        addEvent(projectId, "STAFF_LEAVE", project.getStatus(), project.getStatus(), userId, userName,
            displayName(employee) + " 今日请假：" + reason.trim());
        return mapper.selectStaffLeave(memberUserId, day);
    }

    @Override
    @Transactional
    public void cancelMemberLeave(Long projectId, Long memberUserId, Date leaveDate,
        Long userId, String userName, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireManage(project, userId, boss);
        ensureMutable(project);
        Date day = normalizeToday(leaveDate);
        if (memberUserId == null || mapper.selectMemberRole(projectId, memberUserId) == null)
            throw new ServiceException("只能取消本项目参项人员的请假");
        Map<String, Object> current = mapper.selectStaffLeave(memberUserId, day);
        if (current == null || !"ACTIVE".equals(String.valueOf(current.get("status"))))
            throw new ServiceException("当天没有有效的请假记录");
        if (mapper.cancelStaffLeave(memberUserId, day, userName) != 1)
            throw new ServiceException("请假记录已发生变化，请刷新后重试");
        recalculateAllocatedProjects(memberUserId, day, userName);
        addEvent(projectId, "STAFF_LEAVE_CANCELED", project.getStatus(), project.getStatus(), userId, userName,
            String.valueOf(current.get("userName")) + " 取消今日请假");
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
        if (boss)
        {
            if (userId.equals(project.getInitiatorUserId())) return;
            throw new ServiceException("无权查看其他老板立项的项目");
        }
        if (project.getMainOwnerUserId().equals(userId)) return;
        if (mapper.selectMemberRole(project.getProjectId(), userId) == null) throw new ServiceException("无权查看该项目");
    }

    private void requireManage(BusinessProject project, Long userId, boolean boss)
    {
        if (SecurityUtils.isAdmin(userId)) return;
        if (boss)
        {
            if (userId.equals(project.getInitiatorUserId())) return;
            throw new ServiceException("无权管理其他老板立项的项目");
        }
        String role = mapper.selectMemberRole(project.getProjectId(), userId);
        if (!"OWNER".equals(role) && !"DEPUTY".equals(role)) throw new ServiceException("无权管理该项目");
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
        if (!SecurityUtils.isAdmin(userId) && !userId.equals(project.getInitiatorUserId()))
            throw new ServiceException("无权操作其他老板立项的项目");
    }

    private void requireStatus(BusinessProject project, String expected)
    {
        if (!expected.equals(project.getStatus())) throw new ServiceException("当前项目状态不允许执行此操作");
    }

    private void ensureMutable(BusinessProject project)
    {
        if ("CLOSED".equals(project.getStatus()) || "CANCELED".equals(project.getStatus()))
            throw new ServiceException("已结项或已取消项目不能修改");
    }

    private void ensureReadyForAcceptance(Long projectId)
    {
        List<BusinessProjectTask> tasks = mapper.selectTasks(projectId);
        if (tasks == null || tasks.isEmpty()) throw new ServiceException("项目至少需要一项任务才能验收");
        for (BusinessProjectTask task : tasks)
        {
            if (!"DONE".equals(task.getStatus())) throw new ServiceException("仍有未完成任务，暂不能申请或通过验收");
        }
        List<BusinessProjectMilestone> milestones = mapper.selectMilestones(projectId);
        if (milestones != null)
        {
            for (BusinessProjectMilestone milestone : milestones)
            {
                if (!"DONE".equals(milestone.getStatus())) throw new ServiceException("仍有未完成里程碑，暂不能申请或通过验收");
            }
        }
        List<BusinessProjectRisk> risks = mapper.selectRisks(projectId);
        if (risks != null)
        {
            for (BusinessProjectRisk risk : risks)
            {
                if ("OPEN".equals(risk.getStatus()) && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())))
                    throw new ServiceException("仍有高风险或严重风险未处理，暂不能申请或通过验收");
            }
        }
    }

    private boolean sameLong(Object value, Long expected)
    {
        return value != null && expected != null && String.valueOf(value).equals(String.valueOf(expected));
    }

    private void validateProject(BusinessProject project)
    {
        if (project == null || StringUtils.isBlank(project.getProjectName())) throw new ServiceException("项目名称不能为空");
        if (project.getProjectName().length() > 160) throw new ServiceException("项目名称不能超过160个字符");
        if (project.getMainOwnerUserId() == null) throw new ServiceException("请选择项目主负责人");
        if (StringUtils.isBlank(project.getProjectType())) project.setProjectType("GENERAL");
        if (StringUtils.isNotBlank(project.getExecutionSource()) && !"LIVE".equals(project.getExecutionSource()))
            throw new ServiceException("项目执行系统类型不正确");
        if (project.getCompanyDeptId() != null && mapper.selectCompanyById(project.getCompanyDeptId()) == null)
            throw new ServiceException("项目归属公司不正确");
        if (StringUtils.isBlank(project.getAccountingMode())) project.setAccountingMode("PROFIT");
        if (!ACCOUNTING_MODES.contains(project.getAccountingMode())) throw new ServiceException("项目核算模式不正确");
        if (StringUtils.isBlank(project.getManagementMode())) project.setManagementMode("SIMPLE");
        if (!MANAGEMENT_MODES.contains(project.getManagementMode())) throw new ServiceException("项目管理模式不正确");
        if (StringUtils.isBlank(project.getPriority())) project.setPriority("MEDIUM");
        if (!PRIORITIES.contains(project.getPriority())) throw new ServiceException("项目优先级不正确");
        if (StringUtils.isBlank(project.getBaseCurrency())) project.setBaseCurrency("CNY");
        if (project.getBaseCurrency().length() != 3) throw new ServiceException("币种代码必须是3位");
        if (project.getBudgetLimit() != null && project.getBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算上限不能为负数");
        if (project.getPlanStartDate() != null && project.getPlanEndDate() != null
            && project.getPlanStartDate().after(project.getPlanEndDate()))
            throw new ServiceException("计划结束日期不能早于开始日期");
    }

    private void validateParent(Long parentId, Long currentProjectId, Long initiatorUserId)
    {
        if (parentId == null) return;
        if (parentId.equals(currentProjectId)) throw new ServiceException("项目不能成为自己的父项目");
        BusinessProject cursor = requireProject(parentId);
        if (!initiatorUserId.equals(cursor.getInitiatorUserId()))
            throw new ServiceException("上级项目必须由同一位老板立项");
        int depth = 1;
        while (cursor.getParentId() != null)
        {
            if (cursor.getParentId().equals(currentProjectId)) throw new ServiceException("父子项目关系不能形成循环");
            if (++depth > 5) throw new ServiceException("项目层级最多支持5层");
            cursor = requireProject(cursor.getParentId());
        }
    }

    private Map<String, Object> requireActiveUser(Long userId)
    {
        if (userId == null) throw new ServiceException("请选择有效账号");
        Map<String, Object> user = mapper.selectActiveUserById(userId);
        if (user == null) throw new ServiceException("所选账号不存在或已停用");
        return user;
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
    }

    private void addEvent(Long projectId, String eventType, String fromStatus, String toStatus,
        Long userId, String userName, String comment)
    {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("projectId", projectId); event.put("eventType", eventType);
        event.put("fromStatus", fromStatus); event.put("toStatus", toStatus);
        event.put("operatorUserId", userId); event.put("operatorName", userName);
        event.put("comment", comment);
        mapper.insertEvent(event);
    }

    private ServiceException changed()
    {
        return new ServiceException("数据已被其他人修改，请刷新后重试");
    }
}
