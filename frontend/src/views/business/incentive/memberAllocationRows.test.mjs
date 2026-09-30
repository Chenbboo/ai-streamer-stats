import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse, compileScript } from 'vue/compiler-sfc'
import { allocatedMembersBySource, memberAllocationRows } from './memberAllocationRows.js'

const proposal = {
  awardId: 3, ruleName: '项目奖金', status: 'SUBMITTED', currency: 'CNY',
  ruleMainOwnerBonusAmount: 800,
  applicationAllocation: { mode: 'AMOUNT', lines: [
    { userId: 10, userName: '甲', amount: 200 },
    { userId: 11, userName: '乙', amount: 100, percentage: 12.5 }
  ] }
}

test('submitted application shows every recipient with share, amount and unpaid status', () => {
  const rows = memberAllocationRows([proposal], { allocations: [], awards: [] })
  assert.deepEqual(rows.map(row => [row.userName, row.percentage, row.amount, row.paymentStatus]), [
    ['甲', 25, 200, 'UNPAID'], ['乙', 12.5, 100, 'UNPAID']
  ])
  assert.equal(rows[0].award, proposal)
  assert.equal(rows[0].batch, null)
  assert.equal(memberAllocationRows([{ ...proposal, status: 'DRAFT' }], {}).length, 0)
})

test('approved application is replaced by its allocation batch without duplicate members', () => {
  const batch = {
    allocationId: 8, awardId: 3, ruleName: '项目奖金', status: 'APPROVED', currency: 'CNY',
    lines: [{ lineId: 14, userName: '甲', amount: 200, paidAmount: 50, paymentStatus: 'PARTIAL' }]
  }
  const rows = memberAllocationRows([{ ...proposal, status: 'APPROVED' }], {
    awards: [{ awardId: 3, sourceAmount: 800 }], allocations: [batch]
  })
  assert.equal(rows.length, 1)
  assert.equal(rows[0].batch, batch)
  assert.equal(rows[0].percentage, 25)
  assert.equal(rows[0].paidAmount, 50)
  assert.equal(rows[0].paymentStatus, 'PARTIAL')
})

test('source summary shows each non-canceled allocated member, percentage and amount', () => {
  const distribution = {
    awards: [{ awardId: 3, sourceAmount: 800 }],
    allocations: [
      { allocationId: 8, awardId: 3, status: 'APPROVED', currency: 'CNY', lines: [{ lineId: 14, userName: '甲', amount: 200, paidAmount: 50 }] },
      { allocationId: 9, awardId: 3, status: 'DRAFT', currency: 'CNY', lines: [{ lineId: 15, userName: '乙', amount: 100, percentage: 12.5 }] },
      { allocationId: 10, awardId: 3, status: 'CANCELED', currency: 'CNY', lines: [{ lineId: 16, userName: '丙', amount: 50 }] }
    ]
  }
  const members = allocatedMembersBySource(distribution).get('3')
  assert.deepEqual(members.map(row => [row.userName, row.percentage, row.amount, row.allocationStatus]), [
    ['甲', 25, 200, 'APPROVED'], ['乙', 12.5, 100, 'DRAFT']
  ])
  assert.equal(allocatedMembersBySource({ awards: distribution.awards, allocations: [] }).has('3'), false)
})

test('one member table includes both allocation and payment fields, actions and expandable history', async () => {
  const component = await readFile(new URL('./DistributionPanel.vue', import.meta.url), 'utf8')
  const page = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
  for (const label of ['person', 'batch', 'memberBonusRate', 'memberBonusAmount', 'paid', 'unpaid', 'paymentState', 'allocationStatus', 'actions']) {
    assert.ok(component.includes(`:label="t('${label}')"`), label)
  }
  assert.equal((component.match(/<el-table ref="allocationTable"/g) || []).length, 1)
  assert.ok(!component.includes('batchSectionOpen'))
  assert.ok(!component.includes("t('batchRecords')"))
  assert.ok(component.includes(':expand-row-keys="requestedMemberKeys"'))
  assert.ok(component.includes('memberPayments(row)'))
  assert.ok(component.includes("emit('open-award',row.award)"))
  assert.ok(component.includes("act(row.batch,'APPROVED')"))
  assert.ok(component.includes('openPayment(row.batch,row.line)'))
  assert.ok(page.includes(':application-awards="data.distributionOnly ? [] : data.awards || []"'))
  const { descriptor, errors } = parse(component)
  assert.deepEqual(errors, [])
  assert.doesNotThrow(() => compileScript(descriptor, { id: 'member-allocation-test', inlineTemplate: true }))
})
