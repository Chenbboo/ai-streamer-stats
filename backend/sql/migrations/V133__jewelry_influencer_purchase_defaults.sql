-- Purchase suggestions belong to the influencer/product binding, not shared product master data.
set @has_preferred_supplier := (select count(*) from information_schema.columns
  where table_schema=database() and table_name='jewelry_influencer_product_price'
    and column_name='preferred_supplier_id');
set @sql := if(@has_preferred_supplier=0,
  'alter table jewelry_influencer_product_price add column preferred_supplier_id bigint default null comment ''常用供应商，仅作采购录单建议''',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_reference_purchase_price := (select count(*) from information_schema.columns
  where table_schema=database() and table_name='jewelry_influencer_product_price'
    and column_name='reference_purchase_price');
set @sql := if(@has_reference_purchase_price=0,
  'alter table jewelry_influencer_product_price add column reference_purchase_price decimal(18,4) not null default 0 comment ''参考采购单价，仅作采购录单建议''',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_preferred_supplier_index := (select count(*) from information_schema.statistics
  where table_schema=database() and table_name='jewelry_influencer_product_price'
    and index_name='idx_jewelry_influencer_preferred_supplier');
set @sql := if(@has_preferred_supplier_index=0,
  'create index idx_jewelry_influencer_preferred_supplier on jewelry_influencer_product_price(preferred_supplier_id)',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
