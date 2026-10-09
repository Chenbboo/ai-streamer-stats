<template>
  <el-dialog v-model="visible" class="estimate-source-dialog" :title="$tr('{0}金额来源', [label])" width="min(960px, 96vw)" append-to-body destroy-on-close>
    <template v-if="budget">
      <div class="source-summary">
        <div><span>{{ label }}</span><strong :class="{ negative: Number(budget[metric]) < 0 }">{{ money(budget[metric]) }}</strong></div>
        <p>{{ budget.month || $tr('本次测算期间') }} · {{ budget.startDate || '—' }} — {{ budget.endDate || '—' }}</p>
      </div>
      <div v-if="metric === 'plannedTotalCost' || metric === 'profit'" class="source-formula">
        <button v-if="metric === 'profit'" type="button" @click="metric = 'revenueAmount'"><span>{{ $tr('预计收入') }}</span><b>{{ money(budget.revenueAmount) }}</b></button>
        <span v-if="metric === 'profit'" class="operator">−</span>
        <button type="button" @click="metric = 'plannedBusinessAmount'"><span>{{ $tr('业务支出') }}</span><b>{{ money(budget.plannedBusinessAmount) }}</b></button>
        <span class="operator">{{ metric === 'profit' ? '−' : '+' }}</span>
        <button type="button" @click="metric = 'personnelAmount'"><span>{{ $tr('人员成本') }}</span><b>{{ money(budget.personnelAmount) }}</b></button>
        <span class="operator">=</span>
        <div><span>{{ label }}</span><b>{{ money(budget[metric]) }}</b></div>
      </div>
      <el-alert v-if="budget[metric] == null" :title="$tr('本项金额待完善，以下明细不代表完整测算结果')" type="warning" :closable="false" show-icon />
      <section v-for="section in cashSections" :key="section.key" class="source-section">
        <div class="source-heading"><h3>{{ section.label }}</h3><b>{{ money(budget[section.totalKey]) }}</b></div>
        <p class="source-note">{{ section.key === 'revenueSources' ? $tr('仅计入基础情景收入和主项目拨款；按测算期间折算。') : $tr('来自支出计划，按测算期间折算；业务预算上限不作为支出金额。') }}</p>
        <el-alert v-if="!Array.isArray(budget[section.key])" :title="$tr('该测算结果未保存来源明细，请重新测算后查看。')" type="info" :closable="false" />
        <el-table v-else :data="budget[section.key]" size="small" :empty-text="$tr('本期没有计入的计划明细')">
          <el-table-column :label="$tr('来源项目')" min-width="170"><template #default="{ row }"><b>{{ row.sourceType === 'PARENT_FUNDING' ? $tr('主项目拨款') : row.itemName || $tr('未填写') }}</b><small v-if="row.note" class="source-note">{{ row.note }}</small><small v-if="Number(row.roundingAdjustment)" class="source-note">{{ $tr('含分月舍入调整 {0}', [money(row.roundingAdjustment)]) }}</small></template></el-table-column>
          <el-table-column :label="$tr('发生方式')" min-width="95"><template #default="{ row }">{{ frequency(row.occurrenceType) }}</template></el-table-column>
          <el-table-column :label="$tr('计划原金额')" min-width="135" align="right"><template #default="{ row }">{{ money(row.inputAmount) }}</template></el-table-column>
          <el-table-column :label="$tr('本期计入期间')" min-width="185"><template #default="{ row }">{{ period(row) }}</template></el-table-column>
          <el-table-column :label="$tr('本期计入金额')" min-width="145" align="right"><template #default="{ row }"><b>{{ money(row.amount) }}</b></template></el-table-column>
        </el-table>
      </section>
      <section v-if="showPersonnel" class="source-section">
        <div class="source-heading"><h3>{{ $tr('人员成本') }}</h3><b>{{ money(budget.personnelAmount) }}</b></div>
        <p v-if="['PROJECT_MONTH_SHARE_DAY_V3', 'CALENDAR_MONTH_V1'].includes(budget.personnelCostRule)" class="source-note">{{ $tr('月成本按当月实际工作日和当日投入比例分摊，整月100%投入等于设置的月成本；展开人员可查看分段金额。') }}</p>
        <p v-else class="source-note">{{ $tr('日成本按月度用人成本除以地区标准天数计算（国内21.75天、越南26天），再按计费工作日和当日投入比例累计；展开人员可查看分段金额。') }}</p>
        <el-table :data="budget.staffingStatus || []" size="small" :empty-text="$tr('本期没有人员成本明细')">
          <el-table-column type="expand" width="40"><template #default="{ row }">
            <div class="personnel-breakdown">
              <el-alert v-if="row.issues?.length" :title="row.issues.map(issue => $tr(issue)).join('；')" type="warning" :closable="false" />
              <el-table v-if="row.allocationPeriods?.length" :data="row.allocationPeriods" size="small">
                <el-table-column :label="$tr('计费日期')" min-width="180"><template #default="{ row: segment }">{{ period(segment) }}</template></el-table-column>
                <el-table-column :label="$tr('计费工作日')" min-width="100"><template #default="{ row: segment }">{{ segment.workingDays }} {{ $tr('天') }}</template></el-table-column>
                <el-table-column :label="$tr('项目投入比例')" min-width="110"><template #default="{ row: segment }">{{ Number(segment.allocationPercent).toFixed(2) }}%</template></el-table-column>
                <el-table-column :label="$tr('本期计入金额')" min-width="145" align="right"><template #default="{ row: segment }">{{ money(segment.amount) }}</template></el-table-column>
              </el-table>
              <p v-else class="source-note">{{ $tr('没有可展示的计费分段') }}</p>
              <p v-if="row.status === 'PENDING'" class="source-note">{{ $tr('以上仅展示已完成测算的部分，待完善项不计作完整金额。') }}</p>
            </div>
          </template></el-table-column>
          <el-table-column :label="$tr('人员')" min-width="120"><template #default="{ row }">{{ row.userName || $tr('人员编号 {0}', [row.userId || '—']) }}</template></el-table-column>
          <el-table-column :label="$tr('参与期间')" min-width="180"><template #default="{ row }">{{ period(row) }}</template></el-table-column>
          <el-table-column :label="$tr('计费工作日')" min-width="100"><template #default="{ row }">{{ row.workingDays == null ? '—' : `${row.workingDays} ${$tr('天')}` }}</template></el-table-column>
          <el-table-column :label="$tr('本期计入金额')" min-width="145" align="right"><template #default="{ row }"><b>{{ money(row.amount) }}</b></template></el-table-column>
          <el-table-column :label="$tr('状态')" min-width="100"><template #default="{ row }"><el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">{{ row.status === 'PENDING' ? $tr('待完善') : $tr('已完成') }}</el-tag></template></el-table-column>
        </el-table>
      </section>
      <p v-if="cashSections.length" class="source-note source-rule">{{ budget.cashCostRule === 'FIXED_30_DAY_V1' ? $tr('此历史测算按月费用以 30 天折算；按周费用以 7 天折算，按日费用按覆盖天数累计。') : $tr('一次性按发生期间计入；按月费用按自然月覆盖天数折算，按周费用按 7 天折算，按日费用按覆盖天数累计。') }}</p>
    </template>
    <template #footer><el-button @click="visible = false">{{ $tr('关闭') }}</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { translateText } from '@/locales/translate'
