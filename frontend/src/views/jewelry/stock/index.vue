<template>
  <div class="app-container">
    <div ref="stockToolbar" class="stock-toolbar">
      <el-form inline>
        <el-form-item><el-input v-model="query.keyword" placeholder="SKU或商品名称" clearable/></el-form-item>
        <el-form-item><el-select v-model="query.productType" placeholder="全部商品类型" clearable style="width:150px">
          <el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select></el-form-item>
        <el-form-item><el-checkbox v-model="query.warningOnly">只看预警</el-checkbox></el-form-item>
        <el-form-item v-if="query.warningOnly"><el-select v-model="query.warningType" style="width:165px">
          <el-option label="全部预警" value="all"/><el-option label="库存不足" value="quantity"/><el-option label="库龄超期" value="age"/>
          <el-option label="退供不足7天" value="supplierReturn"/>
        </el-select></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button></el-form-item>
      </el-form>
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:10px">
      <div class="warning-setting">
        <span>库龄预警</span>
        <el-input-number v-if="canConfigureWarning" v-model="warningDays" :min="1" :max="365" controls-position="right"/>
        <b v-else>{{ warningDays }}</b>
        <span>天</span>
        <el-button v-if="canConfigureWarning" type="primary" plain icon="Check"
          :loading="savingWarning" @click="saveWarningDays">保存</el-button>
      </div>
      <div class="warning-setting">
        <el-tooltip content="仅成品商品参与退货时间预警。采购业务日期加统一期限；采购单单独设置的约定退货日期优先，历史采购单同样适用。">
          <span>供应商退货期限</span>
        </el-tooltip>
        <el-input-number v-if="canConfigureWarning" v-model="supplierReturnDays" :min="1" :max="365" :precision="0" controls-position="right"/>
        <b v-else>{{supplierReturnDays}}</b><span>天</span>
        <el-button v-if="canConfigureWarning" type="primary" plain icon="Check"
          :loading="savingReturnDays" @click="saveReturnDays">保存</el-button>
      </div>
      </div>
    </div>
    <el-table ref="stockTable" :max-height="tableMaxHeight" :data="rows" v-loading="loading" border
      @expand-change="onSampleExpand">
      <el-table-column v-if="appliedProductType==='SAMPLE'" type="expand" width="48">
        <template #default="{row}">
          <div class="sample-expanded">
            <div v-if="row.sampleInboundLoading" class="sample-expanded-state">正在加载入库记录…</div>
            <el-button v-else-if="row.sampleInboundError" link type="primary" @click="loadSampleInboundDetails(row)">加载失败，点击重试</el-button>
            <div v-else-if="!row.sampleInboundDetails.length" class="sample-expanded-state">暂无已生效的样品入库记录</div>
            <el-table v-else :data="row.sampleInboundDetails" :show-header="false" border size="small" max-height="360" class="sample-inbound-table">
              <el-table-column width="140"/>
              <el-table-column min-width="180"/>
              <el-table-column width="110"/>
              <el-table-column width="100"/>
              <el-table-column label="账面总库存" width="115" align="right"><template #default="{row:item}">{{item.totalStockQty}}</template></el-table-column>
              <el-table-column label="可用库存" width="100" align="right"><template #default="{row:item}">{{item.availableQty}}</template></el-table-column>
              <el-table-column label="入库时间" width="115">
                <template #default="{row:item}">
                  {{formatInboundDate(item.inboundDate)}}
                  <small class="sample-detail-note">货号 {{item.goodsNo || '—'}} · {{item.docNo}}</small>
                </template>
              </el-table-column>
              <el-table-column label="库龄" width="90" align="right"><template #default="{row:item}">{{item.stockAgeDays}}天</template></el-table-column>
              <el-table-column label="离供应商退货时间" width="185">
                <template #default="{row:item}">
                  <span :class="{danger:Number(item.supplierReturnDays)<=0}">{{returnCountdown(item.supplierReturnDays)}}</span>
                  <small class="sample-detail-note">截止 {{formatInboundDate(item.supplierReturnDate)}} · {{item.supplierName || '供应商未记录'}}</small>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90"><template #default><el-button link type="primary" @click="showFlow(row)">流水</el-button></template></el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column><el-table-column prop="sku" label="SKU" width="140"/><el-table-column prop="productName" label="商品名称" min-width="180"/><el-table-column label="商品类型" width="110"><template #default="{row}"><el-tag :type="jewelryProductType(row.productType)?.tagType || 'info'" effect="plain">{{jewelryProductType(row.productType)?.label || row.productType || '—'}}</el-tag></template></el-table-column><el-table-column prop="specification" label="规格类型" width="100"><template #default="{row}">{{row.specification || '—'}}</template></el-table-column><el-table-column prop="totalStockQty" label="账面总库存" width="115" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="onHandQty" label="可售库存" width="100" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="reservedOutQty" label="出库冻结" width="100" align="right"/><el-table-column prop="availableQty" label="可用库存" width="100" align="right"><template #default="{row}"><span :class="{danger:row.quantityWarning}">{{row.availableQty}}</span></template></el-table-column><el-table-column prop="oldestInboundDate" :label="appliedProductType === 'SAMPLE' ? '入库时间' : '最早入库'" width="115">
      <template #default="{row}">
        <el-tooltip v-if="Number(row.stockOriginFirstPurchase) && !Number(row.stockOriginUnknown)"
          content="退回商品存在多次采购，按首次采购入库日期计算库龄及退供期限，不代表已关联实际批次。">
          <span>{{row.oldestInboundDate}}</span>
        </el-tooltip>
        <span v-else>{{Number(row.stockOriginUnknown)?'来源待确认':row.oldestInboundDate || '—'}}</span>
      </template>
    </el-table-column><el-table-column prop="stockAgeDays" label="库龄" width="90" align="right"><template #default="{row}"><el-tooltip v-if="Number(row.stockOriginUnknown)" content="缺少有效原入库或采购依据，不能以退货或质检日期重新计算库龄。"><span>来源待确认</span></el-tooltip><el-tag v-else-if="row.ageWarning" type="danger" effect="plain">{{row.stockAgeDays}}天</el-tag><span v-else>{{row.oldestInboundDate ? `${row.stockAgeDays}天` : '—'}}</span></template></el-table-column><el-table-column label="离供应商退货时间" width="185" align="center">
      <template #default="{row}">
        <span v-if="row.productType!=='FINISHED'">—</span>
        <el-tooltip v-else-if="row.supplierReturnDate" placement="top"
          :content="`采购单：${row.supplierReturnDocNo}；供应商：${row.supplierReturnSupplierName || '—'}。${Number(row.stockOriginFirstPurchase)?'退回商品存在多次采购，按首次采购单计算，特殊约定日期优先。':'按先进先出推算剩余采购批次，显示最早退货期限。'}`">
          <div class="return-deadline">
            <el-tag :type="Number(row.supplierReturnDays)<=0?'danger':Number(row.supplierReturnDays)<7?'warning':'success'" effect="plain">
              {{Number(row.supplierReturnDays)>0?`剩余 ${row.supplierReturnDays} 天`:Number(row.supplierReturnDays)===0?'今天到期':`已超期 ${Math.abs(Number(row.supplierReturnDays))} 天`}}
            </el-tag>
            <small>截止 {{String(row.supplierReturnDate).slice(0,10)}}</small>
          </div>
        </el-tooltip>
        <el-tooltip v-else-if="Number(row.stockOriginUnknown)" content="退回商品缺少有效原入库或采购依据，暂时无法计算退供期限。"><span>来源待确认</span></el-tooltip>
        <span v-else>{{Number(row.onHandQty)>0?'未设置':'—'}}</span>
      </template>
    </el-table-column><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="inspectionQty" label="待检" width="85" align="right"/><el-table-column v-if="appliedProductType!=='SAMPLE'" prop="defectQty" label="次品" width="85" align="right"/><el-table-column v-if="canViewFinance && appliedProductType!=='SAMPLE'" prop="avgCost" label="可售平均成本" width="125" align="right"/><el-table-column v-if="canViewFinance && appliedProductType!=='SAMPLE'" prop="stockAmount" label="库存总金额" width="120" align="right"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="showFlow(row)">流水</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-drawer v-model="drawer" title="库存流水" size="70%"><el-table :data="flows" border><el-table-column prop="createTime" label="时间" width="170"/><el-table-column prop="docNo" label="单号" width="180"/><el-table-column prop="transactionType" label="类型" width="150"/><el-table-column prop="onHandChange" label="库存变化" width="100"/><el-table-column prop="beforeOnHand" label="变更前" width="90"/><el-table-column prop="afterOnHand" label="变更后" width="90"/><el-table-column v-if="canViewFinance" prop="beforeAvgCost" label="原成本" width="110"/><el-table-column v-if="canViewFinance" prop="afterAvgCost" label="新成本" width="110"/><el-table-column prop="operatorName" label="操作人"/></el-table></el-drawer>
  </div>
