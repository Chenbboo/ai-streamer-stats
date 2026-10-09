<template>
  <section v-loading="loading" class="budget-card">
    <header><div><h2>{{ $tr('项目预算使用对比') }}</h2><p>{{ $tr('按各项目当前预算期间统计，不与上方图表期间混用') }}</p></div><el-button text icon="Refresh" @click="load">{{ $tr('刷新') }}</el-button></header>
    <el-alert v-if="error" type="error" :title="$tr('预算加载失败，请刷新重试')" :closable="false" show-icon />
    <el-empty v-else-if="!visibleRows.length && !loading" :description="$tr('暂无可查看的项目预算')" :image-size="70" />
    <div v-if="pagedRows.length" class="budget-list">
    <article v-for="row in pagedRows" :key="row.projectId" class="budget-row" :class="{ 'is-exceeded': state(row).exceeded }">
      <div class="title">
        <button type="button" @click="openPlan(row)">{{ row.projectName }} ›</button>
        <div class="budget-tags">
          <el-tag v-if="state(row).percent != null" :type="state(row).exceeded ? 'danger' : state(row).percent >= 80 ? 'warning' : 'success'" size="small" effect="light">{{ state(row).exceeded ? $tr('已超预算') : state(row).percent >= 80 ? $tr('接近预算上限') : $tr('预算内') }}</el-tag>
          <el-tag type="info" size="small" effect="plain">{{ modeLabel(row) }}</el-tag>
        </div>
      </div>
      <p class="period">{{ row.periodStart }} — {{ row.periodEnd || $tr('不限期') }} · {{ $tr('统计截至 {0}',[row.asOf]) }} · {{ row.scope==='CASH_EXPENSE'?$tr('仅外部支出'):$tr('全部成本') }}</p>
      <div class="budget-visual" :class="{ 'is-unavailable': state(row).percent == null, 'is-near-limit': !state(row).exceeded && state(row).percent >= 80 }">
        <div class="budget-donut" role="img" :aria-label="`${chartCaption(row)}：${chartValue(row)}`">
          <svg viewBox="0 0 200 200" aria-hidden="true">
            <circle class="budget-donut__track" cx="100" cy="100" r="82" />
            <circle v-if="state(row).percent != null" class="budget-donut__used" cx="100" cy="100" r="82" pathLength="100" :stroke-dasharray="`${state(row).progress} 100`" transform="rotate(-90 100 100)" />
          </svg>
          <div class="budget-donut__center"><strong>{{ chartValue(row) }}</strong><span>{{ chartCaption(row) }}</span></div>
        </div>
        <div class="amounts">
          <div class="amount-tile amount-tile--used"><span><i aria-hidden="true" />{{ row.mode==='DAILY'?$tr('当日已核算'):$tr('已核算使用') }}</span><b>{{ money(row.used) }} <small>{{ unit(row) }}</small></b></div>
          <div class="amount-tile amount-tile--remaining" :class="{ danger: state(row).exceeded, 'is-unavailable': !state(row).known }"><span><i aria-hidden="true" />{{ state(row).exceeded?$tr('已超出'):$tr('剩余额度') }}</span><b>{{ !state(row).known || row.remaining == null ? '—' : money(Math.abs(Number(row.remaining))) }} <small v-if="state(row).known && row.remaining != null">{{ unit(row) }}</small></b></div>
          <div class="budget-limit"><span>{{ row.mode==='DAILY'?$tr('每日上限'):$tr('预算额度') }}</span><b>{{ row.limit == null ? $tr('不设上限') : `${money(row.limit)} ${unit(row)}` }}</b></div>
        </div>
      </div>
      <p v-if="state(row).known && Number(row.limit)===0" class="period" :class="{danger:state(row).exceeded}">{{ state(row).exceeded?$tr('零额度预算已有支出'):$tr('预算额度为零，尚无支出') }}</p>
      <p v-if="row.pendingCount" class="warning">{{ $tr('有 {0} 项人员成本待完善，剩余额度暂不计算', [row.pendingCount]) }}</p>
      <p v-if="row.mode==='DAILY'" class="period">{{ $tr('当日成本可能包含一次性支出，暂不计算每日额度使用率。启动预算：{0}', [row.startupLimit == null ? $tr('未设置') : `${money(row.startupLimit)} ${unit(row)}`]) }}</p>
      <p v-if="row.expired && row.mode!=='DAILY'" class="warning">{{ $tr('此预算期间已结束，请进入项目计划核对') }}</p>
    </article>
    </div>
    <footer v-if="visibleRows.length>pageSize" class="budget-pagination">
      <span>{{ $tr('共 {0} 个项目', [visibleRows.length]) }}</span>
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="visibleRows.length" :pager-count="5" layout="prev, pager, next" small background />
    </footer>
  </section>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getCompanyProjectBudgets, getProjectBudgetUsage } from '@/api/business/projectResources'
