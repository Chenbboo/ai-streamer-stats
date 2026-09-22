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

test('Vietnamese product labels retain canonical specification and product type values', async () => {
  try {
    i18n.global.locale.value = 'vi-VN'
    const { jewelrySpecifications, jewelryProductTypes } = await import('../utils/jewelryProduct.js?locale-test')
    assert.deepEqual(jewelrySpecifications.map(item => item.value), ['精品', '普通'])
    assert.ok(jewelrySpecifications.every(item => !/[\u3400-\u9fff]/.test(item.label)))
    assert.deepEqual(jewelryProductTypes.map(item => item.value), ['FINISHED', 'PART', 'ACCESSORY', 'WELFARE', 'SAMPLE'])
    const { buildProductBatchRequest } = await import('../utils/jewelryProductBatch.js')
    assert.deepEqual(buildProductBatchRequest([{ productId: 1 }], ['specification', 'unit'], { specification: '普通', unit: '件' }, true), {
      productIds: [1], changes: { specification: '普通', unit: '件' }
    })
    assert.throws(() => buildProductBatchRequest([{ productId: 1 }], ['specification'], { specification: 'Thông thường' }, true))
  } finally { i18n.global.locale.value = 'zh-CN' }
})
