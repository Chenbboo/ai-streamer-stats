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

/** Real pending UNION, counts and scope verified in a disposable clone; fixtures rolled back. */
@EnabledIfEnvironmentVariable(named="BUSINESS_FLOW_TEST_DB", matches="flow_verify_[0-9]+")
class BusinessBossAwardPendingMysqlTest {
    static final long PROJECT=-810001, BOSS=-810002, OWNER=-810003, OTHER=-810004;
    SqlSession session;
    BusinessProjectMapper mapper;
    @BeforeEach void setup() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/"+System.getenv("BUSINESS_FLOW_TEST_DB")+"?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        Configuration config=new Configuration(new Environment("award-clone",new JdbcTransactionFactory(),
            new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root",System.getenv("BUSINESS_FLOW_TEST_PASSWORD"))));
        config.getTypeAliasRegistry().registerAliases("com.ruoyi.business.domain");
        String resource="mapper/business/BusinessProjectMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){
            new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(config).openSession(false);
        mapper=session.getMapper(BusinessProjectMapper.class);
        sql("insert into biz_project(project_id,project_no,project_name,initiator_user_id,initiator_name,sponsor_owner_user_id,main_owner_user_id,main_owner_name,status,delivery_policy_version,accounting_state) values("+PROJECT+",'award_pending_fixture','Award fixture',"+OTHER+",'Initiator',"+BOSS+","+OWNER+",'Applicant','ACTIVE','SEPARATED_V1','OPEN')");
        award(-810011,OWNER,"SUBMITTED");award(-810012,OWNER,"SUBMITTED");
        award(-810013,OWNER,"DRAFT");award(-810014,OWNER,"APPROVED");award(-810015,OWNER,"RETURNED");award(-810016,OWNER,"CANCELED");
        award(-810017,BOSS,"SUBMITTED");
    }
    void award(long id,long applicant,String status)throws Exception{
        sql("insert into biz_incentive_award(award_id,project_id,company_dept_id,rule_id,rule_version,rule_name,policy_version,amount,currency,biz_date,reason,request_key,status,applicant_user_id,applicant_user_name) values("+id+","+PROJECT+",100,1,1,'Fixture rule','FIXED_V1',200,'CNY',current_date(),'Review fixture','fixture"+id+"','"+status+"',"+applicant+",'Applicant')");
    }
    void sql(String statement)throws Exception{
        try(Statement s=session.getConnection().createStatement()){s.execute(statement);}
        session.clearCache();
    }
    List<Map<String,Object>> rows(long user,boolean all,String category,int offset,int limit){
        return mapper.selectBossPendingPage(user,all,new java.util.Date(),category,offset,limit);
    }
    long count(long user,boolean all){return ((Number)mapper.selectBossPendingCounts(user,all,new java.util.Date()).get("incentiveReviewCount")).longValue();}
    @AfterEach void close(){if(session!=null){session.rollback(true);session.close();}}
    @Test void unionCountsPaginationAndAwardIdentityAgree(){
        assertEquals(2,count(BOSS,false));
        List<Map<String,Object>> awards=rows(BOSS,false,"INCENTIVE_REVIEW",0,50);
        assertEquals(2,awards.size());
        assertNotEquals(awards.get(0).get("itemKey"),awards.get(1).get("itemKey"));
        assertNotNull(awards.get(0).get("awardId"));
        assertEquals("Fixture rule",awards.get(0).get("categoryName"));
        assertEquals(awards.get(1),rows(BOSS,false,"INCENTIVE_REVIEW",1,1).get(0));
        assertTrue(rows(BOSS,false,"INCENTIVE_REVIEW",2,1).isEmpty());
        List<Map<String,Object>> all=rows(BOSS,false,"ALL",0,50);
        long total=mapper.selectBossPendingCounts(BOSS,false,new java.util.Date()).values().stream().mapToLong(v->((Number)v).longValue()).sum();
        assertEquals(total,all.size());
        assertEquals(2,all.stream().filter(r->"INCENTIVE_REVIEW".equals(r.get("category"))).count());
        assertTrue(all.stream().anyMatch(r->"KPI_MISSING".equals(r.get("category"))));
    }
    @Test void reviewedReturnedCanceledDisappearAndResubmissionReappears()throws Exception{
        for(String state:new String[]{"APPROVED","RETURNED","CANCELED"}){
            sql("update biz_incentive_award set status='"+state+"' where award_id=-810011");
            assertEquals(1,count(BOSS,false));
            assertEquals(1,rows(BOSS,false,"INCENTIVE_REVIEW",0,50).size());
            sql("update biz_incentive_award set status='SUBMITTED' where award_id=-810011");
            assertEquals(2,count(BOSS,false));
        }
    }
    @Test void onlyActualSponsorEvenWithViewAllAndNoSelfApproval()throws Exception{
        assertEquals(2,count(BOSS,true));
        assertEquals(0,count(OTHER,false));assertEquals(0,count(OTHER,true));assertEquals(0,count(OWNER,true));
        assertTrue(rows(OTHER,true,"INCENTIVE_REVIEW",0,50).isEmpty());
        sql("update biz_project set sponsor_owner_user_id=null where project_id="+PROJECT);
        assertEquals(0,count(BOSS,true));assertEquals(3,count(OTHER,false));
        sql("update biz_project set del_flag='2' where project_id="+PROJECT);
        assertEquals(0,count(OTHER,false));
    }
    void paymentFixture()throws Exception{
        sql("insert into biz_operating_fact(fact_id,project_id,company_dept_id,biz_date,category_id,category_code,category_name,fact_kind,amount,currency,description,status,idempotency_key,create_user_id) values(-810020,"+PROJECT+",100,current_date(),1,'BONUS','Bonus','COST',200,'CNY','Fixture','CONFIRMED','bonus_pending_fixture',"+OWNER+")");
        sql("update biz_incentive_award set accounting_fact_id=-810020 where award_id=-810014");
        sql("insert into biz_bonus_allocation(allocation_id,award_id,project_id,status,mode,reason,request_key,amount,created_user_id,created_user_name) values(-810021,-810014,"+PROJECT+",'APPROVED','AMOUNT','Fixture','bonus_pending_fixture',200,"+OWNER+",'Owner')");
        sql("insert into biz_bonus_allocation_line(line_id,allocation_id,user_id,user_name,amount,reason) values(-810022,-810021,"+OWNER+",'Owner',150,'Fixture'),(-810023,-810021,"+OTHER+",'Member',50,'Fixture')");
    }
    void payment(long id,long line,int amount,String status)throws Exception{
        sql("insert into biz_bonus_payment(payment_id,project_id,line_id,amount,paid_date,method,reference_no,voucher,reason,request_key,status,recorded_user_id,recorded_user_name) values("+id+","+PROJECT+","+line+","+amount+",current_date(),'BANK','fixture"+id+"','fixture','Fixture','fixture"+id+"','"+status+"',"+BOSS+",'Boss')");
    }
    void assertPayment(long amount,long people){
        List<Map<String,Object>> pending=rows(BOSS,false,"BONUS_PAYMENT",0,50);
        assertEquals(amount==0?0:1,pending.size());
        assertEquals(amount==0?0:1,((Number)mapper.selectBossPendingCounts(BOSS,false,new java.util.Date()).get("bonusPaymentCount")).intValue());
        if(amount>0){
            assertEquals(-810021L,((Number)pending.get(0).get("allocationId")).longValue());
            assertEquals(0,new java.math.BigDecimal(amount).compareTo((java.math.BigDecimal)pending.get(0).get("amount")));
            assertEquals(people,((Number)pending.get(0).get("quantity")).longValue());
        }
    }
    @Test void bonusPaymentTracksPartialPaymentsAndDisappearsOnlyWhenFullyPaid()throws Exception{
        paymentFixture();assertPayment(200,2);
        assertEquals(1,rows(BOSS,false,"ALL",0,50).stream().filter(r->"BONUS_PAYMENT".equals(r.get("category"))).count());
        assertTrue(rows(BOSS,false,"BONUS_PAYMENT",1,1).isEmpty());
        payment(-810024,-810022,60,"RECORDED");assertPayment(140,2);
        payment(-810025,-810022,90,"RECORDED");assertPayment(50,1);
        payment(-810026,-810023,50,"VOIDED");assertPayment(50,1);
        payment(-810027,-810023,50,"RECORDED");assertPayment(0,0);
    }
    @Test void bonusPaymentRequiresApprovedConfirmedAwardAndActualSponsorButSurvivesClosure()throws Exception{
        paymentFixture();assertPayment(200,2);
        assertTrue(rows(OTHER,true,"BONUS_PAYMENT",0,50).isEmpty());
        assertTrue(rows(OWNER,false,"BONUS_PAYMENT",0,50).isEmpty());
        sql("update biz_project set status='CLOSED',accounting_state='CLOSED' where project_id="+PROJECT);assertPayment(200,2);
        for(String status:new String[]{"DRAFT","SUBMITTED","RETURNED","CANCELED"}){
            sql("update biz_bonus_allocation set status='"+status+"' where allocation_id=-810021");assertPayment(0,0);
        }
        sql("update biz_bonus_allocation set status='APPROVED' where allocation_id=-810021");
        sql("update biz_incentive_award set status='CANCELED' where award_id=-810014");assertPayment(0,0);
        sql("update biz_incentive_award set status='APPROVED' where award_id=-810014");
        sql("update biz_operating_fact set status='DRAFT' where fact_id=-810020");assertPayment(0,0);
        sql("update biz_operating_fact set status='CONFIRMED' where fact_id=-810020");assertPayment(200,2);
        sql("update biz_project set del_flag='2' where project_id="+PROJECT);assertPayment(0,0);
    }
    @Test void accountingLifecycleMatchesReviewEligibility()throws Exception{
        sql("update biz_project set status='CLOSED' where project_id="+PROJECT);
        assertEquals(2,count(BOSS,false));
        sql("update biz_project set accounting_state='CLOSED' where project_id="+PROJECT);
        assertEquals(0,count(BOSS,false));
        sql("update biz_project set accounting_state='OPEN',delivery_policy_version='LEGACY_V1' where project_id="+PROJECT);
        assertEquals(0,count(BOSS,false));
        sql("update biz_project set status='ACTIVE',delivery_policy_version='LEGACY_V1',accounting_state='OPEN' where project_id="+PROJECT);
        assertEquals(2,count(BOSS,false));
        sql("update biz_project set delivery_policy_version='FUTURE' where project_id="+PROJECT);
        assertEquals(0,count(BOSS,false));
    }
}
