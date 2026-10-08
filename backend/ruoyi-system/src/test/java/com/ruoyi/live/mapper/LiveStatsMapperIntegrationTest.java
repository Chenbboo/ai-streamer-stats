package com.ruoyi.live.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.math.BigDecimal;
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

class LiveStatsMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        String databaseName = "live_stats_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        createSchema();

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/live/LiveStatsMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void revenueUsesDailyReportWhileGiftAmountRemainsCustomerAnalysisData() throws Exception
    {
        execute("insert into live_streamer(streamer_id,stage_name,status) values(1,'Streamer A','0')");
        execute("insert into live_daily_report(report_id,biz_date,streamer_id,total_xu) values(1,'2026-07-14',1,10000)");
        execute("insert into live_gift_record(gift_id,biz_date,streamer_id,customer_id,xu) "
            + "values(1,'2026-07-14',1,101,5000),(2,'2026-07-14',1,102,3000)");
        execute("insert into live_chat_contact(contact_id,biz_date,streamer_id,customer_id) "
            + "values(1,'2026-07-14',1,101),(2,'2026-07-14',1,103)");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            LiveStatsMapper mapper = session.getMapper(LiveStatsMapper.class);
            List<Map<String, Object>> cards = mapper.selectStreamerCards("2026-07-14", "2026-07-14", null);

            assertEquals(1, cards.size());
            assertEquals(new BigDecimal("10000"), decimal(value(cards.get(0), "totalXu")));
            assertEquals(new BigDecimal("8000"), decimal(value(cards.get(0), "giftXu")));
            assertEquals(2, ((Number) value(cards.get(0), "giftCustomers")).intValue());
            assertEquals(2, ((Number) value(cards.get(0), "chatCustomers")).intValue());
        }
    }

    private void createSchema() throws Exception
    {
        execute("create table live_streamer(streamer_id bigint primary key,stage_name varchar(100),status char(1))");
        execute("create table live_daily_report(report_id bigint primary key,biz_date date,streamer_id bigint,total_xu int)");
        execute("create table live_gift_record(gift_id bigint primary key,biz_date date,streamer_id bigint,customer_id bigint,xu int)");
        execute("create table live_chat_contact(contact_id bigint primary key,biz_date date,streamer_id bigint,customer_id bigint)");
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
        {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    private BigDecimal decimal(Object value)
    {
        return new BigDecimal(String.valueOf(value));
    }
}
