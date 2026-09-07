package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.service.impl.BusinessAccountingServiceImpl;

/** Actual option SQL and pending-work SQL feed the service; unrelated dashboard data is stubbed. */
class BusinessAccountingOptionsIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach void setup() throws Exception
    {
        dataSource=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:accounting_options_"+UUID.randomUUID().toString().replace("-","")+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(100),del_flag varchar(1),status varchar(1),parent_id bigint,order_num int)",
            "create table biz_project(project_id bigint primary key,project_no varchar(50),project_name varchar(100),company_dept_id bigint,accounting_mode varchar(32),base_currency varchar(3),initiator_name varchar(100),status varchar(24),delivery_policy_version varchar(32),accounting_state varchar(24),actual_end_date date,cost_policy_version varchar(32),sponsor_owner_user_id bigint,initiator_user_id bigint,del_flag varchar(1))",
            "create table biz_project_work_entry(entry_id bigint primary key,project_id bigint,status varchar(24),is_current varchar(1))",
            "create table biz_project_work_cost(entry_id bigint primary key,pricing_status varchar(24))",
            "create table biz_project_work_event(entry_id bigint primary key,status varchar(24))",
            "insert into sys_dept values(110,'target-company','0','0',100,1),(111,'other-company','0','0',100,2)",
            "insert into biz_project values(1,'P1','target-project',110,'PROFIT','CNY','creator8','ACTIVE','SEPARATED_V1','OPEN',null,'ACTUAL_WORK_V1',9,8,'0'),(2,'P2','legacy-sponsor-fallback',111,'PROFIT','CNY','creator9','ACTIVE','SEPARATED_V1','OPEN',null,'ACTUAL_WORK_V1',null,9,'0'),(3,'P3','foreign-project',111,'PROFIT','CNY','creator10','ACTIVE','SEPARATED_V1','OPEN',null,'ACTUAL_WORK_V1',10,10,'0')",
            "insert into biz_project_work_entry values(11,1,'CONFIRMED','1'),(12,1,'CONFIRMED','1'),(13,1,'SUPERSEDED','0'),(21,2,'CONFIRMED','1'),(31,3,'CONFIRMED','1')",
            "insert into biz_project_work_cost values(12,'PRICED')",
            "insert into biz_project_work_event values(12,'PENDING')");
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        for(String name:Arrays.asList("BusinessAccountingMapper","BusinessProjectWorkMapper"))
        {
            String path="mapper/business/"+name+".xml";
            try(InputStream input=Resources.getResourceAsStream(path)){new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        }
        sessions=new SqlSessionFactoryBuilder().build(config);
    }

    @Test void projectOptionsCarryCostVersionAndSponsorIdentityWithinAuthorizedScope()
    {
        try(SqlSession session=sessions.openSession())
        {
            List<Map<String,Object>> options=session.getMapper(BusinessAccountingMapper.class).selectProjectOptions(9L,false);
            assertEquals(2,options.size());Map<String,Object> direct=option(options,1L),fallback=option(options,2L);
            assertEquals("ACTUAL_WORK_V1",direct.get("costPolicyVersion"));assertEquals(9L,direct.get("sponsorOwnerUserId"));assertEquals(8L,direct.get("initiatorUserId"));
            assertEquals(9L,fallback.get("sponsorOwnerUserId"));assertEquals(9L,fallback.get("initiatorUserId"));
            assertTrue(options.stream().noneMatch(row->Long.valueOf(3).equals(row.get("projectId"))));
        }
    }

    @Test void dashboardCountsRealUnpricedWorkAndUnfinishedPricingEventsInsteadOfAlwaysZero() throws Exception
    {
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingServiceImpl service=service(session,null);
            assertEquals(3,service.dashboard(Collections.emptyMap(),9L,false).get("pendingCostCount"));
            Map<String,Object> query=Collections.singletonMap("projectId",1L);
            Map<String,Object> pending=service.dashboard(query,9L,false);
            assertEquals(2,pending.get("pendingCostCount"));assertEquals("PENDING_COST",pending.get("costDataStatus"));
            assertEquals(1,service.dashboard(Collections.singletonMap("companyDeptId",111L),9L,false).get("pendingCostCount"));
            execute("insert into biz_project_work_cost values(11,'PRICED')","update biz_project_work_event set status='DONE' where entry_id=12");session.clearCache();
            assertEquals(0,service.dashboard(query,9L,false).get("pendingCostCount"));
        }
    }

    @Test void closedProjectsRemainInReadOnlyHistoryButNotInDefaultWriteOptions() throws Exception
    {
        execute("update biz_project set accounting_state='CLOSED',status='CLOSED' where project_id in(1,3)");
        try(SqlSession session=sessions.openSession())
        {
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);
            List<Map<String,Object>> writeOptions=mapper.selectProjectOptions(9L,false);
            assertEquals(1,writeOptions.size());assertEquals(2L,writeOptions.get(0).get("projectId"));
            Map<String,Object> forgedQuery=new HashMap<>();forgedQuery.put("includeClosed",true);forgedQuery.put("viewAll",true);forgedQuery.put("userId",10L);
            List<Map<String,Object>> history=(List<Map<String,Object>>)service(session,null).dashboard(forgedQuery,9L,false).get("projects");
            assertEquals(2,history.size());assertEquals("CLOSED",option(history,1L).get("accountingState"));
            assertTrue(history.stream().noneMatch(row->Long.valueOf(3).equals(row.get("projectId"))));
        }
    }

    @Test void authorizedProjectCockpitCannotReturnForeignProjectOrCompanyDirectory() throws Exception
    {
        execute("update biz_project set accounting_state='CLOSED',status='CLOSED' where project_id=1");
        try(SqlSession session=sessions.openSession())
        {
            Map<String,Object> project=new HashMap<>();project.put("projectId",1L);project.put("companyDeptId",110L);project.put("mainOwnerUserId",7L);project.put("initiatorUserId",9L);project.put("costPolicyVersion","ACTUAL_WORK_V1");
            BusinessAccountingServiceImpl service=service(session,project);
            Map<String,Object> cockpit=service.projectDashboard(1L,Collections.emptyMap(),7L,false);
            List<Map<String,Object>> projects=(List<Map<String,Object>>)cockpit.get("projects"),companies=(List<Map<String,Object>>)cockpit.get("companies");
            assertEquals(1,projects.size());assertEquals(1L,projects.get(0).get("projectId"));
            assertEquals(1,companies.size());assertEquals(110L,companies.get(0).get("companyDeptId"));
            assertEquals(2,cockpit.get("pendingCostCount"));
            assertFalse(cockpit.toString().contains("foreign-project"));assertFalse(cockpit.toString().contains("other-company"));
            assertEquals(3,((List<?>)service.dashboard(Collections.emptyMap(),1L,true).get("projects")).size()); // Ordinary admin directory remains available.
        }
    }

    private BusinessAccountingServiceImpl service(SqlSession session,Map<String,Object> project)
    {
        BusinessAccountingMapper actual=session.getMapper(BusinessAccountingMapper.class),bridge=mock(BusinessAccountingMapper.class);
        when(bridge.selectProjectOptions(anyLong(),anyBoolean(),anyBoolean())).thenAnswer(call->actual.selectProjectOptions(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
        when(bridge.selectCompanies()).thenAnswer(call->actual.selectCompanies());
        if(project!=null)when(bridge.selectProjectForAccounting(1L)).thenReturn(project);
        BusinessAccountingServiceImpl service=new BusinessAccountingServiceImpl();ReflectionTestUtils.setField(service,"mapper",bridge);ReflectionTestUtils.setField(service,"workMapper",session.getMapper(BusinessProjectWorkMapper.class));return service;
    }
    private Map<String,Object> option(List<Map<String,Object>> options,Long id)
    {return options.stream().filter(row->id.equals(row.get("projectId"))).findFirst().orElseThrow(()->new AssertionError("Missing project "+id));}
    private void execute(String...sql) throws Exception
    {try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){for(String value:sql)statement.execute(value);}}
}
