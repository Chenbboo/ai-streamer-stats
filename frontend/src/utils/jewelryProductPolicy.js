export const isInfluencerPurchaseType = type => ['FINISHED', 'GIFT', 'WELFARE'].includes(type)
export const isIndependentSalesType = type => ['FINISHED', 'WELFARE'].includes(type)
export const isCustomerReturnType = (type, role = 'NORMAL') => role === 'ADDON'
  ? ['ACCESSORY', 'GIFT'].includes(type)
  : role === 'MAIN' ? type === 'FINISHED' : isIndependentSalesType(type)
