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
      add(`allocation-${item.requestId}`, '确认跨项目人员投入', `${item.userName} · ${item.applicantName}发起 · ${item.effectiveDate}生效`, 'allocation-review', { item, urgent: true })
  }
  if (active && kpi && kpiWorkspace.canManage && can('business:kpi:manage')) {
    const hasPublishedPlan = (kpiWorkspace.plans || []).some(plan => plan.status !== 'VOIDED')
    if (!hasPublishedPlan)
      add('kpi-setup', '设置项目 KPI', '项目已启动，请设置指标并发布考核方案', 'kpi-settings', { urgent: true })
  }
  if (active && report && p.goalMode !== 'NO_TOTAL' && !data.todayProjectProgress)
    add('progress', '填报今日项目进度', today, 'progress')
  const dailyRevenue = data.accounting?.dailyRevenue
  const hasDailyRevenueRecord = Number(dailyRevenue?.confirmedCount || 0) + Number(dailyRevenue?.draftCount || 0) > 0
  if (executing && accountingOpen && p.companyDeptId && report && !hasDailyRevenueRecord)
    add('revenue', '填写今日收入', '无收入可直接确认', 'revenue', { allowZero: true })
  if (executing && accountingOpen && p.companyDeptId && report && !data.accounting?.dailySpend)
    add('spend', '填写今日花费', '无支出可直接确认', 'spend', { allowZero: true })
  else if (accountingOpen && p.companyDeptId && report && data.accounting?.dailySpend?.status === 'RETURNED')
    add('spend', '修改退回的花费', data.accounting.dailySpend.returnReason || '修改后重新确认', 'spend', { urgent: true })
  if (executing) {
    for (const r of data.todayRoutines || []) {
      if (r.activeStatus === 'VOID' || r.sourceManaged || day(r.startDate) > today || r.endDate && day(r.endDate) < today) continue
      const leave = (data.todayLeaves || []).some(item => same(item.userId, r.assigneeUserId))
      if (leave || r.todayReportId) continue
      if (active && manage && r.targetMode === 'DAILY_DYNAMIC' && !r.todayTargetId)
        add(`target-${r.routineId}`, '下达今日目标', r.routineName, 'target', { item: r })
      else if (report && p.companyDeptId && same(r.assigneeUserId, userId) && !(r.targetMode === 'DAILY_DYNAMIC' && !r.todayTargetId))
        add(`routine-${r.routineId}`, '填报持续工作', r.routineName, 'routine', { item: r })
    }
    for (const task of data.openTasks || []) {
      if (['DONE','CANCELED','CANCELLED'].includes(task.status) || task.activeStatus === 'VOID' || day(task.planStartDate) > today) continue
      if (report && p.companyDeptId && same(task.assigneeUserId, userId))
        add(`task-${task.taskId}`, task.taskName, task.dueDate ? `截止 ${day(task.dueDate)}` : '本人未完成任务', 'task', { urgent: !!task.dueDate && day(task.dueDate) < today })
      else if (active && manage && !task.assigneeUserId)
        add(`assign-${task.taskId}`, '分配任务负责人', task.taskName, 'project', { tab: 'tasks' })
    }
    if (can('business:project:edit')) for (const item of data.pendingEffortRequests || []) {
      if (same(item.projectId, p.projectId))
        add(`effort-${item.effortId}`, '确认人员投入申请', `${item.userName} · ${day(item.bizDate)} · ${item.actualPercent}%`, 'effort', { item })
    }
  }
  if (active && can('business:project:edit')) {
    if (p.budget?.endDate && day(p.budget.endDate) < today)
      add('budget', '续编项目预算', `上期截至 ${day(p.budget.endDate)}`, 'project', { tab: 'plan', urgent: true })
    for (const risk of p.risks || []) if (risk.status === 'OPEN' && same(risk.ownerUserId, userId))
      add(`risk-${risk.riskId}`, '处理项目风险', risk.riskTitle, 'project', { tab: 'risks', urgent: ['HIGH','CRITICAL'].includes(risk.severity) })
    if (p.closeMethod === 'STAGED_ACCEPTANCE') {
      if (!p.milestones?.length) add('milestones', '设置项目里程碑', '阶段验收需要明确交付节点', 'project', { tab: 'milestones' })
      for (const m of p.milestones || []) if (!['DONE','REVIEWING'].includes(m.status) && milestoneTasksReady(p.tasks, m.milestoneId))
        add(`milestone-${m.milestoneId}`, '完成阶段成果并提交验收', m.milestoneName, 'project', { tab: 'stageAcceptance', urgent: !!m.planDate && day(m.planDate) < today })
    }
    if (Number(p.progressPercent) >= 100)
      add('close', '办理项目结项', '项目进度已达 100%', 'project', { tab: p.closeMethod === 'RESULT_ACCEPTANCE' ? 'acceptance' : 'overview' })
  }
  if (accountingOpen && kpiWorkspace.canSettle && can('business:kpi:settle')) for (const plan of kpiWorkspace.plans || []) {
    if (plan.status !== 'VOIDED' && ['DRAFT','RETURNED'].includes(plan.settlementStatus) && day(plan.cycleEnd) && day(plan.cycleEnd) < today)
      add(`kpi-${plan.planId}`, '确认 KPI 结算结果', `第 ${plan.planVersion} 版 · 截至 ${day(plan.cycleEnd)}`, 'kpi', { planId: plan.planId })
  }
  return rows.sort((a, b) => Number(!!b.urgent) - Number(!!a.urgent))
}
