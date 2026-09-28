package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;

class BusinessMemberDayAttendanceMapperTest {
    private DataSource source;
    private SqlSessionFactory sessions;
    @BeforeEach void setup() throws Exception {
        source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:day_attendance_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection();Statement s=c.createStatement()) {
            s.execute("create table biz_project_member(project_id bigint,user_id bigint)");
            s.execute("create table biz_project_member_cost_period(project_id bigint,user_id bigint)");
            s.execute("create table biz_project_member_day_cost(project_id bigint,user_id bigint)");
            s.execute("create table biz_feishu_connection(connection_id bigint,state varchar(24))");
            s.execute("create table biz_feishu_mapping(mapping_id bigint,connection_id bigint,user_id bigint,status varchar(24),effective_from date,effective_to date)");
            s.execute("create table biz_feishu_observation(observation_id bigint,mapping_id bigint,connection_id bigint,user_id bigint,business_date date,kind varchar(24),normalized_status varchar(24),quality varchar(24),intervals_json varchar(200),is_current int)");
            s.execute("insert into biz_project_member values(12,137),(13,137),(14,138)");
            s.execute("insert into biz_feishu_connection values(1,'PARALLEL')");
            s.execute("insert into biz_feishu_mapping values(1,1,137,'CONFIRMED','2026-09-01',null),(2,1,138,'CONFIRMED','2026-09-01',null)");
            s.execute("insert into biz_feishu_observation values(610,1,1,137,'2026-09-28','LEAVE','CONFIRMED','KNOWN','[[1,10]]',0),(700,1,1,137,'2026-09-28','LEAVE','CANCELED','KNOWN','[[1,10]]',1),(626,1,1,137,'2026-09-28','SHIFT','CONFIRMED','KNOWN','[[1,10]]',1),(900,2,1,138,'2026-09-28','LEAVE','CONFIRMED','KNOWN','[[1,10]]',1)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        String path="mapper/business/BusinessMemberDayCostMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(path)) {new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        sessions=new SqlSessionFactoryBuilder().build(config);
    }
    private List<Map<String,Object>> read(long project) {
        try(SqlSession session=sessions.openSession()) {
            return session.getMapper(BusinessMemberDayCostMapper.class).selectCostAttendance(project,"2026-09-28","2026-09-28");
        }
    }
    private void execute(String sql) throws Exception {
        try(Connection c=source.getConnection();Statement s=c.createStatement()) {s.execute(sql);}
    }
    @Test void currentCanceledRevisionReplacesApprovalAndAppliesAcrossThisPersonsProjects() {
        List<Map<String,Object>> rows=read(12);
        assertEquals(2,rows.size());assertEquals(rows,read(13));
        assertTrue(rows.stream().allMatch(r->137L==((Number)r.get("userId")).longValue()));
        assertTrue(rows.stream().anyMatch(r->"CANCELED".equals(r.get("normalizedStatus"))));
        assertEquals(1,read(14).size());
    }
    @Test void mappingDatesLimitRetiredIdentitiesButStillReadValidHistoricalDay() throws Exception {
        execute("update biz_feishu_mapping set status='RETIRED',effective_to='2026-09-28' where mapping_id=1");
        assertEquals(2,read(12).size());
        execute("update biz_feishu_mapping set effective_to='2026-09-27' where mapping_id=1");
        assertTrue(read(12).isEmpty());
    }
    @Test void archivedMemberCostsRemainEligibleForCorrectionsAndUnrelatedUsersStayExcluded() throws Exception {
        execute("delete from biz_project_member where project_id=12");
        execute("insert into biz_project_member_cost_period values(12,137)");
        assertEquals(2,read(12).size());
        execute("delete from biz_project_member_cost_period where project_id=12");
        execute("insert into biz_project_member_day_cost values(12,137)");
        assertEquals(2,read(12).size());
    }
}
