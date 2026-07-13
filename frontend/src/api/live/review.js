import request from '@/utils/request'

export function listReview(query) {
  return request({
    url: '/live/review/list',
    method: 'get',
    params: query
  })
}

export function mockRecognize(uploadId) {
  return request({
    url: '/live/review/mock/' + uploadId,
    method: 'post'
  })
}

export function recognizeUpload(uploadId) {
  return request({
    url: '/live/review/recognize/' + uploadId,
    method: 'post',
    // 识别失败时后端会额外请求一次模型修复 JSON，需覆盖两次模型调用的等待时间。
    timeout: 300000
  })
}

export function saveReviewResult(uploadId, aiResult) {
  return request({
    url: '/live/review/result/' + uploadId,
    method: 'put',
    data: { aiResult }
  })
}

export function confirmReview(uploadId) {
  return request({
    url: '/live/review/confirm/' + uploadId,
    method: 'post'
  })
}
