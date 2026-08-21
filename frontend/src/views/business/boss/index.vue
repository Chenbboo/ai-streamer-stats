<template>
  <div class="app-container business-page">
    <header class="hero">
      <div><span class="eyebrow">OWNER COMMAND CENTER</span><h1>老板工作台</h1><p>只保留需要判断的事项、今日经营结果和项目整体状态。</p></div>
      <div class="hero-actions"><el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button><el-button type="primary" icon="DocumentChecked" @click="openProposals">立项审批</el-button></div>
    </header>

    <section class="panel pending-panel">
      <div class="panel-head">
        <div><h2>待老板处理</h2><p>立项、项目状态、KPI、人员成本和结算统一集中在这里。</p></div>
        <el-tag :type="totalPendingCount ? 'warning' : 'success'">{{ totalPendingCount }} 项</el-tag>
      </div>
      <div v-if="!totalPendingCount && !loading" class="empty-state">当前没有需要老板处理的事项</div>

      <div v-if="totalPendingCount" class="pending-toolbar">
        <el-radio-group v-model="pendingFilter" size="small" @change="changePendingFilter">
          <el-radio-button v-for="option in pendingFilterOptions" :key="option.value" :value="option.value">{{ option.label }} {{ option.count }}</el-radio-button>
        </el-radio-group>
        <span>每页 {{ pendingPageSize }} 项</span>
      </div>

      <article v-for="row in pendingRows" :key="row.itemKey" class="decision-row">
        <div class="decision-copy">
          <div class="decision-title"><el-tag size="small" :type="pendingTone(row)">{{ pendingLabel(row) }}</el-tag><b>{{ row.userName || row.projectName }}</b></div>
          <span>{{ pendingDescription(row) }}</span><small>{{ pendingMeta(row) }}</small>
        </div>
        <div class="decision-actions">
          <template v-if="row.category==='PROPOSAL'"><el-button size="small" type="success" @click="decideProposal(row,'APPROVED')">批准并启动</el-button><el-button size="small" type="warning" plain @click="decideProposal(row,'RETURNED')">退回修改</el-button><el-button size="small" @click="openProposal(row)">详情</el-button></template>
          <template v-else-if="row.category==='KPI_MISSING'"><el-button size="small" type="primary" @click="openKpi(row)">{{ Number(row.targetCount) ? '发布KPI方案' : '设置KPI' }}</el-button><el-button size="small" @click="openProject(row)">项目详情</el-button></template>
          <template v-else-if="row.category==='KPI_REVIEW'"><el-button size="small" type="primary" @click="openKpi(row)">审核结算</el-button><el-button size="small" @click="openProject(row)">项目详情</el-button></template>
          <template v-else-if="row.category==='PERSONNEL_COST'"><el-button size="small" type="primary" @click="openPersonnelIssue(row)">{{ row.costStatus==='MISSING_REGION' ? '设置国家' : '设置月薪' }}</el-button></template>
          <template v-else><el-button v-for="action in decisionActions(row)" :key="action.key" size="small" :type="action.type" :plain="action.plain" @click="doTransition(row,action.key)">{{ action.label }}</el-button><el-button size="small" @click="openProject(row)">详情</el-button></template>
        </div>
      </article>

      <div v-if="totalPendingCount > pendingPageSize" class="pending-pagination">
        <span>共 {{ totalPendingCount }} 项</span>
        <el-pagination v-model:current-page="pendingPage" :page-size="pendingPageSize" :total="totalPendingCount" layout="prev, pager, next" background small @current-change="loadPendingPage" />
      </div>
    </section>

    <section class="panel accounting-overview">
      <div class="panel-head"><div><h2>今日经营</h2><p>{{ accounting.bizDate || '—' }} · 已确认口径，详细分项进入每日收支查看。</p></div><div class="panel-actions"><el-tag v-if="accounting.missingDailyResultCount" type="warning">{{ accounting.missingDailyResultCount }} 个项目待核算</el-tag><el-tag v-if="accounting.draftFactCount" type="warning">{{ accounting.draftFactCount }} 条草稿</el-tag><el-button link type="primary" @click="openAccounting()">进入每日收支</el-button></div></div>
      <div class="finance-grid">
        <article><span>确认收入</span><strong>{{ money(accounting.today?.revenueAmount) }}</strong></article>
        <article><span>总成本</span><strong>{{ money(accounting.today?.costAmount) }}</strong><small>业务、人员及项目奖金</small></article>
        <article><span>经营结果</span><strong :class="amountTone(accounting.today?.profitAmount)">{{ signed(accounting.today?.profitAmount) }}</strong></article>
      </div>
      <div class="alert-section">
        <div class="subsection-head">
          <div><b>经营异常</b><span>优先关注亏损、预算和项目归属问题</span></div>
          <el-tag :type="accounting.alerts?.length ? 'danger' : 'success'" effect="plain" round>{{ accounting.alerts?.length || 0 }} 项</el-tag>
        </div>
        <div v-if="!accounting.alerts?.length" class="empty-state compact">暂无亏损、超预算或项目归属异常</div>
        <div v-else class="alert-grid">
          <button v-for="alert in visibleAlerts" :key="`${alert.alertType}-${alert.projectId}`" :class="['alert-card', `alert-card--${alertClass(alert.alertType)}`]" @click="alert.alertType==='MISSING_COMPANY'?openProject(alert):openAccounting({projectId:alert.projectId})">
            <span class="alert-icon">!</span>
            <span class="alert-content">
              <span class="alert-meta"><el-tag size="small" :type="alertTone(alert.alertType)" effect="light">{{ alertLabel[alert.alertType] }}</el-tag><small>点击处理</small></span>
              <b>{{ alert.projectName }}</b>
              <span>{{ alert.alertMessage }}</span>
            </span>
            <span class="alert-arrow">›</span>
          </button>
        </div>
        <div v-if="(accounting.alerts?.length || 0) > visibleAlerts.length" class="alert-footer"><el-button link type="primary" @click="openAccounting()">查看全部异常</el-button></div>
      </div>
    </section>

    <section class="panel project-panel">
      <div class="panel-head"><div><h2>项目概览</h2><p>每个项目只展示一次；项目详情、KPI 和经营数据从这里进入。</p></div><div class="panel-actions"><span class="project-count">共 {{ summary.totalCount || projects.length }} 个</span><el-button link type="primary" @click="router.push('/business/projects')">查看全部项目</el-button></div></div>
      <el-table :data="projects" v-loading="projectLoading" empty-text="尚未创建项目">
        <el-table-column label="项目" min-width="220"><template #default="{ row }"><button class="project-link" @click="openProject(row)">{{ row.projectName }}</button><small class="subline">{{ row.projectNo }} · {{ typeLabel[row.projectType] || row.projectType }}</small></template></el-table-column>
        <el-table-column prop="mainOwnerName" label="负责人" width="120" />
        <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="进度" width="145"><template #default="{ row }"><el-progress :percentage="progress(row)" :stroke-width="8" /></template></el-table-column>
        <el-table-column label="KPI状态" width="125"><template #default="{ row }"><el-tag :type="kpiMeta(row).tone">{{ kpiMeta(row).label }}</el-tag></template></el-table-column>
        <el-table-column label="风险" width="90" align="center"><template #default="{ row }"><el-tag v-if="row.openRiskCount" type="danger">{{ row.openRiskCount }} 项</el-tag><span v-else class="safe-text">无</span></template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openKpi(row)">{{ kpiMeta(row).action }}</el-button><el-button link @click="openProject(row)">详情</el-button></template></el-table-column>
      </el-table>
      <div v-if="projectPage.total > projectPage.pageSize" class="project-pagination">
        <span>第 {{ projectPage.pageNum }} 页，共 {{ projectPage.total }} 个项目</span>
        <el-pagination v-model:current-page="projectPage.pageNum" :page-size="projectPage.pageSize" :total="projectPage.total" layout="prev, pager, next" background @current-change="loadProjectPage" />
      </div>
    </section>
  </div>
