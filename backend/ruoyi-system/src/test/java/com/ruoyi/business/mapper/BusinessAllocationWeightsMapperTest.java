package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import com.ruoyi.business.support.BusinessAllocationWeights;

class BusinessAllocationWeightsMapperTest {
    @Test void shippedQueryIncludesClosedDonorsAndActualEndOverridesPlannedEnd() throws Exception {
        JdbcDataSource source=new JdbcDataSource();source.setURL("jdbc:h2:mem:weights_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection c=source.getConnection();Statement s=c.createStatement()){
            s.execute("create table biz_project(project_id bigint,plan_start_date date,actual_start_date date,plan_end_date date,actual_end_date date,del_flag char(1),cost_policy_version varchar(32))");
            s.execute("create table biz_project_staff_allocation(project_id bigint,user_id bigint,allocation_id bigint,allocation_value decimal(12,4),effective_from date,effective_to date,confirmation_status varchar(20),version int,status varchar(16),allocation_mode varchar(16))");
            s.execute("insert into biz_project values(1,'2026-09-01','2026-09-01','2026-09-30','2026-09-16','0','MEMBER_DAYS_V1'),(2,'2026-09-01','2026-09-01',null,null,'0','MEMBER_DAYS_V1'),(3,'2026-09-01','2026-09-01',null,null,'0','MEMBER_DAYS_V1')");
            s.execute("insert into biz_project_staff_allocation values(1,7,1,10,'2026-09-01','2026-09-16','CONFIRMED',0,'ACTIVE','PERCENTAGE'),(2,7,2,30,'2026-09-01',null,'CONFIRMED',0,'ACTIVE','PERCENTAGE'),(3,7,3,60,'2026-09-01',null,'CONFIRMED',0,'ACTIVE','PERCENTAGE'),(3,7,4,99,'2026-09-01',null,'CONFIRMED',0,'VOID','PERCENTAGE')");
        }
        Configuration config=new Configuration(new Environment("weights",new JdbcTransactionFactory(),source));
        String access="mapper/business/BusinessCompanyAccessMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(access)){new XMLMapperBuilder(input,config,access,config.getSqlFragments()).parse();}
        String resource="mapper/business/BusinessProjectMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession()){
            List<Map<String,Object>> raw=session.getMapper(BusinessProjectMapper.class).selectUserAllocationTimeline(7L);
            // H2 lowercases unquoted aliases; use the same keys returned by MySQL in production.
            List<Map<String,Object>> rows=new ArrayList<>();
            for(Map<String,Object> row:raw){Map<String,Object> normalized=new HashMap<>();for(String key:Arrays.asList("projectId","allocationId","allocationValue","effectiveFrom","effectiveTo","confirmationStatus","allocationVersion","projectStartDate","projectEndDate"))normalized.put(key,row.get(key.toLowerCase(Locale.ROOT)));rows.add(normalized);}
            assertEquals(3,rows.size());
            Map<Long,Map<String,Object>> weights=BusinessAllocationWeights.at(rows,LocalDate.parse("2026-09-17"));
            assertEquals(2,weights.size());assertEquals(0,new BigDecimal("35").compareTo((BigDecimal)weights.get(2L).get("allocationValue")));
            assertEquals(0,new BigDecimal("65").compareTo((BigDecimal)weights.get(3L).get("allocationValue")));
        }
    }
}
