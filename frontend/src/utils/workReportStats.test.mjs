import test from 'node:test'
import assert from 'node:assert/strict'
import { buildWorkReportStats, workReportPeriodRange } from './workReportStats.js'

test('uses Monday through Sunday and calendar month boundaries', () => {
  assert.deepEqual(workReportPeriodRange('2026-09-23', 'WEEKLY'), { start: '2026-09-21', end: '2026-09-27' })
  assert.deepEqual(workReportPeriodRange('2026-09-23', 'MONTHLY'), { start: '2026-09-01', end: '2026-09-30' })
  assert.deepEqual(workReportPeriodRange('2026-09-23', 'DAILY'), { start: '2026-09-23', end: '2026-09-23' })
})

test('shows every active participant and only reports for the selected cycle', () => {
  const members = [
    { userId: 1, status: '0', memberRole: 'OWNER', joinedDate: '2026-01-01' },
    { userId: 2, status: '0', memberRole: 'MEMBER', joinedDate: '2026-01-01' },
    { userId: 3, status: '0', memberRole: 'MEMBER', joinedDate: '2026-01-01' },
    { userId: 4, status: '0', memberRole: 'MEMBER', joinedDate: '2026-10-01' },
    { userId: 5, status: '0', memberRole: 'OBSERVER' },
    { userId: 6, status: '0', memberRole: 'DEPUTY', joinedDate: '2026-01-01' }
  ]
  const reports = [
    { reportId: 7, submittedUserId: 2, frequency: 'WEEKLY', periodStart: '2026-09-21', periodEnd: '2026-09-27', status: 'PENDING' },
    { reportId: 8, submittedUserId: 2, frequency: 'WEEKLY', periodStart: '2026-09-21', periodEnd: '2026-09-27', status: 'APPROVED' },
    { reportId: 9, submittedUserId: 3, frequency: 'WEEKLY', periodStart: '2026-09-21', periodEnd: '2026-09-27', status: 'RETURNED' },
    { reportId: 10, submittedUserId: 1, frequency: 'DAILY', periodStart: '2026-09-23', periodEnd: '2026-09-23', status: 'APPROVED' }
  ]
  const result = buildWorkReportStats(members, reports, '2026-09-23', 'WEEKLY')
  assert.deepEqual(result.rows.map(row => row.state), ['REPORTED', 'RETURNED', 'NOT_JOINED', 'MISSING'])
  assert.equal(result.rows[0].report.reportId, 8)
  assert.equal(result.dueCount, 3)
  assert.equal(result.reportedCount, 1)
  assert.equal(result.returnedCount, 1)
  assert.equal(result.missingCount, 1)
})
