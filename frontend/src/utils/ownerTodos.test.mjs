import test from 'node:test'
import assert from 'node:assert/strict'
import { buildOwnerTodos, buildPublicExpenseTodos, buildAllocationReviewTodos } from './ownerTodos.js'

const expense = { allocationId: 51, billStatus: 'PUBLISHED', status: 'DRAFT', companyName: '上海公司', month: '2026-09', remainingAmount: 200, amount: 500, currency: 'CNY' }
test('published owner expense produces a todo linking the exact allocation and month', () => {
  const [todo] = buildPublicExpenseTodos([expense])
  assert.equal(todo.action, 'public-expense')
  assert.equal(todo.allocationId, 51)
  assert.equal(todo.month, '2026-09')
  assert.equal(todo.title, '分摊公共费用')
  assert.match(todo.detail, /上海公司.*2026-09.*200.00 CNY/)
})
test('fully allocated draft still needs submission; submitted, recalled and settled bills do not', () => {
  const bills = [
    {...expense, remainingAmount: 0},
    {...expense, allocationId: 52, status: 'SUBMITTED'},
    {...expense, allocationId: 53, billStatus: 'DRAFT'},
    {...expense, allocationId: 54, billStatus: 'SETTLED'}
  ]
  const todos = buildPublicExpenseTodos(bills)
  assert.equal(todos.length, 1)
  assert.equal(todos[0].title, '提交公共费用分摊')
  assert.match(todos[0].detail, /500.00 CNY/)
})
test('expense todos deduplicate allocations and preserve separate currencies', () => {
  const todos = buildPublicExpenseTodos([expense, {...expense, allocationId: '51'}, {...expense, allocationId: 52, currency: 'VND', amount: 1000}])
  assert.equal(todos.length, 2)
  assert.notEqual(todos[0].key,todos[1].key)
  assert.match(todos[1].detail,/VND/)
  assert.deepEqual(buildPublicExpenseTodos(), [])
})

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

test('reviewer queue includes other-project adjustments once and carries exact employee/date', () => {
  const request={requestId:1,projectId:9,userId:132,userName:'蔡新武',applicantName:'蔡新武',effectiveDate:'2026-09-16'}
  const projects=[{projectId:12,projectName:'3'},{projectId:9,projectName:'meimaru管理系统开发'}]
  const todos=buildAllocationReviewTodos([request,{...request,requestId:'1'}],projects)
  assert.equal(todos.length,1)
  assert.equal(todos[0].projectId,9)
  assert.equal(todos[0].projectName,'meimaru管理系统开发')
  assert.equal(todos[0].item.userId,132)
  assert.equal(todos[0].item.effectiveDate,'2026-09-16')
  assert.match(todos[0].detail,/蔡新武发起/)
  assert.deepEqual(buildAllocationReviewTodos([]),[])
  for(const status of ['APPROVED','APPLIED','REJECTED','WITHDRAWN'])
    assert.deepEqual(buildAllocationReviewTodos([{...request,status}],projects),[])
})
