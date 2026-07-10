import request from '@/utils/request'

// 查询 KPI 配置列表
export function listKpiConfig(query) {
  return request({
    url: '/live/kpi/list',
    method: 'get',
    params: query
  })
}

// 获取 KPI 配置详情
export function getKpiConfig(kpiId) {
  return request({
    url: '/live/kpi/' + kpiId,
    method: 'get'
  })
}

// 获取指定主播指定月份的 KPI 配置
export function getKpiByStreamer(streamerId, year, month) {
  return request({
    url: '/live/kpi/get',
    method: 'get',
    params: { streamerId, year, month }
  })
}

// 新增 KPI 配置
export function addKpiConfig(data) {
  return request({
    url: '/live/kpi',
    method: 'post',
    data
  })
}

// 修改 KPI 配置
export function updateKpiConfig(data) {
  return request({
    url: '/live/kpi',
    method: 'put',
    data
  })
}

// 删除 KPI 配置
export function delKpiConfig(kpiIds) {
  return request({
    url: '/live/kpi/' + kpiIds,
    method: 'delete'
  })
}
