package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.ruoyi.business.service.IBusinessProjectProposalService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

@Service
public class BusinessProjectProposalServiceImpl implements IBusinessProjectProposalService
{
    private static final List<String> ACCOUNTING_MODES = Arrays.asList("PROFIT", "COST", "VALUE", "HYBRID");
    private static final List<String> MANAGEMENT_MODES = Arrays.asList("LIGHT", "STANDARD", "KEY_CONTROL");
    private static final List<String> CLOSE_METHODS = Arrays.asList("DIRECT", "RESULT_ACCEPTANCE", "STAGED_ACCEPTANCE");
    private static final List<String> PRIORITIES = Arrays.asList("LOW", "MEDIUM", "HIGH");

    @Autowired private BusinessProjectProposalMapper mapper;
    @Autowired private IBusinessProjectService projectService;
    @Autowired private ObjectMapper objectMapper;

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
        return proposal;
    }

    @Override
    @Transactional
    public BusinessProjectProposal create(BusinessProjectProposal proposal, Long userId, String userName)
    {
        Map<String, Object> applicant = requireActiveUser(userId);
        proposal.setApplicantUserId(userId);
        proposal.setApplicantName(displayName(applicant));
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
        input.setApplicantUserId(userId);
        input.setApplicantName(current.getApplicantName());
        input.setVersion(current.getVersion());
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
        if (mapper.updateComputedPlan(current) != 1) throw changed();
        savePlanLines(current);
        BusinessProject project = projectService.createApprovedProject(current, userId, userName);
        if (mapper.activate(proposalId, userId, current.getVersion(), project.getProjectId(),
            current.getApplicantName(), userName) != 1) throw changed();
        BusinessProjectProposal stored = require(proposalId);
        addEvent(stored, "OWNER_LAUNCH", current.getStatus(), "APPROVED", userId, userName,
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
        normalizeAndValidate(current);
        Map<String, Object> reviewer = requireActiveBoss(userId);
        Long projectId = null;
        if ("APPROVED".equals(decision))
        {
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
        return result;
    }

    private void normalizeAndValidate(BusinessProjectProposal proposal)
    {
        if (proposal == null || StringUtils.isBlank(proposal.getProjectName())) throw new ServiceException("项目名称不能为空");
        proposal.setProjectName(proposal.getProjectName().trim());
        if (proposal.getProjectName().length() > 160) throw new ServiceException("项目名称不能超过160个字符");
        if (proposal.getApplicantUserId() == null) throw new ServiceException("申请人不能为空");
        requireActiveUser(proposal.getApplicantUserId());
        if (proposal.getCompanyDeptId() == null || mapper.selectCompany(proposal.getCompanyDeptId()) == null)
            throw new ServiceException("请选择有效归属公司");
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
        if (StringUtils.isNotBlank(proposal.getManagementReason()) && proposal.getManagementReason().length() > 1000)
            throw new ServiceException("管理模式说明不能超过1000个字符");
        if ("KEY_CONTROL".equals(proposal.getManagementMode()) && StringUtils.isBlank(proposal.getManagementReason()))
            throw new ServiceException("重点监管项目必须说明选择原因和主要监管事项");
        if (StringUtils.isNotBlank(proposal.getAcceptanceCriteria()) && proposal.getAcceptanceCriteria().length() > 2000)
            throw new ServiceException("验收标准不能超过2000个字符");
        if (!"DIRECT".equals(proposal.getCloseMethod()) && StringUtils.isBlank(proposal.getAcceptanceCriteria()))
            throw new ServiceException("成果验收或阶段验收项目必须填写验收标准");
        if (StringUtils.isBlank(proposal.getPriority())) proposal.setPriority("MEDIUM");
        if (!PRIORITIES.contains(proposal.getPriority())) throw new ServiceException("项目优先级不正确");
        if (StringUtils.isBlank(proposal.getBaseCurrency())) proposal.setBaseCurrency("CNY");
        proposal.setBaseCurrency(proposal.getBaseCurrency().trim().toUpperCase());
        if (!proposal.getBaseCurrency().matches("^[A-Z]{3}$"))
            throw new ServiceException("币种必须是 ISO 4217 的3位大写英文代码");
        proposal.setNoBudget("1".equals(proposal.getNoBudget()) ? "1" : "0");
        if ("1".equals(proposal.getNoBudget())) proposal.setBudgetLimit(null);
        if (!"1".equals(proposal.getNoBudget()) && proposal.getBudgetLimit() == null)
            throw new ServiceException("请填写预算或明确选择不设置预算");
        if (proposal.getBudgetLimit() != null && proposal.getBudgetLimit().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("预算不能为负数");
        if (StringUtils.isNotBlank(proposal.getExecutionSource()) && !"LIVE".equals(proposal.getExecutionSource()))
            throw new ServiceException("项目执行系统类型不正确");
        if (proposal.getParentProjectId() != null)
        {
            Map<String, Object> parent = mapper.selectParentProject(proposal.getParentProjectId());
            if (parent == null) throw new ServiceException("上级项目不存在或已经结束");
            if (!String.valueOf(proposal.getSponsorOwnerUserId()).equals(String.valueOf(parent.get("sponsorOwnerUserId"))))
                throw new ServiceException("上级项目必须属于同一位归属老板");
        }
        normalizeBusinessPlan(proposal);
    }

    private void normalizeBusinessPlan(BusinessProjectProposal proposal)
    {
        List<Map<String, Object>> revenues = cleanLines(proposal.getRevenueLines(), "itemName");
        List<Map<String, Object>> expenses = cleanLines(proposal.getExpenseLines(), "itemName");
        List<Map<String, Object>> staffing = cleanStaffingLines(proposal.getStaffingLines());
        List<Map<String, Object>> targets = cleanLines(proposal.getTargetLines(), "targetName");
        proposal.setRevenueLines(revenues); proposal.setExpenseLines(expenses);
        proposal.setStaffingLines(staffing); proposal.setTargetLines(targets);

        BigDecimal revenue = BigDecimal.ZERO;
        for (Map<String, Object> line : revenues)
        {
            String scenario = code(line.get("scenario"), "BASE");
            if (!Arrays.asList("CONSERVATIVE", "BASE", "OPTIMISTIC").contains(scenario))
                throw new ServiceException("收入预测场景不正确");
            line.put("scenario", scenario);
            BigDecimal amount = nonNegative(line.get("expectedAmount"), "预计收入");
            line.put("expectedAmount", amount);
            if ("BASE".equals(scenario)) revenue = revenue.add(amount);
        }
        BigDecimal external = BigDecimal.ZERO;
        for (Map<String, Object> line : expenses)
        {
            BigDecimal amount = nonNegative(line.get("amount"), "计划支出");
            line.put("amount", amount); external = external.add(amount);
            line.put("expenseType", code(line.get("expenseType"), "ONE_TIME"));
            line.put("hasQuotation", "1".equals(String.valueOf(line.get("hasQuotation"))) ? "1" : "0");
        }
        BigDecimal personnel = BigDecimal.ZERO;
        int headcount = 0;
        Set<Long> selectedUsers = new HashSet<Long>();
        BigDecimal plannedDays = plannedDays(proposal.getPlanStartDate(), proposal.getPlanEndDate());
        for (Map<String, Object> line : staffing)
        {
            Long selectedUserId = longValue(line.get("userId"));
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
            line.put("allocationPercent", 100);
            line.put("personMonths", null);
            line.put("planStartDate", proposal.getPlanStartDate());
            line.put("planEndDate", proposal.getPlanEndDate());
            line.put("costPolicyId", monthlyCost == null ? null : staff.get("costPolicyId"));
            line.put("costPolicyVersion", monthlyCost == null ? null : staff.get("costPolicyVersion"));
            line.put("monthlyCostSnapshot", monthlyCost);
            line.put("standardWorkDaysSnapshot", standardWorkDays);
            line.put("dailyCostSnapshot", dailyCost);
            line.put("costCurrency", monthlyCost == null ? null : text(staff.get("costCurrency")));
            BigDecimal cost = proposal.getPlanEndDate() == null
                ? (monthlyCost == null ? BigDecimal.ZERO : monthlyCost.setScale(2, RoundingMode.HALF_UP))
                : (dailyCost == null ? BigDecimal.ZERO
                    : dailyCost.multiply(plannedDays).setScale(2, RoundingMode.HALF_UP));
            line.put("estimatedCost", cost);
            headcount++; personnel = personnel.add(cost);
        }
        for (Map<String, Object> line : targets)
        {
            line.put("targetType", code(line.get("targetType"), "RESULT"));
            line.put("targetValue", nonNegative(line.get("targetValue"), "目标值"));
        }
        BigDecimal bonus = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal contingency = BigDecimal.ZERO;
        BigDecimal total = external.add(personnel).add(bonus).add(tax).add(contingency);
        BigDecimal profit = revenue.subtract(total);
        proposal.setEstimatedRevenue(revenue); proposal.setEstimatedExternalCost(external);
        proposal.setEstimatedPersonnelCost(personnel); proposal.setEstimatedBonusCost(bonus);
        proposal.setEstimatedTaxCost(tax); proposal.setContingencyCost(contingency);
        proposal.setEstimatedTotalCost(total); proposal.setExpectedProfit(profit);
        proposal.setExpectedMargin(revenue.compareTo(BigDecimal.ZERO) == 0 ? null
            : profit.multiply(new BigDecimal("100")).divide(revenue, 4, RoundingMode.HALF_UP));
        proposal.setBreakEvenRevenue(total);
        if (proposal.getPeakCashNeed() == null) proposal.setPeakCashNeed(total);
        else proposal.setPeakCashNeed(nonNegative(proposal.getPeakCashNeed(), "最大资金占用"));
        proposal.setFundingPlan(null);
        proposal.setKeyAssumptions(null);
        proposal.setStopLossRule(null);
        proposal.setPlannedHeadcount(headcount);
    }

    private void validateBusinessPlanForLaunch(BusinessProjectProposal proposal)
    {
        if (proposal.getStaffingLines() == null || proposal.getStaffingLines().isEmpty())
            throw new ServiceException("请至少填写一项人员投入计划");
        if (proposal.getTargetLines() == null || proposal.getTargetLines().isEmpty())
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
        if ("1".equals(proposal.getNoBudget())) throw new ServiceException("启动项目前必须设置预算上限");
        if (proposal.getBudgetLimit() == null || proposal.getBudgetLimit().compareTo(proposal.getEstimatedTotalCost()) < 0)
            throw new ServiceException("预算上限不能低于预计总成本");
        if (StringUtils.isBlank(proposal.getRiskSummary())) throw new ServiceException("请填写项目主要风险");
    }

    private void hydratePlanLines(BusinessProjectProposal proposal)
    {
        proposal.setRevenueLines(mapper.selectRevenueLines(proposal.getProposalId()));
        proposal.setExpenseLines(mapper.selectExpenseLines(proposal.getProposalId()));
        proposal.setStaffingLines(mapper.selectStaffingLines(proposal.getProposalId()));
        proposal.setTargetLines(mapper.selectTargetLines(proposal.getProposalId()));
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
        if (companyDeptId != null && mapper.selectCompany(companyDeptId) == null)
            throw new ServiceException("请选择有效归属公司");
        Date date = StringUtils.isBlank(effectiveDate) ? new Date() : DateUtils.parseDate(effectiveDate);
        if (date == null) throw new ServiceException("计划开始日期格式不正确");
        return mapper.selectStaffOptions(companyDeptId, date);
    }

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
        if (!Arrays.asList("DRAFT", "RETURNED", "WITHDRAWN").contains(proposal.getStatus()))
            throw new ServiceException("当前状态不能修改或提交");
    }

    private void decorate(BusinessProjectProposal proposal, Long userId, boolean boss, boolean viewAll)
    {
        proposal.setCanOpen(viewAll || userId.equals(proposal.getApplicantUserId())
            || userId.equals(proposal.getSponsorOwnerUserId()));
        proposal.setCanEdit(userId.equals(proposal.getApplicantUserId())
            && Arrays.asList("DRAFT", "RETURNED", "WITHDRAWN").contains(proposal.getStatus()));
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
