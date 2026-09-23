package com.ruoyi.business.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectWorkReport;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkReportMapper;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;

@Service
public class BusinessProjectWorkReportService
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessProjectWorkReportMapper reportMapper;
    @Autowired private BusinessFileService fileService;

    @Transactional
    public BusinessProjectWorkReport submit(BusinessProjectWorkReport input, Long userId, String userName)
    {
        if (input == null || input.getProjectId() == null && input.getRoutineId() == null)
            throw new ServiceException("请选择汇报项目");
        BusinessProjectRoutine routine = null;
        if (input.getRoutineId() != null)
        {
            routine = projectMapper.selectRoutineById(input.getRoutineId());
            if (routine == null || !"ACTIVE".equals(routine.getStatus()))
                throw new ServiceException("持续工作不存在或已停用");
            if (input.getProjectId() != null && !input.getProjectId().equals(routine.getProjectId()))
                throw new ServiceException("持续工作与汇报项目不一致");
        }
        Long projectId = routine == null ? input.getProjectId() : routine.getProjectId();
        BusinessProject project = projectMapper.selectProjectById(projectId);
        if (project == null || !Arrays.asList("ACTIVE", "ACCEPTANCE").contains(project.getStatus()))
            throw new ServiceException("项目进入执行中后才能提交工作汇报");
        if (routine != null && !userId.equals(routine.getAssigneeUserId()))
            throw new ServiceException("只能由持续工作执行人本人提交工作汇报");
        String role = projectMapper.selectMemberRole(project.getProjectId(), userId);
        if (!userId.equals(project.getMainOwnerUserId()) && (role == null || "OBSERVER".equals(role)))
            throw new ServiceException("已不再是该项目的有效执行成员");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (routine != null && (routine.getStartDate() != null && today.isBefore(toBusinessDate(routine.getStartDate()))
            || routine.getEndDate() != null && today.isAfter(toBusinessDate(routine.getEndDate()))))
            throw new ServiceException("当前不在持续工作的执行区间内");
        String frequency = input.getFrequency() == null ? "" : input.getFrequency().trim().toUpperCase();
        if (!Arrays.asList("DAILY", "WEEKLY", "MONTHLY").contains(frequency))
            throw new ServiceException("请选择每日、每周或每月汇报");
        String content = StringUtils.trim(input.getContent());
        String attachments = StringUtils.trim(input.getAttachmentUrls());
        if (StringUtils.isBlank(content) && StringUtils.isBlank(attachments))
            throw new ServiceException("请填写汇报内容或上传附件");
        if (content != null && content.length() > 4000)
            throw new ServiceException("汇报内容不能超过4000个字符");
        if (attachments != null && attachments.length() > 4000)
            throw new ServiceException("附件地址过长");
        fileService.validateReferences(attachments, project.getProjectId(), userId, false, SecurityUtils.isAdmin(userId));

        LocalDate start = today;
        LocalDate end = today;
        if ("WEEKLY".equals(frequency))
        {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = start.plusDays(6);
        }
        else if ("MONTHLY".equals(frequency))
        {
            start = today.withDayOfMonth(1);
            end = today.withDayOfMonth(today.lengthOfMonth());
        }
        BusinessProjectWorkReport report = new BusinessProjectWorkReport();
        report.setRoutineId(routine == null ? null : routine.getRoutineId());
        report.setProjectId(project.getProjectId());
        report.setFrequency(frequency);
        report.setPeriodStart(java.sql.Date.valueOf(start));
        report.setPeriodEnd(java.sql.Date.valueOf(end));
        report.setContent(content);
        report.setAttachmentUrls(attachments);
        report.setSubmittedUserId(userId);
        report.setSubmittedUserName(routine == null || StringUtils.isBlank(routine.getAssigneeName())
            ? userName : routine.getAssigneeName());
        report.setStatus("PENDING");
        reportMapper.insert(report);
        return reportMapper.selectById(report.getReportId());
    }

    public List<BusinessProjectWorkReport> listForProject(Long projectId)
    {
        return reportMapper.selectByProject(projectId);
    }

    public List<BusinessProjectWorkReport> latestForSubmitter(Long userId)
    {
        return reportMapper.selectLatestBySubmitter(userId);
    }

    public List<Map<String, Object>> returnNotifications(Long userId)
    {
        return reportMapper.selectReturnNotifications(userId);
    }

    public int readReturnNotification(Long notificationId, Long userId)
    {
        return reportMapper.readReturnNotification(notificationId, userId);
    }

    public void readAllReturnNotifications(Long userId)
    {
        reportMapper.readAllReturnNotifications(userId);
    }

    @Transactional
    public BusinessProjectWorkReport review(Long reportId, String decision, String comment,
        Long userId, String userName)
    {
        BusinessProjectWorkReport report = reportId == null ? null : reportMapper.selectById(reportId);
        if (report == null) throw new ServiceException("工作汇报不存在");
        BusinessProject project = projectMapper.selectProjectById(report.getProjectId());
        if (project == null) throw new ServiceException("项目不存在");
        if (!SecurityUtils.isAdmin(userId) && !userId.equals(project.getMainOwnerUserId()))
            throw new ServiceException("只有项目负责人可以验收工作汇报");
        if (!"PENDING".equals(report.getStatus()))
            throw new ServiceException("该工作汇报已处理，请刷新后查看");
        if (!"APPROVED".equals(decision) && !"RETURNED".equals(decision))
            throw new ServiceException("验收结果不正确");
        String note = StringUtils.trim(comment);
        if ("RETURNED".equals(decision) && StringUtils.isBlank(note))
            throw new ServiceException("退回时请填写原因");
        if (note != null && note.length() > 500)
            throw new ServiceException("验收意见不能超过500个字符");
        String reviewerName = userId.equals(project.getMainOwnerUserId())
            && StringUtils.isNotBlank(project.getMainOwnerName()) ? project.getMainOwnerName() : userName;
        if (reportMapper.review(reportId, decision, note, userId, reviewerName) != 1)
            throw new ServiceException("该工作汇报已被处理，请刷新后查看");
        if ("RETURNED".equals(decision))
            reportMapper.insertReturnNotification(reportId, report.getSubmittedUserId());
        return reportMapper.selectById(reportId);
    }

    private LocalDate toBusinessDate(java.util.Date date)
    {
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
    }
}
