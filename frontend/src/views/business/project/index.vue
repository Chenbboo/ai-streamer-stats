<template>
  <div class="app-container project-page">
    <header class="page-head">
      <div><span class="eyebrow">PROJECT PORTFOLIO</span><h1>项目中心</h1><p>负责人完成立项申请并启动后，在这里自主管理执行、核算与结项。</p></div>
      <el-button v-hasPermi="['business:project:proposal:add']" type="primary" icon="Plus" @click="router.push('/business/project-proposals')">发起立项申请</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item><el-input v-model="query.keyword" clearable placeholder="项目名称 / 编号 / 负责人" style="width:240px" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-select v-model="query.status" clearable placeholder="全部状态" style="width:140px"><el-option v-for="(label,key) in statusLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item><el-select v-model="query.managementMode" clearable placeholder="全部管理模式" style="width:145px"><el-option label="轻量" value="LIGHT"/><el-option label="标准" value="STANDARD"/><el-option label="重点监管" value="KEY_CONTROL"/></el-select></el-form-item>
        <el-form-item><el-select v-model="query.closeMethod" clearable placeholder="全部结项方式" style="width:145px"><el-option v-for="(label,key) in closeMethodLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" @row-click="openDetail" class="click-table">
        <el-table-column label="项目" min-width="240"><template #default="{ row }"><b>{{ row.projectName }}</b><small class="subline">{{ row.projectNo }}<template v-if="row.parentName"> · 上级：{{ row.parentName }}</template></small></template></el-table-column>
        <el-table-column prop="companyName" label="归属公司" min-width="150" />
        <el-table-column prop="sponsorOwnerName" label="归属老板" min-width="110"><template #default="{row}">{{ row.sponsorOwnerName || row.initiatorName }}</template></el-table-column>
        <el-table-column prop="mainOwnerName" label="负责人" min-width="110" />
        <el-table-column label="治理方式" min-width="160"><template #default="{row}"><b>{{ managementModeLabel[row.managementMode] || row.managementMode }}</b><small class="subline">{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</small></template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ typeLabel[row.projectType] || row.projectType }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTone[row.status] || 'info'">{{ projectStatusLabel(row) }}</el-tag></template></el-table-column>
          <el-table-column label="计划周期" min-width="185"><template #default="{ row }">{{ row.planStartDate ? `${row.planStartDate} 至 ${row.planEndDate || '不限期'}` : '—' }}</template></el-table-column>
        <el-table-column label="任务进度" width="150"><template #default="{ row }"><el-progress :percentage="projectProgress(row)" :stroke-width="8" /></template></el-table-column>
        <el-table-column label="成员 / 风险" width="120" align="center"><template #default="{ row }">{{ row.memberCount || 0 }} / <span :class="{ danger: row.openRiskCount }">{{ row.openRiskCount || 0 }}</span></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">打开</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>

    <el-drawer v-model="detailVisible" size="min(920px, 96vw)" destroy-on-close @closed="detail = null">
      <template #header><div v-if="detail" class="drawer-title"><div><span>{{ detail.projectNo }}</span><h2>{{ detail.projectName }}</h2></div><el-tag :type="statusTone[detail.status] || 'info'">{{ projectStatusLabel(detail) }}</el-tag></div></template>
      <template v-if="detail">
        <section class="project-summary">
        <div><span>归属公司</span><b>{{ detail.companyName || '待设置' }}</b></div><div><span>归属老板</span><b>{{ detail.sponsorOwnerName || detail.initiatorName }}</b></div><div><span>申请人</span><b>{{ detail.applicantName || detail.mainOwnerName }}</b></div><div><span>主负责人</span><b>{{ detail.mainOwnerName }}</b></div><div><span>管理模式</span><b>{{ managementModeLabel[detail.managementMode] || detail.managementMode }}</b></div><div><span>结项方式</span><b>{{ closeMethodLabel[detail.closeMethod] || detail.closeMethod }}</b></div><div><span>核算方式</span><b>{{ accountingLabel[detail.accountingMode] }}</b></div><div><span>计划周期</span><b>{{ detail.planStartDate ? `${detail.planStartDate} 至 ${detail.planEndDate || '不限期'}` : '—' }}</b></div>
        </section>
        <div class="objective"><span>项目目标</span><p>{{ detail.objective || '尚未填写项目目标' }}</p></div>
        <el-alert class="governance-banner" :title="governanceTitle" :description="governanceDescription" type="info" :closable="false" show-icon />
        <section v-if="operating.executionSummary" class="execution-summary">
          <div class="execution-title"><div><span>直播日报 · {{ operating.executionSummary.statDate }}</span><h3>昨天提交情况</h3></div><el-tag type="info" effect="plain">只读数字</el-tag></div>
          <div class="execution-metrics">
            <div><span>应提交</span><strong>{{ number(operating.executionSummary.expectedStreamerCount) }}</strong></div>
            <div><span>已提交</span><strong>{{ number(operating.executionSummary.submittedStreamerCount) }}</strong></div>
            <div><span>未提交</span><strong :class="{ danger: yesterdayMissing > 0 }">{{ number(yesterdayMissing) }}</strong></div>
            <div><span>完成率</span><strong>{{ yesterdayRate }}%</strong></div>
          </div>
          <p>这里只返回数字，不显示主播名单和日报内容；主播仍只在直播数据管理中提交。</p>
        </section>
        <div class="action-bar">
          <el-button v-if="canManage" icon="Edit" @click="openProjectForm(detail)">编辑资料</el-button>
          <el-button v-if="isBoss" icon="User" @click="openOwnerDialog">更换主负责人</el-button>
          <el-button v-for="action in availableActions" :key="action.key" :type="action.type" :disabled="isKpiBlockedCloseAction(action)" :title="isKpiBlockedCloseAction(action)?'请先完成并确认全部KPI结算':''" @click="runTransition(action)">{{ action.label }}</el-button>
        </div>
        <section v-if="showKpiClosureGuard && detail.closeMethod!=='RESULT_ACCEPTANCE'" :class="['kpi-close-guard', `is-${kpiClosureState.tone}`]">
          <div class="kpi-close-mark">KPI</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>项目结项前置条件</span><el-tag :type="kpiClosureState.tone" effect="light">{{ kpiClosureState.label }}</el-tag></div>
            <b>{{ kpiClosureState.title }}</b>
            <p>{{ kpiClosureState.description }}</p>
            <div v-if="kpiClosureState.planCount" class="kpi-close-progress"><span>已确认 {{ kpiClosureState.confirmedCount }} / {{ kpiClosureState.planCount }} 个方案</span><el-progress :percentage="kpiClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
          </div>
          <el-button :type="kpiClosureState.actionType" plain @click="openKpiWorkspace">{{ kpiClosureState.actionLabel }}</el-button>
        </section>
        <section v-if="showStageClosureGuard" :class="['kpi-close-guard', `is-${stageClosureState.tone}`]">
          <div class="kpi-close-mark">结项</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>阶段验收项目结项</span><el-tag :type="stageClosureState.tone" effect="light">{{ stageClosureState.label }}</el-tag></div>
            <b>{{ stageClosureState.title }}</b>
            <p>{{ stageClosureState.description }}</p>
            <div v-if="stageClosureState.milestoneCount" class="kpi-close-progress"><span>已验收 {{ stageClosureState.doneCount }} / {{ stageClosureState.milestoneCount }} 个里程碑</span><el-progress :percentage="stageClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
          </div>
          <div class="stage-close-actions">
            <el-button v-if="stageClosureState.canRequest" type="success" @click="runTransition({key:'REQUEST_CLOSE',label:'申请结项',type:'success'})">提交老板检验</el-button>
            <template v-if="stageClosureState.canReview"><el-button type="success" @click="runTransition({key:'CLOSE',label:'确认结项',type:'success'})">确认结项</el-button><el-button type="warning" plain @click="runTransition({key:'RETURN_ACTIVE',label:'退回补充'})">退回补充</el-button></template>
          </div>
        </section>
        <section v-if="showDirectClosureGuard" class="kpi-close-guard is-warning">
          <div class="kpi-close-mark">审核</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>直接结项检验</span><el-tag type="warning" effect="light">待老板检验</el-tag></div>
            <b>负责人已提交直接结项申请</b>
            <p>{{ isBoss ? '请核对项目成果和结项前置条件，通过后项目才会正式关闭。' : '正在等待归属老板检验；负责人无权自行通过或关闭项目。' }}</p>
          </div>
          <div v-if="isBoss" class="stage-close-actions"><el-button type="success" @click="runTransition({key:'CLOSE',label:'检验通过并结项',type:'success'})">检验通过并结项</el-button><el-button type="warning" plain @click="runTransition({key:'RETURN_ACTIVE',label:'退回补充'})">退回补充</el-button></div>
        </section>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="项目总览" name="overview">
            <section class="cockpit-hero">
              <div><span>项目整体完成率</span><strong>{{ projectProgress(detail) }}%</strong><el-progress :percentage="projectProgress(detail)" :stroke-width="9" /></div>
              <div><span>计划时间进度</span><strong>{{ scheduleProgress }}%</strong><el-progress :percentage="scheduleProgress" :status="scheduleProgress>projectProgress(detail)?'warning':undefined" :stroke-width="9" /></div>
              <div><span>剩余时间</span><strong>{{ remainingDaysText }}</strong><small>{{ scheduleStatusText }}</small></div>
            </section>
            <el-alert v-if="cockpitError" title="经营数据暂时无法读取，任务、进度和目标信息仍可正常查看。" type="warning" :closable="false" show-icon />
            <section class="cockpit-metrics" v-loading="cockpitLoading">
              <article><span>项目预算</span><b>{{ money(cockpitBudget) }}</b><small>{{ cockpitCurrency }}</small></article>
              <article :class="budgetTone"><span>预算已使用</span><b>{{ money(cockpitBudgetSpent) }}</b><small>{{ budgetUsage }}% · 剩余 {{ money(cockpitBudgetRemaining) }}</small></article>
              <article><span>累计收入</span><b>{{ money(cockpitSummary.revenueAmount) }}</b><small>{{ cockpitCurrency }}</small></article>
              <article><span>累计总成本</span><b>{{ money(cockpitTotalCost) }}</b><small>业务、人员及奖金成本</small></article>
              <article :class="Number(cockpitSummary.profitAmount)<0?'is-danger':'is-success'"><span>累计经营结果</span><b>{{ signedMoney(cockpitSummary.profitAmount) }}</b><small>{{ cockpitCurrency }}</small></article>
              <article><span>经营结果天数</span><b>{{ cockpitSummary.resultCount || 0 }}</b><small>{{ cockpitDateRange }}</small></article>
            </section>
            <div class="cockpit-columns">
              <section class="cockpit-card">
                <div class="cockpit-card-head"><div><h3>KPI目标与差距</h3><p>当前生效目标及已填报实际值</p></div><el-button link type="primary" @click="openKpiWorkspace">进入KPI工作区</el-button></div>
                <div v-if="cockpitSettlement" class="kpi-settlement-summary">
                  <div><span>结算状态</span><b>{{ settlementStatusText }}</b></div>
                  <div><span>综合得分</span><b>{{ cockpitSettlement.totalScore ?? '待计算' }}</b></div>
                  <div><span>命中档位</span><b>{{ matchedTier?.tierName || '未命中' }}</b></div>
                  <div><span>{{ cockpitSettlement.status==='CONFIRMED'?'确认奖金':'预计奖金' }}</span><b>¥{{ money(cockpitSettlement.bonusAmount) }}</b></div>
                </div>
                <div v-if="!cockpitKpis.length" class="cockpit-empty">尚未设置项目KPI</div>
                <div v-for="item in cockpitKpis" :key="item.itemId||item.kpiId" class="kpi-overview-row">
                  <div><b>{{ item.kpiName }}</b><small>目标 {{ item.targetValue }} {{ item.unit || '' }} · 当前 {{ kpiActualText(item) }}</small></div>
                  <div><strong>{{ kpiCompletion(item) }}%</strong><el-progress :percentage="kpiCompletion(item)" :stroke-width="7" /></div>
                  <span>{{ kpiGapText(item) }}</span>
                </div>
              </section>
              <section class="cockpit-card">
                <div class="cockpit-card-head"><div><h3>执行与风险</h3><p>项目当前需要关注的事项</p></div></div>
                <div class="execution-overview-grid">
                  <div><span>一次性任务</span><b>{{ detail.completedTaskCount || 0 }} / {{ detail.taskCount || detail.tasks?.length || 0 }}</b></div>
                  <div><span>未完成任务</span><b>{{ openTaskCount }}</b></div>
                  <div><span>逾期任务</span><b :class="{danger:overdueTaskCount}">{{ overdueTaskCount }}</b></div>
                  <div><span>开放风险</span><b :class="{danger:openRiskCount}">{{ openRiskCount }}</b></div>
                  <div><span>项目成员</span><b>{{ detail.members?.length || 0 }}</b></div>
                  <div><span>计划投入配置</span><b>{{ operating.staffAllocations?.length || 0 }}</b></div>
                </div>
              </section>
            </div>
          </el-tab-pane>
          <el-tab-pane label="经营配置" name="operating">
            <div class="operating-grid">
              <section class="operating-card budget-card"><div class="operating-head"><div><small>项目预算上限</small><strong>{{ money(operating.budgetLimit) }} {{ operating.currency || detail.baseCurrency }}</strong></div><el-button v-if="isBoss||myRole==='OWNER'" size="small" type="primary" @click="openBudgetDialog">调整预算</el-button></div><p>负责人可按经营变化调整预算；每次金额、原因、操作人和版本都会保留，老板可随时查看和修正。</p></section>
              <section class="operating-card"><div class="operating-head"><div><small>当前KPI</small><strong>{{ currentKpis.length }} 项</strong></div><el-button v-if="isBoss||myRole==='OWNER'" size="small" type="primary" @click="router.push({path:'/business/kpi-bonus',query:{projectId:detail.projectId}})">配置KPI与奖金</el-button></div><p>项目负责人设置目标、发布方案并完成结算；老板保留修改能力。</p></section>
              <section class="operating-card"><div class="operating-head"><div><small>成员计划投入</small><strong>{{ operating.staffAllocations?.length || 0 }} 项</strong></div><el-button v-if="canManageAllocation" size="small" type="primary" @click="openAllocationDialog()">新增计划</el-button></div><p>{{ canManageAllocation ? '由项目主负责人安排成员投入比例。' : '计划投入由项目主负责人维护，当前账号只读。' }}</p></section>
            </div>
            <div class="tab-tools section-gap"><b>项目KPI</b><span class="muted">共 {{ operating.kpis?.length || 0 }} 个版本</span></div>
            <el-table :data="currentKpis" size="small" empty-text="尚未设置KPI"><el-table-column prop="kpiName" label="指标" min-width="150"><template #default="{row}"><b>{{ row.kpiName }}</b><small class="subline">{{ row.kpiCode }} · v{{ row.targetVersion }}</small></template></el-table-column><el-table-column label="项目目标" min-width="130"><template #default="{row}">{{ row.targetValue }} {{ row.unit || '' }}</template></el-table-column><el-table-column label="方向" width="95"><template #default="{row}">{{ row.direction==='LOWER_BETTER'?'越低越好':'越高越好' }}</template></el-table-column><el-table-column label="周期" width="90"><template #default="{row}">{{ periodLabel[row.periodType] }}</template></el-table-column><el-table-column prop="weight" label="权重%" width="78" /></el-table>
            <el-collapse class="history-collapse"><el-collapse-item title="查看KPI历史版本"><el-table :data="retiredKpis" size="small" empty-text="暂无历史版本"><el-table-column prop="kpiName" label="指标" /><el-table-column prop="targetVersion" label="版本" width="70" /><el-table-column prop="targetValue" label="目标值" /><el-table-column prop="effectiveFrom" label="生效" /><el-table-column prop="effectiveTo" label="失效" /><el-table-column prop="remark" label="调整说明" /></el-table></el-collapse-item></el-collapse>
            <div class="tab-tools section-gap"><b>成员计划投入</b><span class="muted">主负责人填写工作投入；老板设置月度成本并只读查看折算结果</span></div>
            <el-table :data="operating.staffAllocations || []" size="small" empty-text="尚未配置成员计划投入"><el-table-column prop="userName" label="人员" /><el-table-column label="投入方式"><template #default="{row}">{{ allocationModeLabel[row.allocationMode] }}</template></el-table-column><el-table-column label="计划投入"><template #default="{row}">{{ row.allocationValue }}{{ row.allocationMode==='PERCENTAGE'?'%':'' }}</template></el-table-column><el-table-column label="预计人员成本/天"><template #default="{row}"><b>{{ money(row.allocatedCost) }} {{ row.currency || operating.currency }}</b></template></el-table-column><el-table-column v-if="operating.rawCostVisible" label="内部成本原价"><template #default="{row}">{{ money(row.unitCost) }} {{ row.currency }} / {{ costModeLabel[row.costMode] }}</template></el-table-column><el-table-column label="生效区间" min-width="170"><template #default="{row}">{{ row.effectiveFrom }} 至 {{ row.effectiveTo || '长期' }}</template></el-table-column><el-table-column v-if="canManageAllocation" label="操作" width="105"><template #default="{row}"><el-button link @click="openAllocationDialog(row)">编辑</el-button><el-button link type="danger" @click="removeAllocation(row)">停用</el-button></template></el-table-column></el-table>
            <div class="tab-tools section-gap"><b>预算调整历史</b></div>
            <el-table :data="operating.budgetHistory || []" size="small" empty-text="暂无预算记录"><el-table-column label="版本" width="70"><template #default="{row}">v{{ row.budgetVersion }}</template></el-table-column><el-table-column label="调整"><template #default="{row}">{{ money(row.fromAmount) }} → {{ money(row.toAmount) }} {{ row.currency }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="160" /><el-table-column prop="operatorName" label="操作人" width="100" /><el-table-column prop="effectiveTime" label="生效时间" width="165" /></el-table>
          </el-tab-pane>
          <el-tab-pane label="持续工作" name="routines">
            <div class="tab-tools"><div><b>持续工作计划</b><span class="muted routine-tip">直播主播由执行系统自动同步，日报仍只在直播数据管理中提交。</span></div><el-button v-if="canManage" size="small" type="primary" @click="openRoutine()">新增持续工作</el-button></div>
            <el-table :data="detail.routines || []" size="small" empty-text="尚未设置持续工作">
              <el-table-column label="工作内容" min-width="220"><template #default="{row}"><b>{{ row.routineName }}</b><small v-if="row.sourceManaged" class="subline">直播同步 · 数据日期 {{ row.sourceBizDate }}</small></template></el-table-column>
              <el-table-column label="周期目标" min-width="130"><template #default="{row}">{{ frequencyLabel[row.frequency] }} {{ row.targetValue }} {{ row.unit }}</template></el-table-column>
              <el-table-column label="负责人" width="110"><template #default="{row}"><span :class="{ danger: !row.assigneeUserId }">{{ row.assigneeName || '未分配' }}</span></template></el-table-column>
              <el-table-column label="当前状态" min-width="115"><template #default="{row}"><el-tag v-if="row.sourceManaged" :type="row.todayReportId?'success':'warning'">{{ row.todayReportId ? '已提交' : '未提交' }}</el-tag><span v-else>{{ row.cumulativeActual || 0 }} {{ row.unit }}</span></template></el-table-column>
              <el-table-column label="监督 / 执行区间" min-width="210"><template #default="{row}"><span v-if="row.sourceManaged">{{ row.supervisorName }}监督 · {{ row.startDate }} 至 {{ row.endDate || '长期' }}</span><span v-else>{{ row.startDate }} 至 {{ row.endDate || '长期' }}</span></template></el-table-column>
              <el-table-column v-if="canManage" label="操作" width="110"><template #default="{row}"><el-tag v-if="row.sourceManaged" type="info" effect="plain">自动同步</el-tag><template v-else><el-button link @click="openRoutine(row)">编辑</el-button><el-button link type="danger" @click="removeRoutine(row)">停用</el-button></template></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="一次性任务" name="tasks"><div class="tab-tools"><div><b>一次性任务</b><span class="muted routine-tip">用于有明确完成时点的事项；需验收里程碑时，请把任务关联到对应里程碑。</span></div><el-button v-if="canManage" size="small" type="primary" @click="openItem('task')">新增任务</el-button></div><el-table :data="detail.tasks" size="small"><el-table-column prop="taskName" label="任务" min-width="180" /><el-table-column v-if="showMilestones" label="所属里程碑" min-width="130"><template #default="{row}">{{ milestoneName(row.milestoneId) }}</template></el-table-column><el-table-column prop="assigneeName" label="负责人" width="110" /><el-table-column label="状态" width="100"><template #default="{row}">{{ taskStatusLabel[row.status] }}</template></el-table-column><el-table-column label="进度" width="130"><template #default="{row}"><el-progress :percentage="row.progress || 0" :stroke-width="7" /></template></el-table-column><el-table-column prop="dueDate" label="截止日期" width="115" /><el-table-column v-if="canManage" label="操作" width="110"><template #default="{row}"><el-button link @click="openItem('task',row)">编辑</el-button><el-button link type="danger" @click="removeItem('task',row)">删除</el-button></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane label="成员" name="members"><div class="tab-tools"><b>项目成员</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('member')">添加成员</el-button></div><el-table :data="detail.members" size="small"><el-table-column prop="userNameSnapshot" label="姓名" /><el-table-column label="项目角色"><template #default="{row}">{{ memberRoleLabel[row.memberRole] }}</template></el-table-column><el-table-column prop="joinedDate" label="加入日期" /><el-table-column v-if="canManage" label="操作" width="80"><template #default="{row}"><el-button v-if="row.memberRole !== 'OWNER' && (row.memberRole !== 'DEPUTY' || canManageDeputies)" link type="danger" @click="removeItem('member',row)">移除</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane v-if="showMilestones" label="里程碑" name="milestones"><div class="tab-tools"><b>关键里程碑</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('milestone')">新增里程碑</el-button></div><el-table :data="detail.milestones" size="small"><el-table-column prop="milestoneName" label="里程碑" /><el-table-column prop="planDate" label="计划日期" width="115" /><el-table-column label="状态" width="100"><template #default="{row}">{{ milestoneStatusLabel[row.status] || row.status }}</template></el-table-column><el-table-column v-if="canManage" label="操作" width="110"><template #default="{row}"><template v-if="!['REVIEWING','DONE'].includes(row.status)"><el-button link @click="openItem('milestone',row)">编辑</el-button><el-button link type="danger" @click="removeItem('milestone',row)">删除</el-button></template><el-tag v-else type="info" effect="plain">验收锁定</el-tag></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane v-if="showRisks" label="风险" name="risks"><div class="tab-tools"><b>风险台账</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('risk')">登记风险</el-button></div><el-table :data="detail.risks" size="small"><el-table-column prop="riskTitle" label="风险" min-width="180" /><el-table-column label="等级" width="90"><template #default="{row}"><el-tag :type="['HIGH','CRITICAL'].includes(row.severity)?'danger':'warning'">{{ severityLabel[row.severity] }}</el-tag></template></el-table-column><el-table-column prop="ownerName" label="负责人" width="110" /><el-table-column prop="dueDate" label="处理期限" width="115" /><el-table-column label="状态" width="90"><template #default="{row}">{{ riskStatusLabel[row.status] }}</template></el-table-column><el-table-column v-if="canManage" label="操作" width="110"><template #default="{row}"><el-button link @click="openItem('risk',row)">编辑</el-button><el-button link type="danger" @click="removeItem('risk',row)">删除</el-button></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane v-if="detail.closeMethod==='RESULT_ACCEPTANCE'" label="成果验收" name="acceptance"><div class="tab-tools"><b>验收资料与老板意见</b><el-button v-if="canSubmitAcceptance" size="small" type="success" @click="openAcceptanceSubmit">提交验收</el-button></div>
            <section v-if="showKpiClosureGuard" :class="['kpi-close-guard', 'in-tab', `is-${kpiClosureState.tone}`]">
              <div class="kpi-close-mark">KPI</div>
              <div class="kpi-close-copy">
                <div class="kpi-close-title"><span>验收通过前置条件</span><el-tag :type="kpiClosureState.tone" effect="light">{{ kpiClosureState.label }}</el-tag></div>
                <b>{{ kpiClosureState.title }}</b>
                <p>{{ kpiClosureState.description }}</p>
                <div v-if="kpiClosureState.planCount" class="kpi-close-progress"><span>已确认 {{ kpiClosureState.confirmedCount }} / {{ kpiClosureState.planCount }} 个方案</span><el-progress :percentage="kpiClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
              </div>
              <el-button :type="kpiClosureState.actionType" plain @click="openKpiWorkspace">{{ kpiClosureState.actionLabel }}</el-button>
            </section>
            <el-alert v-if="detail.status==='ACTIVE'" title="完成全部任务和里程碑、处理高风险事项后，才能提交验收。" type="info" :closable="false" show-icon />
            <el-empty v-if="!detail.acceptances?.length" description="尚未提交验收资料" />
            <article v-for="record in detail.acceptances" :key="record.acceptanceId" class="acceptance-record">
              <div class="acceptance-head"><div><b>第 {{ record.submissionVersion }} 次提交</b><span>{{ record.submittedUserName }} · {{ record.submittedTime }}</span></div><el-tag :type="acceptanceTone[record.reviewStatus]">{{ acceptanceLabel[record.reviewStatus] }}</el-tag></div>
              <h4>结果摘要</h4><p>{{ record.resultSummary }}</p><h4>交付成果</h4><p>{{ record.deliverables }}</p>
              <business-file-upload v-if="record.attachmentUrls" v-model="record.attachmentUrls" :project-id="detail.projectId" disabled :is-show-tip="false" />
              <div v-if="record.reviewStatus!=='PENDING'" class="review-result"><b>{{ record.reviewedUserName }}的验收意见</b><p>{{ record.reviewComment || (record.reviewStatus==='APPROVED'?'验收通过':'已退回') }}</p><small>{{ record.reviewedTime }}</small></div>
              <div v-if="record.reviewStatus==='PENDING' && isBoss" class="review-actions"><el-button type="success" :disabled="kpiClosureState.ready===false" :title="kpiClosureState.ready===false?'请先完成并确认全部KPI结算':''" @click="openAcceptanceReview('APPROVED',record)">验收通过并关闭</el-button><el-button type="warning" plain @click="openAcceptanceReview('RETURNED',record)">退回执行</el-button></div>
            </article>
          </el-tab-pane>
          <el-tab-pane v-if="showMilestones" label="里程碑验收" name="stageAcceptance">
            <el-alert title="每个里程碑关联的任务全部完成后，由负责人提交阶段成果，老板通过后该里程碑才算完成。" type="info" :closable="false" show-icon />
            <div class="stage-grid">
              <article v-for="milestone in detail.milestones || []" :key="milestone.milestoneId" class="acceptance-record">
                <div class="acceptance-head"><div><b>{{ milestone.milestoneName }}</b><span>计划日期 {{ milestone.planDate || '未设置' }}</span></div><el-tag :type="milestone.status==='DONE'?'success':milestone.status==='REVIEWING'?'warning':'info'">{{ milestoneStatusLabel[milestone.status] || milestone.status }}</el-tag></div>
                <template v-for="record in stageRecords(milestone.milestoneId)" :key="record.stageAcceptanceId"><h4>第 {{ record.submissionVersion }} 次提交 · {{ record.submittedUserName }}</h4><p>{{ record.resultSummary }}</p><p class="muted">交付成果：{{ record.deliverables }}</p><business-file-upload v-if="record.attachmentUrls" v-model="record.attachmentUrls" :project-id="detail.projectId" disabled :is-show-tip="false" /><div v-if="record.reviewStatus==='PENDING'&&isBoss" class="review-actions"><el-button type="success" @click="openStageReview('APPROVED',record)">阶段通过</el-button><el-button type="warning" plain @click="openStageReview('RETURNED',record)">退回补充</el-button></div><div v-else-if="record.reviewStatus!=='PENDING'" class="review-result"><b>{{ acceptanceLabel[record.reviewStatus] }}</b><p>{{ record.reviewComment || '—' }}</p></div></template>
                <el-button v-if="canSubmitStage(milestone)" class="stage-submit" type="primary" plain @click="openStageSubmit(milestone)">提交该阶段验收</el-button>
              </article>
            </div>
          </el-tab-pane>
          <el-tab-pane label="负责人交接" name="ownerHistory"><el-table :data="detail.ownerHistory" size="small" empty-text="暂无交接记录"><el-table-column label="变更"><template #default="{row}">{{ row.fromUserName || '初始任命' }} → {{ row.toUserName }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="180" /><el-table-column prop="operatorName" label="操作人" width="100" /><el-table-column prop="effectiveTime" label="生效时间" width="165" /></el-table></el-tab-pane>
          <el-tab-pane label="动态" name="events">
            <el-empty v-if="!detail.events?.length" description="暂无项目动态" />
            <el-timeline v-else class="event-line">
              <el-timeline-item v-for="event in detail.events" :key="event.eventId" :timestamp="formatEventTime(event.createTime)" :type="eventTone(event)">
                <article class="event-card">
                  <div class="event-head">
                    <div class="event-actor">
                      <b>{{ eventSummary(event) }}</b>
                      <small v-if="event.operatorAccount && event.operatorName !== '系统'">操作账号：{{ event.operatorAccount }}</small>
                    </div>
                    <el-tag size="small" effect="plain" :type="eventTone(event)">{{ eventLabel[event.eventType] || '其他操作' }}</el-tag>
                  </div>
                  <p v-if="formatEventComment(event)"><span class="event-detail-label">{{ eventDetailLabel(event) }}</span>{{ formatEventComment(event) }}</p>
                  <small v-if="eventStatusChange(event)" class="event-status">项目状态：{{ eventStatusChange(event) }}</small>
                </article>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="projectDialog" title="编辑项目资料" width="min(680px, 94vw)" destroy-on-close>
      <el-form ref="projectFormRef" :model="projectForm" :rules="projectRules" label-width="100px">
        <el-row :gutter="16"><el-col :sm="16" :xs="24"><el-form-item label="项目名称" prop="projectName"><el-input v-model="projectForm.projectName" maxlength="160" /></el-form-item></el-col><el-col :sm="8" :xs="24"><el-form-item label="优先级"><el-select v-model="projectForm.priority"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :sm="12" :xs="24"><el-form-item label="项目类型"><el-select v-model="projectForm.projectType"><el-option v-for="(label,key) in typeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="核算方式"><el-select v-model="projectForm.accountingMode"><el-option v-for="(label,key) in accountingLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="管理模式"><el-select v-model="projectForm.managementMode" style="width:100%"><el-option label="轻量 · 核心执行，异常驱动" value="LIGHT"/><el-option label="标准 · 周度跟踪、里程碑、风险" value="STANDARD"/><el-option label="重点监管 · 强化预警和变更管控" value="KEY_CONTROL"/></el-select><small class="form-tip">管理模式决定过程管控强度，不再代替结项方式。</small></el-form-item>
        <el-form-item label="结项方式"><el-select v-model="projectForm.closeMethod" style="width:100%"><el-option label="直接结项" value="DIRECT"/><el-option label="成果验收" value="RESULT_ACCEPTANCE"/><el-option label="阶段验收" value="STAGED_ACCEPTANCE"/></el-select><small class="form-tip">结项方式独立决定最终成果如何确认。</small></el-form-item>
        <el-form-item v-if="projectForm.managementMode==='KEY_CONTROL'" label="监管原因" required><el-input v-model="projectForm.managementReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item v-if="projectForm.closeMethod!=='DIRECT'" label="验收标准" required><el-input v-model="projectForm.acceptanceCriteria" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item v-if="governanceChanged" label="变更原因" required><el-input v-model="projectForm.governanceChangeReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明为何调整管理模式或结项方式" /></el-form-item>
        <el-form-item label="主负责人"><el-input :model-value="projectForm.mainOwnerName" disabled /><small class="form-tip">负责人变更请使用项目详情中的“更换主负责人”。</small></el-form-item>
        <el-form-item label="归属公司" prop="companyDeptId"><el-select v-if="isBoss" v-model="projectForm.companyDeptId" placeholder="选择上海或越南公司" style="width:100%"><el-option v-for="c in companies" :key="c.companyDeptId" :label="c.companyName" :value="c.companyDeptId" /></el-select><el-input v-else :model-value="projectForm.companyName || '待老板设置'" disabled /></el-form-item>
        <el-form-item v-if="isBoss" label="执行系统"><el-checkbox v-model="projectForm.executionSource" true-label="LIVE" false-label="">关联直播数据管理</el-checkbox><small class="form-tip">只读取已确认的汇总结果，不开放直播原始明细或审核权限。</small></el-form-item>
        <el-form-item label="上级项目"><el-select v-model="projectForm.parentId" clearable filterable style="width:100%"><el-option v-for="p in parentOptions" :key="p.projectId" :label="p.projectName" :value="p.projectId" /></el-select></el-form-item>
        <el-form-item label="项目目标" prop="objective"><el-input v-model="projectForm.objective" type="textarea" :rows="3" placeholder="定义可验收的业务目标" /></el-form-item>
        <el-form-item label="计划周期" required><div class="project-period-line"><el-date-picker v-model="projectForm.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width:100%" /><span>至</span><el-date-picker v-model="projectForm.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled="projectOpenEnded" :disabled-date="disableProjectEndDate" :placeholder="projectOpenEnded ? '不限期' : '结束日期'" style="width:100%" /><el-checkbox v-model="projectOpenEnded" @change="handleProjectOpenEndedChange">不限期</el-checkbox></div></el-form-item>
        <el-row :gutter="16"><el-col :sm="12" :xs="24"><el-form-item label="预算上限"><el-input-number v-model="projectForm.budgetLimit" :disabled="!!projectForm.projectId" :min="0" :precision="2" style="width:100%" /><small v-if="projectForm.projectId" class="form-tip">请在“经营配置”中调整并填写原因</small></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="币种"><el-input v-model="projectForm.baseCurrency" :disabled="!!projectForm.projectId" maxlength="3" /></el-form-item></el-col></el-row>
        <el-form-item label="备注"><el-input v-model="projectForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="projectDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProject">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="budgetDialog" title="调整项目预算" width="min(520px, 94vw)" append-to-body><el-alert title="预算调整会立即生效并永久保留历史记录。" type="warning" :closable="false" show-icon/><el-form :model="budgetForm" label-width="90px" class="decision-form"><el-form-item label="预算金额" required><el-input-number v-model="budgetForm.budgetLimit" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="币种" required><el-input v-model="budgetForm.currency" maxlength="3" /></el-form-item><el-form-item label="调整原因" required><el-input v-model="budgetForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="budgetDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveBudget">确认调整</el-button></template></el-dialog>

    <el-dialog v-model="kpiDialog" :title="kpiForm.kpiId?'调整KPI目标':'新增项目KPI'" width="min(680px, 94vw)" append-to-body><el-alert v-if="kpiForm.kpiId" title="保存后会生成新的目标版本，原版本不会被覆盖。" type="info" :closable="false" show-icon/><el-form :model="kpiForm" label-width="92px" class="decision-form"><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="系统编码"><el-input v-model="kpiForm.kpiCode" disabled placeholder="保存后自动生成" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="KPI名称" required><el-input v-model="kpiForm.kpiName" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="指标类型"><el-select v-model="kpiForm.metricType" style="width:100%"><el-option v-for="(label,key) in metricTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="统计周期"><el-select v-model="kpiForm.periodType" style="width:100%"><el-option v-for="(label,key) in periodLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="目标值" required><el-input-number v-model="kpiForm.targetValue" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="当前实际值"><el-input-number v-model="kpiForm.actualValue" :precision="4" style="width:100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="单位"><el-input v-model="kpiForm.unit" placeholder="个、元、%等" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="权重%"><el-input-number v-model="kpiForm.weight" :min="0" :max="100" :precision="2" style="width:100%" /></el-form-item></el-col></el-row><el-form-item label="指标负责人"><el-select v-model="kpiForm.ownerUserId" clearable style="width:100%"><el-option v-for="m in detail?.members || []" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item label="生效日期" required><el-date-picker v-model="kpiForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="调整说明"><el-input v-model="kpiForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="kpiDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveKpi">保存KPI版本</el-button></template></el-dialog>

    <el-dialog v-model="allocationDialog" :title="allocationForm.allocationId?'编辑计划投入':'新增计划投入'" width="min(620px, 94vw)" append-to-body><el-alert title="主负责人只填写项目投入比例；系统用老板设置的月度用人成本和所属国家折算规则自动计算项目日成本。" type="info" :closable="false" show-icon/><el-form :model="allocationForm" label-width="100px" class="decision-form"><el-form-item label="项目人员" required><el-select v-model="allocationForm.userId" :disabled="!!allocationForm.allocationId" style="width:100%"><el-option v-for="m in detail?.members || []" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item label="投入方式"><el-input model-value="按项目投入比例" disabled /></el-form-item><el-form-item label="计划投入" required><el-input-number v-model="allocationForm.allocationValue" :min="0" :max="100" :precision="2" style="width:100%" /><small class="form-tip">填写 0–100，例如投入一半工作精力填写 50%。</small></el-form-item><el-form-item label="生效区间"><div class="allocation-period-field"><el-checkbox v-model="allocationFollowProject" @change="syncAllocationProjectPeriod">跟随项目周期</el-checkbox><el-date-picker v-model="allocationDates" :disabled="allocationFollowProject" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期（可不填）" style="width:100%" /><small v-if="allocationFollowProject" class="form-tip">自动使用项目周期：{{ detail?.planStartDate || '未设置开始日期' }} 至 {{ detail?.planEndDate || '长期' }}</small></div></el-form-item><el-form-item label="安排说明"><el-input v-model="allocationForm.remark" type="textarea" :rows="2" /></el-form-item></el-form><template #footer><el-button @click="allocationDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAllocation">保存计划</el-button></template></el-dialog>

    <el-dialog v-model="routineDialog" :title="routineForm.routineId?'编辑持续工作':'新增持续工作'" width="min(620px, 94vw)" append-to-body>
      <el-alert title="持续工作不会被一次性完成。负责人按日填报实际完成量，系统长期累计。" type="info" :closable="false" show-icon />
      <el-form :model="routineForm" label-width="96px" class="decision-form">
        <el-form-item label="工作内容" required><el-input v-model="routineForm.routineName" maxlength="200" placeholder="例如：短视频剪辑发布" /></el-form-item>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="目标周期" required><el-select v-model="routineForm.frequency" style="width:100%"><el-option v-for="(label,key) in frequencyLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="负责人" required><el-select v-model="routineForm.assigneeUserId" filterable style="width:100%"><el-option v-for="m in detail?.members || []" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="周期目标" required><el-input-number v-model="routineForm.targetValue" :min="0.0001" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="单位" required><el-input v-model="routineForm.unit" maxlength="30" placeholder="条、个、场等" /></el-form-item></el-col></el-row>
        <el-form-item label="执行区间" required><div class="routine-period-line"><el-date-picker v-model="routineForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width:100%" /><span>至</span><el-date-picker v-model="routineForm.endDate" type="date" value-format="YYYY-MM-DD" :disabled="routineLongTerm" :disabled-date="disableRoutineEndDate" :placeholder="routineLongTerm ? '长期' : '结束日期'" style="width:100%" /><el-checkbox v-model="routineLongTerm" @change="handleRoutineLongTermChange">长期</el-checkbox></div></el-form-item>
        <el-form-item><el-checkbox v-model="routineForm.evidenceRequired" true-label="1" false-label="0">每日填报必须上传凭证</el-checkbox></el-form-item>
        <el-form-item label="说明"><el-input v-model="routineForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明口径、质量要求或交付位置" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="routineDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRoutine">保存持续工作</el-button></template>
    </el-dialog>

    <el-dialog v-model="ownerDialog" title="更换项目主负责人" width="min(520px, 94vw)" append-to-body>
      <el-alert title="更换后原负责人保留为普通项目成员，交接原因会永久记录。" type="warning" :closable="false" show-icon />
      <el-form :model="ownerForm" label-width="100px" class="decision-form"><el-form-item label="新负责人" required><el-select v-model="ownerForm.ownerUserId" filterable style="width:100%"><el-option v-for="u in ownerCandidates" :key="u.userId" :label="userOptionLabel(u)" :value="u.userId" /></el-select></el-form-item><el-form-item label="变更原因" required><el-input v-model="ownerForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="ownerDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveOwner">确认更换</el-button></template>
    </el-dialog>

    <el-dialog v-model="acceptanceDialog" title="提交项目验收资料" width="min(660px, 94vw)" append-to-body>
      <el-form :model="acceptanceForm" label-width="100px" class="decision-form"><el-form-item label="结果摘要" required><el-input v-model="acceptanceForm.resultSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="说明项目目标完成情况和最终结果" /></el-form-item><el-form-item label="交付成果" required><el-input v-model="acceptanceForm.deliverables" type="textarea" :rows="5" maxlength="4000" show-word-limit placeholder="逐项列出可验收的成果、文件或业务结果" /></el-form-item><el-form-item label="成果附件"><business-file-upload v-model="acceptanceForm.attachmentUrls" :project-id="detail.projectId" /></el-form-item></el-form>
      <template #footer><el-button @click="acceptanceDialog=false">取消</el-button><el-button type="success" :loading="saving" @click="saveAcceptance">提交老板验收</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" :title="reviewForm.decision==='APPROVED'?'验收通过并关闭项目':'退回项目继续执行'" width="min(560px, 94vw)" append-to-body>
      <el-alert :title="reviewForm.decision==='APPROVED'?'确认后项目将正式关闭并只读保存。':'退回后项目恢复执行，负责人可修改后再次提交。'" :type="reviewForm.decision==='APPROVED'?'success':'warning'" :closable="false" show-icon />
      <el-form :model="reviewForm" label-width="90px" class="decision-form"><el-form-item label="验收意见" :required="reviewForm.decision==='RETURNED'"><el-input v-model="reviewForm.comment" type="textarea" :rows="5" maxlength="2000" show-word-limit :placeholder="reviewForm.decision==='APPROVED'?'可填写验收结论':'必须说明退回原因和需要补充的内容'" /></el-form-item></el-form>
      <template #footer><el-button @click="reviewDialog=false">取消</el-button><el-button :type="reviewForm.decision==='APPROVED'?'success':'warning'" :loading="saving" @click="saveAcceptanceReview">确认</el-button></template>
    </el-dialog>

    <el-dialog v-model="stageDialog" :title="`确认阶段成果 · ${stageForm.milestoneName || ''}`" width="min(660px,94vw)" append-to-body><el-form :model="stageForm" label-width="100px"><el-form-item label="阶段结果" required><el-input v-model="stageForm.resultSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item><el-form-item label="交付成果" required><el-input v-model="stageForm.deliverables" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item><el-form-item label="成果附件"><business-file-upload v-model="stageForm.attachmentUrls" :project-id="detail.projectId" /></el-form-item></el-form><template #footer><el-button @click="stageDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStageAcceptance">确认阶段完成</el-button></template></el-dialog>
    <el-dialog v-model="stageReviewDialog" :title="stageReviewForm.decision==='APPROVED'?'阶段验收通过':'退回阶段成果'" width="min(540px,94vw)" append-to-body><el-form :model="stageReviewForm" label-width="90px"><el-form-item label="验收意见" :required="stageReviewForm.decision==='RETURNED'"><el-input v-model="stageReviewForm.comment" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="stageReviewDialog=false">取消</el-button><el-button :type="stageReviewForm.decision==='APPROVED'?'success':'warning'" :loading="saving" @click="saveStageReview">确认</el-button></template></el-dialog>

    <el-dialog v-model="itemDialog" :title="itemTitle" width="min(560px, 94vw)" destroy-on-close>
      <el-form :model="itemForm" label-width="92px">
        <template v-if="itemKind === 'member'"><el-form-item label="公司" required><el-select v-model="itemForm.companyKey" filterable placeholder="请先选择公司" style="width:100%" @change="itemForm.userId=null"><el-option v-for="company in memberCompanyOptions" :key="company.key" :label="company.companyName" :value="company.key" /></el-select></el-form-item><el-form-item label="人员" required><el-select v-model="itemForm.userId" filterable :disabled="!itemForm.companyKey" placeholder="输入姓名、账号或部门查询" style="width:100%"><el-option v-for="u in memberUserOptions" :key="u.userId" :label="userOptionLabel(u)" :value="u.userId" /></el-select><small v-if="itemForm.companyKey&&!memberUserOptions.length" class="form-tip">该公司暂无可添加人员。</small></el-form-item><el-form-item label="项目角色"><el-select v-model="itemForm.memberRole"><el-option v-if="canManageDeputies" label="副负责人" value="DEPUTY"/><el-option label="成员" value="MEMBER"/><el-option label="观察者" value="OBSERVER"/></el-select><small v-if="!canManageDeputies" class="form-tip">副负责人只能由主负责人或老板任命。</small></el-form-item></template>
        <template v-else-if="itemKind === 'task'"><el-form-item label="任务名称"><el-input v-model="itemForm.taskName" /></el-form-item><el-form-item label="负责人"><el-select v-model="itemForm.assigneeUserId" clearable style="width:100%"><el-option v-for="m in detail.members" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item v-if="showMilestones" label="所属里程碑"><el-select v-model="itemForm.milestoneId" clearable style="width:100%"><el-option v-for="m in detail.milestones || []" :key="m.milestoneId" :label="m.milestoneName" :value="m.milestoneId" /></el-select></el-form-item><el-form-item label="状态"><el-input :model-value="taskStatusLabel[itemForm.status] || '待开始'" disabled /></el-form-item><el-form-item label="进度"><el-slider v-model="itemForm.progress" show-input disabled /><small class="form-tip">进度由任务负责人在“我的安排”中填报，项目负责人不可修改。</small></el-form-item><el-form-item label="截止日期"><el-date-picker v-model="itemForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></template>
        <template v-else-if="itemKind === 'milestone'"><el-form-item label="名称"><el-input v-model="itemForm.milestoneName" /></el-form-item><el-form-item label="计划日期"><el-date-picker v-model="itemForm.planDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="状态"><el-select v-if="!['REVIEWING','DONE'].includes(itemForm.status)" v-model="itemForm.status"><el-option label="未开始" value="PENDING" /><el-option label="进行中" value="DOING" /></el-select><el-input v-else :model-value="milestoneStatusLabel[itemForm.status] || itemForm.status" disabled /><small class="form-tip">负责人提交阶段结果和交付凭证后，必须由老板检验通过才算完成。</small></el-form-item></template>
        <template v-else-if="itemKind === 'risk'"><el-form-item label="风险标题"><el-input v-model="itemForm.riskTitle" /></el-form-item><el-form-item label="风险等级"><el-select v-model="itemForm.severity"><el-option v-for="(label,key) in severityLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="负责人"><el-select v-model="itemForm.ownerUserId" clearable style="width:100%"><el-option v-for="m in detail.members" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item label="处理期限"><el-date-picker v-model="itemForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="状态"><el-select v-model="itemForm.status"><el-option v-for="(label,key) in riskStatusLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="应对方案"><el-input v-model="itemForm.responsePlan" type="textarea" :rows="3" /></el-form-item></template>
      </el-form>
      <template #footer><el-button @click="itemDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveItem">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessProject">
