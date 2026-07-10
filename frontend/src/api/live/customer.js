import request from '@/utils/request'

export function listCustomers(query) {
  return request({ url: '/live/customer/list', method: 'get', params: query })
}

export function mergeCustomers(primaryId, secondaryId) {
  return request({ url: '/live/customer/merge', method: 'post', params: { primaryId, secondaryId } })
}
