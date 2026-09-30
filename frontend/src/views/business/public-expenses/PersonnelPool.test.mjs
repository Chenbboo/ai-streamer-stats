import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, ref, reactive, effectScope } from 'vue'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'

const { descriptor, errors } = parse(readFileSync(new URL('./PersonnelPool.vue', import.meta.url), 'utf8'))
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'personnel-pool-test' })
const factory = new Function('computed', 'ref', 'onMounted', 'onBeforeUnmount', 'ElMessage', 'translateText', 'translateServerMessage', 'getPublicPersonnelPreview', 'savePublicPersonnel', 'useBusinessRefreshOnReactivated', script.content.replace(/^import .*$/gm, '').replace('export default', 'return'))
const translate = (text, params = []) => text.replace(/\{(\d+)\}/g, (_, index) => params[index])
function setup(t, preview) {
  const writes = [], events = [], unmounts = []
  const props = reactive({ filters: { companyDeptId: 110, month: '2026-09', currency: 'CNY' }, bill: { status: 'DRAFT', version: 2 }, editable: true, disabled: false })
  const component = factory(computed, ref, () => {}, callback => unmounts.push(callback), { success() {} }, translate, translate, async () => ({ data: preview }), async data => writes.push(data), () => {})
  const scope = effectScope()
  const state = scope.run(() => component.setup(props, { expose() {}, emit: (...event) => events.push(event) }))
  t.after(() => scope.stop())
  return { state, props, writes, events, unmounts }
}
test('leaving the personnel preview releases its page busy state', t => {
  const { events, unmounts } = setup(t, {})
  unmounts.forEach(callback => callback())
  assert.deepEqual(events.at(-1), ['busy', false])
})
test('the personnel details and IT amount panel compile with actual Vue bindings', () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'PersonnelPool.vue', id: 'personnel-pool-test', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(result.errors, [])
})
test('IT-only net loss can proceed without a payroll row or client amounts', async t => {
  const { state, writes, events } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], issues: [], publicAmount: 7000, itLoss: { netLoss: 7000, projects: [{ projectId: 9 }] } })
  await state.fetchPreview()
  assert.equal(state.total.value, 7000)
  assert.equal(state.blocked.value, false)
  await state.save()
  assert.deepEqual(writes, [{ companyDeptId: 110, month: '2026-09', currency: 'CNY', version: 2 }])
  assert.ok(events.some(([name]) => name === 'saved'))
})
test('an incomplete IT result stays unknown and cannot be saved', async t => {
  const { state, writes } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], issues: ['核算待完善'], publicAmount: null, itLoss: { netLoss: null, projects: [{ projectId: 9 }] } })
  await state.fetchPreview()
  assert.equal(state.total.value, null)
  assert.equal(state.blocked.value, true)
  await state.save()
  assert.equal(writes.length, 0)
})
test('other public payroll and IT loss use the server total exactly once', async t => {
  const support = { userId: 5, totalAmount: 2000, projectAmount: 0, businessAmount: 0, publicAmount: 2000 }
  const business = { userId: 1, totalAmount: 10000, projectAmount: 10000, directProjectCost: true, publicAmount: 0 }
  const { state } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [support], personnelDetails: [business, support], issues: [], publicAmount: 9000, itLoss: { netLoss: 7000, projects: [{ projectId: 9 }] } })
  await state.fetchPreview()
  assert.equal(state.total.value, 9000)
  assert.equal(state.pendingTotal.value, 9000)
  assert.equal(state.displayPayroll.value, 12000)
  assert.equal(state.displayRows.value.length, 2)
  assert.equal(state.displayRows.value[0].publicAmount, 0)
  assert.equal(state.itPendingAmount.value, 7000)
})
test('published bills keep their saved values until recalled', async t => {
  const { state, props, writes } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], issues: [], publicAmount: 7000, itLoss: { netLoss: 7000, projects: [{ projectId: 9 }] } })
  const savedRows = [{ userId: 1, totalAmount: 2000, projectAmount: 0, publicAmount: 2000 }]
  props.bill = { status: 'PUBLISHED', personnel: { sourceMode: 'DEPARTMENT_NET_V1', publicAmount: 32000, rows: savedRows, itLoss: { netLoss: 30000 } } }
  props.editable = false
  await state.fetchPreview()
  assert.equal(state.snapshot.value.publicAmount, 32000)
  assert.equal(state.pendingTotal.value, 32000)
  assert.equal(state.itPendingAmount.value, 30000)
  assert.deepEqual(state.displayRows.value, savedRows)
  await state.save()
  assert.equal(writes.length, 0)
})

test('a recalled bill shows the current preview instead of its previous snapshot', async t => {
  const { state, props } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], issues: [], publicAmount: 7000, itLoss: { netLoss: 7000, projects: [{ projectId: 9 }] } })
  props.bill.personnel = { publicAmount: 32000 }
  await state.fetchPreview()
  assert.equal(state.pendingTotal.value, 7000)
  assert.equal(state.itPendingAmount.value, 7000)
})

test('unallocated business payroll blocks allocation while IT remains a single amount', async t => {
  const { state, writes } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], personnelDetails: [{ userId: 139, directProjectCost: true, totalAmount: 11250, projectAmount: null, publicAmount: 0, projectIssues: ['投入比例待确认'] }], issues: ['盈利部门人员项目成本待完善'], publicAmount: null, itLoss: { netLoss: 7000 } })
  await state.fetchPreview()
  assert.equal(state.blocked.value, true)
  assert.equal(state.itPendingAmount.value, 7000)
  await state.save()
  assert.equal(writes.length, 0)
})

test('published payroll resolves deleted project names from the current metadata and keeps saved amounts', async t => {
  const current = { userId: 118, totalAmount: 7500, projectAmount: 7500, projectAllocations: [{ projectId: 17, projectName: '唐勃珠宝', projectDeleted: true, amount: 170.46 }] }
  const { state, props } = setup(t, { sourceMode: 'DEPARTMENT_NET_V1', rows: [], personnelDetails: [current], issues: [], publicAmount: 7000, itLoss: { netLoss: 7000 } })
  props.editable = false
  const saved = { projectId: 17, projectName: '未知项目', amount: 170.46 }
  props.bill = { status: 'PUBLISHED', personnel: { sourceMode: 'DEPARTMENT_NET_V1', publicAmount: 32000, personnelDetails: [{ userId: 118, totalAmount: 7500, projectAmount: 7500, projectAllocations: [saved] }] } }
  await state.fetchPreview()
  assert.equal(state.allocationProjectName(saved), '唐勃珠宝（已删除）')
  assert.equal(state.displayRows.value[0].projectAllocations[0].amount, 170.46)
  assert.equal(state.pendingTotal.value, 32000)
})
