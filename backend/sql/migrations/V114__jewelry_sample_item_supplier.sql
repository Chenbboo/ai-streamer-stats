-- Sample receipt lines keep their own supplier; image_urls already exists on document items.
set @sql=(select if(count(*)=0,
  'alter table jewelry_document_item add column supplier_id bigint null after biz_date',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='supplier_id');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql=(select if(count(*)=0,
  'alter table jewelry_document_item add column supplier_name_snapshot varchar(128) null after supplier_id',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='supplier_name_snapshot');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- Older sample receipts had no item supplier; leave them empty rather than guessing a source.
