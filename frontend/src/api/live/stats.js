import request from '@/utils/request'

export function weeklyStats(query) {
  return request({
    url: '/live/stats/weekly',
    method: 'get',
    params: query
  })
}

export function streamerCardDetail(streamerId) {
  return request({
    url: '/live/stats/streamer-card',
    method: 'get',
    params: { streamerId }
  })
}

export function highValueUsers(streamerId, month) {
  return request({
    url: '/live/stats/high-value-users',
    method: 'get',
    params: { streamerId, month }
  })
}

export function newTippers(streamerId, month) {
  return request({
    url: '/live/stats/new-tippers',
    method: 'get',
    params: { streamerId, month }
  })
}

export function weijiStats(date) {
  return request({
    url: '/live/stats/weiji',
    method: 'get',
    params: { date }
  })
}

export function weijiMonthStats(beginDate, endDate) {
  return request({
    url: '/live/stats/weiji-month',
    method: 'get',
    params: { beginDate, endDate }
  })
}

export function weijiDetail(streamerId, beginDate, endDate) {
  return request({
    url: '/live/stats/weiji-detail',
    method: 'get',
    params: { streamerId, beginDate, endDate }
  })
}

export function adviceData() {
  return request({
    url: '/live/stats/advice',
    method: 'get'
  })
}
