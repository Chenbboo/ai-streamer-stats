<template>
  <div class="app-container business-page">
    <header class="hero">
      <div><span class="eyebrow">OWNER COMMAND CENTER</span><h1>老板工作台</h1><p>集中查看本人立项项目的状态、计划审批、延期与高风险预警。</p></div>
      <div class="hero-actions"><el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button><el-button type="primary" icon="Plus" @click="startProject">开启项目</el-button></div>
    </header>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.key" class="metric-card" :class="`tone-${item.tone}`">
        <span>{{ item.label }}</span><strong>{{ summary[item.key] || 0 }}</strong><small>{{ item.hint }}</small>
      </article>
    </section>

    <section class="panel accounting-overview">
      <div class="panel-head"><div><h2>经营结果</h2><p>负责人填报业务成本，系统自动计算人员成本并汇总今日经营结果</p></div><div class="accounting-actions"><el-tag v-if="accounting.draftFactCount" type="warning">{{ accounting.draftFactCount }} 条其他草稿待确认</el-tag><el-button link type="primary" @click="openAccounting()">进入每日收支</el-button></div></div>
      <div class="accounting-periods"><article class="period-card today-card"><div><span>今日经营结果</span><small>{{ accounting.bizDate || '—' }} · 已确认口径</small></div><strong :class="amountTone(accounting.today?.profitAmount)">{{ signed(accounting.today?.profitAmount) }}</strong><div class="period-breakdown"><span>收入 {{ money(accounting.today?.revenueAmount) }}</span><span>业务成本 {{ money(accounting.today?.businessCost) }}</span><span>人员成本 {{ money(accounting.today?.personnelCost) }}</span><span>总成本 {{ money(accounting.today?.costAmount) }}</span><span>调整 {{ signed(accounting.today?.adjustmentAmount) }}</span><span>{{ accounting.today?.projectCount || 0 }} 个已核算项目</span></div></article></div>
      <div class="company-result-grid"><button v-for="company in accounting.companies || []" :key="company.companyDeptId" @click="openAccounting({companyDeptId:company.companyDeptId,dateFrom:accounting.bizDate,dateTo:accounting.bizDate})"><span>{{ company.companyName }}</span><b :class="amountTone(company.profitAmount)">{{ signed(company.profitAmount) }}</b><small>收入 {{ money(company.revenueAmount) }} · 业务 {{ money(company.businessCost) }} · 人员 {{ money(company.personnelCost) }}</small></button><div v-if="!accounting.companies?.length" class="accounting-empty">今日尚未生成公司经营日报</div></div>
      <div class="accounting-detail-grid">
        <div><div class="subsection-head"><b>经营异常与待处理</b><span>{{ accounting.alerts?.length || 0 }} 项</span></div><div v-if="!accounting.alerts?.length" class="accounting-empty">暂无亏损、超预算或项目归属异常</div><button v-for="alert in accounting.alerts || []" :key="`${alert.alertType}-${alert.projectId}`" class="alert-row" @click="alert.alertType==='MISSING_COMPANY'?openProject(alert):openAccounting({projectId:alert.projectId})"><el-tag size="small" :type="alert.alertType==='LOSS'?'danger':'warning'">{{ alertLabel[alert.alertType] }}</el-tag><span><b>{{ alert.projectName }}</b><small>{{ alert.alertMessage }}</small></span></button></div>
        <div><div class="subsection-head"><b>今日项目经营排行</b><span>按今日经营结果排序</span></div><div v-if="!accounting.ranking?.length" class="accounting-empty">今日尚未生成项目日报</div><button v-for="(row,index) in accounting.ranking || []" :key="row.projectId" class="ranking-row" @click="openAccounting({projectId:row.projectId,dateFrom:accounting.bizDate,dateTo:accounting.bizDate})"><i>{{ index+1 }}</i><span><b>{{ row.projectName }}</b><small>{{ row.companyName || '待设置公司' }}</small></span><strong :class="amountTone(row.profitAmount)">{{ signed(row.profitAmount) }}</strong></button></div>
      </div>
    </section>

    <section class="workflow-panel">
      <div v-for="(stage,index) in workflowStages" :key="stage.key" class="workflow-stage"><span>{{ index + 1 }}</span><div><b>{{ stage.label }}</b><small>{{ summary[stage.key] || 0 }} 个项目</small></div></div>
    </section>

    <section class="panel decision-panel">
      <div class="panel-head"><div><h2>待老板处理</h2><p>从立项、计划确认到暂停恢复和验收结项，所有老板决策集中在这里</p></div><el-tag type="warning">{{ decisions.length }} 项</el-tag></div>
      <div v-if="!decisions.length && !loading" class="empty-tasks">当前没有需要老板处理的项目</div>
      <article v-for="row in decisions" :key="row.projectId" class="decision-row">
        <div class="decision-copy"><b>{{ row.projectName }}</b><span>{{ decisionHint(row) }}</span><small>{{ row.mainOwnerName || '未指定负责人' }} · {{ statusLabel[row.status] }}</small></div>
        <div class="decision-actions">
          <el-button v-for="action in decisionActions(row)" :key="action.key" size="small" :type="action.type" :plain="action.plain" @click="doTransition(row,action.key)">{{ action.label }}</el-button>
          <el-button size="small" @click="openProject(row)">查看详情</el-button>
        </div>
      </article>
    </section>

    <section class="panel directory-panel">
      <div class="panel-head"><div><h2>全公司项目名称目录</h2><p>两位老板共享项目名称和立项归属；非本人项目的运营详情仍然隔离</p></div><span class="directory-count">{{ companyProjects.length }} 个项目</span></div>
      <el-table :data="companyProjects" v-loading="loading" size="small" empty-text="尚未创建项目"><el-table-column prop="projectName" label="项目名称" min-width="220" /><el-table-column label="立项老板" width="140"><template #default="{row}"><el-tag size="small" type="warning">{{ row.initiatorName }}立项</el-tag></template></el-table-column><el-table-column label="权限" width="150"><template #default="{row}"><el-button v-if="row.canOpen" link type="primary" @click="openProject(row)">打开项目</el-button><span v-else class="isolated-label">仅名称可见</span></template></el-table-column></el-table>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2>我的经营项目态势</h2><p>优先处理本人立项项目的待确认计划、逾期任务和高风险事项</p></div><el-button link type="primary" @click="router.push('/business/projects')">查看我的项目</el-button></div>
      <el-table :data="projects" v-loading="loading" empty-text="尚未创建项目">
        <el-table-column label="项目" min-width="220"><template #default="{ row }"><button class="project-link" @click="openProject(row)">{{ row.projectName }}</button><small class="subline">{{ row.projectNo }} · {{ typeLabel[row.projectType] || row.projectType }}</small></template></el-table-column>
        <el-table-column prop="mainOwnerName" label="负责人" width="130" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="计划" min-width="180"><template #default="{ row }">{{ row.planStartDate || '—' }} 至 {{ row.planEndDate || '—' }}</template></el-table-column>
        <el-table-column label="进度" width="150"><template #default="{ row }"><el-progress :percentage="progress(row)" :stroke-width="8" /></template></el-table-column>
        <el-table-column label="风险" width="90" align="center"><template #default="{ row }"><el-badge :value="row.openRiskCount || 0" :hidden="!row.openRiskCount" type="danger"><span class="risk-anchor">风险</span></el-badge></template></el-table-column>
        <el-table-column label="决策" width="180" fixed="right"><template #default="{ row }">
          <el-button v-if="primaryAction(row)" link :type="primaryAction(row).type" @click="doTransition(row,primaryAction(row).key)">{{ primaryAction(row).label }}</el-button>
          <el-button link type="primary" @click="openProject(row)">详情</el-button>
        </template></el-table-column>
      </el-table>
    </section>

    <section class="panel personal-panel">
      <div class="panel-head"><div><h2>我的待办</h2><p>分配给我的未完成任务，按截止时间排序</p></div></div>
      <div v-if="!tasks.length && !loading" class="empty-tasks">当前没有未完成任务</div>
      <button v-for="task in tasks" :key="task.taskId" class="task-row" @click="openProject(task)">
        <span class="priority-dot" :class="`priority-${task.priority?.toLowerCase()}`"></span>
        <span class="task-copy"><b>{{ task.taskName }}</b><small>{{ task.projectName }} · {{ task.projectNo }}</small></span>
        <span class="task-meta">{{ taskStatusLabel[task.status] || task.status }}<small :class="{ overdue: isOverdue(task.dueDate) }">{{ task.dueDate || '未设期限' }}</small></span>
      </button>
    </section>
  </div>
