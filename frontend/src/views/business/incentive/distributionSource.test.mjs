import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse, compileScript } from 'vue/compiler-sfc'
import messages from './distributionMessages.js'

const source = await readFile(new URL('./DistributionPanel.vue', import.meta.url), 'utf8')
const sources = await readFile(new URL('./DistributionSources.vue', import.meta.url), 'utf8')

test('source amount and percentage preview use the server-calculated owner pool, not the full award', () => {
  assert.ok(source.includes('money(a.sourceAmount)'))
  assert.ok(source.includes('money(selectedAward?.sourceAmount)'))
  assert.ok(sources.includes("['remaining','reserved']"))
  assert.ok(sources.includes('money(row[key])'))
  assert.ok(!sources.includes("t('score')"))
  assert.ok(source.includes('cents(selectedAward.value?.sourceAmount)'))
  assert.ok(!source.includes('cents(selectedAward.value?.amount)'))
  assert.ok(source.includes('Number(selectedAward.value?.remaining||0)+Number(form.originalAmount||0)'))
  assert.ok(source.includes('cents(total.value)>cents(capacity.value)'))
})

test('form explains owner-only source and still compiles with all allocation controls', () => {
  for (const locale of ['zh-CN', 'vi-VN']) assert.ok(messages[locale].sourceHint)
  assert.equal(messages['zh-CN'].total, '奖金来源金额')
  assert.equal(messages['zh-CN'].capacity, '本次可分配金额')
  for (const component of [source, sources]) {
    const { descriptor, errors } = parse(component)
    assert.deepEqual(errors, [])
    assert.doesNotThrow(() => compileScript(descriptor, { id: 'distribution-source-test', inlineTemplate: true }))
  }
})

test('source balances are not displayed below application records or inside details', async () => {
  const page = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
  const applicationStart = page.indexOf('name="awards"')
  const applicationTab = page.slice(applicationStart, page.indexOf('</el-tab-pane>', applicationStart))
  const verificationStart = page.indexOf('name="distribution"')
  const verificationTab = page.slice(verificationStart, page.indexOf('</el-tab-pane>', verificationStart))
  assert.ok(!applicationTab.includes('<DistributionSources'))
  assert.ok(!verificationTab.includes('<DistributionSources'))
  assert.ok(!page.includes('<DistributionSources'))
  assert.ok(verificationTab.includes('<DistributionPanel'))
  assert.ok(!source.includes(':data="data.awards"'))
  assert.ok(sources.includes('v-if="!data.personal"'))
  assert.ok(sources.includes("t('reservedHint')"))
  assert.ok(page.includes('v-if="!data.distributionOnly" :label="t(\'awardProcessing\')"'))
  for (const label of ['allocatedMembers', 'memberBonusRate', 'memberBonusAmount']) {
    assert.ok(sources.includes(`:label="t('${label}')"`), label)
    for (const locale of ['zh-CN', 'vi-VN']) assert.ok(messages[locale][label])
  }
  assert.ok(sources.includes('allocatedMembersBySource(props.data)'))
})

test('verification and payment no longer offers standalone new allocation', () => {
  assert.ok(!source.includes('@click="openEdit()"'))
  assert.ok(!source.includes('function openEdit(row){Object.keys(form)'))
  assert.ok(source.includes('function openEdit(row){if(!row)return;'))
  assert.ok(source.includes('@click="openEdit(row.batch)"'))
  assert.ok(source.includes(':title="t(\'edit\')"'))
})
