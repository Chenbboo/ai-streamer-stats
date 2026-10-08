import request from '@/utils/request'
export const confirmProjectNoSpend = id => request({url:`/business/flow/project/${id}/no-spend`,method:'post'})
export const getDepartureChecklist = id => request({url:`/business/flow/staff/${id}/departure`,method:'get'})
export const requestDeparture = (id,data) => request({url:`/business/flow/staff/${id}/departure`,method:'post',data})
export const cancelDeparture = id => request({url:`/business/flow/staff/${id}/departure/cancel`,method:'post'})
export const getClosedAdjustments = id => request({url:`/business/flow/project/${id}/adjustments`,method:'get'})
export const requestClosedAdjustment = (id,data) => request({url:`/business/flow/project/${id}/adjustments`,method:'post',data})
export const reviewClosedAdjustment = (id,data) => request({url:`/business/flow/adjustments/${id}/review`,method:'post',data})
