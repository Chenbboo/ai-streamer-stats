package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

public class BusinessPublicExpenseOwnerEligibilityTest
{
    public static int findInSet(String needle, String haystack)
    {
        if (haystack == null) return 0;
        String[] parts = haystack.split(",");
        for (int i = 0; i < parts.length; i++) if (parts[i].equals(needle)) return i + 1;
        return 0;
    }

    @Test void candidatesRespectEmploymentDepartmentAccountAndCompany() throws Exception
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:expense_owners_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement())
        {
            for (String statement : new String[] {
                "create alias find_in_set for \"com.ruoyi.business.mapper.BusinessPublicExpenseOwnerEligibilityTest.findInSet\"",
                "create table sys_dept(dept_id bigint,parent_id bigint,dept_name varchar(40),ancestors varchar(100),del_flag char(1),status char(1))",
                "create table sys_user(user_id bigint,dept_id bigint,nick_name varchar(40),user_name varchar(40),del_flag char(1),status char(1))",
                "create table biz_staff_profile(user_id bigint,employment_status varchar(20))",
                "create table sys_role(role_id bigint,role_key varchar(30),status char(1),del_flag char(1))",
                "create table sys_user_role(user_id bigint,role_id bigint)",
                "create table biz_project(main_owner_user_id bigint,company_dept_id bigint,del_flag char(1))",
                "insert into sys_dept values(110,100,'Company','0,100','0','0'),(201,110,'Active','0,100,110','0','0'),(202,110,'Disabled','0,100,110','0','1'),(203,111,'Other company','0,100,111','0','0')",
                "insert into sys_role values(1,'project_owner','0','0')",
                "insert into sys_user values(1,201,'Active','a','0','0'),(2,201,'Left','b','0','0'),(3,202,'Disabled department','c','0','0'),(4,201,'Disabled account','d','0','1'),(5,203,'Other company','e','0','0'),(6,201,'Legacy without profile','f','0','0'),(7,201,'Owns project','g','0','0'),(8,201,'Ordinary employee','h','0','0')",
                "insert into biz_staff_profile values(1,'ACTIVE'),(2,'LEFT'),(3,'ACTIVE'),(4,'ACTIVE'),(5,'ACTIVE'),(7,'ACTIVE')",
                "insert into sys_user_role values(1,1),(2,1),(3,1),(4,1),(5,1),(6,1)",
                "insert into biz_project values(2,110,'0'),(7,110,'0')"
            }) sql.execute(statement);
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        String path = "mapper/business/BusinessPublicExpenseMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path))
        { com.ruoyi.business.CompanyAccessTestSupport.register(configuration);new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse(); }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession())
        {
            List<Map<String,Object>> owners = session.getMapper(BusinessPublicExpenseMapper.class).selectOwners(110L);
            assertEquals(java.util.Arrays.asList(1L,6L,7L), owners.stream()
                .map(row -> ((Number) row.get("userId")).longValue()).collect(Collectors.toList()));
        }
    }
}
