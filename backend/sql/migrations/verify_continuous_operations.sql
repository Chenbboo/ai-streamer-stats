-- V069–V073 read-only checks; every problem_rows value must be zero.
select 'missing_continuous_work_tables' check_name,2-count(*) problem_rows
from information_schema.tables where table_schema=database()
  and table_name in ('biz_project_work_period','biz_project_routine_daily_target');

select 'duplicate_current_daily_target' check_name,count(*) problem_rows
from (select routine_id,biz_date from biz_project_routine_daily_target
  where status='CURRENT' group by routine_id,biz_date having count(*)>1) duplicated
union all select 'duplicate_active_work_period',count(*)
from (select work_type,work_id from biz_project_work_period where status='ACTIVE'
  group by work_type,work_id having count(*)>1) duplicated
union all select 'invalid_work_period_dates',count(*) from biz_project_work_period
where end_date<start_date or (status='CLOSED' and end_date is null)
union all select 'invalid_daily_budget_policy',count(*) from biz_project
where budget_mode not in ('TOTAL','DAILY','NONE') or budget_scope not in ('FULL_COST','CASH_EXPENSE')
  or (budget_mode='DAILY' and (daily_budget_limit is null or daily_budget_limit<=0))
union all select 'invalid_routine_target_mode',count(*) from biz_project_routine
where target_mode not in ('FIXED','AUTO_TOTAL','DAILY_DYNAMIC','NONE')
union all select 'daily_target_project_mismatch',count(*) from biz_project_routine_daily_target t
left join biz_project_routine r on r.routine_id=t.routine_id
where r.routine_id is null or t.project_id<>r.project_id;
