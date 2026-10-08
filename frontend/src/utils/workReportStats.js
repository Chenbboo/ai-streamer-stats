const dateOnly = value => String(value || '').slice(0, 10)

export function workReportPeriodRange(anchorDate, frequency) {
  const anchor = dateOnly(anchorDate)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(anchor)) return { start: '', end: '' }
  if (frequency === 'DAILY') return { start: anchor, end: anchor }
  const date = new Date(`${anchor}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return { start: '', end: '' }
  if (frequency === 'WEEKLY') {
    const mondayOffset = (date.getUTCDay() + 6) % 7
    date.setUTCDate(date.getUTCDate() - mondayOffset)
    const start = date.toISOString().slice(0, 10)
    date.setUTCDate(date.getUTCDate() + 6)
    return { start, end: date.toISOString().slice(0, 10) }
  }
  if (frequency === 'MONTHLY') {
    const year = date.getUTCFullYear()
    const month = date.getUTCMonth()
    return {
      start: new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 10),
      end: new Date(Date.UTC(year, month + 1, 0)).toISOString().slice(0, 10)
    }
  }
  return { start: '', end: '' }
}

export function buildWorkReportStats(members, reports, anchorDate, frequency) {
  const period = workReportPeriodRange(anchorDate, frequency)
  const latestByMember = new Map()
  for (const report of reports || []) {
    if (report.frequency !== frequency || dateOnly(report.periodStart) !== period.start || dateOnly(report.periodEnd) !== period.end) continue
    const key = String(report.submittedUserId)
    const previous = latestByMember.get(key)
    if (!previous || Number(report.reportId) > Number(previous.reportId)) latestByMember.set(key, report)
  }
  const rows = (members || [])
    .filter(member => member.status === '0' && ['DEPUTY', 'MEMBER'].includes(member.memberRole))
    .map(member => {
      const joinedAfterPeriod = dateOnly(member.joinedDate) > period.end
      const report = joinedAfterPeriod ? null : latestByMember.get(String(member.userId)) || null
      return { member, report, state: joinedAfterPeriod ? 'NOT_JOINED' : report?.status === 'RETURNED' ? 'RETURNED' : report ? 'REPORTED' : 'MISSING' }
    })
  return {
    ...period,
    rows,
    dueCount: rows.filter(row => row.state !== 'NOT_JOINED').length,
    reportedCount: rows.filter(row => row.state === 'REPORTED').length,
    returnedCount: rows.filter(row => row.state === 'RETURNED').length,
    missingCount: rows.filter(row => row.state === 'MISSING').length
  }
}
