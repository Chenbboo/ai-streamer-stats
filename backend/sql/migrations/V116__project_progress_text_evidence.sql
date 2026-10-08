-- Progress evidence can be entered as text while preserving existing file evidence.
set @sql=(select if(count(*)=0,
  'alter table biz_project_progress_report add column evidence_text varchar(2000) null comment ''文字成果凭证'' after evidence_urls',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_progress_report' and column_name='evidence_text');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