</template>

<script setup name="BusinessBoss">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBossBusinessDashboard, getBossBusinessPending, transitionBusinessProject } from '@/api/business/project'
import { getBusinessBossAccountingOverview } from '@/api/business/accounting'
import { getProjectKpiOverview } from '@/api/business/kpi'
import { reviewProjectProposal } from '@/api/business/proposal'

const router = useRouter()
const loading = ref(false)
const projectLoading = ref(false)
const summary = ref({})
const projects = ref([])
const projectPage = reactive({pageNum:1,pageSize:10,total:0})
const kpiOverviews = ref([])
const accounting = ref({today:{},alerts:[],draftFactCount:0})
const statusLabel = { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '已暂停', ACCEPTANCE: '待验收', CLOSED: '已关闭', CANCELED: '已取消' }
const statusTone = { DRAFT: 'info', PLANNING: 'warning', ACTIVE: 'primary', PAUSED: 'info', ACCEPTANCE: 'success', CLOSED: 'success', CANCELED: 'danger' }
const typeLabel = { GENERAL: '通用', LIVE: '直播', JEWELRY: '珠宝', CONTENT: '内容', ECOMMERCE: '电商', OPERATIONS: '运营', INTERNAL: '内部', OTHER: '其他' }
const alertLabel = {LOSS:'当日亏损',OVER_BUDGET:'预算超支',MISSING_COMPANY:'待设公司'}
const alertTone = type => ({LOSS:'danger',OVER_BUDGET:'warning',MISSING_COMPANY:'info'}[type] || 'warning')
const alertClass = type => String(type || 'warning').toLowerCase().replaceAll('_','-')
const actionMeta = {START_PLANNING:{label:'进入规划',type:'primary'},CONFIRM_BASELINE:{label:'确认并启动',type:'success'},RETURN_PLAN:{label:'退回计划',type:'warning',plain:true},RESUME:{label:'恢复执行',type:'primary'},REVIEW_ACCEPTANCE:{label:'查看验收资料',type:'success'}}
const kpiOverviewMap = computed(() => new Map(kpiOverviews.value.map(item => [Number(item.projectId), item])))
const pendingPageSize = 5
const pendingPage = ref(1)
const pendingFilter = ref('ALL')
const pendingRows = ref([])
const pendingCounts = ref({})
const pendingTotal = ref(0)
const totalPendingCount = computed(() => pendingTotal.value)
const pendingFilterOptions = computed(() => [
  {value:'ALL',label:'全部',count:Number(pendingCounts.value.totalCount || 0)},
  {value:'PROPOSAL',label:'立项审批',count:Number(pendingCounts.value.proposalCount || 0)},
  {value:'KPI_MISSING',label:'KPI待设置/发布',count:Number(pendingCounts.value.kpiMissingCount || 0)},
  {value:'KPI_REVIEW',label:'KPI结算确认',count:Number(pendingCounts.value.kpiReviewCount || 0)},
  {value:'PERSONNEL_COST',label:'人员成本',count:Number(pendingCounts.value.personnelCostCount || 0)},
  {value:'PROJECT',label:'项目状态',count:Number(pendingCounts.value.projectCount || 0)}
].filter(option => option.value === 'ALL' || option.count))
const visibleAlerts = computed(() => (accounting.value.alerts || []).slice(0, 5))
const progress = row => row.taskCount ? Math.round((row.completedTaskCount || 0) * 100 / row.taskCount) : 0
const openProject = (row,tab) => router.push({ path: '/business/projects', query: { id: row.projectId, ...(tab?{tab}:{}) } })
const openProposals = () => router.push({path:'/business/project-proposals',query:{tab:'review'}})
const openProposal = row => router.push({path:'/business/project-proposals',query:{tab:'review',id:row.proposalId}})
const openAccounting = (query={}) => router.push({path:'/business/accounting',query})
const openPersonnelIssue = row => router.push({path:'/business/staff',query:{userId:row.userId,action:row.costStatus==='MISSING_REGION'?'edit':'cost'}})
const openKpi = row => { const overview=kpiOverviewMap.value.get(Number(row.projectId)); const planId=row.planId || overview?.planId; router.push({path:'/business/kpi-bonus',query:{projectId:row.projectId,...(planId?{planId}:{})}}) }
const money = value => Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const signed = value => `${Number(value||0)>0?'+':''}${money(value)}`
const amountTone = value => Number(value||0)<0?'amount-loss':'amount-profit'
const kpiMeta = row => {
  const overview=kpiOverviewMap.value.get(Number(row.projectId))
  if (!overview || !Number(overview.targetCount)) return {label:'未设置',tone:'info',action:'设置KPI'}
  if (!overview.planId) return {label:'待发布',tone:'warning',action:'继续配置'}
  const meta={DRAFT:{label:'填报中',tone:'primary'},SUBMITTED:{label:'待确认',tone:'warning'},RETURNED:{label:'已退回',tone:'danger'},CONFIRMED:{label:'已确认',tone:'success'}}[overview.settlementStatus]
  return {...(meta||{label:`方案 v${overview.planVersion}`,tone:'info'}),action:overview.settlementStatus==='SUBMITTED'?'审核结算':'查看KPI'}
}
const decisionActions = row => row.status==='DRAFT'?[{key:'START_PLANNING',...actionMeta.START_PLANNING}]:row.status==='PLANNING'&&row.baselineStatus==='SUBMITTED'?[{key:'CONFIRM_BASELINE',...actionMeta.CONFIRM_BASELINE},{key:'RETURN_PLAN',...actionMeta.RETURN_PLAN}]:row.status==='PAUSED'?[{key:'RESUME',...actionMeta.RESUME}]:row.status==='ACCEPTANCE'?[{key:'REVIEW_ACCEPTANCE',...actionMeta.REVIEW_ACCEPTANCE}]:[]
const decisionHint = row => row.status==='DRAFT'?'历史草稿等待确认进入规划':row.status==='PLANNING'?'历史计划已提交，等待确认或退回':row.status==='PAUSED'?'项目处于暂停状态，决定是否恢复执行':row.status==='ACCEPTANCE'?'验收资料已提交，等待关闭或退回执行':'需要老板处理'
const personnelIssueHint = row => row.costStatus==='MISSING_REGION'?'国家/地区尚未明确，系统无法确定按 21.75 天还是 26 天折算。':row.costStatus==='LEGACY_COST'?'当前仍是历史成本口径，请更新为人民币月度用人成本。':'尚未设置今天生效的人民币月度用人成本。'
const pendingLabel = row => row.category==='PROPOSAL'?'立项审批':row.category==='KPI_MISSING'?(Number(row.targetCount)?'KPI待发布':'KPI未设置'):row.category==='KPI_REVIEW'?'KPI结算待确认':row.category==='PERSONNEL_COST'?'人员成本':'项目状态'
const pendingTone = row => row.category==='KPI_MISSING'||row.costStatus==='MISSING_REGION'?'danger':row.category==='PROPOSAL'||row.category==='KPI_REVIEW'||row.category==='PERSONNEL_COST'?'warning':'info'
const pendingDescription = row => row.category==='PROPOSAL'?(row.objective || '新的立项申请等待审批'):row.category==='KPI_MISSING'?(Number(row.targetCount)?`已有 ${row.targetCount} 项 KPI 目标，但尚未发布考核与奖金方案。`:'项目已进入执行流程，但还没有设置当前 KPI 目标。'):row.category==='KPI_REVIEW'?'负责人已提交 KPI 结果，确认后项目奖金会立即计入成本，并解除该周期的结项限制。':row.category==='PERSONNEL_COST'?personnelIssueHint(row):decisionHint(row)
const pendingMeta = row => {
  if(row.category==='PROPOSAL')return `${row.applicantName} 负责 · ${row.companyName || '未设置公司'} · ${row.planStartDate || '—'} 至 ${row.planEndDate || '—'}`
  if(row.category==='KPI_REVIEW')return `方案 v${row.planVersion} · 截止 ${row.cycleEnd || '—'} · 综合得分 ${row.totalScore ?? '—'} · 预计项目奖金 ¥${money(row.bonusAmount)}`
  if(row.category==='PERSONNEL_COST')return row.projectCount?`${row.companyName || '未设置所属公司'} · 影响 ${row.projectCount} 个项目：${row.projectNameText || '—'}`:`${row.companyName || '未设置所属公司'} · 尚未加入执行中项目`
  return `${row.mainOwnerName || '未指定负责人'} · ${statusLabel[row.status] || row.status || '—'}`
}

