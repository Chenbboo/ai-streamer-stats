import test from 'node:test'
import assert from 'node:assert/strict'
import { projectPlanMissingFields } from './projectPlanValidation.js'

const validForm = () => ({
  projectName: 'ins', priority: 'MEDIUM', objective: '目标测试', applicationReason: '理由测试',
  planStartDate: '2026-09-01', planEndDate: '2026-09-30', reason: '',
  targetLines: [], revenueLines: [], expenseLines: []
})

test('save identifies the separate change reason; preview does not require it', () => {
  const form = validForm()
  assert.deepEqual(projectPlanMissingFields(form), [])
  const issues = projectPlanMissingFields(form, { includeReason: true })
  assert.deepEqual(issues.map(issue => issue.path), ['reason'])
  assert.match(issues[0].zh, /依据与原因/)
  form.reason = '   '
  assert.equal(projectPlanMissingFields(form, { includeReason: true }).length, 1)
  form.reason = '调整业务预算'
  assert.deepEqual(projectPlanMissingFields(form, { includeReason: true }), [])
})

test('reports all missing basic fields while optional sections may stay empty', () => {
  const form = validForm()
  Object.assign(form, { projectName: ' ', objective: '', applicationReason: null, planStartDate: '' })
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), [
    'projectName', 'objective', 'applicationReason', 'planStartDate'
  ])
})

test('unlimited projects require their budget period but no end date', () => {
  const form = { ...validForm(), planEndDate: null, budget: { mode: 'TOTAL', businessAmount: 0, cycle: 'MONTH', anchorDate: null } }
  const issues = projectPlanMissingFields(form, { openEnded: true })
  assert.deepEqual(issues.map(issue => issue.path), ['budget.anchorDate'])
  assert.equal(issues[0].zh, '预算所属月份')
  form.budget.anchorDate = '2026-09-01'
  assert.deepEqual(projectPlanMissingFields(form, { openEnded: true }), [])
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), ['planEndDate'])
})

test('added rows identify the exact row and field; zero amounts remain valid', () => {
  const form = validForm()
  form.revenueLines = [
    { revenueType: 'SALES', itemName: '销售', expectedAmount: 0, occurrenceType: 'ONE_TIME', expectedDate: '2026-09-01' },
    { revenueType: 'SERVICE', itemName: '', expectedAmount: 0, occurrenceType: 'MONTHLY', expectedDate: null }
  ]
  form.expenseLines = [{ expenseCategory: 'OTHER', itemName: '推广', purpose: ' ', amount: 0, occurrenceType: 'ONE_TIME', occurDate: null }]
  const issues = projectPlanMissingFields(form)
  assert.deepEqual(issues.map(issue => issue.path), [
    'revenueLines.1.itemName', 'revenueLines.1.expectedDate', 'expenseLines.0.purpose', 'expenseLines.0.occurDate'
  ])
  assert.equal(issues[1].zh, '收入计划第2行：收入月份 / 开始')
  assert.deepEqual([issues[1].section, issues[1].row, issues[1].field], ['revenueLines', 1, 'expectedDate'])
  assert.match(projectPlanMissingFields(form, { dateType: 'date' })[1].zh, /收入日期/)
})

test('delivery targets do not require a value or unit; acceptance evidence is required', () => {
  const form = validForm()
  form.targetLines = [{ targetType: 'DELIVERY', targetName: '交付', acceptanceEvidence: '文件' }]
  assert.deepEqual(projectPlanMissingFields(form), [])
  form.targetLines.push({ targetType: 'QUANTITY', targetName: '数量', targetValue: 0, unit: '', acceptanceEvidence: '' })
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), ['targetLines.1.unit', 'targetLines.1.acceptanceEvidence'])
  form.targetLines = []
  assert.deepEqual(projectPlanMissingFields(form), [])
})

test('budget required fields depend on the selected control mode', () => {
  const form = { ...validForm(), budget: { mode: 'TOTAL', businessAmount: null } }
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), ['budget.businessAmount'])
  form.budget = { mode: 'NONE', reason: ' ' }
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), ['budget.reason'])
  form.budget.reason = '内部研发不设上限'
  assert.deepEqual(projectPlanMissingFields(form), [])
  form.budget = { mode: 'DAILY', dailyLimit: null }
  assert.deepEqual(projectPlanMissingFields(form).map(issue => issue.path), ['budget.dailyLimit'])
})