</template>
<script setup name="JewelryStock">
import {useElementSize,useWindowSize} from '@vueuse/core'
import useSettingsStore from '@/store/modules/settings'
import {listJewelryStock,listJewelryTransactions,listJewelrySampleInbounds,getJewelryStockWarningDays,updateJewelryStockWarningDays} from '@/api/jewelry/erp'
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
  if(!Number.isInteger(supplierReturnDays.value)||supplierReturnDays.value<1||supplierReturnDays.value>365){proxy.$modal.msgWarning('请输入1到365之间的整数天数');return}
  savingReturnDays.value=true
  try{await updateJewelrySupplierReturnDays(supplierReturnDays.value);proxy.$modal.msgSuccess('统一退货期限已更新，特殊约定日期不变');await load()}
  finally{savingReturnDays.value=false}
}
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canConfigureWarning=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('jewelry:stock:config'))
const {proxy}=getCurrentInstance()
const routeWarningType=['quantity','age','supplierReturn'].includes(route.query.warningType)?route.query.warningType:'all'
const appliedProductType=ref('')
const rows=ref([]),flows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),warningDays=ref(25),savingWarning=ref(false);const query=reactive({pageNum:1,pageSize:10,keyword:'',productType:'',warningOnly:route.query.warningOnly==='true',warningType:routeWarningType})
async function load(){loading.value=true;try{const productType=query.productType;const r=await listJewelryStock(query);rows.value=(r.rows||[]).map(row=>({...row,sampleInboundDetails:[],sampleInboundLoaded:false,sampleInboundLoading:false,sampleInboundError:false}));appliedProductType.value=productType;total.value=r.total||0;await nextTick();stockTable.value?.setScrollTop(0)}finally{loading.value=false}}
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
  return count>0?`剩余 ${count} 天`:count===0?'今天到期':`已超期 ${Math.abs(count)} 天`
}
function search(){query.pageNum=1;load()}
async function loadWarningDays(){warningDays.value=Number((await getJewelryStockWarningDays()).data||25)}
async function saveWarningDays(){savingWarning.value=true;try{await updateJewelryStockWarningDays(warningDays.value);proxy.$modal.msgSuccess('库龄预警天数已更新');load()}finally{savingWarning.value=false}}
async function showFlow(row){const r=await listJewelryTransactions({productId:row.productId,pageNum:1,pageSize:100});flows.value=r.rows||[];drawer.value=true}
watch(()=>[route.query.warningOnly,route.query.warningType],([warningOnly,warningType])=>{
  query.warningOnly=warningOnly==='true'
  query.warningType=['quantity','age','supplierReturn'].includes(warningType)?warningType:'all'
  query.keyword=''
  query.productType=''
  query.pageNum=1
  load()
})
loadWarningDays();loadReturnDays();load()
</script>
<style scoped>.return-deadline{display:flex;flex-direction:column;align-items:center;gap:4px;padding:4px 0}.return-deadline small{color:#64748b;font-size:12px}.stock-toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.stock-toolbar :deep(.el-form-item){margin-bottom:14px}.warning-setting{display:flex;align-items:center;gap:8px;color:#64748b;font-size:13px;white-space:nowrap}.warning-setting .el-input-number{width:105px}.warning-setting b{color:#334155}.danger{color:#c2413a;font-weight:700}@media(max-width:900px){.stock-toolbar{align-items:stretch;flex-direction:column;gap:0}.warning-setting{margin-bottom:14px}}</style>
<style scoped>
.sample-expanded{padding-left:48px}
.sample-expanded-state{color:#909399;padding:12px 0;font-size:13px}
.sample-detail-note{display:block;color:#909399;font-size:11px;line-height:1.4;margin-top:3px;overflow-wrap:anywhere}
.sample-inbound-table :deep(.el-table__cell){vertical-align:top}
</style>
