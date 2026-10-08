export const isSeparatedDelivery = project => project?.deliveryPolicyVersion === 'SEPARATED_V1'

export const isDeliveryEnded = project => ['CLOSED', 'CANCELED'].includes(project?.status)

// Older responses do not contain an independent accounting state. Never infer
// that a terminal legacy project has reopened its books.
export const projectAccountingState = project => {
  if (project?.deliveryPolicyVersion && !['LEGACY_V1', 'SEPARATED_V1'].includes(project.deliveryPolicyVersion)) return 'UNKNOWN'
  if (!isSeparatedDelivery(project) && isDeliveryEnded(project)) return 'CLOSED'
  if (['OPEN', 'CLOSED'].includes(project?.accountingState)) return project.accountingState
  if (!project?.status || isSeparatedDelivery(project)) return 'UNKNOWN'
  return isDeliveryEnded(project) ? 'CLOSED' : 'OPEN'
}

export const canContinueProjectSettlement = project => projectAccountingState(project) === 'OPEN'
  && (['ACTIVE', 'ACCEPTANCE'].includes(project?.status) || (isSeparatedDelivery(project) && isDeliveryEnded(project)))
