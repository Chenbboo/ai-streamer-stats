-- 内部项目支出指定接收项目，自动生成同日同币种收入。历史支出保留，不追溯生成收入。
set @sql=(select if(count(*)=0,
  'alter table biz_operating_fact add column target_project_id bigint null comment ''内部项目支出的指定项目''',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_operating_fact' and column_name='target_project_id');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

insert into biz_fact_category
  (category_code,category_name,fact_kind,default_sign,unit_type,status,sort_order,remark)
values
  ('INTERNAL_PROJECT_REVENUE','内部项目收入','REVENUE',1,'MONEY','0',75,'由内部项目支出自动生成，仅可随来源支出修改或冲销')
on duplicate key update
  category_name=values(category_name),fact_kind=values(fact_kind),default_sign=values(default_sign),
  unit_type=values(unit_type),status='0',sort_order=values(sort_order),remark=values(remark);
