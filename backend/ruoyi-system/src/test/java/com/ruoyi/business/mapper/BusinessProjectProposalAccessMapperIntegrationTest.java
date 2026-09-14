package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
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

public class BusinessProjectProposalAccessMapperIntegrationTest {
    private SqlSessionFactory factory;
    // H2 compatibility implementation for MySQL's hierarchy membership function.
    public static int findInSet(String value,String list){
        if(list==null)return 0;
        String[] values=list.split(",");for(int i=0;i<values.length;i++)if(values[i].equals(value))return i+1;return 0;
    }
    @BeforeEach void setup() throws Exception {
        DataSource source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:proposal_access_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection();Statement sql=c.createStatement()){
            sql.execute("create alias find_in_set for \"com.ruoyi.business.mapper.BusinessProjectProposalAccessMapperIntegrationTest.findInSet\"");
            sql.execute("create table sys_dept(dept_id bigint primary key,parent_id bigint,leader_user_id bigint,status char(1),del_flag char(1),ancestors varchar(255))");
            sql.execute("create table sys_user(user_id bigint primary key,dept_id bigint,status char(1),del_flag char(1))");
            sql.execute("insert into sys_dept values(111,100,23,'0','0','0,100'),(112,111,null,'0','0','0,100,111'),(222,100,24,'0','0','0,100')");
            sql.execute("insert into sys_user values(9,112,'0','0'),(10,111,'0','0'),(11,222,'0','0'),(12,112,'1','0')");
            sql.execute("create table biz_project_proposal(proposal_id bigint primary key,applicant_user_id bigint,create_request_key varchar(64),del_flag char(1),unique(applicant_user_id,create_request_key))");
            sql.execute("insert into biz_project_proposal values(77,9,'proposal-request-123456','0')");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        String resource="mapper/business/BusinessProjectProposalMapper.xml";
        try(InputStream xml=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();}
        factory=new SqlSessionFactoryBuilder().build(config);
    }
    @Test void companyRatesAllowOwnCompanyDescendantsAndLeaderOnly(){
        try(SqlSession session=factory.openSession()){
            BusinessProjectProposalMapper mapper=session.getMapper(BusinessProjectProposalMapper.class);
            assertEquals(1,mapper.canReadCompanyRates(9L,111L));
            assertEquals(1,mapper.canReadCompanyRates(10L,111L));
            assertEquals(1,mapper.canReadCompanyRates(23L,111L));
            assertEquals(0,mapper.canReadCompanyRates(11L,111L));
            assertEquals(0,mapper.canReadCompanyRates(12L,111L));
            assertEquals(0,mapper.canReadCompanyRates(9L,222L));
        }
    }
    @Test void createRequestsAreScopedByApplicantAndReservedAfterDeletion() throws Exception {
        try(SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()){
            BusinessProjectProposalMapper mapper=session.getMapper(BusinessProjectProposalMapper.class);
            assertEquals(9L,mapper.lockCreateApplicant(9L));
            assertEquals(77L,mapper.selectCreateRequest(9L,"proposal-request-123456").get("proposalId"));
            assertNull(mapper.selectCreateRequest(11L,"proposal-request-123456"));
            assertThrows(SQLException.class,()->sql.execute("insert into biz_project_proposal values(78,9,'proposal-request-123456','0')"));
            sql.execute("insert into biz_project_proposal values(79,11,'proposal-request-123456','0')");
            sql.execute("update biz_project_proposal set del_flag='2' where proposal_id=77");session.clearCache();
            assertEquals("2",mapper.selectCreateRequest(9L,"proposal-request-123456").get("delFlag"));
            assertThrows(SQLException.class,()->sql.execute("insert into biz_project_proposal values(80,9,'proposal-request-123456','0')"));
        }
    }
}
