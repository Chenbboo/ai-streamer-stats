import test from 'node:test'
import assert from 'node:assert/strict'
import { sumExpenseAmounts, normalizeBusinessBudget, rebaseBusinessBudget } from './proposalBudget.js'

test('business budget sums all expense rows regardless of period or frequency', () => {
  assert.equal(sumExpenseAmounts([
    { amount: 0.1, occurDate: '2026-08-01' },
    { amount: 0.2, occurDate: '2026-12-01' },
    { amount: 100, occurrenceType: 'MONTHLY' },
    { amount: null }
  ]), 100.3)
  assert.equal(sumExpenseAmounts([]), 0)
})

test('business budget allows additional funds but cannot go below expenses', () => {
  assert.equal(normalizeBusinessBudget(900, 1000), 1000)
  assert.equal(normalizeBusinessBudget(1200, 1000), 1200)
  assert.equal(normalizeBusinessBudget(null, 1000), 1000)
})

test('expense edits and removals preserve only the additional allowance', () => {
  assert.equal(rebaseBusinessBudget(1000, 1000, 1500), 1500)
  assert.equal(rebaseBusinessBudget(1200, 1000, 1500), 1700)
  assert.equal(rebaseBusinessBudget(1200, 1000, 500), 700)
  assert.equal(rebaseBusinessBudget(1000, 1000, 0), 0)
})
