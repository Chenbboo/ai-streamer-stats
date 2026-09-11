package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.mapper.BusinessProjectProposalMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessProjectProposalService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

@Service
public class BusinessProjectProposalServiceImpl implements IBusinessProjectProposalService
{
    private static final List<String> ACCOUNTING_MODES = Arrays.asList("PROFIT", "COST", "VALUE", "HYBRID");
    private static final List<String> MANAGEMENT_MODES = Arrays.asList("LIGHT", "STANDARD", "KEY_CONTROL");
    private static final List<String> CLOSE_METHODS = Arrays.asList("DIRECT", "RESULT_ACCEPTANCE", "STAGED_ACCEPTANCE");
    private static final List<String> PRIORITIES = Arrays.asList("LOW", "MEDIUM", "HIGH");
    private static final List<String> TARGET_TYPES = Arrays.asList("FINANCIAL", "QUANTITY", "SCHEDULE", "QUALITY",
        "EFFICIENCY", "GROWTH", "CUSTOMER", "COMPLIANCE", "OTHER");

    @Autowired private BusinessProjectProposalMapper mapper;
    @Autowired private IBusinessProjectService projectService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BusinessProjectWorkMapper workMapper;
    @Autowired private BusinessProjectBudgetService budgetService;

    @Override
    public List<BusinessProjectProposal> listOwn(Map<String, Object> query, Long userId, boolean viewAll)
    {
        Map<String, Object> scoped = copy(query);
        scoped.put("userId", userId);
        scoped.put("viewAll", viewAll);
        List<BusinessProjectProposal> rows = mapper.selectOwnList(scoped);
        for (BusinessProjectProposal row : rows) decorate(row, userId, false, viewAll);
        return rows;
    }

    @Override
    public List<BusinessProjectProposal> listForReview(Map<String, Object> query, Long userId, boolean boss)
    {
        if (!boss) throw new ServiceException("只有老板可以查看待审批立项申请");
        Map<String, Object> scoped = copy(query);
        scoped.put("userId", userId);
        List<BusinessProjectProposal> rows = mapper.selectReviewList(scoped);
        for (BusinessProjectProposal row : rows) decorate(row, userId, true, false);
        return rows;
    }

    @Override
    public List<BusinessProjectProposal> directory(Map<String, Object> query, boolean boss, boolean viewAll)
    {
        if (!boss && !viewAll) throw new ServiceException("只有老板可以查看公司立项目录");
        List<BusinessProjectProposal> rows = mapper.selectDirectory(copy(query));
        for (BusinessProjectProposal row : rows)
        {
            row.setCanOpen(Boolean.FALSE);
            row.setCanEdit(Boolean.FALSE);
            row.setCanReview(Boolean.FALSE);
        }
        return rows;
    }

    @Override
    public BusinessProjectProposal get(Long proposalId, Long userId, boolean boss, boolean viewAll)
    {
        BusinessProjectProposal proposal = require(proposalId);
        boolean applicant = userId.equals(proposal.getApplicantUserId());
        boolean reviewer = userId.equals(proposal.getSponsorOwnerUserId());
        if (!viewAll && !applicant && !reviewer)
        {
            throw new ServiceException("无权查看该立项申请");
        }
        hydratePlanLines(proposal);
        proposal.setEvents(mapper.selectEvents(proposalId));
        decorate(proposal, userId, boss, viewAll);
        if (!canReadRawRates(userId)) redactRawRates(proposal);
        return proposal;
    }

    private boolean canReadRawRates(Long userId)
    {
        if (SecurityUtils.isAdmin(userId)) return true;
        try { return SecurityUtils.hasPermi("business:staff:cost"); }
        catch (ServiceException ex) { return false; }
    }

    private void redactRawRates(BusinessProjectProposal proposal)
    {
        proposal.setStaffingLines(redactStaffing(proposal.getStaffingLines()));
        List<Map<String,Object>> events=new ArrayList<Map<String,Object>>();
        if(proposal.getEvents()!=null) for(Map<String,Object> original:proposal.getEvents())
        {
            Map<String,Object> event=new LinkedHashMap<String,Object>(original);
            // Event summaries remain readable; archived financial snapshots require the separate cost permission.
            event.remove("snapshotJson");event.remove("snapshot_json");events.add(event);
        }
        proposal.setEvents(events);
    }

    private List<Map<String,Object>> redactStaffing(List<Map<String,Object>> rows)
    {
        List<Map<String,Object>> safe=new ArrayList<Map<String,Object>>();
        if(rows!=null) for(Map<String,Object> original:rows)
        {
            Map<String,Object> row=new LinkedHashMap<String,Object>(original);
            for(String field:Arrays.asList("monthlyCostSnapshot","standardWorkDaysSnapshot","dailyCostSnapshot","costPolicyId","costPolicyVersion","costCurrency","estimatedCost"))row.remove(field);
            safe.add(row);
        }
        return safe;
    }

    @Override
    @Transactional
    public BusinessProjectProposal create(BusinessProjectProposal proposal, Long userId, String userName)
    {
        Map<String, Object> applicant = requireActiveUser(userId);
        proposal.setApplicantUserId(userId);
        proposal.setApplicantName(displayName(applicant));
        if (StringUtils.isBlank(proposal.getTemplateVersion())) proposal.setTemplateVersion("LIGHT_V1");
        if("LEGACY_V1".equals(proposal.getTemplateVersion()))throw new ServiceException("新立项必须选择已发布标准模板，不能创建旧策略项目");
        normalizeAndValidate(proposal);
        proposal.setProposalNo("LX" + DateUtils.dateTimeNow("yyyyMMddHHmmss")
            + IdUtils.fastSimpleUUID().substring(0, 4).toUpperCase());
        proposal.setCreateBy(userName);
        if (mapper.insertProposal(proposal) != 1) throw new ServiceException("创建立项申请失败");
        savePlanLines(proposal);
        BusinessProjectProposal stored = require(proposal.getProposalId());
        addEvent(stored, "CREATE", null, "DRAFT", userId, userName, "创建立项申请草稿");
        return get(stored.getProposalId(), userId, false, false);
    }

    @Override
    @Transactional
    public BusinessProjectProposal update(BusinessProjectProposal input, Long userId, String userName)
    {
        BusinessProjectProposal current = require(input == null ? null : input.getProposalId());
        requireApplicant(current, userId);
        requireEditable(current);
        if (!java.util.Objects.equals(input.getParentProjectId(), current.getParentProjectId()))
            throw new ServiceException("归属主项目不可修改");
        input.setApplicantUserId(userId);
        input.setApplicantName(current.getApplicantName());
        if (input.getVersion() == null || !input.getVersion().equals(current.getVersion())) throw changed();
        input.setVersion(current.getVersion());
        if (!isNewTemplate(current)) input.setTemplateVersion("LEGACY_V1");
        else input.setTemplateVersion(current.getTemplateVersion());
        normalizeAndValidate(input);
        input.setUpdateBy(userName);
        if (mapper.updateDraft(input) != 1) throw changed();
        savePlanLines(input);
        BusinessProjectProposal stored = require(input.getProposalId());
        addEvent(stored, "EDIT", current.getStatus(), stored.getStatus(), userId, userName, "修改立项申请草稿");
        return get(stored.getProposalId(), userId, false, false);
    }

