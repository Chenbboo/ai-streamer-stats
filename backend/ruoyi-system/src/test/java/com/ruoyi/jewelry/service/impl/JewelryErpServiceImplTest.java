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
    private static final Long SALES_INFLUENCER_ID = 9001L;
    private static final Long RETURN_INFLUENCER_ID = 9002L;

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
        lenient().when(mapper.selectInfluencerById(SALES_INFLUENCER_ID)).thenReturn(activeInfluencer(false));
        lenient().when(mapper.selectInfluencerById(RETURN_INFLUENCER_ID)).thenReturn(activeInfluencer(true));
        lenient().when(mapper.selectInfluencerProductPrices(RETURN_INFLUENCER_ID))
            .thenReturn(Arrays.asList(pricedProductPrice(PRODUCT_ID, "50.0000", 1)));
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
    void basicProductUpdateNormalizesNameAndKeepsOnlyOneImage()
    {
        when(mapper.updateProductBasic(any())).thenReturn(1);
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 201L);
        product.put("productName", "  新商品名称  ");
        product.put("imageUrls", "/profile/one.jpg,/profile/two.jpg");
        product.put("updateBy", "maker");

        assertEquals(1, service.updateProductBasic(product));

        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateProductBasic(fields.capture());
        assertEquals("新商品名称", fields.getValue().get("productName"));
        assertEquals("/profile/one.jpg", fields.getValue().get("imageUrl"));
        assertEquals("/profile/one.jpg", fields.getValue().get("imageUrls"));
    }

    @Test
    void basicProductUpdateRejectsEmptyName()
    {
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 201L);
        product.put("productName", "  ");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.updateProductBasic(product));

        assertTrue(error.getMessage().contains("商品名称不能为空"));
        verify(mapper, never()).updateProductBasic(any());
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
    void creatorCanDeleteOwnDraftAndItsRelatedRecords()
    {
        JewelryDocument document = document(64L, "SALES_OUT", "DRAFT");
        when(mapper.selectDocumentByIdForUpdate(64L)).thenReturn(document);
        when(mapper.deleteDraftDocument(64L, MAKER_ID)).thenReturn(1);

        service.deleteDraft(64L, MAKER_ID);

        verify(mapper).deleteDocumentApprovals(64L);
        verify(mapper).deleteDocumentEvents(64L);
        verify(mapper).deleteDocumentItems(64L);
        verify(mapper).deleteDraftDocument(64L, MAKER_ID);
    }

    @Test
    void makerCannotDeleteAnotherCreatorsDraft()
    {
        JewelryDocument document = document(65L, "SALES_OUT", "DRAFT");
        when(mapper.selectDocumentByIdForUpdate(65L)).thenReturn(document);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.deleteDraft(65L, 99L));

        assertTrue(error.getMessage().contains("只能删除自己创建的草稿"));
        verify(mapper, never()).deleteDocumentItems(anyLong());
        verify(mapper, never()).deleteDraftDocument(anyLong(), anyLong());
    }

    @Test
    void submittedDocumentCannotBeDeleted()
    {
        JewelryDocument document = document(66L, "SALES_OUT", "PENDING_FIRST");
        when(mapper.selectDocumentByIdForUpdate(66L)).thenReturn(document);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.deleteDraft(66L, MAKER_ID));

        assertTrue(error.getMessage().contains("只有草稿单据可以删除"));
        verify(mapper, never()).deleteDocumentItems(anyLong());
        verify(mapper, never()).deleteDraftDocument(anyLong(), anyLong());
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
    void salesRiskAssessmentRejectsNegativeOtherFee()
    {
        JewelryDocument sales = document(null, "SALES_OUT", "DRAFT");
        sales.setSalesChannel("douyin");
        JewelryDocumentItem salesItem = item(null, 1, "500.00");
        salesItem.setOtherFee2(decimal("-0.01"));
        sales.setItems(Arrays.asList(salesItem));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assessDocumentRisk(sales));

        assertTrue(error.getMessage().contains("其他2不能小于0"));
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
        input.put("otherFee1", "1.00");
        input.put("otherFee2", "2.00");
        input.put("otherFee3", "3.00");
        input.put("platformRate", "5");
        input.put("commissionRate", "20");
        input.put("taxRate", "1");

        Map<String, Object> result = service.calculateProfit(input);

        assertMoney("419.00", (BigDecimal) result.get("profit"));
        assertMoney("838.00", (BigDecimal) result.get("totalProfit"));
        assertMoney("21.00", (BigDecimal) result.get("fixedFees"));
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
        item.setOtherFee1(decimal("1.00"));
        item.setOtherFee2(decimal("2.00"));
        item.setOtherFee3(decimal("3.00"));
        item.setInfluencerPriceSnapshot(decimal("1000.0000"));
        item.setInfluencerPriceVersion(0);
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID)).thenReturn(stock(10, 2, 0, 0, 0, 0, "300.00", "0", "0"));
        when(mapper.selectInfluencerByIdForUpdate(SALES_INFLUENCER_ID)).thenReturn(activeInfluencer(false));
        when(mapper.selectInfluencerProductPriceForUpdate(SALES_INFLUENCER_ID, PRODUCT_ID))
            .thenReturn(pendingProductPrice(PRODUCT_ID, "1000.0000", 8L));
        when(mapper.promoteInfluencerProductPrice(SALES_INFLUENCER_ID, PRODUCT_ID, 8L, "reviewer2"))
            .thenReturn(1);

        service.approve(8L, "", null, REVIEWER_TWO_ID, "reviewer2");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(8), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("300.00"), decimalEq("0"), decimalEq("0"));
        assertMoney("642.00", item.getCostAmount());
        assertMoney("838.00", item.getProfitAmount());
        assertMoney("838.00", document.getTotalProfit());
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
        main.setInfluencerPriceSnapshot(decimal("1000.0000"));
        main.setInfluencerPriceVersion(0);
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
        when(mapper.selectInfluencerByIdForUpdate(SALES_INFLUENCER_ID)).thenReturn(activeInfluencer(false));
        when(mapper.selectInfluencerProductPriceForUpdate(SALES_INFLUENCER_ID, PRODUCT_ID))
            .thenReturn(pendingProductPrice(PRODUCT_ID, "1000.0000", 81L));
        when(mapper.promoteInfluencerProductPrice(SALES_INFLUENCER_ID, PRODUCT_ID, 81L, "reviewer2"))
            .thenReturn(1);

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
        ArgumentCaptor<Map<String, Object>> binding = ArgumentCaptor.forClass(Map.class);
        verify(mapper).upsertInfluencerBundleItem(binding.capture());
        assertEquals(SALES_INFLUENCER_ID, binding.getValue().get("influencerId"));
        assertEquals(PRODUCT_ID, binding.getValue().get("mainProductId"));
        assertEquals(200L, binding.getValue().get("addonProductId"));
        assertEquals(2, binding.getValue().get("mainQty"));
        assertEquals(3, binding.getValue().get("addonQty"));
        assertEquals("INCLUDED", binding.getValue().get("pricingMode"));
        assertEquals(81L, binding.getValue().get("sourceDocumentId"));
    }

    @Test
    void unlinkedCustomerReturnAllowsArbitraryUnpricedProductAndIsFlaggedForReview()
    {
        JewelryDocument customerReturn = document(null, "CUSTOMER_RETURN", null);
        customerReturn.setSalesChannel("shop");
        customerReturn.setReturnReason("customer return");
        customerReturn.setActualRefundAmount(decimal("900.00"));
        JewelryDocumentItem arbitraryProduct = item(null, 1, "1000.1234");
        arbitraryProduct.setProductId(777L);
        customerReturn.setItems(Arrays.asList(arbitraryProduct));
        when(mapper.insertDocument(customerReturn)).thenAnswer(invocation -> {
            customerReturn.setDocumentId(93L);
            return 1;
        });
        when(mapper.selectDocumentById(93L)).thenReturn(customerReturn);
        when(mapper.selectDocumentItems(93L)).thenReturn(customerReturn.getItems());

        JewelryDocument saved = service.saveDocument(customerReturn, MAKER_ID, "maker");

        assertEquals(93L, saved.getDocumentId());
        assertEquals("REVIEW", customerReturn.getRiskStatus());
        assertMoney("1000.1234", customerReturn.getItems().get(0).getUnitPrice());
        assertEquals(null, customerReturn.getItems().get(0).getInfluencerPriceSnapshot());
        assertEquals(null, customerReturn.getItems().get(0).getInfluencerPriceVersion());
        assertMoney("100.00", customerReturn.getItems().get(0).getUnitCost());
        assertMoney("-900.00", customerReturn.getTotalAmount());
        verify(mapper).insertDocument(customerReturn);
    }

    @Test
    void unlinkedCustomerReturnAllowsInfluencerIncludedBundleAddonAtZeroRefundPrice()
    {
        Long addonProductId = 101L;
        JewelryDocument customerReturn = document(null, "CUSTOMER_RETURN", null);
        customerReturn.setSalesChannel("douyin");
        customerReturn.setReturnReason("退回搭售散件");
        customerReturn.setActualRefundAmount(decimal("5.95"));
        JewelryDocumentItem addon = item(null, 1, "5.9500");
        addon.setProductId(addonProductId);
        customerReturn.setItems(Arrays.asList(addon));
        Map<String, Object> binding = new HashMap<String, Object>();
        binding.put("addonProductId", addonProductId);
        binding.put("pricingMode", "INCLUDED");
        when(mapper.selectInfluencerBundleItems(RETURN_INFLUENCER_ID))
            .thenReturn(Arrays.asList(binding));
        when(mapper.insertDocument(customerReturn)).thenAnswer(invocation -> {
            customerReturn.setDocumentId(94L);
            return 1;
        });
        when(mapper.selectDocumentById(94L)).thenReturn(customerReturn);
        when(mapper.selectDocumentItems(94L)).thenReturn(customerReturn.getItems());

        service.saveDocument(customerReturn, MAKER_ID, "maker");

        assertMoney("0", addon.getUnitPrice());
        assertMoney("0", addon.getInfluencerPriceSnapshot());
        assertEquals(0, addon.getInfluencerPriceVersion());
        assertEquals("ADDON", addon.getSaleRole());
        assertEquals("INCLUDED", addon.getPricingMode());
        verify(mapper).insertDocument(customerReturn);
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
    void customerReturnSourceKeepsBundleLinesAndRemainingQuantities()
    {
        JewelryDocument sale = document(9L, "SALES_OUT", "POSTED");
        JewelryDocumentItem main = item(901L, 3, "1000.00");
        main.setItemId(901L);
        main.setBundleGroupNo(1);
        main.setSaleRole("MAIN");
        main.setRemainingReturnQty(1);
        JewelryDocumentItem addon = item(902L, 3, "0");
        addon.setItemId(902L);
        addon.setProductId(200L);
        addon.setBundleGroupNo(1);
        addon.setSaleRole("ADDON");
        addon.setPricingMode("INCLUDED");
        addon.setRemainingReturnQty(3);

        when(mapper.selectDocumentById(9L)).thenReturn(sale);
        when(mapper.selectCustomerReturnSourceItems(9L, 88L)).thenReturn(Arrays.asList(main, addon));

        JewelryDocument source = service.getCustomerReturnSource(9L, 88L);

        assertEquals(2, source.getItems().size());
        assertEquals("MAIN", source.getItems().get(0).getSaleRole());
        assertEquals(1, source.getItems().get(0).getRemainingReturnQty());
        assertEquals("ADDON", source.getItems().get(1).getSaleRole());
        assertEquals(3, source.getItems().get(1).getRemainingReturnQty());
        verify(mapper).countReversalBySource(9L);
        verify(mapper).selectCustomerReturnSourceItems(9L, 88L);
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
        item.setInfluencerPriceSnapshot(decimal("1000.0000"));
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
    void purchaseCalculationKeepsFourDecimalUnitPriceAndAmounts() throws Exception
    {
        JewelryDocument document = document(null, "PURCHASE_IN", "DRAFT");
        JewelryDocumentItem item = item(null, 3, "0.12345");
        document.setItems(Arrays.asList(item));

        Method calculate = JewelryErpServiceImpl.class.getDeclaredMethod(
            "calculateDocument", JewelryDocument.class);
        calculate.setAccessible(true);
        calculate.invoke(service, document);

        assertEquals(new BigDecimal("0.1235"), item.getUnitPrice());
        assertEquals(new BigDecimal("0.3705"), item.getAmount());
        assertEquals(new BigDecimal("0.3705"), item.getCostAmount());
        assertEquals(new BigDecimal("0.3705"), document.getTotalAmount());
        assertEquals(new BigDecimal("0.3705"), document.getTotalCost());
    }

    @Test
    void supplierReturnCalculationKeepsFourDecimalUnitPriceAndAmounts() throws Exception
    {
        JewelryDocument document = document(null, "SUPPLIER_RETURN", "DRAFT");
        JewelryDocumentItem item = item(null, 3, "0.12345");
        item.setUnitCost(decimal("0.100000"));
        document.setItems(Arrays.asList(item));

        Method calculate = JewelryErpServiceImpl.class.getDeclaredMethod(
            "calculateDocument", JewelryDocument.class);
        calculate.setAccessible(true);
        calculate.invoke(service, document);

        assertEquals(new BigDecimal("0.1235"), item.getUnitPrice());
        assertEquals(new BigDecimal("-0.3705"), item.getAmount());
        assertEquals(new BigDecimal("-0.30"), item.getCostAmount());
        assertEquals(new BigDecimal("-0.3705"), document.getTotalAmount());
        assertEquals(new BigDecimal("-0.30"), document.getTotalCost());
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
    void supplierReturnMustLinkPostedPurchaseFromSameSupplier()
    {
        JewelryDocument supplierReturn = document(null, "SUPPLIER_RETURN", null);
        supplierReturn.setSupplierId(9L);
        supplierReturn.setReturnReason("质量问题");
        supplierReturn.setItems(Arrays.asList(item(null, 1, "100.00")));

        ServiceException missingSource = assertThrows(ServiceException.class,
            () -> service.saveDocument(supplierReturn, MAKER_ID, "maker"));
        assertTrue(missingSource.getMessage().contains("必须关联原采购单"));

        JewelryDocument purchase = document(90L, "PURCHASE_IN", "POSTED");
        purchase.setSupplierId(8L);
        supplierReturn.setSourceDocumentId(90L);
        when(mapper.selectDocumentById(90L)).thenReturn(purchase);

        ServiceException wrongSupplier = assertThrows(ServiceException.class,
            () -> service.saveDocument(supplierReturn, MAKER_ID, "maker"));
        assertTrue(wrongSupplier.getMessage().contains("供应商不一致"));
    }

    @Test
    void supplierReturnCannotExceedSourcePurchaseRemainingQuantity()
    {
        JewelryDocument purchase = document(90L, "PURCHASE_IN", "POSTED");
        purchase.setSupplierId(9L);
        JewelryDocumentItem purchaseItem = item(901L, 5, "100.00");

        JewelryDocument supplierReturn = document(null, "SUPPLIER_RETURN", null);
        supplierReturn.setSupplierId(9L);
        supplierReturn.setSourceDocumentId(90L);
        supplierReturn.setReturnReason("质量问题");
        JewelryDocumentItem returnItem = item(null, 3, "100.00");
        returnItem.setSourceItemId(901L);
        supplierReturn.setItems(Arrays.asList(returnItem));

        when(mapper.selectDocumentById(90L)).thenReturn(purchase);
        when(mapper.selectDocumentItems(90L)).thenReturn(Arrays.asList(purchaseItem));
        when(mapper.selectSupplierReturnedQtyBySourceItem(901L, null)).thenReturn(3);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.saveDocument(supplierReturn, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("剩余可退数量2件"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
    }

    @Test
    void purchaseReversalIsBlockedByActiveSupplierReturn()
    {
        JewelryDocument purchase = document(90L, "PURCHASE_IN", "POSTED");
        when(mapper.selectDocumentByIdForUpdate(90L)).thenReturn(purchase);
        when(mapper.countActiveSupplierReturnsBySource(90L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.createReversal(90L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("供应商退货"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
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

        assertTrue(error.getMessage().contains("可售库存已变化"));
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

        service.approve(121L, "", null, REVIEWER_TWO_ID, "admin", "jewelry_admin");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(12), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("316.666667"), decimalEq("0"), decimalEq("0"));
    }

    @Test
    void administratorCanReviseStockGainCostDuringFinalApproval()
    {
        JewelryDocument document = document(123L, "STOCK_ADJUST", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(223L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(12);
        item.setAdjustmentQty(2);
        item.setUnitCost(decimal("400.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "300.00", "0", "0"));
        Map<Long, BigDecimal> revisedCosts = new HashMap<Long, BigDecimal>();
        revisedCosts.put(223L, decimal("450.00"));

        service.approve(123L, "按复核成本入账", null, REVIEWER_TWO_ID, "admin", "jewelry_admin",
            revisedCosts);

        assertMoney("450.00", item.getUnitCost());
        assertMoney("900.00", item.getCostAmount());
        verify(mapper).updateDocumentItemCost(item);
        verify(mapper).updateDocumentFinancials(document);
        verify(mapper).insertEvent(eq(123L), eq("ADMIN_COST_EDIT"), eq("PENDING_SECOND"),
            eq("PENDING_SECOND"), eq(REVIEWER_TWO_ID), eq("admin"),
            org.mockito.ArgumentMatchers.contains("400→450"));
        verify(mapper).applyStock(eq(PRODUCT_ID), eq(12), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("325.000000"), decimalEq("0"), decimalEq("0"));
    }

    @Test
    void reviewerCannotReviseStockGainCostDuringFirstApproval()
    {
        JewelryDocument document = document(124L, "STOCK_ADJUST", "PENDING_FIRST");
        JewelryDocumentItem item = item(224L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(12);
        item.setAdjustmentQty(2);
        item.setUnitCost(decimal("400.00"));
        stubDocument(document, item);
        Map<Long, BigDecimal> revisedCosts = new HashMap<Long, BigDecimal>();
        revisedCosts.put(224L, decimal("450.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(124L, "", null, REVIEWER_ONE_ID, "reviewer", "jewelry_reviewer",
                revisedCosts));

        assertTrue(error.getMessage().contains("只有管理员终审"));
        verify(mapper, never()).updateDocumentItemCost(any(JewelryDocumentItem.class));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void administratorCannotReviseCostForStockLossLine()
    {
        JewelryDocument document = document(125L, "STOCK_ADJUST", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(225L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(8);
        item.setAdjustmentQty(-2);
        item.setUnitCost(decimal("300.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 2, 0, 0, 0, 0, "300.00", "0", "0"));
        Map<Long, BigDecimal> revisedCosts = new HashMap<Long, BigDecimal>();
        revisedCosts.put(225L, decimal("450.00"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(125L, "", null, REVIEWER_TWO_ID, "admin", "jewelry_admin",
                revisedCosts));

        assertTrue(error.getMessage().contains("不是盘盈明细"));
        verify(mapper, never()).updateDocumentItemCost(any(JewelryDocumentItem.class));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void reviewerApprovalMovesStockAdjustmentToAdministratorReviewWithoutPosting()
    {
        JewelryDocument document = document(122L, "STOCK_ADJUST", "PENDING_FIRST");
        JewelryDocumentItem item = item(222L, 2, "0");
        item.setSystemQty(10);
        item.setCountedQty(12);
        item.setAdjustmentQty(2);
        item.setUnitCost(decimal("400.00"));
        stubDocument(document, item);

        service.approve(122L, "盘点无误", null, REVIEWER_ONE_ID, "reviewer", "jewelry_reviewer");

        verify(mapper).updateDocumentStatus(122L, "PENDING_FIRST", "PENDING_SECOND",
            REVIEWER_ONE_ID, "reviewer", null, 1);
        verify(mapper).insertApproval(122L, 1, "PASS", REVIEWER_ONE_ID, "reviewer", "盘点无误");
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
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

    @Test
    void savingCostAdjustmentSnapshotsCurrentInventoryAndAverageCost()
    {
        JewelryDocument document = document(null, "COST_ADJUST", "DRAFT");
        document.setReturnReason("修正历史采购成本");
        JewelryDocumentItem item = item(null, 1, "120.00");
        document.setItems(Arrays.asList(item));
        when(mapper.insertDocument(document)).thenAnswer(invocation -> {
            document.setDocumentId(401L);
            return 1;
        });
        when(mapper.selectDocumentById(401L)).thenReturn(document);
        when(mapper.selectDocumentItems(401L)).thenReturn(Arrays.asList(item));

        JewelryDocument saved = service.saveDocument(document, MAKER_ID, "maker");

        assertEquals("COST_ADJUST", saved.getDocType());
        assertEquals(10, item.getSystemQty());
        assertEquals(10, item.getQty());
        assertMoney("100.00", item.getUnitCost());
        assertMoney("120.00", item.getUnitPrice());
        assertMoney("200.00", item.getAmount());
        assertMoney("1200.00", item.getCostAmount());
        assertMoney("200.00", document.getTotalAmount());
        assertMoney("1200.00", document.getTotalCost());
    }

    @Test
    void reviewerApprovalMovesCostAdjustmentToAdministratorReviewWithoutPosting()
    {
        JewelryDocument document = document(402L, "COST_ADJUST", "PENDING_FIRST");
        document.setReturnReason("修正成本");
        JewelryDocumentItem item = item(4021L, 10, "120.00");
        item.setSystemQty(10);
        item.setUnitCost(decimal("100.00"));
        stubDocument(document, item);

        service.approve(402L, "初审通过", null, REVIEWER_ONE_ID, "reviewer", "jewelry_reviewer");

        verify(mapper).updateDocumentStatus(402L, "PENDING_FIRST", "PENDING_SECOND",
            REVIEWER_ONE_ID, "reviewer", null, 1);
        verify(mapper).insertApproval(402L, 1, "PASS", REVIEWER_ONE_ID, "reviewer", "初审通过");
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void administratorApprovalPostsCostAdjustmentWithoutChangingQuantity()
    {
        JewelryDocument document = document(403L, "COST_ADJUST", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(4031L, 10, "120.00");
        item.setSystemQty(10);
        item.setUnitCost(decimal("100.00"));
        stubDocument(document, item);

        service.approve(403L, "复核通过", null, REVIEWER_TWO_ID, "admin", "jewelry_admin");

        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("120.00"), decimalEq("0"), decimalEq("0"));
        verify(mapper).updateDocumentStatus(403L, "PENDING_SECOND", "POSTED",
            REVIEWER_TWO_ID, "admin", null, 2);
        verify(mapper).insertApproval(403L, 2, "PASS", REVIEWER_TWO_ID, "admin", "复核通过");
        assertEquals(10, item.getQty());
        assertMoney("200.00", item.getAmount());
        assertMoney("1200.00", item.getCostAmount());
        ArgumentCaptor<Map<String, Object>> transaction = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStockTransaction(transaction.capture());
        assertEquals("COST_ADJUST", transaction.getValue().get("transactionType"));
        assertEquals(0, transaction.getValue().get("onHandChange"));
        assertMoney("200.00", (BigDecimal) transaction.getValue().get("costAmountChange"));
    }

    @Test
    void administratorDirectCostAdjustmentPostsImmediatelyWithoutApproval()
    {
        JewelryDocument document = document(null, "COST_ADJUST", null);
        document.setReturnReason("修正盘点成本");
        JewelryDocumentItem item = item(null, 10, "120.00");
        document.setItems(Arrays.asList(item));
        when(mapper.insertDocument(document)).thenAnswer(invocation -> {
            document.setDocumentId(409L);
            return 1;
        });
        when(mapper.selectDocumentById(409L)).thenReturn(document);
        when(mapper.selectDocumentItems(409L)).thenReturn(Arrays.asList(item));

        JewelryDocument posted = service.directAdjustCosts(document, REVIEWER_TWO_ID, "admin",
            "jewelry_admin");

        assertEquals("POSTED", posted.getStatus());
        assertMoney("100.00", item.getUnitCost());
        assertMoney("120.00", item.getUnitPrice());
        verify(mapper).updateDocumentStatus(409L, "DRAFT", "POSTED", REVIEWER_TWO_ID, "admin", null, null);
        verify(mapper).insertEvent(409L, "ADMIN_DIRECT_COST_ADJUST", "DRAFT", "POSTED",
            REVIEWER_TWO_ID, "admin", "修正盘点成本");
        verify(mapper, never()).insertApproval(anyLong(), anyInt(), anyString(), anyLong(), anyString(), anyString());
        verify(mapper).applyStock(eq(PRODUCT_ID), eq(10), eq(0), eq(0), eq(0), eq(0), eq(0),
            decimalEq("120.00"), decimalEq("0"), decimalEq("0"));
        ArgumentCaptor<Map<String, Object>> transaction = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStockTransaction(transaction.capture());
        assertEquals("COST_ADJUST", transaction.getValue().get("transactionType"));
        assertEquals(0, transaction.getValue().get("onHandChange"));
        assertMoney("200.00", (BigDecimal) transaction.getValue().get("costAmountChange"));
    }

    @Test
    void nonAdministratorCannotUseDirectCostAdjustment()
    {
        JewelryDocument document = document(null, "COST_ADJUST", null);
        document.setReturnReason("尝试调价");
        document.setItems(Arrays.asList(item(null, 10, "120.00")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.directAdjustCosts(document, REVIEWER_ONE_ID, "reviewer", "jewelry_reviewer"));

        assertTrue(error.getMessage().contains("只有管理员"));
        verify(mapper, never()).insertDocument(any(JewelryDocument.class));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void costAdjustmentEnforcesApprovalRolesAndDifferentApprovers()
    {
        JewelryDocument firstStage = document(404L, "COST_ADJUST", "PENDING_FIRST");
        JewelryDocumentItem firstItem = item(4041L, 10, "120.00");
        firstItem.setUnitCost(decimal("100.00"));
        stubDocument(firstStage, firstItem);

        ServiceException firstError = assertThrows(ServiceException.class,
            () -> service.approve(404L, "", null, REVIEWER_TWO_ID, "admin", "jewelry_admin"));
        assertTrue(firstError.getMessage().contains("必须先由审核员审核"));

        JewelryDocument secondStage = document(405L, "COST_ADJUST", "PENDING_SECOND");
        secondStage.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem secondItem = item(4051L, 10, "120.00");
        secondItem.setUnitCost(decimal("100.00"));
        stubDocument(secondStage, secondItem);

        ServiceException secondError = assertThrows(ServiceException.class,
            () -> service.approve(405L, "", null, REVIEWER_ONE_ID, "reviewer", "jewelry_admin"));
        assertTrue(secondError.getMessage().contains("不能由同一人完成"));
    }

    @Test
    void purchaseSubmissionIsBlockedWhileSkuHasPendingCostAdjustment()
    {
        JewelryDocument purchase = document(406L, "PURCHASE_IN", "DRAFT");
        purchase.setSupplierId(1L);
        JewelryDocumentItem item = item(4061L, 2, "90.00");
        stubDocument(purchase, item);
        when(mapper.countPendingCostChangesByProduct(PRODUCT_ID)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(406L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("正在进行库存成本调价"));
        verify(mapper, never()).updateDocumentStatus(eq(406L), anyString(), anyString(), anyLong(), anyString(),
            any(), any());
    }

    @Test
    void costAdjustmentSubmissionIsBlockedWhileSkuHasPendingPurchase()
    {
        JewelryDocument adjustment = document(407L, "COST_ADJUST", "DRAFT");
        adjustment.setReturnReason("修正成本");
        JewelryDocumentItem item = item(4071L, 10, "120.00");
        item.setSystemQty(10);
        item.setUnitCost(decimal("100.00"));
        stubDocument(adjustment, item);
        when(mapper.countPendingPurchasesByProduct(PRODUCT_ID)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.submit(407L, MAKER_ID, "maker"));

        assertTrue(error.getMessage().contains("存在待审核采购入库单"));
        verify(mapper, never()).updateDocumentStatus(eq(407L), anyString(), anyString(), anyLong(), anyString(),
            any(), any());
    }

    @Test
    void finalCostAdjustmentApprovalFailsWhenAverageCostChanged()
    {
        JewelryDocument document = document(408L, "COST_ADJUST", "PENDING_SECOND");
        document.setFirstReviewerUserId(REVIEWER_ONE_ID);
        JewelryDocumentItem item = item(4081L, 10, "120.00");
        item.setUnitCost(decimal("100.00"));
        stubDocument(document, item);
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 0, 0, 0, 0, 0, "105.00", "0", "0"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.approve(408L, "", null, REVIEWER_TWO_ID, "admin", "jewelry_admin"));

        assertTrue(error.getMessage().contains("平均成本已变化"));
        verify(mapper, never()).applyStock(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
            any(), any(), any());
    }

    @Test
    void pricedInfluencerLocksSalesPriceDuringRiskAssessment()
    {
        Long influencerId = 9100L;
        Map<String, Object> influencer = activeInfluencer(true);
        influencer.put("influencerId", influencerId);
        when(mapper.selectInfluencerById(influencerId)).thenReturn(influencer);
        when(mapper.selectInfluencerProductPrices(influencerId))
            .thenReturn(Arrays.asList(pricedProductPrice(PRODUCT_ID, "1888.1234", 3)));
        JewelryDocument document = document(null, "SALES_OUT", null);
        document.setInfluencerId(influencerId);
        document.setSalesChannel("douyin");
        JewelryDocumentItem item = item(null, 1, "1.00");
        document.setItems(Arrays.asList(item));

        service.assessDocumentRisk(document);

        assertEquals(0, decimal("1888.1234").compareTo(item.getUnitPrice()));
        assertEquals(0, decimal("1888.1234").compareTo(item.getInfluencerPriceSnapshot()));
        assertEquals(3, item.getInfluencerPriceVersion());
        assertEquals(null, document.getInfluencerPriceSnapshot());
    }

    @Test
    void newInfluencerGetsSystemGeneratedCode()
    {
        Map<String, Object> influencer = new HashMap<String, Object>();
        influencer.put("influencerName", "自动编码达人");
        influencer.put("status", "0");
        influencer.put("createBy", "maker");
        when(mapper.insertInfluencer(any())).thenAnswer(invocation ->
        {
            Map<String, Object> inserted = invocation.getArgument(0);
            inserted.put("influencerId", 42L);
            return 1;
        });
        when(mapper.updateInfluencerCode(42L, "DR000042", "maker")).thenReturn(1);

        assertEquals(1, service.saveInfluencer(influencer));

        assertEquals("DR000042", influencer.get("influencerCode"));
        verify(mapper).updateInfluencerCode(42L, "DR000042", "maker");
    }

    @Test
    void savingSalesDraftCreatesPendingPriceForEachUnpricedProduct()
    {
        JewelryDocument document = document(null, "SALES_OUT", null);
        document.setSalesChannel("douyin");
        JewelryDocumentItem item = item(null, 1, "128.5678");
        document.setItems(Arrays.asList(item));
        when(mapper.insertDocument(document)).thenAnswer(invocation ->
        {
            document.setDocumentId(9201L);
            return 1;
        });
        when(mapper.selectDocumentById(9201L)).thenReturn(document);
        when(mapper.selectDocumentItems(9201L)).thenReturn(document.getItems());

        service.saveDocument(document, MAKER_ID, "maker");

        ArgumentCaptor<Map<String, Object>> pending = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertPendingInfluencerProductPrice(pending.capture());
        assertEquals(SALES_INFLUENCER_ID, pending.getValue().get("influencerId"));
        assertEquals(PRODUCT_ID, pending.getValue().get("productId"));
        assertEquals(9201L, pending.getValue().get("sourceDocumentId"));
        assertEquals(0, decimal("128.5678").compareTo((BigDecimal) pending.getValue().get("fixedUnitPrice")));
        assertEquals(0, decimal("128.5678").compareTo(item.getInfluencerPriceSnapshot()));
        assertEquals(0, item.getInfluencerPriceVersion());
    }

    @Test
    void firstSalesPostingEstablishesInfluencerPriceAndHistory()
    {
        JewelryDocument document = document(9101L, "SALES_OUT", "PENDING_FIRST");
        JewelryDocumentItem item = item(9102L, 1, "999.1234");
        item.setInfluencerPriceSnapshot(decimal("999.1234"));
        item.setInfluencerPriceVersion(0);
        stubDocument(document, item);
        when(mapper.selectInfluencerByIdForUpdate(SALES_INFLUENCER_ID)).thenReturn(activeInfluencer(false));
        when(mapper.selectInfluencerProductPriceForUpdate(SALES_INFLUENCER_ID, PRODUCT_ID))
            .thenReturn(pendingProductPrice(PRODUCT_ID, "999.1234", 9101L));
        when(mapper.promoteInfluencerProductPrice(SALES_INFLUENCER_ID, PRODUCT_ID, 9101L, "reviewer1"))
            .thenReturn(1);
        when(mapper.selectStockForUpdate(PRODUCT_ID))
            .thenReturn(stock(10, 1, 0, 0, 0, 0, "100.00", "0", "0"));

        service.approve(9101L, "", null, REVIEWER_ONE_ID, "reviewer1");

        ArgumentCaptor<Map<String, Object>> history = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertInfluencerPriceHistory(history.capture());
        assertEquals("FIRST_SALE", history.getValue().get("sourceType"));
        assertEquals(9101L, history.getValue().get("sourceDocumentId"));
        assertEquals(PRODUCT_ID, history.getValue().get("productId"));
        assertEquals(0, decimal("999.1234").compareTo((BigDecimal) history.getValue().get("newPrice")));
    }

    @Test
    void administratorPriceChangeIsVersionedAndAudited()
    {
        Map<String, Object> influencer = activeInfluencer(true);
        influencer.put("influencerId", RETURN_INFLUENCER_ID);
        when(mapper.selectInfluencerByIdForUpdate(RETURN_INFLUENCER_ID)).thenReturn(influencer);
        when(mapper.selectInfluencerProductPriceForUpdate(RETURN_INFLUENCER_ID, PRODUCT_ID))
            .thenReturn(pricedProductPrice(PRODUCT_ID, "50.0000", 4));
        when(mapper.updateInfluencerProductPrice(RETURN_INFLUENCER_ID, PRODUCT_ID,
            decimal("66.1234"), 4, "admin")).thenReturn(1);

        service.changeInfluencerProductPrice(RETURN_INFLUENCER_ID, PRODUCT_ID, decimal("66.1234"), "合同改价",
            REVIEWER_TWO_ID, "admin");

        ArgumentCaptor<Map<String, Object>> history = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertInfluencerPriceHistory(history.capture());
        assertEquals("ADMIN_CHANGE", history.getValue().get("sourceType"));
        assertEquals(PRODUCT_ID, history.getValue().get("productId"));
        assertEquals(5, history.getValue().get("priceVersion"));
        assertEquals("合同改价", history.getValue().get("changeReason"));
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
        if ("SALES_OUT".equals(type)) document.setInfluencerId(SALES_INFLUENCER_ID);
        if ("CUSTOMER_RETURN".equals(type)) document.setInfluencerId(RETURN_INFLUENCER_ID);
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
        item.setOtherFee1(BigDecimal.ZERO);
        item.setOtherFee2(BigDecimal.ZERO);
        item.setOtherFee3(BigDecimal.ZERO);
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

    private Map<String, Object> activeInfluencer(boolean priced)
    {
        Map<String, Object> influencer = new HashMap<String, Object>();
        influencer.put("influencerId", priced ? RETURN_INFLUENCER_ID : SALES_INFLUENCER_ID);
        influencer.put("influencerName", priced ? "退货达人" : "销售达人");
        influencer.put("salesChannel", "douyin");
        influencer.put("status", "0");
        return influencer;
    }

    private Map<String, Object> pricedProductPrice(Long productId, String price, int version)
    {
        Map<String, Object> record = new HashMap<String, Object>();
        record.put("productId", productId);
        record.put("fixedUnitPrice", decimal(price));
        record.put("priceStatus", "PRICED");
        record.put("priceVersion", version);
        return record;
    }

    private Map<String, Object> pendingProductPrice(Long productId, String price, Long sourceDocumentId)
    {
        Map<String, Object> record = new HashMap<String, Object>();
        record.put("productId", productId);
        record.put("fixedUnitPrice", decimal(price));
        record.put("priceStatus", "PENDING");
        record.put("priceVersion", 0);
        record.put("pendingSourceDocumentId", sourceDocumentId);
        return record;
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
