<template>
  <section class="snapshot">
    <template v-if="snapshot">
      <p v-if="archiveDetails">归档时间：{{ parseTime(snapshot.capturedAt) || '未记录' }} · 当时进度权重：{{ snapshot.progressWeight ?? 1 }}</p>
      <template v-if="showTasks">
        <h4>一次性任务 · 已完成 {{ completion.done }} / {{ completion.total }}（{{ completion.percent }}%）</h4>
        <el-table :data="snapshot.tasks || []" size="small" empty-text="暂无一次性任务" max-height="300">
          <el-table-column prop="taskName" label="任务" min-width="140" />
          <el-table-column prop="assigneeName" label="执行人" width="90" />
          <el-table-column label="状态" width="90"><template #default="{row}">{{ status[row.status] || row.status }}</template></el-table-column>
          <el-table-column label="进度" width="80"><template #default="{row}">{{ row.progress ?? 0 }}%</template></el-table-column>
          <el-table-column label="最新成果" min-width="160"><template #default="{row}">{{ latestTask(row.taskId)?.completionSummary || '暂无填报' }}</template></el-table-column>
        </el-table>
      </template>
      <template v-if="archiveDetails">
        <h4>关联任务填报记录</h4>
        <el-table :data="snapshot.taskReports || []" size="small" empty-text="暂无关联任务填报" max-height="300">
          <el-table-column prop="taskId" label="任务ID" width="85"/>
          <el-table-column label="填报日期" width="110"><template #default="{row}">{{ parseTime(row.bizDate, '{y}-{m}-{d}') }}</template></el-table-column>
          <el-table-column prop="submittedUserName" label="填报人" width="90"/>
          <el-table-column label="进度" width="80"><template #default="{row}">{{ row.progress }}%</template></el-table-column>
          <el-table-column prop="completionSummary" label="完成成果" min-width="180"/>
        </el-table>
        <h4>关联工作执行区间</h4>
        <el-table :data="snapshot.executionPeriods || []" size="small" empty-text="暂无执行区间" max-height="300">
          <el-table-column label="工作" min-width="140"><template #default="{row}">{{ row.workType === 'TASK' ? '一次性任务' : '持续工作' }} #{{ row.workId }}</template></el-table-column>
          <el-table-column prop="assigneeName" label="执行人" width="90"/>
          <el-table-column label="执行开始" width="110"><template #default="{row}">{{ parseTime(row.startDate, '{y}-{m}-{d}') }}</template></el-table-column>
          <el-table-column label="执行结束" width="110"><template #default="{row}">{{ parseTime(row.endDate, '{y}-{m}-{d}') || '持续执行' }}</template></el-table-column>
        </el-table>
      </template>
      <template v-if="showRoutines">
        <h4>持续工作 · 汇报当日执行情况</h4>
        <el-table :data="snapshot.routines || []" size="small" empty-text="暂无持续工作" max-height="300">
          <el-table-column prop="routineName" label="工作" min-width="140" />
          <el-table-column prop="assigneeName" label="执行人" width="90" />
          <el-table-column label="当日完成 / 目标" min-width="130"><template #default="{row}">{{ row.todayActual ?? '未填报' }} / {{ row.todayTarget ?? '未下达' }} {{ row.unit }}</template></el-table-column>
          <el-table-column label="累计完成" width="100"><template #default="{row}">{{ row.cumulativeActual ?? 0 }} {{ row.unit }}</template></el-table-column>
          <el-table-column prop="todaySummary" label="执行说明" min-width="160" />
          <el-table-column prop="todayIssueReason" label="问题" min-width="140" />
        </el-table>
      </template>
    </template>
    <el-alert v-else title="此为历史旧版记录，未保存关联数据快照。" type="info" :closable="false" />
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { taskCompletion } from '@/utils/projectProgress'
import { parseTime } from '@/utils/ruoyi'
const props = defineProps({ snapshot: Object, archiveDetails: Boolean, showTasks: { type: Boolean, default: true }, showRoutines: { type: Boolean, default: true } })
const completion = computed(() => taskCompletion(props.snapshot?.tasks || []))
const status = { TODO: '待开始', DOING: '进行中', BLOCKED: '阻塞', DONE: '已完成', CANCELED: '已取消' }
const latestTask = id => (props.snapshot?.taskReports || []).filter(r => String(r.taskId) === String(id)).at(-1)
</script>
<style scoped>.snapshot h4{margin:16px 0 10px;color:#536277;font-size:13px}</style>
