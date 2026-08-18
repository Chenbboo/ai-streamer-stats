package com.ruoyi.jewelry.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void pendingCostAdjustmentAndPurchaseQueriesAreScopedBySku()
    {
        insertDocument(1L, "COST-PENDING", "COST_ADJUST", "PENDING_FIRST", null);
        insertDocument(2L, "COST-POSTED", "COST_ADJUST", "POSTED", null);
        insertDocument(3L, "COST-REVERSAL", "REVERSAL", "PENDING_SECOND", 2L);
        insertDocument(4L, "PURCHASE-PENDING", "PURCHASE_IN", "PENDING_FIRST", null);
        insertDocument(5L, "COST-DRAFT", "COST_ADJUST", "DRAFT", null);
        insertItem(101L, 1L, null, 10L, 1);
        insertItem(102L, 3L, null, 11L, 1);
        insertItem(103L, 4L, null, 12L, 1);
        insertItem(104L, 5L, null, 13L, 1);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            JewelryErpMapper mapper = session.getMapper(JewelryErpMapper.class);
            assertEquals(1, mapper.countPendingCostChangesByProduct(10L));
            assertEquals(1, mapper.countPendingCostChangesByProduct(11L));
            assertEquals(0, mapper.countPendingCostChangesByProduct(13L));
            assertEquals(1, mapper.countPendingPurchasesByProduct(12L));
            assertEquals(0, mapper.countPendingPurchasesByProduct(10L));
            assertEquals("COST_ADJUST", mapper.selectDocumentById(3L).getSourceDocType());
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
        execute("create table jewelry_product ("
            + "product_id bigint auto_increment primary key,sku varchar(64) not null unique,"
            + "product_name varchar(128) not null,product_type varchar(16) not null,category varchar(64),"
            + "specification varchar(16) not null,image_url varchar(500),image_urls varchar(1000),"
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
            + "doc_type varchar(32) not null,biz_date date not null,status varchar(24) not null,"
            + "supplier_id bigint,supplier_name_snapshot varchar(128) default '',"
            + "sales_channel varchar(64) default '',external_no varchar(64) default '',"
            + "influencer_name varchar(64) default '',platform_rate decimal(9,6) default 0,"
            + "commission_rate decimal(9,6) default 0,tax_rate decimal(9,6) default 0,"
            + "return_reason varchar(255) default '',source_document_id bigint,"
            + "unlinked_reason varchar(255) default '',actual_refund_amount decimal(20,2),"
            + "total_qty int not null default 0,"
            + "total_amount decimal(20,2) not null default 0,total_cost decimal(20,2) not null default 0,"
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
            + "product_type_snapshot varchar(16),specification_snapshot varchar(16),image_urls varchar(1000),"
            + "qty int not null default 0,good_qty int not null default 0,defect_qty int not null default 0,"
            + "system_qty int,counted_qty int,adjustment_qty int not null default 0,"
            + "unit_price decimal(18,6) not null default 0,unit_cost decimal(18,6) not null default 0,"
            + "pack_fee decimal(18,6) not null default 0,ship_fee decimal(18,6) not null default 0,"
            + "cert_fee decimal(18,6) not null default 0,other_fee1 decimal(18,6) not null default 0,"
            + "other_fee2 decimal(18,6) not null default 0,other_fee3 decimal(18,6) not null default 0,"
            + "amount decimal(20,2) not null default 0,"
            + "cost_amount decimal(20,2) not null default 0,profit_amount decimal(20,2) not null default 0,"
            + "profit_rate decimal(9,6) not null default 0,line_reason varchar(255) default '')");
        execute("create table jewelry_approval (approval_id bigint auto_increment primary key,document_id bigint not null)");
        execute("create table jewelry_document_event (event_id bigint auto_increment primary key,document_id bigint not null)");
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
