-- Warehouse transfer is outbound-only from the current single warehouse.
-- Names record the route; no receiving inventory is created automatically.
set @sql=(select if(count(*)=0,
  'alter table jewelry_document add column source_warehouse varchar(100) null comment ''调货出库仓库''',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='source_warehouse');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql=(select if(count(*)=0,
  'alter table jewelry_document add column target_warehouse varchar(100) null comment ''调货入库仓库（仅记录去向）''',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='target_warehouse');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
