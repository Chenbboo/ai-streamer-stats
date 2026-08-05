export const jewelryProductTypes = [
  { value: 'FINISHED', label: '成品商品', tagType: 'success' },
  { value: 'PART', label: '散件商品', tagType: 'warning' },
  { value: 'ACCESSORY', label: '配件商品', tagType: 'primary' },
  { value: 'WELFARE', label: '福利商品', tagType: 'danger' }
]

export const jewelrySpecifications = [
  { value: '精品', label: '精品' },
  { value: '普通', label: '普通' }
]

export const jewelryProductType = value => jewelryProductTypes.find(item => item.value === value)
