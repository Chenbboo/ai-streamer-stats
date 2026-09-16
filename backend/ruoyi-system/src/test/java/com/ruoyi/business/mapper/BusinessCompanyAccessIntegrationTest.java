package com.ruoyi.business.mapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.CompanyAccessTestSupport;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

public class BusinessCompanyAccessIntegrationTest
{
    private SqlSessionFactory factory;
    public static int findInSet(String value,String values)
    { return values==null?0:Arrays.asList(values.split(",")).indexOf(value)+1; }
    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:company_access_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection c=source.getConnection();Statement sql=c.createStatement())
        {
            sql.execute("create alias find_in_set for \"com.ruoyi.business.mapper.BusinessCompanyAccessIntegrationTest.findInSet\"");
            sql.execute("create table sys_user(user_id bigint primary key,user_name varchar(64),nick_name varchar(64),status char(1),del_flag char(1),dept_id bigint)");
            sql.execute("create table sys_dept(dept_id bigint primary key,parent_id bigint,ancestors varchar(100),dept_name varchar(64),status char(1),del_flag char(1),order_num int)");
            sql.execute("create table sys_role(role_id bigint primary key,role_key varchar(64),status char(1),del_flag char(1))");
            sql.execute("create table sys_user_role(user_id bigint,role_id bigint)");
            sql.execute("create table biz_company_access(company_dept_id bigint,user_id bigint,capabilities varchar(255),version int,update_by varchar(64),update_time timestamp default current_timestamp,primary key(company_dept_id,user_id))");
            sql.execute("create table biz_company_access_event(event_id bigint auto_increment primary key,company_dept_id bigint,user_id bigint,capabilities varchar(255),operator_id bigint,operator_name varchar(64),reason varchar(500))");
            sql.execute("create table biz_project(project_id bigint primary key,company_dept_id bigint,del_flag char(1))");
            sql.execute("insert into sys_user values(8,'boss_a','老板甲','0','0',110),(9,'boss_b','老板乙','0','0',120),(10,'staff','员工','0','0',111)");
            sql.execute("insert into sys_dept values(110,100,'0,100','甲公司','0','0',1),(120,100,'0,100','乙公司','0','0',2),(130,100,'0,100','未授权公司','0','0',3),(111,110,'0,100,110','甲部门','0','0',1)");
            sql.execute("insert into sys_role values(2,'company_owner','0','0');insert into sys_user_role values(8,2),(9,2)");
            sql.execute("insert into biz_project values(1,110,'0'),(2,120,'0'),(3,130,'0')");
            sql.execute("insert into biz_company_access(company_dept_id,user_id,capabilities,version) values(110,8,'BUSINESS,STAFF,COST_READ,COST_WRITE,AUTHORIZE',1),(120,8,'BUSINESS,AUTHORIZE',1),(110,9,'BUSINESS,COST_READ',1),(120,9,'BUSINESS,COST_READ',1)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        CompanyAccessTestSupport.register(config);
        String xml="<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'http://mybatis.org/dtd/mybatis-3-mapper.dtd'><mapper namespace='CompanyScopeTest'><select id='projects' resultType='long'>select p.project_id from biz_project p where <include refid='com.ruoyi.business.mapper.BusinessCompanyAccessMapper.scope'><property name='company' value='p.company_dept_id'/><property name='capability' value='BUSINESS'/></include> order by p.project_id</select></mapper>";
        new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),config,"company-scope-test",config.getSqlFragments()).parse();
        factory=new SqlSessionFactoryBuilder().build(config);
    }
    private BusinessCompanyAccessService service(SqlSession session)
    { BusinessCompanyAccessService service=new BusinessCompanyAccessService();ReflectionTestUtils.setField(service,"mapper",session.getMapper(BusinessCompanyAccessMapper.class));return service; }
    @Test void bothBossesSeeAuthorizedCompaniesAndNeverThirdCompany()
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessCompanyAccessService service=service(session);
            for(Long user:Arrays.asList(8L,9L))
            {
                assertEquals(Arrays.asList(1L,2L),session.selectList("CompanyScopeTest.projects",Collections.singletonMap("userId",user)));
                assertTrue(service.project(1L,user));assertTrue(service.project(2L,user));assertFalse(service.project(3L,user));
            }
            assertFalse(service.project(1L,10L));assertFalse(service.project((BusinessProject)null,8L));
        }
    }
    @Test void revocationAndDisabledAccountsImmediatelyAffectListAndObjectAccess() throws Exception
    {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement())
        {
            sql.execute("update biz_company_access set capabilities='' where user_id=9 and company_dept_id=110");
            assertFalse(service(session).project(1L,9L));
            assertEquals(Collections.singletonList(2L),session.selectList("CompanyScopeTest.projects",Collections.singletonMap("userId",9L)));
            sql.execute("update sys_user set status='1' where user_id=9");session.clearCache();
            assertFalse(service(session).project(2L,9L));
            assertTrue(session.selectList("CompanyScopeTest.projects",Collections.singletonMap("userId",9L)).isEmpty());
        }
    }
    @Test void costReadDoesNotGrantCostWriteAndCompanyFollowsDepartment()
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessCompanyAccessService service=service(session);
            assertEquals(110L,service.staffCompany(10L));assertTrue(service.staff(9L,10L,"COST_READ"));
            assertFalse(service.staff(9L,10L,"COST_WRITE"));assertTrue(service.staff(8L,10L,"COST_WRITE"));
            assertFalse(service.allowed(8L,110L,"INTEGRATION"));assertFalse(service.allowed(8L,110L,"CUTOVER"));
        }
    }
    @Test void secondBossOpensOriginalBossProjectAndRevocationDeniesIt() throws Exception
    {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement())
        {
            BusinessProject project=new BusinessProject();project.setProjectId(1L);project.setCompanyDeptId(110L);
            project.setMainOwnerUserId(10L);project.setSponsorOwnerUserId(8L);project.setStatus("ACTIVE");
            BusinessProjectMapper projects=org.mockito.Mockito.mock(BusinessProjectMapper.class);
            org.mockito.Mockito.when(projects.selectProjectById(1L)).thenReturn(project);
            com.ruoyi.business.service.impl.BusinessProjectServiceImpl workflow=new com.ruoyi.business.service.impl.BusinessProjectServiceImpl();
            ReflectionTestUtils.setField(workflow,"mapper",projects);ReflectionTestUtils.setField(workflow,"companyAccess",service(session));
            assertSame(project,workflow.getProject(1L,9L,false,true));
            assertEquals(true,project.getGovernanceProfile().get("companyManager"));
            assertEquals(8L,project.getSponsorOwnerUserId());assertEquals(10L,project.getMainOwnerUserId());
            sql.execute("update biz_company_access set capabilities='' where company_dept_id=110 and user_id=9");session.clearCache();
            assertThrows(ServiceException.class,()->workflow.getProject(1L,9L,false,true));
        }
    }
    @Test void grantsRequireAuthorityCeilingReasonAndCurrentVersionAndPreserveActor()
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessCompanyAccessService service=service(session);
            assertThrows(ServiceException.class,()->service.save(110L,8L,Arrays.asList("BUSINESS"),1,"test",9L,"乙"));
            assertThrows(ServiceException.class,()->service.save(110L,9L,Arrays.asList("INTEGRATION"),1,"test",8L,"甲"));
            assertThrows(ServiceException.class,()->service.save(110L,9L,Arrays.asList("COST_WRITE"),1,"test",8L,"甲"));
            assertThrows(ServiceException.class,()->service.save(110L,9L,Arrays.asList("BUSINESS"),0,"test",8L,"甲"));
            service.save(110L,9L,Collections.emptyList(),1,"撤销公司权限",8L,"甲");
            assertFalse(service.project(1L,9L));assertTrue(service.project(2L,9L));
            assertEquals(2,session.getMapper(BusinessCompanyAccessMapper.class).version(110L,9L));
            try(Statement sql=session.getConnection().createStatement();ResultSet row=sql.executeQuery("select operator_id,reason from biz_company_access_event"))
            { assertTrue(row.next());assertEquals(8L,row.getLong(1));assertEquals("撤销公司权限",row.getString(2)); }
            catch(SQLException ex){throw new AssertionError(ex);}
        }
    }
    @Test void authorizerCanAdjustOwnedCapabilitiesWhilePreservingIndependentCutoverGrant() throws Exception
    {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement())
        {
            sql.execute("update biz_company_access set capabilities='BUSINESS,COST_READ,CUTOVER' where company_dept_id=110 and user_id=9");
            BusinessCompanyAccessService service=service(session);
            service.save(110L,9L,Arrays.asList("COST_READ","CUTOVER"),1,"保留独立验收授权，仅撤销经营管理",8L,"甲");
            assertFalse(service.project(1L,9L));assertTrue(service.allowed(9L,110L,"CUTOVER"));
            assertThrows(ServiceException.class,()->service.save(110L,9L,Arrays.asList("COST_READ"),2,"越权撤销验收权限",8L,"甲"));
        }
    }
    @Test void ordinaryPersonnelManagersKeepPlatformScopeButBossesCannotBypassCompanyGrants()
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessCompanyAccessService service=service(session);
            com.ruoyi.system.service.ISysUserService users=org.mockito.Mockito.mock(com.ruoyi.system.service.ISysUserService.class);
            com.ruoyi.system.service.ISysDeptService departments=org.mockito.Mockito.mock(com.ruoyi.system.service.ISysDeptService.class);
            ReflectionTestUtils.setField(service,"users",users);ReflectionTestUtils.setField(service,"departments",departments);
            login(10L);service.requireStaff(10L);service.requireDepartment(111L);
            org.mockito.Mockito.verify(users).checkUserDataScope(10L);org.mockito.Mockito.verify(departments).checkDeptDataScope(111L);
            login(9L);
            assertThrows(ServiceException.class,()->service.requireStaff(10L));
            assertThrows(ServiceException.class,()->service.requireDepartment(111L));
            org.mockito.Mockito.verifyNoMoreInteractions(users,departments);
        }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    private void login(Long userId)
    {
        com.ruoyi.common.core.domain.model.LoginUser user=new com.ruoyi.common.core.domain.model.LoginUser();user.setUserId(userId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,Collections.emptyList()));
    }
}
