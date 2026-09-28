export const spendHistoryQuery = (mode, month, date) => mode === 'month' ? { month } : { bizDate: date }

export function spendPersonnelDescription(value, currency, translateText, money) {
  if (!value) return '—'
  let basis
  try { basis = typeof value === 'string' ? JSON.parse(value) : value } catch { return String(value) }
  if (!basis || typeof basis !== 'object' || Array.isArray(basis)) return String(value)
  if (basis.issue) return basis.issue
  const parts = []
  if (basis.fullDailyCost != null) parts.push(translateText('日成本：{0} {1}', [money(basis.fullDailyCost), currency]))
  if (basis.allocationPercent != null) parts.push(translateText('项目投入：{0}%', [basis.allocationPercent]))
  if (basis.attendanceAdjustment) parts.push(translateText('含请假调整'))
  return parts.join(' · ') || (basis.formula ? translateText(basis.formula) : String(value))
}

export function spendExpenseItems(row, currency, translateText, money) {
  const items = [...(row.expenseItems || [])]
  if (Number(row.publicCost) || Number(row.publicEstimatedCost)) items.push({
    categoryName: translateText('公共费用（含暂估）'), amount: row.publicCost, currency, status: 'CONFIRMED',
    description: translateText('公共费用按日确认，其中暂估：{0} {1}（已包含在本项中）', [money(row.publicEstimatedCost), currency])
  })
  return items
}
