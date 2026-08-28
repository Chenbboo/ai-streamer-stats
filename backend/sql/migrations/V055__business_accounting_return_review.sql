-- Bosses can return a draft operating fact to its submitter for correction without deleting its audit history.

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_operating_fact' and column_name='returned_user_id')=0,
  'alter table biz_operating_fact add column returned_user_id bigint default null after confirmed_time',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_operating_fact' and column_name='returned_user_name')=0,
  'alter table biz_operating_fact add column returned_user_name varchar(100) default null after returned_user_id',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_operating_fact' and column_name='returned_time')=0,
  'alter table biz_operating_fact add column returned_time datetime default null after returned_user_name',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_operating_fact' and column_name='return_reason')=0,
  'alter table biz_operating_fact add column return_reason varchar(500) default null after returned_time',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