import { budgetUsage } from '@/utils/projectResourceCharts'
import { translateText } from '@/locales/translate'
const props=defineProps({projectId:[Number,String],companyDeptId:[Number,String],currency:String,ready:{type:Boolean,default:true},refreshKey:[Number,String]})
const emit=defineEmits(['loaded'])
const router=useRouter(),rows=ref([]),loading=ref(false),error=ref(false),page=ref(1)
const pageSize=2
let sequence=0,disposed=false
const visibleRows=computed(()=>rows.value.filter(row=>!props.currency||row.currency===props.currency).slice().sort((a,b)=>Number(budgetUsage(b).exceeded)-Number(budgetUsage(a).exceeded)))
const pagedRows=computed(()=>visibleRows.value.slice((page.value-1)*pageSize,page.value*pageSize))
const state=budgetUsage,money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const unit=row=>row.currency==='CNY'?translateText('元'):row.currency
const chartValue=row=>state(row).percent != null ? `${state(row).percent.toFixed(1)}%` : row.limit == null ? '∞' : '—'
const chartCaption=row=>state(row).percent != null ? translateText('预算使用率') : row.pendingCount ? translateText('待完善') : row.limit == null ? translateText('不设上限') : row.mode === 'DAILY' ? translateText('每日预算') : state(row).known && Number(row.limit) === 0 ? translateText('零额度') : translateText('暂不计算')
const modeLabel=row=>row.mode==='NONE'?translateText('不设上限'):row.mode==='DAILY'?translateText('每日预算'):translateText('总额上限 · {0}',[{MONTH:translateText('月度'),QUARTER:translateText('季度'),YEAR:translateText('年度'),PROJECT:translateText('整个项目')}[row.cycle]||row.cycle||translateText('整个项目')])
const openPlan=row=>router.push({path:'/business/projects',query:{id:row.projectId,tab:row.planTab||'plan'}})
async function load(){const id=++sequence;rows.value=[];page.value=1;error.value=false;if(!props.ready||(!props.projectId&&!props.companyDeptId)){emit('loaded',[]);loading.value=false;return}loading.value=true
  try{const response=props.projectId?await getProjectBudgetUsage(props.projectId):await getCompanyProjectBudgets({companyDeptId:props.companyDeptId});if(id===sequence&&!disposed){rows.value=props.projectId?[response.data].filter(Boolean):response.data||[];emit('loaded',rows.value)}}
  catch{if(id===sequence&&!disposed){error.value=true;emit('loaded',[])}}finally{if(id===sequence&&!disposed)loading.value=false}
}
watch(()=>[props.projectId,props.companyDeptId,props.ready,props.refreshKey],load,{immediate:true})
watch(()=>props.currency,()=>{page.value=1})
onBeforeUnmount(()=>{disposed=true;sequence++})
defineExpose({reload:load})
</script>
<style scoped>
.budget-card {
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border: 1px solid #e7edf4;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  min-width: 0;
  color: var(--el-text-color-primary);
}
header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
h2 { font-size: 17px; font-weight: 700; margin: 0; }
header p, .period { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
header p { margin: 8px 0 0; }
.budget-list { display: flex; flex-direction: column; gap: 16px; margin-top: 20px; flex: 1; }
.budget-row {
  --budget-used-color: var(--el-color-primary);
  --budget-remaining-color: var(--el-color-success-light-7);
  padding: 18px 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
}
.budget-row.is-exceeded { --budget-used-color: var(--el-color-danger); border-color: var(--el-color-danger-light-7); }
.title { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.title button { font: inherit; font-size: 15px; font-weight: 600; background: transparent; border: 0; cursor: pointer; color: var(--el-color-primary); padding: 0; text-align: left; overflow-wrap: anywhere; }
.title :deep(.el-tag) { flex-shrink: 0; }
.budget-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.period { margin: 10px 0 0; }
.budget-visual { display: grid; grid-template-columns: 190px minmax(0,1fr); align-items: center; gap: 28px; margin-top: 20px; }
.budget-visual.is-near-limit { --budget-used-color: var(--el-color-warning); }
.budget-donut { position: relative; width: 190px; height: 190px; margin: auto; }
.budget-donut svg { display: block; width: 100%; height: 100%; }
.budget-donut circle { fill: none; stroke-width: 16; }
.budget-donut__track { stroke: var(--budget-remaining-color); }
.budget-donut__used { stroke: var(--budget-used-color); }
.budget-visual.is-unavailable .budget-donut__track { stroke: var(--el-border-color-lighter); }
.budget-donut__center { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 8px; padding: 32px; text-align: center; }
.budget-donut__center strong { max-width: 100%; font-size: 30px; font-weight: 700; line-height: 1.2; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; color: var(--budget-used-color); }
.budget-donut__center span { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; }
.budget-visual.is-unavailable .budget-donut__center strong { color: var(--el-text-color-secondary); }
.amounts { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; font-size: 12px; color: var(--el-text-color-secondary); }
.amount-tile { padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: var(--el-bg-color); }
.amount-tile > span { display: flex; align-items: center; gap: 7px; line-height: 1.5; }
.amount-tile i { width: 8px; height: 8px; flex-shrink: 0; border-radius: 50%; }
.amount-tile--used i { background: var(--budget-used-color); }
.amount-tile--remaining i { background: var(--budget-remaining-color); }
.amounts b { display: block; margin-top: 12px; color: var(--el-text-color-primary); font-size: 22px; font-weight: 650; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; line-height: 1.4; }
.amounts b small { font-size: 12px; font-weight: 400; white-space: nowrap; }
.amount-tile--used b { color: var(--budget-used-color); }
.amount-tile--remaining b { color: var(--el-color-success-dark-2); }
.amount-tile--remaining.danger i { background: var(--el-color-danger); }
.amount-tile--remaining.is-unavailable i { background: var(--el-border-color); }
.amount-tile--remaining.is-unavailable b { color: var(--el-text-color-secondary); }
.budget-limit { grid-column: 1 / -1; display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; padding: 4px 2px; }
.amounts .budget-limit b { margin: 0; font-size: 14px; }
.danger, .amounts .danger b { color: var(--el-color-danger); }
.warning { margin: 10px 0 0; font-size: 12px; color: var(--el-color-warning); line-height: 1.7; }
.budget-pagination { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding-top: 16px; margin-top: 20px; border-top: 1px solid var(--el-border-color-lighter); }
.budget-pagination > span { font-size: 12px; color: var(--el-text-color-secondary); }
@container budget-card (max-width: 570px) {
  .budget-visual { grid-template-columns: 1fr; gap: 20px; }
  .amounts b { font-size: 19px; }
}
@container budget-card (max-width: 340px) {
  .amounts { grid-template-columns: 1fr; }
  .amount-tile { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; }
  .amount-tile b { margin-top: 0; }
}
.budget-card { container-type: inline-size; container-name: budget-card; }
@media(max-width:600px) {
  .budget-card { padding: 18px 16px; }
  .budget-row { padding: 16px 14px; }
  .budget-visual { grid-template-columns: 1fr; gap: 16px; }
  .budget-donut { width: 170px; height: 170px; }
  .amounts { gap: 10px; }
  .amount-tile { padding: 14px 12px; }
  .amounts b { font-size: 18px; }
  .amounts .budget-limit b { font-size: 13px; }
  .title { align-items: flex-start; flex-direction: column; gap: 8px; }
  .budget-tags { justify-content: flex-start; }
  .budget-pagination { flex-wrap: wrap; }
}
</style>
