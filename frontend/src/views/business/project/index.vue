<template>
  <div class="app-container project-page">
    <header class="page-head">
      <div><span class="eyebrow">PROJECT PORTFOLIO</span><h1>{{ $tr("项目中心") }}</h1><p>{{ $tr("负责人完成立项申请并启动后，在这里自主管理执行、核算与结项。") }}</p></div>
      <el-button v-hasPermi="['business:project:proposal:add']" type="primary" icon="Plus" @click="router.push('/business/project-proposals')">{{ $tr("发起立项申请") }}</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item><el-input v-model="query.keyword" clearable :placeholder="$tr(&quot;项目名称 / 编号 / 负责人&quot;)" style="width:240px" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-select v-model="query.companyDeptId" clearable filterable :placeholder="$tr(&quot;全部公司&quot;)" style="width:200px" @change="query.mainOwnerDeptId=''" @visible-change="visible => visible && loadFilterOptions()"><el-option v-for="company in companies" :key="company.companyDeptId" :label="company.companyName" :value="company.companyDeptId" /></el-select></el-form-item>
        <el-form-item><el-select v-model="query.mainOwnerDeptId" clearable filterable :placeholder="$tr(&quot;全部部门（主负责人）&quot;)" style="width:220px" @visible-change="visible => visible && loadFilterOptions()"><el-option v-for="department in filterDepartments" :key="department.deptId" :label="query.companyDeptId ? department.deptName : `${department.companyName} / ${department.deptName}`" :value="department.deptId" /></el-select></el-form-item>
        <el-form-item><el-select v-model="query.status" clearable :placeholder="$tr(&quot;全部状态&quot;)" style="width:140px"><el-option v-for="(label,key) in statusLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item><el-select v-model="query.managementMode" clearable :placeholder="$tr(&quot;全部管理模式&quot;)" style="width:145px"><el-option :label="$tr(&quot;轻量&quot;)" value="LIGHT"/><el-option :label="$tr(&quot;标准&quot;)" value="STANDARD"/><el-option :label="$tr(&quot;重点监管&quot;)" value="KEY_CONTROL"/></el-select></el-form-item>
        <el-form-item><el-select v-model="query.closeMethod" clearable :placeholder="$tr(&quot;全部结项方式&quot;)" style="width:145px"><el-option v-for="(label,key) in closeMethodLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="search">{{ $tr("查询") }}</el-button><el-button @click="resetQuery">{{ $tr("重置") }}</el-button></el-form-item>
      </el-form>
    </el-card>

    <ProjectDeletionReviews v-if="canReviewProjectDeletion" ref="deletionReviews" @reviewed="handleDeletionReviewed" />
    <el-card shadow="never" class="table-card">
      <ProjectHierarchyTable ref="hierarchyTable" :query="appliedQuery" @create="openSubprojectForm" @detail="openDetail" @deleted="handleProjectDeleted" @progress="row => progressPanel.open(row)" />
    </el-card>

    <el-drawer v-model="detailVisible" size="min(920px, 96vw)" destroy-on-close @closed="detail = null">
      <template #header><div v-if="detail" class="drawer-title"><div><span>{{ detail.projectNo }}</span><h2>{{ detail.projectName }}</h2></div><BusinessProjectState :project="detail" /></div></template>
      <template v-if="detail">
        <section class="project-summary">
        <div><span>{{ $tr("归属公司") }}</span><b>{{ detail.companyName || $tr("待设置") }}</b></div><div><span>{{ $tr("归属老板") }}</span><b>{{ detail.sponsorOwnerName || detail.initiatorName }}</b></div><div><span>{{ $tr("申请人") }}</span><b>{{ detail.applicantName || detail.mainOwnerName }}</b></div><div><span>{{ $tr("主负责人") }}</span><b>{{ detail.mainOwnerName }}</b></div><div><span>{{ $tr("管理模式") }}</span><b>{{ managementModeLabel[detail.managementMode] || detail.managementMode }}</b></div><div><span>{{ $tr("结项方式") }}</span><b>{{ closeMethodLabel[detail.closeMethod] || detail.closeMethod }}</b></div><div><span>{{ $tr("核算方式") }}</span><b>{{ accountingLabel[detail.accountingMode] || detail.accountingMode }}</b></div><div><span>{{ $tr("目标模式") }}</span><b>{{ goalModeLabel[detail.goalMode || 'TOTAL'] }}</b></div><div><span>{{ $tr("计划周期") }}</span><b>{{ detail.planStartDate ? $tr("{0} 至 {1}", [detail.planStartDate, detail.planEndDate || $tr("不限期")]) : '—' }}</b></div>
        </section>
        <BusinessSettlementPanel ref="settlementPanelRef" :project="detail" @closed="refreshDetail" />
        <div class="objective"><span>{{ $tr("项目目标") }}</span><p>{{ detail.objective || $tr("尚未填写项目目标") }}</p></div>
        <el-alert class="governance-banner" :title="governanceTitle" :description="governanceDescription" type="info" :closable="false" show-icon />
        <section v-if="operating.executionSummary" class="execution-summary">
          <div class="execution-title"><div><span>{{ $tr("直播日报 · {0}", [operating.executionSummary.statDate]) }}</span><h3>{{ $tr("昨天提交情况") }}</h3></div><el-tag type="info" effect="plain">{{ $tr("只读数字") }}</el-tag></div>
          <div class="execution-metrics">
            <div><span>{{ $tr("应提交") }}</span><strong>{{ number(operating.executionSummary.expectedStreamerCount) }}</strong></div>
            <div><span>{{ $tr("已提交") }}</span><strong>{{ number(operating.executionSummary.submittedStreamerCount) }}</strong></div>
            <div><span>{{ $tr("未提交") }}</span><strong :class="{ danger: yesterdayMissing > 0 }">{{ number(yesterdayMissing) }}</strong></div>
            <div><span>{{ $tr("完成率") }}</span><strong>{{ yesterdayRate }}%</strong></div>
          </div>
          <p>{{ $tr("这里只返回数字，不显示主播名单和日报内容；主播仍只在直播数据管理中提交。") }}</p>
        </section>
        <div class="action-bar">
          <el-button @click="progressPanel.open(detail)">{{ $tr("进度汇报历史") }}</el-button>
          <el-button v-if="canManage && !usesActualWork" icon="Edit" @click="openProjectForm(detail)">{{ $tr("编辑资料") }}</el-button>
          <el-button v-if="isBoss && projectAccountingState(detail)==='OPEN'" type="success" icon="Plus" @click="openProjectAccountingEntry('revenue')">{{ $tr("录入收入") }}</el-button>
          <el-button v-if="isBoss && projectAccountingState(detail)==='OPEN'" type="primary" icon="Plus" @click="openProjectAccountingEntry('spend')">{{ $tr("录入支出") }}</el-button>
          <el-button v-if="isBoss && !isDeliveryEnded(detail)" icon="User" @click="openOwnerDialog">{{ $tr("更换主负责人") }}</el-button>
          <el-button v-for="action in availableActions" :key="action.key" :type="action.type" :disabled="isKpiBlockedCloseAction(action)" :title="isKpiBlockedCloseAction(action)?$tr(&quot;请先完成并确认全部KPI结算&quot;):''" @click="runTransition(action)">{{ action.label }}</el-button>
        </div>
        <section v-if="showKpiClosureGuard && detail.closeMethod!=='RESULT_ACCEPTANCE'" :class="['kpi-close-guard', `is-${kpiClosureState.tone}`]">
          <div class="kpi-close-mark">KPI</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>{{ $tr("项目结项前置条件") }}</span><el-tag :type="kpiClosureState.tone" effect="light">{{ kpiClosureState.label }}</el-tag></div>
            <b>{{ kpiClosureState.title }}</b>
            <p>{{ kpiClosureState.description }}</p>
            <div v-if="kpiClosureState.planCount" class="kpi-close-progress"><span>{{ $tr("已确认 {0} / {1} 个方案", [kpiClosureState.confirmedCount, kpiClosureState.planCount]) }}</span><el-progress :percentage="kpiClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
          </div>
          <el-button :type="kpiClosureState.actionType" plain @click="openKpiWorkspace">{{ kpiClosureState.actionLabel }}</el-button>
        </section>
        <section v-if="showStageClosureGuard" :class="['kpi-close-guard', `is-${stageClosureState.tone}`]">
          <div class="kpi-close-mark">{{ $tr("结项") }}</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>{{ $tr("阶段验收项目结项") }}</span><el-tag :type="stageClosureState.tone" effect="light">{{ stageClosureState.label }}</el-tag></div>
            <b>{{ stageClosureState.title }}</b>
            <p>{{ stageClosureState.description }}</p>
            <div v-if="stageClosureState.milestoneCount" class="kpi-close-progress"><span>{{ $tr("已验收 {0} / {1} 个里程碑", [stageClosureState.doneCount, stageClosureState.milestoneCount]) }}</span><el-progress :percentage="stageClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
          </div>
          <div class="stage-close-actions">
            <el-button v-if="stageClosureState.canRequest" type="success" @click="runTransition({key:'REQUEST_CLOSE',label:$tr(&quot;申请结项&quot;),type:'success'})">{{ $tr("提交{0}检验", [acceptanceReviewerLabel]) }}</el-button>
            <template v-if="stageClosureState.canReview"><el-button type="success" @click="runTransition({key:'CLOSE',label:$tr(&quot;确认结项并冻结&quot;),type:'success'})">{{ $tr("确认结项并冻结") }}</el-button><el-button type="warning" plain @click="runTransition({key:'RETURN_ACTIVE',label:$tr(&quot;退回补充&quot;)})">{{ $tr("退回补充") }}</el-button></template>
          </div>
        </section>
        <section v-if="showDirectClosureGuard" class="kpi-close-guard is-warning">
          <div class="kpi-close-mark">{{ $tr("审核") }}</div>
          <div class="kpi-close-copy">
            <div class="kpi-close-title"><span>{{ $tr("直接结项检验") }}</span><el-tag type="warning" effect="light">{{ $tr("待{0}检验", [acceptanceReviewerLabel]) }}</el-tag></div>
            <b>{{ $tr("负责人已提交直接结项申请") }}</b>
            <p>{{ canReviewAcceptance ? $tr("请核对项目成果和结项前置条件，通过后项目才会正式关闭。") : $tr("正在等待{0}检验；子项目负责人无权自行通过。", [acceptanceReviewerLabel]) }}</p>
          </div>
          <div v-if="canReviewAcceptance" class="stage-close-actions"><el-button type="success" @click="runTransition({key:'CLOSE',label:$tr(&quot;检验通过、结项并冻结&quot;),type:'success'})">{{ $tr("检验通过、结项并冻结") }}</el-button><el-button type="warning" plain @click="runTransition({key:'RETURN_ACTIVE',label:$tr(&quot;退回补充&quot;)})">{{ $tr("退回补充") }}</el-button></div>
        </section>
        <el-tabs ref="detailTabs" v-model="activeTab" class="project-detail-tabs">
          <el-tab-pane :label="$tr(&quot;项目总览&quot;)" name="overview">
            <section class="cockpit-hero">
              <div v-if="detail.parentId || detail.goalMode!=='NO_TOTAL'"><span>{{ $tr("项目整体完成率") }}</span><el-button class="progress-link" link type="primary" @click="progressPanel.open(detail)"><strong>{{ projectProgress(detail) }}%</strong></el-button><small v-if="detail.subprojectCount">{{ $tr("主项目进度由负责人填报，点击查看各子项目汇报") }}</small><el-progress :percentage="projectProgress(detail)" :stroke-width="9" /></div>
              <div v-else><span>{{ $tr("项目目标模式") }}</span><strong>{{ $tr("持续经营") }}</strong><small>{{ $tr("不填写虚假的总完成百分比，以每日目标和任务成果持续跟踪。") }}</small></div>
              <div><span>{{ $tr("计划时间进度") }}</span><strong>{{ scheduleProgress }}%</strong><el-progress :percentage="scheduleProgress" :status="scheduleProgress>projectProgress(detail)?'warning':undefined" :stroke-width="9" /></div>
              <div><span>{{ $tr("剩余时间") }}</span><strong>{{ remainingDaysText }}</strong><small>{{ scheduleStatusText }}</small></div>
            </section>
            <el-alert v-if="cockpitError" :title="$tr(&quot;经营数据暂时无法读取，任务、进度和目标信息仍可正常查看。&quot;)" type="warning" :closable="false" show-icon />
            <el-alert v-else-if="cockpitCostIncomplete" :title="cockpitCostNotice" type="warning" :closable="false" show-icon />
            <section class="cockpit-metrics" v-loading="cockpitLoading">
              <article><span>{{ projectBudgetTitle }}</span><b>{{ cockpitBudget==null?$tr("未设置"):money(cockpitBudget) }}</b><small>{{ cockpitCurrency }}</small></article>
              <el-alert v-if="cockpit.budgetPeriodExpired" :title="$tr(&quot;本期预算已到期，请在项目计划与变更中续编；此处保留上一期使用情况。&quot;)" type="warning" :closable="false"/><article v-if="isDailyBudget"><span>{{ $tr("每日统计口径") }}</span><b>{{ (operating.budgetScope||detail.budgetScope)==='CASH_EXPENSE'?$tr("仅外部支出"):$tr("全成本") }}</b><small>{{ $tr("按业务日分别预警；启动预算 {0}", [money(operating.startupBudgetLimit??detail.startupBudgetLimit)]) }}</small></article><article v-else :class="budgetTone"><span>{{ detail.budget?$tr("本期预算已使用"):cockpitCostIncomplete?$tr("已核算预算使用"):$tr("预算已使用") }}</span><b>{{ money(cockpitBudgetSpent) }}</b><small v-if="cockpitCostIncomplete">{{ $tr("剩余预算待成本完整后确认") }}</small><small v-else>{{ $tr("{0} · 剩余 {1}", [budgetUsage==null?'—':`${budgetUsage}%`, money(cockpitBudgetRemaining)]) }}</small></article>
              <article><span>{{ $tr("累计收入") }}</span><b>{{ money(cockpitSummary.revenueAmount) }}</b><small>{{ cockpitCurrency }}</small></article>
              <article><span>{{ cockpitCostIncomplete?$tr("累计已核算成本"):$tr("累计总成本") }}</span><b>{{ money(cockpitTotalCost) }}</b><small>{{ cockpitCostIncomplete?$tr("部分人员成本未知，待完善"):$tr("业务、人员、奖金及公共费用") }}</small></article>
              <article :class="cockpitCostIncomplete?'is-warning':cockpitSummary.profitAmount==null?'':Number(cockpitSummary.profitAmount)<0?'is-danger':'is-success'"><span>{{ cockpitCostIncomplete?$tr("已核算税前结果"):$tr("累计税前结果") }}</span><b>{{ signedMoney(cockpitSummary.pretaxProfit ?? cockpitSummary.profitAmount) }}</b><small>{{ cockpitCostIncomplete?$tr("成本尚不完整，不代表最终利润"):cockpitCurrency }}</small></article>
              <article><span>{{ $tr("累计税额") }}</span><b>{{ money(cockpitSummary.taxAmount) }}</b><small>{{ $tr("按公司累计结果分配，含税额冲回") }}</small></article><article :class="Number(cockpitSummary.afterTaxProfit)<0?'is-danger':'is-success'"><span>{{ cockpitCostIncomplete?$tr("已核算税后盈利"):$tr("税后盈利结果") }}</span><b>{{ signedMoney(cockpitSummary.afterTaxProfit) }}</b><small>{{ cockpit.taxUnconfiguredCount?$tr("税率未设置，暂按0%"):cockpitCostIncomplete?$tr("成本待完善"):cockpitCurrency }}</small></article><article><span>{{ $tr("经营结果天数") }}</span><b>{{ cockpitSummary.resultCount || 0 }}</b><small>{{ cockpitDateRange }}</small></article>
            </section>
            <ProjectPublicExpensePanel :project-id="detail.projectId" :currency="cockpitCurrency" />
            <div class="cockpit-columns">
              <section class="cockpit-card">
                <div class="cockpit-card-head"><div><h3>{{ $tr("KPI目标与差距") }}</h3><p>{{ $tr("当前目标及自动统计或手工填报的实际值") }}</p></div><el-button link type="primary" @click="openKpiWorkspace">{{ $tr("进入KPI工作区") }}</el-button></div>
                <el-alert v-if="kpiWorkspaceError" :title="$tr(&quot;KPI自动数据暂时无法读取，请刷新后重试。&quot;)" type="warning" :closable="false" show-icon />
                <div v-if="cockpitSettlement" class="kpi-settlement-summary">
                  <div><span>{{ $tr("结算状态") }}</span><b>{{ settlementStatusText }}</b></div>
                  <div><span>{{ $tr("综合得分") }}</span><b>{{ cockpitSettlement.totalScore ?? $tr("待计算") }}</b></div>
                  <div v-if="cockpitPlan?.rewardPolicyVersion!=='INDEPENDENT_V1'"><span>{{ $tr("历史档位") }}</span><b>{{ matchedTier?.tierName || $tr("未命中") }}</b></div>
                  <div v-if="cockpitPlan?.rewardPolicyVersion!=='INDEPENDENT_V1'"><span>{{ $tr("历史奖金池") }}</span><b>¥{{ money(cockpitSettlement.bonusAmount) }}</b></div>
                </div>
                <div v-if="!cockpitKpis.length" class="cockpit-empty">{{ $tr("尚未设置项目KPI") }}</div>
                <div v-for="item in cockpitKpis" :key="item.itemId||item.kpiId" class="kpi-overview-row">
                  <div><b>{{ item.kpiName }}</b><small>{{ $tr("目标 {0} {1} · 当前 {2}", [item.targetValue, $tr(item.unit) || '', kpiActualText(item)]) }}</small></div>
                  <div><strong>{{ kpiHasActual(item) ? `${kpiCompletion(item)}%` : '—' }}</strong><el-progress :percentage="kpiHasActual(item) ? kpiCompletion(item) : 0" :stroke-width="7" /></div>
                  <span>{{ kpiGapText(item) }}</span>
                </div>
              </section>
              <section class="cockpit-card">
                <div class="cockpit-card-head"><div><h3>{{ $tr("执行与风险") }}</h3><p>{{ $tr("项目当前需要关注的事项") }}</p></div></div>
                <div class="execution-overview-grid">
                  <div><span>{{ $tr("一次性任务") }}</span><b>{{ detail.completedTaskCount || 0 }} / {{ detail.taskCount || detail.tasks?.length || 0 }}</b></div>
                  <div><span>{{ $tr("未完成任务") }}</span><b>{{ openTaskCount }}</b></div>
                  <div><span>{{ $tr("逾期任务") }}</span><b :class="{danger:overdueTaskCount}">{{ overdueTaskCount }}</b></div>
                  <div><span>{{ $tr("开放风险") }}</span><b :class="{danger:openRiskCount}">{{ openRiskCount }}</b></div>
                  <div><span>{{ $tr("项目成员") }}</span><b>{{ detail.members?.length || 0 }}</b></div>
                  <div v-if="!usesActualWork"><span>{{ $tr("计划投入配置") }}</span><b>{{ operating.staffAllocations?.length || 0 }}</b></div>
                </div>
              </section>
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="usesActualWork" :label="$tr(&quot;项目计划与变更&quot;)" name="plan"><BusinessProjectPlanPanel :project="detail" @changed="refreshDetail"/></el-tab-pane>
          <el-tab-pane v-if="usesActualWork" :label="$tr(&quot;人员工作日成本&quot;)" name="resources"><BusinessProjectWorkPanel ref="projectWorkPanel" :project-id="detail.projectId" :members="detail.members || []" :can-manage="canManageAllocation" @changed="loadCockpit"/></el-tab-pane>
          <el-tab-pane v-if="!usesActualWork" :label="$tr(&quot;历史经营配置&quot;)" name="operating">
            <div class="operating-grid">
              <section class="operating-card budget-card"><div class="operating-head"><div><small>{{ projectBudgetTitle }}</small><strong>{{ projectBudgetText }}</strong></div><el-button v-if="!detail.budget && !isDeliveryEnded(detail) && (isBoss||myRole==='OWNER')" size="small" type="primary" @click="openBudgetDialog">{{ $tr("调整预算") }}</el-button></div><p v-if="detail.budget">{{ $tr("预算期间：{0} 至 {1}；人员预算 {2} ＋ 业务预算 {3}。需调整或续编时，请使用“项目计划与变更”。", [detail.budget.startDate, detail.budget.endDate, money(detail.budget.personnelAmount), money(detail.budget.businessAmount)]) }}</p><p v-else>{{ $tr("负责人可按经营变化调整预算；每次金额、原因、操作人和版本都会保留，老板可随时查看和修正。") }}</p></section>
              <section class="operating-card"><div class="operating-head"><div><small>{{ $tr("当前KPI") }}</small><strong>{{ $tr("{0} 项", [currentKpis.length]) }}</strong></div><el-button v-if="isBoss||myRole==='OWNER'" size="small" type="primary" @click="router.push({path:'/business/kpi-bonus',query:{projectId:detail.projectId}})">{{ isDeliveryEnded(detail) ? $tr("查看KPI结算") : $tr("配置项目指标") }}</el-button></div><p>{{ $tr("项目负责人设置指标目标和权重，发布方案时统一设置考核时间；老板保留修改能力。") }}</p></section>
            </div>
            <div class="tab-tools section-gap"><b>{{ $tr("项目KPI") }}</b><span class="muted">{{ $tr("共 {0} 个版本", [operating.kpis?.length || 0]) }}</span></div>
            <el-table :data="currentKpis" size="small" :empty-text="$tr(&quot;尚未设置KPI&quot;)"><el-table-column prop="kpiName" :label="$tr(&quot;指标&quot;)" min-width="150"><template #default="{row}"><b>{{ row.kpiName }}</b><small class="subline">{{ row.kpiCode }} · v{{ row.targetVersion }}</small></template></el-table-column><el-table-column :label="$tr(&quot;项目目标&quot;)" min-width="130"><template #default="{row}">{{ row.targetValue }} {{ $tr(row.unit) || '' }}</template></el-table-column><el-table-column :label="$tr(&quot;方向&quot;)" width="95"><template #default="{row}">{{ row.direction==='LOWER_BETTER'?$tr("越低越好"):$tr("越高越好") }}</template></el-table-column><el-table-column prop="weight" :label="$tr(&quot;权重%&quot;)" width="78" /></el-table>
            <el-collapse class="history-collapse"><el-collapse-item :title="$tr(&quot;查看KPI历史版本&quot;)"><el-table :data="retiredKpis" size="small" :empty-text="$tr(&quot;暂无历史版本&quot;)"><el-table-column prop="kpiName" :label="$tr(&quot;指标&quot;)" /><el-table-column prop="targetVersion" :label="$tr(&quot;版本&quot;)" width="70" /><el-table-column prop="targetValue" :label="$tr(&quot;目标值&quot;)" /><el-table-column prop="createTime" :label="$tr(&quot;版本创建时间&quot;)" /><el-table-column prop="remark" :label="$tr(&quot;调整说明&quot;)" /></el-table></el-collapse-item></el-collapse>
            <div class="tab-tools section-gap"><b>{{ $tr("预算调整历史") }}</b></div>
            <el-table :data="operating.budgetHistory || []" size="small" :empty-text="$tr(&quot;暂无预算记录&quot;)"><el-table-column :label="$tr(&quot;版本&quot;)" width="70"><template #default="{row}">v{{ row.budgetVersion }}</template></el-table-column><el-table-column :label="$tr(&quot;调整&quot;)"><template #default="{row}">{{ money(row.fromAmount) }} → {{ money(row.toAmount) }} {{ row.currency }}</template></el-table-column><el-table-column prop="reason" :label="$tr(&quot;原因&quot;)" min-width="160" /><el-table-column prop="operatorName" :label="$tr(&quot;操作人&quot;)" width="100" /><el-table-column prop="effectiveTime" :label="$tr(&quot;生效时间&quot;)" width="165" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="$tr(&quot;持续工作&quot;)" name="routines">
            <div class="tab-tools"><div><b>{{ $tr("持续工作计划") }}</b><span class="muted routine-tip">{{ $tr("直播主播由执行系统自动同步，日报仍只在直播数据管理中提交。") }}</span></div><el-button v-if="canManage" size="small" type="primary" @click="openRoutine()">{{ $tr("新增持续工作") }}</el-button></div>
            <el-table :data="allProjectRoutines" size="small" :empty-text="$tr(&quot;尚未设置持续工作&quot;)">
              <el-table-column :label="$tr(&quot;工作内容&quot;)" min-width="220"><template #default="{row}"><b>{{ row.routineName }}</b><small v-if="row.sourceManaged" class="subline">{{ $tr("直播同步 · 数据日期 {0}", [row.sourceBizDate]) }}</small><small v-else-if="row.status==='VOID'" class="subline danger">{{ $tr("已停用，历史数据保留") }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;目标方式&quot;)" min-width="170"><template #default="{row}"><b>{{ routineTargetModeLabel[row.targetMode || 'FIXED'] }}</b><small class="subline">{{ routineTargetText(row) }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;负责人&quot;)" width="110"><template #default="{row}"><span :class="{ danger: !row.assigneeUserId }">{{ row.assigneeName || $tr("未分配") }}</span></template></el-table-column>
              <el-table-column :label="$tr(&quot;当前状态&quot;)" min-width="115"><template #default="{row}"><el-tag v-if="row.sourceManaged" :type="row.todayReportId?'success':'warning'">{{ row.todayReportId ? $tr("已提交") : $tr("未提交") }}</el-tag><span v-else>{{ row.cumulativeActual || 0 }} {{ $tr(row.unit) }}</span></template></el-table-column>
              <el-table-column :label="$tr(&quot;监督 / 执行区间记录&quot;)" min-width="240"><template #default="{row}"><span v-if="row.sourceManaged">{{ $tr("{0}监督 · {1} 至 {2}", [row.supervisorName, row.startDate, row.endDate || todayText()]) }}</span><div v-else class="period-list"><span v-for="(period,index) in workPeriods(row)" :key="period.periodId||index">{{ $tr("第 {0} 段：{1}", [index+1, executionPeriodText(period)]) }}</span></div></template></el-table-column>
              <el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="120"><template #default="{row}"><el-tag v-if="row.sourceManaged" type="info" effect="plain">{{ $tr("自动同步") }}</el-tag><template v-else-if="row.status==='ACTIVE'"><el-button link @click="openRoutine(row)">{{ $tr("编辑") }}</el-button><el-button link type="danger" @click="removeRoutine(row)">{{ $tr("停用") }}</el-button></template><el-button v-else link type="success" @click="enableRoutine(row)">{{ $tr("启用") }}</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="$tr(&quot;一次性任务&quot;)" name="tasks"><div class="tab-tools"><div><b>{{ $tr("一次性任务") }}</b><span class="muted routine-tip">{{ $tr("停用只结束当前执行区间，任务资料和填报记录会继续保留。") }}</span></div><el-button v-if="canManage" size="small" type="primary" @click="openItem('task')">{{ $tr("新增任务") }}</el-button></div><el-table :data="allProjectTasks" size="small"><el-table-column :label="$tr(&quot;任务&quot;)" min-width="180"><template #default="{row}"><b>{{ row.taskName }}</b><small v-if="row.activeStatus==='VOID'" class="subline danger">{{ $tr("已停用，历史数据保留") }}</small></template></el-table-column><el-table-column v-if="showMilestones" :label="$tr(&quot;所属里程碑&quot;)" min-width="130"><template #default="{row}">{{ milestoneName(row.milestoneId) }}</template></el-table-column><el-table-column prop="assigneeName" :label="$tr(&quot;负责人&quot;)" width="110" /><el-table-column :label="$tr(&quot;状态&quot;)" width="100"><template #default="{row}">{{ row.activeStatus==='VOID'?$tr("已停用"):taskStatusLabel[row.status] }}</template></el-table-column><el-table-column :label="$tr(&quot;进度&quot;)" width="115"><template #default="{row}"><el-progress :percentage="row.progress || 0" :stroke-width="7" /></template></el-table-column><el-table-column :label="$tr(&quot;执行区间记录&quot;)" min-width="220"><template #default="{row}"><div class="period-list"><span v-for="(period,index) in workPeriods(row)" :key="period.periodId||index">{{ $tr("第 {0} 段：{1}", [index+1, executionPeriodText(period)]) }}</span></div></template></el-table-column><el-table-column prop="dueDate" :label="$tr(&quot;截止日期&quot;)" width="115" /><el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="120"><template #default="{row}"><template v-if="row.activeStatus!=='VOID'"><el-button link @click="openItem('task',row)">{{ $tr("编辑") }}</el-button><el-button link type="danger" @click="removeItem('task',row)">{{ $tr("停用") }}</el-button></template><el-button v-else link type="success" @click="enableTask(row)">{{ $tr("启用") }}</el-button></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane :label="$tr(&quot;成员&quot;)" name="members"><div class="tab-tools"><b>{{ $tr("项目成员") }}</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('member')">{{ $tr("添加成员") }}</el-button></div><el-table :data="detail.members" size="small"><el-table-column prop="userNameSnapshot" :label="$tr(&quot;姓名&quot;)" /><el-table-column :label="$tr(&quot;项目角色&quot;)"><template #default="{row}">{{ memberRoleLabel[row.memberRole] }}</template></el-table-column><el-table-column prop="joinedDate" :label="$tr(&quot;加入日期&quot;)" /><el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="145"><template #default="{row}"><el-button v-if="row.memberRole !== 'OWNER' && (row.memberRole !== 'DEPUTY' || canManageDeputies)" link type="primary" @click="openItem('member',row)">{{ $tr("调整角色") }}</el-button><el-button v-if="row.memberRole !== 'OWNER' && (row.memberRole !== 'DEPUTY' || canManageDeputies)" link type="danger" @click="removeItem('member',row)">{{ $tr("移除") }}</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane v-if="showMilestones" :label="$tr(&quot;里程碑&quot;)" name="milestones"><div class="tab-tools"><b>{{ $tr("关键里程碑") }}</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('milestone')">{{ $tr("新增里程碑") }}</el-button></div><el-table :data="detail.milestones" size="small"><el-table-column prop="milestoneName" :label="$tr(&quot;里程碑&quot;)" /><el-table-column prop="planDate" :label="$tr(&quot;计划日期&quot;)" width="115" /><el-table-column :label="$tr(&quot;状态&quot;)" width="100"><template #default="{row}">{{ milestoneStatusLabel[row.status] || row.status }}</template></el-table-column><el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="110"><template #default="{row}"><template v-if="!['REVIEWING','DONE'].includes(row.status)"><el-button link @click="openItem('milestone',row)">{{ $tr("编辑") }}</el-button><el-button link type="danger" @click="removeItem('milestone',row)">{{ $tr("删除") }}</el-button></template><el-tag v-else type="info" effect="plain">{{ $tr("验收锁定") }}</el-tag></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane v-if="showRisks" :label="$tr(&quot;风险&quot;)" name="risks"><div class="tab-tools"><b>{{ $tr("风险台账") }}</b><el-button v-if="canManage" size="small" type="primary" @click="openItem('risk')">{{ $tr("登记风险") }}</el-button></div><el-table :data="detail.risks" size="small"><el-table-column prop="riskTitle" :label="$tr(&quot;风险&quot;)" min-width="180" /><el-table-column :label="$tr(&quot;等级&quot;)" width="90"><template #default="{row}"><el-tag :type="['HIGH','CRITICAL'].includes(row.severity)?'danger':'warning'">{{ severityLabel[row.severity] }}</el-tag></template></el-table-column><el-table-column prop="ownerName" :label="$tr(&quot;负责人&quot;)" width="110" /><el-table-column prop="dueDate" :label="$tr(&quot;处理期限&quot;)" width="115" /><el-table-column :label="$tr(&quot;状态&quot;)" width="90"><template #default="{row}">{{ riskStatusLabel[row.status] }}</template></el-table-column><el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="110"><template #default="{row}"><el-button link @click="openItem('risk',row)">{{ $tr("编辑") }}</el-button><el-button link type="danger" @click="removeItem('risk',row)">{{ $tr("删除") }}</el-button></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane v-if="detail.closeMethod==='RESULT_ACCEPTANCE'" :label="$tr(&quot;成果验收&quot;)" name="acceptance"><div class="tab-tools"><b>{{ $tr("验收资料与{0}意见", [acceptanceReviewerLabel]) }}</b><el-button v-if="canSubmitAcceptance" size="small" type="success" :disabled="kpiClosureState.ready===false" :title="kpiClosureState.ready===false?$tr(&quot;请先完成并确认全部KPI结算&quot;):''" @click="openAcceptanceSubmit">{{ $tr("提交验收") }}</el-button></div>
            <section v-if="showKpiClosureGuard" :class="['kpi-close-guard', 'in-tab', `is-${kpiClosureState.tone}`]">
              <div class="kpi-close-mark">KPI</div>
              <div class="kpi-close-copy">
                <div class="kpi-close-title"><span>{{ $tr("验收通过前置条件") }}</span><el-tag :type="kpiClosureState.tone" effect="light">{{ kpiClosureState.label }}</el-tag></div>
                <b>{{ kpiClosureState.title }}</b>
                <p>{{ kpiClosureState.description }}</p>
                <div v-if="kpiClosureState.planCount" class="kpi-close-progress"><span>{{ $tr("已确认 {0} / {1} 个方案", [kpiClosureState.confirmedCount, kpiClosureState.planCount]) }}</span><el-progress :percentage="kpiClosureState.percentage" :show-text="false" :stroke-width="7" /></div>
              </div>
              <el-button :type="kpiClosureState.actionType" plain @click="openKpiWorkspace">{{ kpiClosureState.actionLabel }}</el-button>
            </section>
            <el-alert v-if="detail.status==='ACTIVE'" :title="$tr(&quot;完成全部任务和里程碑、处理高风险事项后，才能提交验收。&quot;)" type="info" :closable="false" show-icon />
            <el-empty v-if="!detail.acceptances?.length" :description="$tr(&quot;尚未提交验收资料&quot;)" />
            <article v-for="record in detail.acceptances" :key="record.acceptanceId" class="acceptance-record">
              <div class="acceptance-head"><div><b>{{ $tr("第 {0} 次提交", [record.submissionVersion]) }}</b><span>{{ record.submittedUserName }} · {{ record.submittedTime }}</span></div><el-tag :type="acceptanceTone[record.reviewStatus]">{{ acceptanceLabel[record.reviewStatus] }}</el-tag></div>
              <h4>{{ $tr("结果摘要") }}</h4><p>{{ record.resultSummary }}</p><h4>{{ $tr("交付成果") }}</h4><p>{{ record.deliverables }}</p>
              <business-file-upload v-if="record.attachmentUrls" v-model="record.attachmentUrls" :project-id="detail.projectId" disabled :is-show-tip="false" />
              <div v-if="record.reviewStatus!=='PENDING'" class="review-result"><b>{{ $tr("{0}的验收意见", [record.reviewedUserName]) }}</b><p>{{ record.reviewComment || (record.reviewStatus==='APPROVED'?$tr("验收通过"):$tr("已退回")) }}</p><small>{{ record.reviewedTime }}</small></div>
              <div v-if="record.reviewStatus==='PENDING' && canReviewAcceptance && detail.status==='ACCEPTANCE'" class="review-actions"><el-button type="success" :disabled="kpiClosureState.ready===false" :title="kpiClosureState.ready===false?$tr(&quot;请先完成并确认全部KPI结算&quot;):''" @click="openAcceptanceReview('APPROVED',record)">{{ $tr("验收通过、结项并冻结") }}</el-button><el-button type="warning" plain @click="openAcceptanceReview('RETURNED',record)">{{ $tr("退回执行") }}</el-button></div>
            </article>
          </el-tab-pane>
          <el-tab-pane v-if="showMilestones" :label="$tr(&quot;里程碑验收&quot;)" name="stageAcceptance">
            <el-alert :title="$tr(&quot;每个里程碑关联的任务全部完成后，由负责人提交阶段成果，老板通过后该里程碑才算完成。&quot;)" type="info" :closable="false" show-icon />
            <div class="stage-grid">
              <article v-for="milestone in detail.milestones || []" :key="milestone.milestoneId" class="acceptance-record">
                <div class="acceptance-head"><div><b>{{ milestone.milestoneName }}</b><span>{{ $tr("计划日期 {0}", [milestone.planDate || $tr("未设置")]) }}</span></div><el-tag :type="milestone.status==='DONE'?'success':milestone.status==='REVIEWING'?'warning':'info'">{{ milestoneStatusLabel[milestone.status] || milestone.status }}</el-tag></div>
                <template v-for="record in stageRecords(milestone.milestoneId)" :key="record.stageAcceptanceId"><h4>{{ $tr("第 {0} 次提交 · {1}", [record.submissionVersion, record.submittedUserName]) }}</h4><p>{{ record.resultSummary }}</p><p class="muted">{{ $tr("交付成果：{0}", [record.deliverables]) }}</p><business-file-upload v-if="record.attachmentUrls" v-model="record.attachmentUrls" :project-id="detail.projectId" disabled :is-show-tip="false" /><div v-if="record.reviewStatus==='PENDING'&&canReviewAcceptance&&detail.status==='ACTIVE'" class="review-actions"><el-button type="success" @click="openStageReview('APPROVED',record)">{{ $tr("阶段通过") }}</el-button><el-button type="warning" plain @click="openStageReview('RETURNED',record)">{{ $tr("退回补充") }}</el-button></div><div v-else-if="record.reviewStatus!=='PENDING'" class="review-result"><b>{{ acceptanceLabel[record.reviewStatus] }}</b><p>{{ record.reviewComment || '—' }}</p></div></template>
                <el-button v-if="canSubmitStage(milestone)" class="stage-submit" type="primary" plain @click="openStageSubmit(milestone)">{{ $tr("提交该阶段验收") }}</el-button>
              </article>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="$tr(&quot;负责人交接&quot;)" name="ownerHistory"><el-table :data="detail.ownerHistory" size="small" :empty-text="$tr(&quot;暂无交接记录&quot;)"><el-table-column :label="$tr(&quot;变更&quot;)"><template #default="{row}">{{ row.fromUserName || $tr("初始任命") }} → {{ row.toUserName }}</template></el-table-column><el-table-column prop="reason" :label="$tr(&quot;原因&quot;)" min-width="180" /><el-table-column prop="operatorName" :label="$tr(&quot;操作人&quot;)" width="100" /><el-table-column prop="effectiveTime" :label="$tr(&quot;生效时间&quot;)" width="165" /></el-table></el-tab-pane>
          <el-tab-pane :label="$tr(&quot;动态&quot;)" name="events">
            <el-empty v-if="!detail.events?.length" :description="$tr(&quot;暂无项目动态&quot;)" />
            <el-timeline v-else class="event-line">
              <el-timeline-item v-for="event in detail.events" :key="event.eventId" :type="eventTone(event)">
                <article class="event-card">
                  <div class="event-head">
                    <div class="event-actor">
                      <b>{{ eventSummary(event) }}</b>
                      <div class="event-meta">
                        <span>{{ $tr("账号：") }}<strong>{{ eventActorAccount(event) }}</strong></span>
                        <span v-if="eventWorkAssignee(event)">{{ $tr("执行人：") }}<strong>{{ eventWorkAssignee(event) }}</strong></span>
                        <span>{{ $tr("记录时间：") }}<time>{{ formatEventTime(event.createTime) || $tr("未记录") }}</time></span>
                      </div>
                    </div>
                    <el-tag size="small" effect="plain" :type="eventTone(event)">{{ (event.eventType==='SUBPROJECT_PROGRESS' ? $tr("子项目进度汇报") : eventLabel[event.eventType]) || $tr("其他操作") }}</el-tag>
                  </div>
                  <p v-if="formatEventComment(event)"><span class="event-detail-label">{{ $tr("操作内容：") }}</span>{{ formatEventComment(event) }}</p>
                  <el-button v-if="progressEventTarget(event)" link type="primary" @click="progressPanel.open(progressEventTarget(event))">{{ event.eventType === 'SUBPROJECT_PROGRESS' ? $tr("跳转子项目 · 查看本次汇报") : $tr("查看本次汇报") }}</el-button>
                  <el-button v-else-if="event.eventType==='PROJECT_PROGRESS'" link type="primary" @click="progressPanel.open(detail)">{{ $tr("查看汇报历史") }}</el-button>
                  <small v-if="eventStatusChange(event)" class="event-status">{{ $tr("项目状态：{0}", [eventStatusChange(event)]) }}</small>
                </article>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <BusinessProjectProgress ref="progressPanel" :allow-submit="false" @submitted="handleProgressSubmitted" @closed="clearProgressQuery" />
    <el-dialog v-model="projectDialog" top="5vh" :title="projectForm.projectId ? (projectForm.parentId ? $tr(&quot;编辑子项目&quot;) : $tr(&quot;编辑主项目&quot;)) : $tr(&quot;新增子项目&quot;)" width="min(680px, 94vw)" destroy-on-close>
      <el-alert v-if="projectFormFrozen" :title="$tr(&quot;范围、计划周期和验收基线请通过项目详情的“项目计划与变更”调整。&quot;)" type="info" :closable="false" show-icon style="margin-bottom:16px" />
      <el-form class="project-edit-form" ref="projectFormRef" :model="projectForm" :rules="projectRules" label-width="100px">
        <el-row :gutter="16"><el-col :sm="16" :xs="24"><el-form-item :label="$tr(&quot;项目名称&quot;)" prop="projectName"><el-input v-model="projectForm.projectName" maxlength="160" /></el-form-item></el-col><el-col :sm="8" :xs="24"><el-form-item :label="$tr(&quot;优先级&quot;)"><el-select v-model="projectForm.priority"><el-option :label="$tr(&quot;低&quot;)" value="LOW"/><el-option :label="$tr(&quot;中&quot;)" value="MEDIUM"/><el-option :label="$tr(&quot;高&quot;)" value="HIGH"/></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;项目类型&quot;)"><el-select v-model="projectForm.projectType" :disabled="!!projectForm.projectId && !isBoss"><el-option v-for="(label,key) in typeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;核算方式&quot;)"><el-select v-model="projectForm.accountingMode" :disabled="!!projectForm.projectId && !isBoss"><el-option v-for="(label,key) in accountingLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col></el-row>
        <el-form-item :label="$tr(&quot;管理模式&quot;)"><el-select :disabled="projectFormFrozen" v-model="projectForm.managementMode" style="width:100%"><el-option :label="$tr(&quot;轻量 · 核心执行，异常驱动&quot;)" value="LIGHT"/><el-option :label="$tr(&quot;标准 · 周度跟踪、里程碑、风险&quot;)" value="STANDARD"/><el-option :label="$tr(&quot;重点监管 · 强化预警和变更管控&quot;)" value="KEY_CONTROL"/></el-select><small class="form-tip">{{ $tr("管理模式决定过程管控强度，不再代替结项方式。") }}</small></el-form-item>
        <el-form-item :label="$tr(&quot;结项方式&quot;)"><el-select :disabled="projectFormFrozen" v-model="projectForm.closeMethod" style="width:100%"><el-option :label="$tr(&quot;直接结项&quot;)" value="DIRECT"/><el-option :label="$tr(&quot;成果验收&quot;)" value="RESULT_ACCEPTANCE"/><el-option :label="$tr(&quot;阶段验收&quot;)" value="STAGED_ACCEPTANCE"/></el-select><small class="form-tip">{{ $tr("结项方式独立决定最终成果如何确认。") }}</small></el-form-item>
        <el-form-item :label="$tr(&quot;目标模式&quot;)"><el-select v-model="projectForm.goalMode" style="width:100%"><el-option :label="$tr(&quot;有项目总目标&quot;)" value="TOTAL"/><el-option :label="$tr(&quot;不计入公司总目标（持续经营）&quot;)" value="NO_TOTAL"/></el-select><small class="form-tip">{{ $tr("持续经营项目不要求负责人填写项目完成百分比，仍保留每日目标、任务、收入、成本和KPI。") }}</small></el-form-item>
        <el-form-item v-if="projectForm.managementMode==='KEY_CONTROL'" :label="$tr(&quot;监管原因&quot;)" required><el-input v-model="projectForm.managementReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item v-if="projectForm.closeMethod!=='DIRECT'" :label="$tr(&quot;验收标准&quot;)" required><el-input :disabled="projectFormFrozen" v-model="projectForm.acceptanceCriteria" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item v-if="governanceChanged" :label="$tr(&quot;变更原因&quot;)" required><el-input v-model="projectForm.governanceChangeReason" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="$tr(&quot;说明为何调整管理模式或结项方式&quot;)" /></el-form-item>
        <el-form-item v-if="goalModeChanged" :label="$tr(&quot;目标变更原因&quot;)" required><el-input v-model="projectForm.goalModeChangeReason" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="$tr(&quot;说明为何调整项目目标模式&quot;)" /></el-form-item>
        <el-form-item :label="$tr(&quot;负责人&quot;)" prop="mainOwnerUserId"><el-select v-if="!projectForm.projectId" v-model="projectForm.mainOwnerUserId" filterable style="width:100%"><el-option v-for="u in users" :key="u.userId" :label="u.nickName || u.userName" :value="u.userId" /></el-select><template v-else><el-input :model-value="projectForm.mainOwnerName" disabled/><small class="form-tip">{{ $tr("负责人变更请使用项目详情中的“更换主负责人”。") }}</small></template></el-form-item>
        <el-form-item :label="$tr(&quot;归属老板&quot;)"><el-input :model-value="projectForm.sponsorOwnerName || projectForm.initiatorName" disabled/><small class="form-tip">{{ $tr("主子项目沿用同一归属老板，遵循现有数据权限。") }}</small></el-form-item>
        <el-form-item :label="$tr(&quot;归属公司&quot;)" prop="companyDeptId"><el-select v-if="isBoss || !projectForm.projectId" v-model="projectForm.companyDeptId" :placeholder="$tr(&quot;选择归属公司&quot;)" style="width:100%"><el-option v-for="c in companies" :key="c.companyDeptId" :label="c.companyName" :value="c.companyDeptId" /></el-select><el-input v-else :model-value="projectForm.companyName || $tr(&quot;待老板设置&quot;)" disabled /></el-form-item>
        <el-form-item v-if="isBoss && projectForm.projectId" :label="$tr(&quot;执行系统&quot;)"><el-checkbox v-model="projectForm.executionSource" true-label="LIVE" false-label="">{{ $tr("关联直播数据管理") }}</el-checkbox><small class="form-tip">{{ $tr("只读取已确认的汇总结果，不开放直播原始明细或审核权限。") }}</small></el-form-item>
        <el-form-item v-if="projectForm.parentId" :label="$tr(&quot;归属主项目&quot;)"><el-input :model-value="projectForm.parentName" disabled /></el-form-item>
        <el-form-item :label="$tr(&quot;项目目标&quot;)" prop="objective"><el-input :disabled="projectFormFrozen" v-model="projectForm.objective" maxlength="1000" show-word-limit type="textarea" :rows="3" :placeholder="$tr(&quot;定义可验收的业务目标&quot;)" /></el-form-item>
        <el-form-item :label="$tr(&quot;计划周期&quot;)" required><div class="project-period-line"><el-date-picker :disabled="projectFormFrozen" v-model="projectForm.planStartDate" type="date" value-format="YYYY-MM-DD" :placeholder="$tr(&quot;开始日期&quot;)" style="width:100%" /><span>{{ $tr("至") }}</span><el-date-picker v-model="projectForm.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled="projectFormFrozen || projectOpenEnded" :disabled-date="disableProjectEndDate" :placeholder="projectOpenEnded ? $tr(&quot;不限期&quot;) : $tr(&quot;结束日期&quot;)" style="width:100%" /><el-checkbox :disabled="projectFormFrozen" v-model="projectOpenEnded" @change="handleProjectOpenEndedChange">{{ $tr("不限期") }}</el-checkbox></div></el-form-item>
        <el-row :gutter="16"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;预算上限&quot;)"><el-input-number v-model="projectForm.budgetLimit" :disabled="!!projectForm.projectId" :min="0" :precision="2" style="width:100%" /><small v-if="projectForm.projectId" class="form-tip">{{ $tr("请在“经营配置”中调整并填写原因") }}</small></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;币种&quot;)"><el-input v-model="projectForm.baseCurrency" :disabled="!!projectForm.projectId" maxlength="3" /></el-form-item></el-col></el-row>
        <el-form-item :label="$tr(&quot;备注&quot;)"><el-input v-model="projectForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="projectDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveProject">{{ $tr("保存") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="budgetDialog" :title="isDailyBudget?$tr(&quot;调整每日预算&quot;):$tr(&quot;调整项目预算&quot;)" width="min(520px, 94vw)" append-to-body><el-alert :title="$tr(&quot;预算调整会立即生效并永久保留历史记录。&quot;)" type="warning" :closable="false" show-icon/><el-form :model="budgetForm" label-width="100px" class="decision-form"><el-form-item :label="isDailyBudget?$tr(&quot;每日上限&quot;):$tr(&quot;总额上限&quot;)" required><el-input-number v-model="budgetForm.budgetLimit" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item :label="$tr(&quot;币种&quot;)" required><el-input v-model="budgetForm.currency" maxlength="3" /></el-form-item><el-form-item :label="$tr(&quot;调整原因&quot;)" required><el-input v-model="budgetForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="budgetDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveBudget">{{ $tr("确认调整") }}</el-button></template></el-dialog>

    <el-dialog v-model="kpiDialog" :title="kpiForm.kpiId?$tr(&quot;调整KPI目标&quot;):$tr(&quot;新增项目KPI&quot;)" width="min(680px, 94vw)" append-to-body><el-alert v-if="kpiForm.kpiId" :title="$tr(&quot;保存后会生成新的目标版本，原版本不会被覆盖。&quot;)" type="info" :closable="false" show-icon/><el-form :model="kpiForm" label-width="92px" class="decision-form"><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;系统编码&quot;)"><el-input v-model="kpiForm.kpiCode" disabled :placeholder="$tr(&quot;保存后自动生成&quot;)" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;KPI名称&quot;)" required><el-input v-model="kpiForm.kpiName" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;指标类型&quot;)"><el-select v-model="kpiForm.metricType" style="width:100%"><el-option v-for="(label,key) in metricTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;目标值&quot;)" required><el-input-number v-model="kpiForm.targetValue" :precision="4" style="width:100%" /></el-form-item></el-col></el-row><el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;单位&quot;)"><el-select :model-value="kpiForm.unit" @update:model-value="changeProjectKpiUnit" filterable allow-create default-first-option clearable :placeholder="$tr(&quot;选择或输入单位&quot;)" style="width:100%"><el-option v-for="unit in commonKpiUnits" :key="unit" :label="$tr(unit)" :value="unit" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;权重%&quot;)"><el-input-number v-model="kpiForm.weight" :min="0" :max="100" :precision="2" style="width:100%" /></el-form-item></el-col></el-row><el-form-item :label="$tr(&quot;指标负责人&quot;)"><el-select v-model="kpiForm.ownerUserId" clearable style="width:100%"><el-option v-for="m in detail?.members || []" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item :label="$tr(&quot;调整说明&quot;)"><el-input v-model="kpiForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="kpiDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveKpi">{{ $tr("保存KPI版本") }}</el-button></template></el-dialog>


    <el-dialog v-model="routineDialog" :title="routineForm.routineId?$tr(&quot;编辑持续工作&quot;):$tr(&quot;新增持续工作&quot;)" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="$tr(&quot;先选择目标方式。客户动态日目标由负责人每天在工作台下达，员工再填报完成情况。&quot;)" type="info" :closable="false" show-icon />
      <el-form :model="routineForm" label-width="96px" class="decision-form">
        <el-form-item :label="$tr(&quot;工作内容&quot;)" required><el-input v-model="routineForm.routineName" maxlength="200" :placeholder="$tr(&quot;例如：短视频剪辑发布&quot;)" /></el-form-item>
        <el-form-item :label="$tr(&quot;目标方式&quot;)" required><el-select v-model="routineForm.targetMode" style="width:100%" @change="handleRoutineTargetModeChange"><el-option :label="$tr(&quot;固定每日目标&quot;)" value="FIXED"/><el-option v-if="detail?.goalMode!=='NO_TOTAL'" :label="$tr(&quot;总目标自动分配到每日&quot;)" value="AUTO_TOTAL"/><el-option :label="$tr(&quot;客户动态日目标（负责人每日下达）&quot;)" value="DAILY_DYNAMIC"/><el-option :label="$tr(&quot;无量化目标（只填完成说明）&quot;)" value="NONE"/></el-select></el-form-item>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;目标频率&quot;)" required><el-select v-model="routineForm.frequency" disabled style="width:100%"><el-option :label="$tr(&quot;每日&quot;)" value="DAILY" /></el-select><small class="form-tip">{{ $tr("目标按天重复；执行区间只决定从哪天到哪天生效。") }}</small></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;负责人&quot;)" required><el-select v-model="routineForm.assigneeUserId" filterable style="width:100%"><el-option v-for="m in detail?.members || []" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col v-if="['FIXED','AUTO_TOTAL'].includes(routineForm.targetMode)" :sm="12" :xs="24"><el-form-item :label="routineForm.targetMode==='AUTO_TOTAL'?$tr(&quot;总目标&quot;):$tr(&quot;每日目标&quot;)" required><el-input-number v-model="routineForm.targetValue" :min="0.0001" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;单位&quot;)" :required="routineForm.targetMode!=='NONE'"><el-select :model-value="routineForm.unit" @update:model-value="changeRoutineUnit" filterable allow-create default-first-option :placeholder="$tr(&quot;选择或输入单位&quot;)" style="width:100%"><el-option v-if="routineForm.unit && !commonKpiUnits.includes(routineForm.unit)" :label="$tr(routineForm.unit)" :value="routineForm.unit"/><el-option v-for="unit in commonKpiUnits" :key="unit" :label="$tr(unit)" :value="unit" /></el-select></el-form-item></el-col></el-row>
        <el-form-item :label="$tr(&quot;执行区间&quot;)" required><div class="routine-period-line"><el-date-picker v-model="routineForm.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$tr(&quot;开始日期&quot;)" style="width:100%" /><span>{{ $tr("至") }}</span><el-date-picker v-model="routineForm.endDate" type="date" value-format="YYYY-MM-DD" :disabled="routineLongTerm" :disabled-date="disableRoutineEndDate" :placeholder="routineLongTerm ? $tr(&quot;长期&quot;) : $tr(&quot;结束日期&quot;)" style="width:100%" /><el-checkbox v-model="routineLongTerm" :disabled="routineForm.targetMode==='AUTO_TOTAL'" @change="handleRoutineLongTermChange">{{ $tr("长期") }}</el-checkbox></div></el-form-item>
        <el-form-item :label="$tr(&quot;说明&quot;)"><el-input v-model="routineForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="$tr(&quot;说明口径、质量要求或交付位置&quot;)" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="routineDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveRoutine">{{ $tr("保存持续工作") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="ownerDialog" :title="$tr(&quot;更换项目主负责人&quot;)" width="min(520px, 94vw)" append-to-body>
      <el-alert :title="$tr(&quot;可选择原负责人继续参与或退出项目；退出时将未完任务及持续工作交接给新负责人，保留交接历史和已计价成本。&quot;)" type="warning" :closable="false" show-icon />
      <el-form :model="ownerForm" label-width="100px" class="decision-form"><el-form-item :label="$tr(&quot;新负责人&quot;)" required><el-select v-model="ownerForm.ownerUserId" filterable style="width:100%"><el-option v-for="u in ownerCandidates" :key="u.userId" :label="userOptionLabel(u)" :value="u.userId" /></el-select></el-form-item><el-form-item :label="$tr(&quot;原负责人&quot;)"><el-radio-group v-model="ownerForm.exitOldOwner"><el-radio :value="false">{{ $tr("继续作为成员") }}</el-radio><el-radio :value="true">{{ $tr("退出项目，未完工作交接给新负责人") }}</el-radio></el-radio-group></el-form-item><el-form-item :label="$tr(&quot;变更原因&quot;)" required><el-input v-model="ownerForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="ownerDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveOwner">{{ $tr("确认更换") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="acceptanceDialog" :title="$tr(&quot;提交项目验收资料&quot;)" width="min(660px, 94vw)" append-to-body>
      <el-form :model="acceptanceForm" label-width="100px" class="decision-form"><el-form-item :label="$tr(&quot;结果摘要&quot;)" required><el-input v-model="acceptanceForm.resultSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit :placeholder="$tr(&quot;说明项目目标完成情况和最终结果&quot;)" /></el-form-item><el-form-item :label="$tr(&quot;交付成果&quot;)" required><el-input v-model="acceptanceForm.deliverables" type="textarea" :rows="5" maxlength="4000" show-word-limit :placeholder="$tr(&quot;逐项列出可验收的成果、文件或业务结果&quot;)" /></el-form-item><el-form-item :label="$tr(&quot;成果附件&quot;)"><business-file-upload v-model="acceptanceForm.attachmentUrls" :project-id="detail.projectId" /></el-form-item></el-form>
      <template #footer><el-button @click="acceptanceDialog=false">{{ $tr("取消") }}</el-button><el-button type="success" :loading="saving" @click="saveAcceptance">{{ $tr("提交{0}验收", [acceptanceReviewerLabel]) }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" :title="reviewForm.decision==='APPROVED'?$tr(&quot;验收通过、结项并冻结&quot;):$tr(&quot;退回项目继续执行&quot;)" width="min(560px, 94vw)" append-to-body>
      <el-alert :title="reviewForm.decision==='APPROVED'?$tr(&quot;系统将同时确认最终核算、结项并冻结项目数据；付款凭证和受控调账仍可登记。&quot;):$tr(&quot;退回后项目恢复执行，负责人可修改后再次提交。&quot;)" :type="reviewForm.decision==='APPROVED'?'success':'warning'" :closable="false" show-icon />
      <el-form :model="reviewForm" label-width="90px" class="decision-form"><el-form-item :label="$tr(&quot;验收意见&quot;)" :required="reviewForm.decision==='RETURNED'"><el-input v-model="reviewForm.comment" type="textarea" :rows="5" maxlength="2000" show-word-limit :placeholder="reviewForm.decision==='APPROVED'?$tr(&quot;可填写验收结论&quot;):$tr(&quot;必须说明退回原因和需要补充的内容&quot;)" /></el-form-item></el-form>
      <template #footer><el-button @click="reviewDialog=false">{{ $tr("取消") }}</el-button><el-button :type="reviewForm.decision==='APPROVED'?'success':'warning'" :loading="saving" @click="saveAcceptanceReview">{{ $tr("确认") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="stageDialog" :title="$tr(&quot;确认阶段成果 · {0}&quot;, [stageForm.milestoneName || ''])" width="min(660px,94vw)" append-to-body><el-form :model="stageForm" label-width="100px"><el-form-item :label="$tr(&quot;阶段结果&quot;)" required><el-input v-model="stageForm.resultSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item><el-form-item :label="$tr(&quot;交付成果&quot;)" required><el-input v-model="stageForm.deliverables" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item><el-form-item :label="$tr(&quot;成果附件&quot;)"><business-file-upload v-model="stageForm.attachmentUrls" :project-id="detail.projectId" /></el-form-item></el-form><template #footer><el-button @click="stageDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveStageAcceptance">{{ $tr("确认阶段完成") }}</el-button></template></el-dialog>
    <el-dialog v-model="stageReviewDialog" :title="stageReviewForm.decision==='APPROVED'?$tr(&quot;阶段验收通过&quot;):$tr(&quot;退回阶段成果&quot;)" width="min(540px,94vw)" append-to-body><el-form :model="stageReviewForm" label-width="90px"><el-form-item :label="$tr(&quot;验收意见&quot;)" :required="stageReviewForm.decision==='RETURNED'"><el-input v-model="stageReviewForm.comment" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="stageReviewDialog=false">{{ $tr("取消") }}</el-button><el-button :type="stageReviewForm.decision==='APPROVED'?'success':'warning'" :loading="saving" @click="saveStageReview">{{ $tr("确认") }}</el-button></template></el-dialog>

    <el-dialog v-model="itemDialog" :title="itemTitle" :width="itemKind==='member' && usesActualWork && !itemForm.memberId ? 'min(900px, 96vw)' : 'min(560px, 94vw)'" destroy-on-close>
      <el-form :model="itemForm" label-width="92px" :disabled="saving">
        <template v-if="itemKind === 'member'"><el-form-item v-if="itemForm.memberId" :label="$tr(&quot;人员&quot;)"><el-input :model-value="itemForm.userNameSnapshot" disabled /></el-form-item><template v-else><el-form-item :label="$tr(&quot;公司&quot;)" required><el-select v-model="itemForm.companyKey" filterable :placeholder="$tr(&quot;请先选择公司&quot;)" style="width:100%" @change="itemForm.userId=null"><el-option v-for="company in memberCompanyOptions" :key="company.key" :label="company.companyName" :value="company.key" /></el-select></el-form-item><el-form-item :label="$tr(&quot;人员&quot;)" required><el-select v-model="itemForm.userId" filterable :disabled="!itemForm.companyKey" :placeholder="$tr(&quot;输入姓名、账号或部门查询&quot;)" style="width:100%"><el-option v-for="u in memberUserOptions" :key="u.userId" :label="userOptionLabel(u)" :value="u.userId" /></el-select><small v-if="itemForm.companyKey&&!memberUserOptions.length" class="form-tip">{{ $tr("该公司暂无可添加人员。") }}</small></el-form-item></template><el-form-item :label="$tr(&quot;项目角色&quot;)"><el-select v-model="itemForm.memberRole"><el-option v-if="canManageDeputies" :label="$tr(&quot;副负责人&quot;)" value="DEPUTY"/><el-option :label="$tr(&quot;成员&quot;)" value="MEMBER"/><el-option :label="$tr(&quot;观察者&quot;)" value="OBSERVER"/></el-select><small v-if="!canManageDeputies" class="form-tip">{{ $tr("副负责人只能由主负责人或老板任命。") }}</small></el-form-item><el-form-item v-if="usesActualWork && !itemForm.memberId && itemForm.memberRole !== 'OBSERVER'" :label="$tr(&quot;投入比例&quot;)" required>
          <el-input-number v-model="itemForm.allocationPercent" :min="0.01" :max="100" :precision="2" :step="5" controls-position="right" /><span style="margin-left:8px">%</span>
          <small class="form-tip" style="width:100%">{{ $tr("设置该成员在本项目的投入比例。") }}</small>
          <el-button type="primary" plain :aria-expanded="memberAllocationVisible" aria-controls="member-allocation-details" style="margin-top:8px" @click="memberAllocationVisible=!memberAllocationVisible">{{ memberAllocationVisible ? $tr("收起其他项目投入") : $tr("查看和调整其他项目投入") }}</el-button>
        </el-form-item><BusinessMemberAllocationPlan v-if="usesActualWork && !itemForm.memberId && itemForm.memberRole !== 'OBSERVER'" v-show="memberAllocationVisible" id="member-allocation-details" ref="memberAllocationPanel" v-model="itemForm.allocationPercent" :project="detail" :user-id="itemForm.userId" :saving="saving" @save="saveItem" /><p v-if="usesActualWork && !itemForm.memberId && itemForm.memberRole === 'OBSERVER'" class="form-tip">{{ $tr("观察者不参与人员成本分摊，无需设置投入比例。") }}</p><p v-if="itemForm.memberId" class="form-tip">{{ $tr("角色调整从今天起生效；过去的工作日与已核算成本保留。修改历史金额请通过核算调整办理。") }}</p></template>
        <template v-else-if="itemKind === 'task'"><el-form-item :label="$tr(&quot;任务名称&quot;)"><el-input v-model="itemForm.taskName" /></el-form-item><el-form-item :label="$tr(&quot;负责人&quot;)"><el-select v-model="itemForm.assigneeUserId" clearable style="width:100%"><el-option v-for="m in detail.members" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item v-if="showMilestones" :label="$tr(&quot;所属里程碑&quot;)"><el-select v-model="itemForm.milestoneId" clearable style="width:100%"><el-option v-for="m in detail.milestones || []" :key="m.milestoneId" :label="m.milestoneName" :value="m.milestoneId" /></el-select></el-form-item><el-form-item :label="$tr(&quot;状态&quot;)"><el-input :model-value="taskStatusLabel[itemForm.status] || $tr(&quot;待开始&quot;)" disabled /></el-form-item><el-form-item :label="$tr(&quot;进度&quot;)"><el-slider v-model="itemForm.progress" show-input disabled /><small class="form-tip">{{ $tr("进度由任务负责人在“我的安排”中填报，项目负责人不可修改。") }}</small></el-form-item><el-form-item :label="$tr(&quot;截止日期&quot;)"><el-date-picker v-model="itemForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></template>
        <template v-else-if="itemKind === 'milestone'"><el-form-item :label="$tr(&quot;名称&quot;)"><el-input v-model="itemForm.milestoneName" /></el-form-item><el-form-item :label="$tr(&quot;计划日期&quot;)"><el-date-picker v-model="itemForm.planDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item :label="$tr(&quot;状态&quot;)"><el-select v-if="!['REVIEWING','DONE'].includes(itemForm.status)" v-model="itemForm.status"><el-option :label="$tr(&quot;未开始&quot;)" value="PENDING" /><el-option :label="$tr(&quot;进行中&quot;)" value="DOING" /></el-select><el-input v-else :model-value="milestoneStatusLabel[itemForm.status] || itemForm.status" disabled /><small class="form-tip">{{ $tr("负责人提交阶段结果和交付凭证后，必须由{0}检验通过才算完成。", [acceptanceReviewerLabel]) }}</small></el-form-item></template>
        <template v-else-if="itemKind === 'risk'"><el-form-item :label="$tr(&quot;风险标题&quot;)"><el-input v-model="itemForm.riskTitle" /></el-form-item><el-form-item :label="$tr(&quot;风险等级&quot;)"><el-select v-model="itemForm.severity"><el-option v-for="(label,key) in severityLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item :label="$tr(&quot;负责人&quot;)"><el-select v-model="itemForm.ownerUserId" clearable style="width:100%"><el-option v-for="m in detail.members" :key="m.userId" :label="memberOptionLabel(m)" :value="m.userId" /></el-select></el-form-item><el-form-item :label="$tr(&quot;处理期限&quot;)"><el-date-picker v-model="itemForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item :label="$tr(&quot;状态&quot;)"><el-select v-model="itemForm.status"><el-option v-for="(label,key) in riskStatusLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item :label="$tr(&quot;应对方案&quot;)"><el-input v-model="itemForm.responsePlan" type="textarea" :rows="3" /></el-form-item></template>
      </el-form>
      <template #footer><el-button @click="itemDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveItem">{{ $tr("保存") }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessProject">
