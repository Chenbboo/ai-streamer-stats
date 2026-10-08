-- V102 (formerly local V097): optional per-purchase return deadline; historical documents remain unset.
set @sql=(select if(count(*)=0,
  'alter table jewelry_document add column supplier_return_date date null comment ''约定退货日期'' after biz_date',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='supplier_return_date');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
