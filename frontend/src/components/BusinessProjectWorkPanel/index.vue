<template>
  <section v-loading="loading" class="member-cost-panel">
    <div class="heading"><div><h3>人员工作日成本</h3><p>按成员参与日期和工作日历自动计算，休息日不计费。每日使用当日有效日成本；已核算日期保留原依据。</p></div><el-button icon="Refresh" @click="load">刷新</el-button></div>
    <el-alert v-if="data.overdue" title="项目已超过计划结束日，仍参与的成员继续按工作日计费，请更新项目计划。" type="warning" :closable="false" show-icon />
    <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" @change="load" />
    <el-alert v-if="data.pendingCount" title="部分日期缺少有效成本或工作日历，请完善用人成本后刷新。" type="warning" :closable="false" show-icon />
    <p>本期累计：<b>{{ data.totalAmount == null ? '待完善成本' : money(data.totalAmount) + ' ' + (data.currency || '') }}</b><span class="hint">（截至今天）</span></p>
    <el-table :data="data.rows || []" empty-text="所选期间没有应计费的成员工作日">
      <el-table-column prop="userName" label="成员" min-width="100" />
      <el-table-column label="计费日期" min-width="215"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
      <el-table-column prop="workingDays" label="工作日数" width="110" />
      <el-table-column label="人员成本" min-width="135"><template #default="{ row }">{{ row.amount == null ? '待完善' : money(row.amount) + ' ' + data.currency }}</template></el-table-column>
      <el-table-column label="说明" min-width="160"><template #default="{ row }">{{ row.issues?.join('；') || '已按工作日自动计算' }}</template></el-table-column>
    </el-table>
  </section>
</template>
<script setup>
import { ref, watch } from 'vue'
import { getProjectWork } from '@/api/business/projectWork'
import { parseTime } from '@/utils/ruoyi'
const props = defineProps({ projectId: [Number, String] })
const now = new Date()
const dates = ref([parseTime(new Date(now.getFullYear(), now.getMonth(), 1), '{y}-{m}-{d}'), parseTime(now, '{y}-{m}-{d}')])
const data = ref({}), loading = ref(false)
const money = value => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
let sequence = 0
async function load() {
  const current = ++sequence
  if (!props.projectId || !dates.value?.length) return
  loading.value = true
  try {
    const response = await getProjectWork(props.projectId, { dateFrom: dates.value[0], dateTo: dates.value[1] })
    if (current === sequence) data.value = response.data || {}
  } finally { if (current === sequence) loading.value = false }
}
watch(() => props.projectId, () => { data.value = {}; load() }, { immediate: true })
</script>
<style scoped>
.heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.heading h3{margin:0}.heading p,.hint{color:#8492a3}.member-cost-panel :deep(.el-alert){margin-top:16px}.member-cost-panel :deep(.el-date-editor){max-width:100%}
</style>