</template>

<script setup name="BusinessBoss">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBossBusinessDashboard, getBossProjectDirectory, transitionBusinessProject } from '@/api/business/project'
import { getBusinessBossAccountingOverview } from '@/api/business/accounting'

const router = useRouter()
const loading = ref(false)
const summary = ref({})
const projects = ref([])
const tasks = ref([])
const decisions = ref([])
const companyProjects = ref([])
const accounting=ref({today:{},alerts:[],ranking:[],companies:[],draftFactCount:0})
const metrics = [
  { key: 'totalCount', label: '我的项目', hint: '本人立项项目总量', tone: 'ink' },
  { key: 'activeCount', label: '执行中', hint: '已确认基线', tone: 'blue' },
  { key: 'pendingDecisionCount', label: '待我处理', hint: '立项、计划、暂停和验收', tone: 'violet' },
  { key: 'acceptanceCount', label: '待验收', hint: '等待最终确认', tone: 'green' },
  { key: 'overdueProjectCount', label: '存在逾期', hint: '至少一项任务逾期', tone: 'orange' },
  { key: 'highRiskProjectCount', label: '高风险', hint: '高/严重未关闭风险', tone: 'red' }
]
const statusLabel = { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '已暂停', ACCEPTANCE: '待验收', CLOSED: '已关闭', CANCELED: '已取消' }
const statusTone = { DRAFT: 'info', PLANNING: 'warning', ACTIVE: 'primary', PAUSED: 'info', ACCEPTANCE: 'success', CLOSED: 'success', CANCELED: 'danger' }
const typeLabel = { LIVE: '直播', JEWELRY: '珠宝', ECOMMERCE: '电商', OPERATIONS: '运营', INTERNAL: '内部', OTHER: '其他' }
const taskStatusLabel = { TODO: '待开始', DOING: '进行中', BLOCKED: '受阻' }
const alertLabel={LOSS:'当日亏损',OVER_BUDGET:'预算超支',MISSING_COMPANY:'待设公司'}
const workflowStages = [{key:'draftCount',label:'立项草稿'},{key:'planningCount',label:'规划与计划'},{key:'activeCount',label:'执行监管'},{key:'pausedCount',label:'暂停处理'},{key:'acceptanceCount',label:'验收结项'}]
const actionMeta = {START_PLANNING:{label:'进入规划',type:'primary'},CONFIRM_BASELINE:{label:'确认并启动',type:'success'},RETURN_PLAN:{label:'退回计划',type:'warning',plain:true},RESUME:{label:'恢复执行',type:'primary'},REVIEW_ACCEPTANCE:{label:'查看验收资料',type:'success'}}
const progress = row => row.taskCount ? Math.round((row.completedTaskCount || 0) * 100 / row.taskCount) : 0
const openProject = (row,tab) => router.push({ path: '/business/projects', query: { id: row.projectId, ...(tab?{tab}:{}) } })
const startProject = () => router.push({path:'/business/projects',query:{create:'1'}})
const openAccounting=(query={})=>router.push({path:'/business/accounting',query})
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const signed=value=>`${Number(value||0)>0?'+':''}${money(value)}`
const amountTone=value=>Number(value||0)<0?'amount-loss':'amount-profit'
const isOverdue = date => date && date < new Date().toISOString().slice(0, 10)
const decisionActions = row => row.status==='DRAFT'?[{key:'START_PLANNING',...actionMeta.START_PLANNING}]:row.status==='PLANNING'&&row.baselineStatus==='SUBMITTED'?[{key:'CONFIRM_BASELINE',...actionMeta.CONFIRM_BASELINE},{key:'RETURN_PLAN',...actionMeta.RETURN_PLAN}]:row.status==='PAUSED'?[{key:'RESUME',...actionMeta.RESUME}]:row.status==='ACCEPTANCE'?[{key:'REVIEW_ACCEPTANCE',...actionMeta.REVIEW_ACCEPTANCE}]:[]
const primaryAction = row => decisionActions(row)[0]
const decisionHint = row => row.status==='DRAFT'?'确认项目进入规划，由负责人开始拆解计划':row.status==='PLANNING'?'计划已提交，等待确认或退回':row.status==='PAUSED'?'项目处于暂停状态，决定是否恢复执行':row.status==='ACCEPTANCE'?'验收资料已提交，等待关闭或退回执行':'需要老板处理'