import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { getBusinessAccountingDashboard } from '@/api/business/accounting'
import { getProjectKpiWorkspace } from '@/api/business/kpi'
import { getBusinessProjectDashboard } from '@/api/business/accounting'
import { changeBusinessProjectOwner, getBusinessOperatingConfig, getBusinessProject, listBusinessProjects, listBusinessUsers, removeBusinessMilestone, removeBusinessProjectMember, removeBusinessRisk, removeBusinessRoutine, removeBusinessStaffAllocation, removeBusinessTask, retireBusinessProjectKpi, reviewBusinessProjectAcceptance, reviewBusinessProjectStageAcceptance, saveBusinessMilestone, saveBusinessProjectKpi, saveBusinessProjectMember, saveBusinessRisk, saveBusinessRoutine, saveBusinessStaffAllocation, saveBusinessTask, submitBusinessProjectAcceptance, submitBusinessProjectStageAcceptance, transitionBusinessProject, updateBusinessProject, updateBusinessProjectBudget } from '@/api/business/project'

const route = useRoute(), router = useRouter(), userStore = useUserStore()
const loading = ref(false), saving = ref(false), total = ref(0), rows = ref([]), users = ref([]), companies=ref([])
const detailVisible = ref(false), detail = ref(null), activeTab = ref('overview')
const projectDialog = ref(false), projectFormRef = ref(), projectOpenEnded = ref(false), projectForm = ref({})
const itemDialog = ref(false), itemKind = ref(''), itemForm = ref({})
const ownerDialog=ref(false),ownerForm=reactive({ownerUserId:null,reason:''})
const acceptanceDialog=ref(false),acceptanceForm=reactive({resultSummary:'',deliverables:'',attachmentUrls:''})
const reviewDialog=ref(false),reviewForm=reactive({decision:'',comment:'',acceptanceId:null})
const stageDialog=ref(false),stageForm=reactive({milestoneId:null,milestoneName:'',resultSummary:'',deliverables:'',attachmentUrls:''})
const stageReviewDialog=ref(false),stageReviewForm=reactive({milestoneId:null,decision:'',comment:''})
const operating=ref({kpis:[],budgetHistory:[],staffAllocations:[]}),budgetDialog=ref(false),budgetForm=reactive({budgetLimit:null,currency:'CNY',reason:''})
const kpiWorkspace=ref({plans:[]}),kpiWorkspaceLoading=ref(false),kpiWorkspaceError=ref(false)
const cockpit=ref({summary:{},results:[]}),cockpitLoading=ref(false),cockpitError=ref(false)
const kpiDialog=ref(false),kpiForm=reactive({}),allocationDialog=ref(false),allocationForm=reactive({}),allocationDates=ref([]),allocationFollowProject=ref(true)
const routineDialog=ref(false),routineForm=reactive({}),routineLongTerm=ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', managementMode:'', closeMethod:'' })
const statusLabel = { DRAFT:'草稿', PLANNING:'规划中', ACTIVE:'执行中', PAUSED:'已暂停', ACCEPTANCE:'待验收', CLOSED:'已关闭', CANCELED:'已取消' }
const statusTone = { DRAFT:'info', PLANNING:'warning', ACTIVE:'primary', PAUSED:'info', ACCEPTANCE:'success', CLOSED:'success', CANCELED:'danger' }
const typeLabel = { LIVE:'直播', JEWELRY:'珠宝', ECOMMERCE:'电商', OPERATIONS:'运营', INTERNAL:'内部', GENERAL:'通用', OTHER:'其他' }
const accountingLabel = { PROFIT:'利润项目', COST:'成本项目', VALUE:'价值项目', HYBRID:'混合核算' }
const managementModeLabel={LIGHT:'轻量模式',STANDARD:'标准模式',KEY_CONTROL:'重点监管',SIMPLE:'轻量模式',DELIVERY:'标准模式'}
const closeMethodLabel={DIRECT:'直接结项',RESULT_ACCEPTANCE:'成果验收',STAGED_ACCEPTANCE:'阶段验收'}
const frequencyLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月'}
const baselineLabel = { DRAFT:'未提交', SUBMITTED:'待确认', APPROVED:'已确认' }
const taskStatusLabel = { TODO:'待开始', DOING:'进行中', BLOCKED:'受阻', DONE:'已完成' }
const memberRoleLabel = { OWNER:'主负责人', DEPUTY:'副负责人', MEMBER:'成员', OBSERVER:'观察者' }
const milestoneStatusLabel = { PENDING:'未开始', DOING:'进行中', REVIEWING:'待阶段验收', DONE:'验收通过' }
const severityLabel = { LOW:'低', MEDIUM:'中', HIGH:'高', CRITICAL:'严重' }
const riskStatusLabel = { OPEN:'待处理', MITIGATED:'已缓解', CLOSED:'已关闭' }
const acceptanceLabel={PENDING:'待老板验收',APPROVED:'老板已通过',RETURNED:'老板已退回'}
const acceptanceTone={PENDING:'warning',APPROVED:'success',RETURNED:'danger'}
const eventLabel = {
  CREATE:'创建项目',CREATE_FROM_PROPOSAL:'立项批准并创建项目',EDIT:'更新项目资料',GOVERNANCE_CHANGE:'调整治理方式',
  SOURCE_LINK:'关联执行系统',SOURCE_UNLINK:'解除执行系统',OWNER_CHANGE:'更换主负责人',STATUS_CHANGE:'变更项目状态',
  START_PLANNING:'进入规划',SUBMIT_BASELINE:'提交项目计划',RETURN_PLAN:'退回项目计划',CONFIRM_BASELINE:'确认计划并启动',
  PAUSE:'暂停项目',RESUME:'恢复项目',REQUEST_ACCEPTANCE:'提交成果验收',REQUEST_CLOSE:'发起项目结项',
  REQUEST_STAGE_ACCEPTANCE:'提交阶段验收',APPROVE_STAGE:'阶段验收通过',RETURN_STAGE:'退回阶段成果',RETURN_ACTIVE:'退回执行',
  CLOSE:'项目结项',CANCEL:'取消项目',MEMBER_SAVE:'维护项目成员',MEMBER_REMOVE:'移除项目成员',
  TASK_SAVE:'维护一次性任务',TASK_PROGRESS:'更新任务进度',PROJECT_PROGRESS:'填报项目进度',
  ROUTINE_SAVE:'维护持续工作',ROUTINE_VOID:'停用持续工作',ROUTINE_REPORT:'填报持续工作成果',
  MILESTONE_SAVE:'维护项目里程碑',RISK_SAVE:'维护风险台账',BUDGET_CHANGE:'调整项目预算',
  KPI_CHANGE:'调整KPI',KPI_RETIRE:'停用KPI',KPI_PLAN_PUBLISHED:'发布KPI与奖金方案',KPI_PLAN_VOIDED:'作废KPI与奖金方案',
  KPI_SETTLEMENT_SUBMITTED:'提交KPI结算',KPI_SETTLEMENT_RETURNED:'退回KPI结算',KPI_SETTLEMENT_CONFIRMED:'确认KPI结算',
  COST_ALLOCATION:'调整成员计划投入',COST_ALLOCATION_VOID:'停用成员计划投入',
  EFFORT_WEEK_CONFIRMED:'确认周投入',EFFORT_DAY_CONFIRMED:'确认当日投入',EFFORT_DAY_RETURNED:'退回当日投入',
  STAFF_LEAVE_REQUESTED:'提交成员请假',STAFF_LEAVE_APPROVED:'成员请假已批准',STAFF_LEAVE_RETURNED:'成员请假已退回',
  STAFF_LEAVE_CANCEL_REQUESTED:'申请撤销成员请假',STAFF_LEAVE_CANCELED:'成员请假已撤销'
}
const metricTypeLabel={COUNT:'数量',AMOUNT:'金额',PERCENT:'百分比',DURATION:'时长',SCORE:'评分',MILESTONE:'里程碑'}
const periodLabel={DAY:'每日',WEEK:'每周',MONTH:'每月',QUARTER:'每季度',PROJECT:'整个项目'}
const costModeLabel={DAILY:'日成本',HOURLY:'时成本',MONTHLY:'月成本',FIXED_PROJECT:'项目固定',FIXED_TASK:'任务固定',VARIABLE:'浮动成本'}
const allocationModeLabel={PERCENTAGE:'比例分摊',HOURS:'确认工时',ATTENDANCE:'出勤天数',FIXED_DAILY:'固定日金额',PER_TASK:'按任务数'}
const isBoss = computed(() => userStore.roles.includes('admin') || userStore.permissions.includes('*:*:*') || userStore.permissions.includes('business:boss:view'))
const isAdmin = computed(() => userStore.roles.includes('admin') || userStore.permissions.includes('*:*:*'))
const myRole = computed(() => detail.value?.members?.find(m => Number(m.userId) === Number(userStore.id))?.memberRole)
const canManage = computed(() => isBoss.value || ['OWNER','DEPUTY'].includes(myRole.value))
const canManageDeputies = computed(() => isBoss.value || myRole.value === 'OWNER')
const canManageAllocation = computed(() => isAdmin.value || isBoss.value || myRole.value === 'OWNER')
const canSubmitAcceptance=computed(()=>detail.value?.closeMethod==='RESULT_ACCEPTANCE'&&detail.value?.status==='ACTIVE'&&(isBoss.value||myRole.value==='OWNER'))
const showRisks=computed(()=>detail.value?.governanceProfile?.riskRequired??['STANDARD','KEY_CONTROL','DELIVERY'].includes(detail.value?.managementMode))
const showMilestones=computed(()=>Boolean(detail.value?.milestones?.length)||(detail.value?.governanceProfile?.enabledModules?.includes('MILESTONE')??(detail.value?.managementMode!=='LIGHT')))
const governanceChanged=computed(()=>detail.value&&projectForm.value&&((projectForm.value.managementMode||'STANDARD')!==(detail.value.managementMode||'STANDARD')||(projectForm.value.closeMethod||'DIRECT')!==(detail.value.closeMethod||'DIRECT')))
const governanceTitle=computed(()=>`${managementModeLabel[detail.value?.managementMode] || detail.value?.managementMode} · ${closeMethodLabel[detail.value?.closeMethod] || detail.value?.closeMethod}`)
const governanceDescription=computed(()=>{const mode={LIGHT:'保留任务、工时、成本和KPI，风险按异常处理。',STANDARD:'执行周度跟踪、里程碑、风险台账和预算预警。',KEY_CONTROL:'执行强化里程碑、风险、预算分级预警和治理变更管控。'}[detail.value?.managementMode]||'';const close={DIRECT:'老板核对前置条件后直接关闭。',RESULT_ACCEPTANCE:'负责人提交整体验收资料，老板通过后关闭。',STAGED_ACCEPTANCE:'逐里程碑验收，全部通过后方可关闭。'}[detail.value?.closeMethod]||'';return `${mode}${close}${detail.value?.acceptanceCriteria?` 验收标准：${detail.value.acceptanceCriteria}`:''}`})
const ownerCandidates=computed(()=>users.value.filter(user=>Number(user.userId)!==Number(detail.value?.mainOwnerUserId)))
const userCompanyKey=user=>user.companyDeptId?`id:${user.companyDeptId}`:user.companyName?`name:${user.companyName}`:''
const memberCompanyOptions=computed(()=>{const unique=new Map();for(const user of users.value){const key=userCompanyKey(user);if(key&&!unique.has(key))unique.set(key,{key,companyName:user.companyName||'未命名公司'})}return [...unique.values()].sort((a,b)=>a.companyName.localeCompare(b.companyName,'zh-CN'))})
const memberUserOptions=computed(()=>{const memberIds=new Set((detail.value?.members||[]).map(member=>Number(member.userId)));return users.value.filter(user=>userCompanyKey(user)===itemForm.value.companyKey&&!memberIds.has(Number(user.userId)))})
const parentOptions = computed(() => rows.value.filter(p => p.projectId !== projectForm.value.projectId))
const currentKpis = computed(() => (operating.value.kpis || []).filter(row=>row.status==='CURRENT'))
const retiredKpis = computed(() => (operating.value.kpis || []).filter(row=>row.status==='RETIRED'))
const yesterdayExpected=computed(()=>Number(operating.value.executionSummary?.expectedStreamerCount||0))
const yesterdaySubmitted=computed(()=>Number(operating.value.executionSummary?.submittedStreamerCount||0))
const yesterdayMissing=computed(()=>Math.max(yesterdayExpected.value-yesterdaySubmitted.value,0))
const yesterdayRate=computed(()=>yesterdayExpected.value?Math.round(yesterdaySubmitted.value*100/yesterdayExpected.value):0)
const cockpitSummary=computed(()=>cockpit.value.summary||{})
const cockpitCurrency=computed(()=>operating.value.currency||detail.value?.baseCurrency||'CNY')
const cockpitBudget=computed(()=>Number(operating.value.budgetLimit??detail.value?.budgetLimit??0))
const cockpitBudgetSpent=computed(()=>Number(cockpitSummary.value.businessCost||0)+Number(cockpitSummary.value.personnelCost||0)+Number(cockpitSummary.value.bonusCost||0))
const cockpitTotalCost=computed(()=>cockpitBudgetSpent.value)
const cockpitBudgetRemaining=computed(()=>cockpitBudget.value-cockpitBudgetSpent.value)
const budgetUsage=computed(()=>cockpitBudget.value?Math.round(cockpitBudgetSpent.value*1000/cockpitBudget.value)/10:0)
const budgetTone=computed(()=>budgetUsage.value>=100?'is-danger':budgetUsage.value>=80?'is-warning':'')
const cockpitDateRange=computed(()=>`${detail.value?.planStartDate||'项目开始'} 至 ${todayText()}`)
const scheduleProgress=computed(()=>{if(!detail.value?.planStartDate||!detail.value?.planEndDate)return 0;const start=dateMs(detail.value.planStartDate),end=dateMs(detail.value.planEndDate),now=Math.min(Math.max(Date.now(),start),end);return end<=start?100:Math.round((now-start)*100/(end-start))})
const remainingDaysText=computed(()=>{if(!detail.value?.planEndDate)return '不限期';const days=Math.ceil((dateMs(detail.value.planEndDate)-dayStart())/86400000);return days<0?`逾期 ${Math.abs(days)} 天`:days===0?'今天到期':`${days} 天`})
const scheduleStatusText=computed(()=>scheduleProgress.value>projectProgress(detail.value)?`进度落后时间计划 ${scheduleProgress.value-projectProgress(detail.value)} 个百分点`:'当前进度不落后于时间计划')
const openTaskCount=computed(()=>(detail.value?.tasks||[]).filter(item=>item.status!=='DONE').length)
const overdueTaskCount=computed(()=>(detail.value?.tasks||[]).filter(item=>item.status!=='DONE'&&item.dueDate&&item.dueDate<todayText()).length)
const openRiskCount=computed(()=>(detail.value?.risks||[]).filter(item=>item.status==='OPEN').length)
const cockpitPlan=computed(()=>kpiWorkspace.value?.selectedPlan||null)
const cockpitSettlement=computed(()=>cockpitPlan.value?.settlement||null)
const cockpitKpis=computed(()=>{const plan=cockpitPlan.value;if(!plan?.items?.length)return currentKpis.value;const results=plan.settlement?.results||[];return plan.items.map(item=>({...item,actualValue:results.find(result=>Number(result.planItemId)===Number(item.itemId))?.actualValue}))})
const settlementStatusText=computed(()=>({DRAFT:'填报中',SUBMITTED:'待确认',RETURNED:'已退回',CONFIRMED:'已确认'}[cockpitSettlement.value?.status]||cockpitSettlement.value?.status||'未结算'))
const matchedTier=computed(()=>{const score=Number(cockpitSettlement.value?.totalScore);if(!Number.isFinite(score))return null;return (cockpitPlan.value?.tiers||[]).find(tier=>score>=Number(tier.minScore||0)&&(tier.maxScore===null||tier.maxScore===undefined||score<Number(tier.maxScore)))||null})
const showKpiClosureGuard=computed(()=>detail.value&&['ACTIVE','ACCEPTANCE'].includes(detail.value.status)&&(isBoss.value||myRole.value==='OWNER'))
const kpiClosureState=computed(()=>{
  if(kpiWorkspaceLoading.value)return {ready:null,tone:'info',label:'检查中',title:'正在检查KPI结算状态',description:'系统正在核对该项目所有已发布方案，请稍候。',actionLabel:'进入KPI工作区',actionType:'primary',planCount:0,confirmedCount:0,percentage:0,planId:null}
  if(kpiWorkspaceError.value)return {ready:null,tone:'warning',label:'请核对',title:'暂时未能读取KPI结算状态',description:'请进入KPI工作区确认全部方案均已结算；最终结项仍由系统后台校验。',actionLabel:'进入KPI工作区',actionType:'warning',planCount:0,confirmedCount:0,percentage:0,planId:null}
  const plans=kpiWorkspace.value?.plans||[],confirmed=plans.filter(plan=>plan.settlementStatus==='CONFIRMED'),pending=plans.filter(plan=>plan.settlementStatus!=='CONFIRMED')
  if(!plans.length)return {ready:false,tone:'danger',label:'尚未满足',title:'尚未发布KPI与奖金方案',description:'结项前由负责人设置目标、发布方案并完成结算。',actionLabel:(isBoss.value||myRole.value==='OWNER')?'设置并发布KPI':'查看KPI要求',actionType:'danger',planCount:0,confirmedCount:0,percentage:0,planId:null}
  if(!pending.length)return {ready:true,tone:'success',label:'已满足',title:'所有KPI结算均已确认',description:`共 ${plans.length} 个已发布方案已完成确认，可以继续办理项目结项。`,actionLabel:'查看已确认结算',actionType:'success',planCount:plans.length,confirmedCount:confirmed.length,percentage:100,planId:plans[0]?.planId}
  const priority={SUBMITTED:0,RETURNED:1,DRAFT:2},focus=[...pending].sort((a,b)=>(priority[a.settlementStatus]??9)-(priority[b.settlementStatus]??9))[0]
  const period=`方案 v${focus.planVersion}${focus.cycleStart&&focus.cycleEnd?`（${focus.cycleStart} 至 ${focus.cycleEnd}）`:''}`
  const cycleNotEnded=focus.settlementStatus==='DRAFT'&&focus.cycleEnd&&focus.cycleEnd>=todayText()
  const meta={
    SUBMITTED:{tone:'warning',label:'历史待确认',title:'升级前KPI结算待处理',description:`${period} 是旧流程遗留记录，可由归属老板处理。`,actionLabel:isBoss.value?'处理历史结算':'查看结算',actionType:'warning'},
    RETURNED:{tone:'danger',label:'历史已退回',title:'KPI结算需要负责人修改',description:`${period} 是旧流程退回记录；负责人修正并重新提交后将直接确认。`,actionLabel:myRole.value==='OWNER'?'修改并确认':'查看退回内容',actionType:'danger'},
    DRAFT:cycleNotEnded?{tone:'info',label:'考核中',title:'KPI考核周期尚未结束',description:`${period} 正在执行，周期结束并完成结果填报与确认后，才能办理结项。`,actionLabel:'查看KPI进度',actionType:'primary'}:{tone:'warning',label:'待填报',title:'KPI结果尚未完成结算',description:`${period} 仍在填报中；负责人提交结果后系统立即确认并计入项目核算。`,actionLabel:myRole.value==='OWNER'?'填报并确认KPI':'查看填报进度',actionType:'warning'}
  }[focus.settlementStatus]||{tone:'warning',label:'未完成',title:'仍有KPI方案尚未结算',description:`${period} 尚未完成确认，处理完毕后才能办理结项。`,actionLabel:'进入KPI工作区',actionType:'warning'}
  return {ready:false,...meta,planCount:plans.length,confirmedCount:confirmed.length,percentage:Math.round(confirmed.length*100/plans.length),planId:focus.planId}
})
const projectStatusLabel=row=>row?.status==='ACCEPTANCE'&&row?.closeMethod==='STAGED_ACCEPTANCE'?'待结项':statusLabel[row?.status]||row?.status
const showStageClosureGuard=computed(()=>detail.value?.closeMethod==='STAGED_ACCEPTANCE'&&['ACTIVE','ACCEPTANCE'].includes(detail.value.status)&&(isBoss.value||myRole.value==='OWNER'))
const showDirectClosureGuard=computed(()=>detail.value?.closeMethod==='DIRECT'&&detail.value?.status==='ACCEPTANCE'&&(isBoss.value||myRole.value==='OWNER'))
const stageClosureState=computed(()=>{
  const milestones=detail.value?.milestones||[],doneCount=milestones.filter(item=>item.status==='DONE').length,milestoneCount=milestones.length
  const base={doneCount,milestoneCount,percentage:milestoneCount?Math.round(doneCount*100/milestoneCount):0,canRequest:false,canReview:false}
  if(detail.value?.status==='ACCEPTANCE')return {...base,tone:'warning',label:'待老板检验',title:'负责人已提交项目结项申请',description:isBoss.value?'请检验全部阶段成果，确认通过后项目才会结项。':'正在等待归属老板检验，负责人不能自行通过验收。',canReview:isBoss.value}
  if(!milestoneCount)return {...base,tone:'danger',label:'尚未满足',title:'尚未设置项目里程碑',description:'阶段验收项目至少需要一个里程碑，全部里程碑验收通过后才能发起结项。'}
  if(doneCount<milestoneCount)return {...base,tone:'info',label:'验收进行中',title:'仍有里程碑尚未验收通过',description:`还需完成 ${milestoneCount-doneCount} 个里程碑验收；全部通过后，系统将开放“发起结项”。`}
  const blockingRisks=(detail.value?.risks||[]).filter(item=>item.status==='OPEN'&&['HIGH','CRITICAL'].includes(item.severity))
  if(blockingRisks.length)return {...base,tone:'danger',label:'存在阻塞',title:'仍有高风险或严重风险未关闭',description:`请先处理：${blockingRisks.map(item=>item.riskTitle||'未命名风险').join('、')}。`}
  if(kpiClosureState.value.ready!==true)return {...base,tone:kpiClosureState.value.tone||'warning',label:'等待KPI结算',title:'里程碑已全部验收，KPI结算尚未确认',description:'完成并确认全部KPI结算后，即可发起项目结项。'}
  return {...base,tone:'success',label:'可以申请',title:'所有里程碑已完成，结项申请条件已满足',description:myRole.value==='OWNER'?'提交后由归属老板检验，通过后项目才会结项。':'等待项目负责人提交结项申请。',canRequest:myRole.value==='OWNER'}
})
const projectProgress = row => {
  if (row.status === 'CLOSED') return 100
  if (row.status === 'CANCELED') return 0
  const value = Number(row.progressPercent)
  if (Number.isFinite(value)) return Math.min(100, Math.max(0, Math.round(value)))
  return row.taskCount ? Math.round((row.completedTaskCount || 0) * 100 / row.taskCount) : 0
}
const isKpiBlockedCloseAction=action=>action.key==='CLOSE'&&kpiClosureState.value.ready===false
const userOptionLabel = user => `${user.nickName || user.userName} · ${user.userName} · ${user.companyName || '集团'}${user.deptName && user.deptName !== user.companyName ? ` / ${user.deptName}` : ''}`
const memberOptionLabel = member => `${member.userNameSnapshot}${member.accountName ? ` · ${member.accountName}` : ` · ID ${member.userId}`}`
const formatEventTime=value=>value?String(value).replace('T',' ').replace(/\.\d+$/,''):''
const eventTone=event=>{
  if(['CLOSE','APPROVE_STAGE','CONFIRM_BASELINE','KPI_SETTLEMENT_CONFIRMED','STAFF_LEAVE_APPROVED'].includes(event?.eventType))return 'success'
  if(['CANCEL','RETURN_PLAN','RETURN_STAGE','KPI_SETTLEMENT_RETURNED','STAFF_LEAVE_RETURNED'].includes(event?.eventType))return 'danger'
  if(['PAUSE','REQUEST_ACCEPTANCE','REQUEST_CLOSE','REQUEST_STAGE_ACCEPTANCE','KPI_SETTLEMENT_SUBMITTED'].includes(event?.eventType))return 'warning'
  return 'primary'
}
const formatEventComment=event=>{
  const value=event?.comment||''
  if(event?.eventType==='MEMBER_SAVE'&&/^.+?\s*\/\s*(OWNER|DEPUTY|MEMBER|OBSERVER)\s*$/.test(value))return ''
  if(event?.eventType==='MEMBER_REMOVE')return value.replace(/^移除账号ID\s*\d+\s*[；;]?\s*/, '')
  return value
}
const memberSaveParts=event=>{
  const match=(event?.comment||'').match(/^(.+?)\s*\/\s*(OWNER|DEPUTY|MEMBER|OBSERVER)\s*$/)
  return match?{name:match[1].trim(),role:memberRoleLabel[match[2]]||match[2]}:null
}
const eventSummary=event=>{
  const actor=event?.operatorName||event?.operatorAccount||'系统'
  if(event?.eventType==='MEMBER_SAVE'){
    const member=memberSaveParts(event)
    return member?`${actor}将${member.name}设为${member.role}`:`${actor}维护了项目成员`
  }
  if(event?.eventType==='MEMBER_REMOVE'){
    const subject=event?.subjectName||event?.subjectAccount
    return subject?`${actor}移除了项目成员：${subject}`:`${actor}移除了一名项目成员`
  }
  return `${actor}${eventLabel[event?.eventType]||'执行了项目操作'}`
}
const eventDetailLabel=event=>event?.eventType==='MEMBER_REMOVE'?'影响：':'说明：'
const eventStatusChange=event=>event?.fromStatus&&event?.toStatus&&event.fromStatus!==event.toStatus&&statusLabel[event.fromStatus]&&statusLabel[event.toStatus]?`${statusLabel[event.fromStatus]} → ${statusLabel[event.toStatus]}`:''
const projectRules = { projectName:[{ required:true,message:'请输入项目名称',trigger:'blur' }],companyDeptId:[{required:true,message:'请选择归属公司',trigger:'change'}] }
const itemTitle = computed(() => ({ member:'添加项目成员', task:'维护任务', milestone:'维护里程碑', risk:'维护风险' }[itemKind.value]))
const availableActions = computed(() => {
  if (!detail.value) return []
  const d = detail.value, actions = []
  if (isBoss.value && d.status === 'DRAFT') actions.push({key:'START_PLANNING',label:'进入规划',type:'primary'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'PLANNING' && d.baselineStatus !== 'SUBMITTED') actions.push({key:'SUBMIT_BASELINE',label:'提交计划',type:'warning'})
  if (isBoss.value && d.status === 'PLANNING' && d.baselineStatus === 'SUBMITTED') actions.push({key:'CONFIRM_BASELINE',label:'确认并启动',type:'success'},{key:'RETURN_PLAN',label:'退回计划'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'ACTIVE') actions.push({key:'PAUSE',label:'暂停项目'})
  if (isBoss.value && d.status === 'ACTIVE' && d.closeMethod === 'DIRECT') actions.push({key:'CLOSE',label:'检验并直接结项',type:'success'})
  if (myRole.value === 'OWNER' && d.status === 'ACTIVE' && d.closeMethod === 'DIRECT') actions.push({key:'REQUEST_CLOSE',label:'申请直接结项',type:'success'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'ACTIVE' && d.closeMethod === 'RESULT_ACCEPTANCE') actions.push({key:'SUBMIT_ACCEPTANCE',label:'提交成果验收',type:'success'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'PAUSED') actions.push({key:'RESUME',label:'恢复执行',type:'primary'})
  if (isBoss.value && d.status === 'ACCEPTANCE' && d.closeMethod === 'RESULT_ACCEPTANCE') actions.push({key:'OPEN_ACCEPTANCE',label:'查看验收资料',type:'success'})
  if ((isBoss.value || myRole.value === 'OWNER') && !['CLOSED','CANCELED'].includes(d.status)) actions.push({key:'CANCEL',label:'取消项目',type:'danger'})
  return actions
})

async function load() { loading.value=true; try { const res=await listBusinessProjects(query); rows.value=res.rows||[]; total.value=res.total||0 } finally { loading.value=false } }
function search(){ query.pageNum=1; load() }
function resetQuery(){ query.keyword=''; query.status=''; query.managementMode='';query.closeMethod='';search() }
async function openDetail(row){ const res=await getBusinessProject(row.projectId); detail.value=res.data;activeTab.value=route.query.tab||'overview'; detailVisible.value=true; router.replace({query:{...route.query,id:row.projectId}}); await Promise.all([loadOperatingConfig(),loadKpiClosureState(),loadCockpit()]) }
async function refreshDetail(){ if(!detail.value)return; detail.value=(await getBusinessProject(detail.value.projectId)).data; await Promise.all([load(),loadOperatingConfig(),loadKpiClosureState(),loadCockpit()]) }
async function loadOperatingConfig(){if(!detail.value)return;operating.value=(await getBusinessOperatingConfig(detail.value.projectId)).data||{kpis:[],budgetHistory:[],staffAllocations:[]}}
async function loadCockpit(){if(!detail.value)return;cockpitLoading.value=true;cockpitError.value=false;try{cockpit.value=(await getBusinessProjectDashboard(detail.value.projectId,{dateFrom:detail.value.planStartDate||'2000-01-01',dateTo:todayText()})).data||{summary:{},results:[]}}catch{cockpit.value={summary:{},results:[]};cockpitError.value=true}finally{cockpitLoading.value=false}}
async function loadKpiClosureState(){kpiWorkspace.value={plans:[]};kpiWorkspaceError.value=false;if(!showKpiClosureGuard.value)return;kpiWorkspaceLoading.value=true;try{kpiWorkspace.value=(await getProjectKpiWorkspace(detail.value.projectId)).data||{plans:[]}}catch{kpiWorkspaceError.value=true}finally{kpiWorkspaceLoading.value=false}}
function openKpiWorkspace(){const planId=kpiClosureState.value.planId;router.push({path:'/business/kpi-bonus',query:{projectId:detail.value.projectId,...(planId?{planId}:{})}})}
async function ensureUsers(){ if(!users.value.length) users.value=(await listBusinessUsers()).data||[] }
async function ensureCompanies(){if(isBoss.value&&!companies.value.length)companies.value=(await getBusinessAccountingDashboard({dateFrom:new Date().toISOString().slice(0,10),dateTo:new Date().toISOString().slice(0,10)})).data?.companies||[]}
async function openProjectForm(row){ if(!row?.projectId)return router.push('/business/project-proposals'); await Promise.all([ensureUsers(),ensureCompanies()]); const legacyMode=row.managementMode==='SIMPLE'?'LIGHT':row.managementMode==='DELIVERY'?'STANDARD':row.managementMode; const base={managementMode:'STANDARD',closeMethod:row.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT',governanceChangeReason:'',...row,managementMode:legacyMode,closeMethod:row.closeMethod||(row.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT')}; projectForm.value=base; projectOpenEnded.value=!!base.planStartDate&&!base.planEndDate; projectDialog.value=true }
async function saveProject(){ if(saving.value)return; await projectFormRef.value.validate(); if(!projectForm.value.planStartDate)return ElMessage.warning('请选择计划开始日期');if(!projectOpenEnded.value&&!projectForm.value.planEndDate)return ElMessage.warning('请选择计划结束日期或勾选不限期');if(projectForm.value.planEndDate&&projectForm.value.planStartDate>projectForm.value.planEndDate)return ElMessage.warning('计划结束日期不能早于开始日期');if(projectForm.value.managementMode==='KEY_CONTROL'&&!projectForm.value.managementReason?.trim())return ElMessage.warning('重点监管项目请填写监管原因');if(projectForm.value.closeMethod!=='DIRECT'&&!projectForm.value.acceptanceCriteria?.trim())return ElMessage.warning('请填写验收标准');if(governanceChanged.value&&!projectForm.value.governanceChangeReason?.trim())return ElMessage.warning('请填写治理方式变更原因'); const data={...projectForm.value,planEndDate:projectOpenEnded.value?null:projectForm.value.planEndDate}; saving.value=true; try { const res=await updateBusinessProject(data); projectDialog.value=false; ElMessage.success('项目资料已保存'); await load(); if(res.data?.projectId) await openDetail(res.data) } finally { saving.value=false } }
function handleProjectOpenEndedChange(value){if(value)projectForm.value.planEndDate=null}
function disableProjectEndDate(date){return !!projectForm.value.planStartDate&&date.getTime()<new Date(`${projectForm.value.planStartDate}T00:00:00`).getTime()}
async function runTransition(action){ if(action.key==='SUBMIT_ACCEPTANCE')return openAcceptanceSubmit();if(action.key==='OPEN_ACCEPTANCE'){activeTab.value='acceptance';return}let comment=''; if(['RETURN_PLAN','RETURN_ACTIVE','PAUSE','CLOSE','CANCEL','REQUEST_CLOSE'].includes(action.key)){ const r=await ElMessageBox.prompt(action.key==='CLOSE'?'请填写老板检验结论':action.key==='REQUEST_CLOSE'?'请填写结项申请说明':`请输入“${action.label}”原因`,'状态确认',{inputValidator:v=>!!v||'必须填写说明'}); comment=r.value } else await ElMessageBox.confirm(`确定执行“${action.label}”吗？`,'状态确认',{type:'warning'}); await transitionBusinessProject(detail.value.projectId,{action:action.key,comment}); ElMessage.success(action.key==='REQUEST_CLOSE'?'结项申请已提交，等待老板检验':'状态已更新'); await refreshDetail() }
async function openOwnerDialog(){await ensureUsers();Object.assign(ownerForm,{ownerUserId:null,reason:''});ownerDialog.value=true}
async function saveOwner(){if(!ownerForm.ownerUserId)return ElMessage.warning('请选择新负责人');if(!ownerForm.reason?.trim())return ElMessage.warning('请填写变更原因');saving.value=true;try{detail.value=(await changeBusinessProjectOwner(detail.value.projectId,ownerForm)).data;ownerDialog.value=false;ElMessage.success('主负责人已更换，交接历史已记录');await load()}finally{saving.value=false}}
function openAcceptanceSubmit(){Object.assign(acceptanceForm,{resultSummary:'',deliverables:'',attachmentUrls:''});acceptanceDialog.value=true;activeTab.value='acceptance'}
async function saveAcceptance(){if(!acceptanceForm.resultSummary?.trim())return ElMessage.warning('请填写结果摘要');if(!acceptanceForm.deliverables?.trim())return ElMessage.warning('请填写交付成果');await ElMessageBox.confirm('确认提交成果验收资料给老板检验吗？老板通过后项目才会结项。','提交成果验收',{type:'warning'});saving.value=true;try{detail.value=(await submitBusinessProjectAcceptance(detail.value.projectId,acceptanceForm)).data;acceptanceDialog.value=false;activeTab.value='acceptance';ElMessage.success('成果验收已提交，等待老板检验');await load()}finally{saving.value=false}}
function openAcceptanceReview(decision,record){Object.assign(reviewForm,{decision,comment:'',acceptanceId:record.acceptanceId});reviewDialog.value=true;activeTab.value='acceptance'}
async function saveAcceptanceReview(){if(reviewForm.decision==='RETURNED'&&!reviewForm.comment?.trim())return ElMessage.warning('请填写退回原因');saving.value=true;try{detail.value=(await reviewBusinessProjectAcceptance(detail.value.projectId,reviewForm)).data;reviewDialog.value=false;activeTab.value='acceptance';ElMessage.success(reviewForm.decision==='APPROVED'?'项目已验收关闭':'项目已退回执行');await load()}finally{saving.value=false}}
const stageRecords=milestoneId=>(detail.value?.stageAcceptances||[]).filter(row=>Number(row.milestoneId)===Number(milestoneId))
const milestoneName=milestoneId=>(detail.value?.milestones||[]).find(row=>Number(row.milestoneId)===Number(milestoneId))?.milestoneName||'未关联'
const canSubmitStage=milestone=>detail.value?.status==='ACTIVE'&&(isBoss.value||myRole.value==='OWNER')&&!['DONE','REVIEWING'].includes(milestone.status)
function openStageSubmit(milestone){Object.assign(stageForm,{milestoneId:milestone.milestoneId,milestoneName:milestone.milestoneName,resultSummary:'',deliverables:'',attachmentUrls:''});stageDialog.value=true;activeTab.value='stageAcceptance'}
async function saveStageAcceptance(){if(!stageForm.resultSummary?.trim())return ElMessage.warning('请填写阶段结果');if(!stageForm.deliverables?.trim())return ElMessage.warning('请填写阶段交付成果');saving.value=true;try{detail.value=(await submitBusinessProjectStageAcceptance(detail.value.projectId,stageForm)).data;stageDialog.value=false;activeTab.value='stageAcceptance';ElMessage.success('阶段成果已提交，等待老板检验')}finally{saving.value=false}}
function openStageReview(decision,record){Object.assign(stageReviewForm,{milestoneId:record.milestoneId,decision,comment:''});stageReviewDialog.value=true;activeTab.value='stageAcceptance'}
async function saveStageReview(){if(stageReviewForm.decision==='RETURNED'&&!stageReviewForm.comment?.trim())return ElMessage.warning('请填写退回原因');saving.value=true;try{detail.value=(await reviewBusinessProjectStageAcceptance(detail.value.projectId,stageReviewForm.milestoneId,stageReviewForm)).data;stageReviewDialog.value=false;activeTab.value='stageAcceptance';ElMessage.success(stageReviewForm.decision==='APPROVED'?'阶段验收已通过':'阶段成果已退回')}finally{saving.value=false}}
async function openItem(kind,row={}){ await ensureUsers(); itemKind.value=kind; const defaults={ member:{companyKey:null,userId:null,memberRole:'MEMBER'}, task:{status:'TODO',progress:0,priority:'MEDIUM'}, milestone:{status:'PENDING'}, risk:{riskType:'GENERAL',severity:'MEDIUM',probability:'MEDIUM',status:'OPEN'} }; itemForm.value={...defaults[kind],...row,projectId:detail.value.projectId}; itemDialog.value=true }
async function saveItem(){ if(saving.value)return;if(itemKind.value==='member'&&!itemForm.value.companyKey)return ElMessage.warning('请先选择公司');if(itemKind.value==='member'&&!itemForm.value.userId)return ElMessage.warning('请选择人员'); const api={member:saveBusinessProjectMember,task:saveBusinessTask,milestone:saveBusinessMilestone,risk:saveBusinessRisk}[itemKind.value]; const payload={...itemForm.value}; if(itemKind.value==='member')delete payload.companyKey;if(itemKind.value==='milestone')delete payload.weight; saving.value=true; try{ await api(payload); itemDialog.value=false; ElMessage.success('保存成功'); await refreshDetail() }finally{ saving.value=false } }
const removalItemNames=items=>{
  const names=items.map(item=>item.taskName||item.routineName).filter(Boolean)
  if(!names.length)return ''
  return names.length>3?`${names.slice(0,3).join('、')} 等 ${names.length} 项`:names.join('、')
}
function memberRemovalImpact(row){
  const userId=Number(row.userId)
  const tasks=(detail.value?.tasks||[]).filter(item=>Number(item.assigneeUserId)===userId&&item.status!=='DONE')
  const routines=(detail.value?.routines||[]).filter(item=>Number(item.assigneeUserId)===userId&&item.status==='ACTIVE'&&!item.sourceManaged)
  return {tasks,routines}
}
async function confirmMemberRemoval(row){
  const name=row.userNameSnapshot||row.userName||`账号ID ${row.userId}`
  const impact=memberRemovalImpact(row)
  if(!impact.tasks.length&&!impact.routines.length){
    await ElMessageBox.confirm(`确定将“${name}”移出项目吗？该成员当前没有未完成的一次性任务或持续工作。`,'移除项目成员',{type:'warning',confirmButtonText:'确认移除',cancelButtonText:'取消'})
    return impact
  }
  const content=[
    h('p',{style:'margin:0 0 12px;line-height:1.7'},`“${name}”仍负责以下未完成工作。确认移除后，系统将同步解除相关安排：`)
  ]
  if(impact.tasks.length)content.push(h('div',{style:'margin:8px 0;padding:10px 12px;border-radius:6px;background:#fff7e8;line-height:1.65'},[
    h('b',null,`一次性任务 ${impact.tasks.length} 项`),
    h('span',null,`：${removalItemNames(impact.tasks)}`),
    h('small',{style:'display:block;color:#8a6d3b'},'将解除负责人，任务本身保留，等待重新分配。')
  ]))
  if(impact.routines.length)content.push(h('div',{style:'margin:8px 0;padding:10px 12px;border-radius:6px;background:#fff7e8;line-height:1.65'},[
    h('b',null,`持续工作 ${impact.routines.length} 项`),
    h('span',null,`：${removalItemNames(impact.routines)}`),
    h('small',{style:'display:block;color:#8a6d3b'},'将解除负责人，持续工作及历史填报保留，等待重新分配。')
  ]))
  content.push(h('p',{style:'margin:12px 0 0;color:#d7474f;font-weight:600'},'请确认这些工作已准备重新安排负责人。'))
  await ElMessageBox.confirm(h('div',null,content),'移除成员并解除工作分配',{type:'warning',confirmButtonText:'确认移除并解除',cancelButtonText:'取消'})
  return impact
}
async function chooseMemberRemovalCost(){
  try{
    await ElMessageBox.confirm('请选择移除当天的人员成本处理方式。若员工今天没有参与项目，选择“今天不计成本”；若员工今天已经工作，选择“保留今天成本”。','移除当日成本',{type:'warning',confirmButtonText:'今天不计成本',cancelButtonText:'保留今天成本',distinguishCancelAndClose:true,closeOnClickModal:false})
    return false
  }catch(action){
    if(action==='cancel')return true
    throw action
  }
}
async function removeItem(kind,row){
  let memberImpact=null
  if(kind==='member'){
    memberImpact=await confirmMemberRemoval(row)
    memberImpact.retainTodayCost=await chooseMemberRemovalCost()
  }
  else await ElMessageBox.confirm('确定删除这条记录吗？','确认删除',{type:'warning'})
  const calls={member:()=>removeBusinessProjectMember(detail.value.projectId,row.userId,memberImpact?.retainTodayCost),task:()=>removeBusinessTask(detail.value.projectId,row.taskId),milestone:()=>removeBusinessMilestone(detail.value.projectId,row.milestoneId),risk:()=>removeBusinessRisk(detail.value.projectId,row.riskId)}
  await calls[kind]()
  if(kind==='member'){
    const affected=(memberImpact?.tasks.length||0)+(memberImpact?.routines.length||0)
    ElMessage.success(affected?`成员已移除，已同步处理 ${affected} 项未完成工作`:'成员已移除')
  }else ElMessage.success('删除成功')
  await refreshDetail()
}
const money=value=>value===null||value===undefined?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:4})
const signedMoney=value=>{const amount=Number(value||0);return `${amount>0?'+':''}${money(amount)}`}
const todayText=()=>new Date().toISOString().slice(0,10)
const dateMs=value=>new Date(`${value}T00:00:00`).getTime()
const dayStart=()=>dateMs(todayText())
const kpiActual=item=>Number(item.actualValue||0)
const kpiCompletion=item=>{const target=Number(item.targetValue||0),actual=kpiActual(item);if(!target)return 0;return Math.max(0,Math.min(100,Math.round((item.direction==='LOWER_BETTER'?target/Math.max(actual,target):actual/target)*100)))}
const kpiActualText=item=>item.actualValue===null||item.actualValue===undefined?`待填报`:`${item.actualValue} ${item.unit||''}`
const kpiGapText=item=>{if(item.actualValue===null||item.actualValue===undefined)return '距离目标：待填报实际值';const gap=Number(item.targetValue||0)-Number(item.actualValue||0);if(item.direction==='LOWER_BETTER')return gap>=0?'已达到目标':`超过目标上限 ${money(Math.abs(gap))} ${item.unit||''}`;return gap<=0?'已达到目标':`距离目标还差 ${money(gap)} ${item.unit||''}`}
const number=value=>Number(value||0).toLocaleString('zh-CN')
function openBudgetDialog(){Object.assign(budgetForm,{budgetLimit:Number(operating.value.budgetLimit||0),currency:operating.value.currency||detail.value.baseCurrency||'CNY',reason:''});budgetDialog.value=true}
async function saveBudget(){if(budgetForm.budgetLimit===null)return ElMessage.warning('请填写预算金额');if(!budgetForm.reason?.trim())return ElMessage.warning('请填写调整原因');saving.value=true;try{detail.value=(await updateBusinessProjectBudget(detail.value.projectId,budgetForm)).data;budgetDialog.value=false;await Promise.all([loadOperatingConfig(),loadCockpit(),load()]);ElMessage.success('预算已调整，历史记录已保存')}finally{saving.value=false}}
function openKpiDialog(row={}){Object.assign(kpiForm,{kpiId:null,projectId:detail.value.projectId,kpiCode:'',kpiName:'',metricType:'COUNT',periodType:'PROJECT',targetValue:null,actualValue:null,unit:'',weight:0,direction:'HIGHER_BETTER',aggregateType:'SUM',sourceType:'MANUAL',ownerUserId:null,effectiveFrom:new Date().toISOString().slice(0,10),remark:'',...row});kpiDialog.value=true}
async function saveKpi(){if(!kpiForm.kpiName?.trim())return ElMessage.warning('请填写KPI名称');if(kpiForm.targetValue===null||kpiForm.targetValue===undefined)return ElMessage.warning('请填写目标值');saving.value=true;try{await saveBusinessProjectKpi({...kpiForm,projectId:detail.value.projectId});kpiDialog.value=false;await loadOperatingConfig();ElMessage.success(kpiForm.kpiId?'KPI新版本已生效':'KPI已创建，编码已自动生成')}finally{saving.value=false}}
async function retireKpi(row){await ElMessageBox.confirm(`确定停用“${row.kpiName}”吗？历史版本仍会保留。`,'停用KPI',{type:'warning'});await retireBusinessProjectKpi(detail.value.projectId,row.kpiId);await loadOperatingConfig();ElMessage.success('KPI已停用')}
function projectAllocationPeriod(){return [detail.value?.planStartDate||new Date().toISOString().slice(0,10),detail.value?.planEndDate||null]}
function syncAllocationProjectPeriod(follow=allocationFollowProject.value){if(follow)allocationDates.value=projectAllocationPeriod()}
function openAllocationDialog(row={}){Object.assign(allocationForm,{allocationId:null,projectId:detail.value.projectId,userId:null,allocationMode:'PERCENTAGE',allocationValue:100,version:null,remark:'',...row});const projectPeriod=projectAllocationPeriod();allocationFollowProject.value=!row.allocationId||(row.effectiveFrom===projectPeriod[0]&&(row.effectiveTo||null)===projectPeriod[1]);allocationDates.value=allocationFollowProject.value?projectPeriod:(row.effectiveFrom?[row.effectiveFrom,row.effectiveTo||null]:[]);allocationDialog.value=true}
async function saveAllocation(){if(!allocationForm.userId)return ElMessage.warning('请选择项目人员');if(allocationForm.allocationValue===null||allocationForm.allocationValue===undefined)return ElMessage.warning('请填写计划投入');if(Number(allocationForm.allocationValue)>100)return ElMessage.warning('项目投入比例不能超过100%');if(allocationFollowProject.value)syncAllocationProjectPeriod(true);const from=allocationDates.value?.[0]||new Date().toISOString().slice(0,10),to=allocationDates.value?.[1]||null;saving.value=true;try{await saveBusinessStaffAllocation({...allocationForm,allocationMode:'PERCENTAGE',exceptionAllowed:'0',exceptionReason:'',projectId:detail.value.projectId,effectiveFrom:from,effectiveTo:to});allocationDialog.value=false;await loadOperatingConfig();ElMessage.success('成员计划投入已保存')}finally{saving.value=false}}
async function removeAllocation(row){await ElMessageBox.confirm('确定停用这条人员成本分摊吗？历史核算数据不会删除。','停用分摊',{type:'warning'});await removeBusinessStaffAllocation(detail.value.projectId,row.allocationId);await loadOperatingConfig();ElMessage.success('分摊已停用')}
function openRoutine(row={}){Object.assign(routineForm,{routineId:null,projectId:detail.value.projectId,routineName:'',frequency:'DAILY',targetValue:null,unit:'条',assigneeUserId:detail.value.mainOwnerUserId,startDate:detail.value.planStartDate||new Date().toISOString().slice(0,10),endDate:detail.value.planEndDate||null,evidenceRequired:'0',remark:'',version:null,...row});routineLongTerm.value=!!routineForm.startDate&&!routineForm.endDate;routineDialog.value=true}
async function saveRoutine(){if(!routineForm.routineName?.trim())return ElMessage.warning('请填写持续工作内容');if(!routineForm.assigneeUserId)return ElMessage.warning('请选择负责人');if(!(Number(routineForm.targetValue)>0))return ElMessage.warning('周期目标必须大于0');if(!routineForm.unit?.trim())return ElMessage.warning('请填写目标单位');if(!routineForm.startDate)return ElMessage.warning('请选择执行开始日期');if(!routineLongTerm.value&&!routineForm.endDate)return ElMessage.warning('请选择执行结束日期或勾选长期');if(routineForm.endDate&&routineForm.startDate>routineForm.endDate)return ElMessage.warning('执行结束日期不能早于开始日期');saving.value=true;try{await saveBusinessRoutine({...routineForm,projectId:detail.value.projectId,endDate:routineLongTerm.value?null:routineForm.endDate});routineDialog.value=false;activeTab.value='routines';await refreshDetail();ElMessage.success('持续工作已保存')}finally{saving.value=false}}
function handleRoutineLongTermChange(value){if(value)routineForm.endDate=null}
function disableRoutineEndDate(date){return !!routineForm.startDate&&date.getTime()<new Date(`${routineForm.startDate}T00:00:00`).getTime()}
async function removeRoutine(row){await ElMessageBox.confirm(`确定停用“${row.routineName}”吗？历史填报不会删除。`,'停用持续工作',{type:'warning'});await removeBusinessRoutine(detail.value.projectId,row.routineId);await refreshDetail();ElMessage.success('持续工作已停用')}
watch(()=>route.query.create,value=>{if(value)router.replace('/business/project-proposals')},{immediate:true})
watch(()=>route.query.id,async value=>{if(!value||Number(value)===Number(detail.value?.projectId))return;try{await openDetail({projectId:Number(value)})}catch{const nextQuery={...route.query};delete nextQuery.id;router.replace({query:nextQuery})}},{immediate:true})
watch(()=>route.query.tab,value=>{if(['overview','operating','routines','tasks','members','milestones','risks','acceptance','stageAcceptance','ownerHistory','events'].includes(value))activeTab.value=value},{immediate:true})
onMounted(load)
</script>

<style scoped>
.kpi-close-guard{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:14px;margin:-4px 0 16px;padding:14px 16px;border:1px solid #e6cf91;border-left:4px solid #d59a23;border-radius:10px;background:#fffaf0}.kpi-close-guard.in-tab{margin:0 0 12px}.kpi-close-guard.is-success{border-color:#bcded2;border-left-color:#2a8b6e;background:#f3faf7}.kpi-close-guard.is-danger{border-color:#efc5c8;border-left-color:#d74b55;background:#fff6f6}.kpi-close-guard.is-info{border-color:#cbdceb;border-left-color:#4d83b5;background:#f5f9fd}.kpi-close-mark{display:flex;width:42px;height:42px;align-items:center;justify-content:center;border-radius:12px;background:#fff;color:#a56c08;font-size:12px;font-weight:800;letter-spacing:.05em;box-shadow:0 2px 8px rgba(80,61,22,.08)}.is-success .kpi-close-mark{color:#23745f}.is-danger .kpi-close-mark{color:#c43d47}.is-info .kpi-close-mark{color:#3d709e}.kpi-close-copy{min-width:0}.kpi-close-title{display:flex;align-items:center;gap:8px;margin-bottom:5px}.kpi-close-title>span{color:#7b8795;font-size:12px}.kpi-close-copy>b{display:block;color:#24354a;font-size:15px}.kpi-close-copy>p{margin:4px 0 0;color:#687789;font-size:12px;line-height:1.55}.kpi-close-progress{display:grid;grid-template-columns:auto minmax(90px,180px);align-items:center;gap:10px;margin-top:9px;color:#7f8b98;font-size:12px}
.project-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;letter-spacing:.16em;color:#3977c5}.page-head h1{margin:4px 0;font-size:27px;color:#172033}.page-head p{margin:0;color:#778394}.filter-card,.table-card{border-color:#dfe4ea}.filter-card{margin-bottom:12px}.filter-card :deep(.el-card__body){padding:14px 16px 0}.click-table :deep(.el-table__row){cursor:pointer}.subline{display:block;margin-top:4px;color:#8a95a3}.danger{color:#d7474f;font-weight:700}.drawer-title{display:flex;align-items:center;justify-content:space-between;width:100%;padding-right:18px}.drawer-title span{color:#8994a3;font-size:12px}.drawer-title h2{margin:3px 0 0;color:#1b2b40}.project-summary{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #e2e7ed;border-radius:10px;background:#fafbfd}.project-summary div{padding:14px;border-right:1px solid #e2e7ed}.project-summary div:last-child{border:0}.project-summary span,.objective span{display:block;color:#8792a1;font-size:12px}.project-summary b{display:block;margin-top:6px;color:#26374d;font-size:14px}.objective{margin:14px 0;padding:14px 16px;border-left:3px solid #3b7cc4;background:#f5f8fb}.objective p{margin:7px 0 0;line-height:1.65}.action-bar{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px}.action-bar .el-button+.el-button{margin-left:0}.tab-tools{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.routine-tip{display:block;margin-top:4px}.operating-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.operating-card{padding:14px;border:1px solid #dfe5ec;border-radius:10px;background:#fafbfd}.operating-card p{margin:10px 0 0;color:#7a8796;font-size:12px;line-height:1.55}.operating-head{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.operating-head small,.operating-head strong{display:block}.operating-head small{color:#8390a0}.operating-head strong{margin-top:7px;color:#1c3048;font-size:19px}.budget-card{border-top:3px solid #397ac5}.section-gap{margin-top:22px}.muted,.form-tip{color:#8a95a3;font-size:12px}.form-tip{display:block;margin-top:5px}.history-collapse{margin-top:10px}.acceptance-record{margin-top:12px;padding:16px;border:1px solid #e2e7ed;border-radius:10px;background:#fff}.acceptance-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.acceptance-head b,.acceptance-head span{display:block}.acceptance-head span{margin-top:4px;color:#8793a1;font-size:12px}.acceptance-record h4{margin:16px 0 6px;color:#526174;font-size:13px}.acceptance-record>p{margin:0;line-height:1.7;white-space:pre-wrap}.review-result{margin-top:14px;padding:12px;border-left:3px solid #4b8c80;background:#f3f8f7}.review-result p{margin:6px 0}.review-result small{color:#8793a1}.review-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}.decision-form{margin-top:18px}.project-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.project-period-line>span{color:#7f8a99}.allocation-period-field{display:flex;width:100%;flex-direction:column;gap:8px}.allocation-period-field .el-checkbox{align-self:flex-start}.event-line{padding-top:12px}.event-card{padding:12px 14px;border:1px solid #e5e9ef;border-radius:9px;background:#fafbfd}.event-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.event-actor b,.event-actor small{display:block}.event-actor b{color:#26374c;font-size:14px}.event-actor small{margin-top:4px;color:#8a95a3;font-size:12px}.event-card p{margin:9px 0 0;color:#66768a;line-height:1.6;white-space:pre-wrap}.event-status{display:block;margin-top:8px;color:#76869a}.event-line :deep(.el-timeline-item__timestamp){color:#929cab;font-size:12px}@media(max-width:760px){.project-page{padding:14px}.page-head{align-items:flex-start;flex-direction:column;gap:14px}.page-head>.el-button{width:100%}.project-summary{grid-template-columns:repeat(2,1fr)}.project-summary div:nth-child(2){border-right:0}.project-summary div:nth-child(-n+2){border-bottom:1px solid #e2e7ed}.operating-grid{grid-template-columns:1fr}.filter-card :deep(.el-form-item){display:flex;margin-right:0}.filter-card :deep(.el-input),.filter-card :deep(.el-select){width:100%!important}.tab-tools{align-items:stretch;flex-direction:column;gap:8px}.tab-tools>.el-button{width:100%;margin:0}.review-actions{display:grid;grid-template-columns:1fr 1fr}.review-actions .el-button{width:100%;margin:0}.project-period-line{grid-template-columns:1fr}.project-period-line>span{display:none}.event-card{padding:11px}.event-head{gap:8px}}
.routine-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.routine-period-line>span{color:#7f8a99}@media(max-width:760px){.routine-period-line{grid-template-columns:1fr}.routine-period-line>span{display:none}}
.governance-banner{margin:0 0 16px}.stage-grid{display:grid;gap:12px;margin-top:12px}.stage-submit{margin-top:14px}
.stage-close-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px}.stage-close-actions :deep(.el-button){margin:0}
.execution-summary{margin:0 0 16px;padding:16px;border:1px solid #d8e5f2;border-radius:12px;background:linear-gradient(135deg,#f8fbff,#f4f8fc)}
.execution-title{display:flex;align-items:flex-start;justify-content:space-between}.execution-title span{color:#8491a1;font-size:12px}.execution-title h3{margin:4px 0 0;color:#1e3856;font-size:17px}
.execution-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:14px}.execution-metrics div{padding:12px;border-radius:8px;background:#fff}.execution-metrics span,.execution-metrics strong{display:block}.execution-metrics span{color:#8491a1;font-size:12px}.execution-metrics strong{margin-top:7px;color:#203751;font-size:19px}
.execution-summary>p{margin:12px 0 0;color:#748397;font-size:12px;line-height:1.6}
.event-detail-label{margin-right:4px;color:#4d5f75;font-weight:600}
.cockpit-hero{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:14px}.cockpit-hero>div{padding:16px;border:1px solid #dce5ee;border-radius:12px;background:linear-gradient(145deg,#f8fbfe,#fff)}.cockpit-hero span,.cockpit-hero small,.cockpit-hero strong{display:block}.cockpit-hero span{color:#7d8998;font-size:12px}.cockpit-hero strong{margin:7px 0 9px;color:#1d3855;font-size:25px}.cockpit-hero small{margin-top:7px;color:#7a8796;line-height:1.45}.cockpit-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}.cockpit-metrics article{padding:15px;border:1px solid #e0e6ec;border-top:3px solid #4a7fb4;border-radius:10px;background:#fff}.cockpit-metrics span,.cockpit-metrics b,.cockpit-metrics small{display:block}.cockpit-metrics span{color:#7e8b9a;font-size:12px}.cockpit-metrics b{margin:7px 0 5px;color:#21364e;font-size:21px}.cockpit-metrics small{color:#8a95a2}.cockpit-metrics .is-success{border-top-color:#2a9676}.cockpit-metrics .is-danger{border-top-color:#d74b55;background:#fff8f8}.cockpit-metrics .is-warning{border-top-color:#d99a28;background:#fffbf2}.cockpit-columns{display:grid;grid-template-columns:1.15fr .85fr;gap:12px}.cockpit-card{padding:16px;border:1px solid #e0e6ec;border-radius:12px;background:#fafbfd}.cockpit-card-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px}.cockpit-card-head h3{margin:0;color:#26384d;font-size:17px}.cockpit-card-head p{margin:5px 0 0;color:#8491a0;font-size:12px}.cockpit-empty{padding:30px;text-align:center;color:#929caa}.kpi-overview-row{display:grid;grid-template-columns:minmax(150px,1fr) 135px minmax(140px,.8fr);align-items:center;gap:12px;padding:12px 0;border-top:1px solid #e4e9ee}.kpi-overview-row:first-of-type{border-top:0}.kpi-overview-row b,.kpi-overview-row small{display:block}.kpi-overview-row small{margin-top:4px;color:#8491a0}.kpi-overview-row>div:nth-child(2) strong{display:block;margin-bottom:5px;color:#315f8c}.kpi-overview-row>span{color:#68778a;font-size:12px;text-align:right}.execution-overview-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:9px}.execution-overview-grid div{padding:13px;border-radius:9px;background:#fff}.execution-overview-grid span,.execution-overview-grid b{display:block}.execution-overview-grid span{color:#8491a0;font-size:12px}.execution-overview-grid b{margin-top:6px;color:#243950;font-size:19px}
.kpi-settlement-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:10px;padding:10px;border-radius:10px;background:#eef6f3}.kpi-settlement-summary>div{padding:8px}.kpi-settlement-summary span,.kpi-settlement-summary b{display:block}.kpi-settlement-summary span{color:#75887f;font-size:11px}.kpi-settlement-summary b{margin-top:5px;color:#245b4d;font-size:15px}
@media(max-width:760px){.execution-metrics{grid-template-columns:repeat(2,1fr)}}
@media(max-width:760px){.cockpit-hero,.cockpit-metrics,.cockpit-columns{grid-template-columns:1fr}.kpi-overview-row{grid-template-columns:1fr}.kpi-overview-row>span{text-align:left}.cockpit-card-head{gap:8px}.cockpit-metrics,.kpi-settlement-summary{grid-template-columns:repeat(2,1fr)}}
@media(max-width:760px){.kpi-close-guard{grid-template-columns:auto minmax(0,1fr)}.kpi-close-guard>.el-button,.stage-close-actions{grid-column:1/-1;width:100%}.stage-close-actions{display:grid;grid-template-columns:1fr 1fr}.stage-close-actions :deep(.el-button){width:100%}.kpi-close-progress{grid-template-columns:1fr}}
</style>
