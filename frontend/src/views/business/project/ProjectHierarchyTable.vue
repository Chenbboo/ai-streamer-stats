<template>
  <el-alert v-if="loadError" title="项目列表读取失败" type="error" :closable="false" show-icon><el-button link type="primary" @click="refresh">重新加载</el-button></el-alert>
  <el-table ref="tableRef" :data="visibleRows" row-key="projectId" :tree-props="{ children: '_unusedChildren', hasChildren: '_unusedHasChildren' }" v-loading="loading" :row-class-name="rowClass" :span-method="spanMethod" empty-text="暂无匹配的主项目或子项目">
    <el-table-column width="48" label="" fixed="left" class-name="toggle-cell">
      <template #default="{ row }">
        <div v-if="row.empty" class="empty-children"><span v-if="row.childLoading">正在加载子项目…</span><template v-else-if="row.childError">子项目加载失败 <el-button link type="primary" @click="loadChildren(row.parentId, true)">重试</el-button></template><span v-else>暂无可查看的子项目，可通过主项目操作栏新增</span></div>
        <el-button v-else-if="!row.parentId" text circle :aria-label="`${expanded.has(row.projectId) ? '收起' : '展开'}${row.projectName}的子项目`" :aria-expanded="expanded.has(row.projectId)" @click.stop="toggle(row.projectId)">
          <el-icon><ArrowDown v-if="expanded.has(row.projectId)"/><ArrowRight v-else/></el-icon>
        </el-button>
      </template>
    </el-table-column>
    <el-table-column label="项目名" min-width="220">
      <template #default="{ row }"><div :data-project-id="row.projectId" :style="{ paddingLeft: `${row.depth * 22}px` }"><el-tag v-if="row.parentId" size="small" effect="plain" class="child-tag">子项目</el-tag><b>{{ row.projectName }}</b><small>{{ row.projectNo || (row.contextOnly ? '仅显示层级，详情按项目权限开放' : '—') }}</small></div></template>
    </el-table-column>
    <el-table-column prop="companyName" label="归属公司" min-width="130"><template #default="{ row }">{{ row.companyName || '—' }}</template></el-table-column>
    <el-table-column label="归属老板" min-width="90"><template #default="{ row }">{{ row.sponsorOwnerName || row.initiatorName || '—' }}</template></el-table-column>
    <el-table-column label="负责人" min-width="90"><template #default="{ row }">{{ row.mainOwnerName || '—' }}</template></el-table-column>
    <el-table-column label="治理方式" min-width="135"><template #default="{ row }">{{ managementLabels[row.managementMode] || '—' }}<small>{{ closeLabels[row.closeMethod] }}</small></template></el-table-column>
    <el-table-column label="类型" width="90"><template #default="{ row }">{{ typeLabels[row.projectType] || row.projectType || '—' }}</template></el-table-column>
    <el-table-column label="交付 / 核算" min-width="160"><template #default="{ row }"><template v-if="!row.contextOnly"><BusinessProjectState :project="row"/><small>{{ accountingLabels[row.accountingMode] }}</small></template><span v-else>—</span></template></el-table-column>
    <el-table-column label="计划周期" min-width="185"><template #default="{ row }">{{ row.planStartDate ? `${row.planStartDate} 至 ${row.planEndDate || '不限期'}` : '—' }}</template></el-table-column>
    <el-table-column label="项目目标" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.objective || '—' }}</template></el-table-column>
    <el-table-column label="成员 / 风险" width="130" align="center"><template #default="{ row }"><el-button v-if="!row.contextOnly" link type="primary" :aria-label="`查看${row.projectName}成员和风险详情`" @click.stop="showPeopleRisks(row)">{{ row.memberCount || 0 }} 人 / <span :class="{ danger: row.openRiskCount }">{{ row.openRiskCount || 0 }} 风险</span></el-button><span v-else>—</span></template></el-table-column>
    <el-table-column label="项目进度 / 最新汇报" min-width="200"><template #default="{row}"><template v-if="!row.contextOnly"><el-button link type="primary" @click.stop="$emit('progress',row)">{{ row.progressPercent ?? 0 }}%{{ row.subprojectCount ? ' · 展开子项目汇总' : ' · 查看汇报' }}</el-button><small>{{ row.progressSummary || '尚无汇报' }}</small></template><span v-else>—</span></template></el-table-column>
    <el-table-column label="操作" width="350" fixed="right"><template #default="{ row }"><div v-if="!row.contextOnly" class="row-actions">
      <el-button v-if="row.manageable && !row.parentId && !ended(row)" v-hasPermi="['business:project:proposal:add']" link type="primary" @click.stop="$emit('create', row)">新增子项目</el-button>
      <el-button v-if="row.manageable && !ended(row)" v-hasPermi="['business:project:edit']" link type="primary" @click.stop="$emit('edit', row)">编辑</el-button>
      <el-button v-if="canReportProgress(row,userStore.id)" v-hasPermi="['business:project:report']" link type="primary" @click.stop="$emit('report',row)">汇报进度</el-button>
      <el-button link type="primary" @click.stop="$emit('detail', row)">查看详情</el-button>
      <el-button v-if="row.manageable" v-hasPermi="['business:project:edit']" link type="danger" :loading="deleting===row.projectId" @click.stop="remove(row)">删除</el-button>
    </div></template></el-table-column>
  </el-table>
  <pagination v-show="total" :total="total" v-model:page="page" v-model:limit="pageSize" @pagination="refresh"/>
  <el-dialog v-model="summaryOpen" :title="`${summaryName} · 成员 / 风险`" width="min(760px, 94vw)" destroy-on-close>
    <div v-loading="summaryLoading" class="summary-content">
      <el-alert v-if="summaryError" title="成员与风险读取失败，请关闭后重试" type="error" :closable="false"/>
      <template v-else><h3>项目成员（{{ summary.members?.length || 0 }}）</h3>
        <el-table :data="summary.members || []" empty-text="暂无成员" max-height="260"><el-table-column prop="userNameSnapshot" label="姓名"/><el-table-column label="角色"><template #default="{ row }">{{ roleLabels[row.memberRole] || row.memberRole }}</template></el-table-column><el-table-column prop="responsibility" label="职责"/></el-table>
        <h3>风险详情（{{ summary.risks?.length || 0 }}）</h3>
        <el-table :data="summary.risks || []" empty-text="暂无风险" max-height="320"><el-table-column prop="riskTitle" label="风险" min-width="150"/><el-table-column label="等级" width="70"><template #default="{ row }">{{ severityLabels[row.severity] }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{ row }">{{ riskLabels[row.status] }}</template></el-table-column><el-table-column prop="ownerName" label="负责人" width="90"/><el-table-column prop="responsePlan" label="应对措施" min-width="160"/><el-table-column prop="dueDate" label="截止日期" width="110"/></el-table>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import useUserStore from '@/store/modules/user'
