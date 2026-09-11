export function flattenProjectRows(roots, expanded) {
  const rows = []
  function visit(row, depth) {
    rows.push({ ...row, depth })
    if (!expanded.has(row.projectId)) return
    if (!row.children.length) rows.push({ projectId: `empty-${row.projectId}`, parentId: row.projectId, empty: true, childLoading: row.childLoading, childError: row.childError, depth: depth + 1 })
    else row.children.forEach(child => visit(child, depth + 1))
  }
  roots.forEach(row => visit(row, 0))
  return rows
}
