package com.ruoyi.business.mapper;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.domain.BusinessIncentiveTier;
import com.ruoyi.business.domain.BusinessBonusAllocation;
import com.ruoyi.business.domain.BusinessBonusAllocationLine;
import com.ruoyi.business.domain.BusinessBonusPayment;

/** Runs the production table DDL and mapper SQL; these tests do not replace MySQL migration rehearsal. */
class BusinessIncentiveMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory factory;

    @BeforeEach void setup() throws Exception
    {
        dataSource=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:incentive_"+UUID.randomUUID().toString().replace("-","")
            +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        execute("create table biz_project(project_id bigint primary key,project_no varchar(40),project_name varchar(100),"
            +"status varchar(24),company_dept_id bigint,base_currency varchar(3),accounting_state varchar(16),"
            +"main_owner_user_id bigint,sponsor_owner_user_id bigint,initiator_user_id bigint,del_flag char(1),update_time timestamp)");
        execute("create table biz_operating_fact(fact_id bigint primary key,project_id bigint,status varchar(24),"
            +"source_domain varchar(40),source_type varchar(40),version int,update_by varchar(100),update_time timestamp)");
        execute("create table biz_project_kpi_plan(plan_id bigint primary key,plan_version int,reward_policy_version varchar(24))");
        execute("create table biz_project_kpi_settlement(settlement_id bigint primary key,project_id bigint,plan_id bigint,"
            +"period_start date,period_end date,total_score decimal(9,2),bonus_amount decimal(20,2),currency varchar(3),status varchar(16),accounting_fact_id bigint)");
        String ddl=new String(Files.readAllBytes(migration()),StandardCharsets.UTF_8);
        Matcher tables=Pattern.compile("(?s)create table if not exists biz_incentive_.*?\\) engine=InnoDB default charset=utf8mb4 comment='[^']*';").matcher(ddl);
        int count=0;
        while(tables.find()) { execute(tables.group()); execute(tables.group()); count++; }
        assertEquals(3,count,"P3 production migration tables must remain covered");
        String scoreDdl=new String(Files.readAllBytes(migration().resolveSibling("V076__kpi_score_incentive_tiers.sql")),StandardCharsets.UTF_8);
        Matcher column=Pattern.compile("alter table biz_incentive_rule add column kpi_plan_id bigint default null").matcher(scoreDdl);
        if (!column.find()) throw new AssertionError("Missing KPI plan column migration");
        execute(column.group());
        Matcher scoreTable=Pattern.compile("(?s)create table if not exists biz_incentive_tier.*?\\) engine=InnoDB default charset=utf8mb4 comment='[^']*';").matcher(scoreDdl);
        if (!scoreTable.find()) throw new AssertionError("Missing score tier migration");
        execute(scoreTable.group());execute(scoreTable.group());
        execute("insert into biz_project values(1,'P1','Project 1','ACTIVE',110,'CNY','OPEN',9,8,8,'0',current_timestamp),"
            +"(2,'P2','Project 2','ACTIVE',120,'VND','OPEN',19,18,18,'0',current_timestamp),"
            +"(3,'P3','Deleted','ACTIVE',110,'CNY','OPEN',9,8,8,'2',current_timestamp)");
        String distributionDdl=new String(Files.readAllBytes(migration().resolveSibling("V077__bonus_allocation_payment.sql")),StandardCharsets.UTF_8);
        Matcher distributionTables=Pattern.compile("(?s)create table if not exists biz_bonus_.*?\\) engine=InnoDB default charset=utf8mb4 comment='[^']*';").matcher(distributionDdl);
        int distributionCount=0;
        while(distributionTables.find()){execute(distributionTables.group());execute(distributionTables.group());distributionCount++;}
        assertEquals(4,distributionCount);
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        config.setMapUnderscoreToCamelCase(true);
        String resource="mapper/business/BusinessIncentiveMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(resource))
        { new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse(); }
        String distributionResource="mapper/business/BusinessBonusDistributionMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(distributionResource))
        {new XMLMapperBuilder(input,config,distributionResource,config.getSqlFragments()).parse();}
        factory=new SqlSessionFactoryBuilder().build(config);
    }

    @Test void authorizedProjectDirectoryDoesNotLeakOtherCompanyOrDeletedProjects()
    {
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            assertEquals(1,mapper.selectProjects(9L,false).size());
            assertEquals(1,mapper.selectProjects(8L,false).size());
            assertEquals(0,mapper.selectProjects(10L,false).size());
            assertEquals(2,mapper.selectProjects(1L,true).size());
        }
    }

    @Test void ruleSnapshotsAwardVersionsSourceCancellationAndAuditArePreserved() throws Exception
    {
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            BusinessIncentiveRule rule=rule();rule.setRuleVersion(mapper.nextRuleVersion(1L));
            assertEquals(1,mapper.insertRule(rule));assertNotNull(rule.getRuleId());
            assertEquals(Integer.valueOf(2),mapper.nextRuleVersion(1L));
            assertEquals("FIXED_V1",mapper.selectRule(rule.getRuleId()).getPolicyVersion());
            BusinessIncentiveAward award=award(rule.getRuleId());mapper.insertAward(award);
            assertEquals(1,mapper.countPendingAwards(1L));
            assertEquals("NOT_CREATED",mapper.selectAwardForUpdate(award.getAwardId()).getCostStatus());
            assertEquals(0,mapper.transitionAward(award.getAwardId(),"DRAFT","SUBMITTED",99,9L,"owner","submit",null));
            assertEquals(1,mapper.transitionAward(award.getAwardId(),"DRAFT","SUBMITTED",0,9L,"owner","submit",null));
            assertEquals(1,mapper.retireRule(rule.getRuleId(),"boss"));
            assertEquals(0,mapper.retireRule(rule.getRuleId(),"boss"));
            assertEquals(new BigDecimal("800.00"),mapper.selectAward(award.getAwardId()).getAmount());
            session.commit();
            execute("insert into biz_operating_fact values(71,1,'DRAFT','HR_INCENTIVE','BONUS',0,null,null)");
            assertEquals(1,mapper.transitionAward(award.getAwardId(),"SUBMITTED","APPROVED",1,8L,"boss","approve",71L));
            assertEquals(0,mapper.countPendingAwards(1L));
            BusinessIncentiveAward approved=mapper.selectAwardForUpdate(award.getAwardId());
            assertEquals(Long.valueOf(8),approved.getApprovedUserId());
            assertNotNull(approved.getApprovedTime());assertEquals("DRAFT",approved.getCostStatus());
            assertEquals("NOT_RECORDED",approved.getPaymentStatus());assertEquals("NOT_RECORDED",approved.getAllocationStatus());
            assertEquals(1,mapper.voidSourceFact(71L,0,"boss"));
            assertEquals(0,mapper.voidSourceFact(71L,0,"boss"));
            assertEquals(1,mapper.transitionAward(award.getAwardId(),"APPROVED","CANCELED",2,8L,"boss","cancel",71L));
            Map<String,Object> event=new LinkedHashMap<String,Object>();event.put("projectId",1L);event.put("awardId",award.getAwardId());
            event.put("eventType","AWARD_CANCELED");event.put("fromStatus","APPROVED");event.put("toStatus","CANCELED");
            event.put("operatorUserId",8L);event.put("operatorName","boss");event.put("reason","preserve trail");
            mapper.insertEvent(event);assertEquals(1,mapper.selectEvents(1L).size());
            BusinessIncentiveAward canceled=mapper.selectAwardForUpdate(award.getAwardId());
            assertEquals("VOIDED",canceled.getCostStatus());assertEquals(Long.valueOf(8),canceled.getApprovedUserId());
            assertEquals(new BigDecimal("800.00"),canceled.getAmount());assertEquals(1,mapper.selectAwards(1L).size());
        }
    }

    @Test void scoreTiersAndPlanBindingSurviveRuleRevisionAndPreventRepeatAwards()
    {
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            BusinessIncentiveRule rule=rule();rule.setPolicyVersion("SCORE_TIERS_V1");rule.setKpiPlanId(10L);
            mapper.insertRule(rule);
            for (int i=0;i<2;i++)
            {
                BusinessIncentiveTier tier=new BusinessIncentiveTier();tier.setRuleId(rule.getRuleId());tier.setSortOrder(i+1);
                tier.setMinScore(new BigDecimal(i==0?"0":"80"));tier.setMaxScore(i==0?new BigDecimal("80"):null);
                tier.setAmount(new BigDecimal(i==0?"0.00":"800.00"));mapper.insertTier(tier);
            }
            BusinessIncentiveRule stored=mapper.selectRule(rule.getRuleId());
            assertEquals(Long.valueOf(10L),stored.getKpiPlanId());assertEquals(2,stored.getTiers().size());
            assertEquals(new BigDecimal("800.00"),stored.getTiers().get(1).getAmount());
            assertEquals(2,mapper.selectRules(1L).get(0).getTiers().size());
            assertEquals(0,mapper.retirePlanRules(2L,10L,"boss"));
            assertEquals(1,mapper.retirePlanRules(1L,10L,"boss"));
            assertEquals(2,mapper.selectRule(rule.getRuleId()).getTiers().size());
            BusinessIncentiveAward award=award(rule.getRuleId());award.setPolicyVersion("SCORE_TIERS_V1");award.setSettlementId(20L);
            mapper.insertAward(award);
            assertEquals(1,mapper.countExistingScoreAward(1L,20L));assertEquals(0,mapper.countExistingScoreAward(2L,20L));
            mapper.transitionAward(award.getAwardId(),"DRAFT","CANCELED",0,9L,"owner","cancel",null);
            assertEquals(0,mapper.countExistingScoreAward(1L,20L));
        }
    }

    @Test void uniqueRequestAndCostAssociationAreEnforcedByDatabase()
    {
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            BusinessIncentiveRule rule=rule();mapper.insertRule(rule);
            BusinessIncentiveAward award=award(rule.getRuleId());mapper.insertAward(award);
            assertEquals(award.getAwardId(),mapper.selectAwardByRequest(1L,"request-key-123").getAwardId());
            assertThrows(Exception.class,()->mapper.insertAward(award(rule.getRuleId())));
        }
    }

    @Test void sourceMutationsRejectManualConfirmedAndWrongVersionFacts() throws Exception
    {
        execute("insert into biz_operating_fact values(71,1,'RETURNED','HR_INCENTIVE','BONUS',3,null,null),"
            +"(72,1,'DRAFT','MANUAL','MANUAL',0,null,null),(73,1,'CONFIRMED','HR_INCENTIVE','BONUS',0,null,null)");
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            assertEquals(0,mapper.resubmitSourceFact(71L,2,"boss"));
            assertEquals(1,mapper.resubmitSourceFact(71L,3,"boss"));
            assertEquals(0,mapper.resubmitSourceFact(71L,4,"boss"));
            assertEquals(0,mapper.voidSourceFact(72L,0,"boss"));assertEquals(0,mapper.voidSourceFact(73L,0,"boss"));
        }
    }

    @Test void legacyReadModelNeverClaimsPersonalAllocationOrPayment() throws Exception
    {
        execute("insert into biz_project_kpi_plan values(10,1,'LEGACY_LINKED'),(11,2,'INDEPENDENT_V1')");
        execute("insert into biz_project_kpi_settlement values(20,1,10,'2026-01-01','2026-01-31',100,800,'CNY','CONFIRMED',null),"
            +"(21,1,11,'2026-02-01','2026-02-28',100,null,'CNY','CONFIRMED',null)");
        try(SqlSession session=factory.openSession(false))
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            assertEquals(1,mapper.selectLegacyBonuses(1L).size());assertEquals(1,mapper.selectConfirmedKpis(1L).size());
            Map<String,Object> legacy=mapper.selectLegacyBonuses(1L).get(0);
            assertEquals("NOT_RECORDED",value(legacy,"paymentStatus"));assertEquals("NOT_RECORDED",value(legacy,"allocationStatus"));
        }
    }

    @Test void allocationPaymentAndPersonalDirectoryUseRealMapperAndRollback() throws Exception
    {
        try(SqlSession session=factory.openSession(false))
        {
            BusinessBonusDistributionMapper m=session.getMapper(BusinessBonusDistributionMapper.class);
            BusinessBonusAllocation b=new BusinessBonusAllocation();b.setAwardId(99L);b.setProjectId(1L);b.setMode("AMOUNT");
            b.setAmount(new BigDecimal("100.00"));b.setReason("allocate");b.setRequestKey("batch1");b.setCreatedUserId(9L);b.setCreatedUserName("owner");
            assertEquals(1,m.insertAllocation(b));
            BusinessBonusAllocationLine l=new BusinessBonusAllocationLine();l.setAllocationId(b.getAllocationId());l.setUserId(30L);l.setUserName("Alice");l.setAmount(new BigDecimal("100.00"));l.setReason("delivery");
            m.insertLine(l);
            assertEquals(new BigDecimal("100.00"),m.reserved(99L,null));assertEquals(1,m.countAllocations(99L));
            assertEquals(1,session.getMapper(BusinessIncentiveMapper.class).countDistributionReservations(99L));
            assertTrue(m.projects(30L,false,false).isEmpty(),"Unapproved allocation must not expose project");
            assertEquals(1,m.transition(b.getAllocationId(),0,"SUBMITTED",9L,"owner"));
            assertEquals(0,m.transition(b.getAllocationId(),0,"APPROVED",8L,"boss"),"stale transition rejected");
            assertEquals(1,m.transition(b.getAllocationId(),1,"APPROVED",8L,"boss"));
            BusinessBonusAllocation stored=m.allocation(b.getAllocationId());assertEquals(2,stored.getVersion());assertEquals(8L,stored.getApprovedUserId());assertNotNull(stored.getApprovedTime());
            assertEquals(1,m.projects(30L,false,false).size());assertTrue(m.projects(31L,false,false).isEmpty());
            assertEquals(2,m.projects(1L,true,false).size(),"deleted project excluded");
            BusinessBonusPayment p=new BusinessBonusPayment();p.setProjectId(1L);p.setLineId(l.getLineId());p.setAmount(new BigDecimal("40.00"));p.setPaidDate(new java.util.Date());
            p.setMethod("BANK");p.setReferenceNo("bank-1");p.setVoucher("/profile/upload/proof.pdf");p.setReason("first");p.setRequestKey("payment-1");p.setRecordedUserId(50L);p.setRecordedUserName("finance");
            m.insertPayment(p);assertEquals(new BigDecimal("40.00"),m.line(l.getLineId()).getPaidAmount());
            assertEquals(p.getPaymentId(),m.paymentRequest(1L,"payment-1").getPaymentId());assertEquals(p.getPaymentId(),m.paymentReference(l.getLineId(),"bank-1").getPaymentId());assertEquals(1,m.payments(b.getAllocationId()).size());
            assertEquals(new BigDecimal("0"),m.reserved(99L,b.getAllocationId()).stripTrailingZeros());
            Map<String,Object> event=new java.util.HashMap<>();event.put("projectId",1L);event.put("allocationId",b.getAllocationId());event.put("eventType","PAYMENT");event.put("userId",50L);event.put("userName","finance");event.put("reason","first");event.put("snapshot","{}");m.event(event);
            assertEquals(1,m.events(b.getAllocationId()).size());
            session.rollback();
        }
        try(SqlSession session=factory.openSession()){assertTrue(session.getMapper(BusinessBonusDistributionMapper.class).allocations(1L).isEmpty());}
    }

    private Path migration()
    {
        for(Path directory=Paths.get("").toAbsolutePath();directory!=null;directory=directory.getParent())
        {
            Path file=directory.resolve("backend/sql/migrations/V065__independent_project_incentive.sql");
            if(Files.isRegularFile(file))return file;
        }
        throw new AssertionError("Cannot locate P3 migration");
    }
    private void execute(String sql) throws Exception
    { try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){statement.execute(sql);} }
    private Object value(Map<String,Object> row,String key)
    { for(Map.Entry<String,Object> entry:row.entrySet())if(entry.getKey().equalsIgnoreCase(key))return entry.getValue();return null; }
    private BusinessIncentiveRule rule()
    {
        BusinessIncentiveRule r=new BusinessIncentiveRule();r.setProjectId(1L);r.setRuleVersion(1);r.setRuleName("Delivery");
        r.setPolicyVersion("FIXED_V1");r.setAmount(new BigDecimal("800.00"));r.setCurrency("CNY");r.setStatus("ACTIVE");
        r.setCreatedUserId(8L);r.setCreatedUserName("boss");r.setReason("Published rule");r.setCreateBy("boss");return r;
    }
    private BusinessIncentiveAward award(Long ruleId)
    {
        BusinessIncentiveAward a=new BusinessIncentiveAward();a.setProjectId(1L);a.setCompanyDeptId(110L);a.setRuleId(ruleId);
        a.setRuleVersion(1);a.setRuleName("Delivery");a.setPolicyVersion("FIXED_V1");a.setAmount(new BigDecimal("800.00"));a.setCurrency("CNY");
        a.setBizDate(Date.valueOf("2026-06-30"));a.setReason("Delivery evidence");a.setRequestKey("request-key-123");a.setStatus("DRAFT");
        a.setApplicantUserId(9L);a.setApplicantUserName("owner");a.setCreateBy("owner");return a;
    }
}
