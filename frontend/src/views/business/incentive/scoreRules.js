const validNumber = value => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
export const isScoreRule = rule => rule?.policyVersion === 'SCORE_TIERS_V1'
export function validTiers(tiers) {
  if (!tiers?.length || tiers.length > 20) return false
  let next = 0
  return tiers.every((tier, index) => {
    if (!validNumber(tier.minScore) || Number(tier.minScore) !== next || next < 0 || next > 120) return false
    if (!validNumber(tier.amount) || Number(tier.amount) < 0 || Number(tier.amount) > 99999999999999.99) return false
    if (index === tiers.length - 1) return tier.maxScore === null
    if (!validNumber(tier.maxScore) || Number(tier.maxScore) <= next || Number(tier.maxScore) > 120) return false
    next = Number(tier.maxScore)
    return true
  }) && tiers.some(tier => Number(tier.amount) > 0)
}
export function matchTier(tiers, score) {
  if (!validNumber(score) || Number(score) < 0 || Number(score) > 120 || !validTiers(tiers)) return null
  return tiers.find(tier => Number(score) >= Number(tier.minScore) && (tier.maxScore === null || Number(score) < Number(tier.maxScore))) || null
}
