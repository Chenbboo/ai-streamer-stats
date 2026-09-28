package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import com.ruoyi.business.domain.BusinessOperatingFact;

class BusinessInternalTransferMapperIntegrationTest
{
    SqlSessionFactory sessions;
    java.sql.Date date=java.sql.Date.valueOf("2026-09-28");

    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:internal_transfer_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement s=c.createStatement())
        {
            s.execute("create table biz_project(project_id bigint primary key,project_name varchar(160),base_currency varchar(3),company_dept_id bigint,status varchar(24),actual_start_date date,actual_end_date date,delivery_policy_version varchar(32),accounting_state varchar(24),del_flag varchar(1))");
            s.execute("insert into biz_project values(1,'支出项目','CNY',110,'ACTIVE','2026-09-01',null,'SEPARATED_V1','OPEN','0'),(2,'接收项目','CNY',110,'ACTIVE','2026-09-01',null,'SEPARATED_V1','OPEN','0'),(3,'越南项目','VND',111,'ACTIVE','2026-09-01',null,'SEPARATED_V1','OPEN','0'),(4,'已关账项目','CNY',110,'CLOSED','2026-09-01','2026-09-20','SEPARATED_V1','CLOSED','0'),(5,'已删除项目','CNY',110,'ACTIVE','2026-09-01',null,'SEPARATED_V1','OPEN','1')");
            s.execute("create table biz_fact_category(category_id bigint primary key,category_code varchar(40),category_name varchar(100),fact_kind varchar(20),default_sign int,unit_type varchar(20),status varchar(1),sort_order int)");
            s.execute("insert into biz_fact_category values(6,'INTERNAL_PROJECT_COST','内部项目支出','COST',-1,'MONEY','0',180),(7,'INTERNAL_PROJECT_REVENUE','内部项目收入','REVENUE',1,'MONEY','0',75),(8,'SALES_REVENUE','销售收入','REVENUE',1,'MONEY','0',10)");
            s.execute("create table biz_operating_fact(fact_id bigint auto_increment primary key,project_id bigint,target_project_id bigint,company_dept_id bigint,biz_date date,category_id bigint,category_code varchar(40),category_name varchar(100),fact_kind varchar(20),amount decimal(20,2),quantity decimal(20,2),currency varchar(3),unit varchar(30),description varchar(500),counterparty varchar(200),attachment_urls varchar(500),source_domain varchar(32),source_type varchar(32),source_id varchar(100),source_line_key varchar(100),status varchar(24),reversal_fact_id bigint,idempotency_key varchar(160) unique,version int,confirmed_user_id bigint,confirmed_user_name varchar(100),confirmed_time timestamp,returned_user_id bigint,returned_user_name varchar(100),returned_time timestamp,return_reason varchar(500),create_user_id bigint,create_by varchar(100),create_time timestamp,update_by varchar(100),update_time timestamp,remark varchar(500))");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        String path="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            com.ruoyi.business.CompanyAccessTestSupport.register(config);
            new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();
        }
        sessions=new SqlSessionFactoryBuilder().build(config);
    }

    @Test void directoryListsAllUndeletedProjectsButDoesNotExposeAccountingOrPersonnel() throws Exception
    {
        try(SqlSession session=sessions.openSession())
        {
            List<Map<String,Object>> options=session.getMapper(BusinessAccountingMapper.class).selectInternalTransferProjects();
            assertEquals(4,options.size());
            assertTrue(options.stream().anyMatch(p->Long.valueOf(3).equals(p.get("projectId"))));
            assertEquals(1,((Number)options.stream().filter(p->Long.valueOf(4).equals(p.get("projectId"))).findFirst().orElseThrow(NoSuchElementException::new).get("accountingClosed")).intValue());
            for(Map<String,Object> option:options){assertFalse(option.containsKey("budgetLimit"));assertFalse(option.containsKey("initiatorUserId"));}
            List<Map<String,Object>> categories=session.getMapper(BusinessAccountingMapper.class).selectCategories();
            assertEquals(2,categories.size());assertTrue(categories.stream().noneMatch(c->"INTERNAL_PROJECT_REVENUE".equals(c.get("categoryCode"))));
        }
    }

    @Test void recipientSurvivesPersistenceAndIncomeAppearsInRevenueSummary() throws Exception
    {
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            BusinessOperatingFact expense=fact(1L,"COST","INTERNAL_PROJECT_COST","expense");
            expense.setTargetProjectId(2L);expense.setSourceDomain("PROJECT_DAILY");expense.setSourceType("DAILY_ITEM");
            mapper.insertFact(expense);
            BusinessOperatingFact income=fact(2L,"REVENUE","INTERNAL_PROJECT_REVENUE","income");
            income.setSourceDomain("INTERNAL_PROJECT");income.setSourceType("TRANSFER_REVENUE");income.setSourceId(String.valueOf(expense.getFactId()));
            mapper.insertFact(income);session.commit();
            assertEquals(2L,mapper.selectFactById(expense.getFactId()).getTargetProjectId());
            assertEquals(2L,mapper.selectFactByIdForUpdate(expense.getFactId()).getTargetProjectId());
            assertEquals(2L,mapper.selectFactByIdempotencyKey("expense").getTargetProjectId());
            assertEquals(2L,mapper.selectProjectDailySpendItems(1L,date).get(0).getTargetProjectId());
            assertEquals(new BigDecimal("50.00"),mapper.selectProjectRevenueSummary(2L,date).get("confirmedAmount"));
            assertEquals(new BigDecimal("50.00"),mapper.sumProjectFacts(1L,date).get("internalProjectCost"));
            assertEquals(new BigDecimal("50.00"),mapper.sumProjectFacts(2L,date).get("revenueAmount"));
            List<Map<String,Object>> details=mapper.selectProjectInternalRevenueItems(2L,date);
            assertEquals(1,details.size());
            assertEquals(1L,details.get(0).get("sourceProjectId"));
            assertEquals("支出项目",details.get(0).get("sourceProjectName"));
            assertEquals("内部协作",details.get(0).get("description"));
            assertEquals(new BigDecimal("50.00"),details.get(0).get("amount"));
        }
    }

    @Test void internalIncomeDetailsExcludeReversedDraftOtherDayAndUnrelatedRecipients() throws Exception
    {
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            for(String scenario:Arrays.asList("valid","reversedExpense","reversedIncome","draftIncome","otherDay","otherProject","wrongRecipient","manualIncome"))
            {
                BusinessOperatingFact expense=fact(1L,"COST","INTERNAL_PROJECT_COST","expense-"+scenario);
                expense.setTargetProjectId("otherProject".equals(scenario)||"wrongRecipient".equals(scenario)?3L:2L);
                expense.setSourceDomain("PROJECT_DAILY");expense.setSourceType("DAILY_ITEM");
                if("reversedExpense".equals(scenario))expense.setStatus("REVERSED");
                mapper.insertFact(expense);
                BusinessOperatingFact income=fact("otherProject".equals(scenario)?3L:2L,"REVENUE","INTERNAL_PROJECT_REVENUE","income-"+scenario);
                income.setSourceDomain("manualIncome".equals(scenario)?"MANUAL":"INTERNAL_PROJECT");
                income.setSourceType("TRANSFER_REVENUE");income.setSourceId(String.valueOf(expense.getFactId()));
                if("reversedIncome".equals(scenario))income.setStatus("REVERSED");
                if("draftIncome".equals(scenario))income.setStatus("DRAFT");
                if("otherDay".equals(scenario))income.setBizDate(java.sql.Date.valueOf("2026-09-27"));
                mapper.insertFact(income);
            }
            session.commit();
            List<Map<String,Object>> details=mapper.selectProjectInternalRevenueItems(2L,date);
            assertEquals(1,details.size());assertEquals(new BigDecimal("50.00"),details.get(0).get("amount"));
        }
    }

    @Test void transactionRollbackDoesNotLeaveOneSidedAccountingFacts() throws Exception
    {
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            mapper.insertFact(fact(1L,"COST","INTERNAL_PROJECT_COST","cost-rollback"));
            mapper.insertFact(fact(2L,"REVENUE","INTERNAL_PROJECT_REVENUE","revenue-rollback"));
            session.rollback();
        }
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            assertNull(mapper.selectFactByIdempotencyKey("cost-rollback"));assertNull(mapper.selectFactByIdempotencyKey("revenue-rollback"));
        }
    }

    private BusinessOperatingFact fact(Long id,String kind,String code,String key)
    {
        BusinessOperatingFact f=new BusinessOperatingFact();f.setProjectId(id);f.setCompanyDeptId(110L);f.setBizDate(date);
        f.setCategoryId("COST".equals(kind)?6L:7L);f.setCategoryCode(code);f.setCategoryName(code);f.setFactKind(kind);
        f.setAmount(new BigDecimal("50.00"));f.setCurrency("CNY");f.setDescription("内部协作");f.setStatus("CONFIRMED");f.setIdempotencyKey(key);return f;
    }
}
