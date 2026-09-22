-- Preserve the creator's product-specific rates on each sales document item.
-- NULL means an older/manual line that uses the document's rates.
alter table jewelry_document_item
  add column platform_rate_snapshot decimal(9,6) default null,
  add column commission_rate_snapshot decimal(9,6) default null,
  add column tax_rate_snapshot decimal(9,6) default null;
