package com.ruoyi.jewelry.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.jewelry.domain.JewelryDocument;

public interface IJewelryErpService
{
    List<Map<String, Object>> listProducts(Map<String, Object> query);
    int saveProduct(Map<String, Object> product);
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
    JewelryDocument saveDocument(JewelryDocument document, Long userId, String userName);
    JewelryDocument createReversal(Long sourceDocumentId, Long userId, String userName);
    void submit(Long documentId, Long userId, String userName);
    void withdraw(Long documentId, Long userId, String userName);
    void approve(Long documentId, String comment, BigDecimal expectedTotalCost, Long userId, String userName);
    void reject(Long documentId, String comment, Long userId, String userName);
}
