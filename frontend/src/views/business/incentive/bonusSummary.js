// The overview shows current financial results and published shares, never unsaved form values.
export function currentBonusShares(rules = [], setting = {}) {
  const active = rules.filter(rule => rule.policyVersion === 'PROFIT_SHARE_V1' && rule.status === 'ACTIVE')
    .reduce((latest, rule) => !latest || Number(rule.ruleVersion) > Number(latest.ruleVersion) ? rule : latest, null)
  if (active) return active
  return Number(setting.version) > 0 ? setting : null
}

export function profitShareAmount(profitResult, rate) {
  if (!profitResult?.available || rate == null) return null
  const scaled = (value, precision) => {
    const parts = /^(-?)(\d+)(?:\.(\d+))?$/.exec(String(value))
    if (!parts || (parts[3]?.length || 0) > precision) return null
    const units = BigInt(parts[2] + (parts[3] || '').padEnd(precision, '0'))
    return parts[1] ? -units : units
  }
  const profitCents = scaled(profitResult.afterTaxProfit, 2), rateUnits = scaled(rate, 4)
  if (profitCents == null || rateUnits == null || rateUnits < 0n || rateUnits > 1000000n) return null
  // Half-up rounding to cents, with the same positive-profit basis as published rules.
  const cents = ((profitCents > 0n ? profitCents : 0n) * rateUnits + 500000n) / 1000000n
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}
