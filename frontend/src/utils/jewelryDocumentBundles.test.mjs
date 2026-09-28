import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import { documentBundleAddons, documentBundleExpanded, visibleDocumentBundleItems } from './jewelryDocumentBundles.js'

const main = no => ({ saleRole: 'MAIN', bundleGroupNo: no, productId: no, qty: 2, unitPrice: 100 })
const addon = (no, pricingMode = 'INCLUDED') => ({ saleRole: 'ADDON', bundleGroupNo: no, pricingMode, qty: 2, unitPrice: pricingMode === 'INCLUDED' ? 0 : 20 })
const source = readFileSync(new URL('../views/jewelry/document/index.vue', import.meta.url), 'utf8')
const { descriptor, errors } = parse(source)
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'jewelry-document-test' })

test('customer returns stay expanded initially and each bundle can collapse independently', () => {
  const rows = [main(1), addon(1), main(2), addon(2)]
  assert.equal(documentBundleExpanded('CUSTOMER_RETURN', {}, 1), true)
  assert.deepEqual(visibleDocumentBundleItems(rows, 'CUSTOMER_RETURN', {}), rows)
  assert.deepEqual(visibleDocumentBundleItems(rows, 'CUSTOMER_RETURN', { 1: false }), [rows[0], rows[2], rows[3]])
  assert.deepEqual(visibleDocumentBundleItems(rows, 'CUSTOMER_RETURN', { 1: true, 2: false }), [rows[0], rows[1], rows[2]])
})

test('sales collapse both included and separately priced addons with one control', () => {
  const rows = [main(1), addon(1), addon(1, 'SEPARATE')]
  assert.equal(documentBundleExpanded('SALES_OUT', {}, 1), false)
  assert.deepEqual(visibleDocumentBundleItems(rows, 'SALES_OUT', {}), [rows[0]])
  assert.deepEqual(visibleDocumentBundleItems(rows, 'SALES_OUT', { 1: true }), rows)
  assert.deepEqual(documentBundleAddons(rows, rows[0]), rows.slice(1))
})

test('collapsing changes visibility only, leaving quantities, prices, totals and row identities intact', () => {
  const rows = [main(1), addon(1), addon(1, 'SEPARATE'), main(2), addon(2)]
  const before = JSON.stringify(rows)
  const total = () => rows.reduce((sum, row) => sum + row.qty * row.unitPrice, 0)
  const amount = total()
  const shown = visibleDocumentBundleItems(rows, 'CUSTOMER_RETURN', { 1: false, 2: false })
  assert.strictEqual(shown[0], rows[0])
  assert.strictEqual(shown[1], rows[3])
  assert.equal(total(), amount)
  assert.equal(JSON.stringify(rows), before)
})

test('normal and orphan addon rows never disappear even with invalid group metadata', () => {
  const rows = [main(1), addon(1), addon(2), addon(null), { saleRole: 'NORMAL' }, main(0), addon(0)]
  assert.deepEqual(visibleDocumentBundleItems(rows, 'SALES_OUT', {}), [rows[0], ...rows.slice(2)])
  assert.deepEqual(documentBundleAddons(rows, rows[5]), [])
  assert.deepEqual(documentBundleAddons(rows, rows[4]), [])
})

test('group numbers from JSON or selection inputs resolve consistently', () => {
  const rows = [main(1), addon('1')]
  assert.deepEqual(documentBundleAddons(rows, rows[0]), [rows[1]])
  assert.deepEqual(visibleDocumentBundleItems(rows, 'CUSTOMER_RETURN', { 1: false }), [rows[0]])
})

test('readonly grouped documents can collapse; unrelated document types remain unchanged', () => {
  const rows = [main(1), addon(1)]
  assert.deepEqual(visibleDocumentBundleItems(rows, 'REVERSAL', {}), rows)
  assert.deepEqual(visibleDocumentBundleItems(rows, 'REVERSAL', { 1: false }), [rows[0]])
  assert.strictEqual(visibleDocumentBundleItems(rows, 'PURCHASE_IN', { 1: false }, false), rows)
})

test('deleting a visible row resolves its real index after another group is collapsed', async () => {
  const form = { docType: 'SALES_OUT', items: [main(1), addon(1), main(2), addon(2)] }
  const shown = visibleDocumentBundleItems(form.items, form.docType, {})
  const row = shown[1]
  assert.equal(form.items.indexOf(row), 2)
  const fn = script.scriptSetupAst.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'removeItem')
  const fnSource = descriptor.scriptSetup.content.slice(fn.start, fn.end)
  let confirmations = 0
  const removeItem = new Function('form', 'normalizedSaleRole', 'isUnlinkedInfluencerReturn', 'proxy', 'translateText', `return ${fnSource}`)(
    form, row => row.saleRole || 'NORMAL', () => false,
    { $modal: { confirm: async () => { confirmations++ } } }, value => value
  )
  await removeItem(form.items.indexOf(row))
  assert.equal(confirmations, 1)
  assert.deepEqual(form.items.map(row => row.bundleGroupNo), [1, 1])
})

test('the form compiles and its only delete column is at the front and fixed on the left', () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'document.vue', id: 'jewelry-document-test', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(result.errors, [])
  const tableStart = descriptor.template.content.indexOf('<el-table :data="displayItems"')
  const tableEnd = descriptor.template.content.indexOf('</el-table>', tableStart)
  const table = descriptor.template.content.slice(tableStart, tableEnd)
  assert.equal((table.match(/@click="removeItem\(form\.items\.indexOf\(row\)\)"/g) || []).length, 1)
  assert.ok(table.indexOf('fixed="left"') < table.indexOf('type="index"'))
  assert.match(table, /v-if="!readonly"/)
  assert.match(table, /:aria-expanded="bundleExpanded\(row\)"/)
  assert.match(table, /'ArrowUp' : 'ArrowDown'/)
})
