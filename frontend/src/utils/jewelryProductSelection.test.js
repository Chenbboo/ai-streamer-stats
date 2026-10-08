import test from 'node:test'
import assert from 'node:assert/strict'
import { matchesJewelryProductFilters } from './jewelryProduct.js'

const product = {
  productType: 'FINISHED',
  influencerIds: '12,18',
  supplierIds: '3,5',
  boundSupplierIds: '7'
}

test('product selection filters can be combined', () => {
  assert.equal(matchesJewelryProductFilters(product, { productType: 'FINISHED', influencerId: 18, supplierId: 5 }), true)
  assert.equal(matchesJewelryProductFilters(product, { productType: 'PART' }), false)
  assert.equal(matchesJewelryProductFilters(product, { influencerId: 8 }), false)
})

test('supplier filter accepts posted inbound and influencer binding suppliers', () => {
  assert.equal(matchesJewelryProductFilters(product, { supplierId: 3 }), true)
  assert.equal(matchesJewelryProductFilters(product, { supplierId: 7 }), true)
  assert.equal(matchesJewelryProductFilters(product, { supplierId: 17 }), false)
})

test('empty filters and exact ids behave safely', () => {
  assert.equal(matchesJewelryProductFilters(product), true)
  assert.equal(matchesJewelryProductFilters(product, { influencerId: 2 }), false)
  assert.equal(matchesJewelryProductFilters(null, { productType: 'FINISHED' }), false)
})

test('old product option responses can use loaded bindings and supplier names', () => {
  const oldResponse = { productId: 21, productType: 'FINISHED', supplierNames: '供应商甲、供应商乙' }
  const relations = {
    influencerProductIds: new Set(['21']),
    boundSupplierProductIds: new Set(['21'])
  }
  assert.equal(matchesJewelryProductFilters(oldResponse,
    { influencerId: 8, supplierId: 3, supplierName: '供应商甲' }, relations), true)
  assert.equal(matchesJewelryProductFilters(oldResponse,
    { influencerId: 8, supplierId: 7, supplierName: '其他供应商' }, relations), true)
  assert.equal(matchesJewelryProductFilters(oldResponse,
    { influencerId: 9, supplierId: 3, supplierName: '供应商甲' }, { influencerProductIds: new Set() }), false)
})
