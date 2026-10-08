-- KPI 方案改为可审计作废：保留方案快照、奖金阶梯、结算草稿和结果草稿。

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_plan' and column_name='voided_user_id')=0,
  "alter table biz_project_kpi_plan add column voided_user_id bigint null comment '作废人ID' after closed_time",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_plan' and column_name='voided_user_name')=0,
  "alter table biz_project_kpi_plan add column voided_user_name varchar(100) null comment '作废人名称' after voided_user_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_plan' and column_name='voided_time')=0,
  "alter table biz_project_kpi_plan add column voided_time datetime null comment '作废时间' after voided_user_name",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_settlement' and column_name='voided_user_id')=0,
  "alter table biz_project_kpi_settlement add column voided_user_id bigint null comment '作废人ID' after accounting_fact_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_settlement' and column_name='voided_user_name')=0,
  "alter table biz_project_kpi_settlement add column voided_user_name varchar(100) null comment '作废人名称' after voided_user_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_settlement' and column_name='voided_time')=0,
  "alter table biz_project_kpi_settlement add column voided_time datetime null comment '作废时间' after voided_user_name",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

alter table biz_project_kpi_plan modify column status varchar(16) not null default 'PUBLISHED'
  comment 'PUBLISHED/CLOSED/VOIDED';
alter table biz_project_kpi_settlement modify column status varchar(16) not null default 'DRAFT'
  comment 'DRAFT/SUBMITTED/RETURNED/CONFIRMED/VOIDED';
