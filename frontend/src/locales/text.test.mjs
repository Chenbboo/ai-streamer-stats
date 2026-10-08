import test from 'node:test'
import assert from 'node:assert/strict'
import { translateCopy, translateMessage, sourceText } from './text.js'
import i18n from './index.js'
import { translateText } from './translate.js'

test('Chinese copy is preserved and Vietnamese copy uses the same parameters', () => {
  assert.equal(translateCopy('项目：{0}', 'zh-CN', ['上海项目']), '项目：上海项目')
  assert.equal(translateCopy('项目：{0}', 'vi-VN', ['上海项目']), 'Dự án: 上海项目')
  assert.equal(translateCopy('项目：{0}', 'vi-VN', ['{1}<test>']), 'Dự án: {1}<test>')
  assert.equal(translateCopy('未知用户原文', 'vi-VN'), '未知用户原文')
  assert.equal(translateCopy(null, 'vi-VN'), null)
})

test('server errors translate whole sentences while keeping project names and quantities intact', () => {
  const source = '请先更换项目“上海测试项目”的主负责人'
  const translated = translateMessage(source, 'vi-VN')
  assert.notEqual(translated, source)
  assert.ok(translated.includes('上海测试项目'))
  assert.equal(translated.replace('上海测试项目', '').match(/[\u3400-\u9fff]/), null)
  assert.equal(translateMessage(source, 'zh-CN'), source)
  assert.equal(translateMessage('单个附件不能超过25MB', 'vi-VN').includes('25'), true)
  assert.equal(translateMessage('记录备注：用户填写的原文', 'vi-VN'), '记录备注：用户填写的原文')
})

test('locale changes update shared messages in both directions', () => {
  try {
    i18n.global.locale.value = 'vi-VN'
    assert.equal(translateText('保存'), 'Lưu')
    assert.equal(sourceText(translateText('已确认')), '已确认')
    i18n.global.locale.value = 'zh-CN'
    assert.equal(translateText('保存'), '保存')
  } finally { i18n.global.locale.value = 'zh-CN' }
})

test('Vietnamese product labels retain canonical product type values', async () => {
  try {
    i18n.global.locale.value = 'vi-VN'
    const { jewelryProductTypes } = await import('../utils/jewelryProduct.js?locale-test')
    assert.deepEqual(jewelryProductTypes.map(item => item.value), ['FINISHED', 'PART', 'ACCESSORY', 'WELFARE', 'SAMPLE', 'GIFT'])
    const { buildProductBatchRequest } = await import('../utils/jewelryProductBatch.js')
    assert.deepEqual(buildProductBatchRequest([{ productId: 1 }], ['unit'], { unit: '件' }, true), {
      productIds: [1], changes: { unit: '件' }
    })
    assert.throws(() => buildProductBatchRequest([{ productId: 1 }], ['specification'], { specification: '普通' }, true))
  } finally { i18n.global.locale.value = 'zh-CN' }
})

test('sample supplier-return warnings translate in Chinese and Vietnamese without losing source details', () => {
  const messages = [
    '个商品退供不足7天',
    '退供不足7天的成品和样品',
    '统计有可售库存的成品和样品，距供应商退货期限不足7天（含今天到期和已超期，不含剩余7天）'
  ]
  for (const source of messages) {
    assert.equal(translateCopy(source, 'zh-CN'), source)
    const translated = translateCopy(source, 'vi-VN')
    assert.notEqual(translated, source)
    assert.equal(translated.match(/[\u3400-\u9fff]/), null)
  }
  const source = '样品入库来源：{0}；供应商：{1}。按每行入库日期及剩余库存推算，显示最早退货期限。'
  for (const locale of ['zh-CN', 'vi-VN']) {
    const translated = translateCopy(source, locale, ['YP-001', 'JC'])
    assert.ok(translated.includes('YP-001'))
    assert.ok(translated.includes('JC'))
    assert.equal(translated.match(/\{[01]\}/), null)
  }
})
