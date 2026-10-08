-- V096: A stable create key belongs to one applicant and survives soft deletion.
-- Nullable for existing drafts and older clients. Apply before deploying the new backend.
set @sql=(select if(count(*)=0,
  'alter table biz_project_proposal add column create_request_key varchar(64) character set ascii collate ascii_bin null',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='create_request_key');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
set @sql=(select if(count(*)=0,
  'alter table biz_project_proposal add unique key uk_proposal_create_request (applicant_user_id,create_request_key)',
  'select 1') from information_schema.statistics where table_schema=database()
    and table_name='biz_project_proposal' and index_name='uk_proposal_create_request');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
