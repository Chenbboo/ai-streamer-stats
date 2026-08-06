<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.docNo" placeholder="单号" clearable/></el-form-item><el-form-item><el-select v-model="query.docType" placeholder="全部类型" clearable style="width:150px"><el-option v-for="o in types" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px"><el-option v-for="o in statuses" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item></el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:document:add']" @click="open()">新建单据</el-button>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="docNo" label="单号" width="190"/>
      <el-table-column label="类型" width="130"><template #default="{row}">{{labelOf(types,row.docType)}}</template></el-table-column>
      <el-table-column prop="bizDate" label="业务日期" width="110"/>
      <el-table-column label="业务对象" min-width="130"><template #default="{row}">{{row.supplierNameSnapshot || row.salesChannel || (row.docType==='ASSEMBLY'?'手工组装':row.docType==='COST_ADJUST'?'库存成本调整':'—')}}</template></el-table-column>
      <el-table-column prop="totalQty" label="数量" width="80" align="right"/>
      <el-table-column label="金额" width="120" align="right"><template #default="{row}">{{money(row.totalAmount)}}</template></el-table-column>
      <el-table-column v-if="canViewFinance" label="毛利" width="110" align="right"><template #default="{row}"><span v-if="row.docType==='COST_ADJUST'">—</span><span v-else :class="{loss:Number(row.totalProfit)<0}">{{money(row.totalProfit)}}</span></template></el-table-column>
      <el-table-column label="风险" width="100"><template #default="{row}"><el-tag v-if="row.riskStatus==='LOSS'" type="danger">亏损</el-tag><el-tag v-else-if="row.riskStatus==='REVIEW'" type="warning">需复核</el-tag><span v-else>—</span></template></el-table-column>
      <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="statusType(row.status)">{{documentStatusLabel(row)}}</el-tag></template></el-table-column>
      <el-table-column prop="creatorName" label="制单人" width="100"/>
      <el-table-column label="审批人" width="150"><template #default="{row}"><span v-if="isDualApproval(row) && row.firstReviewerName">{{row.firstReviewerName}}<template v-if="row.secondReviewerName"> / {{row.secondReviewerName}}</template></span><span v-else>{{['POSTED','REVERSED'].includes(row.status)?(row.secondReviewerName||row.firstReviewerName||'—'):'—'}}</span></template></el-table-column>
      <el-table-column label="操作" width="285" fixed="right"><template #default="{row}"><el-button link type="primary" @click="view(row)">查看</el-button><el-button v-if="['DRAFT','REJECTED'].includes(row.status) && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="primary" v-hasPermi="['jewelry:document:edit']" @click="edit(row)">编辑</el-button><el-button v-if="row.status==='DRAFT' || (row.docType==='REVERSAL' && row.status==='REJECTED')" link type="success" v-hasPermi="['jewelry:document:submit']" @click="submit(row)">提交</el-button><el-button v-if="canDeleteDraft(row)" link type="danger" v-hasPermi="['jewelry:document:edit']" @click="removeDraft(row)">删除</el-button><el-button v-if="row.status==='PENDING_FIRST'" link type="warning" v-hasPermi="['jewelry:document:withdraw']" @click="withdraw(row)">撤回</el-button><el-button v-if="row.status==='POSTED' && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="danger" v-hasPermi="['jewelry:document:reverse']" @click="reverse(row)">红冲</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="dialog" :title="readonly?'查看单据':(form.documentId?'编辑单据':'新建单据')" width="94%" top="4vh" destroy-on-close>
      <div class="sheet">
        <el-alert v-if="form.docType === 'REVERSAL'" :title="isDualApproval(form)?'该红冲涉及库存调整，仍需审核员初审和管理员复核后入账。':'红冲单明细来自原单，不允许修改；提交后由审核员审核通过即可入账。'" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType === 'STOCK_ADJUST'" title="库存调整单提交后先由审核员初审，再由管理员复核；复核通过后才真正调整库存。" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType === 'COST_ADJUST'" title="库存成本调价单提交后，将先由审核员审核，再由管理员复核；复核通过后才修改库存平均成本。审批期间对应SKU不能采购入库。" type="warning" :closable="false" show-icon />
        <el-form :model="form" label-position="top"><div class="sheet-head">
          <el-form-item label="单据类型" required><el-select v-model="form.docType" :disabled="readonly" @change="typeChanged"><el-option v-for="o in editableTypes" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item>
          <el-form-item label="业务日期" required><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="needsSupplier" label="供应商" required><el-select v-model="form.supplierId" filterable clearable :disabled="readonly" @change="supplierChanged"><el-option v-for="s in suppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId"/></el-select></el-form-item>
          <el-form-item label="外部单号"><el-input v-model="form.externalNo" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" label="原销售单" required>
            <el-select v-model="form.sourceDocumentId" filterable clearable :disabled="readonly" @change="salesSourceChanged">
              <el-option v-for="d in salesDocuments" :key="d.documentId"
                :label="`${d.docNo} · ${d.bizDate} · ${d.salesChannel || '未填写渠道'}`" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='RETURN_INSPECT'" label="原客户退货单" required>
            <el-select v-model="form.sourceDocumentId" filterable clearable :disabled="readonly" @change="inspectionSourceChanged">
              <el-option v-for="d in returnDocuments" :key="d.documentId"
                :label="`${d.docNo} · ${d.bizDate} · ${d.salesChannel || '未填写渠道'}`" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" label="实际退款总额" required>
            <el-input-number v-model="form.actualRefundAmount" :min="0" :precision="2" :disabled="readonly" />
          </el-form-item>
          <el-form-item v-if="needsSalesChannel" label="销售渠道" required><el-input v-model="form.salesChannel" :disabled="readonly || form.docType==='CUSTOMER_RETURN' || !!form.sourceDocumentId"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="达人/主播"><el-input v-model="form.influencerName" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="平台扣点率（%）"><el-input-number v-model="platformPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="达人佣金率（%）"><el-input-number v-model="commissionPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="税率（%）"><el-input-number v-model="taxPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
        </div></el-form>
        <el-alert v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId && !readonly"
          title="客户退货必须先选择已入账的原销售单，销售渠道、商品和成本将由系统自动带入。"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType==='RETURN_INSPECT' && !form.sourceDocumentId && !readonly"
          title="退货质检必须先选择已入账的客户退货单，系统会带出尚未处理的退货明细。"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="refundAmountDiffers" :title="`实际退款 ¥${money(form.actualRefundAmount)} 与所选明细原成交金额 ¥${money(expectedReturnRefund)} 不一致，提交后将标记为需复核。`"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="(canViewFinance && estimatedProfit < 0) || serverRiskStatus==='LOSS' || form.riskStatus==='LOSS'" title="当前销售单预计亏损，提交后审批页面将显示亏损风险。" type="error" :closable="false" show-icon />
        <div v-if="excelImportSupported && !readonly" class="item-toolbar">
          <div>
            <b>商品明细</b>
            <span>支持通过 Excel 批量填充，导入后仍可修改</span>
          </div>
          <div class="item-toolbar-actions">
            <div v-if="importProgress.active" class="excel-compress-progress">
              <span>{{ importProgress.text }}</span>
              <el-progress :percentage="importProgress.percentage" :stroke-width="5" :show-text="false" />
            </div>
            <el-button icon="Download" @click="downloadImportTemplate">下载模板</el-button>
            <el-upload action="#" :accept="form.docType==='PURCHASE_IN'?'.xlsx':'.xls,.xlsx'" :auto-upload="false" :show-file-list="false"
              :on-change="handleImportFile">
              <el-button type="primary" plain icon="Upload" :loading="importLoading">Excel导入</el-button>
            </el-upload>
          </div>
        </div>
        <el-table :data="form.items" border class="item-table" :row-class-name="bundleRowClass">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column label="商品" min-width="390">
            <template #default="{ row }">
              <div class="product-picker">
                <el-select v-model="row.productId" filterable :disabled="readonly || ['CUSTOMER_RETURN','RETURN_INSPECT'].includes(form.docType)" @change="productChanged(row)">
                  <el-option v-for="p in availableProducts(row)" :key="p.productId" :label="p.sku + ' · ' + p.productName" :value="p.productId" />
                </el-select>
                <el-button v-if="form.docType==='PURCHASE_IN' && !readonly" type="primary" plain icon="Plus"
                  v-hasPermi="['jewelry:product:add']" @click="openQuickProduct(row)">新增商品</el-button>
                <el-button v-if="form.docType==='SALES_OUT' && !readonly && canAddAddon(row)" type="warning" plain icon="Plus"
                  @click="addAddon(row)">搭售商品</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" label="销售角色" width="130">
            <template #default="{row}">
              <el-tag v-if="normalizedSaleRole(row)==='MAIN'" type="success" effect="plain">组合{{row.bundleGroupNo}}·主商品</el-tag>
              <el-tag v-else-if="normalizedSaleRole(row)==='ADDON'" type="warning" effect="plain">组合{{row.bundleGroupNo}}·搭售</el-tag>
              <el-tag v-else type="info" effect="plain">独立销售</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" label="搭售用途" width="110">
            <template #default="{row}">
              <el-tag v-if="isAccessoryPackaging(row)" type="warning" effect="plain">包装耗材</el-tag>
              <span v-else-if="normalizedSaleRole(row)==='ADDON'">普通搭售</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" label="计价方式" width="145">
            <template #default="{row}">
              <el-select v-if="form.docType==='SALES_OUT' && normalizedSaleRole(row)==='ADDON'" v-model="row.pricingMode"
                :disabled="readonly || isAccessoryPackaging(row)" @change="pricingModeChanged(row)">
                <el-option label="包含在组合价" value="INCLUDED" />
                <el-option label="单独计价" value="SEPARATE" />
              </el-select>
              <span v-else>{{normalizedPricingMode(row)==='INCLUDED'?'包含在组合价':'单独计价'}}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='ASSEMBLY'" label="角色" width="90">
            <template #default="{row}"><el-tag :type="row.itemRole==='OUTPUT'?'success':'warning'" effect="plain">{{row.itemRole==='OUTPUT'?'成品产出':'散件投入'}}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="form.docType==='PURCHASE_IN' || (readonly && form.items.some(item=>item.imageUrls))" label="实物图片" width="190">
            <template #default="{row}">
              <image-upload v-model="row.imageUrls" :limit="1" :file-size="8" :disabled="readonly"/>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='RETURN_INSPECT'" label="剩余待检" width="110" align="right">
            <template #default="{ row }">{{ row.remainingInspectQty }}</template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" label="良品数" width="120">
            <template #default="{ row }"><el-input-number v-model="row.goodQty" :min="0" :max="form.docType==='RETURN_INSPECT'?Math.max(0,Number(row.remainingInspectQty||0)-Number(row.defectQty||0)):undefined" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" label="次品数" width="120">
            <template #default="{ row }"><el-input-number v-model="row.defectQty" :min="0" :max="form.docType==='RETURN_INSPECT'?Math.max(0,Number(row.remainingInspectQty||0)-Number(row.goodQty||0)):undefined" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="系统库存" width="110">
            <template #default="{ row }">{{ row.systemQty }}</template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="实盘库存" width="130">
            <template #default="{ row }"><el-input-number v-model="row.countedQty" :min="0" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="差异数量" width="100">
            <template #default="{ row }">{{ Number(row.countedQty || 0) - Number(row.systemQty || 0) }}</template>
          </el-table-column>
          <el-table-column v-if="form.docType==='COST_ADJUST'" label="当前库存" width="110" align="right"><template #default="{row}">{{row.qty}}</template></el-table-column>
          <el-table-column v-if="showQuantityColumn" label="数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" :disabled="readonly || (form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId)" /></template>
          </el-table-column>
          <el-table-column v-if="showPriceColumn" :label="priceLabel" width="170"><template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :disabled="readonly || (form.docType==='CUSTOMER_RETURN' && !!form.sourceDocumentId) || (form.docType==='SALES_OUT' && normalizedPricingMode(row)==='INCLUDED')" style="width:100%"/></template></el-table-column>
          <el-table-column v-if="showCostColumn" :label="form.docType==='COST_ADJUST'?'当前平均成本':'单位成本'" width="140"><template #default="{row}"><span>{{money(row.unitCost)}}</span></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="包装费/件" width="220">
            <template #default="{row}">
              <div v-if="isAccessoryPackaging(row)" class="pack-fee-cell packaging-cost-note">
                <span>配件耗材 ¥{{money(row.unitCost)}} × {{effectiveQty(row)}}</span>
                <small>耗材成本 ¥{{money(Number(row.unitCost||0)*effectiveQty(row))}}</small>
              </div>
              <div v-else class="pack-fee-cell">
                <el-input-number v-model="row.packFee" :min="0" :precision="2" :disabled="readonly"/>
                <template v-if="normalizedSaleRole(row)==='MAIN' && accessoryPackagingMetrics(row).accessoryTotal>0">
                  <small>配件耗材 ¥{{money(accessoryPackagingMetrics(row).accessoryTotal)}}，包装费 ¥{{money(accessoryPackagingMetrics(row).manualTotal)}}</small>
                  <small v-if="accessoryPackagingMetrics(row).shortage>0" class="packaging-shortage">
                    不足 ¥{{money(accessoryPackagingMetrics(row).shortage)}}，不能提交
                  </small>
                  <small v-else class="packaging-covered">包装费已覆盖配件耗材</small>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="物流费/件" width="145"><template #default="{row}"><el-input-number v-model="row.shipFee" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="鉴定费/件" width="145"><template #default="{row}"><el-input-number v-model="row.certFee" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="其他1/件" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee1" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="其他2/件" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee2" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="其他3/件" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee3" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="showPriceColumn" :label="amountLabel" width="130" align="right"><template #default="{row}">{{money(lineAmount(row))}}</template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="平台等扣费" width="130" align="right"><template #default="{row}">{{money(lineDeductions(row))}}</template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="预计净入账" width="130" align="right"><template #default="{row}">{{money(lineNetReceipt(row))}}</template></el-table-column>
          <el-table-column v-if="canViewFinance && form.docType==='SALES_OUT'" label="预计毛利" width="120" align="right">
            <template #default="{row}">
              <span v-if="isAccessoryPackaging(row)">—</span>
              <span v-else :class="{loss:lineProfit(row)<0}">{{money(lineProfit(row))}}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="调整原因" min-width="180"><template #default="{row}"><el-input v-model="row.lineReason" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="!readonly && (form.docType!=='CUSTOMER_RETURN' || form.sourceDocumentId)" width="60"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="removeItem($index)"/></template></el-table-column>
        </el-table>
        <div v-if="form.docType==='SALES_OUT' && bundleSummaries.length" class="bundle-summaries">
          <div v-for="group in bundleSummaries" :key="group.groupNo">
            <b>组合{{group.groupNo}}</b>
            <span>成交 ¥{{money(group.amount)}}</span>
            <span v-if="group.accessoryTotal>0">配件耗材 ¥{{money(group.accessoryTotal)}}</span>
            <span v-if="group.accessoryTotal>0">包装费 ¥{{money(group.manualPackagingTotal)}}</span>
            <span v-if="group.packagingShortage>0" class="packaging-shortage">包装费不足 ¥{{money(group.packagingShortage)}}</span>
            <span v-if="canViewFinance">成本及费用 ¥{{money(group.cost)}}</span>
            <span v-if="canViewFinance" :class="{loss:group.profit<0}">预计毛利 ¥{{money(group.profit)}}</span>
          </div>
        </div>
        <el-button v-if="!readonly && !['CUSTOMER_RETURN','RETURN_INSPECT'].includes(form.docType)" plain icon="Plus" class="add-line" @click="addNormalItem">增加一行</el-button>
        <div class="document-total">
          <span>SKU {{ form.items.length }} 种</span>
          <span>总件数 <b>{{ estimatedQty }}</b></span>
          <span v-if="showPriceColumn">{{ totalAmountLabel }} <b>¥ {{ money(estimatedAmount) }}</b></span>
          <span v-if="form.docType==='COST_ADJUST'">调整后库存金额 <b>¥ {{ money(adjustedInventoryAmount) }}</b></span>
          <span v-if="form.docType==='SALES_OUT'">平台等扣费 <b>¥ {{ money(estimatedDeductions) }}</b></span>
          <span v-if="form.docType==='SALES_OUT'">预计净入账 <b>¥ {{ money(estimatedNetReceipt) }}</b></span>
          <span v-if="canViewFinance && form.docType==='SALES_OUT'" :class="{loss:estimatedProfit<0}">预计毛利 <b>¥ {{ money(estimatedProfit) }}</b></span>
        </div>
        <div class="sheet-foot"><el-form label-width="110px"><el-form-item v-if="needsReason" :label="reasonLabel" required><el-input v-model="form.returnReason" :disabled="readonly"/></el-form-item><el-form-item v-if="readonly && form.docType==='CUSTOMER_RETURN' && form.unlinkedReason" label="历史未关联原因"><el-input v-model="form.unlinkedReason" disabled/></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" :disabled="readonly"/></el-form-item></el-form></div>
      </div>
      <template #footer>
        <el-button :disabled="!!savingAction" @click="dialog=false">关闭</el-button>
        <el-button v-if="!readonly" :loading="savingAction==='draft'" :disabled="!!savingAction" @click="save(false)">保存草稿</el-button>
        <el-button v-if="!readonly" type="primary" :loading="savingAction==='submit'" :disabled="!!savingAction"
          v-hasPermi="['jewelry:document:submit']" @click="save(true)">直接提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="productDialog" title="新增商品档案" width="640px" append-to-body destroy-on-close>
      <el-form ref="productFormRef" :model="quickProduct" :rules="productRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="SKU" prop="sku"><el-input v-model="quickProduct.sku" placeholder="请输入唯一商品编码"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品名称" prop="productName"><el-input v-model="quickProduct.productName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品类型" prop="productType"><el-select v-model="quickProduct.productType" style="width:100%"><el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类"><el-input v-model="quickProduct.category"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格类型" prop="specification"><el-select v-model="quickProduct.specification" style="width:100%"><el-option v-for="item in jewelrySpecifications" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="quickProduct.unit"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预警值"><el-input-number v-model="quickProduct.warningQty" :min="0" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="实物图片"><image-upload v-model="quickProduct.imageUrls" :limit="1" :file-size="8"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="productDialog=false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="saveQuickProduct">保存并选中</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog" title="Excel导入预览" width="88%" top="6vh"
      append-to-body destroy-on-close>
      <div class="import-summary">
        <el-tag type="success">可导入 {{ importPreview.validCount || 0 }} 行</el-tag>
        <el-tag v-if="importPreview.newProductCount" type="warning">新商品 {{ importPreview.newProductCount }} 个</el-tag>
        <el-tag :type="importPreview.errorCount ? 'danger' : 'info'">错误 {{ importPreview.errorCount || 0 }} 行</el-tag>
        <el-tag v-if="importCompression?.compressed" type="info">
          本地压缩 {{ formatFileSize(importCompression.originalSize) }} → {{ formatFileSize(importCompression.outputSize) }}
        </el-tag>
        <span v-if="importPreview.errorCount">请修正 Excel 中的错误后重新上传。</span>
      </div>
      <el-table :data="importPreview.rows || []" border max-height="520">
        <el-table-column prop="rowNumber" label="Excel行" width="76" align="center"/>
        <el-table-column label="状态" width="92">
          <template #default="{row}">
            <el-tag v-if="row.status==='VALID'" type="success">可导入</el-tag>
            <el-tag v-else-if="row.status==='NEW'" type="warning">新商品</el-tag>
            <el-tag v-else type="danger">有错误</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" width="150"/>
        <el-table-column prop="productName" label="商品名称" min-width="160"/>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" label="商品类型" width="110" align="center">
          <template #default="{row}">{{jewelryProductType(row.productType)?.label||'—'}}</template>
        </el-table-column>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" label="图片" width="82" align="center">
          <template #default="{row}">
            <el-image v-if="row.imageUrl" :src="imageSrc(row.imageUrl)" :preview-src-list="[imageSrc(row.imageUrl)]"
              preview-teleported fit="cover" style="width:46px;height:46px"/>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="form.docType!=='STOCK_ADJUST'" prop="qty" label="数量" width="86" align="right"/>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" prop="countedQty" label="实盘数量" width="100" align="right"/>
        <el-table-column v-if="form.docType!=='STOCK_ADJUST'" prop="unitPrice" :label="priceLabel" width="110" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee1" label="其他1/件" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee2" label="其他2/件" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee3" label="其他3/件" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="availableQty" label="可用库存" width="100" align="right"/>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" prop="lineReason" label="调整原因" min-width="150"/>
        <el-table-column prop="errorMessage" label="校验结果" min-width="240">
          <template #default="{row}"><span :class="{ 'import-error': !row.valid }">{{row.errorMessage || '校验通过'}}</span></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" label="操作" width="82" align="center" fixed="right">
          <template #default="{$index}">
            <el-button link type="danger" icon="Delete" @click="removeImportPreviewRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importDialog=false">取消</el-button>
        <el-button type="primary" :loading="applyingImport"
          :disabled="Number(importPreview.errorCount)>0 || Number(importPreview.validCount)<=0"
          @click="applyImportRows">导入到当前单据</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryDocument">
