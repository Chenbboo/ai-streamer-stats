import test from 'node:test'
import assert from 'node:assert/strict'
import { buildProductBatchRequest } from './jewelryProductBatch.js'

const rows = [{ productId: 1, productName: 'A' }, { productId: 2, productName: 'B' }]
test('only checked fields are sent, not stale product rows or unchecked defaults', () => {
  assert.deepEqual(buildProductBatchRequest(rows, ['productType'], { productType: 'SAMPLE', productName: 'no', warningQty: 0 }, true),
    { productIds: [1, 2], changes: { productType: 'SAMPLE' } })
})
test('zero and deliberate empty values are retained', () => {
  const changes = { warningQty: 0, imageUrls: '' }
  assert.deepEqual(buildProductBatchRequest(rows, Object.keys(changes), changes, true).changes, changes)
})
test('basic-only custom permission can edit name or image but not protected fields', () => {
  assert.deepEqual(buildProductBatchRequest(rows, ['productName'], { productName: ' New ' }, false).changes, { productName: 'New' })
  assert.throws(() => buildProductBatchRequest(rows, ['productType'], { productType: 'SAMPLE' }, false))
  assert.throws(() => buildProductBatchRequest(rows, ['avgCost'], { avgCost: 0 }, true))
})
test('empty selection, duplicate ids and unselected fields cannot be saved', () => {
  assert.throws(() => buildProductBatchRequest([], ['status'], { status: '1' }, true))
  assert.throws(() => buildProductBatchRequest([rows[0], rows[0]], ['status'], { status: '1' }, true))
  assert.throws(() => buildProductBatchRequest(rows, [], {}, true))
})
test('invalid values cannot be saved', () => {
  for (const [key, value] of [['productName', ' '], ['unit', ''], ['status', '3'], ['specification', 'x'],
    ['warningQty', 1.5], ['warningQty', undefined], ['warningQty', -1]]) {
    assert.throws(() => buildProductBatchRequest(rows, [key], { [key]: value }, true))
  }
})
