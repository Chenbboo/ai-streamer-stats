package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.domain.BusinessProject;

/** Executes the shipped work mapper and additive migration tables, rather than a SQL imitation. */
class BusinessProjectWorkMapperIntegrationTest
{
    @Test void budgetRateRangeAndRenewedSnapshotUseProductionSql() throws Exception
    {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement())
        {
            sql.execute("insert into biz_staff_cost_policy(policy_id,user_id,policy_version,cost_mode,unit_cost,currency,effective_from,effective_to,status) values(101,30,1,'HOURLY',100,'CNY','2026-09-01','2026-09-15','ACTIVE'),(102,30,2,'HOURLY',200,'CNY','2026-09-16',null,'ACTIVE'),(103,30,3,'HOURLY',300,'CNY','2026-09-01',null,'VOID')");
            BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);
            assertEquals(2,m.selectBudgetRates(30L,"2026-09-01","2026-09-30").size());assertEquals(1,m.selectBudgetRates(30L,"2026-10-01","2026-10-31").size());
            String snapshot="{\"budget\":{\"cycle\":\"MONTH\",\"totalAmount\":1000}}";
            assertEquals(1,m.applyPlanChange(row("projectId",1L,"baseVersion",1,"projectVersion",0,"objective","目标","acceptanceCriteria","交付","planStartDate","2026-09-01","planEndDate",null,"budgetLimit",1000,"templateSnapshotJson",snapshot,"userName","owner")));
            try(ResultSet result=sql.executeQuery("select budget_limit,template_snapshot_json from biz_project where project_id=1")){assertTrue(result.next());assertEquals(new BigDecimal("1000.00"),result.getBigDecimal(1));assertEquals(snapshot,result.getString(2));}
        }
    }
    DataSource source;SqlSessionFactory factory;
    @BeforeEach void setup()throws Exception
    {
        source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:p2_"+UUID.randomUUID().toString().replace("-","")+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Path migration=Paths.get("../sql/migrations/V064__project_actual_work_and_templates.sql");if(!Files.exists(migration))migration=Paths.get("sql/migrations/V064__project_actual_work_and_templates.sql");
        String sql=new String(Files.readAllBytes(migration),StandardCharsets.UTF_8);Matcher create=Pattern.compile("(?is)create table if not exists .*?;").matcher(sql);
        try(Connection c=source.getConnection();Statement s=c.createStatement())
        {
            while(create.find())s.execute(create.group());
            s.execute("alter table biz_project_resource_assignment modify effective_to date null");
            s.execute("create table sys_user(user_id bigint primary key,nick_name varchar(80),del_flag char(1))");
            s.execute("insert into sys_user values(30,'member','0')");
            s.execute("create table biz_project(project_id bigint primary key,actual_end_date date,objective varchar(1000),plan_start_date date,plan_end_date date,acceptance_criteria varchar(2000),budget_limit decimal(20,2),baseline_version int,version int,accounting_state varchar(16),status varchar(16),update_by varchar(64),update_time timestamp)");
            s.execute("insert into biz_project(project_id,baseline_version,version,accounting_state,status) values(1,1,0,'OPEN','ACTIVE')");
            s.execute("create table biz_project_member(member_id bigint primary key,project_id bigint,user_id bigint,user_name_snapshot varchar(80),member_role varchar(16),joined_date date,left_date date,status char(1))");
            s.execute("insert into biz_project_member values(1,1,30,'member','MEMBER','2026-01-01',null,'0')");
            s.execute("create table biz_staff_cost_policy(policy_id bigint primary key,user_id bigint,policy_version int,cost_mode varchar(16),unit_cost decimal(20,4),standard_work_days decimal(8,2),rate_minutes_per_day int default 480,currency varchar(3),effective_from date,effective_to date,status varchar(16))");
            s.execute("alter table biz_staff_cost_policy modify policy_id bigint auto_increment");
            for(String column:Arrays.asList("country_region_snapshot varchar(16)","create_by varchar(64)","create_time timestamp","remark varchar(500)","voided_user_id bigint","voided_user_name varchar(80)","voided_time timestamp","void_reason varchar(500)"))s.execute("alter table biz_staff_cost_policy add "+column);
            s.execute("create table biz_project_staff_allocation(cost_policy_id bigint)");
            s.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(80))");
            for(String column:Arrays.asList("project_name varchar(160)","company_dept_id bigint","actual_start_date date","cost_policy_version varchar(32)","base_currency varchar(3)","del_flag char(1)","sponsor_owner_user_id bigint","initiator_user_id bigint"))s.execute("alter table biz_project add "+column);
            s.execute("update biz_project set project_name='sample',cost_policy_version='ACTUAL_WORK_V1',base_currency='CNY',del_flag='0'");
            s.execute("alter table biz_project modify project_id bigint auto_increment");
            for(String column:Arrays.asList("goal_mode varchar(16)","budget_mode varchar(16)","daily_budget_limit decimal(20,2)","budget_scope varchar(24)","startup_budget_limit decimal(20,2)","budget_reason varchar(500)","project_no varchar(80)","parent_id bigint","project_type varchar(32)","accounting_mode varchar(32)","management_mode varchar(32)","close_method varchar(32)","management_reason varchar(1000)","delivery_policy_version varchar(32)","settlement_policy_version varchar(32)","template_version varchar(32)","template_snapshot_json clob","baseline_status varchar(32)","applicant_user_id bigint","applicant_name varchar(80)","sponsor_owner_name varchar(80)","initiator_name varchar(80)","main_owner_user_id bigint","main_owner_name varchar(80)","source_proposal_id bigint","priority varchar(16)","create_by varchar(64)","create_time timestamp","remark varchar(2000)"))s.execute("alter table biz_project add "+column);
        }
        Configuration config=new Configuration(new Environment("p2",new JdbcTransactionFactory(),source));config.setMapUnderscoreToCamelCase(true);
        for(String name:Arrays.asList("BusinessProjectWorkMapper","BusinessProjectMapper")){String resource="mapper/business/"+name+".xml";try(InputStream in=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}}factory=new SqlSessionFactoryBuilder().build(config);
    }
    @Test void unknownActualCannotProduceCostAndPendingWorkBlocksClose()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);assertEquals(0,m.countPendingCosts(1L));assertTrue(m.selectWorkCosts(1L,java.sql.Date.valueOf("2026-03-02")).isEmpty());Map<String,Object> e=entry();m.insertEntry(e);assertEquals(1,m.countPendingWork(1L));assertEquals(0,m.countPendingCosts(1L));assertEquals(1,m.countMembership(1L,30L,"2026-03-02"));}
    }
    @Test void confirmedVersionsAreUniqueInCostReadAndCorrectionsDoNotDoubleCount()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);Map<String,Object> original=entry();m.insertEntry(original);Long old=((Number)original.get("entryId")).longValue();transition(m,old,"DRAFT","SUBMITTED",0);transition(m,old,"SUBMITTED","CONFIRMED",1);Map<String,Object> cost=row("entryId",old,"projectId",1L,"userId",30L,"bizDate","2026-03-02","pricingStatus","PRICED","amount",new BigDecimal("100.00"),"currency","CNY","basisJson","{\"revision\":1}");m.upsertWorkCost(cost);
            Map<String,Object> correction=entry();correction.put("sourceKey","correction");correction.put("logicalEntryId",old);correction.put("parentEntryId",old);correction.put("revisionNo",2);correction.put("workMinutes",120);m.insertEntry(correction);Long current=((Number)correction.get("entryId")).longValue();
            assertEquals(1,m.selectWorkCosts(1L,java.sql.Date.valueOf("2026-03-02")).size());transition(m,current,"DRAFT","SUBMITTED",0);assertEquals(1,m.supersedeEntry(old,2,"owner"));transition(m,current,"SUBMITTED","CONFIRMED",1);assertEquals(1,m.countPendingCosts(1L));cost.put("entryId",current);cost.put("amount",new BigDecimal("50.00"));m.upsertWorkCost(cost);assertEquals(0,m.countPendingCosts(1L));List<Map<String,Object>> costs=m.selectWorkCosts(1L,java.sql.Date.valueOf("2026-03-02"));assertEquals(1,costs.size());assertEquals(new BigDecimal("50.00"),value(costs.get(0),"amount"));
        }
    }
    @Test void eventSourceKeyMakesRepeatedPublicationIdempotent()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);m.insertEvent(row("entryId",99L,"projectId",1L));m.insertEvent(row("entryId",99L,"projectId",1L));assertEquals(1,m.selectPendingEvents().size());}
    }
    @Test void reservationsCountCurrentAndSubmittedPerLogicalRecordOnlyOnce()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);Map<String,Object> e=entry();m.insertEntry(e);Long id=((Number)e.get("entryId")).longValue();transition(m,id,"DRAFT","SUBMITTED",0);transition(m,id,"SUBMITTED","CONFIRMED",1);Map<String,Object> revision=entry();revision.put("sourceKey","revision");revision.put("logicalEntryId",id);revision.put("parentEntryId",id);revision.put("revisionNo",2);revision.put("workMinutes",120);m.insertEntry(revision);transition(m,((Number)revision.get("entryId")).longValue(),"DRAFT","SUBMITTED",0);assertEquals(240,m.sumReservedMinutes(30L,"2026-03-02",999L));assertEquals(0,m.sumReservedMinutes(30L,"2026-03-02",id));}
    }
    @Test void draftRevisionPreservesAuditAndCalendarVersionsAreAppendOnly()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);Map<String,Object> c=row("calendarName","six hours","timeZone","Asia/Shanghai","workingWeekdays","1,2,3,4,5","dailyMinutes",360,"exceptionsJson","[{\"bizDate\":\"2026-03-03\",\"minutes\":0}]","effectiveFrom","2026-01-01","userName","admin");m.insertCalendar(c);assertNotNull(value(m.selectCalendar(((Number)c.get("calendarId")).longValue()),"exceptionsJson"));Map<String,Object> e=entry();m.insertEntry(e);e.put("action","EDIT");e.put("snapshotJson","{\"workMinutes\":240}");m.insertAudit(e);e.put("workMinutes",120);e.put("inputQuantity",new BigDecimal("0.25"));e.put("version",0);assertEquals(1,m.updateDraftEntry(e));assertEquals(1,m.selectAudit(((Number)e.get("entryId")).longValue()).size());assertEquals(120,((Number)value(m.selectEntry(((Number)e.get("entryId")).longValue()),"workMinutes")).intValue());}
    }
    @Test void changingPlanAppendsBaselineAndDoesNotModifyPreviousSnapshot()throws Exception
    {
        try(SqlSession session=factory.openSession()){BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);Map<String,Object> b=row("projectId",1L,"baselineVersion",1,"templateVersion","LIGHT_V1","snapshotJson","{\"scope\":\"original\"}","authorizationSource","SELF_AUTHORIZED","userId",10L,"userName","owner");m.insertBaseline(b);Map<String,Object> proposed=row("projectId",1L,"baseVersion",1,"projectVersion",0,"objective","new scope","planStartDate","2026-01-01","planEndDate","2026-03-01","acceptanceCriteria","criteria","userName","owner");assertEquals(1,m.applyPlanChange(proposed));b.put("baselineVersion",2);b.put("snapshotJson","{\"scope\":\"new\"}");m.insertBaseline(b);assertEquals(2,m.selectBaselines(1L).size());assertEquals("{\"scope\":\"original\"}",value(m.selectBaselines(1L).get(1),"snapshotJson"));assertEquals(0,m.applyPlanChange(proposed));}
    }
    @Test void rateModesRoundTripTheirIndependentDenominatorsThroughProductionInsertAndSelect()throws Exception
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectMapper m=session.getMapper(BusinessProjectMapper.class);
            for(String mode:Arrays.asList("MONTHLY","DAILY","HOURLY"))
            {
                BusinessStaffCostPolicy policy=new BusinessStaffCostPolicy();policy.setUserId(30L);policy.setCostMode(mode);policy.setUnitCost(new BigDecimal("123.4567"));policy.setCurrency("USD");policy.setCountryRegion("OTHER");policy.setRateMinutesPerDay(360);policy.setStandardWorkDays("MONTHLY".equals(mode)?new BigDecimal("22.50"):null);policy.setEffectiveFrom(java.sql.Date.valueOf("2026-03-01"));policy.setPolicyVersion(m.selectNextStaffCostVersion(30L));policy.setStatus("ACTIVE");policy.setCreateBy("finance");
                assertEquals(1,m.insertStaffCostPolicy(policy));BusinessStaffCostPolicy stored=m.selectStaffCostPolicyById(policy.getPolicyId());
                assertEquals(mode,stored.getCostMode());assertEquals(new BigDecimal("123.4567"),stored.getUnitCost());assertEquals(Integer.valueOf(360),stored.getRateMinutesPerDay());assertEquals(policy.getStandardWorkDays(),stored.getStandardWorkDays());assertEquals("USD",stored.getCurrency());
            }
            assertEquals(3,m.selectStaffCostPolicies(30L).size());
        }
    }
    @Test void actualOverviewNeverLabelsMissingOrUnpricedWorkAsCalculatedZero()throws Exception
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);Map<String,Object> query=row("bizDate","2026-03-02","viewAll",true);
            Map<String,Object> result=m.selectPersonnelCostOverview(query).get(0);assertEquals("MISSING_ACTUAL",value(result,"costStatus"));assertNull(value(result,"personnelCost"));
            Map<String,Object> e=entry();m.insertEntry(e);Long id=((Number)e.get("entryId")).longValue();transition(m,id,"DRAFT","SUBMITTED",0);transition(m,id,"SUBMITTED","CONFIRMED",1);m.insertEvent(row("entryId",id,"projectId",1L));
            result=m.selectPersonnelCostOverview(query).get(0);assertEquals("PENDING_COST",value(result,"costStatus"));assertEquals(240,((Number)value(result,"workMinutes")).intValue());assertNull(value(result,"personnelCost"));
            m.upsertWorkCost(row("entryId",id,"projectId",1L,"userId",30L,"bizDate","2026-03-02","pricingStatus","PRICED","amount",new BigDecimal("100.00"),"currency","CNY","basisJson","{}"));
            result=m.selectPersonnelCostOverview(query).get(0);assertEquals("PENDING_COST",value(result,"costStatus"));
            Map<String,Object> event=m.selectPendingProjectEvents(1L).get(0);m.finishEvent(row("eventId",value(event,"eventId"),"status","DONE"));
            result=m.selectPersonnelCostOverview(query).get(0);assertEquals("READY",value(result,"costStatus"));assertEquals(new BigDecimal("100.00"),value(result,"personnelCost"));
        }
    }
    @Test void actualProjectInsertFirstBaselineAndFirstChangeAdvanceTogether()throws Exception
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectMapper projects=session.getMapper(BusinessProjectMapper.class);BusinessProjectWorkMapper work=session.getMapper(BusinessProjectWorkMapper.class);
            BusinessProject p=new BusinessProject();p.setProjectNo("standard-2");p.setProjectName("standard");p.setCostPolicyVersion("ACTUAL_WORK_V1");p.setTemplateVersion("LIGHT_V1");p.setBaselineVersion(1);p.setStatus("ACTIVE");projects.insertProject(p);
            BusinessProject stored=projects.selectProjectByIdForUpdate(p.getProjectId());assertEquals(Integer.valueOf(1),stored.getBaselineVersion());
            Map<String,Object> b=row("projectId",p.getProjectId(),"baselineVersion",stored.getBaselineVersion(),"templateVersion","LIGHT_V1","snapshotJson","{}","authorizationSource","SELF_AUTHORIZED","userId",10L,"userName","owner");work.insertBaseline(b);
            Map<String,Object> change=row("projectId",p.getProjectId(),"baseVersion",stored.getBaselineVersion(),"projectVersion",stored.getVersion(),"objective","updated","planStartDate","2026-01-01","planEndDate","2026-03-01","acceptanceCriteria","result","userName","owner");assertEquals(1,work.applyPlanChange(change));
            stored=projects.selectProjectByIdForUpdate(p.getProjectId());assertEquals(Integer.valueOf(2),stored.getBaselineVersion());b.put("baselineVersion",stored.getBaselineVersion());assertEquals(1,work.insertBaseline(b));assertEquals(2,work.selectBaselines(p.getProjectId()).size());
            BusinessProject old=new BusinessProject();old.setProjectName("legacy");projects.insertProject(old);assertEquals(Integer.valueOf(0),projects.selectProjectByIdForUpdate(old.getProjectId()).getBaselineVersion());
        }
    }
    @Test void closingDatesAreDistinctSortedAndExcludeUnconfirmedOrSupersededWork()throws Exception
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectWorkMapper m=session.getMapper(BusinessProjectWorkMapper.class);
            for(int i=0;i<4;i++)
            {
                Map<String,Object> e=entry();e.put("sourceKey","close-day-"+i);e.put("bizDate",i==0?"2026-03-03":i==3?"2026-03-01":"2026-03-02");m.insertEntry(e);
                if(i<3){Long id=((Number)e.get("entryId")).longValue();transition(m,id,"DRAFT","SUBMITTED",0);transition(m,id,"SUBMITTED","CONFIRMED",1);}
            }
            assertEquals(Arrays.asList("2026-03-02","2026-03-03"),m.selectConfirmedWorkDates(1L));
        }
    }
    @Test void unlimitedPlanClearsEndDateAndStillChecksStaffStartBoundary() throws Exception
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectWorkMapper work=session.getMapper(BusinessProjectWorkMapper.class);
            try(Statement sql=session.getConnection().createStatement())
            { sql.execute("update biz_project set plan_end_date='2026-12-31' where project_id=1"); }
            work.insertAssignment(row("projectId",1L,"userId",30L,"effectiveFrom","2027-01-01","effectiveTo","2027-01-31",
                "inputUnit","DAY","inputQuantity",1,"plannedMinutes",480,"calendarId",1L,"calendarSnapshotJson","{}",
                "unitPolicyId",1L,"unitSnapshotJson","{}","userName","owner"));
            Map<String,Object> plan=row("projectId",1L,"baseVersion",1,"projectVersion",0,"objective","持续运营",
                "planStartDate","2026-01-01","planEndDate",null,"acceptanceCriteria","按阶段验收","userName","owner");
            assertEquals(0,work.countAssignmentsOutside(plan));assertEquals(1,work.applyPlanChange(plan));
            assertNull(session.getMapper(BusinessProjectMapper.class).selectProjectByIdForUpdate(1L).getPlanEndDate());
            plan.put("planStartDate","2027-01-02");assertEquals(1,work.countAssignmentsOutside(plan));
            plan.put("planStartDate","2026-01-01");plan.put("planEndDate","2026-12-31");assertEquals(1,work.countAssignmentsOutside(plan));
        }
    }
    private void transition(BusinessProjectWorkMapper mapper,Long id,String from,String to,int version){assertEquals(1,mapper.transitionEntry(row("entryId",id,"fromStatus",from,"toStatus",to,"version",version,"isCurrent","CONFIRMED".equals(to)?"1":"0","actorId",10L,"userName","owner")));}
    private Map<String,Object> entry(){return row("projectId",1L,"userId",30L,"bizDate","2026-03-02","timeZone","Asia/Shanghai","activity","delivery","inputUnit","DAY","inputQuantity",new BigDecimal("0.5"),"workMinutes",240,"calendarId",1L,"calendarSnapshotJson","{}","unitPolicyId",1L,"unitSnapshotJson","{}","minutesPerDay",480,"capacityMinutes",360,"sourceKey","request-1","revisionNo",1,"actorId",30L,"userName","member");}
    private Map<String,Object> row(Object... v){Map<String,Object> r=new LinkedHashMap<String,Object>();for(int i=0;i<v.length;i+=2)r.put(String.valueOf(v[i]),v[i+1]);return r;}
    private Object value(Map<String,Object> row,String key){for(Map.Entry<String,Object> e:row.entrySet())if(e.getKey().equalsIgnoreCase(key)){Object v=e.getValue();if(v instanceof java.sql.Clob)try{return ((java.sql.Clob)v).getSubString(1,(int)((java.sql.Clob)v).length());}catch(Exception ex){throw new RuntimeException(ex);}return v;}return null;}
}
