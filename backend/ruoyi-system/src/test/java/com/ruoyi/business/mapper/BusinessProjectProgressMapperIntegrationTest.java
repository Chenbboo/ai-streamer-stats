package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectProgressReport;

class BusinessProjectProgressMapperIntegrationTest {
    private SqlSessionFactory factory;
    @BeforeEach void setup() throws Exception {
        BusinessProjectWorkMapperIntegrationTest fixture=new BusinessProjectWorkMapperIntegrationTest();fixture.setup();
        factory=fixture.factory;factory.getConfiguration().addMapper(BusinessProjectProgressMapper.class);
        try(Connection c=fixture.source.getConnection();Statement sql=c.createStatement()) {
            sql.execute("create alias field for \"com.ruoyi.business.mapper.BusinessProjectHierarchyMapperIntegrationTest.field\"");
            sql.execute("alter table sys_dept add del_flag char(1)");
            sql.execute("create table biz_project_task(project_id bigint,active_status varchar(20),status varchar(20))");
            sql.execute("create table biz_project_risk(project_id bigint,status varchar(20))");
            sql.execute("create table biz_project_progress_report(report_id bigint auto_increment primary key,project_id bigint,biz_date date,progress int,completion_standard varchar(16) not null default 'STANDARD',completion_summary varchar(2000),evidence_urls varchar(4000),evidence_text varchar(2000),submitted_user_id bigint,submitted_user_name varchar(100),version int,create_by varchar(64),create_time timestamp,update_by varchar(64),update_time timestamp,issues_risks varchar(2000),next_plan varchar(2000),sync_tasks boolean,sync_routines boolean,snapshot_json clob,parent_project_id bigint,project_name_snapshot varchar(160))");
            sql.execute("create table biz_project_progress_notification(notification_id bigint auto_increment primary key,report_id bigint,recipient_user_id bigint,read_time timestamp,create_time timestamp default current_timestamp,unique(report_id,recipient_user_id))");
            sql.execute("insert into biz_project(project_id,parent_id,project_name,status,del_flag,version) values(2,1,'child A','ACTIVE','0',0),(3,1,'child B','ACTIVE','0',0),(4,1,'deleted','ACTIVE','2',0)");
        }
    }
    BusinessProjectProgressReport report(Long projectId,int version,int percent,String snapshot) {
        return report(projectId,version,percent,snapshot,java.time.LocalDate.now());
    }
    BusinessProjectProgressReport report(Long projectId,int version,int percent,String snapshot,java.time.LocalDate bizDate) {
        BusinessProjectProgressReport r=new BusinessProjectProgressReport();r.setProjectId(projectId);r.setParentProjectId(projectId==1L?null:1L);
        r.setProjectNameSnapshot("child");r.setBizDate(java.sql.Date.valueOf(bizDate));r.setCreateTime(new java.util.Date());
        r.setProgress(percent);r.setCompletionStandard("STANDARD");r.setVersion(version);r.setCompletionSummary("阶段成果 "+version);r.setIssuesRisks("无");
        r.setNextPlan("下一步");r.setSyncTasks(true);r.setSyncRoutines(false);r.setSnapshotJson(snapshot);
        r.setSubmittedUserId(9L);r.setSubmittedUserName("负责人");r.setEvidenceUrls("");r.setCreateBy("owner");return r;
    }
    @Test void currentProgressUsesOnlyTheLatestReportFromTheCurrentCalendarMonth() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            java.time.LocalDate today=java.time.LocalDate.now();
            projects.insertProjectProgressReport(report(2L,1,90,"{\"month\":\"previous\"}",today.minusMonths(1)));
            BusinessProject beforeCurrentMonthReport=projects.selectProjectById(2L);
            assertEquals(0,beforeCurrentMonthReport.getProgressPercent());
            assertNull(beforeCurrentMonthReport.getProgressReportId());
            assertEquals("STANDARD",beforeCurrentMonthReport.getProgressCompletionStandard());

