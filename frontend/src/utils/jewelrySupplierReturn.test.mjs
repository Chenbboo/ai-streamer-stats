import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeSupplierReturnRows, supplierReturnSourceReference, supplierReturnQuantityValid, supplierReturnMaxQty } from './jewelrySupplierReturn.js'

const blank = () => ({ qty: 1, unitPrice: 0 })
const source = (documentId, itemId, productId = 1) => ({ documentId, docNo: `RK-${documentId}`, items: [
  { itemId, productId, remainingReturnQty: 5, availableReturnQty: 5, unitPrice: documentId }
] })

test('selecting another purchase appends rows and preserves entered quantities and prices', () => {
  const first = mergeSupplierReturnRows([blank()], source(1, 11), blank)
  first[0].qty = 2; first[0].unitPrice = 1.1234
  const rows = mergeSupplierReturnRows(first, source(2, 22), blank)
  assert.equal(rows.length, 2)
  assert.equal(rows[0], first[0])
  assert.equal(rows[0].qty, 2)
  assert.equal(rows[0].unitPrice, 1.1234)
  assert.equal(rows[1].sourceDocNo, 'RK-2')
  assert.equal(rows[1].sourceDocumentId, 2)
  assert.equal(rows[1].sourceUnitPrice, 2)
  assert.deepEqual(supplierReturnSourceReference(rows), { sourceDocumentId: 1, sourceDocNo: 'RK-1' })
  assert.deepEqual(supplierReturnSourceReference(rows.slice(1)), { sourceDocumentId: 2, sourceDocNo: 'RK-2' })
})

test('repeat selection deduplicates source items rather than merging different purchase batches', () => {
  const first = mergeSupplierReturnRows([], source(1, 11), blank)
  const repeated = mergeSupplierReturnRows(first, source(1, '11'), blank)
  assert.equal(repeated.length, 1)
  assert.equal(repeated[0], first[0])
  assert.equal(mergeSupplierReturnRows(first, source(2, 22), blank).length, 2)
  assert.equal(mergeSupplierReturnRows(first, { documentId: 3, items: [{ itemId: 33, remainingReturnQty: 0 }] }, blank).length, 1)
})

test('editing refreshes only retained rows, keeps removed rows removed and flags exhausted quotas', () => {
  let first = mergeSupplierReturnRows([], source(1, 11), blank)
  first[0].qty = 3; first[0].unitPrice = 9
  const refreshedSource = source(1, 11)
  refreshedSource.items[0].remainingReturnQty = 1
  refreshedSource.items.push({ itemId: 12, productId: 2, remainingReturnQty: 5 })
  const refreshed = mergeSupplierReturnRows(first, refreshedSource, blank, true)
  assert.equal(refreshed.length, 1)
  assert.equal(refreshed[0].qty, 3)
  assert.equal(refreshed[0].unitPrice, 9)
  assert.equal(refreshed[0].remainingReturnQty, 1)
  assert.equal(supplierReturnQuantityValid(refreshed), false)
  const absent = mergeSupplierReturnRows(first, { documentId: 1, items: [] }, blank, true)
  assert.equal(absent.length, 1)
  assert.equal(absent[0].remainingReturnQty, 0)
})

test('shared inventory limits the sum across batches, and each batch retains its own quota', () => {
  let rows = mergeSupplierReturnRows([], source(1, 11), blank)
  rows = mergeSupplierReturnRows(rows, source(2, 22), blank)
  rows[0].qty = 3; rows[1].qty = 2
  assert.equal(supplierReturnQuantityValid(rows), true)
  assert.equal(supplierReturnMaxQty(rows[1], rows), 2)
  rows[1].qty = 3
  assert.equal(supplierReturnQuantityValid(rows), false)
  rows[1].qty = 1; rows[1].remainingReturnQty = 0
  assert.equal(supplierReturnQuantityValid(rows), false)
  rows[1].remainingReturnQty = 5; rows[1].sourceItemId = 11
  assert.equal(supplierReturnQuantityValid(rows), false)
})
