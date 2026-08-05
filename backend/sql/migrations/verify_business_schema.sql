-- 只读验证。missing_* 应为0，数据完整性检查也应为0。

select
  count(case when table_name='live_streamer' then 1 end)=0 as missing_live_streamer,
  count(case when table_name='live_customer' then 1 end)=0 as missing_live_customer,
  count(case when table_name='live_customer_alias' then 1 end)=0 as missing_live_customer_alias,
  count(case when table_name='live_upload' then 1 end)=0 as missing_live_upload,
  count(case when table_name='live_gift_record' then 1 end)=0 as missing_live_gift_record,
  count(case when table_name='live_chat_contact' then 1 end)=0 as missing_live_chat_contact,
  count(case when table_name='live_chat_message' then 1 end)=0 as missing_live_chat_message,
  count(case when table_name='live_follow_record' then 1 end)=0 as missing_live_follow_record,
  count(case when table_name='live_daily_report' then 1 end)=0 as missing_live_daily_report,
  count(case when table_name='live_kpi_config' then 1 end)=0 as missing_live_kpi_config,
  count(case when table_name='live_weiji_stats' then 1 end)=0 as missing_live_weiji_stats
from information_schema.tables
where table_schema=database() and table_name like 'live\_%';

select
  count(case when table_name='jewelry_staff' then 1 end)=0 as missing_jewelry_staff,
  count(case when table_name='jewelry_product' then 1 end)=0 as missing_jewelry_product,
  count(case when table_name='jewelry_supplier' then 1 end)=0 as missing_jewelry_supplier,
  count(case when table_name='jewelry_stock' then 1 end)=0 as missing_jewelry_stock,
  count(case when table_name='jewelry_document' then 1 end)=0 as missing_jewelry_document,
  count(case when table_name='jewelry_document_item' then 1 end)=0 as missing_jewelry_document_item,
  count(case when table_name='jewelry_approval' then 1 end)=0 as missing_jewelry_approval,
  count(case when table_name='jewelry_document_event' then 1 end)=0 as missing_jewelry_document_event,
  count(case when table_name='jewelry_stock_transaction' then 1 end)=0 as missing_jewelry_stock_transaction
from information_schema.tables
where table_schema=database() and table_name like 'jewelry\_%';

select
  count(case when column_name='streamer_id' then 1 end)=0 as missing_customer_streamer_id
from information_schema.columns
where table_schema=database() and table_name='live_customer';

select
  count(case when table_name='jewelry_product' and column_name='product_type' then 1 end)=0
    as missing_jewelry_product_type,
  count(case when table_name='jewelry_product' and column_name='specification' then 1 end)=0
    as missing_jewelry_specification,
  count(case when table_name='jewelry_document' and column_name='actual_refund_amount' then 1 end)=0
    as missing_jewelry_actual_refund_amount,
  count(case when table_name='jewelry_document_item' and column_name='bundle_group_no' then 1 end)=0
    as missing_jewelry_bundle_group_no,
  count(case when table_name='jewelry_document_item' and column_name='sale_role' then 1 end)=0
    as missing_jewelry_sale_role,
  count(case when table_name='jewelry_document_item' and column_name='pricing_mode' then 1 end)=0
    as missing_jewelry_pricing_mode,
  count(case when table_name='jewelry_document_item' and column_name='product_type_snapshot' then 1 end)=0
    as missing_jewelry_product_type_snapshot,
  count(case when table_name='jewelry_document_item' and column_name='specification_snapshot' then 1 end)=0
    as missing_jewelry_specification_snapshot,
  count(case when table_name='jewelry_document_item' and column_name='packaging_material' then 1 end)=0
    as missing_jewelry_packaging_material
from information_schema.columns
where table_schema=database()
  and table_name in ('jewelry_product','jewelry_document','jewelry_document_item');

