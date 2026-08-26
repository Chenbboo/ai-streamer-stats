package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        normalizeAndValidate(current);
        if (mapper.submit(proposalId, userId, current.getVersion(), userName) != 1) throw changed();
        BusinessProjectProposal stored = require(proposalId);
        addEvent(stored, current.getSubmissionVersion() == null || current.getSubmissionVersion() == 0
            ? "SUBMIT" : "RESUBMIT", current.getStatus(), "PENDING", userId, userName, "提交老板审批");
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
        result.put("bosses", mapper.selectBossOptions(userId));
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
        if (proposal.getSponsorOwnerUserId() == null) throw new ServiceException("请选择审批老板");
        Map<String, Object> selectedBoss = requireActiveBoss(proposal.getSponsorOwnerUserId());
        proposal.setSponsorOwnerName(displayName(selectedBoss));
        if (proposal.getApplicantUserId().equals(proposal.getSponsorOwnerUserId()))
            throw new ServiceException("申请人不能选择本人作为审批老板");
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
        proposal.setBaseCurrency(proposal.getBaseCurrency().toUpperCase());
        if (proposal.getBaseCurrency().length() != 3) throw new ServiceException("币种代码必须是3位");
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
    }

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
