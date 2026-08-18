package com.ruoyi.business.ai.capability.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectAcceptance;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.domain.BusinessProjectTask;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/** Deterministic acceptance facts shared by the read and decision capabilities. */
@Component
public class ProjectAcceptanceCapabilitySupport
{
    private final IBusinessProjectService service;

    @Autowired
    public ProjectAcceptanceCapabilitySupport(IBusinessProjectService service)
    {
        this.service = service;
    }

    public Map<String, Object> review(AiCapabilityInvocation invocation, Long projectId)
    {
        if (projectId == null) throw new ServiceException("请先确定要验收的项目");
        BusinessProject detail = service.getProject(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        if (!"ACCEPTANCE".equals(detail.getStatus())) throw new ServiceException("项目当前不在待验收状态");
        BusinessProjectAcceptance acceptance = latestPending(detail.getAcceptances());
        if (acceptance == null) throw new ServiceException("项目当前没有待审核的验收提交");

        int taskCount = size(detail.getTasks());
        int completedTaskCount = 0;
        if (detail.getTasks() != null) for (BusinessProjectTask task : detail.getTasks())
            if (task != null && "DONE".equals(task.getStatus())) completedTaskCount++;
        int milestoneCount = size(detail.getMilestones());
        int completedMilestoneCount = 0;
        if (detail.getMilestones() != null) for (BusinessProjectMilestone milestone : detail.getMilestones())
            if (milestone != null && "DONE".equals(milestone.getStatus())) completedMilestoneCount++;
        int openHighRiskCount = 0;
        if (detail.getRisks() != null) for (BusinessProjectRisk risk : detail.getRisks())
            if (risk != null && "OPEN".equals(risk.getStatus())
                && ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity()))) openHighRiskCount++;

        List<String> attachments = attachments(acceptance.getAttachmentUrls());
        boolean canApprove = taskCount > 0 && completedTaskCount == taskCount
            && completedMilestoneCount == milestoneCount && openHighRiskCount == 0;
        List<String> checks = new ArrayList<String>();
        checks.add("已提交第 " + acceptance.getSubmissionVersion() + " 版验收资料");
        checks.add("一次性任务已完成 " + completedTaskCount + "/" + taskCount + " 项");
        if (milestoneCount > 0) checks.add("里程碑已完成 " + completedMilestoneCount + "/" + milestoneCount + " 项");
        checks.add("交付凭证 " + attachments.size() + " 份");
        List<String> warnings = new ArrayList<String>();
        if (taskCount == 0) warnings.add("项目没有可核验的一次性任务，暂不满足通过条件");
        else if (completedTaskCount < taskCount) warnings.add("仍有 " + (taskCount - completedTaskCount) + " 项一次性任务未完成");
        if (completedMilestoneCount < milestoneCount)
            warnings.add("仍有 " + (milestoneCount - completedMilestoneCount) + " 个里程碑未完成");
        if (openHighRiskCount > 0) warnings.add("仍有 " + openHighRiskCount + " 项未关闭的高风险或严重风险");
        if (attachments.isEmpty()) warnings.add("负责人没有上传交付凭证，请先核对成果说明和交付物");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ready", true);
        result.put("project", project(detail));
        result.put("acceptance", acceptance(acceptance));
        result.put("taskCount", taskCount);
        result.put("completedTaskCount", completedTaskCount);
        result.put("milestoneCount", milestoneCount);
        result.put("completedMilestoneCount", completedMilestoneCount);
        result.put("openHighRiskCount", openHighRiskCount);
        result.put("attachmentCount", attachments.size());
        result.put("attachmentList", attachments);
        result.put("canApprove", canApprove);
        result.put("checks", checks);
        result.put("warnings", warnings);
        result.put("recommendation", canApprove
            ? "系统前置条件已通过，请老板核对成果内容与凭证后决定是否验收"
            : "当前不满足验收通过条件，可以退回负责人补充或完成剩余事项");
        return result;
    }

    public BusinessProject decide(AiCapabilityInvocation invocation, Long projectId, String decision, String comment)
    {
        Map<String, Object> review = review(invocation, projectId);
        if ("APPROVED".equals(decision) && !Boolean.TRUE.equals(review.get("canApprove")))
            throw new ServiceException("项目当前不满足验收通过条件");
        if ("RETURNED".equals(decision) && StringUtils.isBlank(comment)) throw new ServiceException("退回原因不能为空");
        return service.reviewAcceptance(projectId, decision, comment, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), true);
    }

    private Map<String, Object> project(BusinessProject value)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", value.getProjectId()); result.put("projectNo", value.getProjectNo());
        result.put("projectName", value.getProjectName()); result.put("companyName", value.getCompanyName());
        result.put("mainOwnerName", value.getMainOwnerName()); result.put("objective", value.getObjective());
        result.put("status", value.getStatus()); result.put("managementMode", value.getManagementMode());
        return result;
    }

    private Map<String, Object> acceptance(BusinessProjectAcceptance value)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("acceptanceId", value.getAcceptanceId()); result.put("submissionVersion", value.getSubmissionVersion());
        result.put("resultSummary", value.getResultSummary()); result.put("deliverables", value.getDeliverables());
        result.put("attachmentUrls", value.getAttachmentUrls()); result.put("submittedUserName", value.getSubmittedUserName());
        result.put("submittedTime", value.getSubmittedTime()); result.put("reviewStatus", value.getReviewStatus());
        return result;
    }

    private BusinessProjectAcceptance latestPending(List<BusinessProjectAcceptance> values)
    {
        BusinessProjectAcceptance latest = null;
        if (values == null) return null;
        for (BusinessProjectAcceptance item : values)
        {
            if (item == null || item.getAcceptanceId() == null || !"PENDING".equals(item.getReviewStatus())) continue;
            int version = item.getSubmissionVersion() == null ? 0 : item.getSubmissionVersion();
            int latestVersion = latest == null || latest.getSubmissionVersion() == null ? 0 : latest.getSubmissionVersion();
            if (latest == null || version > latestVersion
                || (version == latestVersion && item.getAcceptanceId() > latest.getAcceptanceId())) latest = item;
        }
        return latest;
    }

    private List<String> attachments(String value)
    {
        if (StringUtils.isBlank(value)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String item : value.split("[,;\\n\\r]+"))
            if (StringUtils.isNotBlank(StringUtils.trim(item))) result.add(StringUtils.trim(item));
        return result;
    }

    private int size(List<?> value) { return value == null ? 0 : value.size(); }
}
