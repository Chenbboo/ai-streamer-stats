-- 销售组合：一个成品主商品可以关联多个搭售散件，并支持包含在组合价或单独计价。
-- 所有结构变更均可重复执行，并兼容已经执行到一半的数据库。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document'
     and column_name='actual_refund_amount') = 0,
  "alter table jewelry_document add column actual_refund_amount decimal(20,2) default null comment '客户退货实际退款总额' after unlinked_reason",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='bundle_group_no') = 0,
  "alter table jewelry_document_item add column bundle_group_no int default null comment '销售组合序号' after source_item_id",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='sale_role') = 0,
  "alter table jewelry_document_item add column sale_role varchar(16) not null default 'NORMAL' comment 'NORMAL独立销售 MAIN主商品 ADDON搭售散件' after bundle_group_no",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='pricing_mode') = 0,
  "alter table jewelry_document_item add column pricing_mode varchar(16) not null default 'SEPARATE' comment 'SEPARATE单独计价 INCLUDED包含在组合价' after sale_role",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='product_type_snapshot') = 0,
  "alter table jewelry_document_item add column product_type_snapshot varchar(16) default null comment '销售时商品类型快照' after product_name_snapshot",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='specification_snapshot') = 0,
  "alter table jewelry_document_item add column specification_snapshot varchar(16) default null comment '销售时规格类型快照' after product_type_snapshot",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

update jewelry_document_item i
join jewelry_product p on p.product_id=i.product_id
set i.product_type_snapshot=p.product_type,
    i.specification_snapshot=p.specification
where i.product_type_snapshot is null
   or i.specification_snapshot is null;

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='jewelry_document_item'
     and index_name='uk_jewelry_document_product') > 0,
  'alter table jewelry_document_item drop index uk_jewelry_document_product',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='jewelry_document_item'
     and index_name='idx_jewelry_document_product') = 0,
  'alter table jewelry_document_item add key idx_jewelry_document_product (document_id, product_id)',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='jewelry_document_item'
     and index_name='idx_jewelry_item_bundle') = 0,
  'alter table jewelry_document_item add key idx_jewelry_item_bundle (document_id, bundle_group_no, sale_role)',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
