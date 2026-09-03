<template>
  <div class="app-container owner-page" v-loading="loading">
    <header class="owner-hero">
      <div>
        <span>PROJECT OWNER WORKBENCH</span>
        <h1>项目负责人工作台</h1>
        <p>负责人管理项目执行、收入、花费、人员投入和目标；提交后直接进入项目经营核算。</p>
      </div>
      <div class="hero-actions">
        <el-button v-hasPermi="['business:project:report']" type="primary" :disabled="!canReport" @click="openRevenue">录入收入</el-button>
        <el-button v-hasPermi="['business:project:report']" type="success" plain :disabled="!canReport" @click="openDailySpend">填写花费</el-button>
        <el-button v-hasPermi="['business:project:proposal:add']" type="primary" plain @click="openProposals">发起立项申请</el-button>
        <el-select v-model="selectedProjectId" filterable placeholder="选择负责项目" @change="switchProject">
          <el-option v-for="item in projects" :key="item.projectId" :label="`${item.projectName} · ${item.sponsorOwnerName || item.initiatorName || '未标注老板'} · ${projectStatusLabel(item)}`" :value="item.projectId" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="load(selectedProjectId)">刷新</el-button>
      </div>
    </header>

    <div v-if="!project && !loading" class="no-project">
      <el-empty description="你目前还不是任何项目的主负责人">
        <p>你可以先发起立项申请；老板批准后，你负责的正式项目会自动出现在这里。</p>
        <el-button v-hasPermi="['business:project:proposal:add']" type="primary" @click="openProposals">发起立项申请</el-button>
      </el-empty>
    </div>

    <template v-if="project">
      <el-alert class="governance-alert" :title="`${managementLabel[project.managementMode] || project.managementMode} · ${closeMethodLabel[project.closeMethod] || project.closeMethod}`" :description="governanceDescription" type="info" :closable="false" show-icon>
        <template #default><el-button link type="primary" @click="openProject">查看治理要求与验收进度</el-button></template>
      </el-alert>
      <section class="metric-grid">
        <article><span>昨日汇报总额</span><b>{{ xu(yesterdayReportedTotalXu) }} <em>Xu</em></b><small>{{ reportedSourceRoutineCount }} 人已提交 · {{ unreportedSourceRoutineCount }} 人未提交</small></article>
        <article><span>持续工作</span><b>{{ todayRoutines.length }}</b><small>{{ sourceRoutineCount }} 项直播同步 · {{ unreportedRoutineCount }} 项未完成</small></article>
        <article><span>未完成任务</span><b>{{ openTasks.length }}</b><small>{{ overdueTaskCount }} 项已逾期</small></article>
        <article :class="{ 'metric-warning': pendingEffortRequests.length }"><span>投入偏差待确认</span><b>{{ pendingEffortRequests.length }}</b><small>全部负责项目 · 当前项目 {{ pendingTodayEfforts.length }} 项</small></article>
        <article :class="{ 'metric-warning': personnelSetupIssueCount }"><span>人员成本待处理</span><b>{{ personnelSetupIssueCount }}</b><small>{{ missingAllocationMemberCount }} 人待设投入 · {{ bossBlockingMemberCount }} 人等待老板</small></article>
        <article><span>项目进度</span><b>{{ projectProgress }}%</b><small>{{ project.progressBizDate ? `${project.progressBizDate} 负责人填报` : '负责人尚未填报' }}</small></article>
      </section>

      <section v-if="pendingEffortRequests.length" class="panel pending-effort-panel">
        <div class="panel-head">
          <div><h2>待确认投入偏差</h2><p>汇总你负责的全部项目，不受当前项目选择影响。</p></div>
          <el-tag type="warning" effect="plain">{{ pendingEffortRequests.length }} 项待处理</el-tag>
        </div>
        <div v-for="item in pendingEffortRequests" :key="item.effortId" class="pending-effort-row">
          <span><b>{{ item.userName }}申报了投入偏差</b><small>{{ item.projectName }} · {{ item.bizDate }}</small></span>
          <div class="pending-effort-change"><span>计划 {{ item.plannedPercent }}%</span><b>→</b><span>实际 {{ item.actualPercent }}%</span></div>
          <p>原因：{{ item.deviationReason || '未填写' }}</p>
          <div class="pending-effort-actions">
            <el-button size="small" @click="switchProject(item.projectId)">查看项目</el-button>
            <el-button size="small" type="success" plain :loading="saving" @click="confirmPendingEffort(item)">确认</el-button>
            <el-button size="small" type="danger" plain :loading="saving" @click="returnPendingEffort(item)">退回</el-button>
          </div>
        </div>
      </section>

      <section v-if="allocationAlerts.length" class="panel allocation-alert-panel">
        <div class="panel-head">
          <div><h2>人员成本配置待处理</h2><p>负责人设置项目投入；国家或月度用人成本缺失时，由老板先在人员管理中补充。</p></div>
          <el-tag type="warning" effect="plain">{{ allocationAlerts.length }} 个项目</el-tag>
        </div>
        <div v-for="item in allocationAlerts" :key="item.projectId" class="allocation-alert-row">
          <span><b>{{ item.projectName }}</b><small>{{ item.projectNo || '暂无项目编号' }} · {{ projectStatusLabel(item) }}</small></span>
          <div class="allocation-issue-list">
            <p v-if="Number(item.missingRegionCount)"><el-tag size="small" type="danger" effect="plain">等待老板</el-tag><span>{{ item.missingRegionMemberNames }}：国家/地区未设置</span></p>
            <p v-if="Number(item.missingCostCount)"><el-tag size="small" type="warning" effect="plain">等待老板</el-tag><span>{{ item.missingCostMemberNames }}：月度用人成本未设置</span></p>
            <p v-if="Number(item.missingAllocationCount)"><el-tag size="small" type="primary" effect="plain">负责人处理</el-tag><span>{{ item.missingMemberNames }}：项目投入未设置</span></p>
          </div>
          <el-button v-if="Number(item.missingAllocationCount)" type="primary" plain @click="openProjectAllocation(item)">设置投入</el-button>
          <el-button v-else disabled>等待老板处理</el-button>
        </div>
      </section>

      <section class="workspace-grid">
        <div class="main-column">
          <article class="panel project-progress-panel">
            <div class="panel-head">
              <div><h2>项目完成量</h2><p>由主负责人每日填报项目整体完成情况，保存后同步到老板工作台。</p></div>
              <el-button v-hasPermi="['business:project:report']" type="primary" :plain="!!todayProjectProgress" :disabled="!canReportProgress" @click="openProjectProgressReport">{{ todayProjectProgress ? '修改今日填报' : '填报今日完成量' }}</el-button>
            </div>
            <el-alert v-if="!canReportProgress" :title="progressReportBlockReason" type="info" :closable="false" show-icon />
            <div class="project-progress-card">
              <div class="project-progress-title"><span><b>{{ project.projectName }}</b><small>{{ project.mainOwnerName || '未指定负责人' }}负责</small></span><strong>{{ projectProgress }}%</strong></div>
              <el-progress :percentage="projectProgress" :status="project.status==='CLOSED'?'success':undefined" :stroke-width="9" />
              <template v-if="project.progressReportId">
                <div class="project-progress-meta"><span>{{ project.progressBizDate }} · {{ project.progressReporterName || project.mainOwnerName }}填报</span><el-tag v-if="todayProjectProgress" size="small" type="success">今日已填报</el-tag></div>
                <p class="project-progress-summary">实际完成情况：{{ project.progressSummary }}</p>
                <el-button v-if="project.progressEvidenceUrls" size="small" type="primary" plain @click="openProjectProgressEvidence">查看成果凭证（{{ evidenceCount(project.progressEvidenceUrls) }}）</el-button>
              </template>
              <div v-else class="empty-block compact">负责人尚未填报项目整体进度</div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>持续工作状态</h2><p>手工工作按当天填报；直播主播自动展示昨日的日报提交状态，无需在这里重复提交。</p></div>
              <el-button link type="primary" @click="openProject">进入项目详情</el-button>
            </div>
            <div v-if="!todayRoutines.length" class="empty-block">当前没有持续工作计划，可在项目详情中新增</div>
            <div v-for="routine in todayRoutines" :key="routine.routineId" class="routine-card">
              <div class="routine-main">
                <div class="routine-title"><b>{{ routine.routineName }}</b><el-tag size="small" effect="plain">{{ frequencyLabel[routine.frequency] }}</el-tag><el-tag v-if="routine.sourceManaged" size="small" type="info" effect="plain">直播同步</el-tag></div>
                <small v-if="routine.sourceManaged">{{ routine.assigneeName }} · 数据日期 {{ routine.sourceBizDate }} · {{ routine.supervisorName }}监督</small>
                <small v-else>{{ routine.assigneeName || '未分配' }} · 目标 {{ routine.targetValue }} {{ routine.unit }} · 累计 {{ routine.cumulativeActual || 0 }} {{ routine.unit }}</small>
                <el-progress :percentage="routineRate(routine)" :status="routine.todayReportId && Number(routine.todayActual) >= Number(routine.targetValue) ? 'success' : undefined" :stroke-width="7" />
                <p v-if="routine.todaySummary">今日说明：{{ routine.todaySummary }}</p>
                <p v-if="routineBelowTarget(routine) && routine.todayIssueReason" class="danger">未达原因：{{ routine.todayIssueReason }}</p>
              </div>
              <div class="routine-result">
                <span>{{ routine.sourceManaged ? '昨日直播日报' : (routineLeave(routine) ? '今日状态' : '今日完成') }}</span>
                <el-tag v-if="routine.sourceManaged" :type="routine.todayReportId?'success':'warning'">{{ routine.todayReportId ? '已提交' : '未提交' }}</el-tag>
                <b v-if="routine.sourceManaged && routine.todayReportId" class="routine-xu">{{ xu(routine.sourceReportedAmount) }} Xu</b>
                <b v-else-if="!routine.sourceManaged && !routineLeave(routine)">{{ routine.todayReportId ? routine.todayActual : '—' }} {{ routine.unit }}</b><el-tag v-else-if="!routine.sourceManaged" type="info">今日请假</el-tag>
                <el-button v-if="routine.todayEvidenceUrls" size="small" type="primary" plain @click="openEvidence(routine)">查看成果凭证（{{ evidenceCount(routine.todayEvidenceUrls) }}）</el-button>
                <el-button v-if="canSubmitRoutine(routine)" v-hasPermi="['business:project:report']" size="small" :type="routine.todayReportId?'default':'primary'" :disabled="!canReport" @click="openRoutineReport(routine)">{{ routine.todayReportId ? '修改填报' : '填报完成量' }}</el-button>
                <small v-else class="assignee-report-hint">{{ routine.sourceManaged ? '完成状态由直播数据管理自动回传' : (!routine.assigneeUserId ? '等待重新分配负责人' : (routineLeave(routine) ? '今日请假，无需填报' : `由 ${routine.assigneeName} 本人填报`)) }}</small>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>一次性任务</h2><p>只放有明确完成时点的事项；“每天做多少”请放到持续工作。</p></div>
              <el-button v-if="taskReports.length" link type="primary" @click="openTaskReports()">查看全部填报（{{ taskReports.length }}）</el-button>
            </div>
            <section class="task-group">
              <div class="task-group-head"><h3>未完成</h3><el-tag size="small" type="warning" effect="plain">{{ openTasksWithReports.length }}</el-tag></div>
              <div v-if="!openTasksWithReports.length" class="empty-block compact">当前没有未完成任务</div>
              <div v-for="task in openTasksWithReports" :key="task.taskId" class="task-card">
                <i :class="`priority-${(task.priority || 'MEDIUM').toLowerCase()}`"></i>
                <div class="task-content">
                  <b>{{ task.taskName }}</b>
                  <small>{{ task.assigneeName || '未分配' }} · 截止日期：{{ task.dueDate || '未设置' }}</small>
                  <el-progress :percentage="task.progress || 0" :stroke-width="6" />
                  <p v-if="task.latestReport" class="task-latest-report">
                    <span>{{ task.latestReport.bizDate }}填报：</span>{{ task.latestReport.completionSummary || '未填写完成内容' }}
                  </p>
                  <small v-else>任务负责人尚未提交每日填报</small>
                </div>
                <div class="task-actions">
                  <el-button v-if="task.reportCount" size="small" plain type="primary" @click="openTaskReports(task)">查看填报（{{ task.reportCount }}）</el-button>
                  <el-tag v-else size="small" type="info" effect="plain">进度由任务负责人填报</el-tag>
                </div>
              </div>
            </section>
            <section class="task-group completed-task-group">
              <div class="task-group-head"><h3>已完成</h3><el-tag size="small" type="success" effect="plain">{{ completedTasksWithReports.length }}</el-tag></div>
              <div v-if="!completedTasksWithReports.length" class="empty-block compact">当前没有已完成任务</div>
              <div v-for="task in completedTasksWithReports" :key="task.taskId" class="task-card completed-task-card">
                <i :class="`priority-${(task.priority || 'MEDIUM').toLowerCase()}`"></i>
                <div class="task-content">
                  <b>{{ task.taskName }}</b>
                  <small>{{ task.assigneeName || '未分配' }} · 截止日期：{{ task.dueDate || '未设置' }} · 完成时间：{{ taskFinishTime(task) }}</small>
                  <el-progress :percentage="100" status="success" :stroke-width="6" />
                  <p v-if="task.latestReport" class="task-latest-report">
                    <span>{{ task.latestReport.bizDate }}填报：</span>{{ task.latestReport.completionSummary || '未填写完成内容' }}
                  </p>
                  <small v-else>暂无完成填报记录</small>
                </div>
                <div class="task-actions completed-task-actions">
                  <el-tag :type="isTaskCompletedLate(task)?'danger':'success'" effect="light">{{ isTaskCompletedLate(task) ? '逾期完成' : '已完成' }}</el-tag>
                  <el-button v-if="task.reportCount" size="small" plain type="primary" @click="openTaskReports(task)">查看填报（{{ task.reportCount }}）</el-button>
                </div>
              </div>
            </section>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>今日成员投入确认</h2><p>{{ today() }} · 员工只申报当天投入偏差，负责人逐条确认或退回。</p></div>
              <el-button link @click="openProjectAllocation(project)">设置计划</el-button>
            </div>
            <div v-if="!todayEfforts.length" class="empty-block">尚未设置今日成员计划投入</div>
            <div v-for="item in todayEfforts" :key="item.allocationId" class="today-effort-card">
              <div class="today-effort-head">
                <span><b>{{ item.userName }}</b><small>{{ effortDayHint(item) }}</small></span>
                <el-tag size="small" :type="effortStatusTone[item.reportStatus]">{{ effortStatusLabel[item.reportStatus] }}</el-tag>
              </div>
              <div class="today-effort-values"><span>计划投入 <b>{{ item.plannedPercent }}%</b></span><span>实际投入 <b>{{ item.actualPercent }}%</b></span></div>
              <p v-if="item.deviationReason" class="today-effort-reason">偏差原因：{{ item.deviationReason }}</p>
              <p v-if="item.reviewComment" class="danger">退回原因：{{ item.reviewComment }}</p>
              <div class="today-effort-actions">
                <template v-if="item.reportStatus==='SUBMITTED'">
                  <el-button type="success" plain :loading="saving" @click="confirmEffort(item)">确认</el-button>
                  <el-button type="danger" plain :loading="saving" @click="openEffortReturn(item)">退回修改</el-button>
                </template>
              </div>
            </div>
          </article>

          <article class="panel week-effort-summary">
            <div class="panel-head"><div><h2>本周投入汇总</h2><p>{{ data.effortWeekFrom }} 至 {{ data.effortWeekTo }} · 仅用于查看，不在这里批量审批偏差。</p></div></div>
            <div v-if="!effortMembers.length" class="empty-block">本周暂无投入安排</div>
            <div v-for="member in effortMembers" :key="member.userId" class="week-effort-row">
              <span><b>{{ member.userName }}</b><small>{{ member.deviationCount }} 天存在投入偏差</small></span>
              <span>计划日均 <b>{{ member.plannedAverage }}%</b></span>
              <span>实际日均 <b>{{ member.actualAverage }}%</b></span>
              <el-tag :type="member.confirmedDays===member.days?'success':'info'">已处理 {{ member.confirmedDays }}/{{ member.days }} 天</el-tag>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>今日项目总花费</h2><p>{{ accounting.bizDate }} · 负责人确认后直接计入经营结果；不包含人员成本</p></div>
              <el-button v-hasPermi="['business:project:report']" type="primary" :icon="accounting.dailySpend?'Edit':'Plus'" :disabled="!canReport" @click="openDailySpend">{{ accounting.dailySpend ? '修改花费' : '填写花费' }}</el-button>
            </div>
            <el-alert v-if="!canReport" :title="reportBlockReason" type="info" :closable="false" show-icon />
            <div v-if="!accounting.dailySpend" class="empty-block">今日尚未填写项目总花费</div>
            <div v-else class="daily-spend-row">
              <span><small>今日填报花费</small><b>{{ money(accounting.dailySpend.amount) }} {{ accounting.dailySpend.currency || project.baseCurrency }}</b></span>
              <span><small>说明</small><b>{{ accounting.dailySpend.description || '无' }}</b></span>
              <el-tag :type="accounting.dailySpend.status==='RETURNED'?'danger':accounting.dailySpend.status==='DRAFT'?'warning':'success'">{{ accounting.dailySpend.status==='RETURNED'?'历史退回':accounting.dailySpend.status==='DRAFT'?'历史待确认':'已计入经营结果' }}</el-tag>
            </div>
            <el-alert v-if="accounting.dailySpend?.status==='RETURNED'" :title="`老板已退回：${accounting.dailySpend.returnReason||'请修改后重新提交'}`" type="warning" :closable="false" show-icon />
          </article>

          <article class="panel revenue-summary-panel">
            <div class="panel-head">
              <div><h2>今日项目总收入</h2><p>{{ accounting.bizDate }} · 收入由负责人确认后直接计入经营结果</p></div>
              <el-button v-hasPermi="['business:project:report']" type="primary" plain :disabled="!canReport" @click="openRevenue">录入收入</el-button>
            </div>
            <div class="daily-revenue-row">
              <span><small>今日填报总额</small><b>{{ money(revenueSubmittedAmount) }} {{ project.baseCurrency || 'CNY' }}</b></span>
              <span><small>已确认入账</small><b class="green">{{ money(dailyRevenue.confirmedAmount) }} {{ project.baseCurrency || 'CNY' }}</b></span>
              <span><small>历史待确认</small><b class="pending-revenue">{{ money(dailyRevenue.draftAmount) }} {{ project.baseCurrency || 'CNY' }} · {{ Number(dailyRevenue.draftCount || 0) }} 笔</b></span>
              <el-tag :type="revenueStatusTone">{{ revenueStatusLabel }}</el-tag>
            </div>
          </article>
        </div>

        <aside class="side-column">
          <article class="panel project-summary">
            <div class="project-title"><div><small>{{ project.projectNo }}</small><h2>{{ project.projectName }}</h2></div><el-tag :type="statusTone[project.status] || 'info'">{{ projectStatusLabel(project) }}</el-tag></div>
            <p>{{ project.objective || '尚未填写项目目标' }}</p>
      <dl><div><dt>归属老板</dt><dd>{{ project.sponsorOwnerName || project.initiatorName }}</dd></div><div><dt>归属公司</dt><dd>{{ project.companyName || '待设置' }}</dd></div><div><dt>计划周期</dt><dd>{{ project.planStartDate ? `${project.planStartDate} 至 ${project.planEndDate || '不限期'}` : '—' }}</dd></div><div><dt>项目进度</dt><dd>{{ projectProgress }}%</dd></div></dl>
          </article>

          <article class="panel">
            <div class="panel-head"><div><h2>项目 KPI</h2><p>负责人设置目标、发布奖金方案并完成结果结算。</p></div><el-button size="small" @click="openKpiBonus">管理KPI与结算</el-button></div>
            <div v-if="!currentKpis.length" class="empty-block compact">尚未设置 KPI</div>
            <div v-for="kpi in currentKpis" :key="kpi.kpiId" class="kpi-row"><span><b>{{ kpi.kpiName }}</b><small>项目目标 {{ kpi.targetValue }} {{ kpi.unit || '' }}</small></span><strong>{{ kpi.weight }}%</strong></div>
          </article>

          <article class="panel">
            <div class="panel-head"><div><h2>参项人员</h2><p>本项目共 {{ project.members?.length || 0 }} 人，不展示人员成本金额。</p></div></div>
            <div v-if="!project.members?.length" class="empty-block compact">尚未添加参项人员</div>
            <div v-else class="participant-list">
              <div v-for="member in project.members" :key="member.memberId || member.userId" class="participant-row">
                <el-avatar :size="34">{{ (member.userNameSnapshot || '员').slice(0, 1) }}</el-avatar>
                <span><b>{{ member.userNameSnapshot }}</b><small v-if="participantLeave(member)" class="leave-note">今日请假：{{ participantLeave(member).reason || '已登记' }}</small><small v-else>{{ member.joinedDate ? `${member.joinedDate} 加入` : '项目成员' }}</small></span>
                <el-tag size="small" :type="memberRoleTone[member.memberRole] || 'info'">{{ memberRoleLabel[member.memberRole] || member.memberRole }}</el-tag>
                <div class="participant-action">
                  <template v-if="participantLeave(member)">
                    <el-tag v-if="cancelPendingLeaveRequest(member)" size="small" type="warning">取消请假待审批</el-tag>
                    <template v-else><el-tag size="small" type="success">请假已批准</el-tag><el-button v-if="approvedLeaveRequest(member)" link type="danger" :loading="saving" @click="withdrawLeave(approvedLeaveRequest(member))">申请取消</el-button></template>
                  </template>
                  <template v-else-if="pendingLeaveRequest(member)">
                    <el-tag size="small" type="warning">请假待审批</el-tag>
                    <el-button link type="danger" :loading="saving" @click="withdrawLeave(pendingLeaveRequest(member))">撤回</el-button>
                  </template>
                  <template v-else>
                    <el-tag v-if="participantEffort(member)" size="small" :type="effortStatusTone[participantEffort(member).reportStatus]">{{ effortStatusLabel[participantEffort(member).reportStatus] }}</el-tag>
                    <el-tag v-else size="small" type="info">今日无投入计划</el-tag>
                    <el-button link type="primary" :loading="saving" @click="openLeave(member)">申请请假</el-button>
                  </template>
                </div>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head"><div><h2>请假申请记录</h2><p>负责人提交、老板审批；批准后才影响全部项目投入和人员成本。</p></div></div>
            <div v-if="!leaveRequests.length" class="empty-block compact">暂无请假申请</div>
            <div v-for="request in leaveRequests" :key="request.requestId" class="leave-request-row">
              <span><b>{{ request.userName }} · {{ leaveTypeLabel[request.leaveType] || request.leaveType }}</b><small>{{ leaveDateText(request) }} · {{ request.reason }}</small></span>
              <el-tag size="small" :type="leaveStatusTone[request.status]">{{ leaveStatusLabel[request.status] || request.status }}</el-tag>
              <el-button v-if="request.status==='PENDING'" link type="danger" :loading="saving" @click="withdrawLeave(request)">撤回</el-button>
              <el-button v-else-if="request.status==='APPROVED'" link type="danger" :loading="saving" @click="withdrawLeave(request)">申请取消</el-button>
              <business-file-upload v-if="request.attachmentUrls" class="leave-request-files" :model-value="request.attachmentUrls" :project-id="request.submittedProjectId" disabled :drag="false" :is-show-tip="false" />
              <small v-if="request.reviewComment" class="leave-review-comment">审批意见：{{ request.reviewComment }}</small>
            </div>
          </article>
        </aside>
      </section>
    </template>

    <el-dialog v-model="revenueDialog" title="录入今日收入" width="min(680px, 94vw)" append-to-body>
      <el-alert title="负责人确认后收入将直接计入项目经营结果；如需更正，请通过新增记录或财务冲正保留审计轨迹。" type="success" :closable="false" show-icon />
      <el-form :model="revenueForm" label-width="92px" class="report-form">
        <el-form-item label="归属项目"><el-input :model-value="project?.projectName" disabled /></el-form-item>
        <el-form-item label="业务日期"><el-input :model-value="accounting.bizDate" disabled /></el-form-item>
        <el-form-item label="收入类别" required>
          <el-select v-model="revenueForm.categoryId" placeholder="请选择收入类别" style="width:100%">
            <el-option v-for="item in revenueCategories" :key="item.categoryId" :label="item.categoryName" :value="item.categoryId" />
          </el-select>
        </el-form-item>
        <el-form-item label="收入金额" required><el-input-number v-model="revenueForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="revenueForm.currency" maxlength="3" /></el-form-item>
        <el-form-item label="收入说明" required><el-input v-model="revenueForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请说明收入来源或对应业务" /></el-form-item>
        <el-form-item label="付款单位"><el-input v-model="revenueForm.counterparty" maxlength="200" /></el-form-item>
        <el-form-item label="凭证附件"><business-file-upload v-model="revenueForm.attachmentUrls" :project-id="revenueForm.projectId" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="revenueForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="revenueDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRevenue">提交收入草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="projectProgressDialog" :title="projectProgressForm.reportId?'修改今日项目完成量':'填报今日项目完成量'" width="min(660px, 94vw)" append-to-body>
      <el-alert :title="`${projectProgressForm.projectName || ''} · ${accounting.bizDate || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="projectProgressForm" label-width="108px" class="report-form project-progress-form">
        <el-form-item label="项目名称"><el-input :model-value="projectProgressForm.projectName" disabled /></el-form-item>
        <el-form-item label="实际完成情况" required><el-input v-model="projectProgressForm.completionSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请说明今天推动项目完成的内容和结果" /></el-form-item>
        <el-form-item label="项目进度" required><el-slider v-model="projectProgressForm.progress" show-input :min="0" :max="100" :disabled="Number(projectProgressForm.minimumProgress || 0) >= 100" @input="keepProjectProgress" /><small class="progress-tip">当前项目进度 {{ projectProgressForm.minimumProgress || 0 }}%，只能向上调整，与一次性任务进度无关。</small></el-form-item>
        <el-form-item label="成果凭证" required><business-file-upload v-model="projectProgressForm.evidenceUrls" :project-id="projectProgressForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="projectProgressDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitProjectProgress">保存今日项目完成量</el-button></template>
    </el-dialog>

    <el-dialog v-model="routineReportDialog" :title="routineReportForm.reportId?'修改今日完成量':'填报今日完成量'" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${routineReportForm.routineName || ''} · ${accounting.bizDate || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="routineReportForm" label-width="92px" class="report-form">
        <el-form-item label="周期目标"><el-input :model-value="`${routineReportForm.targetValue || 0} ${routineReportForm.unit || ''}`" disabled /></el-form-item>
        <el-form-item label="实际完成" required><el-input-number v-model="routineReportForm.actualValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item label="今日说明"><el-input v-model="routineReportForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可填写成果位置、质量情况或下一步安排" /></el-form-item>
        <el-form-item v-if="routineReportNeedsReason" label="未达原因" required><el-input v-model="routineReportForm.issueReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明未达到今日目标的原因和改进安排" /></el-form-item>
        <el-form-item label="成果凭证" :required="routineReportForm.evidenceRequired==='1'"><business-file-upload v-model="routineReportForm.evidenceUrls" :project-id="routineReportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="routineReportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoutineReport">保存今日完成量</el-button></template>
    </el-dialog>

    <el-dialog v-model="taskReportDialog" :title="taskReportDialogTitle" width="min(860px, 96vw)" append-to-body destroy-on-close>
      <div v-if="!visibleTaskReports.length" class="empty-block">暂无每日填报记录</div>
      <div v-else class="task-report-list">
        <article v-for="report in visibleTaskReports" :key="report.reportId" class="task-report-row">
          <div class="task-report-head">
            <span><b>{{ taskName(report.taskId) }}</b><small>{{ report.bizDate }} · {{ report.submittedUserName || '任务负责人' }}提交</small></span>
            <el-tag type="success" effect="plain">任务进度 {{ Number(report.progress || 0) }}%</el-tag>
          </div>
          <p>{{ report.completionSummary || '未填写完成内容' }}</p>
          <div class="task-report-footer">
            <small>填报内容按日期倒序保留，可用于追溯任务执行过程。</small>
            <el-button v-if="evidenceCount(report.evidenceUrls)" link type="primary" @click="openTaskReportEvidence(report)">查看成果凭证（{{ evidenceCount(report.evidenceUrls) }}）</el-button>
            <span v-else class="no-evidence">未上传成果凭证</span>
          </div>
        </article>
      </div>
      <template #footer><el-button type="primary" @click="taskReportDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="evidenceDialog" :title="`${evidencePreview.title || ''} · 成果凭证`" width="min(840px, 96vw)" append-to-body destroy-on-close>
      <div class="evidence-dialog-summary">
        <span>{{ evidencePreview.assigneeName || '执行人' }}提交</span>
        <span>{{ evidencePreview.bizDate || accounting.bizDate || today() }}</span>
        <span>共 {{ evidencePreview.files.length }} 个凭证</span>
      </div>
      <business-file-upload
        :model-value="evidencePreview.rawUrls"
        :project-id="evidencePreview.projectId || project?.projectId"
        disabled
        :drag="false"
        :is-show-tip="false"
      />
      <template #footer><el-button type="primary" @click="evidenceDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="effortReturnDialog" title="退回今日投入申报" width="min(520px, 94vw)" append-to-body>
      <el-alert :title="`${effortReturnForm.userName || ''} · ${effortReturnForm.bizDate || today()} · 退回后由员工修改并重新提交`" type="warning" :closable="false" show-icon />
      <el-form :model="effortReturnForm" label-width="88px" class="report-form">
        <el-form-item label="退回原因" required><el-input v-model="effortReturnForm.reviewComment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请明确说明需要员工修改的内容" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="effortReturnDialog=false">取消</el-button><el-button type="danger" :loading="saving" @click="submitEffortReturn">确认退回</el-button></template>
    </el-dialog>

    <el-dialog v-model="leaveDialog" title="提交员工请假申请" width="min(620px, 94vw)" append-to-body>
      <el-alert title="提交后由归属老板审批；只有批准后才会对该员工同期参与的全部项目生效，并重新计算人员成本。" type="info" :closable="false" show-icon />
      <el-form :model="leaveForm" label-width="88px" class="report-form">
        <el-form-item label="请假人员"><el-input :model-value="leaveForm.userName" disabled /></el-form-item>
        <el-form-item label="请假类型" required><el-select v-model="leaveForm.leaveType" style="width:100%"><el-option v-for="(label,key) in leaveTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item label="请假日期" required><el-date-picker v-model="leaveForm.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :disabled-date="disablePastLeaveDate" style="width:100%" /></el-form-item>
        <el-form-item label="请假原因" required><el-input v-model="leaveForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写便于老板判断的具体原因" /></el-form-item>
        <el-form-item label="证明附件"><business-file-upload v-model="leaveForm.attachmentUrls" :project-id="project?.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="leaveDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitLeave">提交老板审批</el-button></template>
    </el-dialog>

    <el-dialog v-model="reportDialog" :title="accounting.dailySpend ? '修改今日项目总花费' : '填写今日项目总花费'" width="min(620px, 94vw)" append-to-body>
      <el-alert title="这里仅填写项目当天发生的业务花费，不含人员成本；负责人确认后直接计入经营结果，修改会保留原记录及冲正轨迹。" type="info" :closable="false" show-icon />
      <el-alert v-if="reportForm.status==='RETURNED'" class="returned-spend-alert" :title="`退回原因：${reportForm.returnReason||'未填写'}`" type="warning" :closable="false" show-icon />
      <el-form :model="reportForm" label-width="104px" class="report-form">
        <el-form-item label="归属项目"><el-input :model-value="project?.projectName" disabled /></el-form-item>
        <el-form-item label="业务日期"><el-input :model-value="accounting.bizDate" disabled /></el-form-item>
        <el-form-item label="今日总花费" required><el-input-number v-model="reportForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="费用说明"><el-input v-model="reportForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填，例如：投流、采购、物流等合计" /></el-form-item>
        <el-form-item label="凭证附件"><business-file-upload v-model="reportForm.attachmentUrls" :project-id="reportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitDailySpend">确认并计入项目成本</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessOwnerWorkbench">
import { getBusinessOwnerWorkbench, submitBusinessProjectProgressReport, submitBusinessRoutineReport, confirmBusinessMemberEffort, returnBusinessMemberEffort, markBusinessMemberLeave, cancelBusinessMemberLeaveRequest } from '@/api/business/project'
import { saveBusinessProjectDailySpend, saveBusinessProjectFact } from '@/api/business/accounting'
import useUserStore from '@/store/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const route=useRoute(),router=useRouter()
const userStore=useUserStore()
const loading=ref(false),saving=ref(false),data=ref({}),selectedProjectId=ref(null),revenueDialog=ref(false),reportDialog=ref(false),projectProgressDialog=ref(false),routineReportDialog=ref(false),leaveDialog=ref(false),effortReturnDialog=ref(false),taskReportDialog=ref(false),evidenceDialog=ref(false)
const projects=computed(()=>data.value.projects||[]),project=computed(()=>data.value.project||null)
const operating=computed(()=>data.value.operating||{}),accounting=computed(()=>data.value.accounting||{})
const revenueCategories=computed(()=>accounting.value.revenueCategories||[])
const dailyRevenue=computed(()=>accounting.value.dailyRevenue||{})
const revenueSubmittedAmount=computed(()=>Number(dailyRevenue.value.confirmedAmount||0)+Number(dailyRevenue.value.draftAmount||0))
const revenueStatusTone=computed(()=>Number(dailyRevenue.value.draftCount||0)>0?'warning':Number(dailyRevenue.value.confirmedCount||0)>0?'success':'info')
const revenueStatusLabel=computed(()=>Number(dailyRevenue.value.draftCount||0)>0?'存在历史待确认':Number(dailyRevenue.value.confirmedCount||0)>0?'已计入经营结果':'今日暂无收入')
const allocationAlerts=computed(()=>data.value.allocationAlerts||[])
const pendingEffortRequests=computed(()=>data.value.pendingEffortRequests||[])
const missingAllocationMemberCount=computed(()=>allocationAlerts.value.reduce((sum,item)=>sum+Number(item.missingAllocationCount||0),0))
const bossBlockingMemberCount=computed(()=>allocationAlerts.value.reduce((sum,item)=>sum+Number(item.missingRegionCount||0)+Number(item.missingCostCount||0),0))
const personnelSetupIssueCount=computed(()=>missingAllocationMemberCount.value+bossBlockingMemberCount.value)
const openTasks=computed(()=>data.value.openTasks||[])
const taskReports=computed(()=>data.value.taskReports||[])
const taskReportTaskId=ref(null)
const taskWithReports=task=>{const reports=taskReports.value.filter(report=>Number(report.taskId)===Number(task.taskId));return {...task,reportCount:reports.length,latestReport:reports[0]||null}}
const openTasksWithReports=computed(()=>openTasks.value.map(taskWithReports))
const completedTasksWithReports=computed(()=>(project.value?.tasks||[]).filter(task=>task.status==='DONE').map(taskWithReports))
const visibleTaskReports=computed(()=>taskReportTaskId.value===null?taskReports.value:taskReports.value.filter(report=>Number(report.taskId)===Number(taskReportTaskId.value)))
const taskReportDialogTitle=computed(()=>taskReportTaskId.value===null?`一次性任务填报记录 · 共 ${visibleTaskReports.value.length} 条`:`${taskName(taskReportTaskId.value)} · 填报记录`)
const todayProjectProgress=computed(()=>data.value.todayProjectProgress||null)
const todayRoutines=computed(()=>(data.value.todayRoutines||[]).filter(item=>(!item.startDate||item.startDate<=today())&&(!item.endDate||item.endDate>=today())))
const sourceRoutines=computed(()=>todayRoutines.value.filter(item=>item.sourceManaged))
const sourceRoutineCount=computed(()=>sourceRoutines.value.length)
const reportedSourceRoutineCount=computed(()=>sourceRoutines.value.filter(item=>item.todayReportId).length)
const unreportedSourceRoutineCount=computed(()=>sourceRoutineCount.value-reportedSourceRoutineCount.value)
const yesterdayReportedTotalXu=computed(()=>sourceRoutines.value.reduce((sum,item)=>sum+Number(item.sourceReportedAmount||0),0))
const currentKpis=computed(()=>(operating.value.kpis||[]).filter(item=>item.status==='CURRENT'))
const leaveRequests=computed(()=>data.value.leaveRequests||[])
const canReport=computed(()=>['ACTIVE','ACCEPTANCE'].includes(project.value?.status)&&!!project.value?.companyDeptId)
const canReportProgress=computed(()=>project.value?.status==='ACTIVE')
const unreportedRoutineCount=computed(()=>todayRoutines.value.filter(item=>!item.todayReportId&&!routineLeave(item)).length)
const routineReportNeedsReason=computed(()=>routineReportForm.value.frequency==='DAILY'&&routineReportForm.value.actualValue!==null&&routineReportForm.value.actualValue!==undefined&&Number(routineReportForm.value.actualValue)<Number(routineReportForm.value.targetValue||0))
const reportBlockReason=computed(()=>!project.value?.companyDeptId?'项目尚未设置归属公司，请联系归属老板完善后再填报':'项目进入执行中后才能提交经营数据')
const progressReportBlockReason=computed(()=>project.value?.status==='CLOSED'?'项目已结项，进度固定为 100%':'项目进入执行中后才能填报项目完成量')
const overdueTaskCount=computed(()=>openTasks.value.filter(task=>task.dueDate&&task.dueDate<today()).length)
const todayEfforts=computed(()=>(data.value.effortWeek||[]).filter(item=>item.bizDate===today()))
const pendingTodayEfforts=computed(()=>todayEfforts.value.filter(item=>item.reportStatus==='SUBMITTED'))
const effortMembers=computed(()=>{
  const grouped=new Map()
  for(const row of (data.value.effortWeek||[]).filter(item=>item.bizDate<=today())){
    const key=Number(row.userId)
    if(!grouped.has(key))grouped.set(key,{userId:key,userName:row.userName,days:0,confirmedDays:0,deviationCount:0,plannedTotal:0,actualTotal:0,todayOnLeave:false,leaveReason:'',deviations:[]})
    const item=grouped.get(key)
    item.days++
    item.plannedTotal+=Number(row.plannedPercent||0)
    item.actualTotal+=Number(row.actualPercent||0)
    if(!['SUBMITTED','RETURNED'].includes(row.reportStatus))item.confirmedDays++
    if(row.reportStatus!=='LEAVE'&&Number(row.actualPercent)!==Number(row.plannedPercent)){
      item.deviationCount++
      item.deviations.push({bizDate:row.bizDate,plannedPercent:Number(row.plannedPercent||0),actualPercent:Number(row.actualPercent||0),deviationReason:row.deviationReason||'',reportStatus:row.reportStatus})
    }
    if(row.bizDate===today()&&row.reportStatus==='LEAVE'){item.todayOnLeave=true;item.leaveReason=row.leaveReason||'已登记请假'}
  }
  return [...grouped.values()].map(item=>({...item,plannedAverage:item.days?Math.round(item.plannedTotal*10/item.days)/10:0,actualAverage:item.days?Math.round(item.actualTotal*10/item.days)/10:0}))
})
const confirmedEffortDays=computed(()=>effortMembers.value.reduce((sum,item)=>sum+item.confirmedDays,0))
const totalEffortDays=computed(()=>effortMembers.value.reduce((sum,item)=>sum+item.days,0))
const projectProgress=computed(()=>Math.min(100,Math.max(0,Math.round(Number(project.value?.progressPercent||0)))))
const statusLabel={DRAFT:'草稿',PLANNING:'规划中',ACTIVE:'执行中',PAUSED:'已暂停',ACCEPTANCE:'待验收',CLOSED:'已结项',CANCELED:'已取消'}
const statusTone={DRAFT:'info',PLANNING:'warning',ACTIVE:'primary',PAUSED:'info',ACCEPTANCE:'success',CLOSED:'success',CANCELED:'danger'}
const projectStatusLabel=item=>item?.status==='ACCEPTANCE'&&item?.closeMethod==='STAGED_ACCEPTANCE'?'待结项':statusLabel[item?.status]||item?.status
const managementLabel={LIGHT:'轻量管理',STANDARD:'标准管理',KEY_CONTROL:'重点监管',SIMPLE:'轻量管理',DELIVERY:'标准管理'}
const closeMethodLabel={DIRECT:'直接结项',RESULT_ACCEPTANCE:'成果验收',STAGED_ACCEPTANCE:'阶段验收'}
const governanceDescription=computed(()=>{const p=project.value;if(!p)return '';const cycle=p.governanceProfile?.reportCycle==='EXCEPTION'?'异常时更新':p.governanceProfile?.reportCycle==='WEEKLY_AND_EVENT'?'每周更新并在重大事件时专项汇报':'每周更新';const close={DIRECT:'负责人提交结项申请，由老板检验通过后结项。',RESULT_ACCEPTANCE:'负责人提交整体验收资料，由老板验收通过后结项。',STAGED_ACCEPTANCE:'负责人按里程碑提交成果，由老板逐项验收并最终确认结项。'}[p.closeMethod]||'';return `过程要求：${cycle}；${close}`})
const frequencyLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月'}
const memberRoleLabel={OWNER:'主负责人',DEPUTY:'副负责人',MEMBER:'成员',OBSERVER:'观察者'}
const memberRoleTone={OWNER:'primary',DEPUTY:'success',MEMBER:'info',OBSERVER:'warning'}
const effortStatusLabel={UNSUBMITTED:'按计划执行',SUBMITTED:'待确认',CONFIRMED:'已确认',RETURNED:'已退回',LEAVE:'今日请假'}
const effortStatusTone={UNSUBMITTED:'info',SUBMITTED:'warning',CONFIRMED:'success',RETURNED:'danger',LEAVE:'info'}
const leaveTypeLabel={SICK:'病假',PERSONAL:'事假',ANNUAL:'年假',COMPENSATORY:'调休',OTHER:'其他'}
const leaveStatusLabel={PENDING:'待老板审批',APPROVED:'已批准',RETURNED:'已退回',CANCEL_PENDING:'取消待审批',CANCELED:'已取消'}
const leaveStatusTone={PENDING:'warning',APPROVED:'success',RETURNED:'danger',CANCEL_PENDING:'warning',CANCELED:'info'}
const blankReport=()=>({projectId:null,bizDate:today(),amount:null,description:'',attachmentUrls:''})
const reportForm=ref(blankReport())
const blankRevenue=()=>({projectId:null,bizDate:today(),categoryId:null,amount:null,currency:'CNY',description:'',counterparty:'',attachmentUrls:'',remark:''})
const revenueForm=ref(blankRevenue())
const projectProgressForm=ref({})
const routineReportForm=ref({})
const leaveForm=ref({userId:null,userName:'',leaveType:'SICK',dates:[today(),today()],reason:'',attachmentUrls:''})
const effortReturnForm=ref({userId:null,userName:'',bizDate:today(),reviewComment:''})
const evidencePreview=ref({title:'',assigneeName:'',bizDate:'',rawUrls:'',projectId:null,files:[]})
function today(){return new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})}
function evidencePaths(value){return String(value||'').split(',').map(item=>item.trim()).filter(Boolean)}
function evidenceCount(value){return evidencePaths(value).length}
function evidenceName(path){const clean=path.split('?')[0];try{return decodeURIComponent(clean.slice(clean.lastIndexOf('/')+1))||'成果凭证'}catch{return clean.slice(clean.lastIndexOf('/')+1)||'成果凭证'}}
function evidenceKind(path){const ext=path.split('?')[0].split('.').pop()?.toLowerCase();if(['jpg','jpeg','png','gif','webp','bmp'].includes(ext))return 'image';if(['mp4','mov','webm','ogg'].includes(ext))return 'video';return 'file'}
function openEvidenceFiles(title,assigneeName,bizDate,urls){const files=evidencePaths(urls).map(path=>({path,name:evidenceName(path),kind:evidenceKind(path)}));evidencePreview.value={title,assigneeName,bizDate,rawUrls:urls,projectId:project.value?.projectId,files};evidenceDialog.value=true}
function openEvidence(routine){openEvidenceFiles(routine.routineName,routine.assigneeName,accounting.value.bizDate||today(),routine.todayEvidenceUrls)}
function openProjectProgressEvidence(){openEvidenceFiles(project.value.projectName,project.value.progressReporterName||project.value.mainOwnerName,project.value.progressBizDate,project.value.progressEvidenceUrls)}
function taskName(taskId){return (project.value?.tasks||[]).find(task=>Number(task.taskId)===Number(taskId))?.taskName||'一次性任务'}
function taskFinishDate(task){return String(task.actualFinishTime||task.latestReport?.bizDate||'').slice(0,10)}
function taskFinishTime(task){return task.actualFinishTime||task.latestReport?.bizDate||'未记录'}
function isTaskCompletedLate(task){const finishDate=taskFinishDate(task);return !!task.dueDate&&!!finishDate&&finishDate>task.dueDate}
function openTaskReports(task){taskReportTaskId.value=task?.taskId??null;taskReportDialog.value=true}
function openTaskReportEvidence(report){openEvidenceFiles(taskName(report.taskId),report.submittedUserName,report.bizDate,report.evidenceUrls)}
function money(value){return Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}
function xu(value){return Number(value||0).toLocaleString('zh-CN',{maximumFractionDigits:2})}
function signed(value){const n=Number(value||0);return `${n>0?'+':''}${money(n)}`}
function amountTone(value){return Number(value||0)<0?'amount-loss':'amount-profit'}
function kpiRate(kpi){const target=Number(kpi.targetValue||0);return target?Math.round(Number(kpi.actualValue||0)*100/target):0}
function routineRate(routine){const target=Number(routine.targetValue||0);return target?Math.min(100,Math.round(Number(routine.todayActual||0)*100/target)):0}
function routineBelowTarget(routine){return routine.frequency==='DAILY'&&!!routine.todayReportId&&Number(routine.todayActual)<Number(routine.targetValue||0)}
function canSubmitRoutine(routine){return !routine.sourceManaged&&!routineLeave(routine)&&Number(routine.assigneeUserId)===Number(userStore.id)}
function effortDayHint(item){if(item.reportStatus==='SUBMITTED')return '员工已申报偏差，请逐条审核';if(item.reportStatus==='CONFIRMED')return '当天实际投入已确认并锁定';if(item.reportStatus==='RETURNED')return '已退回员工修改';if(item.reportStatus==='LEAVE')return `今日请假${item.leaveReason?`：${item.leaveReason}`:''}`;return '未申报偏差，按计划投入自动核算'}
function participantEffort(member){return todayEfforts.value.find(item=>Number(item.userId)===Number(member.userId))}
function participantLeave(member){const stored=(data.value.todayLeaves||[]).find(item=>Number(item.userId)===Number(member.userId));if(stored)return stored;const effort=participantEffort(member);return effort?.reportStatus==='LEAVE'?{userId:member.userId,reason:effort.leaveReason||''}:null}
function pendingLeaveRequest(member){return leaveRequests.value.find(item=>Number(item.userId)===Number(member.userId)&&item.status==='PENDING')}
function approvedLeaveRequest(member){return leaveRequests.value.find(item=>Number(item.userId)===Number(member.userId)&&item.status==='APPROVED'&&item.startDate<=today()&&item.endDate>=today())}
function cancelPendingLeaveRequest(member){return leaveRequests.value.find(item=>Number(item.userId)===Number(member.userId)&&item.status==='CANCEL_PENDING'&&item.startDate<=today()&&item.endDate>=today())}
function leaveDateText(request){return request.startDate===request.endDate?request.startDate:`${request.startDate} 至 ${request.endDate}`}
function disablePastLeaveDate(date){return date.getTime()<new Date(`${today()}T00:00:00+08:00`).getTime()}
function routineLeave(routine){return routine.sourceManaged?null:(data.value.todayLeaves||[]).find(item=>Number(item.userId)===Number(routine.assigneeUserId))}
function participantName(member){return member.userNameSnapshot||member.userName||'项目成员'}
async function load(projectId){loading.value=true;try{const{data:payload={}}=await getBusinessOwnerWorkbench(projectId||undefined);data.value=payload;selectedProjectId.value=payload.project?.projectId||null;if(selectedProjectId.value)router.replace({query:{...route.query,projectId:selectedProjectId.value}})}finally{loading.value=false}}
function switchProject(id){load(id)}
function openProject(){router.push({path:'/business/projects',query:{id:project.value.projectId}})}
function openProjectAllocation(item){router.push({path:'/business/projects',query:{id:item.projectId,tab:'operating'}})}
function openKpiBonus(){router.push({path:'/business/kpi-bonus',query:{projectId:project.value.projectId}})}
function openProposals(){router.push('/business/project-proposals')}
function openRevenue(){
  if(!revenueCategories.value.length)return ElMessage.warning('收入类别尚未初始化，请联系管理员')
  revenueForm.value={...blankRevenue(),projectId:project.value.projectId,bizDate:accounting.value.bizDate||today(),categoryId:revenueCategories.value[0].categoryId,currency:project.value.baseCurrency||'CNY'}
  revenueDialog.value=true
}
async function submitRevenue(){
  const form=revenueForm.value
  if(!form.categoryId)return ElMessage.warning('请选择收入类别')
  if(form.amount===null||form.amount===undefined||Number(form.amount)<0)return ElMessage.warning('请填写收入金额')
  if(!form.description?.trim())return ElMessage.warning('请填写收入说明')
  saving.value=true
  try{
    await saveBusinessProjectFact({...form,description:form.description.trim(),counterparty:form.counterparty?.trim(),currency:(form.currency||project.value.baseCurrency||'CNY').trim().toUpperCase()})
    revenueDialog.value=false
    ElMessage({type:'success',message:'收入已由负责人确认并计入经营结果',duration:3500,showClose:true})
    await load(selectedProjectId.value)
  }finally{saving.value=false}
}
function openProjectProgressReport(){const current=Number(projectProgress.value||0),todayReport=todayProjectProgress.value||{};projectProgressForm.value={reportId:todayReport.reportId||null,projectId:project.value.projectId,bizDate:accounting.value.bizDate||today(),projectName:project.value.projectName,minimumProgress:current,progress:Number(todayReport.progress??current),completionSummary:todayReport.completionSummary||'',evidenceUrls:todayReport.evidenceUrls||''};projectProgressDialog.value=true}
function keepProjectProgress(value){const minimum=Number(projectProgressForm.value.minimumProgress||0);if(Number(value)<minimum)projectProgressForm.value.progress=minimum}
async function submitProjectProgress(){const form=projectProgressForm.value;if(!form.completionSummary?.trim())return ElMessage.warning('请填写实际完成情况');if(form.progress===null||form.progress===undefined||Number(form.progress)<Number(form.minimumProgress||0)||Number(form.progress)>100)return ElMessage.warning(`项目进度只能增加，不能低于 ${form.minimumProgress||0}%`);if(!form.evidenceUrls)return ElMessage.warning('请上传成果凭证');saving.value=true;try{await submitBusinessProjectProgressReport(form);projectProgressDialog.value=false;ElMessage.success('今日项目完成量已保存并同步到老板工作台');await load(selectedProjectId.value)}finally{saving.value=false}}
async function confirmEffort(item){
  saving.value=true
  try{
    const response=await confirmBusinessMemberEffort(project.value.projectId,item.userId,{bizDate:item.bizDate})
    const saved=response?.data||{}
    item.reportStatus=saved.reportStatus||'CONFIRMED'
    item.confirmedUserName=saved.confirmedUserName||userStore.nickName||userStore.name
    item.confirmedTime=saved.confirmedTime||new Date().toISOString()
    item.reviewComment=''
    await load(selectedProjectId.value)
    ElMessage({type:'success',message:`${item.userName} 的今日投入已确认，人员成本已重新计算`,duration:3000,showClose:true})
  }finally{saving.value=false}
}
async function confirmPendingEffort(item){
  await ElMessageBox.confirm(`确认 ${item.userName} 在 ${item.projectName} 的实际投入为 ${item.actualPercent}% 吗？`,'确认投入偏差',{type:'warning'})
  saving.value=true
  try{
    await confirmBusinessMemberEffort(item.projectId,item.userId,{bizDate:item.bizDate})
    ElMessage.success(`${item.userName} 的投入已确认`)
    await load(selectedProjectId.value)
  }finally{saving.value=false}
}
async function returnPendingEffort(item){
  const{value}=await ElMessageBox.prompt(`退回 ${item.userName} 在 ${item.projectName} 的投入申报`, '退回投入偏差', {inputPlaceholder:'请填写退回原因',inputValidator:value=>!!value?.trim()||'必须填写退回原因',type:'warning'})
  saving.value=true
  try{
    await returnBusinessMemberEffort(item.projectId,item.userId,{bizDate:item.bizDate,reviewComment:value.trim()})
    ElMessage.success('已退回员工修改')
    await load(selectedProjectId.value)
  }finally{saving.value=false}
}
function openEffortReturn(item){effortReturnForm.value={userId:item.userId,userName:item.userName,bizDate:item.bizDate,reviewComment:''};effortReturnDialog.value=true}
async function submitEffortReturn(){
  const form=effortReturnForm.value
  if(!form.reviewComment?.trim())return ElMessage.warning('请填写退回原因')
  saving.value=true
  try{
    await returnBusinessMemberEffort(project.value.projectId,form.userId,{bizDate:form.bizDate,reviewComment:form.reviewComment.trim()})
    effortReturnDialog.value=false
    await load(selectedProjectId.value)
    ElMessage({type:'success',message:'已退回员工修改',duration:3000,showClose:true})
  }finally{saving.value=false}
}
function openRoutineReport(routine){routineReportForm.value={reportId:routine.todayReportId||null,routineId:routine.routineId,projectId:project.value.projectId,bizDate:accounting.value.bizDate||today(),routineName:routine.routineName,frequency:routine.frequency,targetValue:routine.targetValue,actualValue:routine.todayReportId?Number(routine.todayActual):null,unit:routine.unit,summary:routine.todaySummary||'',issueReason:routine.todayIssueReason||'',evidenceUrls:routine.todayEvidenceUrls||'',evidenceRequired:routine.evidenceRequired,version:null};routineReportDialog.value=true}
async function submitRoutineReport(){const form=routineReportForm.value;if(form.actualValue===null||form.actualValue===undefined||Number(form.actualValue)<0)return ElMessage.warning('请填写实际完成量');if(routineReportNeedsReason.value&&!form.issueReason?.trim())return ElMessage.warning('未达到每日目标时请填写原因');if(form.evidenceRequired==='1'&&!form.evidenceUrls)return ElMessage.warning('该工作要求上传成果凭证');form.issueReason=routineReportNeedsReason.value?form.issueReason.trim():null;saving.value=true;try{await submitBusinessRoutineReport(form);routineReportDialog.value=false;ElMessage.success('今日完成量已保存');await load(selectedProjectId.value)}finally{saving.value=false}}
function openLeave(member){leaveForm.value={userId:member.userId,userName:participantName(member),leaveType:'SICK',dates:[today(),today()],reason:'',attachmentUrls:''};leaveDialog.value=true}
async function submitLeave(){const form=leaveForm.value;if(!form.dates?.[0]||!form.dates?.[1])return ElMessage.warning('请选择请假日期');if(!form.reason?.trim())return ElMessage.warning('请填写请假原因');saving.value=true;try{await markBusinessMemberLeave(project.value.projectId,form.userId,{startDate:form.dates[0],endDate:form.dates[1],leaveType:form.leaveType,reason:form.reason.trim(),attachmentUrls:form.attachmentUrls});leaveDialog.value=false;ElMessage({type:'success',message:'请假申请已提交，等待老板审批；批准前不会影响人员成本',duration:3500,showClose:true});await load(selectedProjectId.value)}finally{saving.value=false}}
async function withdrawLeave(request){const approved=request.status==='APPROVED';const{value}=await ElMessageBox.prompt(approved?`申请取消 ${request.userName} 已批准的请假吗？取消仍需老板审批。`:`确认撤回 ${request.userName} 的请假申请吗？`,approved?'申请取消请假':'撤回请假申请',{inputPlaceholder:'请填写原因',inputValidator:value=>!!value?.trim()||'必须填写原因',type:'warning'});saving.value=true;try{await cancelBusinessMemberLeaveRequest(request.requestId,{reason:value.trim()});ElMessage.success(approved?'取消请假申请已提交，等待老板审批':'请假申请已撤回');await load(selectedProjectId.value)}finally{saving.value=false}}
function openDailySpend(){reportForm.value={...blankReport(),...(accounting.value.dailySpend||{}),projectId:project.value.projectId,bizDate:accounting.value.bizDate};reportDialog.value=true}
async function submitDailySpend(){if(reportForm.value.amount===null||reportForm.value.amount===undefined)return ElMessage.warning('请填写今日项目总花费');saving.value=true;try{await saveBusinessProjectDailySpend(reportForm.value);reportDialog.value=false;ElMessage({type:'success',message:'今日花费已由负责人确认并计入经营结果',duration:3500,showClose:true});await load(selectedProjectId.value)}finally{saving.value=false}}
load(route.query.projectId?Number(route.query.projectId):undefined)
useBusinessRefreshOnReactivated(() => load(selectedProjectId.value || (route.query.projectId ? Number(route.query.projectId) : undefined)))
</script>

