import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse, compileScript } from 'vue/compiler-sfc'
import { emptyAllocationProposal, hasAllocationProposal, proposalLineAmount, proposalPercentTotal, proposalTotal, proposalError, proposalPayload } from './allocationProposal.js'
import distributionMessages from './distributionMessages.js'

const filled = () => ({ mode: 'AMOUNT', reason: '分配依据', lines: [{ userId: 9, amount: 40, percentage: null, reason: '负责人贡献' }] })
test('blank allocation preserves award-only applications and never creates a phantom batch', () => {
  const proposal = emptyAllocationProposal()
  assert.equal(hasAllocationProposal(proposal), false)
  assert.equal(proposalError(proposal, null), null)
  assert.equal(proposalPayload(proposal), null)
})
test('owner source caps amounts and totals, and incomplete or duplicate recipients are rejected', () => {
  const proposal = filled()
  assert.equal(proposalTotal(proposal, 40), '40.00')
  assert.equal(proposalError(proposal, 40), null)
  assert.equal(proposalError(proposal, 39.99), 'invalid')
  proposal.lines.push({ ...proposal.lines[0], amount: 0.01 })
  assert.equal(proposalError(proposal, 100), 'invalid')
  proposal.lines = [{ userId: null, amount: 1, reason: '' }]
  assert.equal(proposalError(proposal, 100), 'required')
})
test('member percentages must total 100% of the owner share and strip inactive values', () => {
  const proposal = filled(); proposal.mode = 'PERCENT'; proposal.lines[0].percentage = 10
  assert.equal(proposalLineAmount(proposal, proposal.lines[0], '1.15'), '0.12')
  assert.equal(proposalError(proposal, '1.15'), 'percentTotal')
  proposal.lines[0].percentage = 100
  assert.equal(proposalPercentTotal(proposal), 100)
  assert.equal(proposalLineAmount(proposal, proposal.lines[0], '1.15'), '1.15')
  assert.equal(proposalTotal(proposal, '1.15'), '1.15')
  assert.equal(proposalError(proposal, '1.15'), null)
  assert.equal(proposalPayload(proposal).lines[0].amount, null)
  assert.equal(proposalPayload(proposal).lines[0].percentage, 100)
  proposal.lines[0].percentage = 100.01
  assert.equal(proposalError(proposal, 100), 'invalid')
})
test('final member receives the rounding remainder so 100% equals the owner amount', () => {
  const proposal = { mode: 'PERCENT', reason: '成员分配', lines: [
    { userId: 1, percentage: 33.33, reason: '一' },
    { userId: 2, percentage: 66.67, reason: '二' }
  ] }
  assert.equal(proposalLineAmount(proposal, proposal.lines[0], '0.05'), '0.02')
  assert.equal(proposalLineAmount(proposal, proposal.lines[1], '0.05'), '0.03')
  assert.equal(proposalTotal(proposal, '0.05'), '0.05')
  assert.equal(proposalError(proposal, '0.05'), null)
})
test('zero or unknown owner pool cannot save a filled allocation', () => {
  assert.equal(proposalError(filled(), 0), 'invalid')
  assert.equal(proposalError(filled(), null), 'invalid')
  const proposal = filled(); proposal.lines[0].amount = 1.001
  assert.equal(proposalError(proposal, 100), 'required')
})
test('switching rules resets proposal and the award request includes allocation in one call', async () => {
  const source = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
  assert.ok(source.includes('<AwardAllocationEditor :model="applicationAllocation"'))
  assert.ok(source.includes('function changeAwardRule(){Object.assign(applicationAllocation,emptyAllocationProposal());'))
  assert.ok(source.includes('applicationAllocation:proposalPayload(applicationAllocation)'))
  assert.ok(source.includes('<AwardAllocationDetails :allocation="history.applicationAllocation"'))
  assert.ok(source.includes('afterTaxProfit:awardRule.value.afterTaxProfit'))
  for (const file of ['index.vue', 'AwardAllocationEditor.vue', 'AwardAllocationDetails.vue']) {
    const { descriptor, errors } = parse(await readFile(new URL(`./${file}`, import.meta.url), 'utf8'))
    assert.deepEqual(errors, [])
    assert.doesNotThrow(() => compileScript(descriptor, { id: 'combined-award-test', inlineTemplate: true }))
  }
})

test('embedded award form labels its allocation section as member allocation', async () => {
  const editor = await readFile(new URL('./AwardAllocationEditor.vue', import.meta.url), 'utf8')
  assert.ok(editor.includes("<h3>{{ t('memberAllocationTitle') }}</h3>"))
  assert.equal(distributionMessages['zh-CN'].memberAllocationTitle, '成员分配')
  assert.ok(distributionMessages['vi-VN'].memberAllocationTitle)
})