async function loadPendingPage() {
  const result = await getBossBusinessPending({pageNum:pendingPage.value,pageSize:pendingPageSize,category:pendingFilter.value})
  const page = result.data || {}
  pendingRows.value = page.rows || []
  pendingTotal.value = Number(page.total || 0)
  pendingCounts.value = page.counts || {}
}
async function loadVisibleProjectKpis() {
  const projectIds = projects.value.map(row => row.projectId).filter(Boolean)
  if (!projectIds.length) { kpiOverviews.value = []; return }
  const result = await getProjectKpiOverview({projectIds:projectIds.join(',')})
  kpiOverviews.value = result.data || []
}
async function changePendingFilter() {
  pendingPage.value = 1
  await loadPendingPage()
}

async function load() {
  loading.value = true
  try {
    const [dashboardResult,accountingResult,pendingResult] = await Promise.all([getBossBusinessDashboard({projectPageNum:projectPage.pageNum,projectPageSize:projectPage.pageSize,decisionPageSize:1}),getBusinessBossAccountingOverview(),getBossBusinessPending({pageNum:pendingPage.value,pageSize:pendingPageSize,category:pendingFilter.value})])
    const data = dashboardResult.data || {}
    summary.value = data.summary || {}
    const page = data.projectPage || {}
    projects.value = page.rows || data.projects || []
    projectPage.total = Number(page.total ?? summary.value.totalCount ?? projects.value.length)
    projectPage.pageNum = Number(page.pageNum || projectPage.pageNum)
    projectPage.pageSize = Number(page.pageSize || projectPage.pageSize)
    accounting.value = accountingResult.data || {today:{},alerts:[]}
    const pending = pendingResult.data || {}
    pendingRows.value = pending.rows || []
    pendingTotal.value = Number(pending.total || 0)
    pendingCounts.value = pending.counts || {}
    await loadVisibleProjectKpis()
  } finally { loading.value = false }
}
async function loadProjectPage() {
  projectLoading.value = true
  try {
    const result = await getBossBusinessDashboard({projectPageNum:projectPage.pageNum,projectPageSize:projectPage.pageSize,decisionPageSize:1})
    const data = result.data || {}
    const page = data.projectPage || {}
    projects.value = page.rows || data.projects || []
    projectPage.total = Number(page.total ?? projectPage.total)
    await loadVisibleProjectKpis()
  } finally { projectLoading.value = false }
}
async function decideProposal(row,decision){let comment='';if(decision==='RETURNED'){const result=await ElMessageBox.prompt('请填写退回原因','退回立项申请',{inputValidator:value=>!!value?.trim()||'必须填写退回原因'});comment=result.value}else await ElMessageBox.confirm(`批准“${row.projectName}”后将直接创建执行中项目，并由 ${row.applicantName} 负责。确认批准吗？`,'批准立项',{type:'warning'});await reviewProjectProposal(row.proposalId,{decision,comment});ElMessage.success(decision==='APPROVED'?'已批准立项并启动项目':'申请已退回修改');await load()}
async function doTransition(row, action) {
  if(action==='REVIEW_ACCEPTANCE')return openProject(row,'acceptance')
  const meta = actionMeta[action] || {label:'执行操作'}
  let comment = ''
  if (['RETURN_PLAN','RETURN_ACTIVE'].includes(action)) {
    const result = await ElMessageBox.prompt(`请输入“${meta.label}”的原因，负责人将在项目动态中看到`,'老板决策',{inputValidator:value=>!!value?.trim()||'必须填写原因'})
    comment = result.value
  } else await ElMessageBox.confirm(`确定对“${row.projectName}”执行“${meta.label}”吗？`, '老板确认', { type: 'warning' })
  await transitionBusinessProject(row.projectId, { action, comment })
  ElMessage.success('操作成功')
  await load()
}
load()
</script>

