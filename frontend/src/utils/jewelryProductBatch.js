import { translateText } from '../locales/translate.js'
import { jewelryProductTypes } from './jewelryProduct.js'

export const productBatchFields = [
  { key: 'productType', label: translateText("商品类型"), kind: 'select', options: jewelryProductTypes },
  { key: 'unit', label: translateText("单位"), kind: 'text', max: 16 },
  { key: 'warningQty', label: translateText("库存预警值"), kind: 'number', precision: 0, max: 2147483647 },
  { key: 'status', label: translateText("状态"), kind: 'select', options: [{ label: translateText("启用"), value: '0' }, { label: translateText("停用"), value: '1' }] },
  { key: 'productName', label: translateText("商品名称"), kind: 'text', max: 128, basic: true },
  { key: 'imageUrls', label: translateText("实物图片"), kind: 'image', basic: true }
]

export function buildProductBatchRequest(rows, selectedFields, values, fullEdit) {
  if (!rows.length || rows.length > 200) throw new Error(translateText("请选择1到200件商品"))
  const ids = rows.map(row => row.productId)
  if (ids.some(id => id == null) || new Set(ids.map(String)).size !== ids.length) throw new Error(translateText("商品选择无效，请重新选择"))
  if (!selectedFields.length) throw new Error(translateText("请勾选需要修改的字段"))
  const changes = {}
  for (const key of selectedFields) {
    const field = productBatchFields.find(field => field.key === key)
    if (!field || (!fullEdit && !field.basic)) throw new Error(translateText("无权批量修改该字段"))
    let value = values[key]
    if (value == null) throw new Error(translateText("请填写{0}", [field.label]))
    if (field.kind === 'number') {
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > field.max
        || Number(value.toFixed(field.precision)) !== value) throw new Error(translateText("{0}格式不正确", [field.label]))
    } else {
      value = String(value).trim()
      if (field.kind === 'select' && !field.options.some(option => option.value === value)) throw new Error(translateText("请选择{0}", [field.label]))
      if (['productName', 'unit'].includes(key) && !value) throw new Error(translateText("{0}不能为空", [field.label]))
      if (field.kind === 'text' && value.length > field.max) throw new Error(translateText("{0}内容过长", [field.label]))
    }
    changes[key] = value
  }
  return { productIds: ids, changes }
}
