<template>
  <section class="project-public-expenses" v-loading="loading" aria-label="项目公共费用">
    <header class="project-expense-heading">
      <div><h3>项目公共费用</h3><p>按月查看公司公共费用分摊及结算记录</p></div>
      <div class="project-expense-controls"><el-date-picker v-model="selectedMonth" type="month" value-format="YYYY-MM" format="YYYY年MM月" :clearable="false" aria-label="项目公共费用月份" /><el-button icon="Refresh" :loading="loading" @click="refresh">刷新</el-button></div>
    </header>
    <el-alert v-if="loadFailed" title="项目公共费用暂未加载，相关费用及经营结果暂不可用，请刷新重试。" type="error" :closable="false" show-icon />
    <template v-else-if="costs">
      <el-alert v-if="Number(costs.pendingCount) > 0" title="本月公共费用尚未全部确认；已提交的分摊按天计入暂估成本，月结后核实。" type="warning" :closable="false" show-icon />
      <el-alert v-else-if="!hasPublishedExpenses" title="本月暂无已下发的公司公共费用。" type="info" :closable="false" show-icon />
      <div class="project-expense-metrics">
        <div><span>本月分摊费用</span><b>{{ hasPublishedExpenses ? money(costs.monthAmount) : '待下发' }}<small v-if="hasPublishedExpenses">{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? '本月已计入成本' : '本月已结算费用' }}</span><b>{{ money(costs.dailyRecognition ? costs.accruedMonthAmount : costs.settledMonthAmount) }}<small>{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? '其中：本月暂估' : '累计已结算费用' }}</span><b>{{ money(costs.dailyRecognition ? costs.estimatedMonthAmount : costs.lifetimeAmount) }}<small>{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? '今日公共费用' : '计入方式' }}</span><b>{{ costs.dailyRecognition ? money(costs.todayAmount) : '历史月结' }}<small v-if="costs.dailyRecognition">{{ displayCurrency }}</small></b><small>{{ costs.dailyRecognition ? '已包含在项目日成本中' : '保留原月结记录' }}</small></div>
      </div>
      <el-table :data="costs.allocations || []" size="small" empty-text="本月暂无项目分摊记录">
        <el-table-column prop="companyName" label="公司" min-width="140" />
        <el-table-column prop="ownerName" label="分配负责人" min-width="110" /><el-table-column label="费用类型" min-width="120"><template #default="{ row }">{{ row.costPool === 'PERSONNEL' ? '公共人员成本' : '日常公共费用' }}</template></el-table-column>
        <el-table-column label="项目分配比例" min-width="125" align="right"><template #default="{ row }">{{ Number(row.percentage || 0).toFixed(2) }}%</template></el-table-column>
        <el-table-column label="本月费用" min-width="155" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency || displayCurrency }}</template></el-table-column>
        <el-table-column label="人员日估算" min-width="130" align="right"><template #default="{ row }">{{ row.costPool === 'PERSONNEL' ? money(row.amount / 21.75) : '—' }}</template></el-table-column><el-table-column label="状态" min-width="130"><template #default="{ row }"><el-tag :type="row.status === 'SETTLED' ? 'success' : row.status === 'SUBMITTED' ? 'primary' : 'warning'" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      </el-table>
      <el-collapse v-if="costs.dailyCosts?.length" class="daily-cost-details">
        <el-collapse-item title="查看每日分摊明细" name="days">
          <el-table :data="costs.dailyCosts" size="small">
            <el-table-column prop="bizDate" label="日期" />
            <el-table-column label="公共费用"><template #default="{ row }">{{ money(row.amount) }} {{ displayCurrency }}</template></el-table-column>
            <el-table-column label="状态"><template #default="{ row }"><el-tag :type="!row.accrued ? 'info' : row.status === 'ESTIMATED' ? 'warning' : 'success'">{{ !row.accrued ? '未到日期，尚未计入' : row.status === 'ESTIMATED' ? '暂估，已计入成本' : '已确认' }}</el-tag></template></el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <template v-if="costs.adjustments?.length">
        <h4>费用调整记录</h4>
        <el-table :data="costs.adjustments" size="small">
          <el-table-column prop="month" label="调整月份" min-width="100" />
          <el-table-column label="调整金额" min-width="140" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency || displayCurrency }}</template></el-table-column>
          <el-table-column prop="reason" label="调整原因" min-width="220" />
        </el-table>
      </template>
      <p class="project-expense-note">负责人提交后，公共人员成本按月承担额 ÷ 21.75 计入每日估算；日常公共费用按承担期间的自然日暂估。未来日期暂不计入。统一月结时，以实际月额替换估算并处理尾差，不重复扣费。</p>
    </template>
  </section>
</template>

<script setup name="ProjectPublicExpensePanel">
import { computed, ref, watch } from 'vue'
import { getProjectPublicExpenseCosts } from '@/api/business/publicExpense'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const props = defineProps({ projectId: { type: [Number, String], required: true }, currency: { type: String, default: '' } })
const now = new Date()
const selectedMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const costs = ref(null)
const loading = ref(false)
const loadFailed = ref(false)
let requestSequence = 0
const displayCurrency = computed(() => costs.value?.currency || props.currency)
const hasPublishedExpenses = computed(() => Number(costs.value?.hasPublishedBill) === 1 || !!costs.value?.allocations?.length || Number(costs.value?.monthAmount || 0) !== 0)
const money = value => value === null || value === undefined || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const statusLabel = value => ({ DRAFT: '草稿，待提交', PUBLISHED: '已下发，待月结', SUBMITTED: '已提交，待月结', SETTLED: '已月结' }[value] || value || '待确认')
async function refresh() {
  const sequence = ++requestSequence
  costs.value = null
  loadFailed.value = false
  if (!props.projectId) { loading.value = false; return }
  loading.value = true
  try {
    const response = await getProjectPublicExpenseCosts(props.projectId, selectedMonth.value)
    if (sequence === requestSequence) costs.value = response.data || null
  } catch {
    if (sequence === requestSequence) loadFailed.value = true
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
watch([() => props.projectId, selectedMonth], refresh, { immediate: true })
useBusinessRefreshOnReactivated(refresh)
defineExpose({ refresh })
</script>

<style scoped>
.project-public-expenses{margin:16px 0;padding:18px;border:1px solid #dfe7eb;border-radius:12px;background:#fff}.project-expense-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;margin-bottom:14px}.project-expense-heading h3{margin:0;font-size:17px;color:#243844}.project-expense-heading p{margin:6px 0 0;font-size:12px;color:#81909b}.project-expense-controls{display:flex;gap:8px;align-items:center}.project-expense-controls :deep(.el-date-editor){width:150px}.project-expense-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:16px 0;padding:16px;background:#f5f9fa;border-radius:9px}.project-expense-metrics>div{display:flex;min-width:0;flex-direction:column;gap:7px}.project-expense-metrics span,.project-expense-metrics small{font-size:12px;color:#7d8c96}.project-expense-metrics b{font-size:21px;color:#253b47;overflow-wrap:anywhere}.project-expense-metrics b small{margin-left:6px;font-size:11px;font-weight:400}.project-expense-note{margin:14px 0 0;font-size:12px;color:#82909b;line-height:1.7}.project-public-expenses h4{font-size:14px;margin:18px 0 10px;color:#4a616e}@media(max-width:960px){.project-expense-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.project-expense-heading{flex-direction:column}.project-public-expenses{padding:14px}.project-expense-metrics{gap:20px 12px;padding:13px}.project-expense-metrics b{font-size:18px}}
</style>
