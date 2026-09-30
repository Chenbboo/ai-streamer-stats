-- Preserve the allocation proposal inside its award; no historical awards or payments are changed.
set @ddl = if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_incentive_award' and column_name='allocation_proposal_json')=0,
  'alter table biz_incentive_award add column allocation_proposal_json mediumtext default null', 'select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
-- Rollback: retain the nullable column and application history.
