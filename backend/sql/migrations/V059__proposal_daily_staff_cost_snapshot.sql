-- 立项人员成本增加标准工作天数和日用人成本快照。
-- 有结束日期按自然日天数乘日成本；不限期按一个月的月度成本。

set @proposal_staff_work_days_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='standard_work_days_snapshot')=0,
  "alter table biz_project_proposal_staffing add column standard_work_days_snapshot decimal(10,2) null comment '月度标准工作天数快照' after monthly_cost_snapshot",
  'select 1');
prepare proposal_staff_work_days_stmt from @proposal_staff_work_days_sql;
execute proposal_staff_work_days_stmt;
deallocate prepare proposal_staff_work_days_stmt;

set @proposal_staff_daily_cost_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='daily_cost_snapshot')=0,
  "alter table biz_project_proposal_staffing add column daily_cost_snapshot decimal(20,4) null comment '日用人成本快照' after standard_work_days_snapshot",
  'select 1');
prepare proposal_staff_daily_cost_stmt from @proposal_staff_daily_cost_sql;
execute proposal_staff_daily_cost_stmt;
deallocate prepare proposal_staff_daily_cost_stmt;
