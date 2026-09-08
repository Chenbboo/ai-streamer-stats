package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.business.support.BusinessProposalParticipation;

class BusinessProjectProposalStaffingMapperIntegrationTest {
    @Test void selectedModesSurviveRealSqlSaveAndReload() throws Exception {
        DataSource source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:staffing_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection();Statement sql=c.createStatement()){
            sql.execute("create table biz_project_proposal_staffing (staffing_line_id bigint auto_increment primary key,proposal_id bigint,"
                +"user_id bigint,user_name varchar(100),role_name varchar(100),participation_mode varchar(20),headcount int,allocation_percent decimal,"
                +"plan_start_date date,plan_end_date date,person_months decimal,cost_policy_id bigint,cost_policy_version int,monthly_cost_snapshot decimal,"
                +"standard_work_days_snapshot decimal,daily_cost_snapshot decimal,cost_currency varchar(3),estimated_cost decimal,note varchar(500),"
                +"input_unit varchar(20),input_quantity decimal,calendar_id bigint,unit_policy_id bigint,sort_order int,create_time timestamp)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        String resource="mapper/business/BusinessProjectProposalMapper.xml";
        try(InputStream xml=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();}
        SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(config);
        try(SqlSession session=factory.openSession()){
            BusinessProjectProposalMapper mapper=session.getMapper(BusinessProjectProposalMapper.class);
            String[] modes={"FOLLOW_PROJECT","UNLIMITED","CUSTOM"};
            for(int i=0;i<modes.length;i++){
                Map<String,Object> row=new HashMap<>();row.put("proposalId",28L);row.put("userId",142L+i);row.put("sortOrder",i);
                row.put("participationMode",modes[i]);row.put("planStartDate",java.sql.Date.valueOf("2026-09-08"));
                // Explicit CUSTOM must remain CUSTOM even when its dates equal the project.
                row.put("planEndDate","CUSTOM".equals(modes[i])?java.sql.Date.valueOf("2026-09-30"):null);
                mapper.insertStaffingLine(row);
            }
            session.commit();session.clearCache();
            List<Map<String,Object>> rows=mapper.selectStaffingLines(28L);assertEquals(3,rows.size());
            BusinessProjectProposal p=new BusinessProjectProposal();p.setPlanStartDate(java.sql.Date.valueOf("2026-09-08"));
            p.setPlanEndDate(java.sql.Date.valueOf("2026-09-30"));
            for(int i=0;i<modes.length;i++){
                assertEquals(modes[i],rows.get(i).get("participationMode"));assertEquals(modes[i],BusinessProposalParticipation.mode(rows.get(i),p));
                assertEquals(java.sql.Date.valueOf("2026-09-08"),BusinessProposalParticipation.date(rows.get(i).get("planStartDate")));
            }
        }
    }
}
