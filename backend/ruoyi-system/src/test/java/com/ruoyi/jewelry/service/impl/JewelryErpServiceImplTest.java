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
    }

    @Test
    void makerCannotApproveOwnDocument()
    {
        JewelryDocument document = document(1L, "PURCHASE_IN", "PENDING_FIRST");
        stubDocument(document, item(11L, 2, "100.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(1L, "", MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("制单人不能审核自己的单据"));
        verify(mapper, never()).insertApproval(anyLong(), anyInt(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void sameReviewerCannotPerformSecondApproval()
    {
        JewelryDocument document = document(2L, "PURCHASE_IN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        stubDocument(document, item(12L, 2, "100.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(2L, "", REVIEWER_ONE_ID, "reviewer1"));

        assertTrue(error.getMessage().contains("一审与复核不能由同一人完成"));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void firstApprovalMovesDocumentToSecondReview()
    {
        JewelryDocument document = document(3L, "PURCHASE_IN", "PENDING_FIRST");
        stubDocument(document, item(13L, 1, "100.00"));

        service.approve(3L, "checked", REVIEWER_ONE_ID, "reviewer1");

        verify(mapper).updateDocumentStatus(3L, "PENDING_FIRST", "PENDING_SECOND",
            REVIEWER_ONE_ID, "reviewer1", null, 1);
        verify(mapper).insertApproval(3L, 1, "PASS", REVIEWER_ONE_ID, "reviewer1", "checked");
        verify(mapper).insertEvent(3L, "FIRST_APPROVE", "PENDING_FIRST", "PENDING_SECOND",
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
        stubDocument(document, item(16L, 4, "500.00"));
        when(mapper.reserveOutbound(PRODUCT_ID, 4)).thenReturn(1);

        service.submit(6L, MAKER_ID, "maker");

        verify(mapper).reserveOutbound(PRODUCT_ID, 4);
        verify(mapper).updateDocumentStatus(6L, "DRAFT", "PENDING_FIRST",
            MAKER_ID, "maker", null, null);
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

        service.approve(7L, "", REVIEWER_TWO_ID, "reviewer2");

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

        service.approve(8L, "", REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        assertMoney("630.00", item.getCostAmount());
        assertMoney("850.00", item.getProfitAmount());
        assertMoney("850.00", document.getTotalProfit());
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
    void customerReturnPostingMovesStockIntoInspection()
    {
        JewelryDocument document = document(10L, "CUSTOMER_RETURN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(20L, 2, "1000.00");
        item.setUnitCost(decimal("300.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(8, 0, 1, 0, 0, 0, "300.00", "300.00", "0"));

        service.approve(10L, "", REVIEWER_TWO_ID, "reviewer2");

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
    void supplierReturnPostingConsumesReservedAndOnHandStock()
    {
        JewelryDocument document = document(101L, "SUPPLIER_RETURN", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(201L, 2, "200.00");
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 2, 0, 0, 0, 0, "300.00", "0", "0"));

        service.approve(101L, "", REVIEWER_TWO_ID, "reviewer2");

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

        service.approve(11L, "", REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(1), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("300.00"));
    }

    @Test
    void staleStockAdjustmentCannotBeSubmitted()
    {
        JewelryDocument document = document(12L, "STOCK_ADJUST", "DRAFT");
        JewelryDocumentItem item = item(22L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(8);
        item.setAdjustmentQty(-2);
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

        service.approve(121L, "", REVIEWER_TWO_ID, "reviewer2");

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
        when(mapper.selectDocumentById(13L)).thenReturn(source);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(8, 0, 0, 0, 0, 0, "300.00", "0", "0"));
        when(mapper.markOriginalReversed(13L, "reviewer2")).thenReturn(1);

        service.approve(14L, "", REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).markOriginalReversed(13L, "reviewer2");
        verify(mapper).updateDocumentStatus(14L, "PENDING_SECOND", "POSTED",
            REVIEWER_TWO_ID, "reviewer2", null, 2);
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

    private Map<String, Object> product()
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("sku", "SKU-100");
        product.put("productName", "Test jewelry");
        return product;
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
