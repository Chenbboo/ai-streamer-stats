import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, ref, watch, reactive, effectScope, nextTick } from 'vue'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import { spendHistoryQuery, spendPersonnelDescription, spendExpenseItems } from '../../utils/spendHistory.js'

const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')
const { descriptor, errors } = parse(source)
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'spend-history-test' })
const compiled = script.content.replace(/^import .*$/gm, '').replace('export default', 'return')
const createComponent = new Function('computed', 'ref', 'watch', 'getBusinessOwnerSpendHistory', 'translateText', 'spendHistoryQuery', 'spendPersonnelDescription', 'spendExpenseItems', compiled)
const translateText = (value, params = []) => value.replace(/\{(\d+)\}/g, (_, index) => params[index])
const flush = async () => { await nextTick(); await Promise.resolve(); await nextTick() }

function setup(t, request) {
  const props = reactive({ modelValue: false, projectId: 13, projectName: '测试项目' })
  const component = createComponent(computed, ref, watch, request, translateText, spendHistoryQuery, spendPersonnelDescription, spendExpenseItems)
  const scope = effectScope()
  const state = scope.run(() => component.setup(props, { expose() {} }))
  t.after(() => scope.stop())
  return { props, state }
}

test('dialog template compiles with its real script bindings', () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'index.vue', id: 'spend-history-test', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(result.errors, [])
  assert.match(descriptor.template.content, /type="expand"/)
  assert.match(descriptor.template.content, /row\.personnelItems/)
  assert.match(descriptor.template.content, /expenseItems\(row\)/)
})

test('opening the dialog requests the selected month for only its project', async t => {
  const calls = []
  const { props, state } = setup(t, async (id, params) => { calls.push({ id, params }); return { data: { currency: 'CNY', rows: [{ bizDate: '2024-02-29', amount: 20 }], totals: { amount: 20 } } } })
  state.month.value = '2024-02'; await flush()
  assert.equal(calls.length, 0)
  props.modelValue = true; await flush()
  assert.deepEqual(calls, [{ id: 13, params: { month: '2024-02' } }])
  assert.equal(state.history.value.rows[0].amount, 20)
  assert.equal(state.loading.value, false)
  assert.equal(state.failed.value, false)
})

test('switching to a date removes the month parameter and retains VND amounts', async t => {
  const calls = []
  const { props, state } = setup(t, async (id, params) => { calls.push({ id, params }); return { data: { currency: 'VND', rows: [], totals: { amount: 200000 } } } })
  props.modelValue = true; await flush()
  state.filterMode.value = 'date'; state.date.value = '2024-02-29'; await flush()
  assert.deepEqual(calls.at(-1), { id: 13, params: { bizDate: '2024-02-29' } })
  assert.equal(state.currency.value, 'VND')
  assert.equal(state.money(state.history.value.totals.amount), '200,000.00')
})

test('late responses cannot overwrite a newer month or reopen a closed dialog', async t => {
  const pending = []
  const { props, state } = setup(t, () => new Promise(resolve => pending.push(resolve)))
  props.modelValue = true; await flush()
  state.month.value = '2024-02'; await flush()
  pending[1]({ data: { dateFrom: '2024-02-01', rows: [] } }); await flush()
  pending[0]({ data: { dateFrom: '2024-01-01', rows: [] } }); await flush()
  assert.equal(state.history.value.dateFrom, '2024-02-01')
  state.month.value = '2024-03'; await flush()
  props.modelValue = false; await flush()
  pending[2]({ data: { rows: [{ amount: 999 }] } }); await flush()
  assert.deepEqual(state.history.value, {})
  assert.equal(state.loading.value, false)
})

test('an API failure is explicit and query retry replaces it with data', async t => {
  let requests = 0
  const { props, state } = setup(t, async () => { if (++requests === 1) throw new Error('request failed'); return { data: { currency: 'CNY', rows: [], totals: { amount: 0 } } } })
  props.modelValue = true; await flush()
  assert.equal(state.failed.value, true)
  assert.deepEqual(state.history.value, {})
  await state.loadHistory()
  assert.equal(state.failed.value, false)
  assert.equal(state.history.value.totals.amount, 0)
})

test('changing project reloads with its own id and expense detail does not add estimates twice', async t => {
  const calls = []
  const { props, state } = setup(t, async (id, params) => { calls.push({ id, params }); return { data: { projectId: id, currency: 'CNY', rows: [] } } })
  props.modelValue = true; await flush()
  props.projectId = 12; await flush()
  assert.equal(calls.at(-1).id, 12)
  assert.equal(state.history.value.projectId, 12)
  const items = state.expenseItems({ expenseItems: [{ amount: 80 }], publicCost: 5, publicEstimatedCost: 2 })
  assert.equal(items.reduce((sum, item) => sum + Number(item.amount), 0), 85)
})
