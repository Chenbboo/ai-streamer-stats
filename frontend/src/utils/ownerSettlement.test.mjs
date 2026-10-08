import test from 'node:test'
import assert from 'node:assert/strict'
import { projectSettlementCount, pendingKpiPlans, reportedProjectProgress } from './ownerSettlement.js'

test('project totals include KPI and effort, while shared company bills are counted separately', () => {
  assert.equal(projectSettlementCount({ projectId: 12, pendingKpiCount: 1, pendingPublicExpenseCount: 1 }), 1)
  assert.equal(projectSettlementCount({ projectId: 14, pendingKpiCount: 1, pendingPublicExpenseCount: 1 }), 1)
  assert.equal(projectSettlementCount({ projectId: 12, pendingKpiCount: 2, pendingEffortCount: '3', pendingFactCount: 4, pendingCostCount: 5, pendingAwardCount: 6, pendingLeaveCount: 1, pendingPublicExpenseCount: 99 }), 21)
  assert.equal(projectSettlementCount({}), null)
  assert.equal(projectSettlementCount({ projectId: 12 }), 0)
})

test('KPI queue follows the existing SQL closure definition, including older draft and closed plans', () => {
  const plans = [
    { planId: 1, status: 'DRAFT', settlementStatus: 'DRAFT' },
    { planId: 2, status: 'PUBLISHED', settlementStatus: 'RETURNED' },
    { planId: 3, status: 'CLOSED', settlementStatus: 'SUBMITTED' },
    { planId: 4, status: 'CLOSED', settlementStatus: 'CONFIRMED' },
    { planId: 5, status: 'VOIDED', settlementStatus: 'DRAFT' }
  ]
  assert.deepEqual(pendingKpiPlans({ plans }).map(plan => plan.planId), [1, 2, 3])
})

test('a missing or old monthly report stays unknown; a real zero report stays zero', () => {
  assert.equal(reportedProjectProgress({ progressPercent: 0 }, '2026-09'), null)
  assert.equal(reportedProjectProgress({ progressReportId: 9, progressBizDate: '2026-08-31', progressPercent: 100 }, '2026-09'), null)
  assert.equal(reportedProjectProgress({ progressReportId: 10, progressBizDate: '2026-09-20', progressPercent: 75 }, '2026-09'), 75)
  assert.equal(reportedProjectProgress({ progressReportId: 11, progressBizDate: '2026-09-28', progressPercent: 0 }, '2026-09'), 0)
})
