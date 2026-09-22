<template>
  <section class="snapshot">
    <template v-if="snapshot">
      <p v-if="archiveDetails">{{ $tr("归档时间：{0}", [parseTime(snapshot.capturedAt) || $tr("未记录")]) }}</p>
      <template v-if="showTasks">
        <h4>{{ $tr("一次性任务 · 已完成 {0} / {1}（{2}%）", [completion.done, completion.total, completion.percent]) }}</h4>
        <el-table :data="snapshot.tasks || []" size="small" :empty-text="$tr(&quot;暂无一次性任务&quot;)" max-height="300">
          <el-table-column prop="taskName" :label="$tr(&quot;任务&quot;)" min-width="140" />
          <el-table-column prop="assigneeName" :label="$tr(&quot;执行人&quot;)" width="90" />
          <el-table-column :label="$tr(&quot;状态&quot;)" width="90"><template #default="{row}">{{ status[row.status] || row.status }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;进度&quot;)" width="80"><template #default="{row}">{{ row.progress ?? 0 }}%</template></el-table-column>
          <el-table-column :label="$tr(&quot;最新成果&quot;)" min-width="160"><template #default="{row}">{{ latestTask(row.taskId)?.completionSummary || $tr("暂无填报") }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;成果凭证&quot;)" min-width="200"><template #default="{row}"><BusinessFileUpload v-if="latestTask(row.taskId)?.evidenceUrls" :model-value="latestTask(row.taskId).evidenceUrls" :project-id="snapshot.projectId" disabled :drag="false" :is-show-tip="false" inline-document-preview /><span v-else>{{ $tr("未上传") }}</span></template></el-table-column>
        </el-table>
      </template>
      <template v-if="archiveDetails">
        <h4>{{ $tr("关联任务填报记录") }}</h4>
        <el-table :data="snapshot.taskReports || []" size="small" :empty-text="$tr(&quot;暂无关联任务填报&quot;)" max-height="300">
          <el-table-column prop="taskId" :label="$tr(&quot;任务ID&quot;)" width="85"/>
          <el-table-column :label="$tr(&quot;填报日期&quot;)" width="110"><template #default="{row}">{{ parseTime(row.bizDate, '{y}-{m}-{d}') }}</template></el-table-column>
          <el-table-column prop="submittedUserName" :label="$tr(&quot;填报人&quot;)" width="90"/>
          <el-table-column :label="$tr(&quot;进度&quot;)" width="80"><template #default="{row}">{{ row.progress }}%</template></el-table-column>
          <el-table-column prop="completionSummary" :label="$tr(&quot;完成成果&quot;)" min-width="180"/>
          <el-table-column :label="$tr(&quot;成果凭证&quot;)" min-width="200"><template #default="{row}"><BusinessFileUpload v-if="row.evidenceUrls" :model-value="row.evidenceUrls" :project-id="snapshot.projectId" disabled :drag="false" :is-show-tip="false" inline-document-preview /><span v-else>{{ $tr("未上传") }}</span></template></el-table-column>
        </el-table>
        <h4>{{ $tr("关联工作执行区间") }}</h4>
        <el-table :data="snapshot.executionPeriods || []" size="small" :empty-text="$tr(&quot;暂无执行区间&quot;)" max-height="300">
          <el-table-column :label="$tr(&quot;工作&quot;)" min-width="140"><template #default="{row}">{{ row.workType === 'TASK' ? $tr("一次性任务") : $tr("持续工作") }} #{{ row.workId }}</template></el-table-column>
          <el-table-column prop="assigneeName" :label="$tr(&quot;执行人&quot;)" width="90"/>
          <el-table-column :label="$tr(&quot;执行开始&quot;)" width="110"><template #default="{row}">{{ parseTime(row.startDate, '{y}-{m}-{d}') }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;执行结束&quot;)" width="110"><template #default="{row}">{{ parseTime(row.endDate, '{y}-{m}-{d}') || $tr("持续执行") }}</template></el-table-column>
        </el-table>
      </template>
      <template v-if="showRoutines">
        <h4>{{ $tr("持续工作 · 汇报当日执行情况") }}</h4>
        <el-table :data="snapshot.routines || []" size="small" :empty-text="$tr(&quot;暂无持续工作&quot;)" max-height="300">
          <el-table-column prop="routineName" :label="$tr(&quot;工作&quot;)" min-width="140" />
          <el-table-column prop="assigneeName" :label="$tr(&quot;执行人&quot;)" width="90" />
          <el-table-column :label="$tr(&quot;当日完成 / 目标&quot;)" min-width="130"><template #default="{row}">{{ row.todayActual ?? $tr("未填报") }} / {{ row.todayTarget ?? $tr("未下达") }} {{ $tr(row.unit) }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;累计完成&quot;)" width="100"><template #default="{row}">{{ row.cumulativeActual ?? 0 }} {{ $tr(row.unit) }}</template></el-table-column>
          <el-table-column prop="todaySummary" :label="$tr(&quot;执行说明&quot;)" min-width="160" />
          <el-table-column prop="todayIssueReason" :label="$tr(&quot;问题&quot;)" min-width="140" />
          <el-table-column :label="$tr(&quot;成果凭证&quot;)" min-width="200"><template #default="{row}"><BusinessFileUpload v-if="row.todayEvidenceUrls" :model-value="row.todayEvidenceUrls" :project-id="snapshot.projectId" disabled :drag="false" :is-show-tip="false" inline-document-preview /><span v-else>{{ $tr("未上传") }}</span></template></el-table-column>
        </el-table>
      </template>
    </template>
    <el-alert v-else :title="$tr(&quot;此为历史旧版记录，未保存关联数据快照。&quot;)" type="info" :closable="false" />
  </section>
</template>
<script setup>
import { translateText } from '@/locales/translate'

import { computed } from 'vue'
import { taskCompletion } from '@/utils/projectProgress'
import { parseTime } from '@/utils/ruoyi'
import BusinessFileUpload from '@/components/BusinessFileUpload/index.vue'
const props = defineProps({ snapshot: Object, archiveDetails: Boolean, showTasks: { type: Boolean, default: true }, showRoutines: { type: Boolean, default: true } })
const completion = computed(() => taskCompletion(props.snapshot?.tasks || []))
const status = { TODO: translateText("待开始"), DOING: translateText("进行中"), BLOCKED: translateText("阻塞"), DONE: translateText("已完成"), CANCELED: translateText("已取消") }
const latestTask = id => (props.snapshot?.taskReports || []).filter(r => String(r.taskId) === String(id)).at(-1)
</script>
<style scoped>.snapshot h4{margin:16px 0 10px;color:#536277;font-size:13px}</style>
