package com.ruoyi.jewelry.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.domain.JewelryDocumentItem;

public interface JewelryErpMapper
{
    List<Map<String, Object>> selectStaffList(Map<String, Object> query);
    Map<String, Object> selectStaffById(Long staffId);
    int insertStaff(Map<String, Object> staff);
    int updateStaff(Map<String, Object> staff);
    Long selectRoleIdByKey(String roleKey);
    int deleteJewelryRolesByUserId(Long userId);
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    List<Map<String, Object>> selectProductList(Map<String, Object> query);
    Map<String, Object> selectProductById(Long productId);
    int insertProduct(Map<String, Object> product);
    int updateProduct(Map<String, Object> product);
    int updateProductBasic(Map<String, Object> product);
    int updateProductImagesIfEmpty(@Param("productId") Long productId, @Param("imageUrl") String imageUrl,
        @Param("imageUrls") String imageUrls, @Param("userName") String userName);
    int ensureStock(Long productId);


    List<Map<String, Object>> selectSupplierList(Map<String, Object> query);
    Map<String, Object> selectSupplierById(Long supplierId);
    int insertSupplier(Map<String, Object> supplier);
    int updateSupplier(Map<String, Object> supplier);

    List<Map<String, Object>> selectStockList(Map<String, Object> query);
    Map<String, Object> selectStockForUpdate(Long productId);
    List<Map<String, Object>> selectStockTransactions(Map<String, Object> query);
    Integer selectStockWarningDays();
    int upsertStockWarningDays(@Param("days") Integer days, @Param("userName") String userName);
    Map<String, Object> selectDashboard();

    List<JewelryDocument> selectDocumentList(JewelryDocument query);
    JewelryDocument selectDocumentById(Long documentId);
    JewelryDocument selectDocumentByIdForUpdate(Long documentId);
    int countReversalBySource(Long sourceDocumentId);
    int countActiveCustomerReturnsBySource(Long sourceDocumentId);
    int countActiveSupplierReturnsBySource(Long sourceDocumentId);
    int countPendingCostChangesByProduct(Long productId);
    int countPendingPurchasesByProduct(Long productId);
    int selectReturnedQtyBySourceItem(@Param("sourceItemId") Long sourceItemId,
        @Param("excludeDocumentId") Long excludeDocumentId);
    int selectSupplierReturnedQtyBySourceItem(@Param("sourceItemId") Long sourceItemId,
        @Param("excludeDocumentId") Long excludeDocumentId);
    List<JewelryDocument> selectSupplierReturnSourceList(Long supplierId);
    List<JewelryDocumentItem> selectSupplierReturnSourceItems(@Param("sourceDocumentId") Long sourceDocumentId,
        @Param("excludeDocumentId") Long excludeDocumentId);
    int selectInspectedQtyBySourceItem(@Param("sourceItemId") Long sourceItemId,
        @Param("excludeDocumentId") Long excludeDocumentId);
    List<JewelryDocumentItem> selectReturnInspectionSourceItems(@Param("sourceDocumentId") Long sourceDocumentId,
        @Param("excludeDocumentId") Long excludeDocumentId);
    List<JewelryDocumentItem> selectDocumentItems(Long documentId);
    int insertDocument(JewelryDocument document);
    int updateDocument(JewelryDocument document);
    int updateDocumentFinancials(JewelryDocument document);
    int deleteDocumentApprovals(Long documentId);
    int deleteDocumentEvents(Long documentId);
    int deleteDocumentItems(Long documentId);
    int deleteDraftDocument(@Param("documentId") Long documentId, @Param("creatorUserId") Long creatorUserId);
    int insertDocumentItem(JewelryDocumentItem item);
    int updateDocumentItemCost(JewelryDocumentItem item);
    int updateCostAdjustmentPostedItem(JewelryDocumentItem item);
    int updateDocumentStatus(@Param("documentId") Long documentId, @Param("fromStatus") String fromStatus,
        @Param("toStatus") String toStatus, @Param("userId") Long userId, @Param("userName") String userName,
        @Param("reason") String reason, @Param("stage") Integer stage);
    int insertApproval(@Param("documentId") Long documentId, @Param("stage") Integer stage,
        @Param("action") String action, @Param("userId") Long userId, @Param("userName") String userName,
        @Param("comment") String comment);
    int insertEvent(@Param("documentId") Long documentId, @Param("eventType") String eventType,
        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
        @Param("userId") Long userId, @Param("userName") String userName, @Param("comment") String comment);

    int reserveOutbound(@Param("productId") Long productId, @Param("qty") Integer qty);
    int releaseOutbound(@Param("productId") Long productId, @Param("qty") Integer qty);
    int reserveInspection(@Param("productId") Long productId, @Param("qty") Integer qty);
    int releaseInspection(@Param("productId") Long productId, @Param("qty") Integer qty);
    int reserveDefect(@Param("productId") Long productId, @Param("qty") Integer qty);
    int releaseDefect(@Param("productId") Long productId, @Param("qty") Integer qty);
    int markOriginalReversed(@Param("documentId") Long documentId, @Param("userName") String userName);
    int applyStock(@Param("productId") Long productId, @Param("onHand") Integer onHand,
        @Param("reserved") Integer reserved, @Param("inspection") Integer inspection,
        @Param("inspectionReserved") Integer inspectionReserved, @Param("defect") Integer defect,
        @Param("defectReserved") Integer defectReserved, @Param("avgCost") java.math.BigDecimal avgCost,
        @Param("inspectionCost") java.math.BigDecimal inspectionCost,
        @Param("defectCost") java.math.BigDecimal defectCost);
    int insertStockTransaction(Map<String, Object> transaction);
}