import { translateText } from '@/locales/translate'

import ProjectPublicExpensePanel from '@/views/business/components/ProjectPublicExpensePanel.vue'
import { h, nextTick } from 'vue'
import ProjectHierarchyTable from './ProjectHierarchyTable.vue'
import ProjectDeletionReviews from './ProjectDeletionReviews.vue'
import { getBusinessProjectCompanies, getBusinessProjectDepartments } from '@/api/business/project'
import { useResizeObserver } from '@vueuse/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'
import BusinessProjectWorkPanel from '@/components/BusinessProjectWorkPanel/index.vue'
import BusinessProjectPlanPanel from '@/components/BusinessProjectPlanPanel/index.vue'
import BusinessSettlementPanel from '@/components/BusinessSettlementPanel/index.vue'
import { isSeparatedDelivery, isDeliveryEnded, projectAccountingState } from '@/utils/businessProjectState'
import BusinessProjectProgress from '@/components/BusinessProjectProgress/index.vue'
import { progressEventTarget } from '@/utils/projectProgress'
import useUserStore from '@/store/modules/user'
import { getProjectKpiWorkspace } from '@/api/business/kpi'
import { getBusinessProjectDashboard } from '@/api/business/accounting'
import { changeBusinessProjectOwner, enableBusinessRoutine, enableBusinessTask, getBusinessOperatingConfig, getBusinessProject, listBusinessUsers, removeBusinessMilestone, removeBusinessProjectMember, removeBusinessRisk, removeBusinessRoutine, removeBusinessStaffAllocation, removeBusinessTask, retireBusinessProjectKpi, reviewBusinessProjectAcceptance, reviewBusinessProjectStageAcceptance, saveBusinessMilestone, saveBusinessProjectKpi, saveBusinessProjectMember, saveBusinessRisk, saveBusinessRoutine, saveBusinessStaffAllocation, saveBusinessTask, submitBusinessProjectAcceptance, submitBusinessProjectStageAcceptance, transitionBusinessProject, updateBusinessProject, updateBusinessProjectBudget } from '@/api/business/project'
import { todayLocal } from '@/utils/businessDate'
import BusinessMemberAllocationPlan from '@/components/BusinessMemberAllocationPlan/index.vue'
import { milestoneTasksReady } from '@/utils/ownerTodos'

