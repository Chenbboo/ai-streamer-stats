<template>
  <div class="app-container">
    <div class="stock-toolbar">
      <el-form inline>
        <el-form-item><el-input v-model="query.keyword" placeholder="SKU或商品名称" clearable/></el-form-item>
        <el-form-item><el-select v-model="query.productType" placeholder="全部商品类型" clearable style="width:150px">
          <el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select></el-form-item>
        <el-form-item><el-checkbox v-model="query.warningOnly">只看预警</el-checkbox></el-form-item>
        <el-form-item v-if="query.warningOnly"><el-select v-model="query.warningType" style="width:120px">
          <el-option label="全部预警" value="all"/><el-option label="库存不足" value="quantity"/><el-option label="库龄超期" value="age"/>
        </el-select></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button></el-form-item>
      </el-form>
      <div class="warning-setting">
        <span>库龄预警</span>
        <el-input-number v-if="canConfigureWarning" v-model="warningDays" :min="1" :max="365" controls-position="right"/>
        <b v-else>{{ warningDays }}</b>
        <span>天</span>
        <el-button v-if="canConfigureWarning" type="primary" plain icon="Check"
          :loading="savingWarning" @click="saveWarningDays">保存</el-button>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border><el-table-column prop="sku" label="SKU" width="140"/><el-table-column prop="productName" label="商品名称" min-width="180"/><el-table-column label="商品类型" width="110"><template #default="{row}"><el-tag :type="jewelryProductType(row.productType)?.tagType || 'info'" effect="plain">{{jewelryProductType(row.productType)?.label || row.productType || '—'}}</el-tag></template></el-table-column><el-table-column prop="specification" label="规格类型" width="100"><template #default="{row}">{{row.specification || '—'}}</template></el-table-column><el-table-column prop="onHandQty" label="账面库存" width="100" align="right"/><el-table-column prop="reservedOutQty" label="出库冻结" width="100" align="right"/><el-table-column prop="availableQty" label="可用库存" width="100" align="right"><template #default="{row}"><span :class="{danger:row.quantityWarning}">{{row.availableQty}}</span></template></el-table-column><el-table-column prop="oldestInboundDate" label="最早入库" width="115"><template #default="{row}">{{row.oldestInboundDate || '—'}}</template></el-table-column><el-table-column prop="stockAgeDays" label="库龄" width="90" align="right"><template #default="{row}"><el-tag v-if="row.ageWarning" type="danger" effect="plain">{{row.stockAgeDays}}天</el-tag><span v-else>{{row.oldestInboundDate ? `${row.stockAgeDays}天` : '—'}}</span></template></el-table-column><el-table-column prop="inspectionQty" label="待检" width="85" align="right"/><el-table-column prop="defectQty" label="次品" width="85" align="right"/><el-table-column v-if="canViewFinance" prop="avgCost" label="平均成本" width="115" align="right"/><el-table-column v-if="canViewFinance" prop="stockAmount" label="库存金额" width="120" align="right"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="showFlow(row)">流水</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-drawer v-model="drawer" title="库存流水" size="70%"><el-table :data="flows" border><el-table-column prop="createTime" label="时间" width="170"/><el-table-column prop="docNo" label="单号" width="180"/><el-table-column prop="transactionType" label="类型" width="150"/><el-table-column prop="onHandChange" label="库存变化" width="100"/><el-table-column prop="beforeOnHand" label="变更前" width="90"/><el-table-column prop="afterOnHand" label="变更后" width="90"/><el-table-column v-if="canViewFinance" prop="beforeAvgCost" label="原成本" width="110"/><el-table-column v-if="canViewFinance" prop="afterAvgCost" label="新成本" width="110"/><el-table-column prop="operatorName" label="操作人"/></el-table></el-drawer>
  </div>
</template>
<script setup name="JewelryStock">
import {listJewelryStock,listJewelryTransactions,getJewelryStockWarningDays,updateJewelryStockWarningDays} from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
import {jewelryProductType,jewelryProductTypes} from '@/utils/jewelryProduct'
const route=useRoute()
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canConfigureWarning=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('jewelry:stock:config'))
const {proxy}=getCurrentInstance()
const routeWarningType=['quantity','age'].includes(route.query.warningType)?route.query.warningType:'all'
const rows=ref([]),flows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),warningDays=ref(25),savingWarning=ref(false);const query=reactive({pageNum:1,pageSize:10,keyword:'',productType:'',warningOnly:route.query.warningOnly==='true',warningType:routeWarningType})
async function load(){loading.value=true;try{const r=await listJewelryStock(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function search(){query.pageNum=1;load()}
async function loadWarningDays(){warningDays.value=Number((await getJewelryStockWarningDays()).data||25)}
async function saveWarningDays(){savingWarning.value=true;try{await updateJewelryStockWarningDays(warningDays.value);proxy.$modal.msgSuccess('库龄预警天数已更新');load()}finally{savingWarning.value=false}}
async function showFlow(row){const r=await listJewelryTransactions({productId:row.productId,pageNum:1,pageSize:100});flows.value=r.rows||[];drawer.value=true}
watch(()=>[route.query.warningOnly,route.query.warningType],([warningOnly,warningType])=>{
  query.warningOnly=warningOnly==='true'
  query.warningType=['quantity','age'].includes(warningType)?warningType:'all'
  query.pageNum=1
  load()
})
loadWarningDays();load()
</script>
<style scoped>.stock-toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.stock-toolbar :deep(.el-form-item){margin-bottom:14px}.warning-setting{display:flex;align-items:center;gap:8px;color:#64748b;font-size:13px;white-space:nowrap}.warning-setting .el-input-number{width:105px}.warning-setting b{color:#334155}.danger{color:#c2413a;font-weight:700}@media(max-width:900px){.stock-toolbar{align-items:stretch;flex-direction:column;gap:0}.warning-setting{margin-bottom:14px}}</style>
