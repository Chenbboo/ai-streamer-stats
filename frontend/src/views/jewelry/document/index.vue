<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.docNo" :placeholder="$tr(&quot;单号&quot;)" clearable/></el-form-item><el-form-item><el-select v-model="query.docType" :placeholder="$tr(&quot;全部类型&quot;)" clearable style="width:150px"><el-option v-for="o in types" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-select v-model="query.status" :placeholder="$tr(&quot;全部状态&quot;)" clearable style="width:140px"><el-option v-for="o in statuses" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="load">{{ $tr("查询") }}</el-button></el-form-item></el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:document:add']" @click="open()">{{ $tr("新建单据") }}</el-button>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="docNo" :label="$tr(&quot;单号&quot;)" width="190"/>
      <el-table-column :label="$tr(&quot;类型&quot;)" width="130"><template #default="{row}">{{labelOf(types,row.docType)}}</template></el-table-column>
      <el-table-column prop="bizDate" :label="$tr(&quot;业务日期&quot;)" width="110"/>
      <el-table-column :label="$tr(&quot;业务对象&quot;)" min-width="130"><template #default="{row}">{{isTransfer(row)?`${row.sourceWarehouse || '—'} → ${row.targetWarehouse || '—'}`:row.docType==='SALES_OUT'?(row.influencerName || row.salesChannel || '—'):row.supplierNameSnapshot || (row.docType==='ASSEMBLY'?$tr("手工组装"):row.docType==='COST_ADJUST'?$tr("库存成本调整"):'—')}}</template></el-table-column>
      <el-table-column prop="totalQty" :label="$tr(&quot;数量&quot;)" width="80" align="right"/>
      <el-table-column :label="$tr(&quot;金额&quot;)" width="120" align="right"><template #default="{row}">{{documentAmount(row.totalAmount,row)}}</template></el-table-column>
      <el-table-column v-if="canViewFinance" :label="$tr(&quot;毛利&quot;)" width="110" align="right"><template #default="{row}"><span v-if="row.docType==='COST_ADJUST' || isTransfer(row)">—</span><span v-else :class="{loss:Number(row.totalProfit)<0}">{{money(row.totalProfit)}}</span></template></el-table-column>
      <el-table-column :label="$tr(&quot;风险&quot;)" width="100"><template #default="{row}"><el-tag v-if="row.riskStatus==='LOSS'" type="danger">{{ $tr("亏损") }}</el-tag><el-tag v-else-if="row.riskStatus==='REVIEW'" type="warning">{{ $tr("需复核") }}</el-tag><span v-else>—</span></template></el-table-column>
      <el-table-column :label="$tr(&quot;状态&quot;)" width="130"><template #default="{row}"><el-tag :type="statusType(row.status)">{{documentStatusLabel(row)}}</el-tag></template></el-table-column>
      <el-table-column prop="creatorName" :label="$tr(&quot;制单人&quot;)" width="100"/>
      <el-table-column :label="$tr(&quot;审批人&quot;)" width="150"><template #default="{row}"><span v-if="isDualApproval(row) && row.firstReviewerName">{{row.firstReviewerName}}<template v-if="row.secondReviewerName"> / {{row.secondReviewerName}}</template></span><span v-else>{{['POSTED','REVERSED'].includes(row.status)?(row.secondReviewerName||row.firstReviewerName||'—'):'—'}}</span></template></el-table-column>
      <el-table-column :label="$tr(&quot;操作&quot;)" width="285" fixed="right"><template #default="{row}"><el-button link type="primary" @click="view(row)">{{ $tr("查看") }}</el-button><el-button v-if="['DRAFT','REJECTED'].includes(row.status) && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="primary" v-hasPermi="['jewelry:document:edit']" @click="edit(row)">{{ $tr("编辑") }}</el-button><el-button v-if="row.status==='DRAFT' || (row.docType==='REVERSAL' && row.status==='REJECTED')" link type="success" v-hasPermi="['jewelry:document:submit']" @click="submit(row)">{{ $tr("提交") }}</el-button><el-button v-if="canDeleteDraft(row)" link type="danger" v-hasPermi="['jewelry:document:edit']" @click="removeDraft(row)">{{ $tr("删除") }}</el-button><el-button v-if="row.status==='PENDING_FIRST'" link type="warning" v-hasPermi="['jewelry:document:withdraw']" @click="withdraw(row)">{{ $tr("撤回") }}</el-button><el-button v-if="row.status==='POSTED' && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="danger" v-hasPermi="['jewelry:document:reverse']" @click="reverse(row)">{{ $tr("红冲") }}</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="dialog" :title="readonly?$tr(&quot;查看单据&quot;):(form.documentId?$tr(&quot;编辑单据&quot;):$tr(&quot;新建单据&quot;))" width="94%" top="4vh" destroy-on-close>
      <div class="sheet">
        <el-alert v-if="form.docType === 'REVERSAL'" :title="isDualApproval(form)?$tr(&quot;该红冲涉及库存调整，仍需审核员初审和管理员复核后入账。&quot;):$tr(&quot;红冲单明细来自原单，不允许修改；提交后由审核员审核通过即可入账。&quot;)" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType === 'STOCK_ADJUST'" :title="$tr(&quot;库存调整单提交后先由审核员初审，再由管理员复核；复核通过后才真正调整库存。&quot;)" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType === 'COST_ADJUST'" :title="$tr(&quot;库存成本调价单提交后，将先由审核员审核，再由管理员复核；复核通过后才修改库存平均成本。审批期间对应SKU不能采购入库。&quot;)" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType === 'SAMPLE_IN'" :title="$tr(&quot;每行手动填写SKU、样品商品、业务日期、供应商和数量，可上传实物图片；单价与本次入库成本固定为0。审核通过后增加库存。&quot;)" type="info" :closable="false" show-icon />
        <el-form :model="form" label-position="top"><div class="sheet-head">
          <el-form-item :label="$tr(&quot;单据类型&quot;)" required><el-select v-model="form.docType" :disabled="readonly" @change="typeChanged"><el-option v-for="o in editableTypes" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item>
          <el-form-item v-if="form.docType!=='SAMPLE_IN'" :label="isTransfer(form)?$tr(&quot;调货时间&quot;):$tr(&quot;业务日期&quot;)" required><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='PURCHASE_IN'" :label="$tr(&quot;约定退货日期&quot;)">
            <el-date-picker v-model="form.supplierReturnDate" type="date" value-format="YYYY-MM-DD"
              :placeholder="$tr(&quot;留空按统一退货期限&quot;)" clearable :disabled="readonly"/>
            <el-button v-if="readonly && form.status==='POSTED'" v-hasPermi="['jewelry:stock:config']"
              link type="primary" @click="openReturnDate">{{ $tr("设置特殊日期") }}</el-button>
          </el-form-item>
          <el-form-item v-if="isTransfer(form)" :label="$tr(&quot;出库仓库&quot;)" required><el-input v-model="form.sourceWarehouse" maxlength="100" :placeholder="$tr(&quot;当前仓库名称&quot;)" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="isTransfer(form)" :label="$tr(&quot;入库仓库&quot;)" required><el-input v-model="form.targetWarehouse" maxlength="100" :placeholder="$tr(&quot;调往仓库名称&quot;)" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='PURCHASE_IN'" :label="$tr(&quot;达人/主播（成品、赠品必选）&quot;)">
            <el-select v-model="form.influencerId" filterable clearable :disabled="readonly" :placeholder="$tr(&quot;采购成品或赠品时必选，其他类型可不选&quot;)" @change="influencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId" :label="`${item.influencerName} · ${item.platform || ''}`" :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='SUPPLIER_RETURN'" :label="$tr(&quot;达人/主播&quot;)" required>
            <el-select v-model="form.influencerId" filterable clearable :disabled="readonly" :placeholder="$tr(&quot;请先选择达人/主播&quot;)" @change="supplierReturnInfluencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId"
                :label="$tr(&quot;{0}{1} · {2} · 已定价{3}种{4}&quot;, [item.influencerName, item.externalInfluencerId?`（ID：${item.externalInfluencerId}）`:'', item.platform || $tr(&quot;未填平台&quot;), Number(item.pricedProductCount||0), Number(item.pendingProductCount||0)>0?$tr(&quot; / 待生效{0}种&quot;, [Number(item.pendingProductCount)]):''])"
                :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" :label="$tr(&quot;达人/主播&quot;)" required>
            <el-select v-model="form.influencerId" filterable clearable :disabled="readonly" :placeholder="$tr(&quot;请先选择达人/主播&quot;)" @change="customerReturnInfluencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId"
                :label="$tr(&quot;{0}{1} · {2} · 已定价{3}种{4}&quot;, [item.influencerName, item.externalInfluencerId?`（ID：${item.externalInfluencerId}）`:'', item.platform || $tr(&quot;未填平台&quot;), Number(item.pricedProductCount||0), Number(item.pendingProductCount||0)>0?$tr(&quot; / 待生效{0}种&quot;, [Number(item.pendingProductCount)]):''])"
                :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="needsSupplier" :label="$tr(&quot;供应商&quot;)" required><el-select v-model="form.supplierId" filterable clearable :disabled="readonly || (form.docType==='PURCHASE_IN' && purchaseHasBoundRows && !form.influencerId) || (form.docType==='SUPPLIER_RETURN' && !form.influencerId)" :placeholder="((form.docType==='PURCHASE_IN' && purchaseHasBoundRows) || form.docType==='SUPPLIER_RETURN') && !form.influencerId?$tr(&quot;请先选择达人/主播&quot;):$tr(&quot;请选择&quot;)" @change="supplierChanged"><el-option v-for="s in documentSupplierOptions" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId"/></el-select></el-form-item>
          <el-form-item v-if="form.docType==='SUPPLIER_RETURN'" :label="$tr(&quot;原采购单&quot;)" required>
            <el-input v-if="readonly" :model-value="form.sourceDocNo || form.sourceDocumentId" disabled />
            <el-select v-else v-model="form.sourceDocumentId" filterable clearable :disabled="!form.supplierId" @change="supplierReturnSourceChanged">
              <el-option v-for="d in purchaseDocuments" :key="d.documentId"
                :label="$tr(&quot;{0} · {1} · 采购 ¥{2}&quot;, [d.docNo, d.bizDate, fourDecimalMoney(Math.abs(Number(d.totalAmount||0)))])" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isTransfer(form) && form.docType!=='SAMPLE_IN'" :label="$tr(&quot;外部单号&quot;)"><el-input v-model="form.externalNo" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" :label="$tr(&quot;原销售单（可选）&quot;)">
            <el-select v-model="form.sourceDocumentId" filterable clearable :loading="salesSourceLoading" :no-data-text="salesSourceError?$tr(&quot;加载失败，请重新打开下拉框重试&quot;):$tr(&quot;该达人暂无已入账销售单，待审核单不能关联退货&quot;)" :disabled="readonly || !form.influencerId" :placeholder="form.influencerId?$tr(&quot;请选择&quot;):$tr(&quot;请先选择达人/主播&quot;)" @visible-change="customerReturnSalesOpened" @change="salesSourceChanged">
              <el-option v-for="d in customerReturnSalesDocuments" :key="d.documentId"
                :label="`${d.docNo} · ${d.bizDate} · ${d.salesChannel || $tr(&quot;未填写渠道&quot;)}`" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='RETURN_INSPECT'" :label="$tr(&quot;达人/主播&quot;)" required>
            <el-select v-model="form.influencerId" filterable clearable :disabled="readonly" :placeholder="$tr(&quot;请先选择达人/主播&quot;)" @change="inspectionInfluencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId" :label="`${item.influencerName} · ${item.platform || $tr(&quot;未填平台&quot;)}`" :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='RETURN_INSPECT'" :label="$tr(&quot;原客户退货单&quot;)" required>
            <el-input v-if="readonly" :model-value="form.sourceDocNo || form.sourceDocumentId" disabled/>
            <el-select v-else v-model="form.sourceDocumentId" filterable clearable :disabled="!form.influencerId" :placeholder="form.influencerId?$tr(&quot;请选择&quot;):$tr(&quot;请先选择达人/主播&quot;)" @change="inspectionSourceChanged">
              <el-option v-for="d in inspectionReturnDocuments" :key="d.documentId"
                :label="`${d.docNo} · ${d.bizDate} · ${d.salesChannel || $tr(&quot;未填写渠道&quot;)}`" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" :label="$tr(&quot;实际退款总额&quot;)" required>
            <el-input-number v-model="form.actualRefundAmount" :min="0" :precision="2" :disabled="readonly" @change="actualRefundTotalChanged" />
          </el-form-item>
          <el-form-item v-if="needsSalesChannel" :label="$tr(&quot;销售渠道&quot;)" required><el-input v-model="form.salesChannel" :disabled="readonly || !!form.sourceDocumentId"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;达人/主播&quot;)" required>
            <el-select v-model="form.influencerId" filterable :clearable="false" :placeholder="$tr(&quot;请选择&quot;)" :disabled="readonly" @change="influencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId"
                :label="$tr(&quot;{0}{1} · {2} · 已定价{3}种{4}&quot;, [item.influencerName, item.externalInfluencerId?`（ID：${item.externalInfluencerId}）`:'', item.platform || $tr(&quot;未填平台&quot;), Number(item.pricedProductCount||0), Number(item.pendingProductCount||0)>0?$tr(&quot; / 待生效{0}种&quot;, [Number(item.pendingProductCount)]):''])"
                :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="['PURCHASE_IN','SALES_OUT','CUSTOMER_RETURN'].includes(form.docType) && form.influencerId" :label="$tr(&quot;商品定价情况&quot;)">
            <el-input :model-value="selectedInfluencerPriceSummary" disabled/>
          </el-form-item>
        </div></el-form>
        <el-alert v-if="form.docType==='PURCHASE_IN' && !readonly" :title="$tr(&quot;成品、赠品必须选择达人，再选择其绑定的供应商和商品；散件、配件、福利商品可不选达人，直接选择供应商。&quot;)" type="info" :closable="false" show-icon />
        <el-alert v-if="form.docType==='PURCHASE_IN' && purchaseHasBoundRows && form.influencerId && !purchaseSupplierOptions.length && !readonly" :title="$tr(&quot;该达人暂无已绑定供应商的成品或赠品商品，请先在达人档案中设置供应商和商品绑定。&quot;)" type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType==='SALES_OUT' && !readonly" :title="form.influencerId?$tr(&quot;独立销售和组合主商品按达人绑定配置带入价格和费率；搭售可选择有可用库存的配件商品，或当前达人已绑定且有可用库存的赠品商品。&quot;):$tr(&quot;请先选择达人/主播，再选择销售商品。&quot;)" type="info" :closable="false" show-icon />
        <el-alert v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId && !readonly"
          :title="$tr(&quot;请先选择达人/主播。先选择该达人已绑定的成品，再点“选择搭售退货”添加历史上随该成品售出的配件或赠品；每项退货数量均受已售及剩余可退数量限制。&quot;)"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType==='CUSTOMER_RETURN' && form.sourceDocumentId && form.items.some(item=>normalizedSaleRole(item)==='ADDON') && !readonly"
          :title="$tr(&quot;已按原销售组合带出主商品和搭售散件。修改主商品退货数量会按原组合比例同步散件数量；未实际退回的散件可单独修改数量或删除。&quot;)"
          type="success" :closable="false" show-icon />
        <el-alert v-if="form.docType==='SUPPLIER_RETURN' && !form.sourceDocumentId && !readonly"
          :title="$tr(&quot;请依次选择达人/主播、该达人绑定商品所对应的供应商，以及同达人同供应商的已入账采购单。剩余可退取原采购单剩余额度与当前可用库存的较小值，已扣除其他待审出库占用。&quot;)"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="form.docType==='RETURN_INSPECT' && !form.sourceDocumentId && !readonly"
          :title="$tr(&quot;退货质检请先选择达人/主播，再选择该达人已入账的客户退货单，系统会带出尚未处理的退货明细。&quot;)"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="refundAmountDiffers" :title="$tr(&quot;实际退款 ¥{0} 与系统应退金额 ¥{1} 不一致，提交后将标记为需复核。&quot;, [money(form.actualRefundAmount), money(expectedReturnRefund)])"
          type="warning" :closable="false" show-icon />
        <el-alert v-if="(canViewFinance && estimatedProfit < 0) || serverRiskStatus==='LOSS' || form.riskStatus==='LOSS'" :title="$tr(&quot;当前销售单预计亏损，提交后审批页面将显示亏损风险。&quot;)" type="error" :closable="false" show-icon />
        <div v-if="excelImportSupported && !readonly" class="item-toolbar">
          <div>
            <b>{{ $tr("商品明细") }}</b>
            <span>{{ $tr("支持通过 Excel 批量填充，导入后仍可修改") }}</span>
          </div>
          <div class="item-toolbar-actions">
            <div v-if="importProgress.active" class="excel-compress-progress">
              <span>{{ importProgress.text }}</span>
              <el-progress :percentage="importProgress.percentage" :stroke-width="5" :show-text="false" />
            </div>
            <el-button icon="Download" @click="downloadImportTemplate">{{ $tr("下载模板") }}</el-button>
            <el-upload action="#" :accept="['PURCHASE_IN','SAMPLE_IN'].includes(form.docType)?'.xlsx':'.xls,.xlsx'" :auto-upload="false" :show-file-list="false"
              :on-change="handleImportFile">
              <el-button type="primary" plain icon="Upload" :loading="importLoading">{{ $tr("Excel导入") }}</el-button>
            </el-upload>
          </div>
        </div>
        <div v-if="isTransfer(form)" class="item-toolbar"><b>{{ $tr("商品明细") }}</b><span>{{ $tr("仅扣减当前仓库库存；接收方另做采购入库。") }}</span></div>
        <el-form v-if="supportsProductFilters && !readonly" inline class="product-filter-bar">
          <el-form-item :label="$tr(&quot;商品类型&quot;)">
            <el-select v-model="productFilters.productType" :placeholder="$tr(&quot;全部商品类型&quot;)" clearable>
              <el-option v-for="type in jewelryProductTypes" :key="type.value" :label="type.label" :value="type.value"/>
            </el-select>
          </el-form-item>
          <el-form-item :label="$tr(&quot;达人/主播&quot;)">
            <el-select v-model="productFilters.influencerId" :placeholder="$tr(&quot;请选择&quot;)" filterable clearable
              :loading="productFilterLoading" @change="productFilterInfluencerChanged">
              <el-option v-for="item in influencers" :key="item.influencerId" :label="`${item.influencerName} · ${item.platform || ''}`" :value="item.influencerId"/>
            </el-select>
          </el-form-item>
          <el-form-item :label="$tr(&quot;供应商&quot;)">
            <el-select v-model="productFilters.supplierId" :placeholder="$tr(&quot;全部供应商&quot;)" filterable clearable>
              <el-option v-for="supplier in suppliers" :key="supplier.supplierId" :label="supplier.supplierName" :value="supplier.supplierId"/>
            </el-select>
          </el-form-item>
          <el-form-item><el-button @click="resetProductFilters">{{ $tr("重置") }}</el-button></el-form-item>
        </el-form>
        <el-table :data="displayItems" border class="item-table" :row-class-name="bundleRowClass">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column v-if="form.docType==='PURCHASE_IN'" :label="$tr(&quot;商品类型&quot;)" width="150">
            <template #default="{row}"><el-select v-model="row.productTypeSnapshot" :placeholder="$tr(&quot;请选择商品类型&quot;)" :disabled="readonly" @change="purchaseTypeChanged(row)">
              <el-option v-for="type in purchaseProductTypes" :key="type.value" :label="type.label" :value="type.value"/>
            </el-select></template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SAMPLE_IN'" label="SKU" width="180">
            <template #default="{row}">
              <span v-if="readonly">{{row.skuSnapshot || productOf(row)?.sku || '—'}}</span>
              <el-input v-else v-model.trim="row.sampleSkuInput" maxlength="64" :placeholder="$tr(&quot;请输入SKU&quot;)" @change="sampleSkuChanged(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="$tr(&quot;商品&quot;)" min-width="390">
            <template #default="{ row }">
              <div class="product-picker">
                <el-select v-model="row.productId" filterable :loading="supportsProductFilters && productFilterLoading || customerReturnProductLoading"
                  :no-data-text="customerReturnNoDataText(row)"
                  :placeholder="form.docType==='PURCHASE_IN' && !row.productTypeSnapshot?$tr(&quot;请先选择商品类型&quot;):form.docType==='PURCHASE_IN' && !form.supplierId?$tr(&quot;请先选择供应商&quot;):['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)&&!form.influencerId?$tr(&quot;请先选择达人&quot;):$tr(&quot;请选择&quot;)" :disabled="readonly || (form.docType==='PURCHASE_IN' && (!row.productTypeSnapshot || !form.supplierId || (isPurchaseBoundType(row.productTypeSnapshot) && !form.influencerId))) || (['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)&&!form.influencerId) || ['SUPPLIER_RETURN','RETURN_INSPECT'].includes(form.docType) || (form.docType==='CUSTOMER_RETURN' && !!form.sourceDocumentId)" @change="productChanged(row)">
                  <el-option v-for="p in availableProducts(row)" :key="p.productId" :label="productOptionLabel(p,row)" :value="p.productId" :disabled="productOptionDisabled(row,p)" />
                </el-select>
                <el-button v-if="(form.docType==='PURCHASE_IN' && row.productTypeSnapshot && !isPurchaseBoundType(row.productTypeSnapshot) && form.supplierId || form.docType==='SAMPLE_IN') && !readonly" type="primary" plain icon="Plus"
                  v-hasPermi="['jewelry:product:add']" @click="openQuickProduct(row)">{{ $tr("新增商品") }}</el-button>
                <el-button v-if="!readonly && canAddAddon(row) && (form.docType==='SALES_OUT' || isUnlinkedInfluencerReturn())" type="warning" plain icon="Plus"
                  :disabled="isUnlinkedInfluencerReturn() && !customerReturnAddonStats(row).some(item=>Number(item.remainingReturnQty||0)>0)"
                  @click="addAddon(row)">{{ form.docType==='CUSTOMER_RETURN'?$tr("选择搭售退货"):$tr("搭售商品") }}</el-button>
              </div>
              <div v-if="form.docType==='SALES_OUT' && normalizedSaleRole(row)==='MAIN' && includedAddonRows(row).length" class="included-addons">
                <el-button link type="primary" @click="toggleIncludedAddons(row)">{{ expandedBundleGroups[row.bundleGroupNo] ? $tr('收起组合内搭售') : $tr('查看/编辑组合内搭售（{0}种）', [includedAddonRows(row).length]) }}</el-button>
                <span v-if="!expandedBundleGroups[row.bundleGroupNo]" class="included-addon-summary">
                  <span v-for="(addon, addonIndex) in includedAddonRows(row)" :key="addon.itemId || addonIndex">
                    {{productOf(addon)?.sku || addon.skuSnapshot || $tr('待选商品')}} × {{effectiveQty(addon)}}{{addonIndex<includedAddonRows(row).length-1?'、':''}}
                  </span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;供应商&quot;)" min-width="170" show-overflow-tooltip>
            <template #default="{row}">{{productOf(row)?.supplierNames || '—'}}</template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SAMPLE_IN'" :label="$tr(&quot;业务日期&quot;)" width="180">
            <template #default="{row}"><el-date-picker v-model="row.bizDate" type="date" value-format="YYYY-MM-DD" :placeholder="$tr(&quot;请选择日期&quot;)" :disabled="readonly" style="width:100%" /></template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SAMPLE_IN'" :label="$tr(&quot;供应商&quot;)" width="190">
            <template #default="{row}">
              <span v-if="readonly">{{row.supplierNameSnapshot || '—'}}</span>
              <el-select v-else v-model="row.supplierId" filterable clearable :placeholder="$tr(&quot;请选择供应商&quot;)">
                <el-option v-for="supplier in suppliers" :key="supplier.supplierId" :label="supplier.supplierName" :value="supplier.supplierId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" :label="$tr(&quot;销售角色&quot;)" width="130">
            <template #default="{row}">
              <el-tag v-if="normalizedSaleRole(row)==='MAIN'" type="success" effect="plain">{{ $tr("组合{0}·主商品", [row.bundleGroupNo]) }}</el-tag>
              <el-tag v-else-if="normalizedSaleRole(row)==='ADDON'" type="warning" effect="plain">{{ $tr("组合{0}·搭售", [row.bundleGroupNo]) }}</el-tag>
              <el-tag v-else type="info" effect="plain">{{ form.docType==='CUSTOMER_RETURN'?$tr("独立退货"):$tr("独立销售") }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" :label="$tr(&quot;搭售用途&quot;)" width="110">
            <template #default="{row}">
              <el-tag v-if="form.docType==='SALES_OUT' && isAccessoryPackaging(row)" type="warning" effect="plain">{{ $tr("包装耗材") }}</el-tag>
              <span v-else-if="normalizedSaleRole(row)==='ADDON'">{{ form.docType==='CUSTOMER_RETURN'?$tr("一并退回"):$tr("普通搭售") }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column v-if="showSalesBundleColumns" :label="$tr(&quot;计价方式&quot;)" width="145">
            <template #default="{row}">
              <el-select v-if="form.docType==='SALES_OUT' && normalizedSaleRole(row)==='ADDON'" v-model="row.pricingMode"
                :disabled="readonly || isAccessoryPackaging(row)" @change="pricingModeChanged(row)">
                <el-option :label="$tr(&quot;包含在组合价&quot;)" value="INCLUDED" />
                <el-option :label="$tr(&quot;单独计价&quot;)" value="SEPARATE" />
              </el-select>
              <span v-else>{{normalizedPricingMode(row)==='INCLUDED'?$tr("包含在组合价"):$tr("单独计价")}}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='ASSEMBLY'" :label="$tr(&quot;角色&quot;)" width="90">
            <template #default="{row}"><el-tag :type="row.itemRole==='OUTPUT'?'success':'warning'" effect="plain">{{row.itemRole==='OUTPUT'?$tr("成品产出"):$tr("散件投入")}}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="['PURCHASE_IN','SAMPLE_IN'].includes(form.docType) || (readonly && form.items.some(item=>item.imageUrls))" :label="$tr(&quot;实物图片&quot;)" width="190">
            <template #default="{row}">
              <image-upload v-model="row.imageUrls" :limit="1" :file-size="8" :disabled="readonly"/>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='RETURN_INSPECT'" :label="$tr(&quot;剩余待检&quot;)" width="110" align="right">
            <template #default="{ row }">{{ row.remainingInspectQty }}</template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" :label="$tr(&quot;良品数&quot;)" width="120">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.goodQty }}</span>
              <el-input-number v-else v-model="row.goodQty" :min="0" :max="form.docType==='RETURN_INSPECT'?Math.max(0,Number(row.remainingInspectQty||0)-Number(row.defectQty||0)):undefined" />
            </template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" :label="$tr(&quot;次品数&quot;)" width="120">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.defectQty }}</span>
              <el-input-number v-else v-model="row.defectQty" :min="0" :max="form.docType==='RETURN_INSPECT'?Math.max(0,Number(row.remainingInspectQty||0)-Number(row.goodQty||0)):undefined" />
            </template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" :label="$tr(&quot;系统库存&quot;)" width="110">
            <template #default="{ row }">{{ row.systemQty }}</template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" :label="$tr(&quot;实盘库存&quot;)" width="130">
            <template #default="{ row }"><el-input-number v-model="row.countedQty" :min="0" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" :label="$tr(&quot;差异数量&quot;)" width="100">
            <template #default="{ row }">{{ Number(row.countedQty || 0) - Number(row.systemQty || 0) }}</template>
          </el-table-column>
          <el-table-column v-if="form.docType==='COST_ADJUST'" :label="$tr(&quot;当前库存&quot;)" width="110" align="right"><template #default="{row}">{{row.qty}}</template></el-table-column>
          <el-table-column v-if="form.docType==='SUPPLIER_RETURN'" :label="$tr(&quot;剩余可退&quot;)" width="105" align="right"><template #default="{row}">{{row.remainingReturnQty}}</template></el-table-column>
          <el-table-column v-if="form.docType==='CUSTOMER_RETURN' && form.sourceDocumentId" :label="$tr(&quot;原销售数量&quot;)" width="105" align="right"><template #default="{row}">{{row.sourceQty}}</template></el-table-column>
          <el-table-column v-if="form.docType==='CUSTOMER_RETURN' && form.sourceDocumentId" :label="$tr(&quot;剩余可退&quot;)" width="105" align="right"><template #default="{row}">{{row.remainingReturnQty}}</template></el-table-column>
          <el-table-column v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId" :label="$tr(&quot;已售数量&quot;)" width="105" align="right"><template #default="{row}">{{row.soldQty||0}}</template></el-table-column>
          <el-table-column v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId" :label="$tr(&quot;剩余可退&quot;)" width="105" align="right"><template #default="{row}">{{row.remainingReturnQty||0}}</template></el-table-column>
          <el-table-column v-if="showQuantityColumn" :label="form.docType==='CUSTOMER_RETURN'?$tr(&quot;退货数量&quot;):$tr(&quot;数量&quot;)" width="130">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.qty }}</span>
              <el-input-number v-else v-model="row.qty" :min="1" :max="linkedReturnMaxQty(row)"
                :disabled="form.docType==='SUPPLIER_RETURN' && (!form.sourceDocumentId || Number(row.remainingReturnQty || 0)<=0) || form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId && (!row.productId || Number(row.remainingReturnQty || 0)<=0)"
                @change="linkedReturnQtyChanged(row)" />
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SUPPLIER_RETURN'" :label="$tr(&quot;原采购单价&quot;)" width="125" align="right"><template #default="{row}">{{fourDecimalMoney(row.sourceUnitPrice)}}</template></el-table-column>
          <el-table-column v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId" :label="$tr(&quot;系统应退单价&quot;)" width="135" align="right">
            <template #default="{row}">{{row.influencerPriceSnapshot==null?'—':fourDecimalMoney(row.influencerPriceSnapshot)}}</template>
          </el-table-column>
          <el-table-column v-if="showPriceColumn" :label="priceLabel" width="180"><template #default="{row}">
            <div class="unit-price-cell">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="unitPricePrecision" :step="unitPriceStep" :disabled="readonly || (form.docType==='CUSTOMER_RETURN' && !!form.sourceDocumentId) || (form.docType==='SALES_OUT' && (normalizedPricingMode(row)==='INCLUDED' || Number(row.influencerPriceVersion||0)>0))" style="width:100%"/>
              <small v-if="isUnlinkedInfluencerReturn() && row.productId && row.influencerPriceSnapshot==null" class="pending-price-note">{{ $tr("无可用达人固定价，请手填；提交后需复核") }}</small>
              <small v-if="form.docType==='SALES_OUT' && normalizedPricingMode(row)!=='INCLUDED' && row.productId" :class="row.influencerPriceStatus==='PENDING'?'pending-price-note':'fixed-price-note'">
                {{Number(row.influencerPriceVersion||0)>0?$tr("达人商品固定价"):row.influencerPriceStatus==='PENDING'?$tr("本草稿待生效价格"):$tr("保存草稿后关联达人库")}}
              </small>
            </div>
          </template></el-table-column>
          <el-table-column v-if="showCostColumn" :label="form.docType==='COST_ADJUST'?$tr(&quot;当前平均成本&quot;):$tr(&quot;单位成本&quot;)" width="140"><template #default="{row}"><span>{{money(row.unitCost)}}</span><small v-if="form.docType==='SALES_OUT' && normalizedSaleRole(row)==='MAIN' && !expandedBundleGroups[row.bundleGroupNo] && includedGiftCost(row)>0" class="included-gift-cost">{{ $tr('含价赠品成本合计 ¥{0}', [money(includedGiftCost(row))]) }}</small></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;包装费/件&quot;)" width="220">
            <template #default="{row}">
              <div v-if="isAccessoryPackaging(row)" class="pack-fee-cell packaging-cost-note">
                <span>{{ $tr("配件耗材 ¥{0} × {1}", [money(row.unitCost), effectiveQty(row)]) }}</span>
                <small>{{ $tr("耗材成本 ¥{0}", [money(Number(row.unitCost||0)*effectiveQty(row))]) }}</small>
              </div>
              <div v-else class="pack-fee-cell">
                <el-input-number v-model="row.packFee" :min="0" :precision="2" :disabled="readonly || hasBoundTerms(row)"/>
                <template v-if="normalizedSaleRole(row)==='MAIN' && accessoryPackagingMetrics(row).accessoryTotal>0">
                  <small>{{ $tr("配件耗材 ¥{0}，包装费 ¥{1}", [money(accessoryPackagingMetrics(row).accessoryTotal), money(accessoryPackagingMetrics(row).manualTotal)]) }}</small>
                  <small v-if="accessoryPackagingMetrics(row).shortage>0" class="packaging-shortage">{{ $tr(" 不足 ¥{0}，不能提交 ", [money(accessoryPackagingMetrics(row).shortage)]) }}</small>
                  <small v-else class="packaging-covered">{{ $tr("包装费已覆盖配件耗材") }}</small>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;物流费/件&quot;)" width="145"><template #default="{row}"><el-input-number v-model="row.shipFee" :min="0" :precision="2" :disabled="readonly || hasBoundTerms(row)"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;鉴定费/件&quot;)" width="145"><template #default="{row}"><el-input-number v-model="row.certFee" :min="0" :precision="2" :disabled="readonly || hasBoundTerms(row)"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;扣点/佣金/税率&quot;)" width="180"><template #default="{row}">{{(rowRate(row,'platformRate')*100).toFixed(2)}}% / {{(rowRate(row,'commissionRate')*100).toFixed(2)}}% / {{(rowRate(row,'taxRate')*100).toFixed(2)}}%</template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;其他1/件&quot;)" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee1" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;其他2/件&quot;)" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee2" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;其他3/件&quot;)" width="145"><template #default="{row}"><el-input-number v-model="row.otherFee3" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="showPriceColumn" :label="amountLabel" width="130" align="right"><template #default="{row}">{{documentAmount(lineAmount(row),form)}}</template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;平台等扣费&quot;)" width="130" align="right"><template #default="{row}">{{money(lineDeductions(row))}}</template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" :label="$tr(&quot;预计净入账&quot;)" width="130" align="right"><template #default="{row}">{{money(lineNetReceipt(row))}}</template></el-table-column>
          <el-table-column v-if="canViewFinance && form.docType==='SALES_OUT'" :label="$tr(&quot;预计毛利&quot;)" width="120" align="right">
            <template #default="{row}">
              <span v-if="isAccessoryPackaging(row)">—</span>
              <span v-else :class="{loss:displayLineProfit(row)<0}">{{money(displayLineProfit(row))}}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" :label="$tr(&quot;调整原因&quot;)" min-width="180"><template #default="{row}"><el-input v-model="row.lineReason" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="!readonly" width="60"><template #default="{ row }"><el-button link type="danger" icon="Delete" @click="removeItem(form.items.indexOf(row))"/></template></el-table-column>
        </el-table>
        <div v-if="form.docType==='SALES_OUT' && bundleSummaries.length" class="bundle-summaries">
          <div v-for="group in bundleSummaries" :key="group.groupNo">
            <b>{{ $tr("组合{0}", [group.groupNo]) }}</b>
            <span>{{ $tr("成交 ¥{0}", [money(group.amount)]) }}</span>
            <span v-if="group.accessoryTotal>0">{{ $tr("配件耗材 ¥{0}", [money(group.accessoryTotal)]) }}</span>
            <span v-if="group.accessoryTotal>0">{{ $tr("包装费 ¥{0}", [money(group.manualPackagingTotal)]) }}</span>
            <span v-if="group.packagingShortage>0" class="packaging-shortage">{{ $tr("包装费不足 ¥{0}", [money(group.packagingShortage)]) }}</span>
            <span v-if="canViewFinance">{{ $tr("成本及费用 ¥{0}", [money(group.cost)]) }}</span>
            <span v-if="canViewFinance" :class="{loss:group.profit<0}">{{ $tr("预计毛利 ¥{0}", [money(group.profit)]) }}</span>
          </div>
        </div>
        <el-button v-if="!readonly && !['SUPPLIER_RETURN','RETURN_INSPECT'].includes(form.docType) && (form.docType!=='CUSTOMER_RETURN' || !form.sourceDocumentId)" plain icon="Plus" class="add-line" @click="addNormalItem">{{ $tr("增加一行") }}</el-button>
        <div class="document-total">
          <span>{{ $tr("SKU {0} 种", [form.items.length]) }}</span>
          <span>{{ $tr("总件数 ") }}<b>{{ estimatedQty }}</b></span>
          <span v-if="showPriceColumn">{{ totalAmountLabel }} <b>¥ {{ documentAmount(estimatedAmount,form) }}</b></span>
          <span v-if="form.docType==='COST_ADJUST'">{{ $tr("调整后库存金额 ") }}<b>¥ {{ money(adjustedInventoryAmount) }}</b></span>
          <span v-if="form.docType==='SALES_OUT'">{{ $tr("平台等扣费 ") }}<b>¥ {{ money(estimatedDeductions) }}</b></span>
          <span v-if="form.docType==='SALES_OUT'">{{ $tr("预计净入账 ") }}<b>¥ {{ money(estimatedNetReceipt) }}</b></span>
          <span v-if="canViewFinance && form.docType==='SALES_OUT'" :class="{loss:estimatedProfit<0}">{{ $tr("预计毛利 ") }}<b>¥ {{ money(estimatedProfit) }}</b></span>
        </div>
        <div class="sheet-foot"><el-form label-width="110px"><el-form-item v-if="needsReason" :label="reasonLabel" required><el-input v-model="form.returnReason" :disabled="readonly"/></el-form-item><el-form-item v-if="readonly && form.docType==='CUSTOMER_RETURN' && form.unlinkedReason" :label="$tr(&quot;历史未关联原因&quot;)"><el-input v-model="form.unlinkedReason" disabled/></el-form-item><el-form-item v-if="!isTransfer(form) && form.docType!=='SAMPLE_IN'" :label="$tr(&quot;备注&quot;)"><el-input v-model="form.remark" :disabled="readonly"/></el-form-item></el-form></div>
      </div>
      <template #footer>
        <el-button :disabled="!!savingAction" @click="dialog=false">{{ $tr("关闭") }}</el-button>
        <el-button v-if="!readonly" :loading="savingAction==='draft'" :disabled="!!savingAction" @click="save(false)">{{ $tr("保存草稿") }}</el-button>
        <el-button v-if="!readonly" type="primary" :loading="savingAction==='submit'" :disabled="!!savingAction"
          v-hasPermi="['jewelry:document:submit']" @click="save(true)">{{ $tr("直接提交") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="productDialog" :title="$tr(&quot;新增商品档案&quot;)" width="640px" append-to-body destroy-on-close>
      <el-form ref="productFormRef" :model="quickProduct" :rules="productRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="SKU" prop="sku"><el-input v-model="quickProduct.sku" :placeholder="$tr(&quot;成品、样品、赠品可共用SKU&quot;)"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$tr(&quot;商品名称&quot;)" prop="productName"><el-input v-model="quickProduct.productName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$tr(&quot;商品类型&quot;)" prop="productType"><el-select v-model="quickProduct.productType" :disabled="['PURCHASE_IN','SAMPLE_IN'].includes(form.docType)" style="width:100%"><el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$tr(&quot;单位&quot;)"><el-input v-model="quickProduct.unit"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$tr(&quot;预警值&quot;)"><el-input-number v-model="quickProduct.warningQty" :min="0" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="$tr(&quot;实物图片&quot;)"><image-upload v-model="quickProduct.imageUrls" :limit="1" :file-size="8"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="productDialog=false">{{ $tr("取消") }}</el-button>
        <el-button type="primary" :loading="productSaving" @click="saveQuickProduct">{{ $tr("保存并选中") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog" :title="$tr(&quot;Excel导入预览&quot;)" width="88%" top="6vh"
      append-to-body destroy-on-close>
      <div class="import-summary">
        <el-tag type="success">{{ $tr("可导入 {0} 行", [importPreview.validCount || 0]) }}</el-tag>
        <el-tag v-if="importPreview.newProductCount" type="warning">{{ $tr("新商品 {0} 个", [importPreview.newProductCount]) }}</el-tag>
        <el-tag :type="importPreview.errorCount ? 'danger' : 'info'">{{ $tr("错误 {0} 行", [importPreview.errorCount || 0]) }}</el-tag>
        <el-tag v-if="importCompression?.compressed" type="info">{{ $tr(" 本地压缩 {0} → {1}", [formatFileSize(importCompression.originalSize), formatFileSize(importCompression.outputSize)]) }}
        </el-tag>
        <span v-if="importNeedsValidation">{{ $tr("内容已修改，请重新校验全部行。") }}</span>
        <span v-else-if="importPreview.errorCount">{{ $tr("请在表格中修正错误并重新校验。") }}</span>
      </div>
      <el-table :data="importPreview.rows || []" border max-height="520">
        <el-table-column prop="rowNumber" :label="$tr(&quot;Excel行&quot;)" width="76" align="center"/>
        <el-table-column :label="$tr(&quot;状态&quot;)" width="92">
          <template #default="{row}">
            <el-tag v-if="row.status==='VALID'" type="success">{{ $tr("可导入") }}</el-tag>
            <el-tag v-else-if="row.status==='NEW'" type="warning">{{ $tr("新商品") }}</el-tag>
            <el-tag v-else type="danger">{{ $tr("有错误") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="form.docType==='SAMPLE_IN'" :label="$tr(&quot;Excel商品&quot;)" min-width="170">
          <template #default="{row}"><el-input v-model="row.productInput" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column label="SKU" min-width="160">
          <template #default="{row}"><el-input v-model="row.sku" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" :label="$tr(&quot;商品名称&quot;)" min-width="180">
          <template #default="{row}"><el-input v-model="row.productName" :disabled="!row.newProduct" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-else prop="productName" :label="$tr(&quot;商品名称&quot;)" min-width="160"/>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" :label="$tr(&quot;单位&quot;)" width="110">
          <template #default="{row}"><el-input v-model="row.unit" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='SAMPLE_IN'" :label="$tr(&quot;业务日期&quot;)" width="170">
          <template #default="{row}"><el-date-picker v-model="row.bizDate" type="date" value-format="YYYY-MM-DD" style="width:145px" @change="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='SAMPLE_IN'" prop="supplierNameSnapshot" :label="$tr(&quot;供应商&quot;)" min-width="130">
          <template #default="{row}"><el-input v-model="row.supplierInput" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType!=='SAMPLE_IN'" :label="$tr(&quot;商品类型&quot;)" width="110" align="center">
          <template #default="{row}">
            <el-select v-if="form.docType==='PURCHASE_IN'" v-model="row.productType" @change="markImportDirty(row)">
              <el-option v-for="type in purchaseProductTypes" :key="type.value" :label="type.label" :value="type.value"/>
            </el-select>
            <span v-else>{{jewelryProductType(row.productType)?.label||'—'}}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="['PURCHASE_IN','SAMPLE_IN'].includes(form.docType)" :label="$tr(&quot;图片&quot;)" width="160" align="center">
          <template #default="{row}">
            <image-upload v-model="row.imageUrls" :limit="1" :file-size="8" :is-show-tip="false" @update:model-value="markImportDirty(row)"/>
          </template>
        </el-table-column>
        <el-table-column v-if="form.docType!=='STOCK_ADJUST'" :label="$tr(&quot;数量&quot;)" width="125" align="center">
          <template #default="{row}"><el-input-number v-model="row.qty" :min="1" :precision="0" controls-position="right" style="width:100px" @change="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" :label="$tr(&quot;实盘数量&quot;)" width="130">
          <template #default="{row}"><el-input-number v-model="row.countedQty" :min="0" :precision="0" controls-position="right" style="width:110px" @change="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" :label="priceLabel" width="155">
          <template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false" style="width:130px" @change="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column v-if="form.docType==='SALES_OUT'" :label="priceLabel" width="110"><template #default="{row}">{{unitPriceText(row.unitPrice)}}</template></el-table-column>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee1" :label="$tr(&quot;其他1/件&quot;)" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee2" :label="$tr(&quot;其他2/件&quot;)" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="otherFee3" :label="$tr(&quot;其他3/件&quot;)" width="95" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="availableQty" :label="$tr(&quot;可用库存&quot;)" width="100" align="right"/>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" :label="$tr(&quot;调整原因&quot;)" min-width="150">
          <template #default="{row}"><el-input v-model="row.lineReason" @input="markImportDirty(row)"/></template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="$tr(&quot;校验结果&quot;)" min-width="240">
          <template #default="{row}"><span v-if="importNeedsValidation">{{ $tr("待重新校验") }}</span><span v-else :class="{ 'import-error': !row.valid }">{{row.errorMessage || $tr("校验通过")}}</span></template>
        </el-table-column>
        <el-table-column v-if="['PURCHASE_IN','SAMPLE_IN'].includes(form.docType)" :label="$tr(&quot;操作&quot;)" width="82" align="center" fixed="right">
          <template #default="{$index}">
            <el-button link type="danger" icon="Delete" @click="removeImportPreviewRow($index)">{{ $tr("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importDialog=false">{{ $tr("取消") }}</el-button>
        <el-button :loading="reviewingImport" @click="reviewImportRows">{{ $tr("重新校验") }}</el-button>
        <el-button type="primary" :loading="applyingImport"
          :disabled="importNeedsValidation || reviewingImport || Number(importPreview.errorCount)>0 || Number(importPreview.validCount)<=0"
          @click="applyImportRows">{{ $tr("导入到当前单据") }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="returnDateDialog" :title="$tr(&quot;设置特殊退货日期&quot;)" width="440px" append-to-body>
      <p>{{ $tr("采购单：{0}", [form.docNo]) }}</p>
      <p>{{ $tr("仅修改本采购单退货期限，不改变库存、金额和审批状态。清空日期后恢复统一规则。") }}</p>
      <el-date-picker v-model="returnDateValue" type="date" value-format="YYYY-MM-DD" clearable :placeholder="$tr(&quot;留空使用统一退货期限&quot;)" style="width:100%"/>
      <template #footer>
        <el-button @click="returnDateDialog=false">{{ $tr("取消") }}</el-button>
        <el-button type="primary" :loading="savingReturnDate" @click="saveReturnDate">{{ $tr("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryDocument">
import {toRaw} from 'vue'
import { translateText } from '@/locales/translate'
import {updateJewelrySupplierReturnDate} from '@/api/jewelry/erp'
import {saveAs} from 'file-saver'
import {listJewelryDocuments,getJewelryDocument,listSupplierReturnSources,getSupplierReturnSource,getCustomerReturnSource,listCustomerReturnProducts,getReturnInspectionSource,saveJewelryDocument,deleteJewelryDraft,assessJewelryDocumentRisk,submitJewelryDocument,withdrawJewelryDocument,createJewelryReversal,listJewelryProducts,listJewelryProductOptions,listJewelrySuppliers,listJewelryInfluencerOptions,getJewelryInfluencerProductPrices,getJewelryInfluencerBundleItems,saveJewelryProduct,downloadJewelryDocumentImportTemplate,previewJewelryDocumentImport,reviewJewelryDocumentImport} from '@/api/jewelry/erp'
import {compressXlsxImages,formatFileSize} from '@/utils/xlsxImageCompressor'
import {jewelryProductTypes,jewelryProductType,matchesJewelryProductFilters} from '@/utils/jewelryProduct'
import useUserStore from '@/store/modules/user'
const {proxy}=getCurrentInstance(),rows=ref([]),total=ref(0),loading=ref(false),dialog=ref(false),readonly=ref(false),savingAction=ref(''),products=ref([]),suppliers=ref([]),influencers=ref([]),purchaseDocuments=ref([]),salesDocuments=ref([]),returnDocuments=ref([])
const salesSourceLoading=ref(false)
const salesSourceError=ref(false)
let salesSourceRequest=0
const customerReturnProductStats=ref([]),customerReturnProductLoading=ref(false)
let customerReturnProductRequest=0
let inspectionLoadSequence=0
const productDialog=ref(false),productSaving=ref(false),productFormRef=ref(),activeProductRow=ref(null)
const importDialog=ref(false),importLoading=ref(false),applyingImport=ref(false),reviewingImport=ref(false),importNeedsValidation=ref(false),importPreview=ref({})
const importCompression=ref(null)
const returnDateDialog=ref(false),returnDateValue=ref(null),savingReturnDate=ref(false)
function openReturnDate(){returnDateValue.value=form.supplierReturnDate||null;returnDateDialog.value=true}
async function saveReturnDate(){
  if(returnDateValue.value && returnDateValue.value<form.bizDate){proxy.$modal.msgWarning(translateText("约定退货日期不能早于采购入库业务日期"));return}
  savingReturnDate.value=true
  try{await updateJewelrySupplierReturnDate(form.documentId,returnDateValue.value||null);form.supplierReturnDate=returnDateValue.value||null;returnDateDialog.value=false;proxy.$modal.msgSuccess(returnDateValue.value?translateText("特殊退货日期已保存"):translateText("已恢复按统一退货期限计算"))}
  finally{savingReturnDate.value=false}
}
const importProgress=reactive({active:false,percentage:0,text:''})
const actualRefundManuallyEdited=ref(false)
const influencerProductPrices=ref([])
const influencerBundleItems=ref([])
let influencerLoadSequence=0
const blankQuickProduct=()=>({sku:'',productName:'',productType:'FINISHED',imageUrl:'',imageUrls:'',unit:'件',warningQty:5,status:'0',defaultPackFee:0,defaultShipFee:0,defaultCertFee:0})
const quickProduct=reactive(blankQuickProduct())
const productRules={sku:[{required:true,message:translateText("请输入SKU"),trigger:'blur'}],productName:[{required:true,message:translateText("请输入商品名称"),trigger:'blur'}],productType:[{required:true,type:'enum',enum:jewelryProductTypes.map(item=>item.value),message:translateText("请选择商品类型")}]}
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canDeleteDraft=row=>row.status==='DRAFT'&&String(row.creatorUserId)===String(userStore.id)
const isDualApproval=row=>['STOCK_ADJUST','COST_ADJUST'].includes(row?.docType)||(row?.docType==='REVERSAL'&&['STOCK_ADJUST','COST_ADJUST'].includes(row?.sourceDocType))
const isTransfer=row=>row?.docType==='TRANSFER_OUT'||(row?.docType==='REVERSAL'&&row?.sourceDocType==='TRANSFER_OUT')
const types=[{value:'PURCHASE_IN',label:translateText("采购入库")},{value:'SAMPLE_IN',label:translateText("样品入库")},{value:'SALES_OUT',label:translateText("销售出库")},{value:'SUPPLIER_RETURN',label:translateText("供应商退货")},{value:'CUSTOMER_RETURN',label:translateText("客户退货")},{value:'RETURN_INSPECT',label:translateText("退货质检")},{value:'STOCK_ADJUST',label:translateText("库存调整")},{value:'COST_ADJUST',label:translateText("库存成本调价")},{value:'TRANSFER_OUT',label:translateText("仓库调货")},{value:'ASSEMBLY',label:translateText("手工组装")},{value:'REVERSAL',label:translateText("红冲单")}]
const editableTypes=types.filter(item=>!['REVERSAL','ASSEMBLY'].includes(item.value))
const statuses=[{value:'DRAFT',label:translateText("草稿")},{value:'PENDING_FIRST',label:translateText("待审核")},{value:'PENDING_SECOND',label:translateText("待审核")},{value:'POSTED',label:translateText("已入账")},{value:'REJECTED',label:translateText("已驳回")},{value:'REVERSED',label:translateText("已红冲")}]
const query=reactive({pageNum:1,pageSize:10,docNo:'',docType:'',status:''})
const blankItem=()=>({productId:null,sourceItemId:null,itemRole:'NORMAL',bundleGroupNo:null,saleRole:'NORMAL',pricingMode:'SEPARATE',productTypeSnapshot:'',specificationSnapshot:'',skuSnapshot:'',sampleSkuInput:'',imageUrls:'',bizDate:null,supplierId:null,supplierNameSnapshot:'',sampleGoodsNo:'',qty:1,sourceQty:0,soldQty:0,remainingReturnQty:0,goodQty:0,defectQty:0,remainingInspectQty:0,systemQty:0,countedQty:0,adjustmentQty:0,unitPrice:0,sourceUnitPrice:0,unitCost:0,packFee:0,shipFee:0,certFee:0,otherFee1:0,otherFee2:0,otherFee3:0,platformRateSnapshot:null,commissionRateSnapshot:null,taxRateSnapshot:null,influencerPriceSnapshot:null,influencerPriceVersion:0,influencerPriceStatus:'',lineReason:''})
const blankPurchaseItem=()=>({...blankItem(),productTypeSnapshot:''})
const purchaseProductTypes=jewelryProductTypes.filter(type=>type.value!=='SAMPLE')
const blankProductFilters=()=>({productType:'',influencerId:null,supplierId:null})
const productFilters=reactive(blankProductFilters())
const productFilterBindings=ref([]),productFilterLoading=ref(false)
let productFilterRequest=0
const blank=()=>({documentId:null,docType:'PURCHASE_IN',bizDate:new Date().toISOString().slice(0,10),supplierReturnDate:null,supplierId:null,supplierNameSnapshot:'',sourceWarehouse:'',targetWarehouse:'',externalNo:'',salesChannel:'',influencerId:null,influencerName:'',influencerPriceSnapshot:null,influencerPriceVersion:0,platformRate:0,commissionRate:0,taxRate:0,returnReason:'',sourceDocumentId:null,sourceDocNo:'',unlinkedReason:'',actualRefundAmount:null,riskStatus:'',remark:'',items:[blankPurchaseItem()]})
const form=reactive(blank())
const expandedBundleGroups=reactive({})
const resetExpandedBundleGroups=()=>{for(const groupNo of Object.keys(expandedBundleGroups))delete expandedBundleGroups[groupNo]}
const serverRiskStatus=ref('')
let riskTimer=null,riskSequence=0
const showInspectColumns=computed(()=>form.docType==='RETURN_INSPECT'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)>0)))
const showAdjustmentColumn=computed(()=>form.docType==='STOCK_ADJUST'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.adjustmentQty||0)!==0)))
const showQuantityColumn=computed(()=>!showInspectColumns.value&&!showAdjustmentColumn.value&&form.docType!=='COST_ADJUST')
const needsSupplier=computed(()=>['PURCHASE_IN','SUPPLIER_RETURN'].includes(form.docType))
const needsSalesChannel=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType))
const showPriceColumn=computed(()=>['PURCHASE_IN','SALES_OUT','SUPPLIER_RETURN','CUSTOMER_RETURN','COST_ADJUST'].includes(form.docType))
const showCostColumn=computed(()=>!isTransfer(form)&&form.docType!=='SAMPLE_IN'&&(form.docType==='COST_ADJUST'||(canViewFinance.value&&form.docType!=='PURCHASE_IN')))
const showSalesBundleColumns=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)||(readonly.value&&form.items?.some(item=>['MAIN','ADDON'].includes(item.saleRole))))
const includedAddonRows=main=>form.items.filter(item=>normalizedSaleRole(item)==='ADDON'&&item.bundleGroupNo===main.bundleGroupNo&&normalizedPricingMode(item)==='INCLUDED')
const displayItems=computed(()=>form.docType==='SALES_OUT'?form.items.filter(row=>normalizedSaleRole(row)!=='ADDON'||normalizedPricingMode(row)!=='INCLUDED'||expandedBundleGroups[row.bundleGroupNo]||!form.items.some(item=>normalizedSaleRole(item)==='MAIN'&&item.bundleGroupNo===row.bundleGroupNo)):form.items)
const toggleIncludedAddons=main=>{expandedBundleGroups[main.bundleGroupNo]=!expandedBundleGroups[main.bundleGroupNo]}
const supportsProductFilters=computed(()=>['COST_ADJUST','TRANSFER_OUT'].includes(form.docType))
const excelImportSupported=computed(()=>['PURCHASE_IN','SAMPLE_IN','SALES_OUT','STOCK_ADJUST'].includes(form.docType))
const priceLabel=computed(()=>form.docType==='PURCHASE_IN'?translateText("采购单价"):form.docType==='SALES_OUT'?translateText("成交单价"):form.docType==='SUPPLIER_RETURN'?translateText("实际退货单价"):form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId?translateText("实际退款单价"):form.docType==='COST_ADJUST'?translateText("调整后平均成本"):translateText("原成交单价"))
const unitPricePrecision=computed(()=>isFourDecimalUnitPriceDocument(form)?4:2)
const unitPriceStep=computed(()=>isFourDecimalUnitPriceDocument(form)?0.0001:0.01)
const amountLabel=computed(()=>form.docType==='PURCHASE_IN'?translateText("采购金额"):form.docType==='SALES_OUT'?translateText("成交总额"):form.docType==='SUPPLIER_RETURN'?translateText("退货金额"):form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId?translateText("退款金额"):form.docType==='COST_ADJUST'?translateText("库存金额变化"):translateText("原成交金额"))
const totalAmountLabel=computed(()=>form.docType==='PURCHASE_IN'?translateText("采购总额"):form.docType==='SALES_OUT'?translateText("成交总额"):form.docType==='SUPPLIER_RETURN'?translateText("退货总额"):form.docType==='COST_ADJUST'?translateText("库存金额变化"):translateText("退款总额"))
const needsReason=computed(()=>['SUPPLIER_RETURN','CUSTOMER_RETURN','STOCK_ADJUST','COST_ADJUST'].includes(form.docType))
const reasonLabel=computed(()=>form.docType==='STOCK_ADJUST'?translateText("调整原因"):form.docType==='COST_ADJUST'?translateText("调价原因"):translateText("退货原因"))
const effectiveQty=row=>form.docType==='RETURN_INSPECT'?Number(row.goodQty||0)+Number(row.defectQty||0):form.docType==='STOCK_ADJUST'?Math.abs(Number(row.countedQty||0)-Number(row.systemQty||0)):Number(row.qty||0)
const lineAmount=row=>form.docType==='COST_ADJUST'?(Number(row.unitPrice||0)-Number(row.unitCost||0))*effectiveQty(row):Number(row.unitPrice||0)*effectiveQty(row)
const rowRate=(row,key)=>Number(row[`${key}Snapshot`]??form[key]??0)
const lineDeductions=row=>lineAmount(row)*(rowRate(row,'platformRate')+rowRate(row,'commissionRate')+rowRate(row,'taxRate'))
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
const includedGiftCost=main=>normalizedSaleRole(main)==='MAIN'?includedAddonRows(main).filter(row=>!isAccessoryPackaging(row)).reduce((sum,row)=>sum+Number(row.unitCost||0)*effectiveQty(row),0):0
const displayLineProfit=row=>lineProfit(row)+(normalizedSaleRole(row)==='MAIN'&&form.docType==='SALES_OUT'&&!expandedBundleGroups[row.bundleGroupNo]?includedAddonRows(row).reduce((sum,addon)=>sum+lineProfit(addon),0):0)
const estimatedQty=computed(()=>form.items.reduce((sum,row)=>sum+effectiveQty(row),0))
const enteredReturnRefund=computed(()=>form.items.reduce((sum,row)=>sum+lineAmount(row),0))
const expectedReturnRefund=computed(()=>form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId
  ?form.items.reduce((sum,row)=>sum+Number(row.influencerPriceSnapshot||0)*effectiveQty(row),0)
  :enteredReturnRefund.value)
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
const refundAmountDiffers=computed(()=>form.docType==='CUSTOMER_RETURN'&&form.actualRefundAmount!==null&&Math.abs(Number(form.actualRefundAmount)-expectedReturnRefund.value)>0.009)
watch([()=>form.docType,()=>form.sourceDocumentId,enteredReturnRefund],([docType,sourceDocumentId,refundTotal])=>{
  if(docType==='CUSTOMER_RETURN'&&!actualRefundManuallyEdited.value){
    form.actualRefundAmount=Math.round((Number(refundTotal||0)+Number.EPSILON)*100)/100
  }
})
const riskFingerprint=computed(()=>JSON.stringify({
  open:dialog.value,readonly:readonly.value,docType:form.docType,
  influencerId:form.influencerId,
  platformRate:form.platformRate,commissionRate:form.commissionRate,taxRate:form.taxRate,
  items:(form.items||[]).map(({productId,qty,unitPrice,packFee,shipFee,certFee,otherFee1,otherFee2,otherFee3,platformRateSnapshot,commissionRateSnapshot,taxRateSnapshot,bundleGroupNo,saleRole,pricingMode,influencerPriceSnapshot,influencerPriceVersion})=>({productId,qty,unitPrice,packFee,shipFee,certFee,otherFee1,otherFee2,otherFee3,platformRateSnapshot,commissionRateSnapshot,taxRateSnapshot,bundleGroupNo,saleRole,pricingMode,influencerPriceSnapshot,influencerPriceVersion}))
}))
watch(riskFingerprint,()=>{
  serverRiskStatus.value=''
  if(!readonly.value)form.riskStatus=''
  riskSequence+=1
  const sequence=riskSequence
  if(riskTimer)clearTimeout(riskTimer)
  const rates=[form.platformRate,form.commissionRate,form.taxRate].map(Number)
  const valid=form.docType==='SALES_OUT'&&dialog.value&&!readonly.value&&form.items?.length
    &&form.items.every(row=>row.productId&&Number(row.qty)>0&&[row.unitPrice,row.packFee,row.shipFee,row.certFee,row.otherFee1,row.otherFee2,row.otherFee3].every(value=>Number(value)>=0)
      &&['platformRate','commissionRate','taxRate'].every(key=>Number.isFinite(rowRate(row,key))&&rowRate(row,key)>=0&&rowRate(row,key)<=1)
      &&['platformRate','commissionRate','taxRate'].reduce((sum,key)=>sum+rowRate(row,key),0)<1)
    &&rates.every(value=>Number.isFinite(value)&&value>=0&&value<=1)&&rates.reduce((sum,value)=>sum+value,0)<1
  if(!valid)return
  riskTimer=setTimeout(async()=>{
    try{
      const response=await assessJewelryDocumentRisk(form)
      if(sequence===riskSequence)serverRiskStatus.value=response.data?.riskStatus||''
    }catch(_error){/* 保存和提交时仍会由后端给出明确校验错误 */}
  },350)
})
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const fourDecimalMoney=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:4,maximumFractionDigits:4})
const isFourDecimalAmountDocument=document=>{
  const docType=typeof document==='string'?document:document?.docType
  return ['PURCHASE_IN','SUPPLIER_RETURN'].includes(docType)
    ||(docType==='REVERSAL'&&['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.sourceDocType))
}
const isFourDecimalUnitPriceDocument=document=>{
  const docType=typeof document==='string'?document:document?.docType
  return ['PURCHASE_IN','SUPPLIER_RETURN','SALES_OUT'].includes(docType)
    ||(docType==='CUSTOMER_RETURN'&&(typeof document==='string'||!document?.sourceDocumentId))
    ||(docType==='REVERSAL'&&['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.sourceDocType))
}
const documentAmount=(value,document)=>isFourDecimalAmountDocument(document)?fourDecimalMoney(value):money(value)
const unitPriceText=value=>isFourDecimalUnitPriceDocument(form)?fourDecimalMoney(value):money(value)
const imageSrc=value=>/^https?:/i.test(value||'')?value:import.meta.env.VITE_APP_BASE_API+(value||'')
const labelOf=(list,value)=>list.find(x=>x.value===value)?.label||value;const statusType=s=>s==='POSTED'?'success':['REJECTED','REVERSED'].includes(s)?'danger':s==='DRAFT'?'info':'warning'
const documentStatusLabel=row=>isDualApproval(row)&&row.status==='PENDING_SECOND'?translateText("待管理员复核"):isDualApproval(row)&&row.status==='PENDING_FIRST'?translateText("待审核员审核"):labelOf(statuses,row.status)
async function preload(){const [p,s,i,sales]=await Promise.all([listJewelryProductOptions({status:'0'}),listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'}),listJewelryInfluencerOptions({}),listJewelryDocuments({pageNum:1,pageSize:500,docType:'SALES_OUT',status:'POSTED'})]);products.value=p.data||[];suppliers.value=s.rows||[];influencers.value=i.data||[];salesDocuments.value=sales.rows||[]}
async function reloadProducts(purpose){const r=await listJewelryProductOptions({status:'0',...(purpose?{purpose}:{})});products.value=r.data||[]}
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function resetProductFilters(){productFilterRequest++;Object.assign(productFilters,blankProductFilters());productFilterBindings.value=[];productFilterLoading.value=false}
function open(){influencerLoadSequence++;inspectionLoadSequence++;customerReturnProductRequest++;actualRefundManuallyEdited.value=false;Object.assign(form,blank());resetExpandedBundleGroups();resetProductFilters();purchaseDocuments.value=[];returnDocuments.value=[];customerReturnProductStats.value=[];customerReturnProductLoading.value=false;influencerProductPrices.value=[];influencerBundleItems.value=[];readonly.value=false;dialog.value=true}
function normalizeLoadedDocument(){form.items=(form.items||[]).map(item=>{const normalized={...blankItem(),...item,remainingInspectQty:item.remainingInspectQty??(form.docType==='RETURN_INSPECT'?Number(item.goodQty||0)+Number(item.defectQty||0):0),saleRole:item.saleRole||'NORMAL',pricingMode:item.pricingMode||'SEPARATE'};if(form.docType==='SAMPLE_IN'){if(!normalized.bizDate)normalized.bizDate=form.bizDate;normalized.sampleSkuInput=normalized.skuSnapshot||''}normalized.influencerPriceStatus=Number(normalized.influencerPriceVersion||0)>0?'PRICED':normalized.influencerPriceSnapshot!=null?'PENDING':'';if(form.docType==='SALES_OUT'&&normalized.saleRole==='ADDON'&&normalized.productTypeSnapshot==='ACCESSORY'){normalized.pricingMode='INCLUDED';normalized.unitPrice=0;normalized.packFee=0;normalized.shipFee=0;normalized.certFee=0;normalized.otherFee1=0;normalized.otherFee2=0;normalized.otherFee3=0;clearRowInfluencerPrice(normalized)}return normalized});if(form.docType==='CUSTOMER_RETURN'&&form.actualRefundAmount===null)form.actualRefundAmount=Math.abs(Number(form.totalAmount||0))}
async function edit(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);normalizeLoadedDocument();resetExpandedBundleGroups();resetProductFilters();actualRefundManuallyEdited.value=form.docType==='CUSTOMER_RETURN'&&form.actualRefundAmount!==null;await reloadProducts(form.docType==='COST_ADJUST'?'COST_ADJUST':undefined);if(form.influencerId&&['PURCHASE_IN','SALES_OUT','SUPPLIER_RETURN','CUSTOMER_RETURN'].includes(form.docType)){await loadInfluencerReferences(form.influencerId);if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId)await loadCustomerReturnProductStats(form.influencerId);if(form.docType==='SALES_OUT'||(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId))applyInfluencerPriceToRows()}else{influencerProductPrices.value=[];influencerBundleItems.value=[];customerReturnProductStats.value=[];}if(form.docType==='SUPPLIER_RETURN'){await loadSupplierReturnSources(form.influencerId,form.supplierId);if(form.sourceDocumentId)await loadSupplierReturnSource(form.sourceDocumentId,true)}if(form.docType==='CUSTOMER_RETURN'&&form.sourceDocumentId)await loadCustomerReturnSource(form.sourceDocumentId,true);if(form.docType==='RETURN_INSPECT'){await loadInspectionSources(form.influencerId);if(form.sourceDocumentId)await loadInspectionSource(form.sourceDocumentId,true)}readonly.value=false;dialog.value=true}
async function view(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);normalizeLoadedDocument();resetExpandedBundleGroups();if(form.influencerId){await loadInfluencerReferences(form.influencerId);if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId)await loadCustomerReturnProductStats(form.influencerId)}else{influencerProductPrices.value=[];influencerBundleItems.value=[];customerReturnProductStats.value=[];}readonly.value=true;dialog.value=true}
const normalizedSaleRole=row=>row?.saleRole||'NORMAL'
const normalizedPricingMode=row=>row?.pricingMode||'SEPARATE'
const productOf=row=>products.value.find(product=>row?.productId!=null&&String(product.productId)===String(row.productId))
const influencerOf=id=>influencers.value.find(item=>String(item.influencerId)===String(id))
const customerReturnSalesDocuments=computed(()=>salesDocuments.value.filter(source=>form.influencerId
  &&(String(source.influencerId)===String(form.influencerId)
    ||(form.documentId&&String(source.documentId)===String(form.sourceDocumentId)))))
const inspectionReturnDocuments=computed(()=>returnDocuments.value.filter(source=>form.influencerId
  &&String(source.influencerId)===String(form.influencerId)))
const selectedInfluencerPriceSummary=computed(()=>{
  if(!form.influencerId)return '—'
  const priced=influencerProductPrices.value.filter(price=>isConfiguredSalesBinding(price)
    &&products.value.some(product=>String(product.productId)===String(price.productId)&&product.productType==='FINISHED')).length
  const pending=influencerProductPrices.value.filter(item=>item.priceStatus==='PENDING').length
  const bundleAddons=new Set(influencerBundleItems.value.map(item=>String(item.addonProductId))).size
  return translateText("可售绑定 {0} 种", [priced])
    + (bundleAddons ? translateText("，历史搭售 {0} 种", [bundleAddons]) : '')
    + (pending ? translateText("，待生效 {0} 种", [pending]) : '')
})
const influencerPriceOf=productId=>influencerProductPrices.value.find(item=>String(item.productId)===String(productId))
const bundleMainRow=row=>form.items.find(item=>normalizedSaleRole(item)==='MAIN'&&item.bundleGroupNo===row?.bundleGroupNo)
const customerReturnProductStatOf=(productId,row=null)=>{
  const role=normalizedSaleRole(row)
  if(role==='ADDON'){
    const mainProductId=bundleMainRow(row)?.productId
    return customerReturnProductStats.value.find(item=>item.saleRole==='ADDON'
      &&String(item.productId)===String(productId)&&String(item.mainProductId)===String(mainProductId))
  }
  return customerReturnProductStats.value.find(item=>item.saleRole!=='ADDON'&&String(item.productId)===String(productId))
}
const customerReturnAddonStats=main=>customerReturnProductStats.value.filter(item=>item.saleRole==='ADDON'
  &&String(item.mainProductId)===String(main?.productId))
const isConfiguredSalesBinding=price=>price?.priceStatus==='PRICED'&&price.bindingStatus==='0'
  &&price.fixedUnitPrice!=null&&price.unitCost!=null&&price.commissionRate!=null&&price.platformRate!=null&&price.taxRate!=null
const isActiveSalesBinding=productId=>isConfiguredSalesBinding(influencerPriceOf(productId))
const isActiveCustomerReturnBinding=productId=>influencerPriceOf(productId)?.bindingStatus==='0'
const isAllowedCustomerReturnProduct=row=>{
  const product=productOf(row)
  return normalizedSaleRole(row)==='ADDON'
    ?['ACCESSORY','GIFT'].includes(product?.productType)&&!!customerReturnProductStatOf(row.productId,row)
    :product?.productType==='FINISHED'&&isActiveCustomerReturnBinding(row.productId)
}
const customerReturnBundlesValid=()=>{
  const groups=new Map()
  for(const row of form.items){
    if(!row.bundleGroupNo||!['MAIN','ADDON'].includes(normalizedSaleRole(row)))continue
    const group=groups.get(row.bundleGroupNo)||{main:0,addon:0}
    if(normalizedSaleRole(row)==='MAIN')group.main+=1
    else group.addon+=1
    groups.set(row.bundleGroupNo,group)
  }
  return [...groups.values()].every(group=>group.main===1&&group.addon>=1)
}
const isPurchaseBoundType=type=>['FINISHED','GIFT'].includes(type)
const purchaseHasBoundRows=computed(()=>form.docType==='PURCHASE_IN'&&form.items.some(row=>isPurchaseBoundType(row.productTypeSnapshot)||isPurchaseBoundType(productOf(row)?.productType)))
const purchaseSupplierOptions=computed(()=>{
  if(form.docType!=='PURCHASE_IN'||!purchaseHasBoundRows.value)return suppliers.value
  if(!form.influencerId)return []
  const ids=new Set(influencerProductPrices.value.filter(price=>isConfiguredSalesBinding(price)&&price.preferredSupplierId
    &&products.value.some(product=>String(product.productId)===String(price.productId)&&isPurchaseBoundType(product.productType)))
    .map(price=>String(price.preferredSupplierId)))
  return suppliers.value.filter(supplier=>ids.has(String(supplier.supplierId))
    ||(form.documentId&&String(supplier.supplierId)===String(form.supplierId)))
})
const documentSupplierOptions=computed(()=>{
  if(form.docType!=='SUPPLIER_RETURN')return purchaseSupplierOptions.value
  if(!form.influencerId)return []
  const ids=new Set(influencerProductPrices.value.filter(binding=>binding.preferredSupplierId)
    .map(binding=>String(binding.preferredSupplierId)))
  return suppliers.value.filter(supplier=>ids.has(String(supplier.supplierId))
    ||(form.documentId&&String(supplier.supplierId)===String(form.supplierId)))
})
const isPurchaseBoundBinding=(productId,supplierId)=>{
  const binding=influencerPriceOf(productId)
  return !!form.influencerId&&!!supplierId&&isConfiguredSalesBinding(binding)
    &&String(binding.preferredSupplierId||'')===String(supplierId)
}
const isAvailableAccessory=product=>product?.productType==='ACCESSORY'
  &&Number(product.onHandQty||0)-Number(product.reservedOutQty||0)>0
const isAvailableAddonProduct=product=>isAvailableAccessory(product)
  ||(product?.productType==='GIFT'&&isActiveSalesBinding(product.productId)
    &&Number(product.onHandQty||0)-Number(product.reservedOutQty||0)>0)
const isAllowedSalesProduct=(row,productId=row?.productId)=>normalizedSaleRole(row)==='ADDON'
  ?products.value.some(product=>String(product.productId)===String(productId)
    &&isAvailableAddonProduct(product)
    &&(product.productType==='GIFT'||normalizedPricingMode(row)==='INCLUDED'))
  :products.value.some(product=>String(product.productId)===String(productId)&&product.productType==='FINISHED')
    &&isActiveSalesBinding(productId)
const hasBoundTerms=row=>form.docType==='SALES_OUT'&&normalizedPricingMode(row)!=='INCLUDED'&&isActiveSalesBinding(row.productId)
const isUnlinkedInfluencerReturn=()=>form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId
async function loadInfluencerReferences(id){
  const sequence=++influencerLoadSequence
  if(!id){influencerProductPrices.value=[];influencerBundleItems.value=[];return true}
  const [prices,bundles]=await Promise.all([getJewelryInfluencerProductPrices(id),getJewelryInfluencerBundleItems(id)])
  if(sequence!==influencerLoadSequence||String(form.influencerId)!==String(id))return false
  influencerProductPrices.value=prices.data||[]
  influencerBundleItems.value=bundles.data||[]
  return true
}
function applyCustomerReturnProductStatsToRows(){
  if(form.docType!=='CUSTOMER_RETURN'||form.sourceDocumentId)return
  for(const row of form.items){
    const stat=customerReturnProductStatOf(row.productId,row)
    row.soldQty=Number(stat?.soldQty||0)
    row.remainingReturnQty=Number(stat?.remainingReturnQty||0)
    if(row.productId&&row.remainingReturnQty>0&&Number(row.qty||0)>row.remainingReturnQty)row.qty=row.remainingReturnQty
  }
}
async function loadCustomerReturnProductStats(id){
  const request=++customerReturnProductRequest
  customerReturnProductStats.value=[]
  if(!id){customerReturnProductLoading.value=false;applyCustomerReturnProductStatsToRows();return true}
  customerReturnProductLoading.value=true
  try{
    const response=await listCustomerReturnProducts(id,form.documentId)
    if(request!==customerReturnProductRequest||String(form.influencerId)!==String(id))return false
    customerReturnProductStats.value=response.data||[]
    applyCustomerReturnProductStatsToRows()
    return true
  }catch{
    return false
  }finally{if(request===customerReturnProductRequest)customerReturnProductLoading.value=false}
}
const productOptionLabel=(product,row)=>{
  const base=`${product.sku} · ${product.productName} · ${jewelryProductType(product.productType)?.label||product.productType}`
  if(form.docType!=='CUSTOMER_RETURN'||form.sourceDocumentId)return base
  const stat=customerReturnProductStatOf(product.productId,row)
  return `${base} · ${translateText('已售 {0} 件，可退 {1} 件',[Number(stat?.soldQty||0),Number(stat?.remainingReturnQty||0)])}`
}
const productOptionDisabled=(row,product)=>form.docType==='SALES_OUT'&&!isAllowedSalesProduct(row,product.productId)
  ||form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&Number(customerReturnProductStatOf(product.productId,row)?.remainingReturnQty||0)<=0
const customerReturnNoDataText=row=>form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&form.influencerId
  ?normalizedSaleRole(row)==='ADDON'?translateText('该成品暂无可退的历史搭售商品'):translateText('该达人暂无已绑定的成品商品')
  :translateText('无数据')
function clearRowInfluencerPrice(row){row.influencerPriceSnapshot=null;row.influencerPriceVersion=0;row.influencerPriceStatus='';row.platformRateSnapshot=null;row.commissionRateSnapshot=null;row.taxRateSnapshot=null}
function applyInfluencerProductPrice(row,{notify=true}={}){
  if(!row?.productId||!form.influencerId){clearRowInfluencerPrice(row);return true}
  const price=influencerPriceOf(row.productId)
  if(form.docType==='SALES_OUT'&&!isAllowedSalesProduct(row)){
    clearRowInfluencerPrice(row)
    if(notify)proxy.$modal.msgWarning(translateText('销售商品需完成达人绑定；搭售仅可选择有库存的配件或当前达人已绑定的赠品'))
    return false
  }
  if(form.docType==='SALES_OUT'&&normalizedPricingMode(row)==='INCLUDED'){
    clearRowInfluencerPrice(row);row.unitPrice=0;row.platformRateSnapshot=0;row.commissionRateSnapshot=0;row.taxRateSnapshot=0;return true
  }
  if(price?.priceStatus==='PRICED'){
    row.influencerPriceSnapshot=Number(price.fixedUnitPrice)
    row.influencerPriceVersion=Number(price.priceVersion||0)
    row.influencerPriceStatus='PRICED'
    if(form.docType==='SALES_OUT'||(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId))row.unitPrice=Number(price.fixedUnitPrice)
    if(form.docType==='SALES_OUT'){
      row.unitCost=Number(price.unitCost||0)
      row.platformRateSnapshot=Number(price.platformRate||0)
      row.commissionRateSnapshot=Number(price.commissionRate||0)
      row.taxRateSnapshot=Number(price.taxRate||0)
      row.packFee=Number(price.packFee||0)
      row.shipFee=Number(price.shipFee||0)
      row.certFee=Number(price.certFee||0)
    }
    return true
  }
  if(price?.priceStatus==='PENDING'){
    const belongsToCurrentDraft=form.documentId&&String(price.pendingSourceDocumentId)===String(form.documentId)
    if(form.docType==='SALES_OUT'&&belongsToCurrentDraft){
      row.influencerPriceSnapshot=Number(price.fixedUnitPrice)
      row.influencerPriceVersion=0
      row.influencerPriceStatus='PENDING'
      row.unitPrice=Number(price.fixedUnitPrice)
      return true
    }
    clearRowInfluencerPrice(row)
    if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){
      if(notify)proxy.$modal.msgWarning(translateText("该达人对应商品的固定价尚未生效，请填写实际退款单价；提交后将进入复核"))
      return true
    }
    if(notify)proxy.$modal.msgWarning(translateText("该达人对应商品的价格正在其他销售单中等待生效，暂不能重复定价"))
    return false
  }
  clearRowInfluencerPrice(row)
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){
    if(notify)proxy.$modal.msgWarning(translateText("该达人尚未建立此商品的固定价，请填写实际退款单价；提交后将进入复核"))
    return true
  }
  return true
}
function applyInfluencerPriceToRows(){
  for(const row of form.items)applyInfluencerProductPrice(row,{notify:false})
}
async function influencerChanged(id){
  const influencer=influencerOf(id)
  if(form.docType==='PURCHASE_IN'){
    form.influencerName=influencer?.influencerName||''
    if(!await loadInfluencerReferences(id))return
    if(form.supplierId&&!purchaseSupplierOptions.value.some(supplier=>String(supplier.supplierId)===String(form.supplierId))){
      form.supplierId=null
      form.supplierNameSnapshot=''
    }
    let cleared=false
    for(const row of form.items){
      if(row.productId&&isPurchaseBoundType(row.productTypeSnapshot)&&!isPurchaseBoundBinding(row.productId,form.supplierId)){
        Object.assign(row,blankPurchaseItem())
        cleared=true
      }
    }
    if(cleared)proxy.$modal.msgWarning('达人已变更，请重新选择符合供应商的成品或赠品商品')
    return
  }
  if(!influencer){
    form.influencerName='';await loadInfluencerReferences(null)
    if(form.docType==='SALES_OUT')form.salesChannel=''
    for(const row of form.items){
      clearRowInfluencerPrice(row)
      if(isUnlinkedInfluencerReturn()){row.unitPrice=0;row.saleRole='NORMAL';row.pricingMode='SEPARATE'}
    }
    if(isUnlinkedInfluencerReturn()&&!actualRefundManuallyEdited.value)form.actualRefundAmount=0
    return
  }
  form.influencerName=influencer.influencerName||''
  if(form.docType==='SALES_OUT')form.salesChannel=influencer.salesChannel||''
  else if(!form.salesChannel&&influencer.salesChannel)form.salesChannel=influencer.salesChannel
  if(!await loadInfluencerReferences(id))return
  if(form.docType==='SALES_OUT'&&form.items.some(row=>row.productId&&!isAllowedSalesProduct(row))){
    form.items=[blankItem()]
    proxy.$modal.msgWarning(translateText('已清空不符合当前达人绑定或搭售商品库存条件的商品明细，请重新选择'))
  }
  for(const row of form.items){if(normalizedPricingMode(row)!=='INCLUDED')row.unitPrice=0}
  applyInfluencerPriceToRows()
  if(form.docType==='CUSTOMER_RETURN'&&!actualRefundManuallyEdited.value)form.actualRefundAmount=Math.round((expectedReturnRefund.value+Number.EPSILON)*100)/100
}
async function customerReturnInfluencerChanged(id){
  form.sourceDocumentId=null
  form.sourceDocNo=''
  form.items=[blankItem()]
  form.unlinkedReason=''
  form.actualRefundAmount=null
  actualRefundManuallyEdited.value=false
  form.salesChannel=influencerOf(id)?.salesChannel||''
  await Promise.all([influencerChanged(id),refreshCustomerReturnSales(id),loadCustomerReturnProductStats(id)])
}
async function refreshCustomerReturnSales(id){
  const request=++salesSourceRequest
  if(!id){salesDocuments.value=[];salesSourceLoading.value=false;salesSourceError.value=false;return}
  salesSourceLoading.value=true
  salesSourceError.value=false
  try{
    const pageSize=500
    const params={pageSize,docType:'SALES_OUT',status:'POSTED',influencerId:id}
    const first=await listJewelryDocuments({...params,pageNum:1})
    const documents=[...(first.rows||[])]
    const pageCount=Math.ceil(Number(first.total||documents.length)/pageSize)
    for(let pageNum=2;pageNum<=pageCount;pageNum++){
      const page=await listJewelryDocuments({...params,pageNum})
      documents.push(...(page.rows||[]))
    }
    if(request===salesSourceRequest&&String(form.influencerId)===String(id))
      salesDocuments.value=documents.filter(source=>String(source.influencerId)===String(id))
  }catch(error){
    if(request===salesSourceRequest){
      salesDocuments.value=[]
      salesSourceError.value=true
      proxy.$modal.msgError(error?.message||translateText('加载已入账销售单失败'))
    }
  }finally{
    if(request===salesSourceRequest)salesSourceLoading.value=false
  }
}
function customerReturnSalesOpened(visible){
  if(visible&&form.influencerId&&!readonly.value&&!salesSourceLoading.value)refreshCustomerReturnSales(form.influencerId)
}
const availableProducts=row=>{
  let available=products.value
  if(supportsProductFilters.value)available=available.filter(product=>matchesJewelryProductFilters(product,{
    ...productFilters,supplierName:suppliers.value.find(item=>String(item.supplierId)===String(productFilters.supplierId))?.supplierName||''
  },productFilterRelations.value)
    ||(row.productId!=null&&String(row.productId)===String(product.productId)))
  if(form.docType==='SAMPLE_IN')available=available.filter(product=>product.productType==='SAMPLE')
  if(form.docType==='PURCHASE_IN')available=available.filter(product=>product.productType!=='SAMPLE'&&product.productType===row.productTypeSnapshot&&form.supplierId
    &&(!isPurchaseBoundType(product.productType)||isPurchaseBoundBinding(product.productId,form.supplierId)
      ||(row.productId!=null&&String(row.productId)===String(product.productId)&&!!form.documentId)))
  if(form.docType==='SALES_OUT')available=available.filter(product=>normalizedSaleRole(row)==='ADDON'
    ?isAvailableAddonProduct(product)
    :isAllowedSalesProduct(row,product.productId)
      ||(row.productId!=null&&String(row.productId)===String(product.productId)&&!!form.documentId))
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){
    if(normalizedSaleRole(row)==='ADDON'){
      const addonIds=new Set(customerReturnAddonStats(bundleMainRow(row)).map(item=>String(item.productId)))
      available=available.filter(product=>addonIds.has(String(product.productId)))
    }else available=available.filter(product=>product.productType==='FINISHED'
      &&isActiveCustomerReturnBinding(product.productId))
  }
  return normalizedSaleRole(row)==='ADDON'?available.filter(product=>product.productType!=='FINISHED'):available
}
const productFilterRelations=computed(()=>{
  if(!productFilters.influencerId)return{}
  const active=productFilterBindings.value.filter(binding=>binding.bindingStatus==null||String(binding.bindingStatus)==='0')
  return{
    influencerProductIds:new Set(active.map(binding=>String(binding.productId))),
    boundSupplierProductIds:new Set(active.filter(binding=>productFilters.supplierId
      &&String(binding.preferredSupplierId||'')===String(productFilters.supplierId)).map(binding=>String(binding.productId)))
  }
})
async function productFilterInfluencerChanged(id){
  const request=++productFilterRequest
  productFilterBindings.value=[]
  if(!id){productFilterLoading.value=false;return}
  productFilterLoading.value=true
  try{
    const response=await getJewelryInfluencerProductPrices(id)
    if(request===productFilterRequest&&String(productFilters.influencerId)===String(id))productFilterBindings.value=response.data||[]
  }catch(error){
    if(request===productFilterRequest)proxy.$modal.msgError(error?.message||translateText("加载达人绑定供应商失败"))
  }finally{if(request===productFilterRequest)productFilterLoading.value=false}
}
function purchaseTypeChanged(row){
  const type=row.productTypeSnapshot
  Object.assign(row,blankItem(),{productTypeSnapshot:type})
  if(purchaseHasBoundRows.value&&(!form.influencerId
    ||!purchaseSupplierOptions.value.some(supplier=>String(supplier.supplierId)===String(form.supplierId)))){
    form.supplierId=null
    form.supplierNameSnapshot=''
  }
}
async function supplierReturnInfluencerChanged(id){
  const influencer=influencerOf(id)
  form.influencerName=influencer?.influencerName||''
  form.supplierId=null
  form.supplierNameSnapshot=''
  form.sourceDocumentId=null
  form.sourceDocNo=''
  form.items=[blankItem()]
  purchaseDocuments.value=[]
  try{await loadInfluencerReferences(id)}catch(error){influencerProductPrices.value=[];proxy.$modal.msgError(error?.message||translateText("加载达人绑定供应商失败"))}
}
const saleGroupKey=row=>normalizedSaleRole(row)==='NORMAL'?'NORMAL':String(row.bundleGroupNo||'')
const canAddAddon=row=>normalizedSaleRole(row)!=='ADDON'&&productOf(row)?.productType==='FINISHED'
const nextBundleGroupNo=()=>Math.max(0,...form.items.map(item=>Number(item.bundleGroupNo||0)))+1
function sampleSkuChanged(row){
  const sku=String(row.sampleSkuInput||'').trim().toUpperCase()
  const product=products.value.find(item=>item.productType==='SAMPLE'&&String(item.sku||'').trim().toUpperCase()===sku)
  if(!product){row.productId=null;row.skuSnapshot='';if(sku)proxy.$modal.msgWarning(translateText("未找到启用的样品SKU；可点击“新增商品”建立样品档案，已停用样品请先启用"));return}
  row.productId=product.productId
  productChanged(row)
}
function productChanged(row){
  const product=productOf(row)
  if(!product)return
  if(form.docType==='PURCHASE_IN'&&product.productType==='SAMPLE'){
    row.productId=null
    proxy.$modal.msgWarning('样品商品请使用样品入库单据')
    return
  }
  if(form.docType==='PURCHASE_IN'&&(!form.supplierId||product.productType!==row.productTypeSnapshot
    ||(isPurchaseBoundType(product.productType)&&!isPurchaseBoundBinding(product.productId,form.supplierId)))){
    row.productId=null
    proxy.$modal.msgWarning('请选择符合当前达人和供应商的商品')
    return
  }
  if(form.docType==='SALES_OUT'&&normalizedSaleRole(row)==='ADDON'&&!isAvailableAddonProduct(product)){
    proxy.$modal.msgWarning(translateText('搭售商品只能选择有可用库存的配件或当前达人已绑定的赠品'))
    row.productId=null;clearRowInfluencerPrice(row);return
  }
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){
    const addon=normalizedSaleRole(row)==='ADDON'
    const valid=addon
      ?['ACCESSORY','GIFT'].includes(product.productType)&&!!customerReturnProductStatOf(product.productId,row)
      :product.productType==='FINISHED'&&isActiveCustomerReturnBinding(product.productId)
    if(!valid){
      proxy.$modal.msgWarning(translateText(addon?'只能选择历史上随该成品售出的配件或赠品':'客户退货只能选择当前达人已绑定的成品商品'))
      row.productId=null;clearRowInfluencerPrice(row);return
    }
    if(!addon&&normalizedSaleRole(row)==='MAIN'){
      for(const addonRow of form.items.filter(item=>normalizedSaleRole(item)==='ADDON'&&item.bundleGroupNo===row.bundleGroupNo))
        Object.assign(addonRow,blankItem(),{bundleGroupNo:row.bundleGroupNo,saleRole:'ADDON',pricingMode:'INCLUDED'})
    }
  }
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId
    &&Number(customerReturnProductStatOf(product.productId,row)?.remainingReturnQty||0)<=0){
    proxy.$modal.msgWarning(translateText('该商品没有剩余可退数量'))
    row.productId=null;clearRowInfluencerPrice(row);return
  }
  if(form.docType==='SALES_OUT'&&!isAllowedSalesProduct(row)){
    proxy.$modal.msgWarning(translateText('独立销售和组合主商品需完成达人绑定'))
    row.productId=null;clearRowInfluencerPrice(row);return
  }
  if(form.docType==='SAMPLE_IN'&&product.productType!=='SAMPLE'){proxy.$modal.msgWarning(translateText('样品入库只能选择样品商品'));row.productId=null;return}
  if(normalizedSaleRole(row)==='ADDON'&&product.productType==='FINISHED'){proxy.$modal.msgWarning(translateText('搭售商品不能选择成品商品'));row.productId=null;return}
  if(normalizedSaleRole(row)==='MAIN'&&product.productType!=='FINISHED'){proxy.$modal.msgWarning(translateText('销售组合主商品必须选择成品商品'));row.productId=null;return}
  const groupedDocument=['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)&&!form.sourceDocumentId
  const duplicate=form.items.some(item=>toRaw(item)!==toRaw(row)&&item.productId!=null&&String(item.productId)===String(row.productId)&&(form.docType==='SAMPLE_IN'?Boolean(item.bizDate&&row.bizDate&&item.bizDate===row.bizDate&&item.supplierId&&row.supplierId&&item.supplierId===row.supplierId):!groupedDocument||saleGroupKey(item)===saleGroupKey(row)))
  if(duplicate){proxy.$modal.msgWarning(groupedDocument?translateText('同一组合中不能重复选择同一商品'):form.docType==='SAMPLE_IN'?translateText('同一SKU、日期和供应商不能重复，请合并数量'):translateText('同一商品不能重复，请直接修改已有行的数量'));row.productId=null;return}
  if(form.docType==='SAMPLE_IN'){row.sampleSkuInput=product.sku||'';row.skuSnapshot=product.sku||''}
  row.productTypeSnapshot=product.productType||''
  row.specificationSnapshot=product.specification||''
  if(form.docType==='PURCHASE_IN')row.imageUrls=product.imageUrls||product.imageUrl||''
  row.unitCost=Number(product.avgCost||0)
  if(form.docType==='SAMPLE_IN')row.unitCost=0
  row.systemQty=Number(product.onHandQty||0)
  row.countedQty=Number(product.onHandQty||0)
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId){
    const stat=customerReturnProductStatOf(product.productId,row)
    row.soldQty=Number(stat?.soldQty||0)
    row.remainingReturnQty=Number(stat?.remainingReturnQty||0)
    row.qty=Math.min(Math.max(1,Number(row.qty||1)),row.remainingReturnQty)
    if(normalizedSaleRole(row)==='ADDON')row.pricingMode=stat?.pricingMode||'INCLUDED'
  }
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
  if(['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType)&&form.influencerId&&!applyInfluencerProductPrice(row)){
    row.productId=null
    clearRowInfluencerPrice(row)
  }
  if(form.docType==='PURCHASE_IN'&&isPurchaseBoundType(product.productType)){
    const binding=influencerPriceOf(row.productId)
    row.unitCost=Number(binding.unitCost||0)
    row.unitPrice=Number(binding.referencePurchasePrice||0)
    row.packFee=Number(binding.packFee||0)
    row.shipFee=Number(binding.shipFee||0)
    row.certFee=Number(binding.certFee||0)
  }
}
function addAddon(mainRow){
  if(normalizedSaleRole(mainRow)==='NORMAL'){
    mainRow.bundleGroupNo=nextBundleGroupNo()
    mainRow.saleRole='MAIN'
    mainRow.pricingMode='SEPARATE'
  }
  const addon={...blankItem(),bundleGroupNo:mainRow.bundleGroupNo,saleRole:'ADDON',pricingMode:'INCLUDED'}
  expandedBundleGroups[mainRow.bundleGroupNo]=true
  let insertAt=form.items.findIndex(item=>toRaw(item)===toRaw(mainRow))+1
  while(insertAt<form.items.length&&form.items[insertAt].bundleGroupNo===mainRow.bundleGroupNo)insertAt+=1
  form.items.splice(insertAt,0,addon)
}
function pricingModeChanged(row){if(isAccessoryPackaging(row))row.pricingMode='INCLUDED';if(normalizedPricingMode(row)==='INCLUDED'){row.unitPrice=0;row.packFee=0;row.shipFee=0;row.certFee=0;row.otherFee1=0;row.otherFee2=0;row.otherFee3=0;clearRowInfluencerPrice(row);row.platformRateSnapshot=0;row.commissionRateSnapshot=0;row.taxRateSnapshot=0}else applyInfluencerProductPrice(row)}
function addNormalItem(){form.items.push(form.docType==='PURCHASE_IN'?blankPurchaseItem():blankItem())}
async function removeItem(index){
  const row=form.items[index]
  const groupedDocument=form.docType==='SALES_OUT'||isUnlinkedInfluencerReturn()
  if(groupedDocument&&normalizedSaleRole(row)==='MAIN'){
    const groupItems=form.items.filter(item=>item.bundleGroupNo===row.bundleGroupNo)
    if(groupItems.length>1)await proxy.$modal.confirm(translateText("删除主商品会同时删除组合{0}的搭售商品，确认继续吗？", [row.bundleGroupNo]))
    form.items=form.items.filter(item=>item.bundleGroupNo!==row.bundleGroupNo)
    return
  }
  const groupNo=row.bundleGroupNo
  form.items.splice(index,1)
  if(groupedDocument&&normalizedSaleRole(row)==='ADDON'&&!form.items.some(item=>normalizedSaleRole(item)==='ADDON'&&item.bundleGroupNo===groupNo)){
    const main=form.items.find(item=>normalizedSaleRole(item)==='MAIN'&&item.bundleGroupNo===groupNo)
    if(main){main.bundleGroupNo=null;main.saleRole='NORMAL';main.pricingMode='SEPARATE'}
  }
}
const bundleRowClass=({row})=>normalizedSaleRole(row)==='ADDON'?'bundle-addon-row':''
function openQuickProduct(row){
  if(form.docType==='PURCHASE_IN'&&(!form.supplierId||isPurchaseBoundType(row.productTypeSnapshot))){
    proxy.$modal.msgWarning('请先选择供应商；成品商品请在达人档案完成绑定')
    return
  }
  activeProductRow.value=row
  Object.assign(quickProduct,blankQuickProduct())
  if(form.docType==='PURCHASE_IN')quickProduct.productType=row.productTypeSnapshot
  if(form.docType==='SAMPLE_IN'){quickProduct.sku=row.sampleSkuInput||'';quickProduct.productType='SAMPLE'}
  productDialog.value=true
}
async function saveQuickProduct(){
  await productFormRef.value.validate()
  if(form.docType==='PURCHASE_IN'&&(!form.supplierId||isPurchaseBoundType(quickProduct.productType)
    ||quickProduct.productType==='SAMPLE'||quickProduct.productType!==activeProductRow.value?.productTypeSnapshot)){
    proxy.$modal.msgError('请先选择供应商和可采购的商品类型；成品或赠品请在达人档案完成绑定，样品请使用样品入库')
    return
  }
  productSaving.value=true
  try{
    quickProduct.imageUrl=String(quickProduct.imageUrls||'').split(',')[0]||''
    await saveJewelryProduct(quickProduct)
    await reloadProducts()
    const created=products.value.find(p=>p.sku===quickProduct.sku&&p.productType===quickProduct.productType)
    if(!created)throw new Error(translateText("商品已保存，但未能重新加载，请刷新后选择"))
    activeProductRow.value.productId=created.productId
    activeProductRow.value.imageUrls=quickProduct.imageUrls||''
    productChanged(activeProductRow.value)
    productDialog.value=false
    proxy.$modal.msgSuccess(translateText("商品已新增并自动选中"))
  }finally{productSaving.value=false}
}
async function downloadImportTemplate(){
  const blob=await downloadJewelryDocumentImportTemplate(form.docType)
  saveAs(blob,translateText("{0}导入模板.xlsx", [labelOf(types,form.docType)]))
}
async function handleImportFile(uploadFile){
  if(!uploadFile?.raw)return
  if(form.docType==='SALES_OUT'&&!form.influencerId){proxy.$modal.msgWarning(translateText('请先选择达人/主播，再导入其已绑定的销售商品'));return}
  if(form.docType==='PURCHASE_IN'&&purchaseHasBoundRows.value&&!form.influencerId){proxy.$modal.msgWarning('成品或赠品采购入库请先选择达人/主播，再选择其绑定的供应商');return}
  if(form.docType==='PURCHASE_IN'&&!form.supplierId){proxy.$modal.msgWarning('请先选择供应商，再导入采购商品');return}
  importLoading.value=true
  importCompression.value=null
  try{
    let importFile=uploadFile.raw
    if(['PURCHASE_IN','SAMPLE_IN'].includes(form.docType)&&String(importFile.name||'').toLowerCase().endsWith('.xlsx')){
      importProgress.active=true
      importProgress.percentage=0
      importProgress.text=translateText("准备压缩")
      const compression=await compressXlsxImages(importFile,progress=>Object.assign(importProgress,progress))
      importFile=compression.file
      importCompression.value=compression
      if(compression.compressed){
        proxy.$modal.msgSuccess(translateText("本地压缩完成：{0} → {1}，处理 {2} 张图片", [formatFileSize(compression.originalSize), formatFileSize(compression.outputSize), compression.compressed]))
      }
      if(compression.skipped){
        proxy.$modal.msgWarning(translateText("{0} 张图片未能压缩，已保留原图", [compression.skipped]))
      }
      importProgress.text=translateText("正在上传并校验")
    }
    const r=await previewJewelryDocumentImport(form.docType,importFile)
    importPreview.value=r.data||{}
    applyImportBindingRules(importPreview.value)
    importNeedsValidation.value=false
    importDialog.value=true
  }catch(error){
    proxy.$modal.msgError(error?.message||translateText("Excel 导入失败"))
  }finally{
    importLoading.value=false
    importProgress.active=false
  }
}
function applyImportBindingRules(preview){
    if(form.docType==='PURCHASE_IN'){
      let additionalErrors=0
      for(const row of preview.rows||[]){
        if(!row.valid||!isPurchaseBoundType(row.productType))continue
        if(row.newProduct||!isPurchaseBoundBinding(row.productId,form.supplierId)){
          row.valid=false
          row.status='ERROR'
          row.errorMessage='成品或赠品须先在达人档案绑定当前供应商，导入时选择该达人和供应商'
          additionalErrors++
        }
      }
      preview.validCount=Number(preview.validCount||0)-additionalErrors
      preview.errorCount=Number(preview.errorCount||0)+additionalErrors
    }
    if(form.docType==='SALES_OUT'){
      let additionalErrors=0
      for(const row of preview.rows||[]){
        if(!row.valid)continue
        const binding=influencerPriceOf(row.productId)
        const product=products.value.find(item=>String(item.productId)===String(row.productId))
        if(product?.productType!=='FINISHED'||!isConfiguredSalesBinding(binding)){
          row.valid=false;row.status='ERROR';row.errorMessage=translateText('该商品未在所选达人档案中完成有效绑定')
          additionalErrors++;continue
        }
        row.unitPrice=Number(binding.fixedUnitPrice)
        row.unitCost=Number(binding.unitCost||0)
        row.packFee=Number(binding.packFee||0)
        row.shipFee=Number(binding.shipFee||0)
        row.certFee=Number(binding.certFee||0)
      }
      preview.validCount=Number(preview.validCount||0)-additionalErrors
      preview.errorCount=Number(preview.errorCount||0)+additionalErrors
    }
}
function markImportDirty(){
  importNeedsValidation.value=true
}
async function reviewImportRows(){
  if(!importPreview.value.rows?.length){proxy.$modal.msgWarning('请至少保留一行商品明细');return false}
  reviewingImport.value=true
  try{
    const result=await reviewJewelryDocumentImport(form.docType,importPreview.value.rows)
    const preview=result.data||{}
    applyImportBindingRules(preview)
    importPreview.value=preview
    importNeedsValidation.value=false
    if(Number(preview.errorCount)>0){proxy.$modal.msgWarning('仍有明细未通过校验，请按提示修正');return false}
    return true
  }catch(error){
    importNeedsValidation.value=true
    proxy.$modal.msgError(error?.message||'重新校验失败')
    return false
  }finally{reviewingImport.value=false}
}
async function applyImportRows(){
  if(Number(importPreview.value.errorCount)>0||Number(importPreview.value.validCount)<=0)return
  if(form.docType==='SALES_OUT'){
    if(!form.influencerId){proxy.$modal.msgError(translateText('请先选择达人/主播'));return}
    if(!await loadInfluencerReferences(form.influencerId))return
    if((importPreview.value.rows||[]).some(row=>!isAllowedSalesProduct({productId:row.productId,saleRole:'NORMAL',pricingMode:'SEPARATE'}))){
      proxy.$modal.msgError(translateText('达人商品绑定已变化，请重新预览 Excel 后导入'));return
    }
  }
  if(form.items.some(item=>item.productId)){
    await proxy.$modal.confirm(translateText("导入会替换当前已经填写的商品明细，确认继续吗？"))
  }
  applyingImport.value=true
  try{
    if(!await reviewImportRows())return
    await reloadProducts()
    const importProductKey=product=>`${product.productType||''}:${String(product.sku||'').trim().toUpperCase()}`
    const currentProducts=new Map(products.value.map(product=>[importProductKey(product),product]))
    const createdSkus=new Set()
    for(const row of importPreview.value.rows||[]){
      const skuKey=importProductKey(row)
      if(form.docType==='SALES_OUT'&&row.newProduct)throw new Error(translateText('SKU {0} 不在商品档案中，销售明细只能导入已绑定商品', [row.sku]))
      if(form.docType==='PURCHASE_IN'&&row.productType==='SAMPLE')throw new Error('样品商品请使用样品入库单据')
      if(form.docType==='PURCHASE_IN'&&isPurchaseBoundType(row.productType)&&row.newProduct)
        throw new Error('成品或赠品请先在达人档案中建立并绑定')
      if(row.newProduct&&!createdSkus.has(skuKey)){
        const existing=currentProducts.get(skuKey)
        if(existing){
          if(existing.productType!==row.productType||existing.productName!==row.productName)
            throw new Error(translateText("SKU {0} 已被其他商品占用，请重新预览Excel", [row.sku]))
          createdSkus.add(skuKey)
          continue
        }
        await saveJewelryProduct({
          sku:row.sku,productName:row.productName,productType:row.productType,
          imageUrl:row.imageUrl||'',imageUrls:row.imageUrls||row.imageUrl||'',
          unit:row.unit||'件',warningQty:5,status:'0',
          defaultPackFee:0,defaultShipFee:0,defaultCertFee:0
        })
        createdSkus.add(skuKey)
      }
    }
    await reloadProducts()
    const productMap=new Map(products.value.map(product=>[importProductKey(product),product]))
    form.items=(importPreview.value.rows||[]).map(row=>{
      const item=blankItem()
      const product=productMap.get(importProductKey(row))
      if(!product)throw new Error(translateText("SKU {0} 导入后未找到商品档案", [row.sku]))
      item.productId=product.productId
      item.productTypeSnapshot=product.productType||''
      item.imageUrls=form.docType==='SAMPLE_IN'?(row.imageUrls||row.imageUrl||''):(row.imageUrls||row.imageUrl||product.imageUrls||product.imageUrl||'')
      item.unitCost=form.docType==='SAMPLE_IN'?0:Number(product.avgCost||0)
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
        if(form.docType==='PURCHASE_IN'&&isPurchaseBoundType(product.productType)){
          const binding=influencerPriceOf(product.productId)
          if(!isPurchaseBoundBinding(product.productId,form.supplierId))throw new Error('导入的成品或赠品不符合当前达人和供应商')
          item.unitCost=Number(binding.unitCost||0)
          if(item.unitPrice<=0)item.unitPrice=Number(binding.referencePurchasePrice||0)
          item.packFee=Number(binding.packFee||0)
          item.shipFee=Number(binding.shipFee||0)
          item.certFee=Number(binding.certFee||0)
        }
        if(form.docType==='SAMPLE_IN'){
          item.sampleSkuInput=row.sku||''
          item.skuSnapshot=row.sku||''
          item.bizDate=row.bizDate||null
          item.supplierId=row.supplierId||null
          item.supplierNameSnapshot=row.supplierNameSnapshot||''
          item.packFee=0
          item.shipFee=0
          item.certFee=0
        }
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
    if(form.docType==='PURCHASE_IN'&&!purchaseHasBoundRows.value){
      form.influencerId=null
      form.influencerName=''
      influencerProductPrices.value=[]
    }
    if(form.docType==='SALES_OUT'&&form.influencerId)applyInfluencerPriceToRows()
    proxy.$modal.msgSuccess(translateText("已导入 {0} 行商品明细", [form.items.length]))
  }finally{applyingImport.value=false}
}
function removeImportPreviewRow(index){
  const rows=[...(importPreview.value.rows||[])]
  if(index<0||index>=rows.length)return
  rows.splice(index,1)
  if(form.docType==='SAMPLE_IN'){
    const duplicateMessage=translateText("同一SKU、业务日期和供应商重复，请合并数量")
    const counts=new Map()
    const keyOf=row=>(row.productId||row.sku)&&row.bizDate&&row.supplierId
      ?`${row.productId?'id:'+row.productId:'sku:'+String(row.sku).trim().toUpperCase()}:${row.bizDate}:${row.supplierId}`:null
    rows.forEach(row=>{const key=keyOf(row);if(key)counts.set(key,(counts.get(key)||0)+1)})
    rows.forEach(row=>{
      const errors=String(row.errorMessage||'').split('；').filter(message=>message&&message!==duplicateMessage)
      const key=keyOf(row)
      if(key&&counts.get(key)>1)errors.push(duplicateMessage)
      row.errorMessage=errors.join('；')
      row.valid=!errors.length
      row.status=row.valid?(row.newProduct?'NEW':'VALID'):'ERROR'
    })
  }
  importPreview.value={
    ...importPreview.value,
    rows,
    validCount:rows.filter(row=>row.valid).length,
    errorCount:rows.filter(row=>!row.valid).length,
    newProductCount:new Set(rows.filter(row=>row.valid&&row.newProduct).map(row=>row.sku)).size
  }
  importNeedsValidation.value=true
}
async function loadSupplierReturnSources(influencerId,supplierId){purchaseDocuments.value=influencerId&&supplierId?((await listSupplierReturnSources(influencerId,supplierId)).data||[]):[]}
async function supplierChanged(id){
  form.supplierNameSnapshot=suppliers.value.find(x=>x.supplierId===id)?.supplierName||''
  if(form.docType==='PURCHASE_IN'){
    let cleared=false
    for(const row of form.items){
      if(row.productId&&isPurchaseBoundType(row.productTypeSnapshot)&&!isPurchaseBoundBinding(row.productId,id)){
        Object.assign(row,blankPurchaseItem())
        cleared=true
      }
    }
    if(cleared)proxy.$modal.msgWarning('供应商已变更，请重新选择该供应商下的达人绑定成品或赠品')
    return
  }
  if(form.docType!=='SUPPLIER_RETURN')return
  form.sourceDocumentId=null
  form.sourceDocNo=''
  form.items=[blankItem()]
  try{await loadSupplierReturnSources(form.influencerId,id)}catch(error){purchaseDocuments.value=[];proxy.$modal.msgError(error?.message||translateText("加载采购单失败"))}
}
async function loadSupplierReturnSource(id,preserveCurrent=false){
  const currentBySourceItem=new Map((form.items||[]).filter(item=>item.sourceItemId).map(item=>[item.sourceItemId,item]))
  const source=(await getSupplierReturnSource(id,form.documentId)).data
  if(String(source.supplierId)!==String(form.supplierId))throw new Error(translateText("原采购单与所选供应商不一致"))
  if(String(source.influencerId)!==String(form.influencerId))throw new Error(translateText("原采购单与所选达人/主播不一致"))
  form.sourceDocNo=source.docNo||''
  form.items=(source.items||[]).filter(sourceItem=>Number(sourceItem.remainingReturnQty||0)>0
    || (preserveCurrent && currentBySourceItem.has(sourceItem.itemId))).map(sourceItem=>{
    const current=preserveCurrent?currentBySourceItem.get(sourceItem.itemId):null
    const remaining=Number(sourceItem.remainingReturnQty||0)
    return {...blankItem(),...(current||{}),productId:sourceItem.productId,sourceItemId:sourceItem.itemId,
      qty:current?Number(current.qty||0):Math.min(1,remaining),remainingReturnQty:remaining,
      unitPrice:current?Number(current.unitPrice||0):Number(sourceItem.unitPrice||0),
      sourceUnitPrice:Number(sourceItem.unitPrice||0),
      productTypeSnapshot:sourceItem.productTypeSnapshot||'',specificationSnapshot:sourceItem.specificationSnapshot||'',
      imageUrls:sourceItem.imageUrls||''}
  })
  if(!form.items.length)throw new Error(translateText("该采购单没有当前可退商品，请检查采购单剩余额度和可用库存"))
}
async function supplierReturnSourceChanged(id){
  form.sourceDocNo=''
  if(!id){form.items=[blankItem()];return}
  try{await loadSupplierReturnSource(id,false)}catch(error){form.sourceDocumentId=null;form.items=[blankItem()];proxy.$modal.msgError(error?.message||translateText("加载采购单失败"))}
}
const linkedReturnMaxQty=row=>{
  if(form.docType==='SUPPLIER_RETURN'&&form.sourceDocumentId&&row.sourceItemId)return Math.max(1,Number(row.remainingReturnQty||0))
  if(form.docType==='CUSTOMER_RETURN'&&form.sourceDocumentId&&row.sourceItemId)return Math.max(1,Number(row.remainingReturnQty||0))
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&row.productId)return Math.max(1,Number(row.remainingReturnQty||0))
  return undefined
}
function initialCustomerReturnQty(sourceItem,sourceItems){
  const remaining=Number(sourceItem.remainingReturnQty||0)
  if(!sourceItem.bundleGroupNo||normalizedSaleRole(sourceItem)!=='ADDON')return Math.min(1,remaining)
  const main=sourceItems.find(item=>item.bundleGroupNo===sourceItem.bundleGroupNo&&normalizedSaleRole(item)==='MAIN')
  if(!main||Number(main.qty||0)<=0)return Math.min(1,remaining)
  const mainQty=Math.min(1,Number(main.remainingReturnQty||0))
  const proportional=Math.max(1,Math.round(mainQty*Number(sourceItem.qty||0)/Number(main.qty||1)))
  return Math.min(remaining,proportional)
}
function linkedReturnQtyChanged(row){
  if(form.docType!=='CUSTOMER_RETURN'||!form.sourceDocumentId||normalizedSaleRole(row)!=='MAIN'||!row.bundleGroupNo)return
  const mainSourceQty=Number(row.sourceQty||0)
  if(mainSourceQty<=0)return
  for(const addon of form.items.filter(item=>item.bundleGroupNo===row.bundleGroupNo&&normalizedSaleRole(item)==='ADDON')){
    const proportional=Math.max(1,Math.round(Number(row.qty||0)*Number(addon.sourceQty||0)/mainSourceQty))
    addon.qty=Math.min(Number(addon.remainingReturnQty||0),proportional)
  }
}
async function loadCustomerReturnSource(id,preserveCurrent=false){
  const currentBySourceItem=new Map((form.items||[]).filter(item=>item.sourceItemId).map(item=>[item.sourceItemId,item]))
  const source=(await getCustomerReturnSource(id,form.documentId)).data
  if(!preserveCurrent&&(!form.influencerId||String(source.influencerId)!==String(form.influencerId)))
    throw new Error(translateText('原销售单与所选达人/主播不一致'))
  const sourceItems=source.items||[]
  form.sourceDocNo=source.docNo||''
  form.salesChannel=source.salesChannel||''
  form.influencerId=source.influencerId||null
  form.influencerName=source.influencerName||''
  await loadInfluencerReferences(form.influencerId)
  form.platformRate=0
  form.commissionRate=0
  form.taxRate=0
  form.items=sourceItems.map(sourceItem=>{
    const current=preserveCurrent?currentBySourceItem.get(sourceItem.itemId):null
    return {...blankItem(),...(current||{}),productId:sourceItem.productId,sourceItemId:sourceItem.itemId,
      qty:current?Number(current.qty||0):initialCustomerReturnQty(sourceItem,sourceItems),
      sourceQty:Number(sourceItem.qty||0),remainingReturnQty:Number(sourceItem.remainingReturnQty||0),
      unitPrice:Number(sourceItem.unitPrice||0),unitCost:Number(sourceItem.unitCost||0),packFee:0,
      shipFee:Number(sourceItem.shipFee||0),certFee:Number(sourceItem.certFee||0),bundleGroupNo:sourceItem.bundleGroupNo,
      saleRole:sourceItem.saleRole||'NORMAL',pricingMode:sourceItem.pricingMode||'SEPARATE',
      productTypeSnapshot:sourceItem.productTypeSnapshot||'',specificationSnapshot:sourceItem.specificationSnapshot||'',
      influencerPriceSnapshot:sourceItem.influencerPriceSnapshot??sourceItem.unitPrice,
      influencerPriceVersion:Number(sourceItem.influencerPriceVersion||0),
      influencerPriceStatus:Number(sourceItem.influencerPriceVersion||0)>0?'PRICED':''}
  })
  if(!form.items.length)throw new Error(translateText("该销售单已没有可退商品"))
}
async function salesSourceChanged(id){
  actualRefundManuallyEdited.value=false
  form.unlinkedReason=''
  form.actualRefundAmount=null
  form.sourceDocNo=''
  if(!id){form.items=[blankItem()];form.salesChannel=influencerOf(form.influencerId)?.salesChannel||'';form.platformRate=0;form.commissionRate=0;form.taxRate=0;await loadCustomerReturnProductStats(form.influencerId);return}
  try{
    await loadCustomerReturnSource(id,false)
    form.actualRefundAmount=expectedReturnRefund.value
  }catch(error){
    form.sourceDocumentId=null
    form.items=[blankItem()]
    form.salesChannel=influencerOf(form.influencerId)?.salesChannel||''
    proxy.$modal.msgError(error?.message||translateText("加载销售单失败"))
  }
}
async function loadInspectionSources(influencerId){
  const sequence=++inspectionLoadSequence
  returnDocuments.value=[]
  if(!influencerId)return
  const result=await listJewelryDocuments({pageNum:1,pageSize:500,docType:'CUSTOMER_RETURN',status:'POSTED',influencerId})
  if(sequence===inspectionLoadSequence&&form.docType==='RETURN_INSPECT'&&String(form.influencerId)===String(influencerId))
    returnDocuments.value=result.rows||[]
}
async function inspectionInfluencerChanged(id){
  form.influencerName=influencerOf(id)?.influencerName||''
  form.sourceDocumentId=null
  form.sourceDocNo=''
  form.items=[blankItem()]
  try{await loadInspectionSources(id)}catch(error){returnDocuments.value=[];proxy.$modal.msgError(error?.message||translateText('加载客户退货单失败'))}
}
async function loadInspectionSource(id,preserveCurrent=false){
  const currentBySourceItem=new Map((form.items||[]).filter(item=>item.sourceItemId).map(item=>[item.sourceItemId,item]))
  const source=(await getReturnInspectionSource(id,form.documentId)).data
  if(preserveCurrent&&!form.influencerId&&source.influencerId){
    form.influencerId=source.influencerId
    await loadInspectionSources(form.influencerId)
  }
  if(!form.influencerId||String(source.influencerId)!==String(form.influencerId))
    throw new Error(translateText('原客户退货单与所选达人/主播不一致'))
  form.influencerName=source.influencerName||''
  form.sourceDocNo=source.docNo||''
  form.items=(source.items||[]).map(sourceItem=>{
    const current=preserveCurrent?currentBySourceItem.get(sourceItem.itemId):null
    return {...blankItem(),...(current||{}),productId:sourceItem.productId,sourceItemId:sourceItem.itemId,
      qty:Number(sourceItem.remainingInspectQty||0),remainingInspectQty:Number(sourceItem.remainingInspectQty||0),
      unitCost:Number(sourceItem.unitCost||0),productTypeSnapshot:sourceItem.productTypeSnapshot||'',
      specificationSnapshot:sourceItem.specificationSnapshot||'',imageUrls:sourceItem.imageUrls||''}
  })
  if(!form.items.length)throw new Error(translateText("该客户退货单的商品已全部完成质检"))
}
async function inspectionSourceChanged(id){
  form.sourceDocNo=''
  if(!id){form.items=[blankItem()];return}
  try{await loadInspectionSource(id,false)}catch(error){form.sourceDocumentId=null;form.items=[blankItem()];proxy.$modal.msgError(error?.message||translateText("加载客户退货单失败"))}
}
function actualRefundTotalChanged(){actualRefundManuallyEdited.value=true}
async function typeChanged(){influencerLoadSequence++;inspectionLoadSequence++;customerReturnProductRequest++;resetExpandedBundleGroups();resetProductFilters();form.sourceWarehouse='';form.targetWarehouse='';form.supplierReturnDate=null;actualRefundManuallyEdited.value=false;form.items=[form.docType==='PURCHASE_IN'?blankPurchaseItem():blankItem()];form.supplierId=null;form.supplierNameSnapshot='';form.salesChannel='';form.influencerId=null;form.influencerName='';influencerProductPrices.value=[];influencerBundleItems.value=[];customerReturnProductStats.value=[];customerReturnProductLoading.value=false;form.influencerPriceSnapshot=null;form.influencerPriceVersion=0;form.platformRate=0;form.commissionRate=0;form.taxRate=0;form.returnReason='';form.sourceDocumentId=null;form.sourceDocNo='';form.unlinkedReason='';form.actualRefundAmount=null;purchaseDocuments.value=[];returnDocuments.value=[];importPreview.value={};importCompression.value=null;await reloadProducts(form.docType==='COST_ADJUST'?'COST_ADJUST':undefined)}
function validateDocument(requireSubmit=false){
  if(form.docType==='PURCHASE_IN'&&form.items.some(row=>!row.productTypeSnapshot)){proxy.$modal.msgError(translateText('请先选择商品类型'));return false}
  if(form.docType==='PURCHASE_IN'&&form.items.some(row=>row.productTypeSnapshot==='SAMPLE'||productOf(row)?.productType==='SAMPLE')){proxy.$modal.msgError('样品商品请使用样品入库单据');return false}
  if(form.docType==='SAMPLE_IN'&&form.items.some(x=>productOf(x)?.productType!=='SAMPLE')){proxy.$modal.msgError(translateText("样品入库只能选择样品商品"));return false}
  if(form.docType==='SAMPLE_IN'&&form.items.some(x=>!x.bizDate)){proxy.$modal.msgError(translateText("请填写每行样品商品的业务日期"));return false}
  if(form.docType==='SAMPLE_IN'&&form.items.some(x=>!x.supplierId)){proxy.$modal.msgError(translateText("请填写每行样品商品的供应商"));return false}
  if(form.docType==='SAMPLE_IN'&&form.items.some(x=>!x.sampleSkuInput?.trim())){proxy.$modal.msgError(translateText("请填写每行样品商品的SKU"));return false}
  if(form.docType==='SAMPLE_IN'&&form.items.some(x=>String(x.sampleSkuInput||'').trim().toUpperCase()!==String(productOf(x)?.sku||'').trim().toUpperCase())){proxy.$modal.msgError(translateText("SKU与所选商品不一致"));return false}
  if(form.docType==='SAMPLE_IN'&&new Set(form.items.map(x=>`${x.productId}:${x.bizDate}:${x.supplierId}`)).size!==form.items.length){proxy.$modal.msgError(translateText("同一SKU、业务日期和供应商不能重复，请合并数量"));return false}
  if(isTransfer(form)){
    if(!form.sourceWarehouse?.trim()||!form.targetWarehouse?.trim()){proxy.$modal.msgError(translateText("请填写出库仓库和入库仓库"));return false}
    if(form.sourceWarehouse.trim().toLowerCase()===form.targetWarehouse.trim().toLowerCase()){proxy.$modal.msgError(translateText("出库仓库与入库仓库不能相同"));return false}
  }
  if(form.docType==='PURCHASE_IN'&&form.supplierReturnDate&&form.bizDate&&form.supplierReturnDate<form.bizDate){proxy.$modal.msgError(translateText("约定退货日期不能早于采购入库业务日期"));return false}
  if(form.docType==='PURCHASE_IN'&&purchaseHasBoundRows.value&&!form.influencerId){proxy.$modal.msgError('成品或赠品采购入库请先选择达人/主播');return false}
  if(form.docType==='PURCHASE_IN'&&form.items.some(row=>row.productId&&productOf(row)?.productType!==row.productTypeSnapshot)){
    proxy.$modal.msgError('商品类型与所选商品不一致，请重新选择');return false
  }
  if(form.docType==='PURCHASE_IN'&&form.items.some(row=>row.productId&&isPurchaseBoundType(productOf(row)?.productType)
    &&!isPurchaseBoundBinding(row.productId,form.supplierId))){
    proxy.$modal.msgError('成品或赠品必须已绑定当前达人和供应商，请重新选择');return false
  }
  if(form.docType==='SUPPLIER_RETURN'&&!form.influencerId){proxy.$modal.msgError(translateText("供应商退货请先选择达人/主播"));return false}
  if(form.docType==='SUPPLIER_RETURN'&&!form.sourceDocumentId){proxy.$modal.msgError(translateText("供应商退货必须选择原采购单"));return false}
  if(form.docType==='CUSTOMER_RETURN'&&!form.influencerId){proxy.$modal.msgError(translateText("客户退货请先选择达人/主播"));return false}
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&form.items.some(x=>!isAllowedCustomerReturnProduct(x))){proxy.$modal.msgError(translateText("客户退货只能选择当前达人已绑定的成品，搭售退货只能选择历史上随该成品售出的配件或赠品"));return false}
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&!customerReturnBundlesValid()){proxy.$modal.msgError(translateText("每个退货组合必须包含一个成品主商品和至少一个搭售商品"));return false}
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&form.items.some(x=>Number(x.qty||0)>Number(x.remainingReturnQty||0))){proxy.$modal.msgError(translateText("退货数量不能超过剩余可退数量"));return false}
  if(form.docType==='CUSTOMER_RETURN'&&(form.actualRefundAmount===null||Number(form.actualRefundAmount)<0)){proxy.$modal.msgError(translateText("请填写实际退款总额"));return false}
  if(form.docType==='SALES_OUT'&&!form.influencerId){proxy.$modal.msgError(translateText("销售出库必须选择达人/主播"));return false}
  if(form.docType==='SALES_OUT'&&form.items.some(x=>x.productId&&!isAllowedSalesProduct(x))){proxy.$modal.msgError(translateText('销售商品需完成达人绑定；搭售仅可选择有库存的配件或当前达人已绑定的赠品'));return false}
  if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&form.items.some(x=>Number(x.unitPrice||0)<=0)){proxy.$modal.msgError(translateText("未关联原销售单时，请填写每件商品的实际退款单价"));return false}
  if(form.docType==='SALES_OUT'&&form.items.some(x=>normalizedPricingMode(x)!=='INCLUDED'&&Number(x.unitPrice||0)<=0)){proxy.$modal.msgError(translateText('请在达人档案配置商品直播价，并重新选择该商品'));return false}
  if(form.docType==='RETURN_INSPECT'&&!form.influencerId){proxy.$modal.msgError(translateText('退货质检请先选择达人/主播'));return false}
  if(form.docType==='RETURN_INSPECT'&&!form.sourceDocumentId){proxy.$modal.msgError(translateText("退货质检必须选择原客户退货单"));return false}
  if(!form.items.length||form.items.some(x=>!x.productId)){proxy.$modal.msgError(translateText("请完整选择商品"));return false}
  if(form.docType==='SUPPLIER_RETURN'&&form.items.some(x=>!x.sourceItemId)){proxy.$modal.msgError(translateText("退供商品必须来自原采购单"));return false}
  if(form.docType==='SUPPLIER_RETURN'&&form.items.some(x=>Number(x.qty||0)>Number(x.remainingReturnQty||0))){proxy.$modal.msgError(translateText("退货数量不能超过当前剩余可退数量，请检查可用库存；不可退的行请删除"));return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>!x.sourceItemId)){proxy.$modal.msgError(translateText("质检商品必须来自原客户退货单"));return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)<=0)){proxy.$modal.msgError(translateText("每行至少填写一个良品或次品数量"));return false}
  if(form.docType==='RETURN_INSPECT'&&form.items.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)>Number(x.remainingInspectQty||0))){proxy.$modal.msgError(translateText("质检数量不能超过原退货单剩余待检数量"));return false}
  if(needsSupplier.value&&!form.supplierId){proxy.$modal.msgError(translateText("请选择供应商"));return false}
  if(needsSalesChannel.value&&!form.salesChannel.trim()){proxy.$modal.msgError(translateText("请填写销售渠道"));return false}
  if(needsReason.value&&!form.returnReason.trim()){proxy.$modal.msgError(translateText("请填写{0}", [reasonLabel.value]));return false}
  if(form.docType==='COST_ADJUST'&&form.items.some(x=>Number(x.qty||0)<=0)){proxy.$modal.msgError(translateText("只能调整当前有库存的商品"));return false}
  if(form.docType==='COST_ADJUST'&&form.items.some(x=>Number(x.unitPrice||0)===Number(x.unitCost||0))){proxy.$modal.msgError(translateText("调整后平均成本不能与当前平均成本相同"));return false}
  if(requireSubmit&&form.docType==='SALES_OUT'&&accessoryPackagingProblems.value.length){const group=accessoryPackagingProblems.value[0];proxy.$modal.msgError(translateText("组合{0}配件耗材成本 ¥{1}，高于包装费 ¥{2}，还差 ¥{3}，请调整包装费后再提交", [group.groupNo, money(group.accessoryTotal), money(group.manualPackagingTotal), money(group.packagingShortage)]));return false}
  return true
}
async function save(andSubmit=false){
  if(!validateDocument(andSubmit))return
  savingAction.value=andSubmit?'submit':'draft'
  try{
    const response=await saveJewelryDocument(form)
    form.documentId=response.data?.documentId||form.documentId
    if(andSubmit){
      if(!form.documentId)throw new Error(translateText("单据已保存，但未返回单据ID"))
      try{
        await submitJewelryDocument(form.documentId)
      }catch(error){
        proxy.$modal.msgError(translateText("草稿已保存，但未提交：{0}", [error?.message||translateText("请检查包装费和库存后重试")]))
        return
      }
    }
    proxy.$modal.msgSuccess(andSubmit?translateText("单据已直接提交审核"):translateText("草稿已保存"))
    dialog.value=false
    load()
  }finally{savingAction.value=''}
}
async function submit(row){await proxy.$modal.confirm(translateText("确认提交单据 {0}？", [row.docNo]));await submitJewelryDocument(row.documentId);proxy.$modal.msgSuccess(translateText("已提交"));load()}
async function removeDraft(row){await proxy.$modal.confirm(translateText("确认删除草稿 {0}？删除后无法恢复。", [row.docNo]));await deleteJewelryDraft(row.documentId);proxy.$modal.msgSuccess(translateText("草稿已删除"));load()}
async function withdraw(row){await proxy.$modal.confirm(translateText("确认撤回单据 {0}？", [row.docNo]));await withdrawJewelryDocument(row.documentId);proxy.$modal.msgSuccess(translateText("已撤回"));load()}
async function reverse(row){await proxy.$modal.confirm(translateText("确认对单据 {0} 发起整单红冲？{1}", [row.docNo, ['STOCK_ADJUST','COST_ADJUST'].includes(row.docType)?translateText("红冲单需要审核员和管理员两级审批。"):translateText("红冲单审核通过后入账。")]));await createJewelryReversal(row.documentId);proxy.$modal.msgSuccess(translateText("红冲草稿已生成"));load()}
preload();load()
</script>
<style scoped>.sheet{border:1px solid #cfd5dc}.sheet-head{display:grid;grid-template-columns:repeat(6,minmax(150px,1fr));gap:12px;padding:14px;background:#f4f6f8}.sheet-head :deep(.el-form-item){margin:0}.sheet-head :deep(.el-input-number),.sheet-head :deep(.el-select),.sheet-head :deep(.el-date-editor){width:100%}.item-toolbar{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-top:1px solid #d9dee5;background:#fafbfc}.item-toolbar>div:first-child{display:flex;align-items:baseline;gap:10px}.item-toolbar b{color:#334155;font-size:14px}.item-toolbar span{color:#8490a0;font-size:12px}.item-toolbar-actions{display:flex;align-items:center;gap:8px}.product-filter-bar{display:flex;align-items:center;gap:0 12px;margin:0;padding:12px 14px 0;border-top:1px solid #d9dee5;background:#fafbfc}.product-filter-bar :deep(.el-form-item){margin-right:0}.product-filter-bar :deep(.el-select){width:210px}.excel-compress-progress{display:flex!important;flex-direction:column;align-items:stretch!important;gap:4px!important;width:180px}.excel-compress-progress span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.excel-compress-progress :deep(.el-progress){width:100%}.item-table{border-left:0;border-right:0}.item-table :deep(.el-input-number){width:100%;min-width:0}.item-table :deep(.bundle-addon-row){background:#fffaf0}.item-table :deep(.bundle-addon-row td:nth-child(2) .product-picker){padding-left:18px;border-left:3px solid #e6a23c}.product-picker{display:flex;align-items:center;gap:8px}.product-picker .el-select{flex:1;min-width:0}.product-picker .el-button{flex:none}.unit-price-cell{display:flex;flex-direction:column;gap:3px}.unit-price-cell small{line-height:1.25}.fixed-price-note{color:#16803c}.pending-price-note{color:#b45309}.pack-fee-cell{display:flex;flex-direction:column;gap:3px}.pack-fee-cell small{line-height:1.35;color:#6b7280}.packaging-cost-note{color:#b45309}.packaging-shortage{color:#dc2626!important;font-weight:600}.packaging-covered{color:#16803c!important}.bundle-summaries{display:flex;gap:10px;flex-wrap:wrap;padding:10px 12px 0}.bundle-summaries>div{display:flex;gap:14px;align-items:center;padding:8px 12px;border:1px solid #f1d39c;border-radius:4px;background:#fffaf0;color:#6b7280;font-size:13px}.bundle-summaries b{color:#92400e}.add-line{margin:12px}.document-total{display:flex;justify-content:flex-end;gap:28px;padding:12px 16px;border-top:1px solid #d9dee5;background:#f8fafc;color:#475569}.document-total b{color:#111827}.loss,.document-total .loss,.document-total .loss b{color:#dc2626;font-weight:700}.sheet-foot{padding:12px 14px 0;border-top:1px solid #d9dee5}.import-summary{display:flex;align-items:center;gap:10px;margin-bottom:14px;flex-wrap:wrap}.import-summary span:last-child{color:#7c8796}.import-error{color:#c2413a}@media(max-width:1200px){.sheet-head{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.sheet-head{grid-template-columns:1fr}.item-toolbar{align-items:stretch;flex-direction:column;gap:10px}.item-toolbar>div:first-child{align-items:flex-start;flex-direction:column;gap:2px}.item-toolbar-actions{flex-wrap:wrap}.product-filter-bar{align-items:stretch;flex-direction:column;padding-right:14px}.product-filter-bar :deep(.el-form-item),.product-filter-bar :deep(.el-select){width:100%}.excel-compress-progress{width:100%}.product-picker{align-items:stretch;flex-direction:column}.bundle-summaries>div{align-items:flex-start;flex-direction:column;gap:4px}.document-total{justify-content:flex-start;flex-wrap:wrap;gap:12px 20px}}</style>
<style scoped>.included-addons{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:5px}.included-addons .el-button{margin:0;padding:0;height:auto;font-size:12px}.included-addon-summary{color:#6b7280;font-size:12px;line-height:1.4}.included-gift-cost{display:block;color:#6b7280;font-size:11px;line-height:1.4}</style>