    @Override
    @Transactional
    public void delete(Long proposalId, Long userId, String userName)
    {
        BusinessProjectProposal current = require(proposalId);
        requireApplicant(current, userId);
        if (!"DRAFT".equals(current.getStatus())) throw new ServiceException("只有草稿可以删除");
        addEvent(current, "DELETE", "DRAFT", "DELETED", userId, userName, "删除立项申请草稿");
        if (mapper.softDelete(proposalId, userId, current.getVersion(), userName) != 1) throw changed();
    }

    @Override
    @Transactional
    public BusinessProjectProposal submit(Long proposalId, Long userId, String userName)
    {
        BusinessProjectProposal current = require(proposalId);
        requireApplicant(current, userId);
        requireEditable(current);
        hydratePlanLines(current);
        normalizeAndValidate(current);
        validateBusinessPlanForLaunch(current);
        if (isNewTemplate(current)) updateGovernanceSnapshot(current, true);
        if (mapper.updateComputedPlan(current) != 1) throw changed();
        savePlanLines(current);
        String fromStatus = current.getStatus();
        BusinessProject project = projectService.createApprovedProject(current, userId, userName);
        if (mapper.activate(proposalId, userId, current.getVersion(), project.getProjectId(),
            current.getApplicantName(), userName) != 1) throw changed();
        BusinessProjectProposal stored = require(proposalId);
        addEvent(stored, isNewTemplate(current) ? "SELF_AUTHORIZED" : "OWNER_LAUNCH", fromStatus, "APPROVED", userId, userName,
            "负责人确认项目测算并自主启动项目");
        return get(proposalId, userId, false, false);
    }

    @Override
    @Transactional
    public BusinessProjectProposal withdraw(Long proposalId, String comment, Long userId, String userName)
    {
        BusinessProjectProposal current = require(proposalId);
        requireApplicant(current, userId);
        if (!"PENDING".equals(current.getStatus())) throw new ServiceException("只有待审批申请可以撤回");
        if (mapper.withdraw(proposalId, userId, current.getVersion(), trim(comment), userName) != 1) throw changed();
        BusinessProjectProposal stored = require(proposalId);
        addEvent(stored, "WITHDRAW", "PENDING", "WITHDRAWN", userId, userName,
            StringUtils.isBlank(comment) ? "申请人撤回" : comment);
        return get(proposalId, userId, false, false);
    }

