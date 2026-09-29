// Sources stay at row level; the document header is only a legacy first-source reference.
export function mergeSupplierReturnRows(currentRows, source, blankRow, refreshOnly = false) {
  const existing = currentRows.filter(row => row.productId && row.sourceItemId)
  const sourceItems = new Map((source.items || []).map(item => [String(item.itemId), item]))
  const build = (item, current) => ({
    ...blankRow(), ...(current || {}), productId: item.productId, sourceItemId: item.itemId,
    sourceDocumentId: source.documentId, sourceDocNo: source.docNo || '',
    qty: current ? Number(current.qty || 0) : Math.min(1, Number(item.remainingReturnQty || 0)),
    remainingReturnQty: Number(item.remainingReturnQty || 0),
    availableReturnQty: item.availableReturnQty == null ? undefined : Number(item.availableReturnQty),
    unitPrice: current ? Number(current.unitPrice || 0) : Number(item.unitPrice || 0),
    sourceUnitPrice: Number(item.unitPrice || 0),
    productTypeSnapshot: item.productTypeSnapshot || '', specificationSnapshot: item.specificationSnapshot || '',
    imageUrls: item.imageUrls || ''
  })
  if (refreshOnly) return existing.map(row => {
    if (String(row.sourceDocumentId) !== String(source.documentId)) return row
    const item = sourceItems.get(String(row.sourceItemId))
    return item ? build(item, row) : { ...row, remainingReturnQty: 0 }
  })
  const selected = new Set(existing.map(row => String(row.sourceItemId)))
  return [...existing, ...(source.items || [])
    .filter(item => Number(item.remainingReturnQty || 0) > 0 && !selected.has(String(item.itemId)))
    .map(item => build(item, null))]
}

export function supplierReturnSourceReference(rows) {
  const first = rows.find(row => row.productId && row.sourceItemId && row.sourceDocumentId)
  return { sourceDocumentId: first?.sourceDocumentId || null, sourceDocNo: first?.sourceDocNo || '' }
}

export function supplierReturnQuantityValid(rows) {
  const totals = new Map(), limits = new Map(), sources = new Set()
  for (const row of rows) {
    const source = String(row.sourceItemId), product = String(row.productId), qty = Number(row.qty || 0)
    if (!row.sourceItemId || sources.has(source) || qty <= 0 || qty > Number(row.remainingReturnQty || 0)) return false
    sources.add(source)
    totals.set(product, (totals.get(product) || 0) + qty)
    if (row.availableReturnQty != null) limits.set(product, Math.min(limits.get(product) ?? Infinity, Number(row.availableReturnQty)))
  }
  return [...totals].every(([product, qty]) => qty <= (limits.get(product) ?? Infinity))
}

export function supplierReturnMaxQty(row, rows) {
  const others = rows.filter(item => item !== row && String(item.productId) === String(row.productId))
    .reduce((sum, item) => sum + Number(item.qty || 0), 0)
  return Math.max(1, Math.min(Number(row.remainingReturnQty || 0),
    row.availableReturnQty == null ? Infinity : Number(row.availableReturnQty) - others))
}
