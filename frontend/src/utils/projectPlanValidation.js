// Only rows that have been added need to be completed. Zero amounts are valid.
export function projectPlanMissingFields(form, { openEnded = false, includeReason = false, dateType = 'month' } = {}) {
  const issues = []
  const missing = value => value == null || (typeof value === 'string' && !value.trim())
  const check = (value, path, zh, vi, section, row, field) => {
    if (missing(value)) issues.push({ path, zh, vi, section, row, field: field || path })
  }
  check(form.projectName, 'projectName', '项目名称', 'Tên dự án')
  check(form.priority, 'priority', '优先级', 'Ưu tiên')
  check(form.objective, 'objective', '范围与目标', 'Phạm vi và mục tiêu')
  check(form.applicationReason, 'applicationReason', '立项理由', 'Lý do lập dự án')
  check(form.planStartDate, 'planStartDate', '计划开始日期', 'Ngày bắt đầu kế hoạch')
  if (!openEnded) check(form.planEndDate, 'planEndDate', '计划结束日期', 'Ngày kết thúc kế hoạch')

  const rows = (section, zh, vi, fields) => (form[section] || []).forEach((row, index) => {
    fields(row).forEach(([field, label, labelVi]) => {
      check(row[field], `${section}.${index}.${field}`, `${zh}第${index + 1}行：${label}`,
        `${vi}, dòng ${index + 1}: ${labelVi}`, section, index, field)
    })
  })
  rows('targetLines', '项目验收目标', 'Mục tiêu nghiệm thu', row => [
    ['targetType', '类型', 'Loại'], ['targetName', '目标名称', 'Tên mục tiêu'],
    ...(row.targetType === 'DELIVERY' ? [] : [['targetValue', '目标值', 'Giá trị mục tiêu'], ['unit', '单位', 'Đơn vị']]),
    ['acceptanceEvidence', '验收依据', 'Căn cứ nghiệm thu']
  ])
  rows('revenueLines', '收入计划', 'Kế hoạch doanh thu', () => [
    ['revenueType', '收入方式', 'Loại doanh thu'], ['itemName', '收入项目', 'Hạng mục doanh thu'],
    ['expectedAmount', '预计金额', 'Số tiền dự kiến'], ['occurrenceType', '发生方式', 'Tần suất'],
    ['expectedDate', dateType === 'date' ? '收入日期 / 开始' : '收入月份 / 开始', 'Ngày hoặc tháng bắt đầu doanh thu']
  ])
  rows('expenseLines', '支出计划', 'Kế hoạch chi phí', () => [
    ['expenseCategory', '类别', 'Loại'], ['itemName', '支出项目', 'Hạng mục chi phí'], ['purpose', '具体用途', 'Mục đích'],
    ['amount', '金额', 'Số tiền'], ['occurrenceType', '发生方式', 'Tần suất'],
    ['occurDate', dateType === 'date' ? '支出日期 / 开始' : '支出月份 / 开始', 'Ngày hoặc tháng bắt đầu chi phí']
  ])

  if (form.budget) {
    const budget = form.budget
    if ((budget.mode || 'TOTAL') === 'TOTAL') check(budget.businessAmount, 'budget.businessAmount', '业务预算', 'Ngân sách kinh doanh')
    if (budget.mode === 'DAILY') check(budget.dailyLimit, 'budget.dailyLimit', '每日预算上限', 'Giới hạn ngân sách hàng ngày')
    if (budget.mode === 'NONE') check(budget.reason, 'budget.reason', '预算说明（不设预算上限的原因）', 'Lý do không giới hạn ngân sách')
    if (openEnded) {
      check(budget.cycle, 'budget.cycle', '预算周期', 'Chu kỳ ngân sách')
      const label = { WEEK: '预算所属周', MONTH: '预算所属月份', QUARTER: '预算起始月份', YEAR: '预算所属年度' }[budget.cycle] || '预算所属期间'
      check(budget.anchorDate, 'budget.anchorDate', label, 'Kỳ ngân sách')
    }
  }
  if (includeReason) check(form.reason, 'reason', '依据与原因（本次计划变更）', 'Căn cứ và lý do thay đổi kế hoạch')
  return issues
}
