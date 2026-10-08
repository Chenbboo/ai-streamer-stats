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

export function confirmReviews(uploadIds) {
  return request({
    url: '/live/review/confirm/batch',
    method: 'post',
    data: uploadIds,
    // 大任务组会在一个事务中完成，避免沿用全局 10 秒超时导致前端误报失败。
    timeout: 300000
  })
}
