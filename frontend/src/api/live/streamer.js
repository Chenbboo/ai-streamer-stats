import request from '@/utils/request'

export function listStreamers(query) {
  return request({
    url: '/live/streamer/list',
    method: 'get',
    params: query
  })
}

export function getStreamer(streamerId) {
  return request({
    url: '/live/streamer/' + streamerId,
    method: 'get'
  })
}

export function addStreamer(data) {
  return request({
    url: '/live/streamer',
    method: 'post',
    data
  })
}

export function updateStreamer(data) {
  return request({
    url: '/live/streamer',
    method: 'put',
    data
  })
}

export function delStreamer(streamerIds) {
  return request({
    url: '/live/streamer/' + streamerIds,
    method: 'delete'
  })
}
