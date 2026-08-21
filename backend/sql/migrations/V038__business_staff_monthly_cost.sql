-- 人员内部成本统一为人民币月度用人成本；按国家保存标准工作日快照，保证历史核算可追溯。

set @staff_cost_country_added = (select count(*)=0 from information_schema.columns where table_schema=database()
  and table_name='biz_staff_cost_policy' and column_name='country_region_snapshot');
set @sql = if(@staff_cost_country_added,
  "alter table biz_staff_cost_policy add column country_region_snapshot varchar(16) not null default 'CN' comment '成本版本国家地区快照' after currency",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @staff_cost_days_added = (select count(*)=0 from information_schema.columns where table_schema=database()
  and table_name='biz_staff_cost_policy' and column_name='standard_work_days');
set @sql = if(@staff_cost_days_added,
  "alter table biz_staff_cost_policy add column standard_work_days decimal(6,2) not null default 21.75 comment '月成本折算标准工作日快照' after country_region_snapshot",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 旧成本版本只补充折算快照，不改变原核算方式和原金额。
set @sql = if(@staff_cost_country_added or @staff_cost_days_added,
  "update biz_staff_cost_policy c left join biz_staff_profile p on p.user_id=c.user_id set c.country_region_snapshot=coalesce(nullif(p.country_region,''),'CN'), c.standard_work_days=case when p.country_region='VN' then 26 else 21.75 end",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
