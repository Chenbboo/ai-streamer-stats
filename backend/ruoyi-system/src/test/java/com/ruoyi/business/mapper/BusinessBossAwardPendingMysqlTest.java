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
