import { translateText } from '../locales/translate.js'
export function milestoneTasksReady(tasks = [], milestoneId) {
  const linkedTasks = tasks.filter(task => task.activeStatus !== 'VOID' && String(task.milestoneId) === String(milestoneId))
  return linkedTasks.length > 0 && linkedTasks.every(task => task.status === 'DONE' || Number(task.progress) >= 100)
}

// Owner actions for the selected project. Approval queues belong to their reviewers.
export function buildOwnerTodos({ data = {}, userId, today, permissions = [], kpi = null }) {
  const p = data.project
  if (!p) return []
  const rows = [], same = (a, b) => a != null && b != null && String(a) === String(b)
  const kpiWorkspace = kpi || {}
  const can = key => permissions.includes('*:*:*') || permissions.includes(key)
  const active = p.status === 'ACTIVE'
  const executing = ['ACTIVE', 'ACCEPTANCE'].includes(p.status)
  const accountingOpen = p.accountingState !== 'CLOSED'
  const report = can('business:project:report')
  const manage = can('business:project:task')
  const day = value => String(value || '').slice(0, 10)
  const add = (key, title, detail, action, extra = {}) => rows.push({ key, title, detail, action, ...extra })
  for (const item of data.pendingAllocationRequests || []) {
    if (same(item.projectId, p.projectId))
      add(`allocation-${item.requestId}`, translateText("确认跨项目人员投入"), translateText("{0} · {1}发起 · {2}生效", [item.userName, item.applicantName, item.effectiveDate]), 'allocation-review', { item, urgent: true })
  }
  if (active && kpi && kpiWorkspace.canManage && can('business:kpi:manage')) {
    const hasPublishedPlan = (kpiWorkspace.plans || []).some(plan => plan.status !== 'VOIDED')
    if (!hasPublishedPlan)
      add('kpi-setup', translateText("设置项目 KPI"), translateText("项目已启动，请设置指标并发布考核方案"), 'kpi-settings', { urgent: true })
  }
  if (active && report && p.goalMode !== 'NO_TOTAL' && !data.todayProjectProgress)
    add('progress', translateText("填报今日项目进度"), today, 'progress')
  const dailyRevenue = data.accounting?.dailyRevenue
  const hasDailyRevenueRecord = Number(dailyRevenue?.confirmedCount || 0) + Number(dailyRevenue?.draftCount || 0) > 0
  if (executing && accountingOpen && p.companyDeptId && report && !hasDailyRevenueRecord)
    add('revenue', translateText("填写今日收入"), translateText("无收入可直接确认"), 'revenue', { allowZero: true })
  if (executing && accountingOpen && p.companyDeptId && report && !data.accounting?.dailySpend)
    add('spend', translateText("填写今日花费"), translateText("无支出可直接确认"), 'spend', { allowZero: true })
  else if (accountingOpen && p.companyDeptId && report && data.accounting?.dailySpend?.status === 'RETURNED')
    add('spend', translateText("修改退回的花费"), data.accounting.dailySpend.returnReason || translateText("修改后重新确认"), 'spend', { urgent: true })
  if (executing) {
    for (const r of data.todayRoutines || []) {
      if (r.activeStatus === 'VOID' || r.sourceManaged || day(r.startDate) > today || r.endDate && day(r.endDate) < today) continue
      const leave = (data.todayLeaves || []).some(item => same(item.userId, r.assigneeUserId))
      if (leave || r.todayReportId) continue
      if (active && manage && r.targetMode === 'DAILY_DYNAMIC' && !r.todayTargetId)
        add(`target-${r.routineId}`, translateText("下达今日目标"), r.routineName, 'target', { item: r })
      else if (report && p.companyDeptId && same(r.assigneeUserId, userId) && !(r.targetMode === 'DAILY_DYNAMIC' && !r.todayTargetId))
        add(`routine-${r.routineId}`, translateText("填报持续工作"), r.routineName, 'routine', { item: r })
    }
    for (const task of data.openTasks || []) {
      if (['DONE','CANCELED','CANCELLED'].includes(task.status) || task.activeStatus === 'VOID' || day(task.planStartDate) > today) continue
      if (report && p.companyDeptId && same(task.assigneeUserId, userId))
        add(`task-${task.taskId}`, task.taskName, task.dueDate ? translateText("截止 {0}", [day(task.dueDate)]) : translateText("本人未完成任务"), 'task', { urgent: !!task.dueDate && day(task.dueDate) < today })
      else if (active && manage && !task.assigneeUserId)
        add(`assign-${task.taskId}`, translateText("分配任务负责人"), task.taskName, 'project', { tab: 'tasks' })
    }
    if (can('business:project:edit')) for (const item of data.pendingEffortRequests || []) {
      if (same(item.projectId, p.projectId))
        add(`effort-${item.effortId}`, translateText("确认人员投入申请"), `${item.userName} · ${day(item.bizDate)} · ${item.actualPercent}%`, 'effort', { item })
    }
  }
  if (active && can('business:project:edit')) {
    if (p.budget?.endDate && day(p.budget.endDate) < today)
      add('budget', translateText("续编项目预算"), translateText("上期截至 {0}", [day(p.budget.endDate)]), 'project', { tab: 'plan', urgent: true })
    for (const risk of p.risks || []) if (risk.status === 'OPEN' && same(risk.ownerUserId, userId))
      add(`risk-${risk.riskId}`, translateText("处理项目风险"), risk.riskTitle, 'project', { tab: 'risks', urgent: ['HIGH','CRITICAL'].includes(risk.severity) })
    if (p.closeMethod === 'STAGED_ACCEPTANCE') {
      if (!p.milestones?.length) add('milestones', translateText("设置项目里程碑"), translateText("阶段验收需要明确交付节点"), 'project', { tab: 'milestones' })
      for (const m of p.milestones || []) if (!['DONE','REVIEWING'].includes(m.status) && milestoneTasksReady(p.tasks, m.milestoneId))
        add(`milestone-${m.milestoneId}`, translateText("完成阶段成果并提交验收"), m.milestoneName, 'project', { tab: 'stageAcceptance', urgent: !!m.planDate && day(m.planDate) < today })
    }
    if (Number(p.progressPercent) >= 100)
      add('close', translateText("办理项目结项"), translateText("项目进度已达 100%"), 'project', { tab: p.closeMethod === 'RESULT_ACCEPTANCE' ? 'acceptance' : 'overview' })
  }
  if (accountingOpen && kpiWorkspace.canSettle && can('business:kpi:settle')) for (const plan of kpiWorkspace.plans || []) {
    if (plan.status !== 'VOIDED' && ['DRAFT','RETURNED'].includes(plan.settlementStatus) && day(plan.cycleEnd) && day(plan.cycleEnd) < today)
      add(`kpi-${plan.planId}`, translateText("确认 KPI 结算结果"), translateText("第 {0} 版 · 截至 {1}", [plan.planVersion, day(plan.cycleEnd)]), 'kpi', { planId: plan.planId })
  }
  return rows.sort((a, b) => Number(!!b.urgent) - Number(!!a.urgent))
}
export function buildPublicExpenseTodos(bills = []) {
  const seen = new Set()
  return bills.filter(bill => {
    if (bill.billStatus !== 'PUBLISHED' || bill.status === 'SUBMITTED' || bill.allocationId == null || seen.has(String(bill.allocationId))) return false
    seen.add(String(bill.allocationId))
    return true
  }).map(bill => ({
    key: `public-expense-${bill.allocationId}`,
    title: Number(bill.remainingAmount) > 0 ? translateText("分摊公共费用") : translateText("提交公共费用分摊"),
    detail: `${bill.companyName || translateText("公司公共费用")} · ${bill.month} · ${Number(bill.remainingAmount) > 0 ? translateText("待分摊") : translateText("待提交")} ${Number(Number(bill.remainingAmount) > 0 ? bill.remainingAmount : bill.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${bill.currency}`,
    action: 'public-expense', projectName: translateText("公司公共费用"), allocationId: bill.allocationId, month: bill.month
  }))
}

