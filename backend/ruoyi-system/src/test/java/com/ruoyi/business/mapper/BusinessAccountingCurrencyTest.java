package com.ruoyi.business.mapper;
import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

class BusinessAccountingCurrencyTest {
    @Test void totalsKeepCurrenciesSeparateAndSingleCurrencyRetainsSummary()throws Exception{
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:currency_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("create table biz_project(project_id bigint,del_flag char(1),base_currency varchar(3),sponsor_owner_user_id bigint,initiator_user_id bigint)");
            s.execute("create table biz_project_daily_result(project_id bigint,is_current char(1),company_dept_id bigint,biz_date date,revenue_amount decimal(18,2),cost_amount decimal(18,2),personnel_cost decimal(18,2),bonus_cost decimal(18,2),adjustment_amount decimal(18,2),profit_amount decimal(18,2))");
            s.execute("insert into biz_project values(1,'0','CNY',10,10),(2,'0','VND',10,10)");
            s.execute("insert into biz_project_daily_result values(1,'1',111,'2026-09-01',100,0,0,0,0,100),(2,'1',222,'2026-09-01',100,0,0,0,0,100)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        String path="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path)){new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession()){
            BusinessAccountingMapper mapper=session.getMapper(BusinessAccountingMapper.class);Map<String,Object> query=new HashMap<>();query.put("viewAll",true);
            Map<String,Object> total=mapper.selectDailySummary(query);assertNull(total.get("revenueAmount"));assertNull(total.get("profitAmount"));
            List<Map<String,Object>> groups=mapper.selectDailySummaryByCurrency(query);assertEquals(2,groups.size());
            assertEquals("CNY",groups.get(0).get("currency"));assertEquals("VND",groups.get(1).get("currency"));
            assertEquals(new BigDecimal("100.00"),groups.get(0).get("revenueAmount"));
            query.put("projectId",1L);assertEquals(new BigDecimal("100.00"),mapper.selectDailySummary(query).get("revenueAmount"));
        }
    }
}