const route = useRoute(), router = useRouter(), userStore = useUserStore()
const progressPanel = ref(null)
async function handleProgressSubmitted() { await (detail.value ? refreshDetail() : load()) }
function clearProgressQuery(){const query={...route.query};delete query.progressProjectId;delete query.reportId;router.replace({query})}
watch(() => [route.query.progressProjectId, route.query.reportId], async ([id, reportId]) => { if(id){ await nextTick();progressPanel.value?.open({projectId:Number(id),reportId}) } }, {immediate:true})
const saving = ref(false), users = ref([]), companies=ref([]), departments=ref([])
const hierarchyTable = ref(null), appliedQuery = ref({}), projectBaseline = ref(null)
const deletionReviews = ref(null)
async function handleDeletionReviewed(row, decision) {
  if (decision === 'APPROVED') handleProjectDeleted(row)
  await hierarchyTable.value?.refresh()
}
const projectFormFrozen = computed(() => ['MEMBER_DAYS_V1','ACTUAL_WORK_V1'].includes(projectBaseline.value?.costPolicyVersion))
const detailVisible = ref(false), detail = ref(null), activeTab = ref('overview')
const detailTabs = ref(null)
const projectWorkPanel = ref(null)
// Keep enough space below the tab strip so a shorter pane cannot clamp the drawer's scrollTop.
useResizeObserver(() => detailTabs.value?.$el?.closest('.el-drawer__body'), ([entry]) => {
  const tabs = detailTabs.value?.$el
  if (!entry || !tabs) return
  const header = tabs.querySelector(':scope > .el-tabs__header')
  const headerHeight = header ? header.offsetHeight + (parseFloat(getComputedStyle(header).marginBottom) || 0) : 0
  tabs.style.setProperty('--project-tab-content-height', `${Math.max(0, entry.target.clientHeight - headerHeight)}px`)
})
const allProjectTasks=computed(()=>[...(detail.value?.tasks||[]),...(detail.value?.inactiveTasks||[])])
const allProjectRoutines=computed(()=>[...(detail.value?.routines||[]),...(detail.value?.retiredRoutines||[])])
const projectDialog = ref(false), projectFormRef = ref(), projectOpenEnded = ref(false), projectForm = ref({})
const itemDialog = ref(false), itemKind = ref(''), itemForm = ref({})
const memberAllocationPanel=ref(null),memberAllocationVisible=ref(false)
const ownerDialog=ref(false),ownerForm=reactive({ownerUserId:null,reason:'',exitOldOwner:false})
const acceptanceDialog=ref(false),acceptanceForm=reactive({resultSummary:'',deliverables:'',attachmentUrls:''})
const reviewDialog=ref(false),reviewForm=reactive({decision:'',comment:'',acceptanceId:null})
const stageDialog=ref(false),stageForm=reactive({milestoneId:null,milestoneName:'',resultSummary:'',deliverables:'',attachmentUrls:''})
const stageReviewDialog=ref(false),stageReviewForm=reactive({milestoneId:null,decision:'',comment:''})
const usesActualWork=computed(()=>detail.value?.costPolicyVersion==='MEMBER_DAYS_V1')
const operating=ref({kpis:[],budgetHistory:[],staffAllocations:[]}),budgetDialog=ref(false),budgetForm=reactive({budgetLimit:null,currency:'CNY',reason:''})
const kpiWorkspace=ref({plans:[]}),kpiWorkspaceLoading=ref(false),kpiWorkspaceError=ref(false)
const cockpit=ref({summary:{},results:[]}),cockpitLoading=ref(false),cockpitError=ref(false)
let cockpitRequest=0
const kpiDialog=ref(false),kpiForm=reactive({}),allocationDialog=ref(false),allocationForm=reactive({}),allocationDates=ref([]),allocationFollowProject=ref(true)
const routineDialog=ref(false),routineForm=reactive({}),routineLongTerm=ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', companyDeptId: '', mainOwnerDeptId: '', status: '', managementMode:'', closeMethod:'' })
const filterDepartments = computed(() => departments.value.filter(department => !query.companyDeptId || String(department.companyDeptId) === String(query.companyDeptId)))
const statusLabel = { DRAFT:translateText("草稿"), PLANNING:translateText("规划中"), ACTIVE:translateText("执行中"), PAUSED:translateText("已暂停"), ACCEPTANCE:translateText("待验收"), CLOSED:translateText("已关闭"), CANCELED:translateText("已取消") }
const statusTone = { DRAFT:'info', PLANNING:'warning', ACTIVE:'primary', PAUSED:'info', ACCEPTANCE:'success', CLOSED:'success', CANCELED:'danger' }
const typeLabel = { LIVE:translateText("直播"), JEWELRY:translateText("珠宝"), ECOMMERCE:translateText("电商"), OPERATIONS:translateText("运营"), INTERNAL:translateText("内部"), GENERAL:translateText("通用"), OTHER:translateText("其他") }
const accountingLabel = { PROFIT:translateText("利润项目"), COST:translateText("成本项目"), VALUE:translateText("价值项目"), HYBRID:translateText("混合核算") }
const managementModeLabel={LIGHT:translateText("轻量模式"),STANDARD:translateText("标准模式"),KEY_CONTROL:translateText("重点监管"),SIMPLE:translateText("轻量模式"),DELIVERY:translateText("标准模式")}
const closeMethodLabel={DIRECT:translateText("直接结项"),RESULT_ACCEPTANCE:translateText("成果验收"),STAGED_ACCEPTANCE:translateText("阶段验收")}
const goalModeLabel={TOTAL:translateText("有项目总目标"),NO_TOTAL:translateText("持续经营（不计入公司总目标）")}
const frequencyLabel={DAILY:translateText("每日"),WEEKLY:translateText("每周"),MONTHLY:translateText("每月")}
const routineTargetModeLabel={FIXED:translateText("固定每日目标"),AUTO_TOTAL:translateText("总目标自动分配"),DAILY_DYNAMIC:translateText("客户动态日目标"),NONE:translateText("无量化目标")}
const baselineLabel = { DRAFT:translateText("未提交"), SUBMITTED:translateText("待确认"), APPROVED:translateText("已确认") }
const taskStatusLabel = { TODO:translateText("待开始"), DOING:translateText("进行中"), BLOCKED:translateText("受阻"), DONE:translateText("已完成") }
const memberRoleLabel = { OWNER:translateText("主负责人"), DEPUTY:translateText("副负责人"), MEMBER:translateText("成员"), OBSERVER:translateText("观察者") }
const milestoneStatusLabel = { PENDING:translateText("未开始"), DOING:translateText("进行中"), REVIEWING:translateText("待阶段验收"), DONE:translateText("验收通过") }
const severityLabel = { LOW:translateText("低"), MEDIUM:translateText("中"), HIGH:translateText("高"), CRITICAL:translateText("严重") }
const riskStatusLabel = { OPEN:translateText("待处理"), MITIGATED:translateText("已缓解"), CLOSED:translateText("已关闭") }
const acceptanceReviewerLabel=computed(()=>detail.value?.governanceProfile?.acceptanceReviewerLabel||(detail.value?.parentId?translateText("主项目主负责人"):translateText("归属老板")))
const acceptanceLabel=computed(()=>({PENDING:translateText("待{0}验收", [acceptanceReviewerLabel.value]),APPROVED:translateText("{0}已通过", [acceptanceReviewerLabel.value]),RETURNED:translateText("{0}已退回", [acceptanceReviewerLabel.value])}))
const acceptanceTone={PENDING:'warning',APPROVED:'success',RETURNED:'danger'}
const eventLabel = {
  SUBPROJECT_PROGRESS:translateText("子项目进度汇报"),PROGRESS_WEIGHT:translateText("调整子项目进度权重"),
  CREATE:translateText("创建项目"),CREATE_FROM_PROPOSAL:translateText("立项批准并创建项目"),EDIT:translateText("更新项目资料"),GOVERNANCE_CHANGE:translateText("调整治理方式"),GOAL_MODE_CHANGE:translateText("调整目标模式"),
  SOURCE_LINK:translateText("关联执行系统"),SOURCE_UNLINK:translateText("解除执行系统"),OWNER_CHANGE:translateText("更换主负责人"),STATUS_CHANGE:translateText("变更项目状态"),
  START_PLANNING:translateText("进入规划"),SUBMIT_BASELINE:translateText("提交项目计划"),RETURN_PLAN:translateText("退回项目计划"),CONFIRM_BASELINE:translateText("确认计划并启动"),
  PAUSE:translateText("暂停项目"),RESUME:translateText("恢复项目"),REQUEST_ACCEPTANCE:translateText("提交成果验收"),REQUEST_CLOSE:translateText("发起项目结项"),
  REQUEST_STAGE_ACCEPTANCE:translateText("提交阶段验收"),APPROVE_STAGE:translateText("阶段验收通过"),RETURN_STAGE:translateText("退回阶段成果"),RETURN_ACTIVE:translateText("退回执行"),
  CLOSE:translateText("项目交付关闭"),CLOSE_AND_FREEZE:translateText("结项、核算并冻结"),ACCOUNTING_CLOSE:translateText("项目核算关闭"),CANCEL:translateText("取消项目"),MEMBER_SAVE:translateText("维护项目成员"),MEMBER_REMOVE:translateText("移除项目成员"),
  TASK_SAVE:translateText("安排/调整一次性任务"),TASK_PROGRESS:translateText("更新任务进度"),TASK_VOID:translateText("停用一次性任务"),TASK_ENABLE:translateText("启用一次性任务"),PROJECT_PROGRESS:translateText("填报项目进度"),
  ROUTINE_SAVE:translateText("安排/调整持续工作"),ROUTINE_VOID:translateText("停用持续工作"),ROUTINE_ENABLE:translateText("启用持续工作"),ROUTINE_REPORT:translateText("填报持续工作成果"),ROUTINE_TARGET_SAVE:translateText("下达/修改今日目标"),
  MILESTONE_SAVE:translateText("维护项目里程碑"),RISK_SAVE:translateText("维护风险台账"),BUDGET_CHANGE:translateText("调整项目预算"),
  KPI_CHANGE:translateText("调整KPI"),KPI_RETIRE:translateText("停用KPI"),KPI_PLAN_PUBLISHED:translateText("发布KPI与奖金方案"),KPI_PLAN_VOIDED:translateText("作废KPI与奖金方案"),
  KPI_SETTLEMENT_SUBMITTED:translateText("提交KPI结算"),KPI_SETTLEMENT_RETURNED:translateText("退回KPI结算"),KPI_SETTLEMENT_CONFIRMED:translateText("确认KPI结算"),
  COST_ALLOCATION:translateText("调整项目投入权重"),COST_ALLOCATION_VOID:translateText("停用项目投入权重"),
  EFFORT_WEEK_CONFIRMED:translateText("确认周投入"),EFFORT_DAY_CONFIRMED:translateText("确认当日投入"),EFFORT_DAY_RETURNED:translateText("退回当日投入"),
  STAFF_LEAVE_REQUESTED:translateText("提交成员请假"),STAFF_LEAVE_APPROVED:translateText("成员请假已批准"),STAFF_LEAVE_RETURNED:translateText("成员请假已退回"),
  STAFF_LEAVE_CANCEL_REQUESTED:translateText("申请撤销成员请假"),STAFF_LEAVE_CANCELED:translateText("成员请假已撤销")
}
const metricTypeLabel={COUNT:translateText("数量"),AMOUNT:translateText("金额"),PERCENT:translateText("百分比"),DURATION:translateText("时长"),SCORE:translateText("评分"),MILESTONE:translateText("里程碑")}
const commonKpiUnits=computed(()=>['个','件','项','次','单','条','人','户',...(detail.value?.baseCurrency==='CNY'?['元','万元']:[detail.value?.baseCurrency||'CNY']),'%','分','小时','天'])
const periodLabel={DAY:translateText("每日"),WEEK:translateText("每周"),MONTH:translateText("每月"),QUARTER:translateText("每季度"),PROJECT:translateText("整个项目")}
const costModeLabel={DAILY:translateText("日成本"),HOURLY:translateText("时成本"),MONTHLY:translateText("月成本"),FIXED_PROJECT:translateText("项目固定"),FIXED_TASK:translateText("任务固定"),VARIABLE:translateText("浮动成本")}
const allocationModeLabel={PERCENTAGE:translateText("比例分摊"),HOURS:translateText("确认工时"),ATTENDANCE:translateText("出勤天数"),FIXED_DAILY:translateText("固定日金额"),PER_TASK:translateText("按任务数")}
const isBoss = computed(() => userStore.roles.includes('admin') || userStore.permissions.includes('*:*:*') || (userStore.permissions.includes('business:boss:view') && (!detail.value || detail.value.governanceProfile?.companyManager === true)))
const isAdmin = computed(() => userStore.roles.includes('admin') || userStore.permissions.includes('*:*:*'))
const canReviewProjectDeletion = computed(() => isAdmin.value || userStore.permissions.includes('business:boss:view'))
const myRole = computed(() => detail.value?.members?.find(m => Number(m.userId) === Number(userStore.id))?.memberRole)
const canReviewAcceptance = computed(() => detail.value?.governanceProfile?.acceptanceReviewer === true)
const canManage = computed(() => !isDeliveryEnded(detail.value) && (isBoss.value || ['OWNER','DEPUTY'].includes(myRole.value)))
const canManageDeputies = computed(() => !isDeliveryEnded(detail.value) && (isBoss.value || myRole.value === 'OWNER'))
const canManageAllocation = computed(() => !isDeliveryEnded(detail.value) && (isAdmin.value || isBoss.value || myRole.value === 'OWNER'))
const canActAsProjectOwner=computed(()=>myRole.value==='OWNER'||(isBoss.value&&!detail.value?.parentId))
const canSubmitAcceptance=computed(()=>detail.value?.closeMethod==='RESULT_ACCEPTANCE'&&detail.value?.status==='ACTIVE'&&canActAsProjectOwner.value)
const showRisks=computed(()=>detail.value?.governanceProfile?.riskRequired??['STANDARD','KEY_CONTROL','DELIVERY'].includes(detail.value?.managementMode))
const showMilestones=computed(()=>Boolean(detail.value?.milestones?.length)||(detail.value?.governanceProfile?.enabledModules?.includes('MILESTONE')??(detail.value?.managementMode!=='LIGHT')))
const governanceChanged=computed(()=>projectBaseline.value&&((projectForm.value.managementMode||'STANDARD')!==(projectBaseline.value.managementMode||'STANDARD')||(projectForm.value.closeMethod||'DIRECT')!==(projectBaseline.value.closeMethod||'DIRECT')))
const goalModeChanged=computed(()=>projectBaseline.value&&(projectForm.value.goalMode||'TOTAL')!==(projectBaseline.value.goalMode||'TOTAL'))
const governanceTitle=computed(()=>`${managementModeLabel[detail.value?.managementMode] || detail.value?.managementMode} · ${closeMethodLabel[detail.value?.closeMethod] || detail.value?.closeMethod}`)
const governanceDescription=computed(()=>{const mode={LIGHT:translateText("保留任务、成本和KPI，风险按异常处理。"),STANDARD:translateText("执行周度跟踪、里程碑、风险台账和预算预警。"),KEY_CONTROL:translateText("执行强化里程碑、风险、预算分级预警和治理变更管控。")}[detail.value?.managementMode]||'';const reviewer=acceptanceReviewerLabel.value;const close={DIRECT:translateText("{0}核对前置条件后直接关闭。", [reviewer]),RESULT_ACCEPTANCE:translateText("负责人提交整体验收资料，{0}通过后关闭。", [reviewer]),STAGED_ACCEPTANCE:translateText("负责人逐里程碑提交验收，由{0}审核，全部通过后方可关闭。", [reviewer])}[detail.value?.closeMethod]||'';return `${mode}${close}${detail.value?.acceptanceCriteria?translateText(" 验收标准：{0}", [detail.value.acceptanceCriteria]):''}`})
const ownerCandidates=computed(()=>users.value.filter(user=>Number(user.userId)!==Number(detail.value?.mainOwnerUserId)))
const userCompanyKey=user=>user.companyDeptId?`id:${user.companyDeptId}`:user.companyName?`name:${user.companyName}`:''
const memberCompanyOptions=computed(()=>{const unique=new Map();for(const user of users.value){const key=userCompanyKey(user);if(key&&!unique.has(key))unique.set(key,{key,companyName:user.companyName||translateText("未命名公司")})}return [...unique.values()].sort((a,b)=>a.companyName.localeCompare(b.companyName,'zh-CN'))})
const memberUserOptions=computed(()=>{const memberIds=new Set((detail.value?.members||[]).map(member=>Number(member.userId)));return users.value.filter(user=>userCompanyKey(user)===itemForm.value.companyKey&&!memberIds.has(Number(user.userId)))})

