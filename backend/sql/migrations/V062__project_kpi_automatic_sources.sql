-- 项目KPI自动取数来源。
-- 当前指标与已发布方案分别保存来源引用，保证历史方案的统计口径不被后续调整覆盖。

set @kpi_source_ref_sql=(select if(count(*)=0,
  'alter table biz_project_kpi add column source_ref_id bigint null comment ''绑定的持续工作/任务/里程碑ID'' after source_type',
  'select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_kpi' and column_name='source_ref_id');
prepare kpi_source_ref_stmt from @kpi_source_ref_sql;
execute kpi_source_ref_stmt;
deallocate prepare kpi_source_ref_stmt;

set @plan_item_source_ref_sql=(select if(count(*)=0,
  'alter table biz_project_kpi_plan_item add column source_ref_id bigint null comment ''自动取数来源快照ID'' after source_type',
  'select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_kpi_plan_item' and column_name='source_ref_id');
prepare plan_item_source_ref_stmt from @plan_item_source_ref_sql;
execute plan_item_source_ref_stmt;
deallocate prepare plan_item_source_ref_stmt;
