-- 立项人员投入改为直接选择人员，并保存人员管理中当期月度内部成本快照。
-- 原岗位、人数、投入比例和人月字段保留，用于兼容历史立项记录。

set @proposal_staff_user_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='user_id')=0,
  "alter table biz_project_proposal_staffing add column user_id bigint null comment '选择的系统用户' after proposal_id",
  'select 1');
prepare proposal_staff_user_stmt from @proposal_staff_user_sql;
execute proposal_staff_user_stmt;
deallocate prepare proposal_staff_user_stmt;

set @proposal_staff_name_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='user_name')=0,
  "alter table biz_project_proposal_staffing add column user_name varchar(100) null comment '人员名称快照' after user_id",
  'select 1');
prepare proposal_staff_name_stmt from @proposal_staff_name_sql;
execute proposal_staff_name_stmt;
deallocate prepare proposal_staff_name_stmt;

set @proposal_staff_policy_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='cost_policy_id')=0,
  "alter table biz_project_proposal_staffing add column cost_policy_id bigint null comment '人员成本政策快照ID' after person_months",
  'select 1');
prepare proposal_staff_policy_stmt from @proposal_staff_policy_sql;
execute proposal_staff_policy_stmt;
deallocate prepare proposal_staff_policy_stmt;

set @proposal_staff_policy_version_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='cost_policy_version')=0,
  "alter table biz_project_proposal_staffing add column cost_policy_version int null comment '人员成本政策版本' after cost_policy_id",
  'select 1');
prepare proposal_staff_policy_version_stmt from @proposal_staff_policy_version_sql;
execute proposal_staff_policy_version_stmt;
deallocate prepare proposal_staff_policy_version_stmt;

set @proposal_staff_monthly_cost_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='monthly_cost_snapshot')=0,
  "alter table biz_project_proposal_staffing add column monthly_cost_snapshot decimal(20,2) null comment '月度内部成本快照' after cost_policy_version",
  'select 1');
prepare proposal_staff_monthly_cost_stmt from @proposal_staff_monthly_cost_sql;
execute proposal_staff_monthly_cost_stmt;
deallocate prepare proposal_staff_monthly_cost_stmt;

set @proposal_staff_currency_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='cost_currency')=0,
  "alter table biz_project_proposal_staffing add column cost_currency varchar(3) null comment '成本币种' after monthly_cost_snapshot",
  'select 1');
prepare proposal_staff_currency_stmt from @proposal_staff_currency_sql;
execute proposal_staff_currency_stmt;
deallocate prepare proposal_staff_currency_stmt;

set @proposal_staff_index_sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
    and table_name='biz_project_proposal_staffing' and index_name='idx_biz_proposal_staff_user')=0,
  'alter table biz_project_proposal_staffing add key idx_biz_proposal_staff_user (proposal_id,user_id)',
  'select 1');
prepare proposal_staff_index_stmt from @proposal_staff_index_sql;
execute proposal_staff_index_stmt;
deallocate prepare proposal_staff_index_stmt;
