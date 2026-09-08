-- 项目任务改为可恢复停用，并为一次性任务、持续工作保存每一段实际启用区间。

set @task_active_status_sql=(select if(count(*)=0,
  'alter table biz_project_task add column active_status varchar(16) not null default ''ACTIVE'' comment ''ACTIVE/VOID'' after actual_finish_time','select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_task' and column_name='active_status');
prepare task_active_status_stmt from @task_active_status_sql;
execute task_active_status_stmt;
deallocate prepare task_active_status_stmt;

create table if not exists biz_project_work_period (
  period_id bigint not null auto_increment comment '执行区间ID',
  project_id bigint not null comment '项目ID',
  work_type varchar(16) not null comment 'TASK/ROUTINE',
  work_id bigint not null comment '任务或持续工作ID',
  assignee_user_id bigint null comment '本段执行人ID快照',
  assignee_name varchar(64) null comment '本段执行人姓名快照',
  start_date date not null comment '本段启用日期',
  end_date date null comment '本段停用日期，空表示当前启用',
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/CLOSED',
  version int not null default 0,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (period_id),
  key idx_work_period_project (project_id,work_type,work_id,start_date),
  key idx_work_period_assignee (assignee_user_id,start_date,end_date),
  key idx_work_period_active (work_type,work_id,status)
) engine=InnoDB default charset=utf8mb4 comment='项目工作实际启用区间';

insert into biz_project_work_period(project_id,work_type,work_id,assignee_user_id,assignee_name,
  start_date,end_date,status,version,create_by,create_time,update_by,update_time)
select task.project_id,'TASK',task.task_id,task.assignee_user_id,task.assignee_name,
  coalesce(task.plan_start_date,date(task.create_time),current_date()),
  case when project.status in('CLOSED','CANCELED') then greatest(
    coalesce(task.plan_start_date,date(task.create_time),current_date()),
    coalesce(project.actual_end_date,date(project.update_time),current_date())) else null end,
  case when project.status in('CLOSED','CANCELED') then 'CLOSED' else 'ACTIVE' end,0,
  'migration-v071',sysdate(),null,null
from biz_project_task task
join biz_project project on project.project_id=task.project_id
where task.active_status='ACTIVE' and task.assignee_user_id is not null
  and not exists(select 1 from biz_project_work_period period_row
    where period_row.work_type='TASK' and period_row.work_id=task.task_id);

insert into biz_project_work_period(project_id,work_type,work_id,assignee_user_id,assignee_name,
  start_date,end_date,status,version,create_by,create_time,update_by,update_time)
select routine.project_id,'ROUTINE',routine.routine_id,routine.assignee_user_id,routine.assignee_name,
  routine.start_date,
  case when routine.status='VOID' then greatest(routine.start_date,
    case when project.status in('CLOSED','CANCELED') then coalesce(project.actual_end_date,date(project.update_time),current_date())
      else date(coalesce(routine.update_time,routine.create_time)) end) else null end,
  case when routine.status='VOID' then 'CLOSED' else 'ACTIVE' end,0,
  'migration-v071',sysdate(),null,null
from biz_project_routine routine
join biz_project project on project.project_id=routine.project_id
where routine.assignee_user_id is not null
  and not exists(select 1 from biz_project_work_period period_row
    where period_row.work_type='ROUTINE' and period_row.work_id=routine.routine_id);
