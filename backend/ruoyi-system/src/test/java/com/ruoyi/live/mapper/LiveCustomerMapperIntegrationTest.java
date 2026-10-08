package com.ruoyi.live.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveCustomerMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        String databaseName = "live_customer_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        createSchema();

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/live/LiveCustomerMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void mergeStatementsConsolidateDuplicateDailyRecords() throws Exception
    {
        execute("insert into live_customer(customer_id,streamer_id,nickname,first_seen_date,last_seen_date) "
            + "values(1,10,'primary','2026-07-01','2026-07-10'),(2,10,'old-name','2026-06-01','2026-07-20')");
        execute("insert into live_gift_record(gift_id,biz_date,streamer_id,customer_id,xu,rank_no,upload_id,confirm_status,ai_confidence,create_time) "
            + "values(1,'2026-07-10',10,1,100,2,11,'1','1',current_timestamp),"
            + "(2,'2026-07-10',10,2,50,1,12,'1','0',current_timestamp)");
        execute("insert into live_chat_contact(contact_id,biz_date,streamer_id,customer_id,upload_id,has_interaction,create_time) "
            + "values(1,'2026-07-10',10,1,11,0,current_timestamp),(2,'2026-07-10',10,2,12,1,current_timestamp)");
        execute("insert into live_follow_record(follow_id,biz_date,streamer_id,customer_id,upload_id,follow_status,create_time) "
            + "values(1,'2026-07-10',10,1,11,'followed',current_timestamp),"
            + "(2,'2026-07-10',10,2,12,'pending',current_timestamp)");
        execute("insert into live_chat_message(msg_id,streamer_id,customer_id) values(1,10,2)");
        execute("insert into live_customer_alias(alias_id,customer_id,streamer_id,nickname) values(1,2,10,'older-name')");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            LiveCustomerMapper mapper = session.getMapper(LiveCustomerMapper.class);
            mapper.mergeGiftRecords(2L, 1L);
            mapper.deleteGiftRecords(2L);
            mapper.mergeChatContacts(2L, 1L);
            mapper.deleteChatContacts(2L);
            mapper.mergeFollowRecords(2L, 1L);
            mapper.deleteFollowRecords(2L);
            mapper.updateChatMessageCustomerId(2L, 1L);
            mapper.moveAliases(2L, 1L, 10L);
            session.commit();
        }

        assertEquals(150, intValue("select xu from live_gift_record where customer_id=1"));
        assertEquals(1, intValue("select rank_no from live_gift_record where customer_id=1"));
        assertEquals(0, intValue("select count(*) from live_gift_record where customer_id=2"));
        assertEquals(1, intValue("select has_interaction from live_chat_contact where customer_id=1"));
        assertEquals("pending", stringValue("select follow_status from live_follow_record where customer_id=1"));
        assertEquals(1, intValue("select customer_id from live_chat_message where msg_id=1"));
        assertEquals(1, intValue("select customer_id from live_customer_alias where alias_id=1"));
    }

    private void createSchema() throws Exception
    {
        execute("create table live_customer(customer_id bigint auto_increment primary key,streamer_id bigint,nickname varchar(200),"
            + "profile_url varchar(300) default '',avatar_path varchar(300) default '',badge varchar(50) default '',"
            + "merged_into_id bigint,first_seen_date date,last_seen_date date,update_time timestamp)");
        execute("create table live_gift_record(gift_id bigint auto_increment primary key,biz_date date,streamer_id bigint,customer_id bigint,"
            + "xu int,rank_no int,upload_id bigint,confirm_status char(1),ai_confidence char(1),create_time timestamp,"
            + "update_by varchar(64),update_time timestamp,unique(biz_date,streamer_id,customer_id))");
        execute("create table live_chat_contact(contact_id bigint auto_increment primary key,biz_date date,streamer_id bigint,customer_id bigint,"
            + "upload_id bigint,has_interaction int,create_time timestamp,unique(biz_date,streamer_id,customer_id))");
        execute("create table live_follow_record(follow_id bigint auto_increment primary key,biz_date date,streamer_id bigint,customer_id bigint,"
            + "upload_id bigint,follow_status varchar(32),create_time timestamp,update_time timestamp,"
            + "unique(biz_date,streamer_id,customer_id))");
        execute("create table live_chat_message(msg_id bigint auto_increment primary key,streamer_id bigint,customer_id bigint)");
        execute("create table live_customer_alias(alias_id bigint auto_increment primary key,customer_id bigint,streamer_id bigint,"
            + "nickname varchar(200),source_type varchar(20),first_seen_date date,last_seen_date date,create_time timestamp)");
    }

    private void execute(String sql) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    private int intValue(String sql) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getInt(1);
        }
    }

    private String stringValue(String sql) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getString(1);
        }
    }
}
