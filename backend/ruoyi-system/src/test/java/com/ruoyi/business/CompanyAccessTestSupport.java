package com.ruoyi.business;

import java.io.InputStream;
import java.sql.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Existing workflow fixtures grant their original sponsor access. Shared/revoked grants have separate tests. */
public final class CompanyAccessTestSupport
{
    private CompanyAccessTestSupport() { }
    public static BusinessCompanyAccessService sponsorFixture()
    {
        BusinessCompanyAccessService access=mock(BusinessCompanyAccessService.class);
        lenient().when(access.project(any(BusinessProject.class),anyLong())).thenAnswer(call -> {
            BusinessProject p=call.getArgument(0); Long actor=call.getArgument(1);
            return actor.equals(p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId());
        });
        return access;
    }
    public static void register(Configuration configuration) throws Exception
    {
        String resource="mapper/business/BusinessCompanyAccessMapper.xml";
        if(configuration.isResourceLoaded(resource))return;
        try(InputStream input=Resources.getResourceAsStream(resource))
        { new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse(); }
    }
    public static int findInSet(String value,String values)
    { return values==null?0:java.util.Arrays.asList(values.split(",")).indexOf(value)+1; }
    public static void grant(Connection connection,long company,long user) throws Exception
    {
        try(Statement sql=connection.createStatement())
        {
            sql.execute("create alias if not exists find_in_set for \"com.ruoyi.business.CompanyAccessTestSupport.findInSet\"");
            sql.execute("create table if not exists sys_user(user_id bigint primary key)");
            sql.execute("create table if not exists sys_dept(dept_id bigint primary key)");
            sql.execute("alter table sys_user add column if not exists status char(1) default '0'");
            sql.execute("alter table sys_user add column if not exists del_flag char(1) default '0'");
            sql.execute("alter table sys_dept add column if not exists status char(1) default '0'");
            sql.execute("alter table sys_dept add column if not exists del_flag char(1) default '0'");
            sql.execute("create table if not exists sys_role(role_id bigint primary key,role_key varchar(64),status char(1),del_flag char(1))");
            sql.execute("create table if not exists sys_user_role(user_id bigint,role_id bigint)");
            sql.execute("create table if not exists biz_company_access(company_dept_id bigint,user_id bigint,capabilities varchar(255),primary key(company_dept_id,user_id))");
            sql.execute("insert into sys_user(user_id,status,del_flag) select "+user+",'0','0' where not exists(select 1 from sys_user where user_id="+user+")");
            sql.execute("insert into sys_dept(dept_id,status,del_flag) select "+company+",'0','0' where not exists(select 1 from sys_dept where dept_id="+company+")");
            sql.execute("insert into sys_role select 9999999,'company_owner','0','0' where not exists(select 1 from sys_role where role_id=9999999)");
            sql.execute("insert into sys_user_role select "+user+",9999999 where not exists(select 1 from sys_user_role where user_id="+user+" and role_id=9999999)");
            sql.execute("merge into biz_company_access key(company_dept_id,user_id) values("+company+","+user+",'BUSINESS,STAFF,COST_READ,COST_WRITE')");
        }
    }
}
