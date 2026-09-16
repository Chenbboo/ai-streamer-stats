package com.ruoyi.business.mapper;

import java.sql.*;
import java.util.*;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Transactional fixtures in a schema-only local test database, never the preview database. */
@EnabledIfEnvironmentVariable(named="BOSS_REVIEW_TEST_DB",matches="boss_review_test_[0-9]+")
class BusinessBossReviewMysqlTest {
    SqlSession session; BusinessAccountingMapper mapper;
    static final long BOSS=-820100, OTHER=-820101;
    @BeforeEach void setup() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/"+System.getenv("BOSS_REVIEW_TEST_DB")+"?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        Configuration c=new Configuration(new Environment("boss-review-test",new JdbcTransactionFactory(),
            new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root",System.getenv("BOSS_REVIEW_TEST_PASSWORD"))));
        c.getTypeAliasRegistry().registerAliases("com.ruoyi.business.domain");
        String resource="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)){com.ruoyi.business.CompanyAccessTestSupport.register(c);new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(c).openSession(false);mapper=session.getMapper(BusinessAccountingMapper.class);
        project(-820001,BOSS,"MEMBER_DAYS_V1","TOTAL");
        project(-820002,BOSS,"MEMBER_DAYS_V1","DAILY");
        project(-820003,OTHER,"MEMBER_DAYS_V1","TOTAL");
        project(-820004,BOSS,"ACTUAL_WORK_V1","TOTAL");
    }
    @AfterEach void cleanup(){if(session!=null){session.rollback(true);session.close();}}
    void sql(String s)throws Exception{try(Statement st=session.getConnection().createStatement()){st.execute(s);}session.clearCache();}
    void project(long id,long owner,String policy,String budget)throws Exception{
        sql("insert into biz_project(project_id,project_no,project_name,company_dept_id,initiator_user_id,initiator_name,sponsor_owner_user_id,main_owner_user_id,main_owner_name,status,cost_policy_version,base_currency,budget_mode,budget_limit,daily_budget_limit) values("+id+",'review"+id+"','Review "+id+"',100,"+owner+",'Boss',"+owner+","+owner+",'Boss','ACTIVE','"+policy+"','CNY','"+budget+"',100,50)");
    }
    void result(long id,long project,String date,int revenue,int cost,int personnel,int budget)throws Exception{
        sql("insert into biz_project_daily_result(result_id,project_id,company_dept_id,biz_date,accounting_mode,revenue_amount,cost_amount,personnel_cost,profit_amount,budget_spent,data_cutoff_time,result_version,is_current) values("+id+","+project+",100,'"+date+"','PROFIT',"+revenue+","+cost+","+personnel+","+(revenue-cost-personnel)+","+budget+",'"+date+" 18:00:00',1,'1')");
    }
    void cost(long project,long user,String date,String status)throws Exception{
        sql("insert into biz_project_member_day_cost(project_id,user_id,biz_date,pricing_status,amount,currency) values("+project+","+user+",'"+date+"','"+status+"',"+(status.equals("PRICED")?"10":"null")+",'CNY')");
    }
    Map<String,Object> query(String date,long user,boolean all){Map<String,Object> q=new HashMap<>();q.put("dateFrom",date);q.put("dateTo",date);q.put("bizDate",date);q.put("userId",user);q.put("viewAll",all);return q;}
    long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    @Test void missingResultsUseHistoricalSourcesAndDoNotTreatWeekendAllocationsAsMemberWork()throws Exception{
        sql("update biz_project set status='CLOSED',actual_end_date='2026-09-12' where project_id=-820001");
        cost(-820001,1,"2026-09-11","PENDING");cost(-820003,2,"2026-09-11","PRICED");
        sql("insert into biz_project_staff_allocation(project_id,user_id,user_name,allocation_value,effective_from,status) values(-820002,3,'Member',100,'2026-09-01','ACTIVE')");
        assertEquals(1,mapper.countProjectsMissingDailyResult(BOSS,false,java.sql.Date.valueOf("2026-09-11")));
        assertEquals(2,mapper.countProjectsMissingDailyResult(BOSS,true,java.sql.Date.valueOf("2026-09-11")));
        assertEquals(0,mapper.countProjectsMissingDailyResult(BOSS,false,java.sql.Date.valueOf("2026-09-13")));
    }
    @Test void readinessScopesCostsAndDraftsToDateAndOwnerWithoutLosingHistoricalDrafts()throws Exception{
        result(-820010,-820001,"2026-09-13",0,0,0,0);
        cost(-820001,1,"2026-09-13","PENDING");cost(-820001,2,"2026-09-14","PENDING");cost(-820003,3,"2026-09-13","PENDING");
        for(int i=0;i<2;i++)sql("insert into biz_operating_fact(project_id,company_dept_id,biz_date,category_id,category_code,category_name,fact_kind,description,idempotency_key,create_user_id,status) values(-820001,100,'2026-09-"+(i==0?"12":"13")+"',1,'TEST','Test','COST','Fixture','review-draft-"+i+"',"+BOSS+",'DRAFT')");
        Map<String,Object> q=query("2026-09-13",BOSS,false),r=mapper.selectOverviewReadiness(q);
        assertEquals(1,number(r,"resultCount"));assertEquals(1,number(r,"pendingCostCount"));assertEquals(1,number(r,"unfinishedFactCount"));
        assertEquals(2,mapper.countDraftFacts(q));assertEquals(2,number(mapper.selectOverviewReadiness(query("2026-09-13",BOSS,true)),"pendingCostCount"));
        assertEquals(0,number(mapper.selectOverviewReadiness(query("2026-09-12",BOSS,false)),"pendingCostCount"));
        assertEquals(1,mapper.countPendingCostsInRange(q));
        Map<String,Object> allDates=query("2026-09-13",BOSS,false);allDates.remove("dateFrom");allDates.remove("dateTo");
        assertEquals(2,mapper.countPendingCostsInRange(allDates));
        q.put("projectId",-820002L);assertEquals(0,mapper.countPendingCostsInRange(q));
        q.remove("projectId");q.put("companyDeptId",999L);assertEquals(0,mapper.countPendingCostsInRange(q));
    }
    @Test void reportAndCurrentAlertsAreSeparatedAndCurrentBudgetUsesLatestAvailableResult()throws Exception{
        result(-820011,-820001,"2026-09-11",10,20,0,120);
        result(-820012,-820002,"2026-09-13",10,60,0,60);
        result(-820013,-820003,"2026-09-13",0,100,0,150);
        Map<String,Object> q=query("2026-09-13",BOSS,false);q.put("alertScope","PERIOD");
        List<Map<String,Object>> period=mapper.selectAccountingAlerts(q);
        assertEquals(2,period.size());assertTrue(period.stream().allMatch(r->number(r,"projectId")==-820002));
        q.put("bizDate","2026-09-14");q.put("alertScope","CURRENT");
        List<Map<String,Object>> current=mapper.selectAccountingAlerts(q);
        assertEquals(1,current.size());assertEquals(-820001,number(current.get(0),"projectId"));assertEquals("2026-09-11",current.get(0).get("bizDate").toString());
        Map<String,Object> legacy=query("2026-09-14",BOSS,false);assertTrue(mapper.selectAccountingAlerts(legacy).isEmpty());
    }
    @Test void summaryPreservesDateAndCurrencyBoundariesAndCurrentResultVersions()throws Exception{
        result(-820020,-820001,"2026-09-13",100,20,10,30);
        result(-820021,-820002,"2026-09-13",200,40,20,60);
        result(-820022,-820001,"2026-09-14",900,0,0,0);
        result(-820023,-820003,"2026-09-13",500,0,0,0);
        sql("update biz_project set base_currency='VND' where project_id=-820002");
        List<Map<String,Object>> groups=mapper.selectDailySummaryByCurrency(query("2026-09-13",BOSS,false));
        assertEquals(2,groups.size());assertEquals(100,number(groups.get(0),"revenueAmount"));assertEquals(70,number(groups.get(0),"profitAmount"));
        assertEquals(200,number(groups.get(1),"revenueAmount"));assertNull(mapper.selectDailySummary(query("2026-09-13",BOSS,false)).get("profitAmount"));
        assertEquals(3,number(mapper.selectDailySummary(query("2026-09-13",BOSS,true)),"projectCount"));
    }
    @Test void actualWorkMissingEntryAndUnpricedConfirmationRemainIncomplete()throws Exception{
        sql("insert into biz_project_resource_day(assignment_id,project_id,user_id,biz_date,time_zone,planned_minutes,capacity_minutes) values(-820030,-820004,1,'2026-09-13','Asia/Shanghai',60,480)");
        Map<String,Object> q=query("2026-09-13",BOSS,false);
        assertEquals(1,number(mapper.selectOverviewReadiness(q),"unfinishedWorkCount"));
        assertEquals(1,mapper.countProjectsMissingDailyResult(BOSS,false,java.sql.Date.valueOf("2026-09-13")));
        sql("insert into biz_project_work_entry(entry_id,project_id,user_id,biz_date,time_zone,activity,input_unit,input_quantity,work_minutes,calendar_id,calendar_snapshot_json,unit_policy_id,unit_snapshot_json,minutes_per_day,capacity_minutes,source_key,status,is_current,revision_no,create_user_id,create_by,create_time,update_time) values(-820031,-820004,1,'2026-09-13','Asia/Shanghai','Fixture','MINUTE',60,60,1,'{}',1,'{}',480,480,'review-work','CONFIRMED','1',1,1,'fixture',now(),now())");
        assertEquals(0,number(mapper.selectOverviewReadiness(q),"unfinishedWorkCount"));
        assertEquals(1,number(mapper.selectOverviewReadiness(q),"pendingCostCount"));
        assertEquals(0,number(mapper.selectOverviewReadiness(query("2026-09-14",BOSS,false)),"pendingCostCount"));
        assertEquals(0,number(mapper.selectOverviewReadiness(query("2026-09-13",OTHER,false)),"pendingCostCount"));
    }
}
