package com.ruoyi.jewelry.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.domain.JewelryDocumentItem;

class JewelryErpMapperIntegrationTest
{
    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        String databaseName = "jewelry_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        createSchema();

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/jewelry/JewelryErpMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void singleInfluencerProductPriceIncludesConfiguredRates()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification) "
            + "values(19,'SKU-19','项链','FINISHED','普通')");
        execute("insert into jewelry_supplier(supplier_id,supplier_name) values(7,'常用供应商')");
        execute("insert into jewelry_influencer_product_price (influencer_id,product_id,fixed_unit_price,"
            + "price_status,binding_status,commission_rate,platform_rate,tax_rate,preferred_supplier_id,reference_purchase_price) "
            + "values (8,19,300,'PRICED','0',0.1,0.2,0.3,7,85.5000)");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            Map<String, Object> price = session.getMapper(JewelryErpMapper.class)
                .selectInfluencerProductPrice(8L, 19L);
            assertEquals("PRICED", mapValue(price, "priceStatus"));
            assertEquals("0", mapValue(price, "bindingStatus"));
            assertEquals(0, new BigDecimal("0.1").compareTo((BigDecimal) mapValue(price, "commissionRate")));
            assertEquals(0, new BigDecimal("0.2").compareTo((BigDecimal) mapValue(price, "platformRate")));
            assertEquals(0, new BigDecimal("0.3").compareTo((BigDecimal) mapValue(price, "taxRate")));
            assertEquals(7L, ((Number) mapValue(price, "preferredSupplierId")).longValue());
            assertEquals(0, new BigDecimal("85.5000").compareTo((BigDecimal) mapValue(price, "referencePurchasePrice")));
            Map<String, Object> listed = session.getMapper(JewelryErpMapper.class)
                .selectInfluencerProductPrices(8L).get(0);
            assertEquals("常用供应商", mapValue(listed, "preferredSupplierName"));
        }
    }

    @Test
    void customerReturnStatsKeepAddonQuantityUnderItsSoldMainProduct()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification) values"
            + "(19,'MAIN-19','成品','FINISHED','普通'),(20,'ADDON-20','搭售品','ACCESSORY','普通')");
        execute("insert into jewelry_influencer_product_price(influencer_id,product_id,fixed_unit_price,"
            + "binding_status) values(8,19,300,'0')");
        insertDocument(101L, "SALE-101", "SALES_OUT", "POSTED", null);
        execute("update jewelry_document set influencer_id=8 where document_id=101");
        insertItem(1001L, 101L, null, 19L, 3);
        insertItem(1002L, 101L, null, 20L, 2);
        execute("update jewelry_document_item set sale_role='MAIN',bundle_group_no=1 where item_id=1001");
        execute("update jewelry_document_item set sale_role='ADDON',bundle_group_no=1,pricing_mode='INCLUDED'"
            + " where item_id=1002");
        insertDocument(102L, "RETURN-102", "CUSTOMER_RETURN", "DRAFT", null);
        execute("update jewelry_document set influencer_id=8 where document_id=102");
        insertItem(1003L, 102L, null, 19L, 1);
        insertItem(1004L, 102L, null, 20L, 1);
        execute("update jewelry_document_item set sale_role='MAIN',bundle_group_no=1 where item_id=1003");
        execute("update jewelry_document_item set sale_role='ADDON',bundle_group_no=1,pricing_mode='INCLUDED'"
            + " where item_id=1004");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            List<Map<String, Object>> stats = mapper.selectCustomerReturnProductStats(8L, null,
                null, null, null);
            Map<String, Object> main = stats.stream()
                .filter(item -> "MAIN".equals(mapValue(item, "saleRole"))).findFirst().get();
            Map<String, Object> addon = stats.stream()
                .filter(item -> "ADDON".equals(mapValue(item, "saleRole"))).findFirst().get();
            assertEquals(3, ((Number) mapValue(main, "soldQty")).intValue());
            assertEquals(2, ((Number) mapValue(main, "remainingReturnQty")).intValue());
            assertEquals(19L, ((Number) mapValue(addon, "mainProductId")).longValue());
            assertEquals(2, ((Number) mapValue(addon, "soldQty")).intValue());
            assertEquals(1, ((Number) mapValue(addon, "remainingReturnQty")).intValue());
            assertEquals("INCLUDED", mapValue(addon, "pricingMode"));
            assertEquals(1, mapper.selectCustomerReturnProductStats(8L, null, 20L, 19L, "ADDON").size());
        }
    }

    @Test
    void outboundReservationCannotExceedAvailableStock()
    {
        insertStock(1L, 5, 0, 0, 0, 0, 0, "100.00");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.reserveOutbound(1L, 4));
            assertEquals(0, mapper.reserveOutbound(1L, 2));
            assertEquals(1, mapper.releaseOutbound(1L, 3));
            assertEquals(0, mapper.releaseOutbound(1L, 2));
            session.commit();
        }

        assertEquals(1, intValue("select reserved_out_qty from jewelry_stock where product_id=1"));
    }

    @Test
    void purchaseDeadlinePersistsAndCanBeClearedOnDraft() throws Exception
    {
        try (SqlSession session = sqlSessionFactory.openSession(true))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument purchase = new JewelryDocument();
            purchase.setDocNo("DEADLINE-DRAFT");
            purchase.setDocType("PURCHASE_IN");
            purchase.setBizDate(java.sql.Date.valueOf("2026-09-15"));
            purchase.setSupplierReturnDate(java.sql.Date.valueOf("2026-10-15"));
            purchase.setStatus("DRAFT");
            purchase.setCreatorUserId(10L);
            purchase.setCreatorName("maker");
            purchase.setTotalQty(1);
            purchase.setTotalAmount(BigDecimal.ONE);
            purchase.setTotalCost(BigDecimal.ONE);
            purchase.setTotalProfit(BigDecimal.ZERO);
            mapper.insertDocument(purchase);
            assertEquals(purchase.getSupplierReturnDate(), mapper.selectDocumentById(purchase.getDocumentId()).getSupplierReturnDate());
            purchase.setSupplierReturnDate(null);
            mapper.updateDocument(purchase);
            assertEquals(null, mapper.selectDocumentById(purchase.getDocumentId()).getSupplierReturnDate());
        }
    }

    @Test
    void returnCountdownChoosesEarliestRemainingDeadlineAndSkipsConsumedBatches() throws Exception
    {
        insertStock(1L, 15, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-10-20");
        deadlinePurchase(2L, 10, "2026-10-10");
        assertEquals("2026-10-10", nextDeadline());
        // Newest batch has a later deadline; FIFO has completely consumed the oldest batch.
        execute("update jewelry_document set supplier_return_date='2026-10-25' where document_id=2");
        execute("update jewelry_stock set on_hand_qty=10 where product_id=1");
        assertEquals("2026-10-25", nextDeadline());
        execute("update jewelry_stock set on_hand_qty=11 where product_id=1");
        assertEquals("2026-10-20", nextDeadline());
        execute("update jewelry_stock set on_hand_qty=0 where product_id=1");
        assertEquals(null, nextDeadline());
    }

    @Test
    void returnCountdownExcludesReturnedAndReversedPurchasesAndDoesNotDoubleCountReversal() throws Exception
    {
        insertStock(1L, 10, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-10-20");
        deadlinePurchase(2L, 10, "2026-10-10");
        insertDocument(3L, "RETURN-2", "SUPPLIER_RETURN", "POSTED", 2L);
        insertItem(3L, 3L, 2L, 1L, 10);
        assertEquals("2026-10-20", nextDeadline());
        // Reversing the return restores batch 2, and the positive reversal must not hide that batch.
        execute("update jewelry_document set status='REVERSED' where document_id=3");
        insertDocument(4L, "REV-3", "REVERSAL", "POSTED", 3L);
        execute("insert into jewelry_stock_transaction values(4,1,4,4,10,current_timestamp)");
        execute("update jewelry_stock set on_hand_qty=20 where product_id=1");
        assertEquals("2026-10-10", nextDeadline());
        execute("update jewelry_document set status='REVERSED' where document_id=2");
        execute("update jewelry_stock set on_hand_qty=10 where product_id=1");
        assertEquals("2026-10-20", nextDeadline());
          execute("update jewelry_document set supplier_return_date=null where document_id=1");
          execute("update jewelry_document set biz_date='2026-09-01' where document_id=1");
          assertEquals("2026-09-26", nextDeadline());
    }

    @Test
    void unifiedReturnDaysApplyToHistoricalPurchasesButNeverOverrideSpecialDates() throws Exception
    {
        insertStock(1L, 10, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-10-20");
        execute("update jewelry_document set biz_date='2026-09-01',supplier_return_date=null where document_id=1");
        assertEquals("2026-09-26", nextDeadline());
        try (SqlSession session = sqlSessionFactory.openSession(true))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(25, mapper.selectSupplierReturnDays());
            mapper.upsertSupplierReturnDays(30, "admin");
            assertEquals(30, mapper.selectSupplierReturnDays());
            assertEquals("2026-10-01", nextDeadline());
            JewelryDocument dateOnly = new JewelryDocument();
            dateOnly.setDocumentId(1L);
            dateOnly.setSupplierReturnDate(java.sql.Date.valueOf("2026-11-01"));
            dateOnly.setUpdateBy("admin");
            dateOnly.setTotalAmount(new java.math.BigDecimal("999999"));
            assertEquals(1, mapper.updatePostedSupplierReturnDate(dateOnly));
            mapper.upsertSupplierReturnDays(15, "admin");
            assertEquals("2026-11-01", nextDeadline());
            assertEquals(0, intValue("select total_amount from jewelry_document where document_id=1"));
            assertEquals(10, intValue("select on_hand_qty from jewelry_stock where product_id=1"));
            dateOnly.setSupplierReturnDate(null);
            assertEquals(1, mapper.updatePostedSupplierReturnDate(dateOnly));
            assertEquals("2026-09-16", nextDeadline());
            execute("update jewelry_document set status='REVERSED' where document_id=1");
            assertEquals(0, mapper.updatePostedSupplierReturnDate(dateOnly));
        }
    }

    @Test
    void customerReturnInspectionKeepsOriginalPurchaseAgeAndDeadline() throws Exception
    {
        insertStock(1L, 12, 0, 0, 0, 0, 0, "750");
        deadlinePurchase(1L, 29, "2026-09-14");
        execute("update jewelry_document set biz_date='2026-08-20',supplier_return_date=null where document_id=1");
        saleEvent(2L, 29, "2026-08-20");
        inspectedReturn(3L, 4L, null, 20, "2026-08-24", "2026-08-26");
        insertDocument(5L, "SUP-RETURN", "SUPPLIER_RETURN", "POSTED", 1L);
        insertItem(5L, 5L, 1L, 1L, 8);
        assertEquals("2026-08-20", originValue("oldest_inbound_date"));
        assertEquals("0", originValue("origin_unknown"));
        assertEquals("2026-09-14", nextDeadline());
        execute("update jewelry_document set supplier_return_date='2026-09-30' where document_id=1");
        assertEquals("2026-09-30", nextDeadline());
        execute("update jewelry_stock set on_hand_qty=0 where product_id=1");
        assertEquals(null, originValue("oldest_inbound_date"));
        assertEquals(null, nextDeadline());
    }

    @Test
    void ambiguousReturnsUseFirstPurchaseAndItsOwnDeadline() throws Exception
    {
        insertStock(1L, 2, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-10");
        deadlinePurchase(2L, 10, "2026-09-30");
        // First purchase means earliest business date, not lowest ID or shortest deadline.
        execute("update jewelry_document set biz_date='2026-08-24',supplier_name_snapshot='later' where document_id=1");
        execute("update jewelry_document set biz_date='2026-08-20',supplier_name_snapshot='first' where document_id=2");
        inspectedReturn(3L, 4L, null, 2, "2026-08-24", "2026-08-26");
        assertEquals("0", originValue("origin_unknown"));
        assertEquals("1", originValue("origin_first_purchase"));
        assertEquals("2026-08-20", originValue("oldest_inbound_date"));
        assertEquals("2026-09-30", nextDeadline());
        assertEquals("PUR-2", deadlineValue("doc_no"));
        assertEquals("first", deadlineValue("supplier_name_snapshot"));
        // Even with another remaining known batch, the agreed fallback uses the FIRST purchase's terms.
        execute("update jewelry_stock set on_hand_qty=15 where product_id=1");
        assertEquals("2026-09-30", nextDeadline());
        execute("update jewelry_document set supplier_return_date=null where document_id=2");
        assertEquals("2026-09-14", nextDeadline());
        // Reversed purchases are excluded; unique sources retain their existing behavior.
        execute("update jewelry_document set status='REVERSED' where document_id=2");
        assertEquals("2026-08-24", originValue("oldest_inbound_date"));
        assertEquals("0", originValue("origin_first_purchase"));
        assertEquals("2026-09-10", nextDeadline());
    }

    @Test
    void transferWarehouseNamesPersistOnCreateAndEdit()
    {
        try (SqlSession session = sqlSessionFactory.openSession(true))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument document = new JewelryDocument();
            document.setDocNo("DH-TEST");
            document.setDocType("TRANSFER_OUT");
            document.setBizDate(java.sql.Date.valueOf("2026-09-17"));
            document.setStatus("DRAFT");
            document.setCreatorUserId(10L);
            document.setCreatorName("maker");
            document.setSourceWarehouse("本仓");
            document.setTargetWarehouse("接收仓");
            document.setTotalQty(2);
            document.setTotalAmount(BigDecimal.ZERO);
            document.setTotalCost(BigDecimal.TEN);
            document.setTotalProfit(BigDecimal.ZERO);
            assertEquals(1, mapper.insertDocument(document));
            JewelryDocument loaded = mapper.selectDocumentById(document.getDocumentId());
            assertEquals("本仓", loaded.getSourceWarehouse());
            assertEquals("接收仓", loaded.getTargetWarehouse());
            document.setTargetWarehouse("接收二仓");
            assertEquals(1, mapper.updateDocument(document));
            assertEquals("接收二仓", mapper.selectDocumentById(document.getDocumentId()).getTargetWarehouse());
        }
    }

    @Test
    void linkedSalesExcludeLaterPurchasesWhenTracingReturnedGoods() throws Exception
    {
        insertStock(1L, 12, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-14");
        execute("update jewelry_document set biz_date='2026-08-20' where document_id=1");
        saleEvent(2L, 10, "2026-08-21");
        deadlinePurchase(3L, 10, "2026-09-16");
        execute("update jewelry_document set biz_date='2026-08-22' where document_id=3");
        inspectedReturn(4L, 5L, 2L, 2, "2026-08-24", "2026-08-26");
        assertEquals("2026-08-20", originValue("oldest_inbound_date"));
        assertEquals("2026-09-14", nextDeadline());
        // Undoing inspection restores the later purchase as the remaining saleable stock.
        execute("update jewelry_document set status='REVERSED' where document_id=5");
        execute("update jewelry_stock set on_hand_qty=10 where product_id=1");
        assertEquals("2026-08-22", originValue("oldest_inbound_date"));
        assertEquals("2026-09-16", nextDeadline());
    }

    @Test
    void firstPurchaseFallbackBreaksSameDateTiesByDocumentId() throws Exception
    {
        insertStock(1L, 2, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-30");
        deadlinePurchase(2L, 10, "2026-09-01");
        execute("update jewelry_document set biz_date='2026-08-20' where document_id in(1,2)");
        inspectedReturn(3L, 4L, null, 2, "2026-08-24", "2026-08-26");
        assertEquals("PUR-1", deadlineValue("doc_no"));
        assertEquals("2026-09-30", nextDeadline());
    }

    @Test
    void missingOriginalPurchaseIsNotInventedOrReplacedByLaterPurchase() throws Exception
    {
        insertStock(1L, 2, 0, 0, 0, 0, 0, "10");
        inspectedReturn(1L, 2L, null, 2, "2026-08-24", "2026-08-26");
        deadlinePurchase(3L, 10, "2026-09-30");
        execute("update jewelry_document set biz_date='2026-08-27' where document_id=3");
        // Later purchase was returned to its supplier; it must not become the earlier customer's source.
        insertDocument(4L, "SUP-LATER", "SUPPLIER_RETURN", "POSTED", 3L);
        insertItem(4L, 4L, 3L, 1L, 10);
        assertEquals("1", originValue("origin_unknown"));
        assertEquals(null, nextDeadline());
    }

    @Test
    void purchaseAfterCustomerReturnCannotBecomeItsOrigin() throws Exception
    {
        insertStock(1L, 12, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-14");
        execute("update jewelry_document set biz_date='2026-08-20' where document_id=1");
        deadlinePurchase(2L, 10, "2026-09-19");
        execute("update jewelry_document set biz_date='2026-08-25' where document_id=2");
        inspectedReturn(3L, 4L, null, 2, "2026-08-24", "2026-08-26");
        assertEquals("2026-08-20", originValue("oldest_inbound_date"));
        assertEquals("2026-09-14", nextDeadline());
    }

    @Test
    void supplierReturnWarningUsesStrictSevenDaysAndMatchesDrillDown() throws Exception
    {
        insertStock(1L, 10, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-14");
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification)"
            + " values(1,'SKU-1','退供预警边界测试','FINISHED','普通')");
        for (int days : new int[] {-5, 0, 1, 6, 7, 8})
        {
            execute("update jewelry_document set supplier_return_date=timestampadd(DAY," + days + ",current_date) where document_id=1");
            assertSupplierReturnWarningCount(days < 7 ? 1 : 0);
        }
        execute("update jewelry_document set supplier_return_date=current_date where document_id=1");
        for (String type : Arrays.asList("PART", "ACCESSORY", "WELFARE", "SAMPLE"))
        {
            execute("update jewelry_product set product_type='" + type + "' where product_id=1");
            assertSupplierReturnWarningCount(0);
        }
        execute("update jewelry_product set product_type='FINISHED',status='1' where product_id=1");
        assertSupplierReturnWarningCount(0);
        execute("update jewelry_product set status='0' where product_id=1");
        execute("update jewelry_stock set on_hand_qty=0 where product_id=1");
        assertSupplierReturnWarningCount(0);
        execute("update jewelry_stock set on_hand_qty=10 where product_id=1");
        execute("update jewelry_document set status='REVERSED' where document_id=1");
        assertSupplierReturnWarningCount(0);
    }

    private void assertSupplierReturnWarningCount(int expected) throws Exception
    {
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("warningOnly", true);
        query.put("warningType", "supplierReturn");
        String stockSql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList")
            .getBoundSql(query).getSql();
        String ctes = stockSql.substring(stockSql.indexOf("return_config as"), stockSql.indexOf("select p.product_id productId"));
        // Execute the actual production filter, excluding unrelated MySQL-only age projections.
        String from = stockSql.substring(stockSql.indexOf("from jewelry_stock s join jewelry_product p", stockSql.indexOf("select p.product_id productId")), stockSql.lastIndexOf("order by p.product_id desc"));
        String dashboardSql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectDashboard")
            .getBoundSql(Collections.emptyMap()).getSql();
        int end = dashboardSql.indexOf(") supplierReturnWarningCount");
        int start = dashboardSql.lastIndexOf("(select count(*)", end);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            materializeDeadlineCtes(connection, ctes);
            statement.execute("create local temporary table warning_config as select 25 warning_days");
            for (String querySql : Arrays.asList("select count(*) " + from, dashboardSql.substring(start + 1, end)))
            {
                try (ResultSet result = statement.executeQuery(querySql))
                {
                    assertTrue(result.next());
                    assertEquals(expected, result.getInt(1));
                }
            }
        }
    }

    private void saleEvent(Long id, int qty, String date)
    {
        insertDocument(id, "SALE-" + id, "SALES_OUT", "POSTED", null);
        insertItem(id, id, null, 1L, qty);
        execute("update jewelry_document set biz_date='" + date + "' where document_id=" + id);
        execute("insert into jewelry_stock_transaction values(" + id + ",1," + id + "," + id + ",-" + qty + ",current_timestamp)");
    }

    private void inspectedReturn(Long returnId, Long inspectId, Long saleItemId, int qty, String returnDate, String inspectDate)
    {
        insertDocument(returnId, "RETURN-" + returnId, "CUSTOMER_RETURN", "POSTED", saleItemId);
        insertItem(returnId, returnId, saleItemId, 1L, qty);
        execute("update jewelry_document set biz_date='" + returnDate + "' where document_id=" + returnId);
        insertDocument(inspectId, "INSPECT-" + inspectId, "RETURN_INSPECT", "POSTED", returnId);
        insertItem(inspectId, inspectId, returnId, 1L, qty);
        execute("update jewelry_document set biz_date='" + inspectDate + "' where document_id=" + inspectId);
        execute("insert into jewelry_stock_transaction values(" + inspectId + ",1," + inspectId + "," + inspectId + "," + qty + ",current_timestamp)");
    }

    private String originValue(String column) throws Exception
    {
        String sql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList")
            .getBoundSql(Collections.emptyMap()).getSql();
        String ctes = sql.substring(sql.indexOf("return_config as"), sql.indexOf("select p.product_id productId"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            materializeDeadlineCtes(connection, ctes);
            try (ResultSet result = statement.executeQuery("select " + column + " from stock_origin_summary where product_id=1"))
            {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    @Test
    void onlyFinishedProductsExposeSupplierReturnCountdown() throws Exception
    {
        insertStock(1L, 10, 0, 0, 0, 0, 0, "10");
        deadlinePurchase(1L, 10, "2026-09-14");
        execute("update jewelry_document set biz_date='2026-08-20' where document_id=1");
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification)"
            + " values(1,'SKU-1','分类预警测试','FINISHED','普通')");
        String sql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList")
            .getBoundSql(Collections.emptyMap()).getSql();
        String ctes = sql.substring(sql.indexOf("return_config as"), sql.indexOf("select p.product_id productId"));
        String deadlineJoin = sql.substring(sql.indexOf("left join remaining_deadlines rd"), sql.indexOf("cross join warning_config wc"));
        for (String type : Arrays.asList("FINISHED", "PART", "ACCESSORY", "WELFARE", "SAMPLE", "FINISHED"))
        {
            execute("update jewelry_product set product_type='" + type + "' where product_id=1");
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
            {
                materializeDeadlineCtes(connection, ctes);
                try (ResultSet result = statement.executeQuery(
                    "select rd.deadline,rd.doc_no from jewelry_stock s join jewelry_product p on p.product_id=s.product_id " + deadlineJoin))
                {
                    assertTrue(result.next());
                    assertEquals("FINISHED".equals(type) ? java.sql.Date.valueOf("2026-09-14") : null, result.getDate(1));
                    assertEquals("FINISHED".equals(type) ? "PUR-1" : null, result.getString(2));
                }
            }
            // Product-type gating must not alter the independent stock-age calculation.
            assertEquals("2026-08-20", originValue("oldest_inbound_date"));
        }
    }

    private void deadlinePurchase(Long id, int qty, String deadline)
    {
        insertDocument(id, "PUR-" + id, "PURCHASE_IN", "POSTED", null);
        insertItem(id, id, null, 1L, qty);
        execute("update jewelry_document set supplier_return_date='" + deadline + "' where document_id=" + id);
        execute("insert into jewelry_stock_transaction values(" + id + ",1," + id + "," + id + "," + qty + ",current_timestamp)");
    }

    private String nextDeadline() throws Exception
    {
        return deadlineValue("deadline");
    }

    private String deadlineValue(String column) throws Exception
    {
        // Execute the production CTEs, independently of existing MySQL-only stock-age expressions.
        String sql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList")
            .getBoundSql(Collections.emptyMap()).getSql();
        String ctes = sql.substring(sql.indexOf("return_config as"), sql.indexOf("select p.product_id productId"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            materializeDeadlineCtes(connection, ctes);
            try (ResultSet result = statement.executeQuery("select " + column + " from remaining_deadlines where deadline_rank=1"))
            {
                if (!result.next()) return null;
                return "deadline".equals(column) ? result.getDate(1).toString() : result.getString(1);
            }
        }
    }

    private void materializeDeadlineCtes(Connection connection, String ctes) throws Exception
    {
        // H2 1.4 repeatedly expands this shared CTE graph while optimizing joins.
        // Evaluate each production SELECT unchanged, in dependency order, on the same fixture.
        // Connection-local tables disappear on close. This checks results, not MySQL query plans.
        Pattern header = Pattern.compile("\\s*,?\\s*([a-z_]+)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE);
        int offset = 0;
        try (Statement statement = connection.createStatement())
        {
            while (!ctes.substring(offset).trim().isEmpty())
            {
                Matcher match = header.matcher(ctes).region(offset, ctes.length());
                assertTrue(match.lookingAt(), "Unrecognized production CTE at " + offset);
                String name = match.group(1);
                int start = match.end(), cursor = start, depth = 1;
                boolean quoted = false;
                while (cursor < ctes.length() && depth > 0)
                {
                    char current = ctes.charAt(cursor++);
                    if (current == '\'')
                    {
                        if (quoted && cursor < ctes.length() && ctes.charAt(cursor) == '\'') cursor++;
                        else quoted = !quoted;
                    }
                    else if (!quoted)
                    {
                        if (current == '(') depth++;
                        else if (current == ')') depth--;
                    }
                }
                assertEquals(0, depth, "Unclosed production CTE: " + name);
                assertFalse(quoted, "Unclosed SQL literal in " + name);
                statement.execute("create local temporary table " + name + " as " + ctes.substring(start, cursor - 1));
                offset = cursor;
            }
        }
    }

    @Test
    void insertProductUsesLegacyColumnDefaults()
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("sku", "NEW-NO-CLASSIFICATION");
        product.put("productName", "新商品");
        product.put("productType", "FINISHED");
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            session.getMapper(JewelryErpMapper.class).insertProduct(product);
            session.commit();
        }
        assertEquals("", stringValue("select category from jewelry_product where sku='NEW-NO-CLASSIFICATION'"));
        assertEquals("普通", stringValue("select specification from jewelry_product where sku='NEW-NO-CLASSIFICATION'"));
    }

    @Test
    void basicProductUpdateCannotChangeProtectedProductFields()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,category,specification,"
            + "image_url,image_urls,unit,default_pack_fee,default_ship_fee,default_cert_fee,warning_qty,status)"
            + " values(1,'SKU-1','旧名称','ACCESSORY','包装','普通','/old.jpg','/old.jpg','只',1,2,3,5,'0')");
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("productId", 1L);
        fields.put("productName", "新名称");
        fields.put("imageUrl", "/new.jpg");
        fields.put("imageUrls", "/new.jpg");
        fields.put("updateBy", "maker");
        fields.put("productType", "WELFARE");
        fields.put("status", "1");
        fields.put("defaultPackFee", 999);

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.updateProductBasic(fields));
            session.commit();
        }

        assertEquals("新名称", stringValue("select product_name from jewelry_product where product_id=1"));
        assertEquals("/new.jpg", stringValue("select image_url from jewelry_product where product_id=1"));
        assertEquals("ACCESSORY", stringValue("select product_type from jewelry_product where product_id=1"));
        assertEquals("0", stringValue("select status from jewelry_product where product_id=1"));
        assertEquals(new BigDecimal("1.000000"),
            decimalValue("select default_pack_fee from jewelry_product where product_id=1"));
    }

    @Test
    void batchProductPatchOnlyUpdatesSelectedFieldsAndRows()
    {
        for (int id = 1; id <= 3; id++)
            execute("insert into jewelry_product(product_id,sku,product_name,product_type,category,specification,"
                + "image_url,image_urls,unit,default_pack_fee,default_ship_fee,default_cert_fee,warning_qty,status)"
                + " values(" + id + ",'SKU-" + id + "','原名" + id + "','FINISHED','旧分类','普通','/old.jpg','/old.jpg','只',1,2,3,5,'0')");
        execute("insert into jewelry_stock(product_id,on_hand_qty,avg_cost) values(1,7,12.34)");
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("productType", "SAMPLE");
        fields.put("warningQty", 0);
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(Arrays.asList(1L, 2L), mapper.lockProductIds(Arrays.asList(1L, 2L)));
            assertEquals(2, mapper.batchUpdateProducts(Arrays.asList(1L, 2L), fields, "admin"));
            session.commit();
        }
        for (int id = 1; id <= 2; id++)
        {
            assertEquals("SAMPLE", stringValue("select product_type from jewelry_product where product_id=" + id));
            assertEquals("原名" + id, stringValue("select product_name from jewelry_product where product_id=" + id));
            assertEquals("/old.jpg", stringValue("select image_url from jewelry_product where product_id=" + id));
            assertEquals("旧分类", stringValue("select category from jewelry_product where product_id=" + id));
            assertEquals(0, intValue("select warning_qty from jewelry_product where product_id=" + id));
            assertEquals("0", stringValue("select status from jewelry_product where product_id=" + id));
            assertEquals("admin", stringValue("select update_by from jewelry_product where product_id=" + id));
        }
        assertEquals("FINISHED", stringValue("select product_type from jewelry_product where product_id=3"));
        assertEquals(7, intValue("select on_hand_qty from jewelry_stock where product_id=1"));
        assertEquals(new BigDecimal("12.340000"), decimalValue("select avg_cost from jewelry_stock where product_id=1"));
    }

    @Test
    void productDeletionReferenceCheckIncludesDraftHistoryPricesAndBothBundleSides()
    {
        insertDocument(1L, "DELETE-GUARD", "PURCHASE_IN", "DRAFT", null);
        insertItem(1L, 1L, null, 1L, 1);
        execute("insert into jewelry_stock_transaction values(1,2,1,1,1,current_timestamp)");
        execute("insert into jewelry_influencer_product_price(influencer_id,product_id,fixed_unit_price) values(1,3,1)");
        execute("insert into jewelry_influencer_price_history(influencer_id,product_id,new_price,source_type,"
            + "price_version,change_reason,operator_user_id,operator_name,create_time)"
            + " values(1,4,1,'MANUAL',1,'test',1,'tester',current_timestamp)");
        execute("insert into jewelry_influencer_bundle_item(influencer_id,main_product_id,addon_product_id,"
            + "main_qty,addon_qty,source_document_id,last_sale_time) values(1,5,6,1,1,1,current_timestamp)");
        try (SqlSession session = sqlSessionFactory.openSession(true))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            for (long id = 1; id <= 6; id++) assertEquals(1, mapper.countProductReferences(id));
            assertEquals(0, mapper.countProductReferences(7L));
        }
    }

    @Test
    void productDeletionRemovesOnlySelectedRowsAndStockAndSupportsRollback()
    {
        for (int id = 1; id <= 3; id++)
        {
            execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification) values(" + id + ",'SKU-" + id + "','未使用','FINISHED','普通')");
            insertStock((long) id, 0, 0, 0, 0, 0, 0, "0");
        }
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals("SKU-1", mapper.selectProductByIdForUpdate(1L).get("sku"));
            assertEquals(2, mapper.deleteProductStock(Arrays.asList(1L, 2L)));
            assertEquals(2, mapper.deleteProducts(Arrays.asList(1L, 2L)));
            session.rollback();
        }
        assertEquals(3, intValue("select count(*) from jewelry_product"));
        assertEquals(3, intValue("select count(*) from jewelry_stock"));
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            mapper.deleteProductStock(Arrays.asList(1L, 2L));
            mapper.deleteProducts(Arrays.asList(1L, 2L));
            session.commit();
        }
        assertEquals(1, intValue("select count(*) from jewelry_product"));
        assertEquals(3, intValue("select product_id from jewelry_product"));
        assertEquals(3, intValue("select product_id from jewelry_stock"));
    }

    @Test
    void productReferenceLockPreventsDeletionUntilDocumentCreationCommits() throws Exception
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification) values(1,'LOCK-1','锁定','FINISHED','普通')");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try (SqlSession writer = sqlSessionFactory.openSession(false))
        {
            writer.getMapper(JewelryErpMapper.class).selectProductByIdForUpdate(1L);
            Future<Integer> references = executor.submit(() -> {
                try (SqlSession deleting = sqlSessionFactory.openSession(false))
                {
                    started.countDown();
                    JewelryErpMapper mapper = deleting.getMapper(JewelryErpMapper.class);
                    mapper.lockProductIds(Collections.singletonList(1L));
                    return mapper.countProductReferences(1L);
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                () -> references.get(150, TimeUnit.MILLISECONDS));
            try (Statement statement = writer.getConnection().createStatement())
            {
                statement.execute("insert into jewelry_document_item(item_id,document_id,product_id,qty,sku_snapshot,product_name_snapshot) values(1,1,1,1,'LOCK-1','锁定')");
            }
            writer.commit(true);
            assertEquals(1, references.get(3, TimeUnit.SECONDS));
        }
        finally { executor.shutdownNow(); }
    }

    @Test
    void batchProductPatchSupportsImageClearAndRollsBackTogether()
    {
        for (int id = 1; id <= 2; id++)
            execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification,image_url,image_urls)"
                + " values(" + id + ",'SKU-" + id + "','旧名','FINISHED','普通','/old.jpg','/old.jpg')");
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("productName", "统一名称");
        fields.put("imageUrls", "");
        fields.put("unit", "个");
        fields.put("status", "1");
        fields.put("defaultPackFee", new BigDecimal("0"));
        fields.put("defaultShipFee", new BigDecimal("1.23"));
        fields.put("defaultCertFee", new BigDecimal("4.56"));
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            mapper.batchUpdateProducts(Arrays.asList(1L, 2L), fields, "admin");
            session.rollback();
        }
        assertEquals(2, intValue("select count(*) from jewelry_product where product_name='旧名' and image_url='/old.jpg'"));
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            session.getMapper(JewelryErpMapper.class).batchUpdateProducts(Arrays.asList(1L, 2L), fields, "admin");
            session.commit();
        }
        assertEquals(2, intValue("select count(*) from jewelry_product where product_name='统一名称' and image_url=''"
            + " and image_urls='' and specification='普通' and unit='个' and status='1'"
            + " and default_pack_fee=0 and default_ship_fee=1.23 and default_cert_fee=4.56"));
    }

    @Test
    void stockListQueryCanFilterByProductType()
    {
        MappedStatement statement = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList");
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("productType", "ACCESSORY");
        String filteredSql = statement.getBoundSql(query).getSql().replaceAll("\\s+", " ");
        assertTrue(filteredSql.contains("p.product_type=?"));
        assertTrue(filteredSql.contains("(s.on_hand_qty+s.inspection_qty+s.defect_qty) totalStockQty"));
        assertTrue(filteredSql.contains("s.on_hand_qty*s.avg_cost+s.inspection_cost_amount+s.defect_cost_amount"));

        String unfilteredSql = statement.getBoundSql(Collections.emptyMap()).getSql().replaceAll("\\s+", " ");
        assertFalse(unfilteredSql.contains("p.product_type=?"));

        query.put("inStockOnly", true);
        query.put("supplierIds", Arrays.asList(2L, 3L));
        String selectedSql = statement.getBoundSql(query).getSql().replaceAll("\\s+", " ");
        assertTrue(selectedSql.contains("(s.on_hand_qty+s.inspection_qty+s.defect_qty) > 0"));
        assertTrue(selectedSql.matches("(?s).*coalesce\\(si\\.supplier_id,sd\\.supplier_id\\) in \\(\\s*\\?\\s*,\\s*\\?\\s*\\).*"));
        assertFalse(unfilteredSql.contains("coalesce(si.supplier_id,sd.supplier_id) in"));
    }

    @Test
    void stockSupplierOptionsIncludePostedPurchaseAndSampleSuppliers()
    {
        execute("insert into jewelry_supplier(supplier_id,supplier_name) values(1,'采购供应商'),(2,'样品供应商'),(3,'草稿供应商')");
        insertDocument(1L, "PURCHASE-POSTED", "PURCHASE_IN", "POSTED", null);
        insertDocument(2L, "SAMPLE-POSTED", "SAMPLE_IN", "POSTED", null);
        insertDocument(3L, "SAMPLE-DRAFT", "SAMPLE_IN", "DRAFT", null);
        insertItem(11L, 1L, null, 10L, 1);
        insertItem(12L, 2L, null, 20L, 1);
        insertItem(13L, 3L, null, 30L, 1);
        execute("update jewelry_document set supplier_id=1,supplier_name_snapshot='采购供应商' where document_id=1");
        execute("update jewelry_document_item set supplier_id=2,supplier_name_snapshot='样品供应商' where item_id=12");
        execute("update jewelry_document_item set supplier_id=3,supplier_name_snapshot='草稿供应商' where item_id=13");
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<Map<String, Object>> options = session.getMapper(JewelryErpMapper.class).selectStockSupplierOptions();
            assertEquals(2, options.size());
            assertTrue(options.stream().anyMatch(item -> item.containsValue("采购供应商")));
            assertTrue(options.stream().anyMatch(item -> item.containsValue("样品供应商")));
        }
    }

    @Test
    void productOptionsShowDistinctSuppliersFromPostedInboundDocuments()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification)"
            + " values(10,'PRODUCT-10','商品','FINISHED','普通')");
        execute("insert into jewelry_supplier(supplier_id,supplier_name)"
            + " values(1,'供应商甲'),(2,'供应商乙'),(3,'草稿供应商'),(4,'绑定供应商')");
        execute("insert into jewelry_influencer(influencer_id,influencer_code,influencer_name,status)"
            + " values(8,'LIVE-8','启用达人','0'),(9,'LIVE-9','停用达人','1')");
        execute("insert into jewelry_influencer_product_price(influencer_id,product_id,fixed_unit_price,"
            + "preferred_supplier_id,binding_status) values(8,10,100,4,'0'),(9,10,100,3,'0')");
        insertDocument(1L, "PURCHASE-1", "PURCHASE_IN", "POSTED", null);
        insertDocument(2L, "SAMPLE-2", "SAMPLE_IN", "POSTED", null);
        insertDocument(3L, "PURCHASE-3", "PURCHASE_IN", "DRAFT", null);
        insertDocument(4L, "SALE-4", "SALES_OUT", "PENDING_FIRST", null);
        insertItem(11L, 1L, null, 10L, 1);
        insertItem(12L, 2L, null, 10L, 1);
        insertItem(13L, 3L, null, 10L, 1);
        insertItem(14L, 4L, null, 10L, 1);
        execute("update jewelry_document set supplier_id=1 where document_id=1");
        execute("update jewelry_document_item set supplier_id=2 where item_id=12");
        execute("update jewelry_document_item set supplier_id=3 where item_id=13");
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            List<Map<String, Object>> products = mapper.selectProductList(Collections.emptyMap());
            assertEquals(1, products.size());
            String supplierNames = String.valueOf(products.get(0).get("suppliernames"));
            assertTrue(supplierNames.contains("供应商甲"));
            assertTrue(supplierNames.contains("供应商乙"));
            assertFalse(supplierNames.contains("草稿供应商"));
            String supplierIds = String.valueOf(mapValue(products.get(0), "supplierIds"));
            assertTrue(Arrays.asList(supplierIds.split(",")).contains("1"));
            assertTrue(Arrays.asList(supplierIds.split(",")).contains("2"));
            assertFalse(Arrays.asList(supplierIds.split(",")).contains("3"));
            assertEquals("8", String.valueOf(mapValue(products.get(0), "influencerIds")));
            assertEquals("4", String.valueOf(mapValue(products.get(0), "boundSupplierIds")));
            List<JewelryDocumentItem> saleItems = mapper.selectDocumentItems(4L);
            assertEquals(1, saleItems.size());
            assertTrue(saleItems.get(0).getProductSupplierNames().contains("供应商甲"));
            assertTrue(saleItems.get(0).getProductSupplierNames().contains("供应商乙"));
            assertFalse(saleItems.get(0).getProductSupplierNames().contains("草稿供应商"));
        }
    }

    @Test
    void sampleProductsCanBeFilteredAndAreNotSupplierReturnWarningCandidates()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification)"
            + " values(1,'SAMPLE-1','样品项链','SAMPLE','普通'),(2,'FINISHED-1','成品项链','FINISHED','普通')");
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("productType", "SAMPLE");
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<Map<String, Object>> rows = session.getMapper(JewelryErpMapper.class).selectProductList(query);
            assertEquals(1, rows.size());
            assertEquals("SAMPLE-1", rows.get(0).get("sku"));
        }
        String stockSql = sqlSessionFactory.getConfiguration()
            .getMappedStatement("com.ruoyi.jewelry.mapper.JewelryErpMapper.selectStockList")
            .getBoundSql(query).getSql().replaceAll("\\s+", " ");
        assertTrue(stockSql.contains("p.product_type=?"));
        assertTrue(stockSql.contains("p.product_type='FINISHED'"));
    }

    @Test
    void sampleInboundDetailsUseEachPostedItemDateAndCurrentReturnPeriod()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification) values"
            + "(1,'SAMPLE-1','样品一','SAMPLE','普通'),(2,'SAMPLE-2','样品二','SAMPLE','普通'),"
            + "(3,'FINISHED-1','成品','FINISHED','普通')");
        execute("insert into sys_config(config_key,config_value) values('jewelry.supplier.return.days','10')");
        insertDocument(1L, "SAMPLE-POSTED", "SAMPLE_IN", "POSTED", null);
        insertDocument(2L, "SAMPLE-REVERSED", "SAMPLE_IN", "REVERSED", null);
        insertDocument(3L, "SAMPLE-DRAFT", "SAMPLE_IN", "DRAFT", null);
        insertDocument(4L, "PURCHASE-POSTED", "PURCHASE_IN", "POSTED", null);
        insertDocument(5L, "OTHER-SAMPLE", "SAMPLE_IN", "POSTED", null);
        insertDocument(6L, "FINISHED-SAMPLE", "SAMPLE_IN", "POSTED", null);
        insertItem(11L, 1L, null, 1L, 2);
        insertItem(12L, 1L, null, 1L, 3);
        insertItem(21L, 2L, null, 1L, 4);
        insertItem(31L, 3L, null, 1L, 5);
        insertItem(41L, 4L, null, 1L, 6);
        insertItem(51L, 5L, null, 2L, 7);
        insertItem(61L, 6L, null, 3L, 8);
        insertStock(1L, 3, 1, 1, 0, 0, 0, "100.00");
        execute("update jewelry_document_item set biz_date=timestampadd(DAY,-6,current_date),"
            + "sample_goods_no='YP-11',supplier_name_snapshot='供应商甲' where item_id=11");
        execute("update jewelry_document_item set biz_date=timestampadd(DAY,-3,current_date),"
            + "sample_goods_no='YP-12',supplier_name_snapshot='供应商乙' where item_id=12");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            List<Map<String, Object>> details = mapper.selectSampleInboundDetails(1L);
            assertEquals(2, details.size());
            assertEquals(3, ((Number) details.get(0).get("stockAgeDays")).intValue());
            assertEquals(7, ((Number) details.get(0).get("supplierReturnDays")).intValue());
            assertEquals("YP-12", details.get(0).get("goodsNo"));
            assertEquals(3, ((Number) details.get(0).get("inboundQty")).intValue());
            assertEquals(3, ((Number) details.get(0).get("totalStockQty")).intValue());
            assertEquals(3, ((Number) details.get(0).get("onHandQty")).intValue());
            assertEquals(1, ((Number) details.get(0).get("reservedOutQty")).intValue());
            assertEquals(2, ((Number) details.get(0).get("availableQty")).intValue());
            assertEquals("供应商乙", details.get(0).get("supplierName"));
            assertEquals("SAMPLE-POSTED", details.get(0).get("docNo"));
            assertEquals(java.time.LocalDate.now().plusDays(7).toString(),
                String.valueOf(details.get(0).get("supplierReturnDate")).substring(0, 10));
            assertEquals(6, ((Number) details.get(1).get("stockAgeDays")).intValue());
            assertEquals(4, ((Number) details.get(1).get("supplierReturnDays")).intValue());
            assertEquals(1, ((Number) details.get(1).get("totalStockQty")).intValue());
            assertEquals(0, ((Number) details.get(1).get("onHandQty")).intValue());
            assertEquals(4, details.stream().mapToInt(row -> ((Number) row.get("totalStockQty")).intValue()).sum());
            assertEquals(3, details.stream().mapToInt(row -> ((Number) row.get("onHandQty")).intValue()).sum());
            assertEquals(1, mapper.selectSampleInboundDetails(2L).size());
            assertTrue(mapper.selectSampleInboundDetails(3L).isEmpty());
        }

        execute("update sys_config set config_value='20' where config_key='jewelry.supplier.return.days'");
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            assertEquals(17, ((Number) session.getMapper(JewelryErpMapper.class)
                .selectSampleInboundDetails(1L).get(0).get("supplierReturnDays")).intValue());
        }
    }

    @Test
    void staffListReturnsOneRowWhenUserAlsoHasANonJewelryRole()
    {
        execute("insert into sys_user(user_id,user_name,status,del_flag) values(1,'erp-admin','0','0')");
        execute("insert into sys_role(role_id,role_key,role_name,del_flag) values"
            + "(1,'common','普通角色','0'),(2,'jewelry_admin','珠宝ERP管理员','0')");
        execute("insert into sys_user_role(user_id,role_id) values(1,1),(1,2)");
        execute("insert into jewelry_staff(staff_id,user_id,staff_no,real_name,status)"
            + " values(1,1,'010','测试管理员','0')");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            List<Map<String, Object>> staff = mapper.selectStaffList(Collections.emptyMap());

            assertEquals(1, staff.size());
            assertTrue(staff.get(0).containsValue("010"));
            assertTrue(staff.get(0).containsValue("jewelry_admin"));
        }
    }

    @Test
    void influencerPriceAndHistoryRoundTrip()
    {
        execute("insert into jewelry_product(product_id,sku,product_name,product_type,specification,status)"
            + " values(100,'SKU-100','测试商品','FINISHED','普通','0')");
        insertDocument(900L, "SALE-900", "SALES_OUT", "DRAFT", null);
        final Long[] influencerId = new Long[1];
        Map<String, Object> influencer = new HashMap<String, Object>();
        influencer.put("influencerCode", "KOL-001");
        influencer.put("externalInfluencerId", "DY-778899");
        influencer.put("influencerName", "测试主播");
        influencer.put("platform", "抖音");
        influencer.put("platformAccount", "douyin-001");
        influencer.put("salesChannel", "抖音");
        influencer.put("contactPhone", "13800000000");
        influencer.put("status", "0");
        influencer.put("createBy", "maker");
        influencer.put("remark", "");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.insertInfluencer(influencer));
            influencerId[0] = ((Number) influencer.get("influencerId")).longValue();
            String generatedCode = String.format("DR%06d", influencerId[0]);
            assertEquals(1, mapper.updateInfluencerCode(influencerId[0], generatedCode, "maker"));
            influencer.put("influencerCode", generatedCode);
            Map<String, Object> pending = new HashMap<String, Object>();
            pending.put("influencerId", influencerId[0]);
            pending.put("productId", 100L);
            pending.put("fixedUnitPrice", new BigDecimal("88.1234"));
            pending.put("sourceDocumentId", 900L);
            pending.put("userName", "maker");
            assertEquals(1, mapper.insertPendingInfluencerProductPrice(pending));
            assertEquals(1, mapper.promoteInfluencerProductPrice(influencerId[0], 100L, 900L, "reviewer"));

            Map<String, Object> history = new HashMap<String, Object>();
            history.put("influencerId", influencerId[0]);
            history.put("productId", 100L);
            history.put("oldPrice", null);
            history.put("newPrice", new BigDecimal("88.1234"));
            history.put("sourceType", "FIRST_SALE");
            history.put("sourceDocumentId", 900L);
            history.put("priceVersion", 1);
            history.put("changeReason", "首笔销售入账自动建立固定价");
            history.put("operatorUserId", 20L);
            history.put("operatorName", "reviewer");
            assertEquals(1, mapper.insertInfluencerPriceHistory(history));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            Map<String, Object> stored = mapper.selectInfluencerList(
                Collections.<String, Object>singletonMap("keyword", "测试")).get(0);
            assertEquals(influencer.get("influencerCode"), mapValue(stored, "influencerCode"));
            assertEquals("DY-778899", mapValue(stored, "externalInfluencerId"));
            assertEquals(1L, ((Number) mapValue(stored, "pricedProductCount")).longValue());
            Map<String, Object> productPrice = mapper.selectInfluencerProductPrices(influencerId[0]).get(0);
            assertEquals("PRICED", mapValue(productPrice, "priceStatus"));
            assertEquals("SKU-100", mapValue(productPrice, "sku"));
            assertEquals(0, new BigDecimal(String.valueOf(mapValue(productPrice, "fixedUnitPrice")))
                .compareTo(new BigDecimal("88.1234")));
            assertEquals(1, mapper.selectInfluencerPriceHistory(influencerId[0]).size());
        }
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            Map<String, Object> binding = new HashMap<String, Object>();
            binding.put("influencerId", influencerId[0]);
            binding.put("productId", 100L);
            binding.put("priceVersion", 1);
            binding.put("fixedUnitPrice", new BigDecimal("99.0000"));
            binding.put("unitCost", new BigDecimal("55.0000"));
            binding.put("platformRate", new BigDecimal("0.050000"));
            binding.put("commissionRate", new BigDecimal("0.200000"));
            binding.put("taxRate", new BigDecimal("0.010000"));
            binding.put("packFee", new BigDecimal("2.0000"));
            binding.put("shipFee", new BigDecimal("3.0000"));
            binding.put("certFee", new BigDecimal("4.0000"));
            binding.put("bindingStatus", "0");
            binding.put("bindingRemark", "本次直播约定");
            binding.put("userName", "admin");
            assertEquals(1, mapper.updateInfluencerBinding(binding));
            session.commit();
            Map<String, Object> saved = mapper.selectInfluencerProductPrices(influencerId[0]).get(0);
            assertEquals(0, new BigDecimal("99.0000").compareTo((BigDecimal) mapValue(saved, "fixedUnitPrice")));
            assertEquals(0, new BigDecimal("55.0000").compareTo((BigDecimal) mapValue(saved, "unitCost")));
            assertEquals(0, new BigDecimal("0.200000").compareTo((BigDecimal) mapValue(saved, "commissionRate")));
            assertEquals("本次直播约定", mapValue(saved, "bindingRemark"));
        }
    }

    @Test
    void supplierReturnSourcesAreCappedByAvailableStockNotPurchaseQuota()
    {
        insertDocument(1L, "PUR-29", "PURCHASE_IN", "POSTED", null);
        insertItem(101L, 1L, null, 10L, 29);
        insertDocument(2L, "RETURN-8", "SUPPLIER_RETURN", "POSTED", 1L);
        insertItem(201L, 2L, 101L, 10L, 8);
        insertDocument(3L, "RETURN-DRAFT", "SUPPLIER_RETURN", "DRAFT", 1L);
        insertItem(301L, 3L, 101L, 10L, 2);
        insertStock(10L, 12, 0, 30, 0, 40, 0, "750");
        // on-hand, frozen outbound, expected: inspection/defect stock never increases the limit.
        for (int[] sample : new int[][] {{12,0,12},{12,3,9},{30,0,21},{0,0,0},{12,12,0},{2,3,0}})
        {
            execute("update jewelry_stock set on_hand_qty=" + sample[0] + ",reserved_out_qty=" + sample[1] + " where product_id=10");
            try (SqlSession session = sqlSessionFactory.openSession())
            {
                JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
                assertEquals(sample[2], mapper.selectSupplierReturnSourceItems(1L, null).get(0).getRemainingReturnQty());
                assertEquals(sample[2], mapper.selectSupplierReturnSourceItems(1L, 3L).get(0).getRemainingReturnQty());
            }
        }
        execute("delete from jewelry_stock where product_id=10");
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            assertEquals(0, session.getMapper(JewelryErpMapper.class)
                .selectSupplierReturnSourceItems(1L, null).get(0).getRemainingReturnQty());
        }
    }

    @Test
    void supplierReturnSourcesTrackPendingAndPostedReturnedQuantities()
    {
        insertStock(10L, 5, 2, 0, 0, 0, 0, "12.3456");
        insertDocument(1L, "PURCHASE-1", "PURCHASE_IN", "POSTED", null);
        execute("update jewelry_document set supplier_id=9,influencer_id=17 where document_id=1");
        insertItem(101L, 1L, null, 10L, 5);
        execute("update jewelry_document_item set unit_price=12.3456 where item_id=101");
        insertDocument(2L, "RETURN-PENDING", "SUPPLIER_RETURN", "PENDING_FIRST", 1L);
        insertDocument(3L, "RETURN-REJECTED", "SUPPLIER_RETURN", "REJECTED", 1L);
        insertDocument(4L, "RETURN-DRAFT", "SUPPLIER_RETURN", "DRAFT", 1L);
        insertItem(201L, 2L, 101L, 10L, 2);
        insertItem(301L, 3L, 101L, 10L, 1);
        insertItem(401L, 4L, 101L, 10L, 1);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(2, mapper.selectSupplierReturnedQtyBySourceItem(101L, null));
            assertEquals(new BigDecimal("12.345600"), mapper.selectDocumentItems(2L).get(0).getSourceUnitPrice());
            List<JewelryDocumentItem> items = mapper.selectSupplierReturnSourceItems(1L, null);
            assertEquals(1, items.size());
            assertEquals(3, items.get(0).getRemainingReturnQty());
            assertEquals(1, mapper.selectSupplierReturnSourceList(17L, 9L).size());
            assertEquals(0, mapper.selectSupplierReturnSourceList(18L, 9L).size());
        }

        insertDocument(5L, "RETURN-POSTED", "SUPPLIER_RETURN", "POSTED", 1L);
        insertItem(501L, 5L, 101L, 10L, 3);
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(5, mapper.selectSupplierReturnedQtyBySourceItem(101L, null));
            assertEquals(0, mapper.selectSupplierReturnSourceList(17L, 9L).size());
        }
    }

    @Test
    void inspectionAndDefectReservationsRespectTheirOwnAvailableQuantities()
    {
        insertStock(1L, 5, 0, 3, 1, 2, 0, "100.00");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.reserveInspection(1L, 2));
            assertEquals(0, mapper.reserveInspection(1L, 1));
            assertEquals(1, mapper.reserveDefect(1L, 2));
            assertEquals(0, mapper.reserveDefect(1L, 1));
            assertEquals(1, mapper.releaseInspection(1L, 1));
            assertEquals(1, mapper.releaseDefect(1L, 1));
            session.commit();
        }

        assertEquals(2, intValue("select inspection_reserved_qty from jewelry_stock where product_id=1"));
        assertEquals(1, intValue("select defect_reserved_qty from jewelry_stock where product_id=1"));
    }

    @Test
    void documentStatusUpdateUsesCompareAndSetAndRecordsReviewers()
    {
        insertDocument(1L, "DOC-1", "PURCHASE_IN", "DRAFT", null);

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.updateDocumentStatus(1L, "DRAFT", "PENDING_FIRST",
                10L, "maker", null, null));
            assertEquals(0, mapper.updateDocumentStatus(1L, "DRAFT", "PENDING_FIRST",
                10L, "maker", null, null));
            assertEquals(1, mapper.updateDocumentStatus(1L, "PENDING_FIRST", "PENDING_SECOND",
                20L, "reviewer1", null, 1));
            assertEquals(1, mapper.updateDocumentStatus(1L, "PENDING_SECOND", "REJECTED",
                30L, "reviewer2", "wrong quantity", null));
            session.commit();
        }

        assertEquals("REJECTED", stringValue("select status from jewelry_document where document_id=1"));
        assertEquals("reviewer1",
            stringValue("select first_reviewer_name from jewelry_document where document_id=1"));
        assertEquals("reviewer2",
            stringValue("select reject_user_name from jewelry_document where document_id=1"));
        assertEquals("wrong quantity",
            stringValue("select reject_reason from jewelry_document where document_id=1"));
    }

    @Test
    void draftDeletionRemovesRelatedRecordsAndChecksCreator()
    {
        insertDocument(1L, "DOC-DRAFT", "SALES_OUT", "DRAFT", null);
        insertItem(101L, 1L, null, 10L, 1);
        execute("insert into jewelry_approval(document_id) values(1)");
        execute("insert into jewelry_document_event(document_id) values(1)");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(0, mapper.deleteDraftDocument(1L, 99L));
            assertEquals(1, mapper.deleteDocumentApprovals(1L));
            assertEquals(1, mapper.deleteDocumentEvents(1L));
            assertEquals(1, mapper.deleteDocumentItems(1L));
            assertEquals(1, mapper.deleteDraftDocument(1L, 10L));
            session.commit();
        }

        assertEquals(0, intValue("select count(*) from jewelry_document where document_id=1"));
        assertEquals(0, intValue("select count(*) from jewelry_document_item where document_id=1"));
        assertEquals(0, intValue("select count(*) from jewelry_approval where document_id=1"));
        assertEquals(0, intValue("select count(*) from jewelry_document_event where document_id=1"));
    }

    @Test
    void pendingDocumentFilterIncludesLegacySecondReview()
    {
        insertDocument(1L, "DOC-PENDING", "PURCHASE_IN", "PENDING_FIRST", null);
        insertDocument(2L, "DOC-LEGACY", "PURCHASE_IN", "PENDING_SECOND", null);
        insertDocument(3L, "DOC-POSTED", "PURCHASE_IN", "POSTED", null);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument query = new JewelryDocument();
            query.setStatus("PENDING");

            assertEquals(2, mapper.selectDocumentList(query).size());
        }
    }

    @Test
    void documentListCanHideUnsubmittedDraftsForReviewer()
    {
        insertDocument(1L, "MAKER-DRAFT", "PURCHASE_IN", "DRAFT", null);
        insertDocument(2L, "MAKER-PENDING", "PURCHASE_IN", "PENDING_FIRST", null);
        insertDocument(3L, "MAKER-POSTED", "PURCHASE_IN", "POSTED", null);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument query = new JewelryDocument();
            assertEquals(3, mapper.selectDocumentList(query).size());

            query.setHideDrafts(true);
            assertEquals(2, mapper.selectDocumentList(query).size());
            query.setStatus("DRAFT");
            assertEquals(0, mapper.selectDocumentList(query).size());
            query.setStatus("PENDING");
            assertEquals(1, mapper.selectDocumentList(query).size());
        }
    }

    @Test
    void customerReturnListCanFilterByInfluencerForInspection()
    {
        insertDocument(1L, "RETURN-A", "CUSTOMER_RETURN", "POSTED", null);
        insertDocument(2L, "RETURN-B", "CUSTOMER_RETURN", "POSTED", null);
        execute("update jewelry_document set influencer_id=17 where document_id=1");
        execute("update jewelry_document set influencer_id=18 where document_id=2");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryDocument query = new JewelryDocument();
            query.setDocType("CUSTOMER_RETURN");
            query.setStatus("POSTED");
            query.setInfluencerId(17L);

            List<JewelryDocument> sources = session.getMapper(JewelryErpMapper.class).selectDocumentList(query);
            assertEquals(1, sources.size());
            assertEquals("RETURN-A", sources.get(0).getDocNo());
        }
    }

    @Test
    void pendingSampleListShowsSuppliersStoredOnItems()
    {
        insertDocument(1L, "SAMPLE-PENDING", "SAMPLE_IN", "PENDING_FIRST", null);
        insertItem(11L, 1L, null, 10L, 1);
        insertItem(12L, 1L, null, 10L, 1);
        execute("update jewelry_document_item set supplier_name_snapshot='供应商甲' where item_id=11");
        execute("update jewelry_document_item set supplier_name_snapshot='供应商乙' where item_id=12");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryDocument query = new JewelryDocument();
            query.setStatus("PENDING");
            JewelryDocument sample = session.getMapper(JewelryErpMapper.class).selectDocumentList(query).get(0);
            List<String> names = Arrays.asList(sample.getItemSupplierNames().split("、"));
            assertEquals(2, names.size());
            assertTrue(names.contains("供应商甲"));
            assertTrue(names.contains("供应商乙"));
        }
    }

    @Test
    void pendingCostAdjustmentAndInboundQueriesAreScopedBySku()
    {
        insertDocument(1L, "COST-PENDING", "COST_ADJUST", "PENDING_FIRST", null);
        insertDocument(2L, "COST-POSTED", "COST_ADJUST", "POSTED", null);
        insertDocument(3L, "COST-REVERSAL", "REVERSAL", "PENDING_SECOND", 2L);
        insertDocument(4L, "PURCHASE-PENDING", "PURCHASE_IN", "PENDING_FIRST", null);
        insertDocument(5L, "COST-DRAFT", "COST_ADJUST", "DRAFT", null);
        insertDocument(6L, "SAMPLE-PENDING", "SAMPLE_IN", "PENDING_FIRST", null);
        insertItem(101L, 1L, null, 10L, 1);
        insertItem(102L, 3L, null, 11L, 1);
        insertItem(103L, 4L, null, 12L, 1);
        insertItem(104L, 5L, null, 13L, 1);
        insertItem(105L, 6L, null, 14L, 1);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.countPendingCostChangesByProduct(10L));
            assertEquals(1, mapper.countPendingCostChangesByProduct(11L));
            assertEquals(0, mapper.countPendingCostChangesByProduct(13L));
            assertEquals(1, mapper.countPendingPurchasesByProduct(12L));
            assertEquals(1, mapper.countPendingPurchasesByProduct(14L));
            assertEquals(0, mapper.countPendingPurchasesByProduct(10L));
            assertEquals("COST_ADJUST", mapper.selectDocumentById(3L).getSourceDocType());
        }
    }

    @Test
    void sampleItemBusinessDatePersistsIndependentlyOfDocumentDate()
    {
        insertDocument(70L, "SAMPLE-DRAFT", "SAMPLE_IN", "DRAFT", null);
        insertItem(701L, 70L, null, 10L, 1);

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocumentItem item = mapper.selectDocumentItems(70L).get(0);
            item.setItemId(null);
            item.setBizDate(java.sql.Date.valueOf("2026-09-18"));
            item.setSupplierId(11L);
            item.setSupplierNameSnapshot("供应商甲");
            item.setSampleGoodsNo("YP-001");
            item.setImageUrls("/profile/upload/sample.jpg");
            mapper.insertDocumentItem(item);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            List<JewelryDocumentItem> items = mapper.selectDocumentItems(70L);
            assertEquals(null, items.get(0).getBizDate());
            assertEquals(java.sql.Date.valueOf("2026-09-18"), items.get(1).getBizDate());
            assertEquals(11L, items.get(1).getSupplierId());
            assertEquals("供应商甲", items.get(1).getSupplierNameSnapshot());
            assertEquals("YP-001", items.get(1).getSampleGoodsNo());
            assertEquals("/profile/upload/sample.jpg", items.get(1).getImageUrls());
        }
    }

    @Test
    void inventoryChangeFilterIncludesStockAndCostAdjustmentsOnly()
    {
        insertDocument(1L, "PD-POSTED", "STOCK_ADJUST", "POSTED", null);
        insertDocument(2L, "TJ-POSTED", "COST_ADJUST", "POSTED", null);
        insertDocument(3L, "CG-POSTED", "PURCHASE_IN", "POSTED", null);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument query = new JewelryDocument();
            query.setDocType("INVENTORY_CHANGE");

            assertEquals(2, mapper.selectDocumentList(query).size());
            assertEquals("COST_ADJUST", mapper.selectDocumentList(query).get(0).getDocType());
            assertEquals("STOCK_ADJUST", mapper.selectDocumentList(query).get(1).getDocType());
        }
    }

    @Test
    void returnedQuantityIgnoresRejectedDocumentsAndCanExcludeCurrentDraft()
    {
        insertDocument(1L, "SALE-1", "SALES_OUT", "POSTED", null);
        insertDocument(2L, "RETURN-POSTED", "CUSTOMER_RETURN", "POSTED", 1L);
        insertDocument(3L, "RETURN-REJECTED", "CUSTOMER_RETURN", "REJECTED", 1L);
        insertDocument(4L, "RETURN-DRAFT", "CUSTOMER_RETURN", "DRAFT", 1L);
        insertItem(101L, 1L, null, 10L, 8);
        insertItem(102L, 2L, 101L, 10L, 2);
        insertItem(103L, 3L, 101L, 10L, 7);
        insertItem(104L, 4L, 101L, 10L, 3);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(5, mapper.selectReturnedQtyBySourceItem(101L, null));
            assertEquals(2, mapper.selectReturnedQtyBySourceItem(101L, 4L));
        }
    }

    @Test
    void returnInspectionSourceReportsRemainingQuantityPerReturnLine()
    {
        insertDocument(1L, "RETURN-1", "CUSTOMER_RETURN", "POSTED", null);
        insertDocument(2L, "INSPECT-POSTED", "RETURN_INSPECT", "POSTED", 1L);
        insertDocument(3L, "INSPECT-REJECTED", "RETURN_INSPECT", "REJECTED", 1L);
        insertDocument(4L, "INSPECT-DRAFT", "RETURN_INSPECT", "DRAFT", 1L);
        insertItem(101L, 1L, null, 10L, 5);
        insertItem(201L, 2L, 101L, 10L, 3);
        insertItem(301L, 3L, 101L, 10L, 2);
        insertItem(401L, 4L, 101L, 10L, 1);
        execute("update jewelry_document_item set good_qty=2,defect_qty=1 where item_id=201");
        execute("update jewelry_document_item set good_qty=2 where item_id=301");
        execute("update jewelry_document_item set good_qty=1 where item_id=401");

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(4, mapper.selectInspectedQtyBySourceItem(101L, null));
            assertEquals(3, mapper.selectInspectedQtyBySourceItem(101L, 4L));
            assertEquals(1, mapper.selectReturnInspectionSourceItems(1L, null).get(0)
                .getRemainingInspectQty().intValue());
            assertEquals(2, mapper.selectReturnInspectionSourceItems(1L, 4L).get(0)
                .getRemainingInspectQty().intValue());
        }
    }

    @Test
    void activeReturnCountExcludesDraftRejectedAndAlreadyReversedReturns()
    {
        insertDocument(1L, "SALE-1", "SALES_OUT", "POSTED", null);
        insertDocument(2L, "RETURN-PENDING", "CUSTOMER_RETURN", "PENDING_FIRST", 1L);
        insertDocument(3L, "RETURN-POSTED", "CUSTOMER_RETURN", "POSTED", 1L);
        insertDocument(4L, "RETURN-DRAFT", "CUSTOMER_RETURN", "DRAFT", 1L);
        insertDocument(5L, "RETURN-REJECTED", "CUSTOMER_RETURN", "REJECTED", 1L);
        insertDocument(6L, "RETURN-REVERSED", "CUSTOMER_RETURN", "REVERSED", 1L);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(2, mapper.countActiveCustomerReturnsBySource(1L));
        }
    }

    @Test
    void originalDocumentCanOnlyBeMarkedReversedOnce()
    {
        insertDocument(1L, "SALE-1", "SALES_OUT", "POSTED", null);

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.markOriginalReversed(1L, "admin"));
            assertEquals(0, mapper.markOriginalReversed(1L, "admin"));
            session.commit();
        }

        assertEquals("REVERSED", stringValue("select status from jewelry_document where document_id=1"));
    }

    @Test
    void applyStockPersistsAllInventoryBucketsAndCosts()
    {
        insertStock(1L, 5, 1, 2, 1, 3, 1, "100.00");

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.applyStock(1L, 8, 2, 4, 2, 5, 3,
                new BigDecimal("125.500000"), new BigDecimal("300.000000"),
                new BigDecimal("450.000000")));
            session.commit();
        }

        assertEquals(8, intValue("select on_hand_qty from jewelry_stock where product_id=1"));
        assertEquals(2, intValue("select reserved_out_qty from jewelry_stock where product_id=1"));
        assertEquals(4, intValue("select inspection_qty from jewelry_stock where product_id=1"));
        assertEquals(5, intValue("select defect_qty from jewelry_stock where product_id=1"));
        assertEquals(0, decimalValue("select avg_cost from jewelry_stock where product_id=1")
            .compareTo(new BigDecimal("125.500000")));
    }

    @Test
    void salesBundleAndRefundFieldsRoundTrip()
    {
        JewelryDocument document = new JewelryDocument();
        document.setDocNo("SALE-BUNDLE-1");
        document.setDocType("SALES_OUT");
        document.setBizDate(new java.util.Date());
        document.setStatus("DRAFT");
        document.setActualRefundAmount(new BigDecimal("25.00"));
        document.setTotalQty(2);
        document.setTotalAmount(new BigDecimal("1000.00"));
        document.setTotalCost(new BigDecimal("680.00"));
        document.setTotalProfit(new BigDecimal("320.00"));
        document.setRiskStatus("NORMAL");
        document.setLaborFee(BigDecimal.ZERO);
        document.setProcessingFee(BigDecimal.ZERO);
        document.setOtherFee(BigDecimal.ZERO);
        document.setCreatorUserId(10L);
        document.setCreatorName("maker");
        document.setCreateBy("maker");

        JewelryDocumentItem item = new JewelryDocumentItem();
        item.setProductId(1L);
        item.setItemRole("NORMAL");
        item.setBundleGroupNo(1);
        item.setSaleRole("ADDON");
        item.setPricingMode("INCLUDED");
        item.setSkuSnapshot("PART-1");
        item.setProductNameSnapshot("Bundled part");
        item.setProductTypeSnapshot("PART");
        item.setSpecificationSnapshot("普通");
        item.setQty(1);
        item.setGoodQty(0);
        item.setDefectQty(0);
        item.setAdjustmentQty(0);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setUnitCost(new BigDecimal("80.00"));
        item.setPackFee(BigDecimal.ZERO);
        item.setShipFee(BigDecimal.ZERO);
        item.setCertFee(BigDecimal.ZERO);
        item.setOtherFee1(new BigDecimal("1.00"));
        item.setOtherFee2(new BigDecimal("2.00"));
        item.setOtherFee3(new BigDecimal("3.00"));
        item.setPlatformRateSnapshot(new BigDecimal("0.050000"));
        item.setCommissionRateSnapshot(new BigDecimal("0.200000"));
        item.setTaxRateSnapshot(new BigDecimal("0.010000"));
        item.setAmount(BigDecimal.ZERO);
        item.setCostAmount(new BigDecimal("80.00"));
        item.setProfitAmount(new BigDecimal("-80.00"));
        item.setProfitRate(BigDecimal.ZERO);

        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.insertDocument(document));
            item.setDocumentId(document.getDocumentId());
            assertEquals(1, mapper.insertDocumentItem(item));
            item.setProductTypeSnapshot("ACCESSORY");
            item.setUnitCost(new BigDecimal("85.00"));
            item.setCostAmount(new BigDecimal("85.00"));
            item.setProfitAmount(new BigDecimal("-85.00"));
            assertEquals(1, mapper.updateDocumentItemCost(item));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            JewelryDocument stored = mapper.selectDocumentById(document.getDocumentId());
            JewelryDocumentItem storedItem = mapper.selectDocumentItems(document.getDocumentId()).get(0);
            assertEquals(0, stored.getActualRefundAmount().compareTo(new BigDecimal("25.00")));
            assertEquals(1, storedItem.getBundleGroupNo());
            assertEquals("ADDON", storedItem.getSaleRole());
            assertEquals("INCLUDED", storedItem.getPricingMode());
            assertEquals("ACCESSORY", storedItem.getProductTypeSnapshot());
            assertEquals(0, storedItem.getUnitCost().compareTo(new BigDecimal("85.00")));
            assertEquals(0, storedItem.getOtherFee1().compareTo(new BigDecimal("1.00")));
            assertEquals(0, storedItem.getOtherFee2().compareTo(new BigDecimal("2.00")));
            assertEquals(0, storedItem.getOtherFee3().compareTo(new BigDecimal("3.00")));
            assertEquals(0, storedItem.getPlatformRateSnapshot().compareTo(new BigDecimal("0.050000")));
            assertEquals(0, storedItem.getCommissionRateSnapshot().compareTo(new BigDecimal("0.200000")));
            assertEquals(0, storedItem.getTaxRateSnapshot().compareTo(new BigDecimal("0.010000")));
            assertEquals("普通", storedItem.getSpecificationSnapshot());
        }
    }

    @Test
    void concurrentOutboundReservationsCannotOversell() throws Exception
    {
        insertStock(1L, 5, 0, 0, 0, 0, 0, "100.00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
        try
        {
            for (int i = 0; i < 2; i++)
            {
                futures.add(pool.submit(() -> {
                    try (SqlSession session = sqlSessionFactory.openSession(false))
                    {
                        JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        int rows = mapper.reserveOutbound(1L, 4);
                        session.commit();
                        return rows;
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            List<Integer> results = Arrays.asList(
                futures.get(0).get(10, TimeUnit.SECONDS),
                futures.get(1).get(10, TimeUnit.SECONDS));
            Collections.sort(results);
            assertEquals(Arrays.asList(0, 1), results);
        }
        finally
        {
            pool.shutdownNow();
        }

        assertEquals(4, intValue("select reserved_out_qty from jewelry_stock where product_id=1"));
    }

    private void createSchema() throws Exception
    {
        execute("create table jewelry_stock_transaction (transaction_id bigint primary key,"
            + "product_id bigint,document_id bigint,item_id bigint,on_hand_change int,create_time timestamp)");
        execute("create table sys_config (config_key varchar(100) unique,config_value varchar(100),"
            + "config_name varchar(100),config_type char(1),create_by varchar(64),update_by varchar(64),"
            + "create_time timestamp,update_time timestamp,remark varchar(500))");
        execute("create table sys_user (user_id bigint primary key,user_name varchar(64),"
            + "status char(1),del_flag char(1))");
        execute("create table sys_role (role_id bigint primary key,role_key varchar(100),"
            + "role_name varchar(100),del_flag char(1))");
        execute("create table sys_user_role (user_id bigint,role_id bigint)");
        execute("create table jewelry_staff (staff_id bigint primary key,user_id bigint,"
            + "staff_no varchar(32),real_name varchar(64),phone varchar(32),status char(1),"
            + "joined_date date,remark varchar(500))");
        execute("create table jewelry_supplier (supplier_id bigint primary key,supplier_name varchar(128))");
        execute("create table jewelry_product ("
            + "product_id bigint auto_increment primary key,sku varchar(64) not null unique,"
            + "product_name varchar(128) not null,product_type varchar(16) not null,category varchar(64) default '',"
            + "specification varchar(16) not null default '普通',image_url varchar(500),image_urls varchar(1000),"
            + "unit varchar(16),default_pack_fee decimal(18,6) default 0,"
            + "default_ship_fee decimal(18,6) default 0,default_cert_fee decimal(18,6) default 0,"
            + "warning_qty int default 5,status char(1) default '0',create_by varchar(64),create_time timestamp,"
            + "update_by varchar(64),update_time timestamp,remark varchar(500))");
        execute("create table jewelry_stock ("
            + "product_id bigint primary key,on_hand_qty int not null default 0,"
            + "reserved_out_qty int not null default 0,inspection_qty int not null default 0,"
            + "inspection_reserved_qty int not null default 0,defect_qty int not null default 0,"
            + "defect_reserved_qty int not null default 0,avg_cost decimal(18,6) not null default 0,"
            + "inspection_cost_amount decimal(20,6) not null default 0,"
            + "defect_cost_amount decimal(20,6) not null default 0,version int not null default 0,"
            + "update_time timestamp)");
        execute("create table jewelry_document ("
            + "document_id bigint auto_increment primary key,doc_no varchar(32) not null unique,"
            + "doc_type varchar(32) not null,biz_date date not null,supplier_return_date date,source_warehouse varchar(100),target_warehouse varchar(100),status varchar(24) not null,"
            + "supplier_id bigint,supplier_name_snapshot varchar(128) default '',"
            + "sales_channel varchar(64) default '',external_no varchar(64) default '',influencer_id bigint,"
            + "influencer_name varchar(64) default '',influencer_price_snapshot decimal(18,4),"
            + "influencer_price_version int,platform_rate decimal(9,6) default 0,"
            + "commission_rate decimal(9,6) default 0,tax_rate decimal(9,6) default 0,"
            + "return_reason varchar(255) default '',source_document_id bigint,"
            + "unlinked_reason varchar(255) default '',actual_refund_amount decimal(20,2),"
            + "total_qty int not null default 0,"
            + "total_amount decimal(20,4) not null default 0,total_cost decimal(20,4) not null default 0,"
            + "total_profit decimal(20,2) not null default 0,risk_status varchar(16) default 'NORMAL',"
            + "labor_fee decimal(18,2) default 0,processing_fee decimal(18,2) default 0,"
            + "other_fee decimal(18,2) default 0,"
            + "creator_user_id bigint not null,creator_name varchar(64) not null,"
            + "first_reviewer_user_id bigint,first_reviewer_name varchar(64) default '',"
            + "second_reviewer_user_id bigint,second_reviewer_name varchar(64) default '',"
            + "reject_user_id bigint,reject_user_name varchar(64) default '',reject_reason varchar(500) default '',"
            + "version int not null default 0,create_by varchar(64) default '',create_time timestamp,"
            + "update_by varchar(64) default '',update_time timestamp,remark varchar(500))");
        execute("create table jewelry_document_item ("
            + "item_id bigint auto_increment primary key,document_id bigint not null,product_id bigint not null,"
            + "item_role varchar(16) not null default 'NORMAL',source_item_id bigint,bundle_group_no int,"
            + "sale_role varchar(16) not null default 'NORMAL',pricing_mode varchar(16) not null default 'SEPARATE',"
            + "sku_snapshot varchar(64) not null,product_name_snapshot varchar(128) not null,"
            + "product_type_snapshot varchar(16),specification_snapshot varchar(16),image_urls varchar(1000),biz_date date,"
            + "supplier_id bigint,supplier_name_snapshot varchar(128),sample_goods_no varchar(64),"
            + "qty int not null default 0,good_qty int not null default 0,defect_qty int not null default 0,"
            + "system_qty int,counted_qty int,adjustment_qty int not null default 0,"
            + "unit_price decimal(18,6) not null default 0,influencer_price_snapshot decimal(18,4),"
            + "influencer_price_version int,unit_cost decimal(18,6) not null default 0,"
            + "platform_rate_snapshot decimal(9,6),commission_rate_snapshot decimal(9,6),tax_rate_snapshot decimal(9,6),"
            + "pack_fee decimal(18,6) not null default 0,ship_fee decimal(18,6) not null default 0,"
            + "cert_fee decimal(18,6) not null default 0,other_fee1 decimal(18,6) not null default 0,"
            + "other_fee2 decimal(18,6) not null default 0,other_fee3 decimal(18,6) not null default 0,"
            + "amount decimal(20,4) not null default 0,"
            + "cost_amount decimal(20,4) not null default 0,profit_amount decimal(20,2) not null default 0,"
            + "profit_rate decimal(9,6) not null default 0,line_reason varchar(255) default '')");
        execute("create table jewelry_approval (approval_id bigint auto_increment primary key,document_id bigint not null)");
        execute("create table jewelry_document_event (event_id bigint auto_increment primary key,document_id bigint not null)");
        execute("create table jewelry_influencer (influencer_id bigint auto_increment primary key,"
            + "influencer_code varchar(32) not null unique,influencer_name varchar(128) not null,"
            + "external_influencer_id varchar(64) default '',"
            + "platform varchar(64) default '',platform_code varchar(8),platform_account varchar(128) default '',sales_channel varchar(64) default '',"
            + "fixed_unit_price decimal(18,4),price_status varchar(16) default 'UNPRICED',price_version int default 0,"
            + "price_source_document_id bigint,price_effective_time timestamp,last_sale_time timestamp,"
            + "contact_phone varchar(32) default '',status char(1) default '0',create_by varchar(64),create_time timestamp,"
            + "update_by varchar(64),update_time timestamp,remark varchar(500))");
        execute("create table jewelry_influencer_product_price (price_id bigint auto_increment primary key,"
            + "influencer_id bigint not null,product_id bigint not null,fixed_unit_price decimal(18,4) not null,"
            + "unit_cost decimal(18,4) not null default 0,"
            + "price_status varchar(16) not null default 'PENDING',price_version int not null default 0,"
            + "pending_source_document_id bigint,price_source_document_id bigint,price_effective_time timestamp,"
            + "commission_rate decimal(9,6),platform_rate decimal(9,6),tax_rate decimal(9,6),"
            + "pack_fee decimal(18,4),ship_fee decimal(18,4),cert_fee decimal(18,4),"
            + "preferred_supplier_id bigint,reference_purchase_price decimal(18,4) default 0,"
            + "binding_status char(1) default '0',binding_remark varchar(500),"
            + "create_by varchar(64),create_time timestamp,update_by varchar(64),update_time timestamp,"
            + "unique(influencer_id,product_id))");
        execute("create table jewelry_influencer_price_history (history_id bigint auto_increment primary key,"
            + "influencer_id bigint not null,product_id bigint not null,old_price decimal(18,4),new_price decimal(18,4) not null,"
            + "source_type varchar(24) not null,source_document_id bigint,price_version int not null,"
            + "change_reason varchar(500) not null,operator_user_id bigint not null,operator_name varchar(64) not null,"
            + "create_time timestamp not null)");
        execute("create table jewelry_influencer_bundle_item (bundle_item_id bigint auto_increment primary key,"
            + "influencer_id bigint not null,main_product_id bigint not null,addon_product_id bigint not null,"
            + "main_qty int not null,addon_qty int not null,pricing_mode varchar(16) not null default 'INCLUDED',"
            + "source_document_id bigint not null,last_sale_time timestamp not null,create_by varchar(64),"
            + "create_time timestamp,update_by varchar(64),update_time timestamp,"
            + "unique(influencer_id,main_product_id,addon_product_id))");
        execute("create table jewelry_influencer_platform (platform_code varchar(8) primary key,"
            + "platform_name varchar(64),next_no bigint,status char(1))");
        execute("create table jewelry_influencer_bundle_config (config_id bigint auto_increment primary key,"
            + "influencer_id bigint,main_product_id bigint,addon_product_id bigint,"
            + "main_qty int,addon_qty int,pricing_mode varchar(16),create_by varchar(64),"
            + "create_time timestamp,update_by varchar(64),update_time timestamp,"
            + "unique(influencer_id,main_product_id,addon_product_id))");
    }

    private void insertStock(Long productId, int onHand, int reserved, int inspection,
        int inspectionReserved, int defect, int defectReserved, String avgCost)
    {
        execute("insert into jewelry_stock(product_id,on_hand_qty,reserved_out_qty,inspection_qty,"
            + "inspection_reserved_qty,defect_qty,defect_reserved_qty,avg_cost,"
            + "inspection_cost_amount,defect_cost_amount,version) values("
            + productId + "," + onHand + "," + reserved + "," + inspection + "," + inspectionReserved
            + "," + defect + "," + defectReserved + "," + avgCost + ",0,0,0)");
    }

    private void insertDocument(Long id, String number, String type, String status, Long sourceId)
    {
        execute("insert into jewelry_document(document_id,doc_no,doc_type,biz_date,status,source_document_id,"
            + "creator_user_id,creator_name) values(" + id + ",'" + number + "','" + type
            + "',current_date,'" + status + "'," + (sourceId == null ? "null" : sourceId)
            + ",10,'maker')");
    }

    private void insertItem(Long id, Long documentId, Long sourceItemId, Long productId, int qty)
    {
        execute("insert into jewelry_document_item(item_id,document_id,product_id,source_item_id,"
            + "sku_snapshot,product_name_snapshot,qty) values(" + id + "," + documentId + ","
            + productId + "," + (sourceItemId == null ? "null" : sourceItemId)
            + ",'SKU','Product'," + qty + ")");
    }

    private void execute(String sql)
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private int intValue(String sql)
    {
        return ((Number) scalar(sql)).intValue();
    }

    private String stringValue(String sql)
    {
        return String.valueOf(scalar(sql));
    }

    private BigDecimal decimalValue(String sql)
    {
        return new BigDecimal(String.valueOf(scalar(sql)));
    }

    private Object mapValue(Map<String, Object> row, String key)
    {
        return row.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private Object scalar(String sql)
    {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getObject(1);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
