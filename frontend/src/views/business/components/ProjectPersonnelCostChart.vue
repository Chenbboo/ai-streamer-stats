<template>
  <section v-loading="loading" class="resource-card">
    <header><div><h2>{{ $tr('项目人员成本构成') }}</h2><p>{{ data.projectName || $tr('请选择项目') }}</p></div><el-button text icon="Refresh" :disabled="!projectId" @click="load">{{ $tr('刷新') }}</el-button></header>
    <div class="filters">
      <slot name="project-selector" />
      <el-radio-group v-model="periodMode" size="small"><el-radio-button value="range">{{ $tr('所选期间') }}</el-radio-button><el-radio-button value="project">{{ $tr('项目累计') }}</el-radio-button></el-radio-group>
      <el-date-picker v-if="periodMode==='range'" v-model="dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="futureDay" :start-placeholder="$tr('开始日期')" :end-placeholder="$tr('结束日期')" @change="load" />
    </div>
    <el-alert v-if="error" :title="$tr('人员成本加载失败，请刷新重试')" type="error" :closable="false" show-icon />
    <template v-else-if="projectId && data.projectId">
      <p class="hint">{{ data.entireProject ? $tr('项目累计，截至 {0}', [data.dateTo]) : `${data.dateFrom} — ${data.dateTo}` }} · {{ $tr('仅展示已核算成本') }}</p>
      <el-alert v-if="data.pendingCount" :title="$tr('有 {0} 项人员成本待完善，当前金额不是完整成本', [data.pendingCount])" type="warning" :closable="false" show-icon />
      <div class="personnel-body">
        <div class="donut"><div ref="canvas" class="canvas" /><div class="total"><small>{{ $tr('已核算人员成本') }}</small><strong>{{ money(data.totalAmount) }}</strong><small>{{ unit }}</small><small v-if="!distribution.total">{{ $tr('暂无正向成本') }}</small></div></div>
        <div class="personnel-list">
          <div class="list-heading"><span>{{ $tr('人员') }}</span><span>{{ $tr('已核算成本 / 占比') }}</span><span>{{ $tr('当前投入') }}</span></div>
          <button v-for="(row,index) in visibleRows" :key="row.userId ?? index" type="button" class="person" @click="selectPerson(row)">
            <span><i :style="{ background: color(row) }" />{{ row.userName || $tr('未记录姓名') }}<small v-if="!row.canAdjust && !row.currentMember && row.userId">{{ $tr('历史成本') }}</small></span>
            <span><b>{{ amountReady(row) ? money(row.amount) : $tr('尚未核算') }}</b><small>{{ share(row) }}</small></span>
            <span>{{ row.allocationPercent == null ? '—' : `${Number(row.allocationPercent).toFixed(2)}%` }} ›</span>
          </button>
          <el-button v-if="rows.length>6" text type="primary" @click="expanded=!expanded">{{ expanded ? $tr('收起人员') : $tr('查看全部 {0} 人', [rows.length]) }}</el-button>
          <p v-if="!rows.length" class="hint">{{ $tr('没有人员成本记录') }}</p>
        </div>
      </div>
      <p class="hint">{{ $tr('成本占比按本期正向成本计算；当前投入为今日跨项目分配比例，两者含义不同。负向调整单列，不进入饼图。') }}</p>
      <p v-if="rows.some(row=>row.unattributed)" class="hint">{{ $tr('核算差额无法归属到个人，单独保留以与项目核算总额一致。') }}</p>
    </template>
    <el-empty v-else :description="projectId ? $tr('正在读取核算数据') : $tr('请选择项目查看人员成本')" :image-size="70" />
    <el-drawer v-model="detailVisible" class="personnel-detail-drawer" :title="$tr('人员成本详情')" size="min(480px, 100vw)" append-to-body>
      <div v-if="selected" class="personnel-detail">
        <div class="personnel-detail__identity">
          <div class="personnel-detail__avatar" aria-hidden="true">{{ Array.from(selected.userName || '').slice(0, 1).join('') || '—' }}</div>
          <div class="personnel-detail__identity-copy">
            <h3>{{ selected.userName || $tr('未记录姓名') }}</h3>
            <span class="personnel-detail__project"><i :style="{ background: color(selected) }" aria-hidden="true" />{{ data.projectName || $tr('请选择项目') }}</span>
          </div>
          <el-tag v-if="!selected.canAdjust && !selected.currentMember && selected.userId" type="info" size="small" effect="plain">{{ $tr('历史成本') }}</el-tag>
        </div>

        <section class="personnel-detail__cost">
          <span class="personnel-detail__label">{{ $tr('已核算成本') }}</span>
          <div class="personnel-detail__amount" :class="{ 'is-pending': !amountReady(selected), 'is-negative': Number(selected.amount) < 0 }">
            <strong>{{ amountReady(selected) ? money(selected.amount) : $tr('尚未核算') }}</strong>
            <span v-if="amountReady(selected)">{{ unit }}</span>
          </div>
          <p>{{ data.entireProject ? $tr('项目累计，截至 {0}', [data.dateTo]) : `${data.dateFrom} — ${data.dateTo}` }}</p>
        </section>

        <div class="personnel-detail__metrics">
          <section class="personnel-detail__metric">
            <span class="personnel-detail__label">{{ $tr('成本占比') }}</span>
            <strong>{{ share(selected) }}</strong>
            <el-progress v-if="Number(selected.amount) > 0 && distribution.total > 0" :percentage="Math.min(100, Number(selected.amount) / distribution.total * 100)" :show-text="false" :stroke-width="5" :color="color(selected)" />
          </section>
          <section class="personnel-detail__metric">
            <span class="personnel-detail__label">{{ $tr('当前投入比例') }}</span>
            <strong>{{ selected.allocationPercent == null ? '—' : `${Number(selected.allocationPercent).toFixed(2)}%` }}</strong>
            <el-progress v-if="selected.allocationPercent != null" :percentage="Math.max(0, Math.min(100, Number(selected.allocationPercent)))" :show-text="false" :stroke-width="5" color="var(--el-color-primary)" />
          </section>
        </div>

        <section v-if="selected.startDate || selected.workingDays != null || selected.userId" class="personnel-detail__billing">
          <div v-if="selected.startDate" class="personnel-detail__billing-dates">
            <span class="personnel-detail__label"><el-icon><Calendar /></el-icon>{{ $tr('计费日期') }}</span>
            <div><time>{{ selected.startDate }}</time><span class="personnel-detail__date-separator">—</span><time>{{ selected.endDate || '—' }}</time></div>
          </div>
          <div v-if="selected.workingDays != null" class="personnel-detail__billing-days">
            <span class="personnel-detail__label">{{ $tr('计费工作日') }}</span>
            <span><strong>{{ selected.workingDays }}</strong> {{ $tr('天') }}</span>
          </div>
          <div v-if="selected.userId" class="personnel-detail__salary">
            <div class="personnel-detail__billing-days">
              <span class="personnel-detail__label">{{ $tr('日薪（人力资源）') }}</span>
              <span v-if="selected.rawCostVisible && selected.dailySalary != null"><strong>{{ salary(selected) }}</strong> {{ salaryUnit(selected) }} / {{ $tr('天') }}</span>
              <span v-else class="personnel-detail__label">{{ selected.rawCostVisible ? $tr('暂无日薪记录') : $tr('需人员成本查看权限') }}</span>
            </div>
            <p v-if="selected.rawCostVisible && selected.dailySalary != null">{{ $tr('月度用人成本') }} {{ money(selected.salaryMonthlyCost) }} {{ salaryUnit(selected) }} ÷ {{ selected.salaryStandardWorkDays }} {{ $tr('天') }}<br>{{ $tr('人力资源当前生效费率') }} · {{ selected.salaryDate }}</p>
          </div>
        </section>
        <p class="personnel-detail__note">{{ $tr('成本占比按本期正向成本计算；当前投入为今日跨项目分配比例，两者含义不同。负向调整单列，不进入饼图。') }}</p>
      </div>
      <template #footer>
        <template v-if="selected">
          <el-button v-if="allowAdjust && selected.canAdjust && selected.userId" class="personnel-detail__adjust" type="primary" size="large" @click="adjust"><el-icon><EditPen /></el-icon>{{ $tr('设置该人员投入比例') }}</el-button>
          <p v-else class="personnel-detail__readonly">{{ $tr('此处仅查看成本；历史人员或无调整权限时不能修改投入。') }}</p>
        </template>
      </template>
    </el-drawer>
    <BusinessProjectWorkPanel ref="allocationPanel" :project-id="projectId" :members="data.members || []" :can-manage="allowAdjust" :show-costs="false" @changed="changed" />
  </section>
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount, onActivated } from 'vue'
import { Calendar, EditPen } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProjectPersonnel } from '@/api/business/projectResources'
import { personnelDistribution } from '@/utils/projectResourceCharts'
import { parseTime } from '@/utils/ruoyi'
import { translateText } from '@/locales/translate'
import useUserStore from '@/store/modules/user'
import BusinessProjectWorkPanel from '@/components/BusinessProjectWorkPanel/index.vue'
echarts.use([PieChart, TooltipComponent, CanvasRenderer])
const props=defineProps({ projectId:[Number,String], dateFrom:String, dateTo:String, canManage:{type:Boolean,default:true}, refreshKey:[Number,String] })
const emit=defineEmits(['changed'])
const now=new Date(), today=()=>parseTime(new Date(),'{y}-{m}-{d}')
const dates=ref([parseTime(new Date(now.getFullYear(),now.getMonth(),1),'{y}-{m}-{d}'),today()]),periodMode=ref('range')
const data=ref({}), loading=ref(false), error=ref(false),expanded=ref(false),canvas=ref(),selected=ref(),detailVisible=ref(false),allocationPanel=ref()
const user=useUserStore(),allowAdjust=computed(()=>props.canManage && data.value.canAdjust && (user.permissions.includes('*:*:*') || user.permissions.includes('business:project:allocation')))
const rows=computed(()=>(data.value.rows||[]).slice().sort((a,b)=>Number(b.amount)-Number(a.amount)))
const visibleRows=computed(()=>expanded.value?rows.value:rows.value.slice(0,6)),distribution=computed(()=>personnelDistribution(rows.value))
const unit=computed(()=>data.value.currency==='CNY'?translateText('元'):data.value.currency || '')
const colors=['#3289dc','#f3a354','#48b992','#a08ad7','#ee819f','#9baec2']
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const salary=row=>Number(row.dailySalary).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:4})
const salaryUnit=row=>row.salaryCurrency==='CNY'?translateText('元'):row.salaryCurrency || ''
const amountReady=row=>row.amount!=null && (Number(row.amount)!==0 || Number(row.workingDays)>0 || (!data.value.pendingCount && Number(data.value.resultCount)>0))
const share=row=>Number(row.amount)>0 && distribution.value.total>0?`${(Number(row.amount)/distribution.value.total*100).toFixed(1)}%`:Number(row.amount)<0?translateText('负向调整'):'—'
const color=row=>Number(row.amount)<=0?'#b5bec9':colors[distribution.value.slices.findIndex(slice=>!slice.other && (row.userId!=null?String(slice.userId)===String(row.userId):slice.userName===row.userName))] || colors[5]
const futureDay=date=>parseTime(date,'{y}-{m}-{d}')>today()
let sequence=0,instance,observer,disposed=false
async function load(){const id=++sequence;observer?.disconnect();instance?.dispose();instance=null;data.value={};error.value=false;detailVisible.value=false;if(!props.projectId){loading.value=false;return}loading.value=true
  try{const response=await getProjectPersonnel(props.projectId,periodMode.value==='project'?{entireProject:true}:{dateFrom:dates.value[0],dateTo:dates.value[1]});if(id===sequence && !disposed){data.value=response.data||{};expanded.value=false;await render()}}
  catch{if(id===sequence&&!disposed)error.value=true}finally{if(id===sequence&&!disposed)loading.value=false}
}
function selectPerson(row){selected.value=row;detailVisible.value=true}
async function adjust(){const id=selected.value?.userId;detailVisible.value=false;await nextTick();await allocationPanel.value.openAllocation(id)}
async function changed(){await load();emit('changed')}
async function render(){await nextTick();if(disposed||!canvas.value)return;instance ||= echarts.init(canvas.value);instance.setOption({color:colors,tooltip:{trigger:'item',renderMode:'richText',formatter:item=>`${item.name}\n${money(item.value)} ${unit.value} · ${item.percent}%`},series:[{type:'pie',radius:['66%','89%'],label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:3},data:distribution.value.slices.map(row=>({name:row.other?translateText('其他人员'):row.userName||translateText('未记录姓名'),value:row.value}))}]},true);instance.off('click');instance.on('click',event=>{const row=distribution.value.slices[event.dataIndex];if(row.other)expanded.value=true;else selectPerson(row)});observer?.disconnect();observer ||= new ResizeObserver(()=>instance?.resize());observer.observe(canvas.value);instance.resize()}
watch(()=>[props.projectId,props.refreshKey],load,{immediate:true})
watch(periodMode,load)
watch(()=>[props.dateFrom,props.dateTo],([from,to])=>{if(from&&to){dates.value=[from,to>today()?today():to];if(periodMode.value==='range')load()}},{immediate:true})
onMounted(render);onActivated(()=>nextTick(()=>instance?.resize()));onBeforeUnmount(()=>{disposed=true;sequence++;observer?.disconnect();instance?.dispose()})
defineExpose({reload:load})
</script>

