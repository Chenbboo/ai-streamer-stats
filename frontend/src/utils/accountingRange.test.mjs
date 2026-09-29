import test from 'node:test'
import assert from 'node:assert/strict'
import { accountingRangeFromQuery } from './accountingRange.js'

test('chart links retain cross-month and single-day ranges', () => {
  assert.deepEqual(accountingRangeFromQuery({ dateFrom: '2026-08-30', dateTo: '2026-09-28' }), ['2026-08-30', '2026-09-28'])
  assert.deepEqual(accountingRangeFromQuery({ dateFrom: '2026-09-28', dateTo: '2026-09-28' }), ['2026-09-28', '2026-09-28'])
})
test('ordinary monthly entry and malformed ranges fall back to month filtering', () => {
  for (const query of [{}, { month: '2026-09' }, { dateFrom: '2026-09-28' }, { dateFrom: '2026-02-30', dateTo: '2026-09-28' }, { dateFrom: '2026-09-29', dateTo: '2026-09-28' }, { dateFrom: ['2026-09-01'], dateTo: '2026-09-28' }])
    assert.equal(accountingRangeFromQuery(query), null)
})
