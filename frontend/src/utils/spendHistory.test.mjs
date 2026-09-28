import test from 'node:test'
import assert from 'node:assert/strict'
import { spendHistoryQuery, spendPersonnelDescription, spendExpenseItems } from './spendHistory.js'

const money = value => Number(value || 0).toFixed(2)
const translate = (value, params = []) => value.replace(/\{(\d+)\}/g, (_, index) => params[index])
test('month and date queries never carry conflicting filters', () => {
  assert.deepEqual(spendHistoryQuery('month', '2024-02', '2024-02-29'), { month: '2024-02' })
  assert.deepEqual(spendHistoryQuery('date', '2024-02', '2024-02-29'), { bizDate: '2024-02-29' })
})
test('personnel details show rates and allocation without displaying raw JSON', () => {
  assert.equal(spendPersonnelDescription('{"fullDailyCost":100,"allocationPercent":25}', 'CNY', translate, money), '日成本：100.00 CNY · 项目投入：25%')
  assert.equal(spendPersonnelDescription('{"fullDailyCost":200000,"allocationPercent":100,"attendanceAdjustment":{}}', 'VND', translate, money), '日成本：200000.00 VND · 项目投入：100% · 含请假调整')
})
test('missing prices and legacy calculation descriptions remain explicit', () => {
  assert.equal(spendPersonnelDescription(null, 'CNY', translate, money), '—')
  assert.equal(spendPersonnelDescription('计划投入 25%', 'CNY', translate, money), '计划投入 25%')
  assert.equal(spendPersonnelDescription('{"issue":"缺少有效用人成本"}', 'CNY', translate, money), '缺少有效用人成本')
})
test('public estimates are shown inside public cost rather than added again', () => {
  const row = { expenseItems: [{ amount: 80, categoryName: '内部项目支出' }], publicCost: 5, publicEstimatedCost: 2 }
  const items = spendExpenseItems(row, 'CNY', translate, money)
  assert.equal(items.length, 2)
  assert.equal(items.reduce((sum, item) => sum + Number(item.amount), 0), 85)
  assert.match(items[1].description, /2.00 CNY/)
  assert.equal(row.expenseItems.length, 1)
})
test('zero days have no invented expense and reversal amounts stay negative', () => {
  assert.deepEqual(spendExpenseItems({}, 'VND', translate, money), [])
  const row = { expenseItems: [{ amount: 30, status: 'REVERSED' }, { amount: -30, status: 'CONFIRMED' }] }
  assert.equal(spendExpenseItems(row, 'CNY', translate, money).reduce((sum, item) => sum + item.amount, 0), 0)
})
