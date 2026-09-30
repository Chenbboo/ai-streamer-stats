import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { parse, compileScript } from 'vue/compiler-sfc'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import ElementPlus from 'element-plus'
import messages from './scoreMessages.js'

test('drawer shows application details before the plan and keeps the event history below both', async () => {
  const source = await readFile(new URL('./index.vue', import.meta.url), 'utf8')
  const drawer = source.slice(source.indexOf('<el-drawer'), source.indexOf('</el-drawer>'))
  const application = drawer.indexOf("t('awardApplicationDetails')")
  const applicationEnd = drawer.indexOf('</el-descriptions>')
  const allocation = drawer.indexOf('<AwardAllocationDetails')
  const plan = drawer.indexOf('<AwardRuleDetails')
  const timeline = drawer.indexOf('<el-timeline ')
  assert.ok(application >= 0 && application < applicationEnd)
  assert.ok(applicationEnd < allocation && allocation < plan && plan < timeline)
})

async function renderDetails(award) {
  const source = await readFile(new URL('./AwardRuleDetails.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const compiled = compileScript(descriptor, { id: 'award-rule-details-test', inlineTemplate: true })
  const generated = compiled.content.replace(/from (['"])vue\1/g, `from ${JSON.stringify(import.meta.resolve('vue'))}`)
  const directory = await mkdtemp(path.join(tmpdir(), 'award-rule-details-test-'))
  try {
    const modulePath = path.join(directory, 'component.mjs')
    await writeFile(modulePath, generated)
    const { default: component } = await import(pathToFileURL(modulePath).href)
    const app = createSSRApp(component, {
      award, label: key => messages['zh-CN'][key] || ({ rule: '奖金方案', reason: '依据与说明' })[key] || key,
      formatAmount: value => value == null ? '—' : Number(value).toFixed(2)
    })
    app.use(ElementPlus)
    return await renderToString(app)
  } finally { await rm(directory, { recursive: true, force: true }) }
}

test('approval details display profit, both shares and amounts, and the rule reason', async () => {
  const html = await renderDetails({ ruleName: '原方案', ruleVersion: 1, policyVersion: 'PROFIT_SHARE_V1', currency: 'CNY',
    ruleAfterTaxProfit: 10000, ruleMainOwnerBonusRate: 40, ruleSponsorOwnerBonusRate: 60,
    ruleMainOwnerBonusAmount: 4000, ruleSponsorOwnerBonusAmount: 6000, ruleReason: '原方案依据', reason: '另一个申请说明' })
  for (const text of ['项目税后盈利金额', '10000.00 CNY', '负责人奖金占比', '40%', '负责人分配金额', '4000.00 CNY',
    '归属老板奖金占比', '60%', '归属老板分配金额', '6000.00 CNY', '依据与说明', '原方案依据']) assert.ok(html.includes(text), text)
  assert.ok(!html.includes('另一个申请说明'))
})

test('legacy rules do not invent profit or personal allocation details', async () => {
  const html = await renderDetails({ ruleName: '历史奖励', ruleVersion: 1, policyVersion: 'FIXED_V1', currency: 'CNY', ruleReason: '原固定奖励说明' })
  assert.ok(html.includes('原固定奖励说明'))
  assert.ok(html.includes(messages['zh-CN'].legacyRuleDetailsHint))
  assert.ok(!html.includes('负责人分配金额'))
})
