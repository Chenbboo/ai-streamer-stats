package com.ruoyi.jewelry.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.jewelry.domain.JewelryDocument;

public interface IJewelryErpService
{
    List<Map<String, Object>> listProducts(Map<String, Object> query);
    int saveProduct(Map<String, Object> product);
    int updateProductBasic(Map<String, Object> product);
    List<Map<String, Object>> listSuppliers(Map<String, Object> query);
    int saveSupplier(Map<String, Object> supplier);
    List<Map<String, Object>> listStock(Map<String, Object> query);
    List<Map<String, Object>> listTransactions(Map<String, Object> query);
    int getStockWarningDays();
    void setStockWarningDays(int days, String userName);
    Map<String, Object> dashboard();
    List<JewelryDocument> listDocuments(JewelryDocument query);
    JewelryDocument getDocument(Long documentId);
    JewelryDocument getDocumentForDisplay(Long documentId);
    JewelryDocument getReturnInspectionSource(Long sourceDocumentId, Long excludeDocumentId);
    List<JewelryDocument> listSupplierReturnSources(Long supplierId);
    JewelryDocument getSupplierReturnSource(Long sourceDocumentId, Long excludeDocumentId);
    Map<String, Object> assessDocumentRisk(JewelryDocument document);
    Map<String, Object> calculateProfit(Map<String, Object> input);
    JewelryDocument saveDocument(JewelryDocument document, Long userId, String userName);
    JewelryDocument directAdjustCosts(JewelryDocument document, Long userId, String userName, String operatorRole);
    void deleteDraft(Long documentId, Long userId);
    JewelryDocument createReversal(Long sourceDocumentId, Long userId, String userName);
    void submit(Long documentId, Long userId, String userName);
    void withdraw(Long documentId, Long userId, String userName);
    void approve(Long documentId, String comment, BigDecimal expectedTotalCost, Long userId, String userName,
        String approvalRole, Map<Long, BigDecimal> stockAdjustmentCosts);
    void reject(Long documentId, String comment, Long userId, String userName, String approvalRole);

    default void approve(Long documentId, String comment, BigDecimal expectedTotalCost, Long userId, String userName)
    {
        approve(documentId, comment, expectedTotalCost, userId, userName, "jewelry_reviewer");
    }

    default void approve(Long documentId, String comment, BigDecimal expectedTotalCost, Long userId, String userName,
        String approvalRole)
    {
        approve(documentId, comment, expectedTotalCost, userId, userName, approvalRole,
            java.util.Collections.<Long, BigDecimal>emptyMap());
    }

    default void reject(Long documentId, String comment, Long userId, String userName)
    {
        reject(documentId, comment, userId, userName, "jewelry_reviewer");
    }
}