// Cross-project reviews belong to the reviewer, regardless of the selected project.
export function buildAllocationReviewTodos(requests = [], projects = []) {
  const seen = new Set()
  return requests.filter(item => {
    if (item.requestId == null || item.projectId == null || item.status && item.status !== 'PENDING' || seen.has(String(item.requestId))) return false
    seen.add(String(item.requestId))
    return true
  }).map(item => ({
    key: `allocation-${item.requestId}`, title: translateText("确认人员投入调整"),
    detail: translateText("{0}发起 · 调整人员：{1} · {2}生效", [item.applicantName, item.userName, item.effectiveDate]),
    action: 'allocation-review', urgent: true, item, projectId: item.projectId,
    projectName: projects.find(project => String(project.projectId) === String(item.projectId))?.projectName || translateText("跨项目投入调整")
  }))
}

// A handed-off child proposal is not a formal project yet, but it is already the
// assigned owner's responsibility and therefore belongs in the owner workbench.
export function buildProposalHandoffTodos(proposals = [], userId) {
  const same = (a, b) => a != null && b != null && String(a) === String(b)
  return proposals.filter(item => item?.parentProjectId && item.status === 'DRAFT'
    && same(item.assignedOwnerUserId, userId) && item.canEdit !== false)
    .map(item => ({
      key: `proposal-handoff-${item.proposalId}`,
      title: translateText("子项目待你完善并启动"),
      detail: translateText("{0}已转交 · {1} 至 {2}", [item.parentProjectName || translateText("主项目"), item.planStartDate || translateText("开始日期待完善"), item.planEndDate || translateText("不限期")]),
      action: 'proposal-handoff',
      urgent: true,
      proposalId: item.proposalId,
      projectName: item.projectName || translateText("待完善子项目")
    }))
}

// Child projects remain owned by their child owner, so their acceptance reviews are
// cross-project duties of the parent project's current main owner.
export function buildChildAcceptanceTodos(reviews = []) {
  return reviews.map(item => {
    const stage = item.reviewType === 'STAGE_ACCEPTANCE'
    const result = item.reviewType === 'RESULT_ACCEPTANCE'
    return {
      key: `child-acceptance-${item.reviewType}-${item.projectId}-${item.milestoneId || 'project'}`,
      title: stage ? translateText("验收子项目阶段成果") : result ? translateText("验收子项目成果") : translateText("审核子项目结项"),
      detail: stage
        ? translateText("{0}提交 · {1}", [item.childOwnerName || translateText("子项目负责人"), item.milestoneName || translateText("未命名里程碑")])
        : translateText("{0}提交 · 归属{1}", [item.childOwnerName || translateText("子项目负责人"), item.parentProjectName || translateText("主项目")]),
      action: 'child-acceptance',
      tab: stage ? 'stageAcceptance' : result ? 'acceptance' : 'overview',
      urgent: true,
      projectId: item.projectId,
      projectName: item.projectName || translateText("子项目")
    }
  })
}
