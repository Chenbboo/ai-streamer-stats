<template>
  <section class="allocation-timeline">
    <div class="timeline-heading"><div><h4>{{ $tr('按时间段查看投入') }}</h4><p>{{ $tr('显示该期间实际生效的项目分配，点击时间段查看明细。') }}</p></div><el-button v-if="issues.length" type="warning" plain size="small" @click="filter='ISSUES'">{{ $tr('待核对 {0} 段',[issues.length]) }}</el-button></div>
    <div class="timeline-filters" role="group" :aria-label="$tr('筛选投入时间段')"><el-button v-for="item in filters" :key="item.value" size="small" :type="filter===item.value?'primary':'default'" :plain="filter!==item.value" @click="filter=item.value">{{ item.label }}</el-button></div>
    <p v-if="!shown.length" class="timeline-empty">{{ $tr('该范围暂无投入时间段') }}</p>
    <article v-for="period in shown" :key="period.dateFrom" class="timeline-period" :class="{'timeline-period--issue':period.status!=='FULL'}">
      <button type="button" class="period-toggle" :aria-expanded="expanded.includes(period.dateFrom)" @click="toggle(period.dateFrom)">
        <span class="period-date">{{ period.dateFrom }} — {{ period.dateTo || $tr('持续生效') }}<el-tag v-if="current(period)" size="small" effect="plain">{{ $tr('当前') }}</el-tag></span>
        <span class="period-state"><el-tag :type="tone(period)" size="small">{{ stateLabel(period) }}</el-tag><span aria-hidden="true">{{ expanded.includes(period.dateFrom)?'⌃':'⌄' }}</span></span>
      </button>
      <div class="period-distribution" :aria-label="$tr('已分配 {0}',[percent(period.totalPercent)])">
        <span v-for="project in positive(period)" :key="project.projectId" :style="{width:width(project,period),background:color(project.projectId)}" :title="`${project.projectName} ${percent(project.allocationValue)}`"></span>
      </div>
      <div class="period-legend"><span v-for="project in positive(period)" :key="project.projectId"><i :style="{background:color(project.projectId)}"></i>{{ project.projectName }} <b>{{ percent(project.allocationValue) }}</b></span><span v-if="period.status==='INCOMPLETE'" class="allocation-unknown">{{ $tr('缺少记录或待确认') }}</span><span v-else-if="Number(period.totalPercent)<100" class="allocation-unknown">{{ $tr('未分配 {0}',[percent(100-Number(period.totalPercent))]) }}</span><span v-if="!positive(period).length&&period.status!=='INCOMPLETE'">{{ $tr('该期间无投入') }}</span></div>
      <div v-if="expanded.includes(period.dateFrom)" class="period-details">
        <div v-for="project in detailProjects(period)" :key="project.projectId" class="period-project">
          <div class="project-identity"><b><i :style="{background:color(project.projectId)}"></i>{{ project.projectName }}</b><small>{{ project.projectNo }} · {{ $tr('开始 {0}',[project.planStartDate || project.actualStartDate || '—']) }}</small><el-tag v-if="lockLabel(project)" size="small" type="info">{{ lockLabel(project) }}</el-tag><small v-if="project.frozen" class="lock-help">{{ lockHint(project) }}</small></div>
          <div class="project-share"><b>{{ project.allocationMissing?$tr('未设置'):project.confirmationStatus==='PENDING'?$tr('待确认'):percent(project.allocationValue) }}</b><small>{{ project.autoRedistributed?$tr('含自动分配'):project.allocationMissing?$tr('缺少投入记录'):project.confirmationStatus==='PENDING'?$tr('尚未生效'):$tr('已生效') }}</small></div>
        </div>
        <div class="period-actions"><el-button v-if="zeros(period).length" link @click="toggleZeros(period.dateFrom)">{{ zeroExpanded.includes(period.dateFrom)?$tr('收起0%项目'):$tr('查看0%项目（{0}）',[zeros(period).length]) }}</el-button><el-button v-if="editable(period)" type="primary" plain size="small" :disabled="busy" @click="$emit('adjust',period)">{{ $tr('调整本段') }}</el-button></div>
      </div>
    </article>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { translateText as tr } from '@/locales/translate'