const visible = ref(false), budget = ref(null), metric = ref('revenueAmount')
const labels = computed(() => ({ revenueAmount: translateText('预计收入'), plannedBusinessAmount: translateText('业务支出'), personnelAmount: translateText('人员成本'), plannedTotalCost: translateText('预计总成本'), profit: translateText('预计利润') }))
const label = computed(() => labels.value[metric.value])
const showPersonnel = computed(() => ['personnelAmount', 'plannedTotalCost', 'profit'].includes(metric.value))
const cashSections = computed(() => [
  { key: 'revenueSources', totalKey: 'revenueAmount', label: translateText('收入计划'), metrics: ['revenueAmount', 'profit'] },
  { key: 'expenseSources', totalKey: 'plannedBusinessAmount', label: translateText('支出计划'), metrics: ['plannedBusinessAmount', 'plannedTotalCost', 'profit'] }
].filter(section => section.metrics.includes(metric.value)))
const money = value => value == null ? translateText('待完善') : `${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${budget.value?.currency || 'CNY'}`
const frequency = value => ({ ONE_TIME: translateText('一次性'), DAILY: translateText('按日'), WEEKLY: translateText('按周'), MONTHLY: translateText('按月') }[value] || value || '—')
const period = row => row.startDate || row.plannedDate ? `${row.startDate || row.plannedDate}${row.endDate && row.endDate !== (row.startDate || row.plannedDate) ? ` — ${row.endDate}` : ''}` : '—'
function open(key, source) { metric.value = key; budget.value = JSON.parse(JSON.stringify(source)); visible.value = true }
defineExpose({ open, close: () => { visible.value = false } })
</script>

<style scoped>
.source-summary { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 20px; border-radius: 12px; background: var(--el-color-primary-light-9); }
.source-summary span { display: block; color: var(--el-text-color-secondary); font-size: 13px; }
.source-summary strong { display: block; margin-top: 8px; font-size: 26px; color: var(--el-color-primary); font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.source-summary .negative { color: var(--el-color-danger); }
.source-summary p { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.8; }
.source-formula { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 18px 0; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; }
.source-formula button { border: 0; background: var(--el-fill-color-light); border-radius: 8px; cursor: pointer; padding: 12px; font: inherit; text-align: left; }
.source-formula button:hover { background: var(--el-color-primary-light-9); }
.source-formula span { display: block; font-size: 12px; color: var(--el-text-color-secondary); }
.source-formula b { display: block; margin-top: 6px; font-size: 14px; color: var(--el-text-color-primary); }
.source-formula .operator { font-size: 20px; }
.source-section { margin-top: 22px; }
.source-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 8px; }
.source-heading h3 { margin: 0; font-size: 15px; }
.source-heading b { font-variant-numeric: tabular-nums; }
.source-note { display: block; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.8; margin: 6px 0; overflow-wrap: anywhere; }
.source-rule { margin-top: 20px; }
.personnel-breakdown { padding: 12px 20px; background: var(--el-fill-color-lighter); }
@media (max-width: 600px) { .source-summary { flex-direction: column; align-items: flex-start; padding: 16px; } .source-summary strong { font-size: 22px; } .source-heading { flex-wrap: wrap; } .personnel-breakdown { padding: 12px; } }
</style>

<style>
.estimate-source-dialog .el-dialog__body { max-height: 68vh; overflow: auto; }
</style>
