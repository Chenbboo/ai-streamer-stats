import test from 'node:test'
import assert from 'node:assert/strict'
import { workExecutionPeriods } from './projectExecutionPeriods.js'

const history = [
  { periodId: 1, assigneeName: '石头', startDate: '2026-10-08', endDate: '2026-10-08', status: 'CLOSED' },
  { periodId: 2, assigneeName: '周宁', startDate: '2026-10-08', endDate: null, status: 'ACTIVE' }
]
test('active routine uses the edited end date while previous assignee history stays unchanged', () => {
  const row = { routineId: 10, status: 'ACTIVE', endDate: '2026-10-31', executionPeriods: history }
  const periods = workExecutionPeriods(row, '2026-10-08')
  assert.equal(periods[0].endDate, '2026-10-08')
  assert.equal(periods[1].endDate, '2026-10-31')
  assert.equal(history[1].endDate, null)
  row.endDate = '2026-11-30'
  assert.equal(workExecutionPeriods(row, '2026-10-08')[1].endDate, '2026-11-30')
})
test('long term routines retain an open interval instead of ending today', () => {
  const periods = workExecutionPeriods({ routineId: 10, executionPeriods: history }, '2026-10-08')
  assert.equal(periods[1].endDate, null)
  assert.equal(periods[1].longTerm, true)
  assert.equal(periods[0].longTerm, undefined)
})
test('retired routine history is not overwritten by its planned end date', () => {
  const periods = workExecutionPeriods({ routineId: 10, status: 'VOID', endDate: '2026-10-31', executionPeriods: [history[0]] }, '2026-10-08')
  assert.equal(periods[0].endDate, '2026-10-08')
})
test('legacy routines without period records also use the edited interval', () => {
  const row = { routineId: 10, startDate: '2026-10-08', endDate: '2026-10-31', assigneeName: '周宁' }
  assert.equal(workExecutionPeriods(row, '2026-10-08')[0].endDate, '2026-10-31')
  assert.equal(workExecutionPeriods({ ...row, endDate: null }, '2026-10-08')[0].longTerm, true)
})
test('ongoing task periods retain their actual history rather than treating the deadline as a stop date', () => {
  const periods = workExecutionPeriods({ taskId: 10, dueDate: '2026-10-31', executionPeriods: history }, '2026-10-08')
  assert.equal(periods[0].endDate, '2026-10-08')
  assert.equal(periods[1].endDate, null)
  assert.equal(periods[1].ongoing, true)
  assert.equal(periods[1].longTerm, undefined)
})
