import request from '@/utils/request'

export const listBusinessDepartments = params => request({ url: '/business/department/list', method: 'get', params })
export const listBusinessDepartmentStaff = () => request({ url: '/business/department/staff', method: 'get' })
export const addBusinessDepartment = data => request({ url: '/business/department', method: 'post', data })
export const updateBusinessDepartment = data => request({ url: '/business/department', method: 'put', data })
export const saveBusinessDepartmentSort = data => request({ url: '/business/department/sort', method: 'put', data })
export const removeBusinessDepartment = id => request({ url: `/business/department/${id}`, method: 'delete' })
