<template>
  <div class="app-container business-page">
    <header class="hero">
      <div>
        <span class="eyebrow">OWNER COMMAND CENTER</span>
        <h1>老板工作台</h1>
        <p>只保留需要判断的事项、今日经营结果和项目整体状态。</p>
      </div>
      <div class="hero-actions">
        <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" icon="DocumentChecked" @click="openProposals">立项审批</el-button>
      </div>
    </header>

    <section class="panel pending-panel">
      <div class="section-title">
        <h2>待处理</h2>
        <span>· {{ totalPendingCount ? `${totalPendingCount} 项待处理` : '已全部处理' }}</span>
      </div>
      <div v-if="!groupedPendingRows.length && !loading" class="empty-state success-empty">
        <span>✓</span>当前没有需要老板处理的事项
      </div>

      <article
        v-for="row in groupedPendingRows"
        :key="row.itemKey"
        :class="['decision-row', { 'personnel-row': row.category === 'PERSONNEL_COST_GROUP' }]"
      >
        <span :class="['decision-dot', pendingDotClass(row)]"></span>

        <div v-if="row.category === 'PERSONNEL_COST_GROUP'" class="decision-copy">
          <div class="decision-title">
            <b>人员成本未设置</b>
            <span class="decision-count">{{ personnelRows.length }} 人</span>
          </div>
          <p>{{ personnelPreview }}· 尚未设置今日生效的月度用人成本</p>

          <div v-if="personnelExpanded" class="personnel-list">
            <div v-for="person in personnelRows" :key="person.itemKey" class="personnel-item">
              <b>{{ person.userName }}</b>
              <span>{{ personnelMeta(person) }}</span>
              <el-button size="small" :type="person.costStatus === 'MISSING_REGION' ? 'warning' : ''" plain @click="openSingleCost(person)">
                {{ person.costStatus === 'MISSING_REGION' ? '设置国家' : '设置' }}
              </el-button>
            </div>
          </div>
        </div>

        <div v-else class="decision-copy">
          <div class="decision-title">
            <b>{{ row.category === 'ACCOUNTING' ? row.projectName : (row.userName || row.projectName) }}</b>
            <span :class="['decision-count', pendingBadgeClass(row)]">{{ pendingLabel(row) }}</span>
          </div>
          <p>{{ pendingMeta(row) }}· {{ pendingDescription(row) }}</p>
        </div>

        <div class="decision-actions">
          <template v-if="row.category === 'PERSONNEL_COST_GROUP'">
            <el-button type="primary" size="small" :disabled="!batchEligibleRows.length" @click="openBatchCost">批量设置</el-button>
            <el-button size="small" @click="personnelExpanded = !personnelExpanded">{{ personnelExpanded ? '收起' : '查看人员' }}</el-button>
          </template>
          <template v-else-if="row.category === 'PROPOSAL'">
            <el-button size="small" type="success" @click="decideProposal(row, 'APPROVED')">批准并启动</el-button>
            <el-button size="small" type="warning" plain @click="decideProposal(row, 'RETURNED')">退回修改</el-button>
            <el-button size="small" @click="openProposal(row)">详情</el-button>
          </template>
          <template v-else-if="row.category === 'KPI_MISSING'">
            <el-button size="small" type="primary" @click="openKpi(row)">{{ Number(row.targetCount) ? '发布KPI方案' : '设置KPI' }}</el-button>
            <el-button size="small" @click="openProject(row)">项目详情</el-button>
          </template>
          <template v-else-if="row.category === 'KPI_REVIEW'">
            <el-button size="small" type="primary" @click="openKpi(row)">审核结算</el-button>
            <el-button size="small" @click="openProject(row)">项目详情</el-button>
          </template>
          <template v-else-if="row.category === 'LEAVE_REQUEST'">
            <el-button v-if="row.attachmentUrls" size="small" type="primary" plain @click="openLeaveEvidence(row)">证明附件（{{ evidenceCount(row.attachmentUrls) }}）</el-button>
            <el-button size="small" type="success" @click="decideLeave(row, 'APPROVED')">{{ row.status==='CANCEL_PENDING'?'批准取消':'批准请假' }}</el-button>
            <el-button size="small" type="warning" plain @click="decideLeave(row, 'RETURNED')">{{ row.status==='CANCEL_PENDING'?'保留请假':'退回申请' }}</el-button>
          </template>
          <template v-else-if="row.category === 'STAGE_ACCEPTANCE'">
            <el-button v-if="row.attachmentUrls" size="small" type="primary" plain @click="openStageEvidence(row)">验收文件（{{ evidenceCount(row.attachmentUrls) }}）</el-button>
            <el-button size="small" type="success" @click="openProject(row, 'stageAcceptance')">立即验收</el-button>
          </template>
          <template v-else-if="row.category === 'ACCOUNTING'">
            <el-button size="small" type="success" @click="confirmPendingAccounting(row)">确认入账</el-button>
            <el-button size="small" type="warning" plain @click="returnPendingAccounting(row)">退回修改</el-button>
            <el-button size="small" @click="openPendingAccounting(row)">查看明细</el-button>
          </template>
          <template v-else>
            <el-button
              v-for="action in decisionActions(row)"
              :key="action.key"
              size="small"
              :type="action.type"
              :plain="action.plain"
              @click="doTransition(row, action.key)"
            >{{ action.label }}</el-button>
            <el-button size="small" @click="openProject(row)">详情</el-button>
          </template>
        </div>
      </article>
    </section>

    <section class="panel accounting-overview">
      <div class="section-title section-title--between">
        <h2>今日经营</h2>
        <div class="panel-actions">
          <el-tag v-if="accounting.missingDailyResultCount" type="warning">{{ accounting.missingDailyResultCount }} 个项目待核算</el-tag>
          <el-tag v-if="accounting.draftFactCount" type="warning">{{ accounting.draftFactCount }} 条草稿</el-tag>
          <el-button link type="primary" @click="openAccounting()">进入每日收支</el-button>
        </div>
      </div>
      <div class="finance-grid">
        <article><span>确认收入</span><strong>{{ money(accounting.today?.revenueAmount) }}</strong></article>
        <article><span>总成本</span><strong>{{ money(accounting.today?.costAmount) }}</strong></article>
        <article><span>经营结果</span><strong :class="amountTone(accounting.today?.profitAmount)">{{ signed(accounting.today?.profitAmount) }}</strong></article>
      </div>
      <div v-if="!accounting.alerts?.length" class="healthy-banner">✓ 今日无亏损、超预算或项目归属异常</div>
      <div v-else class="alert-section">
        <div class="subsection-head">
          <div><b>经营异常</b><span>优先关注亏损、预算和项目归属问题</span></div>
          <el-tag type="danger" effect="plain" round>{{ accounting.alerts.length }} 项</el-tag>
        </div>
        <div class="alert-grid">
          <button
            v-for="alert in visibleAlerts"
            :key="`${alert.alertType}-${alert.projectId}`"
            :class="['alert-card', `alert-card--${alertClass(alert.alertType)}`]"
            @click="alert.alertType === 'MISSING_COMPANY' ? openProject(alert) : openAccounting({ projectId: alert.projectId })"
          >
            <span class="alert-icon">!</span>
            <span class="alert-content"><b>{{ alert.projectName }}</b><span>{{ alert.alertMessage }}</span></span>
            <span class="alert-arrow">›</span>
          </button>
        </div>
        <div v-if="accounting.alerts.length > visibleAlerts.length" class="alert-footer">
          <el-button link type="primary" @click="openAccounting()">查看全部异常</el-button>
        </div>
      </div>
    </section>

    <section class="panel project-panel">
      <div class="section-title section-title--between">
        <h2>项目状态</h2>
        <el-button link type="primary" @click="router.push('/business/projects')">查看全部项目</el-button>
      </div>
      <div v-loading="projectLoading" class="project-grid">
        <article v-for="row in projects" :key="row.projectId" class="project-card">
          <div class="project-card-head">
            <button class="project-link" @click="openProject(row)">{{ row.projectName }}</button>
            <span><el-tag size="small" effect="plain">{{ managementLabel[row.managementMode] || row.managementMode }}</el-tag><el-tag size="small" type="success" effect="plain">{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</el-tag><el-tag :type="statusTone[row.status] || 'info'" effect="light" round>{{ projectStatusLabel(row) }}</el-tag></span>
          </div>
          <div class="progress-row">
            <span>进度</span>
            <el-progress :percentage="progress(row)" :status="row.status === 'CLOSED' ? 'success' : undefined" :stroke-width="8" />
            <span :title="progressHint(row)">{{ progressText(row) }}</span>
          </div>
          <div v-if="row.progressReportId" class="latest-progress-report">
            <div><span>{{ row.progressBizDate }} · {{ row.progressReporterName || row.mainOwnerName }}填报</span><b>{{ row.progressSummary }}</b></div>
            <el-button v-if="row.progressEvidenceUrls" size="small" type="primary" plain @click="openProgressEvidence(row)">成果凭证（{{ evidenceCount(row.progressEvidenceUrls) }}）</el-button>
          </div>
          <div v-else-if="!['CLOSED','CANCELED'].includes(row.status)" class="latest-progress-empty">负责人尚未填报项目整体进度</div>
          <div class="project-card-foot">
            <span>{{ row.mainOwnerName || '未指定' }} 负责</span>
            <el-tag size="small" :type="kpiMeta(row).tone" effect="light">KPI {{ kpiMeta(row).label }}</el-tag>
            <span>风险：{{ row.openRiskCount ? `${row.openRiskCount} 项` : '无' }}</span>
            <span class="project-actions">
              <el-button v-if="kpiMeta(row).label !== '已确认'" size="small" :type="kpiMeta(row).action === '查看KPI' ? 'success' : 'primary'" @click="openKpi(row)">{{ kpiMeta(row).action }}</el-button>
              <el-button size="small" @click="openProject(row)">详情</el-button>
            </span>
          </div>
        </article>
        <div v-if="!projects.length && !projectLoading" class="empty-state">尚未创建项目</div>
      </div>
      <div v-if="projectPage.total > projectPage.pageSize" class="project-pagination">
        <span>第 {{ projectPage.pageNum }} 页，共 {{ projectPage.total }} 个项目</span>
        <el-pagination v-model:current-page="projectPage.pageNum" :page-size="projectPage.pageSize" :total="projectPage.total" layout="prev, pager, next" background @current-change="loadProjectPage" />
      </div>
    </section>

    <el-dialog v-model="costDialogOpen" :title="costDialogTitle" width="min(580px, 94vw)" append-to-body destroy-on-close>
      <el-alert title="仅用于公司内部项目核算，不代表员工工资单。每次保存都会产生新版本，历史成本不会被覆盖。" type="warning" :closable="false" show-icon />
      <el-form :model="costForm" label-width="126px" class="cost-form">
        <el-form-item label="人员">
          <el-input :model-value="costPersonText" disabled />
          <div v-if="costDialogMode === 'batch'" class="form-help">保存后将为上述人员各自产生一个新的成本版本。<span v-if="batchUnavailableCount"> 另有 {{ batchUnavailableCount }} 人需先设置国家/地区，本次不处理。</span></div>
        </el-form-item>
        <el-form-item label="国家/地区">
          <el-input :model-value="costRegionText" disabled />
        </el-form-item>
        <el-form-item label="月度用人成本" required>
          <el-input-number v-model="costForm.unitCost" :min="0" :precision="2" :step="100" controls-position="right" style="width:100%" />
          <div class="form-help">统一使用人民币（CNY）填写。</div>
        </el-form-item>
        <div class="cost-preview">
          <span>系统折算</span>
          <template v-for="preview in costPreviews" :key="preview.region">
            <b>{{ preview.formula }}</b>
            <small>{{ preview.hint }}</small>
          </template>
        </div>
        <el-form-item label="生效日期" required>
          <el-date-picker v-model="costForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker v-model="costForm.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width:100%" />
        </el-form-item>
        <el-form-item label="调整说明">
          <el-input v-model="costForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：转正后调整月度用人成本" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="costDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="costSaving" @click="submitCostPolicy">{{ costSubmitLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evidenceDialog" :title="`${evidencePreview.title || ''} · ${evidencePreview.label || '项目成果凭证'}`" width="min(840px, 96vw)" append-to-body destroy-on-close>
      <div class="evidence-dialog-summary"><span>{{ evidencePreview.submitter || '项目负责人' }}提交</span><span>{{ evidencePreview.date }}</span><span>共 {{ evidencePreview.files.length }} 个文件</span></div>
      <business-file-upload
        :model-value="evidencePreview.rawUrls"
        :project-id="evidencePreview.projectId"
        disabled
        :drag="false"
        :is-show-tip="false"
      />
      <template #footer><el-button type="primary" @click="evidenceDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessBoss">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBossBusinessDashboard, getBossBusinessPending, transitionBusinessProject, reviewBusinessMemberLeave } from '@/api/business/project'
import { confirmBusinessOperatingFact, getBusinessBossAccountingOverview, returnBusinessOperatingFact } from '@/api/business/accounting'
import { getProjectKpiOverview } from '@/api/business/kpi'
import { reviewProjectProposal } from '@/api/business/proposal'
import { saveBusinessStaffCostPolicies, saveBusinessStaffCostPolicy } from '@/api/business/staff'

const router = useRouter()
const loading = ref(false)
const projectLoading = ref(false)
const summary = ref({})
const projects = ref([])
const projectPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const kpiOverviews = ref([])
const accounting = ref({ today: {}, alerts: [], draftFactCount: 0 })
const pendingRows = ref([])
const pendingCounts = ref({})
const pendingTotal = ref(0)
const personnelExpanded = ref(false)
const costDialogOpen = ref(false)
const costDialogMode = ref('single')
const selectedPersonnel = ref(null)
const costSaving = ref(false)
const evidenceDialog = ref(false)
const evidencePreview = ref({ title: '', label: '', submitter: '', date: '', rawUrls: '', projectId: null, files: [] })
const costForm = reactive({ unitCost: null, effectiveFrom: '', effectiveTo: null, remark: '' })

const statusLabel = { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '已暂停', ACCEPTANCE: '待验收', CLOSED: '已关闭', CANCELED: '已取消' }
const statusTone = { DRAFT: 'info', PLANNING: 'warning', ACTIVE: 'primary', PAUSED: 'info', ACCEPTANCE: 'success', CLOSED: 'success', CANCELED: 'danger' }
const managementLabel={LIGHT:'轻量',STANDARD:'标准',KEY_CONTROL:'重点监管',SIMPLE:'轻量',DELIVERY:'标准'}
const closeMethodLabel={DIRECT:'直接结项',RESULT_ACCEPTANCE:'成果验收',STAGED_ACCEPTANCE:'阶段验收'}
const actionMeta = { START_PLANNING: { label: '进入规划', type: 'primary' }, CONFIRM_BASELINE: { label: '确认并启动', type: 'success' }, RETURN_PLAN: { label: '退回计划', type: 'warning', plain: true }, RESUME: { label: '恢复执行', type: 'primary' }, REVIEW_ACCEPTANCE: { label: '查看验收资料', type: 'success' }, CLOSE: { label: '确认结项', type: 'success' }, RETURN_ACTIVE: { label: '退回补充', type: 'warning', plain: true } }
const projectStatusLabel = row => row?.status === 'ACCEPTANCE' && row?.closeMethod === 'STAGED_ACCEPTANCE' ? '待结项' : statusLabel[row?.status] || row?.status

const idKey = value => value === null || value === undefined ? '' : String(value)
const kpiOverviewMap = computed(() => new Map(kpiOverviews.value.map(item => [idKey(item.projectId), item])))
const totalPendingCount = computed(() => pendingTotal.value)
const personnelRows = computed(() => pendingRows.value.filter(row => row.category === 'PERSONNEL_COST'))
const batchEligibleRows = computed(() => personnelRows.value.filter(row => row.costStatus !== 'MISSING_REGION'))
const batchUnavailableCount = computed(() => personnelRows.value.length - batchEligibleRows.value.length)
const groupedPendingRows = computed(() => {
  const result = []
  let personnelAdded = false
  pendingRows.value.forEach(row => {
    if (row.category !== 'PERSONNEL_COST') return result.push(row)
    if (!personnelAdded) {
      result.push({ ...row, category: 'PERSONNEL_COST_GROUP', itemKey: 'personnel-cost-group' })
      personnelAdded = true
    }
  })
  return result
})
const personnelPreview = computed(() => {
  const names = personnelRows.value.slice(0, 4).map(row => row.userName).join('、')
  return `${names}${personnelRows.value.length > 4 ? ` 等 ${personnelRows.value.length} 人` : ''} `
})
const visibleAlerts = computed(() => (accounting.value.alerts || []).slice(0, 5))
const costDialogTitle = computed(() => costDialogMode.value === 'batch' ? '批量设置月度用人成本' : '设置月度用人成本')
const costSubmitLabel = computed(() => costDialogMode.value === 'batch' ? '批量保存成本版本' : '保存成本版本')
const costTargetRows = computed(() => costDialogMode.value === 'batch' ? batchEligibleRows.value : selectedPersonnel.value ? [selectedPersonnel.value] : [])
const costPersonText = computed(() => costDialogMode.value === 'batch' ? `${costTargetRows.value.length} 名未设置人员` : selectedPersonnel.value?.userName || '—')
const costRegionCounts = computed(() => costTargetRows.value.reduce((result, row) => {
  const region = row.countryRegion
  if (region === 'CN' || region === 'VN') result[region] = (result[region] || 0) + 1
  return result
}, {}))
const costRegionText = computed(() => {
  if (costDialogMode.value === 'single') return regionLabel(selectedPersonnel.value?.countryRegion)
  return [['CN', '中国'], ['VN', '越南']].filter(([code]) => costRegionCounts.value[code]).map(([code, label]) => `${label} ${costRegionCounts.value[code]} 人`).join('、') || '—'
})
const costPreviews = computed(() => {
  const definitions = { CN: { label: '中国', days: 21.75 }, VN: { label: '越南', days: 26 } }
  const regions = costDialogMode.value === 'batch' ? Object.keys(costRegionCounts.value) : [selectedPersonnel.value?.countryRegion].filter(Boolean)
  return regions.map(region => {
    const definition = definitions[region]
    if (!definition) return { region, formula: '— ÷ — 天 = — 元/天', hint: '该国家/地区尚未配置折算规则' }
    const monthly = costForm.unitCost === null || costForm.unitCost === undefined ? '—' : costMoney(costForm.unitCost)
    const daily = costForm.unitCost === null || costForm.unitCost === undefined ? '—' : costMoney(Number(costForm.unitCost) / definition.days)
    const countHint = costDialogMode.value === 'batch' ? `，本次 ${costRegionCounts.value[region]} 人` : ''
    return { region, formula: `${monthly} ÷ ${definition.days} 天 = ${daily} 元/天`, hint: `${definition.label}员工按 ${definition.days} 天${countHint}` }
  })
})

const progress = row => {
  if (row.status === 'CLOSED') return 100
  if (row.status === 'CANCELED') return 0
  const value = Number(row.progressPercent)
  if (Number.isFinite(value)) return Math.min(100, Math.max(0, Math.round(value)))
  return 0
}
const progressText = row => !row.progressReportId && !['CLOSED', 'CANCELED'].includes(row.status) ? '暂无填报' : `${progress(row)}%`
const progressHint = row => row.progressReportId
  ? `项目负责人于 ${row.progressBizDate} 填报，与一次性任务进度独立`
  : row.status === 'CLOSED' ? '项目已正式结项' : row.status === 'CANCELED' ? '项目已取消' : '等待项目负责人填报整体完成进度'
const evidencePaths = value => String(value || '').split(',').map(item => item.trim()).filter(Boolean)
const evidenceCount = value => evidencePaths(value).length
const evidenceName = path => { const clean = path.split('?')[0]; try { return decodeURIComponent(clean.slice(clean.lastIndexOf('/') + 1)) || '成果凭证' } catch { return clean.slice(clean.lastIndexOf('/') + 1) || '成果凭证' } }
const evidenceKind = path => { const ext = path.split('?')[0].split('.').pop()?.toLowerCase(); if (['jpg','jpeg','png','gif','webp','bmp'].includes(ext)) return 'image'; if (['mp4','mov','webm','ogg'].includes(ext)) return 'video'; return 'file' }
function buildEvidenceFiles(value) { return evidencePaths(value).map(path => ({ path, name: evidenceName(path), kind: evidenceKind(path) })) }
function openProgressEvidence(row) { evidencePreview.value = { title: row.projectName, label: '项目成果凭证', submitter: row.progressReporterName || row.mainOwnerName, date: row.progressBizDate, rawUrls: row.progressEvidenceUrls, projectId: row.projectId, files: buildEvidenceFiles(row.progressEvidenceUrls) }; evidenceDialog.value = true }
function openStageEvidence(row) { evidencePreview.value = { title: `${row.projectName} · ${row.milestoneName}`, label: '阶段验收文件', submitter: row.submitterName || row.mainOwnerName, date: row.submittedTime, rawUrls: row.attachmentUrls, projectId: row.projectId, files: buildEvidenceFiles(row.attachmentUrls) }; evidenceDialog.value = true }
function openLeaveEvidence(row) { evidencePreview.value = { title: `${row.userName} · 请假申请`, label: '请假证明附件', submitter: row.submitterName || row.mainOwnerName, date: row.submittedTime, rawUrls: row.attachmentUrls, projectId: row.projectId || row.submittedProjectId, files: buildEvidenceFiles(row.attachmentUrls) }; evidenceDialog.value = true }
function projectIdFromRow(row) {
  if (idKey(row?.projectId)) return row.projectId
  const keyMatch = idKey(row?.itemKey).match(/^kpi-(?:missing|review)-(\d+)$/)
  if (keyMatch) return keyMatch[1]
  return projects.value.find(project => project.projectName === row?.projectName)?.projectId
}
const openProject = (row, tab) => {
  const projectId = projectIdFromRow(row)
  if (!idKey(projectId)) return ElMessage.warning('未识别到当前项目，请刷新后重试')
  router.push({ path: '/business/projects', query: { id: projectId, ...(tab ? { tab } : {}) } })
}
const openProposals = () => router.push({ path: '/business/project-proposals', query: { tab: 'review' } })
const openProposal = row => router.push({ path: '/business/project-proposals', query: { tab: 'review', id: row.proposalId } })
const openAccounting = (query = {}) => router.push({ path: '/business/accounting', query })
const openPendingAccounting = row => openAccounting({ projectId: row.projectId, dateFrom: row.bizDate, dateTo: row.bizDate })
const openKpi = row => {
  const projectId = projectIdFromRow(row)
  if (!idKey(projectId)) return ElMessage.warning('未识别到当前项目，请刷新后重试')
  const overview = kpiOverviewMap.value.get(idKey(projectId))
  const planId = row.planId || overview?.planId
  router.push({ path: '/business/kpi-bonus', query: { projectId, ...(planId ? { planId } : {}) } })
}
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const costMoney = value => value === null || value === undefined ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const regionLabel = value => value === 'CN' ? '中国' : value === 'VN' ? '越南' : value || '未设置'
const signed = value => `${Number(value || 0) > 0 ? '+' : ''}${money(value)}`
const amountTone = value => Number(value || 0) < 0 ? 'amount-loss' : 'amount-profit'
const alertClass = type => String(type || 'warning').toLowerCase().replaceAll('_', '-')
const kpiMeta = row => {
  const overview = kpiOverviewMap.value.get(idKey(row.projectId))
  if (!overview || !Number(overview.targetCount)) return { label: '未设置', tone: 'warning', action: '设置KPI' }
  if (!overview.planId) return { label: '待发布', tone: 'warning', action: '继续配置' }
  const meta = { DRAFT: { label: '填报中', tone: 'primary' }, SUBMITTED: { label: '待确认', tone: 'warning' }, RETURNED: { label: '已退回', tone: 'danger' }, CONFIRMED: { label: '已确认', tone: 'success' } }[overview.settlementStatus]
  return { ...(meta || { label: `方案 v${overview.planVersion}`, tone: 'info' }), action: overview.settlementStatus === 'SUBMITTED' ? '审核结算' : '查看KPI' }
}
const isStagedClosePending = row => row.status === 'ACCEPTANCE' && row.description === 'STAGED_ACCEPTANCE'
const decisionActions = row => row.status === 'DRAFT' ? [{ key: 'START_PLANNING', ...actionMeta.START_PLANNING }] : row.status === 'PLANNING' && row.baselineStatus === 'SUBMITTED' ? [{ key: 'CONFIRM_BASELINE', ...actionMeta.CONFIRM_BASELINE }, { key: 'RETURN_PLAN', ...actionMeta.RETURN_PLAN }] : row.status === 'PAUSED' ? [{ key: 'RESUME', ...actionMeta.RESUME }] : isStagedClosePending(row) ? [{ key: 'CLOSE', ...actionMeta.CLOSE }, { key: 'RETURN_ACTIVE', ...actionMeta.RETURN_ACTIVE }] : row.status === 'ACCEPTANCE' ? [{ key: 'REVIEW_ACCEPTANCE', ...actionMeta.REVIEW_ACCEPTANCE }] : []
const decisionHint = row => row.status === 'DRAFT' ? '历史草稿等待确认进入规划' : row.status === 'PLANNING' ? '历史计划已提交，等待确认或退回' : row.status === 'PAUSED' ? '项目处于暂停状态，决定是否恢复执行' : isStagedClosePending(row) ? '所有里程碑和结项前置条件已完成，负责人申请确认结项' : row.status === 'ACCEPTANCE' ? '验收资料已提交，等待关闭或退回执行' : '需要老板处理'
const accountingValue = row => row.factKind === 'VALUE' ? `${row.quantity ?? '—'} ${row.unit || ''}`.trim() : `${money(row.amount)} ${row.currency || ''}`.trim()
const leaveTypeLabel={SICK:'病假',PERSONAL:'事假',ANNUAL:'年假',COMPENSATORY:'调休',OTHER:'其他'}
const pendingLabel = row => row.category === 'PROPOSAL' ? '立项待审批' : row.category === 'ACCOUNTING' ? '收支待确认' : row.category === 'LEAVE_REQUEST' ? (row.status==='CANCEL_PENDING'?'取消请假待审批':'请假待审批') : row.category === 'STAGE_ACCEPTANCE' ? '待阶段验收' : row.category === 'KPI_MISSING' ? (Number(row.targetCount) ? 'KPI 待发布' : 'KPI 待设置') : row.category === 'KPI_REVIEW' ? 'KPI 结算待确认' : isStagedClosePending(row) ? '项目待结项' : '项目状态待处理'
const pendingDotClass = row => row.category === 'KPI_MISSING' ? 'dot-danger' : ['PERSONNEL_COST_GROUP', 'PROPOSAL', 'ACCOUNTING', 'LEAVE_REQUEST', 'STAGE_ACCEPTANCE', 'KPI_REVIEW'].includes(row.category) ? 'dot-warning' : 'dot-info'
const pendingBadgeClass = row => row.category === 'KPI_MISSING' ? 'badge-danger' : ['PROPOSAL', 'ACCOUNTING', 'LEAVE_REQUEST', 'STAGE_ACCEPTANCE', 'KPI_REVIEW'].includes(row.category) ? 'badge-warning' : 'badge-info'
const pendingDescription = row => row.category === 'PROPOSAL' ? (row.objective || '新的立项申请等待审批') : row.category === 'ACCOUNTING' ? `${row.categoryName || '项目收支'}：${row.description || '负责人提交的今日收支'}（${accountingValue(row)}）` : row.category === 'LEAVE_REQUEST' ? `${leaveTypeLabel[row.categoryName]||row.categoryName}：${row.description||'未填写原因'}` : row.category === 'STAGE_ACCEPTANCE' ? `${row.resultSummary || '负责人已提交阶段成果'} · 交付成果：${row.deliverables || '—'}` : row.category === 'KPI_MISSING' ? (Number(row.targetCount) ? `已有 ${row.targetCount} 项 KPI 目标，但尚未发布考核与奖金方案` : '项目已进入执行流程，KPI 目标待设置') : row.category === 'KPI_REVIEW' ? '负责人已提交 KPI 结果，确认后项目奖金会立即计入成本' : decisionHint(row)
const pendingMeta = row => {
  if (row.category === 'PROPOSAL') return `${row.applicantName} 负责 · ${row.companyName || '未设置公司'} `
  if (row.category === 'ACCOUNTING') return `${row.submitterName || '项目负责人'}提交 · ${row.bizDate || '—'} · ${row.companyName || '未设置公司'} `
  if (row.category === 'LEAVE_REQUEST') return `${row.submitterName || row.mainOwnerName || '项目负责人'}提交 · ${row.planStartDate}${row.planEndDate===row.planStartDate?'':` 至 ${row.planEndDate}`} · ${row.projectName} `
  if (row.category === 'STAGE_ACCEPTANCE') return `${row.submitterName || row.mainOwnerName || '项目负责人'}提交 · 里程碑“${row.milestoneName || '未命名'}” · ${row.submittedTime || '—'} `
  if (row.category === 'KPI_REVIEW') return `方案 v${row.planVersion} · 截止 ${row.cycleEnd || '—'} · 综合得分 ${row.totalScore ?? '—'} `
  return `${row.mainOwnerName || '未指定负责人'} 负责 `
}
const personnelMeta = row => `${row.companyName || '未设置所属公司'} · ${row.projectNameText || '尚未加入未结束项目'}`

function localToday() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}
function resetCostForm() {
  costForm.unitCost = null
  costForm.effectiveFrom = accounting.value.bizDate || localToday()
  costForm.effectiveTo = null
  costForm.remark = ''
}
function openSingleCost(row) {
  if (row.costStatus === 'MISSING_REGION') return router.push({ path: '/business/staff', query: { userId: row.userId, action: 'edit' } })
  selectedPersonnel.value = row
  costDialogMode.value = 'single'
  resetCostForm()
  costDialogOpen.value = true
}
function openBatchCost() {
  if (!batchEligibleRows.value.length) return ElMessage.warning('请先为人员设置国家/地区')
  selectedPersonnel.value = null
  costDialogMode.value = 'batch'
  resetCostForm()
  costForm.remark = '老板工作台批量设置'
  costDialogOpen.value = true
}
function costPolicyPayload(row) {
  return { userId: row.userId, costMode: 'MONTHLY', unitCost: costForm.unitCost, currency: 'CNY', effectiveFrom: costForm.effectiveFrom, effectiveTo: costForm.effectiveTo, remark: costForm.remark?.trim() || '' }
}
async function submitCostPolicy() {
  if (costForm.unitCost === null || costForm.unitCost === undefined) return ElMessage.warning('请填写月度用人成本')
  if (!costForm.effectiveFrom) return ElMessage.warning('请选择生效日期')
  if (costForm.effectiveTo && costForm.effectiveTo < costForm.effectiveFrom) return ElMessage.warning('失效日期不能早于生效日期')
  costSaving.value = true
  try {
    if (costDialogMode.value === 'batch') {
      await saveBusinessStaffCostPolicies(batchEligibleRows.value.map(costPolicyPayload))
      ElMessage.success(`已为 ${batchEligibleRows.value.length} 名人员设置月度用人成本`)
    } else {
      await saveBusinessStaffCostPolicy(costPolicyPayload(selectedPersonnel.value))
      ElMessage.success(`已设置 ${selectedPersonnel.value.userName} 的月度用人成本`)
    }
    costDialogOpen.value = false
    await load()
  } finally {
    costSaving.value = false
  }
}

