import request from '@/utils/request'

export function weeklyStats(query) {
  return request({
    url: '/live/stats/weekly',
    method: 'get',
    params: query
  })
}

export function streamerCardDetail(streamerId, beginDate, endDate) {
  return request({
    url: '/live/stats/streamer-card',
    method: 'get',
    params: { streamerId, beginDate, endDate }
  })
}

export function highValueUsers(streamerId, beginDate, endDate) {
  return request({
    url: '/live/stats/high-value-users',
    method: 'get',
    params: { streamerId, beginDate, endDate }
  })
}

export function newTippers(streamerId, beginDate, endDate) {
  return request({
    url: '/live/stats/new-tippers',
    method: 'get',
    params: { streamerId, beginDate, endDate }
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

export function adviceData(beginDate, endDate) {
  return request({
    url: '/live/stats/advice',
    method: 'get',
    params: { beginDate, endDate }
  })
}
