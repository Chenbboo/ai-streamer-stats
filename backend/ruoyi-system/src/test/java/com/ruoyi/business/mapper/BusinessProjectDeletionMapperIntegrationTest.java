package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
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
import com.ruoyi.business.CompanyAccessTestSupport;

/** Exercises the production deletion SQL against isolated project and company grants. */
class BusinessProjectDeletionMapperIntegrationTest
{
    private SqlSessionFactory factory;

    @BeforeEach
    void setup() throws Exception
    {
        DataSource source = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:project_delete_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement())
        {
            sql.execute("create alias find_in_set for \"com.ruoyi.business.CompanyAccessTestSupport.findInSet\"");
            sql.execute("create table sys_user(user_id bigint primary key,status char(1),del_flag char(1))");
            sql.execute("create table sys_dept(dept_id bigint primary key,status char(1),del_flag char(1))");
            sql.execute("create table sys_role(role_id bigint primary key,role_key varchar(64),status char(1),del_flag char(1))");
            sql.execute("create table sys_user_role(user_id bigint,role_id bigint)");
            sql.execute("create table biz_company_access(company_dept_id bigint,user_id bigint,capabilities varchar(255))");
            sql.execute("create table biz_project(project_id bigint primary key,company_dept_id bigint,del_flag char(1),version int,update_by varchar(64),update_time timestamp)");
            sql.execute("create table biz_project_delete_request(request_id bigint primary key,project_id bigint,project_name varchar(200),reason varchar(500),status varchar(16),request_user_id bigint,request_user_name varchar(64),request_time timestamp,review_user_id bigint,review_user_name varchar(64),review_comment varchar(500),review_time timestamp)");
            sql.execute("create table biz_project_delete_notification(notification_id bigint auto_increment primary key,request_id bigint unique,recipient_user_id bigint,read_time timestamp,create_time timestamp)");
            sql.execute("insert into sys_user values(1,'0','0'),(8,'0','0'),(9,'0','0'),(30,'0','0'),(31,'0','0')");
            sql.execute("insert into sys_dept values(110,'0','0'),(120,'0','0')");
            sql.execute("insert into sys_role values(2,'company_owner','0','0')");
            sql.execute("insert into sys_user_role values(8,2),(9,2)");
            sql.execute("insert into biz_company_access values(110,8,'BUSINESS'),(120,9,'BUSINESS')");
            sql.execute("insert into biz_project(project_id,company_dept_id,del_flag,version) values(10,110,'0',0),(20,120,'0',0)");
            sql.execute("insert into biz_project_delete_request(request_id,project_id,project_name,reason,status,request_user_id,request_user_name,request_time) values(101,10,'甲项目','申请删除','PENDING',30,'owner',current_timestamp),(102,20,'乙项目','申请删除','PENDING',31,'owner2',current_timestamp)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        CompanyAccessTestSupport.register(configuration);
        String resource = "mapper/business/BusinessProjectMapper.xml";
        try (InputStream xml = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void adminSeesAllButBossOnlyAuthorizedCompanyAndOwnerOnlyOwnRequest() throws Exception
    {
        try (SqlSession session = factory.openSession(); Statement sql = session.getConnection().createStatement())
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            assertEquals(2, mapper.selectProjectDeletionRequests(1L, true, true).size());
            assertEquals(10L, projectId(mapper.selectProjectDeletionRequests(8L, false, true)));
            assertEquals(20L, projectId(mapper.selectProjectDeletionRequests(9L, false, true)));
            assertEquals(10L, projectId(mapper.selectProjectDeletionRequests(30L, false, false)));
            assertTrue(mapper.selectProjectDeletionRequests(31L, false, false).stream()
                .allMatch(row -> ((Number) row.get("requestUserId")).longValue() == 31L));
            sql.execute("update biz_company_access set capabilities='' where user_id=8");
            session.clearCache();
            assertTrue(mapper.selectProjectDeletionRequests(8L, false, true).isEmpty());
            assertEquals(2, mapper.selectProjectDeletionRequests(1L, true, true).size());
        }
    }

    @Test
    void firstReviewChangesStatusAndLaterReviewCannotChangeItAgain()
    {
        try (SqlSession session = factory.openSession())
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            assertEquals("PENDING", mapper.selectPendingProjectDeletion(10L).get("status"));
            assertEquals(1, mapper.reviewProjectDeletionRequest(101L, "APPROVED", "同意", 8L, "boss8"));
            assertEquals(1, mapper.insertProjectDeletionNotification(101L, 30L));
            assertEquals(0, mapper.reviewProjectDeletionRequest(101L, "REJECTED", "反对", 1L, "admin"));
            assertNull(mapper.selectPendingProjectDeletion(10L));
            assertEquals(1, mapper.softDeleteProject(10L, 0, "boss8"));
            Map<String, Object> notification = mapper.selectProjectDeletionNotifications(30L).get(0);
            assertEquals("APPROVED", notification.get("status"));
            assertEquals("甲项目", notification.get("projectName"));
            assertEquals("boss8", notification.get("reviewerName"));
            assertTrue(mapper.selectProjectDeletionNotifications(8L).isEmpty());
            Long notificationId = ((Number) notification.get("notificationId")).longValue();
            assertEquals(0, mapper.readProjectDeletionNotification(notificationId, 31L));
            assertEquals(1, mapper.readProjectDeletionNotification(notificationId, 30L));
            assertEquals(0, mapper.readAllProjectDeletionNotifications(30L));
            assertTrue(mapper.selectProjectDeletionNotifications(30L).get(0).get("readTime") != null);
            List<Map<String, Object>> remaining = mapper.selectProjectDeletionRequests(1L, true, true);
            assertEquals(1, remaining.size());
            assertEquals(20L, projectId(remaining));
        }
    }

    private long projectId(List<Map<String, Object>> rows)
    {
        assertEquals(1, rows.size());
        return ((Number) rows.get(0).get("projectId")).longValue();
    }
}