import {saveAs} from 'file-saver'
import {listJewelryDocuments,getJewelryDocument,getReturnInspectionSource,saveJewelryDocument,deleteJewelryDraft,assessJewelryDocumentRisk,submitJewelryDocument,withdrawJewelryDocument,createJewelryReversal,listJewelryProducts,listJewelryProductOptions,listJewelrySuppliers,saveJewelryProduct,downloadJewelryDocumentImportTemplate,previewJewelryDocumentImport} from '@/api/jewelry/erp'
import {compressXlsxImages,formatFileSize} from '@/utils/xlsxImageCompressor'
import {jewelryProductTypes,jewelrySpecifications,jewelryProductType} from '@/utils/jewelryProduct'
import useUserStore from '@/store/modules/user'
const {proxy}=getCurrentInstance(),rows=ref([]),total=ref(0),loading=ref(false),dialog=ref(false),readonly=ref(false),savingAction=ref(''),products=ref([]),suppliers=ref([]),salesDocuments=ref([]),returnDocuments=ref([])
const productDialog=ref(false),productSaving=ref(false),productFormRef=ref(),activeProductRow=ref(null)
const importDialog=ref(false),importLoading=ref(false),applyingImport=ref(false),importPreview=ref({})
const importCompression=ref(null)
const importProgress=reactive({active:false,percentage:0,text:''})
const blankQuickProduct=()=>({sku:'',productName:'',productType:'FINISHED',category:'',specification:'普通',imageUrl:'',imageUrls:'',unit:'件',warningQty:5,status:'0',defaultPackFee:0,defaultShipFee:0,defaultCertFee:0})
const quickProduct=reactive(blankQuickProduct())
const productRules={sku:[{required:true,message:'请输入SKU',trigger:'blur'}],productName:[{required:true,message:'请输入商品名称',trigger:'blur'}],productType:[{required:true,type:'enum',enum:jewelryProductTypes.map(item=>item.value),message:'请选择商品类型'}],specification:[{required:true,type:'enum',enum:jewelrySpecifications.map(item=>item.value),message:'请选择规格类型'}]}
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canDeleteDraft=row=>row.status==='DRAFT'&&String(row.creatorUserId)===String(userStore.id)
const isDualApproval=row=>['STOCK_ADJUST','COST_ADJUST'].includes(row?.docType)||(row?.docType==='REVERSAL'&&['STOCK_ADJUST','COST_ADJUST'].includes(row?.sourceDocType))
const types=[{value:'PURCHASE_IN',label:'采购入库'},{value:'SALES_OUT',label:'销售出库'},{value:'SUPPLIER_RETURN',label:'供应商退货'},{value:'CUSTOMER_RETURN',label:'客户退货'},{value:'RETURN_INSPECT',label:'退货质检'},{value:'STOCK_ADJUST',label:'库存调整'},{value:'COST_ADJUST',label:'库存成本调价'},{value:'ASSEMBLY',label:'手工组装'},{value:'REVERSAL',label:'红冲单'}]
const editableTypes=types.filter(item=>!['REVERSAL','ASSEMBLY'].includes(item.value))
const statuses=[{value:'DRAFT',label:'草稿'},{value:'PENDING_FIRST',label:'待审核'},{value:'PENDING_SECOND',label:'待审核'},{value:'POSTED',label:'已入账'},{value:'REJECTED',label:'已驳回'},{value:'REVERSED',label:'已红冲'}]
const query=reactive({pageNum:1,pageSize:10,docNo:'',docType:'',status:''})
const blankItem=()=>({productId:null,sourceItemId:null,itemRole:'NORMAL',bundleGroupNo:null,saleRole:'NORMAL',pricingMode:'SEPARATE',productTypeSnapshot:'',specificationSnapshot:'',imageUrls:'',qty:1,goodQty:0,defectQty:0,remainingInspectQty:0,systemQty:0,countedQty:0,adjustmentQty:0,unitPrice:0,unitCost:0,packFee:0,shipFee:0,certFee:0,otherFee1:0,otherFee2:0,otherFee3:0,lineReason:''})
const blank=()=>({documentId:null,docType:'PURCHASE_IN',bizDate:new Date().toISOString().slice(0,10),supplierId:null,supplierNameSnapshot:'',externalNo:'',salesChannel:'',influencerName:'',platformRate:0,commissionRate:0,taxRate:0,returnReason:'',sourceDocumentId:null,unlinkedReason:'',actualRefundAmount:null,riskStatus:'',remark:'',items:[blankItem()]})
const form=reactive(blank())
const serverRiskStatus=ref('')
let riskTimer=null,riskSequence=0
const showInspectColumns=computed(()=>form.docType==='RETURN_INSPECT'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)>0)))
const showAdjustmentColumn=computed(()=>form.docType==='STOCK_ADJUST'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.adjustmentQty||0)!==0)))
const showQuantityColumn=computed(()=>!showInspectColumns.value&&!showAdjustmentColumn.value&&form.docType!=='COST_ADJUST')
const needsSupplier=computed(()=>['PURCHASE_IN','SUPPLIER_RETURN'].includes(form.docType))
const needsSalesChannel=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType))
const showPriceColumn=computed(()=>['PURCHASE_IN','SALES_OUT','SUPPLIER_RETURN','CUSTOMER_RETURN','COST_ADJUST'].includes(form.docType))
const showCostColumn=computed(()=>form.docType==='COST_ADJUST'||(canViewFinance.value&&form.docType!=='PURCHASE_IN'))
const showSalesBundleColumns=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)||(readonly.value&&form.items?.some(item=>['MAIN','ADDON'].includes(item.saleRole))))
const excelImportSupported=computed(()=>['PURCHASE_IN','SALES_OUT','STOCK_ADJUST'].includes(form.docType))
const priceLabel=computed(()=>form.docType==='PURCHASE_IN'?'采购单价':form.docType==='SALES_OUT'?'成交单价':form.docType==='SUPPLIER_RETURN'?'退货单价':form.docType==='COST_ADJUST'?'调整后平均成本':'原成交单价')
const amountLabel=computed(()=>form.docType==='PURCHASE_IN'?'采购金额':form.docType==='SALES_OUT'?'成交总额':form.docType==='SUPPLIER_RETURN'?'退货金额':form.docType==='COST_ADJUST'?'库存金额变化':'原成交金额')
const totalAmountLabel=computed(()=>form.docType==='PURCHASE_IN'?'采购总额':form.docType==='SALES_OUT'?'成交总额':form.docType==='SUPPLIER_RETURN'?'退货总额':form.docType==='COST_ADJUST'?'库存金额变化':'退款总额')
const needsReason=computed(()=>['SUPPLIER_RETURN','CUSTOMER_RETURN','STOCK_ADJUST','COST_ADJUST'].includes(form.docType))
const reasonLabel=computed(()=>form.docType==='STOCK_ADJUST'?'调整原因':form.docType==='COST_ADJUST'?'调价原因':'退货原因')
const effectiveQty=row=>form.docType==='RETURN_INSPECT'?Number(row.goodQty||0)+Number(row.defectQty||0):form.docType==='STOCK_ADJUST'?Math.abs(Number(row.countedQty||0)-Number(row.systemQty||0)):Number(row.qty||0)
const lineAmount=row=>form.docType==='COST_ADJUST'?(Number(row.unitPrice||0)-Number(row.unitCost||0))*effectiveQty(row):Number(row.unitPrice||0)*effectiveQty(row)
const salesRate=computed(()=>Number(form.platformRate||0)+Number(form.commissionRate||0)+Number(form.taxRate||0))
const lineDeductions=row=>lineAmount(row)*salesRate.value
const lineNetReceipt=row=>lineAmount(row)-lineDeductions(row)
const isAccessoryPackaging=row=>normalizedSaleRole(row)==='ADDON'&&(row?.productTypeSnapshot||productOf(row)?.productType)==='ACCESSORY'
const accessoryPackagingMetrics=row=>{
  if(normalizedSaleRole(row)!=='MAIN'||!row.bundleGroupNo)return{accessoryTotal:0,manualTotal:0,shortage:0,effectiveTotal:Number(row.packFee||0)*effectiveQty(row)}
  const accessoryTotal=form.items.filter(item=>item.bundleGroupNo===row.bundleGroupNo&&isAccessoryPackaging(item))
    .reduce((sum,item)=>sum+Number(item.unitCost||0)*effectiveQty(item),0)
  const manualTotal=Number(row.packFee||0)*effectiveQty(row)
  return{accessoryTotal,manualTotal,shortage:Math.max(0,accessoryTotal-manualTotal),effectiveTotal:Math.max(accessoryTotal,manualTotal)}
}
const financialPackFeePerUnit=row=>{
  if(isAccessoryPackaging(row))return 0
  return Number(row.packFee||0)
}
const otherFeesPerUnit=row=>Number(row.otherFee1||0)+Number(row.otherFee2||0)+Number(row.otherFee3||0)
const lineProfit=row=>{if(isAccessoryPackaging(row))return 0;const qty=effectiveQty(row),amount=lineAmount(row),fees=(financialPackFeePerUnit(row)+Number(row.shipFee||0)+Number(row.certFee||0)+otherFeesPerUnit(row))*qty;return amount-Number(row.unitCost||0)*qty-fees-lineDeductions(row)}
const estimatedQty=computed(()=>form.items.reduce((sum,row)=>sum+effectiveQty(row),0))
const expectedReturnRefund=computed(()=>form.items.reduce((sum,row)=>sum+lineAmount(row),0))
const estimatedAmount=computed(()=>form.docType==='CUSTOMER_RETURN'?Number(form.actualRefundAmount||0):form.items.reduce((sum,row)=>sum+lineAmount(row),0))
const adjustedInventoryAmount=computed(()=>form.docType==='COST_ADJUST'?form.items.reduce((sum,row)=>sum+Number(row.unitPrice||0)*effectiveQty(row),0):0)
const estimatedDeductions=computed(()=>form.items.reduce((sum,row)=>sum+lineDeductions(row),0))
const estimatedNetReceipt=computed(()=>estimatedAmount.value-estimatedDeductions.value)
const estimatedProfit=computed(()=>form.docType==='SALES_OUT'?form.items.reduce((sum,row)=>sum+lineProfit(row),0):0)
const bundleSummaries=computed(()=>{
  const groups=new Map()
  for(const row of form.items){
    if(!row.bundleGroupNo||!['MAIN','ADDON'].includes(normalizedSaleRole(row)))continue
    const group=groups.get(row.bundleGroupNo)||{groupNo:row.bundleGroupNo,amount:0,cost:0,profit:0,accessoryTotal:0,manualPackagingTotal:0,packagingShortage:0}
    const qty=effectiveQty(row)
    const amount=lineAmount(row)
    const fees=(financialPackFeePerUnit(row)+Number(row.shipFee||0)+Number(row.certFee||0)+otherFeesPerUnit(row))*qty
    group.amount+=amount
    group.cost+=(isAccessoryPackaging(row)?0:Number(row.unitCost||0)*qty)+fees+lineDeductions(row)
    group.profit+=lineProfit(row)
    groups.set(row.bundleGroupNo,group)
  }
  for(const group of groups.values()){
    const main=form.items.find(item=>normalizedSaleRole(item)==='MAIN'&&item.bundleGroupNo===group.groupNo)
    if(main){const metrics=accessoryPackagingMetrics(main);group.accessoryTotal=metrics.accessoryTotal;group.manualPackagingTotal=metrics.manualTotal;group.packagingShortage=metrics.shortage}
  }
  return [...groups.values()].sort((a,b)=>a.groupNo-b.groupNo)
})
const accessoryPackagingProblems=computed(()=>bundleSummaries.value.filter(group=>group.packagingShortage>0))
const refundAmountDiffers=computed(()=>form.docType==='CUSTOMER_RETURN'&&!!form.sourceDocumentId&&form.actualRefundAmount!==null&&Math.abs(Number(form.actualRefundAmount)-expectedReturnRefund.value)>0.009)
const riskFingerprint=computed(()=>JSON.stringify({
  open:dialog.value,readonly:readonly.value,docType:form.docType,
  platformRate:form.platformRate,commissionRate:form.commissionRate,taxRate:form.taxRate,
  items:(form.items||[]).map(({productId,qty,unitPrice,packFee,shipFee,certFee,otherFee1,otherFee2,otherFee3,bundleGroupNo,saleRole,pricingMode})=>({productId,qty,unitPrice,packFee,shipFee,certFee,otherFee1,otherFee2,otherFee3,bundleGroupNo,saleRole,pricingMode}))
}))
watch(riskFingerprint,()=>{
  serverRiskStatus.value=''
  if(!readonly.value)form.riskStatus=''
  riskSequence+=1
  const sequence=riskSequence
  if(riskTimer)clearTimeout(riskTimer)
  const rates=[form.platformRate,form.commissionRate,form.taxRate].map(Number)
  const valid=form.docType==='SALES_OUT'&&dialog.value&&!readonly.value&&form.items?.length
    &&form.items.every(row=>row.productId&&Number(row.qty)>0&&[row.unitPrice,row.packFee,row.shipFee,row.certFee,row.otherFee1,row.otherFee2,row.otherFee3].every(value=>Number(value)>=0))
    &&rates.every(value=>Number.isFinite(value)&&value>=0&&value<=1)&&rates.reduce((sum,value)=>sum+value,0)<1
  if(!valid)return
  riskTimer=setTimeout(async()=>{
    try{
      const response=await assessJewelryDocumentRisk(form)
      if(sequence===riskSequence)serverRiskStatus.value=response.data?.riskStatus||''
    }catch(_error){/* 保存和提交时仍会由后端给出明确校验错误 */}
  },350)
})
const percentageModel=key=>computed({get:()=>Number(form[key]||0)*100,set:value=>{form[key]=Number(value||0)/100}})
const platformPercent=percentageModel('platformRate')
const commissionPercent=percentageModel('commissionRate')
const taxPercent=percentageModel('taxRate')
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const imageSrc=value=>/^https?:/i.test(value||'')?value:import.meta.env.VITE_APP_BASE_API+(value||'')
const labelOf=(list,value)=>list.find(x=>x.value===value)?.label||value;const statusType=s=>s==='POSTED'?'success':['REJECTED','REVERSED'].includes(s)?'danger':s==='DRAFT'?'info':'warning'
const documentStatusLabel=row=>isDualApproval(row)&&row.status==='PENDING_SECOND'?'待管理员复核':isDualApproval(row)&&row.status==='PENDING_FIRST'?'待审核员审核':labelOf(statuses,row.status)
async function preload(){const [p,s,sales,returns]=await Promise.all([listJewelryProductOptions({status:'0'}),listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'}),listJewelryDocuments({pageNum:1,pageSize:500,docType:'SALES_OUT',status:'POSTED'}),listJewelryDocuments({pageNum:1,pageSize:500,docType:'CUSTOMER_RETURN',status:'POSTED'})]);products.value=p.data||[];suppliers.value=s.rows||[];salesDocuments.value=sales.rows||[];returnDocuments.value=returns.rows||[]}
async function reloadProducts(purpose){const r=await listJewelryProductOptions({status:'0',...(purpose?{purpose}:{})});products.value=r.data||[]}
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function open(){Object.assign(form,blank());readonly.value=false;dialog.value=true}
function normalizeLoadedDocument(){form.items=(form.items||[]).map(item=>{const normalized={...blankItem(),...item,remainingInspectQty:item.remainingInspectQty??(form.docType==='RETURN_INSPECT'?Number(item.goodQty||0)+Number(item.defectQty||0):0),saleRole:item.saleRole||'NORMAL',pricingMode:item.pricingMode||'SEPARATE'};if(form.docType==='SALES_OUT'&&normalized.saleRole==='ADDON'&&normalized.productTypeSnapshot==='ACCESSORY'){normalized.pricingMode='INCLUDED';normalized.unitPrice=0;normalized.packFee=0;normalized.shipFee=0;normalized.certFee=0;normalized.otherFee1=0;normalized.otherFee2=0;normalized.otherFee3=0}return normalized});if(form.docType==='CUSTOMER_RETURN'&&form.actualRefundAmount===null)form.actualRefundAmount=Math.abs(Number(form.totalAmount||0))}
async function edit(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);normalizeLoadedDocument();if(form.docType==='COST_ADJUST')await reloadProducts('COST_ADJUST');if(form.docType==='RETURN_INSPECT'&&form.sourceDocumentId)await loadInspectionSource(form.sourceDocumentId,true);readonly.value=false;dialog.value=true}
async function view(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);normalizeLoadedDocument();readonly.value=true;dialog.value=true}
const normalizedSaleRole=row=>row?.saleRole||'NORMAL'
const normalizedPricingMode=row=>row?.pricingMode||'SEPARATE'
const productOf=row=>products.value.find(product=>product.productId===row.productId)
const availableProducts=row=>normalizedSaleRole(row)==='ADDON'?products.value.filter(product=>product.productType!=='FINISHED'):products.value
const saleGroupKey=row=>normalizedSaleRole(row)==='NORMAL'?'NORMAL':String(row.bundleGroupNo||'')
const canAddAddon=row=>normalizedSaleRole(row)!=='ADDON'&&productOf(row)?.productType==='FINISHED'
const nextBundleGroupNo=()=>Math.max(0,...form.items.map(item=>Number(item.bundleGroupNo||0)))+1
function productChanged(row){
  const product=productOf(row)
  if(!product)return
  if(normalizedSaleRole(row)==='ADDON'&&product.productType==='FINISHED'){proxy.$modal.msgWarning('搭售商品不能选择成品商品');row.productId=null;return}
  if(normalizedSaleRole(row)==='MAIN'&&product.productType!=='FINISHED'){proxy.$modal.msgWarning('销售组合主商品必须选择成品商品');row.productId=null;return}
  const duplicate=form.items.some(item=>item!==row&&item.productId===row.productId&&(form.docType!=='SALES_OUT'||saleGroupKey(item)===saleGroupKey(row)))
  if(duplicate){proxy.$modal.msgWarning(form.docType==='SALES_OUT'?'同一销售组合中不能重复选择同一商品':'同一商品不能重复，请直接修改已有行的数量');row.productId=null;return}
  row.productTypeSnapshot=product.productType||''
  row.specificationSnapshot=product.specification||''
  row.unitCost=Number(product.avgCost||0)
  row.systemQty=Number(product.onHandQty||0)
  row.countedQty=Number(product.onHandQty||0)
  if(form.docType==='COST_ADJUST'){
    row.qty=Number(product.onHandQty||0)
    row.unitPrice=Number(product.avgCost||0)
    row.packFee=0;row.shipFee=0;row.certFee=0;row.otherFee1=0;row.otherFee2=0;row.otherFee3=0
    return
  }
  if(normalizedSaleRole(row)==='ADDON'){
    row.packFee=0;row.shipFee=0;row.certFee=0;row.otherFee1=0;row.otherFee2=0;row.otherFee3=0
    if(product.productType==='ACCESSORY')row.pricingMode='INCLUDED'
    if(normalizedPricingMode(row)==='INCLUDED')row.unitPrice=0
  }else{
    row.packFee=form.docType==='CUSTOMER_RETURN'?0:Number(product.defaultPackFee||0)
    row.shipFee=Number(product.defaultShipFee||0)
    row.certFee=Number(product.defaultCertFee||0)
    row.otherFee1=0;row.otherFee2=0;row.otherFee3=0
  }
}
function addAddon(mainRow){
  if(normalizedSaleRole(mainRow)==='NORMAL'){
    mainRow.bundleGroupNo=nextBundleGroupNo()
    mainRow.saleRole='MAIN'
    mainRow.pricingMode='SEPARATE'
  }
  const addon={...blankItem(),bundleGroupNo:mainRow.bundleGroupNo,saleRole:'ADDON',pricingMode:'INCLUDED'}
  let insertAt=form.items.findIndex(item=>item===mainRow)+1
  while(insertAt<form.items.length&&form.items[insertAt].bundleGroupNo===mainRow.bundleGroupNo)insertAt+=1
  form.items.splice(insertAt,0,addon)
}
function pricingModeChanged(row){if(isAccessoryPackaging(row))row.pricingMode='INCLUDED';if(normalizedPricingMode(row)==='INCLUDED'){row.unitPrice=0;row.packFee=0;row.shipFee=0;row.certFee=0;row.otherFee1=0;row.otherFee2=0;row.otherFee3=0}}
function addNormalItem(){form.items.push(blankItem())}
async function removeItem(index){
  const row=form.items[index]
  if(form.docType==='SALES_OUT'&&normalizedSaleRole(row)==='MAIN'){
    const groupItems=form.items.filter(item=>item.bundleGroupNo===row.bundleGroupNo)
    if(groupItems.length>1)await proxy.$modal.confirm(`删除主商品会同时删除组合${row.bundleGroupNo}的搭售商品，确认继续吗？`)
    form.items=form.items.filter(item=>item.bundleGroupNo!==row.bundleGroupNo)
    return
  }
  const groupNo=row.bundleGroupNo
  form.items.splice(index,1)
  if(form.docType==='SALES_OUT'&&normalizedSaleRole(row)==='ADDON'&&!form.items.some(item=>normalizedSaleRole(item)==='ADDON'&&item.bundleGroupNo===groupNo)){
    const main=form.items.find(item=>normalizedSaleRole(item)==='MAIN'&&item.bundleGroupNo===groupNo)
    if(main){main.bundleGroupNo=null;main.saleRole='NORMAL';main.pricingMode='SEPARATE'}
  }
}
const bundleRowClass=({row})=>normalizedSaleRole(row)==='ADDON'?'bundle-addon-row':''
function openQuickProduct(row){activeProductRow.value=row;Object.assign(quickProduct,blankQuickProduct());productDialog.value=true}
async function saveQuickProduct(){await productFormRef.value.validate();productSaving.value=true;try{quickProduct.imageUrl=String(quickProduct.imageUrls||'').split(',')[0]||'';await saveJewelryProduct(quickProduct);const r=await listJewelryProducts({pageNum:1,pageSize:500,status:'0'});products.value=r.rows||[];const created=products.value.find(p=>p.sku===quickProduct.sku);if(!created)throw new Error('商品已保存，但未能重新加载，请刷新后选择');activeProductRow.value.productId=created.productId;activeProductRow.value.imageUrls=quickProduct.imageUrls||'';productChanged(activeProductRow.value);productDialog.value=false;proxy.$modal.msgSuccess('商品已新增并自动选中')}finally{productSaving.value=false}}
async function downloadImportTemplate(){
  const blob=await downloadJewelryDocumentImportTemplate(form.docType)
  saveAs(blob,`${labelOf(types,form.docType)}导入模板.xlsx`)
}
async function handleImportFile(uploadFile){
  if(!uploadFile?.raw)return
  importLoading.value=true
  importCompression.value=null
  try{
    let importFile=uploadFile.raw
    if(form.docType==='PURCHASE_IN'&&String(importFile.name||'').toLowerCase().endsWith('.xlsx')){
      importProgress.active=true
      importProgress.percentage=0
      importProgress.text='准备压缩'
      const compression=await compressXlsxImages(importFile,progress=>Object.assign(importProgress,progress))
      importFile=compression.file
      importCompression.value=compression
      if(compression.compressed){
        proxy.$modal.msgSuccess(`本地压缩完成：${formatFileSize(compression.originalSize)} → ${formatFileSize(compression.outputSize)}，处理 ${compression.compressed} 张图片`)
      }
      if(compression.skipped){
        proxy.$modal.msgWarning(`${compression.skipped} 张图片未能压缩，已保留原图`)
      }
      importProgress.text='正在上传并校验'
    }
    const r=await previewJewelryDocumentImport(form.docType,importFile)
    importPreview.value=r.data||{}
    importDialog.value=true
  }catch(error){
    proxy.$modal.msgError(error?.message||'Excel 导入失败')
  }finally{
    importLoading.value=false
    importProgress.active=false
  }
}
async function applyImportRows(){
  if(Number(importPreview.value.errorCount)>0||Number(importPreview.value.validCount)<=0)return
  if(form.items.some(item=>item.productId)){
    await proxy.$modal.confirm('导入会替换当前已经填写的商品明细，确认继续吗？')
  }
  applyingImport.value=true
  try{
    for(const row of importPreview.value.rows||[]){
      if(row.newProduct){
        await saveJewelryProduct({
          sku:row.sku,productName:row.productName,productType:row.productType,
          imageUrl:row.imageUrl||'',imageUrls:row.imageUrls||row.imageUrl||'',category:row.category||'',
          specification:row.specification||'',unit:row.unit||'件',warningQty:5,status:'0',
          defaultPackFee:0,defaultShipFee:0,defaultCertFee:0
        })
      }
    }
    await reloadProducts()
    const productMap=new Map(products.value.map(product=>[String(product.sku||'').trim().toUpperCase(),product]))
    form.items=(importPreview.value.rows||[]).map(row=>{
      const item=blankItem()
      const product=productMap.get(String(row.sku||'').trim().toUpperCase())
      if(!product)throw new Error(`SKU ${row.sku} 导入后未找到商品档案`)
      item.productId=product.productId
      item.imageUrls=row.imageUrls||row.imageUrl||product.imageUrls||product.imageUrl||''
      item.unitCost=Number(product.avgCost||0)
      item.systemQty=Number(product.onHandQty||0)
      item.countedQty=Number(product.onHandQty||0)
      item.packFee=Number(product.defaultPackFee||0)
      item.shipFee=Number(product.defaultShipFee||0)
      item.certFee=Number(product.defaultCertFee||0)
      if(form.docType==='STOCK_ADJUST'){
        item.countedQty=Number(row.countedQty||0)
        item.lineReason=row.lineReason||''
      }else{
        item.qty=Number(row.qty||0)
        item.unitPrice=Number(row.unitPrice||0)
        if(form.docType==='SALES_OUT'){
          item.packFee=Number(row.packFee||0)
          item.shipFee=Number(row.shipFee||0)
          item.certFee=Number(row.certFee||0)
          item.otherFee1=Number(row.otherFee1||0)
          item.otherFee2=Number(row.otherFee2||0)
          item.otherFee3=Number(row.otherFee3||0)
        }
      }
      return item
    })
    importDialog.value=false
    proxy.$modal.msgSuccess(`已导入 ${form.items.length} 行商品明细`)
  }finally{applyingImport.value=false}
}
function removeImportPreviewRow(index){
  const rows=[...(importPreview.value.rows||[])]
  if(index<0||index>=rows.length)return
  rows.splice(index,1)
  importPreview.value={
    ...importPreview.value,
    rows,
    validCount:rows.filter(row=>row.valid).length,
    errorCount:rows.filter(row=>!row.valid).length,
    newProductCount:rows.filter(row=>row.valid&&row.newProduct).length
  }
}
function supplierChanged(id){form.supplierNameSnapshot=suppliers.value.find(x=>x.supplierId===id)?.supplierName||''}
async function salesSourceChanged(id){
  form.unlinkedReason=''
  form.actualRefundAmount=null
  if(!id){form.items=[blankItem()];form.salesChannel='';form.influencerName='';form.platformRate=0;form.commissionRate=0;form.taxRate=0;return}
  const source=(await getJewelryDocument(id)).data
  form.salesChannel=source.salesChannel||''
  form.influencerName=source.influencerName||''
  form.platformRate=0
  form.commissionRate=0
  form.taxRate=0
  form.items=(source.items||[]).map(item=>({...blankItem(),productId:item.productId,qty:1,unitPrice:Number(item.unitPrice||0),unitCost:Number(item.unitCost||0),packFee:0,shipFee:Number(item.shipFee||0),certFee:Number(item.certFee||0),sourceItemId:item.itemId,bundleGroupNo:item.bundleGroupNo,saleRole:item.saleRole||'NORMAL',pricingMode:item.pricingMode||'SEPARATE',productTypeSnapshot:item.productTypeSnapshot||'',specificationSnapshot:item.specificationSnapshot||''}))
  form.actualRefundAmount=expectedReturnRefund.value
}
async function loadInspectionSource(id,preserveCurrent=false){
  const currentBySourceItem=new Map((form.items||[]).filter(item=>item.sourceItemId).map(item=>[item.sourceItemId,item]))
  const source=(await getReturnInspectionSource(id,form.documentId)).data
  form.items=(source.items||[]).map(sourceItem=>{
    const current=preserveCurrent?currentBySourceItem.get(sourceItem.itemId):null
    return {...blankItem(),...(current||{}),productId:sourceItem.productId,sourceItemId:sourceItem.itemId,
      qty:Number(sourceItem.remainingInspectQty||0),remainingInspectQty:Number(sourceItem.remainingInspectQty||0),
      unitCost:Number(sourceItem.unitCost||0),productTypeSnapshot:sourceItem.productTypeSnapshot||'',
      specificationSnapshot:sourceItem.specificationSnapshot||'',imageUrls:sourceItem.imageUrls||''}
  })
  if(!form.items.length)throw new Error('该客户退货单的商品已全部完成质检')
}
async function inspectionSourceChanged(id){
  if(!id){form.items=[blankItem()];return}
  try{await loadInspectionSource(id,false)}catch(error){form.sourceDocumentId=null;form.items=[blankItem()];proxy.$modal.msgError(error?.message||'加载客户退货单失败')}
}
async function typeChanged(){form.items=[blankItem()];form.supplierId=null;form.supplierNameSnapshot='';form.salesChannel='';form.platformRate=0;form.commissionRate=0;form.taxRate=0;form.returnReason='';form.sourceDocumentId=null;form.unlinkedReason='';form.actualRefundAmount=null;importPreview.value={};importCompression.value=null;if(form.docType==='COST_ADJUST')await reloadProducts('COST_ADJUST')}
function validateDocument(requireSubmit=false){
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){proxy.$modal.msgError('客户退货必须选择原销售单');return false}
  if(form.docType==='CUSTOMER_RETURN'&&(form.actualRefundAmount===null||Number(form.actualRefundAmount)<0)){proxy.$modal.msgError('请填写实际退款总额');return false}
  if(form.docType==='RETURN_INSPECT'&&!form.sourceDocumentId){proxy.$modal.msgError('退货质检必须选择原客户退货单');return false}
  if(!form.items.length||form.items.some(x=>!x.productId)){proxy.$modal.msgError('请完整选择商品');return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>!x.sourceItemId)){proxy.$modal.msgError('质检商品必须来自原客户退货单');return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)<=0)){proxy.$modal.msgError('每行至少填写一个良品或次品数量');return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)>Number(x.remainingInspectQty||0))){proxy.$modal.msgError('质检数量不能超过原退货单剩余待检数量');return false}
  if(needsSupplier.value&&!form.supplierId){proxy.$modal.msgError('请选择供应商');return false}
  if(needsSalesChannel.value&&!form.salesChannel.trim()){proxy.$modal.msgError('请填写销售渠道');return false}
  if(needsReason.value&&!form.returnReason.trim()){proxy.$modal.msgError(`请填写${reasonLabel.value}`);return false}
  if(form.docType==='COST_ADJUST'&&form.items.some(x=>Number(x.qty||0)<=0)){proxy.$modal.msgError('只能调整当前有库存的商品');return false}
  if(form.docType==='COST_ADJUST'&&form.items.some(x=>Number(x.unitPrice||0)===Number(x.unitCost||0))){proxy.$modal.msgError('调整后平均成本不能与当前平均成本相同');return false}
  if(requireSubmit&&form.docType==='SALES_OUT'&&accessoryPackagingProblems.value.length){const group=accessoryPackagingProblems.value[0];proxy.$modal.msgError(`组合${group.groupNo}配件耗材成本 ¥${money(group.accessoryTotal)}，高于包装费 ¥${money(group.manualPackagingTotal)}，还差 ¥${money(group.packagingShortage)}，请调整包装费后再提交`);return false}
  return true
}
async function save(andSubmit=false){
  if(!validateDocument(andSubmit))return
  savingAction.value=andSubmit?'submit':'draft'
  try{
    const response=await saveJewelryDocument(form)
    form.documentId=response.data?.documentId||form.documentId
    if(andSubmit){
      if(!form.documentId)throw new Error('单据已保存，但未返回单据ID')
      try{
        await submitJewelryDocument(form.documentId)
      }catch(error){
        proxy.$modal.msgError(`草稿已保存，但未提交：${error?.message||'请检查包装费和库存后重试'}`)
        return
      }
    }
    proxy.$modal.msgSuccess(andSubmit?'单据已直接提交审核':'草稿已保存')
    dialog.value=false
    load()
  }finally{savingAction.value=''}
}
async function submit(row){await proxy.$modal.confirm(`确认提交单据 ${row.docNo}？`);await submitJewelryDocument(row.documentId);proxy.$modal.msgSuccess('已提交');load()}
async function removeDraft(row){await proxy.$modal.confirm(`确认删除草稿 ${row.docNo}？删除后无法恢复。`);await deleteJewelryDraft(row.documentId);proxy.$modal.msgSuccess('草稿已删除');load()}
async function withdraw(row){await proxy.$modal.confirm(`确认撤回单据 ${row.docNo}？`);await withdrawJewelryDocument(row.documentId);proxy.$modal.msgSuccess('已撤回');load()}
async function reverse(row){await proxy.$modal.confirm(`确认对单据 ${row.docNo} 发起整单红冲？${['STOCK_ADJUST','COST_ADJUST'].includes(row.docType)?'红冲单需要审核员和管理员两级审批。':'红冲单审核通过后入账。'}`);await createJewelryReversal(row.documentId);proxy.$modal.msgSuccess('红冲草稿已生成');load()}
preload();load()
</script>
<style scoped>.sheet{border:1px solid #cfd5dc}.sheet-head{display:grid;grid-template-columns:repeat(6,minmax(150px,1fr));gap:12px;padding:14px;background:#f4f6f8}.sheet-head :deep(.el-form-item){margin:0}.sheet-head :deep(.el-input-number),.sheet-head :deep(.el-select),.sheet-head :deep(.el-date-editor){width:100%}.item-toolbar{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-top:1px solid #d9dee5;background:#fafbfc}.item-toolbar>div:first-child{display:flex;align-items:baseline;gap:10px}.item-toolbar b{color:#334155;font-size:14px}.item-toolbar span{color:#8490a0;font-size:12px}.item-toolbar-actions{display:flex;align-items:center;gap:8px}.excel-compress-progress{display:flex!important;flex-direction:column;align-items:stretch!important;gap:4px!important;width:180px}.excel-compress-progress span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.excel-compress-progress :deep(.el-progress){width:100%}.item-table{border-left:0;border-right:0}.item-table :deep(.el-input-number){width:100%;min-width:0}.item-table :deep(.bundle-addon-row){background:#fffaf0}.item-table :deep(.bundle-addon-row td:nth-child(2) .product-picker){padding-left:18px;border-left:3px solid #e6a23c}.product-picker{display:flex;align-items:center;gap:8px}.product-picker .el-select{flex:1;min-width:0}.product-picker .el-button{flex:none}.pack-fee-cell{display:flex;flex-direction:column;gap:3px}.pack-fee-cell small{line-height:1.35;color:#6b7280}.packaging-cost-note{color:#b45309}.packaging-shortage{color:#dc2626!important;font-weight:600}.packaging-covered{color:#16803c!important}.bundle-summaries{display:flex;gap:10px;flex-wrap:wrap;padding:10px 12px 0}.bundle-summaries>div{display:flex;gap:14px;align-items:center;padding:8px 12px;border:1px solid #f1d39c;border-radius:4px;background:#fffaf0;color:#6b7280;font-size:13px}.bundle-summaries b{color:#92400e}.add-line{margin:12px}.document-total{display:flex;justify-content:flex-end;gap:28px;padding:12px 16px;border-top:1px solid #d9dee5;background:#f8fafc;color:#475569}.document-total b{color:#111827}.loss,.document-total .loss,.document-total .loss b{color:#dc2626;font-weight:700}.sheet-foot{padding:12px 14px 0;border-top:1px solid #d9dee5}.import-summary{display:flex;align-items:center;gap:10px;margin-bottom:14px;flex-wrap:wrap}.import-summary span:last-child{color:#7c8796}.import-error{color:#c2413a}@media(max-width:1200px){.sheet-head{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.sheet-head{grid-template-columns:1fr}.item-toolbar{align-items:stretch;flex-direction:column;gap:10px}.item-toolbar>div:first-child{align-items:flex-start;flex-direction:column;gap:2px}.item-toolbar-actions{flex-wrap:wrap}.excel-compress-progress{width:100%}.product-picker{align-items:stretch;flex-direction:column}.bundle-summaries>div{align-items:flex-start;flex-direction:column;gap:4px}.document-total{justify-content:flex-start;flex-wrap:wrap;gap:12px 20px}}</style>
