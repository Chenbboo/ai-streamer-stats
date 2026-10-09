<template>
  <section class="panel member-completion-panel">
    <div class="panel-head"><div><h2>{{ $tr('成员汇报详情') }}</h2><p>{{ $tr('查看持续工作和一次性工作的历史填报，共 {0} 条。', [filteredReports.length]) }}</p></div></div>
    <el-alert :title="$tr('每次提交的内容和附件至少保存三个月，修改填报也保留原版本，三个月后不会自动删除。')" type="info" :closable="false" class="completion-retention" />
    <div class="completion-filters">
      <el-select v-model="memberUserId" filterable clearable :placeholder="$tr('全部成员')" :aria-label="$tr('筛选成员名字')">
        <el-option v-for="member in memberOptions" :key="member.userId" :label="member.name" :value="member.userId" />
      </el-select>
    </div>
    <el-calendar v-model="calendarDate" class="completion-calendar">
      <template #date-cell="{ data }">
        <div class="calendar-day"><span class="calendar-day-number">{{ Number(data.day.slice(-2)) }}</span>
          <button v-for="entry in calendarEntries[data.day] || []" :key="entry.projectId" type="button" class="calendar-project" :title="entry.projectName" @click.stop="selectedEntry=entry">{{ entry.projectName }}</button>
        </div>
      </template>
    </el-calendar>
    <el-dialog :model-value="!!selectedEntry" :title="selectedEntry ? `${selectedEntry.projectName} · ${selectedEntry.day}` : ''" width="min(960px, 96vw)" append-to-body destroy-on-close @close="selectedEntry=null">
    <el-table :data="selectedEntry?.reports || []" :row-key="row => row.submissionId || `${row.workType}:${row.reportId}`" :empty-text="$tr('暂无符合筛选条件的填报记录')">
      <el-table-column prop="memberName" :label="$tr('成员名字')" min-width="120" />
      <el-table-column prop="projectName" :label="$tr('项目名称')" min-width="180" />
      <el-table-column :label="$tr('工作分类')" min-width="120"><template #default="{row}"><el-tag :type="row.workType==='ROUTINE'?'primary':'success'" effect="plain">{{ row.workType==='ROUTINE'?$tr('持续工作'):$tr('一次性工作') }}</el-tag></template></el-table-column>
      <el-table-column prop="reportDate" :label="$tr('汇报日期')" min-width="120" />
      <el-table-column :label="$tr('汇报详情')" min-width="330">
        <template #default="{row}">
          <b>{{ row.workName }}</b>
          <div class="completion-meta">{{ $tr('提交时间：{0}', [row.submittedTime || row.reportDate]) }}</div>
          <div v-if="row.workType==='ROUTINE' && row.targetMode!=='NONE' && row.actualValue!=null" class="completion-meta">{{ $tr('完成量：{0} {1}', [row.actualValue, row.unit || '']) }}</div>
          <div v-if="row.workType==='TASK'" class="completion-meta">{{ $tr('任务进度：{0}%', [row.progress || 0]) }}</div>
          <p v-if="row.reportDetails" class="completion-details">{{ row.reportDetails }}</p>
          <p v-if="row.issueReason" class="completion-details completion-issue">{{ $tr('未达原因：{0}', [row.issueReason]) }}</p>
          <el-button v-if="row.evidenceUrls" link type="primary" @click="attachmentReport=row">{{ $tr('查看成果凭证') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
      <template #footer><el-button @click="selectedEntry=null">{{ $tr('关闭') }}</el-button></template>
    </el-dialog>
    <el-dialog :model-value="!!attachmentReport" :title="$tr('成果凭证')" width="min(720px, 94vw)" append-to-body destroy-on-close @close="attachmentReport=null">
      <template v-if="attachmentReport"><p>{{ attachmentReport.memberName }} · {{ attachmentReport.projectName }} · {{ attachmentReport.workName }} · {{ attachmentReport.reportDate }}</p><business-file-upload :model-value="attachmentReport.evidenceUrls" :project-id="attachmentReport.projectId" disabled /></template>
      <template #footer><el-button @click="attachmentReport=null">{{ $tr('关闭') }}</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { filterMemberCompletionReports } from '@/utils/memberCompletionReports'
const props=defineProps({ records:{type:Array,default:()=>[]}, projects:{type:Array,default:()=>[]} })
const memberUserId=ref(''),calendarDate=ref(new Date()),selectedEntry=ref(null),attachmentReport=ref(null)
const memberOptions=computed(()=>{
  const members=new Map()
  for(const row of props.records)members.set(String(row.memberUserId),{userId:String(row.memberUserId),name:row.memberName})
  return [...members.values()].sort((a,b)=>String(a.name).localeCompare(String(b.name),'zh-CN'))
})
const filteredReports=computed(()=>filterMemberCompletionReports(props.records,{memberUserId:memberUserId.value}))
const calendarEntries=computed(()=>{
  const days={}
  for(const report of filteredReports.value){
    const day=String(report.reportDate || '').slice(0,10)
    if(!/^\d{4}-\d{2}-\d{2}$/.test(day))continue
    const entries=days[day] ||= []
    let entry=entries.find(item=>String(item.projectId)===String(report.projectId))
    if(!entry){entry={day,projectId:report.projectId,projectName:report.projectName,reports:[]};entries.push(entry)}
    entry.reports.push(report)
  }
  return days
})
</script>

<style scoped>
.panel-head{display:flex;align-items:center;margin-bottom:16px}
.panel-head h2{font-size:18px;margin:0;color:#203a4b}
.panel-head p{font-size:13px;color:#7a8c99;margin:6px 0 0}
.completion-filters{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:16px}
.completion-retention{margin-bottom:16px}
.completion-filters>.el-select{width:210px;max-width:100%}
.completion-filters :deep(.el-date-editor){width:290px;max-width:100%;flex-grow:0}
.completion-meta{font-size:12px;color:#64748b;margin-top:4px}
.completion-details{white-space:pre-wrap;overflow-wrap:anywhere;margin:6px 0;line-height:1.6}
.completion-issue{color:#b45309}
.completion-calendar :deep(.el-calendar__body){padding:0}
.completion-calendar :deep(.el-calendar-day){height:auto;min-height:125px;padding:8px}
.calendar-day{min-height:109px}
.calendar-day-number{display:block;text-align:right;color:#526779;font-size:13px;margin-bottom:8px}
.calendar-project{display:block;width:100%;border:0;border-radius:4px;background:#e3effa;color:#245c86;text-align:left;padding:5px 7px;margin:4px 0;font-size:12px;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.calendar-project:hover{background:#cde2f6}
.completion-calendar :deep(.is-today) .calendar-day-number{color:#fff;background:#409eff;border-radius:20px;width:24px;line-height:24px;margin-left:auto;text-align:center}
.completion-calendar :deep(.prev) .calendar-day-number,.completion-calendar :deep(.next) .calendar-day-number{color:#a8b3bd}
@media(max-width:700px){.completion-calendar :deep(.el-calendar-day){padding:3px;min-height:100px}.calendar-project{padding:4px 2px;font-size:11px}}
@media(max-width:700px){.completion-filters>.el-select{width:100%}.completion-filters :deep(.el-date-editor){width:100%}}
</style>
