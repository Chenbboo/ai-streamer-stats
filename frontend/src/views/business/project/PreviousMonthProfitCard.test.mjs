import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as Vue from 'vue'
import { renderToString } from 'vue/server-renderer'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'

const source = readFileSync(new URL('./PreviousMonthProfitCard.vue', import.meta.url), 'utf8')
const { descriptor, errors } = parse(source)
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'previous-month-profit-test', inlineTemplate: true })
const generated = script.content.replace(/import\s+\{([^}]+)\}\s+from\s+['"]vue['"]/g, (_, names) =>
  `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
const component = new Function('Vue', generated.replace('export default', 'return'))(Vue)
const formatAmount = value => `${Number(value) > 0 ? '+' : ''}${Number(value).toFixed(2)}`
const label = text => text
const render = props => renderToString(Vue.createSSRApp(component, { label, formatAmount, ...props }))

test('project cockpit replaces the expired-budget notice in its original metrics position', () => {
  const page = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')
  assert.ok(!page.includes('本期预算已到期，请在项目计划与变更中续编'))
  assert.match(page, /<PreviousMonthProfitCard :result="cockpit.previousMonthProfit" :failed="cockpitError"/)
  assert.ok(page.indexOf('<PreviousMonthProfitCard') < page.indexOf('<article v-if="isDailyBudget">'))
  const { descriptor: pageDescriptor } = parse(page)
  const compiled = compileTemplate({ source: pageDescriptor.template.content, filename: 'index.vue', id: 'project-cockpit-test' })
  assert.deepEqual(compiled.errors, [])
})

test('shows the monthly after-tax result and currency, not lifetime profit', async () => {
  const html = await render({ result: { month: '2026-09', currency: 'VND', available: true, afterTaxProfit: '1090.00', pretaxProfit: 1200, taxAmount: 110, taxConfigured: true } })
  for (const text of ['项目上月结算税后盈利结果', '2026-09', 'VND', '+1090.00', 'is-success']) assert.ok(html.includes(text), text)
  assert.ok(!html.includes('1200.00'))
})

test('losses remain negative and known zero is shown rather than no data', async () => {
  const loss = await render({ result: { available: true, afterTaxProfit: -900 } })
  assert.ok(loss.includes('-900.00')); assert.ok(loss.includes('is-danger'))
  const zero = await render({ result: { available: true, afterTaxProfit: 0 } })
  assert.ok(zero.includes('0.00')); assert.ok(!zero.includes('上月暂无'))
})

test('no results and failed requests do not masquerade as settled zero', async () => {
  const empty = await render({ result: { month: '2026-09', available: false, afterTaxProfit: 0 } })
  assert.ok(empty.includes('上月暂无已核算税后盈利结果')); assert.ok(!empty.includes('0.00'))
  const failed = await render({ failed: true, result: { available: true, afterTaxProfit: 1000 } })
  assert.ok(failed.includes('上月盈利结果暂时无法读取')); assert.ok(!failed.includes('1000.00'))
})

test('unpriced costs and unset tax rate are explicit, using the monthly readiness', async () => {
  const html = await render({ result: { available: true, afterTaxProfit: 123, pendingCostCount: 2, taxConfigured: false } })
  for (const text of ['is-warning', '上月成本待完善，当前仅显示已核算部分', '税率未设置，暂按0%']) assert.ok(html.includes(text), text)
})
