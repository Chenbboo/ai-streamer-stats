import test from 'node:test'
import assert from 'node:assert/strict'
import { isInfluencerPurchaseType, isIndependentSalesType, isCustomerReturnType } from './jewelryProductPolicy.js'

test('welfare requires influencer purchase and supports standalone sale and return', () => {
  assert.equal(isInfluencerPurchaseType('WELFARE'), true)
  assert.equal(isIndependentSalesType('WELFARE'), true)
  assert.equal(isCustomerReturnType('WELFARE'), true)
  assert.equal(isCustomerReturnType('WELFARE', 'MAIN'), false)
  assert.equal(isCustomerReturnType('WELFARE', 'ADDON'), false)
})

test('finished, gift and accessory retain their distinct purchase and return roles', () => {
  assert.equal(isInfluencerPurchaseType('FINISHED'), true)
  assert.equal(isInfluencerPurchaseType('GIFT'), true)
  assert.equal(isInfluencerPurchaseType('ACCESSORY'), false)
  assert.equal(isInfluencerPurchaseType('PART'), false)
  assert.equal(isCustomerReturnType('FINISHED', 'MAIN'), true)
  assert.equal(isIndependentSalesType('GIFT'), false)
  assert.equal(isCustomerReturnType('GIFT', 'ADDON'), true)
  assert.equal(isCustomerReturnType('ACCESSORY', 'ADDON'), true)
  assert.equal(isCustomerReturnType('ACCESSORY'), false)
})
