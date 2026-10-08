-- 预算控制周期与经营测算周期分离：总额预算继续使用 budget_limit，每日预算单独存储。

set @proposal_budget_forecast_missing=(select count(*)=0 from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal' and column_name='budget_mode');
set @proposal_budget_forecast_sql=if(@proposal_budget_forecast_missing,
  "alter table biz_project_proposal
    add column budget_mode varchar(16) not null default 'TOTAL' comment 'TOTAL/DAILY/NONE' after no_budget,
    add column daily_budget_limit decimal(20,2) null comment '每日预算上限' after budget_mode,
    add column budget_scope varchar(24) not null default 'FULL_COST' comment 'FULL_COST/CASH_EXPENSE' after daily_budget_limit,
    add column startup_budget_limit decimal(20,2) null comment '一次性启动预算上限' after budget_scope,
    add column budget_reason varchar(500) null comment '暂不设预算或特殊预算说明' after startup_budget_limit,
    add column forecast_period varchar(16) not null default 'PROJECT' comment 'PROJECT/MONTH' after budget_reason,
    add column forecast_days int not null default 30 comment '测算周期天数' after forecast_period,
    add column recurring_estimated_revenue decimal(20,2) not null default 0 comment '稳定期预计收入' after estimated_revenue,
    add column recurring_estimated_external_cost decimal(20,2) not null default 0 comment '稳定期外部支出' after estimated_external_cost,
    add column recurring_estimated_total_cost decimal(20,2) null comment '稳定期预计总成本' after estimated_total_cost,
    add column recurring_expected_profit decimal(20,2) null comment '稳定期预计利润' after expected_profit",
  'select 1');
prepare proposal_budget_forecast_stmt from @proposal_budget_forecast_sql;
execute proposal_budget_forecast_stmt;
deallocate prepare proposal_budget_forecast_stmt;

update biz_project_proposal
set budget_mode=case when no_budget='1' then 'NONE' else 'TOTAL' end,
    forecast_period=case when plan_end_date is null then 'MONTH' else 'PROJECT' end,
    forecast_days=case when plan_end_date is null then 30 else greatest(1,datediff(plan_end_date,plan_start_date)+1) end
where @proposal_budget_forecast_missing=1;

set @proposal_revenue_occurrence_sql=(select if(count(*)=0,
  "alter table biz_project_proposal_revenue add column occurrence_type varchar(16) not null default 'ONE_TIME' comment 'ONE_TIME/DAILY/WEEKLY/MONTHLY' after expected_amount",
  'select 1') from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal_revenue' and column_name='occurrence_type');
prepare proposal_revenue_occurrence_stmt from @proposal_revenue_occurrence_sql;
execute proposal_revenue_occurrence_stmt;
deallocate prepare proposal_revenue_occurrence_stmt;

set @proposal_expense_occurrence_missing=(select count(*)=0 from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal_expense' and column_name='occurrence_type');
set @proposal_expense_occurrence_sql=if(@proposal_expense_occurrence_missing,
  "alter table biz_project_proposal_expense add column occurrence_type varchar(16) not null default 'ONE_TIME' comment 'ONE_TIME/DAILY/WEEKLY/MONTHLY' after amount",
  'select 1');
prepare proposal_expense_occurrence_stmt from @proposal_expense_occurrence_sql;
execute proposal_expense_occurrence_stmt;
deallocate prepare proposal_expense_occurrence_stmt;

update biz_project_proposal_expense
set occurrence_type=case when expense_type='RECURRING' then 'MONTHLY' else 'ONE_TIME' end
where @proposal_expense_occurrence_missing=1;

set @project_budget_policy_missing=(select count(*)=0 from information_schema.columns
  where table_schema=database() and table_name='biz_project' and column_name='budget_mode');
set @project_budget_policy_sql=if(@project_budget_policy_missing,
  "alter table biz_project
    add column budget_mode varchar(16) not null default 'TOTAL' comment 'TOTAL/DAILY/NONE' after budget_limit,
    add column daily_budget_limit decimal(20,2) null comment '每日预算上限' after budget_mode,
    add column budget_scope varchar(24) not null default 'FULL_COST' comment 'FULL_COST/CASH_EXPENSE' after daily_budget_limit,
    add column startup_budget_limit decimal(20,2) null comment '一次性启动预算上限' after budget_scope,
    add column budget_reason varchar(500) null comment '预算政策说明' after startup_budget_limit",
  'select 1');
prepare project_budget_policy_stmt from @project_budget_policy_sql;
execute project_budget_policy_stmt;
deallocate prepare project_budget_policy_stmt;

update biz_project set budget_mode=case when budget_limit is null then 'NONE' else 'TOTAL' end
where @project_budget_policy_missing=1;

set @budget_history_mode_sql=(select if(count(*)=0,
  "alter table biz_project_budget_history add column budget_mode varchar(16) null comment '调整后预算方式' after currency",
  'select 1') from information_schema.columns
  where table_schema=database() and table_name='biz_project_budget_history' and column_name='budget_mode');
prepare budget_history_mode_stmt from @budget_history_mode_sql;
execute budget_history_mode_stmt;
deallocate prepare budget_history_mode_stmt;
