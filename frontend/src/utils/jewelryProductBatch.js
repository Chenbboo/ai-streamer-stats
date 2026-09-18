import { jewelryProductTypes, jewelrySpecifications } from './jewelryProduct.js'

export const productBatchFields = [
  { key: 'productType', label: '商品类型', kind: 'select', options: jewelryProductTypes },
  { key: 'category', label: '分类', kind: 'text', max: 64 },
  { key: 'specification', label: '规格类型', kind: 'select', options: jewelrySpecifications },
  { key: 'unit', label: '单位', kind: 'text', max: 16 },
  { key: 'warningQty', label: '库存预警值', kind: 'number', precision: 0, max: 2147483647 },
  { key: 'status', label: '状态', kind: 'select', options: [{ label: '启用', value: '0' }, { label: '停用', value: '1' }] },
  { key: 'defaultPackFee', label: '默认包装费', kind: 'number', precision: 2, max: 999999999999.99 },
  { key: 'defaultShipFee', label: '默认物流费', kind: 'number', precision: 2, max: 999999999999.99 },
  { key: 'defaultCertFee', label: '默认鉴定费', kind: 'number', precision: 2, max: 999999999999.99 },
  { key: 'productName', label: '商品名称', kind: 'text', max: 128, basic: true },
  { key: 'imageUrls', label: '实物图片', kind: 'image', basic: true }
]

export function buildProductBatchRequest(rows, selectedFields, values, fullEdit) {
  if (!rows.length || rows.length > 200) throw new Error('请选择1到200件商品')
  const ids = rows.map(row => row.productId)
  if (ids.some(id => id == null) || new Set(ids.map(String)).size !== ids.length) throw new Error('商品选择无效，请重新选择')
  if (!selectedFields.length) throw new Error('请勾选需要修改的字段')
  const changes = {}
  for (const key of selectedFields) {
    const field = productBatchFields.find(field => field.key === key)
    if (!field || (!fullEdit && !field.basic)) throw new Error('无权批量修改该字段')
    let value = values[key]
    if (value == null) throw new Error(`请填写${field.label}`)
    if (field.kind === 'number') {
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > field.max
        || Number(value.toFixed(field.precision)) !== value) throw new Error(`${field.label}格式不正确`)
    } else {
      value = String(value).trim()
      if (field.kind === 'select' && !field.options.some(option => option.value === value)) throw new Error(`请选择${field.label}`)
      if (['productName', 'unit'].includes(key) && !value) throw new Error(`${field.label}不能为空`)
      if (field.kind === 'text' && value.length > field.max) throw new Error(`${field.label}内容过长`)
    }
    changes[key] = value
  }
  return { productIds: ids, changes }
}