            BusinessProjectProgressReport current=report(2L,2,35,"{\"month\":\"current\"}",today);
            projects.insertProjectProgressReport(current);session.clearCache();
            BusinessProject inCurrentMonth=projects.selectProjectById(2L);
            assertEquals(35,inCurrentMonth.getProgressPercent());
            assertEquals(current.getReportId(),inCurrentMonth.getProgressReportId());
            assertEquals(2,session.getMapper(BusinessProjectProgressMapper.class).history(2L).size());
        }
    }
    @Test void excessStandardPersistsInHistoryAndAllMonthlyProjectReads() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport old=report(2L,1,300,"{}",java.time.LocalDate.now().minusMonths(1));
            old.setCompletionStandard("EXCESS");projects.insertProjectProgressReport(old);
            assertEquals("STANDARD",projects.selectProjectById(2L).getProgressCompletionStandard());
            BusinessProjectProgressReport current=report(2L,2,220,"{}");current.setCompletionStandard("EXCESS");
            projects.insertProjectProgressReport(current);session.clearCache();
            BusinessProject project=projects.selectProjectById(2L);
            assertEquals(220,project.getProgressPercent());assertEquals("EXCESS",project.getProgressCompletionStandard());
            assertEquals("EXCESS",projects.selectProjectProgressReport(2L,current.getBizDate()).getCompletionStandard());
            assertEquals("EXCESS",projects.selectLatestProjectProgressReport(2L).getCompletionStandard());
            assertEquals("EXCESS",reports.history(2L).get(0).getCompletionStandard());
            assertEquals("EXCESS",reports.childHistory(1L).get(0).getCompletionStandard());
            reports.notifyOwner(current.getReportId(),8L);
            assertEquals("EXCESS",reports.recipientHistory(2L,8L).get(0).getCompletionStandard());
            Map<String,Object> query=new HashMap<>();query.put("viewAll",true);query.put("parentId",1L);
            BusinessProject listed=projects.selectProjectList(query).stream().filter(p->p.getProjectId().equals(2L)).findFirst().get();
            assertEquals(220,listed.getProgressPercent());assertEquals("EXCESS",listed.getProgressCompletionStandard());
            BusinessProject dashboard=projects.selectDashboardProjectPage(1L,true,false,0,100,null,null).stream().filter(p->p.getProjectId().equals(2L)).findFirst().get();
            assertEquals(220,dashboard.getProgressPercent());assertEquals("EXCESS",dashboard.getProgressCompletionStandard());
            projects.insertProjectProgressReport(report(2L,3,100,"{}"));session.clearCache();
            assertEquals("STANDARD",projects.selectProjectById(2L).getProgressCompletionStandard());
            assertEquals("EXCESS",reports.history(2L).get(1).getCompletionStandard());
            assertEquals("ACTIVE",projects.selectProjectById(2L).getStatus());
        }
    }

    @Test void sameDayCorrectionsKeepBothVersionsAndReadLatestByInsertionOrder() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport first=report(2L,1,80,"{\"tasks\":[{\"progress\":80}]}");
            BusinessProjectProgressReport correction=report(2L,2,40,"{\"tasks\":[{\"progress\":40}]}");
            correction.setEvidenceText("文字成果凭证");
            projects.insertProjectProgressReport(first);projects.insertProjectProgressReport(correction);
            assertNotEquals(first.getReportId(),correction.getReportId());
            assertEquals(correction.getReportId(),projects.selectProjectProgressReport(2L,first.getBizDate()).getReportId());
            assertEquals(40,projects.selectProjectById(2L).getProgressPercent());
            List<BusinessProjectProgressReport> history=reports.history(2L);
            assertEquals(2,history.size());assertEquals(first.getSnapshotJson(),history.get(1).getSnapshotJson());
            assertEquals("文字成果凭证",history.get(0).getEvidenceText());
            assertEquals("文字成果凭证",projects.selectProjectById(2L).getProgressEvidenceText());
            assertEquals("下一步",history.get(0).getNextPlan());assertEquals(true,history.get(0).getSyncTasks());
        }
    }
    @Test void parentProgressUsesOnlyItsOwnLatestReport() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            projects.insertProjectProgressReport(report(2L,1,80,"{}"));projects.insertProjectProgressReport(report(3L,1,20,"{}"));
            projects.insertProjectProgressReport(report(4L,1,100,"{}"));
            assertEquals(0,projects.selectProjectById(1L).getProgressPercent());
            projects.insertProjectProgressReport(report(1L,1,65,"{}"));
            assertEquals(65,projects.selectProjectById(1L).getProgressPercent());
            projects.insertProjectProgressReport(report(2L,2,40,"{}"));
            assertEquals(65,projects.selectProjectById(1L).getProgressPercent());
            projects.insertProjectProgressReport(report(1L,2,75,"{}"));
            assertEquals(75,projects.selectProjectById(1L).getProgressPercent());
            assertEquals(40,projects.selectProjectById(2L).getProgressPercent());
        }
    }
    @Test void childNotificationsStayRecipientScopedWithoutChangingParentProgress() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport r=report(2L,1,80,"{}");projects.insertProjectProgressReport(r);
            assertEquals(0,projects.selectProjectById(1L).getProgressPercent());
            reports.notifyOwner(r.getReportId(),8L);
            assertEquals(1,reports.notifications(8L).size());assertTrue(reports.notifications(9L).isEmpty());
            Long id=((Number)reports.notifications(8L).get(0).get("notificationId")).longValue();
            assertEquals(0,reports.readNotification(id,9L));assertEquals(1,reports.readNotification(id,8L));
            assertNotNull(reports.notifications(8L).get(0).get("readTime"));
            projects.insertProjectProgressReport(report(2L,2,90,"{\"later\":true}"));
            assertEquals(1,reports.recipientHistory(2L,8L).size());
            assertEquals(r.getReportId(),reports.recipientHistory(2L,8L).get(0).getReportId());
            assertTrue(reports.recipientHistory(2L,9L).isEmpty());
        }
    }
    @Test void terminalChildProgressKeepsItsOwnStatusSemanticsWithoutChangingParent() throws Exception {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            projects.insertProjectProgressReport(report(2L,1,80,"{}"));
            sql.execute("update biz_project set status='CLOSED' where project_id=2");session.clearCache();
            assertEquals(100,projects.selectProjectById(2L).getProgressPercent());assertEquals(0,projects.selectProjectById(1L).getProgressPercent());
            sql.execute("update biz_project set status='CANCELED' where project_id=2");session.clearCache();
            assertEquals(0,projects.selectProjectById(2L).getProgressPercent());assertEquals(0,projects.selectProjectById(1L).getProgressPercent());
        }
    }
    @Test void rollbackDoesNotLeaveReportOrNotificationAndSoftDeletionKeepsParentArchive() throws Exception {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport r=report(2L,1,80,"{\"archived\":true}");projects.insertProjectProgressReport(r);reports.notifyOwner(r.getReportId(),8L);
            session.rollback();assertTrue(reports.history(2L).isEmpty());assertTrue(reports.notifications(8L).isEmpty());
            r.setReportId(null);projects.insertProjectProgressReport(r);
            session.getConnection().createStatement().execute("update biz_project set del_flag='2' where project_id=2");session.clearCache();
            assertEquals("2",reports.archiveProject(2L).getDelFlag());
            assertEquals(r.getSnapshotJson(),reports.childHistory(1L).get(0).getSnapshotJson());
        }
    }
}
