-- 销售出库明细增加三个按件填写的其他费用字段；脚本可重复执行。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='other_fee1') = 0,
  "alter table jewelry_document_item add column other_fee1 decimal(18,6) not null default 0 comment '其他费用1/件' after cert_fee",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='other_fee2') = 0,
  "alter table jewelry_document_item add column other_fee2 decimal(18,6) not null default 0 comment '其他费用2/件' after other_fee1",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item'
     and column_name='other_fee3') = 0,
  "alter table jewelry_document_item add column other_fee3 decimal(18,6) not null default 0 comment '其他费用3/件' after other_fee2",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
