package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessIncentiveAward;

class BusinessIncentiveAwardRuleDetailsTest
{
    @Test void allocationProposalPersistsWithAwardAndIsVisibleWithoutASeparateApprovedBatch() throws Exception
    {
        try(SqlSession session=factory().openSession())
        {
            BusinessIncentiveMapper mapper=session.getMapper(BusinessIncentiveMapper.class);
            BusinessIncentiveAward award=mapper.selectAward(21L);assertNull(award.getApplicationAllocation());
            award.setAwardId(null);award.setStatus("DRAFT");award.setRequestKey("combined-request");
            com.ruoyi.business.domain.BusinessBonusAllocation proposal=new com.ruoyi.business.domain.BusinessBonusAllocation();proposal.setMode("AMOUNT");proposal.setReason("分配说明");proposal.setAmount(new BigDecimal("4000.00"));
            com.ruoyi.business.domain.BusinessBonusAllocationLine line=new com.ruoyi.business.domain.BusinessBonusAllocationLine();line.setUserId(9L);line.setUserName("负责人");line.setAmount(new BigDecimal("4000.00"));line.setReason("参与贡献");proposal.setLines(java.util.Collections.singletonList(line));award.setApplicationAllocation(proposal);
            mapper.insertAward(award);session.clearCache();
            BusinessIncentiveAward saved=mapper.selectAward(award.getAwardId());assertEquals("DRAFT",saved.getStatus());
            assertEquals(new BigDecimal("4000.00"),saved.getApplicationAllocation().getAmount());
            assertEquals("参与贡献",saved.getApplicationAllocation().getLines().get(0).getReason());
            String json=new ObjectMapper().writeValueAsString(saved);assertTrue(json.contains("applicationAllocation"));assertFalse(json.contains("allocationProposalJson"));
        }
        BusinessIncentiveAward spoof=new ObjectMapper().readValue("{\"allocationProposalJson\":\"{}\"}",BusinessIncentiveAward.class);assertNull(spoof.getApplicationAllocation());
    }
    @Test void awardReadsItsExactRetiredRuleVersionInsteadOfLatestPublishedValues() throws Exception
    {
        try (SqlSession session = factory().openSession())
        {
            BusinessIncentiveMapper mapper = session.getMapper(BusinessIncentiveMapper.class);
            BusinessIncentiveAward award = mapper.selectAward(21L);
            assertEquals(new BigDecimal("10000.00"), award.getRuleAfterTaxProfit());
            assertEquals(new BigDecimal("40.0000"), award.getRuleMainOwnerBonusRate());
            assertEquals(new BigDecimal("60.0000"), award.getRuleSponsorOwnerBonusRate());
            assertEquals(new BigDecimal("4000.00"), award.getRuleMainOwnerBonusAmount());
            assertEquals(new BigDecimal("6000.00"), award.getRuleSponsorOwnerBonusAmount());
            assertEquals("原方案依据", award.getRuleReason());
            assertEquals("申请说明", award.getReason());
            assertEquals("原方案依据", mapper.selectAwards(1L).get(0).getRuleReason());
            assertEquals("原方案依据", mapper.selectAwardForUpdate(21L).getRuleReason());
        }
    }

    @Test void mismatchedProjectVersionPolicyOrCurrencyDoesNotExposeAnotherRuleSnapshot() throws Exception
    {
        try (SqlSession session = factory().openSession(); Statement sql = session.getConnection().createStatement())
        {
            BusinessIncentiveMapper mapper = session.getMapper(BusinessIncentiveMapper.class);
            for (String change : new String[]{"rule_version=2", "project_id=2", "policy_version='FIXED_V1'", "currency='USD'"})
            {
                sql.execute("update biz_incentive_award set project_id=1,rule_version=1,policy_version='PROFIT_SHARE_V1',currency='CNY' where award_id=21");
                sql.execute("update biz_incentive_award set " + change + " where award_id=21");
                session.clearCache();
                BusinessIncentiveAward award = mapper.selectAward(21L);
                assertNull(award.getRuleReason()); assertNull(award.getRuleAfterTaxProfit());
                assertNull(award.getRuleMainOwnerBonusAmount()); assertNull(award.getRuleSponsorOwnerBonusAmount());
            }
        }
    }