<style scoped>
.business-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#172033}.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:26px 30px;border-radius:16px;background:linear-gradient(120deg,#10233f,#1d4566);color:#fff;box-shadow:0 12px 35px rgba(15,35,62,.18)}.eyebrow{font-size:11px;letter-spacing:.18em;color:#8ed8d0}.hero h1{margin:5px 0 4px;font-size:28px}.hero p{margin:0;color:#c8d5e2}.hero-actions,.panel-actions{display:flex;align-items:center;gap:10px}.panel{margin-top:14px;padding:20px;border:1px solid #e0e5eb;border-radius:14px;background:#fff}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:4px 0 0;color:#8490a0;font-size:13px}.pending-toolbar,.pending-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px}.pending-toolbar{padding:10px 12px;border:1px solid #e4e9ee;border-radius:10px;background:#f8fafc}.pending-toolbar>span,.pending-pagination>span{color:#7e8a98;font-size:12px;white-space:nowrap}.pending-pagination{justify-content:flex-end;padding-top:16px;border-top:1px solid #edf0f3}.decision-row{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:14px 0;border-top:1px solid #edf0f3}.decision-copy{display:flex;min-width:0;flex:1;flex-direction:column}.decision-title{display:flex;align-items:center;gap:9px}.decision-copy span{margin-top:5px;color:#536477}.decision-copy small{margin-top:4px;color:#8a95a3}.decision-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.decision-actions .el-button+.el-button{margin-left:0}.empty-state{padding:28px 0;text-align:center;color:#9aa4b1}.empty-state.compact{padding:18px 0}.finance-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.finance-grid article{padding:17px 18px;border:1px solid #dfe6ec;border-radius:12px;background:#f8fafc}.finance-grid span,.finance-grid small,.finance-grid strong{display:block}.finance-grid span,.finance-grid small{color:#7f8b98}.finance-grid strong{margin-top:8px;font-size:25px}.finance-grid small{margin-top:5px;font-size:12px}.amount-profit{color:#21836a}.amount-loss{color:#cf4852}.alert-section{margin-top:18px;padding:16px;border:1px solid #e5eaf0;border-radius:12px;background:#f8fafc}.subsection-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.subsection-head>div{display:flex;align-items:baseline;gap:10px}.subsection-head>div span{color:#8a95a2;font-size:12px}.alert-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.alert-card{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:start;gap:11px;min-height:112px;padding:14px;border:1px solid #e0e6ec;border-radius:11px;background:#fff;color:inherit;text-align:left;cursor:pointer;transition:border-color .18s,box-shadow .18s,transform .18s}.alert-card:hover{border-color:#b9c7d5;box-shadow:0 7px 18px rgba(31,53,74,.09);transform:translateY(-1px)}.alert-icon{display:flex;width:30px;height:30px;align-items:center;justify-content:center;border-radius:9px;background:#fff0f1;color:#d94e58;font-size:17px;font-weight:800}.alert-card--over-budget .alert-icon{background:#fff5e6;color:#c8841c}.alert-card--missing-company .alert-icon{background:#eef4fb;color:#4f78a8}.alert-content{display:flex;min-width:0;flex-direction:column}.alert-meta{display:flex;align-items:center;justify-content:space-between;gap:8px}.alert-meta small{color:#9aa4af;font-size:11px}.alert-content>b{margin-top:9px;overflow:hidden;color:#223248;text-overflow:ellipsis;white-space:nowrap}.alert-content>span:last-child{margin-top:4px;color:#788695;font-size:12px;line-height:1.45}.alert-arrow{margin-top:39px;color:#a3adb8;font-size:24px;line-height:1}.alert-card:hover .alert-arrow{color:#58748d}.alert-footer{display:flex;justify-content:flex-end;padding-top:8px}.project-count{color:#728092;font-size:13px}.project-link{padding:0;border:0;background:none;color:#24364d;font:inherit;font-weight:650;cursor:pointer}.project-link:hover{color:#409eff}.subline{display:block;margin-top:4px;color:#8a94a3}.safe-text{color:#7d8997}.el-progress{max-width:120px}@media(max-width:1050px){.alert-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:900px){.finance-grid{grid-template-columns:1fr}.panel-head{align-items:flex-start}.panel-actions{align-items:flex-end;flex-direction:column}}@media(max-width:760px){.business-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:18px;padding:22px}.hero-actions{width:100%}.hero-actions .el-button{flex:1}.panel{padding:14px}.panel-head p{display:none}.pending-toolbar{align-items:stretch;flex-direction:column}.pending-toolbar :deep(.el-radio-group){display:grid;grid-template-columns:repeat(2,1fr)}.pending-toolbar :deep(.el-radio-button__inner){width:100%;padding:8px 5px}.pending-toolbar>span{text-align:right}.pending-pagination{align-items:flex-end;flex-direction:column}.decision-row{align-items:flex-start;flex-direction:column}.decision-actions{width:100%;justify-content:flex-start}.decision-actions .el-button{flex:1}.subsection-head{align-items:flex-start}.subsection-head>div{align-items:flex-start;flex-direction:column;gap:3px}.alert-grid{grid-template-columns:1fr}.alert-card{min-height:0}}
.project-pagination{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:16px}.project-pagination>span{color:#7e8a98;font-size:12px}
</style>
