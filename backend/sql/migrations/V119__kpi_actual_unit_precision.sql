-- Monetary KPI results can be recorded in ten-thousand-yuan units. Yuan cents require
-- six fractional places after conversion; preserve the full ledger precision.
set @kpi_actual_precision_sql=(select if(count(*)=1,
  'alter table biz_project_kpi_result modify column actual_value decimal(24,8) not null comment ''实际值（按指标单位）''',
  'select 1')
  from information_schema.columns where table_schema=database()
    and table_name='biz_project_kpi_result' and column_name='actual_value'
    and (numeric_precision<24 or numeric_scale<8));
prepare kpi_actual_precision_stmt from @kpi_actual_precision_sql;
execute kpi_actual_precision_stmt;
deallocate prepare kpi_actual_precision_stmt;
