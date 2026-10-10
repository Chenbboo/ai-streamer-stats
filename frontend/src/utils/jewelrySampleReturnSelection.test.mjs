import test from 'node:test'
import assert from 'node:assert/strict'
import { selectAllSampleReturnProducts, supplierReturnProductQuantitiesValid } from './jewelrySupplierReturn.js'

const blank = () => ({ productId: null, qty: 1, unitPrice: 0, lineReason: '' })
const product = (productId, remainingReturnQty) => ({ productId, sku: `SAMPLE-${productId}`,
  productName: `样品${productId}`, productType: 'SAMPLE', remainingReturnQty })

test('selecting all fills each eligible sample with its entire remaining return quota at zero cost', () => {
  const rows = selectAllSampleReturnProducts([product(1, 3), product(2, 7), product(3, 0), product(4, -1)], [blank()], blank)
  assert.deepEqual(rows.map(row => [row.productId, row.qty, row.unitPrice]), [[1, 3, 0], [2, 7, 0]])
  assert.equal(rows[0].skuSnapshot, 'SAMPLE-1')
  assert.equal(rows[0].productTypeSnapshot, 'SAMPLE')
  assert.equal(supplierReturnProductQuantitiesValid(rows), true)
})

test('repeat selection does not duplicate products and preserves entered reasons while clearing old source allocations', () => {
  const current = [{ productId: '1', qty: 1, unitPrice: 9, lineReason: '样品退回', sourceItemId: 11,
    sourceDocumentId: 22, sourceDocNo: 'OLD' }, { productId: 1, qty: 1 }, { productId: 9, qty: 2 }]
  const options = [product(1, 3), product('1', 3), product(2, 4)]
  const rows = selectAllSampleReturnProducts(options, current, blank)
  assert.equal(rows.length, 2)
  assert.equal(rows[0].lineReason, '样品退回')
  assert.equal(rows[0].qty, 3)
  assert.equal(rows[0].sourceItemId, null)
  assert.equal(rows[0].sourceDocumentId, null)
  assert.equal(rows[0].sourceDocNo, '')
  assert.equal(rows[0].sourceUnitPrice, 0)
  assert.deepEqual(selectAllSampleReturnProducts(options, rows, blank), rows)
  assert.equal(current[0].qty, 1)
})

test('selection follows refreshed supplier availability and ignores invalid quotas', () => {
  const first = selectAllSampleReturnProducts([product(1, 5)], [], blank)
  const refreshed = selectAllSampleReturnProducts([product(1, 2), product(2, 0.5), product(3, 'bad')], first, blank)
  assert.equal(refreshed.length, 1)
  assert.equal(refreshed[0].qty, 2)
  assert.deepEqual(selectAllSampleReturnProducts([], first, blank), [])
  assert.deepEqual(selectAllSampleReturnProducts([product(7, 1)], first, blank).map(row => row.productId), [7])
})
