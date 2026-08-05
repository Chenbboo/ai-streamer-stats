-- 销售组合中的搭售商品可以标记为包装耗材，用于按实际库存成本核算包装费。
set @add_packaging_material = (
  select if(count(*) = 0,
    'alter table jewelry_document_item add column packaging_material tinyint(1) not null default 0 comment ''是否作为包装耗材计入组合包装费'' after pricing_mode',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'jewelry_document_item'
    and column_name = 'packaging_material'
);
prepare add_packaging_material_stmt from @add_packaging_material;
execute add_packaging_material_stmt;
deallocate prepare add_packaging_material_stmt;
