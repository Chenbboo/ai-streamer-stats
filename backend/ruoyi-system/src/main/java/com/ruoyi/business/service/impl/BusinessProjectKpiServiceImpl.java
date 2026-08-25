package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectBonusTier;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiPlanItem;
import com.ruoyi.business.domain.BusinessProjectKpiResult;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.IBusinessProjectKpiService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

@Service
public class BusinessProjectKpiServiceImpl implements IBusinessProjectKpiService
{
    private static final List<String> CYCLE_TYPES = Arrays.asList("MONTH", "QUARTER", "PROJECT");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_ITEM_SCORE = new BigDecimal("120");

    @Autowired private BusinessProjectKpiMapper mapper;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private IBusinessAccountingService accountingService;

    @Override
    public List<Map<String, Object>> overview(Long userId, boolean viewAll, boolean boss)
    {
        return mapper.selectProjectOverviews(userId, viewAll, boss, null);
    }

    @Override
    public List<Map<String, Object>> overview(List<Long> projectIds, Long userId, boolean viewAll, boolean boss)
    {
        if (projectIds == null || projectIds.isEmpty()) return Collections.<Map<String, Object>>emptyList();
        return mapper.selectProjectOverviews(userId, viewAll, boss,
            new ArrayList<Long>(new java.util.LinkedHashSet<Long>(projectIds)));
    }

