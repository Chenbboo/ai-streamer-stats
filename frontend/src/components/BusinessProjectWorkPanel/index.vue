<template>
  <section v-loading="loading" class="member-cost-panel">
    <template v-if="showCosts">
    <div class="heading"><div><h3>{{ $tr("人员工作日成本") }}</h3><p>{{ $tr("日成本按月度用人成本除以地区标准天数计算（国内21.75天、越南26天），再按计费工作日和当日投入比例累计。") }}</p></div><div class="heading-actions"><el-button v-if="canManage" type="primary" @click="openAllocation">{{ $tr("申请调整投入") }}</el-button><el-button icon="Refresh" @click="load">{{ $tr("刷新") }}</el-button></div></div>
    <el-alert v-if="data.overdue" :title="$tr(&quot;项目已超过计划结束日，仍参与的成员继续按工作日计费，请更新项目计划。&quot;)" type="warning" :closable="false" show-icon />
    <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" :start-placeholder="$tr(&quot;开始日期&quot;)" :end-placeholder="$tr(&quot;结束日期&quot;)" :clearable="false" @change="load" />
    <p class="history-hint">{{ $tr("此处按所选日期展示已发生的成本。人员移除后，退出前的历史成本仍会保留；移除当天是否计费以移除时的选择为准。") }}</p>
    <el-alert v-if="data.pendingCount" :title="$tr(&quot;部分人员成本待完善，请查看下方说明，确认投入分配或完善成本、工作日历。&quot;)" type="warning" :closable="false" show-icon />
    <p>{{ $tr("本期累计：") }}<b>{{ data.totalAmount == null ? $tr("待完善成本") : money(data.totalAmount) + ' ' + (data.currency || '') }}</b><span class="hint">{{ $tr("（截至今天）") }}</span></p>
    <el-table :data="pagedCostRows" :empty-text="$tr(&quot;所选期间没有应计费的成员工作日&quot;)">
      <el-table-column prop="userName" :label="$tr(&quot;成员&quot;)" min-width="100" />
      <el-table-column :label="$tr(&quot;计费日期&quot;)" min-width="215"><template #default="{ row }">{{ $tr("{0} 至 {1}", [row.startDate, row.endDate]) }}</template></el-table-column>
      <el-table-column prop="workingDays" :label="$tr(&quot;工作日数&quot;)" width="110" />
      <el-table-column :label="$tr(&quot;人员成本&quot;)" min-width="135"><template #default="{ row }">{{ row.amount == null ? $tr("待完善") : money(row.amount) + ' ' + data.currency }}</template></el-table-column>
      <el-table-column :label="$tr(&quot;说明&quot;)" min-width="180"><template #default="{ row }">{{ row.issues?.join('；') || $tr("已按工作日和投入权重计算") }}</template></el-table-column>
    </el-table>
    <div v-if="costRows.length > costPageSize" class="cost-pagination">
      <el-pagination v-model:current-page="costPage" :page-size="costPageSize" :total="costRows.length" layout="total, prev, pager, next" small background />
    </div>
    </template>

    <BusinessAllocationSchedule ref="allocationEditor" :project-id="projectId" :members="members" :can-manage="canManage" @changed="allocationChanged" />
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { getProjectWork } from '@/api/business/projectWork'
import BusinessAllocationSchedule from '@/components/BusinessAllocationSchedule/index.vue'
import { parseTime } from '@/utils/ruoyi'
const props = defineProps({ projectId: [Number, String], members: { type: Array, default: () => [] }, canManage: Boolean, showCosts: { type: Boolean, default: true } })
const emit = defineEmits(['changed'])
const now = new Date()
const dates = ref([parseTime(new Date(now.getFullYear(), now.getMonth(), 1), '{y}-{m}-{d}'), parseTime(now, '{y}-{m}-{d}')])
const data = ref({}), loading = ref(false), allocationEditor = ref()
const costPage = ref(1), costPageSize = 5
const costRows = computed(() => data.value.rows || [])
const pagedCostRows = computed(() => costRows.value.slice((costPage.value - 1) * costPageSize, costPage.value * costPageSize))
const money = value => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
let sequence = 0
async function load() {
  const current = ++sequence
  if (!props.showCosts || !props.projectId || !dates.value?.length) return
  loading.value = true
  try {
    const response = await getProjectWork(props.projectId, { dateFrom: dates.value[0], dateTo: dates.value[1] })
    if (current === sequence) { data.value = response.data || {}; costPage.value = 1 }
  } finally { if (current === sequence) loading.value = false }
}
async function openAllocation(userId, effectiveDate) { await allocationEditor.value?.open(userId, effectiveDate) }
async function allocationChanged() { await load(); emit('changed') }
defineExpose({openAllocation,reload:load})
watch(() => props.projectId, () => { data.value = {}; load() }, { immediate: true })
</script>
<style scoped>
.heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.heading h3{margin:0}.heading p,.hint{color:#8492a3}.heading-actions{display:flex;gap:8px}.member-cost-panel :deep(.el-alert){margin-top:16px}.member-cost-panel :deep(.el-date-editor){max-width:100%}.cost-pagination{display:flex;justify-content:flex-end;margin-top:14px}
@media(max-width:640px){.heading{align-items:flex-start;flex-direction:column}.heading-actions{width:100%;flex-wrap:wrap}.cost-pagination{justify-content:center}}
</style>
