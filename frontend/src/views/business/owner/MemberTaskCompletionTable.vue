<template>
  <section class="panel member-completion-panel">
    <div class="panel-head"><div><h2>{{ $tr('成员汇报详情') }}</h2><p>{{ $tr('查看持续工作和一次性工作的历史填报，共 {0} 条。', [filteredReports.length]) }}</p></div></div>
    <el-alert :title="$tr('每次提交的内容和附件至少保存三个月，修改填报也保留原版本，三个月后不会自动删除。')" type="info" :closable="false" class="completion-retention" />
    <div class="completion-filters">
      <el-select v-model="memberUserId" filterable clearable :placeholder="$tr('全部成员')" :aria-label="$tr('筛选成员名字')">
        <el-option v-for="member in memberOptions" :key="member.userId" :label="member.name" :value="member.userId" />
      </el-select>
      <el-select v-model="projectId" filterable clearable :placeholder="$tr('全部项目')" :aria-label="$tr('筛选项目名称')">
        <el-option v-for="project in projects" :key="project.projectId" :label="project.projectName" :value="String(project.projectId)" />
      </el-select>
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" :start-placeholder="$tr('开始日期')" :end-placeholder="$tr('结束日期')" :aria-label="$tr('筛选汇报日期')" />
      <el-button @click="resetFilters">{{ $tr('重置筛选') }}</el-button>
    </div>
    <el-table :data="pagedReports" :row-key="row => row.submissionId || `${row.workType}:${row.reportId}`" :empty-text="$tr('暂无符合筛选条件的填报记录')">
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
    <el-pagination v-if="filteredReports.length" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50]" :total="filteredReports.length" layout="total, sizes, prev, pager, next" class="completion-pagination" />
    <el-dialog :model-value="!!attachmentReport" :title="$tr('成果凭证')" width="min(720px, 94vw)" append-to-body destroy-on-close @close="attachmentReport=null">
      <template v-if="attachmentReport"><p>{{ attachmentReport.memberName }} · {{ attachmentReport.projectName }} · {{ attachmentReport.workName }} · {{ attachmentReport.reportDate }}</p><business-file-upload :model-value="attachmentReport.evidenceUrls" :project-id="attachmentReport.projectId" disabled /></template>
      <template #footer><el-button @click="attachmentReport=null">{{ $tr('关闭') }}</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { filterMemberCompletionReports } from '@/utils/memberCompletionReports'
const props=defineProps({ records:{type:Array,default:()=>[]}, projects:{type:Array,default:()=>[]} })
const memberUserId=ref(''),projectId=ref(''),dates=ref(null),page=ref(1),pageSize=ref(10),attachmentReport=ref(null)
const memberOptions=computed(()=>{
  const members=new Map()
  for(const row of props.records)members.set(String(row.memberUserId),{userId:String(row.memberUserId),name:row.memberName})
  return [...members.values()].sort((a,b)=>String(a.name).localeCompare(String(b.name),'zh-CN'))
})
const filteredReports=computed(()=>filterMemberCompletionReports(props.records,{memberUserId:memberUserId.value,projectId:projectId.value,dates:dates.value}))
const pagedReports=computed(()=>filteredReports.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value))
watch([memberUserId,projectId,dates,pageSize,()=>props.records],()=>{page.value=1})
watch(()=>props.projects,projects=>{if(projectId.value&&!projects.some(project=>String(project.projectId)===projectId.value))projectId.value=''})
function resetFilters(){memberUserId.value='';projectId.value='';dates.value=null}
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
.completion-pagination{margin-top:16px;overflow-x:auto}
@media(max-width:700px){.completion-filters>.el-select{width:100%}.completion-filters :deep(.el-date-editor){width:100%}}
</style>
