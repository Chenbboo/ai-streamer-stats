import test from 'node:test'
import assert from 'node:assert/strict'
import { buildBossChartData, chartCurrencies } from './bossCharts.js'

test('missing days remain null and generated zero remains zero across leap day', () => {
  const data = { dateFrom: '2024-02-28', dateTo: '2024-03-01', trend: [
    { currency: 'CNY', bucket: '2024-02-29', revenueAmount: 0, costAmount: 0, profitAmount: 0 },
    { currency: 'VND', bucket: '2024-03-01', revenueAmount: 1000 }
  ] }
  const result = buildBossChartData(data, 'CNY')
  assert.deepEqual(result.buckets, ['2024-02-28', '2024-02-29', '2024-03-01'])
  assert.equal(result.trend[0], null)
  assert.equal(result.trend[1].profitAmount, 0)
  assert.equal(result.trend[2], null)
  assert.deepEqual(chartCurrencies(data), ['CNY', 'VND'])
})
test('calendar months retain their actual day count and current month cutoff', () => {
  const leap = buildBossChartData({ dateFrom: '2024-02-01', dateTo: '2024-02-29' }, 'CNY')
  assert.equal(leap.buckets.length, 29)
  assert.equal(leap.buckets.at(-1), '2024-02-29')
  const regular = buildBossChartData({ dateFrom: '2025-02-01', dateTo: '2025-02-28' }, 'CNY')
  assert.equal(regular.buckets.length, 28)
  const current = buildBossChartData({ dateFrom: '2026-09-01', dateTo: '2026-09-29' }, 'CNY')
  assert.equal(current.buckets.length, 29)
  assert.ok(current.trend.every(row => row === null))
})
test('top five and others conserve positive costs, separating reversals and currencies', () => {
  const projects = [7, 2, 5, 4, 1, 6, 3].map(projectId => ({ projectId, currency: 'CNY', costAmount: projectId }))
  projects.push({ projectId: 8, currency: 'CNY', costAmount: -3 }, { projectId: 9, currency: 'CNY', costAmount: 0 }, { projectId: 10, currency: 'VND', costAmount: 99999 })
  const result = buildBossChartData({ projects }, 'CNY')
  assert.deepEqual(result.slices.slice(0, 5).map(row => row.projectId), [7, 6, 5, 4, 3])
  assert.equal(result.slices[5].value, 3)
  assert.equal(result.positiveTotal, 28)
  assert.equal(result.netTotal, 25)
  assert.equal(result.negative[0].costAmount, -3)
  assert.equal(result.zero.length, 1)
})
test('multi-month trend crosses year and leaves missing months blank', () => {
  const result = buildBossChartData({ dateFrom: '2025-11-01', dateTo: '2026-01-02', monthly: true,
    trend: [{ currency: 'CNY', bucket: '2025-12', costAmount: 0, profitAmount: 0 }] }, 'CNY')
  assert.deepEqual(result.buckets, ['2025-11', '2025-12', '2026-01'])
  assert.equal(result.trend[0], null)
  assert.equal(result.trend[1].costAmount, 0)
  assert.equal(result.trend[2], null)
})

test('revenue shares use their own ranking and denominator, preserving refunds and real zero', () => {
  const projects = [1, 2, 3, 4, 5, 6, 7].map(projectId => ({ projectId, currency: 'CNY', revenueAmount: String(projectId * 10), costAmount: 100 - projectId }))
  projects.push({ projectId: 8, currency: 'CNY', revenueAmount: -30 }, { projectId: 9, currency: 'CNY', revenueAmount: 0 }, { projectId: 10, currency: 'VND', revenueAmount: 999999 })
  const result = buildBossChartData({ projects }, 'CNY')
  assert.equal(result.slices[0].projectId, 1)
  assert.deepEqual(result.revenue.slices.slice(0, 5).map(row => row.projectId), [7, 6, 5, 4, 3])
  assert.equal(result.revenue.slices[5].value, 30)
  assert.equal(result.revenue.positiveTotal, 280)
  assert.equal(result.revenue.netTotal, 250)
  assert.equal(result.revenue.negative[0].revenueAmount, -30)
  assert.equal(result.revenue.zero.length, 1)
  assert.equal(result.revenue.slices.reduce((sum, slice) => sum + slice.value, 0), result.revenue.positiveTotal)
  const zero = buildBossChartData({ projects: [{ projectId: 1, currency: 'CNY', revenueAmount: 0, costAmount: 20 }] }, 'CNY')
  assert.equal(zero.revenue.netTotal, 0)
  assert.deepEqual(zero.revenue.slices, [])
})
