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
            "CUSTOMER_RETURN", "RETURN_INSPECT", "STOCK_ADJUST")));

    @Autowired
    private JewelryErpMapper mapper;

    @Override
    public List<Map<String, Object>> listProducts(Map<String, Object> query) { return mapper.selectProductList(query); }

    @Override
    @Transactional
    public int saveProduct(Map<String, Object> product)
    {
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
            item.setDocumentId(document.getDocumentId());
            mapper.insertDocumentItem(item);
        }
        return getDocument(document.getDocumentId());
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
        reversal.setTotalAmount(money(source.getTotalAmount()).negate().setScale(2, RoundingMode.HALF_UP));
        reversal.setTotalCost(money(source.getTotalCost()).negate().setScale(2, RoundingMode.HALF_UP));
        reversal.setTotalProfit(money(source.getTotalProfit()).negate().setScale(2, RoundingMode.HALF_UP));
        reversal.setRiskStatus("NORMAL");
        reversal.setCreatorUserId(userId);
        reversal.setCreatorName(userName);
        reversal.setCreateBy(userName);
        reversal.setRemark("系统生成，关联原单：" + source.getDocNo());
        mapper.insertDocument(reversal);

        List<JewelryDocumentItem> reversalItems = new ArrayList<JewelryDocumentItem>();
        for (JewelryDocumentItem sourceItem : sourceItems)
        {
            JewelryDocumentItem item = copyReversalItem(sourceItem, reversal.getDocumentId());
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
        if (!"DRAFT".equals(document.getStatus()))
        {
            throw new ServiceException("只有草稿单据可以提交");
        }
        if (!userId.equals(document.getCreatorUserId()))
        {
            throw new ServiceException("只能提交自己创建的单据");
        }
        if ("STOCK_ADJUST".equals(document.getDocType()))
        {
            validateStockAdjustmentSnapshot(document);
        }
        reserve(document);
        changeStatus(document, "DRAFT", "PENDING_FIRST", userId, userName, null, null);
        mapper.insertEvent(documentId, "SUBMIT", "DRAFT", "PENDING_FIRST", userId, userName, "");
    }

    @Override
    @Transactional
    public void withdraw(Long documentId, Long userId, String userName)
    {
        JewelryDocument document = getDocument(documentId);
        if (!"PENDING_FIRST".equals(document.getStatus()))
        {
            throw new ServiceException("只有待一审单据可以撤回");
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
    public void approve(Long documentId, String comment, Long userId, String userName)
    {
        JewelryDocument document = getDocument(documentId);
        ensureReviewer(document, userId);
        if ("PENDING_FIRST".equals(document.getStatus()))
        {
            changeStatus(document, "PENDING_FIRST", "PENDING_SECOND", userId, userName, null, 1);
            mapper.insertApproval(documentId, 1, "PASS", userId, userName, text(comment));
            mapper.insertEvent(documentId, "FIRST_APPROVE", "PENDING_FIRST", "PENDING_SECOND", userId, userName, text(comment));
            return;
        }
        if (!"PENDING_SECOND".equals(document.getStatus()))
        {
            throw new ServiceException("当前单据不在待复核状态");
        }
        if (userId.equals(document.getFirstReviewerUserId()))
        {
            throw new ServiceException("一审与复核不能由同一人完成");
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
        changeStatus(document, "PENDING_SECOND", "POSTED", userId, userName, null, 2);
        mapper.insertApproval(documentId, 2, "PASS", userId, userName, text(comment));
        mapper.insertEvent(documentId, "SECOND_APPROVE", "PENDING_SECOND", "POSTED", userId, userName, text(comment));
    }

    @Override
    @Transactional
    public void reject(Long documentId, String comment, Long userId, String userName)
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
        int stage = "PENDING_FIRST".equals(document.getStatus()) ? 1 : 2;
        if (stage == 2 && userId.equals(document.getFirstReviewerUserId()))
        {
            throw new ServiceException("一审与复核不能由同一人完成");
        }
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
        if (("SALES_OUT".equals(document.getDocType()) || "CUSTOMER_RETURN".equals(document.getDocType()))
            && text(document.getSalesChannel()).trim().isEmpty())
            throw new ServiceException("请填写销售渠道");
        if (("SUPPLIER_RETURN".equals(document.getDocType()) || "CUSTOMER_RETURN".equals(document.getDocType()))
            && text(document.getReturnReason()).trim().isEmpty())
            throw new ServiceException("请填写退货原因");
        if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() == null
            && text(document.getUnlinkedReason()).trim().isEmpty())
            throw new ServiceException("未关联原销售单时必须填写原因");
        validateRate(document.getPlatformRate(), "平台扣点率");
        validateRate(document.getCommissionRate(), "达人佣金率");
        validateRate(document.getTaxRate(), "税率");
        Set<Long> productIds = new HashSet<Long>();
        for (JewelryDocumentItem item : document.getItems())
        {
            if (item.getProductId() == null) throw new ServiceException("请选择商品");
            if (!productIds.add(item.getProductId())) throw new ServiceException("同一商品不能在一张单据中重复出现");
            Map<String, Object> product = mapper.selectProductById(item.getProductId());
            if (product == null) throw new ServiceException("商品不存在或已删除");
            item.setSkuSnapshot(String.valueOf(product.get("sku")));
            item.setProductNameSnapshot(String.valueOf(product.get("productName")));
            item.setQty(nonNegative(item.getQty()));
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
                    JewelryDocumentItem sourceItem = null;
                    for (JewelryDocumentItem candidate : mapper.selectDocumentItems(source.getDocumentId()))
                    {
                        if (item.getProductId().equals(candidate.getProductId()))
                        {
                            sourceItem = candidate;
                            break;
                        }
                    }
                    if (sourceItem == null) throw new ServiceException("原销售单中不存在该商品");
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
                    document.setSalesChannel(source.getSalesChannel());
                    document.setInfluencerName(source.getInfluencerName());
                    document.setPlatformRate(money(source.getPlatformRate()));
                    document.setCommissionRate(money(source.getCommissionRate()));
                    document.setTaxRate(money(source.getTaxRate()));
                }
                else if (money(item.getUnitPrice()).signum() <= 0)
                    throw new ServiceException("未关联原销售单时必须填写实际退款单价");
            }
            item.setGoodQty(nonNegative(item.getGoodQty()));
            item.setDefectQty(nonNegative(item.getDefectQty()));
            item.setAdjustmentQty(item.getAdjustmentQty() == null ? 0 : item.getAdjustmentQty());
            if ("RETURN_INSPECT".equals(document.getDocType()) && item.getGoodQty() + item.getDefectQty() <= 0)
                throw new ServiceException("退货质检的良品数和次品数不能同时为0");
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
            if (!"RETURN_INSPECT".equals(document.getDocType()) && !"STOCK_ADJUST".equals(document.getDocType())
                && item.getQty() <= 0) throw new ServiceException("商品数量必须大于0");
        }
    }

    private void calculateDocument(JewelryDocument document)
    {
        int totalQty = 0;
        BigDecimal totalAmount = ZERO;
        BigDecimal totalCost = ZERO;
        BigDecimal totalProfit = ZERO;
        BigDecimal platformRate = money(document.getPlatformRate());
        BigDecimal commissionRate = money(document.getCommissionRate());
        BigDecimal taxRate = money(document.getTaxRate());
        boolean customerReturn = "CUSTOMER_RETURN".equals(document.getDocType());
        if (customerReturn)
        {
            platformRate = ZERO;
            commissionRate = ZERO;
            taxRate = ZERO;
        }
        for (JewelryDocumentItem item : document.getItems())
        {
            int qty = effectiveQty(document.getDocType(), item);
            BigDecimal price = money(item.getUnitPrice());
            BigDecimal cost = money(item.getUnitCost());
            BigDecimal packFee = money(item.getPackFee());
            BigDecimal shipFee = money(item.getShipFee());
            BigDecimal certFee = money(item.getCertFee());
            if (customerReturn)
            {
                packFee = ZERO;
            }
            BigDecimal fees = "SALES_OUT".equals(document.getDocType())
                ? packFee.add(shipFee).add(certFee)
                : customerReturn ? shipFee.multiply(BigDecimal.valueOf(2)).add(certFee) : ZERO;
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
            item.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            item.setCostAmount(costAmount.setScale(2, RoundingMode.HALF_UP));
            item.setProfitAmount(profit.setScale(2, RoundingMode.HALF_UP));
            item.setProfitRate(grossAmount.signum() == 0 ? ZERO :
                profit.divide(grossAmount, 6, RoundingMode.HALF_UP));
            totalQty += qty;
            totalAmount = totalAmount.add(amount);
            totalCost = totalCost.add(costAmount);
            totalProfit = totalProfit.add(profit);
        }
        document.setPlatformRate(platformRate); document.setCommissionRate(commissionRate); document.setTaxRate(taxRate);
        document.setTotalQty(totalQty); document.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        document.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        document.setTotalProfit(totalProfit.setScale(2, RoundingMode.HALF_UP));
        if ("SALES_OUT".equals(document.getDocType()) && totalProfit.signum() < 0)
            document.setRiskStatus("LOSS");
        else if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() == null)
            document.setRiskStatus("REVIEW");
        else
            document.setRiskStatus("NORMAL");
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
                item.setCostAmount(incomingCost.setScale(2, RoundingMode.HALF_UP));
                mapper.updateDocumentItemCost(item);
            }
            else if ("SALES_OUT".equals(document.getDocType()))
            {
                onHand -= qty; reserved -= qty;
                item.setUnitCost(beforeAvg);
                item.setCostAmount(beforeAvg.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
                BigDecimal fees = money(item.getPackFee()).add(money(item.getShipFee()))
                    .add(money(item.getCertFee())).multiply(BigDecimal.valueOf(qty));
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
            persistStockChange(document, item, stock, onHand, reserved, inspection, inspectionReserved,
                defect, defectReserved, avg, inspectionCost, defectCost, document.getDocType(), userId, userName);
        }
        refreshDocumentFinancials(document);
    }

    private void refreshDocumentFinancials(JewelryDocument document)
    {
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
        document.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        document.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        document.setTotalProfit(totalProfit.setScale(2, RoundingMode.HALF_UP));
        if ("SALES_OUT".equals(document.getDocType()) && totalProfit.signum() < 0)
            document.setRiskStatus("LOSS");
        else if ("CUSTOMER_RETURN".equals(document.getDocType()) && document.getSourceDocumentId() == null)
            document.setRiskStatus("REVIEW");
        else
            document.setRiskStatus("NORMAL");
        mapper.updateDocumentFinancials(document);
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
        JewelryDocument source = requireDocument(reversal.getSourceDocumentId());
        if (!"POSTED".equals(source.getStatus()))
            throw new ServiceException("原单已不是可红冲状态");
        return source;
    }

    private JewelryDocumentItem copyReversalItem(JewelryDocumentItem source, Long reversalId)
    {
        JewelryDocumentItem item = new JewelryDocumentItem();
        item.setDocumentId(reversalId);
        item.setProductId(source.getProductId());
        item.setSourceItemId(source.getItemId());
        item.setSkuSnapshot(source.getSkuSnapshot());
        item.setProductNameSnapshot(source.getProductNameSnapshot());
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
        item.setAmount(money(source.getAmount()).negate().setScale(2, RoundingMode.HALF_UP));
        item.setCostAmount(money(source.getCostAmount()).negate().setScale(2, RoundingMode.HALF_UP));
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

    private String createDocNo(String type)
    {
        String prefix;
        if ("PURCHASE_IN".equals(type)) prefix = "RK";
        else if ("SALES_OUT".equals(type)) prefix = "CK";
        else if ("SUPPLIER_RETURN".equals(type)) prefix = "TG";
        else if ("CUSTOMER_RETURN".equals(type)) prefix = "SH";
        else if ("RETURN_INSPECT".equals(type)) prefix = "ZJ";
        else if ("STOCK_ADJUST".equals(type)) prefix = "PD";
        else if ("REVERSAL".equals(type)) prefix = "HC";
        else prefix = "JE";
        return prefix + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
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
    private String text(String value) { return value == null ? "" : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(6, RoundingMode.HALF_UP); }
    private BigDecimal decimal(Object value) { return value == null ? ZERO : new BigDecimal(String.valueOf(value)); }
    private int intValue(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private long longValue(Object value) { return value == null ? 0L : ((Number) value).longValue(); }
}
