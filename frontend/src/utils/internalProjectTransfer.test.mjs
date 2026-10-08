import test from 'node:test'
import assert from 'node:assert/strict'
import { internalTransferProjectIssue } from './internalProjectTransfer.js'

const context = { projectId: 1, currency: 'CNY', bizDate: '2026-09-28' }
const target = { projectId: 2, currency: 'CNY', status: 'ACTIVE', companyDeptId: 110, accountingClosed: 0, actualStartDate: '2026-09-01' }
test('another active project with the same currency can receive income', () => {
  assert.equal(internalTransferProjectIssue(target, context), '')
  assert.equal(internalTransferProjectIssue({ ...target, currency: 'VND' }, { ...context, currency: 'VND' }), '')
})
test('missing, self, closed, unassigned, draft and different-currency projects cannot be chosen', () => {
  assert.ok(internalTransferProjectIssue(null, context))
  for (const changes of [{ projectId: '1' }, { accountingClosed: 1 }, { companyDeptId: null }, { status: 'DRAFT' }, { currency: 'VND' }])
    assert.ok(internalTransferProjectIssue({ ...target, ...changes }, context))
})
test('recipient execution dates bound historical transfers', () => {
  assert.ok(internalTransferProjectIssue({ ...target, actualStartDate: '2026-09-29' }, context))
  assert.ok(internalTransferProjectIssue({ ...target, status: 'CLOSED', actualEndDate: '2026-09-27' }, context))
  assert.equal(internalTransferProjectIssue({ ...target, status: 'CLOSED', actualEndDate: '2026-09-28' }, context), '')
})
