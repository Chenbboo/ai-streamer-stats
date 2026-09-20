-- Goods number belongs only to a sample receipt line; historical lines stay empty.
set @sql=(select if(count(*)=0,
  'alter table jewelry_document_item add column sample_goods_no varchar(64) null after supplier_name_snapshot',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='sample_goods_no');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