const currentKpis = computed(() => (operating.value.kpis || []).filter(row=>row.status==='CURRENT'))
const retiredKpis = computed(() => (operating.value.kpis || []).filter(row=>row.status==='RETIRED'))
const yesterdayExpected=computed(()=>Number(operating.value.executionSummary?.expectedStreamerCount||0))
const yesterdaySubmitted=computed(()=>Number(operating.value.executionSummary?.submittedStreamerCount||0))
const yesterdayMissing=computed(()=>Math.max(yesterdayExpected.value-yesterdaySubmitted.value,0))
const yesterdayRate=computed(()=>yesterdayExpected.value?Math.round(yesterdaySubmitted.value*100/yesterdayExpected.value):0)
const cockpitSummary=computed(()=>cockpit.value.summary||{})
const cockpitCostIncomplete=computed(()=>Number(cockpit.value.pendingCostCount)>0||cockpit.value.hasUnpricedOrMissingWork===true||cockpitSummary.value.hasUnpricedOrMissingWork===true)
const cockpitCostNotice=computed(()=>Number(cockpit.value.pendingCostCount)>0
  ? translateText("本项目有 {0} 条已确认工作待计价。下列成本和经营结果仅包含已核算部分，部分人员成本仍未知。", [cockpit.value.pendingCostCount])
  : translateText("部分成员工作日尚缺有效成本或日历，请完善后查看完整核算结果。"))
