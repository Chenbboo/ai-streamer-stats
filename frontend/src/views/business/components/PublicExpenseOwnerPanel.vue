<template>
  <section ref="panelElement" class="public-expense-owner" v-loading="loading" aria-label="负责人公共费用">
    <div class="expense-heading">
      <div>
        <h2>本月公共费用 <el-tag v-if="pendingBills.length" size="small" type="warning">{{ pendingBills.length }} 项待提交</el-tag></h2>
        <p>查看本人承担的公司费用，并分配到项目；切换月份可查看历史记录。</p>
      </div>
      <div class="expense-controls">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" format="YYYY年MM月" :clearable="false" :disabled="saving" aria-label="公共费用月份" />
        <el-button icon="Refresh" :disabled="saving" :loading="loading" @click="refresh">刷新</el-button>
      </div>
    </div>
    <el-alert v-if="loadFailed" title="公共费用加载失败，费用金额暂不可用，请刷新重试。" type="error" :closable="false" show-icon />
    <div v-else-if="!loading && !bills.length" class="expense-empty">{{ month }} 暂无下发给你的公共费用。老板下发后，会在这里显示待分配金额。</div>
    <div v-else class="expense-bills">
      <article v-for="bill in bills" :key="bill.allocationId" class="expense-bill">
        <div class="bill-heading">
          <div><h3>{{ bill.companyName || '公司公共费用' }} <small>{{ bill.currency }}</small></h3><p>公司月费用 {{ money(bill.totalAmount) }} {{ bill.currency }} × 本人比例 {{ percent(bill.percentage) }}%</p></div>
          <el-tag :type="statusTone(bill)" effect="plain">{{ statusLabel(bill) }}</el-tag>
        </div>
        <div class="expense-metrics">
          <div><span>本月承担</span><strong>{{ money(bill.amount) }}<small>{{ bill.currency }}</small></strong></div>
          <div><span>已分摊{{ bill.status === 'DRAFT' ? '（草稿）' : '' }}</span><strong>{{ money(bill.allocatedAmount) }}</strong></div>
          <div><span>待分摊</span><strong :class="{ attention: Number(bill.remainingAmount) > 0 }">{{ money(bill.remainingAmount) }}</strong></div>
          <div><span>成本计入</span><strong>{{ bill.billStatus === "SETTLED" ? "已确认" : bill.status === "SUBMITTED" ? "按天暂估" : "待提交" }}</strong><small>已提交的分摊按项目日期计入成本</small></div>
        </div>
        <el-alert v-if="bill.billStatus !== 'SETTLED' && bill.status !== 'SUBMITTED'" class="bill-alert" :title="Number(bill.remainingAmount) > 0 ? '公共费用待分摊，项目经营参考结果尚不完整。' : '项目分摊已保存为草稿，提交后按天计入暂估成本。'" type="warning" :closable="false" show-icon />
        <div class="bill-actions">
          <el-button type="primary" :plain="bill.status === 'SUBMITTED' || bill.billStatus === 'SETTLED'" :disabled="loading || saving" @click="openAllocation(bill)">{{ bill.billStatus === 'SETTLED' ? '查看项目分摊' : bill.status === 'SUBMITTED' ? '查看 / 调整分摊' : '分配到项目' }}</el-button>
          <el-button link type="primary" @click="toggleEntries(bill.allocationId)">{{ expandedEntries.includes(bill.allocationId) ? '收起费用构成' : '查看费用构成' }}</el-button>
        </div>
        <el-table v-if="expandedEntries.includes(bill.allocationId)" :data="bill.entries || []" size="small" class="expense-entry-table">
          <el-table-column prop="name" label="费用名称" min-width="150" />
          <el-table-column label="类别" min-width="100"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
          <el-table-column label="公司月费用" min-width="150" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ bill.currency }}</template></el-table-column>
          <el-table-column label="本人承担" min-width="150" align="right"><template #default="{ row }">{{ money(row.ownerAmount) }} {{ bill.currency }}</template></el-table-column>
        </el-table>
      </article>
    </div>
    <p class="expense-footnote">提交后按项目当月承担费用期间的自然日计入暂估成本，月结核实实际金额，不重复扣费。请勿再录入项目其他花费。</p>

    <el-dialog v-model="dialogVisible" :title="`${activeBill?.companyName || '公司公共费用'} · ${activeBill?.month || month} 项目分摊`" width="min(920px, 96vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeDialog">
      <div v-if="activeBill" v-loading="saving || loading" class="allocation-dialog">
        <div class="allocation-summary">
          <span>本月承担 <b>{{ money(activeBill.amount) }} {{ activeBill.currency }}</b></span>
          <span>分配比例 <b :class="{ attention: percentageTotal !== 100 }">{{ percent(percentageTotal) }}%</b></span>
          <span>待分摊 <b>{{ money(previewRemaining) }} {{ activeBill.currency }}</b></span>
        </div>
        <el-alert v-if="readOnly" title="本月已结算，分摊记录已锁定。后续更正由老板登记调整记录。" type="success" :closable="false" show-icon />
        <el-alert v-else-if="activeBill.status === 'SUBMITTED'" title="此分摊已提交。保存修改后会回到草稿状态，请重新提交。" type="info" :closable="false" show-icon />
        <el-alert v-if="!readOnly && !allocationRows.length" title="暂无可分配的同公司、同币种项目。费用仍保留为待分摊，请联系老板处理或建立项目后再分配。" type="warning" :closable="false" show-icon />
        <div class="allocation-toolbar"><span>仅可分配给本人负责的同公司、同币种项目</span><el-button v-if="!readOnly" :disabled="saving || loading" @click="copyPrevious">复制上月分配</el-button></div>
        <el-table :data="previewRows" row-key="projectId" empty-text="暂无项目分摊记录">
          <el-table-column label="项目" min-width="190"><template #default="{ row }"><b>{{ row.projectName }}</b><small v-if="row.unavailable && !readOnly" class="unavailable-project">当前不可分配，请将比例设为 0</small></template></el-table-column>
          <el-table-column label="分配比例" width="190"><template #default="{ row, $index }"><span v-if="readOnly">{{ percent(row.percentage) }}%</span><div v-else class="percentage-input"><el-input-number v-model="allocationRows[$index].percentage" :min="0" :max="100" :precision="2" :step="1" :disabled="saving || loading" controls-position="right" :aria-label="`${row.projectName}分配比例`" /><span>%</span></div></template></el-table-column>
          <el-table-column label="本月公共费用" min-width="155" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ activeBill.currency }}</template></el-table-column>
        </el-table>
        <div class="allocation-totals"><span>合计 {{ percent(percentageTotal) }}%</span><b>{{ money(previewAllocated) }} {{ activeBill.currency }}</b></div>
        <p class="allocation-note">可以先保存部分比例；提交时必须合计 100%。金额精确到分，系统自动处理尾差；月中新增或结束的项目，由你确定当月承担比例。</p>
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readOnly" :disabled="saving || loading || percentageTotal > 100 || invalidRows" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="!readOnly" type="primary" :loading="saving" :disabled="loading || percentageTotal !== 100 || invalidRows" @click="submitAllocation">保存并提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup name="PublicExpenseOwnerPanel">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getOwnerPublicExpenseWorkspace, savePublicExpenseProjectAllocations, submitPublicExpenseProjectAllocations, copyPreviousPublicExpenseProjects } from '@/api/business/publicExpense'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const emit = defineEmits(['changed'])
const currentMonth = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
const month = ref(currentMonth())
const bills = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const saving = ref(false)
const expandedEntries = ref([])
const dialogVisible = ref(false)
const activeAllocationId = ref(null)
const allocationRows = ref([])
const panelElement = ref(null)
let requestSequence = 0
const activeBill = computed(() => bills.value.find(bill => String(bill.allocationId) === String(activeAllocationId.value)))
const readOnly = computed(() => activeBill.value?.billStatus === 'SETTLED')
const pendingBills = computed(() => bills.value.filter(bill => bill.billStatus !== 'SETTLED' && bill.status !== 'SUBMITTED'))
const percentageUnits = value => Math.round(Number(value || 0) * 100)
const percentageTotal = computed(() => allocationRows.value.reduce((sum, row) => sum + percentageUnits(row.percentage), 0) / 100)
const invalidRows = computed(() => allocationRows.value.some(row => !Number.isFinite(Number(row.percentage)) || (row.unavailable && Number(row.percentage) > 0)))
const previewRows = computed(() => {
  if (readOnly.value) return allocationRows.value
  const totalCents = Math.round(Number(activeBill.value?.amount || 0) * 100)
  const rows = allocationRows.value.map((row, index) => {
    const raw = totalCents * percentageUnits(row.percentage) / 10000
    return { ...row, index, remainder: raw - Math.floor(raw), cents: Math.floor(raw) }
  })
  const ranked = rows.filter(row => Number(row.percentage) > 0).sort((a, b) => b.remainder - a.remainder || a.index - b.index)
  const targetCents = Math.round(totalCents * percentageTotal.value / 100)
  const remainderCents = targetCents - rows.reduce((sum, row) => sum + row.cents, 0)
  for (let index = 0; index < remainderCents && index < ranked.length; index++) ranked[index].cents++
  return rows.map(row => ({ ...row, amount: row.cents / 100 }))
})
const previewAllocated = computed(() => previewRows.value.reduce((sum, row) => sum + Math.round(Number(row.amount || 0) * 100), 0) / 100)
const previewRemaining = computed(() => (Math.round(Number(activeBill.value?.amount || 0) * 100) - Math.round(previewAllocated.value * 100)) / 100)
const money = value => value === null || value === undefined || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = value => Number(value || 0).toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
const categoryLabel = value => ({
  RENT: '场地房租',
  UTILITIES: '水电费',
  PROPERTY: '物业费',
  NETWORK: '网络通讯',
  INTERNET: '网络通讯',
  OFFICE: '办公杂费',
  OTHER: '其他费用'
}[String(value || '').trim().toUpperCase()] || '其他费用')
const statusLabel = bill => bill.billStatus === 'SETTLED' ? '已月结' : bill.status === 'SUBMITTED' ? '已提交，待月结' : '待分摊 / 提交'
const statusTone = bill => bill.billStatus === 'SETTLED' ? 'success' : bill.status === 'SUBMITTED' ? 'primary' : 'warning'

