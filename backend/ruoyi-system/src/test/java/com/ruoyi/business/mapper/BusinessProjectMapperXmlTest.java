package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BusinessProjectMapperXmlTest
{
    @Test
    void currentMemberQueryExcludesMembersWhoAlreadyLeft()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectMembers\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("where m.project_id=#{projectId} and m.status='0'"));
    }

    @Test
    void oneOffTaskDashboardIncludesApprovedLeaveState()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectMyWorkTasks\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("leave_record.leave_id todayLeaveId"));
        assertTrue(query.contains("leave_record.status='ACTIVE'"));
    }

    @Test
    void projectEventsExposePersonnelNameAndLoginAccountSeparately()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectEvents\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("left join sys_user operator_user on operator_user.user_id=event.operator_user_id"));
        assertTrue(query.contains("operator_user.nick_name"));
        assertTrue(query.contains("operatorAccount"));
        assertTrue(query.contains("left join sys_user subject_user"));
        assertTrue(query.contains("subjectName"));
    }

    @Test
    void ownerPendingEffortQuerySpansAllOwnedProjects()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectOwnerPendingEffortRequests\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("e.report_status='SUBMITTED'"));
        assertTrue(query.contains("p.main_owner_user_id=#{userId}"));
        assertTrue(query.contains("e.deviation_reason deviationReason"));
    }

    @Test
    void removingMemberUnassignsRoutineWithoutHidingItsHistory()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<update id=\"unassignActiveMemberRoutines\"");
        int end = xml.indexOf("</update>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("assignee_user_id=null"));
        assertTrue(query.contains("assignee_name=null"));
        assertTrue(query.contains("status='ACTIVE'"));
        assertTrue(!query.contains("status='VOID'"));
    }

    @Test
    void attachmentLookupNormalizesLegacyWindowsSeparators()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectAttachmentProjectIds\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("char(92)"));
        assertTrue(query.contains("biz_project_task_report"));
        assertTrue(query.contains("biz_staff_leave_request"));
    }

    @Test
    void terminalGuardReadsTheRealEffortReportTable()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"countPendingProjectEfforts\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("from biz_project_effort_report"));
        assertTrue(!query.contains("biz_project_staff_effort"));
    }

    @Test
    void terminalGuardReadsTheRealLeaveRequestStatusColumn()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"countPendingProjectLeaveRequests\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("status in ('PENDING','CANCEL_PENDING')"));
        assertTrue(!query.contains("request_status"));
    }

}