<style scoped>
.filters :deep(.el-date-editor){flex-grow:0;flex-shrink:0}
</style>

<style>
.personnel-detail-drawer {
  --personnel-detail-space: 28px;
  color: var(--el-text-color-primary);
  border-radius: 20px 0 0 20px;
}
.personnel-detail-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 24px var(--personnel-detail-space) 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.personnel-detail-drawer .el-drawer__title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}
.personnel-detail-drawer .el-drawer__close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.personnel-detail-drawer .el-drawer__body {
  padding: var(--personnel-detail-space);
  background: var(--el-fill-color-lighter);
}
.personnel-detail-drawer .personnel-detail {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.personnel-detail-drawer .personnel-detail__identity {
  display: flex;
  align-items: center;
  gap: 14px;
}
.personnel-detail-drawer .personnel-detail__avatar {
  display: flex;
  flex: 0 0 52px;
  align-items: center;
  justify-content: center;
  height: 52px;
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 16px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 23px;
  font-weight: 600;
}
.personnel-detail-drawer .personnel-detail__identity-copy {
  flex: 1;
  min-width: 0;
}
.personnel-detail-drawer .personnel-detail__identity h3 {
  margin: 0 0 7px;
  font-size: 21px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.personnel-detail-drawer .personnel-detail__project {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.personnel-detail-drawer .personnel-detail__project i {
  flex: 0 0 7px;
  height: 7px;
  border-radius: 50%;
}
.personnel-detail-drawer .personnel-detail__cost {
  padding: 24px;
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--el-bg-color));
}
.personnel-detail-drawer .personnel-detail__label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.personnel-detail-drawer .personnel-detail__amount {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin-top: 12px;
  color: var(--el-color-primary);
}
.personnel-detail-drawer .personnel-detail__amount strong {
  min-width: 0;
  font-size: 36px;
  font-weight: 650;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.personnel-detail-drawer .personnel-detail__amount > span { font-size: 14px; }
.personnel-detail-drawer .personnel-detail__amount.is-pending strong {
  color: var(--el-text-color-secondary);
  font-size: 25px;
}
.personnel-detail-drawer .personnel-detail__amount.is-negative { color: var(--el-color-danger); }
.personnel-detail-drawer .personnel-detail__cost p {
  margin: 14px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.personnel-detail-drawer .personnel-detail__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.personnel-detail-drawer .personnel-detail__metric {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
}
.personnel-detail-drawer .personnel-detail__metric strong {
  margin: 12px 0 16px;
  font-size: 25px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.personnel-detail-drawer .personnel-detail__metric .el-progress { margin-top: auto; }
.personnel-detail-drawer .personnel-detail__billing {
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
}
.personnel-detail-drawer .personnel-detail__billing-dates > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.personnel-detail-drawer .personnel-detail__date-separator { color: var(--el-text-color-placeholder); }
.personnel-detail-drawer .personnel-detail__billing-days {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.personnel-detail-drawer .personnel-detail__billing-dates + .personnel-detail__billing-days {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.personnel-detail-drawer .personnel-detail__billing-days strong { font-size: 20px; }
.personnel-detail-drawer .personnel-detail__salary { margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.personnel-detail-drawer .personnel-detail__salary p { margin: 10px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.8; }
.personnel-detail-drawer .personnel-detail__note,
.personnel-detail-drawer .personnel-detail__readonly {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.8;
  text-align: left;
}
.personnel-detail-drawer .el-drawer__footer {
  padding: 18px var(--personnel-detail-space) max(20px, env(safe-area-inset-bottom));
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}
.personnel-detail-drawer .personnel-detail__adjust {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-weight: 600;
}
.personnel-detail-drawer .personnel-detail__adjust .el-icon { margin-right: 8px; }
@media (max-width: 480px) {
  .personnel-detail-drawer { --personnel-detail-space: 20px; border-radius: 0; }
  .personnel-detail-drawer .personnel-detail { gap: 18px; }
  .personnel-detail-drawer .personnel-detail__cost { padding: 20px; }
  .personnel-detail-drawer .personnel-detail__amount strong { font-size: 32px; }
  .personnel-detail-drawer .personnel-detail__metrics { gap: 10px; }
  .personnel-detail-drawer .personnel-detail__metric { padding: 14px; }
  .personnel-detail-drawer .personnel-detail__metric strong { font-size: 23px; }
}
</style>

<style scoped>
.resource-card{padding:22px;border:1px solid #e7edf4;border-radius:16px;background:var(--el-bg-color);min-width:0;margin-bottom:24px;color:var(--el-text-color-primary)}header{display:flex;justify-content:space-between;align-items:center}h2{margin:0;font-size:17px}header p,.hint{color:var(--el-text-color-secondary);font-size:12px;line-height:1.7}.filters{display:flex;gap:12px;flex-wrap:wrap;align-items:center;margin:16px 0}.filters :deep(.el-date-editor){max-width:100%;width:280px}.personnel-body{display:grid;grid-template-columns:minmax(220px,.8fr) minmax(260px,1.2fr);align-items:center;gap:20px}.donut{height:265px;position:relative}.canvas{height:100%;width:100%}.total{position:absolute;inset:0;display:flex;justify-content:center;align-items:center;flex-direction:column;gap:8px;pointer-events:none}.total strong{font-size:23px;max-width:58%;overflow-wrap:anywhere}.total small{color:var(--el-text-color-secondary);font-size:12px}.list-heading,.person{display:grid;grid-template-columns:minmax(100px,1fr) minmax(100px,1fr) 88px;gap:10px;align-items:center;font-size:12px}.list-heading{color:var(--el-text-color-secondary);padding:10px 0;border-bottom:1px solid var(--el-border-color-lighter)}.person{width:100%;border:0;border-bottom:1px solid var(--el-border-color-lighter);padding:12px 0;background:transparent;text-align:left;cursor:pointer;color:inherit}.person:hover{background:var(--el-fill-color-light)}.person small{display:block;margin-top:5px;color:var(--el-text-color-secondary)}.person i{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px}.person>span:last-child{text-align:right}.person>span{overflow-wrap:anywhere}@media(max-width:1100px){.personnel-body{grid-template-columns:1fr}.donut{max-width:390px;width:100%;margin:auto}}@media(max-width:600px){.resource-card{padding:16px}.list-heading,.person{grid-template-columns:minmax(80px,1fr) minmax(90px,1fr) 78px;gap:6px}.filters :deep(.el-date-editor){width:100%}}
</style>
