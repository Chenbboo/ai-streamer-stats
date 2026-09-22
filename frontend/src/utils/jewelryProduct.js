import { translateText } from '../locales/translate.js'
export const jewelryProductTypes = [
  { value: 'FINISHED', label: translateText("成品商品"), tagType: 'success' },
  { value: 'PART', label: translateText("散件商品"), tagType: 'warning' },
  { value: 'ACCESSORY', label: translateText("配件商品"), tagType: 'primary' },
  { value: 'WELFARE', label: translateText("福利商品"), tagType: 'danger' },
  { value: 'SAMPLE', label: translateText("样品商品"), tagType: 'info' }
]

export const jewelrySpecifications = [
  { value: '精品', label: translateText("精品") },
  { value: '普通', label: translateText("普通") }
]

export const jewelryProductType = value => jewelryProductTypes.find(item => item.value === value)
