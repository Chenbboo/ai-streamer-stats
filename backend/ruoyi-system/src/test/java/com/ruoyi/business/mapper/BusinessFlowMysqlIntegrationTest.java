package com.ruoyi.business.mapper;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in verification against a disposable local clone, with each fixture rolled back. */
@EnabledIfEnvironmentVariable(named="BUSINESS_FLOW_TEST_DB", matches="flow_verify_[0-9]+")
class BusinessFlowMysqlIntegrationTest {
    SqlSession session;
    BusinessFlowMapper flows;
    static final long PROJECT=-800001, CLOSED=-800002, USER=-800003;
    @BeforeEach void setup() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/"+System.getenv("BUSINESS_FLOW_TEST_DB")+"?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        Configuration config=new Configuration(new Environment("flow-clone",new JdbcTransactionFactory(),
            new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root",System.getenv("BUSINESS_FLOW_TEST_PASSWORD"))));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(BusinessFlowMapper.class);
        config.addMapper(BusinessAccountingMapper.class);
        config.addMapper(BusinessMemberDayCostMapper.class);
        session=new SqlSessionFactoryBuilder().build(config).openSession(false);
        flows=session.getMapper(BusinessFlowMapper.class);
        sql("insert into sys_user(user_id,user_name,nick_name,status,del_flag) values("+USER+",'flow_fixture','Fixture','0','0')");
        sql("insert into biz_staff_profile(user_id,employment_status) values("+USER+",'ACTIVE')");
        for(long p:new long[]{PROJECT,CLOSED}) {
            sql("insert into biz_project(project_id,project_no,project_name,initiator_user_id,initiator_name,main_owner_user_id,main_owner_name,status) values("+p+",'flow_fixture_"+p+"','Fixture',9,'Boss',8,'Owner','"+(p==CLOSED?"CLOSED":"ACTIVE")+"')");
            sql("insert into biz_project_member(project_id,user_id,user_name_snapshot,member_role,joined_date) values("+p+","+USER+",'Fixture','MEMBER',current_date())");
            sql("insert into biz_project_staff_allocation(project_id,user_id,user_name,allocation_value,cost_policy_id,effective_from) values("+p+","+USER+",'Fixture',100,1,current_date())");
            sql("insert into biz_project_work_period(project_id,work_type,work_id,assignee_user_id,start_date) values("+p+",'TASK',1,"+USER+",current_date())");
        }
    }
    @AfterEach void close(){if(session!=null){session.rollback();session.close();}}
    void sql(String statement)throws Exception{try(Statement s=session.getConnection().createStatement()){s.execute(statement);}}
    String scalar(String statement)throws Exception{try(Statement s=session.getConnection().createStatement();ResultSet r=s.executeQuery(statement)){assertTrue(r.next());return r.getString(1);}}
    Map<String,Object> row(Object...pairs){Map<String,Object> r=new HashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
    @Test void independentNoSpendIsUniqueAndNotAnOperatingFact()throws Exception{
        BusinessAccountingMapper accounting=session.getMapper(BusinessAccountingMapper.class);
        java.sql.Date today=java.sql.Date.valueOf(LocalDate.now());
        accounting.confirmNoSpend(PROJECT,today,USER,"Fixture");accounting.confirmNoSpend(PROJECT,today,USER,"Fixture");
        assertEquals("1",scalar("select count(*) from biz_project_spend_confirmation where project_id="+PROJECT));
        assertEquals("Fixture",accounting.selectSpendConfirmation(PROJECT,today).get("confirmedUserName"));
        assertEquals("0",scalar("select count(*) from biz_operating_fact where project_id="+PROJECT));
    }
    @Test void releaseAndResumeHaveExplicitDateBoundary(){
        flows.pause(PROJECT,"Boss","release");
        BusinessMemberDayCostMapper costs=session.getMapper(BusinessMemberDayCostMapper.class);
        assertEquals(LocalDate.now().plusDays(1).toString(),String.valueOf(costs.selectCostPauses(PROJECT).get(0).get("effectiveFrom")));
        flows.resume(PROJECT,"Boss");
        Map<String,Object> r=costs.selectCostPauses(PROJECT).get(0);
        assertEquals(String.valueOf(r.get("effectiveFrom")),String.valueOf(r.get("effectiveTo")));
    }
    @Test void departureSqlPreservesClosedProjectHistory()throws Exception{
        assertEquals(1,flows.responsibilities(USER).size());
        Map<String,Object> input=row("userId",USER,"date",LocalDate.now().toString(),"actor","HR","actorId",9L,"reason","Leaving","handover","Verified");
        flows.insertDeparture(input);Long id=((Number)input.get("id")).longValue();
        assertEquals("SCHEDULED",flows.selectDeparture(id).get("status"));assertTrue(flows.dueDepartures().contains(id));
        assertEquals(Long.valueOf(USER),flows.lockStaff(USER));assertEquals("ACTIVE",flows.employmentStatus(USER));
        assertEquals(1,flows.endMemberships(input));assertEquals(1,flows.endAllocations(input));assertEquals(1,flows.endWorkPeriods(input));
        flows.markDeparted(USER,"HR");flows.departureStatus(id,"COMPLETED",null);
        assertEquals("COMPLETED",flows.lockDeparture(id).get("status"));assertEquals("LEFT",flows.employmentStatus(USER));
        assertEquals("0",scalar("select status from biz_project_member where project_id="+CLOSED+" and user_id="+USER));
        assertEquals("ACTIVE",scalar("select status from biz_project_work_period where project_id="+CLOSED));
        assertNull(scalar("select effective_to from biz_project_staff_allocation where project_id="+CLOSED));
    }
    @Test void adjustmentIsPostedSeparatelyAndRespectsOwnerScope()throws Exception{
        Map<String,Object> input=row("projectId",CLOSED,"date",LocalDate.now().toString(),"delta",new BigDecimal("-25.50"),"currency","CNY","reason","Late expense","requestId",UUID.randomUUID().toString(),"actorId",8L,"actor","Owner");
        flows.insertAdjustment(input);Long id=((Number)input.get("id")).longValue();
        assertEquals(id,((Number)flows.replayAdjustment(input).get("id")).longValue());
        assertEquals("PENDING",flows.selectAdjustment(id).get("status"));
        assertEquals(1,flows.reviewAdjustment(row("id",id,"status","APPROVED","actor","Boss","actorId",9L,"comment","Verified")));
        assertEquals(LocalDate.now().toString(),String.valueOf(flows.lockAdjustment(id).get("posting_date")));
        Map<String,Object> scope=row("viewAll",false,"userId",9L,"projectId",CLOSED,"dateFrom",LocalDate.now().toString(),"dateTo",LocalDate.now().toString());
        assertEquals(new BigDecimal("-25.50"),flows.adjustmentTotals(scope).get(0).get("amount"));
        scope.put("userId",8L);assertTrue(flows.adjustmentTotals(scope).isEmpty());
        assertEquals("0",scalar("select count(*) from biz_project_daily_result where project_id="+CLOSED));
    }
}