async function loadAllPending() {
  const pageSize = 50
  const firstResult = await getBossBusinessPending({ pageNum: 1, pageSize, category: 'ALL' })
  const first = firstResult.data || {}
  const total = Number(first.total || 0)
  const pages = Math.ceil(total / pageSize)
  let rows = first.rows || []
  if (pages > 1) {
    const results = await Promise.all(Array.from({ length: pages - 1 }, (_, index) => getBossBusinessPending({ pageNum: index + 2, pageSize, category: 'ALL' })))
    rows = rows.concat(...results.map(result => result.data?.rows || []))
  }
  return { rows, total, counts: first.counts || {} }
}
async function loadVisibleProjectKpis() {
  const projectIds = projects.value.map(row => row.projectId).filter(Boolean)
  if (!projectIds.length) { kpiOverviews.value = []; return }
  const result = await getProjectKpiOverview({ projectIds: projectIds.join(',') })
  kpiOverviews.value = result.data || []
}
async function load() {
  loading.value = true
  try {
    const [dashboardResult, accountingResult, pending] = await Promise.all([
      getBossBusinessDashboard({ projectPageNum: projectPage.pageNum, projectPageSize: projectPage.pageSize, decisionPageSize: 1 }),
      getBusinessBossAccountingOverview(),
      loadAllPending()
    ])
    const data = dashboardResult.data || {}
    summary.value = data.summary || {}
    const page = data.projectPage || {}
    projects.value = page.rows || data.projects || []
    projectPage.total = Number(page.total ?? summary.value.totalCount ?? projects.value.length)
    projectPage.pageNum = Number(page.pageNum || projectPage.pageNum)
    projectPage.pageSize = Number(page.pageSize || projectPage.pageSize)
    accounting.value = accountingResult.data || { today: {}, alerts: [] }
    pendingRows.value = pending.rows
    pendingTotal.value = pending.total
    pendingCounts.value = pending.counts
    await loadVisibleProjectKpis()
  } finally {
    loading.value = false
  }
}
async function loadProjectPage() {
  projectLoading.value = true
  try {
    const result = await getBossBusinessDashboard({ projectPageNum: projectPage.pageNum, projectPageSize: projectPage.pageSize, decisionPageSize: 1 })
    const data = result.data || {}
    const page = data.projectPage || {}
    projects.value = page.rows || data.projects || []
    projectPage.total = Number(page.total ?? projectPage.total)
    await loadVisibleProjectKpis()
  } finally {
    projectLoading.value = false
  }
}
async function refreshProjectProgress() {
  if (projectLoading.value || document.hidden) return
  const result = await getBossBusinessDashboard({ projectPageNum: projectPage.pageNum, projectPageSize: projectPage.pageSize, decisionPageSize: 1 })
  const data = result.data || {}
  const page = data.projectPage || {}
  projects.value = page.rows || data.projects || []
  projectPage.total = Number(page.total ?? projectPage.total)
}
async function decideProposal(row, decision) {
  let comment = ''
  if (decision === 'RETURNED') {
    const result = await ElMessageBox.prompt('请填写退回原因', '退回立项申请', { inputValidator: value => !!value?.trim() || '必须填写退回原因' })
    comment = result.value
  } else {
    await ElMessageBox.confirm(`批准“${row.projectName}”后将直接创建执行中项目，并由 ${row.applicantName} 负责。确认批准吗？`, '批准立项', { type: 'warning' })
  }
  await reviewProjectProposal(row.proposalId, { decision, comment })
  ElMessage.success(decision === 'APPROVED' ? '已批准立项并启动项目' : '申请已退回修改')
  await load()
}
async function decideLeave(row, decision) {
  let comment = ''
  const canceling = row.status === 'CANCEL_PENDING'
  if (decision === 'RETURNED') {
    const result = await ElMessageBox.prompt(`请填写${canceling?'不批准取消请假':'退回请假申请'}的原因`, canceling?'保留原请假':'退回请假申请', { inputValidator: value => !!value?.trim() || '必须填写原因', type: 'warning' })
    comment = result.value.trim()
  } else {
    await ElMessageBox.confirm(canceling?`批准取消 ${row.userName} 的请假吗？批准后将恢复计划投入并重新核算人员成本。`:`批准 ${row.userName} 在 ${row.planStartDate}${row.planEndDate===row.planStartDate?'':` 至 ${row.planEndDate}`} 的请假吗？批准后其全部相关项目投入按 0 计算。`, canceling?'批准取消请假':'批准请假', { type: 'warning' })
    comment = canceling?'批准取消请假':'批准请假'
  }
  const requestId = String(row.itemKey || '').replace('leave-request-', '')
  await reviewBusinessMemberLeave(requestId, { decision, comment })
  ElMessage.success(canceling?(decision==='APPROVED'?'请假已取消并重新核算相关项目':'已保留原请假'):(decision === 'APPROVED' ? '请假已批准并同步到相关项目' : '请假申请已退回'))
  await load()
}
async function confirmPendingAccounting(row) {
  await ElMessageBox.confirm(`确认“${row.projectName}”的${row.categoryName || '今日收支'} ${accountingValue(row)} 入账吗？确认后将计入正式日报。`, '确认收支入账', { type: 'warning' })
  await confirmBusinessOperatingFact(row.factId)
  ElMessage.success('收支已确认入账并生成项目日结果')
  await load()
}
async function returnPendingAccounting(row) {
  const { value } = await ElMessageBox.prompt(
    `请说明“${row.projectName}”的${row.categoryName || '今日收支'}需要修改的内容，提交人将看到该原因。`,
    '退回收支修改',
    { inputValidator: text => !!text?.trim() || '必须填写退回原因', inputAttributes: { maxlength: 500 }, type: 'warning' }
  )
  await returnBusinessOperatingFact(row.factId, { reason: value.trim() })
  ElMessage.success('收支已退回提交人修改')
  await load()
}
async function doTransition(row, action) {
  if (action === 'REVIEW_ACCEPTANCE') return openProject(row, 'acceptance')
  const meta = actionMeta[action] || { label: '执行操作' }
  let comment = ''
  if (['RETURN_PLAN', 'RETURN_ACTIVE', 'CLOSE'].includes(action)) {
    const result = await ElMessageBox.prompt(action === 'CLOSE' ? '请填写项目完成结论' : `请输入“${meta.label}”的原因，负责人将在项目动态中看到`, '老板决策', { inputValidator: value => !!value?.trim() || '必须填写说明' })
    comment = result.value
  } else {
    await ElMessageBox.confirm(`确定对“${row.projectName}”执行“${meta.label}”吗？`, '老板确认', { type: 'warning' })
  }
  await transitionBusinessProject(row.projectId, { action, comment })
  ElMessage.success('操作成功')
  await load()
}

