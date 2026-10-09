package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

class BusinessInternalProjectCostIntegrationTest
{
    @Test void legacyMonthlyPolicyUsesFixedDivisorInRealAccountingSql() throws Exception
    {
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:fixed_day_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement s=c.createStatement())
        {
            s.execute("create table biz_staff_cost_policy(user_id bigint,status varchar(20),cost_mode varchar(20),unit_cost decimal(18,4),standard_work_days decimal(8,2),country_region_snapshot varchar(10),effective_from date,effective_to date)");
            s.execute("create table biz_project_staff_allocation(project_id bigint,user_id bigint,status varchar(20),allocation_mode varchar(20),allocation_value decimal(8,4),effective_from date,effective_to date)");
            s.execute("create table biz_project_effort_report(project_id bigint,user_id bigint,biz_date date,report_status varchar(20),actual_percent decimal(8,4))");
            s.execute("create table biz_staff_leave(user_id bigint,leave_date date,status varchar(20),leave_id bigint)");
            s.execute("insert into biz_staff_cost_policy values(7,'ACTIVE','MONTHLY',3300,26,'CN','2026-01-01',null)");
            s.execute("insert into biz_project_staff_allocation values(1,7,'ACTIVE','PERCENTAGE',50,'2026-01-01',null)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        String path="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            com.ruoyi.business.CompanyAccessTestSupport.register(config);
            new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();
        }
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession())
        {
            BigDecimal cost=session.getMapper(BusinessAccountingMapper.class).sumProjectPersonnelCost(1L,java.sql.Date.valueOf("2026-10-01"));
            assertEquals(new BigDecimal("75.86"),cost.setScale(2,java.math.RoundingMode.HALF_UP));
            try(Statement s=session.getConnection().createStatement()) {
                s.execute("update biz_staff_cost_policy set country_region_snapshot='VN',standard_work_days=21.75");
            }
            session.clearCache();
            BigDecimal vietnam=session.getMapper(BusinessAccountingMapper.class).sumProjectPersonnelCost(1L,java.sql.Date.valueOf("2026-10-01"));
            assertEquals(new BigDecimal("63.46"),vietnam.setScale(2,java.math.RoundingMode.HALF_UP));
        }
    }
    @Test void internalExpensesRemainInAccountingTotalAndReversalsNetOut() throws Exception
    {
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:internal_cost_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement s=c.createStatement())
        {
            s.execute("create table biz_operating_fact(project_id bigint,biz_date date,status varchar(20),fact_kind varchar(20),category_code varchar(40),amount decimal(18,2),quantity decimal(18,2))");
            s.execute("insert into biz_operating_fact values "
                +"(1,'2026-09-27','CONFIRMED','COST','OTHER_EXPENSE',100,null),"
                +"(1,'2026-09-27','REVERSED','COST','INTERNAL_PROJECT_COST',50,null),"
                +"(1,'2026-09-27','CONFIRMED','COST','INTERNAL_PROJECT_COST',-50,null),"
                +"(1,'2026-09-27','CONFIRMED','COST','INTERNAL_PROJECT_COST',30,null),"
                +"(1,'2026-09-27','DRAFT','COST','INTERNAL_PROJECT_COST',500,null),"
                +"(1,'2026-09-27','CONFIRMED','COST','PROJECT_BONUS_COST',20,null),"
                +"(1,'2026-09-27','CONFIRMED','COST','COMPANY_PUBLIC_COST',10,null),"
                +"(2,'2026-09-27','CONFIRMED','COST','INTERNAL_PROJECT_COST',900,null),"
                +"(1,'2026-09-28','CONFIRMED','COST','INTERNAL_PROJECT_COST',800,null)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        String path="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            com.ruoyi.business.CompanyAccessTestSupport.register(config);
            new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();
        }
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession())
        {
            Map<String,Object> sums=session.getMapper(BusinessAccountingMapper.class)
                .sumProjectFacts(1L,java.sql.Date.valueOf("2026-09-27"));
            assertEquals(new BigDecimal("130.00"),sums.get("costAmount"));
            assertEquals(new BigDecimal("30.00"),sums.get("internalProjectCost"));
            assertEquals(new BigDecimal("20.00"),sums.get("bonusCost"));
            assertEquals(new BigDecimal("10.00"),sums.get("publicCost"));
        }
    }
}
