package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.domain.BusinessIncentiveTier;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.service.IBusinessIncentiveService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/** Independent project reward approval. Allocation, payroll and payment are separate future capabilities. */
@Service
public class BusinessIncentiveServiceImpl implements IBusinessIncentiveService
{
    private static final String SCORE_TIERS = "SCORE_TIERS_V1";
    @Autowired private BusinessIncentiveMapper mapper;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessProjectKpiMapper kpiMapper;
    @Autowired private BusinessAccountingMapper accountingMapper;

    @Override
    public Map<String,Object> workspace(Long projectId, Long userId, boolean viewAll)
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        List<Map<String,Object>> projects = mapper.selectProjects(userId, viewAll);
        result.put("projects", projects);
        if (projectId == null && projects != null && !projects.isEmpty())
            projectId = number(projects.get(0).get("projectId"));
        if (projectId == null)
        {
            result.put("rules", Collections.emptyList()); result.put("awards", Collections.emptyList());
            result.put("legacyBonuses", Collections.emptyList()); result.put("confirmedKpis", Collections.emptyList());
            result.put("events", Collections.emptyList());
            result.put("kpiPlans", Collections.emptyList());
            return result;
        }
        BusinessProject project = project(projectId, false);
        requireView(project, userId, viewAll);
        result.put("project", project);
        result.put("rules", mapper.selectRules(projectId));
        List<BusinessIncentiveAward> awards = mapper.selectAwards(projectId);
        List<Map<String,Object>> events = mapper.selectEvents(projectId);
        if (awards != null) for (BusinessIncentiveAward award : awards)
        {
            actions(award, project, userId);
            List<Map<String,Object>> ownEvents = new ArrayList<Map<String,Object>>();
            if (events != null) for (Map<String,Object> item : events)
                if (Objects.equals(award.getAwardId(), number(item.get("awardId")))) ownEvents.add(item);
            award.setEvents(ownEvents);
        }
        result.put("awards", awards);
        result.put("legacyBonuses", mapper.selectLegacyBonuses(projectId));
        result.put("confirmedKpis", mapper.selectConfirmedKpis(projectId));
        List<Map<String,Object>> plans = new ArrayList<Map<String,Object>>();
        List<Map<String,Object>> summaries = kpiMapper.selectPlanSummaries(projectId);
        if (summaries != null) for (Map<String,Object> plan : summaries)
            if ("INDEPENDENT_V1".equals(plan.get("rewardPolicyVersion"))
                && Arrays.asList("PUBLISHED", "CLOSED").contains(plan.get("status"))) plans.add(plan);
        result.put("kpiPlans", plans);
        result.put("events", events);
        boolean open = !BusinessProjectLifecycle.isAccountingClosed(project);
        result.put("canManageRules", open && "ACTIVE".equals(project.getStatus()) && canManageRules(project, userId, viewAll));
        result.put("canRetireRules", open && allowedState(project) && canManageRules(project, userId, viewAll));
        result.put("canApply", open && allowedState(project) && owner(project, userId));
        result.put("canApprove", open && sponsor(project, userId));
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveRule publishRule(BusinessIncentiveRule input, Long userId, String userName, boolean viewAll)
    {
        if (input == null) throw new ServiceException("请填写奖金规则");
        BusinessProject project = project(input.getProjectId(), true);
        requireRuleManager(project, userId, viewAll);
        requireOpen(project);
        if (!"ACTIVE".equals(project.getStatus())) throw new ServiceException("项目执行中才能发布新的奖金规则");
        String name = required(input.getRuleName(), "规则名称", 100);
        String reason = required(input.getReason(), "规则依据", 500);
        boolean scoreBased = SCORE_TIERS.equals(input.getPolicyVersion());
        List<BusinessIncentiveTier> tiers = Collections.emptyList();
        if (scoreBased)
        {
            requireKpiPlan(project.getProjectId(), input.getKpiPlanId());
            tiers = validateTiers(input.getTiers());
        }
        BigDecimal amount = scoreBased ? tiers.stream().map(BusinessIncentiveTier::getAmount).max(BigDecimal::compareTo).get() : input.getAmount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
            || amount.compareTo(new BigDecimal("99999999999999.99")) > 0)
            throw new ServiceException("规则金额必须大于零，最多两位小数且不超过允许上限");
        if (!Objects.equals(project.getBaseCurrency(), input.getCurrency()))
            throw new ServiceException("奖金规则币种必须与项目本位币一致");
        if (!scoreBased && input.getMinScore() != null && (input.getMinScore().signum() < 0
            || input.getMinScore().compareTo(new BigDecimal("120")) > 0 || input.getMinScore().scale() > 2))
            throw new ServiceException("最低项目指标得分须在 0 至 120 之间，最多两位小数");
        BusinessIncentiveRule rule = new BusinessIncentiveRule();
        rule.setProjectId(project.getProjectId()); rule.setRuleVersion(mapper.nextRuleVersion(project.getProjectId()));
        rule.setRuleName(name); rule.setPolicyVersion(scoreBased ? SCORE_TIERS : "FIXED_V1"); rule.setAmount(amount.setScale(2));
        rule.setCurrency(project.getBaseCurrency()); rule.setMinScore(scoreBased ? BigDecimal.ZERO : input.getMinScore()); rule.setReason(reason);
        rule.setKpiPlanId(scoreBased ? input.getKpiPlanId() : null);
        rule.setStatus("ACTIVE"); rule.setCreatedUserId(userId); rule.setCreatedUserName(userName); rule.setCreateBy(userName);
        if (scoreBased) mapper.retirePlanRules(project.getProjectId(), rule.getKpiPlanId(), userName);
        mapper.insertRule(rule);
        for (BusinessIncentiveTier tier : tiers)
        {
            tier.setRuleId(rule.getRuleId());
            mapper.insertTier(tier);
        }
        event(project, null, "RULE_PUBLISHED", null, "ACTIVE", userId, userName, "发布规则 v" + rule.getRuleVersion() + "：" + name);
        return mapper.selectRule(rule.getRuleId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void retireRule(Long ruleId, String reason, Long userId, String userName, boolean viewAll)
    {
        BusinessIncentiveRule rule = rule(ruleId, null, false);
        BusinessProject project = project(rule.getProjectId(), true);
        requireRuleManager(project, userId, viewAll);
        requireOpen(project);
        reason = required(reason, "停用原因", 500);
        if ("RETIRED".equals(rule.getStatus())) return;
        if (mapper.retireRule(ruleId, userName) != 1) throw changed();
        event(project, null, "RULE_RETIRED", "ACTIVE", "RETIRED", userId, userName, "规则 " + ruleId + "：" + reason);
    }

    @Override
    public Map<String,Object> estimate(Long projectId, Long ruleId, Long settlementId, Long userId, boolean viewAll)
    {
        BusinessProject project = project(projectId, false);
        requireView(project, userId, viewAll);
        BusinessIncentiveRule rule = rule(ruleId, projectId, true);
        requireCurrency(project, rule.getCurrency());
        BusinessProjectKpiSettlement evidence = ruleEvidence(rule, settlementId);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("ruleId", rule.getRuleId()); result.put("ruleVersion", rule.getRuleVersion());
        result.put("policyVersion", rule.getPolicyVersion()); result.put("amount", rewardAmount(rule, evidence));
        result.put("kpiPlanId", rule.getKpiPlanId());
        if (SCORE_TIERS.equals(rule.getPolicyVersion())) result.put("matchedTier", matchedTier(rule, evidence.getTotalScore()));
        result.put("currency", rule.getCurrency()); result.put("settlementId", settlementId);
        result.put("scoreSnapshot", evidence == null ? null : evidence.getTotalScore());
        result.put("status", "ESTIMATE_ONLY"); result.put("paymentStatus", "NOT_RECORDED");
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveAward createAward(BusinessIncentiveAward input, Long userId, String userName)
    {
        if (input == null) throw new ServiceException("请填写奖励申请");
        BusinessProject project = project(input.getProjectId(), true);
        requireOwner(project, userId);
        String key = required(input.getRequestKey(), "请求标识", 64);
        if (!key.matches("[A-Za-z0-9_-]{8,64}")) throw new ServiceException("请求标识须为 8 至 64 位字母、数字、横线或下划线");
        String reason = required(input.getReason(), "奖励依据", 500);
        BusinessIncentiveAward existing = mapper.selectAwardByRequest(project.getProjectId(), key);
        if (existing != null)
        {
            if (!Objects.equals(existing.getApplicantUserId(), userId) || !Objects.equals(existing.getRuleId(), input.getRuleId())
                || !Objects.equals(existing.getSettlementId(), input.getSettlementId()) || !sameDay(existing.getBizDate(), input.getBizDate())
                || !Objects.equals(existing.getReason(), reason)) throw new ServiceException("请求标识已用于另一份奖励申请");
            return actions(existing, project, userId);
        }
        requireOpen(project);
        validateDate(project, input.getBizDate());
        BusinessIncentiveRule rule = rule(input.getRuleId(), project.getProjectId(), true);
        requireCurrency(project, rule.getCurrency());
        BusinessProjectKpiSettlement evidence = ruleEvidence(rule, input.getSettlementId());
        BigDecimal amount = rewardAmount(rule, evidence);
        if (amount.signum() <= 0) throw new ServiceException("当前 KPI 得分对应奖金为零，无需创建奖励申请");
        if (SCORE_TIERS.equals(rule.getPolicyVersion())
            && mapper.countExistingScoreAward(project.getProjectId(), input.getSettlementId()) > 0)
            throw new ServiceException("该 KPI 方案已有阶梯奖金申请，调整规则版本不能重复申请，请先办理原单");
        if (input.getSettlementId() != null && mapper.countExistingEvidenceAward(project.getProjectId(), rule.getRuleId(), input.getSettlementId()) > 0)
            throw new ServiceException("该项目指标已按本规则申请奖励，请办理原单，不能重复申请");
        BusinessIncentiveAward award = new BusinessIncentiveAward();
        award.setProjectId(project.getProjectId()); award.setCompanyDeptId(project.getCompanyDeptId());
        award.setRuleId(rule.getRuleId()); award.setRuleVersion(rule.getRuleVersion()); award.setRuleName(rule.getRuleName());
        award.setPolicyVersion(rule.getPolicyVersion()); award.setAmount(amount); award.setCurrency(rule.getCurrency());
        award.setSettlementId(input.getSettlementId()); award.setScoreSnapshot(evidence == null ? null : evidence.getTotalScore());
        award.setBizDate(input.getBizDate()); award.setReason(reason); award.setRequestKey(key); award.setStatus("DRAFT");
        award.setApplicantUserId(userId); award.setApplicantUserName(userName); award.setCreateBy(userName);
        mapper.insertAward(award);
        event(project, award.getAwardId(), "AWARD_CREATED", null, "DRAFT", userId, userName, reason);
        return actions(requireAward(award.getAwardId(), false), project, userId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveAward submit(Long awardId, Integer version, String reason, Long userId, String userName)
    {
        BusinessIncentiveAward award = requireAward(awardId, false);
        BusinessProject project = project(award.getProjectId(), true);
        award = requireAward(awardId, true);
        requireOwner(project, userId);
        if (!Objects.equals(award.getApplicantUserId(), userId)) throw new ServiceException("只有原申请人可以提交奖励申请");
        if ("SUBMITTED".equals(award.getStatus())) return actions(award, project, userId);
        requireVersion(award, version); requireOpen(project); validateDate(project, award.getBizDate());
        if (!Arrays.asList("DRAFT", "RETURNED").contains(award.getStatus())) throw new ServiceException("当前奖励状态不能提交");
        if ("RETURNED".equals(award.getStatus())) reason = required(reason, "重新提交说明", 500);
        else reason = StringUtils.isBlank(reason) ? "提交奖励核准" : required(reason, "提交说明", 500);
        transition(award, "SUBMITTED", null, project, userId, userName, reason);
        return actions(requireAward(awardId, false), project, userId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveAward review(Long awardId, Integer version, String decision, String reason, Long userId, String userName)
    {
        BusinessIncentiveAward award = requireAward(awardId, false);
        BusinessProject project = project(award.getProjectId(), true);
        award = requireAward(awardId, true);
        requireSponsor(project, userId);
        if (Objects.equals(award.getApplicantUserId(), userId)) throw new ServiceException("不能核准本人提交的奖励申请");
        if (!Arrays.asList("APPROVED", "RETURNED").contains(decision)) throw new ServiceException("请选择核准或退回");
        reason = required(reason, "核准或退回意见", 500);
        if (decision.equals(award.getStatus())) return actions(award, project, userId);
        requireVersion(award, version); requireOpen(project); validateDate(project, award.getBizDate());
        if (!"SUBMITTED".equals(award.getStatus())) throw new ServiceException("只有已提交的奖励申请可以核准或退回");
        Long factId = null;
        if ("APPROVED".equals(decision))
        {
            // Recheck immutable evidence under the same project lock as KPI confirmation and close.
            requireCurrency(project, award.getCurrency());
            BusinessProjectKpiSettlement evidence = evidence(project.getProjectId(), award.getSettlementId(), null);
            if (evidence != null && (award.getScoreSnapshot() == null || evidence.getTotalScore() == null
                || award.getScoreSnapshot().compareTo(evidence.getTotalScore()) != 0))
                throw new ServiceException("指标确认结果与奖励申请快照不同，请核对后重新申请");
            if (SCORE_TIERS.equals(award.getPolicyVersion()))
            {
                BusinessIncentiveRule source = rule(award.getRuleId(), project.getProjectId(), false);
                BusinessProjectKpiSettlement sourceEvidence = ruleEvidence(source, award.getSettlementId());
                if (!SCORE_TIERS.equals(source.getPolicyVersion()) || !Objects.equals(source.getRuleVersion(), award.getRuleVersion())
                    || award.getAmount() == null || rewardAmount(source, sourceEvidence).compareTo(award.getAmount()) != 0)
                    throw new ServiceException("奖金金额与已发布阶梯规则不一致，请核对原申请");
            }
            factId = createCostSource(award, project, userId, userName);
        }
        transition(award, decision, factId, project, userId, userName, reason);
        return actions(requireAward(awardId, false), project, userId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveAward cancel(Long awardId, Integer version, String reason, Long userId, String userName)
    {
        BusinessIncentiveAward award = requireAward(awardId, false);
        BusinessProject project = project(award.getProjectId(), true);
        award = requireAward(awardId, true);
        boolean authorized = "APPROVED".equals(award.getStatus()) ? sponsor(project, userId)
            : owner(project, userId) && Objects.equals(award.getApplicantUserId(), userId) || sponsor(project, userId);
        if (!authorized) throw new ServiceException("无权撤销该奖励申请");
        reason = required(reason, "撤销原因", 500);
        if ("CANCELED".equals(award.getStatus())) return actions(award, project, userId);
        requireVersion(award, version); requireOpen(project);
        if (mapper.countDistributionReservations(awardId) > 0)
            throw new ServiceException("奖金已有个人分配，须先撤销未核准分配；已核准分配不能直接撤销原奖金");
        if (award.getAccountingFactId() != null)
        {
            BusinessOperatingFact fact = sourceFact(award);
            if (!Arrays.asList("DRAFT", "RETURNED").contains(fact.getStatus()))
                throw new ServiceException("奖励成本已确认或已冲销，不能撤销原单；请在核算系统办理有依据的调整");
            if (mapper.voidSourceFact(fact.getFactId(), fact.getVersion(), userName) != 1) throw changed();
        }
        transition(award, "CANCELED", award.getAccountingFactId(), project, userId, userName, reason);
        return actions(requireAward(awardId, false), project, userId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessIncentiveAward resubmitCost(Long awardId, Integer version, String reason, Long userId, String userName)
    {
        BusinessIncentiveAward award = requireAward(awardId, false);
        BusinessProject project = project(award.getProjectId(), true);
        award = requireAward(awardId, true);
        requireSponsor(project, userId); requireVersion(award, version); requireOpen(project);
        reason = required(reason, "重新提交成本说明", 500);
        if (!"APPROVED".equals(award.getStatus())) throw new ServiceException("只有已核准奖励可以重新提交成本");
        BusinessOperatingFact fact = sourceFact(award);
        if (!"RETURNED".equals(fact.getStatus())) throw new ServiceException("奖励成本没有被退回");
        if (mapper.resubmitSourceFact(fact.getFactId(), fact.getVersion(), userName) != 1) throw changed();
        transition(award, "APPROVED", award.getAccountingFactId(), project, userId, userName, reason);
        return actions(requireAward(awardId, false), project, userId);
    }

    private Long createCostSource(BusinessIncentiveAward award, BusinessProject project, Long userId, String userName)
    {
        String sourceKey = "HR-INCENTIVE-AWARD-" + award.getAwardId();
        BusinessOperatingFact existing = accountingMapper.selectFactByIdempotencyKey(sourceKey);
        if (existing != null) throw new ServiceException("奖励成本来源已存在，请刷新核对核准状态");
        Map<String,Object> category = accountingMapper.selectCategoryByCode("PROJECT_BONUS_COST");
        if (category == null) throw new ServiceException("奖金成本类别尚未初始化");
        BusinessOperatingFact fact = new BusinessOperatingFact();
        fact.setProjectId(project.getProjectId()); fact.setCompanyDeptId(project.getCompanyDeptId()); fact.setBizDate(award.getBizDate());
        fact.setCategoryId(number(category.get("categoryId"))); fact.setCategoryCode("PROJECT_BONUS_COST");
        fact.setCategoryName(String.valueOf(category.get("categoryName"))); fact.setFactKind("COST");
        fact.setAmount(award.getAmount()); fact.setCurrency(award.getCurrency());
        fact.setDescription("独立奖励核准 #" + award.getAwardId() + "：" + award.getRuleName());
        fact.setSourceDomain("HR_INCENTIVE"); fact.setSourceType("BONUS"); fact.setSourceId(String.valueOf(award.getAwardId()));
        fact.setSourceLineKey("AWARD"); fact.setIdempotencyKey(sourceKey); fact.setStatus("DRAFT");
        fact.setCreateUserId(userId); fact.setCreateBy(userName); fact.setRemark("奖励已核准；成本待确认；未记录个人分配或支付");
        accountingMapper.insertFact(fact);
        return fact.getFactId();
    }

    private BusinessOperatingFact sourceFact(BusinessIncentiveAward award)
    {
        if (award.getAccountingFactId() == null) throw new ServiceException("奖励成本来源不存在");
        BusinessOperatingFact fact = accountingMapper.selectFactByIdForUpdate(award.getAccountingFactId());
        if (fact == null || !Objects.equals(award.getProjectId(), fact.getProjectId())
            || !"HR_INCENTIVE".equals(fact.getSourceDomain()) || !"BONUS".equals(fact.getSourceType())
            || !String.valueOf(award.getAwardId()).equals(fact.getSourceId()) || fact.getAmount() == null
            || award.getAmount().compareTo(fact.getAmount()) != 0 || !Objects.equals(award.getCurrency(), fact.getCurrency())
            || !sameDay(award.getBizDate(), fact.getBizDate())) throw new ServiceException("奖励与成本来源不一致");
        return fact;
    }

    private void requireKpiPlan(Long projectId, Long planId)
    {
        BusinessProjectKpiPlan plan = planId == null ? null : kpiMapper.selectPlanById(planId);
        if (plan == null || !projectId.equals(plan.getProjectId()) || !"INDEPENDENT_V1".equals(plan.getRewardPolicyVersion())
            || !Arrays.asList("PUBLISHED", "CLOSED").contains(plan.getStatus()))
            throw new ServiceException("请选择本项目已发布的独立 KPI 方案，历史奖金联动方案不能重复设置奖励");
    }

    private List<BusinessIncentiveTier> validateTiers(List<BusinessIncentiveTier> input)
    {
        if (input == null || input.isEmpty() || input.size() > 20) throw new ServiceException("请设置 1 至 20 档得分奖金");
        List<BusinessIncentiveTier> tiers = new ArrayList<BusinessIncentiveTier>();
        BigDecimal next = BigDecimal.ZERO;
        for (int i = 0; i < input.size(); i++)
        {
            BusinessIncentiveTier tier = input.get(i);
            if (tier == null || !validScore(tier.getMinScore()) || tier.getMinScore().compareTo(next) != 0)
                throw new ServiceException("奖金得分区间必须从 0 分开始，按顺序连续设置，不能重叠或留空");
            boolean last = i == input.size() - 1;
            if (last ? tier.getMaxScore() != null : !validScore(tier.getMaxScore()) || tier.getMaxScore().compareTo(tier.getMinScore()) <= 0)
                throw new ServiceException("得分上限必须大于下限，最后一档不设置上限");
            if (tier.getAmount() == null || tier.getAmount().signum() < 0 || tier.getAmount().scale() > 2
                || tier.getAmount().compareTo(new BigDecimal("99999999999999.99")) > 0)
                throw new ServiceException("每档奖金须为非负金额，最多两位小数且不超过允许上限");
            BusinessIncentiveTier saved = new BusinessIncentiveTier();
            saved.setSortOrder(i + 1); saved.setMinScore(tier.getMinScore()); saved.setMaxScore(tier.getMaxScore());
            saved.setAmount(tier.getAmount().setScale(2)); tiers.add(saved); next = tier.getMaxScore();
        }
        return tiers;
    }

    private boolean validScore(BigDecimal score)
    { return score != null && score.signum() >= 0 && score.compareTo(new BigDecimal("120")) <= 0 && score.scale() <= 2; }

    private BusinessProjectKpiSettlement ruleEvidence(BusinessIncentiveRule rule, Long settlementId)
    {
        boolean scoreBased = SCORE_TIERS.equals(rule.getPolicyVersion());
        BusinessProjectKpiSettlement result = evidence(rule.getProjectId(), settlementId, scoreBased ? BigDecimal.ZERO : rule.getMinScore());
        if (scoreBased)
        {
            requireKpiPlan(rule.getProjectId(), rule.getKpiPlanId());
            if (!Objects.equals(rule.getKpiPlanId(), result.getPlanId())) throw new ServiceException("请选择奖金规则关联的 KPI 方案结果");
        }
        return result;
    }

    private BusinessIncentiveTier matchedTier(BusinessIncentiveRule rule, BigDecimal score)
    {
        if (!validScore(score)) throw new ServiceException("已确认 KPI 得分无效，请核对指标结果");
        for (BusinessIncentiveTier tier : validateTiers(rule.getTiers()))
            if (score.compareTo(tier.getMinScore()) >= 0 && (tier.getMaxScore() == null || score.compareTo(tier.getMaxScore()) < 0)) return tier;
        throw new ServiceException("KPI 得分未匹配到奖金档位，请核对规则");
    }

    private BigDecimal rewardAmount(BusinessIncentiveRule rule, BusinessProjectKpiSettlement evidence)
    { return SCORE_TIERS.equals(rule.getPolicyVersion()) ? matchedTier(rule, evidence.getTotalScore()).getAmount() : rule.getAmount(); }

    private BusinessProjectKpiSettlement evidence(Long projectId, Long settlementId, BigDecimal minScore)
    {
        if (settlementId == null)
        {
            if (minScore != null) throw new ServiceException("该规则要求引用已确认的项目指标得分");
            return null;
        }
        BusinessProjectKpiSettlement settlement = kpiMapper.selectSettlementById(settlementId);
        if (settlement == null || !projectId.equals(settlement.getProjectId()) || !"CONFIRMED".equals(settlement.getStatus()))
            throw new ServiceException("奖励依据须为本项目已确认的项目指标");
        if (!"INDEPENDENT_V1".equals(settlement.getRewardPolicyVersion()))
            throw new ServiceException("历史联动 KPI 已按原规则办理奖金，不能作为独立奖励来源重复申请");
        if (minScore != null && (settlement.getTotalScore() == null || settlement.getTotalScore().compareTo(minScore) < 0))
            throw new ServiceException("项目指标得分未达到奖金规则门槛");
        return settlement;
    }

    private void transition(BusinessIncentiveAward award, String status, Long factId, BusinessProject project,
        Long userId, String userName, String reason)
    {
        if (mapper.transitionAward(award.getAwardId(), award.getStatus(), status, award.getVersion(), userId, userName, reason, factId) != 1)
            throw changed();
        event(project, award.getAwardId(), "AWARD_" + status, award.getStatus(), status, userId, userName, reason);
    }

    private void event(BusinessProject project, Long awardId, String type, String from, String to, Long userId, String userName, String reason)
    {
        Map<String,Object> event = new LinkedHashMap<String,Object>();
        event.put("projectId", project.getProjectId()); event.put("awardId", awardId); event.put("eventType", type);
        event.put("fromStatus", from); event.put("toStatus", to); event.put("operatorUserId", userId);
        event.put("operatorName", userName); event.put("reason", reason);
        mapper.insertEvent(event);
    }

    private BusinessIncentiveAward actions(BusinessIncentiveAward award, BusinessProject project, Long userId)
    {
        boolean open = !BusinessProjectLifecycle.isAccountingClosed(project);
        boolean own = owner(project, userId) && Objects.equals(userId, award.getApplicantUserId());
        boolean sponsor = sponsor(project, userId);
        boolean draft = Arrays.asList("DRAFT", "RETURNED").contains(award.getStatus());
        award.setCanSubmit(open && own && draft);
        award.setCanReview(open && sponsor && !Objects.equals(userId, award.getApplicantUserId()) && "SUBMITTED".equals(award.getStatus()));
        boolean costPending = award.getAccountingFactId() == null || Arrays.asList("DRAFT", "RETURNED").contains(award.getCostStatus());
        award.setCanCancel(open && !"CANCELED".equals(award.getStatus()) && costPending
            && ("APPROVED".equals(award.getStatus()) ? sponsor : own || sponsor));
        award.setCanResubmitCost(open && sponsor && "APPROVED".equals(award.getStatus()) && "RETURNED".equals(award.getCostStatus()));
        return award;
    }

    private BusinessProject project(Long projectId, boolean lock)
    {
        if (projectId == null) throw new ServiceException("请选择项目");
        BusinessProject project = lock ? projectMapper.selectProjectByIdForUpdate(projectId) : projectMapper.selectProjectById(projectId);
        if (project == null) throw new ServiceException("项目不存在");
        return project;
    }
    private BusinessIncentiveRule rule(Long ruleId, Long projectId, boolean active)
    {
        BusinessIncentiveRule rule = ruleId == null ? null : mapper.selectRule(ruleId);
        if (rule == null || projectId != null && !projectId.equals(rule.getProjectId())) throw new ServiceException("奖金规则不属于当前项目");
        if (!Arrays.asList("FIXED_V1", SCORE_TIERS).contains(rule.getPolicyVersion())) throw new ServiceException("奖金规则版本无法识别，不能按其他规则自动计算");
        if (active && !"ACTIVE".equals(rule.getStatus())) throw new ServiceException("奖金规则已停用");
        return rule;
    }
    private BusinessIncentiveAward requireAward(Long id, boolean lock)
    {
        BusinessIncentiveAward award = id == null ? null : lock ? mapper.selectAwardForUpdate(id) : mapper.selectAward(id);
        if (award == null) throw new ServiceException("奖励申请不存在");
        return award;
    }
    private void requireOpen(BusinessProject project)
    {
        BusinessProjectLifecycle.requireAccountingOpen(project);
        if (!allowedState(project)) throw new ServiceException("当前项目状态不能办理奖励");
        if (project.getCompanyDeptId() == null) throw new ServiceException("项目未设置归属公司");
    }
    private boolean allowedState(BusinessProject project)
    { return Arrays.asList("ACTIVE", "ACCEPTANCE").contains(project.getStatus()) || BusinessProjectLifecycle.isSeparated(project)
        && Arrays.asList("CLOSED", "CANCELED").contains(project.getStatus()); }
    private void validateDate(BusinessProject project, Date date)
    {
        if (date == null || day(date).compareTo(day(new Date())) > 0) throw new ServiceException("奖励业务日期不能为空或晚于今天");
        Date start = project.getActualStartDate() == null ? project.getPlanStartDate() : project.getActualStartDate();
        if (start != null && day(date).compareTo(day(start)) < 0) throw new ServiceException("奖励业务日期不能早于项目开始日期");
        if (Arrays.asList("CLOSED", "CANCELED").contains(project.getStatus()) && (project.getActualEndDate() == null
            || day(date).compareTo(day(project.getActualEndDate())) > 0)) throw new ServiceException("交付后奖励只能按项目执行期间的业务日期办理");
    }
    private void requireVersion(BusinessIncentiveAward award, Integer version)
    { if (version == null || !version.equals(award.getVersion())) throw changed(); }
    private void requireCurrency(BusinessProject project, String currency)
    { if (!Objects.equals(project.getBaseCurrency(), currency)) throw new ServiceException("奖励币种与项目本位币已不一致，请核对规则和项目配置"); }
    private boolean owner(BusinessProject project, Long userId)
    { return userId != null && userId.equals(project.getMainOwnerUserId()); }
    private boolean sponsor(BusinessProject project, Long userId)
    { return userId != null && userId.equals(project.getSponsorOwnerUserId() == null ? project.getInitiatorUserId() : project.getSponsorOwnerUserId()); }
    private void requireOwner(BusinessProject project, Long userId)
    { if (!owner(project, userId)) throw new ServiceException("只有项目主负责人可以申请奖励"); }
    private void requireSponsor(BusinessProject project, Long userId)
    { if (!sponsor(project, userId)) throw new ServiceException("只有项目归属老板可以核准奖励；管理员不能代替业务核准"); }
    private boolean canManageRules(BusinessProject project, Long userId, boolean viewAll)
    { return viewAll || owner(project, userId) || sponsor(project, userId); }
    private void requireRuleManager(BusinessProject project, Long userId, boolean viewAll)
    { if (!canManageRules(project, userId, viewAll)) throw new ServiceException("只有项目主负责人、归属老板或管理员可以设置奖金方案"); }
    private void requireView(BusinessProject project, Long userId, boolean viewAll)
    { if (!viewAll && !owner(project, userId) && !sponsor(project, userId)) throw new ServiceException("无权查看该项目的奖金激励"); }
    private String required(String value, String name, int max)
    { if (StringUtils.isBlank(value) || value.trim().length() > max) throw new ServiceException(name + "不能为空且不能超过 " + max + " 个字"); return value.trim(); }
    private Long number(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
    private String day(Date value) { return new SimpleDateFormat("yyyy-MM-dd").format(value); }
    private boolean sameDay(Date a, Date b) { return a != null && b != null && day(a).equals(day(b)); }
    private ServiceException changed() { return new ServiceException("奖励单据已变化，请刷新后重新确认"); }
}
