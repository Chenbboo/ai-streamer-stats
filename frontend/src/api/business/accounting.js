import request from '@/utils/request'

export const getBusinessAccountingDashboard = params => request({url:'/business/accounting/dashboard',method:'get',params})
export const getBusinessBossAccountingOverview = () => request({url:'/business/accounting/boss-overview',method:'get'})
export const saveBusinessOperatingFact = data => request({url:'/business/accounting/fact',method:'post',data})
export const saveBusinessProjectFact = data => request({url:'/business/accounting/project-fact',method:'post',data})
export const saveBusinessProjectDailySpend = data => request({url:'/business/accounting/project-daily-spend',method:'post',data})
export const confirmBusinessOperatingFact = id => request({url:`/business/accounting/fact/${id}/confirm`,method:'put'})
export const reverseBusinessOperatingFact = (id,data) => request({url:`/business/accounting/fact/${id}/reverse`,method:'post',data})
export const recalculateBusinessProjectDay = data => request({url:'/business/accounting/recalculate',method:'post',data})
export const getBusinessDailyResult = id => request({url:`/business/accounting/result/${id}`,method:'get'})
