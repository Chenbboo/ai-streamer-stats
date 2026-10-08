import request from '@/utils/request'

export const listProjectProposals = params => request({ url: '/business/project-proposal/list', method: 'get', params })
export const listProposalReviews = params => request({ url: '/business/project-proposal/review-list', method: 'get', params })
export const listProposalDirectory = params => request({ url: '/business/project-proposal/directory', method: 'get', params })
export const getProjectProposal = id => request({ url: `/business/project-proposal/${id}`, method: 'get' })
export const getProjectProposalOptions = () => request({ url: '/business/project-proposal/options', method: 'get' })
export const getProjectProposalStaffOptions = params => request({ url: '/business/project-proposal/staff-options', method: 'get', params })
export const getProjectProposalStaffAllocationPreview = (params, config = {}) => request({ url: '/business/project-proposal/staff-allocation-preview', method: 'get', params, ...config })
export const getProjectProposalParentFunding = (parentProjectId, params = {}) => request({ url: `/business/project-proposal/parent-funding/${parentProjectId}`, method: 'get', params })
export const addProjectProposal = (data, config = {}) => request({ url: '/business/project-proposal', method: 'post', data, ...config })
export const updateProjectProposal = (data, config = {}) => request({ url: '/business/project-proposal', method: 'put', data, ...config })
export const deleteProjectProposal = id => request({ url: `/business/project-proposal/${id}`, method: 'delete' })
export const submitProjectProposal = (id, config = {}) => request({ url: `/business/project-proposal/${id}/submit`, method: 'post', ...config })
export const withdrawProjectProposal = (id, data = {}) => request({ url: `/business/project-proposal/${id}/withdraw`, method: 'post', data })
export const reviewProjectProposal = (id, data) => request({ url: `/business/project-proposal/${id}/review`, method: 'put', data })

export const estimateProjectProposalBudget = data => request({ url: '/business/project-proposal/budget-estimate', method: 'post', data, headers: { repeatSubmit: false } })
