import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import scoreMessages from './scoreMessages.js'
import distributionMessages from './distributionMessages.js'

const source = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
const start = source.indexOf('<el-table ref="awardTable"')
const list = source.slice(start, source.indexOf('</el-table>', start))

test('renamed tabs and matching section titles retain their existing route names', () => {
  assert.equal(scoreMessages['zh-CN'].awardProcessing, '奖金分配与申请')
  assert.equal(scoreMessages['zh-CN'].distributionTitle, '奖金核验与发放')
  assert.equal(distributionMessages['zh-CN'].title, scoreMessages['zh-CN'].distributionTitle)
  assert.ok(distributionMessages['zh-CN'].noAwards.includes('奖金分配与申请'))
  assert.ok(source.includes("<h2>{{ t('awardProcessing') }}</h2>"))
  assert.ok(source.includes(':label="t(\'awardProcessing\')" name="awards"'))
  assert.ok(source.includes(':label="t(\'distributionTitle\')" name="distribution"'))
})

test('application can only submit while boss review remains in verification and payment', async () => {
  const applicationStart = source.indexOf('name="awards"')
  const applicationTab = source.slice(applicationStart, source.indexOf('</el-tab-pane>', applicationStart))
  const verificationStart = source.indexOf('name="distribution"')
  const verificationTab = source.slice(verificationStart, source.indexOf('</el-tab-pane>', verificationStart))
  assert.ok(applicationTab.includes('@click="openAward"'))
  assert.ok(!applicationTab.includes('ref="awardTable"'))
  assert.ok(applicationTab.includes("t('awardApplicationList')"))
  assert.ok(applicationTab.includes(':data="applicationAwards"'))
  assert.ok(applicationTab.includes("@click=\"act(row,'submit')\""))
  assert.ok(applicationTab.includes('@click="editAward(row)"'))
  assert.ok(applicationTab.includes('@click="deleteAward(row)"'))
  assert.ok(source.includes("(data.awards||[]).filter(row=>row.status!=='CANCELED')"))
  assert.ok(source.includes('updateIncentiveAward(editingAward.value.awardId,payload)'))
  for (const action of ['history=row', "act(row,'cancel')", "act(row,'resubmit-cost')", 'openAccounting(row)', 'row.canReview']) {
    assert.ok(!applicationTab.includes(action), action)
  }
  assert.ok(verificationTab.includes('ref="awardTable"'))
  assert.ok(!verificationTab.includes("@click=\"act(row,'submit')\""))
  assert.ok(verificationTab.includes('row.canReview'))
  assert.ok(verificationTab.includes("t('awardHint')"))
  assert.ok(verificationTab.includes("t('awardReviewHint')"))
  assert.ok(verificationTab.indexOf('ref="awardTable"') < verificationTab.indexOf('<DistributionPanel'))
  assert.ok(verificationTab.includes('v-if="!data.distributionOnly"'))
  assert.ok(source.includes("activeTab.value='distribution'"))
  assert.ok(source.includes("activeTab==='distribution' && history?.canReview"))
  const boss = await readFile(new URL('../boss/index.vue', import.meta.url), 'utf8')
  assert.ok(boss.includes("tab: 'distribution', awardId: row.awardId"))
})

test('application and review records retain the same financial and status columns', () => {
  const applicationStart = source.indexOf("<h3>{{ t('awardApplicationList') }}</h3>")
  const applicationTable = source.slice(applicationStart, source.indexOf('</el-table>', applicationStart))
  const labels = table => [...table.matchAll(/<el-table-column\b[^>]*:label="t\('([^']+)'\)"/g)].map(match => match[1])
  assert.deepEqual(labels(applicationTable).slice(0, 8), labels(list).slice(0, 8))
  assert.deepEqual(labels(applicationTable).slice(0, 4), ['rule', 'amount', 'date', 'approval'])
  assert.deepEqual(labels(applicationTable).slice(4, 8), ['mainOwnerShare', 'mainOwnerAllocationAmount', 'sponsorOwnerShare', 'sponsorOwnerAllocationAmount'])
  assert.deepEqual(labels(applicationTable).slice(8), ['memberAllocationColumn', 'actions'])
  assert.deepEqual(labels(list).slice(8), ['memberAllocationColumn', 'actions'])
  assert.ok(applicationTable.includes("@click=\"allocationDetails=row\""))
  assert.ok(list.includes("@click=\"allocationDetails=row\""))
  assert.ok(scoreMessages['zh-CN'].awardApplicationHint.includes('提交核准在本页办理'))
})

test('application records sit within the new-award card without a standalone source table below', () => {
  const tab = source.slice(source.indexOf('name="awards"'), source.indexOf('</el-tab-pane>', source.indexOf('name="awards"')))
  const cardStart = tab.indexOf('<section class="product-card">')
  const button = tab.indexOf('@click="openAward"', cardStart)
  const records = tab.indexOf('<div class="award-application-records">', button)
  const cardEnd = tab.indexOf('</section>', cardStart)
  assert.ok(cardStart >= 0 && button > cardStart && records > button)
  assert.ok(cardEnd > records)
  assert.ok(!tab.includes('<DistributionSources'))
})

test('new award form omits manual reward estimation while retaining member allocation', () => {
  const start = source.indexOf('<el-dialog v-model="awardOpen"')
  const dialog = source.slice(start, source.indexOf('</el-dialog>', start))
  assert.ok(dialog.includes('<AwardAllocationEditor'))
  assert.ok(!dialog.includes("t('estimate')"))
  assert.ok(!dialog.includes("t('estimated')"))
  assert.ok(!dialog.includes('@click="calculate"'))
  assert.ok(source.includes('matchTier(awardRule.value.tiers'))
})

test('award list displays both plan shares and amounts without a profit column', () => {
  assert.ok(start >= 0)
  for (const field of ['ruleMainOwnerBonusRate', 'ruleMainOwnerBonusAmount', 'ruleSponsorOwnerBonusRate', 'ruleSponsorOwnerBonusAmount']) {
    assert.ok(list.includes(`row.${field}`), field)
  }
  assert.ok(!list.includes('data.profitResult'))
  assert.ok(!list.includes('row.ruleAfterTaxProfit'))
  assert.ok(!list.includes("t('afterTaxProfit')"))
  assert.ok(!list.includes("t('cost')"))
  assert.ok(!list.includes("t('payment')"))
})

test('award submission explains the boss-workbench pending review destination', () => {
  assert.ok(source.includes("t('awardReviewHint')"))
  assert.ok(source.includes("action==='submit'?'submittedToBoss':'success'"))
})

test('original explanation and first four columns retain their positions before new plan fields', () => {
  const labels = [...list.matchAll(/<el-table-column\b[^>]*:label="t\('([^']+)'\)"/g)].map(match => match[1])
  assert.deepEqual(labels.slice(0, 4), ['rule', 'amount', 'date', 'approval'])
  assert.deepEqual(labels.slice(4, 8), ['mainOwnerShare', 'mainOwnerAllocationAmount', 'sponsorOwnerShare', 'sponsorOwnerAllocationAmount'])
  assert.ok(source.slice(0, start).trimEnd().endsWith("<p>{{ t('awardHint') }}</p>"))
  assert.ok(source.indexOf("<p>{{ t('awardReviewHint') }}</p>", start) > source.indexOf('</el-table>', start))
})
