// Public expenses are company obligations referenced by several projects.
// Keep them out of project totals rather than counting the same bill twice.
export const projectSettlementFields = ['pendingKpiCount', 'pendingEffortCount', 'pendingFactCount', 'pendingCostCount', 'pendingAwardCount', 'pendingLeaveCount']

export function projectSettlementCount(summary) {
  if (!summary?.projectId) return null
  return projectSettlementFields.reduce((total, field) => total + Math.max(0, Number(summary[field]) || 0), 0)
}

export function pendingKpiPlans(workspace) {
  return (workspace?.plans || []).filter(plan => ['DRAFT', 'PUBLISHED', 'CLOSED'].includes(plan.status) && plan.settlementStatus !== 'CONFIRMED')
}

export function reportedProjectProgress(project, month) {
  if (!project?.progressReportId || String(project.progressBizDate || '').slice(0, 7) !== month) return null
  const value = Number(project.progressPercent)
  return Number.isFinite(value) ? Math.min(100, Math.max(0, Math.round(value))) : null
}
