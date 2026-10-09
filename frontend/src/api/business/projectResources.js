import request from '@/utils/request'

export const getProjectPersonnel = (projectId, params) => request({ url: `/business/project-resources/${projectId}/personnel`, method: 'get', params })
export const getProjectBudgetUsage = (projectId, params) => request({ url: `/business/project-resources/${projectId}/budget`, method: 'get', params })
export const getCompanyProjectBudgets = params => request({ url: '/business/project-resources/projects', method: 'get', params })