import { canReportProgress } from '@/utils/projectProgress'
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'
import { isDeliveryEnded as ended } from '@/utils/businessProjectState'
import { flattenProjectRows } from '@/utils/projectHierarchy'
import { getBusinessProjectHierarchy, getBusinessProjectChildren, getBusinessProject, deleteBusinessProject } from '@/api/business/project'

const userStore = useUserStore()
const props = defineProps({ query: { type: Object, required: true } })
const emit = defineEmits(['create', 'edit', 'detail', 'deleted', 'report', 'progress'])
const records = ref([]), expanded = ref(new Set()), loading = ref(false), deleting = ref(null), loadError = ref(false)
const page = ref(1), pageSize = ref(10), total = ref(0), tableRef = ref(null)
const children = ref({}), childLoading = ref({}), childErrors = ref({})
const roots = computed(() => records.value.map(row => ({ ...row, children: children.value[row.projectId] || [], childLoading: !!childLoading.value[row.projectId], childError: !!childErrors.value[row.projectId] })))
const visibleRows = computed(() => flattenProjectRows(roots.value, expanded.value))
const managementLabels = { LIGHT: '轻量模式', STANDARD: '标准模式', KEY_CONTROL: '重点监管', SIMPLE: '轻量模式', DELIVERY: '标准模式' }
const closeLabels = { DIRECT: '直接结项', RESULT_ACCEPTANCE: '成果验收', STAGED_ACCEPTANCE: '阶段验收' }
const typeLabels = { LIVE: '直播', JEWELRY: '珠宝', ECOMMERCE: '电商', OPERATIONS: '运营', INTERNAL: '内部', GENERAL: '通用', OTHER: '其他' }
const accountingLabels = { PROFIT: '利润项目', COST: '成本项目', VALUE: '价值项目', HYBRID: '混合核算' }
const roleLabels = { OWNER: '主负责人', DEPUTY: '副负责人', MEMBER: '成员', OBSERVER: '观察者' }
const severityLabels = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }
const riskLabels = { OPEN: '待处理', MITIGATED: '已缓解', CLOSED: '已关闭' }
watch(() => props.query, () => { page.value = 1; refresh() }, { deep: true })
async function toggle(id) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
  if (next.has(id)) await loadChildren(id)
}
function rowClass({ row }) {
  const match = records.value.some(root => root.matchedChildId === row.projectId)
  return [row.depth ? 'subproject-row' : 'main-project-row', match ? 'search-match-row' : ''].join(' ')
}
function spanMethod({ row, columnIndex }) { if (row.empty) return columnIndex === 0 ? [1, 13] : [0, 0] }
let loadSequence = 0
const childRequests = new Map()
async function loadChildren(parentId, force = false) {
  if (childRequests.has(parentId)) return childRequests.get(parentId)
  if (!force && Object.hasOwn(children.value, parentId)) return
  const sequence = loadSequence
  childLoading.value[parentId] = true; childErrors.value[parentId] = false
  const request = (async () => {
    try {
      const result = await getBusinessProjectChildren(parentId)
      if (sequence === loadSequence) children.value[parentId] = result.data || []
    } catch { if (sequence === loadSequence) childErrors.value[parentId] = true }
    finally {
      if (sequence === loadSequence) { childLoading.value[parentId] = false; childRequests.delete(parentId) }
    }
  })()
  childRequests.set(parentId, request)
  return request
}
async function refresh() {
  const sequence = ++loadSequence
  loading.value = true; loadError.value = false
  children.value = {}; childLoading.value = {}; childErrors.value = {}; childRequests.clear()
  try {
    const result = await getBusinessProjectHierarchy({ ...props.query, pageNum: page.value, pageSize: pageSize.value })
    if (sequence !== loadSequence) return
    records.value = result.rows || []; total.value = result.total || 0
    if (!records.value.length && page.value > 1) { page.value--; return refresh() }
    const matching = records.value.filter(row => row.matchedChildId != null)
    expanded.value = new Set([...expanded.value, ...matching.map(row => row.projectId)])
    await Promise.all(records.value.filter(row => expanded.value.has(row.projectId)).map(row => loadChildren(row.projectId)))
    if (sequence !== loadSequence || !matching.length) return
    await nextTick()
    const target = tableRef.value?.$el.querySelector('[data-project-id="' + matching[0].matchedChildId + '"]')
    target?.closest('tr')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  } catch { if (sequence === loadSequence) loadError.value = true }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function updated(row) {
  if (row.parentId) { await loadChildren(row.parentId, true) }
  else records.value = records.value.map(existing => existing.projectId === row.projectId ? { ...existing, ...row, manageable: existing.manageable, contextOnly: existing.contextOnly } : existing)
}
async function refreshChildren(parentId) {
  if (!records.value.some(row => row.projectId === parentId)) return
  expanded.value = new Set([...expanded.value, parentId])
  await loadChildren(parentId, true)
}
async function remove(row) {
  try { await ElMessageBox.confirm(`确定删除${row.parentId ? '子项目' : '主项目'}“${row.projectName}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) }
  catch { return }
  deleting.value = row.projectId
  try {
    await deleteBusinessProject(row.projectId)
    if (row.parentId) children.value[row.parentId] = (children.value[row.parentId] || []).filter(item => item.projectId !== row.projectId)
    else { records.value = records.value.filter(item => item.projectId !== row.projectId); total.value--; await refresh() }
    expanded.value.delete(row.projectId)
    emit('deleted', row)
    ElMessage.success('项目已删除')
  } catch { /* The shared request handler displays the server's rejection reason. */ }
  finally { deleting.value = null }
}
const summaryOpen = ref(false), summaryLoading = ref(false), summaryName = ref(''), summary = ref({}), summaryError = ref(false)
let summarySequence = 0
async function showPeopleRisks(row) {
  const sequence = ++summarySequence
  summaryName.value = row.projectName; summary.value = {}; summaryOpen.value = true; summaryLoading.value = true; summaryError.value = false
  try { const result = await getBusinessProject(row.projectId); if (sequence === summarySequence) summary.value = result.data || {} }
  catch { if (sequence === summarySequence) summaryError.value = true }
  finally { if (sequence === summarySequence) summaryLoading.value = false }
}
defineExpose({ refresh, updated, refreshChildren })
</script>

<style scoped>
small{display:block;color:var(--el-text-color-secondary);margin-top:5px;font-size:12px}
:deep(.subproject-row.search-match-row){--el-table-tr-bg-color:#fff6d9}
.child-tag{margin-right:8px}.danger{color:var(--el-color-danger)}
:deep(.toggle-cell .cell){padding:0 6px;overflow:visible;text-overflow:clip}
.row-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.row-actions .el-button{margin:0}
:deep(.subproject-row){--el-table-tr-bg-color:#f5f8fc}:deep(.main-project-row td){padding-top:17px;padding-bottom:17px}
.empty-children{padding:12px 48px;color:var(--el-text-color-secondary);text-align:left}
.summary-content{min-height:200px}.summary-content h3{margin:20px 0 12px;font-size:15px}
</style>