    @Override
    @Transactional
    public BusinessProjectProposal review(Long proposalId, String decision, String comment,
        Long userId, String userName, boolean boss)
    {
        if (!boss) throw new ServiceException("只有老板可以审批立项申请");
        BusinessProjectProposal current = require(proposalId);
        if (!userId.equals(current.getSponsorOwnerUserId())) throw new ServiceException("只能审批分配给本人的立项申请");
        if (userId.equals(current.getApplicantUserId())) throw new ServiceException("不能审批自己提交的立项申请");
        if (!"PENDING".equals(current.getStatus())) throw new ServiceException("该申请已经处理，请刷新后重试");
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision)) throw new ServiceException("审批决定不正确");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
        if (StringUtils.isNotBlank(comment) && comment.length() > 2000) throw new ServiceException("审批意见不能超过2000个字符");
        hydratePlanLines(current);
        normalizeAndValidate(current);
        Map<String, Object> reviewer = requireActiveBoss(userId);
        Long projectId = null;
        if ("APPROVED".equals(decision))
        {
            if (isNewTemplate(current)) validateBusinessPlanForLaunch(current);
            BusinessProject project = projectService.createApprovedProject(current, userId, userName);
            projectId = project.getProjectId();
        }
        String reviewerName = displayName(reviewer);
        if (mapper.review(proposalId, userId, current.getVersion(), decision, userId, reviewerName,
            trim(comment), projectId, userName) != 1) throw changed();
        BusinessProjectProposal stored = require(proposalId);
        addEvent(stored, "APPROVED".equals(decision) ? "APPROVE" : "RETURN", "PENDING", decision,
            userId, userName, StringUtils.isBlank(comment) ? "批准立项" : comment);
        return get(proposalId, userId, true, false);
    }

    @Override
    public Map<String, Object> options(Long userId)
    {
        requireActiveUser(userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("bosses", mapper.selectBossOptions(null));
        result.put("companies", mapper.selectCompanyOptions());
        result.put("applicantUserId", userId);
        result.put("owners", projectService.userOptions(null));
        List<Map<String,Object>> templates=new ArrayList<Map<String,Object>>();
        for(String version:Arrays.asList("LIGHT_V1","CONTROLLED_V1","SERVICE_V1")) { Map<String,Object> template=workMapper.selectTemplate(version);if(template!=null)templates.add(template); }
        result.put("templates",templates);
        result.put("calendars",workMapper.selectCalendars());result.put("unitPolicies",workMapper.selectUnitPolicies());
        return result;
    }

    @Override
    public Map<String,Object> estimateBudget(BusinessProjectProposal proposal,Long userId)
    {
        requireActiveUser(userId);
        if(proposal==null)throw new ServiceException("请填写立项预算资料");
        if(proposal.getProposalId()!=null)
        {
            BusinessProjectProposal current=require(proposal.getProposalId());requireApplicant(current,userId);requireEditable(current);
            if (!java.util.Objects.equals(proposal.getParentProjectId(), current.getParentProjectId())) throw new ServiceException("归属主项目不可修改");
            proposal.setTemplateVersion(current.getTemplateVersion());
        }
        else proposal.setTemplateVersion("LIGHT_V1");
        proposal.setApplicantUserId(userId);
        bindSubprojectOwner(proposal, false);
        return budgetService.estimate(proposal);
    }

    private void bindSubprojectOwner(BusinessProjectProposal proposal, boolean required)
    {
        if (proposal.getParentProjectId() == null) {
            proposal.setAssignedOwnerUserId(null); proposal.setAssignedOwnerName(null); return;
        }
        Map<String, Object> parent = mapper.selectParentProject(proposal.getParentProjectId());
        if (parent == null) throw new ServiceException("主项目不存在或已经结束");
        if (parent.get("parentId") != null) throw new ServiceException("仅支持主项目与子项目两级结构");
        proposal.setSponsorOwnerUserId(Long.valueOf(String.valueOf(parent.get("sponsorOwnerUserId"))));
        projectService.validateSubprojectParent(proposal.getParentProjectId(), proposal.getSponsorOwnerUserId(), proposal.getApplicantUserId());
        if (proposal.getAssignedOwnerUserId() == null) {
            proposal.setAssignedOwnerName(null);
            if (required) throw new ServiceException("请选择子项目负责人");
        } else proposal.setAssignedOwnerName(displayName(requireActiveUser(proposal.getAssignedOwnerUserId())));
    }

    private void normalizeAndValidate(BusinessProjectProposal proposal)
    {
        if (proposal == null || StringUtils.isBlank(proposal.getProjectName())) throw new ServiceException("项目名称不能为空");
        if (isNewTemplate(proposal))
        {
            Map<String,Object> template=workMapper.selectTemplate(proposal.getTemplateVersion());
            if(template==null)throw new ServiceException("项目模板版本不存在或未启用");
            proposal.setTemplateSnapshotJson(text(template.get("snapshotJson")));
            // Templates remain internal compatibility metadata; the applicant chooses governance.
            if (StringUtils.isBlank(proposal.getManagementMode())) proposal.setManagementMode(text(template.get("managementMode")));
            if (StringUtils.isBlank(proposal.getCloseMethod())) proposal.setCloseMethod(text(template.get("closeMethod")));
            if(proposal.getBudgetLimit()==null)proposal.setNoBudget("1");
            if(StringUtils.isBlank(proposal.getAccountingMode()))proposal.setAccountingMode("COST");
        }
        proposal.setProjectName(proposal.getProjectName().trim());
        if (proposal.getProjectName().length() > 160) throw new ServiceException("项目名称不能超过160个字符");
        if (proposal.getApplicantUserId() == null) throw new ServiceException("申请人不能为空");
        requireActiveUser(proposal.getApplicantUserId());
        if (proposal.getCompanyDeptId() == null || mapper.selectCompany(proposal.getCompanyDeptId()) == null)
            throw new ServiceException("请选择有效归属公司");
        bindSubprojectOwner(proposal, true);
        if (proposal.getSponsorOwnerUserId() == null) throw new ServiceException("请选择项目观察老板");
        Map<String, Object> selectedBoss = requireActiveBoss(proposal.getSponsorOwnerUserId());
        proposal.setSponsorOwnerName(displayName(selectedBoss));
        if (StringUtils.isBlank(proposal.getObjective())) throw new ServiceException("请填写项目目标");
        if (proposal.getObjective().length() > 1000) throw new ServiceException("项目目标不能超过1000个字符");
        if (StringUtils.isBlank(proposal.getApplicationReason())) throw new ServiceException("请填写立项理由");
        if (proposal.getApplicationReason().length() > 2000) throw new ServiceException("立项理由不能超过2000个字符");
        if (proposal.getPlanStartDate() == null) throw new ServiceException("请选择计划开始日期");
        if (proposal.getPlanEndDate() != null && proposal.getPlanStartDate().after(proposal.getPlanEndDate()))
            throw new ServiceException("计划结束日期不能早于开始日期");
        if (StringUtils.isBlank(proposal.getProjectType())) proposal.setProjectType("GENERAL");
        if (StringUtils.isBlank(proposal.getAccountingMode())) proposal.setAccountingMode("PROFIT");
        if (!ACCOUNTING_MODES.contains(proposal.getAccountingMode())) throw new ServiceException("项目核算方式不正确");
        proposal.setManagementMode(normalizeCode(proposal.getManagementMode()));
        proposal.setCloseMethod(normalizeCode(proposal.getCloseMethod()));
        if ("SIMPLE".equals(proposal.getManagementMode())) proposal.setManagementMode("LIGHT");
        if ("DELIVERY".equals(proposal.getManagementMode()))
        {
            proposal.setManagementMode("STANDARD");
            if (StringUtils.isBlank(proposal.getCloseMethod())) proposal.setCloseMethod("RESULT_ACCEPTANCE");
        }
        if (StringUtils.isBlank(proposal.getManagementMode())) proposal.setManagementMode("STANDARD");
        if (!MANAGEMENT_MODES.contains(proposal.getManagementMode())) throw new ServiceException("项目管理模式不正确");
        if (StringUtils.isBlank(proposal.getCloseMethod())) proposal.setCloseMethod("DIRECT");
        if (!CLOSE_METHODS.contains(proposal.getCloseMethod())) throw new ServiceException("项目结项方式不正确");
        proposal.setGoalMode(code(proposal.getGoalMode(), "TOTAL"));
        if (!Arrays.asList("TOTAL", "NO_TOTAL").contains(proposal.getGoalMode()))
            throw new ServiceException("项目目标模式不正确");
        if (StringUtils.isNotBlank(proposal.getManagementReason()) && proposal.getManagementReason().length() > 1000)
            throw new ServiceException("管理模式说明不能超过1000个字符");
        if ("KEY_CONTROL".equals(proposal.getManagementMode()) && StringUtils.isBlank(proposal.getManagementReason()))
            throw new ServiceException("重点监管项目必须说明选择原因和主要监管事项");
        if (StringUtils.isNotBlank(proposal.getAcceptanceCriteria()) && proposal.getAcceptanceCriteria().length() > 2000)
            throw new ServiceException("验收标准不能超过2000个字符");
        if (StringUtils.isBlank(proposal.getPriority())) proposal.setPriority("MEDIUM");
        if (!PRIORITIES.contains(proposal.getPriority())) throw new ServiceException("项目优先级不正确");
        if (StringUtils.isBlank(proposal.getBaseCurrency())) proposal.setBaseCurrency("CNY");
        proposal.setBaseCurrency(proposal.getBaseCurrency().trim().toUpperCase());
        if (!proposal.getBaseCurrency().matches("^[A-Z]{3}$"))
            throw new ServiceException("币种必须是 ISO 4217 的3位大写英文代码");
        proposal.setForecastPeriod(proposal.getPlanEndDate()==null ? "MONTH" : "PROJECT");
        proposal.setForecastDays(proposal.getPlanEndDate()==null ? 30 : plannedDays(proposal.getPlanStartDate(),proposal.getPlanEndDate()).intValue());
        proposal.setBudgetMode(code(proposal.getBudgetMode(), isNewTemplate(proposal)||proposal.getBudget()!=null ? "TOTAL" : "1".equals(proposal.getNoBudget())?"NONE":"TOTAL"));
        proposal.setBudgetScope(code(proposal.getBudgetScope(),"FULL_COST"));
        if(!Arrays.asList("TOTAL","DAILY","NONE").contains(proposal.getBudgetMode())||!Arrays.asList("FULL_COST","CASH_EXPENSE").contains(proposal.getBudgetScope()))throw new ServiceException("预算控制方式或统计口径不正确");
        if("DAILY".equals(proposal.getBudgetMode())){
            if(proposal.getDailyBudgetLimit()==null||proposal.getDailyBudgetLimit().signum()<=0)throw new ServiceException("每日预算上限必须大于0");
            proposal.setDailyBudgetLimit(nonNegative(proposal.getDailyBudgetLimit(),"每日预算上限"));
        }else proposal.setDailyBudgetLimit(null);
        if(!"DAILY".equals(proposal.getBudgetMode()))proposal.setStartupBudgetLimit(null);
        if(proposal.getStartupBudgetLimit()!=null)proposal.setStartupBudgetLimit(nonNegative(proposal.getStartupBudgetLimit(),"启动预算上限"));
        if(proposal.getBudgetReason()!=null&&proposal.getBudgetReason().length()>500)throw new ServiceException("预算说明不能超过500个字符");
        proposal.setNoBudget("NONE".equals(proposal.getBudgetMode()) ? "1" : "0");
        if ("1".equals(proposal.getNoBudget())) proposal.setBudgetLimit(null);
        if (!isNewTemplate(proposal) && proposal.getBudget()==null && "TOTAL".equals(proposal.getBudgetMode()) && proposal.getBudgetLimit() == null)
            throw new ServiceException("请填写预算或明确选择不设置预算");
        if (proposal.getBudgetLimit() != null && proposal.getBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算不能为负数");
        if (StringUtils.isNotBlank(proposal.getExecutionSource()) && !"LIVE".equals(proposal.getExecutionSource()))
            throw new ServiceException("项目执行系统类型不正确");
        normalizeBusinessPlan(proposal);
        if(isNewTemplate(proposal)||proposal.getBudget()!=null)budgetService.apply(proposal);
        if (isNewTemplate(proposal)||proposal.getBudget()!=null) updateGovernanceSnapshot(proposal, false);
    }

    private void updateGovernanceSnapshot(BusinessProjectProposal proposal, boolean selfAuthorized)
    {
        try
        {
            Map<String, Object> snapshot = objectMapper.readValue(StringUtils.defaultIfBlank(proposal.getTemplateSnapshotJson(),"{}"),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            snapshot.put("managementMode", proposal.getManagementMode());
            snapshot.put("closeMethod", proposal.getCloseMethod());
            snapshot.put("goalMode", proposal.getGoalMode());
            snapshot.put("finiteReviewWindowRequired", false);
            if(proposal.getBudget()!=null)snapshot.put("budget",proposal.getBudget());
            if (selfAuthorized) snapshot.put("authorizationMode", "SELF_AUTHORIZED");
            proposal.setTemplateSnapshotJson(objectMapper.writeValueAsString(snapshot));
        }
        catch (Exception ex) { throw new ServiceException("项目规则快照无法保存"); }
    }

    private void normalizeBusinessPlan(BusinessProjectProposal proposal)
    {
        budgetService.ensureOwner(proposal);
        validatePlanDetails(proposal);
        List<Map<String, Object>> revenues = cleanLines(proposal.getRevenueLines(), "itemName");
        List<Map<String, Object>> expenses = cleanLines(proposal.getExpenseLines(), "itemName");
        List<Map<String, Object>> staffing = cleanStaffingLines(proposal.getStaffingLines());
        List<Map<String, Object>> targets = "TOTAL".equals(proposal.getGoalMode())
            ? cleanLines(proposal.getTargetLines(), "targetName")
            : new ArrayList<Map<String, Object>>();
        proposal.setRevenueLines(revenues); proposal.setExpenseLines(expenses);
        proposal.setStaffingLines(staffing); proposal.setTargetLines(targets);

        BigDecimal forecastDays = BigDecimal.valueOf(Math.max(1, proposal.getForecastDays()));
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal recurringRevenue = BigDecimal.ZERO;
        for (Map<String, Object> line : revenues)
        {
            String scenario = code(line.get("scenario"), "BASE");
            if (!Arrays.asList("CONSERVATIVE", "BASE", "OPTIMISTIC").contains(scenario))
                throw new ServiceException("收入预测场景不正确");
            line.put("scenario", scenario);
            BigDecimal amount = nonNegative(line.get("expectedAmount"), "预计收入");
            line.put("expectedAmount", amount);
            String occurrenceType = occurrenceType(line.get("occurrenceType"));
            line.put("occurrenceType", occurrenceType);
            if ("BASE".equals(scenario))
            {
                BigDecimal forecastAmount = forecastAmount(amount, occurrenceType, forecastDays);
                revenue = revenue.add(forecastAmount);
                if (!"ONE_TIME".equals(occurrenceType)) recurringRevenue = recurringRevenue.add(forecastAmount);
            }
        }
        BigDecimal external = BigDecimal.ZERO;
        BigDecimal recurringExternal = BigDecimal.ZERO;
        for (Map<String, Object> line : expenses)
        {
            BigDecimal amount = nonNegative(line.get("amount"), "计划支出");
            String occurrenceType = occurrenceType(line.get("occurrenceType"));
            BigDecimal forecastAmount = forecastAmount(amount, occurrenceType, forecastDays);
            line.put("amount", amount); line.put("occurrenceType", occurrenceType);
            external = external.add(forecastAmount);
            if (!"ONE_TIME".equals(occurrenceType)) recurringExternal = recurringExternal.add(forecastAmount);
            line.put("expenseType", "ONE_TIME".equals(occurrenceType) ? "ONE_TIME" : "RECURRING");
            line.put("hasQuotation", "1".equals(String.valueOf(line.get("hasQuotation"))) ? "1" : "0");
        }
        BigDecimal personnel = BigDecimal.ZERO;
        int headcount = 0;
        Set<Long> selectedUsers = new HashSet<Long>();
        BigDecimal plannedDays = plannedDays(proposal.getPlanStartDate(), proposal.getPlanEndDate());
        for (Map<String, Object> line : staffing)
        {
            Long selectedUserId = longValue(line.get("userId"));
            if(isNewTemplate(proposal))
            {
                if(selectedUserId==null||!selectedUsers.add(selectedUserId))throw new ServiceException("人员计划须选择不同的具体人员");
                Map<String,Object> staff=mapper.selectProposalStaff(selectedUserId,proposal.getPlanStartDate());
                if(staff==null||!proposal.getCompanyDeptId().equals(longValue(staff.get("companyDeptId"))))throw new ServiceException("人员不在当前公司有效任职范围");
                String participationMode=com.ruoyi.business.support.BusinessProposalParticipation.mode(line,proposal);
                if(!Arrays.asList("FOLLOW_PROJECT","CUSTOM","UNLIMITED").contains(participationMode))throw new ServiceException("人员参与方式不正确");
                Date from,to;
                if("FOLLOW_PROJECT".equals(participationMode)){from=proposal.getPlanStartDate();to=proposal.getPlanEndDate();}
                else {from=com.ruoyi.business.support.BusinessProposalParticipation.date(line.get("planStartDate"));to="UNLIMITED".equals(participationMode)?null:com.ruoyi.business.support.BusinessProposalParticipation.date(line.get("planEndDate"));}
                if("UNLIMITED".equals(participationMode)&&proposal.getPlanEndDate()!=null)throw new ServiceException("只有不限期项目的人员可以选择不限期参与");
                if(from==null||"CUSTOM".equals(participationMode)&&to==null||to!=null&&from.after(to)||from.before(proposal.getPlanStartDate())
                    ||proposal.getPlanEndDate()!=null&&to!=null&&to.after(proposal.getPlanEndDate()))
                    throw new ServiceException("请填写有效的人员参与方式和项目范围内的日期");
                if(line.get("calendarId")==null)throw new ServiceException("人员计划必须选择工作日历");
                line.put("userName",displayStaffName(staff));line.put("roleName",StringUtils.defaultIfEmpty(text(staff.get("positionName")),"项目成员"));line.put("headcount",1);
                line.put("participationMode",participationMode);line.put("planStartDate",from);line.put("planEndDate",to);
                line.put("inputUnit","PERCENTAGE");line.put("inputQuantity",BigDecimal.ZERO);line.put("unitPolicyId",1L);
                line.put("allocationPercent",null);line.put("estimatedCost",null);
                for(String sensitive:Arrays.asList("costPolicyId","costPolicyVersion","monthlyCostSnapshot","standardWorkDaysSnapshot","dailyCostSnapshot","costCurrency"))line.put(sensitive,null);
                headcount++;continue;
            }
            if (selectedUserId == null)
            {
                // 历史记录仍按原岗位汇总方式读取；新立项启动前必须改为选择具体人员。
                int count = integer(line.get("headcount"), 1);
                if (count < 1) throw new ServiceException("岗位人数必须大于0");
                BigDecimal cost = nonNegative(line.get("estimatedCost"), "人员成本");
                line.put("headcount", count); line.put("estimatedCost", cost);
                headcount += count; personnel = personnel.add(cost);
                continue;
            }
            if (!selectedUsers.add(selectedUserId)) throw new ServiceException("同一人员不能重复选择");
            Map<String, Object> staff = mapper.selectProposalStaff(selectedUserId, proposal.getPlanStartDate());
            if (staff == null || staff.get("userId") == null)
                throw new ServiceException("所选人员不存在、已停用或已经离职");
            Long staffCompanyId = longValue(staff.get("companyDeptId"));
            if (!proposal.getCompanyDeptId().equals(staffCompanyId))
                throw new ServiceException(displayStaffName(staff) + "不属于当前归属公司");
            String costMode = text(staff.get("costMode"));
            BigDecimal monthlyCost = "MONTHLY".equals(costMode) && staff.get("monthlyCost") != null
                ? nonNegative(staff.get("monthlyCost"), "人员月度成本") : null;
            BigDecimal standardWorkDays = monthlyCost != null && staff.get("standardWorkDays") != null
                ? nonNegative(staff.get("standardWorkDays"), "月度标准工作天数") : null;
            BigDecimal dailyCost = monthlyCost != null && staff.get("dailyCost") != null
                ? nonNegative(staff.get("dailyCost"), "日用人成本") : null;
            line.put("userId", selectedUserId);
            line.put("userName", displayStaffName(staff));
            line.put("roleName", StringUtils.isBlank(text(staff.get("positionName")))
                ? "项目成员" : text(staff.get("positionName")));
            line.put("headcount", 1);
            BigDecimal allocationPercent = line.get("allocationPercent") == null
                ? new BigDecimal("100") : nonNegative(line.get("allocationPercent"), "人员投入比例");
            if (allocationPercent.compareTo(BigDecimal.ZERO) <= 0 || allocationPercent.compareTo(new BigDecimal("100")) > 0)
                throw new ServiceException("人员投入比例必须大于0且不超过100%");
            line.put("allocationPercent", allocationPercent);
            line.put("personMonths", null);
            line.put("planStartDate", proposal.getPlanStartDate());
            line.put("planEndDate", proposal.getPlanEndDate());
            line.put("costPolicyId", monthlyCost == null ? null : staff.get("costPolicyId"));
            line.put("costPolicyVersion", monthlyCost == null ? null : staff.get("costPolicyVersion"));
            line.put("monthlyCostSnapshot", monthlyCost);
            line.put("standardWorkDaysSnapshot", standardWorkDays);
            line.put("dailyCostSnapshot", dailyCost);
            line.put("costCurrency", monthlyCost == null ? null : text(staff.get("costCurrency")));
            BigDecimal allocationRate = allocationPercent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            BigDecimal cost = proposal.getPlanEndDate() == null
                ? (monthlyCost == null ? BigDecimal.ZERO : monthlyCost.multiply(allocationRate).setScale(2, RoundingMode.HALF_UP))
                : (dailyCost == null ? BigDecimal.ZERO
                    : dailyCost.multiply(plannedDays).multiply(allocationRate).setScale(2, RoundingMode.HALF_UP));
            line.put("estimatedCost", cost);
            headcount++; personnel = personnel.add(cost);
        }
        for (Map<String, Object> line : targets)
        {
            String targetType = normalizeTargetType(line.get("targetType"));
            if (!TARGET_TYPES.contains(targetType)) throw new ServiceException("量化目标类型不正确");
            line.put("targetType", targetType);
            line.put("targetValue", nonNegative(line.get("targetValue"), "目标值"));
            String unit = text(line.get("unit"));
            if (unit.length() > 32) throw new ServiceException("量化目标单位不能超过32个字符");
            line.put("unit", unit);
        }
        BigDecimal bonus = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal contingency = BigDecimal.ZERO;
        BigDecimal total = external.add(personnel).add(bonus).add(tax).add(contingency);
        BigDecimal profit = revenue.subtract(total);
        BigDecimal recurringTotal = recurringExternal.add(personnel).add(bonus).add(tax).add(contingency);
        BigDecimal recurringProfit = recurringRevenue.subtract(recurringTotal);
        proposal.setEstimatedRevenue(revenue); proposal.setEstimatedExternalCost(external);
        proposal.setRecurringEstimatedRevenue(recurringRevenue);
        proposal.setRecurringEstimatedExternalCost(recurringExternal);
        proposal.setEstimatedPersonnelCost(personnel); proposal.setEstimatedBonusCost(bonus);
        proposal.setEstimatedTaxCost(tax); proposal.setContingencyCost(contingency);
        proposal.setEstimatedTotalCost(total); proposal.setExpectedProfit(profit);
        proposal.setRecurringEstimatedTotalCost(recurringTotal);
        proposal.setRecurringExpectedProfit(recurringProfit);
        proposal.setExpectedMargin(revenue.compareTo(BigDecimal.ZERO) == 0 ? null
            : profit.multiply(new BigDecimal("100")).divide(revenue, 4, RoundingMode.HALF_UP));
        proposal.setBreakEvenRevenue(total);
        if (proposal.getPeakCashNeed() == null) proposal.setPeakCashNeed(total);
        else proposal.setPeakCashNeed(nonNegative(proposal.getPeakCashNeed(), "最大资金占用"));
        proposal.setFundingPlan(null);
        proposal.setKeyAssumptions(null);
        proposal.setStopLossRule(null);
        proposal.setPlannedHeadcount(headcount);
        if(isNewTemplate(proposal)&&headcount>0)
        {
            proposal.setEstimatedPersonnelCost(null);proposal.setEstimatedTotalCost(null);proposal.setExpectedProfit(null);
            proposal.setExpectedMargin(null);proposal.setBreakEvenRevenue(null);
        }
    }

    private void validateBusinessPlanForLaunch(BusinessProjectProposal proposal)
    {
        if(proposal.getBudget()!=null&&!"READY".equals(proposal.getBudget().get("status")))throw new ServiceException("预算尚未计算完整，请处理预算提示后再启动项目："+proposal.getBudget().get("issues"));
        if(isNewTemplate(proposal))
        {
            if(proposal.getBudget()!=null&&!"READY".equals(proposal.getBudget().get("status")))
                throw new ServiceException("预算尚未计算完整，请处理预算提示后再启动项目："+proposal.getBudget().get("issues"));
            if(proposal.getBudget()==null&&proposal.getBudgetLimit()!=null&&proposal.getBudgetLimit().compareTo(proposal.getEstimatedExternalCost())<0)throw new ServiceException("预算上限低于已估算外部支出");
            return;
        }
        if (proposal.getStaffingLines() == null || proposal.getStaffingLines().isEmpty())
            throw new ServiceException("请至少填写一项人员投入计划");
        if ("TOTAL".equals(proposal.getGoalMode())
            && (proposal.getTargetLines() == null || proposal.getTargetLines().isEmpty()))
            throw new ServiceException("请至少填写一项可量化项目目标");
        if (Arrays.asList("PROFIT", "HYBRID").contains(proposal.getAccountingMode())
            && proposal.getEstimatedRevenue().compareTo(BigDecimal.ZERO) <= 0)
            throw new ServiceException("盈利型或混合型项目必须填写基准收入预测");
        for (Map<String, Object> line : proposal.getRevenueLines())
            if (StringUtils.isBlank(text(line.get("revenueType"))) || StringUtils.isBlank(text(line.get("itemName"))))
                throw new ServiceException("请完整填写每项收入的收入方式和项目名称");
        for (Map<String, Object> line : proposal.getExpenseLines())
            if (StringUtils.isBlank(text(line.get("expenseCategory"))) || StringUtils.isBlank(text(line.get("purpose"))))
                throw new ServiceException("请完整填写每笔支出的类别和具体用途");
        for (Map<String, Object> line : proposal.getTargetLines())
            if (StringUtils.isBlank(text(line.get("unit"))) || StringUtils.isBlank(text(line.get("acceptanceEvidence"))))
                throw new ServiceException("请为每项目标填写单位和验收依据");
        for (Map<String, Object> line : proposal.getStaffingLines())
        {
            if (longValue(line.get("userId")) == null)
                throw new ServiceException("人员投入必须直接选择具体人员");
            String personName = StringUtils.isBlank(text(line.get("userName"))) ? "所选人员" : text(line.get("userName"));
            if (line.get("costPolicyId") == null || line.get("monthlyCostSnapshot") == null)
                throw new ServiceException(personName + "尚未在人员管理设置计划开始日有效的月度成本");
            if (proposal.getPlanEndDate() != null && line.get("dailyCostSnapshot") == null)
                throw new ServiceException(personName + "缺少标准工作天数，无法折算日用人成本");
            if (!proposal.getBaseCurrency().equalsIgnoreCase(text(line.get("costCurrency"))))
                throw new ServiceException(personName + "的人员成本币种与项目币种不一致");
        }
        if ("TOTAL".equals(proposal.getBudgetMode())
            && proposal.getBudgetLimit().compareTo(proposal.getEstimatedTotalCost()) < 0)
            throw new ServiceException("项目总额预算上限不能低于当前测算周期的预计总成本");
        if ("DAILY".equals(proposal.getBudgetMode()))
        {
            BigDecimal controllable = proposal.getRecurringEstimatedExternalCost();
            if ("FULL_COST".equals(proposal.getBudgetScope()))
                controllable = controllable.add(proposal.getEstimatedPersonnelCost());
            BigDecimal expectedDaily = controllable.divide(BigDecimal.valueOf(proposal.getForecastDays()), 2, RoundingMode.HALF_UP);
            if (proposal.getDailyBudgetLimit().compareTo(expectedDaily) < 0)
                throw new ServiceException("每日预算上限不能低于预计日均可控成本 " + expectedDaily.toPlainString());
            BigDecimal startupCost = oneTimeExpense(proposal.getExpenseLines());
            if (startupCost.compareTo(BigDecimal.ZERO) > 0
                && (proposal.getStartupBudgetLimit() == null || proposal.getStartupBudgetLimit().compareTo(startupCost) < 0))
                throw new ServiceException("一次性启动预算不能低于一次性支出 " + startupCost.toPlainString());
        }
        if ("NONE".equals(proposal.getBudgetMode()) && StringUtils.isBlank(proposal.getBudgetReason()))
            throw new ServiceException("暂不设置预算时必须填写原因");
        if (StringUtils.isBlank(proposal.getRiskSummary())) throw new ServiceException("请填写项目主要风险");
    }

    private String normalizeTargetType(Object value)
    {
        String type = code(value, "QUANTITY");
        if ("RESULT".equals(type)) return "QUANTITY";
        if ("VALUE".equals(type)) return "OTHER";
        return type;
    }

    private void validatePlanDetails(BusinessProjectProposal proposal)
    {
        validateLines(proposal,proposal.getRevenueLines(),"收入测算",new String[]{"scenario","revenueType","itemName"},new int[]{16,32,160},"expectedDate");
        validateLines(proposal,proposal.getExpenseLines(),"支出计划",new String[]{"expenseCategory","itemName","purpose"},new int[]{32,160,500},"occurDate");
        if (!"NO_TOTAL".equals(proposal.getGoalMode()))
        {
            validateLines(proposal,proposal.getTargetLines(),"量化目标",new String[]{"targetType","targetName","unit","acceptanceEvidence"},new int[]{24,160,32,500},"dueDate");
            if(proposal.getTargetLines()!=null)for(Map<String,Object> line:proposal.getTargetLines())
                if(!TARGET_TYPES.contains(normalizeTargetType(line.get("targetType"))))throw new ServiceException("量化目标类型不正确");
        }
        if(proposal.getStaffingLines()!=null&&proposal.getStaffingLines().size()>100)throw new ServiceException("人员计划一次最多100行");
        if(proposal.getStaffingLines()!=null)for(Map<String,Object> line:proposal.getStaffingLines())
            if(line==null||isNewTemplate(proposal)&&line.get("userId")==null)throw new ServiceException("人员计划存在未选择人员的空行，请补全或删除");
    }

    private void validateLines(BusinessProjectProposal proposal,List<Map<String,Object>> lines,String label,String[] fields,int[] limits,String dateField)
    {
        if(lines==null)return;
        if(lines.size()>100)throw new ServiceException(label+"一次最多100行");
        for(int i=0;i<lines.size();i++)
        {
            Map<String,Object> line=lines.get(i);if(line==null)throw new ServiceException(label+"第"+(i+1)+"行不能为空");
            String amountField="expectedDate".equals(dateField)?"expectedAmount":"occurDate".equals(dateField)?"amount":"targetValue";
            Object rawAmount=line.get(amountField);
            try{java.math.BigDecimal amount=new java.math.BigDecimal(String.valueOf(rawAmount));
                int scale="targetValue".equals(amountField)?4:2;
                if(amount.signum()<0||amount.scale()>scale||amount.compareTo(new java.math.BigDecimal("99999999999999.99"))>0)throw new NumberFormatException();}
            catch(Exception ex){throw new ServiceException(label+"第"+(i+1)+"行请填写有效非负数值，金额最多两位小数、目标值最多四位小数");}
            for(int n=0;n<fields.length;n++)
            {
                String value=text(line.get(fields[n]));
                if(StringUtils.isBlank(value)||value.length()>limits[n])throw new ServiceException(label+"第"+(i+1)+"行必填内容缺失或超过允许长度，请补全后保存");
                line.put(fields[n],value.trim());
            }
            if(StringUtils.isNotBlank(text(line.get(dateField))))
            {
                String issue=com.ruoyi.business.support.BusinessProposalPlanDates.issue(line.get(dateField),proposal.getPlanStartDate(),proposal.getPlanEndDate(),label,i+1);
                if(issue!=null)throw new ServiceException(issue);
            }
        }
    }

    private void hydratePlanLines(BusinessProjectProposal proposal)
    {
        if(proposal.getBudget()==null)proposal.setBudget(com.ruoyi.business.support.BusinessBudgetSnapshot.read(proposal.getTemplateSnapshotJson()));
        normalizeStoredBudgetForMode(proposal);
        proposal.setRevenueLines(mapper.selectRevenueLines(proposal.getProposalId()));
        proposal.setExpenseLines(mapper.selectExpenseLines(proposal.getProposalId()));
        proposal.setStaffingLines(mapper.selectStaffingLines(proposal.getProposalId()));
        if(isNewTemplate(proposal)&&proposal.getStaffingLines()!=null){
            List<Map<String,Object>> staffing=new ArrayList<>();
            for(Map<String,Object> source:proposal.getStaffingLines()){
                if(source==null){staffing.add(null);continue;}
                Map<String,Object> line=new LinkedHashMap<>(source);
                line.put("participationMode",com.ruoyi.business.support.BusinessProposalParticipation.mode(line,proposal));staffing.add(line);
            }
            proposal.setStaffingLines(staffing);
        }
        proposal.setTargetLines(mapper.selectTargetLines(proposal.getProposalId()));
    }

    private void normalizeStoredBudgetForMode(BusinessProjectProposal proposal)
    {
        Map<String,Object> stored=proposal.getBudget();
        String mode=proposal.getBudgetMode()!=null?proposal.getBudgetMode():stored==null?null:text(stored.get("mode"));
        if("DAILY".equals(mode)||stored==null)return;
        proposal.setStartupBudgetLimit(null);
        Map<String,Object> budget=new LinkedHashMap<String,Object>(stored);
        budget.put("startupLimit",null);
        List<Object> issues=new ArrayList<Object>();
        Object rawIssues=budget.get("issues");
        if(rawIssues instanceof Collection<?>)for(Object issue:(Collection<?>)rawIssues)
        {
            String message=String.valueOf(issue);
            if(!message.startsWith("启动预算不能低于本期一次性支出")
                &&!message.startsWith("每日预算模式下请单独设置一次性启动预算"))issues.add(issue);
        }
        else if(rawIssues!=null)issues.add(rawIssues);
        budget.put("issues",issues);
        if(issues.isEmpty()&&"PENDING".equals(budget.get("status")))budget.put("status","READY");
        proposal.setBudget(budget);
    }

    private void savePlanLines(BusinessProjectProposal proposal)
    {
        Long proposalId = proposal.getProposalId();
        mapper.deleteRevenueLines(proposalId); mapper.deleteExpenseLines(proposalId);
        mapper.deleteStaffingLines(proposalId); mapper.deleteTargetLines(proposalId);
        insertLines(proposalId, proposal.getRevenueLines(), "REVENUE");
        insertLines(proposalId, proposal.getExpenseLines(), "EXPENSE");
        insertLines(proposalId, proposal.getStaffingLines(), "STAFFING");
        insertLines(proposalId, proposal.getTargetLines(), "TARGET");
    }

    private void insertLines(Long proposalId, List<Map<String, Object>> lines, String type)
    {
        int sort = 1;
        for (Map<String, Object> source : lines == null ? Collections.<Map<String, Object>>emptyList() : lines)
        {
            Map<String, Object> line = new HashMap<String, Object>(source);
            line.put("proposalId", proposalId); line.put("sortOrder", sort++);
            if ("REVENUE".equals(type)) mapper.insertRevenueLine(line);
            else if ("EXPENSE".equals(type)) mapper.insertExpenseLine(line);
            else if ("STAFFING".equals(type)) mapper.insertStaffingLine(line);
            else mapper.insertTargetLine(line);
        }
    }

    private List<Map<String, Object>> cleanLines(List<Map<String, Object>> source, String nameKey)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (source == null) return result;
        for (Map<String, Object> line : source)
            if (line != null && StringUtils.isNotBlank(text(line.get(nameKey)))) result.add(new HashMap<String, Object>(line));
        return result;
    }

    private List<Map<String, Object>> cleanStaffingLines(List<Map<String, Object>> source)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (source == null) return result;
        for (Map<String, Object> line : source)
            if (line != null && (longValue(line.get("userId")) != null
                || StringUtils.isNotBlank(text(line.get("roleName")))))
                result.add(new HashMap<String, Object>(line));
        return result;
    }

    private String occurrenceType(Object value)
    {
        String result = code(value, "ONE_TIME");
        if (!Arrays.asList("ONE_TIME", "DAILY", "WEEKLY", "MONTHLY").contains(result))
            throw new ServiceException("收支发生方式不正确");
        return result;
    }

    private BigDecimal forecastAmount(BigDecimal amount, String occurrenceType, BigDecimal days)
    {
        BigDecimal multiplier = BigDecimal.ONE;
        if ("DAILY".equals(occurrenceType)) multiplier = days;
        else if ("WEEKLY".equals(occurrenceType))
            multiplier = days.divide(new BigDecimal("7"), 6, RoundingMode.HALF_UP);
        else if ("MONTHLY".equals(occurrenceType))
            multiplier = days.divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP);
        return amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal oneTimeExpense(List<Map<String, Object>> lines)
    {
        BigDecimal result = BigDecimal.ZERO;
        if (lines == null) return result;
        for (Map<String, Object> line : lines)
            if ("ONE_TIME".equals(text(line.get("occurrenceType"))))
                result = result.add(nonNegative(line.get("amount"), "一次性支出"));
        return result;
    }

    private BigDecimal plannedDays(Date start, Date end)
    {
        if (start == null || end == null) return BigDecimal.ZERO;
        long duration = Math.max(0L, end.getTime() - start.getTime());
        return BigDecimal.valueOf(duration / 86400000L + 1L);
    }

    private Long longValue(Object value)
    {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) return null;
        return Long.valueOf(String.valueOf(value));
    }

    private String displayStaffName(Map<String, Object> staff)
    {
        Object nickName = staff.get("nickName");
        return nickName != null && StringUtils.isNotBlank(String.valueOf(nickName))
            ? String.valueOf(nickName) : String.valueOf(staff.get("accountName"));
    }

    @Override
    public List<Map<String, Object>> staffOptions(Long companyDeptId, String effectiveDate, Long userId)
    {
        requireActiveUser(userId);
        if (companyDeptId == null || mapper.selectCompany(companyDeptId) == null)
            throw new ServiceException("请选择有效归属公司");
        Date date = StringUtils.isBlank(effectiveDate) ? new Date() : DateUtils.parseDate(effectiveDate);
        if (date == null) throw new ServiceException("计划开始日期格式不正确");
        List<Map<String,Object>> rows=mapper.selectStaffOptions(companyDeptId,date);
        // This endpoint requires proposal access. Planners need the selected company's
        // effective monthly/daily rates to estimate costs, without staff-cost edit permission.
        for(Map<String,Object> row:rows)row.put("rawCostVisible",true);
        return rows;
    }

    private boolean isNewTemplate(BusinessProjectProposal proposal)
    { return proposal!=null&&proposal.getTemplateVersion()!=null&&!"LEGACY_V1".equals(proposal.getTemplateVersion()); }

    private BigDecimal nonNegative(Object value, String label)
    {
        BigDecimal result = value == null || StringUtils.isBlank(String.valueOf(value))
            ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
        if (result.compareTo(BigDecimal.ZERO) < 0) throw new ServiceException(label + "不能为负数");
        return result;
    }

    private int integer(Object value, int defaultValue)
    {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) return defaultValue;
        return Integer.parseInt(String.valueOf(value));
    }

    private String code(Object value, String defaultValue)
    { String result = text(value); return StringUtils.isBlank(result) ? defaultValue : result.trim().toUpperCase(Locale.ROOT); }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }

    private BusinessProjectProposal require(Long proposalId)
    {
        if (proposalId == null) throw new ServiceException("立项申请ID不能为空");
        BusinessProjectProposal proposal = mapper.selectById(proposalId);
        if (proposal == null) throw new ServiceException("立项申请不存在");
        return proposal;
    }

    private Map<String, Object> requireActiveUser(Long userId)
    {
        Map<String, Object> user = userId == null ? null : mapper.selectActiveUser(userId);
        if (user == null || user.get("userId") == null)
            throw new ServiceException("申请账号不存在、已停用或已经离职");
        return user;
    }

    private Map<String, Object> requireActiveBoss(Long userId)
    {
        Map<String, Object> user = userId == null ? null : mapper.selectActiveBoss(userId);
        if (user == null || user.get("userId") == null)
            throw new ServiceException("审批老板账号不存在、已停用或没有老板角色");
        return user;
    }

    private void requireApplicant(BusinessProjectProposal proposal, Long userId)
    {
        if (!userId.equals(proposal.getApplicantUserId())) throw new ServiceException("只能操作本人创建的立项申请");
    }

    private void requireEditable(BusinessProjectProposal proposal)
    {
        if (!Arrays.asList("DRAFT", "PENDING", "RETURNED", "WITHDRAWN").contains(proposal.getStatus()))
            throw new ServiceException("当前状态不能修改或提交");
    }

    private void decorate(BusinessProjectProposal proposal, Long userId, boolean boss, boolean viewAll)
    {
        proposal.setCanOpen(viewAll || userId.equals(proposal.getApplicantUserId())
            || userId.equals(proposal.getSponsorOwnerUserId()));
        proposal.setCanEdit(userId.equals(proposal.getApplicantUserId())
            && Arrays.asList("DRAFT", "PENDING", "RETURNED", "WITHDRAWN").contains(proposal.getStatus()));
        proposal.setCanReview(boss && userId.equals(proposal.getSponsorOwnerUserId())
            && "PENDING".equals(proposal.getStatus()));
    }

    private void addEvent(BusinessProjectProposal proposal, String eventType, String fromStatus, String toStatus,
        Long userId, String userName, String comment)
    {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("proposalId", proposal.getProposalId());
        event.put("submissionVersion", proposal.getSubmissionVersion() == null ? 0 : proposal.getSubmissionVersion());
        event.put("eventType", eventType);
        event.put("fromStatus", fromStatus);
        event.put("toStatus", toStatus);
        event.put("operatorUserId", userId);
        event.put("operatorName", userName);
        event.put("comment", trim(comment));
        try { event.put("snapshotJson", objectMapper.writeValueAsString(proposal)); }
        catch (Exception ignored) { event.put("snapshotJson", null); }
        mapper.insertEvent(event);
    }

    private Map<String, Object> copy(Map<String, Object> input)
    {
        return input == null ? new HashMap<String, Object>() : new HashMap<String, Object>(input);
    }

    private String displayName(Map<String, Object> user)
    {
        Object nickName = user.get("nickName");
        return nickName != null && StringUtils.isNotBlank(String.valueOf(nickName))
            ? String.valueOf(nickName) : String.valueOf(user.get("userName"));
    }

    private String normalizeCode(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private ServiceException changed() { return new ServiceException("数据已被其他人修改，请刷新后重试"); }
}
