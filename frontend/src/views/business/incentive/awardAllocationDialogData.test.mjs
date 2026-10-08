import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse, compileScript } from 'vue/compiler-sfc'
import { awardAllocationDialogData } from './awardAllocationDialogData.js'
import scoreMessages from './scoreMessages.js'

test('distribution dialog filters source and member allocations to one application', () => {
  const award = { awardId: 3, ruleName: '奖金方案', policyVersion: 'PROFIT_SHARE_V1', ruleMainOwnerBonusAmount: 800, currency: 'CNY' }
  const source = { awardId: 3, ruleName: '奖金方案', sourceAmount: 800, reserved: 300, remaining: 500, currency: 'CNY' }
  const matching = { allocationId: 9, awardId: 3, lines: [{ userName: '甲', amount: 300 }] }
  const result = awardAllocationDialogData(award, {
    awards: [source, { awardId: 4, sourceAmount: 100 }],
    allocations: [matching, { allocationId: 10, awardId: 4, lines: [] }]
  })
  assert.deepEqual(result.awards, [source])
  assert.deepEqual(result.allocations, [matching])
})

test('draft applications show their frozen source without inventing occupied allocation', () => {
  const award = { awardId: 7, ruleName: '历史奖金', amount: 200, currency: 'CNY', scoreSnapshot: null }
  const result = awardAllocationDialogData(award, {})
  assert.equal(result.awards[0].sourceAmount, 200)
  assert.equal(result.awards[0].reserved, 0)
  assert.equal(result.awards[0].remaining, 200)
  assert.deepEqual(result.allocations, [])
  assert.deepEqual(awardAllocationDialogData(null, {}), { awards: [], allocations: [] })
})

test('allocation details dialog shows member allocation without the source summary', async () => {
  const page = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
  assert.ok(page.includes(':title="t(\'allocationDetails\')"'))
  assert.ok(!page.includes('<DistributionSources'))
  assert.ok(page.includes('<AwardAllocationDetails v-if="allocationDetails.applicationAllocation"'))
  assert.ok(page.includes('<el-empty v-else :description="t(\'noMemberAllocation\')" />'))
  for (const locale of ['zh-CN', 'vi-VN']) {
    assert.ok(scoreMessages[locale].memberAllocationColumn)
    assert.ok(scoreMessages[locale].allocationDetails)
  }
  const { descriptor, errors } = parse(page)
  assert.deepEqual(errors, [])
  assert.doesNotThrow(() => compileScript(descriptor, { id: 'award-allocation-dialog-test', inlineTemplate: true }))
})
