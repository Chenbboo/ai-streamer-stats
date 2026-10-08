import test from 'node:test'
import assert from 'node:assert/strict'
import { activeMonthlyRules, recordBonusBlockReason, bonusProfitBlockReason, currentBonusShares, profitShareAmount } from './bonusSummary.js'

test('monthly application choices never reuse cumulative or another month plans', () => {
  const rules = [
    { policyVersion: 'PROFIT_SHARE_V1', status: 'ACTIVE', ruleVersion: 1 },
    { policyVersion: 'PROFIT_SHARE_V1', status: 'ACTIVE', settlementMonth: '2026-08', ruleVersion: 2 },
    { policyVersion: 'PROFIT_SHARE_V1', status: 'ACTIVE', settlementMonth: '2026-09', ruleVersion: 3 },
    { policyVersion: 'PROFIT_SHARE_V1', status: 'RETIRED', settlementMonth: '2026-09', ruleVersion: 4 }
  ]
  assert.deepEqual(activeMonthlyRules(rules, '2026-09'), [rules[2]])
  assert.equal(currentBonusShares(rules, {}, '2026-09'), rules[2])
  assert.equal(currentBonusShares(rules, {}, '2026-10'), null)
  assert.deepEqual(activeMonthlyRules(rules), [])
})

test('payment and approval use their record month, not the newest month eligibility', () => {
  assert.equal(recordBonusBlockReason({ settlementMonth: '2026-09', bonusBlockReason: '' }, 'nonPositiveProfit'), '')
  assert.equal(recordBonusBlockReason({ bonusBlockReason: 'monthlyProfitChanged' }, ''), 'monthlyProfitChanged')
  assert.equal(recordBonusBlockReason({}, 'noProfitResult'), 'noProfitResult')
})

test('bonus setup and payment require current available profit greater than zero', () => {
  for (const afterTaxProfit of ['0.00', '0', '-0.01', '-8352.29']) assert.equal(bonusProfitBlockReason({ available: true, afterTaxProfit }), 'nonPositiveProfit')
  assert.equal(bonusProfitBlockReason({ available: true, afterTaxProfit: '0.01' }), '')
  assert.equal(bonusProfitBlockReason({ available: false, afterTaxProfit: '10000.00' }), 'noProfitResult')
  assert.equal(bonusProfitBlockReason(), 'noProfitResult')
  assert.equal(bonusProfitBlockReason({ available: true, afterTaxProfit: null }), 'noProfitResult')
})

test('overview uses the latest active profit rule instead of retired or KPI rules', () => {
  const rule = { policyVersion: 'PROFIT_SHARE_V1', status: 'ACTIVE', ruleVersion: 3, mainOwnerBonusRate: 10, sponsorOwnerBonusRate: 15 }
  assert.equal(currentBonusShares([
    { ...rule, ruleVersion: 8, status: 'RETIRED' },
    { ...rule, ruleVersion: 9, policyVersion: 'SCORE_TIERS_V1' },
    { ...rule, ruleVersion: 1 }, rule
  ], { version: 1, mainOwnerBonusRate: 20 }), rule)
})

test('saved project shares remain visible when there is no active profit rule', () => {
  const setting = { version: 1, mainOwnerBonusRate: 0, sponsorOwnerBonusRate: 5 }
  assert.equal(currentBonusShares([], setting), setting)
})

test('unconfigured shares are not displayed as an actual zero-percent setting', () => {
  assert.equal(currentBonusShares(), null)
  assert.equal(currentBonusShares([], { version: 0, mainOwnerBonusRate: 0 }), null)
})

test('overview calculates corresponding amounts for both owner percentages', () => {
  const result = { available: true, afterTaxProfit: '12345.67' }
  assert.equal(profitShareAmount(result, 40), '4938.27')
  assert.equal(profitShareAmount(result, 60), '7407.40')
  assert.equal(profitShareAmount(result, 0), '0.00')
})

test('bonus amounts round half cents up without floating-point rounding errors', () => {
  assert.equal(profitShareAmount({ available: true, afterTaxProfit: '1.15' }, '10'), '0.12')
  assert.equal(profitShareAmount({ available: true, afterTaxProfit: '99999999999999.99' }, '100'), '99999999999999.99')
  assert.equal(profitShareAmount({ available: true, afterTaxProfit: '10000.00' }, '0.1234'), '12.34')
})

test('losses produce zero while unknown results or percentages remain unavailable', () => {
  assert.equal(profitShareAmount({ available: true, afterTaxProfit: '-8352.29' }, 40), '0.00')
  assert.equal(profitShareAmount({ available: false, afterTaxProfit: 0 }, 40), null)
  assert.equal(profitShareAmount({ available: true, afterTaxProfit: '12.00' }, null), null)
})
