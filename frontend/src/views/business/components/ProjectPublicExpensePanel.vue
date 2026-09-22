<template>
  <section class="project-public-expenses" v-loading="loading" :aria-label="$tr(&quot;项目公共费用&quot;)">
    <header class="project-expense-heading">
      <div><h3>{{ $tr("项目公共费用") }}</h3><p>{{ $tr("按月查看公司公共费用分摊及结算记录") }}</p></div>
      <div class="project-expense-controls"><el-date-picker v-model="selectedMonth" type="month" value-format="YYYY-MM" :format="$tr(&quot;YYYY年MM月&quot;)" :clearable="false" :aria-label="$tr(&quot;项目公共费用月份&quot;)" /><el-button icon="Refresh" :loading="loading" @click="refresh">{{ $tr("刷新") }}</el-button></div>
    </header>
    <el-alert v-if="loadFailed" :title="$tr(&quot;项目公共费用暂未加载，相关费用及经营结果暂不可用，请刷新重试。&quot;)" type="error" :closable="false" show-icon />
    <template v-else-if="costs">
      <el-alert v-if="Number(costs.pendingCount) > 0" :title="$tr(&quot;本月公共费用尚未全部确认；已提交的分摊按天计入暂估成本，月结后核实。&quot;)" type="warning" :closable="false" show-icon />
      <el-alert v-else-if="!hasPublishedExpenses" :title="$tr(&quot;本月暂无已下发的公司公共费用。&quot;)" type="info" :closable="false" show-icon />
      <div class="project-expense-metrics">
        <div><span>{{ $tr("月分摊金额") }}</span><b>{{ hasPublishedExpenses ? money(costs.monthAmount) : $tr("待下发") }}<small v-if="hasPublishedExpenses">{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? $tr("本月已计入成本") : $tr("本月已结算费用") }}</span><b>{{ money(costs.dailyRecognition ? costs.accruedMonthAmount : costs.settledMonthAmount) }}<small>{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? $tr("其中：本月暂估") : $tr("累计已结算费用") }}</span><b>{{ money(costs.dailyRecognition ? costs.estimatedMonthAmount : costs.lifetimeAmount) }}<small>{{ displayCurrency }}</small></b></div>
        <div><span>{{ costs.dailyRecognition ? $tr("今日公共费用") : $tr("计入方式") }}</span><b>{{ costs.dailyRecognition ? money(costs.todayAmount) : $tr("历史月结") }}<small v-if="costs.dailyRecognition">{{ displayCurrency }}</small></b><small>{{ costs.dailyRecognition ? $tr("已包含在项目日成本中") : $tr("保留原月结记录") }}</small></div>
      </div>
      <el-table :data="costs.allocations || []" size="small" :empty-text="$tr(&quot;本月暂无项目分摊记录&quot;)">
        <el-table-column prop="companyName" :label="$tr(&quot;公司&quot;)" min-width="140" />
        <el-table-column prop="ownerName" :label="$tr(&quot;分摊负责人&quot;)" min-width="110" /><el-table-column :label="$tr(&quot;费用类型&quot;)" min-width="120"><template #default="{ row }">{{ row.costPool === 'PERSONNEL' ? $tr("公共人员成本") : $tr("日常公共费用") }}</template></el-table-column>
        <el-table-column :label="$tr(&quot;分摊比例&quot;)" min-width="125" align="right"><template #default="{ row }">{{ Number(row.percentage || 0).toFixed(2) }}%</template></el-table-column>
        <el-table-column :label="$tr(&quot;月分摊金额&quot;)" min-width="155" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency || displayCurrency }}</template></el-table-column>
        <el-table-column :label="$tr(&quot;日暂估金额（÷ 21.75）&quot;)" min-width="130" align="right"><template #default="{ row }">{{ row.costPool === 'PERSONNEL' ? money(row.amount / 21.75) : '—' }}</template></el-table-column><el-table-column :label="$tr(&quot;状态&quot;)" min-width="130"><template #default="{ row }"><el-tag :type="row.status === 'SETTLED' ? 'success' : row.status === 'SUBMITTED' ? 'primary' : 'warning'" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      </el-table>
      <el-collapse v-if="costs.dailyCosts?.length" class="daily-cost-details">
        <el-collapse-item :title="$tr(&quot;查看每日分摊明细&quot;)" name="days">
          <el-table :data="costs.dailyCosts" size="small">
            <el-table-column prop="bizDate" :label="$tr(&quot;日期&quot;)" />
            <el-table-column :label="$tr(&quot;公共费用&quot;)"><template #default="{ row }">{{ money(row.amount) }} {{ displayCurrency }}</template></el-table-column>
            <el-table-column :label="$tr(&quot;状态&quot;)"><template #default="{ row }"><el-tag :type="!row.accrued ? 'info' : row.status === 'ESTIMATED' ? 'warning' : 'success'">{{ !row.accrued ? $tr("未到日期，尚未计入") : row.status === 'ESTIMATED' ? $tr("暂估，已计入成本") : $tr("已确认") }}</el-tag></template></el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <template v-if="costs.adjustments?.length">
        <h4>{{ $tr("费用调整记录") }}</h4>
        <el-table :data="costs.adjustments" size="small">
          <el-table-column prop="month" :label="$tr(&quot;调整月份&quot;)" min-width="100" />
          <el-table-column :label="$tr(&quot;调整金额&quot;)" min-width="140" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency || displayCurrency }}</template></el-table-column>
          <el-table-column prop="reason" :label="$tr(&quot;调整原因&quot;)" min-width="220" />
        </el-table>
      </template>
      <p class="project-expense-note">{{ $tr("负责人提交后，公共人员成本按月分摊金额 ÷ 21.75 计入每日暂估金额；日常公共费用按承担期间的自然日暂估。未来日期暂不计入。统一月结时，以实际月分摊金额替换暂估金额并处理尾差，不重复扣费。") }}</p>
    </template>
  </section>
</template>

<script setup name="ProjectPublicExpensePanel">
import { translateText } from '@/locales/translate'

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
const statusLabel = value => ({ DRAFT: translateText("草稿，待提交"), PUBLISHED: translateText("已下发，待月结"), SUBMITTED: translateText("已提交，待月结"), SETTLED: translateText("已月结") }[value] || value || translateText("待确认"))
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
