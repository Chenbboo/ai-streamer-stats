export function allocationLockReason(row) {
  if(row.projectDelFlag==='2'||row.freezeReason==='DELETED')return 'DELETED'
  if(row.freezeReason==='ACCOUNTING_CLOSED'||row.accountingState==='CLOSED')return 'ACCOUNTING_CLOSED'
  return row.frozen?'PERIOD_CLOSED':null
}
export function allocationLockLabel(row) {
  return {DELETED:'已删除旧项目',ACCOUNTING_CLOSED:'已关账',PERIOD_CLOSED:'该期间含已冻结账目'}[allocationLockReason(row)]||''
}
export function allocationLockHint(row) {
  return {DELETED:'历史投入不能在此修改，现有调账入口不支持该记录。',ACCOUNTING_CLOSED:'历史金额请在项目详情的“关账后调整”中申请处理。',PERIOD_CLOSED:'该期间的账目已冻结，不能直接修改历史投入。'}[allocationLockReason(row)]||''
}
export function allocationBlockedMessage(row) {
  return {DELETED:'时间段 {0}：项目「{1}」（{2}）已删除，历史投入不能在此修改；现有调账入口不支持该记录，请核对历史归属或选择其他时间段。',ACCOUNTING_CLOSED:'时间段 {0}：项目「{1}」（{2}）已关账，不能直接修改历史投入；历史金额请在“关账后调整”中申请处理。',PERIOD_CLOSED:'时间段 {0}：项目「{1}」（{2}）该期间含已冻结账目，不能直接修改历史投入；请选择未冻结期间或核对历史账目。'}[allocationLockReason(row)]||''
}
