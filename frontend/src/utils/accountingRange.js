function validDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

// Preserve the exact period supplied by a review/chart link; regular entry keeps its month filter.
export function accountingRangeFromQuery(query) {
  const { dateFrom, dateTo } = query
  return validDate(dateFrom) && validDate(dateTo) && dateFrom <= dateTo ? [dateFrom, dateTo] : null
}
