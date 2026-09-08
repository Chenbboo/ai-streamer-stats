export const parseSource = (value, fallback) => { try { return JSON.parse(value || 'null') || fallback } catch { return fallback } }
export function dailyAttendance(records) {
  const days = new Map()
  for (const source of records) {
    if (Number(source.isCurrent) !== 1) continue
    const key = `${source.userId}:${source.businessDate}`
    if (!days.has(key)) days.set(key, {key, userName: source.userName || source.userId, date: source.businessDate, punches: [], shifts: [], leaves: [], remedies: [], warnings: []})
    const day = days.get(key), zone = source.sourceTimezone || 'Asia/Shanghai'
    const details = parseSource(source.detailsJson, {})
    if (source.quality !== 'KNOWN') day.warnings.push(source.quality || 'UNKNOWN')
    if (source.kind === 'ATTENDANCE') day.punches.push(...(details.results || []).map(p => ({...p, zone})))
    if (source.kind === 'SHIFT') day.shifts.push(...parseSource(source.intervalsJson, []).map(p => ({start:p[0], end:p[1], zone})))
    if (source.kind === 'LEAVE') day.leaves.push({status:source.normalizedStatus, seconds:source.sourceDurationSeconds, intervals:parseSource(source.intervalsJson, []), zone})
    if (source.kind === 'REMEDY') day.remedies.push({status:source.normalizedStatus, time:details.remedyTime})
  }
  return [...days.values()]
    .filter(day => !(day.punches.length && day.punches.every(p => p.checkInResult === 'NoNeedCheck' && p.checkOutResult === 'NoNeedCheck')))
    .sort((a,b) => b.date.localeCompare(a.date) || String(a.userName).localeCompare(String(b.userName)))
}
