package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessProjectProposal;

/** Exercise pending-to-active transitions using the production mapper SQL. */
class BusinessProjectProposalLaunchMapperIntegrationTest
{
    private SqlSessionFactory factory;

    @BeforeEach
    void setup() throws Exception
    {
        DataSource source = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:proposal_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement())
        {
            sql.execute("create table biz_project_proposal (proposal_id bigint primary key, applicant_user_id bigint,"
                + "budget_limit decimal(20,2), no_budget char(1), status varchar(20), template_version varchar(32), template_snapshot_json clob,"
                + "budget_mode varchar(16),budget_scope varchar(24),daily_budget_limit decimal(20,2),startup_budget_limit decimal(20,2),budget_reason varchar(500),forecast_period varchar(16),forecast_days int,goal_mode varchar(16),"
                + "recurring_estimated_revenue decimal(20,2),recurring_estimated_external_cost decimal(20,2),recurring_estimated_total_cost decimal(20,2),recurring_expected_profit decimal(20,2),"
                + "management_mode varchar(32), close_method varchar(32), del_flag char(1), version int,"
                + "submission_version int, submitted_time timestamp, reviewed_user_id bigint, reviewed_user_name varchar(80),"
                + "reviewed_time timestamp, review_comment varchar(500), created_project_id bigint,"
                + "update_by varchar(64), update_time timestamp, planned_headcount int,"
                + "estimated_revenue decimal(20,2), estimated_external_cost decimal(20,2),"
                + "estimated_personnel_cost decimal(20,2), estimated_bonus_cost decimal(20,2),"
                + "estimated_tax_cost decimal(20,2), contingency_cost decimal(20,2),"
                + "estimated_total_cost decimal(20,2), expected_profit decimal(20,2),"
                + "expected_margin decimal(20,2), break_even_revenue decimal(20,2), peak_cash_need decimal(20,2))");
            sql.execute("insert into biz_project_proposal (proposal_id,applicant_user_id,status,template_version,"
                + "management_mode,close_method,del_flag,version,submission_version)"
                + " values (77,9,'PENDING','CONTROLLED_V1','STANDARD','RESULT_ACCEPTANCE','0',2,1)");
        }
        Configuration config = new Configuration(new Environment("proposal", new JdbcTransactionFactory(), source));
        String resource = "mapper/business/BusinessProjectProposalMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void pendingApplicantLaunchPreservesGovernanceAndPreventsDuplicateActivation() throws Exception
    {
        try (SqlSession session = factory.openSession())
        {
            BusinessProjectProposalMapper mapper = session.getMapper(BusinessProjectProposalMapper.class);
            BusinessProjectProposal proposal = new BusinessProjectProposal();
            proposal.setProposalId(77L); proposal.setApplicantUserId(9L); proposal.setVersion(2);
            proposal.setManagementMode("KEY_CONTROL"); proposal.setCloseMethod("STAGED_ACCEPTANCE");
            proposal.setTemplateSnapshotJson("{\"authorizationMode\":\"SELF_AUTHORIZED\"}");
            assertEquals(1, mapper.updateComputedPlan(proposal));
            assertEquals(1, mapper.activate(77L, 9L, 2, 88L, "Owner", "owner9"));
            assertEquals(0, mapper.activate(77L, 9L, 2, 89L, "Owner", "owner9"));
            assertEquals(0, mapper.updateComputedPlan(proposal));
            try (Statement sql = session.getConnection().createStatement();
                 ResultSet row = sql.executeQuery("select * from biz_project_proposal where proposal_id=77"))
            {
                assertTrue(row.next()); assertEquals("APPROVED", row.getString("status"));
                assertEquals(88L, row.getLong("created_project_id")); assertEquals(3, row.getInt("version"));
                assertEquals("KEY_CONTROL", row.getString("management_mode"));
                assertEquals("STAGED_ACCEPTANCE", row.getString("close_method"));
                assertEquals(proposal.getTemplateSnapshotJson(), row.getString("template_snapshot_json"));
                assertNull(row.getObject("reviewed_user_id"));
            }
        }
    }

    @Test
    void wrongApplicantAndStaleVersionCannotActivatePendingProposal()
    {
        try (SqlSession session = factory.openSession())
        {
            BusinessProjectProposalMapper mapper = session.getMapper(BusinessProjectProposalMapper.class);
            assertEquals(0, mapper.activate(77L, 23L, 2, 88L, "Sponsor", "boss23"));
            assertEquals(0, mapper.activate(77L, 9L, 1, 88L, "Owner", "owner9"));
            assertEquals(1, mapper.activate(77L, 9L, 2, 88L, "Owner", "owner9"));
        }
    }
}
