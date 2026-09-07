import request from '@/utils/request'

export const getIncentiveWorkspace = params => request({ url: '/business/incentive/workspace', method: 'get', params })
export const createIncentiveRule = data => request({ url: '/business/incentive/rule', method: 'post', data })
export const estimateIncentive = data => request({ url: '/business/incentive/estimate', method: 'post', data })
export const createIncentiveAward = data => request({ url: '/business/incentive/award', method: 'post', data })
export const actIncentiveAward = (id, action, data) => request({ url: `/business/incentive/award/${id}/${action}`, method: 'post', data })

export const retireIncentiveRule = (id, data) => request({ url: `/business/incentive/rule/${id}/retire`, method: 'post', data })
