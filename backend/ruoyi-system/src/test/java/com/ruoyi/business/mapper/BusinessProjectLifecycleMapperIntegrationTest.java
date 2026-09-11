package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;

/** Executes the production mapper XML against a disposable MySQL-compatible database. */
class BusinessProjectLifecycleMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sessionFactory;

    @BeforeEach void setUp() throws Exception
    {
        String url = "jdbc:h2:mem:project_lifecycle_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        createSchema();
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String name : new String[] { "BusinessProjectMapper", "BusinessAccountingMapper", "BusinessProjectKpiMapper" })
        {
            String resource = "mapper/business/" + name + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test void deletingAChildPreservesParentAndHistoryAndRejectsStaleVersion() throws Exception
    {
        insertProject(1, "LEGACY_V1", "DRAFT", "OPEN", 7, "0");
        insertProject(2, "LEGACY_V1", "DRAFT", "OPEN", 7, "0");
        execute("update biz_project set parent_id=1 where project_id=2");
        try (SqlSession session = sessionFactory.openSession(false))
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            assertEquals(1, mapper.countSubprojects(1L));
            assertEquals(0, mapper.softDeleteProject(2L, 6, "boss"));
            assertEquals(1, mapper.softDeleteProject(2L, 7, "boss"));
            assertEquals(0, mapper.countSubprojects(1L));
            assertNull(mapper.selectProjectByIdForUpdate(2L));
            assertEquals(7, mapper.selectProjectByIdForUpdate(1L).getVersion());
            assertEquals(0, mapper.softDeleteProject(2L, 7, "boss"));
        }
    }

    @Test void lockingProjectReadsCarryPoliciesAccountingStateAndDeliveryDate() throws Exception
    {
        insertProject(1, "SEPARATED_V1", "CLOSED", "OPEN", 7, "0");
        try (SqlSession session = sessionFactory.openSession(false))
        {
            BusinessProject project = session.getMapper(BusinessProjectMapper.class).selectProjectByIdForUpdate(1L);
            assertEquals("SEPARATED_V1", project.getDeliveryPolicyVersion());
            assertEquals("OWNER_CONFIRM_V1", project.getSettlementPolicyVersion());
            assertEquals("PERCENTAGE_V1", project.getCostPolicyVersion());
            assertEquals("OPEN", project.getAccountingState());
            assertEquals("CLOSED", project.getStatus());
            assertEquals(Date.valueOf("2026-07-20"), project.getActualEndDate());

            BusinessAccountingMapper accounting = session.getMapper(BusinessAccountingMapper.class);
            Map<String, Object> locked = accounting.selectProjectForAccountingForUpdate(1L);
            assertEquals("SEPARATED_V1", value(locked, "deliveryPolicyVersion"));
            assertEquals("OWNER_CONFIRM_V1", value(locked, "settlementPolicyVersion"));
            assertEquals("PERCENTAGE_V1", value(locked, "costPolicyVersion"));
            assertEquals("OPEN", value(locked, "accountingState"));
            assertEquals(Date.valueOf("2026-07-20"), value(locked, "actualEndDate"));
            assertEquals(8L, ((Number) value(locked, "initiatorUserId")).longValue());
            assertEquals(9L, ((Number) value(locked, "mainOwnerUserId")).longValue());
            assertEquals(value(locked, "accountingState"), value(accounting.selectProjectForAccounting(1L), "accountingState"));
            assertNull(accounting.selectProjectForAccountingForUpdate(999L));
        }
    }

    @Test void closeAccountingRequiresMatchingVersionSeparatedPolicyAndTerminalDelivery() throws Exception
    {
        insertProject(1, "SEPARATED_V1", "CLOSED", "OPEN", 7, "0");
        insertProject(2, "LEGACY_V1", "CLOSED", "OPEN", 7, "0");
        insertProject(3, "SEPARATED_V1", "ACTIVE", "OPEN", 7, "0");
        insertProject(4, "SEPARATED_V1", "CANCELED", "OPEN", 7, "0");
        insertProject(5, "SEPARATED_V1", "CLOSED", "OPEN", 7, "2");
        insertProject(6, "SEPARATED_V1", "CLOSED", "CLOSED", 7, "0");
        try (SqlSession session = sessionFactory.openSession(false))
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            assertEquals(0, mapper.closeAccounting(1L, 6, "finance"));
            assertEquals(0, mapper.closeAccounting(2L, 7, "finance"));
            assertEquals(0, mapper.closeAccounting(3L, 7, "finance"));
            assertEquals(0, mapper.closeAccounting(5L, 7, "finance"));
            assertEquals(0, mapper.closeAccounting(6L, 7, "finance"));
            assertEquals(1, mapper.closeAccounting(1L, 7, "finance"));
            assertEquals(0, mapper.closeAccounting(1L, 7, "finance"));
            assertEquals(0, mapper.closeAccounting(1L, 8, "finance"));
            assertEquals(1, mapper.closeAccounting(4L, 7, "finance"));
            BusinessProject closed = mapper.selectProjectByIdForUpdate(1L);
            assertEquals("CLOSED", closed.getStatus());
            assertEquals("CLOSED", closed.getAccountingState());
            assertEquals(Integer.valueOf(8), closed.getVersion());
            assertEquals("finance", closed.getUpdateBy());
            assertNotNull(closed.getUpdateTime());
            assertEquals(Date.valueOf("2026-07-20"), closed.getActualEndDate());
            assertEquals("CANCELED", mapper.selectProjectByIdForUpdate(4L).getStatus());
            session.commit();
        }
    }

    @Test void pendingKpiIncludesUnresolvedPlansAndClearsOnlyOnConfirmationOrVoid() throws Exception
    {
        execute("insert into biz_project_kpi_plan(plan_id,project_id,status) values"
            + "(10,1,'DRAFT'),(11,1,'PUBLISHED'),(12,1,'PUBLISHED'),(13,1,'PUBLISHED'),"
            + "(14,1,'CLOSED'),(15,1,'VOIDED'),(16,1,'CLOSED'),(17,1,'PUBLISHED'),(18,2,'PUBLISHED')");
        insertSettlement(201, 11, 1, "DRAFT");
        insertSettlement(202, 12, 1, "RETURNED");
        insertSettlement(203, 13, 1, "SUBMITTED");
        insertSettlement(204, 14, 1, "CONFIRMED");
        insertSettlement(205, 15, 1, "DRAFT");
        insertSettlement(207, 17, 1, "VOIDED");
        insertSettlement(208, 18, 2, "DRAFT");
        try (SqlSession session = sessionFactory.openSession(false))
        {
            BusinessProjectMapper projects = session.getMapper(BusinessProjectMapper.class);
            BusinessProjectKpiMapper kpi = session.getMapper(BusinessProjectKpiMapper.class);
            // A draft plan, three unsettled published plans, and two incomplete records remain pending.
            assertEquals(6, projects.countPendingProjectKpi(1L));
            assertEquals(1, projects.countPendingProjectKpi(2L));
            assertEquals(1, kpi.voidDraftSettlement(11L, 8L, "boss8"));
            assertEquals(1, kpi.voidPublishedPlan(11L, 8L, "boss8"));
            assertEquals(5, projects.countPendingProjectKpi(1L));
            assertEquals(1, kpi.confirmSettlement(203L, BigDecimal.TEN, BigDecimal.ZERO,
                null, "confirmed", 8L, "boss8", 0));
            assertEquals(1, kpi.closePlan(13L));
            assertEquals(4, projects.countPendingProjectKpi(1L));
            assertEquals(0, kpi.confirmSettlement(203L, BigDecimal.TEN, BigDecimal.ZERO,
                null, "duplicate", 8L, "boss8", 0));
        }
    }

    @Test void includedFactAndSettlementQueriesExecuteAndPreserveReviewIdentity() throws Exception
    {
        execute("insert into biz_operating_fact(fact_id,project_id,company_dept_id,biz_date,fact_kind,amount,"
            + "currency,status,version,idempotency_key,source_domain,source_type,source_id) values"
            + "(71,1,110,'2026-07-31','COST',30000.00,'CNY','CONFIRMED',2,"
            + "'KPI-BONUS-SETTLEMENT-20','KPI','PROJECT_BONUS','20')");
        insertSettlement(20, 10, 1, "SUBMITTED");
        insertSettlement(21, 11, 1, "VOIDED");
        execute("update biz_project_kpi_settlement set reviewed_user_id=8,reviewed_user_name='boss8',"
            + "review_comment='legacy review' where settlement_id=20");
        try (SqlSession session = sessionFactory.openSession(false))
        {
            BusinessAccountingMapper accounting = session.getMapper(BusinessAccountingMapper.class);
            BusinessOperatingFact fact = accounting.selectFactByIdForUpdate(71L);
            assertEquals(1L, fact.getProjectId().longValue());
            assertEquals(new BigDecimal("30000.00"), fact.getAmount());
            assertEquals("KPI-BONUS-SETTLEMENT-20", fact.getIdempotencyKey());
            assertEquals(Integer.valueOf(2), fact.getVersion());
            assertEquals(fact.getFactId(), accounting.selectFactById(71L).getFactId());
            assertNull(accounting.selectFactByIdForUpdate(999L));

            BusinessProjectKpiMapper kpi = session.getMapper(BusinessProjectKpiMapper.class);
            BusinessProjectKpiSettlement settlement = kpi.selectSettlementByIdForUpdate(20L);
            assertEquals("SUBMITTED", settlement.getStatus());
            assertEquals(8L, settlement.getReviewedUserId().longValue());
            assertEquals("legacy review", settlement.getReviewComment());
            assertEquals(Date.valueOf("2026-07-31"), settlement.getPeriodEnd());
            assertEquals(settlement.getSettlementId(), kpi.selectSettlementById(20L).getSettlementId());
            assertNull(kpi.selectSettlementByIdForUpdate(21L));
            assertNull(kpi.selectSettlementById(21L));
            Map<String, Object> source = accounting.selectProjectBonusSettlement(20L);
            assertEquals(1L, ((Number) value(source, "projectId")).longValue());
            assertEquals(Date.valueOf("2026-07-31"), value(source, "periodEnd"));
            assertEquals(1, accounting.countProjectSettlementDate(1L, Date.valueOf("2026-07-31")));
            assertEquals(0, accounting.countProjectSettlementDate(1L, Date.valueOf("2026-08-31")));
        }
    }

    @Test void accountingLockInvalidatesCachedProjectReadAfterAnotherTransactionClosesIt() throws Exception
    {
        insertProject(1, "SEPARATED_V1", "CLOSED", "OPEN", 7, "0");
        try (SqlSession reader = sessionFactory.openSession(false))
        {
            BusinessAccountingMapper accounting = reader.getMapper(BusinessAccountingMapper.class);
            assertEquals("OPEN", value(accounting.selectProjectForAccounting(1L), "accountingState"));
            try (SqlSession writer = sessionFactory.openSession(false))
            {
                assertEquals(1, writer.getMapper(BusinessProjectMapper.class).closeAccounting(1L, 7, "finance"));
                writer.commit();
            }
            assertEquals("CLOSED", value(accounting.selectProjectForAccountingForUpdate(1L), "accountingState"));
        }
    }

    @Test void projectAndAccountingMappersSerializeOnTheSameProjectRow() throws Exception
    {
        insertProject(1, "SEPARATED_V1", "CLOSED", "OPEN", 7, "0");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch attemptingLock = new CountDownLatch(1);
        try (SqlSession closer = sessionFactory.openSession(false))
        {
            BusinessProjectMapper projects = closer.getMapper(BusinessProjectMapper.class);
            assertEquals("OPEN", projects.selectProjectByIdForUpdate(1L).getAccountingState());
            Future<Map<String, Object>> waitingWriter = executor.submit(() -> {
                try (SqlSession writer = sessionFactory.openSession(false))
                {
                    attemptingLock.countDown();
                    return writer.getMapper(BusinessAccountingMapper.class).selectProjectForAccountingForUpdate(1L);
                }
            });
            assertTrue(attemptingLock.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> waitingWriter.get(150, TimeUnit.MILLISECONDS));
            assertEquals(1, projects.closeAccounting(1L, 7, "finance"));
            closer.commit();
            assertEquals("CLOSED", value(waitingWriter.get(5, TimeUnit.SECONDS), "accountingState"));
        }
        finally
        {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void createSchema() throws Exception
    {
        execute("create table biz_project(project_id bigint primary key,parent_id bigint,project_name varchar(100),company_dept_id bigint,"
            + "accounting_mode varchar(30),base_currency varchar(3),budget_limit decimal(18,2),"
            + "sponsor_owner_user_id bigint,sponsor_owner_name varchar(100),initiator_user_id bigint,initiator_name varchar(100),"
            + "main_owner_user_id bigint,main_owner_name varchar(100),status varchar(30),delivery_policy_version varchar(40),"
            + "accounting_state varchar(20),settlement_policy_version varchar(40),cost_policy_version varchar(40),template_version varchar(32),template_snapshot_json clob,"
            + "actual_start_date date,actual_end_date date,version int,del_flag char(1),update_by varchar(100),update_time timestamp)");
        execute("create table biz_project_kpi_plan(plan_id bigint primary key,project_id bigint,status varchar(30),reward_policy_version varchar(32),"
            + "voided_user_id bigint,voided_user_name varchar(100),voided_time timestamp,closed_time timestamp)");
        execute("create table biz_project_kpi_settlement(settlement_id bigint primary key,plan_id bigint,project_id bigint,"
            + "period_start date,period_end date,status varchar(30),total_score decimal(18,2),bonus_amount decimal(18,2),currency varchar(3),"
            + "submitted_user_id bigint,submitted_user_name varchar(100),submitted_time timestamp,reviewed_user_id bigint,"
            + "reviewed_user_name varchar(100),reviewed_time timestamp,review_comment varchar(500),accounting_fact_id bigint,"
            + "voided_user_id bigint,voided_user_name varchar(100),voided_time timestamp,version int default 0,"
            + "create_by varchar(100),create_time timestamp,update_by varchar(100),update_time timestamp)");
        execute("create table biz_operating_fact(fact_id bigint primary key,project_id bigint,company_dept_id bigint,biz_date date,"
            + "category_id bigint,category_code varchar(100),category_name varchar(100),fact_kind varchar(30),amount decimal(18,2),"
            + "quantity decimal(18,2),currency varchar(3),unit varchar(30),description varchar(500),counterparty varchar(100),"
            + "attachment_urls varchar(500),source_domain varchar(50),source_type varchar(50),source_id varchar(100),"
            + "source_line_key varchar(100),status varchar(30),reversal_fact_id bigint,idempotency_key varchar(150) unique,version int,"
            + "confirmed_user_id bigint,confirmed_user_name varchar(100),confirmed_time timestamp,returned_user_id bigint,"
            + "returned_user_name varchar(100),returned_time timestamp,return_reason varchar(500),create_user_id bigint,"
            + "create_by varchar(100),create_time timestamp,update_by varchar(100),update_time timestamp,remark varchar(500))");
    }

    private void insertProject(long id, String policy, String delivery, String accounting, int version, String deleted) throws Exception
    {
        execute("insert into biz_project(project_id,project_name,company_dept_id,accounting_mode,base_currency,"
            + "sponsor_owner_user_id,sponsor_owner_name,initiator_user_id,main_owner_user_id,main_owner_name,status,"
            + "delivery_policy_version,accounting_state,settlement_policy_version,cost_policy_version,"
            + "actual_start_date,actual_end_date,version,del_flag) values(" + id + ",'Project',110,'PROFIT','CNY',"
            + "8,'boss8',8,9,'owner9','" + delivery + "','" + policy + "','" + accounting + "',"
            + "'OWNER_CONFIRM_V1','PERCENTAGE_V1','2026-07-01','2026-07-20'," + version + ",'" + deleted + "')");
    }

    private void insertSettlement(long id, long planId, long projectId, String status) throws Exception
    {
        execute("insert into biz_project_kpi_settlement(settlement_id,plan_id,project_id,period_start,period_end,status,version)"
            + " values(" + id + "," + planId + "," + projectId + ",'2026-07-01','2026-07-31','" + status + "',0)");
    }

    private void execute(String sql) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    private Object value(Map<String, Object> row, String key)
    {
        for (Map.Entry<String, Object> entry : row.entrySet())
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }
}
