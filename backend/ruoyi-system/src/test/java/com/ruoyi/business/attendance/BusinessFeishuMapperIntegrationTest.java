package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.mapper.BusinessFeishuMapper;

/** Exercises actual production mapper SQL; no tenant, personal or accounting data is used. */
class BusinessFeishuMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sessions;
    @BeforeEach void setup() throws Exception
    {
        dataSource=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:feishu_"+UUID.randomUUID().toString().replace("-","")+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        String migration=new String(Files.readAllBytes(Paths.get("../sql/migrations/V066__feishu_readonly_attendance.sql")),StandardCharsets.UTF_8)
            .replaceAll("(?m)--[^\\r\\n]*","").replaceAll("(?i)\\s+collate\\s+utf8mb4_bin","").replaceAll("(?i)engine=InnoDB\\s+default charset=utf8mb4","");
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement())
        {
            for(String sql:migration.split(";"))if(!sql.trim().isEmpty())s.execute(sql);
            s.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(100),leader_user_id bigint,parent_id bigint,del_flag varchar(1),status varchar(1),order_num int,ancestors varchar(200))");
            s.execute("create table sys_user(user_id bigint primary key,nick_name varchar(100),user_name varchar(100),del_flag varchar(1),dept_id bigint)");
            s.execute("insert into sys_dept values(110,'公司A',9,100,'0','0',1,'0,100'),(111,'公司B',8,100,'0','0',2,'0,100')");
            s.execute("insert into sys_user values(7,'员工A','user7','0',110),(8,'员工B','user8','0',111)");
            s.execute("insert into biz_feishu_connection(connection_id,company_dept_id,tenant_key,source_timezone,state,version,created_by,created_at) values(1,110,'tenant','Asia/Shanghai','PARALLEL',2,9,current_timestamp),(2,111,'tenant','Asia/Shanghai','PARALLEL',1,8,current_timestamp)");
        }
        Configuration configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        try(InputStream input=Resources.getResourceAsStream("mapper/business/BusinessFeishuMapper.xml"))
        {new XMLMapperBuilder(input,configuration,"mapper/business/BusinessFeishuMapper.xml",configuration.getSqlFragments()).parse();}
        sessions=new SqlSessionFactoryBuilder().build(configuration);
    }
    @Test void everyDeclaredMapperOperationIsMappedAndConnectionsDoNotHaveSecretFields()
    {
        for(java.lang.reflect.Method method:BusinessFeishuMapper.class.getDeclaredMethods())
            assertTrue(sessions.getConfiguration().hasStatement(BusinessFeishuMapper.class.getName()+"."+method.getName()),method.getName());
        try(SqlSession session=sessions.openSession())
        {
            Map<String,Object> c=session.getMapper(BusinessFeishuMapper.class).connection(1L);
            assertEquals("PARALLEL",value(c,"state"));
            for(String key:c.keySet())assertFalse(key.toLowerCase().contains("secret")||key.toLowerCase().contains("token"));
        }
    }
    @Test void revisionHistoryKeepsOriginalRunAndOnlyOneCurrentSnapshot()
    {
        try(SqlSession session=sessions.openSession(false))
        {
            BusinessFeishuMapper mapper=session.getMapper(BusinessFeishuMapper.class);
            Map<String,Object> first=observation(1,"CONFIRMED");mapper.insertObservation(first);
            Long original=((Number)first.get("observationId")).longValue();
            mapper.touchObservation(map("observationId",original,"runId",11L));
            assertEquals(10L,((Number)value(mapper.observation(original),"syncRunId")).longValue());
            assertEquals(11L,((Number)value(mapper.observation(original),"lastSeenRunId")).longValue());
            mapper.supersedeObservation(original);
            Map<String,Object> canceled=observation(2,"CANCELED");canceled.put("runId",12L);mapper.insertObservation(canceled);
            assertEquals("CANCELED",value(mapper.currentObservation(canceled),"normalizedStatus"));
            assertEquals(0,((Number)value(mapper.observation(original),"isCurrent")).intValue());
            Map<String,Object> q=map("dateFrom","2026-09-07","dateTo","2026-09-07","companyDeptId",110L,"userId",7L,"includeHistory",true,"offset",0,"pageSize",50);
            assertEquals(2,mapper.records(q).size());q.put("includeHistory",false);assertEquals(1,mapper.records(q).size());
            q.put("companyDeptId",111L);assertEquals(0,mapper.records(q).size());session.commit();
        }
    }
    @Test void identityConflictsAreScopedByTenantAndEffectiveIntervalAcrossCompanies()
    {
        try(SqlSession session=sessions.openSession(false))
        {
            BusinessFeishuMapper mapper=session.getMapper(BusinessFeishuMapper.class);
            mapper.insertMapping(map("connectionId",1L,"userId",7L,"externalUserId","external7","effectiveFrom","2026-01-01","effectiveTo","2026-09-30","actorId",9L));
            Map<String,Object> candidate=map("connectionId",2L,"tenantKey","tenant","userId",8L,"externalUserId","external7","effectiveFrom","2026-09-01","effectiveTo",null);
            assertEquals(1,mapper.mappingConflicts(candidate));
            candidate.put("userId",7L);assertEquals(1,mapper.mappingConflicts(candidate)); // A transfer cannot retain two current company authorities for the same identity.
            candidate.put("externalUserId","renamed-external7");assertEquals(1,mapper.mappingConflicts(candidate));
            candidate.put("userId",8L);candidate.put("externalUserId","external7");
            candidate.put("effectiveFrom","2026-10-01");assertEquals(0,mapper.mappingConflicts(candidate));
            candidate.put("effectiveFrom","2026-09-01");candidate.put("tenantKey","another-tenant");assertEquals(0,mapper.mappingConflicts(candidate));
        }
    }
    @Test void failedOrFormerLeaseHolderCannotAdvanceCompleteWatermark() throws Exception
    {
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement())
        {s.execute("update biz_feishu_connection set running_run_id=10 where connection_id=1");}
        try(SqlSession session=sessions.openSession(false))
        {
            BusinessFeishuMapper mapper=session.getMapper(BusinessFeishuMapper.class);
            assertEquals(0,mapper.releaseRun(map("connectionId",1L,"runId",9L,"status","COMPLETE")));
            assertNull(value(mapper.connection(1L),"lastCompleteAt"));
            assertEquals(1,mapper.releaseRun(map("connectionId",1L,"runId",10L,"status","PARTIAL")));
            assertNull(value(mapper.connection(1L),"lastCompleteAt"));
        }
    }
    @Test void sourceActivationRequiresVersionIdleAndParallelState()
    {
        try(SqlSession session=sessions.openSession(false))
        {
            BusinessFeishuMapper mapper=session.getMapper(BusinessFeishuMapper.class);
            Map<String,Object> change=map("connectionId",1L,"effectiveDate","2026-09-10","version",1);
            assertEquals(0,mapper.activate(change));change.put("version",2);assertEquals(1,mapper.activate(change));
            assertEquals(0,mapper.activate(change));assertEquals("ACTIVE",value(mapper.connection(1L),"state"));
        }
    }
    @Test void leaveAuthorityReadSeesCommittedCutoverAfterAnEarlierSnapshot() throws Exception
    {
        try(SqlSession local=sessions.openSession(false))
        {
            BusinessFeishuMapper mapper=local.getMapper(BusinessFeishuMapper.class);
            assertEquals("PARALLEL",value(mapper.connection(1L),"state"));
            try(SqlSession cutover=sessions.openSession(false))
            {
                assertEquals(1,cutover.getMapper(BusinessFeishuMapper.class).activate(map("connectionId",1L,"effectiveDate","2026-09-10","version",2)));cutover.commit();
            }
            mapper.lockCompany(110L);
            assertEquals("ACTIVE",value(mapper.connectionForCompany(110L),"state"));
            // MySQL repeatable-read needs current reads even when the parent leave transaction already read a project.
            org.apache.ibatis.mapping.MappedStatement authority=sessions.getConfiguration().getMappedStatement(BusinessFeishuMapper.class.getName()+".connectionForCompany");
            assertTrue(authority.getBoundSql(map("companyDeptId",110L)).getSql().trim().toLowerCase().endsWith("for update"));
            assertTrue(authority.isFlushCacheRequired());assertFalse(authority.isUseCache());
        }
    }
    @Test void syncConnectionLockDoesNotAcquireCompanyInReverseOrder() throws Exception
    {
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(SqlSession local=sessions.openSession(false))
        {
            local.getMapper(BusinessFeishuMapper.class).lockCompany(110L);
            java.util.concurrent.Future<String> sync=worker.submit(()->{try(SqlSession remote=sessions.openSession(false)){BusinessFeishuMapper mapper=remote.getMapper(BusinessFeishuMapper.class);assertEquals("PARALLEL",value(mapper.lockConnection(1L),"state"));return String.valueOf(value(mapper.connectionForCompany(110L),"state"));}});
            try{assertEquals("PARALLEL",sync.get(2,java.util.concurrent.TimeUnit.SECONDS));}
            finally{local.rollback();}
        }
        finally{worker.shutdownNow();}
    }
    private Map<String,Object> observation(int revision,String status)
    {return map("connectionId",1L,"mappingId",1L,"userId",7L,"sourceRecordKey",FeishuAttendanceClient.sha256("approval1"),"sourceRevision",revision,"fingerprint",FeishuAttendanceClient.sha256(status),"businessDate","2026-09-07","sourceTimezone","Asia/Shanghai","kind","LEAVE","normalizedStatus",status,"sourceStatus","CONFIRMED".equals(status)?"2":"3","quality","KNOWN","sourceDurationSeconds",3600L,"intervalsJson","[[1,3601]]","detailsJson","{}","runId",10L,"adapterVersion","FEISHU_ATTENDANCE_V1_20260907");}
    private Object value(Map<String,Object> row,String key)
    {for(Map.Entry<String,Object> entry:row.entrySet())if(entry.getKey().equalsIgnoreCase(key))return entry.getValue();return null;}
}