async function load() {
  loading.value = true
  try {
    const [dashboardResult,directoryResult,accountingResult] = await Promise.all([getBossBusinessDashboard(),getBossProjectDirectory(),getBusinessBossAccountingOverview()])
    const data = dashboardResult.data || {}
    summary.value = data.summary || {}
    projects.value = data.projects || []
    decisions.value = data.decisions || []
    tasks.value = data.tasks || []
    companyProjects.value = directoryResult.data || []
    accounting.value=accountingResult.data||{today:{},alerts:[],ranking:[],companies:[]}
  } finally { loading.value = false }
}
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
.business-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#172033}.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:26px 30px;border-radius:16px;background:linear-gradient(120deg,#10233f,#1d4566);color:#fff;box-shadow:0 12px 35px rgba(15,35,62,.18)}.eyebrow{font-size:11px;letter-spacing:.18em;color:#8ed8d0}.hero h1{margin:5px 0 4px;font-size:28px}.hero p{margin:0;color:#c8d5e2}.hero-actions{display:flex;gap:10px}.metric-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;margin:18px 0}.metric-card{position:relative;overflow:hidden;padding:18px;border:1px solid #e0e5eb;border-radius:12px;background:#fff}.metric-card:before{position:absolute;top:0;left:0;width:100%;height:3px;background:var(--tone);content:""}.metric-card span,.metric-card small{display:block;color:#738093}.metric-card strong{display:block;margin:8px 0 5px;font-size:27px}.tone-ink{--tone:#1c314f}.tone-blue{--tone:#3977c5}.tone-violet{--tone:#7357b4}.tone-green{--tone:#1a907f}.tone-orange{--tone:#d58227}.tone-red{--tone:#cb4b55}.workflow-panel{display:grid;grid-template-columns:repeat(5,1fr);margin-bottom:14px;border:1px solid #dfe5eb;border-radius:14px;background:#fff;overflow:hidden}.workflow-stage{display:flex;align-items:center;gap:10px;padding:15px;border-right:1px solid #e5e9ee}.workflow-stage:last-child{border:0}.workflow-stage>span{display:grid;width:28px;height:28px;flex:0 0 28px;place-items:center;border-radius:50%;background:#e8f0f8;color:#2c6197;font-weight:700}.workflow-stage b,.workflow-stage small{display:block}.workflow-stage small{margin-top:3px;color:#8793a1}.panel{padding:20px;border:1px solid #e0e5eb;border-radius:14px;background:#fff}.decision-panel,.directory-panel{margin-bottom:14px}.directory-count{color:#728092;font-size:13px}.isolated-label{color:#9a6c25;font-size:12px}.personal-panel{margin-top:14px}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:4px 0 0;color:#8490a0;font-size:13px}.decision-row{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:14px 0;border-top:1px solid #edf0f3}.decision-copy{display:flex;min-width:0;flex-direction:column}.decision-copy span{margin-top:5px;color:#536477}.decision-copy small{margin-top:4px;color:#8a95a3}.decision-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.decision-actions .el-button+.el-button{margin-left:0}.project-link{padding:0;border:0;background:none;color:#24364d;font:inherit;font-weight:650;cursor:pointer}.project-link:hover{color:#409eff}.subline{display:block;margin-top:4px;color:#8a94a3}.risk-anchor{display:inline-block;padding:4px 8px;color:#697586}.el-progress{max-width:120px}.task-row{display:flex;width:100%;align-items:center;padding:13px 8px;border:0;border-top:1px solid #edf0f3;background:#fff;text-align:left;cursor:pointer}.task-row:hover{background:#f7fafc}.priority-dot{width:8px;height:8px;flex:0 0 8px;margin-right:12px;border-radius:50%;background:#789}.priority-high{background:#d44b54}.priority-medium{background:#db912e}.priority-low{background:#47917c}.task-copy{display:flex;min-width:0;flex:1;flex-direction:column}.task-copy b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.task-copy small,.task-meta small{margin-top:4px;color:#8994a2}.task-meta{display:flex;align-items:flex-end;flex-direction:column;color:#536174;font-size:13px}.task-meta small.overdue{color:#d7474f}.empty-tasks{padding:28px 0;text-align:center;color:#9aa4b1}@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(3,1fr)}.workflow-panel{grid-template-columns:repeat(3,1fr)}.workflow-stage:nth-child(3){border-right:0}.workflow-stage:nth-child(-n+3){border-bottom:1px solid #e5e9ee}}@media(max-width:760px){.business-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:18px;padding:22px}.hero-actions{width:100%}.hero-actions .el-button{flex:1}.metric-grid{grid-template-columns:repeat(2,1fr)}.workflow-panel{grid-template-columns:1fr}.workflow-stage{border-right:0;border-bottom:1px solid #e5e9ee}.workflow-stage:nth-child(-n+3){border-bottom:1px solid #e5e9ee}.panel{padding:14px}.panel-head p{display:none}.decision-row{align-items:flex-start;flex-direction:column}.decision-actions{width:100%;justify-content:flex-start}.decision-actions .el-button{flex:1}.task-row{align-items:flex-start}.task-meta{min-width:80px}}
.accounting-overview{margin-bottom:14px}.accounting-actions{display:flex;align-items:center;gap:10px}.accounting-periods{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.period-card{display:grid;grid-template-columns:1fr auto;gap:10px;padding:17px;border:1px solid #dfe6ec;border-radius:12px;background:#f8fafc}.period-card span,.period-card small{display:block}.period-card small{margin-top:5px;color:#8793a1}.period-card>strong{font-size:25px}.period-breakdown{display:flex;grid-column:1/-1;flex-wrap:wrap;gap:8px 20px;padding-top:10px;border-top:1px solid #e5e9ee;color:#596879;font-size:13px}.today-card{border-left:3px solid #3977c5}.month-card{border-left:3px solid #258a78}.amount-profit{color:#21836a}.amount-loss{color:#cf4852}.company-result-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-top:10px}.company-result-grid button{padding:13px 15px;border:1px solid #e1e6eb;border-radius:10px;background:#fff;text-align:left;cursor:pointer}.company-result-grid span,.company-result-grid b,.company-result-grid small{display:block}.company-result-grid b{margin:6px 0;font-size:19px}.company-result-grid small{color:#8793a1}.accounting-detail-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:18px;margin-top:18px}.accounting-detail-grid>div{min-width:0}.subsection-head{display:flex;justify-content:space-between;padding-bottom:9px;border-bottom:1px solid #e8ecf0}.subsection-head span{color:#8a95a2;font-size:12px}.alert-row,.ranking-row{display:flex;width:100%;align-items:center;gap:10px;padding:11px 4px;border:0;border-bottom:1px solid #edf0f3;background:#fff;text-align:left;cursor:pointer}.alert-row>span,.ranking-row>span{display:flex;min-width:0;flex:1;flex-direction:column}.alert-row small,.ranking-row small{margin-top:4px;color:#8994a2}.ranking-row i{display:grid;width:25px;height:25px;place-items:center;border-radius:50%;background:#edf3f8;color:#3c6388;font-style:normal}.ranking-row strong{white-space:nowrap}.accounting-empty{padding:20px 8px;color:#9aa4b1;text-align:center}@media(max-width:760px){.accounting-periods,.company-result-grid,.accounting-detail-grid{grid-template-columns:1fr}.accounting-actions{align-items:flex-end;flex-direction:column}}
.accounting-periods{grid-template-columns:1fr}
</style>
