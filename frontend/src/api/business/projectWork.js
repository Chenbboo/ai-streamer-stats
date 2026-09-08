import request from '@/utils/request'

export const getProjectWorkOptions = () => request({ url: '/business/project-work/options', method: 'get' })

export const getProjectWork = (id, params) => request({ url: `/business/project-work/${id}/workspace`, method: 'get', params })
export const createProjectCalendar = data => request({ url: '/business/project-work/calendars', method: 'post', data })
export const getProjectWorkHistory = id => request({ url: `/business/project-work/entries/${id}/history`, method: 'get' })
export const getProjectPlan = id => request({ url: `/business/project-work/${id}/plan`, method: 'get' })
export const requestProjectPlanChange = (id, data) => request({ url: `/business/project-work/${id}/plan-changes`, method: 'post', data })
export const reviewProjectPlanChange = (id, data) => request({ url: `/business/project-work/plan-changes/${id}/review`, method: 'post', data })
export const saveProjectForecast = (id, data) => request({ url: `/business/project-work/${id}/forecast`, method: 'post', data })
