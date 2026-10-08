const groupKey = value => value == null || String(value).trim() === '' || String(value) === '0' ? null : String(value)
const role = row => row?.saleRole || 'NORMAL'

export function documentBundleAddons(items, main) {
  const key = groupKey(main?.bundleGroupNo)
  if (role(main) !== 'MAIN' || key == null) return []
  return items.filter(row => role(row) === 'ADDON' && groupKey(row.bundleGroupNo) === key)
}

export function documentBundleExpanded(docType, expanded, groupNo) {
  const key = groupKey(groupNo)
  if (key == null) return true
  return expanded[key] == null ? docType !== 'SALES_OUT' : Boolean(expanded[key])
}

// Visibility only: retain original row objects and leave all persisted quantities and totals untouched.
export function visibleDocumentBundleItems(items, docType, expanded, enabled = true) {
  if (!enabled) return items
  const mains = new Set(items.filter(row => role(row) === 'MAIN').map(row => groupKey(row.bundleGroupNo)).filter(key => key != null))
  return items.filter(row => {
    const key = groupKey(row.bundleGroupNo)
    return role(row) !== 'ADDON' || key == null || !mains.has(key) || documentBundleExpanded(docType, expanded, key)
  })
}
