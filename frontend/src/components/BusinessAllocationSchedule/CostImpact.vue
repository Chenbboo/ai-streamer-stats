<template>
  <div class="cost-impact">
    <p v-if="!segment.impacts?.length" class="cost-note">{{ $tr('比例未变化，保留原记录。') }}</p>
    <article v-for="(impact,index) in segment.impacts || []" :key="impact.projectId || index" class="impact-project">
      <header class="impact-project__header">
        <strong>{{ impact.projectName }}</strong>
        <span>{{ $tr('金额单位：{0}', [impact.currency === 'CNY' ? $tr('元') : impact.currency]) }}</span>
      </header>
      <p v-if="differentEstimatePeriod(impact)" class="cost-note estimate-period">{{ $tr('测算期间') }}：{{ impact.estimateFrom || segment.dateFrom }} — {{ impact.estimateTo || segment.dateTo || $tr('持续生效') }}</p>
      <div class="impact-table-wrap">
        <table class="impact-table">
          <thead><tr><th>{{ $tr('成本范围') }}</th><th>{{ $tr('调整前（已核算）') }}</th><th>{{ $tr('调整后（预计）') }}</th><th>{{ $tr('增减金额') }}</th></tr></thead>
          <tbody>
            <tr class="person-cost">
              <th>{{ $tr('该人员成本') }}<small v-if="userName">{{ userName }}</small></th>
              <td>{{ money(impact.beforeAmount) }}<small v-if="impact.beforePending" class="incomplete">{{ $tr('待补齐') }}</small></td>
              <td class="after-amount">{{ money(impact.afterAmount) }}</td>
              <td :class="differenceClass(impact.difference)">{{ difference(impact.difference) }}</td>
            </tr>
            <tr class="project-cost">
              <th>{{ $tr('项目总成本') }}</th>
              <td>{{ money(impact.projectBeforeAmount) }}</td>
              <td>{{ money(impact.projectAfterAmount) }}<small v-if="impact.otherIssues?.length" class="incomplete">{{ $tr('待补齐') }}</small></td>
              <td :class="differenceClass(projectDifference(impact))">{{ difference(projectDifference(impact)) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="impact.startCorrectionDate" class="start-correction"><span>{{ $tr('成本起算日期修正为') }}</span><b>{{ impact.startCorrectionDate }}</b></div>
      <details v-if="impact.otherIssues?.length" class="cost-issues">
        <summary>{{ $tr('其他成员成本待完善：{0}项', [impact.otherIssues.length]) }}<span>{{ $tr('查看明细') }}</span></summary>
        <ul><li v-for="(issue,issueIndex) in groupedIssues(impact.otherIssues)" :key="issueIndex"><b v-if="issue.name">{{ issue.name }}</b><span v-if="issue.dateFrom" class="issue-dates">{{ issue.dateFrom }}<template v-if="issue.dateTo !== issue.dateFrom"> — {{ issue.dateTo }}</template></span><span>{{ $tr(issue.message) }}</span></li></ul>
      </details>
      <p v-if="impact.beforePending || impact.otherIssues?.length" class="cost-note incomplete-note">{{ $tr('待补齐金额未计入，当前对比不代表完整成本。') }}</p>
    </article>
    <p v-if="segment.impacts?.length" class="cost-note cost-scope-note">{{ $tr('项目总成本包含该人员及其他成员；保存后按有效记录重新核算。') }}</p>
    <p v-if="!segment.dateTo" class="cost-note">{{ $tr('持续生效的成本仅测算至开始月份月末或今天；未来金额为预计成本，不会提前入账。') }}</p>
    <p v-else-if="segment.dateTo > today()" class="cost-note">{{ $tr('未来期间展示预计成本，只按实际发生日期核算入账。') }}</p>
  </div>
</template>

<script setup>
const props=defineProps({segment:{type:Object,required:true},userName:{type:String,default:''}})
const money=value=>value==null?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const difference=value=>value==null?'—':`${Number(value)>0?'+':''}${money(value)}`
const differenceClass=value=>value==null?'':Number(value)>0?'cost-increase':Number(value)<0?'cost-decrease':''
const projectDifference=row=>row.projectAfterAmount==null||row.projectBeforeAmount==null?null:Number(row.projectAfterAmount)-Number(row.projectBeforeAmount)
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const differentEstimatePeriod=impact=>impact.estimateFrom&&impact.estimateTo&&(impact.estimateFrom!==props.segment.dateFrom || impact.estimateTo!==props.segment.dateTo)
function groupedIssues(issues){
  const groups=[],dated=new Map()
  for(const raw of issues){
    const match=String(raw).match(/^(.+?)\s*\/\s*(\d{4}-\d{2}-\d{2})[：:]\s*(.*)$/)
    if(!match){groups.push({message:String(raw)});continue}
    const [,name,date,message]=match,key=JSON.stringify([name,message])
    if(!dated.has(key))dated.set(key,{name,message,dates:new Set()})
    dated.get(key).dates.add(date)
  }
  for(const group of dated.values())for(const date of [...group.dates].sort()){
    const previous=groups.at(-1),next=previous?.dateTo?new Date(`${previous.dateTo}T12:00:00`):null
    if(next)next.setDate(next.getDate()+1)
    const nextDate=next?`${next.getFullYear()}-${String(next.getMonth()+1).padStart(2,'0')}-${String(next.getDate()).padStart(2,'0')}`:null
    if(previous?.name===group.name&&previous?.message===group.message&&date===nextDate)previous.dateTo=date
    else groups.push({name:group.name,message:group.message,dateFrom:date,dateTo:date})
  }
  return groups
}
</script>

<style scoped>
.impact-project{border:1px solid var(--el-border-color-light);border-radius:10px;background:var(--el-bg-color);overflow:hidden}.impact-project+.impact-project{margin-top:12px}.impact-project__header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 16px;border-bottom:1px solid var(--el-border-color-lighter)}.impact-project__header strong{font-size:15px;color:var(--el-text-color-primary)}.impact-project__header>span{font-size:12px;color:var(--el-text-color-secondary)}.impact-table-wrap{overflow-x:auto}.impact-table{width:100%;border-collapse:collapse;min-width:540px;font-size:13px}.impact-table th,.impact-table td{padding:12px 16px;text-align:right;border-bottom:1px solid var(--el-border-color-lighter);font-variant-numeric:tabular-nums;white-space:nowrap}.impact-table th:first-child{text-align:left;width:23%}.impact-table thead th{font-weight:400;background:var(--el-fill-color-light);color:var(--el-text-color-secondary)}.impact-table tbody th{font-weight:500;color:var(--el-text-color-primary)}.impact-table small{display:block;margin-top:4px;font-weight:400;font-size:12px;color:var(--el-text-color-secondary)}.person-cost td{font-size:16px;font-weight:600}.after-amount{color:var(--el-color-primary)}.project-cost td{color:var(--el-text-color-regular)}.impact-table .cost-increase{color:var(--el-color-danger)}.impact-table .cost-decrease{color:var(--el-color-success)}.impact-table .incomplete{font-size:11px;color:var(--el-color-warning)}.cost-note{font-size:12px;line-height:1.6;color:var(--el-text-color-secondary);margin:10px 0 0}.estimate-period{margin:10px 16px}.start-correction{display:flex;flex-wrap:wrap;gap:10px;padding:10px 16px;font-size:12px;color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.start-correction b{font-weight:500}.cost-issues{padding:12px 16px;font-size:12px;background:var(--el-color-warning-light-9)}.cost-issues summary{cursor:pointer;color:var(--el-color-warning-dark-2)}.cost-issues summary>span{float:right;color:var(--el-text-color-secondary)}.cost-issues ul{list-style:none;margin:12px 0 0;padding:0;color:var(--el-text-color-regular)}.cost-issues li{display:flex;flex-wrap:wrap;gap:8px;line-height:1.7;padding:5px 0}.cost-issues li+li{border-top:1px solid var(--el-color-warning-light-7)}.issue-dates{font-variant-numeric:tabular-nums;color:var(--el-text-color-secondary)}.incomplete-note{padding:0 16px 10px}.cost-scope-note{margin-top:12px}
@media(max-width:640px){.impact-project__header{padding:12px}.impact-table th,.impact-table td{padding:10px 12px}.cost-issues,.start-correction{padding:10px 12px}.person-cost td{font-size:14px}}
</style>
