<template>
  <div class="app-container">
    <el-tabs v-model="query.status" @tab-change="load"><el-tab-pane :label="$tr(&quot;待审核&quot;)" name="PENDING"/></el-tabs>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="docNo" :label="$tr(&quot;单号&quot;)" width="190"/>
      <el-table-column :label="$tr(&quot;类型&quot;)" width="150"><template #default="{row}">{{typeLabel(row.docType)}}</template></el-table-column>
      <el-table-column prop="bizDate" :label="$tr(&quot;业务日期&quot;)" width="110"/>
      <el-table-column :label="$tr(&quot;业务对象&quot;)" min-width="150"><template #default="{row}">{{isTransfer(row)?`${row.sourceWarehouse || '—'} → ${row.targetWarehouse || '—'}`:row.docType==='SALES_OUT'?(row.influencerName || row.salesChannel || '—'):row.supplierNameSnapshot || row.itemSupplierNames || (row.docType==='ASSEMBLY'?$tr("手工组装成品"):row.docType==='COST_ADJUST'?$tr("库存成本调整"):'—')}}</template></el-table-column>
      <el-table-column prop="totalQty" :label="$tr(&quot;数量&quot;)" width="90"/>
      <el-table-column :label="$tr(&quot;金额/成本&quot;)" width="120" align="right"><template #default="{row}">{{documentMoney(row.docType==='ASSEMBLY'?row.totalCost:row.totalAmount,row)}}</template></el-table-column>
      <el-table-column :label="$tr(&quot;毛利&quot;)" width="120" align="right"><template #default="{row}"><span v-if="isTransfer(row)||['ASSEMBLY','COST_ADJUST'].includes(row.docType)">—</span><el-button v-else link class="profit-link" :class="{loss:Number(row.totalProfit)<0}" :title="$tr(&quot;查看毛利计算明细&quot;)" @click="showProfit(row)">{{money(row.totalProfit)}}</el-button></template></el-table-column>
      <el-table-column :label="$tr(&quot;风险&quot;)" width="100"><template #default="{row}"><el-tag v-if="row.riskStatus==='LOSS'" type="danger">{{ $tr("亏损") }}</el-tag><el-tag v-else-if="row.riskStatus==='REVIEW'" type="warning">{{ $tr("需复核") }}</el-tag><span v-else>—</span></template></el-table-column>
      <el-table-column prop="creatorName" :label="$tr(&quot;制单人&quot;)" width="100"/>
      <el-table-column :label="$tr(&quot;阶段&quot;)" width="130"><template #default="{row}"><el-tag v-if="isDualApproval(row)" type="warning">{{row.status==='PENDING_SECOND'?$tr("管理员复核"):$tr("审核员审核")}}</el-tag><span v-else>{{ $tr("审核员审核") }}</span></template></el-table-column>
      <el-table-column :label="$tr(&quot;操作&quot;)" width="190"><template #default="{row}"><el-button link type="primary" @click="show(row)">{{ $tr("查看") }}</el-button><template v-if="canAct(row)"><el-button link type="success" v-hasPermi="['jewelry:approval:approve']" @click="act(row,true)">{{ $tr("通过") }}</el-button><el-button link type="danger" v-hasPermi="['jewelry:approval:reject']" @click="act(row,false)">{{ $tr("驳回") }}</el-button></template></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-drawer v-model="drawer" :title="$tr(&quot;单据明细&quot;)" size="75%">
      <el-descriptions v-if="detail" :column="4" border>
        <el-descriptions-item :label="$tr(&quot;单号&quot;)">{{detail.docNo}}</el-descriptions-item>
        <el-descriptions-item :label="$tr(&quot;类型&quot;)">{{typeLabel(detail.docType)}}</el-descriptions-item>
        <el-descriptions-item :label="detail.docType==='SALES_OUT'?$tr(&quot;达人&quot;):$tr(&quot;供应商&quot;)">{{detail.docType==='SALES_OUT'?(detail.influencerName || $tr("未记录")):supplierNames(detail)}}</el-descriptions-item>
        <el-descriptions-item v-if="detail.docType==='SALES_OUT'" :label="$tr(&quot;供应商名称&quot;)">{{supplierNames(detail)}}</el-descriptions-item>
        <el-descriptions-item v-if="detail.docType==='PURCHASE_IN'" :label="$tr(&quot;约定退货日期&quot;)">{{detail.supplierReturnDate || $tr("按统一退货期限")}}</el-descriptions-item>
        <el-descriptions-item v-if="isTransfer(detail)" :label="$tr(&quot;出库仓库&quot;)">{{detail.sourceWarehouse}}</el-descriptions-item>
        <el-descriptions-item v-if="isTransfer(detail)" :label="$tr(&quot;入库仓库&quot;)">{{detail.targetWarehouse}}</el-descriptions-item>
        <el-descriptions-item v-if="isTransfer(detail)" :label="$tr(&quot;调货时间&quot;)">{{detail.bizDate}}</el-descriptions-item>
        <el-descriptions-item :label="$tr(&quot;制单人&quot;)">{{detail.creatorName}}</el-descriptions-item>
        <el-descriptions-item :label="$tr(&quot;数量&quot;)">{{detail.totalQty}}</el-descriptions-item>
        <el-descriptions-item v-if="!isTransfer(detail)" :label="detail.docType==='ASSEMBLY'?$tr(&quot;组装总成本&quot;):detail.docType==='COST_ADJUST'?$tr(&quot;库存金额变化&quot;):$tr(&quot;总金额&quot;)">¥ {{documentMoney(detail.docType==='ASSEMBLY'?detail.totalCost:detail.totalAmount,detail)}}</el-descriptions-item>
        <el-descriptions-item v-if="detail.docType==='COST_ADJUST'" :label="$tr(&quot;调整后库存金额&quot;)">¥ {{money(detail.totalCost)}}</el-descriptions-item>
        <el-descriptions-item v-else-if="detail.docType!=='ASSEMBLY' && !isTransfer(detail)" :label="$tr(&quot;总成本&quot;)">¥ {{costMoney(detail.totalCost,detail)}}</el-descriptions-item>
        <el-descriptions-item v-if="!isTransfer(detail)" :label="$tr(&quot;总毛利&quot;)"><span v-if="['ASSEMBLY','COST_ADJUST'].includes(detail.docType)">—</span><span v-else :class="{loss:Number(detail.totalProfit)<0}">¥ {{money(detail.totalProfit)}}</span></el-descriptions-item>
        <el-descriptions-item :label="$tr(&quot;审批人&quot;)"><span v-if="isDualApproval(detail)">{{ $tr("审核员：{0}；管理员：{1}", [detail.firstReviewerName||$tr("待审核"), detail.secondReviewerName||$tr("待复核")]) }}</span><span v-else>{{detail.secondReviewerName || detail.firstReviewerName || '—'}}</span></el-descriptions-item>
        <el-descriptions-item :label="$tr(&quot;备注&quot;)" :span="4"><el-input :model-value="detail.remark || ''" type="textarea" :autosize="{minRows:2,maxRows:6}" readonly :placeholder="$tr(&quot;暂无备注&quot;)" /></el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detail?.riskStatus==='LOSS'" :title="$tr(&quot;该销售单预计亏损，请核对成交价、商品成本及各项费率后再审批。&quot;)" type="error" :closable="false" show-icon class="mt20"/>
      <el-alert v-if="isAdminStockReview(detail)" :title="$tr(&quot;管理员可调整盘盈明细的核定成本；点击“通过并入账”时，修改后的成本会与库存调整一并保存。&quot;)" type="warning" :closable="false" show-icon class="mt20"/>
      <div v-if="detail?.docType==='ASSEMBLY'" class="assembly-review mt20">
        <el-image
          v-if="firstImage(assemblyOutput(detail)?.imageUrls)"
          :src="imageSrc(firstImage(assemblyOutput(detail)?.imageUrls))"
          :preview-src-list="allImages(assemblyOutput(detail)?.imageUrls).map(imageSrc)"
          fit="cover"
          preview-teleported
        />
        <div v-else class="assembly-no-image">{{ $tr("暂无成品参考图") }}</div>
        <div class="assembly-summary">
          <div class="assembly-title">
            <div>
              <b>{{assemblyOutput(detail)?.productNameSnapshot || $tr("成品组装")}}</b>
              <span>{{assemblyOutput(detail)?.skuSnapshot || '—'}}</span>
            </div>
            <el-tag type="success">{{ $tr("手工组装") }}</el-tag>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$tr(&quot;成品数量&quot;)">{{assemblyOutput(detail)?.qty || 0}}</el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;散件种类&quot;)">{{detail.items?.filter(item=>item.itemRole==='COMPONENT').length || 0}}</el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;人工费&quot;)">¥ {{money(detail.laborFee)}}</el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;加工费&quot;)">¥ {{money(detail.processingFee)}}</el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;其他费用&quot;)">¥ {{money(detail.otherFee)}}</el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;组装总成本&quot;)">¥ {{money(detail.totalCost)}}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
      <el-table v-if="detail" :data="approvalItems" :row-class-name="approvalItemRowClass" border class="mt20 approval-item-table">
        <el-table-column v-if="showBundleRoles" :label="$tr(&quot;销售角色&quot;)" width="190">
          <template #default="{row}">
            <el-tag v-if="row.saleRole==='MAIN'" type="success" effect="plain">{{ $tr("组合{0}·主商品", [row.bundleGroupNo]) }}</el-tag>
            <el-tag v-else-if="row.saleRole==='ADDON'" type="warning" effect="plain">{{ $tr("组合{0}·搭售", [row.bundleGroupNo]) }}</el-tag>
            <el-tag v-else type="info" effect="plain">{{ $tr("独立销售") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="detail.docType==='ASSEMBLY'" :label="$tr(&quot;用途&quot;)" width="90">
          <template #default="{row}"><el-tag :type="row.itemRole==='OUTPUT'?'success':'info'">{{row.itemRole==='OUTPUT'?$tr("成品产出"):$tr("散件投入")}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="skuSnapshot" label="SKU"/>
        <el-table-column :label="$tr(&quot;商品&quot;)" min-width="230">
          <template #default="{row}">
            <div class="approval-product-cell" :class="{'approval-addon-product':row.saleRole==='ADDON'}">
              <span>{{row.productNameSnapshot}}</span>
              <el-button v-if="row.saleRole==='MAIN' && addonCount(row)" link type="primary" class="approval-addon-toggle" @click="toggleAddons(row)">
                {{isAddonExpanded(row)?$tr("收起搭售"):$tr("展开搭售（{0} 件）", [addonCount(row)])}}
                <el-icon><ArrowUp v-if="isAddonExpanded(row)"/><ArrowDown v-else/></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="detail.docType!=='SALES_OUT'" :label="$tr(&quot;供应商&quot;)" min-width="140" show-overflow-tooltip><template #default="{row}">{{itemSupplierNames(row,detail)}}</template></el-table-column>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="systemQty" :label="$tr(&quot;系统库存&quot;)"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="countedQty" :label="$tr(&quot;实盘库存&quot;)"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="adjustmentQty" :label="$tr(&quot;差异&quot;)"/>
        <el-table-column v-if="detail.docType!=='STOCK_ADJUST'" prop="qty" :label="detail.docType==='COST_ADJUST'?$tr(&quot;当前库存&quot;):$tr(&quot;数量&quot;)"/>
        <el-table-column v-if="detail.docType==='RETURN_INSPECT'" prop="goodQty" :label="$tr(&quot;良品&quot;)"/>
        <el-table-column v-if="detail.docType==='RETURN_INSPECT'" prop="defectQty" :label="$tr(&quot;次品&quot;)"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" :label="$tr(&quot;盘盈核定成本&quot;)" width="170">
          <template #default="{row}">
            <el-input-number v-if="canEditGainCost(row)" v-model="row.unitCost" :min="0.01" :precision="2" :step="1" controls-position="right" style="width:145px"/>
            <span v-else>{{money(row.unitCost)}}</span>
          </template>
        </el-table-column>
        <el-table-column v-else-if="!isTransfer(detail)" prop="unitCost" :label="detail.docType==='COST_ADJUST'?$tr(&quot;当前平均成本&quot;):$tr(&quot;成本&quot;)"/>
        <el-table-column v-if="!isTransfer(detail)" :label="detail.docType==='COST_ADJUST'?$tr(&quot;调整后平均成本&quot;):$tr(&quot;单价&quot;)"><template #default="{row}">{{unitPriceMoney(row.unitPrice,detail)}}</template></el-table-column>
        <el-table-column v-if="!isTransfer(detail)" :label="detail.docType==='ASSEMBLY'?$tr(&quot;成本金额&quot;):detail.docType==='COST_ADJUST'?$tr(&quot;库存金额变化&quot;):$tr(&quot;金额&quot;)"><template #default="{row}">{{documentMoney(detail.docType==='ASSEMBLY'?row.costAmount:row.amount,detail)}}</template></el-table-column>
        <el-table-column v-if="!isTransfer(detail)" :label="$tr(&quot;毛利&quot;)"><template #default="{row}"><span v-if="['ASSEMBLY','COST_ADJUST'].includes(detail.docType)">—</span><span v-else :class="{loss:Number(row.profitAmount)<0}">{{money(row.profitAmount)}}</span></template></el-table-column>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="lineReason" :label="$tr(&quot;调整原因&quot;)" min-width="160"/>
      </el-table>
      <template #footer v-if="detail&&canAct(detail)">
        <el-button @click="drawer=false">{{ $tr("关闭") }}</el-button>
        <el-button type="danger" v-hasPermi="['jewelry:approval:reject']" @click="act(detail,false,true)">{{ $tr("驳回") }}</el-button>
        <el-button type="success" v-hasPermi="['jewelry:approval:approve']" @click="act(detail,true,true)">{{isAdminStockReview(detail)?$tr("通过并入账"):$tr("通过")}}</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="profitDialog" :title="$tr(&quot;毛利计算明细&quot;)" width="680px" append-to-body>
      <div v-loading="profitLoading" class="profit-detail">
        <template v-if="profitDetail">
          <div class="profit-heading">
            <div>
              <span>{{ profitDetail.docNo }}</span>
              <small>{{ typeLabel(profitDetail.docType) }}</small>
            </div>
            <b :class="{loss:Number(profitDetail.totalProfit)<0}">
              ¥ {{ money(profitDetail.totalProfit) }}
            </b>
          </div>

          <el-alert
            :title="profitFormulaTitle"
            type="info"
            :closable="false"
            show-icon
          />

          <div v-if="profitBreakdown.supported" class="formula-substitution">
            <div class="formula-label">{{ $tr("代入本单数据") }}</div>
            <div class="formula-line">{{ profitBreakdown.substitution }}</div>
          </div>

          <el-descriptions v-if="profitBreakdown.supported" :column="2" border class="breakdown-list">
            <el-descriptions-item :label="$tr(&quot;成交/退款金额&quot;)">
              ¥ {{ money(profitBreakdown.revenue) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;商品采购成本&quot;)">
              ¥ {{ money(profitBreakdown.productCost) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;平台扣点&quot;)">
              ¥ {{ money(profitBreakdown.platformFee) }}
              <small>（{{ hasItemRates?$tr('按商品费率'):rateText(profitDetail.platformRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;达人佣金&quot;)">
              ¥ {{ money(profitBreakdown.commissionFee) }}
              <small>（{{ hasItemRates?$tr('按商品费率'):rateText(profitDetail.commissionRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;税费&quot;)">
              ¥ {{ money(profitBreakdown.taxFee) }}
              <small>（{{ hasItemRates?$tr('按商品费率'):rateText(profitDetail.taxRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item :label="$tr(&quot;履约费用&quot;)">
              ¥ {{ money(profitBreakdown.fulfillmentFee) }}
              <small>（{{ profitDetail.docType==='CUSTOMER_RETURN' ? $tr("物流×2、鉴定") : $tr("包装、物流、鉴定、其他1～3") }}）</small>
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="profitBreakdown.supported" class="formula-result">
            <span>{{ $tr("系统计算结果") }}</span>
            <b :class="{loss:Number(profitDetail.totalProfit)<0}">
              ¥ {{ money(profitDetail.totalProfit) }}
            </b>
          </div>
          <el-alert
            v-else
            :title="$tr(&quot;该单据类型不参与销售毛利计算，当前毛利为系统记账结果。&quot;)"
            type="warning"
            :closable="false"
            show-icon
          />
        </template>
      </div>
      <template #footer>
        <el-button @click="profitDialog=false">{{ $tr("关闭") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryApproval">
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { translateText } from '@/locales/translate'
import {listJewelryDocuments,getJewelryDocument,approveJewelryDocument,rejectJewelryDocument} from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
const route=useRoute()
const userStore=useUserStore()
const {proxy}=getCurrentInstance(),rows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),detail=ref(null)
const expandedGroups=ref([])
const profitDialog=ref(false),profitLoading=ref(false),profitDetail=ref(null)
const approvalStatuses=['PENDING','PENDING_FIRST','PENDING_SECOND']
const query=reactive({pageNum:1,pageSize:10,status:'PENDING'})
const isTransfer=row=>row?.docType==='TRANSFER_OUT'||(row?.docType==='REVERSAL'&&row?.sourceDocType==='TRANSFER_OUT')
const typeLabels={TRANSFER_OUT:translateText("仓库调货"),PURCHASE_IN:translateText("采购入库"),SAMPLE_IN:translateText("样品入库"),SALES_OUT:translateText("销售出库"),SUPPLIER_RETURN:translateText("供应商退货"),CUSTOMER_RETURN:translateText("客户退货"),RETURN_INSPECT:translateText("退货质检"),STOCK_ADJUST:translateText("库存调整"),COST_ADJUST:translateText("库存成本调价"),ASSEMBLY:translateText("手工组装"),REVERSAL:translateText("红冲单")}
const typeLabel=value=>typeLabels[value]||value
const supplierNames=document=>{
  const recorded=[document?.supplierNameSnapshot,...String(document?.itemSupplierNames||'').split('、'),
    ...(document?.items||[]).map(item=>item.supplierNameSnapshot)]
    .map(value=>String(value||'').trim()).filter(Boolean)
  const names=recorded.length?recorded:(document?.items||[]).flatMap(item=>String(item.productSupplierNames||'').split('、'))
    .map(value=>String(value||'').trim()).filter(Boolean)
  return [...new Set(names)].join('、')||translateText("未记录")
}
const itemSupplierNames=(item,document)=>item?.supplierNameSnapshot||document?.supplierNameSnapshot||item?.productSupplierNames||translateText('未记录')
const isDualApproval=row=>['STOCK_ADJUST','COST_ADJUST'].includes(row?.docType)||(row?.docType==='REVERSAL'&&['STOCK_ADJUST','COST_ADJUST'].includes(row?.sourceDocType))
const isCostAdjustment=row=>row?.docType==='COST_ADJUST'||(row?.docType==='REVERSAL'&&row?.sourceDocType==='COST_ADJUST')
const isAdministrator=()=>((userStore.roles||[]).some(role=>['admin','jewelry_admin'].includes(role)))
const isAdminStockReview=row=>row?.docType==='STOCK_ADJUST'&&row?.status==='PENDING_SECOND'&&isAdministrator()
const canEditGainCost=row=>isAdminStockReview(detail.value)&&Number(row?.adjustmentQty||0)>0
const canAct=row=>{
  if(!isDualApproval(row))return true
  const roles=userStore.roles||[]
  if(row.status==='PENDING_FIRST')return roles.includes('jewelry_reviewer')
  return row.status==='PENDING_SECOND'&&roles.some(role=>['admin','jewelry_admin'].includes(role))
}
const imageSrc=value=>value?.startsWith('http')?value:import.meta.env.VITE_APP_BASE_API+value
const allImages=value=>String(value||'').split(',').map(item=>item.trim()).filter(Boolean)
const firstImage=value=>allImages(value)[0]||''
const assemblyOutput=document=>document?.items?.find(item=>item.itemRole==='OUTPUT')
const showBundleRoles=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(detail.value?.docType)
  ||(detail.value?.items||[]).some(item=>['MAIN','ADDON'].includes(item.saleRole)))
const bundleAddons=computed(()=>{
  const groups=new Map()
  for(const item of detail.value?.items||[]){
    if(item.saleRole!=='ADDON'||item.bundleGroupNo==null)continue
    const key=String(item.bundleGroupNo)
    if(!groups.has(key))groups.set(key,[])
    groups.get(key).push(item)
  }
  return groups
})
const addonCount=row=>bundleAddons.value.get(String(row.bundleGroupNo))?.length||0
const isAddonExpanded=row=>expandedGroups.value.includes(String(row.bundleGroupNo))
const toggleAddons=row=>{
  const key=String(row.bundleGroupNo)
  expandedGroups.value=isAddonExpanded(row)?expandedGroups.value.filter(group=>group!==key):[...expandedGroups.value,key]
}
const approvalItems=computed(()=>{
  const items=detail.value?.items||[]
  const mainGroups=new Set(items.filter(item=>item.saleRole==='MAIN'&&item.bundleGroupNo!=null).map(item=>String(item.bundleGroupNo)))
  const roots=[]
  for(const item of items){
    if(item.saleRole==='ADDON'&&item.bundleGroupNo!=null&&mainGroups.has(String(item.bundleGroupNo)))continue
    roots.push(item)
    if(item.saleRole==='MAIN'&&isAddonExpanded(item))roots.push(...(bundleAddons.value.get(String(item.bundleGroupNo))||[]))
  }
  return roots
})
const approvalItemRowClass=({row})=>row.saleRole==='ADDON'?'bundle-addon-row':''
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const fourDecimalMoney=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:4,maximumFractionDigits:4})
const isFourDecimalAmount=document=>['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.docType)
  ||(document?.docType==='REVERSAL'&&['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.sourceDocType))
const isPurchaseAmount=document=>document?.docType==='PURCHASE_IN'||(document?.docType==='REVERSAL'&&document?.sourceDocType==='PURCHASE_IN')
const isFourDecimalUnitPrice=document=>['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.docType)
  ||(document?.docType==='REVERSAL'&&['PURCHASE_IN','SUPPLIER_RETURN'].includes(document?.sourceDocType))
const documentMoney=(value,document)=>isFourDecimalAmount(document)?fourDecimalMoney(value):money(value)
const costMoney=(value,document)=>isPurchaseAmount(document)?fourDecimalMoney(value):money(value)
const unitPriceMoney=(value,document)=>isFourDecimalUnitPrice(document)?fourDecimalMoney(value):money(value)
const rateText=value=>`${(Number(value||0)*100).toFixed(2)}%`
const hasItemRates=computed(()=>profitDetail.value?.docType==='SALES_OUT'&&profitDetail.value.items?.some(item=>item.platformRateSnapshot!=null||item.commissionRateSnapshot!=null||item.taxRateSnapshot!=null))
const effectiveQty=(type,item)=>type==='RETURN_INSPECT'
  ? Number(item.goodQty||0)+Number(item.defectQty||0)
  : type==='STOCK_ADJUST'
    ? Math.abs(Number(item.adjustmentQty||0))
    : Number(item.qty||0)
const profitBreakdown=computed(()=>{
  const document=profitDetail.value
  if(!document||!['SALES_OUT','CUSTOMER_RETURN'].includes(document.docType))return{supported:false}
  const items=document.items||[]
  const revenue=items.reduce((sum,item)=>sum+Number(item.unitPrice||0)*effectiveQty(document.docType,item),0)
  const productCost=items.reduce((sum,item)=>sum+Number(item.unitCost||0)*effectiveQty(document.docType,item),0)
  const fulfillmentFee=document.docType==='CUSTOMER_RETURN'
    ? items.reduce((sum,item)=>sum+(Number(item.shipFee||0)*2+Number(item.certFee||0))*effectiveQty(document.docType,item),0)
    : items.reduce((sum,item)=>sum+Number(item.costAmount||0),0)-productCost
  const salesFee=(snapshot,header)=>items.reduce((sum,item)=>sum+Number(item.unitPrice||0)*effectiveQty(document.docType,item)*Number(item[snapshot]??document[header]??0),0)
  const platformFee=document.docType==='CUSTOMER_RETURN'?0:salesFee('platformRateSnapshot','platformRate')
  const commissionFee=document.docType==='CUSTOMER_RETURN'?0:salesFee('commissionRateSnapshot','commissionRate')
  const taxFee=document.docType==='CUSTOMER_RETURN'?0:salesFee('taxRateSnapshot','taxRate')
  const values=[revenue,productCost,fulfillmentFee,platformFee,commissionFee,taxFee].map(money)
  const base=`${values[0]} - ${values[1]} - ${values[2]} - ${values[3]} - ${values[4]} - ${values[5]}`
  return{
    supported:true,revenue,productCost,fulfillmentFee,platformFee,commissionFee,taxFee,
    substitution:document.docType==='CUSTOMER_RETURN'
      ? `-${values[0]} + ${values[1]} - ${values[2]} = ${money(document.totalProfit)}`
      : `${base} = ${money(document.totalProfit)}`
  }
})
const profitFormulaTitle=computed(()=>profitDetail.value?.docType==='CUSTOMER_RETURN'
  ? translateText("客户退货毛利影响 = -退款金额 + 退回商品成本 - 双倍物流费 - 鉴定费")
  : profitDetail.value?.docType==='SALES_OUT'
    ? translateText("销售毛利 = 成交金额 - 商品成本 - 履约费用 - 平台扣点 - 达人佣金 - 税费")
    : translateText("当前单据毛利计算说明"))
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
async function show(row){
  expandedGroups.value=[]
  detail.value=(await getJewelryDocument(row.documentId)).data
  drawer.value=true
}
watch(()=>route.query.status,status=>{
  if(approvalStatuses.includes(status)&&query.status!=='PENDING'){query.status='PENDING';query.pageNum=1;load()}
})
async function showProfit(row){profitDialog.value=true;profitLoading.value=true;profitDetail.value=null;try{profitDetail.value=(await getJewelryDocument(row.documentId)).data}finally{profitLoading.value=false}}
async function act(row,pass,fromDetail=false){
  if(pass&&isAdminStockReview(row)&&!fromDetail){await show(row);return}
  let comment='',expectedTotalCost,stockAdjustmentCosts
  if(!pass){
    const result=await proxy.$prompt(translateText("请输入驳回原因"),translateText("驳回单据"),{inputValidator:value=>!!value||translateText("原因不能为空")})
    comment=result.value
  }else{
    let warning=translateText("确认通过 {0}？", [row.docNo])
    if(row.docType==='ASSEMBLY'){
      const current=(await getJewelryDocument(row.documentId)).data
      expectedTotalCost=current.totalCost
      warning=translateText("当前组装总成本为 ¥{0}，确认审核通过并入账？", [money(expectedTotalCost)])
    }else if(isDualApproval(row)){
      warning=row.status==='PENDING_SECOND'
        ? translateText("确认管理员复核通过 {0} 并{1}？", [row.docNo, isCostAdjustment(row)?translateText("正式修改库存平均成本"):translateText("正式调整库存")])
        : translateText("确认审核通过 {0} 并转交管理员复核？", [row.docNo])
    }else if(row.riskStatus==='LOSS'){
      warning=translateText("该单据预计亏损 ¥{0}，确认仍要通过 {1}？", [money(Math.abs(Number(row.totalProfit||0))), row.docNo])
    }
    if(isAdminStockReview(row)){
      const current=detail.value?.documentId===row.documentId?detail.value:(await getJewelryDocument(row.documentId)).data
      const gains=(current.items||[]).filter(item=>Number(item.adjustmentQty||0)>0)
      if(gains.some(item=>!Number.isFinite(Number(item.unitCost))||Number(item.unitCost)<=0)){
        proxy.$modal.msgError(translateText("盘盈核定成本必须大于0"))
        return
      }
      stockAdjustmentCosts=gains.map(item=>({itemId:item.itemId,unitCost:item.unitCost}))
      warning=translateText("确认按当前盘盈核定成本通过 {0} 并正式调整库存？", [row.docNo])
    }
    await proxy.$modal.confirm(warning,row.riskStatus==='LOSS'?translateText("亏损风险确认"):translateText("审批确认"),{type:row.riskStatus==='LOSS'?'error':'warning'})
  }
  if(pass)await approveJewelryDocument(row.documentId,comment,expectedTotalCost,stockAdjustmentCosts)
  else await rejectJewelryDocument(row.documentId,comment)
  proxy.$modal.msgSuccess(pass&&isDualApproval(row)&&row.status==='PENDING_FIRST'?translateText("已转交管理员复核"):translateText("操作成功"))
  if(fromDetail)drawer.value=false
  load()
}
load()
</script>
<style scoped>
.approval-item-table :deep(.bundle-addon-row){background:#fffaf0}
.approval-product-cell{display:flex;align-items:center;justify-content:space-between;gap:8px}.approval-addon-product{padding-left:16px;border-left:3px solid #e6a23c}.approval-addon-toggle{flex:none}
.loss{color:#dc2626!important;font-weight:700}.profit-link{color:#334155;font-weight:700}.profit-link:hover{text-decoration:underline}.assembly-review{display:grid;grid-template-columns:220px 1fr;gap:18px;padding:16px;border:1px solid #dfe5ec;background:#f8fafc}.assembly-review>.el-image,.assembly-no-image{width:220px;aspect-ratio:1/1;border:1px solid #d9e0e8;background:#fff}.assembly-no-image{display:grid;place-items:center;color:#9aa5b1}.assembly-summary{min-width:0}.assembly-title{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.assembly-title div{display:flex;flex-direction:column;gap:4px}.assembly-title b{font-size:18px}.assembly-title span{color:#7a8796}.profit-detail{min-height:120px}.profit-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.profit-heading div{display:flex;flex-direction:column;gap:4px}.profit-heading span{color:#1f2937;font-size:16px;font-weight:700}.profit-heading small{color:#64748b}.profit-heading>b{color:#16825d;font-size:26px}.formula-substitution{margin:16px 0;padding:14px 16px;border-left:3px solid #409eff;background:#f6f9fc}.formula-label{margin-bottom:6px;color:#64748b;font-size:12px}.formula-line{color:#1f2937;font-family:Consolas,"Courier New",monospace;font-size:14px;line-height:1.6;overflow-wrap:anywhere}.breakdown-list{margin-top:16px}.breakdown-list small{color:#94a3b8}.formula-result{display:flex;align-items:center;justify-content:space-between;margin-top:16px;padding-top:14px;border-top:1px solid #e5e7eb;color:#64748b}.formula-result b{color:#16825d;font-size:22px}@media(max-width:760px){.assembly-review{grid-template-columns:1fr}.assembly-review>.el-image,.assembly-no-image{width:100%;max-width:300px}.profit-heading>b{font-size:22px}.breakdown-list :deep(.el-descriptions__body) .el-descriptions__table{display:block}.formula-line{font-size:12px}}
</style>
