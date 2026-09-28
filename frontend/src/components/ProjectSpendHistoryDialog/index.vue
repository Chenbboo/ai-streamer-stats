<template>
  <el-dialog :model-value="modelValue" :title="$tr('{0} · 每日花费明细', [projectName || ''])" width="min(1200px, 96vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <div class="spend-history-toolbar">
      <el-radio-group v-model="filterMode">
        <el-radio-button value="month">{{ $tr('按月份') }}</el-radio-button>
        <el-radio-button value="date">{{ $tr('按日期') }}</el-radio-button>
      </el-radio-group>
      <el-date-picker v-if="filterMode === 'month'" v-model="month" type="month" value-format="YYYY-MM" :clearable="false" :disabled-date="disabledDate" :aria-label="$tr('选择月份')" />
      <el-date-picker v-else v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="disabledDate" :aria-label="$tr('选择日期')" />
      <el-button :loading="loading" @click="loadHistory">{{ $tr('查询') }}</el-button>
    </div>
    <p class="spend-history-note">{{ $tr('与填写花费卡片统计口径一致；冲销按正负流水抵减，待计价人员成本未计入合计。') }}</p>
    <el-alert v-if="failed" :title="$tr('花费明细加载失败，请重试')" type="error" :closable="false" show-icon />
    <div v-else v-loading="loading">
      <div class="spend-history-summary">
        <strong>{{ $tr('合计') }}：{{ money(history.totals?.amount) }} {{ currency }}</strong>
        <span v-for="column in columns" :key="column.key">{{ $tr(column.label) }}：{{ money(history.totals?.[column.key]) }} {{ currency }}</span>
        <el-tag v-if="history.totals?.pendingPersonnelCount" type="warning">{{ $tr('{0} 项人员成本待计价', [history.totals.pendingPersonnelCount]) }}</el-tag>
      </div>
      <p class="spend-history-period" v-if="history.dateFrom">{{ history.dateFrom }} ~ {{ history.dateTo }} · {{ currency }} · {{ $tr('展开日期查看明细') }}</p>
      <el-table :data="history.rows || []" row-key="bizDate" max-height="520" border :empty-text="$tr('暂无花费记录')">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="spend-history-details">
              <h4>{{ $tr('人员成本明细') }}</h4>
              <el-table :data="row.personnelItems || []" size="small" :empty-text="$tr('当日无人员成本记录')">
                <el-table-column prop="name" :label="$tr('人员')" min-width="110" />
                <el-table-column :label="$tr('金额')" min-width="140"><template #default="{ row: item }">{{ item.pricingStatus === 'PRICED' && item.amount != null ? money(item.amount) + ' ' + currency : $tr('待计价') }}</template></el-table-column>
                <el-table-column :label="$tr('计算说明')" min-width="320"><template #default="{ row: item }">{{ spendPersonnelDescription(item.calculationDetail, currency, translateText, money) }}</template></el-table-column>
              </el-table>
              <h4>{{ $tr('支出流水明细') }}</h4>
              <el-table :data="expenseItems(row)" size="small" :empty-text="$tr('当日无支出流水')">
                <el-table-column :label="$tr('支出类别')" min-width="150"><template #default="{ row: item }">{{ $tr(item.categoryName || '其他支出') }}</template></el-table-column>
                <el-table-column :label="$tr('金额')" min-width="140"><template #default="{ row: item }">{{ money(item.amount) }} {{ item.currency || currency }}</template></el-table-column>
                <el-table-column :label="$tr('说明')" min-width="270"><template #default="{ row: item }"><span class="spend-history-description">{{ item.description || '—' }}</span><small v-if="item.counterparty">{{ item.counterparty }}</small></template></el-table-column>
                <el-table-column :label="$tr('状态')" width="110"><template #default="{ row: item }">{{ item.status === 'REVERSED' ? $tr('已冲销原单') : Number(item.amount) < 0 ? $tr('冲销抵减') : $tr('已计入') }}</template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="bizDate" :label="$tr('日期')" width="115" />
        <el-table-column :label="$tr('花费合计')" min-width="130"><template #default="{ row }"><b>{{ money(row.amount) }}</b></template></el-table-column>
        <el-table-column v-for="column in columns" :key="column.key" :label="$tr(column.label)" :min-width="column.key === 'internalProjectCost' ? 145 : 125">
          <template #default="{ row }">{{ money(row[column.key]) }}<el-tag v-if="column.key === 'personnelCost' && row.pendingPersonnelCount" type="warning" size="small">{{ $tr('{0} 项待计价', [row.pendingPersonnelCount]) }}</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer><el-button @click="$emit('update:modelValue', false)">{{ $tr('关闭') }}</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { getBusinessOwnerSpendHistory } from '@/api/business/project'
import { translateText } from '@/locales/translate'
import { spendHistoryQuery, spendPersonnelDescription, spendExpenseItems } from '@/utils/spendHistory'

const props = defineProps({ modelValue: Boolean, projectId: [Number, String], projectName: String })
defineEmits(['update:modelValue'])
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const filterMode = ref('month'), month = ref(today().slice(0, 7)), date = ref(today())
const loading = ref(false), failed = ref(false), history = ref({})
const currency = computed(() => history.value.currency || 'CNY')
const columns = computed(() => [
  { key: 'personnelCost', label: translateText('人员成本') }, { key: 'projectCost', label: translateText('业务成本') },
  { key: 'internalProjectCost', label: translateText('内部项目支出') }, { key: 'bonusCost', label: translateText('项目奖金') },
  { key: 'publicCost', label: translateText('公共费用（含暂估）') }
])
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const disabledDate = value => value.getTime() > new Date().setHours(23, 59, 59, 999) || value.getFullYear() < 1900
let requestId = 0
async function loadHistory() {
  if (!props.modelValue || !props.projectId) return
  const request = ++requestId
  loading.value = true; failed.value = false; history.value = {}
  try {
    const response = await getBusinessOwnerSpendHistory(props.projectId, spendHistoryQuery(filterMode.value, month.value, date.value))
    if (request === requestId) history.value = response.data || {}
  } catch {
    if (request === requestId) failed.value = true
  } finally { if (request === requestId) loading.value = false }
}
function expenseItems(row) {
  return spendExpenseItems(row, currency.value, translateText, money)
}
watch(() => [props.modelValue, props.projectId, filterMode.value, month.value, date.value], () => {
  if (props.modelValue) loadHistory()
  else { requestId++; loading.value = false; history.value = {}; failed.value = false }
})
</script>

<style scoped>
.spend-history-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.spend-history-note,.spend-history-period{color:#7c8997;font-size:13px;line-height:1.6}
.spend-history-summary{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:14px;background:#f5f8fa;border-radius:8px}
.spend-history-summary strong{color:#175e55;font-size:17px}.spend-history-summary span{color:#637583;font-size:13px}
.spend-history-details{padding:4px 24px 20px}.spend-history-details h4{margin:15px 0 8px}
.spend-history-details small{display:block;color:#7c8997}.spend-history-description{white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:640px){.spend-history-toolbar{align-items:stretch;flex-direction:column}.spend-history-toolbar :deep(.el-date-editor){width:100%}.spend-history-details{padding:4px 10px 14px}}
</style>
