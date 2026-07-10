import request from '@/utils/request'

// 明细列表
export function listUpload(query) {
  return request({
    url: '/live/upload/list',
    method: 'get',
    params: query
  })
}

// 按日汇总(完整性)
export function dailySummary(query) {
  return request({
    url: '/live/upload/daily',
    method: 'get',
    params: query
  })
}

// 批量上传截图
export function uploadImages(data) {
  return request({
    url: '/live/upload/img',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data
  })
}

// 提交工作汇报
export function submitReport(data) {
  return request({
    url: '/live/upload/report',
    method: 'post',
    data
  })
}

// 修正工作汇报日期
export function correctReportDate(uploadId, bizDate) {
  return request({
    url: '/live/upload/' + uploadId + '/biz-date',
    method: 'put',
    params: { bizDate }
  })
}

// 删除上传记录
export function delUpload(uploadIds) {
  return request({
    url: '/live/upload/' + uploadIds,
    method: 'delete'
  })
}

// 在职主播下拉
export function listStreamers() {
  return request({
    url: '/live/streamer/listAll',
    method: 'get'
  })
}
