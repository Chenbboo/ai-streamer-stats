-- Sample receipts may contain products with different inbound business dates.
-- Existing sample receipts inherit their document date; other document types keep NULL.
set @sql=(select if(count(*)=0,
  'alter table jewelry_document_item add column biz_date date null comment ''样品入库明细业务日期'' after image_urls',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='biz_date');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

update jewelry_document_item item
join jewelry_document document on document.document_id=item.document_id
set item.biz_date=document.biz_date
where document.doc_type='SAMPLE_IN' and item.biz_date is null;