const cockpitCurrency=computed(()=>operating.value.currency||detail.value?.baseCurrency||'CNY')
const isDailyBudget=computed(()=>(operating.value.budgetMode||detail.value?.budgetMode)==='DAILY')
const cockpitBudget=computed(()=>{if((operating.value.budgetMode||detail.value?.budgetMode)==='NONE')return null;const value=isDailyBudget.value?(operating.value.dailyBudgetLimit??detail.value?.dailyBudgetLimit):(operating.value.budgetLimit??detail.value?.budgetLimit);return value==null?null:Number(value)})
const projectBudgetTitle=computed(()=>operating.value.budgetMode==='DAILY'?translateText("每日预算上限"):operating.value.budgetMode==='NONE'?translateText("预算控制"):translateText("项目总额预算"))
const projectBudgetText=computed(()=>operating.value.budgetMode==='NONE'?translateText("暂不设置"):`${money(cockpitBudget.value)} ${operating.value.currency||detail.value.baseCurrency}${isDailyBudget.value?translateText(" / 日"):''}`)
const cockpitTotalCost=computed(()=>{const values=[cockpitSummary.value.businessCost,cockpitSummary.value.personnelCost,cockpitSummary.value.bonusCost,cockpitSummary.value.publicCost??0];return values.some(value=>value==null)?null:values.reduce((total,value)=>total+Number(value),0)})
const cockpitBudgetSpent=computed(()=>detail.value?.budget?(cockpit.value.budgetPeriodSpent??null):cockpitTotalCost.value)
const cockpitBudgetRemaining=computed(()=>cockpitCostIncomplete.value||cockpitBudget.value==null||cockpitBudgetSpent.value==null?null:cockpitBudget.value-cockpitBudgetSpent.value)
const budgetUsage=computed(()=>cockpitCostIncomplete.value||!cockpitBudget.value||cockpitBudgetSpent.value==null?null:Math.round(cockpitBudgetSpent.value*1000/cockpitBudget.value)/10)
const budgetTone=computed(()=>cockpitCostIncomplete.value?'is-warning':budgetUsage.value>=100?'is-danger':budgetUsage.value>=80?'is-warning':'')
const cockpitDateRange=computed(()=>translateText("{0} 至 {1}", [detail.value?.planStartDate||translateText("项目开始"), todayText()]))
const scheduleProgress=computed(()=>{if(!detail.value?.planStartDate||!detail.value?.planEndDate)return 0;const start=dateMs(detail.value.planStartDate),end=dateMs(detail.value.planEndDate),now=Math.min(Math.max(Date.now(),start),end);return end<=start?100:Math.round((now-start)*100/(end-start))})
const remainingDaysText=computed(()=>{if(!detail.value?.planEndDate)return translateText("不限期");const days=Math.ceil((dateMs(detail.value.planEndDate)-dayStart())/86400000);return days<0?translateText("逾期 {0} 天", [Math.abs(days)]):days===0?translateText("今天到期"):translateText("{0} 天", [days])})
const scheduleStatusText=computed(()=>detail.value?.goalMode==='NO_TOTAL'?translateText("不限期项目以每日成果持续跟踪"):scheduleProgress.value>projectProgress(detail.value)?translateText("进度落后时间计划 {0} 个百分点", [scheduleProgress.value-projectProgress(detail.value)]):translateText("当前进度不落后于时间计划"))
const openTaskCount=computed(()=>(detail.value?.tasks||[]).filter(item=>item.status!=='DONE').length)
const overdueTaskCount=computed(()=>(detail.value?.tasks||[]).filter(item=>item.status!=='DONE'&&item.dueDate&&item.dueDate<todayText()).length)
const openRiskCount=computed(()=>(detail.value?.risks||[]).filter(item=>item.status==='OPEN').length)
const cockpitPlan=computed(()=>kpiWorkspace.value?.selectedPlan||null)
const cockpitSettlement=computed(()=>cockpitPlan.value?.settlement||null)
const cockpitKpis=computed(()=>{const plan=cockpitPlan.value;if(!plan?.items?.length)return currentKpis.value;const results=plan.settlement?.results||[];return plan.items.map(item=>{const result=results.find(result=>Number(result.planItemId)===Number(item.itemId));return {...item,actualValue:result?.actualValue,dataStatus:result?.dataStatus,automatic:result?.automatic,resultNote:result?.resultNote}})})
const settlementStatusText=computed(()=>({DRAFT:translateText("填报中"),SUBMITTED:translateText("待确认"),RETURNED:translateText("已退回"),CONFIRMED:translateText("已确认")}[cockpitSettlement.value?.status]||cockpitSettlement.value?.status||translateText("未结算")))
const matchedTier=computed(()=>{const score=Number(cockpitSettlement.value?.totalScore);if(!Number.isFinite(score))return null;return (cockpitPlan.value?.tiers||[]).find(tier=>score>=Number(tier.minScore||0)&&(tier.maxScore===null||tier.maxScore===undefined||score<Number(tier.maxScore)))||null})
const showKpiClosureGuard=computed(()=>detail.value&&['ACTIVE','ACCEPTANCE','CLOSED'].includes(detail.value.status)&&(canReviewAcceptance.value||isBoss.value||myRole.value==='OWNER'))
const kpiClosureState=computed(()=>{
  if(kpiWorkspaceLoading.value)return {ready:null,tone:'info',label:translateText("检查中"),title:translateText("正在检查KPI结算状态"),description:translateText("系统正在核对该项目所有已发布方案，请稍候。"),actionLabel:translateText("进入KPI工作区"),actionType:'primary',planCount:0,confirmedCount:0,percentage:0,planId:null}
  if(kpiWorkspaceError.value)return {ready:null,tone:'warning',label:translateText("请核对"),title:translateText("暂时未能读取KPI结算状态"),description:translateText("请进入KPI工作区确认全部方案均已结算；最终结项仍由系统后台校验。"),actionLabel:translateText("进入KPI工作区"),actionType:'warning',planCount:0,confirmedCount:0,percentage:0,planId:null}
  const plans=kpiWorkspace.value?.plans||[],confirmed=plans.filter(plan=>plan.settlementStatus==='CONFIRMED'),pending=plans.filter(plan=>plan.settlementStatus!=='CONFIRMED')
  if(!plans.length)return {ready:false,tone:'danger',label:translateText("尚未满足"),title:translateText("尚未发布KPI与奖金方案"),description:translateText("结项前由负责人设置目标、发布方案并完成结算。"),actionLabel:(isBoss.value||myRole.value==='OWNER')?translateText("设置并发布KPI"):translateText("查看KPI要求"),actionType:'danger',planCount:0,confirmedCount:0,percentage:0,planId:null}
  if(!pending.length)return {ready:true,tone:'success',label:translateText("已满足"),title:translateText("所有KPI结算均已确认"),description:translateText("共 {0} 个已发布方案已完成确认，可以继续办理项目结项。", [plans.length]),actionLabel:translateText("查看已确认结算"),actionType:'success',planCount:plans.length,confirmedCount:confirmed.length,percentage:100,planId:plans[0]?.planId}
  const priority={SUBMITTED:0,RETURNED:1,DRAFT:2},focus=[...pending].sort((a,b)=>(priority[a.settlementStatus]??9)-(priority[b.settlementStatus]??9))[0]
  const period=translateText("方案 v{0}{1}", [focus.planVersion, focus.cycleStart&&focus.cycleEnd?translateText("（{0} 至 {1}）", [focus.cycleStart, focus.cycleEnd]):''])
  const cycleNotEnded=focus.settlementStatus==='DRAFT'&&focus.cycleEnd&&focus.cycleEnd>=todayText()
  const meta={
    SUBMITTED:{tone:'warning',label:translateText("历史待确认"),title:translateText("升级前KPI结算待处理"),description:translateText("{0} 是旧流程遗留记录，可由归属老板处理。", [period]),actionLabel:isBoss.value?translateText("处理历史结算"):translateText("查看结算"),actionType:'warning'},
    RETURNED:{tone:'danger',label:translateText("历史已退回"),title:translateText("KPI结算需要负责人修改"),description:translateText("{0} 是旧流程退回记录；负责人修正并重新提交后将直接确认。", [period]),actionLabel:myRole.value==='OWNER'?translateText("修改并确认"):translateText("查看退回内容"),actionType:'danger'},
    DRAFT:cycleNotEnded?{tone:'info',label:translateText("考核中"),title:translateText("KPI尚未全部达标，周期仍在进行"),description:translateText("{0} 全部指标提前达标可立即确认并办理结项；否则需等周期结束。", [period]),actionLabel:translateText("查看KPI进度"),actionType:'primary'}:{tone:'warning',label:translateText("待填报"),title:translateText("KPI结果尚未完成结算"),description:translateText("{0} 仍在填报中；负责人提交结果后系统立即确认并计入项目核算。", [period]),actionLabel:myRole.value==='OWNER'?translateText("填报并确认KPI"):translateText("查看填报进度"),actionType:'warning'}
  }[focus.settlementStatus]||{tone:'warning',label:translateText("未完成"),title:translateText("仍有KPI方案尚未结算"),description:translateText("{0} 尚未完成确认，处理完毕后才能办理结项。", [period]),actionLabel:translateText("进入KPI工作区"),actionType:'warning'}
  return {ready:false,...meta,planCount:plans.length,confirmedCount:confirmed.length,percentage:Math.round(confirmed.length*100/plans.length),planId:focus.planId}
})
const projectStatusLabel=row=>row?.status==='ACCEPTANCE'&&row?.closeMethod==='STAGED_ACCEPTANCE'?translateText("待结项"):statusLabel[row?.status]||row?.status
const showStageClosureGuard=computed(()=>detail.value?.closeMethod==='STAGED_ACCEPTANCE'&&['ACTIVE','ACCEPTANCE'].includes(detail.value.status)&&(canReviewAcceptance.value||myRole.value==='OWNER'))
const showDirectClosureGuard=computed(()=>detail.value?.closeMethod==='DIRECT'&&detail.value?.status==='ACCEPTANCE'&&(canReviewAcceptance.value||myRole.value==='OWNER'))
const stageClosureState=computed(()=>{
  const milestones=detail.value?.milestones||[],doneCount=milestones.filter(item=>item.status==='DONE').length,milestoneCount=milestones.length
  const base={doneCount,milestoneCount,percentage:milestoneCount?Math.round(doneCount*100/milestoneCount):0,canRequest:false,canReview:false}
  if(detail.value?.status==='ACCEPTANCE')return {...base,tone:'warning',label:translateText("待{0}检验", [acceptanceReviewerLabel.value]),title:translateText("负责人已提交项目结项申请"),description:canReviewAcceptance.value?translateText("请检验全部阶段成果，确认通过后项目才会结项。"):translateText("正在等待{0}检验，子项目负责人不能自行通过验收。", [acceptanceReviewerLabel.value]),canReview:canReviewAcceptance.value}
  if(!milestoneCount)return {...base,tone:'danger',label:translateText("尚未满足"),title:translateText("尚未设置项目里程碑"),description:translateText("阶段验收项目至少需要一个里程碑，全部里程碑验收通过后才能发起结项。")}
  if(doneCount<milestoneCount)return {...base,tone:'info',label:translateText("验收进行中"),title:translateText("仍有里程碑尚未验收通过"),description:translateText("还需完成 {0} 个里程碑验收；全部通过后，系统将开放“发起结项”。", [milestoneCount-doneCount])}
  const blockingRisks=(detail.value?.risks||[]).filter(item=>item.status==='OPEN'&&['HIGH','CRITICAL'].includes(item.severity))
  if(blockingRisks.length)return {...base,tone:'danger',label:translateText("存在阻塞"),title:translateText("仍有高风险或严重风险未关闭"),description:translateText("请先处理：{0}。", [blockingRisks.map(item=>item.riskTitle||translateText("未命名风险")).join('、')])}
  if(kpiClosureState.value.ready!==true)return {...base,tone:kpiClosureState.value.tone||'warning',label:translateText("等待KPI结算"),title:translateText("里程碑已全部验收，KPI结算尚未确认"),description:translateText("完成并确认全部KPI结算后，即可发起项目结项。")}
  return {...base,tone:'success',label:translateText("可以申请"),title:translateText("所有里程碑已完成，结项申请条件已满足"),description:myRole.value==='OWNER'?translateText("提交后由{0}检验，通过后项目才会结项。", [acceptanceReviewerLabel.value]):translateText("等待项目负责人提交结项申请。"),canRequest:myRole.value==='OWNER'}
})
const projectProgress = row => {
  const value = Number(row.progressPercent)
  if (Number.isFinite(value)) return Math.min(100, Math.max(0, Math.round(value)))
  return row.taskCount ? Math.round((row.completedTaskCount || 0) * 100 / row.taskCount) : 0
}
const isKpiBlockedCloseAction=action=>['CLOSE','REQUEST_CLOSE','SUBMIT_ACCEPTANCE'].includes(action.key)&&kpiClosureState.value.ready===false
const userOptionLabel = user => `${user.nickName || user.userName} · ${user.userName} · ${user.companyName || translateText("集团")}${user.deptName && user.deptName !== user.companyName ? ` / ${user.deptName}` : ''}`
const memberOptionLabel = member => `${member.userNameSnapshot}${member.accountName ? ` · ${member.accountName}` : ` · ID ${member.userId}`}`
const formatEventTime=value=>value?String(value).replace('T',' ').replace(/\.\d+$/,''):''
const eventActorName=event=>event?.operatorName||event?.operatorAccount||translateText("系统")
const eventActorAccount=event=>event?.operatorAccount||(eventActorName(event)==='系统'?'SYSTEM':translateText("未记录"))
const eventTone=event=>{
  if(['CLOSE','APPROVE_STAGE','CONFIRM_BASELINE','KPI_SETTLEMENT_CONFIRMED','STAFF_LEAVE_APPROVED'].includes(event?.eventType))return 'success'
  if(['CANCEL','RETURN_PLAN','RETURN_STAGE','KPI_SETTLEMENT_RETURNED','STAFF_LEAVE_RETURNED'].includes(event?.eventType))return 'danger'
  if(['PAUSE','REQUEST_ACCEPTANCE','REQUEST_CLOSE','REQUEST_STAGE_ACCEPTANCE','KPI_SETTLEMENT_SUBMITTED'].includes(event?.eventType))return 'warning'
  return 'primary'
}
const formatEventComment=event=>{
  const value=String(event?.comment||'').trim()
  if(progressEventTarget(event))return value.replace(/^\[子项目:\d+\]\[汇报:\d+\]\s*/, '')
  if(event?.eventType==='MEMBER_SAVE'&&/^.+?\s*\/\s*(OWNER|DEPUTY|MEMBER|OBSERVER)\s*$/.test(value))return ''
  if(event?.eventType==='MEMBER_REMOVE')return value.replace(/^移除账号ID\s*\d+\s*[；;]?\s*/, '')
  const slashParts=value.split(/\s*\/\s*/)
  if(event?.eventType==='ROUTINE_SAVE'&&slashParts.length>=3){
    const [name,frequency,...target]=slashParts
    return translateText("持续工作：{0}；目标频率：{1}；{2}：{3}", [name, frequencyLabel[frequency]||frequency, frequency==='DAILY'?translateText("每日目标"):translateText("历史周期目标"), target.join(' / ')])
  }
  if(event?.eventType==='TASK_PROGRESS'&&slashParts.length>=2)return translateText("一次性任务：{0}；当前进度：{1}", [slashParts[0], slashParts.slice(1).join(' / ')])
  if(event?.eventType==='PROJECT_PROGRESS'&&slashParts.length>=2)return translateText("项目进度：{0}；进展说明：{1}", [slashParts[0], slashParts.slice(1).join(' / ')])
  if(event?.eventType==='COST_ALLOCATION'&&slashParts.length>=3)return translateText("成员：{0}；投入方式：{1}；计划投入：{2}{3}", [slashParts[0], allocationModeLabel[slashParts[1]]||slashParts[1], slashParts.slice(2).join(' / '), slashParts[1]==='PERCENTAGE'&&!String(slashParts[2]).includes('%')?'%':''])
  if(event?.eventType==='EFFORT_DAY_CONFIRMED'&&slashParts.length>=3)return translateText("成员：{0}；日期：{1}；确认投入：{2}", [slashParts[0], slashParts[1], slashParts.slice(2).join(' / ')])
  if(event?.eventType==='EFFORT_DAY_RETURNED'&&slashParts.length>=3)return translateText("成员：{0}；日期：{1}；{2}", [slashParts[0], slashParts[1], slashParts.slice(2).join(' / ')])
  const fieldLabels={TASK_SAVE:translateText("一次性任务"),MILESTONE_SAVE:translateText("里程碑"),RISK_SAVE:translateText("风险事项"),KPI_CHANGE:'KPI',KPI_RETIRE:'KPI'}
  if(fieldLabels[event?.eventType]&&value)return `${fieldLabels[event.eventType]}：${value}`
  if(event?.eventType==='ROUTINE_REPORT'){
    const separator=value.indexOf('：')
    if(separator>0)return translateText("持续工作：{0}；本次完成：{1}", [value.slice(0,separator), value.slice(separator+1)])
  }
  return value
}
const memberSaveParts=event=>{
  const match=(event?.comment||'').match(/^(.+?)\s*\/\s*(OWNER|DEPUTY|MEMBER|OBSERVER)\s*$/)
  return match?{name:match[1].trim(),role:memberRoleLabel[match[2]]||match[2]}:null
}
const eventSummary=event=>{
  const actor=eventActorName(event)
  if(['TASK_SAVE','ROUTINE_SAVE'].includes(event?.eventType)&&(event?.subjectName||event?.subjectAccount)){
    const subject=event.subjectName||event.subjectAccount
    const subjectAccount=event.subjectAccount?translateText("（账号：{0}）", [event.subjectAccount]):translateText("（账号未记录）")
    const workType=event.eventType==='TASK_SAVE'?translateText("一次性任务"):translateText("持续工作")
    return translateText("{0} · 给 {1}{2} 安排了{3}", [actor, subject, subjectAccount, workType])
  }
  if(event?.eventType==='MEMBER_SAVE'){
    const member=memberSaveParts(event)
    return member?translateText("{0} · 将 {1} 设为{2}", [actor, member.name, member.role]):translateText("{0} · 维护项目成员", [actor])
  }
  if(event?.eventType==='MEMBER_REMOVE'){
    const subject=event?.subjectName||event?.subjectAccount
    return subject?translateText("{0} · 移除项目成员 {1}", [actor, subject]):translateText("{0} · 移除一名项目成员", [actor])
  }
  return `${actor} · ${eventLabel[event?.eventType]||translateText("执行项目操作")}`
}
const eventStatusChange=event=>event?.fromStatus&&event?.toStatus&&event.fromStatus!==event.toStatus&&statusLabel[event.fromStatus]&&statusLabel[event.toStatus]?`${statusLabel[event.fromStatus]} → ${statusLabel[event.toStatus]}`:''
const workStatusEventTypes=new Set(['TASK_VOID','TASK_ENABLE','ROUTINE_VOID','ROUTINE_ENABLE'])
const eventWorkAssignee=event=>{
  if(!workStatusEventTypes.has(event?.eventType))return ''
  const name=event?.subjectName||event?.subjectAccount
  if(!name)return translateText("未记录")
  return event?.subjectAccount&&event.subjectAccount!==name?translateText("{0}（账号：{1}）", [name, event.subjectAccount]):name
}
const projectRules = { mainOwnerUserId:[{required:true,message:translateText("请选择负责人"),trigger:'change'}], projectName:[{ required:true,message:translateText("请输入项目名称"),trigger:'blur' }],companyDeptId:[{required:true,message:translateText("请选择归属公司"),trigger:'change'}] }
const settlementPanelRef=ref(null)
const itemTitle = computed(() => ({ member:translateText("添加项目成员"), task:translateText("维护任务"), milestone:translateText("维护里程碑"), risk:translateText("维护风险") }[itemKind.value]))
const availableActions = computed(() => {
  if (!detail.value) return []
  const d = detail.value, actions = []
  if (isBoss.value && d.status === 'DRAFT') actions.push({key:'START_PLANNING',label:translateText("进入规划"),type:'primary'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'PLANNING' && d.baselineStatus !== 'SUBMITTED') actions.push({key:'SUBMIT_BASELINE',label:translateText("提交计划"),type:'warning'})
  if (isBoss.value && d.status === 'PLANNING' && d.baselineStatus === 'SUBMITTED') actions.push({key:'CONFIRM_BASELINE',label:translateText("确认并启动"),type:'success'},{key:'RETURN_PLAN',label:translateText("退回计划")})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'ACTIVE') actions.push({key:'PAUSE',label:translateText("暂停项目")})
  if (isBoss.value && !d.parentId && d.status === 'ACTIVE' && d.closeMethod === 'DIRECT') actions.push({key:'CLOSE',label:translateText("确认结项并冻结"),type:'success'})
  if (myRole.value === 'OWNER' && d.status === 'ACTIVE' && d.closeMethod === 'DIRECT') actions.push({key:'REQUEST_CLOSE',label:translateText("申请直接结项"),type:'success'})
  if (canActAsProjectOwner.value && d.status === 'ACTIVE' && d.closeMethod === 'RESULT_ACCEPTANCE') actions.push({key:'SUBMIT_ACCEPTANCE',label:translateText("提交成果验收"),type:'success'})
  if ((isBoss.value || myRole.value === 'OWNER') && d.status === 'PAUSED') actions.push({key:'RESUME',label:translateText("恢复执行"),type:'primary'})
  if (canReviewAcceptance.value && d.status === 'ACCEPTANCE' && d.closeMethod === 'RESULT_ACCEPTANCE') actions.push({key:'OPEN_ACCEPTANCE',label:translateText("查看验收资料"),type:'success'})
  if ((isBoss.value || myRole.value === 'OWNER') && !['CLOSED','CANCELED'].includes(d.status)) actions.push({key:'CANCEL',label:translateText("取消项目"),type:'danger'})
  return actions
})

