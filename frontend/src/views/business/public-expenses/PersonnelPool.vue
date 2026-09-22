<template>
  <section class="personnel-pool" v-loading="loading">
    <div class="heading"><div><h2>{{ $tr("公共人员成本") }}</h2><p>{{ $tr("项目直接承担金额＝人员月成本 × 当月有效项目投入比例之和；剩余成本按老板设置的部门、负责人比例分摊。") }}</p></div><div class="actions"><el-button :disabled="disabled || saving" :loading="loading" @click="refreshPreview">{{ $tr("刷新") }}</el-button><el-button v-if="editable" type="primary" :disabled="disabled || loading || loadFailed || !previewLoaded || blocked" :loading="saving" @click="save">{{ $tr("下一步：分摊给负责人") }}</el-button><el-button v-else-if="bill?.status === 'PUBLISHED'" type="primary" :disabled="disabled || saving" @click="emit('recall')">{{ $tr("退回修改") }}</el-button></div></div>
    <template v-if="snapshot">
      <div class="metrics"><div><span>{{ $tr("人员月成本合计") }}</span><b>{{ money(snapshot.totalAmount) }}</b></div><div><span>{{ $tr("项目已承担金额") }}</span><b>{{ money(Number(snapshot.projectAmount) + Number(snapshot.businessAmount)) }}</b></div><div><span>{{ $tr("公共人员成本合计") }}</span><b>{{ money(snapshot.publicAmount) }} {{ filters.currency }}</b></div><div><span>{{ $tr("日暂估金额（÷ 21.75）") }}</span><b>{{ money(snapshot.dailyReference) }}</b></div></div>
      <el-alert :type="snapshot.estimated ? 'warning' : 'success'" :closable="false" :title="snapshot.estimated ? $tr(&quot;本月金额为整月暂估；月底请刷新整月人员成本，再与日常公共费用一起月结。&quot;) : $tr(&quot;整月人员成本已保存，将与日常公共费用一起月结。&quot;)" />
      <el-collapse><el-collapse-item :title="$tr(&quot;查看人员成本来源（仅老板可见）&quot;)"><el-table :data="snapshot.rows"><el-table-column prop="userName" :label="$tr(&quot;人员&quot;)" min-width="100"/><el-table-column prop="deptName" :label="$tr(&quot;所属部门&quot;)" min-width="110"/><el-table-column :label="$tr(&quot;人员月成本&quot;)" min-width="120"><template #default="{ row }">{{ money(row.totalAmount) }}</template></el-table-column><el-table-column :label="$tr(&quot;项目已承担金额&quot;)" min-width="220"><template #default="{ row }">{{ money(Number(row.projectAmount) + Number(row.businessAmount)) }}<small v-for="item in row.projectAllocations || []" :key="item.projectId">{{ item.projectName }}：{{ item.allocationPercent }}% × {{ money(row.totalAmount) }} = {{ money(item.amount) }}</small></template></el-table-column><el-table-column :label="$tr(&quot;待分摊金额&quot;)" min-width="120"><template #default="{ row }">{{ money(row.publicAmount) }}</template></el-table-column></el-table></el-collapse-item></el-collapse>
    </template>
    <div class="current-source">
      <el-alert v-if="!snapshot" :title="bill?.status === 'PUBLISHED' ? $tr(&quot;本月账单已下发，尚未加入公共人员成本。下方为人员数据预览，未计入费用；加入前需退回修改，修改后重新下发。&quot;) : bill?.status === 'SETTLED' ? $tr(&quot;本月账单已结算，未包含公共人员成本。下方仅供核对，不改动已结算账单。&quot;) : $tr(&quot;金额自动读取，无需重复填写。点击“下一步”，选择部门、负责人及分摊比例。&quot;)" type="info" :closable="false" show-icon/>
      <el-alert v-if="loadFailed" class="note" :title="$tr(&quot;人员成本加载失败，请点击“刷新”重试。&quot;)" type="error" :closable="false" show-icon/>
      <template v-else-if="previewLoaded">
        <div class="metrics preview-metrics"><div><span>{{ $tr("人员数量") }}</span><b>{{ $tr("{0} 人", [rows.length]) }}</b></div><div><span>{{ $tr("人员月成本合计（预览）") }}</span><b>{{ money(previewPayroll) }}</b></div><div><span>{{ $tr("公共人员成本合计（预览）") }}</span><b>{{ money(total) }}</b></div><div><span>{{ $tr("日暂估金额（÷ 21.75）") }}</span><b>{{ money(total == null ? null : total / 21.75) }}</b></div></div>
        <el-alert v-if="rows.some(row => remainder(row) != null && remainder(row) < 0)" type="error" :closable="false" :title="$tr(&quot;项目已承担金额超过人员月成本，请先核对人员成本和项目投入记录。&quot;)"/>
        <el-alert v-if="incompleteCount" type="warning" :closable="false" show-icon :title="$tr(&quot;{0} 位人员成本待完善，请核对下方说明；待完善金额不会按零计入分摊。&quot;, [incompleteCount])"/>
        <el-alert v-for="issue in preview.issues || []" :key="issue" class="note" type="warning" :closable="false" :title="issue"/>
        <el-table :data="rows" class="source-preview" max-height="440" :empty-text="$tr(&quot;所选公司、月份和币种暂无适用人员。请核对人员归属及成本设置。&quot;)">
          <el-table-column prop="userName" :label="$tr(&quot;人员&quot;)" min-width="120"/><el-table-column prop="deptName" :label="$tr(&quot;所属部门&quot;)" min-width="110"/>
          <el-table-column :label="$tr(&quot;人员月成本&quot;)" min-width="130"><template #default="{ row }">{{ money(row.totalAmount) }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;项目直接承担金额&quot;)" min-width="230"><template #default="{ row }"><span>{{ money(row.projectAmount) }}</span><small v-for="item in row.projectAllocations || []" :key="item.projectId">{{ item.projectName }}：{{ item.allocationPercent }}% × {{ money(row.totalAmount) }} = {{ money(item.amount) }}</small><small v-if="row.projectIssues?.length" class="warning">{{ $tr("投入比例待完善，暂不计入分摊") }}</small></template></el-table-column>
          <el-table-column v-if="rows.some(row => businessAmount(row) > 0)" :label="$tr(&quot;历史已入账扣除&quot;)" min-width="130"><template #default="{ row }">{{ money(businessAmount(row)) }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;待分摊金额&quot;)" min-width="130"><template #default="{ row }">{{ money(row.projectIssues?.length ? null : remainder(row)) }}</template></el-table-column>
          <el-table-column :label="$tr(&quot;数据状态&quot;)" min-width="250"><template #default="{ row }"><div class="review-status"><el-tag v-if="row.issues?.length" type="warning" effect="plain">{{ $tr("月成本待完善") }}</el-tag><el-tag v-if="row.projectIssues?.length" type="warning" effect="plain">{{ $tr("项目成本待完善") }}</el-tag><span v-if="!row.issues?.length && !row.projectIssues?.length">{{ $tr("已自动计算") }}</span><el-button v-else link type="primary" @click="showIssues(row)">{{ $tr("查看原因") }}</el-button></div></template></el-table-column>
        </el-table>
      </template>
      <p v-else-if="loading" class="note">{{ $tr("正在获取本公司人员及项目成本…") }}</p>
    </div>
    <el-alert v-if="rows.some(row => businessAmount(row) > 0)" class="note" type="info" :closable="false" :title="$tr(&quot;历史已关联的人员支出继续自动扣除，避免重复分摊。&quot;)"/>
    <p class="note">{{ $tr("按月计价的人员，其月成本与人员成本设置中的月度内部费率一致；月内多次调价时按生效期间折算。需要调整金额时请到人员成本设置修改。公共人员成本单独设置部门、负责人比例。负责人分摊到项目后，日结果按月分摊金额 ÷ 21.75 暂估；月结以实际月分摊金额替换暂估金额，不重复扣费。") }}</p>
    <el-dialog v-model="issuesDialog" :title="$tr(&quot;{0} · 成本核对原因&quot;, [selectedRow?.userName || ''])" width="min(720px, 94vw)" append-to-body>
      <p class="issue-intro">{{ $tr("项目直接承担金额按人员月成本和当月有效投入比例计算。请核对成本币种及投入比例的确认状态。") }}</p>
      <section v-for="group in issueGroups" :key="group.title" class="issue-group">
        <h3>{{ group.title }}</h3>
        <article v-for="(issue, index) in group.items" :key="index" class="issue-item">
          <b v-if="issue.projectName != null">{{ $tr("项目「{0}」", [issue.projectName]) }}</b>
          <div class="warning">{{ issue.reason }}</div>
          <p v-if="issue.dates?.length">{{ $tr("涉及 {0} 天：{1}", [issue.dates.length, formatDates(issue.dates)]) }}</p>
        </article>
        <p class="issue-help">{{ group.help }}</p>
      </section>
      <template #footer><el-button type="primary" @click="issuesDialog = false">{{ $tr("知道了") }}</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { translateText } from '@/locales/translate'

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPublicPersonnelPreview, savePublicPersonnel } from '@/api/business/publicExpense'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
const props = defineProps({ filters: { type: Object, required: true }, bill: Object, editable: Boolean, disabled: Boolean })
const emit = defineEmits(['saved', 'busy', 'recall'])
const loading = ref(false), saving = ref(false), preview = ref({}), rows = ref([])
const previewLoaded = ref(false), loadFailed = ref(false)
const issuesDialog = ref(false), selectedRow = ref(null)
const showIssues = row => { selectedRow.value = row; issuesDialog.value = true }
const issueGroups = computed(() => {
  const row = selectedRow.value || {}
  return [
    { title: translateText("人员月成本"), items: row.issueDetails || (row.issues || []).map(reason => ({ reason })), help: translateText("请到人员成本设置补齐金额和生效期间，并核对入职日期、工作日历，再返回刷新。") },
    { title: translateText("项目直接承担成本"), items: row.projectIssueDetails || (row.projectIssues || []).map(reason => ({ reason })), help: translateText("请在对应项目中补齐或确认投入比例，然后返回刷新。") }
  ].filter(group => group.items.length)
})
function formatDates(dates) {
  const days = [...new Set(dates)].sort(), ranges = []
  let start = days[0], end = start
  const append = () => ranges.push(start === end ? start : translateText("{0} 至 {1}", [start, end]))
  for (const day of days.slice(1)) {
    if (Date.parse(`${day}T00:00:00Z`) - Date.parse(`${end}T00:00:00Z`) === 86400000) end = day
    else { append(); start = end = day }
  }
  if (start) append()
  return ranges.join('、')
}
const snapshot = computed(() => props.bill?.personnel)
const money = value => value == null || !Number.isFinite(Number(value)) ? translateText("待完善") : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const businessAmount = row => Number(row.businessAmount || 0)
const remainder = row => row.totalAmount == null || row.projectIssues?.length ? null : Math.round((Number(row.totalAmount) - Number(row.projectAmount) - businessAmount(row)) * 100) / 100
const incompleteCount = computed(() => rows.value.filter(row => row.totalAmount == null || row.projectIssues?.length).length)
const previewPayroll = computed(() => rows.value.some(row => row.totalAmount == null) ? null : rows.value.reduce((sum, row) => sum + Number(row.totalAmount), 0))
const total = computed(() => incompleteCount.value || preview.value.issues?.length ? null : rows.value.reduce((sum, row) => sum + Number(remainder(row) || 0), 0))
const blocked = computed(() => !rows.value.length || preview.value.issues?.length > 0 || rows.value.some(row => row.totalAmount == null || remainder(row) < 0 || row.projectIssues?.length))
async function fetchPreview() {
  if (loading.value || saving.value) return false
  loading.value = true; loadFailed.value = false; emit('busy', true)
  try {
    const response = await getPublicPersonnelPreview({ ...props.filters })
    preview.value = response.data || {}
    rows.value = preview.value.rows || []
    previewLoaded.value = true
    return true
  } catch { loadFailed.value = true; return false } finally { loading.value = false; emit('busy', false) }
}
async function refreshPreview() {
  if (props.disabled) return
  await fetchPreview()
}
onMounted(fetchPreview)
useBusinessRefreshOnReactivated(fetchPreview)
async function save() {
  if (!props.editable || props.disabled || blocked.value || saving.value || loading.value || loadFailed.value || !previewLoaded.value) return
  saving.value = true; emit('busy', true)
  try {
    await savePublicPersonnel({ ...props.filters, version: props.bill?.version })
    ElMessage.success(translateText("已自动汇总人员成本，请设置部门、负责人和分摊比例。"))
    emit('saved')
  } finally { saving.value = false; emit('busy', false) }
}
</script>
<style scoped>
.personnel-pool { padding: 22px; border: 1px solid #e2e9ed; border-radius: 12px; background: var(--el-bg-color); }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
h2 { margin: 0 0 8px; font-size: 18px; } p, small { color: #758591; font-size: 13px; line-height: 1.7; } small { display: block; }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; } .metrics > div { padding: 18px; background: #f1f8f6; border-radius: 10px; }
.metrics span { display: block; color: #73858e; font-size: 13px; }.metrics b { display: block; margin-top: 10px; font-size: 22px; color: #245e55; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }.actions .el-button + .el-button { margin-left: 0; }.preview-metrics { margin-top: 18px; }.source-preview { margin-top: 18px; }
.note { margin-top: 16px; }.source-table { margin: 18px 0; }.source-table .el-input-number { width: 100%; }.warning { color: #c78018; }.preview-total { padding: 16px; background: #f1f8f6; border-radius: 8px; }
.review-status { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }.issue-intro { margin-top: 0; }.issue-group { margin-top: 20px; }.issue-group h3 { font-size: 15px; }.issue-item { padding: 12px 16px; margin-top: 8px; background: var(--el-fill-color-light); border-radius: 8px; line-height: 1.8; }.issue-item p { margin: 4px 0 0; overflow-wrap: anywhere; }.issue-help { margin-bottom: 0; }
@media(max-width: 800px) { .metrics { grid-template-columns: 1fr 1fr; }.heading { flex-wrap: wrap; } }
</style>
