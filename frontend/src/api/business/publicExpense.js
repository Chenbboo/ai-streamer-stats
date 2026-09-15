import request from '@/utils/request'

const base = '/business/public-expenses'

export const getPublicExpenseWorkspace = params => request({ url: `${base}/workspace`, method: 'get', params })
export const savePublicExpensePolicy = data => request({ url: `${base}/policy`, method: 'post', data })
export const generatePublicExpenseMonth = data => request({ url: `${base}/month`, method: 'post', data })
export const savePublicExpenseEntries = (billId, data) => request({ url: `${base}/month/${billId}/entries`, method: 'put', data })
export const savePublicExpenseOwners = (billId, data) => request({ url: `${base}/month/${billId}/owners`, method: 'put', data })
export const copyPreviousPublicExpenseOwners = (billId, data) => request({ url: `${base}/month/${billId}/copy-owners`, method: 'post', data })
export const publishPublicExpenseMonth = (billId, data) => request({ url: `${base}/month/${billId}/publish`, method: 'post', data })
export const settlePublicExpenseMonth = (billId, data) => request({ url: `${base}/month/${billId}/settle`, method: 'post', data })
export const recallPublicExpenseMonth = (billId, data) => request({ url: `${base}/month/${billId}/recall`, method: 'post', data })
export const adjustPublicExpenseMonth = (billId, data) => request({ url: `${base}/month/${billId}/adjust`, method: 'post', data })

export const getOwnerPublicExpenseWorkspace = (month, params = {}) => request({ url: `${base}/owner-workspace`, method: 'get', params: { ...params, month } })
export const savePublicExpenseProjectAllocations = (allocationId, data) => request({ url: `${base}/owner/${allocationId}/projects`, method: 'put', data })
export const submitPublicExpenseProjectAllocations = (allocationId, data) => request({ url: `${base}/owner/${allocationId}/submit`, method: 'post', data })
export const copyPreviousPublicExpenseProjects = (allocationId, data) => request({ url: `${base}/owner/${allocationId}/copy-projects`, method: 'post', data })
export const getProjectPublicExpenseCosts = (projectId, month) => request({ url: `${base}/project/${projectId}`, method: 'get', params: { month } })
