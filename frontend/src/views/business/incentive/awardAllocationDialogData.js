export function awardAllocationDialogData(award, distribution = {}) {
  if (!award) return { awards: [], allocations: [] }
  const awardId = String(award.awardId)
  const approvedSource = (distribution.awards || []).find(source => String(source.awardId) === awardId)
  const sourceAmount = award.policyVersion === 'PROFIT_SHARE_V1'
    ? award.ruleMainOwnerBonusAmount
    : award.amount ?? award.approvedAmount
  const source = approvedSource || {
    awardId: award.awardId,
    ruleName: award.ruleName,
    score: award.scoreSnapshot,
    currency: award.currency,
    sourceAmount,
    reserved: 0,
    remaining: sourceAmount
  }
  return {
    awards: [source],
    allocations: (distribution.allocations || []).filter(batch => String(batch.awardId) === awardId)
  }
}
