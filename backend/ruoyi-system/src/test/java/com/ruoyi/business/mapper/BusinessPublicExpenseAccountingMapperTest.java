package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/** Executes the production aggregate SQL against actual facts and monthly allocations. */
class BusinessPublicExpenseAccountingMapperTest {
    JdbcDataSource source;
    SqlSessionFactory sessions;
    @BeforeEach void setup() throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:public_cost_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        sql("create table biz_project(project_id bigint primary key,project_name varchar(50),company_dept_id bigint,base_currency varchar(3),sponsor_owner_user_id bigint,initiator_user_id bigint,main_owner_user_id bigint,plan_start_date date,actual_end_date date,del_flag char(1))",
            "create table biz_operating_fact(project_id bigint,biz_date date,fact_kind varchar(20),category_code varchar(40),amount decimal(20,2),quantity decimal(20,2),status varchar(20))",
            "create table biz_project_daily_result(project_id bigint,is_current char(1),company_dept_id bigint,biz_date date,revenue_amount decimal(20,2),cost_amount decimal(20,2),personnel_cost decimal(20,2),bonus_cost decimal(20,2),public_cost decimal(20,2),adjustment_amount decimal(20,2),profit_amount decimal(20,2))",
            "create table biz_public_expense_policy(company_dept_id bigint,currency varchar(3),start_month varchar(7),end_month varchar(7),status varchar(20))",
            "create table biz_public_expense_month(bill_id bigint,company_dept_id bigint,bill_month varchar(7),currency varchar(3),status varchar(20))",
            "create table biz_public_expense_owner(allocation_id bigint,bill_id bigint,owner_user_id bigint,status varchar(20))",
            "create table biz_public_expense_project(allocation_id bigint,project_id bigint,amount decimal(20,2))",
            "create table biz_public_expense_adjustment(bill_id bigint,project_id bigint,amount decimal(20,2))",
            "insert into biz_project values(1,'A',110,'CNY',8,8,9,'2026-08-01',null,'0'),(2,'B',111,'CNY',10,10,11,'2026-08-01',null,'0')",
            "insert into biz_public_expense_month values(1,110,'2026-08','CNY','SETTLED'),(2,111,'2026-08','CNY','PUBLISHED')",
            "insert into biz_public_expense_owner values(1,1,9,'SUBMITTED'),(2,2,11,'DRAFT')",
            "insert into biz_public_expense_project values(1,1,10080),(2,2,4320)",
            "insert into biz_operating_fact values(1,'2026-08-31','REVENUE','REVENUE',20000,null,'CONFIRMED'),(1,'2026-08-31','COST','OTHER_EXPENSE',200,null,'CONFIRMED'),(1,'2026-08-31','COST','PROJECT_BONUS_COST',50,null,'CONFIRMED'),(1,'2026-08-31','COST','COMPANY_PUBLIC_COST',10080,null,'CONFIRMED')",
            "insert into biz_project_daily_result values(1,'1',110,'2026-08-31',20000,200,300,50,10080,0,9370)");
        sql("alter table biz_project add column actual_start_date date",
            "alter table biz_public_expense_month add column total_amount decimal(20,2) default 14400",
            "alter table biz_public_expense_owner add column amount decimal(20,2) default 14400",
            "alter table biz_public_expense_policy add column amount decimal(20,2) default 24000");
        try(java.sql.Connection grantConnection=source.getConnection()){com.ruoyi.business.CompanyAccessTestSupport.grant(grantConnection,110L,8L);com.ruoyi.business.CompanyAccessTestSupport.grant(grantConnection,111L,10L);}
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        String path="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path)){com.ruoyi.business.CompanyAccessTestSupport.register(config);new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        path="mapper/business/BusinessProjectManagementFeeMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path)){com.ruoyi.business.CompanyAccessTestSupport.register(config);new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        sessions=new SqlSessionFactoryBuilder().build(config);
    }

    @Test void factsSplitPublicCostsAndMonthlySummaryConservesTotal() {
        try(SqlSession session=sessions.openSession()){
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            Map<String,Object> fact=mapper.sumProjectFacts(1L,java.sql.Date.valueOf("2026-08-31"));
            assertMoney("10080",fact.get("publicCost"));assertMoney("200",fact.get("costAmount"));assertMoney("50",fact.get("bonusCost"));
            Map<String,Object> query=query();query.put("dateFrom","2026-08-01");
            Map<String,Object> total=mapper.selectDailySummary(query);
            assertMoney("10630",total.get("costAmount"));assertMoney("10080",total.get("publicCost"));assertMoney("9370",total.get("profitAmount"));
            assertMoney("10630",mapper.selectDailySummaryByCurrency(query).get(0).get("costAmount"));
        }
    }

    @Test void budgetIncludesAccruedPublicCostBeforeAndAfterMonthlyConfirmation() throws Exception {
        sql("delete from biz_operating_fact where category_code='COMPANY_PUBLIC_COST'");
        assertBudget();
        sql("insert into biz_operating_fact values(1,'2026-08-31','COST','COMPANY_PUBLIC_COST',10080,null,'CONFIRMED')");
        assertBudget();
    }
    private void assertBudget() {
        try(SqlSession session=sessions.openSession()) {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            assertMoney("10630",mapper.sumProjectCostToDate(1L,java.sql.Date.valueOf("2026-08-31")));
            assertMoney("10630",mapper.sumProjectCostInPeriod(1L,java.sql.Date.valueOf("2026-08-01"),java.sql.Date.valueOf("2026-08-31")));
        }
    }
    @Test void referenceUsesMonthlyAllocationAndRespectsBossScope() {
        try(SqlSession session=sessions.openSession()){
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            List<Map<String,Object>> rows=mapper.selectPublicExpenseReferences(query());assertEquals(1,rows.size());
            assertMoney("463.45",rows.get(0).get("dailyReference"));assertMoney("10080",rows.get(0).get("recognizedPublicCost"));
            Map<String,Object> other=query();other.put("userId",10L);rows=mapper.selectPublicExpenseReferences(other);
            assertEquals(1,rows.size());assertEquals(2L,((Number)rows.get(0).get("projectId")).longValue());
            assertEquals(1,((Number)rows.get(0).get("pendingCount")).intValue());
        }
    }

    @Test void configuredButUngeneratedMonthRemainsPending() throws Exception {
        sql("insert into biz_public_expense_policy values(110,'CNY','2026-09','2027-08','ACTIVE',24000)");
        try(SqlSession session=sessions.openSession()){
            Map<String,Object> query=query();query.put("dateFrom","2026-09-01");query.put("dateTo","2026-09-01");
            List<Map<String,Object>> rows=session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query);
            assertEquals(1,rows.size());assertEquals(1,((Number)rows.get(0).get("pendingCount")).intValue());
        }
    }

    @Test void adjustmentChangesDailyReferenceWithoutOverwritingOriginalAllocation() throws Exception {
        sql("insert into biz_public_expense_adjustment values(1,1,-80)");
        try(SqlSession session=sessions.openSession()){
            Map<String,Object> row=session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query()).get(0);
            assertMoney("10000",row.get("monthAmount"));assertMoney("459.77",row.get("dailyReference"));
        }
        try(Connection c=source.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select amount from biz_public_expense_project where project_id=1")){
            assertTrue(r.next());assertMoney("10080",r.getBigDecimal(1));
        }
    }

    @Test void managementFeeProfitBasisIncludesPublicCostsExactlyOnce() {
        try(SqlSession session=sessions.openSession()){
            Map<String,Object> basis=session.getMapper(BusinessProjectManagementFeeMapper.class).selectLifetimeBasis(1L);
            assertMoney("10280",basis.get("basisBusinessCost"));assertMoney("50",basis.get("basisBonusCost"));
            assertMoney("300",basis.get("basisPersonnelCost"));
        }
    }

    @Test void managementFeeEstimateIncludesDailyCostsBeforeMonthClose() throws Exception {
        sql("delete from biz_operating_fact where category_code='COMPANY_PUBLIC_COST'");
        try(SqlSession session=sessions.openSession()) {
            assertMoney("10280",session.getMapper(BusinessProjectManagementFeeMapper.class).selectLifetimeBasis(1L).get("basisBusinessCost"));
        }
    }

    @Test void actualStartControlsDailyReferencesWhenItDiffersFromThePlan() throws Exception {
        sql("update biz_project set plan_start_date='2026-10-01',actual_start_date='2026-08-01' where project_id=1");
        try(SqlSession session=sessions.openSession()){
            assertEquals(1,session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query()).size());
        }
        sql("update biz_project set plan_start_date='2026-08-01',actual_start_date='2026-09-01' where project_id=1");
        try(SqlSession session=sessions.openSession()){
            assertTrue(session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query()).isEmpty());
        }
    }

    @Test void zeroShareDoesNotKeepPublishedOrSettledMonthIncomplete() throws Exception {
        sql("update biz_public_expense_owner set amount=0,status='DRAFT' where allocation_id=1");
        try(SqlSession session=sessions.openSession()){
            assertEquals(0,((Number)session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query()).get(0).get("pendingCount")).intValue());
        }
        sql("update biz_public_expense_month set status='PUBLISHED' where bill_id=1");
        try(SqlSession session=sessions.openSession()){
            assertEquals(0,((Number)session.getMapper(BusinessAccountingMapper.class).selectPublicExpenseReferences(query()).get(0).get("pendingCount")).intValue());
        }
    }
    private Map<String,Object> query(){Map<String,Object> q=new HashMap<>();q.put("viewAll",false);q.put("userId",8L);q.put("dateFrom","2026-08-31");q.put("dateTo","2026-08-31");return q;}
    private void sql(String... statements) throws Exception {try(Connection c=source.getConnection();Statement s=c.createStatement()){for(String statement:statements)s.execute(statement);}}
    private void assertMoney(String expected,Object actual){assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)actual));}
}
