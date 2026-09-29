package com.ruoyi.business.mapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the chart SQL against isolated fixtures, including the actual company grant fragment. */
public class BusinessBossChartsMapperTest {
    SqlSession session;BusinessAccountingMapper mapper;
    public static String dateFormat(java.sql.Date date,String pattern) {
        return date.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern(pattern.equals("%Y-%m")?"yyyy-MM":"yyyy-MM-dd"));
    }
    @BeforeEach void setup() throws Exception {
        Configuration configuration=new Configuration(new Environment("charts",new JdbcTransactionFactory(),
            new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE","sa","")));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.business.domain");
        com.ruoyi.business.CompanyAccessTestSupport.register(configuration);
        String resource="mapper/business/BusinessAccountingMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(in,configuration,resource,configuration.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(configuration).openSession(false);mapper=session.getMapper(BusinessAccountingMapper.class);
        sql("create alias date_format for \"com.ruoyi.business.mapper.BusinessBossChartsMapperTest.dateFormat\"");
        sql("create table biz_project(project_id bigint primary key,project_name varchar(80),company_dept_id bigint,base_currency varchar(8),del_flag char(1))");
        sql("create table biz_project_daily_result(project_id bigint,company_dept_id bigint,biz_date date,is_current char(1),revenue_amount decimal(18,4),cost_amount decimal(18,4),personnel_cost decimal(18,4),bonus_cost decimal(18,4),public_cost decimal(18,4),profit_amount decimal(18,4))");
        com.ruoyi.business.CompanyAccessTestSupport.grant(session.getConnection(),110,126);
        com.ruoyi.business.CompanyAccessTestSupport.grant(session.getConnection(),111,127);
        sql("insert into biz_project values(1,'First',110,'CNY','0'),(2,'Second',110,'VND','0'),(3,'Other company',111,'CNY','0'),(4,'Deleted',110,'CNY','2'),(5,'Reversal',110,'CNY','0')");
        sql("insert into biz_project_daily_result values(1,110,'2026-09-28','1',100,10,20,3,4,63),(1,110,'2026-09-28','0',999,999,999,0,0,999),(1,110,'2026-09-27','1',0,0,0,0,0,0),(1,110,'2026-08-31','1',10,5,0,0,0,5),(2,110,'2026-09-28','1',1000,200,0,0,0,800),(3,111,'2026-09-28','1',9000,100,0,0,0,8900),(4,110,'2026-09-28','1',8888,100,0,0,0,8788),(5,110,'2026-09-28','1',0,-7,0,0,0,7)");
    }
    void sql(String query)throws Exception { try(Statement statement=session.getConnection().createStatement()){statement.execute(query);} }
    @AfterEach void close(){if(session!=null){session.rollback(true);session.close();}}
    Map<String,Object> query(long company,long user,boolean admin) {
        Map<String,Object> result=new HashMap<>();result.put("dateFrom","2026-09-01");result.put("dateTo","2026-09-28");
        result.put("companyDeptId",company);result.put("userId",user);result.put("viewAll",admin);return result;
    }
    @Test void dailyRowsPreserveZeroAndCurrencyWithoutCreatingMissingDays() {
        List<Map<String,Object>> rows=mapper.selectBossChartTrend(query(110,126,false));
        assertEquals(3,rows.size());assertEquals("2026-09-27",rows.get(0).get("bucket"));
        assertEquals(new BigDecimal("0.0000"),rows.get(0).get("costAmount"));
        assertEquals(new BigDecimal("30.0000"),rows.get(1).get("costAmount"));
        assertEquals(new BigDecimal("70.0000"),rows.get(1).get("profitAmount"));
        assertEquals("VND",rows.get(2).get("currency"));
        assertEquals(new BigDecimal("1000.0000"),rows.get(2).get("revenueAmount"));
    }
    @Test void costSharesMatchTrendAndPreserveNegativeReversal() {
        List<Map<String,Object>> rows=mapper.selectBossChartProjects(query(110,126,false));
        assertEquals(3,rows.size());assertEquals(new BigDecimal("37.0000"),rows.get(0).get("costAmount"));
        assertEquals(new BigDecimal("-7.0000"),rows.get(1).get("costAmount"));
        assertEquals(new BigDecimal("30.0000"),((BigDecimal)rows.get(0).get("costAmount")).add((BigDecimal)rows.get(1).get("costAmount")));
    }
    @Test void calendarRangeDoesNotLeakPreviousMonthOrLaterDays() {
        Map<String,Object> query=query(110,126,false);
        query.put("dateTo","2026-09-27");
        List<Map<String,Object>> rows=mapper.selectBossChartTrend(query);
        assertEquals(1,rows.size());assertEquals("2026-09-27",rows.get(0).get("bucket"));
        assertEquals(new BigDecimal("0.0000"),rows.get(0).get("costAmount"));
        List<Map<String,Object>> projects=mapper.selectBossChartProjects(query);
        assertEquals(1,projects.size());assertEquals(new BigDecimal("0.0000"),projects.get(0).get("costAmount"));
    }
    @Test void requestingAnotherCompanyDoesNotExpandOwnerGrantButAdminCanReadIt() {
        assertTrue(mapper.selectBossChartTrend(query(111,126,false)).isEmpty());
        assertTrue(mapper.selectBossChartProjects(query(111,126,false)).isEmpty());
        assertEquals(1,mapper.selectBossChartTrend(query(111,127,false)).size());
        assertEquals(1,mapper.selectBossChartProjects(query(111,1,true)).size());
    }
    @Test void multiMonthBucketsReconcileWithProjectTotalsAndKeepCurrenciesSeparate() {
        Map<String,Object> query=query(110,126,false);query.put("dateFrom","2026-08-01");query.put("monthly",true);
        List<Map<String,Object>> rows=mapper.selectBossChartTrend(query);
        assertEquals(3,rows.size());assertEquals("2026-08",rows.get(0).get("bucket"));assertEquals("2026-09",rows.get(1).get("bucket"));
        assertEquals(new BigDecimal("5.0000"),rows.get(0).get("costAmount"));
        assertEquals(new BigDecimal("30.0000"),rows.get(1).get("costAmount"));
        assertEquals("VND",rows.get(2).get("currency"));
        BigDecimal net=mapper.selectBossChartProjects(query).stream().filter(row->"CNY".equals(row.get("currency")))
            .map(row->(BigDecimal)row.get("costAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(new BigDecimal("35.0000"),net);
    }
    @Test void revenueSharesMatchMonthlyTrendIncludingRefundsAndExcludeOldVersions() throws Exception {
        sql("insert into biz_project_daily_result values(5,110,'2026-09-27','1',-25,0,0,0,0,-25)");
        Map<String,Object> query=query(110,126,false);query.put("dateFrom","2026-08-01");query.put("monthly",true);
        List<Map<String,Object>> projects=mapper.selectBossChartProjects(query);
        assertEquals(new BigDecimal("110.0000"),projects.get(0).get("revenueAmount"));
        assertEquals(new BigDecimal("-25.0000"),projects.get(1).get("revenueAmount"));
        BigDecimal projectTotal=projects.stream().filter(row->"CNY".equals(row.get("currency")))
            .map(row->(BigDecimal)row.get("revenueAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal trendTotal=mapper.selectBossChartTrend(query).stream().filter(row->"CNY".equals(row.get("currency")))
            .map(row->(BigDecimal)row.get("revenueAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(new BigDecimal("85.0000"),projectTotal);assertEquals(trendTotal,projectTotal);
        assertEquals(new BigDecimal("1000.0000"),projects.stream().filter(row->"VND".equals(row.get("currency"))).findFirst().get().get("revenueAmount"));
    }

}
