import test from 'node:test'
import assert from 'node:assert/strict'
import { buildOwnerTodos } from './ownerTodos.js'

const permissions = ['business:kpi:manage']
const data = { project: { projectId: 12, status: 'ACTIVE', accountingState: 'OPEN', goalMode: 'NO_TOTAL' } }

test('active owner receives KPI setup todo when no plan has been published', () => {
  const rows = buildOwnerTodos({ data, userId: 9, today: '2026-09-11', permissions,
    kpi: { canManage: true, plans: [] } })
  assert.deepEqual(rows.filter(row => row.key === 'kpi-setup'), [{
    key: 'kpi-setup', title: '设置项目 KPI', detail: '项目已启动，请设置指标并发布考核方案',
    action: 'kpi-settings', urgent: true
  }])
})

test('KPI setup todo disappears after a plan is published', () => {
  const rows = buildOwnerTodos({ data, userId: 9, today: '2026-09-11', permissions,
    kpi: { canManage: true, plans: [{ planId: 3, status: 'PUBLISHED' }] } })
  assert.equal(rows.some(row => row.key === 'kpi-setup'), false)
})

test('voided plans still require a replacement KPI plan', () => {
  const rows = buildOwnerTodos({ data, userId: 9, today: '2026-09-11', permissions,
    kpi: { canManage: true, plans: [{ planId: 3, status: 'VOIDED' }] } })
  assert.equal(rows.some(row => row.key === 'kpi-setup'), true)
})

test('KPI loading or failure does not create a false setup todo', () => {
  const rows = buildOwnerTodos({ data, userId: 9, today: '2026-09-11', permissions, kpi: null })
  assert.equal(rows.some(row => row.key === 'kpi-setup'), false)
})

test('allocation confirmation todo belongs to its review project even when paused', () => {
  const request={requestId:6,projectId:12,userId:11,userName:'袁崇焕',applicantName:'张三',effectiveDate:'2026-09-11'}
  const rows=buildOwnerTodos({data:{project:{...data.project,status:'PAUSED'},pendingAllocationRequests:[request,{...request,requestId:7,projectId:99}]},userId:9,today:'2026-09-11'})
  assert.equal(rows.length,1)
  assert.equal(rows[0].action,'allocation-review')
  assert.equal(rows[0].item.userId,11)
  assert.equal(rows[0].urgent,true)
})
