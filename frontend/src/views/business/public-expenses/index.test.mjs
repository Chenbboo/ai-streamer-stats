import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, ref, reactive, effectScope } from 'vue'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
const { descriptor, errors } = parse(readFileSync(new URL('./index.vue', import.meta.url), 'utf8'))
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'expense-workspace-test' })
const names = Object.keys(script.imports)
const factory = new Function(...names, script.content.replace(/^import[\s\S]*?from ['"][^'"]+['"];?/gm, '').replace('export default', 'return'))
const translate = (text, params = []) => text.replace(/\{(\d+)\}/g, (_, index) => params[index])
const response = status => ({ data: { companyDeptId: 110, month: '2026-09', currency: 'CNY', canManage: true, companies: [{ companyDeptId: 110 }], bill: { billId: 1, status, version: 8, entries: [], ownerAllocations: [] } } })
function setup(t, workspace = async () => response('PUBLISHED'), overrides = {}) {
  const bindings = { computed, ref, reactive, onMounted() {}, onBeforeRouteLeave() {}, useRoute: () => ({ query: {} }), useBusinessRefreshOnReactivated() {}, translateText: translate, ElMessage: { success() {}, warning() {} }, ElMessageBox: {}, newSubmissionId: () => 'test', PersonnelPool: {}, getPublicExpenseWorkspace: workspace }
  Object.assign(bindings, overrides)
  const component = factory(...names.map(name => bindings[name] ?? (async () => { throw Error(`Unexpected API ${name}`) })))
  const scope = effectScope()
  const state = scope.run(() => component.setup({}, { expose() {} }))
  t.after(() => scope.stop())
  return state
}
test('the public expense workspace template compiles against the separated busy state', () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'index.vue', id: 'expense-workspace-test', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(result.errors, [])
})
test('reactivating a published bill while preview is busy does not lock the progress page', async t => {
  const state = setup(t)
  state.step.value = 1; state.costTab.value = 'PERSONNEL'; state.personnelBusy.value = true
  assert.equal(state.saving.value, true)
  await state.load()
  assert.equal(state.step.value, 3)
  assert.equal(state.saving.value, false)
  state.selectStep(2)
  assert.equal(state.step.value, 2)
})
test('saving personnel can leave the preview before its completion event without retaining a lock', async t => {
  const state = setup(t, async () => response('DRAFT'))
  state.step.value = 1; state.costTab.value = 'PERSONNEL'; state.personnelBusy.value = true
  await state.personnelSaved()
  assert.equal(state.step.value, 2)
  assert.equal(state.costPool.value, 'COMBINED')
  assert.equal(state.saving.value, false)
})

test('one set of owner shares allocates the full combined amount and conserves cent tails', async t => {
  const result = response('DRAFT')
  Object.assign(result.data.bill, { totalAmount: 72209.83, personnelAmount: 26143.17, personnel: { sourceMode: 'DEPARTMENT_NET_V1' } })
  const state = setup(t, async () => result)
  await state.load()
  assert.equal(state.combinedAllocation.value, true)
  assert.equal(state.costPool.value, 'COMBINED')
  assert.equal(state.selectedPoolAmount.value, 72209.83)
  state.ownerRows.value = [{ ownerUserId: 1, percentage: 33.33 }, { ownerUserId: 2, percentage: 33.33 }, { ownerUserId: 3, percentage: 33.34 }]
  assert.equal(state.allPoolsReady.value, true)
  assert.equal([0, 1, 2].reduce((sum, i) => sum + Math.round(state.ownerAmountPreview(i) * 100), 0), 7220983)
  assert.ok(state.ownerPayload().every(row => row.costPool === 'COMBINED'))
  state.ownerRows.value[2].percentage = 30
  assert.equal(state.allPoolsReady.value, false)
})

test('legacy draft owners use the merged draft and require explicit saving without locking fee review', async t => {
  const result = response('DRAFT')
  Object.assign(result.data.bill, { totalAmount: 100, personnelAmount: 60, ownerAllocations: [{ ownerUserId: 1, costPool: 'EXPENSE', percentage: 100, amount: 40 }], combinedOwnerDraft: [{ ownerUserId: 1, costPool: 'COMBINED', percentage: 40, amount: 40 }] })
  const state = setup(t, async () => result)
  await state.load()
  assert.equal(state.ownerRows.value.length, 1)
  assert.equal(state.percentageTotal.value, 40)
  assert.equal(state.needsCombinedSave.value, true)
  assert.equal(state.ownersDirty.value, false)
  assert.equal(state.allPoolsReady.value, false)
})

test('legacy published allocations retain their original pools', async t => {
  const result = response('PUBLISHED')
  Object.assign(result.data.bill, { totalAmount: 100, personnelAmount: 60, personnel: {}, ownerAllocations: [{ ownerUserId: 1, costPool: 'EXPENSE', percentage: 100, amount: 40 }, { ownerUserId: 1, costPool: 'PERSONNEL', percentage: 100, amount: 60 }] })
  const state = setup(t, async () => result)
  await state.load()
  assert.equal(state.combinedAllocation.value, false)
  assert.equal(state.costPool.value, 'EXPENSE')
  assert.equal(state.ownerRows.value[0].amount, 40)
  state.changePool('PERSONNEL')
  assert.equal(state.ownerRows.value[0].amount, 60)
})

