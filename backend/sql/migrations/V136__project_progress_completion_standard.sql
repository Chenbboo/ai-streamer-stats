-- Completion standard belongs to each immutable monthly report version.
set @has_completion_standard := (select count(*) from information_schema.columns
  where table_schema=database() and table_name='biz_project_progress_report'
    and column_name='completion_standard');
set @sql := if(@has_completion_standard=0,
  'alter table biz_project_progress_report add column completion_standard varchar(16) not null default ''STANDARD'' comment ''STANDARD标准完成0-100 EXCESS超额完成0-300'' after progress',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
