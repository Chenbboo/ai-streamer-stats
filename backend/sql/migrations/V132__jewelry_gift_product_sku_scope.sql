-- Give gift products their own SKU scope while preserving the existing
-- sample and regular-product scopes. Each type retains its own product_id.
set @has_gift_sku_scope := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'jewelry_product'
    and column_name = 'sku_scope' and generation_expression like '%GIFT%');
set @sql := if(@has_gift_sku_scope = 0,
  'alter table jewelry_product modify column sku_scope tinyint generated always as (case when product_type = ''SAMPLE'' then 1 when product_type = ''GIFT'' then 2 else 0 end) stored comment ''0常规 1样品 2赠品''',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
