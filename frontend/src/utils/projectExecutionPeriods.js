// 历史段的结束日期是实际停用日期；当前持续工作段显示编辑表单中的计划结束日期。
export function workExecutionPeriods(row, today) {
  const inactive = row.status === 'VOID' || row.activeStatus === 'VOID'
  const routine = row.routineId != null
  if (row.executionPeriods?.length) {
    return row.executionPeriods.map(period => {
      const ongoing = period.status === 'ACTIVE' || (!period.status && !period.endDate && !inactive)
      if (!ongoing) return period
      return routine
        ? { ...period, endDate: row.endDate || null, ongoing, longTerm: !row.endDate }
        : { ...period, ongoing }
    })
  }
  const start = row.startDate || row.planStartDate
  if (!start) return []
  return [{
    startDate: start,
    endDate: inactive
      ? (row.endDate || String(row.updateTime || '').slice(0, 10) || today)
      : (routine ? row.endDate || null : null),
    assigneeName: row.assigneeName,
    ongoing: !inactive,
    longTerm: routine && !inactive && !row.endDate
  }]
}
