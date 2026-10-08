-- Keep the unit entered on a receipt independent of subsequent product master edits.
set @has_unit_snapshot := (select count(*) from information_schema.columns
  where table_schema=database() and table_name='jewelry_document_item'
    and column_name='unit_snapshot');
set @sql := if(@has_unit_snapshot=0,
  'alter table jewelry_document_item add column unit_snapshot varchar(16) default null comment ''单据商品单位，可手填''',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

update jewelry_document_item item
left join jewelry_product product on product.product_id=item.product_id
set item.unit_snapshot=coalesce(nullif(trim(product.unit),''),'件')
where item.unit_snapshot is null or trim(item.unit_snapshot)='';
