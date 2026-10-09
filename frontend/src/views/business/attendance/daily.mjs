export const parseSource = (value, fallback) => { try { return JSON.parse(value || 'null') || fallback } catch { return fallback } }
export const attendanceCodes = day => [...new Set(day.punches.flatMap(p => [p.checkInResult, p.checkOutResult]))]
// Explain a missing punch only when an approved outing covers that side's scheduled time.
// This is a display projection; keep the Feishu source results for audit and source filters.
export function punchDisplayResult(day, punch, side) {
  const original = side === 'in' ? punch.checkInResult : punch.checkOutResult
  if (original !== 'Lack') return original
  const scheduled = side === 'in' ? punch.scheduledIn : punch.scheduledOut
  if (!Number.isFinite(scheduled) || scheduled <= 0) return original
  const covered = day.outings?.some(outing => outing.status === 'CONFIRMED' && outing.sourceQuality === 'KNOWN'
    && Array.isArray(outing.intervals) && outing.intervals.some(interval => {
      if (!Array.isArray(interval) || interval.length !== 2) return false
      const [start,end] = interval
      return Number.isFinite(start) && Number.isFinite(end) && end > start
        && (side === 'in' ? start <= scheduled && scheduled < end : start < scheduled && scheduled <= end)
    }))
  return covered ? 'APPROVED_OUT' : original
}
export function displayAttendanceCodes(day) {
  const codes = [...new Set(day.punches.flatMap(p => [punchDisplayResult(day,p,'in'),punchDisplayResult(day,p,'out')]))]
  return codes.includes('APPROVED_OUT') ? codes.filter(code => !['Normal','NoNeedCheck','SystemCheck'].includes(code)) : codes
}
// Use explicit source results only: Todo, missing data and stale snapshots are not absence findings.
export function matchesAttendanceStatus(day, status) {
  if (status === 'ALL') return true
  const codes = attendanceCodes(day)
  return status === 'ABNORMAL'
    ? codes.some(code => ['Late', 'Early', 'Lack'].includes(code))
    : codes.includes(status)
}
export function dailyAttendance(records) {
  const days = new Map()
  for (const source of records) {
    if (Number(source.isCurrent) !== 1) continue
    const key = `${source.userId}:${source.businessDate}`
    if (!days.has(key)) days.set(key, {key, userName: source.userName || source.userId, date: source.businessDate, punches: [], shifts: [], leaves: [], outings: [], remedies: [], warnings: []})
    const day = days.get(key), zone = source.sourceTimezone || 'Asia/Shanghai'
    const details = parseSource(source.detailsJson, {})
    if (source.quality !== 'KNOWN') day.warnings.push(source.quality || 'UNKNOWN')
    if (source.kind === 'ATTENDANCE') day.punches.push(...(details.results || []).map(p => ({...p, zone})))
    if (source.kind === 'SHIFT') day.shifts.push(...parseSource(source.intervalsJson, []).map(p => ({start:p[0], end:p[1], zone})))
    if (source.kind === 'LEAVE') day.leaves.push({status:source.normalizedStatus, seconds:source.sourceDurationSeconds, intervals:parseSource(source.intervalsJson, []), zone})
    if (source.kind === 'OUT') day.outings.push({status:source.normalizedStatus, seconds:source.sourceDurationSeconds, intervals:parseSource(source.intervalsJson, []), zone, sourceQuality:source.sourceQuality || source.quality})
    if (source.kind === 'REMEDY') day.remedies.push({status:source.normalizedStatus, time:details.remedyTime})
  }
  return [...days.values()]
    .filter(day => day.outings.length || !(day.punches.length && day.punches.every(p => p.checkInResult === 'NoNeedCheck' && p.checkOutResult === 'NoNeedCheck')))
    .sort((a,b) => b.date.localeCompare(a.date) || String(a.userName).localeCompare(String(b.userName)))
}
