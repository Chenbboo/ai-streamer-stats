export function canReportProgress(project, userId) {
  return !!project?.parentId && String(project.mainOwnerUserId) === String(userId)
    && ['ACTIVE', 'PAUSED'].includes(project.status)
}
export function taskCompletion(tasks = []) {
  const eligible = tasks.filter(task => task.status !== 'CANCELED')
  const done = eligible.filter(task => task.status === 'DONE').length
  return { total: eligible.length, done, percent: eligible.length ? Math.round(done * 100 / eligible.length) : 0 }
}
export function readProgressSnapshot(report) {
  try { return JSON.parse(report.snapshotJson || 'null') } catch { return null }
}
export function progressEventTarget(event) {
  if (!['SUBPROJECT_PROGRESS','PROJECT_PROGRESS'].includes(event?.eventType)) return null
  const match = /^\[子项目:(\d+)\]\[汇报:(\d+)\]/.exec(event.comment || '')
  return match ? { projectId: Number(match[1]), reportId: Number(match[2]) } : null
}

// The server selects the current calendar month's report. A real 0% report is
// different from its 0% fallback when no report exists; never infer progress from tasks.
export function monthlyProgressPercent(project) {
  if (project?.status === 'CLOSED') return 100
  if (project?.status === 'CANCELED') return 0
  if (!project?.progressReportId || project.progressPercent == null || project.progressPercent === '') return null
  const value = Number(project.progressPercent)
  return Number.isFinite(value) ? Math.min(100, Math.max(0, Math.round(value))) : null
}
