import { profitShareAmount } from './bonusSummary.js'

const cents = value => {
  const parts = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(value))
  return parts ? BigInt(parts[1] + (parts[2] || '').padEnd(2, '0')) : null
}
const money = value => `${value / 100n}.${String(value % 100n).padStart(2, '0')}`
export const emptyAllocationProposal = () => ({ mode: 'AMOUNT', reason: '', lines: [{ userId: null, amount: null, percentage: null, reason: '' }] })
export const hasAllocationProposal = form => !!form.reason?.trim() || form.lines?.some(line => line.userId != null || line.amount != null || line.percentage != null || line.reason?.trim())
const percentUnits = form => (form.lines || []).reduce((sum, line) => sum + (cents(line.percentage) || 0n), 0n)
export const proposalPercentTotal = form => Number(percentUnits(form)) / 100
export function proposalLineAmount(form, line, sourceAmount) {
  if (form.mode !== 'PERCENT') return line.amount
  const lines = form.lines || [], index = lines.indexOf(line), source = cents(sourceAmount)
  if (index === lines.length - 1 && percentUnits(form) === 10000n && source != null) {
    const allocated = lines.slice(0, index).reduce((sum, previous) => sum + (cents(profitShareAmount({ available: true, afterTaxProfit: sourceAmount }, previous.percentage)) || 0n), 0n)
    return allocated <= source ? money(source - allocated) : null
  }
  return profitShareAmount({ available: sourceAmount != null, afterTaxProfit: sourceAmount }, line.percentage)
}
export function proposalTotal(form, sourceAmount) {
  return money((form.lines || []).reduce((sum, line) => sum + (cents(proposalLineAmount(form, line, sourceAmount)) || 0n), 0n))
}
export function proposalError(form, sourceAmount) {
  if (!hasAllocationProposal(form)) return null
  if (!form.reason?.trim() || !form.lines?.length || form.lines.some(line => !line.userId || !line.reason?.trim())) return 'required'
  if (!['AMOUNT', 'PERCENT'].includes(form.mode) || form.lines.length > 200 || new Set(form.lines.map(line => String(line.userId))).size !== form.lines.length) return 'invalid'
  if (form.reason.trim().length > 500 || form.lines.some(line => line.reason.trim().length > 500)) return 'invalid'
  if (form.mode === 'PERCENT' && form.lines.some(line => cents(line.percentage) == null || Number(line.percentage) <= 0 || Number(line.percentage) > 100)) return 'invalid'
  if (form.mode === 'PERCENT' && percentUnits(form) !== 10000n) return 'percentTotal'
  if (form.lines.some(line => (cents(proposalLineAmount(form, line, sourceAmount)) || 0n) <= 0n)) return 'required'
  const source = cents(sourceAmount)
  return source == null || cents(proposalTotal(form, sourceAmount)) > source ? 'invalid' : null
}
export function proposalPayload(form) {
  if (!hasAllocationProposal(form)) return null
  return { mode: form.mode, reason: form.reason.trim(), lines: form.lines.map(line => ({ userId: line.userId,
    amount: form.mode === 'AMOUNT' ? line.amount : null, percentage: form.mode === 'PERCENT' ? line.percentage : null, reason: line.reason.trim() })) }
}