async function load() { await hierarchyTable.value?.refresh() }
function search(){ appliedQuery.value={...query} }
function resetQuery(){ query.keyword=''; query.companyDeptId=''; query.mainOwnerDeptId=''; query.status=''; query.managementMode='';query.closeMethod='';search() }
async function openDetail(row){ const res=await getBusinessProject(row.projectId); detail.value=res.data;activeTab.value=route.query.tab||'overview'; detailVisible.value=true; router.replace({query:{...route.query,id:row.projectId}}); await Promise.all([loadOperatingConfig(),loadKpiClosureState(),loadCockpit()]) }
function openProjectAccountingEntry(action){if(!detail.value?.projectId)return;router.push({path:'/business/accounting',query:{action,projectId:detail.value.projectId}})}
async function refreshDetail(){ if(!detail.value)return; detail.value=(await getBusinessProject(detail.value.projectId)).data; await Promise.all([load(),loadOperatingConfig(),loadKpiClosureState(),loadCockpit()]) }
async function loadOperatingConfig(){if(!detail.value)return;operating.value=(await getBusinessOperatingConfig(detail.value.projectId)).data||{kpis:[],budgetHistory:[],staffAllocations:[]}}
async function loadCockpit(){if(!detail.value)return;const request=++cockpitRequest,projectId=detail.value.projectId;cockpitLoading.value=true;cockpitError.value=false;cockpit.value={summary:{},results:[]};try{const response=await getBusinessProjectDashboard(projectId,{dateFrom:'2000-01-01',dateTo:todayText()});if(request===cockpitRequest&&projectId===detail.value?.projectId)cockpit.value=response.data||{summary:{},results:[]}}catch{if(request===cockpitRequest){cockpit.value={summary:{},results:[]};cockpitError.value=true}}finally{if(request===cockpitRequest)cockpitLoading.value=false}}
async function loadKpiClosureState(){kpiWorkspace.value={plans:[]};kpiWorkspaceError.value=false;if(!detail.value)return;kpiWorkspaceLoading.value=true;try{kpiWorkspace.value=(await getProjectKpiWorkspace(detail.value.projectId)).data||{plans:[]}}catch{kpiWorkspaceError.value=true}finally{kpiWorkspaceLoading.value=false}}
function openKpiWorkspace(){const planId=kpiClosureState.value.planId;router.push({path:planId?'/projects/kpi-results':'/business/kpi-bonus',query:{projectId:detail.value.projectId,...(planId?{planId}:{})}})}
async function ensureUsers(){ if(!users.value.length) users.value=(await listBusinessUsers()).data||[] }
async function ensureCompanies(){if(!companies.value.length)companies.value=(await getBusinessProjectCompanies()).data||[]}
let filterOptionsRequest
function loadFilterOptions(){
  if (!filterOptionsRequest) filterOptionsRequest = Promise.all([
    ensureCompanies(),
    getBusinessProjectDepartments().then(res => { departments.value=res.data||[] })
  ]).catch(() => { filterOptionsRequest=undefined })
  return filterOptionsRequest
}
async function openProjectForm(row){
  if(!row?.projectId)return
  const [response] = await Promise.all([getBusinessProject(row.projectId),ensureUsers(),ensureCompanies()])
  const current=response.data
  const normalized={...current,managementMode:current.managementMode==='SIMPLE'?'LIGHT':current.managementMode==='DELIVERY'?'STANDARD':current.managementMode,closeMethod:current.closeMethod||(current.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT'),goalMode:current.goalMode||'TOTAL'}
  projectBaseline.value={...normalized}
  projectForm.value={...normalized,governanceChangeReason:'',goalModeChangeReason:''}
  projectOpenEnded.value=!!current.planStartDate&&!current.planEndDate
  projectDialog.value=true
}
let pendingChildParentId=null
function openSubprojectForm(parent){
  pendingChildParentId=parent.projectId
  router.push({path:'/business/project-proposals',query:{create:'1',parentProjectId:parent.projectId}})
}
async function saveProject(){
  if(saving.value)return
  if(!await projectFormRef.value.validate().catch(()=>false))return
  const form=projectForm.value
  if(!form.planStartDate)return ElMessage.warning(translateText("请选择计划开始日期"))
  if(!projectOpenEnded.value&&!form.planEndDate)return ElMessage.warning(translateText("请选择计划结束日期或勾选不限期"))
  if(form.planEndDate&&form.planStartDate>form.planEndDate)return ElMessage.warning(translateText("计划结束日期不能早于开始日期"))
  if(form.managementMode==='KEY_CONTROL'&&!form.managementReason?.trim())return ElMessage.warning(translateText("重点监管项目请填写监管原因"))
  if(form.closeMethod!=='DIRECT'&&!form.acceptanceCriteria?.trim())return ElMessage.warning(translateText("请填写验收标准"))
  if(governanceChanged.value&&!form.governanceChangeReason?.trim())return ElMessage.warning(translateText("请填写治理方式变更原因"))
  if(goalModeChanged.value&&!form.goalModeChangeReason?.trim())return ElMessage.warning(translateText("请填写目标模式变更原因"))
  const data={...form,planEndDate:projectOpenEnded.value?null:form.planEndDate}
  saving.value=true
  try {
    const result=await updateBusinessProject(data)
    projectDialog.value=false
    ElMessage.success(translateText("项目资料已保存"))
    try { await hierarchyTable.value?.updated(result.data) } catch { ElMessage.warning(translateText("保存已成功，列表刷新失败，请重新查询")) }
    if(detail.value?.projectId===result.data?.projectId)detail.value=result.data
  } finally { saving.value=false }
}
function handleProjectDeleted(row){if(detail.value?.projectId===row.projectId){detailVisible.value=false;detail.value=null;const {id,tab,...rest}=route.query;router.replace({query:rest})}}
function handleProjectOpenEndedChange(value){if(value)projectForm.value.planEndDate=null}
function disableProjectEndDate(date){return !!projectForm.value.planStartDate&&date.getTime()<new Date(`${projectForm.value.planStartDate}T00:00:00`).getTime()}
async function runTransition(action){ if(action.key==='SUBMIT_ACCEPTANCE')return openAcceptanceSubmit();if(action.key==='OPEN_ACCEPTANCE'){activeTab.value='acceptance';return}if(action.key==='CLOSE')return settlementPanelRef.value?.openClose();let comment='',pauseCostMode='KEEP'; if(action.key==='PAUSE'){try{await ElMessageBox.confirm(translateText("暂停期间是否继续保留人员占用？释放后从明天起停止新增工作日成本，已计价记录保留。"),translateText("暂停期间人员安排"),{confirmButtonText:translateText("保留人员，继续计费"),cancelButtonText:translateText("释放人员，停止计费"),distinguishCancelAndClose:true})}catch(choice){if(choice==='cancel')pauseCostMode='RELEASE';else return}} if(['RETURN_PLAN','RETURN_ACTIVE','PAUSE','CLOSE','CANCEL','REQUEST_CLOSE'].includes(action.key)){ const r=await ElMessageBox.prompt(action.key==='CLOSE'?translateText("请填写{0}结项确认说明。确认后系统将完成最终核算并冻结项目数据。", [acceptanceReviewerLabel.value]):action.key==='REQUEST_CLOSE'?translateText("请填写结项申请说明"):translateText("请输入“{0}”原因", [action.label]),translateText("状态确认"),{inputValidator:v=>!!v||translateText("必须填写说明")}); comment=r.value } else await ElMessageBox.confirm(translateText("确定执行“{0}”吗？", [action.label]),translateText("状态确认"),{type:'warning'}); await transitionBusinessProject(detail.value.projectId,{action:action.key,comment,pauseCostMode}); ElMessage.success(action.key==='REQUEST_CLOSE'?translateText("结项申请已提交，等待{0}检验", [acceptanceReviewerLabel.value]):action.key==='CLOSE'?translateText("项目已结项，核算已确认并冻结"):translateText("状态已更新")); await refreshDetail() }
async function openOwnerDialog(){await ensureUsers();Object.assign(ownerForm,{ownerUserId:null,reason:'',exitOldOwner:false});ownerDialog.value=true}
async function saveOwner(){if(!ownerForm.ownerUserId)return ElMessage.warning(translateText("请选择新负责人"));if(!ownerForm.reason?.trim())return ElMessage.warning(translateText("请填写变更原因"));saving.value=true;try{detail.value=(await changeBusinessProjectOwner(detail.value.projectId,ownerForm)).data;ownerDialog.value=false;ElMessage.success(translateText("主负责人已更换，交接历史已记录"));await load()}finally{saving.value=false}}
function openAcceptanceSubmit(){Object.assign(acceptanceForm,{resultSummary:'',deliverables:'',attachmentUrls:''});acceptanceDialog.value=true;activeTab.value='acceptance'}
async function saveAcceptance(){if(!acceptanceForm.resultSummary?.trim())return ElMessage.warning(translateText("请填写结果摘要"));if(!acceptanceForm.deliverables?.trim())return ElMessage.warning(translateText("请填写交付成果"));await ElMessageBox.confirm(translateText("确认提交成果验收资料给{0}检验吗？验收通过后项目才会结项。", [acceptanceReviewerLabel.value]),translateText("提交成果验收"),{type:'warning'});saving.value=true;try{detail.value=(await submitBusinessProjectAcceptance(detail.value.projectId,acceptanceForm)).data;acceptanceDialog.value=false;activeTab.value='acceptance';ElMessage.success(translateText("成果验收已提交，等待{0}检验", [acceptanceReviewerLabel.value]));await load()}finally{saving.value=false}}
function openAcceptanceReview(decision,record){Object.assign(reviewForm,{decision,comment:'',acceptanceId:record.acceptanceId});reviewDialog.value=true;activeTab.value='acceptance'}
async function saveAcceptanceReview(){if(reviewForm.decision==='RETURNED'&&!reviewForm.comment?.trim())return ElMessage.warning(translateText("请填写退回原因"));saving.value=true;try{detail.value=(await reviewBusinessProjectAcceptance(detail.value.projectId,reviewForm)).data;reviewDialog.value=false;activeTab.value='acceptance';ElMessage.success(reviewForm.decision==='APPROVED'?translateText("项目已验收结项，核算已确认并冻结"):translateText("项目已退回执行"));await load()}finally{saving.value=false}}
const stageRecords=milestoneId=>(detail.value?.stageAcceptances||[]).filter(row=>Number(row.milestoneId)===Number(milestoneId))
const milestoneName=milestoneId=>(detail.value?.milestones||[]).find(row=>Number(row.milestoneId)===Number(milestoneId))?.milestoneName||translateText("未关联")
const canSubmitStage=milestone=>detail.value?.status==='ACTIVE'&&canActAsProjectOwner.value&&!['DONE','REVIEWING'].includes(milestone.status)&&milestoneTasksReady(detail.value?.tasks,milestone.milestoneId)
function openStageSubmit(milestone){Object.assign(stageForm,{milestoneId:milestone.milestoneId,milestoneName:milestone.milestoneName,resultSummary:'',deliverables:'',attachmentUrls:''});stageDialog.value=true;activeTab.value='stageAcceptance'}
async function saveStageAcceptance(){if(!stageForm.resultSummary?.trim())return ElMessage.warning(translateText("请填写阶段结果"));if(!stageForm.deliverables?.trim())return ElMessage.warning(translateText("请填写阶段交付成果"));saving.value=true;try{await submitBusinessProjectStageAcceptance(detail.value.projectId,stageForm);stageDialog.value=false;activeTab.value='stageAcceptance';await refreshDetail();ElMessage.success(translateText("阶段成果已提交，等待{0}检验", [acceptanceReviewerLabel.value]))}finally{saving.value=false}}
function openStageReview(decision,record){Object.assign(stageReviewForm,{milestoneId:record.milestoneId,decision,comment:''});stageReviewDialog.value=true;activeTab.value='stageAcceptance'}
async function saveStageReview(){if(stageReviewForm.decision==='RETURNED'&&!stageReviewForm.comment?.trim())return ElMessage.warning(translateText("请填写退回原因"));saving.value=true;try{await reviewBusinessProjectStageAcceptance(detail.value.projectId,stageReviewForm.milestoneId,stageReviewForm);stageReviewDialog.value=false;activeTab.value='stageAcceptance';await refreshDetail();ElMessage.success(stageReviewForm.decision==='APPROVED'?translateText("阶段验收已通过"):translateText("阶段成果已退回"))}finally{saving.value=false}}
async function openItem(kind,row={}){ await ensureUsers(); itemKind.value=kind; memberAllocationVisible.value=false; const defaults={ member:{companyKey:null,userId:null,memberRole:'MEMBER',allocationPercent:100}, task:{status:'TODO',progress:0,priority:'MEDIUM'}, milestone:{status:'PENDING'}, risk:{riskType:'GENERAL',severity:'MEDIUM',probability:'MEDIUM',status:'OPEN'} }; itemForm.value={...defaults[kind],...row,projectId:detail.value.projectId}; itemDialog.value=true }
async function saveItem(){
  if(saving.value)return
  const isMember=itemKind.value==='member'
  const setsAllocation=isMember&&usesActualWork.value&&!itemForm.value.memberId&&itemForm.value.memberRole!=='OBSERVER'
  if(isMember&&!itemForm.value.memberId&&!itemForm.value.companyKey)return ElMessage.warning(translateText("请先选择公司"))
  if(isMember&&!itemForm.value.userId)return ElMessage.warning(translateText("请选择人员"))
  if(setsAllocation){
    const percent=itemForm.value.allocationPercent
    if(percent===null||percent===undefined)return ElMessage.warning(translateText("请填写成员投入比例"))
    if(!Number.isFinite(Number(percent))||Number(percent)<=0||Number(percent)>100)return ElMessage.warning(translateText("成员投入比例必须大于0且不超过100%"))
    if(!memberAllocationPanel.value?.validate()){memberAllocationVisible.value=true;return}
  }
  const api={member:saveBusinessProjectMember,task:saveBusinessTask,milestone:saveBusinessMilestone,risk:saveBusinessRisk}[itemKind.value]
  const payload={...itemForm.value}
  if(isMember){
    delete payload.companyKey
    if(setsAllocation)payload.allocationPlan=memberAllocationPanel.value.getPlan()
    else{delete payload.allocationPercent;delete payload.allocationPlan}
  }
  if(itemKind.value==='milestone')delete payload.weight
  saving.value=true
  try{
    const response=await api(payload)
    itemDialog.value=false
    await refreshDetail()
    if(setsAllocation)await projectWorkPanel.value?.reload()
    ElMessage.success(setsAllocation?(response.data?.allocationOutcome==='PENDING'?translateText("成员已加入，投入调整已提交相关负责人确认"):translateText("成员及投入比例已保存")):translateText("保存成功"))
  }finally{saving.value=false}
}
const removalItemNames=items=>{
  const names=items.map(item=>item.taskName||item.routineName).filter(Boolean)
  if(!names.length)return ''
  return names.length>3?translateText("{0} 等 {1} 项", [names.slice(0,3).join('、'), names.length]):names.join('、')
}
function memberRemovalImpact(row){
  const userId=Number(row.userId)
  const tasks=(detail.value?.tasks||[]).filter(item=>Number(item.assigneeUserId)===userId&&item.status!=='DONE')
  const routines=(detail.value?.routines||[]).filter(item=>Number(item.assigneeUserId)===userId&&item.status==='ACTIVE'&&!item.sourceManaged)
  return {tasks,routines}
}
async function confirmMemberRemoval(row){
  const name=row.userNameSnapshot||row.userName||translateText("账号ID {0}", [row.userId])
  const impact=memberRemovalImpact(row)
  if(!impact.tasks.length&&!impact.routines.length){
    await ElMessageBox.confirm(translateText("确定将“{0}”移出项目吗？该成员当前没有未完成的一次性任务或持续工作。", [name]),translateText("移除项目成员"),{type:'warning',confirmButtonText:translateText("确认移除"),cancelButtonText:translateText("取消")})
    return impact
  }
  const content=[
    h('p',{style:'margin:0 0 12px;line-height:1.7'},translateText("“{0}”仍负责以下未完成工作。确认移除后，系统将同步解除相关安排：", [name]))
  ]
  if(impact.tasks.length)content.push(h('div',{style:'margin:8px 0;padding:10px 12px;border-radius:6px;background:#fff7e8;line-height:1.65'},[
    h('b',null,translateText("一次性任务 {0} 项", [impact.tasks.length])),
    h('span',null,`：${removalItemNames(impact.tasks)}`),
    h('small',{style:'display:block;color:#8a6d3b'},translateText("将解除负责人，任务本身保留，等待重新分配。"))
  ]))
  if(impact.routines.length)content.push(h('div',{style:'margin:8px 0;padding:10px 12px;border-radius:6px;background:#fff7e8;line-height:1.65'},[
    h('b',null,translateText("持续工作 {0} 项", [impact.routines.length])),
    h('span',null,`：${removalItemNames(impact.routines)}`),
    h('small',{style:'display:block;color:#8a6d3b'},translateText("将解除负责人，持续工作及历史填报保留，等待重新分配。"))
  ]))
  content.push(h('p',{style:'margin:12px 0 0;color:#d7474f;font-weight:600'},translateText("请确认这些工作已准备重新安排负责人。")))
  await ElMessageBox.confirm(h('div',null,content),translateText("移除成员并解除工作分配"),{type:'warning',confirmButtonText:translateText("确认移除并解除"),cancelButtonText:translateText("取消")})
  return impact
}
async function chooseMemberRemovalCost(){
  try{
    await ElMessageBox.confirm(translateText("请选择移除当天的人员成本处理方式。若员工今天没有参与项目，选择“今天不计成本”；若员工今天已经工作，选择“保留今天成本”。移除前已发生的成本仍保留。"),translateText("移除当日成本"),{type:'warning',confirmButtonText:translateText("今天不计成本"),cancelButtonText:translateText("保留今天成本"),distinguishCancelAndClose:true,closeOnClickModal:false})
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
  else if(kind==='task')await ElMessageBox.confirm(translateText("确定停用“{0}”吗？任务资料、填报和本次执行区间都会保留。", [row.taskName]),translateText("停用一次性任务"),{type:'warning'})
  else await ElMessageBox.confirm(translateText("确定删除这条记录吗？"),translateText("确认删除"),{type:'warning'})
  const calls={member:()=>removeBusinessProjectMember(detail.value.projectId,row.userId,memberImpact?.retainTodayCost),task:()=>removeBusinessTask(detail.value.projectId,row.taskId),milestone:()=>removeBusinessMilestone(detail.value.projectId,row.milestoneId),risk:()=>removeBusinessRisk(detail.value.projectId,row.riskId)}
  await calls[kind]()
  if(kind==='member'){
    const affected=(memberImpact?.tasks.length||0)+(memberImpact?.routines.length||0)
    ElMessage.success(affected?translateText("成员已移除，已同步处理 {0} 项未完成工作", [affected]):translateText("成员已移除"))
  }else ElMessage.success(kind==='task'?translateText("任务已停用，历史记录已保留"):translateText("删除成功"))
  await refreshDetail()
  if(kind==='member')await projectWorkPanel.value?.reload()
}
const money=value=>value===null||value===undefined?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:4})
const signedMoney=value=>value==null?'—':`${Number(value)>0?'+':''}${money(value)}`
const todayText=()=>todayLocal()
const dateMs=value=>new Date(`${value}T00:00:00`).getTime()
const dayStart=()=>dateMs(todayText())
const automaticKpiSources=['REVENUE','BUSINESS_COST','PERSONNEL_COST','PROFIT','ROUTINE','TASK','MILESTONE']
const kpiHasActual=item=>item.actualValue!==null&&item.actualValue!==undefined
const kpiActual=item=>Number(item.actualValue||0)
const kpiCompletion=item=>{const target=Number(item.targetValue||0),actual=kpiActual(item);if(!target)return 0;return Math.max(0,Math.min(100,Math.round((item.direction==='LOWER_BETTER'?target/Math.max(actual,target):actual/target)*100)))}
const kpiPendingText=item=>item.dataStatus==='PENDING_COST'?translateText("待成本计价"):item.automatic||automaticKpiSources.includes(item.sourceType)?translateText("自动统计中"):translateText("待填报")
const kpiActualText=item=>kpiHasActual(item)?`${item.actualValue} ${translateText(item.unit)||''}`:kpiPendingText(item)
const kpiGapText=item=>{if(!kpiHasActual(item))return translateText("距离目标：{0}", [kpiPendingText(item)]);const gap=Number(item.targetValue||0)-Number(item.actualValue||0);if(item.direction==='LOWER_BETTER')return gap>=0?translateText("已达到目标"):translateText("超过目标上限 {0} {1}", [money(Math.abs(gap)), translateText(item.unit)||'']);return gap<=0?translateText("已达到目标"):translateText("距离目标还差 {0} {1}", [money(gap), translateText(item.unit)||''])}
const number=value=>Number(value||0).toLocaleString('zh-CN')
function openBudgetDialog(){Object.assign(budgetForm,{budgetLimit:Number(isDailyBudget.value?operating.value.dailyBudgetLimit||0:operating.value.budgetLimit||0),currency:operating.value.currency||detail.value.baseCurrency||'CNY',reason:''});budgetDialog.value=true}
async function saveBudget(){if(budgetForm.budgetLimit===null)return ElMessage.warning(translateText("请填写预算金额"));if(!budgetForm.reason?.trim())return ElMessage.warning(translateText("请填写调整原因"));saving.value=true;try{detail.value=(await updateBusinessProjectBudget(detail.value.projectId,budgetForm)).data;budgetDialog.value=false;await Promise.all([loadOperatingConfig(),loadCockpit(),load()]);ElMessage.success(translateText("预算已调整，历史记录已保存"))}finally{saving.value=false}}
function openKpiDialog(row={}){Object.assign(kpiForm,{kpiId:null,projectId:detail.value.projectId,kpiCode:'',kpiName:'',metricType:'COUNT',targetValue:null,actualValue:null,unit:'',weight:0,direction:'HIGHER_BETTER',aggregateType:'SUM',sourceType:'MANUAL',ownerUserId:null,remark:'',...row});if(detail.value?.baseCurrency==='CNY'&&kpiForm.unit==='CNY')kpiForm.unit='元';kpiDialog.value=true}
function changeProjectUnit(form,unit){const cny=detail.value?.baseCurrency==='CNY';if(cny&&unit==='CNY')unit='元';const previous=form.unit;if(previous===unit)return;const factor=value=>value==='万元'?10000:['元','CNY'].includes(value)?1:null;const from=cny?factor(previous):null,to=cny?factor(unit):null;if(form.targetValue!=null&&Number(form.targetValue)!==0){const converted=from&&to?Number(form.targetValue)*from/to:null;if(converted!=null&&Number.isFinite(converted)&&converted>0&&converted<=99999999999999.99&&Math.abs(converted-Number(converted.toFixed(4)))<1e-9)form.targetValue=Number(converted.toFixed(4));else{form.targetValue=null;ElMessage.info(translateText("单位已变化，请按新单位重新填写目标值"))}}form.unit=unit}
function changeProjectKpiUnit(unit){changeProjectUnit(kpiForm,unit);if(['元','万元','CNY','USD','VND'].includes(unit))kpiForm.metricType='AMOUNT'}
function changeRoutineUnit(unit){changeProjectUnit(routineForm,unit)}
async function saveKpi(){if(!kpiForm.kpiName?.trim())return ElMessage.warning(translateText("请填写KPI名称"));if(kpiForm.targetValue===null||kpiForm.targetValue===undefined)return ElMessage.warning(translateText("请填写目标值"));saving.value=true;try{await saveBusinessProjectKpi({...kpiForm,projectId:detail.value.projectId});kpiDialog.value=false;await refreshDetail();ElMessage.success(kpiForm.kpiId?translateText("KPI新版本已生效"):translateText("KPI已创建，编码已自动生成"))}finally{saving.value=false}}
async function retireKpi(row){await ElMessageBox.confirm(translateText("确定停用“{0}”吗？历史版本仍会保留。", [row.kpiName]),translateText("停用KPI"),{type:'warning'});await retireBusinessProjectKpi(detail.value.projectId,row.kpiId);await refreshDetail();ElMessage.success(translateText("KPI已停用"))}
function projectAllocationPeriod(){return [detail.value?.planStartDate||todayText(),detail.value?.planEndDate||null]}
function syncAllocationProjectPeriod(follow=allocationFollowProject.value){if(follow)allocationDates.value=projectAllocationPeriod()}
function openAllocationDialog(row={}){Object.assign(allocationForm,{allocationId:null,projectId:detail.value.projectId,userId:null,allocationMode:'PERCENTAGE',allocationValue:100,version:null,remark:'',...row});const projectPeriod=projectAllocationPeriod();allocationFollowProject.value=!row.allocationId||(row.effectiveFrom===projectPeriod[0]&&(row.effectiveTo||null)===projectPeriod[1]);allocationDates.value=allocationFollowProject.value?projectPeriod:(row.effectiveFrom?[row.effectiveFrom,row.effectiveTo||null]:[]);allocationDialog.value=true}
async function saveAllocation(){if(!allocationForm.userId)return ElMessage.warning(translateText("请选择项目人员"));if(allocationForm.allocationValue===null||allocationForm.allocationValue===undefined)return ElMessage.warning(translateText("请填写计划投入"));if(Number(allocationForm.allocationValue)>100)return ElMessage.warning(translateText("项目投入比例不能超过100%"));if(allocationFollowProject.value)syncAllocationProjectPeriod(true);const from=allocationDates.value?.[0]||todayText(),to=allocationDates.value?.[1]||null;saving.value=true;try{await saveBusinessStaffAllocation({...allocationForm,allocationMode:'PERCENTAGE',exceptionAllowed:'0',exceptionReason:'',projectId:detail.value.projectId,effectiveFrom:from,effectiveTo:to});allocationDialog.value=false;await refreshDetail();ElMessage.success(translateText("成员计划投入已保存"))}finally{saving.value=false}}
async function removeAllocation(row){await ElMessageBox.confirm(translateText("确定停用这条人员成本分摊吗？历史核算数据不会删除。"),translateText("停用分摊"),{type:'warning'});await removeBusinessStaffAllocation(detail.value.projectId,row.allocationId);await refreshDetail();ElMessage.success(translateText("分摊已停用"))}
function openRoutine(row={}){Object.assign(routineForm,{routineId:null,projectId:detail.value.projectId,routineName:'',frequency:'DAILY',targetMode:'FIXED',targetValue:null,unit:'条',assigneeUserId:detail.value.mainOwnerUserId,startDate:detail.value.planStartDate||todayText(),endDate:detail.value.planEndDate||null,remark:'',version:null,...row,targetMode:row.targetMode||'FIXED',frequency:'DAILY',evidenceRequired:'0'});if(detail.value?.baseCurrency==='CNY'&&routineForm.unit==='CNY')routineForm.unit='元';routineLongTerm.value=!!routineForm.startDate&&!routineForm.endDate;routineDialog.value=true}
async function saveRoutine(){if(!routineForm.routineName?.trim())return ElMessage.warning(translateText("请填写持续工作内容"));if(!routineForm.assigneeUserId)return ElMessage.warning(translateText("请选择负责人"));if(['FIXED','AUTO_TOTAL'].includes(routineForm.targetMode)&&!(Number(routineForm.targetValue)>0))return ElMessage.warning(translateText("目标数量必须大于0"));if(routineForm.targetMode!=='NONE'&&!routineForm.unit?.trim())return ElMessage.warning(translateText("请填写目标单位"));if(!routineForm.startDate)return ElMessage.warning(translateText("请选择执行开始日期"));if(routineForm.targetMode==='AUTO_TOTAL'&&routineLongTerm.value)return ElMessage.warning(translateText("总目标自动分配必须设置结束日期"));if(!routineLongTerm.value&&!routineForm.endDate)return ElMessage.warning(translateText("请选择执行结束日期或勾选长期"));if(routineForm.endDate&&routineForm.startDate>routineForm.endDate)return ElMessage.warning(translateText("执行结束日期不能早于开始日期"));saving.value=true;try{await saveBusinessRoutine({...routineForm,frequency:'DAILY',targetValue:['FIXED','AUTO_TOTAL'].includes(routineForm.targetMode)?routineForm.targetValue:0,unit:routineForm.unit||'项',projectId:detail.value.projectId,endDate:routineLongTerm.value?null:routineForm.endDate});routineDialog.value=false;activeTab.value='routines';await refreshDetail();ElMessage.success(translateText("持续工作已保存"))}finally{saving.value=false}}
function handleRoutineTargetModeChange(mode){routineForm.frequency='DAILY';if(!['FIXED','AUTO_TOTAL'].includes(mode))routineForm.targetValue=0;if(mode==='AUTO_TOTAL'&&routineLongTerm.value)routineLongTerm.value=false}
function routineTargetText(row){const mode=row.targetMode||'FIXED';if(mode==='NONE')return translateText("只填每日完成说明");if(mode==='DAILY_DYNAMIC')return row.todayTarget?translateText("今日 {0} {1}", [row.todayTarget, translateText(row.unit)]):translateText("由负责人每日下达");if(mode==='AUTO_TOTAL')return translateText("总量 {0} {1}，按剩余天数自动分配", [row.targetValue, translateText(row.unit)]);return translateText("每日 {0} {1}", [row.targetValue, translateText(row.unit)])}
function handleRoutineLongTermChange(value){if(value)routineForm.endDate=null}
function disableRoutineEndDate(date){return !!routineForm.startDate&&date.getTime()<new Date(`${routineForm.startDate}T00:00:00`).getTime()}
async function removeRoutine(row){await ElMessageBox.confirm(translateText("确定停用“{0}”吗？历史填报不会删除。", [row.routineName]),translateText("停用持续工作"),{type:'warning'});await removeBusinessRoutine(detail.value.projectId,row.routineId);await refreshDetail();ElMessage.success(translateText("持续工作已停用"))}
async function enableRoutine(row){
  let endDate=null
  if((row.targetMode||'FIXED')==='AUTO_TOTAL'){
    const remaining=Math.max(0,Number(row.targetValue||0)-Number(row.cumulativeActual||0))
    if(remaining<=0)return ElMessage.warning(translateText("该持续工作的总目标已经完成，无需重新启用"))
    endDate=row.endDate&&row.endDate>=todayText()?row.endDate:null
    if(!endDate){
      const result=await ElMessageBox.prompt(translateText("剩余目标 {0} {1}。请选择新的执行结束日期。", [money(remaining), translateText(row.unit)||'']),translateText("重新启用总目标自动分配"),{type:'warning',inputType:'date',inputValue:todayText(),inputValidator:value=>!value?translateText("请选择新的结束日期"):value<todayText()?translateText("结束日期不能早于今天"):true,confirmButtonText:translateText("确认启用"),cancelButtonText:translateText("取消")})
      endDate=result.value
    }else{
      const days=Math.floor((dateMs(endDate)-dayStart())/86400000)+1
      const daily=days>0?remaining/days:remaining
      await ElMessageBox.confirm(translateText("剩余目标 {0} {1}，执行至 {2}，预计每日 {3} {4}。确定重新启用吗？", [money(remaining), translateText(row.unit)||'', endDate, money(daily), translateText(row.unit)||'']),translateText("启用持续工作"),{type:'success'})
    }
  }else await ElMessageBox.confirm(translateText("确定重新启用“{0}”吗？新的执行区间将从今天开始。", [row.routineName]),translateText("启用持续工作"),{type:'success'})
  await enableBusinessRoutine(detail.value.projectId,row.routineId,endDate?{endDate}:undefined)
  await refreshDetail()
  ElMessage.success(translateText("持续工作已启用，已开启新的执行区间"))
}
async function enableTask(row){await ElMessageBox.confirm(translateText("确定重新启用“{0}”吗？新的执行区间将从今天开始。", [row.taskName]),translateText("启用一次性任务"),{type:'success'});await enableBusinessTask(detail.value.projectId,row.taskId);await refreshDetail();ElMessage.success(translateText("一次性任务已启用，已开启新的执行区间"))}
function workPeriods(row){if(row.executionPeriods?.length)return row.executionPeriods;const start=row.startDate||row.planStartDate;if(!start)return[];const inactive=row.status==='VOID'||row.activeStatus==='VOID';return[{startDate:start,endDate:inactive?(row.endDate||String(row.updateTime||'').slice(0,10)||todayText()):null,assigneeName:row.assigneeName}]}
function executionPeriodText(period){return translateText("{0}{1} 至 {2}", [period.assigneeName?`${period.assigneeName} · `:'', period.startDate, period.endDate||todayText()])}
watch(()=>route.query.create,value=>{if(value)router.replace('/business/project-proposals')},{immediate:true})
watch(()=>route.query.id,async value=>{if(!value||Number(value)===Number(detail.value?.projectId))return;try{await openDetail({projectId:Number(value)})}catch{const nextQuery={...route.query};delete nextQuery.id;router.replace({query:nextQuery})}},{immediate:true})
watch(()=>route.query.tab,value=>{if(['overview','operating','resources','routines','tasks','members','milestones','risks','acceptance','stageAcceptance','ownerHistory','events'].includes(value))activeTab.value=value},{immediate:true})
watch([()=>route.query.allocationUserId,()=>detail.value?.projectId,()=>projectWorkPanel.value],async([userId,projectId,panel])=>{if(userId&&panel&&Number(projectId)===Number(route.query.id)){activeTab.value='resources';await nextTick();await panel.openAllocation(Number(userId));const query={...route.query};delete query.allocationUserId;router.replace({query})}},{flush:'post'})
onMounted(load)
onMounted(loadFilterOptions)
useBusinessRefreshOnReactivated(async () => {
  if(pendingChildParentId){
    const parentId=pendingChildParentId
    pendingChildParentId=null
    await hierarchyTable.value?.refreshChildren(parentId)
    return
  }
  await (detail.value ? refreshDetail() : load())
})
</script>

<style scoped>
.project-edit-form{max-height:calc(85vh - 120px);overflow-y:auto;padding:4px 12px 0 0}
.initial-risk{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px}.initial-risk>.el-input{flex:1}.initial-risk>.el-textarea{width:100%}

.project-detail-tabs{overflow-anchor:none}
.project-detail-tabs :deep(> .el-tabs__content){min-height:var(--project-tab-content-height,0px)}
.kpi-close-guard{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:14px;margin:-4px 0 16px;padding:14px 16px;border:1px solid #e6cf91;border-left:4px solid #d59a23;border-radius:10px;background:#fffaf0}.kpi-close-guard.in-tab{margin:0 0 12px}.kpi-close-guard.is-success{border-color:#bcded2;border-left-color:#2a8b6e;background:#f3faf7}.kpi-close-guard.is-danger{border-color:#efc5c8;border-left-color:#d74b55;background:#fff6f6}.kpi-close-guard.is-info{border-color:#cbdceb;border-left-color:#4d83b5;background:#f5f9fd}.kpi-close-mark{display:flex;width:42px;height:42px;align-items:center;justify-content:center;border-radius:12px;background:#fff;color:#a56c08;font-size:12px;font-weight:800;letter-spacing:.05em;box-shadow:0 2px 8px rgba(80,61,22,.08)}.is-success .kpi-close-mark{color:#23745f}.is-danger .kpi-close-mark{color:#c43d47}.is-info .kpi-close-mark{color:#3d709e}.kpi-close-copy{min-width:0}.kpi-close-title{display:flex;align-items:center;gap:8px;margin-bottom:5px}.kpi-close-title>span{color:#7b8795;font-size:12px}.kpi-close-copy>b{display:block;color:#24354a;font-size:15px}.kpi-close-copy>p{margin:4px 0 0;color:#687789;font-size:12px;line-height:1.55}.kpi-close-progress{display:grid;grid-template-columns:auto minmax(90px,180px);align-items:center;gap:10px;margin-top:9px;color:#7f8b98;font-size:12px}
.project-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;letter-spacing:.16em;color:#3977c5}.page-head h1{margin:4px 0;font-size:27px;color:#172033}.page-head p{margin:0;color:#778394}.filter-card,.table-card{border-color:#dfe4ea}.filter-card{margin-bottom:12px}.filter-card :deep(.el-card__body){padding:14px 16px 0}.click-table :deep(.el-table__row){cursor:pointer}.subline{display:block;margin-top:4px;color:#8a95a3}.danger{color:#d7474f;font-weight:700}.drawer-title{display:flex;align-items:center;justify-content:space-between;width:100%;padding-right:18px}.drawer-title span{color:#8994a3;font-size:12px}.drawer-title h2{margin:3px 0 0;color:#1b2b40}.project-summary{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #e2e7ed;border-radius:10px;background:#fafbfd}.project-summary div{padding:14px;border-right:1px solid #e2e7ed}.project-summary div:last-child{border:0}.project-summary span,.objective span{display:block;color:#8792a1;font-size:12px}.project-summary b{display:block;margin-top:6px;color:#26374d;font-size:14px}.objective{margin:14px 0;padding:14px 16px;border-left:3px solid #3b7cc4;background:#f5f8fb}.objective p{margin:7px 0 0;line-height:1.65}.action-bar{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px}.action-bar .el-button+.el-button{margin-left:0}.tab-tools{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.routine-tip{display:block;margin-top:4px}.operating-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.operating-card{padding:14px;border:1px solid #dfe5ec;border-radius:10px;background:#fafbfd}.operating-card p{margin:10px 0 0;color:#7a8796;font-size:12px;line-height:1.55}.operating-head{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.operating-head small,.operating-head strong{display:block}.operating-head small{color:#8390a0}.operating-head strong{margin-top:7px;color:#1c3048;font-size:19px}.budget-card{border-top:3px solid #397ac5}.section-gap{margin-top:22px}.muted,.form-tip{color:#8a95a3;font-size:12px}.form-tip{display:block;margin-top:5px}.history-collapse{margin-top:10px}.acceptance-record{margin-top:12px;padding:16px;border:1px solid #e2e7ed;border-radius:10px;background:#fff}.acceptance-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.acceptance-head b,.acceptance-head span{display:block}.acceptance-head span{margin-top:4px;color:#8793a1;font-size:12px}.acceptance-record h4{margin:16px 0 6px;color:#526174;font-size:13px}.acceptance-record>p{margin:0;line-height:1.7;white-space:pre-wrap}.review-result{margin-top:14px;padding:12px;border-left:3px solid #4b8c80;background:#f3f8f7}.review-result p{margin:6px 0}.review-result small{color:#8793a1}.review-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}.decision-form{margin-top:18px}.project-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.project-period-line>span{color:#7f8a99}.allocation-period-field{display:flex;width:100%;flex-direction:column;gap:8px}.allocation-period-field .el-checkbox{align-self:flex-start}.event-line{padding-top:12px}.event-card{padding:14px 16px;border:1px solid #dfe5ec;border-radius:10px;background:#fafbfd}.event-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.event-actor{min-width:0}.event-actor b{display:block;color:#26374c;font-size:15px;line-height:1.5}.event-meta{display:flex;flex-wrap:wrap;gap:5px 18px;margin-top:5px;color:#7c8999;font-size:12px}.event-meta span{display:inline-flex;align-items:center}.event-meta strong,.event-meta time{color:#5e6e81;font-weight:500}.event-card p{margin:11px 0 0;padding-top:10px;border-top:1px dashed #e0e6ed;color:#596b80;line-height:1.65;white-space:pre-wrap}.event-detail-label{color:#34475e;font-weight:600}.event-status{display:block;margin-top:8px;color:#76869a}@media(max-width:760px){.project-page{padding:14px}.page-head{align-items:flex-start;flex-direction:column;gap:14px}.page-head>.el-button{width:100%}.project-summary{grid-template-columns:repeat(2,1fr)}.project-summary div:nth-child(2){border-right:0}.project-summary div:nth-child(-n+2){border-bottom:1px solid #e2e7ed}.operating-grid{grid-template-columns:1fr}.filter-card :deep(.el-form-item){display:flex;margin-right:0}.filter-card :deep(.el-input),.filter-card :deep(.el-select){width:100%!important}.tab-tools{align-items:stretch;flex-direction:column;gap:8px}.tab-tools>.el-button{width:100%;margin:0}.review-actions{display:grid;grid-template-columns:1fr 1fr}.review-actions .el-button{width:100%;margin:0}.project-period-line{grid-template-columns:1fr}.project-period-line>span{display:none}.event-card{padding:12px}.event-head{gap:8px}.event-meta{flex-direction:column;gap:3px}}
.routine-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.routine-period-line>span{color:#7f8a99}@media(max-width:760px){.routine-period-line{grid-template-columns:1fr}.routine-period-line>span{display:none}}
.period-list{display:flex;flex-direction:column;gap:3px;color:#5f6f82;font-size:12px;line-height:1.5}.period-list span{display:block}
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
