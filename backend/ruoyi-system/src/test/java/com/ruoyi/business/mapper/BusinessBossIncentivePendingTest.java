package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class BusinessBossIncentivePendingTest
{
    @Test void submittedAwardsAppearInAuthorizedBossPendingAndLeaveAfterReview() throws Exception
    {
        try (SqlSession session = factory().openSession(); Statement sql = session.getConnection().createStatement())
        {
            Map<String,Object> query = new HashMap<>(); query.put("userId",8L); query.put("viewAll",false);
            List<Map<String,Object>> rows = session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query);
            assertEquals(1,rows.size()); assertEquals(2L,((Number)rows.get(0).get("AWARDID")).longValue());
            assertEquals("INCENTIVE_REVIEW",rows.get(0).get("CATEGORY"));
            sql.execute("update biz_incentive_award set status='SUBMITTED' where award_id=1"); session.clearCache();
            assertEquals(2,session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query).size());
            for (String status : new String[]{"APPROVED","RETURNED","CANCELED"})
            {
                sql.execute("update biz_incentive_award set status='"+status+"' where award_id=1"); session.clearCache();
                assertEquals(1,session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query).size());
                sql.execute("update biz_incentive_award set status='SUBMITTED' where award_id=1"); session.clearCache();
                assertEquals(2,session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query).size());
            }
            sql.execute("update biz_project set accounting_state='CLOSED' where project_id=1"); session.clearCache();
            assertTrue(session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query).isEmpty());
        }
    }

    @Test void pendingDoesNotExposeOtherCompanyAwardsOrSelfApprovalEvenWithViewAll() throws Exception
    {
        try (SqlSession session = factory().openSession())
        {
            Map<String,Object> query = new HashMap<>(); query.put("userId",88L); query.put("viewAll",true);
            assertTrue(session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query).isEmpty());
            query.put("userId",8L);
            List<Map<String,Object>> rows = session.selectList("com.ruoyi.business.mapper.BusinessProjectMapper.testAwardPendingItems",query);
            assertEquals(1,rows.size()); assertEquals(2L,((Number)rows.get(0).get("AWARDID")).longValue());
        }
    }

    private SqlSessionFactory factory() throws Exception
    {
        DataSource source = new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:boss_award_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement())
        {
            com.ruoyi.business.CompanyAccessTestSupport.grant(connection,110L,8L);
            sql.execute("alter table sys_dept add column dept_name varchar(100)");
            sql.execute("create table biz_project(project_id bigint,project_name varchar(100),main_owner_name varchar(100),company_dept_id bigint,del_flag char(1),delivery_policy_version varchar(32),accounting_state varchar(20),status varchar(20))");
            sql.execute("create table biz_incentive_award(award_id bigint,project_id bigint,company_dept_id bigint,rule_name varchar(100),amount decimal(18,2),currency varchar(3),biz_date date,reason varchar(500),status varchar(20),applicant_user_id bigint,applicant_user_name varchar(100),create_time timestamp,update_time timestamp)");
            sql.execute("insert into biz_project values(1,'项目','负责人',110,'0','SEPARATED_V1','OPEN','ACTIVE'),(2,'其他公司项目','其他负责人',220,'0','SEPARATED_V1','OPEN','ACTIVE')");
            for (int id = 1; id <= 7; id++)
            {
                String status = new String[]{"DRAFT","SUBMITTED","APPROVED","RETURNED","CANCELED","SUBMITTED","SUBMITTED"}[id-1];
                sql.execute("insert into biz_incentive_award values("+id+","+(id==7?2:1)+","+(id==7?220:110)+",'奖金方案',10000,'CNY',current_date(),'申请依据','"+status+"',"+(id==6?8:9)+",'负责人',current_timestamp,current_timestamp)");
            }
        }
        Configuration configuration = new Configuration(new Environment("pending",new JdbcTransactionFactory(),source));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.business.domain");
        com.ruoyi.business.CompanyAccessTestSupport.register(configuration);
        String resource = "mapper/business/BusinessProjectMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        { new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse(); }
        // Execute the production pending-review fragment in isolation, without unrelated UNION fixtures.
        String probe = "<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'http://mybatis.org/dtd/mybatis-3-mapper.dtd'><mapper namespace='com.ruoyi.business.mapper.BusinessProjectMapper'><select id='testAwardPendingItems' resultType='map'><include refid='bossPendingIncentiveReview'/></select></mapper>";
        try (InputStream input = new ByteArrayInputStream(probe.getBytes(StandardCharsets.UTF_8)))
        { new XMLMapperBuilder(input,configuration,"boss-award-probe",configuration.getSqlFragments()).parse(); }
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
