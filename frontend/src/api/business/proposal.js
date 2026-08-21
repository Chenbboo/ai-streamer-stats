import request from '@/utils/request'

export const listProjectProposals = params => request({ url: '/business/project-proposal/list', method: 'get', params })
export const listProposalReviews = params => request({ url: '/business/project-proposal/review-list', method: 'get', params })
export const listProposalDirectory = params => request({ url: '/business/project-proposal/directory', method: 'get', params })
export const getProjectProposal = id => request({ url: `/business/project-proposal/${id}`, method: 'get' })
export const getProjectProposalOptions = () => request({ url: '/business/project-proposal/options', method: 'get' })
export const addProjectProposal = data => request({ url: '/business/project-proposal', method: 'post', data })
export const updateProjectProposal = data => request({ url: '/business/project-proposal', method: 'put', data })
export const deleteProjectProposal = id => request({ url: `/business/project-proposal/${id}`, method: 'delete' })
export const submitProjectProposal = id => request({ url: `/business/project-proposal/${id}/submit`, method: 'post' })
export const withdrawProjectProposal = (id, data = {}) => request({ url: `/business/project-proposal/${id}/withdraw`, method: 'post', data })
export const reviewProjectProposal = (id, data) => request({ url: `/business/project-proposal/${id}/review`, method: 'put', data })
