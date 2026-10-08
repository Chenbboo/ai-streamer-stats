package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import com.ruoyi.business.domain.BusinessProjectRoutineReport;
import com.ruoyi.business.domain.BusinessProjectTaskReport;

public class BusinessMemberCompletionReportsMapperTest {
    public static String dateFormat(Timestamp date,String pattern) {
        return date==null?null:date.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern(
            pattern.contains("%H")?"yyyy-MM-dd HH:mm:ss":"yyyy-MM-dd"));
    }
    private List<Map<String,Object>> readReports(BusinessProjectMapper mapper,Long projectId) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectMemberCompletionReports(projectId)) {
            Map<String,Object> normalized=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            normalized.putAll(row);result.add(normalized);
        }
        return result;
    }
    @Test void combinesBothWorkTypesWithOriginalSubmitterAndProjectScope() throws Exception {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:completion_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection connection=source.getConnection();Statement sql=connection.createStatement()) {
            sql.execute("create alias DATE_FORMAT for \"com.ruoyi.business.mapper.BusinessMemberCompletionReportsMapperTest.dateFormat\"");
            sql.execute("create table biz_project(project_id bigint,project_name varchar(100))");
            sql.execute("create table biz_project_routine(routine_id bigint,project_id bigint,routine_name varchar(100),status varchar(16),assignee_user_id bigint,target_mode varchar(16))");
            sql.execute("create table biz_project_task(task_id bigint,project_id bigint,task_name varchar(100),active_status varchar(16))");
            sql.execute("create table biz_project_routine_report(report_id bigint,project_id bigint,routine_id bigint,submitted_user_id bigint,submitted_user_name varchar(64),biz_date date,summary varchar(500),issue_reason varchar(500),evidence_urls varchar(4000),actual_value decimal(12,4),unit varchar(16),status varchar(16),create_time timestamp,update_time timestamp)");
            sql.execute("create table biz_project_task_report(report_id bigint,project_id bigint,task_id bigint,submitted_user_id bigint,submitted_user_name varchar(64),biz_date date,completion_summary varchar(500),evidence_urls varchar(4000),progress int,create_time timestamp,update_time timestamp)");
            sql.execute("insert into biz_project values(10,'项目甲'),(20,'项目乙')");
            sql.execute("insert into biz_project_routine values(11,10,'持续工作甲','VOID',9,'FIXED')");
            sql.execute("insert into biz_project_task values(12,10,'一次性任务甲','VOID')");
            sql.execute("insert into biz_project_routine_report values(1,10,11,7,'原提交人','2026-10-01','整理素材','缺少素材','/profile/proof.pdf',3,'份','SUBMITTED','2026-10-01 12:00:00',null)");
            sql.execute("insert into biz_project_routine_report values(2,10,99,7,'原提交人','2026-09-30','历史记录',null,null,1,'项','SUBMITTED','2026-09-30 12:00:00',null)");
            sql.execute("insert into biz_project_task_report values(1,10,12,8,'成员乙','2026-10-02','提交初稿',null,50,'2026-10-02 12:00:00',null),(3,20,21,7,'原提交人','2026-10-03','其他项目',null,100,'2026-10-03 12:00:00',null)");
            sql.execute("alter table biz_project_routine_report add version int default 0");
            sql.execute("alter table biz_project_task_report add version int default 0");
            sql.execute("create table biz_project_completion_submission(submission_id bigint auto_increment primary key,work_type varchar(16),source_report_id bigint,source_version int,project_id bigint,project_name varchar(200),work_name varchar(200),member_user_id bigint,member_name varchar(64),report_date date,report_details text,issue_reason text,evidence_urls text,actual_value decimal(20,4),unit varchar(32),target_mode varchar(16),progress int,submitted_time timestamp,unique(work_type,source_report_id,source_version))");
        }
        Configuration config=new Configuration(new Environment("completion",new JdbcTransactionFactory(),source));
        for(String resource:Arrays.asList("mapper/business/BusinessCompanyAccessMapper.xml","mapper/business/BusinessProjectMapper.xml"))
            try(InputStream input=Resources.getResourceAsStream(resource)) {new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession()) {
            BusinessProjectMapper mapper=session.getMapper(BusinessProjectMapper.class);
            BusinessProjectRoutineReport routine=new BusinessProjectRoutineReport();routine.setProjectId(10L);routine.setRoutineId(11L);routine.setBizDate(java.sql.Date.valueOf("2026-10-01"));
            assertEquals(1,mapper.insertRoutineCompletionSubmission(routine));
            BusinessProjectRoutineReport retired=new BusinessProjectRoutineReport();retired.setProjectId(10L);retired.setRoutineId(99L);retired.setBizDate(java.sql.Date.valueOf("2026-09-30"));
            assertEquals(1,mapper.insertRoutineCompletionSubmission(retired));
            BusinessProjectTaskReport task=new BusinessProjectTaskReport();task.setProjectId(10L);task.setTaskId(12L);task.setBizDate(java.sql.Date.valueOf("2026-10-02"));
            assertEquals(1,mapper.insertTaskCompletionSubmission(task));
            BusinessProjectTaskReport other=new BusinessProjectTaskReport();other.setProjectId(20L);other.setTaskId(21L);other.setBizDate(java.sql.Date.valueOf("2026-10-03"));
            assertEquals(1,mapper.insertTaskCompletionSubmission(other));
            List<Map<String,Object>> rows=readReports(mapper,10L);
            assertEquals(3,rows.size());
            assertEquals("TASK",rows.get(0).get("worktype"));
            assertEquals("2026-10-02",rows.get(0).get("reportdate"));
            assertEquals("提交初稿",rows.get(0).get("reportdetails"));
            assertEquals("原提交人",rows.get(1).get("membername"));
            assertEquals("/profile/proof.pdf",rows.get(1).get("evidenceurls"));
            assertEquals("缺少素材",rows.get(1).get("issuereason"));
            assertEquals("历史记录",rows.get(2).get("reportdetails"));
            assertTrue(rows.stream().allMatch(row->Long.valueOf(10L).equals(row.get("projectid"))));
            try(Statement sql=session.getConnection().createStatement()) {
                sql.execute("update biz_project_routine_report set version=1,summary='补充素材',evidence_urls='/profile/new.pdf' where report_id=1");
                sql.execute("update biz_project_task_report set version=1,completion_summary='提交终稿',evidence_urls='/profile/task.pdf',progress=100 where project_id=10");
                sql.execute("update biz_project set project_name='修改后的项目名' where project_id=10");
            }
            assertEquals(1,mapper.insertRoutineCompletionSubmission(routine));
            assertEquals(1,mapper.insertTaskCompletionSubmission(task));
            rows=readReports(mapper,10L);
            assertEquals(5,rows.size());
            Map<String,Object> original=rows.stream().filter(row->"整理素材".equals(row.get("reportdetails"))).findFirst().get();
            assertEquals("/profile/proof.pdf",original.get("evidenceurls"));
            assertEquals("项目甲",original.get("projectname"));
            assertTrue(rows.stream().anyMatch(row->"提交初稿".equals(row.get("reportdetails"))));
            assertTrue(rows.stream().anyMatch(row->"提交终稿".equals(row.get("reportdetails"))&&"/profile/task.pdf".equals(row.get("evidenceurls"))));
        }
    }
}