<style scoped>
.owner-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f6f8;color:#172335}.owner-hero{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:25px 28px;border-radius:16px;background:linear-gradient(120deg,#173b59,#1d6d70);color:#fff}.owner-hero span{font-size:11px;letter-spacing:.17em;color:#6de0da}.owner-hero h1{margin:5px 0;font-size:28px}.owner-hero p{margin:0;color:#c1d4de}.hero-actions{display:flex;align-items:center;gap:10px}.hero-actions .el-select{width:360px}.metric-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;margin:16px 0}.metric-grid article{min-width:0;padding:17px 19px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.metric-grid article.metric-warning{border-color:#efc36d;background:#fffaf0}.metric-grid article.metric-warning b{color:#b87513}.metric-grid span,.metric-grid small{display:block}.metric-grid span{color:#6f7d8c}.metric-grid b{display:block;margin:6px 0;font-size:26px;white-space:nowrap}.metric-grid b em{color:#697786;font-size:14px;font-style:normal;font-weight:500}.metric-grid small{color:#98a2ad}.amount-profit{color:#198069}.amount-loss,.danger{color:#cf4650}.allocation-alert-panel{margin-bottom:14px;border-color:#efcf93;background:#fffdf8}.allocation-alert-row{display:grid;grid-template-columns:minmax(220px,.8fr) minmax(220px,1.2fr) auto;align-items:center;gap:16px;padding:13px 4px;border-top:1px solid #f1e5ce}.allocation-alert-row>span{display:flex;min-width:0;flex-direction:column;gap:4px}.allocation-alert-row small{color:#8b7755}.allocation-alert-row p{margin:0;color:#7a633d;overflow-wrap:anywhere}.workspace-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(320px,.65fr);gap:14px}.main-column,.side-column{display:flex;min-width:0;flex-direction:column;gap:14px}.panel{padding:18px;border:1px solid #dfe6eb;border-radius:13px;background:#fff}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.panel h2{margin:0;font-size:17px}.panel p{margin:4px 0;color:#84919f;font-size:12px}.routine-card{display:flex;align-items:center;gap:18px;padding:15px 4px;border-top:1px solid #edf0f2}.routine-main{display:flex;min-width:0;flex:1;flex-direction:column;gap:7px}.routine-title{display:flex;align-items:center;gap:8px}.routine-main>small{color:#8b97a4}.routine-main>p{margin:0}.routine-result{display:flex;min-width:128px;align-items:flex-end;flex-direction:column;gap:6px}.routine-result span{color:#7e8b99;font-size:12px}.routine-result b{font-size:18px}.routine-result .routine-xu{color:#198069}.assignee-report-hint{color:#8b97a4}.task-card{display:flex;align-items:center;gap:12px;padding:13px 4px;border-top:1px solid #edf0f2}.task-card>i{width:8px;height:8px;border-radius:50%;background:#8794a3}.task-card>i.priority-high{background:#d44951}.task-card>i.priority-medium{background:#d68b2a}.task-card>i.priority-low{background:#3f9178}.task-content{display:flex;min-width:0;flex:1;flex-direction:column;gap:5px}.task-content small,.kpi-row small,.risk-row small,.fact-row small,.effort-member-row small{color:#8b97a4}.leave-note{color:#7b6a91!important}.task-actions,.effort-actions{display:flex}.effort-member-row{display:grid;grid-template-columns:minmax(150px,1fr) auto auto auto auto;align-items:center;gap:16px;padding:13px 4px;border-top:1px solid #edf0f2}.effort-member-row>span:first-child{display:flex;min-width:0;flex-direction:column}.fact-row{display:flex;align-items:center;gap:10px;padding:13px 4px;border-top:1px solid #edf0f2}.fact-row>span:nth-child(2){display:flex;min-width:0;flex:1;flex-direction:column}.fact-row strong{white-space:nowrap}.project-title{display:flex;align-items:flex-start;justify-content:space-between}.project-title small{color:#81909e}.project-summary>p{margin:14px 0;line-height:1.7}.project-summary dl{margin:0}.project-summary dl>div{display:flex;justify-content:space-between;padding:9px 0;border-top:1px solid #edf0f2}.project-summary dt{color:#7b8997}.project-summary dd{margin:0;text-align:right}.kpi-row,.risk-row{display:flex;align-items:center;gap:9px;padding:11px 2px;border-top:1px solid #edf0f2}.kpi-row>span,.risk-row>span{display:flex;min-width:0;flex:1;flex-direction:column}.empty-block{padding:28px 0;text-align:center;color:#9aa5b0}.empty-block.compact{padding:15px 0}.no-project{margin-top:16px;padding:50px;border:1px solid #dfe6eb;border-radius:14px;background:#fff}.no-project p{color:#8c98a5}.report-form{margin-top:18px}.returned-spend-alert{margin-top:12px}@media(max-width:1300px){.metric-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:1050px){.metric-grid{grid-template-columns:repeat(2,1fr)}.workspace-grid{grid-template-columns:1fr}}@media(max-width:640px){.owner-page{padding:12px}.owner-hero{align-items:flex-start;flex-direction:column;padding:20px}.hero-actions{width:100%;align-items:stretch;flex-direction:column}.hero-actions .el-select,.hero-actions .el-button{width:100%}.metric-grid{gap:8px}.metric-grid article{padding:14px}.panel-head{align-items:flex-start}.effort-actions{align-items:stretch;flex-direction:column}.effort-member-row{grid-template-columns:1fr 1fr}.effort-member-row>span:first-child,.effort-member-row>.el-tag{grid-column:1/-1}.effort-member-row>.el-button{justify-self:start}.allocation-alert-row{grid-template-columns:1fr}.allocation-alert-row .el-button{width:100%}.routine-card,.task-card,.fact-row{align-items:flex-start;flex-wrap:wrap}.routine-result{width:100%;align-items:stretch}.routine-result b{font-size:17px}.task-actions{width:100%;justify-content:flex-end}.fact-row strong{margin-left:auto}}
.hero-actions{justify-content:flex-end;flex-wrap:wrap}.hero-actions .el-button{margin:0}.hero-actions .el-select{width:320px}@media(max-width:1300px){.owner-hero{align-items:flex-start;flex-direction:column}.hero-actions{width:100%;justify-content:flex-start}.hero-actions .el-select{flex:1;min-width:280px}}@media(max-width:640px){.hero-actions .el-select{min-width:0}}
.daily-spend-row{display:grid;grid-template-columns:180px minmax(0,1fr) auto;align-items:center;gap:18px;padding:18px;border-radius:10px;background:#f6faf9}.daily-spend-row>span{display:flex;min-width:0;flex-direction:column;gap:5px}.daily-spend-row small{color:#8793a1}.daily-spend-row b{overflow-wrap:anywhere}@media(max-width:640px){.daily-spend-row{grid-template-columns:1fr}.daily-spend-row .el-tag{justify-self:start}}
.revenue-summary-panel{border-color:#cfe2df;background:linear-gradient(145deg,#fff,#f7fcfb)}.daily-revenue-row{display:grid;grid-template-columns:repeat(3,minmax(150px,1fr)) auto;align-items:center;gap:18px;padding:18px;border-radius:10px;background:#f3faf8}.daily-revenue-row>span{display:flex;min-width:0;flex-direction:column;gap:5px}.daily-revenue-row small{color:#8793a1}.daily-revenue-row b{overflow-wrap:anywhere;font-size:16px}.daily-revenue-row .pending-revenue{color:#b7791f}.daily-revenue-row .el-tag{justify-self:end}@media(max-width:760px){.daily-revenue-row{grid-template-columns:1fr 1fr}.daily-revenue-row .el-tag{justify-self:start}}@media(max-width:520px){.daily-revenue-row{grid-template-columns:1fr}}
.effort-member-card{border-top:1px solid #edf0f2}.effort-member-card .effort-member-row{border-top:0}.effort-deviation-list{margin:0 4px 13px;padding:10px 12px;border-radius:9px;background:#fff7e8}.effort-deviation-row{display:grid;grid-template-columns:100px auto minmax(140px,1fr) auto;align-items:center;gap:12px;padding:7px 0;color:#735c34;font-size:13px}.effort-deviation-row+.effort-deviation-row{border-top:1px solid #f0dfbd}.effort-deviation-reason{min-width:0;overflow-wrap:anywhere;color:#8a6733}@media(max-width:640px){.effort-deviation-row{grid-template-columns:1fr auto}.effort-deviation-row>span{grid-column:1/-1}}
.today-effort-card{margin-top:10px;padding:14px;border:1px solid #dfe6eb;border-radius:10px;background:#fbfcfd}.today-effort-head,.today-effort-values,.today-effort-actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.today-effort-head>span:first-child{display:flex;min-width:0;flex-direction:column}.today-effort-head small,.week-effort-row small{color:#8b97a4}.today-effort-values{justify-content:flex-start;margin:13px 0}.today-effort-values span{min-width:150px;color:#73808e}.today-effort-reason{padding:10px 12px;border-radius:8px;background:#fff7e8!important;color:#795d2f!important}.today-effort-actions{justify-content:flex-end}.week-effort-summary{background:#fafcfd}.week-effort-row{display:grid;grid-template-columns:minmax(150px,1fr) auto auto auto;align-items:center;gap:16px;padding:12px 4px;border-top:1px solid #edf0f2}.week-effort-row>span:first-child{display:flex;flex-direction:column}@media(max-width:640px){.today-effort-head,.today-effort-values{align-items:flex-start;flex-direction:column}.today-effort-values span{min-width:0}.today-effort-actions{align-items:stretch;flex-direction:column}.today-effort-actions .el-button{width:100%;margin:0}.week-effort-row{grid-template-columns:1fr 1fr}.week-effort-row>span:first-child,.week-effort-row>.el-tag{grid-column:1/-1}}
.participant-list{display:flex;flex-direction:column}.participant-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:11px;padding:11px 0;border-top:1px solid #edf0f2}.participant-row>span{display:flex;min-width:0;flex-direction:column;gap:3px}.participant-row b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.participant-row small{color:#8b97a4}.participant-row .el-avatar{background:#e8f4f2;color:#22746d;font-weight:600}.participant-action{display:flex;align-items:center;justify-content:flex-end;gap:5px}.participant-action .el-button{margin:0}@media(max-width:640px){.participant-row{grid-template-columns:auto minmax(0,1fr) auto}.participant-action{grid-column:2/-1;justify-content:flex-start}}
.evidence-dialog-summary{display:flex;align-items:center;gap:10px;margin-bottom:16px;color:#7a8794;font-size:13px}.evidence-dialog-summary span+span:before{margin-right:10px;color:#c3cbd3;content:'·'}.evidence-preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.evidence-preview-item{min-width:0;padding:10px;border:1px solid #e0e7ec;border-radius:10px;background:#f7f9fa}.evidence-preview-item>.el-image,.evidence-preview-item>video{display:block;width:100%;height:300px;border-radius:7px;background:#eef1f3}.evidence-preview-item>small{display:block;margin-top:8px;overflow:hidden;color:#75818d;text-overflow:ellipsis;white-space:nowrap}.evidence-file-card{display:flex;min-height:150px;align-items:center;justify-content:center;flex-direction:column;gap:12px;padding:20px;text-align:center}.evidence-file-card>.el-icon{color:#7e8c98;font-size:38px}.evidence-file-card>span{max-width:100%;overflow-wrap:anywhere;color:#4d5965}@media(max-width:640px){.evidence-dialog-summary{align-items:flex-start;flex-direction:column;gap:4px}.evidence-dialog-summary span+span:before{content:none}.evidence-preview-grid{grid-template-columns:1fr}.evidence-preview-item>.el-image,.evidence-preview-item>video{height:240px}}
.allocation-issue-list{display:flex;min-width:0;flex-direction:column;gap:7px}.allocation-issue-list p{display:flex;align-items:center;gap:8px;margin:0;color:#6f6250;line-height:1.45}.allocation-issue-list p span{min-width:0;overflow-wrap:anywhere}.allocation-issue-list .el-tag{flex:none}@media(max-width:640px){.allocation-issue-list p{align-items:flex-start;flex-direction:column;gap:4px}}
.project-progress-panel{border-color:#cfe2df;background:linear-gradient(145deg,#fff,#f5fbfa)}.project-progress-card{padding:16px;border:1px solid #d9e8e5;border-radius:11px;background:#fff}.project-progress-title,.project-progress-meta{display:flex;align-items:center;justify-content:space-between;gap:12px}.project-progress-title>span{display:flex;min-width:0;flex-direction:column;gap:4px}.project-progress-title small,.project-progress-meta{color:#7d8b96;font-size:12px}.project-progress-title strong{color:#167268;font-size:26px}.project-progress-card :deep(.el-progress){margin:16px 0}.project-progress-meta{margin-bottom:10px}.project-progress-summary{margin:0 0 12px!important;color:#4c5e69!important;font-size:14px!important;line-height:1.7}.project-progress-form :deep(.el-slider){padding:0 12px}.project-progress-form :deep(.el-slider__runway.show-input){margin-right:88px}.progress-tip{display:block;width:100%;margin-top:6px;color:#909399;font-size:12px}@media(max-width:640px){.project-progress-panel>.panel-head{align-items:stretch;flex-direction:column}.project-progress-title{align-items:flex-start}.project-progress-meta{align-items:flex-start;flex-direction:column}}
.task-latest-report{display:-webkit-box;margin:2px 0 0!important;overflow:hidden;color:#4c5e69!important;font-size:13px!important;line-height:1.55;-webkit-box-orient:vertical;-webkit-line-clamp:2}.task-latest-report span{color:#738392}.task-actions{align-items:center;gap:8px}.task-report-list{display:flex;max-height:65vh;flex-direction:column;gap:12px;overflow-y:auto;padding-right:4px}.task-report-row{padding:16px;border:1px solid #dfe6eb;border-radius:11px;background:#fbfcfd}.task-report-head,.task-report-footer{display:flex;align-items:center;justify-content:space-between;gap:14px}.task-report-head>span{display:flex;min-width:0;flex-direction:column;gap:4px}.task-report-head small,.task-report-footer small,.no-evidence{color:#8b97a4}.task-report-row>p{margin:13px 0!important;color:#415361!important;font-size:14px!important;line-height:1.7;white-space:pre-wrap}.no-evidence{font-size:12px}@media(max-width:640px){.task-report-head,.task-report-footer{align-items:flex-start;flex-direction:column}.task-report-footer .el-button{padding-left:0}.task-report-list{max-height:70vh}}
.leave-request-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:10px;padding:12px 2px;border-top:1px solid #edf0f2}.leave-request-row>span{display:flex;min-width:0;flex-direction:column;gap:4px}.leave-request-row>span small{overflow-wrap:anywhere;color:#8b97a4}.leave-request-files,.leave-review-comment{grid-column:1/-1}.leave-review-comment{color:#a06b22!important}@media(max-width:640px){.leave-request-row{grid-template-columns:1fr auto}.leave-request-row>.el-button{grid-column:2}.leave-request-files,.leave-review-comment{grid-column:1/-1}}
.task-group+.task-group{margin-top:18px}.task-group-head{display:flex;align-items:center;gap:8px;padding:7px 4px;border-bottom:1px solid #edf0f2}.task-group-head h3{margin:0;font-size:14px}.completed-task-group{padding-top:2px}.completed-task-card{background:#fbfdfc}.completed-task-actions{flex-wrap:wrap;justify-content:flex-end}@media(max-width:640px){.completed-task-actions{justify-content:flex-start}}
.pending-effort-panel{margin-bottom:14px;border-color:#efcf93;background:#fffdf8}.pending-effort-row{display:grid;grid-template-columns:minmax(190px,.8fr) auto minmax(180px,1fr) auto;align-items:center;gap:16px;padding:13px 4px;border-top:1px solid #f1e5ce}.pending-effort-row>span{display:flex;min-width:0;flex-direction:column;gap:4px}.pending-effort-row small{color:#8b7755}.pending-effort-row>p{margin:0;color:#7a633d;overflow-wrap:anywhere}.pending-effort-change{display:flex;align-items:center;gap:8px;color:#72592f;white-space:nowrap}.pending-effort-change b{color:#d28b1f}.pending-effort-actions{display:flex;gap:6px}.pending-effort-actions .el-button{margin:0}@media(max-width:900px){.pending-effort-row{grid-template-columns:1fr auto}.pending-effort-row>p{grid-column:1/-1}.pending-effort-actions{grid-column:1/-1;justify-content:flex-end}}@media(max-width:520px){.pending-effort-row{grid-template-columns:1fr}.pending-effort-row>p,.pending-effort-actions{grid-column:auto}.pending-effort-actions{display:grid;grid-template-columns:repeat(3,1fr)}.pending-effort-actions .el-button{width:100%}}
</style>