    @Test void splitAmountsUseHalfUpRoundingAndReadOnlyFieldsIgnoreClientOverrides() throws Exception
    {
        BusinessIncentiveAward award = new BusinessIncentiveAward(); award.setPolicyVersion("PROFIT_SHARE_V1");
        award.setRuleAfterTaxProfit(new BigDecimal("1.15")); award.setRuleMainOwnerBonusRate(new BigDecimal("10"));
        assertEquals(new BigDecimal("0.12"), award.getRuleMainOwnerBonusAmount());
        award.setRuleAfterTaxProfit(new BigDecimal("-1.15")); assertEquals(new BigDecimal("0.00"), award.getRuleMainOwnerBonusAmount());
        award.setRuleSponsorOwnerBonusRate(null); assertNull(award.getRuleSponsorOwnerBonusAmount());
        BusinessIncentiveAward input = new ObjectMapper().readValue("{\"ruleAfterTaxProfit\":999999,\"ruleMainOwnerBonusRate\":100,\"ruleSponsorOwnerBonusRate\":100,\"ruleReason\":\"伪造依据\"}", BusinessIncentiveAward.class);
        assertNull(input.getRuleAfterTaxProfit()); assertNull(input.getRuleMainOwnerBonusRate());
        assertNull(input.getRuleSponsorOwnerBonusRate()); assertNull(input.getRuleReason());
    }

    private SqlSessionFactory factory() throws Exception
    {
        DataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:award_details_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement())
        {
            sql.execute("create table biz_incentive_rule(rule_id bigint,project_id bigint,rule_version int,policy_version varchar(32),currency varchar(3),after_tax_profit decimal(18,2),main_owner_bonus_rate decimal(7,4),sponsor_owner_bonus_rate decimal(7,4),reason varchar(500),status varchar(20))");
            sql.execute("create table biz_incentive_award(award_id bigint auto_increment,project_id bigint,company_dept_id bigint,rule_id bigint,rule_version int,rule_name varchar(100),policy_version varchar(32),settlement_id bigint,score_snapshot decimal(6,2),amount decimal(18,2),currency varchar(3),biz_date date,reason varchar(500),request_key varchar(64),status varchar(20),applicant_user_id bigint,applicant_user_name varchar(100),approved_user_id bigint,approved_user_name varchar(100),approved_time timestamp,accounting_fact_id bigint,review_comment varchar(500),version int,create_by varchar(100),create_time timestamp,update_by varchar(100),update_time timestamp)");
            sql.execute("alter table biz_incentive_award add column allocation_proposal_json clob");
            sql.execute("create table biz_operating_fact(fact_id bigint,status varchar(20))");
            sql.execute("create table sys_user(user_id bigint,nick_name varchar(100),user_name varchar(100),del_flag char(1))");
            sql.execute("insert into biz_incentive_rule values(11,1,1,'PROFIT_SHARE_V1','CNY',10000,40,60,'原方案依据','RETIRED'),(12,1,2,'PROFIT_SHARE_V1','CNY',99000,10,20,'新版方案依据','ACTIVE')");
            sql.execute("insert into biz_incentive_award(award_id,project_id,company_dept_id,rule_id,rule_version,rule_name,policy_version,amount,currency,biz_date,reason,status,applicant_user_id,applicant_user_name,version) values(21,1,110,11,1,'原方案','PROFIT_SHARE_V1',10000,'CNY','2026-06-30','申请说明','SUBMITTED',9,'owner',0)");
        }
        Configuration configuration = new Configuration(new Environment("details", new JdbcTransactionFactory(), source));
        com.ruoyi.business.CompanyAccessTestSupport.register(configuration);
        String resource = "mapper/business/BusinessIncentiveMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        { new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse(); }
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