    @Override
    public Map<String, Object> workspace(Long projectId, Long planId, Long userId, boolean viewAll, boolean boss)
    {
        BusinessProject project = requireProject(projectId);
        requireView(project, userId, viewAll, boss);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("project", project);
        List<BusinessProjectKpi> allTargets = safe(projectMapper.selectProjectKpis(projectId));
        List<BusinessProjectKpi> currentTargets = new ArrayList<BusinessProjectKpi>();
        for (BusinessProjectKpi target : allTargets)
            if ("CURRENT".equals(target.getStatus())) currentTargets.add(target);
        result.put("currentTargets", currentTargets);
        result.put("targetHistory", allTargets);
        result.put("plans", mapper.selectPlanSummaries(projectId));
        Long selectedPlanId = planId == null ? mapper.selectLatestPlanId(projectId) : planId;
        BusinessProjectKpiPlan selectedPlan = selectedPlanId == null ? null : requirePlan(selectedPlanId, projectId);
        if (selectedPlan != null) hydrate(selectedPlan);
        result.put("selectedPlan", selectedPlan);
        result.put("canManage", canManage(project, userId, viewAll, boss));
        result.put("canSettle", userId != null && userId.equals(project.getMainOwnerUserId()));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> publishPlan(BusinessProjectKpiPlan plan, Long userId, String userName,
        boolean viewAll, boolean boss)
    {
        if (plan == null || plan.getProjectId() == null) throw new ServiceException("请选择项目");
        BusinessProject project = requireProject(plan.getProjectId());
        requireBoss(project, userId, viewAll, boss);
        ensureProjectAllowsPlan(project);
        validatePlanPeriod(plan);
        if (mapper.countOverlappingPlans(plan.getProjectId(), plan.getCycleStart(), plan.getCycleEnd()) > 0)
            throw new ServiceException("该项目已有日期重叠的KPI方案");

        List<BusinessProjectKpi> targets = new ArrayList<BusinessProjectKpi>();
        for (BusinessProjectKpi target : safe(projectMapper.selectProjectKpis(plan.getProjectId())))
            if ("CURRENT".equals(target.getStatus())) targets.add(target);
        validateTargets(targets);
        List<BusinessProjectBonusTier> tiers = validateTiers(plan.getTiers());

        plan.setPlanId(null);
        plan.setPlanVersion(mapper.selectNextPlanVersion(plan.getProjectId()));
        plan.setBonusMode("LADDER");
        plan.setCurrency("CNY");
        plan.setStatus("PUBLISHED");
        plan.setPublishedUserId(userId);
        plan.setPublishedUserName(userName);
        plan.setCreateBy(userName);
        mapper.insertPlan(plan);

        int sort = 1;
        for (BusinessProjectKpi target : targets)
        {
            BusinessProjectKpiPlanItem item = snapshot(plan.getPlanId(), target, sort++);
            mapper.insertPlanItem(item);
        }
        sort = 1;
        for (BusinessProjectBonusTier tier : tiers)
        {
            tier.setTierId(null);
            tier.setPlanId(plan.getPlanId());
            tier.setSortOrder(sort++);
            mapper.insertBonusTier(tier);
        }

        BusinessProjectKpiSettlement settlement = new BusinessProjectKpiSettlement();
        settlement.setPlanId(plan.getPlanId());
        settlement.setProjectId(plan.getProjectId());
        settlement.setPeriodStart(plan.getCycleStart());
        settlement.setPeriodEnd(plan.getCycleEnd());
        settlement.setStatus("DRAFT");
        settlement.setCurrency("CNY");
        settlement.setCreateBy(userName);
        mapper.insertSettlement(settlement);
        addEvent(project, "KPI_PLAN_PUBLISHED", userId, userName,
            "发布项目KPI方案 v" + plan.getPlanVersion() + "，周期 " + date(plan.getCycleStart()) + " 至 " + date(plan.getCycleEnd()));
        return workspace(plan.getProjectId(), plan.getPlanId(), userId, viewAll, boss);
    }

    @Override
    @Transactional
    public void voidPlan(Long planId, Long userId, String userName, boolean viewAll, boolean boss)
    {
        BusinessProjectKpiPlan plan = mapper.selectPlanById(planId);
        if (plan == null) throw new ServiceException("KPI方案不存在");
        BusinessProject project = requireProject(plan.getProjectId());
        requireBoss(project, userId, viewAll, boss);
        BusinessProjectKpiSettlement settlement = mapper.selectSettlementByPlanId(planId);
        if (settlement == null) throw new ServiceException("KPI方案结算不存在，不能作废");
        if (!Arrays.asList("DRAFT", "RETURNED").contains(settlement.getStatus())
            || settlement.getAccountingFactId() != null)
            throw new ServiceException("仅可作废未提交、未入账的KPI方案");

        if (mapper.voidDraftSettlement(planId, userId, userName) != 1) throw changed();
        if (mapper.voidPublishedPlan(planId, userId, userName) != 1) throw changed();
        addEvent(project, "KPI_PLAN_VOIDED", userId, userName,
            "作废未提交的项目KPI方案 v" + plan.getPlanVersion() + "，周期 "
                + date(plan.getCycleStart()) + " 至 " + date(plan.getCycleEnd()));
    }

    @Override
    @Transactional
    public BusinessProjectKpiSettlement saveResults(Long settlementId, BusinessProjectKpiSettlement input,
        Long userId, String userName, boolean viewAll)
    {
        BusinessProjectKpiSettlement settlement = requireSettlement(settlementId);
        BusinessProject project = requireProject(settlement.getProjectId());
        requireOwner(project, userId, viewAll);
        ensureProjectAllowsSettlement(project);
        if (!Arrays.asList("DRAFT", "RETURNED").contains(settlement.getStatus()))
            throw new ServiceException("当前结算状态不能修改结果");
        if (input == null || input.getResults() == null || input.getResults().isEmpty())
            throw new ServiceException("请至少填写一项KPI结果");

        List<BusinessProjectKpiPlanItem> items = mapper.selectPlanItems(settlement.getPlanId());
        Map<Long, BusinessProjectKpiPlanItem> itemMap = itemMap(items);
        Set<Long> submittedItems = new HashSet<Long>();
        for (BusinessProjectKpiResult result : input.getResults())
        {
            if (result == null || result.getPlanItemId() == null || !itemMap.containsKey(result.getPlanItemId()))
                throw new ServiceException("KPI结果不属于当前方案");
            if (!submittedItems.add(result.getPlanItemId())) throw new ServiceException("同一KPI不能重复填报");
            validateResult(result);
            BusinessProjectKpiPlanItem item = itemMap.get(result.getPlanItemId());
            result.setSettlementId(settlementId);
            result.setCompletionRate(completionRate(item, result.getActualValue()));
            result.setWeightedScore(weightedScore(result.getCompletionRate(), item.getWeight()));
            result.setInputUserId(userId);
            result.setInputUserName(userName);
            mapper.upsertSettlementResult(result);
        }

        List<BusinessProjectKpiResult> stored = mapper.selectSettlementResults(settlementId);
        BigDecimal total = totalScore(items, stored);
        BigDecimal bonus = stored.size() == items.size() ? matchBonus(mapper.selectBonusTiers(settlement.getPlanId()), total) : null;
        if (mapper.updateSettlementPreview(settlementId, total, bonus, userName, settlement.getVersion()) != 1)
            throw changed();
        return detail(settlementId);
    }

    @Override
    @Transactional
    public BusinessProjectKpiSettlement submit(Long settlementId, Long userId, String userName, boolean viewAll)
    {
        BusinessProjectKpiSettlement settlement = requireSettlement(settlementId);
        BusinessProject project = requireProject(settlement.getProjectId());
        requireOwner(project, userId, viewAll);
        ensureProjectAllowsSettlement(project);
        if (!Arrays.asList("DRAFT", "RETURNED").contains(settlement.getStatus()))
            throw new ServiceException("当前结算状态不能提交");
        if (settlement.getPeriodEnd().after(today())) throw new ServiceException("考核周期尚未结束，不能提交结算");
        List<BusinessProjectKpiPlanItem> items = mapper.selectPlanItems(settlement.getPlanId());
        List<BusinessProjectKpiResult> results = mapper.selectSettlementResults(settlementId);
        requireComplete(items, results);
        BigDecimal total = totalScore(items, results);
        BigDecimal bonus = matchBonus(mapper.selectBonusTiers(settlement.getPlanId()), total);
        if (mapper.submitSettlement(settlementId, total, bonus, userId, userName, settlement.getVersion()) != 1)
            throw changed();
        addEvent(project, "KPI_SETTLEMENT_SUBMITTED", userId, userName,
            "提交KPI结算，综合得分 " + total.toPlainString() + "，预计项目奖金 ¥" + bonus.toPlainString());
        return detail(settlementId);
    }

    @Override
    @Transactional
    public BusinessProjectKpiSettlement review(Long settlementId, String decision, String comment,
        Long userId, String userName, boolean viewAll, boolean boss)
    {
        BusinessProjectKpiSettlement settlement = requireSettlement(settlementId);
        BusinessProject project = requireProject(settlement.getProjectId());
        requireBoss(project, userId, viewAll, boss);
        if (!"SUBMITTED".equals(settlement.getStatus())) throw new ServiceException("没有待确认的KPI结算");
        if (!"CONFIRMED".equals(decision) && !"RETURNED".equals(decision))
            throw new ServiceException("审核决定不正确");
        if ("RETURNED".equals(decision))
        {
            if (StringUtils.isBlank(comment)) throw new ServiceException("请填写退回原因");
            if (mapper.returnSettlement(settlementId, comment.trim(), userId, userName, settlement.getVersion()) != 1)
                throw changed();
            addEvent(project, "KPI_SETTLEMENT_RETURNED", userId, userName, "退回KPI结算：" + comment.trim());
            return detail(settlementId);
        }

        List<BusinessProjectKpiPlanItem> items = mapper.selectPlanItems(settlement.getPlanId());
        List<BusinessProjectKpiResult> results = mapper.selectSettlementResults(settlementId);
        requireComplete(items, results);
        BigDecimal total = totalScore(items, results);
        BigDecimal bonus = matchBonus(mapper.selectBonusTiers(settlement.getPlanId()), total);
        BusinessOperatingFact fact = accountingService.recordProjectBonus(project.getProjectId(), settlement.getPeriodEnd(),
            bonus, settlementId, userId, userName);
        Long factId = fact == null ? null : fact.getFactId();
        if (mapper.confirmSettlement(settlementId, total, bonus, factId,
            StringUtils.isBlank(comment) ? "确认项目KPI及奖金" : comment.trim(),
            userId, userName, settlement.getVersion()) != 1) throw changed();
        if (mapper.closePlan(settlement.getPlanId()) != 1) throw changed();
        addEvent(project, "KPI_SETTLEMENT_CONFIRMED", userId, userName,
            "确认KPI结算，综合得分 " + total.toPlainString() + "，项目奖金 ¥" + bonus.toPlainString());
        return detail(settlementId);
    }

    private void hydrate(BusinessProjectKpiPlan plan)
    {
        plan.setItems(mapper.selectPlanItems(plan.getPlanId()));
        plan.setTiers(mapper.selectBonusTiers(plan.getPlanId()));
        BusinessProjectKpiSettlement settlement = mapper.selectSettlementByPlanId(plan.getPlanId());
        if (settlement != null) settlement.setResults(mapper.selectSettlementResults(settlement.getSettlementId()));
        plan.setSettlement(settlement);
    }

    private BusinessProjectKpiSettlement detail(Long settlementId)
    {
        BusinessProjectKpiSettlement settlement = requireSettlement(settlementId);
        settlement.setResults(mapper.selectSettlementResults(settlementId));
        return settlement;
    }

    private BusinessProjectKpiPlanItem snapshot(Long planId, BusinessProjectKpi target, int sortOrder)
    {
        BusinessProjectKpiPlanItem item = new BusinessProjectKpiPlanItem();
        item.setPlanId(planId); item.setKpiId(target.getKpiId()); item.setKpiCode(target.getKpiCode());
        item.setKpiName(target.getKpiName()); item.setMetricType(target.getMetricType()); item.setUnit(target.getUnit());
        item.setTargetValue(target.getTargetValue()); item.setMinimumValue(target.getMinimumValue());
        item.setWarningValue(target.getWarningValue()); item.setChallengeValue(target.getChallengeValue());
        item.setWeight(target.getWeight()); item.setDirection(StringUtils.isBlank(target.getDirection()) ? "HIGHER_BETTER" : target.getDirection());
        item.setAggregateType(StringUtils.isBlank(target.getAggregateType()) ? "SUM" : target.getAggregateType());
        item.setSourceType(StringUtils.isBlank(target.getSourceType()) ? "MANUAL" : target.getSourceType());
        item.setSortOrder(sortOrder);
        return item;
    }

    private void validatePlanPeriod(BusinessProjectKpiPlan plan)
    {
        if (!CYCLE_TYPES.contains(plan.getCycleType())) throw new ServiceException("考核周期类型不正确");
        if (plan.getCycleStart() == null || plan.getCycleEnd() == null) throw new ServiceException("请选择考核起止日期");
        if (plan.getCycleEnd().before(plan.getCycleStart())) throw new ServiceException("考核结束日期不能早于开始日期");
    }

    private void validateTargets(List<BusinessProjectKpi> targets)
    {
        if (targets.isEmpty()) throw new ServiceException("请先设置至少一项项目KPI");
        BigDecimal weight = BigDecimal.ZERO;
        for (BusinessProjectKpi target : targets)
        {
            if (target.getTargetValue() == null || target.getTargetValue().compareTo(BigDecimal.ZERO) <= 0)
                throw new ServiceException("KPI“" + target.getKpiName() + "”目标值必须大于0");
            if (target.getWeight() == null || target.getWeight().compareTo(BigDecimal.ZERO) < 0)
                throw new ServiceException("KPI权重不能为负数");
            weight = weight.add(target.getWeight());
        }
        if (weight.compareTo(ONE_HUNDRED) != 0)
            throw new ServiceException("当前KPI权重合计必须等于100%，当前为" + weight.stripTrailingZeros().toPlainString() + "%");
    }

    private List<BusinessProjectBonusTier> validateTiers(List<BusinessProjectBonusTier> source)
    {
        if (source == null || source.isEmpty()) throw new ServiceException("请设置项目综合阶梯奖金");
        List<BusinessProjectBonusTier> tiers = new ArrayList<BusinessProjectBonusTier>(source);
        Collections.sort(tiers, new Comparator<BusinessProjectBonusTier>()
        {
            @Override public int compare(BusinessProjectBonusTier left, BusinessProjectBonusTier right)
            { return decimal(left.getMinScore()).compareTo(decimal(right.getMinScore())); }
        });
        BigDecimal expectedMin = BigDecimal.ZERO;
        for (int i = 0; i < tiers.size(); i++)
        {
            BusinessProjectBonusTier tier = tiers.get(i);
            if (StringUtils.isBlank(tier.getTierName())) throw new ServiceException("请填写奖金阶梯名称");
            if (tier.getMinScore() == null || tier.getMinScore().compareTo(BigDecimal.ZERO) < 0)
                throw new ServiceException("奖金阶梯最低分不能为负数");
            if (tier.getMinScore().compareTo(expectedMin) != 0)
                throw new ServiceException("奖金阶梯必须从0分开始并保持连续");
            if (tier.getBonusAmount() == null || tier.getBonusAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new ServiceException("项目奖金不能为负数");
            boolean last = i == tiers.size() - 1;
            if (!last && (tier.getMaxScore() == null || tier.getMaxScore().compareTo(tier.getMinScore()) <= 0))
                throw new ServiceException("非末级奖金阶梯必须设置有效最高分");
            if (last && tier.getMaxScore() != null) throw new ServiceException("最后一个奖金阶梯不应设置最高分");
            if (!last) expectedMin = tier.getMaxScore();
        }
        return tiers;
    }

    private void validateResult(BusinessProjectKpiResult result)
    {
        if (result.getActualValue() == null || result.getActualValue().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("KPI实际值不能为空或为负数");
        if (StringUtils.isBlank(result.getResultNote())) throw new ServiceException("手工填报KPI结果必须填写说明");
        result.setResultNote(result.getResultNote().trim());
        if (result.getResultNote().length() > 1000) throw new ServiceException("KPI结果说明不能超过1000字");
        if (result.getAttachmentUrls() != null && result.getAttachmentUrls().length() > 4000)
            throw new ServiceException("KPI结果凭证过多");
    }

    private BigDecimal completionRate(BusinessProjectKpiPlanItem item, BigDecimal actual)
    {
        BigDecimal rate;
        if ("LOWER_BETTER".equals(item.getDirection()))
            rate = actual.compareTo(BigDecimal.ZERO) == 0 ? MAX_ITEM_SCORE
                : item.getTargetValue().multiply(ONE_HUNDRED).divide(actual, 8, RoundingMode.HALF_UP);
        else
            rate = actual.multiply(ONE_HUNDRED).divide(item.getTargetValue(), 8, RoundingMode.HALF_UP);
        if (rate.compareTo(MAX_ITEM_SCORE) > 0) rate = MAX_ITEM_SCORE;
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedScore(BigDecimal rate, BigDecimal weight)
    { return rate.multiply(weight).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP); }

    private BigDecimal totalScore(List<BusinessProjectKpiPlanItem> items, List<BusinessProjectKpiResult> results)
    {
        Map<Long, BusinessProjectKpiPlanItem> itemsById = itemMap(items);
        BigDecimal total = BigDecimal.ZERO;
        for (BusinessProjectKpiResult result : results)
        {
            BusinessProjectKpiPlanItem item = itemsById.get(result.getPlanItemId());
            if (item == null) throw new ServiceException("KPI结果与方案快照不一致");
            BigDecimal rate = completionRate(item, result.getActualValue());
            total = total.add(weightedScore(rate, item.getWeight()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal matchBonus(List<BusinessProjectBonusTier> tiers, BigDecimal score)
    {
        for (BusinessProjectBonusTier tier : tiers)
            if (score.compareTo(tier.getMinScore()) >= 0
                && (tier.getMaxScore() == null || score.compareTo(tier.getMaxScore()) < 0))
                return tier.getBonusAmount().setScale(2, RoundingMode.HALF_UP);
        throw new ServiceException("综合得分没有匹配到奖金阶梯");
    }

    private void requireComplete(List<BusinessProjectKpiPlanItem> items, List<BusinessProjectKpiResult> results)
    {
        if (items == null || items.isEmpty() || results == null || results.size() != items.size())
            throw new ServiceException("请完整填写所有KPI结果后再提交");
        Set<Long> ids = new HashSet<Long>();
        for (BusinessProjectKpiResult result : results)
        {
            validateResult(result);
            ids.add(result.getPlanItemId());
        }
        for (BusinessProjectKpiPlanItem item : items)
            if (!ids.contains(item.getItemId())) throw new ServiceException("请完整填写所有KPI结果后再提交");
    }

    private Map<Long, BusinessProjectKpiPlanItem> itemMap(List<BusinessProjectKpiPlanItem> items)
    {
        Map<Long, BusinessProjectKpiPlanItem> result = new HashMap<Long, BusinessProjectKpiPlanItem>();
        for (BusinessProjectKpiPlanItem item : items) result.put(item.getItemId(), item);
        return result;
    }

    private BusinessProject requireProject(Long projectId)
    {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        BusinessProject project = projectMapper.selectProjectById(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        return project;
    }

    private BusinessProjectKpiPlan requirePlan(Long planId, Long projectId)
    {
        BusinessProjectKpiPlan plan = mapper.selectPlanById(planId);
        if (plan == null || !projectId.equals(plan.getProjectId())) throw new ServiceException("KPI方案不存在");
        return plan;
    }

    private BusinessProjectKpiSettlement requireSettlement(Long settlementId)
    {
        if (settlementId == null) throw new ServiceException("结算ID不能为空");
        BusinessProjectKpiSettlement settlement = mapper.selectSettlementById(settlementId);
        if (settlement == null) throw new ServiceException("KPI结算不存在");
        return settlement;
    }

    private void requireView(BusinessProject project, Long userId, boolean viewAll, boolean boss)
    {
        if (viewAll) return;
        if (boss && userId.equals(sponsor(project))) return;
        if (userId.equals(project.getMainOwnerUserId())) return;
        throw new ServiceException("无权查看该项目KPI奖金");
    }

    private boolean canManage(BusinessProject project, Long userId, boolean viewAll, boolean boss)
    { return viewAll || (boss && userId.equals(sponsor(project))); }

    private void requireBoss(BusinessProject project, Long userId, boolean viewAll, boolean boss)
    {
        if (!canManage(project, userId, viewAll, boss)) throw new ServiceException("只有项目归属老板可以执行此操作");
    }

    private void requireOwner(BusinessProject project, Long userId, boolean viewAll)
    {
        if (userId == null || !userId.equals(project.getMainOwnerUserId()))
            throw new ServiceException("只有项目主负责人可以填报和提交KPI结算");
    }

    private Long sponsor(BusinessProject project)
    { return project.getSponsorOwnerUserId() == null ? project.getInitiatorUserId() : project.getSponsorOwnerUserId(); }

    private void ensureProjectAllowsPlan(BusinessProject project)
    {
        if (!"ACTIVE".equals(project.getStatus())) throw new ServiceException("只有进行中的项目可以发布KPI方案");
    }

    private void ensureProjectAllowsSettlement(BusinessProject project)
    {
        if (!Arrays.asList("ACTIVE", "ACCEPTANCE").contains(project.getStatus()))
            throw new ServiceException("当前项目状态不能进行KPI结算");
    }

    private void addEvent(BusinessProject project, String type, Long userId, String userName, String comment)
    {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("projectId", project.getProjectId()); event.put("eventType", type);
        event.put("fromStatus", project.getStatus()); event.put("toStatus", project.getStatus());
        event.put("operatorUserId", userId); event.put("operatorName", userName); event.put("comment", comment);
        projectMapper.insertEvent(event);
    }

    private Date today()
    { return java.sql.Date.valueOf(new SimpleDateFormat("yyyy-MM-dd").format(new Date())); }

    private String date(Date value)
    { return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(value); }

    private BigDecimal decimal(BigDecimal value)
    { return value == null ? BigDecimal.ZERO : value; }

    private ServiceException changed()
    { return new ServiceException("数据已发生变化，请刷新后重试"); }

    private <T> List<T> safe(List<T> source)
    { return source == null ? Collections.<T>emptyList() : source; }
}
