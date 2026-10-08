-- Preserve FOLLOW_PROJECT / CUSTOM / UNLIMITED across draft save and launch.
set @proposal_mode_sql=(select if(count(*)=0,
  'alter table biz_project_proposal_staffing add column participation_mode varchar(20) null comment ''人员参与方式'' after role_name',
  'select 1') from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal_staffing' and column_name='participation_mode');
prepare proposal_mode_stmt from @proposal_mode_sql;
execute proposal_mode_stmt;
deallocate prepare proposal_mode_stmt;

-- Match the historical UI inference. Existing explicit choices are never overwritten.
update biz_project_proposal_staffing s
join biz_project_proposal p on p.proposal_id=s.proposal_id
set s.participation_mode=case
  when s.plan_start_date is null and s.plan_end_date is null then 'FOLLOW_PROJECT'
  when s.plan_start_date=p.plan_start_date and s.plan_end_date <=> p.plan_end_date then 'FOLLOW_PROJECT'
  when s.plan_start_date is not null and s.plan_end_date is null then 'UNLIMITED'
  else 'CUSTOM' end
where s.participation_mode is null or trim(s.participation_mode)='';
