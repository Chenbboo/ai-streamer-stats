export function personnelDistribution(rows = [], top = 5) {
  const positive = rows.filter(row => Number(row.amount) > 0).sort((a, b) => Number(b.amount) - Number(a.amount))
  const total = positive.reduce((sum, row) => sum + Number(row.amount), 0)
  const slices = positive.slice(0, top).map(row => ({ ...row, value: Number(row.amount) }))
  if (positive.length > top) slices.push({ other: true, userName: '其他人员', value: positive.slice(top).reduce((sum, row) => sum + Number(row.amount), 0), members: positive.slice(top) })
  return { total, slices, negative: rows.filter(row => Number(row.amount) < 0) }
}

export function budgetUsage(row) {
  const known = row.limit != null && row.comparable !== false && !Number(row.pendingCount)
  const limit = Number(row.limit), used = Number(row.used)
  const percent = known && limit > 0 ? used / limit * 100 : null
  return { known, percent, progress: percent == null ? 0 : Math.max(0, Math.min(100, percent)), exceeded: known && used > limit }
}