let progressRefreshTimer
onMounted(() => {
  load()
  progressRefreshTimer = window.setInterval(() => refreshProjectProgress().catch(() => {}), 15000)
})
onBeforeUnmount(() => window.clearInterval(progressRefreshTimer))
</script>

<style scoped>
.business-page{min-height:calc(100vh - 84px);padding:24px;background:#eef1f5;color:#12213a}.hero{display:flex;align-items:center;justify-content:space-between;min-height:134px;padding:26px 40px;border-radius:18px;background:#1d344f;color:#fff;box-shadow:0 12px 30px rgba(27,48,74,.13)}.eyebrow{font-size:12px;letter-spacing:.28em;color:#78ecd1}.hero h1{margin:15px 0 8px;font-size:30px;line-height:1}.hero p{margin:0;color:#d2deea;font-size:15px}.hero-actions,.panel-actions{display:flex;align-items:center;gap:10px}.hero-actions :deep(.el-button){height:42px;padding:0 20px;border-radius:11px;font-weight:700}.panel{margin-top:20px;padding:24px 26px;border:0;border-radius:17px;background:#fff;box-shadow:0 7px 20px rgba(29,50,75,.06)}.section-title{display:flex;align-items:baseline;gap:7px;margin-bottom:18px}.section-title h2{margin:0;font-size:19px}.section-title>span{color:#8493a7;font-size:13px}.section-title--between{align-items:center;justify-content:space-between}.empty-state{padding:30px;text-align:center;color:#93a0b1}.success-empty{border-radius:10px;background:#edf9f2;color:#18a856}.success-empty span{margin-right:8px;font-weight:800}.decision-row{display:flex;align-items:center;gap:16px;padding:19px 20px;border:1px solid #dfe6ef;border-radius:14px}.decision-row+.decision-row{margin-top:14px}.decision-dot{width:10px;height:10px;flex:none;border-radius:50%}.dot-danger{background:#ef323a}.dot-warning{background:#df7c00}.dot-info{background:#4a83d8}.decision-copy{min-width:0;flex:1}.decision-title{display:flex;align-items:center;gap:10px}.decision-title b{font-size:16px}.decision-count{color:#df7c00;font-weight:700}.badge-danger{color:#e04b00}.badge-warning{color:#df7c00}.badge-info{color:#3f75bd}.decision-copy>p{margin:7px 0 0;color:#8493a7;font-size:14px;line-height:1.55}.decision-actions{display:flex;flex:none;align-self:flex-start;flex-wrap:wrap;justify-content:flex-end;gap:8px}.decision-actions :deep(.el-button){margin:0;font-weight:650}.personnel-list{margin-top:14px;border-top:1px dashed #dce4ee}.personnel-item{display:grid;grid-template-columns:110px minmax(0,1fr) auto;align-items:center;gap:18px;padding:10px 2px;border-bottom:1px dashed #dce4ee}.personnel-item>b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.personnel-item>span{overflow:hidden;color:#8493a7;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.finance-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.finance-grid article{padding:20px;border:1px solid #dfe6ef;border-radius:13px;background:#fafbfd}.finance-grid span,.finance-grid strong{display:block}.finance-grid span{color:#8794a8;font-size:14px}.finance-grid strong{margin-top:12px;font-size:29px;line-height:1}.amount-profit{color:#11a957}.amount-loss{color:#d84e58}.healthy-banner{margin-top:15px;padding:11px 16px;border-radius:10px;background:#e7f7ed;color:#11a957;font-size:14px}.alert-section{margin-top:16px;padding:16px;border:1px solid #e5eaf0;border-radius:12px;background:#f8fafc}.subsection-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.subsection-head>div{display:flex;align-items:baseline;gap:10px}.subsection-head span{color:#8a95a2;font-size:12px}.alert-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.alert-card{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:11px;padding:14px;border:1px solid #e0e6ec;border-radius:11px;background:#fff;color:inherit;text-align:left;cursor:pointer}.alert-card:hover{border-color:#b9c7d5;box-shadow:0 7px 18px rgba(31,53,74,.09)}.alert-icon{display:flex;width:30px;height:30px;align-items:center;justify-content:center;border-radius:9px;background:#fff0f1;color:#d94e58;font-weight:800}.alert-card--over-budget .alert-icon{background:#fff5e6;color:#c8841c}.alert-card--missing-company .alert-icon{background:#eef4fb;color:#4f78a8}.alert-content{display:flex;min-width:0;flex-direction:column}.alert-content>b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.alert-content>span{margin-top:4px;color:#788695;font-size:12px}.alert-arrow{color:#a3adb8;font-size:24px}.alert-footer{display:flex;justify-content:flex-end;padding-top:8px}.project-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;min-height:60px}.project-card{padding:19px 20px;border:1px solid #dfe6ef;border-radius:14px}.project-card-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.project-link{min-width:0;overflow:hidden;padding:0;border:0;background:none;color:#13213a;font:inherit;font-size:16px;font-weight:700;text-align:left;text-overflow:ellipsis;white-space:nowrap;cursor:pointer}.project-link:hover{color:#3478ef}.progress-row{display:grid;grid-template-columns:auto minmax(80px,1fr) auto;align-items:center;gap:14px;margin-top:18px;color:#8493a7;font-size:13px}.progress-row :deep(.el-progress__text){display:none}.progress-row :deep(.el-progress){width:100%}.project-card-foot{display:flex;align-items:center;gap:10px;margin-top:14px;color:#8493a7;font-size:13px}.project-actions{display:flex;margin-left:auto;gap:8px}.project-actions :deep(.el-button){margin:0}.project-pagination{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:18px}.project-pagination>span{color:#7e8a98;font-size:12px}.cost-form{margin-top:18px}.cost-form :deep(.el-form-item){margin-bottom:20px}.form-help{margin-top:6px;color:#8490a0;font-size:12px;line-height:1.5}.cost-preview{display:grid;gap:5px;margin:-4px 0 18px 126px;padding:13px 15px;border:1px solid #cfe3df;border-radius:9px;background:#f0f8f6}.cost-preview span,.cost-preview small{color:#71828c;font-size:12px}.cost-preview b{color:#174f4f;font-size:15px}.cost-preview b:not(:first-of-type){margin-top:7px}
.latest-progress-report{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px;padding:11px 12px;border-radius:9px;background:#f0f8f6}.latest-progress-report>div{display:flex;min-width:0;flex-direction:column;gap:4px}.latest-progress-report span,.latest-progress-empty{color:#7c8a96;font-size:12px}.latest-progress-report b{overflow:hidden;color:#40545d;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.latest-progress-report .el-button{flex:none}.latest-progress-empty{margin-top:12px;padding:10px 12px;border-radius:8px;background:#f5f7f9}.evidence-dialog-summary{display:flex;align-items:center;gap:10px;margin-bottom:16px;color:#7a8794;font-size:13px}.evidence-dialog-summary span+span:before{margin-right:10px;color:#c3cbd3;content:'·'}.evidence-preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.evidence-preview-item{min-width:0;padding:10px;border:1px solid #e0e7ec;border-radius:10px;background:#f7f9fa}.evidence-preview-item>.el-image,.evidence-preview-item>video{display:block;width:100%;height:300px;border-radius:7px;background:#eef1f3}.evidence-preview-item>small{display:block;margin-top:8px;overflow:hidden;color:#75818d;text-overflow:ellipsis;white-space:nowrap}.evidence-file-card{display:flex;min-height:150px;align-items:center;justify-content:center;flex-direction:column;gap:12px;padding:20px;text-align:center}.evidence-file-card>.el-icon{color:#7e8c98;font-size:38px}.evidence-file-card>span{max-width:100%;overflow-wrap:anywhere;color:#4d5965}
@media(max-width:1100px){.alert-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.project-card-foot{align-items:flex-start;flex-wrap:wrap}.project-actions{width:100%;margin-left:0}}
@media(max-width:860px){.finance-grid,.project-grid{grid-template-columns:1fr}.section-title--between{align-items:flex-start}.panel-actions{align-items:flex-end;flex-direction:column}}
@media(max-width:760px){.business-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:20px;min-height:0;padding:24px}.hero-actions{width:100%}.hero-actions :deep(.el-button){flex:1;margin:0}.panel{padding:18px 14px}.decision-row{align-items:flex-start;flex-wrap:wrap;padding:16px 14px}.decision-copy{width:calc(100% - 26px)}.decision-actions{width:100%;padding-left:26px;justify-content:flex-start}.decision-actions :deep(.el-button){flex:1}.personnel-item{grid-template-columns:1fr auto;gap:4px 10px}.personnel-item>span{grid-column:1/2;white-space:normal}.personnel-item :deep(.el-button){grid-column:2;grid-row:1/3}.panel-actions{align-items:flex-end}.alert-grid{grid-template-columns:1fr}.project-card{padding:16px 14px}.project-card-foot{align-items:flex-start}.project-actions{display:grid;grid-template-columns:1fr 1fr}.project-actions :deep(.el-button){width:100%}.project-pagination{align-items:flex-end;flex-direction:column}.cost-preview{margin-left:0}.latest-progress-report{align-items:flex-start;flex-direction:column}.evidence-dialog-summary{align-items:flex-start;flex-direction:column;gap:4px}.evidence-dialog-summary span+span:before{content:none}.evidence-preview-grid{grid-template-columns:1fr}.evidence-preview-item>.el-image,.evidence-preview-item>video{height:240px}:global(.el-dialog .cost-form .el-form-item){display:block}:global(.el-dialog .cost-form .el-form-item__label){width:auto!important;height:auto;margin-bottom:6px;padding:0}:global(.el-dialog .cost-form .el-form-item__content){margin-left:0!important}}
</style>
