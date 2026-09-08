package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class BusinessProjectBonusMapperIntegrationTest
{
    @Test void bonusesRespectMembershipApprovalPolicyAndCurrencyWithoutJoinMultiplication() throws Exception
    {
        DataSource source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:bonus_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection();Statement s=c.createStatement()){
            s.execute("create table biz_project(project_id bigint,project_no varchar(40),project_name varchar(100),base_currency varchar(3),status varchar(16),update_time timestamp,del_flag char(1),main_owner_user_id bigint)");
            s.execute("create table biz_project_member(project_id bigint,user_id bigint,status char(1))");
            s.execute("create table biz_project_kpi_plan(plan_id bigint,reward_policy_version varchar(24))");
            s.execute("create table biz_project_kpi_settlement(project_id bigint,plan_id bigint,bonus_amount decimal(20,2),status varchar(16))");
            s.execute("create table biz_incentive_award(project_id bigint,currency varchar(3),amount decimal(20,2),status varchar(16))");
            s.execute("insert into biz_project values(1,'P1','人民币项目','CNY','ACTIVE',current_timestamp,'0',9),(2,'P2','美元项目','USD','ACTIVE',current_timestamp,'0',9),(3,'P3','无权项目','CNY','ACTIVE',current_timestamp,'0',10)");
            s.execute("insert into biz_project_member values(1,7,'0'),(2,7,'0'),(3,7,'1')");
            s.execute("insert into biz_project_kpi_plan values(1,'LEGACY_LINKED'),(2,'INDEPENDENT_V1')");
            s.execute("insert into biz_project_kpi_settlement values(1,1,100,'CONFIRMED'),(1,1,50,'CONFIRMED'),(1,2,900,'CONFIRMED'),(1,1,999,'SUBMITTED'),(2,1,100,'CONFIRMED'),(3,1,1000,'CONFIRMED')");
            s.execute("insert into biz_incentive_award values(1,'CNY',200,'APPROVED'),(1,'CNY',300,'APPROVED'),(1,'CNY',999,'SUBMITTED'),(2,'USD',25,'APPROVED'),(2,'USD',999,'CANCELED')");
        }
        Configuration config=new Configuration(new Environment("bonus",new JdbcTransactionFactory(),source));
        String resource="mapper/business/BusinessProjectKpiMapper.xml";
        try(InputStream in=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession()){
            List<Map<String,Object>> rows=session.getMapper(BusinessProjectKpiMapper.class).selectMemberProjectBonusTotals(7L);
            assertEquals(2,rows.size());
            Map<String,Object> cny=rows.stream().filter(r->"CNY".equals(r.get("CURRENCY"))).findFirst().get();
            Map<String,Object> usd=rows.stream().filter(r->"USD".equals(r.get("CURRENCY"))).findFirst().get();
            assertEquals(new BigDecimal("650.00"),cny.get("TOTALBONUS"));
            assertEquals(new BigDecimal("25.00"),usd.get("TOTALBONUS"));assertEquals(new BigDecimal("100.00"),usd.get("LEGACYBONUS"));
            assertTrue(session.getMapper(BusinessProjectKpiMapper.class).selectMemberProjectBonusTotals(55L).isEmpty());
        }
    }
}
