<template>
  <div class="app-container">
    <div ref="stockToolbar" class="stock-toolbar">
      <el-form class="stock-filter-form" inline>
        <el-form-item class="filter-keyword"><el-input v-model="query.keyword" :placeholder="$tr(&quot;SKU或商品名称&quot;)" clearable/></el-form-item>
        <el-form-item class="filter-product-type"><el-select v-model="query.productType" :placeholder="$tr(&quot;全部商品类型&quot;)" clearable>
          <el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select></el-form-item>
        <el-form-item class="filter-supplier"><el-select v-model="query.supplierIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
          :placeholder="$tr(&quot;全部供应商&quot;)">
          <el-option v-for="item in supplierOptions" :key="item.supplierId" :label="item.supplierName" :value="item.supplierId"/>
        </el-select></el-form-item>
        <el-form-item><el-tooltip :content="$tr(&quot;按账面总库存筛选：可售、待检和次品库存合计大于0。&quot;)"><el-button :type="query.inStockOnly?'primary':''" :plain="!query.inStockOnly" @click="toggleInStockOnly">{{ $tr("只看有库存") }}</el-button></el-tooltip></el-form-item>
        <el-form-item><el-checkbox v-model="query.warningOnly">{{ $tr("只看预警") }}</el-checkbox></el-form-item>
        <el-form-item v-if="query.warningOnly" class="filter-warning-type"><el-select v-model="query.warningType">
          <el-option :label="$tr(&quot;全部预警&quot;)" value="all"/><el-option :label="$tr(&quot;库存不足&quot;)" value="quantity"/><el-option :label="$tr(&quot;库龄超期&quot;)" value="age"/>
          <el-option :label="$tr(&quot;退供不足7天&quot;)" value="supplierReturn"/>
        </el-select></el-form-item>
        <el-form-item class="filter-search"><el-button type="primary" icon="Search" @click="search">{{ $tr("查询") }}</el-button></el-form-item>
      </el-form>
      <div class="stock-settings">
        <span class="stock-settings-title">{{ $tr("预警设置") }}</span>
        <div class="warning-setting">
          <span>{{ $tr("库龄预警") }}</span>
          <el-input-number v-if="canConfigureWarning" v-model="warningDays" :min="1" :max="365" controls-position="right"/>
          <b v-else>{{ warningDays }}</b>
          <span>{{ $tr("天") }}</span>
          <el-button v-if="canConfigureWarning" type="primary" plain icon="Check"
            :loading="savingWarning" @click="saveWarningDays">{{ $tr("保存") }}</el-button>
        </div>
        <div class="warning-setting">
          <el-tooltip :content="$tr(&quot;仅成品商品参与退货时间预警。采购业务日期加统一期限；采购单单独设置的约定退货日期优先，历史采购单同样适用。&quot;)">
            <span>{{ $tr("供应商退货期限") }}</span>
          </el-tooltip>
          <el-input-number v-if="canConfigureWarning" v-model="supplierReturnDays" :min="1" :max="365" :precision="0" controls-position="right"/>
          <b v-else>{{supplierReturnDays}}</b><span>{{ $tr("天") }}</span>
          <el-button v-if="canConfigureWarning" type="primary" plain icon="Check"
            :loading="savingReturnDays" @click="saveReturnDays">{{ $tr("保存") }}</el-button>
        </div>
      </div>
    </div>
    <el-table ref="stockTable" :max-height="tableMaxHeight" :data="rows" v-loading="loading" border
      @expand-change="onSampleExpand">
      <el-table-column v-if="appliedProductType==='SAMPLE'" type="expand" width="48">
        <template #default="{row}">
          <div class="sample-expanded">
            <div v-if="row.sampleInboundLoading" class="sample-expanded-state">{{ $tr("正在加载入库记录…") }}</div>
            <el-button v-else-if="row.sampleInboundError" link type="primary" @click="loadSampleInboundDetails(row)">{{ $tr("加载失败，点击重试") }}</el-button>
            <div v-else-if="!row.sampleInboundDetails.length" class="sample-expanded-state">{{ $tr("暂无已生效的样品入库记录") }}</div>
            <el-table v-else :data="row.sampleInboundDetails" :show-header="false" border size="small" max-height="360" class="sample-inbound-table">
              <el-table-column width="140"/>
              <el-table-column min-width="180"/>
              <el-table-column width="110"/>
              <el-table-column width="100"/>
              <el-table-column :label="$tr(&quot;账面总库存&quot;)" width="115" align="right"><template #default="{row:item}">{{item.totalStockQty}}</template></el-table-column>
              <el-table-column :label="$tr(&quot;可用库存&quot;)" width="100" align="right"><template #default="{row:item}">{{item.availableQty}}</template></el-table-column>
              <el-table-column :label="$tr(&quot;入库时间&quot;)" width="115">
                <template #default="{row:item}">
                  {{formatInboundDate(item.inboundDate)}}
                  <small class="sample-detail-note">{{ $tr("货号 {0} · {1}", [item.goodsNo || '—', item.docNo]) }}</small>
                </template>
              </el-table-column>
              <el-table-column :label="$tr(&quot;库龄&quot;)" width="90" align="right"><template #default="{row:item}">{{ $tr("{0}天", [item.stockAgeDays]) }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;离供应商退货时间&quot;)" width="185">
                <template #default="{row:item}">
                  <span :class="{danger:Number(item.supplierReturnDays)<=0}">{{returnCountdown(item.supplierReturnDays)}}</span>
                  <small class="sample-detail-note">{{ $tr("截止 {0} · {1}", [formatInboundDate(item.supplierReturnDate), item.supplierName || $tr("供应商未记录")]) }}</small>
                </template>
              </el-table-column>
              <el-table-column :label="$tr(&quot;操作&quot;)" width="90"><template #default><el-button link type="primary" @click="showFlow(row)">{{ $tr("流水") }}</el-button></template></el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column><el-table-column prop="sku" label="SKU" width="140"/><el-table-column prop="productName" :label="$tr(&quot;商品名称&quot;)" min-width="180"/><el-table-column prop="supplierNames" :label="$tr(&quot;供应商&quot;)" min-width="160" show-overflow-tooltip><template #default="{row}">{{row.supplierNames || '—'}}</template></el-table-column><el-table-column :label="$tr(&quot;商品类型&quot;)" width="110"><template #default="{row}"><el-tag :type="jewelryProductType(row.productType)?.tagType || 'info'" effect="plain">{{jewelryProductType(row.productType)?.label || row.productType || '—'}}</el-tag></template></el-table-column><el-table-column prop="specification" :label="$tr(&quot;规格类型&quot;)" width="100"><template #default="{row}">{{$tr(row.specification) || '—'}}</template></el-table-column><el-table-column prop="totalStockQty" :label="$tr(&quot;账面总库存&quot;)" width="115" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="onHandQty" :label="$tr(&quot;可售库存&quot;)" width="100" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="reservedOutQty" :label="$tr(&quot;出库冻结&quot;)" width="100" align="right"/><el-table-column prop="availableQty" :label="$tr(&quot;可用库存&quot;)" width="100" align="right"><template #default="{row}"><span :class="{danger:row.quantityWarning}">{{row.availableQty}}</span></template></el-table-column><el-table-column prop="oldestInboundDate" :label="appliedProductType === 'SAMPLE' ? $tr(&quot;入库时间&quot;) : $tr(&quot;最早入库&quot;)" width="115">
      <template #default="{row}">
        <el-tooltip v-if="Number(row.stockOriginFirstPurchase) && !Number(row.stockOriginUnknown)"
          :content="$tr(&quot;退回商品存在多次采购，按首次采购入库日期计算库龄及退供期限，不代表已关联实际批次。&quot;)">
          <span>{{row.oldestInboundDate}}</span>
        </el-tooltip>
        <span v-else>{{Number(row.stockOriginUnknown)?$tr("来源待确认"):row.oldestInboundDate || '—'}}</span>
      </template>
    </el-table-column><el-table-column prop="stockAgeDays" :label="$tr(&quot;库龄&quot;)" width="90" align="right"><template #default="{row}"><el-tooltip v-if="Number(row.stockOriginUnknown)" :content="$tr(&quot;缺少有效原入库或采购依据，不能以退货或质检日期重新计算库龄。&quot;)"><span>{{ $tr("来源待确认") }}</span></el-tooltip><el-tag v-else-if="row.ageWarning" type="danger" effect="plain">{{ $tr("{0}天", [row.stockAgeDays]) }}</el-tag><span v-else>{{row.oldestInboundDate ? $tr("{0}天", [row.stockAgeDays]) : '—'}}</span></template></el-table-column><el-table-column :label="$tr(&quot;离供应商退货时间&quot;)" width="185" align="center">
      <template #default="{row}">
        <span v-if="row.productType!=='FINISHED'">—</span>
        <el-tooltip v-else-if="row.supplierReturnDate" placement="top"
          :content="$tr(&quot;采购单：{0}；供应商：{1}。{2}&quot;, [row.supplierReturnDocNo, row.supplierReturnSupplierName || '—', Number(row.stockOriginFirstPurchase)?$tr(&quot;退回商品存在多次采购，按首次采购单计算，特殊约定日期优先。&quot;):$tr(&quot;按先进先出推算剩余采购批次，显示最早退货期限。&quot;)])">
          <div class="return-deadline">
            <el-tag :type="Number(row.supplierReturnDays)<=0?'danger':Number(row.supplierReturnDays)<7?'warning':'success'" effect="plain">
              {{Number(row.supplierReturnDays)>0?$tr("剩余 {0} 天", [row.supplierReturnDays]):Number(row.supplierReturnDays)===0?$tr("今天到期"):$tr("已超期 {0} 天", [Math.abs(Number(row.supplierReturnDays))])}}
            </el-tag>
            <small>{{ $tr("截止 {0}", [String(row.supplierReturnDate).slice(0,10)]) }}</small>
          </div>
        </el-tooltip>
        <el-tooltip v-else-if="Number(row.stockOriginUnknown)" :content="$tr(&quot;退回商品缺少有效原入库或采购依据，暂时无法计算退供期限。&quot;)"><span>{{ $tr("来源待确认") }}</span></el-tooltip>
        <span v-else>{{Number(row.onHandQty)>0?$tr("未设置"):'—'}}</span>
      </template>
    </el-table-column><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="inspectionQty" :label="$tr(&quot;待检&quot;)" width="85" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="defectQty" :label="$tr(&quot;次品&quot;)" width="85" align="right"/><el-table-column v-if="canViewFinance && appliedProductType!=='SAMPLE'" prop="avgCost" :label="$tr(&quot;可售平均成本&quot;)" width="125" align="right"/><el-table-column v-if="canViewFinance && appliedProductType!=='SAMPLE'" prop="stockAmount" :label="$tr(&quot;库存总金额&quot;)" width="120" align="right"/><el-table-column :label="$tr(&quot;操作&quot;)" width="90"><template #default="{row}"><el-button link type="primary" @click="showFlow(row)">{{ $tr("流水") }}</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-drawer v-model="drawer" :title="$tr(&quot;库存流水&quot;)" size="70%"><el-table :data="flows" border><el-table-column prop="createTime" :label="$tr(&quot;时间&quot;)" width="170"/><el-table-column prop="docNo" :label="$tr(&quot;单号&quot;)" width="180"/><el-table-column prop="transactionType" :label="$tr(&quot;类型&quot;)" width="150"/><el-table-column prop="onHandChange" :label="$tr(&quot;库存变化&quot;)" width="100"/><el-table-column prop="beforeOnHand" :label="$tr(&quot;变更前&quot;)" width="90"/><el-table-column prop="afterOnHand" :label="$tr(&quot;变更后&quot;)" width="90"/><el-table-column v-if="canViewFinance" prop="beforeAvgCost" :label="$tr(&quot;原成本&quot;)" width="110"/><el-table-column v-if="canViewFinance" prop="afterAvgCost" :label="$tr(&quot;新成本&quot;)" width="110"/><el-table-column prop="operatorName" :label="$tr(&quot;操作人&quot;)"/></el-table></el-drawer>
  </div>
