-- 项目动态保存被安排人的审计快照，确保能明确展示“谁给谁安排了什么”。

set @event_subject_user_sql=(select if(count(*)=0,
  'alter table biz_project_event add column subject_user_id bigint null comment ''被操作或被安排人员ID'' after operator_name',
  'select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_event' and column_name='subject_user_id');
prepare event_subject_user_stmt from @event_subject_user_sql;
execute event_subject_user_stmt;
deallocate prepare event_subject_user_stmt;

set @event_subject_name_sql=(select if(count(*)=0,
  'alter table biz_project_event add column subject_name varchar(64) null comment ''被操作或被安排人员姓名快照'' after subject_user_id',
  'select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_event' and column_name='subject_name');
prepare event_subject_name_stmt from @event_subject_name_sql;
execute event_subject_name_stmt;
deallocate prepare event_subject_name_stmt;

set @event_subject_account_sql=(select if(count(*)=0,
  'alter table biz_project_event add column subject_account varchar(64) null comment ''被操作或被安排人员账号快照'' after subject_name',
  'select 1')
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_event' and column_name='subject_account');
prepare event_subject_account_stmt from @event_subject_account_sql;
execute event_subject_account_stmt;
deallocate prepare event_subject_account_stmt;

-- 旧任务动态仅在同项目、同任务名称能唯一确定执行人时回填，避免产生错误审计信息。
update biz_project_event event_record
join (
  select task.project_id,task.task_name,min(task.assignee_user_id) subject_user_id,
    max(task.assignee_name) subject_name
  from biz_project_task task
  where task.assignee_user_id is not null
  group by task.project_id,task.task_name
  having count(distinct task.assignee_user_id)=1
) task_subject on event_record.event_type='TASK_SAVE'
  and task_subject.project_id=event_record.project_id
  and task_subject.task_name=event_record.event_comment
left join sys_user subject_user on subject_user.user_id=task_subject.subject_user_id
set event_record.subject_user_id=task_subject.subject_user_id,
  event_record.subject_name=coalesce(nullif(trim(task_subject.subject_name),''),
    nullif(trim(subject_user.nick_name),''),nullif(trim(subject_user.user_name),'')),
  event_record.subject_account=nullif(trim(subject_user.user_name),'')
where event_record.subject_user_id is null;

-- 旧持续工作动态用日志中的工作名称匹配；仅唯一执行人时回填。
update biz_project_event event_record
join (
  select routine.project_id,routine.routine_name,min(routine.assignee_user_id) subject_user_id,
    max(routine.assignee_name) subject_name
  from biz_project_routine routine
  where routine.assignee_user_id is not null
  group by routine.project_id,routine.routine_name
  having count(distinct routine.assignee_user_id)=1
) routine_subject on event_record.event_type='ROUTINE_SAVE'
  and routine_subject.project_id=event_record.project_id
  and routine_subject.routine_name=substring_index(event_record.event_comment,' / ',1)
left join sys_user subject_user on subject_user.user_id=routine_subject.subject_user_id
set event_record.subject_user_id=routine_subject.subject_user_id,
  event_record.subject_name=coalesce(nullif(trim(routine_subject.subject_name),''),
    nullif(trim(subject_user.nick_name),''),nullif(trim(subject_user.user_name),'')),
  event_record.subject_account=nullif(trim(subject_user.user_name),'')
where event_record.subject_user_id is null;
