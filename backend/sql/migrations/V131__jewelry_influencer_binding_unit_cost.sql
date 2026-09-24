-- Add the commercial unit cost maintained with each creator/product binding.
set @has_unit_cost := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'jewelry_influencer_product_price'
    and column_name = 'unit_cost');
set @sql := if(@has_unit_cost = 0,
  'alter table jewelry_influencer_product_price add column unit_cost decimal(18,4) not null default 0 comment ''绑定商品成本价'' after fixed_unit_price',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Only the first migration run backfills existing bindings. This preserves a
-- deliberately maintained zero cost if the migration script is run again.
set @sql := if(@has_unit_cost = 0,
  'update jewelry_influencer_product_price pp join jewelry_stock s on s.product_id = pp.product_id set pp.unit_cost = round(s.avg_cost, 4) where pp.unit_cost = 0 and s.avg_cost <> 0',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
