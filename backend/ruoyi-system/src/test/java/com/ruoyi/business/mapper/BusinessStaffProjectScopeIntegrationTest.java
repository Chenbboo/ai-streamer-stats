package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import com.ruoyi.common.core.domain.entity.SysUser;

public class BusinessStaffProjectScopeIntegrationTest
{
    private SqlSessionFactory factory;
    public static int findInSet(String needle,String haystack)
    {return haystack==null?0:Arrays.asList(haystack.split(",")).indexOf(needle)+1;}

    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:staff_scope_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection c=source.getConnection();Statement s=c.createStatement())
        {
            s.execute("create alias find_in_set for \"com.ruoyi.business.mapper.BusinessStaffProjectScopeIntegrationTest.findInSet\"");
            s.execute("create table sys_dept(dept_id bigint,parent_id bigint,ancestors varchar(100),dept_name varchar(100),order_num int,status char(1),del_flag char(1),leader_user_id bigint)");
            s.execute("create table sys_user(user_id bigint,dept_id bigint,user_name varchar(50),nick_name varchar(50),email varchar(50),phonenumber varchar(30),sex char(1),status char(1),del_flag char(1),login_date timestamp,create_time timestamp,remark varchar(100))");
            s.execute("create table biz_staff_profile(user_id bigint,country_region varchar(10),employment_status varchar(20))");
            s.execute("create table biz_project(project_id bigint,main_owner_user_id bigint,del_flag char(1),status varchar(20))");
            s.execute("create table biz_project_member(project_id bigint,user_id bigint,status char(1))");
            s.execute("insert into sys_dept values(111,100,'0,100','公司',1,'0','0',80)");
            for(long id:new long[]{10,11,12,13,14,15,16})
            {
                s.execute("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values("+id+",111,'user"+id+"','成员"+id+"','0','0')");
                s.execute("insert into biz_staff_profile values("+id+",'CN','ACTIVE')");
            }
            s.execute("insert into biz_project values(1,10,'0','ACTIVE'),(2,10,'0','PAUSED'),(3,20,'0','ACTIVE'),(4,10,'0','CLOSED'),(5,10,'1','ACTIVE')");
            s.execute("insert into biz_project_member values(1,10,'0'),(1,11,'0'),(2,11,'0'),(1,12,'1'),(3,13,'0'),(4,14,'0'),(5,15,'0'),(2,16,'0')");
        }
        Configuration config=new Configuration(new Environment("scope",new JdbcTransactionFactory(),source));
        config.setMapUnderscoreToCamelCase(true);
        String resource="mapper/business/BusinessProjectMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(resource))
        {new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        factory=new SqlSessionFactoryBuilder().build(config);
    }

    @Test void directoryAddsCurrentManagedMembersWithoutDuplicatesAndKeepsSearchFilters()
    {
        try(SqlSession session=factory.openSession())
        {
            BusinessProjectMapper mapper=session.getMapper(BusinessProjectMapper.class);
            SysUser query=new SysUser();query.getParams().put("dataScope"," AND u.user_id = 10");
            assertEquals(Arrays.asList(10L,11L,16L),mapper.selectStaffDirectory(query,10L).stream().map(SysUser::getUserId).collect(Collectors.toList()));
            query.setNickName("成员11");
            assertEquals(Collections.singletonList(11L),mapper.selectStaffDirectory(query,10L).stream().map(SysUser::getUserId).collect(Collectors.toList()));
            assertEquals(0,mapper.countManagedProjectMember(10L,12L));
            assertEquals(0,mapper.countManagedProjectMember(10L,13L));
            assertEquals(0,mapper.countManagedProjectMember(10L,14L));
            assertEquals(0,mapper.countManagedProjectMember(10L,15L));
        }
    }

    @Test void costOptionsUseSameManagedProjectScopeAndDoNotGrantWholeCompany()
    {
        try(SqlSession session=factory.openSession())
        {
            Map<String,Object> query=new HashMap<>();query.put("userId",10L);query.put("administrator",false);query.put("companyOwner",false);query.put("financeManager",false);query.put("companyDeptId",111L);
            List<Map<String,Object>> rows=session.getMapper(BusinessProjectMapper.class).selectStaffCostOptions(query);
            assertEquals(Arrays.asList(10L,11L,16L),rows.stream().map(row->((Number)(row.containsKey("userId")?row.get("userId"):row.get("userid"))).longValue()).sorted().collect(Collectors.toList()));
        }
    }
}
