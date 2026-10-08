-- 库存成本调价与采购入库冲突检查需要按商品快速定位待审单据；脚本可重复执行。

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='jewelry_document_item'
     and index_name='idx_jewelry_item_product_document') = 0,
  'alter table jewelry_document_item add index idx_jewelry_item_product_document (product_id, document_id)',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
