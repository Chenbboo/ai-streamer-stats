<template>
  <div class="app-container">
    <el-tabs v-model="query.status" @tab-change="load"><el-tab-pane label="待一审" name="PENDING_FIRST"/><el-tab-pane label="待复核" name="PENDING_SECOND"/></el-tabs>
    <el-table :data="rows" v-loading="loading" border><el-table-column prop="docNo" label="单号" width="190"/><el-table-column label="类型" width="150"><template #default="{row}">{{typeLabel(row.docType)}}</template></el-table-column><el-table-column prop="bizDate" label="业务日期" width="110"/><el-table-column label="业务对象" min-width="150"><template #default="{row}">{{row.supplierNameSnapshot || row.salesChannel || '—'}}</template></el-table-column><el-table-column prop="totalQty" label="数量" width="90"/><el-table-column prop="totalAmount" label="金额" width="120" align="right"/><el-table-column prop="totalProfit" label="毛利" width="120" align="right"><template #default="{row}"><el-button link class="profit-link" :class="{loss:Number(row.totalProfit)<0}" title="查看毛利计算明细" @click="showProfit(row)">{{money(row.totalProfit)}}</el-button></template></el-table-column><el-table-column label="风险" width="100"><template #default="{row}"><el-tag v-if="row.riskStatus==='LOSS'" type="danger">亏损</el-tag><el-tag v-else-if="row.riskStatus==='REVIEW'" type="warning">需复核</el-tag><span v-else>—</span></template></el-table-column><el-table-column prop="creatorName" label="制单人" width="100"/><el-table-column label="操作" width="190"><template #default="{row}"><el-button link type="primary" @click="show(row)">查看</el-button><el-button link type="success" v-hasPermi="['jewelry:approval:approve']" @click="act(row,true)">通过</el-button><el-button link type="danger" v-hasPermi="['jewelry:approval:reject']" @click="act(row,false)">驳回</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-drawer v-model="drawer" title="单据明细" size="75%">
      <el-descriptions v-if="detail" :column="4" border>
        <el-descriptions-item label="单号">{{detail.docNo}}</el-descriptions-item>
        <el-descriptions-item label="类型">{{typeLabel(detail.docType)}}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{detail.creatorName}}</el-descriptions-item>
        <el-descriptions-item label="数量">{{detail.totalQty}}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥ {{money(detail.totalAmount)}}</el-descriptions-item>
        <el-descriptions-item label="总成本">¥ {{money(detail.totalCost)}}</el-descriptions-item>
        <el-descriptions-item label="总毛利"><span :class="{loss:Number(detail.totalProfit)<0}">¥ {{money(detail.totalProfit)}}</span></el-descriptions-item>
        <el-descriptions-item label="一审人">{{detail.firstReviewerName || '—'}}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detail?.riskStatus==='LOSS'" title="该销售单预计亏损，请核对成交价、商品成本及各项费率后再审批。" type="error" :closable="false" show-icon class="mt20"/>
      <el-table v-if="detail" :data="detail.items" border class="mt20">
        <el-table-column prop="skuSnapshot" label="SKU"/>
        <el-table-column prop="productNameSnapshot" label="商品"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="systemQty" label="系统库存"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="countedQty" label="实盘库存"/>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="adjustmentQty" label="差异"/>
        <el-table-column v-if="detail.docType!=='STOCK_ADJUST'" prop="qty" label="数量"/>
        <el-table-column prop="goodQty" label="良品"/>
        <el-table-column prop="defectQty" label="次品"/>
        <el-table-column prop="unitPrice" label="单价"/>
        <el-table-column prop="unitCost" label="成本"/>
        <el-table-column prop="amount" label="金额"/>
        <el-table-column prop="profitAmount" label="毛利"><template #default="{row}"><span :class="{loss:Number(row.profitAmount)<0}">{{money(row.profitAmount)}}</span></template></el-table-column>
        <el-table-column v-if="detail.docType==='STOCK_ADJUST'" prop="lineReason" label="调整原因" min-width="160"/>
      </el-table>
    </el-drawer>

    <el-dialog v-model="profitDialog" title="毛利计算明细" width="680px" append-to-body>
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
            <div class="formula-label">代入本单数据</div>
            <div class="formula-line">{{ profitBreakdown.substitution }}</div>
          </div>

          <el-descriptions v-if="profitBreakdown.supported" :column="2" border class="breakdown-list">
            <el-descriptions-item label="成交/退款金额">
              ¥ {{ money(profitBreakdown.revenue) }}
            </el-descriptions-item>
            <el-descriptions-item label="商品采购成本">
              ¥ {{ money(profitBreakdown.productCost) }}
            </el-descriptions-item>
            <el-descriptions-item label="平台扣点">
              ¥ {{ money(profitBreakdown.platformFee) }}
              <small>（{{ rateText(profitDetail.platformRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item label="达人佣金">
              ¥ {{ money(profitBreakdown.commissionFee) }}
              <small>（{{ rateText(profitDetail.commissionRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item label="税费">
              ¥ {{ money(profitBreakdown.taxFee) }}
              <small>（{{ rateText(profitDetail.taxRate) }}）</small>
            </el-descriptions-item>
            <el-descriptions-item label="履约费用">
              ¥ {{ money(profitBreakdown.fulfillmentFee) }}
              <small>（{{ profitDetail.docType==='CUSTOMER_RETURN' ? '物流×2、鉴定' : '包装、物流、鉴定' }}）</small>
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="profitBreakdown.supported" class="formula-result">
            <span>系统计算结果</span>
            <b :class="{loss:Number(profitDetail.totalProfit)<0}">
              ¥ {{ money(profitDetail.totalProfit) }}
            </b>
          </div>
          <el-alert
            v-else
            title="该单据类型不参与销售毛利计算，当前毛利为系统记账结果。"
            type="warning"
            :closable="false"
            show-icon
          />
        </template>
      </div>
      <template #footer>
        <el-button @click="profitDialog=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryApproval">
import {listJewelryDocuments,getJewelryDocument,approveJewelryDocument,rejectJewelryDocument} from '@/api/jewelry/erp'
const route=useRoute()
const {proxy}=getCurrentInstance(),rows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),detail=ref(null)
const profitDialog=ref(false),profitLoading=ref(false),profitDetail=ref(null)
const approvalStatuses=['PENDING_FIRST','PENDING_SECOND']
const query=reactive({pageNum:1,pageSize:10,status:approvalStatuses.includes(route.query.status)?route.query.status:'PENDING_FIRST'})
const typeLabels={PURCHASE_IN:'采购入库',SALES_OUT:'销售出库',SUPPLIER_RETURN:'供应商退货',CUSTOMER_RETURN:'客户退货',RETURN_INSPECT:'退货质检',STOCK_ADJUST:'库存调整',REVERSAL:'红冲单'}
const typeLabel=value=>typeLabels[value]||value
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const rateText=value=>`${(Number(value||0)*100).toFixed(2)}%`
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
  const fulfillmentFee=items.reduce((sum,item)=>{
    const fees=document.docType==='CUSTOMER_RETURN'
      ? Number(item.shipFee||0)*2+Number(item.certFee||0)
      : Number(item.packFee||0)+Number(item.shipFee||0)+Number(item.certFee||0)
    return sum+fees*effectiveQty(document.docType,item)
  },0)
  const platformFee=document.docType==='CUSTOMER_RETURN'?0:revenue*Number(document.platformRate||0)
  const commissionFee=document.docType==='CUSTOMER_RETURN'?0:revenue*Number(document.commissionRate||0)
  const taxFee=document.docType==='CUSTOMER_RETURN'?0:revenue*Number(document.taxRate||0)
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
  ? '客户退货毛利影响 = -退款金额 + 退回商品成本 - 双倍物流费 - 鉴定费'
  : profitDetail.value?.docType==='SALES_OUT'
    ? '销售毛利 = 成交金额 - 商品成本 - 履约费用 - 平台扣点 - 达人佣金 - 税费'
    : '当前单据毛利计算说明')
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}async function show(row){detail.value=(await getJewelryDocument(row.documentId)).data;drawer.value=true}
watch(()=>route.query.status,status=>{
  if(approvalStatuses.includes(status)&&status!==query.status){query.status=status;query.pageNum=1;load()}
})
async function showProfit(row){profitDialog.value=true;profitLoading.value=true;profitDetail.value=null;try{profitDetail.value=(await getJewelryDocument(row.documentId)).data}finally{profitLoading.value=false}}
async function act(row,pass){let comment='';if(!pass){const r=await proxy.$prompt('请输入驳回原因','驳回单据',{inputValidator:v=>!!v||'原因不能为空'});comment=r.value}else{const warning=row.riskStatus==='LOSS'?`该单据预计亏损 ¥${money(Math.abs(Number(row.totalProfit||0)))}，确认仍要通过 ${row.docNo}？`:`确认通过 ${row.docNo}？`;await proxy.$modal.confirm(warning,row.riskStatus==='LOSS'?'亏损风险确认':'审批确认',{type:row.riskStatus==='LOSS'?'error':'warning'})}if(pass)await approveJewelryDocument(row.documentId,comment);else await rejectJewelryDocument(row.documentId,comment);proxy.$modal.msgSuccess('操作成功');load()}load()
</script>
<style scoped>
.loss{color:#dc2626!important;font-weight:700}.profit-link{color:#334155;font-weight:700}.profit-link:hover{text-decoration:underline}.profit-detail{min-height:120px}.profit-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.profit-heading div{display:flex;flex-direction:column;gap:4px}.profit-heading span{color:#1f2937;font-size:16px;font-weight:700}.profit-heading small{color:#64748b}.profit-heading>b{color:#16825d;font-size:26px}.formula-substitution{margin:16px 0;padding:14px 16px;border-left:3px solid #409eff;background:#f6f9fc}.formula-label{margin-bottom:6px;color:#64748b;font-size:12px}.formula-line{color:#1f2937;font-family:Consolas,"Courier New",monospace;font-size:14px;line-height:1.6;overflow-wrap:anywhere}.breakdown-list{margin-top:16px}.breakdown-list small{color:#94a3b8}.formula-result{display:flex;align-items:center;justify-content:space-between;margin-top:16px;padding-top:14px;border-top:1px solid #e5e7eb;color:#64748b}.formula-result b{color:#16825d;font-size:22px}@media(max-width:760px){.profit-heading>b{font-size:22px}.breakdown-list :deep(.el-descriptions__body) .el-descriptions__table{display:block}.formula-line{font-size:12px}}
</style>
