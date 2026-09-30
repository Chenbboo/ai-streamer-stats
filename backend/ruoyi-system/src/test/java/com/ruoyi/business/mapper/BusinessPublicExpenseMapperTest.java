package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class BusinessPublicExpenseMapperTest
{
    @Test void retainedBudgetCountsOnlyUnrepresentedImmutablePositiveHistory() throws Exception {
        org.h2.jdbcx.JdbcDataSource source=new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:retained_"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE");
        try(java.sql.Connection connection=source.getConnection()) {
            try(java.sql.Statement sql=connection.createStatement()) {
                for(String statement:new String[]{
                    "create table biz_project(project_id bigint,project_name varchar(30),del_flag char(1),accounting_state varchar(20))",
                    "create table biz_public_expense_daily(bill_id bigint,project_id bigint,amount decimal(20,2),it_transfer_amount decimal(20,2))",
                    "create table biz_public_expense_owner(allocation_id bigint,bill_id bigint)",
                    "create table biz_public_expense_project(allocation_id bigint,project_id bigint)",
                    "insert into biz_project values(1,'active','0','OPEN'),(2,'deleted','2','OPEN'),(3,'closed','0','CLOSED'),(5,'represented','2','OPEN'),(6,'IT','2','OPEN')",
                    "insert into biz_public_expense_daily values(10,1,1,0),(10,2,100,0),(10,2,130.33,null),(10,3,10,0),(10,4,20,0),(10,5,30,0),(10,6,-50,-50),(11,2,100,0)",
                    "insert into biz_public_expense_owner values(99,10)",
                    "insert into biz_public_expense_project values(99,5)"
                })sql.execute(statement);
            }
            Configuration config=new Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            try(InputStream stream=getClass().getResourceAsStream("/mapper/business/BusinessPublicExpenseMapper.xml")) {
                com.ruoyi.business.CompanyAccessTestSupport.register(config);new XMLMapperBuilder(stream,config,"public-expenses",config.getSqlFragments()).parse();
            }
            try(org.apache.ibatis.session.SqlSession session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
                java.util.List<Map<String,Object>> rows=session.getMapper(BusinessPublicExpenseMapper.class).selectRetainedDailyCosts(10L);
                assertEquals(3,rows.size());assertEquals(2L,((Number)rows.get(0).get("projectId")).longValue());
                assertEquals(new java.math.BigDecimal("230.33"),rows.get(0).get("amount"));
                assertEquals(new java.math.BigDecimal("260.33"),rows.stream().map(r->(java.math.BigDecimal)r.get("amount")).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
            }
        }
    }
    @Test void recognitionQueueExcludesDeletedAndClosedHistoryButStillRepairsOpenProjects() throws Exception
    {
        org.h2.jdbcx.JdbcDataSource source=new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:public_recognition_"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(java.sql.Connection connection=source.getConnection();java.sql.Statement sql=connection.createStatement()){
            for(String statement:new String[]{
                "create alias date_format for \"com.ruoyi.business.mapper.BusinessBossChartsMapperTest.dateFormat\"",
                "create table biz_project(project_id bigint primary key,del_flag char(1),accounting_state varchar(20))",
                "create table biz_public_expense_daily(bill_id bigint,project_id bigint,biz_date date,amount decimal(20,2))",
                "create table biz_project_daily_result(result_id bigint,project_id bigint,biz_date date,is_current char(1),public_cost decimal(20,2))",
                "insert into biz_project values(1,'0','OPEN'),(2,'2','OPEN'),(3,'0','CLOSED'),(4,'0','OPEN'),(5,'0',null)",
                "insert into biz_public_expense_daily values(10,1,current_date(),10),(10,2,current_date(),20),(10,3,current_date(),30),(10,4,current_date(),40),(10,5,current_date(),50)",
                "insert into biz_project_daily_result values(1,1,current_date(),'1',1),(2,2,current_date(),'1',2),(3,3,current_date(),'1',3),(4,4,current_date(),'1',40)"
            })sql.execute(statement);
        }
        Configuration config=new Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
        try(InputStream stream=getClass().getResourceAsStream("/mapper/business/BusinessPublicExpenseMapper.xml")){
            com.ruoyi.business.CompanyAccessTestSupport.register(config);new XMLMapperBuilder(stream,config,"public-expenses",config.getSqlFragments()).parse();
        }
        try(org.apache.ibatis.session.SqlSession session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()){
            java.util.List<Map<String,Object>> rows=session.getMapper(BusinessPublicExpenseMapper.class).selectUnrecognizedDailyDates(10L);
            java.util.Set<Long> ids=new java.util.HashSet<>();for(Map<String,Object> row:rows)ids.add(((Number)row.get("projectId")).longValue());
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(1L,5L)),ids);
            try(java.sql.Statement sql=session.getConnection().createStatement();java.sql.ResultSet history=sql.executeQuery("select amount from biz_public_expense_daily where project_id=2")){
                assertTrue(history.next());assertEquals(0,new java.math.BigDecimal("20.00").compareTo(history.getBigDecimal(1)));
            }
        }
    }
    @Test void mapperParsesAndMissingMonthlyBillsCannotBypassClosing() throws Exception
    {
        Configuration config=new Configuration();try(InputStream stream=getClass().getResourceAsStream("/mapper/business/BusinessPublicExpenseMapper.xml")){assertNotNull(stream);com.ruoyi.business.CompanyAccessTestSupport.register(config);new XMLMapperBuilder(stream,config,"public-expenses",config.getSqlFragments()).parse();}
        Map<String,Object> args=new HashMap<>();args.put("projectId",1L);args.put("month","2025-02");
        String pending=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".countProjectPending").getBoundSql(args).getSql();
        assertTrue(pending.contains("biz_public_expense_policy"));assertTrue(pending.contains("not exists(select 1 from biz_public_expense_month"));assertTrue(pending.contains("p.base_currency=policy.currency"));
        String read=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".readProjectCosts").getBoundSql(args).getSql();assertTrue(read.contains("biz_public_expense_adjustment"));assertTrue(read.contains("hasPublishedBill"));
        String transfer=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".countProjectUnsubmitted").getBoundSql(args).getSql();assertTrue(transfer.contains("o.status!='SUBMITTED'"));assertTrue(transfer.contains("o.owner_user_id=p.main_owner_user_id"));
        for(java.lang.reflect.Method method:BusinessPublicExpenseMapper.class.getMethods())assertTrue(config.hasStatement(BusinessPublicExpenseMapper.class.getName()+"."+method.getName()),method.getName());
    }
}
