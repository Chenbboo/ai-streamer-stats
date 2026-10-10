package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

public class BusinessHistoricalAllocationMapperTest {
    SqlSessionFactory factory;
    public static String dateFormat(java.sql.Timestamp date,String pattern){return date==null?null:new SimpleDateFormat("yyyy-MM-dd").format(date);}
    @BeforeEach void setup() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:history_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("create alias date_format for \"com.ruoyi.business.mapper.BusinessHistoricalAllocationMapperTest.dateFormat\"");
            s.execute("create table biz_project(project_id bigint, project_no varchar(30),project_name varchar(100),main_owner_user_id bigint,main_owner_name varchar(50),base_currency varchar(5),status varchar(20),accounting_state varchar(20),delivery_policy_version varchar(30),cost_policy_version varchar(30),version int,del_flag char(1),plan_start_date date,actual_start_date date,actual_end_date date,plan_end_date date,create_time timestamp,update_by varchar(50),update_time timestamp)");
            s.execute("create table biz_project_member(project_id bigint,user_id bigint,user_name_snapshot varchar(50),member_role varchar(30),joined_date date,left_date date,status char(1))");
            s.execute("create table biz_project_member_cost_period(project_id bigint,user_id bigint,user_name varchar(50),member_role varchar(30),joined_date date,left_date date)");
            s.execute("create table biz_project_staff_allocation(allocation_id bigint,status varchar(20),version int,update_by varchar(50),update_time timestamp)");
            s.execute("create table biz_project_daily_result(project_id bigint,is_current char(1),close_status varchar(20),biz_date date)");
            s.execute("create table biz_project_event(project_id bigint,event_type varchar(30),create_time timestamp)");
            s.execute("insert into biz_project(project_id,project_name,main_owner_user_id,status,accounting_state,cost_policy_version,version,del_flag,plan_start_date,actual_start_date,create_time) values(1,'历史项目',10,'ACTIVE','OPEN','MEMBER_DAYS_V1',3,'0','2026-08-01','2026-10-01','2026-10-01'),(2,'冻结项目',20,'CLOSED','CLOSED','MEMBER_DAYS_V1',3,'0','2026-08-01','2026-08-01','2026-08-01')");
            s.execute("insert into biz_project_member values(1,7,'成员甲','MEMBER','2026-10-01',null,'0'),(2,7,'成员甲','MEMBER','2026-08-01','2026-09-30','1')");
            s.execute("insert into biz_project_member_cost_period values(1,7,'成员甲','MEMBER','2026-08-01','2026-09-01'),(1,8,'成员乙','MEMBER','2026-08-01','2026-08-15')");
            s.execute("insert into biz_project_staff_allocation values(7,'ACTIVE',2,null,null)");
            s.execute("insert into biz_project_daily_result values(1,'1','CLOSED','2026-09-01'),(1,'0','CLOSED','2026-09-02'),(1,'1','OPEN','2026-09-03')");
        }
        Configuration config=new Configuration(new Environment("history",new JdbcTransactionFactory(),ds));
        String path="mapper/business/BusinessHistoricalAllocationMapper.xml";
        try(InputStream in=Resources.getResourceAsStream(path)){new XMLMapperBuilder(in,config,path,config.getSqlFragments()).parse();}
        factory=new SqlSessionFactoryBuilder().build(config);
    }
    @Test void findsHistoricalExitedMembersAndFrozenPeersBySelectedPeriod(){
        try(SqlSession session=factory.openSession()){
            BusinessHistoricalAllocationMapper m=session.getMapper(BusinessHistoricalAllocationMapper.class);
            List<Map<String,Object>> rows=m.selectMemberships(7L,null,"2026-08-01","2026-09-01");assertEquals(2,rows.size());assertEquals(1L,column(rows.get(0),"projectId"));assertEquals("2026-08-01",column(rows.get(0),"joinedDate"));
            assertEquals(0,m.selectMemberships(8L,null,"2026-09-01","2026-09-30").size());assertEquals(1,m.selectMemberships(null,1L,"2026-10-01","2026-10-10").size());
        }
    }
    Object column(Map<String,Object> row,String name){return row.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).findFirst().orElseThrow(()->new AssertionError("Missing column: "+name)).getValue();}
    @Test void deletedProjectsStopParticipatingAfterDeletionAndRetainEarlierHistory() throws Exception {
        try(SqlSession session=factory.openSession();Statement s=session.getConnection().createStatement()){
            s.execute("insert into biz_project(project_id,project_name,status,accounting_state,cost_policy_version,del_flag,plan_start_date,actual_start_date,update_time) values(3,'同名旧项目','ACTIVE','OPEN','MEMBER_DAYS_V1','2','2026-08-01','2026-08-01','2026-10-10'),(4,'无删除事件的旧项目','ACTIVE','OPEN','MEMBER_DAYS_V1','2','2026-08-01','2026-08-01','2026-09-12')");
            s.execute("insert into biz_project_member values(3,7,'成员甲','MEMBER','2026-08-01',null,'0'),(4,7,'成员甲','MEMBER','2026-08-01',null,'0')");
            s.execute("insert into biz_project_event values(3,'DELETE','2026-09-10 12:00:00'),(3,'DELETE','2026-09-20 12:00:00')");
            BusinessHistoricalAllocationMapper m=session.getMapper(BusinessHistoricalAllocationMapper.class);
            assertEquals(1,m.selectMemberships(7L,3L,"2026-09-01","2026-09-09").size());
            List<Map<String,Object>> deletionDay=m.selectMemberships(7L,3L,"2026-09-10","2026-09-10");
            assertEquals(1,deletionDay.size());assertEquals("2026-09-10",column(deletionDay.get(0),"projectEndDate"));
            assertTrue(m.selectMemberships(7L,3L,"2026-09-11","2026-10-07").isEmpty());
            assertEquals("2026-09-12",column(m.selectMemberships(7L,4L,"2026-09-12","2026-09-12").get(0),"projectEndDate"));
            assertTrue(m.selectMemberships(7L,4L,"2026-09-13","2026-10-07").isEmpty());
            List<Map<String,Object>> current=m.selectMemberships(7L,null,"2026-10-01","2026-10-07");
            assertEquals(1,current.size());assertEquals(1L,column(current.get(0),"projectId"));
        }
    }
    @Test void optimisticVersionsAndFrozenPeriodChecksProtectHistoricalWrites(){
        try(SqlSession session=factory.openSession()){
            BusinessHistoricalAllocationMapper m=session.getMapper(BusinessHistoricalAllocationMapper.class);
            assertEquals(0,m.voidVersion(7L,1,"owner"));assertEquals(1,m.voidVersion(7L,2,"owner"));assertEquals(0,m.voidVersion(7L,2,"owner"));
            assertEquals(0,m.restoreStart(1L,2,"2026-08-01","owner"));assertEquals(1,m.restoreStart(1L,3,"2026-08-01","owner"));assertEquals(0,m.restoreStart(2L,3,"2026-08-01","owner"));
            assertEquals(1,m.countFrozenResults(1L,"2026-09-01","2026-09-30"));assertEquals(0,m.countFrozenResults(1L,"2026-09-02","2026-09-30"));
        }
    }
}
