-- 项目管理费资格：同一归属老板范围内，负责人至少同时负责 3 个在管项目。
-- 设置规则时保存资格快照，后续其他项目结项不会撤销已设置的管理费。
set @sql=(select if(count(*)=0,
  'alter table biz_project_management_fee add column eligibility_project_count int null after configured_time',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_management_fee' and column_name='eligibility_project_count');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql=(select if(count(*)=0,
  'alter table biz_project_management_fee add column eligibility_project_ids varchar(2000) null after eligibility_project_count',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_management_fee' and column_name='eligibility_project_ids');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql=(select if(count(*)=0,
  'alter table biz_project_management_fee add column eligibility_project_names varchar(4000) null after eligibility_project_ids',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_management_fee' and column_name='eligibility_project_names');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql=(select if(count(*)=0,
  'alter table biz_project_management_fee add column eligibility_checked_time datetime null after eligibility_project_names',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_management_fee' and column_name='eligibility_checked_time');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
