const number = value => Number(value || 0)

function bonusPercentage(line, sourceAmount) {
  if (line.percentage != null) return number(line.percentage)
  const source = number(sourceAmount)
  return source > 0 ? Math.round(number(line.amount) / source * 10000) / 100 : null
}

export function memberAllocationRows(applicationAwards = [], distribution = {}) {
  const batches = distribution.allocations || []
  const sourceByAward = new Map((distribution.awards || []).map(award => [String(award.awardId), award.sourceAmount]))
  const awarded = new Set(batches.map(batch => String(batch.awardId)))
  const proposals = applicationAwards
    .filter(award => ['SUBMITTED', 'APPROVED'].includes(award.status) && award.applicationAllocation?.lines?.length && !awarded.has(String(award.awardId)))
    .flatMap(award => award.applicationAllocation.lines.map((line, index) => ({
      key: `award-${award.awardId}-${index}`,
      award,
      batch: null,
      line,
      firstInBatch: index === 0,
      userName: line.userName,
      percentage: bonusPercentage(line, award.ruleMainOwnerBonusAmount ?? award.amount),
      amount: line.amount,
      currency: award.currency,
      paidAmount: 0,
      paymentStatus: 'UNPAID',
      allocationStatus: award.status
    })))
  const allocated = batches.flatMap(batch => (batch.lines || []).map((line, index) => ({
    key: `allocation-${batch.allocationId}-${line.lineId ?? index}`,
    award: null,
    batch,
    line,
    firstInBatch: index === 0,
    userName: line.userName,
    percentage: bonusPercentage(line, sourceByAward.get(String(batch.awardId))),
    amount: line.amount,
    currency: batch.currency,
    paidAmount: line.paidAmount || 0,
    paymentStatus: line.paymentStatus || 'UNPAID',
    allocationStatus: batch.status
  })))
  return [...proposals, ...allocated]
}

export function allocatedMembersBySource(distribution = {}) {
  const byAward = new Map()
  for (const row of memberAllocationRows([], distribution)) {
    if (!row.batch || row.batch.status === 'CANCELED') continue
    const awardId = String(row.batch.awardId)
    if (!byAward.has(awardId)) byAward.set(awardId, [])
    byAward.get(awardId).push(row)
  }
  return byAward
}
