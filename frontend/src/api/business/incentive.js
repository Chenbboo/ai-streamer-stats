import request from '@/utils/request'
export const getBonusDistribution = params => request({ url: '/business/incentive/distribution/workspace', method: 'get', params })
export const saveBonusAllocation = data => request({ url: '/business/incentive/distribution/allocation', method: 'post', data })
export const actBonusAllocation = (id, action, data) => request({ url: `/business/incentive/distribution/allocation/${id}/${action}`, method: 'post', data })
export const recordBonusPayment = data => request({ url: '/business/incentive/distribution/payment', method: 'post', data })

export const getIncentiveWorkspace = params => request({ url: '/business/incentive/workspace', method: 'get', params })
export const createIncentiveRule = data => request({ url: '/business/incentive/rule', method: 'post', data })
export const estimateIncentive = data => request({ url: '/business/incentive/estimate', method: 'post', data })
export const createIncentiveAward = data => request({ url: '/business/incentive/award', method: 'post', data })
export const actIncentiveAward = (id, action, data) => request({ url: `/business/incentive/award/${id}/${action}`, method: 'post', data })

export const retireIncentiveRule = (id, data) => request({ url: `/business/incentive/rule/${id}/retire`, method: 'post', data })
