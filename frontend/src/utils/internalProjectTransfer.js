// All projects remain visible; unavailable recipients explain why they cannot receive a transfer.
export function internalTransferProjectIssue(item, { projectId, currency, bizDate }) {
  if (!item) return '请选择指定项目'
  if (String(item.projectId) === String(projectId)) return '不能指定支出项目本身'
  if (Number(item.accountingClosed) !== 0) return '项目核算已关闭'
  if (!item.companyDeptId) return '项目尚未设置归属公司'
  if (!['ACTIVE', 'ACCEPTANCE', 'CLOSED', 'CANCELED'].includes(item.status)) return '项目尚未进入执行'
  if (String(item.currency).toUpperCase() !== String(currency).toUpperCase()) return '项目币种与支出币种不一致'
  const start = String(item.actualStartDate || '').slice(0, 10)
  const end = String(item.actualEndDate || '').slice(0, 10)
  if (start && bizDate < start) return '业务日期早于指定项目开始日期'
  if (['CLOSED', 'CANCELED'].includes(item.status) && (!end || bizDate > end)) return '业务日期超出指定项目执行期间'
  return ''
}
