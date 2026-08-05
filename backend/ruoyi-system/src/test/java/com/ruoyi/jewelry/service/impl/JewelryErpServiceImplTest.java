package com.ruoyi.jewelry.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.domain.JewelryDocumentItem;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

@ExtendWith(MockitoExtension.class)
class JewelryErpServiceImplTest
{
    private static final Long MAKER_ID = 10L;
    private static final Long REVIEWER_ONE_ID = 20L;
    private static final Long REVIEWER_TWO_ID = 30L;
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private JewelryErpMapper mapper;

    @InjectMocks
    private JewelryErpServiceImpl service;

    @BeforeEach
    void defaultWriteResults()
    {
        lenient().when(mapper.updateDocumentStatus(anyLong(), anyString(), anyString(), anyLong(), anyString(),
            any(), any())).thenReturn(1);
        lenient().when(mapper.selectProductById(anyLong())).thenReturn(product());
        lenient().when(mapper.selectStockForUpdate(anyLong()))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "100.00", "0", "0"));
        lenient().when(mapper.selectSupplierById(anyLong())).thenReturn(activeSupplier());
    }

    @Test
    void productTypeMustUseSupportedBusinessType()
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productType", "OTHER");
        product.put("specification", "普通");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveProduct(product));

        assertTrue(error.getMessage().contains("商品类型只能选择"));
        verify(mapper, never()).insertProduct(any());
        verify(mapper, never()).updateProduct(any());
    }

    @Test
    void productSpecificationMustBePremiumOrNormal()
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productType", "FINISHED");
        product.put("specification", "小");

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveProduct(product));

        assertTrue(error.getMessage().contains("规格类型只能选择精品或普通"));
        verify(mapper, never()).insertProduct(any());
        verify(mapper, never()).updateProduct(any());
    }

    @Test
    void accessoryAndWelfareProductTypesCanBeSaved()
    {
        when(mapper.updateProduct(any())).thenReturn(1);
        for (String type : Arrays.asList("ACCESSORY", "WELFARE"))
        {
            Map<String, Object> product = new HashMap<String, Object>();
            product.put("productId", "ACCESSORY".equals(type) ? 201L : 202L);
            product.put("productType", type);
            product.put("specification", "普通");
            product.put("status", "0");
            assertEquals(1, service.saveProduct(product));
        }
    }

    @Test
    void makerCannotApproveOwnDocument()
    {
        JewelryDocument document = document(1L, "PURCHASE_IN", "PENDING_FIRST");
        stubDocument(document, item(11L, 2, "100.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(1L, "", null, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("制单人不能审核自己的单据"));
        verify(mapper, never()).insertApproval(anyLong(), anyInt(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void legacySecondReviewCanBeApprovedByAssignedReviewer()
    {
        JewelryDocument document = document(2L, "PURCHASE_IN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        stubDocument(document, item(12L, 2, "100.00"));

        service.approve(2L, "", null, REVIEWER_ONE_ID, "reviewer1");

        verify(mapper).updateDocumentStatus(2L, "PENDING_SECOND", "POSTED",
            REVIEWER_ONE_ID, "reviewer1", null, 2);
        verify(mapper).insertApproval(2L, 2, "PASS", REVIEWER_ONE_ID, "reviewer1", "");
        verify(mapper).insertEvent(2L, "APPROVE", "PENDING_SECOND", "POSTED",
            REVIEWER_ONE_ID, "reviewer1", "");
    }

    @Test
    void approvalPostsDocumentImmediately()
    {
        JewelryDocument document = document(3L, "PURCHASE_IN", "PENDING_FIRST");
        stubDocument(document, item(13L, 1, "100.00"));

        service.approve(3L, "checked", null, REVIEWER_ONE_ID, "reviewer1");

        verify(mapper).updateDocumentStatus(3L, "PENDING_FIRST", "POSTED",
            REVIEWER_ONE_ID, "reviewer1", null, 1);
        verify(mapper).insertApproval(3L, 1, "PASS", REVIEWER_ONE_ID, "reviewer1", "checked");
        verify(mapper).insertEvent(3L, "APPROVE", "PENDING_FIRST", "POSTED",
            REVIEWER_ONE_ID, "reviewer1", "checked");
    }

    @Test
    void rejectionRequiresAReason()
    {
        JewelryDocument document = document(4L, "SALES_OUT", "PENDING_FIRST");
        stubDocument(document, item(14L, 1, "500.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reject(4L, " ", REVIEWER_ONE_ID, "reviewer1"));

        assertTrue(error.getMessage().contains("驳回原因不能为空"));
        verify(mapper, never()).releaseOutbound(anyLong(), anyInt());
    }

    @Test
    void rejectingSalesDocumentReleasesReservedStock()
    {
        JewelryDocument document = document(5L, "SALES_OUT", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(15L, 3, "500.00");
        stubDocument(document, item);
        when(mapper.releaseOutbound(PRODUCT_ID, 3)).thenReturn(1);

        service.reject(5L, "wrong quantity", REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).releaseOutbound(PRODUCT_ID, 3);
        verify(mapper).updateDocumentStatus(5L, "PENDING_SECOND", "REJECTED",
            REVIEWER_TWO_ID, "reviewer2", "wrong quantity", null);
        verify(mapper).insertApproval(5L, 2, "REJECT", REVIEWER_TWO_ID, "reviewer2", "wrong quantity");
    }

    @Test
    void submittingSalesDocumentReservesOutboundStock()
    {
        JewelryDocument document = document(6L, "SALES_OUT", "DRAFT");
        document.setSalesChannel("douyin");
        stubDocument(document, item(16L, 4, "500.00"));
        when(mapper.reserveOutbound(PRODUCT_ID, 4)).thenReturn(1);

        service.submit(6L, MAKER_ID, "maker");

        verify(mapper).reserveOutbound(PRODUCT_ID, 4);
        verify(mapper).updateDocumentStatus(6L, "DRAFT", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
    }

    @Test
    void salesRiskAssessmentRejectsNegativeFees()
    {
        JewelryDocument sales = document(null, "SALES_OUT", "DRAFT");
        sales.setSalesChannel("douyin");
        JewelryDocumentItem salesItem = item(null, 1, "500.00");
        salesItem.setPackFee(decimal("-0.01"));
        sales.setItems(Arrays.asList(salesItem));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assessDocumentRisk(sales));

        assertTrue(error.getMessage().contains("包装费不能小于0"));
    }

    @Test
    void salesRiskAssessmentRejectsCombinedRatesAtOneHundredPercent()
    {
        JewelryDocument sales = document(null, "SALES_OUT", "DRAFT");
        sales.setSalesChannel("douyin");
        sales.setPlatformRate(decimal("0.30"));
        sales.setCommissionRate(decimal("0.60"));
        sales.setTaxRate(decimal("0.10"));
        sales.setItems(Arrays.asList(item(null, 1, "500.00")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assessDocumentRisk(sales));

        assertTrue(error.getMessage().contains("合计必须小于100%"));
    }

    @Test
    void salesRiskAssessmentReturnsOnlyLossSignal()
    {
        JewelryDocument sales = document(null, "SALES_OUT", "DRAFT");
        sales.setSalesChannel("douyin");
        sales.setItems(Arrays.asList(item(null, 1, "500.00")));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "700.00", "0", "0"));

        Map<String, Object> result = service.assessDocumentRisk(sales);

        assertEquals(2, result.size());
        assertEquals("LOSS", result.get("riskStatus"));
        assertEquals(Boolean.TRUE, result.get("loss"));
        assertTrue(!result.containsKey("totalCost"));
        assertTrue(!result.containsKey("totalProfit"));
    }

    @Test
    void profitCalculatorUsesSameValidatedSalesFormula()
    {
        Map<String, Object> product = product();
        product.put("onHandQty", 10);
        product.put("reservedOutQty", 2);
        product.put("avgCost", decimal("300.00"));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product);
        Map<String, Object> input = new HashMap<String, Object>();
        input.put("productId", PRODUCT_ID);
        input.put("price", "1000.00");
        input.put("quantity", 2);
        input.put("packFee", "5.00");
        input.put("shipFee", "8.00");
        input.put("certFee", "2.00");
        input.put("platformRate", "5");
        input.put("commissionRate", "20");
        input.put("taxRate", "1");

        Map<String, Object> result = service.calculateProfit(input);

        assertMoney("425.00", (BigDecimal) result.get("profit"));
        assertMoney("850.00", (BigDecimal) result.get("totalProfit"));
        assertEquals(8, result.get("availableQty"));
        assertEquals(6, result.get("remainingQty"));
    }

    @Test
    void anotherMakerCannotSubmitDocument()
    {
        JewelryDocument document = document(61L, "SALES_OUT", "DRAFT");
        stubDocument(document, item(161L, 1, "500.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(61L, 99L, "other-maker"));

        assertTrue(error.getMessage().contains("只能提交自己创建的单据"));
        verify(mapper, never()).reserveOutbound(anyLong(), anyInt());
    }

    @Test
    void salesSubmissionFailsWhenAvailableStockIsInsufficient()
    {
        JewelryDocument document = document(62L, "SALES_OUT", "DRAFT");
        document.setSalesChannel("douyin");
        stubDocument(document, item(162L, 5, "500.00"));
        when(mapper.reserveOutbound(PRODUCT_ID, 5)).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(62L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("可用库存不足"));
        verify(mapper, never()).updateDocumentStatus(eq(62L), anyString(), anyString(), anyLong(),
            anyString(), any(), any());
    }

    @Test
    void withdrawingSalesDocumentReleasesReservedStock()
    {
        JewelryDocument document = document(63L, "SALES_OUT", "PENDING_FIRST");
        stubDocument(document, item(163L, 3, "500.00"));
        when(mapper.releaseOutbound(PRODUCT_ID, 3)).thenReturn(1);

        service.withdraw(63L, MAKER_ID, "maker");

        verify(mapper).releaseOutbound(PRODUCT_ID, 3);
        verify(mapper).updateDocumentStatus(63L, "PENDING_FIRST", "DRAFT",
            MAKER_ID, "maker", null, null);
    }

    @Test
    void purchasePostingIncreasesStockAndRecalculatesWeightedAverage()
    {
        JewelryDocument document = document(7L, "PURCHASE_IN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(17L, 2, "100.00");
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 0, 0, 0, 0, 0, "80.00", "0", "0"));

        service.approve(7L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(12), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("83.333333"), decimalEq("0"), decimalEq("0"));
        assertMoney("200.00", item.getCostAmount());
        verify(mapper).updateDocumentStatus(7L, "PENDING_SECOND", "POSTED",
            REVIEWER_TWO_ID, "reviewer2", null, 2);
    }

    @Test
    void salesPostingConsumesReservationAndCalculatesProfit()
    {
        JewelryDocument document = document(8L, "SALES_OUT", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        document.setPlatformRate(decimal("0.05"));
        document.setCommissionRate(decimal("0.20"));
        document.setTaxRate(decimal("0.01"));
        JewelryDocumentItem item = item(18L, 2, "1000.00");
        item.setAmount(decimal("2000.00"));
        item.setPackFee(decimal("5.00"));
        item.setShipFee(decimal("8.00"));
        item.setCertFee(decimal("2.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 2, 0, 0, 0, 0, "300.00", "0", "0"));

        service.approve(8L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        assertMoney("630.00", item.getCostAmount());
        assertMoney("850.00", item.getProfitAmount());
        assertMoney("850.00", document.getTotalProfit());
    }

    @Test
    void salesPostingCountsAccessoryQuantityAsPackagingWithoutDoubleCounting()
    {
        JewelryDocument document = document(81L, "SALES_OUT", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem main = itemForProduct(181L, PRODUCT_ID, 2);
        main.setProductTypeSnapshot("FINISHED");
        main.setUnitPrice(decimal("1000.00"));
        main.setAmount(decimal("2000.00"));
        main.setPackFee(decimal("10.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(182L, 200L, 3);
        accessory.setProductTypeSnapshot("ACCESSORY");
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("INCLUDED");
        when(mapper.selectDocumentById(81L)).thenReturn(document);
        when(mapper.selectDocumentItems(81L)).thenReturn(Arrays.asList(main, accessory));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 2, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 3, 0, 0, 0, 0, "4.00", "0", "0"));

        service.approve(81L, "", null, REVIEWER_TWO_ID, "reviewer2");

        assertMoney("1208.00", main.getCostAmount());
        assertMoney("12.00", accessory.getCostAmount());
        assertMoney("1220.00", document.getTotalCost());
        assertMoney("780.00", document.getTotalProfit());
        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("600.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).applyStock(eq(200L), eq(7), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("4.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).updateDocumentFinancials(document);
    }

    @Test
    void customerReturnMustLinkOriginalSale()
    {
        JewelryDocument customerReturn = document(null, "CUSTOMER_RETURN", null);
        customerReturn.setSalesChannel("shop");
        customerReturn.setReturnReason("customer return");
        customerReturn.setItems(Arrays.asList(item(null, 1, "1000.00")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(customerReturn, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("必须关联原销售单"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void linkedCustomerReturnCannotExceedOriginalSaleQuantity()
    {
        JewelryDocument sale = document(9L, "SALES_OUT", "POSTED");
        sale.setSalesChannel("shop");
        JewelryDocumentItem saleItem = item(19L, 3, "1000.00");
        saleItem.setItemId(901L);
        saleItem.setUnitCost(decimal("300.00"));

        JewelryDocument customerReturn = document(null, "CUSTOMER_RETURN", null);
        customerReturn.setSourceDocumentId(9L);
        customerReturn.setSalesChannel("shop");
        customerReturn.setReturnReason("customer return");
        JewelryDocumentItem returnItem = item(null, 2, "0");
        customerReturn.setItems(Arrays.asList(returnItem));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product());
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 0, 0, 0, 0, 0, "300.00", "0", "0"));
        when(mapper.selectDocumentById(9L)).thenReturn(sale);
        when(mapper.selectDocumentItems(9L)).thenReturn(Arrays.asList(saleItem));
        when(mapper.selectReturnedQtyBySourceItem(901L, null)).thenReturn(2);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(customerReturn, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("累计退货数量不能超过原销售数量3件"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void disabledSupplierCannotBeUsedForPurchase()
    {
        JewelryDocument purchase = document(null, "PURCHASE_IN", null);
        purchase.setSupplierId(9L);
        purchase.setItems(Arrays.asList(item(null, 1, "100.00")));
        Map<String, Object> supplier = activeSupplier();
        supplier.put("status", "1");
        when(mapper.selectSupplierById(9L)).thenReturn(supplier);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(purchase, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("供应商已停用"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void linkedCustomerReturnCannotSubmitAfterSourceSaleWasReversed()
    {
        JewelryDocument customerReturn = document(91L, "CUSTOMER_RETURN", "DRAFT");
        customerReturn.setSourceDocumentId(90L);
        customerReturn.setSalesChannel("douyin");
        stubDocument(customerReturn, item(191L, 1, "500.00"));
        JewelryDocument source = document(90L, "SALES_OUT", "REVERSED");
        when(mapper.selectDocumentByIdForUpdate(90L)).thenReturn(source);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(91L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("原销售单已失效"));
        verify(mapper, never()).updateDocumentStatus(eq(91L), anyString(), anyString(), anyLong(),
            anyString(), any(), any());
    }

    @Test
    void linkedCustomerReturnCannotSubmitWhenSourceAlreadyHasAReversal()
    {
        JewelryDocument customerReturn = document(92L, "CUSTOMER_RETURN", "DRAFT");
        customerReturn.setSourceDocumentId(90L);
        customerReturn.setSalesChannel("douyin");
        stubDocument(customerReturn, item(192L, 1, "500.00"));
        JewelryDocument source = document(90L, "SALES_OUT", "POSTED");
        when(mapper.selectDocumentByIdForUpdate(90L)).thenReturn(source);
        when(mapper.countReversalBySource(90L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(92L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("已存在红冲单"));
        verify(mapper, never()).reserveInspection(anyLong(), anyInt());
    }

    @Test
    void customerReturnPostingMovesStockIntoInspection()
    {
        JewelryDocument document = document(10L, "CUSTOMER_RETURN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(20L, 2, "1000.00");
        item.setUnitCost(decimal("300.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(8, 0, 1, 0, 0, 0, "300.00", "300.00", "0"));

        service.approve(10L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(3), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("900.00"), decimalEq("0"));
    }

    @Test
    void customerReturnIgnoresSaleRatesAndPackagingButChargesDoubleShipping() throws Exception
    {
        JewelryDocument document = document(102L, "CUSTOMER_RETURN", "DRAFT");
        document.setPlatformRate(decimal("0.05"));
        document.setCommissionRate(decimal("0.20"));
        document.setTaxRate(decimal("0.01"));
        JewelryDocumentItem item = item(202L, 2, "1000.00");
        item.setUnitCost(decimal("300.00"));
        item.setPackFee(decimal("5.00"));
        item.setShipFee(decimal("8.00"));
        item.setCertFee(decimal("2.00"));
        document.setItems(Arrays.asList(item));

        Method calculate = JewelryErpServiceImpl.class.getDeclaredMethod(
            "calculateDocument", JewelryDocument.class);
        calculate.setAccessible(true);
        calculate.invoke(service, document);

        assertMoney("0.00", document.getPlatformRate());
        assertMoney("0.00", document.getCommissionRate());
        assertMoney("0.00", document.getTaxRate());
        assertMoney("0.00", item.getPackFee());
        assertMoney("-564.00", item.getCostAmount());
        assertMoney("-1436.00", item.getProfitAmount());
        assertMoney("-1436.00", document.getTotalProfit());
    }

    @Test
    void purchaseCalculationDefaultsAssemblyFeesToZero() throws Exception
    {
        JewelryDocument document = document(null, "PURCHASE_IN", "DRAFT");
        document.setItems(Arrays.asList(item(null, 1, "20.00")));

        Method calculate = JewelryErpServiceImpl.class.getDeclaredMethod(
            "calculateDocument", JewelryDocument.class);
        calculate.setAccessible(true);
        calculate.invoke(service, document);

        assertMoney("0", document.getLaborFee());
        assertMoney("0", document.getProcessingFee());
        assertMoney("0", document.getOtherFee());
    }

    @Test
    void supplierReturnPostingConsumesReservedAndOnHandStock()
    {
        JewelryDocument document = document(101L, "SUPPLIER_RETURN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(201L, 2, "200.00");
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 2, 0, 0, 0, 0, "300.00", "0", "0"));

        service.approve(101L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        assertMoney("-600.00", item.getCostAmount());
    }

    @Test
    void returnInspectionMovesGoodAndDefectQuantitiesToTheirStocks()
    {
        JewelryDocument document = document(11L, "RETURN_INSPECT", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(21L, 0, "0");
        item.setGoodQty(2);
        item.setDefectQty(1);
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(8, 0, 3, 3, 0, 0, "300.00", "900.00", "0"));

        service.approve(11L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(1), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("300.00"));
    }

    @Test
    void returnInspectionMustLinkPostedCustomerReturn()
    {
        JewelryDocument inspection = document(null, "RETURN_INSPECT", null);
        JewelryDocumentItem inspectionItem = item(null, 0, "0");
        inspectionItem.setGoodQty(1);
        inspection.setItems(Arrays.asList(inspectionItem));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(inspection, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("必须关联原客户退货单"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void returnInspectionCannotExceedSourceReturnRemainingQuantity()
    {
        JewelryDocument source = document(90L, "CUSTOMER_RETURN", "POSTED");
        JewelryDocumentItem sourceItem = item(901L, 3, "500.00");
        sourceItem.setUnitCost(decimal("300.00"));

        JewelryDocument inspection = document(null, "RETURN_INSPECT", null);
        inspection.setSourceDocumentId(90L);
        JewelryDocumentItem inspectionItem = item(null, 0, "0");
        inspectionItem.setSourceItemId(901L);
        inspectionItem.setGoodQty(2);
        inspectionItem.setDefectQty(1);
        inspection.setItems(Arrays.asList(inspectionItem));

        when(mapper.selectDocumentById(90L)).thenReturn(source);
        when(mapper.selectDocumentItems(90L)).thenReturn(Arrays.asList(sourceItem));
        when(mapper.selectInspectedQtyBySourceItem(901L, null)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(inspection, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("剩余待检数量2件"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void staleStockAdjustmentCannotBeSubmitted()
    {
        JewelryDocument document = document(12L, "STOCK_ADJUST", "DRAFT");
        JewelryDocumentItem item = item(22L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(8);
        item.setAdjustmentQty(-2);
        item.setLineReason("盘点差异");
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(11, 0, 0, 0, 0, 0, "300.00", "0", "0"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(12L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("账面库存已变化"));
        verify(mapper, never()).reserveOutbound(anyLong(), anyInt());
    }

    @Test
    void stockGainPostingUpdatesQuantityAndWeightedAverageCost()
    {
        JewelryDocument document = document(121L, "STOCK_ADJUST", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(221L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(12);
        item.setAdjustmentQty(2);
        item.setUnitCost(decimal("400.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 0, 0, 0, 0, 0, "300.00", "0", "0"));

        service.approve(121L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(12), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("316.666667"), decimalEq("0"), decimalEq("0"));
    }

    @Test
    void approvedSalesReversalRestoresStockAndMarksOriginalReversed()
    {
        JewelryDocument source = document(13L, "SALES_OUT", "POSTED");
        JewelryDocument reversal = document(14L, "REVERSAL", "PENDING_SECOND");
        reversal.setSourceDocumentId(13L);
        reversal.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem reversalItem = item(23L, 2, "-1000.00");
        reversalItem.setUnitCost(decimal("300.00"));

        when(mapper.selectDocumentById(14L)).thenReturn(reversal);
        when(mapper.selectDocumentItems(14L)).thenReturn(Arrays.asList(reversalItem));
        when(mapper.selectDocumentByIdForUpdate(13L)).thenReturn(source);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(8, 0, 0, 0, 0, 0, "300.00", "0", "0"));
        when(mapper.markOriginalReversed(13L, "reviewer2")).thenReturn(1);

        service.approve(14L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).markOriginalReversed(13L, "reviewer2");
        verify(mapper).updateDocumentStatus(14L, "PENDING_SECOND", "POSTED",
            REVIEWER_TWO_ID, "reviewer2", null, 2);
    }

    @Test
    void saleWithActiveCustomerReturnCannotBeReversed()
    {
        JewelryDocument source = document(131L, "SALES_OUT", "POSTED");
        when(mapper.selectDocumentByIdForUpdate(131L)).thenReturn(source);
        when(mapper.countActiveCustomerReturnsBySource(131L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.createReversal(131L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("存在待处理或已入账的消费者退货"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void rejectedReversalCanBeResubmitted()
    {
        JewelryDocument source = document(132L, "SALES_OUT", "POSTED");
        JewelryDocument reversal = document(133L, "REVERSAL", "REJECTED");
        reversal.setSourceDocumentId(132L);
        stubDocument(reversal, item(233L, 1, "-500.00"));
        when(mapper.selectDocumentByIdForUpdate(132L)).thenReturn(source);

        service.submit(133L, MAKER_ID, "maker");

        verify(mapper).updateDocumentStatus(133L, "REJECTED", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
        verify(mapper).insertEvent(133L, "RESUBMIT", "REJECTED", "PENDING_FIRST",
            MAKER_ID, "maker", "");
    }

    @Test
    void submittingAssemblyReservesComponentsButNotFinishedProduct()
    {
        JewelryDocument document = document(15L, "ASSEMBLY", "DRAFT");
        JewelryDocumentItem component = item(24L, 6, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(25L, 200L, 2);
        output.setItemRole("OUTPUT");
        when(mapper.selectDocumentById(15L)).thenReturn(document);
        when(mapper.selectDocumentItems(15L)).thenReturn(Arrays.asList(component, output));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("PART"));
        when(mapper.selectProductById(200L)).thenReturn(product("FINISHED"));
        when(mapper.reserveOutbound(PRODUCT_ID, 6)).thenReturn(1);

        service.submit(15L, MAKER_ID, "maker");

        verify(mapper).reserveOutbound(PRODUCT_ID, 6);
        verify(mapper, never()).reserveOutbound(200L, 2);
        verify(mapper).updateDocumentStatus(15L, "DRAFT", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
    }

    @Test
    void savesManualAssemblyWithoutAiDesignRecord()
    {
        JewelryDocument document = document(17L, "ASSEMBLY", "DRAFT");
        document.setBizDate(new java.util.Date());
        document.setLaborFee(decimal("100.00"));
        document.setProcessingFee(decimal("20.00"));
        document.setOtherFee(BigDecimal.ZERO);
        JewelryDocumentItem component = item(28L, 4, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(29L, 200L, 2);
        output.setItemRole("OUTPUT");
        document.setItems(Arrays.asList(component, output));

        when(mapper.selectDocumentById(17L)).thenReturn(document);
        when(mapper.selectDocumentItems(17L)).thenReturn(Arrays.asList(component, output));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("PART"));
        when(mapper.selectProductById(200L)).thenReturn(product("FINISHED"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "100.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(3, 0, 0, 0, 0, 0, "500.00", "0", "0"));
        when(mapper.updateDocument(document)).thenReturn(1);

        JewelryDocument saved = service.saveDocument(document, MAKER_ID, "maker");

        assertEquals(17L, saved.getDocumentId());
        assertMoney("520.00", document.getTotalCost());
        assertMoney("260.00", output.getUnitCost());
        assertMoney("0", component.getPackFee());
        assertMoney("0", component.getShipFee());
        assertMoney("0", component.getCertFee());
        assertMoney("0", output.getPackFee());
        assertMoney("0", output.getShipFee());
        assertMoney("0", output.getCertFee());
        verify(mapper).insertDocumentItem(component);
        verify(mapper).insertDocumentItem(output);
    }

    @Test
    void assemblyCanCreateAndLinkANewFinishedProduct()
    {
        JewelryDocument document = document(null, "ASSEMBLY", "DRAFT");
        document.setBizDate(new java.util.Date());
        JewelryDocumentItem component = item(null, 2, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(null, null, 1);
        output.setItemRole("OUTPUT");
        output.setImageUrls("/profile/jewelry/new-product.jpg");
        document.setItems(Arrays.asList(component, output));
        Map<String, Object> newProduct = new HashMap<String, Object>();
        newProduct.put("sku", "NEW-FINISHED-001");
        newProduct.put("productName", "New finished jewelry");
        newProduct.put("productType", "PART");
        newProduct.put("specification", "精品");
        newProduct.put("warningQty", 3);
        document.setNewOutputProduct(newProduct);

        when(mapper.insertProduct(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> inserted = invocation.getArgument(0);
            inserted.put("productId", 300L);
            return 1;
        });
        when(mapper.insertDocument(document)).thenAnswer(invocation -> {
            document.setDocumentId(21L);
            return 1;
        });
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("PART"));
        Map<String, Object> finished = product("FINISHED");
        finished.put("sku", "NEW-FINISHED-001");
        finished.put("productName", "New finished jewelry");
        when(mapper.selectProductById(300L)).thenReturn(finished);
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(5, 0, 0, 0, 0, 0, "20.00", "0", "0"));
        when(mapper.selectStockForUpdate(300L))
            .thenReturn(stock(0, 0, 0, 0, 0, 0, "0", "0", "0"));
        when(mapper.selectDocumentById(21L)).thenReturn(document);
        when(mapper.selectDocumentItems(21L)).thenReturn(Arrays.asList(component, output));

        JewelryDocument saved = service.saveDocument(document, MAKER_ID, "maker");

        assertEquals(21L, saved.getDocumentId());
        assertEquals(300L, output.getProductId());
        assertEquals("FINISHED", newProduct.get("productType"));
        assertEquals("精品", newProduct.get("specification"));
        assertEquals("0", newProduct.get("status"));
        assertEquals("/profile/jewelry/new-product.jpg", newProduct.get("imageUrl"));
        verify(mapper).ensureStock(300L);
        verify(mapper).insertDocumentItem(output);
    }

    @Test
    void assemblyNewProductReportsDuplicateSkuClearly()
    {
        JewelryDocument document = document(null, "ASSEMBLY", "DRAFT");
        JewelryDocumentItem component = item(null, 1, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(null, null, 1);
        output.setItemRole("OUTPUT");
        document.setItems(Arrays.asList(component, output));
        Map<String, Object> newProduct = new HashMap<String, Object>();
        newProduct.put("sku", "EXISTING-SKU");
        newProduct.put("productName", "Duplicate product");
        newProduct.put("specification", "普通");
        document.setNewOutputProduct(newProduct);
        when(mapper.insertProduct(any())).thenThrow(new DuplicateKeyException("duplicate"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(document, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("SKU已存在"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void assemblyPostingConsumesPartsAndAddsFinishedInventoryAtFullAssemblyCost()
    {
        JewelryDocument document = document(16L, "ASSEMBLY", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        document.setLaborFee(decimal("100.00"));
        document.setProcessingFee(decimal("20.00"));
        document.setOtherFee(BigDecimal.ZERO);
        JewelryDocumentItem component = item(26L, 4, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(27L, 200L, 2);
        output.setItemRole("OUTPUT");
        when(mapper.selectDocumentById(16L)).thenReturn(document);
        when(mapper.selectDocumentItems(16L)).thenReturn(Arrays.asList(component, output));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 4, 0, 0, 0, 0, "100.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(3, 0, 0, 0, 0, 0, "500.00", "0", "0"));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("PART"));
        when(mapper.selectProductById(200L)).thenReturn(product("FINISHED"));

        service.approve(16L, "", decimal("520.00"), REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(6), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("100.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).applyStock(eq(200L), eq(5), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("404.00"), decimalEq("0"), decimalEq("0"));
        assertMoney("260.00", output.getUnitCost());
        assertMoney("520.00", output.getCostAmount());
        assertMoney("520.00", document.getTotalCost());
        assertEquals(2, document.getTotalQty());
        verify(mapper).updateDocumentStatus(16L, "PENDING_SECOND", "POSTED",
            REVIEWER_TWO_ID, "reviewer2", null, 2);
    }

    @Test
    void assemblySecondApprovalRejectsAChangedCost()
    {
        JewelryDocument document = document(18L, "ASSEMBLY", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        document.setLaborFee(decimal("100.00"));
        document.setProcessingFee(decimal("20.00"));
        document.setOtherFee(BigDecimal.ZERO);
        JewelryDocumentItem component = item(28L, 4, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(29L, 200L, 2);
        output.setItemRole("OUTPUT");
        when(mapper.selectDocumentById(18L)).thenReturn(document);
        when(mapper.selectDocumentItems(18L)).thenReturn(Arrays.asList(component, output));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("PART"));
        when(mapper.selectProductById(200L)).thenReturn(product("FINISHED"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 4, 0, 0, 0, 0, "110.00", "0", "0"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(18L, "", decimal("520.00"), REVIEWER_TWO_ID, "reviewer2"));

        assertTrue(error.getMessage().contains("组装成本已变化"));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void submittingAssemblyRejectsDisabledProducts()
    {
        JewelryDocument document = document(19L, "ASSEMBLY", "DRAFT");
        JewelryDocumentItem component = item(30L, 1, "0");
        component.setItemRole("COMPONENT");
        JewelryDocumentItem output = itemForProduct(31L, 200L, 1);
        output.setItemRole("OUTPUT");
        Map<String, Object> disabledPart = product("PART");
        disabledPart.put("status", "1");
        when(mapper.selectDocumentById(19L)).thenReturn(document);
        when(mapper.selectDocumentItems(19L)).thenReturn(Arrays.asList(component, output));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(disabledPart);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(19L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("已停用"));
        verify(mapper, never()).reserveOutbound(anyLong(), anyInt());
    }

    @Test
    void editingCannotChangeTheDocumentType()
    {
        JewelryDocument incoming = document(20L, "PURCHASE_IN", "DRAFT");
        incoming.setSupplierId(1L);
        incoming.setBizDate(new java.util.Date());
        JewelryDocumentItem incomingItem = item(32L, 1, "100.00");
        incoming.setItems(Arrays.asList(incomingItem));
        JewelryDocument current = document(20L, "SALES_OUT", "DRAFT");
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectDocumentById(20L)).thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(incoming, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("单据类型创建后不允许修改"));
        verify(mapper, never()).updateDocument(any(JewelryDocument.class));
    }

    @Test
    void salesBundleIncludesAddonInventoryCostWithoutAddingIncludedPrice()
    {
        JewelryDocument document = document(null, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(null, PRODUCT_ID, 1);
        main.setUnitPrice(decimal("1000.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        main.setPricingMode("SEPARATE");
        JewelryDocumentItem addon = itemForProduct(null, 200L, 1);
        addon.setUnitPrice(decimal("88.00"));
        addon.setBundleGroupNo(1);
        addon.setSaleRole("ADDON");
        addon.setPricingMode("INCLUDED");
        document.setItems(Arrays.asList(main, addon));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("PART"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "80.00", "0", "0"));

        service.assessDocumentRisk(document);

        assertMoney("0", addon.getUnitPrice());
        assertMoney("1000.00", document.getTotalAmount());
        assertMoney("680.00", document.getTotalCost());
        assertMoney("320.00", document.getTotalProfit());
        assertEquals("MAIN", main.getSaleRole());
        assertEquals("ADDON", addon.getSaleRole());
    }

    @Test
    void accessoryAddonIsPackagingMaterialWithoutDoubleCountingItsCost()
    {
        JewelryDocument document = document(null, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(null, PRODUCT_ID, 2);
        main.setUnitPrice(decimal("1000.00"));
        main.setPackFee(decimal("10.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(null, 200L, 3);
        accessory.setUnitPrice(decimal("30.00"));
        accessory.setPackFee(decimal("1.00"));
        accessory.setShipFee(decimal("2.00"));
        accessory.setCertFee(decimal("3.00"));
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("SEPARATE");
        document.setItems(Arrays.asList(main, accessory));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("ACCESSORY"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "4.00", "0", "0"));

        service.assessDocumentRisk(document);

        assertEquals("INCLUDED", accessory.getPricingMode());
        assertMoney("0", accessory.getUnitPrice());
        assertMoney("0", accessory.getPackFee());
        assertMoney("0", accessory.getShipFee());
        assertMoney("0", accessory.getCertFee());
        assertMoney("1208.00", main.getCostAmount());
        assertMoney("12.00", accessory.getCostAmount());
        assertMoney("1220.00", document.getTotalCost());
        assertMoney("780.00", document.getTotalProfit());
    }

    @Test
    void salesSubmissionRejectsAccessoryCostAboveManualPackagingFee()
    {
        JewelryDocument document = document(251L, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(603L, PRODUCT_ID, 2);
        main.setUnitPrice(decimal("1000.00"));
        main.setPackFee(decimal("5.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(604L, 200L, 3);
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("INCLUDED");

        when(mapper.selectDocumentById(251L)).thenReturn(document);
        when(mapper.selectDocumentItems(251L)).thenReturn(Arrays.asList(main, accessory));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("ACCESSORY"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "4.00", "0", "0"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(251L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("配件耗材成本￥12.00"));
        assertTrue(error.getMessage().contains("手填包装费￥10.00"));
        assertTrue(error.getMessage().contains("还差￥2.00"));
        verify(mapper, never()).reserveOutbound(anyLong(), anyInt());
    }

    @Test
    void salesDraftCanBeSavedWhenAccessoryPackagingFeeIsInsufficient()
    {
        JewelryDocument document = document(null, "SALES_OUT", "DRAFT");
        document.setBizDate(new java.util.Date());
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(null, PRODUCT_ID, 2);
        main.setUnitPrice(decimal("1000.00"));
        main.setPackFee(decimal("5.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(null, 200L, 3);
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("INCLUDED");
        document.setItems(Arrays.asList(main, accessory));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("ACCESSORY"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "4.00", "0", "0"));
        when(mapper.insertDocument(document)).thenAnswer(invocation -> {
            document.setDocumentId(253L);
            return 1;
        });
        when(mapper.selectDocumentById(253L)).thenReturn(document);
        when(mapper.selectDocumentItems(253L)).thenReturn(Arrays.asList(main, accessory));

        JewelryDocument saved = service.saveDocument(document, MAKER_ID, "maker");

        assertEquals(253L, saved.getDocumentId());
        assertEquals("DRAFT", saved.getStatus());
        assertMoney("1212.00", saved.getTotalCost());
        verify(mapper).insertDocumentItem(main);
        verify(mapper).insertDocumentItem(accessory);
    }

    @Test
    void salesSubmissionAcceptsAccessoryCostCoveredByManualPackagingFee()
    {
        JewelryDocument document = document(252L, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(605L, PRODUCT_ID, 2);
        main.setUnitPrice(decimal("1000.00"));
        main.setPackFee(decimal("6.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(606L, 200L, 3);
        accessory.setUnitPrice(decimal("20.00"));
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("SEPARATE");

        when(mapper.selectDocumentById(252L)).thenReturn(document);
        when(mapper.selectDocumentItems(252L)).thenReturn(Arrays.asList(main, accessory));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("ACCESSORY"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "4.00", "0", "0"));
        when(mapper.reserveOutbound(PRODUCT_ID, 2)).thenReturn(1);
        when(mapper.reserveOutbound(200L, 3)).thenReturn(1);

        service.submit(252L, MAKER_ID, "maker");

        assertEquals("INCLUDED", accessory.getPricingMode());
        assertMoney("0", accessory.getUnitPrice());
        assertMoney("1212.00", document.getTotalCost());
        verify(mapper).reserveOutbound(PRODUCT_ID, 2);
        verify(mapper).reserveOutbound(200L, 3);
        verify(mapper).updateDocumentStatus(252L, "DRAFT", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
    }

    @Test
    void salesBundleAcceptsAccessoryAndWelfareAddons()
    {
        JewelryDocument document = document(null, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(null, PRODUCT_ID, 1);
        main.setUnitPrice(decimal("1000.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem accessory = itemForProduct(null, 200L, 1);
        accessory.setBundleGroupNo(1);
        accessory.setSaleRole("ADDON");
        accessory.setPricingMode("INCLUDED");
        JewelryDocumentItem welfare = itemForProduct(null, 201L, 1);
        welfare.setBundleGroupNo(1);
        welfare.setSaleRole("ADDON");
        welfare.setPricingMode("INCLUDED");
        document.setItems(Arrays.asList(main, accessory, welfare));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("ACCESSORY"));
        when(mapper.selectProductById(201L)).thenReturn(product("WELFARE"));
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "600.00", "0", "0"));
        when(mapper.selectStockForUpdate(200L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "30.00", "0", "0"));
        when(mapper.selectStockForUpdate(201L))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "10.00", "0", "0"));

        service.assessDocumentRisk(document);

        assertEquals("ADDON", accessory.getSaleRole());
        assertEquals("ADDON", welfare.getSaleRole());
        assertMoney("0", accessory.getUnitPrice());
        assertMoney("0", welfare.getUnitPrice());
    }

    @Test
    void salesBundleRejectsAFinishedAddon()
    {
        JewelryDocument document = document(null, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(null, PRODUCT_ID, 1);
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem addon = itemForProduct(null, 200L, 1);
        addon.setBundleGroupNo(1);
        addon.setSaleRole("ADDON");
        addon.setPricingMode("INCLUDED");
        document.setItems(Arrays.asList(main, addon));

        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("FINISHED"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assessDocumentRisk(document));

        assertTrue(error.getMessage().contains("搭售商品不能选择成品商品"));
    }

    @Test
    void submittingSalesBundleReservesMainAndAddonStockSeparately()
    {
        JewelryDocument document = document(250L, "SALES_OUT", "DRAFT");
        document.setSalesChannel("抖音");
        JewelryDocumentItem main = itemForProduct(601L, PRODUCT_ID, 1);
        main.setUnitPrice(decimal("1000.00"));
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        JewelryDocumentItem addon = itemForProduct(602L, 200L, 2);
        addon.setBundleGroupNo(1);
        addon.setSaleRole("ADDON");
        addon.setPricingMode("INCLUDED");

        when(mapper.selectDocumentById(250L)).thenReturn(document);
        when(mapper.selectDocumentItems(250L)).thenReturn(Arrays.asList(main, addon));
        when(mapper.selectProductById(PRODUCT_ID)).thenReturn(product("FINISHED"));
        when(mapper.selectProductById(200L)).thenReturn(product("PART"));
        when(mapper.reserveOutbound(PRODUCT_ID, 1)).thenReturn(1);
        when(mapper.reserveOutbound(200L, 2)).thenReturn(1);

        service.submit(250L, MAKER_ID, "maker");

        verify(mapper).reserveOutbound(PRODUCT_ID, 1);
        verify(mapper).reserveOutbound(200L, 2);
        verify(mapper).updateDocumentStatus(250L, "DRAFT", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
    }

    @Test
    void customerReturnUsesExactBundledSourceLineAndActualRefund()
    {
        JewelryDocument source = document(300L, "SALES_OUT", "POSTED");
        JewelryDocumentItem firstSaleLine = item(501L, 1, "500.00");
        JewelryDocumentItem bundledSourceLine = item(502L, 1, "0");
        bundledSourceLine.setBundleGroupNo(2);
        bundledSourceLine.setSaleRole("ADDON");
        bundledSourceLine.setPricingMode("INCLUDED");
        bundledSourceLine.setUnitCost(decimal("50.00"));

        JewelryDocument document = document(null, "CUSTOMER_RETURN", "DRAFT");
        document.setBizDate(new java.util.Date());
        document.setSalesChannel("抖音");
        document.setReturnReason("部分退货");
        document.setSourceDocumentId(300L);
        document.setActualRefundAmount(decimal("25.00"));
        JewelryDocumentItem returnedItem = item(null, 1, "0");
        returnedItem.setSourceItemId(502L);
        document.setItems(Arrays.asList(returnedItem));

        when(mapper.selectDocumentById(300L)).thenReturn(source);
        when(mapper.selectDocumentItems(300L)).thenReturn(Arrays.asList(firstSaleLine, bundledSourceLine));
        when(mapper.selectReturnedQtyBySourceItem(502L, null)).thenReturn(0);
        when(mapper.insertDocument(document)).thenAnswer(invocation -> {
            document.setDocumentId(301L);
            return 1;
        });
        when(mapper.selectDocumentById(301L)).thenReturn(document);
        when(mapper.selectDocumentItems(301L)).thenReturn(Arrays.asList(returnedItem));

        JewelryDocument saved = service.saveDocument(document, MAKER_ID, "maker");

        assertEquals(301L, saved.getDocumentId());
        assertEquals(502L, returnedItem.getSourceItemId());
        assertEquals(2, returnedItem.getBundleGroupNo());
        assertEquals("ADDON", returnedItem.getSaleRole());
        assertMoney("-25.00", returnedItem.getAmount());
        assertMoney("-25.00", document.getTotalAmount());
        assertEquals("REVIEW", document.getRiskStatus());
        verify(mapper).selectReturnedQtyBySourceItem(502L, null);
    }

    @Test
    void stockWarningDaysDefaultsToTwentyFiveWhenConfigIsMissing()
    {
        when(mapper.selectStockWarningDays()).thenReturn(null);

        assertEquals(25, service.getStockWarningDays());
    }

    @Test
    void stockWarningDaysUsesConfiguredValue()
    {
        when(mapper.selectStockWarningDays()).thenReturn(40);

        assertEquals(40, service.getStockWarningDays());
        service.setStockWarningDays(40, "admin");

        verify(mapper).upsertStockWarningDays(40, "admin");
    }

    @Test
    void stockWarningDaysRejectsValuesOutsideSupportedRange()
    {
        assertThrows(ServiceException.class, () -> service.setStockWarningDays(0, "admin"));
        assertThrows(ServiceException.class, () -> service.setStockWarningDays(366, "admin"));

        verify(mapper, never()).upsertStockWarningDays(anyInt(), anyString());
    }

    private void stubDocument(JewelryDocument document, JewelryDocumentItem item)
    {
        when(mapper.selectDocumentById(document.getDocumentId())).thenReturn(document);
        when(mapper.selectDocumentItems(document.getDocumentId())).thenReturn(Arrays.asList(item));
    }

    private JewelryDocument document(Long id, String type, String status)
    {
        JewelryDocument document = new JewelryDocument();
        document.setDocumentId(id);
        document.setDocNo("DOC-" + id);
        document.setDocType(type);
        document.setStatus(status);
        document.setCreatorUserId(MAKER_ID);
        document.setCreatorName("maker");
        document.setPlatformRate(BigDecimal.ZERO);
        document.setCommissionRate(BigDecimal.ZERO);
        document.setTaxRate(BigDecimal.ZERO);
        return document;
    }

    private JewelryDocumentItem item(Long id, int qty, String unitPrice)
    {
        JewelryDocumentItem item = new JewelryDocumentItem();
        item.setItemId(id);
        item.setProductId(PRODUCT_ID);
        item.setSkuSnapshot("SKU-100");
        item.setProductNameSnapshot("Test jewelry");
        item.setQty(qty);
        item.setGoodQty(0);
        item.setDefectQty(0);
        item.setAdjustmentQty(0);
        item.setUnitPrice(decimal(unitPrice));
        item.setUnitCost(BigDecimal.ZERO);
        item.setPackFee(BigDecimal.ZERO);
        item.setShipFee(BigDecimal.ZERO);
        item.setCertFee(BigDecimal.ZERO);
        item.setAmount(decimal(unitPrice).multiply(BigDecimal.valueOf(qty)));
        item.setCostAmount(BigDecimal.ZERO);
        item.setProfitAmount(BigDecimal.ZERO);
        return item;
    }

    private JewelryDocumentItem itemForProduct(Long id, Long productId, int qty)
    {
        JewelryDocumentItem item = item(id, qty, "0");
        item.setProductId(productId);
        item.setSkuSnapshot("SKU-" + productId);
        item.setProductNameSnapshot("Product " + productId);
        return item;
    }

    private Map<String, Object> product()
    {
        return product(null);
    }

    private Map<String, Object> product(String productType)
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("sku", "SKU-100");
        product.put("productName", "Test jewelry");
        product.put("productType", productType);
        product.put("status", "0");
        product.put("avgCost", BigDecimal.ZERO);
        return product;
    }

    private Map<String, Object> activeSupplier()
    {
        Map<String, Object> supplier = new HashMap<String, Object>();
        supplier.put("supplierId", 1L);
        supplier.put("supplierName", "Test supplier");
        supplier.put("status", "0");
        return supplier;
    }

    private Map<String, Object> stock(int onHand, int reserved, int inspection, int inspectionReserved,
        int defect, int defectReserved, String avgCost, String inspectionCost, String defectCost)
    {
        Map<String, Object> stock = new HashMap<String, Object>();
        stock.put("onHandQty", onHand);
        stock.put("reservedOutQty", reserved);
        stock.put("inspectionQty", inspection);
        stock.put("inspectionReservedQty", inspectionReserved);
        stock.put("defectQty", defect);
        stock.put("defectReservedQty", defectReserved);
        stock.put("avgCost", decimal(avgCost));
        stock.put("inspectionCostAmount", decimal(inspectionCost));
        stock.put("defectCostAmount", decimal(defectCost));
        return stock;
    }

    private BigDecimal decimal(String value)
    {
        return new BigDecimal(value);
    }

    private BigDecimal decimalEq(String value)
    {
        return org.mockito.ArgumentMatchers.argThat(actual ->
            actual != null && actual.compareTo(decimal(value)) == 0);
    }

    private void assertMoney(String expected, BigDecimal actual)
    {
        assertEquals(0, decimal(expected).compareTo(actual));
    }
}