import { allocationLockLabel, allocationLockHint } from './presentation'
const props=defineProps({periods:{type:Array,default:()=>[]},busy:Boolean,pending:Boolean})
defineEmits(['adjust'])
const filter=ref('CURRENT'),expanded=ref([]),zeroExpanded=ref([])
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const current=p=>p.dateFrom<=today()&&(!p.dateTo||p.dateTo>=today())
const filters=computed(()=>[{value:'CURRENT',label:tr('当前')},{value:'HISTORY',label:tr('历史')},{value:'FUTURE',label:tr('未来')},{value:'ALL',label:tr('全部')},...(issues.value.length?[{value:'ISSUES',label:tr('待核对')}]:[])])
const issues=computed(()=>props.periods.filter(p=>p.status!=='FULL'))
const shown=computed(()=>props.periods.filter(p=>filter.value==='ALL'||filter.value==='ISSUES'&&p.status!=='FULL'||filter.value==='CURRENT'&&current(p)||filter.value==='HISTORY'&&p.dateTo&&p.dateTo<today()||filter.value==='FUTURE'&&p.dateFrom>today()))
watch(()=>props.periods,periods=>{filter.value=periods.some(current)?'CURRENT':'ALL';expanded.value=periods.filter(current).map(p=>p.dateFrom);zeroExpanded.value=[]},{immediate:true})
const percent=v=>`${Number(v||0).toFixed(2)}%`
const positive=p=>p.projects.filter(r=>r.allocationValue!=null&&Number(r.allocationValue)>0)
const zeros=p=>p.projects.filter(r=>!r.allocationMissing&&r.confirmationStatus!=='PENDING'&&Number(r.allocationValue)===0)
const detailProjects=p=>p.projects.filter(r=>zeroExpanded.value.includes(p.dateFrom)||!zeros(p).includes(r))
const color=id=>['#3b82f6','#12a594','#e6a23c','#8b5cf6','#ec6b91','#64748b'][Math.abs(Number(id)||0)%6]
const width=(r,p)=>`${Number(r.allocationValue)/Math.max(100,Number(p.totalPercent))*100}%`
const tone=p=>p.status==='FULL'?'success':p.status==='OVER'?'danger':'warning'
const stateLabel=p=>p.status==='INCOMPLETE'?tr('分配待完善'):p.status==='FULL'?tr('合计100%'):tr('合计 {0}',[percent(p.totalPercent)])
const lockLabel=r=>tr(allocationLockLabel(r))
const lockHint=r=>tr(allocationLockHint(r))
const editable=p=>!props.pending&&p.projects.every(r=>!r.frozen&&r.confirmationStatus!=='PENDING')
function toggle(key){expanded.value=expanded.value.includes(key)?expanded.value.filter(k=>k!==key):[...expanded.value,key]}
function toggleZeros(key){zeroExpanded.value=zeroExpanded.value.includes(key)?zeroExpanded.value.filter(k=>k!==key):[...zeroExpanded.value,key]}
</script>

<style scoped>
.allocation-timeline{margin:20px 0;padding:18px;border:1px solid var(--el-border-color-light);border-radius:12px;background:var(--el-fill-color-extra-light)}.timeline-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.timeline-heading h4{margin:0;font-size:16px}.timeline-heading p,.timeline-empty{margin:7px 0;color:var(--el-text-color-secondary);font-size:12px;line-height:1.6}.timeline-filters{display:flex;flex-wrap:wrap;gap:8px;margin:14px 0}.timeline-filters .el-button{margin:0}.timeline-period{padding:14px;margin-top:10px;border:1px solid var(--el-border-color-light);border-radius:10px;background:var(--el-bg-color)}.timeline-period--issue{border-color:var(--el-color-warning-light-5)}.period-toggle{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;background:none;border:0;padding:0;text-align:left;font:inherit;color:var(--el-text-color-primary);cursor:pointer}.period-date{display:flex;flex-wrap:wrap;align-items:center;gap:8px;font-size:13px;font-weight:600}.period-state{display:flex;align-items:center;gap:10px;flex-shrink:0}.period-distribution{display:flex;height:10px;margin:14px 0 10px;overflow:hidden;border-radius:6px;background:var(--el-fill-color)}.period-distribution>span{height:100%;flex-shrink:0}.period-legend{display:flex;flex-wrap:wrap;gap:8px 18px;font-size:12px;line-height:1.7}.period-legend>span{display:inline-flex;align-items:center;gap:6px}.period-legend i,.project-identity i{display:inline-block;width:8px;height:8px;border-radius:50%;flex-shrink:0}.allocation-unknown{color:var(--el-color-warning)}.period-details{margin-top:14px;border-top:1px solid var(--el-border-color-lighter)}.period-project{display:flex;justify-content:space-between;gap:16px;padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter);font-size:13px}.project-identity b{display:flex;align-items:center;gap:7px}.project-identity small,.project-share small{display:block;margin:5px 0;color:var(--el-text-color-secondary);line-height:1.6;font-size:12px}.project-identity{min-width:0;overflow-wrap:anywhere}.project-identity .lock-help{color:var(--el-color-warning)}.project-share{text-align:right;flex-shrink:0}.period-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:12px;flex-wrap:wrap}@media(max-width:640px){.allocation-timeline{padding:12px}.timeline-heading{align-items:flex-start;flex-direction:column}.period-toggle{align-items:flex-start;flex-direction:column}.period-date{font-size:12px}.period-project{gap:8px}.period-project small{font-size:11px}}
</style>
