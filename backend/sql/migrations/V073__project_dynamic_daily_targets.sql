set @target_schema_sql=(select if(count(*)=0, 'alter table biz_project add column goal_mode varchar(16) not null default ''TOTAL'' comment ''目标模式：TOTAL总目标/NO_TOTAL不计入总目标'' after acceptance_criteria', 'select 1') from information_schema.columns where table_schema=database() and table_name='biz_project' and column_name='goal_mode');
prepare target_schema_stmt from @target_schema_sql;
execute target_schema_stmt;
deallocate prepare target_schema_stmt;

set @target_schema_sql=(select if(count(*)=0, 'alter table biz_project_proposal add column goal_mode varchar(16) not null default ''TOTAL'' comment ''目标模式：TOTAL总目标/NO_TOTAL不计入总目标'' after acceptance_criteria', 'select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal' and column_name='goal_mode');
prepare target_schema_stmt from @target_schema_sql;
execute target_schema_stmt;
deallocate prepare target_schema_stmt;

set @target_schema_sql=(select if(count(*)=0, 'alter table biz_project_routine add column target_mode varchar(24) not null default ''FIXED'' comment ''目标模式：FIXED/AUTO_TOTAL/DAILY_DYNAMIC/NONE'' after frequency', 'select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_routine' and column_name='target_mode');
prepare target_schema_stmt from @target_schema_sql;
execute target_schema_stmt;
deallocate prepare target_schema_stmt;

create table if not exists biz_project_routine_daily_target (
  daily_target_id bigint not null auto_increment comment '每日目标版本ID',
  project_id bigint not null comment '项目ID',
  routine_id bigint not null comment '持续工作ID',
  biz_date date not null comment '目标日期',
  target_value decimal(20,4) not null comment '当日目标值',
  unit varchar(32) default null comment '单位快照',
  customer_requirement varchar(1000) default null comment '客户当日要求',
  change_reason varchar(500) default null comment '修改原因',
  assignee_user_id bigint default null comment '执行人ID快照',
  assignee_name varchar(100) default null comment '执行人姓名快照',
  target_version int not null default 1 comment '日期内版本号',
  status varchar(16) not null default 'CURRENT' comment 'CURRENT/SUPERSEDED',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  remark varchar(500) default null comment '备注',
  primary key (daily_target_id),
  unique key uk_routine_date_version (routine_id,biz_date,target_version),
  key idx_project_date_status (project_id,biz_date,status),
  key idx_routine_date_status (routine_id,biz_date,status)
) engine=InnoDB default charset=utf8mb4 comment='项目持续工作每日目标版本';
