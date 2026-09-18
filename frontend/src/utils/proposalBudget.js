const cents = value => {
  const amount = Number(value)
  return Number.isFinite(amount) ? Math.max(0, Math.round(amount * 100)) : 0
}

export function sumExpenseAmounts(lines = []) {
  return lines.reduce((sum, line) => sum + cents(line.amount), 0) / 100
}

export function normalizeBusinessBudget(value, expenseTotal) {
  return Math.max(cents(value), cents(expenseTotal)) / 100
}

// Keep the additional allowance when expense lines are edited or removed.
export function rebaseBusinessBudget(value, previousTotal, nextTotal) {
  const allowance = Math.max(0, cents(value) - cents(previousTotal))
  return (cents(nextTotal) + allowance) / 100
}
