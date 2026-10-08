-- 人员成本版本支持受控删除和可审计作废。

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_staff_cost_policy' and column_name='voided_user_id')=0,
  "alter table biz_staff_cost_policy add column voided_user_id bigint null comment '作废人ID' after status",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_staff_cost_policy' and column_name='voided_user_name')=0,
  "alter table biz_staff_cost_policy add column voided_user_name varchar(100) null comment '作废人名称' after voided_user_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_staff_cost_policy' and column_name='voided_time')=0,
  "alter table biz_staff_cost_policy add column voided_time datetime null comment '作废时间' after voided_user_name",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_staff_cost_policy' and column_name='void_reason')=0,
  "alter table biz_staff_cost_policy add column void_reason varchar(500) null comment '作废原因' after voided_time",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

alter table biz_staff_cost_policy modify column status varchar(16) not null default 'ACTIVE'
  comment 'ACTIVE/VOID';
