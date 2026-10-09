set @has_column := (select count(*) from information_schema.columns
where table_schema=database() and table_name='biz_project' and column_name='kpi_setup_ignored_user_id');
set @sql := if(@has_column=0,'alter table biz_project add column kpi_setup_ignored_user_id bigint default null','select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
