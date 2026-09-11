package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessProjectProgressReport;

class BusinessProjectProgressMapperIntegrationTest {
    private SqlSessionFactory factory;
    @BeforeEach void setup() throws Exception {
        BusinessProjectWorkMapperIntegrationTest fixture=new BusinessProjectWorkMapperIntegrationTest();fixture.setup();
        factory=fixture.factory;factory.getConfiguration().addMapper(BusinessProjectProgressMapper.class);
        try(Connection c=fixture.source.getConnection();Statement sql=c.createStatement()) {
            sql.execute("alter table sys_dept add del_flag char(1)");
            sql.execute("create table biz_project_task(project_id bigint,active_status varchar(20),status varchar(20))");
            sql.execute("create table biz_project_risk(project_id bigint,status varchar(20))");
            sql.execute("create table biz_project_progress_report(report_id bigint auto_increment primary key,project_id bigint,biz_date date,progress int,completion_summary varchar(2000),evidence_urls varchar(4000),submitted_user_id bigint,submitted_user_name varchar(100),version int,create_by varchar(64),create_time timestamp,update_by varchar(64),update_time timestamp,issues_risks varchar(2000),next_plan varchar(2000),sync_tasks boolean,sync_routines boolean,snapshot_json clob,parent_project_id bigint,project_name_snapshot varchar(160))");
            sql.execute("create table biz_project_progress_notification(notification_id bigint auto_increment primary key,report_id bigint,recipient_user_id bigint,read_time timestamp,create_time timestamp default current_timestamp,unique(report_id,recipient_user_id))");
            sql.execute("insert into biz_project(project_id,parent_id,project_name,status,del_flag,version) values(2,1,'child A','ACTIVE','0',0),(3,1,'child B','ACTIVE','0',0),(4,1,'deleted','ACTIVE','2',0)");
        }
    }
    BusinessProjectProgressReport report(Long projectId,int version,int percent,String snapshot) {
        BusinessProjectProgressReport r=new BusinessProjectProgressReport();r.setProjectId(projectId);r.setParentProjectId(1L);
        r.setProjectNameSnapshot("child");r.setBizDate(java.sql.Date.valueOf("2026-09-11"));r.setCreateTime(new java.util.Date());
        r.setProgress(percent);r.setVersion(version);r.setCompletionSummary("阶段成果 "+version);r.setIssuesRisks("无");
        r.setNextPlan("下一步");r.setSyncTasks(true);r.setSyncRoutines(false);r.setSnapshotJson(snapshot);
        r.setSubmittedUserId(9L);r.setSubmittedUserName("负责人");r.setEvidenceUrls("");r.setCreateBy("owner");return r;
    }
    @Test void sameDayCorrectionsKeepBothVersionsAndReadLatestByInsertionOrder() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport first=report(2L,1,80,"{\"tasks\":[{\"progress\":80}]}");
            BusinessProjectProgressReport correction=report(2L,2,40,"{\"tasks\":[{\"progress\":40}]}");
            projects.insertProjectProgressReport(first);projects.insertProjectProgressReport(correction);
            assertNotEquals(first.getReportId(),correction.getReportId());
            assertEquals(correction.getReportId(),projects.selectProjectProgressReport(2L,first.getBizDate()).getReportId());
            assertEquals(40,projects.selectProjectById(2L).getProgressPercent());
            List<BusinessProjectProgressReport> history=reports.history(2L);
            assertEquals(2,history.size());assertEquals(first.getSnapshotJson(),history.get(1).getSnapshotJson());
            assertEquals("下一步",history.get(0).getNextPlan());assertEquals(true,history.get(0).getSyncTasks());
        }
    }
    @Test void aggregateUsesEqualDefaultsConfiguredWeightsLatestCorrectionsAndExcludesDeletedChildren() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            projects.insertProjectProgressReport(report(2L,1,80,"{}"));projects.insertProjectProgressReport(report(3L,1,20,"{}"));
            projects.insertProjectProgressReport(report(4L,1,100,"{}"));
            assertEquals(50,projects.selectProjectById(1L).getProgressPercent());
            reports.setWeight(2L,1L,new BigDecimal("3"),"parent");
            assertEquals(65,projects.selectProjectById(1L).getProgressPercent());
            projects.insertProjectProgressReport(report(2L,2,40,"{}"));
            assertEquals(35,projects.selectProjectById(1L).getProgressPercent());
            reports.setWeight(2L,1L,null,"parent");assertEquals(30,projects.selectProjectById(1L).getProgressPercent());
        }
    }
    @Test void unreportedChildCountsAsZeroAndNotificationsStayRecipientScoped() {
        try(SqlSession session=factory.openSession()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectProgressMapper reports=session.getMapper(BusinessProjectProgressMapper.class);
            BusinessProjectProgressReport r=report(2L,1,80,"{}");projects.insertProjectProgressReport(r);
            assertEquals(40,projects.selectProjectById(1L).getProgressPercent());
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
    @Test void terminalChildProgressKeepsExistingStatusSemanticsInBothOverviewAndAggregate() throws Exception {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()) {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);
            projects.insertProjectProgressReport(report(2L,1,80,"{}"));
            sql.execute("update biz_project set status='CLOSED' where project_id=2");session.clearCache();
            assertEquals(100,projects.selectProjectById(2L).getProgressPercent());assertEquals(50,projects.selectProjectById(1L).getProgressPercent());
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
