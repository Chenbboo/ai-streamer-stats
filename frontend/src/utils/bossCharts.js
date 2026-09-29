const round = value => Math.round((value + Number.EPSILON) * 10000) / 10000
const amount = value => Number(value ?? 0)

export function chartCurrencies(data) {
  return [...new Set([...(data.trend || []), ...(data.projects || [])].map(row => row.currency))].sort()
}

export function buildBossChartData(data, currency) {
  const buckets = []
  if (data.dateFrom && data.dateTo) {
    const date = new Date(`${data.dateFrom}T00:00:00Z`)
    const end = data.monthly ? data.dateTo.slice(0, 7) : data.dateTo
    while (date.toISOString().slice(0, data.monthly ? 7 : 10) <= end) {
      buckets.push(date.toISOString().slice(0, data.monthly ? 7 : 10))
      if (data.monthly) date.setUTCMonth(date.getUTCMonth() + 1, 1)
      else date.setUTCDate(date.getUTCDate() + 1)
    }
  }
  const indexed = new Map((data.trend || []).filter(row => row.currency === currency).map(row => [row.bucket, row]))
  // An absent bucket is unknown, while an existing zero-valued result is a real zero.
  const trend = buckets.map(bucket => indexed.get(bucket) || null)
  const projects = (data.projects || []).filter(row => row.currency === currency)
  return { buckets, trend, ...buildProjectShare(projects, 'costAmount'), revenue: buildProjectShare(projects, 'revenueAmount') }
}

function buildProjectShare(rows, key) {
  const projects = rows.map(row => ({ ...row, [key]: amount(row[key]) }))
    .sort((a, b) => b[key] - a[key] || String(a.projectId).localeCompare(String(b.projectId)))
  const positive = projects.filter(row => row[key] > 0)
  const negative = projects.filter(row => row[key] < 0)
  const zero = projects.filter(row => row[key] === 0)
  const positiveTotal = round(positive.reduce((sum, row) => sum + row[key], 0))
  const netTotal = round(projects.reduce((sum, row) => sum + row[key], 0))
  const slices = positive.slice(0, 5).map(row => ({ ...row, value: row[key] }))
  const others = positive.slice(5)
  if (others.length) slices.push({ other: true, value: round(others.reduce((sum, row) => sum + row[key], 0)), members: others })
  return { projects, positiveTotal, netTotal, slices, negative, zero }
}