select 'invalid_jewelry_product_type' check_name, count(*) problem_rows
from jewelry_product
where product_type not in ('FINISHED','PART','ACCESSORY','WELFARE')
union all
select 'invalid_jewelry_specification', count(*)
from jewelry_product
where specification not in ('精品','普通')
union all
select 'invalid_jewelry_packaging_material_usage', count(*)
from jewelry_document_item
where packaging_material=1
  and (sale_role<>'ADDON' or bundle_group_no is null or pricing_mode<>'INCLUDED');

select 'customer_missing_streamer' check_name, count(*) problem_rows
from live_customer c left join live_streamer s on s.streamer_id=c.streamer_id
where s.streamer_id is null
union all
select 'gift_orphan_customer', count(*)
from live_gift_record g left join live_customer c on c.customer_id=g.customer_id
where c.customer_id is null
union all
select 'gift_customer_streamer_mismatch', count(*)
from live_gift_record g join live_customer c on c.customer_id=g.customer_id
where g.streamer_id<>c.streamer_id
union all
select 'chat_orphan_customer', count(*)
from live_chat_contact x left join live_customer c on c.customer_id=x.customer_id
where c.customer_id is null
union all
select 'chat_customer_streamer_mismatch', count(*)
from live_chat_contact x join live_customer c on c.customer_id=x.customer_id
where x.streamer_id<>c.streamer_id
union all
select 'follow_orphan_customer', count(*)
from live_follow_record x left join live_customer c on c.customer_id=x.customer_id
where c.customer_id is null
union all
select 'follow_customer_streamer_mismatch', count(*)
from live_follow_record x join live_customer c on c.customer_id=x.customer_id
where x.streamer_id<>c.streamer_id
union all
select 'alias_orphan_customer', count(*)
from live_customer_alias a left join live_customer c on c.customer_id=a.customer_id
where c.customer_id is null
union all
select 'alias_customer_streamer_mismatch', count(*)
from live_customer_alias a join live_customer c on c.customer_id=a.customer_id
where a.streamer_id is null or a.streamer_id<>c.streamer_id
union all
select 'duplicate_active_customer_scope', count(*)
from (
  select nickname,streamer_id
  from live_customer
  where merged_into_id is null
  group by nickname,streamer_id
  having count(*)>1
) duplicate_scope;

select
  count(case when role_key='streamer' then 1 end)=0 as missing_streamer_role,
  count(case when role_key='operator' then 1 end)=0 as missing_operator_role,
  count(case when role_key='live_admin' then 1 end)=0 as missing_live_admin_role,
  count(case when role_key='jewelry_maker' then 1 end)=0 as missing_jewelry_maker_role,
  count(case when role_key='jewelry_reviewer' then 1 end)=0 as missing_jewelry_reviewer_role,
  count(case when role_key='jewelry_admin' then 1 end)=0 as missing_jewelry_admin_role
from sys_role
where del_flag='0' and role_key in
  ('streamer','operator','live_admin','jewelry_maker','jewelry_reviewer','jewelry_admin');

select
  count(case when r.role_key='jewelry_maker' and m.perms='jewelry:product:add' then 1 end)=0
    as missing_jewelry_maker_product_add_permission,
  count(case when r.role_key='jewelry_maker' and m.perms='jewelry:product:edit' then 1 end)>0
    as jewelry_maker_product_edit_mismatch,
  count(case when r.role_key='jewelry_reviewer'
    and m.perms in ('jewelry:product:add','jewelry:product:edit') then 1 end)>0
    as jewelry_reviewer_product_write_mismatch,
  count(case when r.role_key='jewelry_admin' and m.perms='jewelry:product:add' then 1 end)=0
    as missing_jewelry_admin_product_add_permission,
  count(case when r.role_key='jewelry_admin' and m.perms='jewelry:product:edit' then 1 end)=0
    as missing_jewelry_admin_product_edit_permission
from sys_role r
left join sys_role_menu rm on rm.role_id=r.role_id
left join sys_menu m on m.menu_id=rm.menu_id
where r.del_flag='0'
  and r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin');

select count(*) as missing_live_menu_translation
from sys_menu
where menu_id between 2000 and 2048
  and (menu_name_vi is null or trim(menu_name_vi)='');
