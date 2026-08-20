package com.ruoyi.jewelry.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.domain.JewelryDocumentItem;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;
import com.ruoyi.jewelry.service.IJewelryErpService;

@Service
public class JewelryErpServiceImpl implements IJewelryErpService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6);
    private static final Set<String> EDITABLE_DOCUMENT_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("PURCHASE_IN", "SALES_OUT", "SUPPLIER_RETURN",
            "CUSTOMER_RETURN", "RETURN_INSPECT", "STOCK_ADJUST", "COST_ADJUST", "ASSEMBLY")));
    private static final Set<String> PRODUCT_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("FINISHED", "PART", "ACCESSORY", "WELFARE")));
    private static final Set<String> SPECIFICATION_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("精品", "普通")));
    private static final Set<String> SALES_ROLES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("NORMAL", "MAIN", "ADDON")));
    private static final Set<String> SALES_PRICING_MODES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("SEPARATE", "INCLUDED")));
    private static final Set<String> SALES_ADDON_PRODUCT_TYPES = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("PART", "ACCESSORY", "WELFARE")));

    @Autowired
    private JewelryErpMapper mapper;

    @Override
    public List<Map<String, Object>> listProducts(Map<String, Object> query) { return mapper.selectProductList(query); }

    @Override
    @Transactional
    public int saveProduct(Map<String, Object> product)
    {
        String productType = textValue(product.get("productType")).trim();
        if (!PRODUCT_TYPES.contains(productType))
            throw new ServiceException("商品类型只能选择成品商品、散件商品、配件商品或福利商品");
        String specification = textValue(product.get("specification")).trim();
        if (!SPECIFICATION_TYPES.contains(specification))
            throw new ServiceException("规格类型只能选择精品或普通");
        product.put("productType", productType);
        product.put("specification", specification);
        product.put("defaultPackFee", nonNegativeDecimalValue(product.get("defaultPackFee"), "默认包装费"));
        product.put("defaultShipFee", nonNegativeDecimalValue(product.get("defaultShipFee"), "默认物流费"));
        product.put("defaultCertFee", nonNegativeDecimalValue(product.get("defaultCertFee"), "默认鉴定费"));
        product.put("warningQty", nonNegativeValue(product.get("warningQty"), 5));
        String status = textValue(product.get("status"));
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("商品状态不正确");
        String productImage = singleImage(product.get("imageUrls"));
        if (productImage.isEmpty()) productImage = singleImage(product.get("imageUrl"));
        product.put("imageUrls", productImage);
        product.put("imageUrl", productImage);
        int rows;
        if (product.get("productId") == null)
        {
            rows = mapper.insertProduct(product);
            mapper.ensureStock(longValue(product.get("productId")));
        }
        else
        {
            rows = mapper.updateProduct(product);
        }
        return rows;
    }

    @Override
    public int updateProductBasic(Map<String, Object> product)
    {
        String productName = textValue(product.get("productName")).trim();
        if (productName.isEmpty()) throw new ServiceException("商品名称不能为空");
        String productImage = singleImage(product.get("imageUrls"));
        if (productImage.isEmpty()) productImage = singleImage(product.get("imageUrl"));
        product.put("productName", productName);
        product.put("imageUrls", productImage);
        product.put("imageUrl", productImage);
        return mapper.updateProductBasic(product);
    }

    @Override
    public List<Map<String, Object>> listSuppliers(Map<String, Object> query) { return mapper.selectSupplierList(query); }

    @Override
    public int saveSupplier(Map<String, Object> supplier)
    {
        return supplier.get("supplierId") == null ? mapper.insertSupplier(supplier) : mapper.updateSupplier(supplier);
    }

    @Override
    public List<Map<String, Object>> listStock(Map<String, Object> query) { return mapper.selectStockList(query); }
    @Override
    public List<Map<String, Object>> listTransactions(Map<String, Object> query) { return mapper.selectStockTransactions(query); }
    @Override
    public int getStockWarningDays()
    {
        Integer days = mapper.selectStockWarningDays();
        return days == null || days <= 0 ? 25 : days;
    }
    @Override
    public void setStockWarningDays(int days, String userName)
    {
        if (days < 1 || days > 365) throw new ServiceException("库存时间预警必须在1到365天之间");
        mapper.upsertStockWarningDays(days, userName);
    }
    @Override
    public Map<String, Object> dashboard() { return mapper.selectDashboard(); }
    @Override
    public List<JewelryDocument> listDocuments(JewelryDocument query) { return mapper.selectDocumentList(query); }

    @Override
    public JewelryDocument getDocument(Long documentId)
    {
        JewelryDocument document = requireDocument(documentId);
        document.setItems(mapper.selectDocumentItems(documentId));
        return document;
    }

    @Override
    public JewelryDocument getDocumentForDisplay(Long documentId)
    {
        JewelryDocument document = getDocument(documentId);
        if ("ASSEMBLY".equals(document.getDocType())
            && ("PENDING_FIRST".equals(document.getStatus()) || "PENDING_SECOND".equals(document.getStatus())))
        {
            refreshAssemblyCosts(document, false, false);
        }
        return document;
    }

    @Override
    public JewelryDocument getReturnInspectionSource(Long sourceDocumentId, Long excludeDocumentId)
    {
        JewelryDocument source = requirePostedCustomerReturn(sourceDocumentId);
        source.setItems(mapper.selectReturnInspectionSourceItems(sourceDocumentId, excludeDocumentId));
        return source;
    }

    @Override
    public Map<String, Object> assessDocumentRisk(JewelryDocument document)
    {
        if (!"SALES_OUT".equals(document.getDocType()))
            throw new ServiceException("当前仅支持销售出库单风险试算");
        validateDocument(document);
        calculateDocument(document);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("riskStatus", document.getRiskStatus());
        result.put("loss", "LOSS".equals(document.getRiskStatus()));
        return result;
    }

    @Override
    public Map<String, Object> calculateProfit(Map<String, Object> input)
    {
        Long productId = nullableLong(input.get("productId"));
        if (productId == null) throw new ServiceException("请选择需要试算的商品");
        Map<String, Object> product = mapper.selectProductById(productId);
        if (product == null || !"0".equals(textValue(product.get("status"))))
            throw new ServiceException("商品不存在或已停用");

        BigDecimal price = decimalValue(input.get("price"), "成交价");
        if (price.signum() <= 0) throw new ServiceException("成交价必须大于0");
        int quantity = input.get("quantity") == null ? 1 : integerValue(input.get("quantity"), "试算数量");
        int availableQty = decimal(product.get("onHandQty")).subtract(decimal(product.get("reservedOutQty"))).intValue();
        if (quantity <= 0) throw new ServiceException("试算数量必须大于0");
        if (quantity > availableQty) throw new ServiceException("试算数量不能超过当前可用库存" + availableQty + "件");

        BigDecimal cost = decimal(product.get("avgCost"));
        BigDecimal packFee = nonNegativeDecimalValue(input.get("packFee"), "包装费");
        BigDecimal shipFee = nonNegativeDecimalValue(input.get("shipFee"), "物流费");
        BigDecimal certFee = nonNegativeDecimalValue(input.get("certFee"), "鉴定费");
        BigDecimal otherFee1 = nonNegativeDecimalValue(input.get("otherFee1"), "其他1");
        BigDecimal otherFee2 = nonNegativeDecimalValue(input.get("otherFee2"), "其他2");
        BigDecimal otherFee3 = nonNegativeDecimalValue(input.get("otherFee3"), "其他3");
        BigDecimal fees = packFee.add(shipFee).add(certFee)
            .add(otherFee1).add(otherFee2).add(otherFee3);
        BigDecimal platformRate = percentageValue(input.get("platformRate"), "平台扣点率");
        BigDecimal commissionRate = percentageValue(input.get("commissionRate"), "达人佣金率");
        BigDecimal taxRate = percentageValue(input.get("taxRate"), "税率");
        BigDecimal rate = platformRate.add(commissionRate).add(taxRate);
        validateCombinedRate(rate);

        Map<String, BigDecimal> line = calculateSalesLine(price, cost, fees, rate);
        BigDecimal deductions = line.get("deductions");
        BigDecimal profit = line.get("profit");
        BigDecimal breakEvenPrice = cost.add(fees).divide(BigDecimal.ONE.subtract(rate), 2, RoundingMode.HALF_UP);
        BigDecimal maxCommissionRate = BigDecimal.ONE.subtract(platformRate).subtract(taxRate)
            .subtract(cost.add(fees).divide(price, 8, RoundingMode.HALF_UP));
        if (maxCommissionRate.signum() < 0) maxCommissionRate = BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("profit", profit.setScale(2, RoundingMode.HALF_UP));
        result.put("profitRate", profit.divide(price, 6, RoundingMode.HALF_UP));
        result.put("cost", cost.setScale(2, RoundingMode.HALF_UP));
        result.put("deductions", deductions.setScale(2, RoundingMode.HALF_UP));
        result.put("fixedFees", fees.setScale(2, RoundingMode.HALF_UP));
        result.put("breakEvenPrice", breakEvenPrice);
        result.put("maxCommissionRate", maxCommissionRate.setScale(6, RoundingMode.HALF_UP));
        result.put("quantity", quantity);
        result.put("availableQty", availableQty);
        result.put("remainingQty", availableQty - quantity);
        result.put("totalRevenue", price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        result.put("totalProfit", profit.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        result.put("totalDeductions", deductions.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        result.put("totalFixedFees", fees.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    @Override
    @Transactional
    public JewelryDocument saveDocument(JewelryDocument document, Long userId, String userName)
    {
        if ("REVERSAL".equals(document.getDocType()))
        {
            throw new ServiceException("红冲单只能从已入账原单发起，不能手工创建或修改");
        }
        if ("STOCK_ADJUST".equals(document.getDocType()))
        {
            prepareStockAdjustment(document);
        }
        if ("COST_ADJUST".equals(document.getDocType()))
        {
            prepareCostAdjustment(document);
        }
        prepareInlineAssemblyOutput(document, userName);
        validateDocument(document);
        calculateDocument(document);
        document.setUpdateBy(userName);
        if (document.getDocumentId() == null)
        {
            document.setDocNo(createDocNo(document.getDocType()));
            document.setStatus("DRAFT");
            document.setCreatorUserId(userId);
            document.setCreatorName(userName);
            document.setCreateBy(userName);
            mapper.insertDocument(document);
            mapper.insertEvent(document.getDocumentId(), "CREATE", "", "DRAFT", userId, userName, "");
        }
        else
        {
            JewelryDocument current = requireDocument(document.getDocumentId());
            if (!text(current.getDocType()).equals(text(document.getDocType())))
            {
                throw new ServiceException("单据类型创建后不允许修改");
            }
            if ("REVERSAL".equals(current.getDocType()))
            {
                throw new ServiceException("红冲单明细不允许修改");
            }
            if (!"DRAFT".equals(current.getStatus()) && !"REJECTED".equals(current.getStatus()))
            {
                throw new ServiceException("只有草稿或已驳回单据可以修改");
            }
            if (!userId.equals(current.getCreatorUserId()))
            {
                throw new ServiceException("只能修改自己创建的单据");
            }
            if (mapper.updateDocument(document) != 1)
            {
                throw new ServiceException("单据状态已变化，请刷新后重试");
            }
            mapper.deleteDocumentItems(document.getDocumentId());
            mapper.insertEvent(document.getDocumentId(), "EDIT", current.getStatus(), "DRAFT", userId, userName, "");
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            if ("PURCHASE_IN".equals(document.getDocType()))
            {
                item.setImageUrls(singleImage(item.getImageUrls()));
            }
            item.setDocumentId(document.getDocumentId());
            mapper.insertDocumentItem(item);
            if ("PURCHASE_IN".equals(document.getDocType()) && !text(item.getImageUrls()).isEmpty())
            {
                String firstImage = item.getImageUrls().split(",")[0].trim();
                mapper.updateProductImagesIfEmpty(item.getProductId(), firstImage, item.getImageUrls(), userName);
            }
        }
        return getDocument(document.getDocumentId());
    }

    @Override
    @Transactional
    public void deleteDraft(Long documentId, Long userId)
    {
        JewelryDocument document = mapper.selectDocumentByIdForUpdate(documentId);
        if (document == null)
        {
            throw new ServiceException("单据不存在或已被删除");
        }
        if (!"DRAFT".equals(document.getStatus()))
        {
            throw new ServiceException("只有草稿单据可以删除");
        }
        if (!userId.equals(document.getCreatorUserId()))
        {
            throw new ServiceException("只能删除自己创建的草稿");
        }
        mapper.deleteDocumentApprovals(documentId);
        mapper.deleteDocumentEvents(documentId);
        mapper.deleteDocumentItems(documentId);
        if (mapper.deleteDraftDocument(documentId, userId) != 1)
        {
            throw new ServiceException("草稿状态已变化，请刷新后重试");
        }
    }

    private void prepareInlineAssemblyOutput(JewelryDocument document, String userName)
    {
        Map<String, Object> product = document.getNewOutputProduct();
        if (product == null || product.isEmpty()) return;
        if (!"ASSEMBLY".equals(document.getDocType()))
            throw new ServiceException("只有组装单可以同时新建成品档案");
        if (document.getItems() == null || document.getItems().isEmpty())
            throw new ServiceException("组装单明细不能为空");

        JewelryDocumentItem output = null;
        for (JewelryDocumentItem item : document.getItems())
        {
            if (!"OUTPUT".equals(item.getItemRole())) continue;
            if (output != null) throw new ServiceException("组装单必须且只能有一个成品产出");
            output = item;
        }
        if (output == null) throw new ServiceException("请填写组装成品信息");
        if (output.getProductId() != null) throw new ServiceException("新建成品不能同时选择已有成品");

        String sku = text(product.get("sku") == null ? null : String.valueOf(product.get("sku"))).trim();
        String name = text(product.get("productName") == null ? null : String.valueOf(product.get("productName"))).trim();
        if (sku.isEmpty() || name.isEmpty()) throw new ServiceException("新成品的SKU和商品名称不能为空");

        String productImage = singleImage(product.get("imageUrls"));
        if (productImage.isEmpty()) productImage = singleImage(output.getImageUrls());
        product.remove("productId");
        product.put("sku", sku);
        product.put("productName", name);
        product.put("productType", "FINISHED");
        product.put("category", textValue(product.get("category")));
        String specification = textValue(product.get("specification")).trim();
        if (!SPECIFICATION_TYPES.contains(specification))
            throw new ServiceException("新成品必须选择精品或普通规格类型");
        product.put("specification", specification);
        product.put("imageUrl", productImage);
        product.put("imageUrls", productImage);
        product.put("unit", textValue(product.get("unit")).isEmpty() ? "件" : textValue(product.get("unit")));
        product.put("defaultPackFee", ZERO);
        product.put("defaultShipFee", ZERO);
        product.put("defaultCertFee", ZERO);
        product.put("warningQty", nonNegativeValue(product.get("warningQty"), 5));
        product.put("status", "0");
        product.put("createBy", userName);
        product.put("remark", textValue(product.get("remark")));
        try
        {
            mapper.insertProduct(product);
        }
        catch (DuplicateKeyException ex)
        {
            throw new ServiceException("SKU已存在，请更换SKU或选择已有成品");
        }
        Long productId = longValue(product.get("productId"));
        if (productId <= 0) throw new ServiceException("新成品档案创建失败");
        mapper.ensureStock(productId);
        output.setProductId(productId);
        if (text(output.getImageUrls()).isEmpty()) output.setImageUrls(productImage);
        document.setNewOutputProduct(null);
    }

    private String singleImage(Object value)
    {
        String images = value == null ? "" : text(String.valueOf(value));
        if (images.isEmpty()) return "";
        return images.split(",")[0].trim();
    }

    @Override
    @Transactional
    public JewelryDocument createReversal(Long sourceDocumentId, Long userId, String userName)
    {
        JewelryDocument source = mapper.selectDocumentByIdForUpdate(sourceDocumentId);
        if (source == null)
        {
            throw new ServiceException("原单不存在");
        }
        if (!"POSTED".equals(source.getStatus()))
        {
            throw new ServiceException("只有已入账且未红冲的单据可以发起红冲");
        }
        if ("REVERSAL".equals(source.getDocType()))
        {
            throw new ServiceException("红冲单不能再次红冲");
        }
        if ("ASSEMBLY".equals(source.getDocType()))
        {
            throw new ServiceException("组装单暂不支持整单红冲，请通过库存调整处理差异");
        }
        ensureSaleHasNoActiveReturns(source);
        if (mapper.countReversalBySource(sourceDocumentId) > 0)
        {
            throw new ServiceException("该原单已经存在红冲单，请勿重复发起");
        }
        List<JewelryDocumentItem> sourceItems = mapper.selectDocumentItems(sourceDocumentId);
        if (sourceItems == null || sourceItems.isEmpty())
        {
            throw new ServiceException("原单没有商品明细，不能红冲");
        }

        JewelryDocument reversal = new JewelryDocument();
        reversal.setDocNo(createDocNo("REVERSAL"));
        reversal.setDocType("REVERSAL");
        reversal.setBizDate(new Date());
        reversal.setStatus("DRAFT");
        reversal.setSupplierId(source.getSupplierId());
        reversal.setSupplierNameSnapshot(source.getSupplierNameSnapshot());
        reversal.setSalesChannel(source.getSalesChannel());
        reversal.setExternalNo(source.getExternalNo());
        reversal.setInfluencerName(source.getInfluencerName());
        reversal.setPlatformRate(source.getPlatformRate());
        reversal.setCommissionRate(source.getCommissionRate());
        reversal.setTaxRate(source.getTaxRate());
        reversal.setReturnReason("红冲原单 " + source.getDocNo());
        reversal.setSourceDocumentId(sourceDocumentId);
        reversal.setTotalQty(-nonNegative(source.getTotalQty()));
        int reversalAmountScale = "PURCHASE_IN".equals(source.getDocType()) ? 4 : 2;
        reversal.setTotalAmount(money(source.getTotalAmount()).negate()
            .setScale(reversalAmountScale, RoundingMode.HALF_UP));
        reversal.setTotalCost(money(source.getTotalCost()).negate()
            .setScale(reversalAmountScale, RoundingMode.HALF_UP));
        reversal.setTotalProfit(money(source.getTotalProfit()).negate().setScale(2, RoundingMode.HALF_UP));
        reversal.setRiskStatus("NORMAL");
        reversal.setLaborFee(ZERO);
        reversal.setProcessingFee(ZERO);
        reversal.setOtherFee(ZERO);
        reversal.setCreatorUserId(userId);
        reversal.setCreatorName(userName);
        reversal.setCreateBy(userName);
        reversal.setRemark("系统生成，关联原单：" + source.getDocNo());
        mapper.insertDocument(reversal);

        List<JewelryDocumentItem> reversalItems = new ArrayList<JewelryDocumentItem>();
        for (JewelryDocumentItem sourceItem : sourceItems)
        {
            JewelryDocumentItem item = copyReversalItem(sourceItem, reversal.getDocumentId(), source.getDocType());
            mapper.insertDocumentItem(item);
            reversalItems.add(item);
        }
        reversal.setItems(reversalItems);
        mapper.insertEvent(reversal.getDocumentId(), "CREATE_REVERSAL", "", "DRAFT", userId, userName,
            "原单：" + source.getDocNo());
        mapper.insertEvent(sourceDocumentId, "REVERSAL_CREATED", "POSTED", "POSTED", userId, userName,
            "红冲单：" + reversal.getDocNo());
        return reversal;
    }

    @Override
    @Transactional
    public void submit(Long documentId, Long userId, String userName)
    {
        JewelryDocument document = getDocument(documentId);
        String fromStatus = document.getStatus();
        boolean rejectedReversal = "REVERSAL".equals(document.getDocType()) && "REJECTED".equals(fromStatus);
        if (!"DRAFT".equals(fromStatus) && !rejectedReversal)
        {
            throw new ServiceException("只有草稿单据或已驳回红冲单可以提交");
        }
        if (!userId.equals(document.getCreatorUserId()))
        {
            throw new ServiceException("只能提交自己创建的单据");
        }
        if (!"REVERSAL".equals(document.getDocType()))
        {
            if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() != null)
            {
                JewelryDocument source = mapper.selectDocumentByIdForUpdate(document.getSourceDocumentId());
                if (source == null || !"SALES_OUT".equals(source.getDocType()) || !"POSTED".equals(source.getStatus()))
                    throw new ServiceException("关联的原销售单已失效，请重新选择");
                if (mapper.countReversalBySource(source.getDocumentId()) > 0)
                    throw new ServiceException("关联的原销售单已存在红冲单，不能再提交消费者退货");
            }
            else if ("RETURN_INSPECT".equals(document.getDocType()))
            {
                if (document.getSourceDocumentId() == null)
                    throw new ServiceException("退货质检必须关联原客户退货单");
                JewelryDocument source = mapper.selectDocumentByIdForUpdate(document.getSourceDocumentId());
                if (source == null || !"CUSTOMER_RETURN".equals(source.getDocType())
                    || !"POSTED".equals(source.getStatus()))
                    throw new ServiceException("关联的客户退货单已失效，请重新选择");
            }
            validateDocument(document);
            calculateDocument(document);
            if ("SALES_OUT".equals(document.getDocType()))
                validateAccessoryPackagingCoverage(document);
            mapper.updateDocumentFinancials(document);
            for (JewelryDocumentItem item : document.getItems()) mapper.updateDocumentItemCost(item);
        }
        if ("STOCK_ADJUST".equals(document.getDocType()))
        {
            validateStockAdjustmentSnapshot(document);
        }
        if ("COST_ADJUST".equals(document.getDocType()))
        {
            validateCostAdjustmentSnapshot(document);
        }
        if ("PURCHASE_IN".equals(document.getDocType()) || isCostChangeDocument(document))
        {
            validateCostChangeConflicts(document);
        }
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            refreshAssemblyCosts(document, true, false);
        }
        reserve(document);
        changeStatus(document, fromStatus, "PENDING_FIRST", userId, userName, null, null);
        mapper.insertEvent(documentId, rejectedReversal ? "RESUBMIT" : "SUBMIT", fromStatus,
            "PENDING_FIRST", userId, userName, "");
    }

    @Override
    @Transactional
    public void withdraw(Long documentId, Long userId, String userName)
    {
        JewelryDocument document = getDocument(documentId);
        if (!"PENDING_FIRST".equals(document.getStatus()))
        {
            throw new ServiceException("只有待审核单据可以撤回");
        }
        if (!userId.equals(document.getCreatorUserId()))
        {
            throw new ServiceException("只能撤回自己创建的单据");
        }
        release(document);
        changeStatus(document, document.getStatus(), "DRAFT", userId, userName, null, null);
        mapper.insertEvent(documentId, "WITHDRAW", document.getStatus(), "DRAFT", userId, userName, "");
    }

    @Override
    @Transactional
    public void approve(Long documentId, String comment, BigDecimal expectedTotalCost, Long userId, String userName,
        String approvalRole)
    {
        JewelryDocument document = getDocument(documentId);
        ensureReviewer(document, userId);
        String pendingStatus = document.getStatus();
        if (!"PENDING_FIRST".equals(pendingStatus) && !"PENDING_SECOND".equals(pendingStatus))
        {
            throw new ServiceException("当前单据不在待审核状态");
        }
        boolean dualApproval = isDualApprovalDocument(document);
        if (dualApproval)
        {
            ensureDualApprovalRole(document, pendingStatus, userId, approvalRole);
            if ("STOCK_ADJUST".equals(document.getDocType())) validateStockAdjustmentSnapshot(document);
            if ("COST_ADJUST".equals(document.getDocType())) validateCostAdjustmentSnapshot(document);
            if (isCostChangeDocument(document)) validateCostChangeConflicts(document);
            if ("PENDING_FIRST".equals(pendingStatus))
            {
                changeStatus(document, pendingStatus, "PENDING_SECOND", userId, userName, null, 1);
                mapper.insertApproval(documentId, 1, "PASS", userId, userName, text(comment));
                mapper.insertEvent(documentId, "APPROVE_FIRST", pendingStatus, "PENDING_SECOND",
                    userId, userName, text(comment));
                return;
            }
        }
        else if ("PURCHASE_IN".equals(document.getDocType()))
        {
            validateCostChangeConflicts(document);
        }
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            refreshAssemblyCosts(document, true, true);
            if (expectedTotalCost == null)
            {
                throw new ServiceException("请先刷新并确认最新组装成本");
            }
            if (money(expectedTotalCost).compareTo(money(document.getTotalCost())) != 0)
            {
                throw new ServiceException("组装成本已变化，请刷新单据后重新确认");
            }
        }
        if ("REVERSAL".equals(document.getDocType()))
        {
            postReversal(document, userId, userName);
            if (mapper.markOriginalReversed(document.getSourceDocumentId(), userName) != 1)
                throw new ServiceException("原单状态已变化或已经红冲，请刷新后重试");
        }
        else
        {
            post(document, userId, userName);
        }
        int stage = "PENDING_FIRST".equals(pendingStatus) ? 1 : 2;
        changeStatus(document, pendingStatus, "POSTED", userId, userName, null, stage);
        mapper.insertApproval(documentId, stage, "PASS", userId, userName, text(comment));
        mapper.insertEvent(documentId, "APPROVE", pendingStatus, "POSTED", userId, userName, text(comment));
    }

    @Override
    @Transactional
    public void reject(Long documentId, String comment, Long userId, String userName, String approvalRole)
    {
        JewelryDocument document = getDocument(documentId);
        ensureReviewer(document, userId);
        if (!"PENDING_FIRST".equals(document.getStatus()) && !"PENDING_SECOND".equals(document.getStatus()))
        {
            throw new ServiceException("当前单据不在待审批状态");
        }
        if (comment == null || comment.trim().isEmpty())
        {
            throw new ServiceException("驳回原因不能为空");
        }
        if (isDualApprovalDocument(document))
            ensureDualApprovalRole(document, document.getStatus(), userId, approvalRole);
        int stage = "PENDING_FIRST".equals(document.getStatus()) ? 1 : 2;
        release(document);
        changeStatus(document, document.getStatus(), "REJECTED", userId, userName, comment, null);
        mapper.insertApproval(documentId, stage, "REJECT", userId, userName, comment);
        mapper.insertEvent(documentId, "REJECT", document.getStatus(), "REJECTED", userId, userName, comment);
    }

    private void validateDocument(JewelryDocument document)
    {
        if (document.getDocType() == null || document.getDocType().trim().isEmpty()) throw new ServiceException("请选择单据类型");
        if (!EDITABLE_DOCUMENT_TYPES.contains(document.getDocType()))
            throw new ServiceException("单据类型不正确");
        if (document.getBizDate() == null) document.setBizDate(new Date());
        if (document.getItems() == null || document.getItems().isEmpty()) throw new ServiceException("单据至少需要一行商品");
        if (("PURCHASE_IN".equals(document.getDocType()) || "SUPPLIER_RETURN".equals(document.getDocType()))
            && document.getSupplierId() == null)
            throw new ServiceException("请选择供应商");
        if ("PURCHASE_IN".equals(document.getDocType()) || "SUPPLIER_RETURN".equals(document.getDocType()))
            validateSupplierReference(document, true);
        if (("SALES_OUT".equals(document.getDocType()) || "CUSTOMER_RETURN".equals(document.getDocType()))
            && text(document.getSalesChannel()).trim().isEmpty())
            throw new ServiceException("请填写销售渠道");
        if (("SUPPLIER_RETURN".equals(document.getDocType()) || "CUSTOMER_RETURN".equals(document.getDocType()))
            && text(document.getReturnReason()).trim().isEmpty())
            throw new ServiceException("请填写退货原因");
        if ("COST_ADJUST".equals(document.getDocType()) && text(document.getReturnReason()).trim().isEmpty())
            throw new ServiceException("请填写调价原因");
        if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() == null)
            throw new ServiceException("客户退货必须关联原销售单");
        Map<Long, JewelryDocumentItem> returnInspectionSourceItems = new HashMap<Long, JewelryDocumentItem>();
        if ("RETURN_INSPECT".equals(document.getDocType()))
        {
            if (document.getSourceDocumentId() == null)
                throw new ServiceException("退货质检必须关联原客户退货单");
            JewelryDocument source = requirePostedCustomerReturn(document.getSourceDocumentId());
            for (JewelryDocumentItem sourceItem : mapper.selectDocumentItems(source.getDocumentId()))
            {
                returnInspectionSourceItems.put(sourceItem.getItemId(), sourceItem);
            }
        }
        validateRate(document.getPlatformRate(), "平台扣点率");
        validateRate(document.getCommissionRate(), "达人佣金率");
        validateRate(document.getTaxRate(), "税率");
        if ("SALES_OUT".equals(document.getDocType()))
            validateCombinedRate(money(document.getPlatformRate()).add(money(document.getCommissionRate()))
                .add(money(document.getTaxRate())));
        Set<String> itemKeys = new HashSet<String>();
        Map<Integer, Integer> salesMainCounts = new HashMap<Integer, Integer>();
        Map<Integer, Integer> salesAddonCounts = new HashMap<Integer, Integer>();
        int assemblyOutputs = 0;
        int assemblyComponents = 0;
        for (JewelryDocumentItem item : document.getItems())
        {
            if (item.getProductId() == null) throw new ServiceException("请选择商品");
            Map<String, Object> product = mapper.selectProductById(item.getProductId());
            if (product == null) throw new ServiceException("商品不存在或已删除");
            if (!"0".equals(String.valueOf(product.get("status"))))
                throw new ServiceException("商品已停用，不能继续使用");
            item.setSkuSnapshot(String.valueOf(product.get("sku")));
            item.setProductNameSnapshot(String.valueOf(product.get("productName")));
            item.setProductTypeSnapshot(textValue(product.get("productType")));
            item.setSpecificationSnapshot(textValue(product.get("specification")));
            if (item.getItemRole() == null || item.getItemRole().trim().isEmpty()) item.setItemRole("NORMAL");
            if (!"SALES_OUT".equals(document.getDocType()) && !"CUSTOMER_RETURN".equals(document.getDocType()))
            {
                item.setBundleGroupNo(null);
                item.setSaleRole("NORMAL");
                item.setPricingMode("SEPARATE");
            }
            item.setQty(nonNegative(item.getQty()));
            if ("SALES_OUT".equals(document.getDocType()))
            {
                String itemKey = normalizeSalesBundleItem(item, product, salesMainCounts, salesAddonCounts);
                if (!itemKeys.add(itemKey))
                    throw new ServiceException("同一销售组合中不能重复选择同一商品");
            }
            else if (!"CUSTOMER_RETURN".equals(document.getDocType())
                && !"RETURN_INSPECT".equals(document.getDocType())
                && !itemKeys.add(String.valueOf(item.getProductId())))
            {
                throw new ServiceException("同一商品不能在一张单据中重复出现");
            }
            if ("ASSEMBLY".equals(document.getDocType()))
            {
                Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
                if (stock == null) throw new ServiceException("商品库存记录不存在");
                if ("OUTPUT".equals(item.getItemRole()))
                {
                    assemblyOutputs++;
                    if (!"FINISHED".equals(String.valueOf(product.get("productType"))))
                        throw new ServiceException("组装产出必须选择成品商品");
                }
                else if ("COMPONENT".equals(item.getItemRole()))
                {
                    assemblyComponents++;
                    if (!"PART".equals(String.valueOf(product.get("productType"))))
                        throw new ServiceException("组装投入只能选择散件商品");
                    item.setUnitCost(decimal(stock.get("avgCost")));
                }
                else
                {
                    throw new ServiceException("组装明细角色不正确");
                }
            }
            if ("SALES_OUT".equals(document.getDocType()) || "SUPPLIER_RETURN".equals(document.getDocType()))
            {
                Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
                if (stock == null) throw new ServiceException("商品库存记录不存在");
                item.setUnitCost(decimal(stock.get("avgCost")));
                if ("SUPPLIER_RETURN".equals(document.getDocType()) && money(item.getUnitPrice()).signum() <= 0)
                    throw new ServiceException("供应商退货必须填写实际退货单价");
            }
            else if ("CUSTOMER_RETURN".equals(document.getDocType()))
            {
                Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
                if (stock == null) throw new ServiceException("商品库存记录不存在");
                item.setUnitCost(decimal(stock.get("avgCost")));
                if (document.getSourceDocumentId() != null)
                {
                    JewelryDocument source = requireDocument(document.getSourceDocumentId());
                    if (!"SALES_OUT".equals(source.getDocType()) || !"POSTED".equals(source.getStatus()))
                        throw new ServiceException("关联的原单必须是已入账销售出库单");
                    List<JewelryDocumentItem> sourceItems = mapper.selectDocumentItems(source.getDocumentId());
                    JewelryDocumentItem sourceItem = null;
                    for (JewelryDocumentItem candidate : sourceItems)
                    {
                        if (item.getSourceItemId() != null && item.getSourceItemId().equals(candidate.getItemId()))
                        {
                            sourceItem = candidate;
                            break;
                        }
                        if (item.getSourceItemId() == null && item.getProductId().equals(candidate.getProductId()))
                        {
                            sourceItem = candidate;
                            break;
                        }
                    }
                    if (sourceItem == null || !item.getProductId().equals(sourceItem.getProductId()))
                        throw new ServiceException("原销售单中不存在对应的商品明细");
                    if (!itemKeys.add("SOURCE:" + sourceItem.getItemId()))
                        throw new ServiceException("同一原销售明细不能重复退货");
                    int returnedQty = mapper.selectReturnedQtyBySourceItem(sourceItem.getItemId(),
                        document.getDocumentId());
                    if (returnedQty + item.getQty() > nonNegative(sourceItem.getQty()))
                        throw new ServiceException(item.getProductNameSnapshot() + "累计退货数量不能超过原销售数量"
                            + nonNegative(sourceItem.getQty()) + "件");
                    item.setSourceItemId(sourceItem.getItemId());
                    item.setUnitPrice(money(sourceItem.getUnitPrice()));
                    item.setUnitCost(money(sourceItem.getUnitCost()));
                    item.setPackFee(money(sourceItem.getPackFee()));
                    item.setShipFee(money(sourceItem.getShipFee()));
                    item.setCertFee(money(sourceItem.getCertFee()));
                    item.setBundleGroupNo(sourceItem.getBundleGroupNo());
                    item.setSaleRole(normalizedSaleRole(sourceItem.getSaleRole()));
                    item.setPricingMode(normalizedPricingMode(sourceItem.getPricingMode()));
                    item.setProductTypeSnapshot(sourceItem.getProductTypeSnapshot());
                    item.setSpecificationSnapshot(sourceItem.getSpecificationSnapshot());
                    document.setSalesChannel(source.getSalesChannel());
                    document.setInfluencerName(source.getInfluencerName());
                    document.setPlatformRate(money(source.getPlatformRate()));
                    document.setCommissionRate(money(source.getCommissionRate()));
                    document.setTaxRate(money(source.getTaxRate()));
                }
                else if (money(item.getUnitPrice()).signum() <= 0)
                    throw new ServiceException("未关联原销售单时必须填写实际退款单价");
            }
            else if ("RETURN_INSPECT".equals(document.getDocType()))
            {
                JewelryDocumentItem sourceItem = returnInspectionSourceItems.get(item.getSourceItemId());
                if (sourceItem == null || !item.getProductId().equals(sourceItem.getProductId()))
                    throw new ServiceException("质检明细必须来自所关联的客户退货单");
                if (!itemKeys.add("SOURCE:" + sourceItem.getItemId()))
                    throw new ServiceException("同一退货明细不能重复质检");
                int handledQty = nonNegative(item.getGoodQty()) + nonNegative(item.getDefectQty());
                int inspectedQty = mapper.selectInspectedQtyBySourceItem(sourceItem.getItemId(),
                    document.getDocumentId());
                int remainingQty = nonNegative(sourceItem.getQty()) - inspectedQty;
                if (handledQty <= 0)
                    throw new ServiceException("退货质检的良品数和次品数不能同时为0");
                if (handledQty > remainingQty)
                    throw new ServiceException(item.getProductNameSnapshot() + "本次质检数量不能超过原退货单剩余待检数量"
                        + Math.max(remainingQty, 0) + "件");
                item.setQty(handledQty);
                item.setUnitCost(money(sourceItem.getUnitCost()));
                item.setProductTypeSnapshot(sourceItem.getProductTypeSnapshot());
                item.setSpecificationSnapshot(sourceItem.getSpecificationSnapshot());
            }
            validateNonNegative(item.getUnitPrice(), "商品单价");
            validateNonNegative(item.getUnitCost(), "商品成本");
            validateNonNegative(item.getPackFee(), "包装费");
            validateNonNegative(item.getShipFee(), "物流费");
            validateNonNegative(item.getCertFee(), "鉴定费");
            validateNonNegative(item.getOtherFee1(), "其他1");
            validateNonNegative(item.getOtherFee2(), "其他2");
            validateNonNegative(item.getOtherFee3(), "其他3");
            item.setGoodQty(nonNegative(item.getGoodQty()));
            item.setDefectQty(nonNegative(item.getDefectQty()));
            item.setAdjustmentQty(item.getAdjustmentQty() == null ? 0 : item.getAdjustmentQty());
            if ("STOCK_ADJUST".equals(document.getDocType()))
            {
                if (item.getCountedQty() == null || item.getCountedQty() < 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 的实盘库存不能小于0");
                if (item.getAdjustmentQty() == 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 没有盘点差异，无需提交");
                if (item.getAdjustmentQty() > 0 && money(item.getUnitCost()).signum() <= 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 盘盈时必须填写核定单位成本");
                if (text(item.getLineReason()).isEmpty())
                    throw new ServiceException(item.getProductNameSnapshot() + " 必须填写调整原因");
            }
            if ("COST_ADJUST".equals(document.getDocType()))
            {
                if (item.getSystemQty() == null || item.getSystemQty() <= 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 当前库存为0，不能调整库存成本");
                if (money(item.getUnitPrice()).compareTo(money(item.getUnitCost())) == 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 调整后平均成本与当前平均成本相同");
            }
            if (!"RETURN_INSPECT".equals(document.getDocType()) && !"STOCK_ADJUST".equals(document.getDocType())
                && item.getQty() <= 0) throw new ServiceException("商品数量必须大于0");
        }
        if ("SALES_OUT".equals(document.getDocType()))
            validateSalesBundleGroups(salesMainCounts, salesAddonCounts);
        if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getActualRefundAmount() != null)
            validateNonNegative(document.getActualRefundAmount(), "实际退款总额");
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            if (assemblyOutputs != 1) throw new ServiceException("组装单必须且只能有一个成品产出");
            if (assemblyComponents < 1) throw new ServiceException("组装单至少需要一个散件投入");
            if (money(document.getLaborFee()).signum() < 0 || money(document.getProcessingFee()).signum() < 0
                || money(document.getOtherFee()).signum() < 0)
                throw new ServiceException("组装费用不能小于0");
        }
    }

    private String normalizeSalesBundleItem(JewelryDocumentItem item, Map<String, Object> product,
        Map<Integer, Integer> mainCounts, Map<Integer, Integer> addonCounts)
    {
        String role = normalizedSaleRole(item.getSaleRole());
        String pricingMode = normalizedPricingMode(item.getPricingMode());
        Integer groupNo = item.getBundleGroupNo();
        String productType = textValue(product.get("productType"));
        if (!SALES_ROLES.contains(role)) throw new ServiceException("销售商品角色不正确");
        if (!SALES_PRICING_MODES.contains(pricingMode)) throw new ServiceException("搭售计价方式不正确");
        if ("MAIN".equals(role))
        {
            if (groupNo == null || groupNo <= 0) throw new ServiceException("销售组合主商品缺少组合编号");
            if (!"FINISHED".equals(productType)) throw new ServiceException("销售组合主商品必须是成品商品");
            pricingMode = "SEPARATE";
            mainCounts.put(groupNo, mainCounts.getOrDefault(groupNo, 0) + 1);
        }
        else if ("ADDON".equals(role))
        {
            if (groupNo == null || groupNo <= 0) throw new ServiceException("搭售商品缺少销售组合编号");
            if (!SALES_ADDON_PRODUCT_TYPES.contains(productType))
                throw new ServiceException("搭售商品不能选择成品商品");
            addonCounts.put(groupNo, addonCounts.getOrDefault(groupNo, 0) + 1);
            if ("ACCESSORY".equals(productType))
            {
                pricingMode = "INCLUDED";
                item.setUnitPrice(ZERO);
                item.setPackFee(ZERO);
                item.setShipFee(ZERO);
                item.setCertFee(ZERO);
                item.setOtherFee1(ZERO);
                item.setOtherFee2(ZERO);
                item.setOtherFee3(ZERO);
            }
            else if ("INCLUDED".equals(pricingMode)) item.setUnitPrice(ZERO);
        }
        else
        {
            role = "NORMAL";
            pricingMode = "SEPARATE";
            groupNo = null;
        }
        item.setSaleRole(role);
        item.setPricingMode(pricingMode);
        item.setBundleGroupNo(groupNo);
        return item.getProductId() + ":" + (groupNo == null ? "NORMAL" : groupNo);
    }

    private void validateSalesBundleGroups(Map<Integer, Integer> mainCounts, Map<Integer, Integer> addonCounts)
    {
        Set<Integer> groupNumbers = new HashSet<Integer>();
        groupNumbers.addAll(mainCounts.keySet());
        groupNumbers.addAll(addonCounts.keySet());
        for (Integer groupNo : groupNumbers)
        {
            if (mainCounts.getOrDefault(groupNo, 0) != 1)
                throw new ServiceException("销售组合" + groupNo + "必须且只能有一个成品主商品");
            if (addonCounts.getOrDefault(groupNo, 0) < 1)
                throw new ServiceException("销售组合" + groupNo + "至少需要一个搭售商品");
        }
    }

    private String normalizedSaleRole(String value)
    {
        String role = text(value).trim().toUpperCase();
        return role.isEmpty() ? "NORMAL" : role;
    }

    private String normalizedPricingMode(String value)
    {
        String mode = text(value).trim().toUpperCase();
        return mode.isEmpty() ? "SEPARATE" : mode;
    }

    private void refreshAssemblyCosts(JewelryDocument document, boolean validateState, boolean lockStock)
    {
        int outputs = 0;
        int components = 0;
        for (JewelryDocumentItem item : document.getItems())
        {
            Map<String, Object> product = mapper.selectProductById(item.getProductId());
            if (product == null)
            {
                if (validateState) throw new ServiceException("组装商品不存在或已删除");
                continue;
            }
            String role = text(item.getItemRole());
            if ("OUTPUT".equals(role))
            {
                outputs++;
                if (validateState && !"FINISHED".equals(String.valueOf(product.get("productType"))))
                    throw new ServiceException(item.getProductNameSnapshot() + " 已不再是成品，不能完成组装");
            }
            else if ("COMPONENT".equals(role))
            {
                components++;
                if (validateState && !"PART".equals(String.valueOf(product.get("productType"))))
                    throw new ServiceException(item.getProductNameSnapshot() + " 已不再是散件，不能完成组装");
                Map<String, Object> costSource = lockStock ? mapper.selectStockForUpdate(item.getProductId()) : product;
                if (costSource == null)
                    throw new ServiceException(item.getProductNameSnapshot() + " 的库存记录不存在");
                item.setUnitCost(decimal(costSource.get("avgCost")));
            }
            else if (validateState)
            {
                throw new ServiceException("组装明细角色不正确");
            }
            if (validateState && !"0".equals(String.valueOf(product.get("status"))))
                throw new ServiceException(item.getProductNameSnapshot() + " 已停用，不能完成组装");
        }
        if (validateState && (outputs != 1 || components < 1))
            throw new ServiceException("组装单必须有一个成品产出和至少一个散件投入");
        calculateAssembly(document);
    }

    private void calculateDocument(JewelryDocument document)
    {
        document.setLaborFee(money(document.getLaborFee()));
        document.setProcessingFee(money(document.getProcessingFee()));
        document.setOtherFee(money(document.getOtherFee()));
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            calculateAssembly(document);
            return;
        }
        if ("COST_ADJUST".equals(document.getDocType()))
        {
            calculateCostAdjustment(document);
            return;
        }
        int totalQty = 0;
        BigDecimal totalAmount = ZERO;
        BigDecimal totalCost = ZERO;
        BigDecimal totalProfit = ZERO;
        BigDecimal platformRate = money(document.getPlatformRate());
        BigDecimal commissionRate = money(document.getCommissionRate());
        BigDecimal taxRate = money(document.getTaxRate());
        boolean customerReturn = "CUSTOMER_RETURN".equals(document.getDocType());
        Map<Integer, BigDecimal> accessoryPackagingCosts = "SALES_OUT".equals(document.getDocType())
            ? accessoryPackagingCosts(document.getItems()) : Collections.<Integer, BigDecimal>emptyMap();
        if (customerReturn)
        {
            platformRate = ZERO;
            commissionRate = ZERO;
            taxRate = ZERO;
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            int qty = effectiveQty(document.getDocType(), item);
            boolean purchase = "PURCHASE_IN".equals(document.getDocType());
            BigDecimal price = purchase
                ? money(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP)
                : money(item.getUnitPrice());
            BigDecimal cost = money(item.getUnitCost());
            BigDecimal packFee = money(item.getPackFee());
            BigDecimal financialPackFee = packFee;
            BigDecimal shipFee = money(item.getShipFee());
            BigDecimal certFee = money(item.getCertFee());
            BigDecimal otherFee1 = money(item.getOtherFee1());
            BigDecimal otherFee2 = money(item.getOtherFee2());
            BigDecimal otherFee3 = money(item.getOtherFee3());
            if ("SALES_OUT".equals(document.getDocType()) && "MAIN".equals(normalizedSaleRole(item.getSaleRole())))
            {
                BigDecimal accessoryCost = accessoryPackagingCosts.getOrDefault(item.getBundleGroupNo(), ZERO);
                BigDecimal manualTotal = packFee.multiply(BigDecimal.valueOf(qty));
                BigDecimal additionalTotal = manualTotal.subtract(accessoryCost).max(ZERO);
                financialPackFee = qty <= 0 ? ZERO
                    : additionalTotal.divide(BigDecimal.valueOf(qty), 6, RoundingMode.HALF_UP);
            }
            else if ("SALES_OUT".equals(document.getDocType()) && isAccessoryPackagingItem(item))
            {
                financialPackFee = ZERO;
            }
            if (customerReturn)
            {
                packFee = ZERO;
                financialPackFee = ZERO;
            }
            BigDecimal fees = "SALES_OUT".equals(document.getDocType())
                ? financialPackFee.add(shipFee).add(certFee).add(otherFee1).add(otherFee2).add(otherFee3)
                : customerReturn ? shipFee.multiply(BigDecimal.valueOf(2)).add(certFee) : ZERO;
            if (!"SALES_OUT".equals(document.getDocType()))
            {
                otherFee1 = ZERO;
                otherFee2 = ZERO;
                otherFee3 = ZERO;
            }
            if ("PURCHASE_IN".equals(document.getDocType()))
            {
                cost = price;
            }
            BigDecimal grossAmount = price.multiply(BigDecimal.valueOf(qty));
            BigDecimal productCostAmount = cost.multiply(BigDecimal.valueOf(qty));
            BigDecimal feeAmount = fees.multiply(BigDecimal.valueOf(qty));
            BigDecimal grossCost = productCostAmount.add(feeAmount);
            BigDecimal deductions = customerReturn ? ZERO
                : grossAmount.multiply(platformRate.add(commissionRate).add(taxRate));
            BigDecimal amount = grossAmount;
            BigDecimal costAmount = grossCost;
            BigDecimal profit = grossAmount.subtract(grossCost).subtract(deductions);
            if ("SALES_OUT".equals(document.getDocType()))
            {
                Map<String, BigDecimal> line = calculateSalesLine(price, cost, fees,
                    platformRate.add(commissionRate).add(taxRate));
                deductions = line.get("deductions").multiply(BigDecimal.valueOf(qty));
                profit = line.get("profit").multiply(BigDecimal.valueOf(qty));
            }
            if (customerReturn)
            {
                amount = grossAmount.negate();
                costAmount = productCostAmount.negate().add(feeAmount);
                profit = amount.subtract(costAmount);
            }
            else if ("SUPPLIER_RETURN".equals(document.getDocType()))
            {
                amount = grossAmount.negate();
                costAmount = cost.multiply(BigDecimal.valueOf(qty)).negate();
                profit = ZERO;
            }
            item.setUnitPrice(price); item.setUnitCost(cost); item.setPackFee(packFee);
            item.setShipFee(shipFee); item.setCertFee(certFee);
            item.setOtherFee1(otherFee1); item.setOtherFee2(otherFee2); item.setOtherFee3(otherFee3);
            int lineAmountScale = purchase ? 4 : 2;
            item.setAmount(amount.setScale(lineAmountScale, RoundingMode.HALF_UP));
            item.setCostAmount(costAmount.setScale(lineAmountScale, RoundingMode.HALF_UP));
            item.setProfitAmount(profit.setScale(2, RoundingMode.HALF_UP));
            item.setProfitRate(grossAmount.signum() == 0 ? ZERO :
                profit.divide(grossAmount, 6, RoundingMode.HALF_UP));
            totalQty += qty;
            totalAmount = totalAmount.add(amount);
            totalCost = totalCost.add(costAmount);
            totalProfit = totalProfit.add(profit);
        }
        boolean refundNeedsReview = false;
        if (customerReturn)
        {
            BigDecimal expectedRefund = totalAmount.negate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualRefund = document.getActualRefundAmount() == null
                ? expectedRefund : money(document.getActualRefundAmount()).setScale(2, RoundingMode.HALF_UP);
            document.setActualRefundAmount(actualRefund);
            allocateCustomerReturnRefund(document.getItems(), actualRefund);
            totalAmount = ZERO;
            totalCost = ZERO;
            totalProfit = ZERO;
            for (JewelryDocumentItem item : document.getItems())
            {
                totalAmount = totalAmount.add(money(item.getAmount()));
                totalCost = totalCost.add(money(item.getCostAmount()));
                totalProfit = totalProfit.add(money(item.getProfitAmount()));
            }
            refundNeedsReview = actualRefund.compareTo(expectedRefund) != 0;
        }
        document.setPlatformRate(platformRate); document.setCommissionRate(commissionRate); document.setTaxRate(taxRate);
        int totalAmountScale = "PURCHASE_IN".equals(document.getDocType()) ? 4 : 2;
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount.setScale(totalAmountScale, RoundingMode.HALF_UP));
        document.setTotalCost(totalCost.setScale(totalAmountScale, RoundingMode.HALF_UP));
        document.setTotalProfit(totalProfit.setScale(2, RoundingMode.HALF_UP));
        if ("SALES_OUT".equals(document.getDocType()) && totalProfit.signum() < 0)
            document.setRiskStatus("LOSS");
        else if ("CUSTOMER_RETURN".equals(document.getDocType())
            && (document.getSourceDocumentId() == null || refundNeedsReview))
            document.setRiskStatus("REVIEW");
        else
            document.setRiskStatus("NORMAL");
    }

    private Map<Integer, BigDecimal> accessoryPackagingCosts(List<JewelryDocumentItem> items)
    {
        Map<Integer, BigDecimal> costs = new HashMap<Integer, BigDecimal>();
        for (JewelryDocumentItem item : items)
        {
            if (!isAccessoryPackagingItem(item) || item.getBundleGroupNo() == null) continue;
            BigDecimal amount = money(item.getUnitCost())
                .multiply(BigDecimal.valueOf(nonNegative(item.getQty())));
            costs.put(item.getBundleGroupNo(), costs.getOrDefault(item.getBundleGroupNo(), ZERO).add(amount));
        }
        return costs;
    }

    private boolean isAccessoryPackagingItem(JewelryDocumentItem item)
    {
        return "ADDON".equals(normalizedSaleRole(item.getSaleRole()))
            && "ACCESSORY".equals(text(item.getProductTypeSnapshot()).trim().toUpperCase());
    }

    private void validateAccessoryPackagingCoverage(JewelryDocument document)
    {
        Map<Integer, BigDecimal> accessoryCosts = accessoryPackagingCosts(document.getItems());
        if (accessoryCosts.isEmpty()) return;
        for (JewelryDocumentItem item : document.getItems())
        {
            if (!"MAIN".equals(normalizedSaleRole(item.getSaleRole())) || item.getBundleGroupNo() == null) continue;
            BigDecimal accessoryCost = accessoryCosts.getOrDefault(item.getBundleGroupNo(), ZERO);
            BigDecimal manualTotal = money(item.getPackFee())
                .multiply(BigDecimal.valueOf(nonNegative(item.getQty())));
            if (accessoryCost.compareTo(manualTotal) > 0)
            {
                BigDecimal shortage = accessoryCost.subtract(manualTotal);
                throw new ServiceException("销售组合" + item.getBundleGroupNo() + "配件耗材成本￥"
                    + accessoryCost.setScale(2, RoundingMode.HALF_UP) + "，高于手填包装费￥"
                    + manualTotal.setScale(2, RoundingMode.HALF_UP) + "，还差￥"
                    + shortage.setScale(2, RoundingMode.HALF_UP) + "，请调整包装费后再提交");
            }
        }
    }

    private void allocateCustomerReturnRefund(List<JewelryDocumentItem> items, BigDecimal refund)
    {
        BigDecimal totalWeight = ZERO;
        int lastWeightedIndex = -1;
        for (int i = 0; i < items.size(); i++)
        {
            JewelryDocumentItem item = items.get(i);
            BigDecimal weight = money(item.getUnitPrice()).multiply(BigDecimal.valueOf(nonNegative(item.getQty())));
            if (weight.signum() > 0)
            {
                totalWeight = totalWeight.add(weight);
                lastWeightedIndex = i;
            }
        }
        BigDecimal remaining = refund.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < items.size(); i++)
        {
            JewelryDocumentItem item = items.get(i);
            BigDecimal weight = money(item.getUnitPrice()).multiply(BigDecimal.valueOf(nonNegative(item.getQty())));
            BigDecimal allocated = ZERO.setScale(2);
            if (totalWeight.signum() == 0)
            {
                if (i == 0) allocated = remaining;
            }
            else if (weight.signum() > 0)
            {
                allocated = i == lastWeightedIndex ? remaining
                    : refund.multiply(weight).divide(totalWeight, 2, RoundingMode.HALF_UP);
            }
            if (allocated.signum() > 0) remaining = remaining.subtract(allocated);
            BigDecimal amount = allocated.negate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = amount.subtract(money(item.getCostAmount())).setScale(2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            item.setProfitAmount(profit);
            item.setProfitRate(allocated.signum() == 0 ? ZERO
                : profit.divide(allocated, 6, RoundingMode.HALF_UP));
        }
    }

    private void reserve(JewelryDocument document)
    {
        if ("REVERSAL".equals(document.getDocType()))
        {
            reserveReversal(document);
            return;
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            int rows = 1;
            if (isOutbound(document.getDocType()))
                rows = mapper.reserveOutbound(item.getProductId(), item.getQty());
            else if ("ASSEMBLY".equals(document.getDocType()) && "COMPONENT".equals(item.getItemRole()))
                rows = mapper.reserveOutbound(item.getProductId(), item.getQty());
            else if ("STOCK_ADJUST".equals(document.getDocType()) && item.getAdjustmentQty() < 0)
                rows = mapper.reserveOutbound(item.getProductId(), -item.getAdjustmentQty());
            else if ("RETURN_INSPECT".equals(document.getDocType()))
                rows = mapper.reserveInspection(item.getProductId(), item.getGoodQty() + item.getDefectQty());
            if (rows != 1) throw new ServiceException(item.getProductNameSnapshot() + " 可用库存不足");
        }
    }

    private void release(JewelryDocument document)
    {
        if ("REVERSAL".equals(document.getDocType()))
        {
            releaseReversal(document);
            return;
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            int rows = 1;
            if (isOutbound(document.getDocType()))
                rows = mapper.releaseOutbound(item.getProductId(), item.getQty());
            else if ("ASSEMBLY".equals(document.getDocType()) && "COMPONENT".equals(item.getItemRole()))
                rows = mapper.releaseOutbound(item.getProductId(), item.getQty());
            else if ("STOCK_ADJUST".equals(document.getDocType()) && item.getAdjustmentQty() < 0)
                rows = mapper.releaseOutbound(item.getProductId(), -item.getAdjustmentQty());
            else if ("RETURN_INSPECT".equals(document.getDocType()))
                rows = mapper.releaseInspection(item.getProductId(), item.getGoodQty() + item.getDefectQty());
            if (rows != 1) throw new ServiceException("库存冻结数据异常，请联系管理员");
        }
    }

    private void reserveReversal(JewelryDocument reversal)
    {
        JewelryDocument source = requireReversalSource(reversal);
        for (JewelryDocumentItem item : reversal.getItems())
        {
            int rows = 1;
            if ("PURCHASE_IN".equals(source.getDocType()))
                rows = mapper.reserveOutbound(item.getProductId(), item.getQty());
            else if ("CUSTOMER_RETURN".equals(source.getDocType()))
                rows = mapper.reserveInspection(item.getProductId(), item.getQty());
            else if ("RETURN_INSPECT".equals(source.getDocType()))
            {
                if (item.getGoodQty() > 0 && mapper.reserveOutbound(item.getProductId(), item.getGoodQty()) != 1)
                    throw new ServiceException(item.getProductNameSnapshot() + " 可售库存不足，不能红冲质检单");
                if (item.getDefectQty() > 0 && mapper.reserveDefect(item.getProductId(), item.getDefectQty()) != 1)
                    throw new ServiceException(item.getProductNameSnapshot() + " 次品库存不足，不能红冲质检单");
            }
            else if ("STOCK_ADJUST".equals(source.getDocType()) && item.getAdjustmentQty() > 0)
                rows = mapper.reserveOutbound(item.getProductId(), item.getAdjustmentQty());
            if (rows != 1)
                throw new ServiceException(item.getProductNameSnapshot() + " 当前库存不足，不能红冲");
        }
    }

    private void releaseReversal(JewelryDocument reversal)
    {
        JewelryDocument source = requireReversalSource(reversal);
        for (JewelryDocumentItem item : reversal.getItems())
        {
            int rows = 1;
            if ("PURCHASE_IN".equals(source.getDocType()))
                rows = mapper.releaseOutbound(item.getProductId(), item.getQty());
            else if ("CUSTOMER_RETURN".equals(source.getDocType()))
                rows = mapper.releaseInspection(item.getProductId(), item.getQty());
            else if ("RETURN_INSPECT".equals(source.getDocType()))
            {
                if (item.getGoodQty() > 0 && mapper.releaseOutbound(item.getProductId(), item.getGoodQty()) != 1)
                    throw new ServiceException("可售库存红冲冻结数据异常");
                if (item.getDefectQty() > 0 && mapper.releaseDefect(item.getProductId(), item.getDefectQty()) != 1)
                    throw new ServiceException("次品库存红冲冻结数据异常");
            }
            else if ("STOCK_ADJUST".equals(source.getDocType()) && item.getAdjustmentQty() > 0)
                rows = mapper.releaseOutbound(item.getProductId(), item.getAdjustmentQty());
            if (rows != 1) throw new ServiceException("红冲冻结数据异常，请联系管理员");
        }
    }

    private void post(JewelryDocument document, Long userId, String userName)
    {
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            for (JewelryDocumentItem item : document.getItems())
            {
                if ("COMPONENT".equals(item.getItemRole()))
                {
                    Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
                    if (stock == null) throw new ServiceException("商品库存记录不存在");
                    item.setUnitCost(decimal(stock.get("avgCost")));
                }
            }
            calculateAssembly(document);
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            int before = intValue(stock.get("onHandQty"));
            int onHand = before, reserved = intValue(stock.get("reservedOutQty"));
            int inspection = intValue(stock.get("inspectionQty"));
            int inspectionReserved = intValue(stock.get("inspectionReservedQty"));
            int defect = intValue(stock.get("defectQty"));
            int defectReserved = intValue(stock.get("defectReservedQty"));
            BigDecimal beforeAvg = decimal(stock.get("avgCost"));
            BigDecimal avg = beforeAvg;
            BigDecimal inspectionCost = decimal(stock.get("inspectionCostAmount"));
            BigDecimal defectCost = decimal(stock.get("defectCostAmount"));
            int qty = item.getQty();
            if ("PURCHASE_IN".equals(document.getDocType()))
            {
                BigDecimal purchaseCost = money(item.getUnitPrice());
                BigDecimal incomingCost = purchaseCost.multiply(BigDecimal.valueOf(qty));
                BigDecimal existingCost = beforeAvg.multiply(BigDecimal.valueOf(before));
                onHand += qty;
                avg = onHand == 0 ? ZERO : existingCost.add(incomingCost).divide(BigDecimal.valueOf(onHand), 6, RoundingMode.HALF_UP);
                item.setUnitCost(purchaseCost);
                item.setCostAmount(incomingCost.setScale(4, RoundingMode.HALF_UP));
                mapper.updateDocumentItemCost(item);
            }
            else if ("SALES_OUT".equals(document.getDocType()))
            {
                onHand -= qty; reserved -= qty;
                item.setUnitCost(beforeAvg);
                item.setCostAmount(beforeAvg.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
                BigDecimal fees = money(item.getPackFee()).add(money(item.getShipFee()))
                    .add(money(item.getCertFee())).add(money(item.getOtherFee1()))
                    .add(money(item.getOtherFee2())).add(money(item.getOtherFee3()))
                    .multiply(BigDecimal.valueOf(qty));
                BigDecimal deductions = item.getAmount().multiply(
                    money(document.getPlatformRate()).add(money(document.getCommissionRate())).add(money(document.getTaxRate())));
                item.setCostAmount(item.getCostAmount().add(fees).setScale(2, RoundingMode.HALF_UP));
                item.setProfitAmount(item.getAmount().subtract(item.getCostAmount()).subtract(deductions)
                    .setScale(2, RoundingMode.HALF_UP));
                item.setProfitRate(item.getAmount().signum() == 0 ? ZERO :
                    item.getProfitAmount().divide(item.getAmount(), 6, RoundingMode.HALF_UP));
                mapper.updateDocumentItemCost(item);
            }
            else if ("SUPPLIER_RETURN".equals(document.getDocType()))
            {
                onHand -= qty; reserved -= qty;
                item.setUnitCost(beforeAvg);
                item.setCostAmount(beforeAvg.multiply(BigDecimal.valueOf(qty)).negate()
                    .setScale(2, RoundingMode.HALF_UP));
                item.setProfitAmount(ZERO.setScale(2));
                item.setProfitRate(ZERO);
                mapper.updateDocumentItemCost(item);
            }
            else if ("CUSTOMER_RETURN".equals(document.getDocType()))
            {
                inspection += qty;
                inspectionCost = inspectionCost.add(money(item.getUnitCost()).multiply(BigDecimal.valueOf(qty)));
            }
            else if ("RETURN_INSPECT".equals(document.getDocType()))
            {
                int processed = item.getGoodQty() + item.getDefectQty();
                if (inspection < processed || inspectionReserved < processed) throw new ServiceException("待检库存不足");
                BigDecimal inspectUnitCost = inspection == 0 ? ZERO :
                    inspectionCost.divide(BigDecimal.valueOf(inspection), 6, RoundingMode.HALF_UP);
                inspection -= processed; inspectionReserved -= processed;
                inspectionCost = inspectionCost.subtract(inspectUnitCost.multiply(BigDecimal.valueOf(processed))).max(ZERO);
                if (item.getGoodQty() > 0)
                {
                    BigDecimal existingCost = avg.multiply(BigDecimal.valueOf(onHand));
                    onHand += item.getGoodQty();
                    avg = existingCost.add(inspectUnitCost.multiply(BigDecimal.valueOf(item.getGoodQty())))
                        .divide(BigDecimal.valueOf(onHand), 6, RoundingMode.HALF_UP);
                }
                defect += item.getDefectQty();
                defectCost = defectCost.add(inspectUnitCost.multiply(BigDecimal.valueOf(item.getDefectQty())));
                item.setUnitCost(inspectUnitCost);
                item.setCostAmount(inspectUnitCost.multiply(BigDecimal.valueOf(processed)).setScale(2, RoundingMode.HALF_UP));
                item.setProfitAmount(ZERO.setScale(2));
                item.setProfitRate(ZERO);
                mapper.updateDocumentItemCost(item);
            }
            else if ("STOCK_ADJUST".equals(document.getDocType()))
            {
                int adjustment = item.getAdjustmentQty();
                if (item.getSystemQty() == null || before != item.getSystemQty())
                    throw new ServiceException(item.getProductNameSnapshot()
                        + " 的账面库存已变化，请撤回或驳回后重新盘点");
                onHand += adjustment;
                if (adjustment < 0) reserved -= -adjustment;
                if (onHand < 0) throw new ServiceException("调整后库存不能为负数");
                if (adjustment < 0)
                {
                    item.setUnitCost(beforeAvg);
                    item.setCostAmount(beforeAvg.multiply(BigDecimal.valueOf(-adjustment)).setScale(2, RoundingMode.HALF_UP));
                    mapper.updateDocumentItemCost(item);
                }
                if (adjustment > 0 && money(item.getUnitCost()).signum() > 0)
                {
                    BigDecimal existingCost = avg.multiply(BigDecimal.valueOf(before));
                    avg = existingCost.add(money(item.getUnitCost()).multiply(BigDecimal.valueOf(adjustment)))
                        .divide(BigDecimal.valueOf(onHand), 6, RoundingMode.HALF_UP);
                }
            }
            else if ("COST_ADJUST".equals(document.getDocType()))
            {
                if (before <= 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 当前库存为0，不能调整库存成本");
                if (beforeAvg.compareTo(money(item.getUnitCost())) != 0)
                    throw new ServiceException(item.getProductNameSnapshot()
                        + " 的平均成本已变化，请驳回后由制单员重新编辑调价单");
                avg = money(item.getUnitPrice());
                item.setQty(before);
                calculateCostAdjustmentItem(item, before);
                mapper.updateCostAdjustmentPostedItem(item);
            }
            else if ("ASSEMBLY".equals(document.getDocType()))
            {
                if ("COMPONENT".equals(item.getItemRole()))
                {
                    if (before < qty || reserved < qty)
                        throw new ServiceException(item.getProductNameSnapshot() + " 散件库存不足");
                    onHand -= qty;
                    reserved -= qty;
                    item.setUnitCost(beforeAvg);
                    item.setCostAmount(beforeAvg.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
                }
                else if ("OUTPUT".equals(item.getItemRole()))
                {
                    BigDecimal incomingCost = money(item.getUnitCost()).multiply(BigDecimal.valueOf(qty));
                    avg = weightedAverage(before, beforeAvg, qty, incomingCost);
                    onHand += qty;
                    item.setCostAmount(incomingCost.setScale(2, RoundingMode.HALF_UP));
                }
                mapper.updateDocumentItemCost(item);
            }
            persistStockChange(document, item, stock, onHand, reserved, inspection, inspectionReserved,
                defect, defectReserved, avg, inspectionCost, defectCost,
                "ASSEMBLY".equals(document.getDocType())
                    ? ("OUTPUT".equals(item.getItemRole()) ? "ASSEMBLY_OUTPUT" : "ASSEMBLY_CONSUME")
                    : document.getDocType(),
                userId, userName);
        }
        if ("SALES_OUT".equals(document.getDocType()))
        {
            calculateDocument(document);
            validateAccessoryPackagingCoverage(document);
            for (JewelryDocumentItem item : document.getItems()) mapper.updateDocumentItemCost(item);
            mapper.updateDocumentFinancials(document);
        }
        else
        {
            refreshDocumentFinancials(document);
        }
    }

    private void refreshDocumentFinancials(JewelryDocument document)
    {
        if ("ASSEMBLY".equals(document.getDocType()))
        {
            calculateAssembly(document);
            mapper.updateDocumentFinancials(document);
            for (JewelryDocumentItem item : document.getItems()) mapper.updateDocumentItemCost(item);
            return;
        }
        int totalQty = 0;
        BigDecimal totalAmount = ZERO;
        BigDecimal totalCost = ZERO;
        BigDecimal totalProfit = ZERO;
        for (JewelryDocumentItem item : document.getItems())
        {
            totalQty += effectiveQty(document.getDocType(), item);
            totalAmount = totalAmount.add(money(item.getAmount()));
            totalCost = totalCost.add(money(item.getCostAmount()));
            totalProfit = totalProfit.add(money(item.getProfitAmount()));
        }
        document.setTotalQty(totalQty);
        int amountScale = "PURCHASE_IN".equals(document.getDocType()) ? 4 : 2;
        document.setTotalAmount(totalAmount.setScale(amountScale, RoundingMode.HALF_UP));
        document.setTotalCost(totalCost.setScale(amountScale, RoundingMode.HALF_UP));
        document.setTotalProfit(totalProfit.setScale(2, RoundingMode.HALF_UP));
        if ("SALES_OUT".equals(document.getDocType()) && totalProfit.signum() < 0)
            document.setRiskStatus("LOSS");
        else if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() == null)
            document.setRiskStatus("REVIEW");
        else
            document.setRiskStatus("NORMAL");
        mapper.updateDocumentFinancials(document);
    }

    private void calculateAssembly(JewelryDocument document)
    {
        BigDecimal componentCost = ZERO;
        JewelryDocumentItem output = null;
        for (JewelryDocumentItem item : document.getItems())
        {
            if ("OUTPUT".equals(item.getItemRole()))
            {
                output = item;
                continue;
            }
            BigDecimal cost = money(item.getUnitCost());
            item.setUnitPrice(ZERO);
            clearNonSalesFees(item);
            item.setAmount(ZERO.setScale(2));
            item.setCostAmount(cost.multiply(BigDecimal.valueOf(nonNegative(item.getQty())))
                .setScale(2, RoundingMode.HALF_UP));
            item.setProfitAmount(ZERO.setScale(2));
            item.setProfitRate(ZERO);
            componentCost = componentCost.add(item.getCostAmount());
        }
        if (output == null || nonNegative(output.getQty()) <= 0)
            throw new ServiceException("组装单缺少有效成品产出");
        BigDecimal fees = money(document.getLaborFee()).add(money(document.getProcessingFee()))
            .add(money(document.getOtherFee()));
        BigDecimal total = componentCost.add(fees).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitCost = total.divide(BigDecimal.valueOf(output.getQty()), 6, RoundingMode.HALF_UP);
        output.setUnitPrice(ZERO);
        output.setUnitCost(unitCost);
        clearNonSalesFees(output);
        output.setAmount(ZERO.setScale(2));
        output.setCostAmount(total);
        output.setProfitAmount(ZERO.setScale(2));
        output.setProfitRate(ZERO);
        document.setTotalQty(output.getQty());
        document.setTotalAmount(ZERO.setScale(2));
        document.setTotalCost(total);
        document.setTotalProfit(ZERO.setScale(2));
        document.setRiskStatus("NORMAL");
    }

    private void clearNonSalesFees(JewelryDocumentItem item)
    {
        item.setPackFee(ZERO);
        item.setShipFee(ZERO);
        item.setCertFee(ZERO);
        item.setOtherFee1(ZERO);
        item.setOtherFee2(ZERO);
        item.setOtherFee3(ZERO);
    }

    private void postReversal(JewelryDocument reversal, Long userId, String userName)
    {
        JewelryDocument source = requireReversalSource(reversal);
        for (JewelryDocumentItem item : reversal.getItems())
        {
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            int before = intValue(stock.get("onHandQty"));
            int onHand = before, reserved = intValue(stock.get("reservedOutQty"));
            int inspection = intValue(stock.get("inspectionQty"));
            int inspectionReserved = intValue(stock.get("inspectionReservedQty"));
            int defect = intValue(stock.get("defectQty"));
            int defectReserved = intValue(stock.get("defectReservedQty"));
            BigDecimal beforeAvg = decimal(stock.get("avgCost"));
            BigDecimal avg = beforeAvg;
            BigDecimal inspectionCost = decimal(stock.get("inspectionCostAmount"));
            BigDecimal defectCost = decimal(stock.get("defectCostAmount"));
            BigDecimal originalUnitCost = money(item.getUnitCost());
            String sourceType = source.getDocType();

            if ("PURCHASE_IN".equals(sourceType))
            {
                int qty = item.getQty();
                onHand -= qty;
                reserved -= qty;
                avg = averageAfterCostRemoval(before, beforeAvg, qty, originalUnitCost,
                    item.getProductNameSnapshot());
            }
            else if ("SALES_OUT".equals(sourceType) || "SUPPLIER_RETURN".equals(sourceType))
            {
                int qty = item.getQty();
                BigDecimal restoredCost = originalUnitCost.multiply(BigDecimal.valueOf(qty));
                onHand += qty;
                avg = weightedAverage(before, beforeAvg, qty, restoredCost);
            }
            else if ("CUSTOMER_RETURN".equals(sourceType))
            {
                int qty = item.getQty();
                BigDecimal removeCost = originalUnitCost.multiply(BigDecimal.valueOf(qty));
                if (inspection < qty || inspectionReserved < qty || inspectionCost.compareTo(removeCost) < 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 待检库存已被处理，不能红冲");
                inspection -= qty;
                inspectionReserved -= qty;
                inspectionCost = inspectionCost.subtract(removeCost).max(ZERO);
            }
            else if ("RETURN_INSPECT".equals(sourceType))
            {
                int good = item.getGoodQty();
                int bad = item.getDefectQty();
                int processed = good + bad;
                BigDecimal goodCost = originalUnitCost.multiply(BigDecimal.valueOf(good));
                BigDecimal badCost = originalUnitCost.multiply(BigDecimal.valueOf(bad));
                if (onHand < good || reserved < good || defect < bad || defectReserved < bad
                    || defectCost.compareTo(badCost) < 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 质检后库存已不足，不能红冲");
                onHand -= good;
                reserved -= good;
                avg = averageAfterCostRemoval(before, beforeAvg, good, originalUnitCost,
                    item.getProductNameSnapshot());
                defect -= bad;
                defectReserved -= bad;
                defectCost = defectCost.subtract(badCost).max(ZERO);
                inspection += processed;
                inspectionCost = inspectionCost.add(goodCost).add(badCost);
            }
            else if ("STOCK_ADJUST".equals(sourceType))
            {
                int adjustment = item.getAdjustmentQty();
                if (adjustment > 0)
                {
                    onHand -= adjustment;
                    reserved -= adjustment;
                    avg = averageAfterCostRemoval(before, beforeAvg, adjustment, originalUnitCost,
                        item.getProductNameSnapshot());
                }
                else
                {
                    int restore = -adjustment;
                    BigDecimal restoredCost = originalUnitCost.multiply(BigDecimal.valueOf(restore));
                    onHand += restore;
                    avg = weightedAverage(before, beforeAvg, restore, restoredCost);
                }
            }
            else if ("COST_ADJUST".equals(sourceType))
            {
                BigDecimal adjustedCost = money(item.getUnitPrice());
                BigDecimal originalCost = money(item.getUnitCost());
                if (before <= 0)
                    throw new ServiceException(item.getProductNameSnapshot() + " 当前库存为0，不能红冲调价单");
                if (beforeAvg.compareTo(adjustedCost) != 0)
                    throw new ServiceException(item.getProductNameSnapshot()
                        + " 的平均成本已再次变化，不能直接红冲原调价单");
                avg = originalCost;
                item.setQty(before);
                item.setAmount(originalCost.subtract(adjustedCost).multiply(BigDecimal.valueOf(before))
                    .setScale(2, RoundingMode.HALF_UP));
                item.setCostAmount(originalCost.multiply(BigDecimal.valueOf(before))
                    .setScale(2, RoundingMode.HALF_UP));
                item.setProfitAmount(ZERO.setScale(2));
                item.setProfitRate(ZERO);
                mapper.updateCostAdjustmentPostedItem(item);
            }
            else
            {
                throw new ServiceException("暂不支持该原单类型的红冲");
            }
            if (onHand < 0 || reserved < 0 || inspection < 0 || inspectionReserved < 0
                || defect < 0 || defectReserved < 0)
                throw new ServiceException(item.getProductNameSnapshot() + " 红冲后库存不能为负数");
            persistStockChange(reversal, item, stock, onHand, reserved, inspection, inspectionReserved,
                defect, defectReserved, avg, inspectionCost, defectCost, "REVERSAL_" + sourceType, userId, userName);
        }
        if ("COST_ADJUST".equals(source.getDocType())) refreshDocumentFinancials(reversal);
    }

    private void persistStockChange(JewelryDocument document, JewelryDocumentItem item, Map<String, Object> stock,
        int onHand, int reserved, int inspection, int inspectionReserved, int defect, int defectReserved,
        BigDecimal avg, BigDecimal inspectionCost, BigDecimal defectCost, String transactionType,
        Long userId, String userName)
    {
        int beforeOnHand = intValue(stock.get("onHandQty"));
        BigDecimal beforeAvg = decimal(stock.get("avgCost"));
        BigDecimal beforeAsset = beforeAvg.multiply(BigDecimal.valueOf(beforeOnHand))
            .add(decimal(stock.get("inspectionCostAmount"))).add(decimal(stock.get("defectCostAmount")));
        BigDecimal afterAsset = avg.multiply(BigDecimal.valueOf(onHand)).add(inspectionCost).add(defectCost);
        mapper.applyStock(item.getProductId(), onHand, reserved, inspection, inspectionReserved, defect,
            defectReserved, avg, inspectionCost, defectCost);
        Map<String, Object> tx = new HashMap<String, Object>();
        tx.put("documentId", document.getDocumentId()); tx.put("itemId", item.getItemId());
        tx.put("productId", item.getProductId()); tx.put("transactionType", transactionType);
        tx.put("onHandChange", onHand - beforeOnHand);
        tx.put("reservedChange", reserved - intValue(stock.get("reservedOutQty")));
        tx.put("inspectionChange", inspection - intValue(stock.get("inspectionQty")));
        tx.put("inspectionReservedChange", inspectionReserved - intValue(stock.get("inspectionReservedQty")));
        tx.put("defectChange", defect - intValue(stock.get("defectQty")));
        tx.put("defectReservedChange", defectReserved - intValue(stock.get("defectReservedQty")));
        tx.put("costAmountChange", afterAsset.subtract(beforeAsset));
        tx.put("beforeOnHand", beforeOnHand); tx.put("afterOnHand", onHand);
        tx.put("beforeAvgCost", beforeAvg); tx.put("afterAvgCost", avg);
        tx.put("operatorUserId", userId); tx.put("operatorName", userName);
        mapper.insertStockTransaction(tx);
    }

    private void prepareStockAdjustment(JewelryDocument document)
    {
        if (document.getItems() == null || document.getItems().isEmpty()) return;
        Set<Long> productIds = new HashSet<Long>();
        for (JewelryDocumentItem item : document.getItems())
        {
            if (item.getProductId() == null) continue;
            if (!productIds.add(item.getProductId()))
                throw new ServiceException("同一商品不能在一张盘点单中重复出现");
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            if (item.getCountedQty() == null)
                throw new ServiceException("请填写实盘库存");
            int systemQty = intValue(stock.get("onHandQty"));
            item.setSystemQty(systemQty);
            item.setAdjustmentQty(item.getCountedQty() - systemQty);
            item.setQty(Math.abs(item.getAdjustmentQty()));
            if (item.getAdjustmentQty() < 0)
                item.setUnitCost(decimal(stock.get("avgCost")));
        }
    }

    private void validateStockAdjustmentSnapshot(JewelryDocument document)
    {
        for (JewelryDocumentItem item : document.getItems())
        {
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            int currentQty = intValue(stock.get("onHandQty"));
            if (item.getSystemQty() == null || currentQty != item.getSystemQty())
                throw new ServiceException(item.getProductNameSnapshot()
                    + " 的账面库存已变化，请编辑盘点单刷新数据后再提交");
            int expected = item.getCountedQty() - item.getSystemQty();
            if (expected == 0 || expected != item.getAdjustmentQty())
                throw new ServiceException(item.getProductNameSnapshot() + " 的盘点差异数据不一致");
        }
    }

    private void prepareCostAdjustment(JewelryDocument document)
    {
        if (document.getItems() == null || document.getItems().isEmpty()) return;
        Set<Long> productIds = new HashSet<Long>();
        for (JewelryDocumentItem item : document.getItems())
        {
            if (item.getProductId() == null) continue;
            if (!productIds.add(item.getProductId()))
                throw new ServiceException("同一商品不能在一张调价单中重复出现");
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            int currentQty = intValue(stock.get("onHandQty"));
            if (currentQty <= 0) throw new ServiceException("当前库存为0，不能调整库存成本");
            item.setSystemQty(currentQty);
            item.setQty(currentQty);
            item.setUnitCost(decimal(stock.get("avgCost")));
            clearNonSalesFees(item);
        }
    }

    private void validateCostAdjustmentSnapshot(JewelryDocument document)
    {
        for (JewelryDocumentItem item : document.getItems())
        {
            Map<String, Object> stock = mapper.selectStockForUpdate(item.getProductId());
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            if (intValue(stock.get("onHandQty")) <= 0)
                throw new ServiceException(item.getProductNameSnapshot() + " 当前库存为0，不能调整库存成本");
            if (decimal(stock.get("avgCost")).compareTo(money(item.getUnitCost())) != 0)
                throw new ServiceException(item.getProductNameSnapshot()
                    + " 的平均成本已变化，请编辑调价单刷新数据后再提交");
        }
    }

    private void calculateCostAdjustment(JewelryDocument document)
    {
        int totalQty = 0;
        BigDecimal totalChange = ZERO;
        BigDecimal adjustedAsset = ZERO;
        for (JewelryDocumentItem item : document.getItems())
        {
            int qty = nonNegative(item.getQty());
            calculateCostAdjustmentItem(item, qty);
            totalQty += qty;
            totalChange = totalChange.add(money(item.getAmount()));
            adjustedAsset = adjustedAsset.add(money(item.getCostAmount()));
        }
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalChange.setScale(2, RoundingMode.HALF_UP));
        document.setTotalCost(adjustedAsset.setScale(2, RoundingMode.HALF_UP));
        document.setTotalProfit(ZERO.setScale(2));
        document.setRiskStatus("NORMAL");
    }

    private void calculateCostAdjustmentItem(JewelryDocumentItem item, int qty)
    {
        BigDecimal beforeCost = money(item.getUnitCost());
        BigDecimal afterCost = money(item.getUnitPrice());
        clearNonSalesFees(item);
        item.setAmount(afterCost.subtract(beforeCost).multiply(BigDecimal.valueOf(qty))
            .setScale(2, RoundingMode.HALF_UP));
        item.setCostAmount(afterCost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        item.setProfitAmount(ZERO.setScale(2));
        item.setProfitRate(ZERO);
    }

    private void validateCostChangeConflicts(JewelryDocument document)
    {
        List<Long> productIds = new ArrayList<Long>();
        for (JewelryDocumentItem item : document.getItems())
        {
            if (item.getProductId() != null && !productIds.contains(item.getProductId()))
                productIds.add(item.getProductId());
        }
        Collections.sort(productIds);
        for (Long productId : productIds)
        {
            Map<String, Object> stock = mapper.selectStockForUpdate(productId);
            if (stock == null) throw new ServiceException("商品库存记录不存在");
            JewelryDocumentItem item = null;
            for (JewelryDocumentItem candidate : document.getItems())
                if (productId.equals(candidate.getProductId())) { item = candidate; break; }
            String productName = item == null ? "该商品" : item.getProductNameSnapshot();
            if ("PURCHASE_IN".equals(document.getDocType()))
            {
                if (mapper.countPendingCostChangesByProduct(productId) > 0)
                    throw new ServiceException(productName + " 正在进行库存成本调价，采购入库暂不能提交或入账");
            }
            else if (isCostChangeDocument(document)
                && mapper.countPendingPurchasesByProduct(productId) > 0)
            {
                throw new ServiceException(productName + " 存在待审核采购入库单，请先完成或撤回采购单");
            }
        }
    }

    private boolean isCostChangeDocument(JewelryDocument document)
    {
        if ("COST_ADJUST".equals(document.getDocType())) return true;
        if (!"REVERSAL".equals(document.getDocType()) || document.getSourceDocumentId() == null) return false;
        if ("COST_ADJUST".equals(document.getSourceDocType())) return true;
        JewelryDocument source = mapper.selectDocumentById(document.getSourceDocumentId());
        return source != null && "COST_ADJUST".equals(source.getDocType());
    }

    private boolean isDualApprovalDocument(JewelryDocument document)
    {
        if ("STOCK_ADJUST".equals(document.getDocType()) || "COST_ADJUST".equals(document.getDocType()))
            return true;
        if (!"REVERSAL".equals(document.getDocType()) || document.getSourceDocumentId() == null) return false;
        if ("STOCK_ADJUST".equals(document.getSourceDocType())
            || "COST_ADJUST".equals(document.getSourceDocType())) return true;
        JewelryDocument source = mapper.selectDocumentById(document.getSourceDocumentId());
        return source != null && ("STOCK_ADJUST".equals(source.getDocType())
            || "COST_ADJUST".equals(source.getDocType()));
    }

    private void ensureDualApprovalRole(JewelryDocument document, String status, Long userId, String approvalRole)
    {
        String documentName = isCostChangeDocument(document) ? "库存成本调价单" : "库存调整单";
        if ("PENDING_FIRST".equals(status))
        {
            if (!"jewelry_reviewer".equals(approvalRole))
                throw new ServiceException(documentName + "必须先由审核员审核");
        }
        else
        {
            if (!"jewelry_admin".equals(approvalRole))
                throw new ServiceException(documentName + "必须由管理员完成复核");
            if (userId.equals(document.getFirstReviewerUserId()))
                throw new ServiceException("审核员和管理员复核不能由同一人完成");
        }
    }

    private BigDecimal averageAfterCostRemoval(int beforeQty, BigDecimal beforeAvg, int removeQty,
        BigDecimal removeUnitCost, String productName)
    {
        if (removeQty < 0 || beforeQty < removeQty)
            throw new ServiceException(productName + " 当前库存不足，不能红冲");
        int afterQty = beforeQty - removeQty;
        BigDecimal afterAsset = beforeAvg.multiply(BigDecimal.valueOf(beforeQty))
            .subtract(removeUnitCost.multiply(BigDecimal.valueOf(removeQty)));
        if (afterAsset.compareTo(ZERO) < 0)
            throw new ServiceException(productName + " 当前库存资产不足，不能红冲");
        if (afterQty == 0)
        {
            if (afterAsset.abs().compareTo(new BigDecimal("0.01")) > 0)
                throw new ServiceException(productName + " 红冲后库存资产无法归零，请检查后续库存变动");
            return ZERO;
        }
        return afterAsset.divide(BigDecimal.valueOf(afterQty), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedAverage(int beforeQty, BigDecimal beforeAvg, int incomingQty, BigDecimal incomingCost)
    {
        int afterQty = beforeQty + incomingQty;
        if (afterQty <= 0) return ZERO;
        return beforeAvg.multiply(BigDecimal.valueOf(beforeQty)).add(incomingCost)
            .divide(BigDecimal.valueOf(afterQty), 6, RoundingMode.HALF_UP);
    }

    private JewelryDocument requireReversalSource(JewelryDocument reversal)
    {
        if (reversal.getSourceDocumentId() == null)
            throw new ServiceException("红冲单未关联原单");
        JewelryDocument source = mapper.selectDocumentByIdForUpdate(reversal.getSourceDocumentId());
        if (source == null) throw new ServiceException("红冲原单不存在");
        if (!"POSTED".equals(source.getStatus()))
            throw new ServiceException("原单已不是可红冲状态");
        ensureSaleHasNoActiveReturns(source);
        return source;
    }

    private void ensureSaleHasNoActiveReturns(JewelryDocument source)
    {
        if ("SALES_OUT".equals(source.getDocType())
            && mapper.countActiveCustomerReturnsBySource(source.getDocumentId()) > 0)
            throw new ServiceException("原销售单存在待处理或已入账的消费者退货，不能整单红冲");
    }

    private JewelryDocumentItem copyReversalItem(JewelryDocumentItem source, Long reversalId, String sourceDocType)
    {
        JewelryDocumentItem item = new JewelryDocumentItem();
        item.setDocumentId(reversalId);
        item.setProductId(source.getProductId());
        item.setItemRole(text(source.getItemRole()).isEmpty() ? "NORMAL" : source.getItemRole());
        item.setSourceItemId(source.getItemId());
        item.setBundleGroupNo(source.getBundleGroupNo());
        item.setSaleRole(normalizedSaleRole(source.getSaleRole()));
        item.setPricingMode(normalizedPricingMode(source.getPricingMode()));
        item.setSkuSnapshot(source.getSkuSnapshot());
        item.setProductNameSnapshot(source.getProductNameSnapshot());
        item.setProductTypeSnapshot(source.getProductTypeSnapshot());
        item.setSpecificationSnapshot(source.getSpecificationSnapshot());
        item.setImageUrls(source.getImageUrls());
        item.setQty(source.getQty());
        item.setGoodQty(source.getGoodQty());
        item.setDefectQty(source.getDefectQty());
        item.setSystemQty(source.getSystemQty());
        item.setCountedQty(source.getCountedQty());
        item.setAdjustmentQty(source.getAdjustmentQty());
        item.setUnitPrice(source.getUnitPrice());
        item.setUnitCost(source.getUnitCost());
        item.setPackFee(source.getPackFee());
        item.setShipFee(source.getShipFee());
        item.setCertFee(source.getCertFee());
        item.setOtherFee1(source.getOtherFee1());
        item.setOtherFee2(source.getOtherFee2());
        item.setOtherFee3(source.getOtherFee3());
        int amountScale = "PURCHASE_IN".equals(sourceDocType) ? 4 : 2;
        item.setAmount(money(source.getAmount()).negate().setScale(amountScale, RoundingMode.HALF_UP));
        item.setCostAmount(money(source.getCostAmount()).negate().setScale(amountScale, RoundingMode.HALF_UP));
        item.setProfitAmount(money(source.getProfitAmount()).negate().setScale(2, RoundingMode.HALF_UP));
        item.setProfitRate(source.getProfitRate());
        item.setLineReason("红冲原明细 " + source.getItemId());
        return item;
    }

    private void ensureReviewer(JewelryDocument document, Long userId)
    {
        if (userId.equals(document.getCreatorUserId())) throw new ServiceException("制单人不能审核自己的单据");
    }

    private void changeStatus(JewelryDocument document, String from, String to, Long userId, String userName,
        String reason, Integer stage)
    {
        if (mapper.updateDocumentStatus(document.getDocumentId(), from, to, userId, userName, reason, stage) != 1)
            throw new ServiceException("单据状态已变化，请刷新后重试");
    }

    private JewelryDocument requireDocument(Long id)
    {
        JewelryDocument document = mapper.selectDocumentById(id);
        if (document == null) throw new ServiceException("单据不存在");
        return document;
    }

    private JewelryDocument requirePostedCustomerReturn(Long sourceDocumentId)
    {
        JewelryDocument source = requireDocument(sourceDocumentId);
        if (!"CUSTOMER_RETURN".equals(source.getDocType()) || !"POSTED".equals(source.getStatus()))
            throw new ServiceException("关联的原单必须是已入账且未红冲的客户退货单");
        return source;
    }

    private String createDocNo(String type)
    {
        String prefix;
        if ("PURCHASE_IN".equals(type)) prefix = "RK";
        else if ("SALES_OUT".equals(type)) prefix = "CK";
        else if ("SUPPLIER_RETURN".equals(type)) prefix = "TG";
        else if ("CUSTOMER_RETURN".equals(type)) prefix = "SH";
        else if ("RETURN_INSPECT".equals(type)) prefix = "ZJ";
        else if ("STOCK_ADJUST".equals(type)) prefix = "PD";
        else if ("COST_ADJUST".equals(type)) prefix = "TJ";
        else if ("ASSEMBLY".equals(type)) prefix = "ZZ";
        else if ("REVERSAL".equals(type)) prefix = "HC";
        else prefix = "JE";
        return prefix + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
    }

    private void validateSupplierReference(JewelryDocument document, boolean updateSnapshot)
    {
        Map<String, Object> supplier = mapper.selectSupplierById(document.getSupplierId());
        if (supplier == null) throw new ServiceException("供应商不存在");
        if (!"0".equals(textValue(supplier.get("status"))))
            throw new ServiceException("供应商已停用，不能用于新单据");
        if (updateSnapshot) document.setSupplierNameSnapshot(textValue(supplier.get("supplierName")));
    }

    private Map<String, BigDecimal> calculateSalesLine(BigDecimal price, BigDecimal cost,
        BigDecimal fees, BigDecimal rate)
    {
        BigDecimal deductions = price.multiply(rate);
        BigDecimal profit = price.subtract(cost).subtract(fees).subtract(deductions);
        Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();
        result.put("deductions", deductions);
        result.put("profit", profit);
        return result;
    }

    private void validateCombinedRate(BigDecimal rate)
    {
        if (rate.compareTo(BigDecimal.ONE) >= 0)
            throw new ServiceException("平台、佣金和税率合计必须小于100%");
    }

    private void validateNonNegative(BigDecimal value, String label)
    {
        if (money(value).signum() < 0) throw new ServiceException(label + "不能小于0");
    }

    private BigDecimal nonNegativeDecimalValue(Object value, String label)
    {
        BigDecimal result = value == null || textValue(value).isEmpty() ? ZERO : decimalValue(value, label);
        if (result.signum() < 0) throw new ServiceException(label + "不能小于0");
        return result.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageValue(Object value, String label)
    {
        BigDecimal percent = value == null || textValue(value).isEmpty() ? ZERO : decimalValue(value, label);
        if (percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0)
            throw new ServiceException(label + "必须在0%到100%之间");
        return percent.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalValue(Object value, String label)
    {
        try { return new BigDecimal(textValue(value)); }
        catch (RuntimeException ex) { throw new ServiceException(label + "格式不正确"); }
    }

    private int integerValue(Object value, String label)
    {
        try { return Integer.parseInt(textValue(value)); }
        catch (RuntimeException ex) { throw new ServiceException(label + "必须是整数"); }
    }

    private Long nullableLong(Object value)
    {
        if (value == null || textValue(value).isEmpty()) return null;
        try { return Long.valueOf(textValue(value)); }
        catch (RuntimeException ex) { throw new ServiceException("商品ID格式不正确"); }
    }

    private void validateRate(BigDecimal value, String label)
    {
        BigDecimal rate = money(value);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
            throw new ServiceException(label + "必须在0到1之间");
    }

    private boolean isOutbound(String type) { return "SALES_OUT".equals(type) || "SUPPLIER_RETURN".equals(type); }
    private int effectiveQty(String type, JewelryDocumentItem item)
    {
        if ("RETURN_INSPECT".equals(type)) return item.getGoodQty() + item.getDefectQty();
        if ("STOCK_ADJUST".equals(type)) return Math.abs(item.getAdjustmentQty());
        return item.getQty();
    }
    private int nonNegative(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private int nonNegativeValue(Object value, int defaultValue)
    {
        if (value == null || String.valueOf(value).trim().isEmpty()) return defaultValue;
        try { return Math.max(0, Integer.parseInt(String.valueOf(value))); }
        catch (NumberFormatException ex) { throw new ServiceException("库存预警值必须是整数"); }
    }
    private String textValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String text(String value) { return value == null ? "" : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(6, RoundingMode.HALF_UP); }
    private BigDecimal decimal(Object value) { return value == null ? ZERO : new BigDecimal(String.valueOf(value)); }
    private int intValue(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private long longValue(Object value) { return value == null ? 0L : ((Number) value).longValue(); }
}
