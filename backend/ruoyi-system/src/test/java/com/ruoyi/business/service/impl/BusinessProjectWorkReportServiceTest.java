package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectRoutine;
import com.ruoyi.business.domain.BusinessProjectWorkReport;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkReportMapper;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;

class BusinessProjectWorkReportServiceTest
{
    private BusinessProjectWorkReportService service;
    private BusinessProjectMapper projects;
    private BusinessProjectWorkReportMapper reports;
    private BusinessFileService files;
    private BusinessProjectRoutine routine;

    @BeforeEach
    void setUp()
    {
        service = new BusinessProjectWorkReportService();
        projects = mock(BusinessProjectMapper.class);
        reports = mock(BusinessProjectWorkReportMapper.class);
        files = mock(BusinessFileService.class);
        ReflectionTestUtils.setField(service, "projectMapper", projects);
        ReflectionTestUtils.setField(service, "reportMapper", reports);
        ReflectionTestUtils.setField(service, "fileService", files);
        BusinessProject project = new BusinessProject();
        project.setProjectId(10L);
        project.setStatus("ACTIVE");
        project.setMainOwnerUserId(99L);
        routine = new BusinessProjectRoutine();
        routine.setRoutineId(20L);
        routine.setProjectId(10L);
        routine.setStatus("ACTIVE");
        routine.setAssigneeUserId(7L);
        when(projects.selectRoutineById(20L)).thenReturn(routine);
        when(projects.selectProjectById(10L)).thenReturn(project);
        when(projects.selectMemberRole(10L, 7L)).thenReturn("MEMBER");
    }

    @Test
    void onlyAssignedExecutorCanSubmit()
    {
        BusinessProjectWorkReport input = input("DAILY", "已完成素材整理", null);
        assertThrows(ServiceException.class, () -> service.submit(input, 8L, "other"));
    }

    @Test
    void memberCanSubmitProjectReportWithoutRoutine()
    {
        BusinessProjectWorkReport input = input("DAILY", "项目进度已整理", null);
        input.setRoutineId(null);
        input.setProjectId(10L);
        doAnswer(call -> { ((BusinessProjectWorkReport) call.getArgument(0)).setReportId(42L); return 1; })
            .when(reports).insert(any(BusinessProjectWorkReport.class));
        when(reports.selectById(42L)).thenReturn(new BusinessProjectWorkReport());

        service.submit(input, 7L, "member");

        ArgumentCaptor<BusinessProjectWorkReport> saved = ArgumentCaptor.forClass(BusinessProjectWorkReport.class);
        verify(reports).insert(saved.capture());
        assertEquals(10L, saved.getValue().getProjectId());
        assertNull(saved.getValue().getRoutineId());
        assertEquals("member", saved.getValue().getSubmittedUserName());
    }

    @Test
    void unrelatedUserCannotSubmitProjectReport()
    {
        BusinessProjectWorkReport input = input("DAILY", "项目进度已整理", null);
        input.setRoutineId(null);
        input.setProjectId(10L);
        assertThrows(ServiceException.class, () -> service.submit(input, 8L, "other"));
    }

    @Test
    void requiresTextOrAttachment()
    {
        BusinessProjectWorkReport input = input("MONTHLY", "  ", "  ");
        assertThrows(ServiceException.class, () -> service.submit(input, 7L, "member"));
    }

    @Test
    void weeklyReportUsesCurrentMondayToSundayAndValidatesAttachments()
    {
        doAnswer(call -> { ((BusinessProjectWorkReport) call.getArgument(0)).setReportId(42L); return 1; })
            .when(reports).insert(any(BusinessProjectWorkReport.class));
        when(reports.selectById(42L)).thenReturn(new BusinessProjectWorkReport());
        service.submit(input("WEEKLY", null, "/profile/business/10/report.pdf"), 7L, "member");
        ArgumentCaptor<BusinessProjectWorkReport> saved = ArgumentCaptor.forClass(BusinessProjectWorkReport.class);
        verify(reports).insert(saved.capture());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(monday, ((java.sql.Date) saved.getValue().getPeriodStart()).toLocalDate());
        assertEquals(monday.plusDays(6), ((java.sql.Date) saved.getValue().getPeriodEnd()).toLocalDate());
        verify(files).validateReferences(eq("/profile/business/10/report.pdf"), eq(10L), eq(7L), eq(false), eq(false));
    }

    @Test
    void onlyProjectOwnerCanReview()
    {
        when(reports.selectById(42L)).thenReturn(pendingReport());
        assertThrows(ServiceException.class, () -> service.review(42L, "APPROVED", null, 7L, "member"));
    }

    @Test
    void returningReportRequiresReason()
    {
        when(reports.selectById(42L)).thenReturn(pendingReport());
        assertThrows(ServiceException.class, () -> service.review(42L, "RETURNED", "  ", 99L, "owner"));
    }

    @Test
    void projectOwnerCanApprovePendingReport()
    {
        BusinessProjectWorkReport pending = pendingReport();
        BusinessProjectWorkReport approved = pendingReport();
        approved.setStatus("APPROVED");
        when(reports.selectById(42L)).thenReturn(pending, approved);
        when(reports.review(42L, "APPROVED", "", 99L, "owner")).thenReturn(1);
        assertEquals("APPROVED", service.review(42L, "APPROVED", null, 99L, "owner").getStatus());
        verify(reports, never()).insertReturnNotification(any(), any());
    }

    @Test
    void returningReportNotifiesItsSubmitter()
    {
        BusinessProjectWorkReport pending = pendingReport();
        pending.setSubmittedUserId(7L);
        BusinessProjectWorkReport returned = pendingReport();
        returned.setStatus("RETURNED");
        when(reports.selectById(42L)).thenReturn(pending, returned);
        when(reports.review(42L, "RETURNED", "请补充本周成果", 99L, "owner")).thenReturn(1);
        assertEquals("RETURNED", service.review(42L, "RETURNED", "请补充本周成果", 99L, "owner").getStatus());
        verify(reports).insertReturnNotification(42L, 7L);
    }

    private BusinessProjectWorkReport pendingReport()
    {
        BusinessProjectWorkReport report = new BusinessProjectWorkReport();
        report.setReportId(42L);
        report.setProjectId(10L);
        report.setStatus("PENDING");
        return report;
    }

    private BusinessProjectWorkReport input(String frequency, String content, String attachments)
    {
        BusinessProjectWorkReport input = new BusinessProjectWorkReport();
        input.setRoutineId(20L);
        input.setFrequency(frequency);
        input.setContent(content);
        input.setAttachmentUrls(attachments);
        return input;
    }
}