async function refresh() {
  const sequence = ++requestSequence
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getOwnerPublicExpenseWorkspace(month.value)
    if (sequence !== requestSequence) return false
    bills.value = response.data?.bills || []
    if (dialogVisible.value && !activeBill.value) dialogVisible.value = false
    return true
  } catch {
    if (sequence === requestSequence) { bills.value = []; loadFailed.value = true; dialogVisible.value = false }
    return false
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function populateAllocationRows(bill) {
  const saved = bill.projects || []
  const options = bill.projectOptions || []
  allocationRows.value = bill.billStatus === 'SETTLED' ? saved.map(row => ({ ...row })) : [
    ...options.map(option => ({ ...option, percentage: saved.find(row => String(row.projectId) === String(option.projectId))?.percentage || 0 })),
    ...saved.filter(row => !options.some(option => String(option.projectId) === String(row.projectId))).map(row => ({ ...row, unavailable: true }))
  ]
}
function openAllocation(bill) { activeAllocationId.value = bill.allocationId; populateAllocationRows(bill); dialogVisible.value = true }
function toggleEntries(id) { expandedEntries.value = expandedEntries.value.includes(id) ? expandedEntries.value.filter(item => item !== id) : [...expandedEntries.value, id] }
function closeDialog(done) { if (!saving.value) done() }
function allocationPayload() { return { version: activeBill.value.version, allocations: allocationRows.value.filter(row => Number(row.percentage) > 0).map(row => ({ projectId: row.projectId, percentage: Number(row.percentage) })) } }
async function saveAllocation(submit) {
  if (!activeBill.value || saving.value || readOnly.value || invalidRows.value) return
  if (percentageTotal.value > 100 || (submit && percentageTotal.value !== 100)) return ElMessage.warning('提交时项目分配比例必须合计 100%')
  saving.value = true
  try {
    const id = activeBill.value.allocationId
    await savePublicExpenseProjectAllocations(id, allocationPayload())
    if (!await refresh() || !activeBill.value) return
    populateAllocationRows(activeBill.value)
    if (submit) {
      await submitPublicExpenseProjectAllocations(id, { version: activeBill.value.version })
      await refresh()
      dialogVisible.value = false
    }
    ElMessage.success(submit ? '项目费用分摊已提交，等待老板确认月结' : '项目费用分摊草稿已保存')
    emit('changed')
  } catch {
    // The request interceptor displays the server validation or version-conflict message.
  } finally { saving.value = false }
}
const saveDraft = () => saveAllocation(false)
const submitAllocation = () => saveAllocation(true)
async function copyPrevious() {
  if (!activeBill.value || saving.value || readOnly.value) return
  saving.value = true
  try {
    await copyPreviousPublicExpenseProjects(activeBill.value.allocationId, { version: activeBill.value.version })
    if (await refresh() && activeBill.value) { populateAllocationRows(activeBill.value); ElMessage.success('已复制上月分配并保存为草稿，请核对比例后提交'); emit('changed') }
  } catch {
    // The request interceptor displays the actionable backend error.
  } finally { saving.value = false }
}
watch(month, () => { dialogVisible.value = false; expandedEntries.value = []; refresh() }, { immediate: true })
useBusinessRefreshOnReactivated(refresh)
defineExpose({ refresh, openPending: () => { panelElement.value?.scrollIntoView({ block: 'start', behavior: 'smooth' }); if (pendingBills.value.length) openAllocation(pendingBills.value[0]) } })
</script>

<style scoped>
.public-expense-owner{margin:16px 0;padding:20px;border:1px solid #dce6e8;border-radius:14px;background:#fff;color:#172335}.expense-heading,.bill-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.expense-heading h2{display:flex;align-items:center;gap:10px;margin:0;font-size:18px}.expense-heading p,.bill-heading p{margin:7px 0 0;color:#748390;font-size:13px;line-height:1.6}.expense-controls{display:flex;align-items:center;gap:8px}.expense-controls :deep(.el-date-editor){width:155px}.expense-bills{display:grid;gap:14px;margin-top:16px}.expense-bill{padding:18px;border:1px solid #e1e9ec;border-radius:11px;background:#fbfdfd}.bill-heading h3{margin:0;font-size:16px}.bill-heading h3 small{margin-left:6px;color:#738694;font-size:12px;font-weight:400}.expense-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:20px;margin:20px 0}.expense-metrics>div{display:flex;flex-direction:column;gap:6px}.expense-metrics span,.expense-metrics>div>small{color:#788894;font-size:12px}.expense-metrics strong{font-size:22px;overflow-wrap:anywhere}.expense-metrics strong small{margin-left:5px;color:#768895;font-size:11px;font-weight:400}.attention{color:#bc7717!important}.bill-alert{margin-bottom:12px}.bill-actions{display:flex;flex-wrap:wrap;align-items:center;gap:10px}.bill-actions>.el-button{margin-left:0}.expense-entry-table{margin-top:14px}.expense-empty{padding:25px 0;color:#758593;font-size:14px}.expense-footnote{margin:15px 0 0;color:#87949e;font-size:12px;line-height:1.7}.allocation-summary{display:flex;flex-wrap:wrap;gap:14px 28px;padding:16px;background:#f4f9f8;border-radius:8px;margin-bottom:14px}.allocation-summary span{color:#7c8a94}.allocation-summary b{margin-left:8px;color:#253b44}.allocation-toolbar{display:flex;justify-content:space-between;gap:10px;align-items:center;margin:16px 0 10px}.allocation-toolbar>span,.allocation-note{color:#81919c;font-size:12px;line-height:1.7}.percentage-input{display:flex;align-items:center;gap:8px}.percentage-input :deep(.el-input-number){width:135px}.unavailable-project{display:block;color:#bc7717;font-size:12px;font-weight:400;margin-top:5px}.allocation-totals{display:flex;justify-content:flex-end;gap:30px;padding:14px 12px;background:#f5f8fa}.allocation-note{margin-top:14px}@media(max-width:900px){.expense-heading{flex-direction:column}.expense-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:520px){.public-expense-owner{padding:14px}.expense-bill{padding:13px}.bill-heading{flex-wrap:wrap}.expense-metrics{gap:16px}.expense-metrics strong{font-size:19px}.allocation-toolbar{align-items:flex-start;flex-direction:column}.expense-heading h2{flex-wrap:wrap}}
</style>
