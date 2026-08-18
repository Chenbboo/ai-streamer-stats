-- 持续工作计划、周期完成填报和项目管理模式。
-- 不转换或删除现有任务；历史项目默认采用精简模式。

set @management_mode_column_sql=(select if(count(*)=0,
  'alter table biz_project add column management_mode varchar(16) not null default ''SIMPLE'' comment ''SIMPLE/STANDARD/DELIVERY'' after accounting_mode','select 1')
  from information_schema.columns where table_schema=database() and table_name='biz_project' and column_name='management_mode');
prepare management_mode_column_stmt from @management_mode_column_sql;
execute management_mode_column_stmt;
deallocate prepare management_mode_column_stmt;

create table if not exists biz_project_routine (
  routine_id bigint not null auto_increment,
  project_id bigint not null,
  routine_name varchar(200) not null,
  frequency varchar(16) not null default 'DAILY' comment 'DAILY/WEEKLY/MONTHLY',
  target_value decimal(20,4) not null,
  unit varchar(32) not null,
  assignee_user_id bigint not null,
  assignee_name varchar(64) not null,
  start_date date not null,
  end_date date default null,
  evidence_required char(1) not null default '0',
  status varchar(16) not null default 'ACTIVE',
  version int not null default 0,
  create_by varchar(64) default '',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key(routine_id),
  key idx_routine_project(project_id,status,start_date,end_date),
  key idx_routine_assignee(assignee_user_id,status,start_date,end_date)
) engine=InnoDB default charset=utf8mb4 comment='项目持续工作计划';

create table if not exists biz_project_routine_report (
  report_id bigint not null auto_increment,
  routine_id bigint not null,
  project_id bigint not null,
  biz_date date not null,
  target_snapshot decimal(20,4) not null,
  actual_value decimal(20,4) not null,
  unit varchar(32) not null,
  summary varchar(1000) default null,
  issue_reason varchar(1000) default null,
  evidence_urls varchar(4000) default null,
  submitted_user_id bigint not null,
  submitted_user_name varchar(64) not null,
  status varchar(16) not null default 'SUBMITTED',
  version int not null default 0,
  create_by varchar(64) default '',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key(report_id),
  unique key uk_routine_report_date(routine_id,biz_date),
  key idx_routine_report_project(project_id,biz_date,status)
) engine=InnoDB default charset=utf8mb4 comment='持续工作周期完成填报';
