package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
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
import com.ruoyi.business.domain.BusinessProjectProposal;

class BusinessProjectProposalFundingMapperIntegrationTest
{
    private SqlSessionFactory factory;

    @BeforeEach
    void setup() throws Exception
    {
        DataSource source=new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:proposal_funding_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        try(Connection connection=source.getConnection();Statement sql=connection.createStatement())
        {
            sql.execute("create table biz_project(project_id bigint primary key,project_name varchar(160),budget_mode varchar(16),budget_limit decimal(20,2),base_currency varchar(3),parent_id bigint,del_flag char(1),status varchar(16))");
            sql.execute("create table biz_project_proposal(proposal_id bigint primary key,parent_project_id bigint,status varchar(16),del_flag char(1))");
            sql.execute("create table biz_project_subproject_funding(proposal_id bigint primary key,parent_project_id bigint,amount decimal(20,2),reason varchar(500),create_by varchar(64),create_time timestamp,update_by varchar(64),update_time timestamp)");
            sql.execute("insert into biz_project values(1,'主项目','TOTAL',1000,'CNY',null,'0','ACTIVE')");
            sql.execute("insert into biz_project_proposal values(11,1,'APPROVED','0'),(12,1,'DRAFT','0'),(13,1,'DRAFT','0')");
            sql.execute("insert into biz_project_subproject_funding(proposal_id,parent_project_id,amount,reason) values(11,1,200,'已启动'),(12,1,100,'当前草稿'),(13,1,50,'其他草稿')");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        String resource="mapper/business/BusinessProjectProposalMapper.xml";
        try(InputStream xml=Resources.getResourceAsStream(resource))
        {
            com.ruoyi.business.CompanyAccessTestSupport.register(config);
            new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();
        }
        factory=new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void summarySeparatesApprovedAndReservedAndExcludesCurrentDraft() throws Exception
    {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement())
        {
            BusinessProjectProposalMapper mapper=session.getMapper(BusinessProjectProposalMapper.class);
            Map<String,Object> summary=mapper.selectParentFundingSummary(1L,12L);
            assertEquals(new BigDecimal("200.00"),summary.get("activeAllocatedAmount"));
            assertEquals(new BigDecimal("50.00"),summary.get("reservedAllocatedAmount"));

            BusinessProjectProposal proposal=new BusinessProjectProposal();
            proposal.setProposalId(12L);proposal.setParentProjectId(1L);
            proposal.setParentFundingAmount(new BigDecimal("125"));proposal.setParentFundingReason("调整后拨款");
            proposal.setUpdateBy("owner");
            assertEquals(2,mapper.upsertParentFunding(proposal));
            try(ResultSet row=sql.executeQuery("select amount,reason from biz_project_subproject_funding where proposal_id=12"))
            {
                row.next();assertEquals(new BigDecimal("125.00"),row.getBigDecimal("amount"));assertEquals("调整后拨款",row.getString("reason"));
            }
        }
    }
}
