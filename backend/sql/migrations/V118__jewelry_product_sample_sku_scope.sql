-- A sample and a non-sample product may share an SKU. Stock and documents remain keyed by product_id.
set @has_sku_scope := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'jewelry_product' and column_name = 'sku_scope');
set @sql := if(@has_sku_scope = 0,
  'alter table jewelry_product add column sku_scope tinyint generated always as (case when product_type = ''SAMPLE'' then 1 else 0 end) stored comment ''1样品 0其他商品'' after product_type',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_scoped_index := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'jewelry_product' and index_name = 'uk_jewelry_product_sku_scope');
set @sql := if(@has_scoped_index = 0,
  'alter table jewelry_product add unique key uk_jewelry_product_sku_scope (sku, sku_scope)',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_old_index := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'jewelry_product' and index_name = 'uk_jewelry_product_sku');
set @sql := if(@has_old_index > 0,
  'alter table jewelry_product drop index uk_jewelry_product_sku',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
