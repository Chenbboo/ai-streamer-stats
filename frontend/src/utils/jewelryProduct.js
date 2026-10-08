import { translateText } from '../locales/translate.js'
export const jewelryProductTypes = [
  { value: 'FINISHED', label: translateText("成品商品"), tagType: 'success' },
  { value: 'PART', label: translateText("散件商品"), tagType: 'warning' },
  { value: 'ACCESSORY', label: translateText("配件商品"), tagType: 'primary' },
  { value: 'WELFARE', label: translateText("福利商品"), tagType: 'danger' },
  { value: 'SAMPLE', label: translateText("样品商品"), tagType: 'info' },
  { value: 'GIFT', label: translateText("赠品商品"), tagType: 'info' }
]

export const jewelryProductType = value => jewelryProductTypes.find(item => item.value === value)

const csvHasId = (value, id) => id != null && String(value || '').split(',').some(item => item.trim() === String(id))

export const matchesJewelryProductFilters = (product, filters = {}, relations = {}) => {
  if (!product) return false
  if (filters.productType && product.productType !== filters.productType) return false
  if (filters.influencerId) {
    const matchesInfluencer = relations.influencerProductIds instanceof Set
      ? relations.influencerProductIds.has(String(product.productId))
      : csvHasId(product.influencerIds, filters.influencerId)
    if (!matchesInfluencer) return false
  }
  if (filters.supplierId && !csvHasId(product.supplierIds, filters.supplierId)
    && !csvHasId(product.boundSupplierIds, filters.supplierId)
    && !(relations.boundSupplierProductIds instanceof Set
      && relations.boundSupplierProductIds.has(String(product.productId)))
    && !String(product.supplierNames || '').split('、').some(name => name.trim() === filters.supplierName)) return false
  return true
}