test('combined published bills show one owner task based on the total', async t => {
  const result = response('PUBLISHED')
  Object.assign(result.data.bill, { totalAmount: 100, personnelAmount: 60, ownerAllocations: [{ ownerUserId: 1, costPool: 'COMBINED', percentage: 100, amount: 100 }] })
  const state = setup(t, async () => result)
  await state.load()
  assert.equal(state.combinedAllocation.value, true)
  assert.equal(state.ownerRows.value.length, 1)
  assert.equal(state.selectedPoolAmount.value, 100)
})

test('publishing a converted draft saves one combined set, and a publish failure retains the successful proportions', async t => {
  const result = response('DRAFT')
  result.data.owners = [{ userId: 1, deptId: 201 }, { userId: 2, deptId: 201 }]
  Object.assign(result.data.bill, { totalAmount: 100, personnelAmount: 60, ownerAllocations: [{ ownerUserId: 1, costPool: 'EXPENSE', percentage: 100, amount: 40 }], combinedOwnerDraft: [{ ownerUserId: 1, deptId: 201, costPool: 'COMBINED', percentage: 40, amount: 40 }] })
  const calls = []
  const saved = [{ ownerUserId: 1, deptId: 201, costPool: 'COMBINED', percentage: 60, amount: 60 }, { ownerUserId: 2, deptId: 201, costPool: 'COMBINED', percentage: 40, amount: 40 }]
  const state = setup(t, async () => result, {
    ElMessageBox: { confirm: async () => true },
    savePublicExpenseOwners: async (id, payload) => { calls.push(payload); return { data: { version: 9, ownerAllocations: saved, combinedOwnerDraft: saved } } },
    publishPublicExpenseMonth: async (id, payload) => { calls.push(payload); throw Error('publish failed') }
  })
  await state.load()
  state.ownerRows.value = saved.map(row => ({ ...row }))
  await state.publishMonth()
  assert.equal(calls.length, 2)
  assert.equal(calls[0].costPool, 'COMBINED')
  assert.equal(calls[0].allocations.length, 2)
  assert.equal(calls[1].version, 9)
  assert.equal(state.saving.value, false)
  assert.equal(state.ownersDirty.value, false)
  assert.equal(state.needsCombinedSave.value, false)
  assert.deepEqual(state.ownerRows.value.map(row => row.percentage), [60, 40])
})
test('preview cleanup cannot unlock an ongoing bill write, and a failed write releases its own lock', async t => {
  const state = setup(t)
  state.step.value = 3
  let finish
  const pending = state.mutate(() => new Promise(resolve => { finish = resolve }), 'saved')
  assert.equal(state.saving.value, true)
  state.personnelBusy.value = false
  assert.equal(state.saving.value, true)
  finish()
  await pending
  assert.equal(state.saving.value, false)
  assert.equal(await state.mutate(async () => { throw Error('API failed') }, 'saved'), false)
  assert.equal(state.saving.value, false)
})

test('retained costs are deducted once and stale combined amounts must be saved before publishing', async t => {
  const result = response('DRAFT')
  result.data.owners = [{ userId: 1, deptId: 201 }, { userId: 2, deptId: 201 }]
  const saved = [{ ownerUserId: 1, deptId: 201, costPool: 'COMBINED', percentage: 90, amount: 64781.55 }, { ownerUserId: 2, deptId: 201, costPool: 'COMBINED', percentage: 10, amount: 7197.95 }]
  Object.assign(result.data.bill, { totalAmount: 72209.83, allocatableAmount: 71979.50, retainedAmount: 230.33, personnelAmount: 26143.17, ownerAllocations: saved.map((r, i) => ({ ...r, amount: i ? 7220.98 : 64988.85 })), combinedOwnerDraft: saved })
  let confirmation, persisted
  const state = setup(t, async () => result, {
    ElMessageBox: { confirm: async text => { confirmation = text; return true } },
    savePublicExpenseOwners: async (id, payload) => { persisted = payload; return { data: { version: 9, ownerAllocations: saved, combinedOwnerDraft: saved } } },
    publishPublicExpenseMonth: async () => { throw Error('stop before real publish') }
  })
  await state.load()
  assert.equal(state.selectedPoolAmount.value, 71979.50)
  assert.equal(state.ownersDirty.value, false)
  assert.equal(state.needsCombinedSave.value, true)
  assert.equal(state.ownerAmountPreview(0), 64781.55)
  assert.equal(state.ownerAmountPreview(1), 7197.95)
  await state.publishMonth()
  assert.match(confirmation, /71,979.50/)
  assert.equal(persisted.costPool, 'COMBINED')
  assert.equal(state.needsCombinedSave.value, false)
})