</template>
<script setup name="JewelryStock">
import { translateText } from '@/locales/translate'

import {useElementSize,useWindowSize} from '@vueuse/core'
import useSettingsStore from '@/store/modules/settings'
import {listJewelryStock,listJewelryStockSupplierOptions,listJewelryTransactions,listJewelrySampleInbounds,getJewelryStockWarningDays,updateJewelryStockWarningDays} from '@/api/jewelry/erp'
import {getJewelrySupplierReturnDays,updateJewelrySupplierReturnDays} from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
import {jewelryProductType,jewelryProductTypes} from '@/utils/jewelryProduct'
const route=useRoute()
const stockToolbar=ref(null),stockTable=ref(null)
const settingsStore=useSettingsStore()
const {height:viewportHeight}=useWindowSize()
const {height:toolbarHeight}=useElementSize(stockToolbar)
// Reserve navigation, page padding, pagination and optional footer. Element Plus
// scrolls only the table body, keeping its header and horizontal columns aligned.
const tableMaxHeight=computed(()=>Math.max(200,viewportHeight.value
  -(settingsStore.tagsView?84:50)-toolbarHeight.value-100-(settingsStore.footerVisible?36:0)))
const supplierReturnDays=ref(25),savingReturnDays=ref(false)
async function loadReturnDays(){supplierReturnDays.value=Number((await getJewelrySupplierReturnDays()).data||25)}
async function saveReturnDays(){
  if(!Number.isInteger(supplierReturnDays.value)||supplierReturnDays.value<1||supplierReturnDays.value>365){proxy.$modal.msgWarning(translateText("请输入1到365之间的整数天数"));return}
  savingReturnDays.value=true
  try{await updateJewelrySupplierReturnDays(supplierReturnDays.value);proxy.$modal.msgSuccess(translateText("统一退货期限已更新，特殊约定日期不变"));await load()}
  finally{savingReturnDays.value=false}
}
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canConfigureWarning=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('jewelry:stock:config'))
const {proxy}=getCurrentInstance()
const routeWarningType=['quantity','age','supplierReturn'].includes(route.query.warningType)?route.query.warningType:'all'
const appliedProductType=ref('')
const supplierOptions=ref([])
const rows=ref([]),flows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),warningDays=ref(25),savingWarning=ref(false);const query=reactive({pageNum:1,pageSize:10,keyword:'',productType:'',supplierIds:[],inStockOnly:false,warningOnly:route.query.warningOnly==='true',warningType:routeWarningType})
async function loadSupplierOptions(){supplierOptions.value=(await listJewelryStockSupplierOptions()).data||[]}
async function load(){loading.value=true;try{const productType=query.productType;const r=await listJewelryStock({...query,supplierIds:query.supplierIds.join(',')});rows.value=(r.rows||[]).map(row=>({...row,sampleInboundDetails:[],sampleInboundLoaded:false,sampleInboundLoading:false,sampleInboundError:false}));appliedProductType.value=productType;total.value=r.total||0;await nextTick();stockTable.value?.setScrollTop(0)}finally{loading.value=false}}
async function loadSampleInboundDetails(row){
  if(row.sampleInboundLoaded||row.sampleInboundLoading)return
  row.sampleInboundLoading=true
  row.sampleInboundError=false
  try{
    const result=await listJewelrySampleInbounds(row.productId)
    row.sampleInboundDetails=result.data||[]
    row.sampleInboundLoaded=true
  }catch(error){row.sampleInboundError=true}
  finally{row.sampleInboundLoading=false}
}
function onSampleExpand(row,expandedRows){
  if(row.productType==='SAMPLE'&&expandedRows.some(item=>item.productId===row.productId))loadSampleInboundDetails(row)
}
function formatInboundDate(value){return value?String(value).slice(0,10):'—'}
function returnCountdown(days){
  const count=Number(days)
  if(!Number.isFinite(count))return '—'
  return count>0?translateText("剩余 {0} 天", [count]):count===0?translateText("今天到期"):translateText("已超期 {0} 天", [Math.abs(count)])
}
function search(){query.pageNum=1;load()}
function toggleInStockOnly(){query.inStockOnly=!query.inStockOnly;search()}
async function loadWarningDays(){warningDays.value=Number((await getJewelryStockWarningDays()).data||25)}
async function saveWarningDays(){savingWarning.value=true;try{await updateJewelryStockWarningDays(warningDays.value);proxy.$modal.msgSuccess(translateText("库龄预警天数已更新"));load()}finally{savingWarning.value=false}}
async function showFlow(row){const r=await listJewelryTransactions({productId:row.productId,pageNum:1,pageSize:100});flows.value=r.rows||[];drawer.value=true}
watch(()=>[route.query.warningOnly,route.query.warningType],([warningOnly,warningType])=>{
  query.warningOnly=warningOnly==='true'
  query.warningType=['quantity','age','supplierReturn'].includes(warningType)?warningType:'all'
  query.keyword=''
  query.productType=''
  query.supplierIds=[]
  query.inStockOnly=false
  query.pageNum=1
  load()
})
loadWarningDays();loadReturnDays();loadSupplierOptions();load()
</script>
<style scoped>
.return-deadline{display:flex;flex-direction:column;align-items:center;gap:4px;padding:4px 0}
.return-deadline small{color:#64748b;font-size:12px}
.stock-toolbar{display:flex;flex-direction:column;gap:14px;margin-bottom:16px;padding:14px 16px;border:1px solid #e5eaf0;border-radius:8px;background:#fff}
.stock-filter-form{display:flex;flex-wrap:wrap;align-items:center;gap:10px 12px;width:100%}
.stock-filter-form :deep(.el-form-item){margin:0}
.stock-filter-form :deep(.el-form-item__content){width:100%}
.stock-filter-form :deep(.el-input),.stock-filter-form :deep(.el-select){width:100%}
.filter-keyword{flex:1 1 210px;max-width:260px}
.filter-product-type{flex:0 0 180px}
.filter-supplier{flex:0 1 240px;min-width:200px}
.filter-warning-type{flex:0 0 170px}
.stock-filter-form :deep(.filter-search){margin-left:auto}
.stock-settings{display:flex;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:12px 24px;padding-top:12px;border-top:1px solid #edf0f4}
.stock-settings-title{margin-right:auto;color:#64748b;font-size:13px;font-weight:600}
.warning-setting{display:flex;align-items:center;gap:8px;color:#64748b;font-size:13px;white-space:nowrap}
.warning-setting .el-input-number{width:96px}
.warning-setting b{color:#334155}
.danger{color:#c2413a;font-weight:700}
@media(max-width:1100px){
  .stock-filter-form :deep(.filter-search){margin-left:0}
  .stock-settings{justify-content:flex-start}
  .stock-settings-title{width:100%;margin:0}
}
@media(max-width:640px){
  .filter-keyword,.filter-product-type,.filter-supplier,.filter-warning-type{flex:1 1 100%;max-width:none;min-width:0}
  .stock-filter-form :deep(.filter-search){flex:1 1 100%}
  .stock-filter-form :deep(.filter-search .el-button){width:100%}
  .warning-setting{flex-wrap:wrap;white-space:normal}
}
</style>
<style scoped>
.sample-expanded{padding-left:48px}
.sample-expanded-state{color:#909399;padding:12px 0;font-size:13px}
.sample-detail-note{display:block;color:#909399;font-size:11px;line-height:1.4;margin-top:3px;overflow-wrap:anywhere}
.sample-inbound-table :deep(.el-table__cell){vertical-align:top}
</style>
