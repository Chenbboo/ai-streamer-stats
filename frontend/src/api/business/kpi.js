import request from '@/utils/request'

export const getProjectKpiOverview = params => request({ url: '/business/kpi/overview', method: 'get', params })
export const getProjectKpiWorkspace = (projectId, planId) => request({
  url: '/business/kpi/workspace', method: 'get', params: { projectId, planId }
})
export const publishProjectKpiPlan = data => request({ url: '/business/kpi/plan/publish', method: 'post', data })
export const voidProjectKpiPlan = planId => request({ url: `/business/kpi/plan/${planId}`, method: 'delete' })
export const saveProjectKpiResults = (settlementId, data) => request({ url: `/business/kpi/settlement/${settlementId}/results`, method: 'put', data })
export const submitProjectKpiSettlement = settlementId => request({ url: `/business/kpi/settlement/${settlementId}/submit`, method: 'post' })
export const reviewProjectKpiSettlement = (settlementId, data) => request({ url: `/business/kpi/settlement/${settlementId}/review`, method: 'post', data })
