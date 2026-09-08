-- 为启用、停用任务的项目动态补齐执行人审计快照。
-- 同时兼容旧日志以工作 ID 记录、新日志以工作名称记录的两种格式。

update biz_project_event event_record
join (
  select event_source.event_id,min(task.assignee_user_id) subject_user_id,
    max(task.assignee_name) subject_name
  from biz_project_event event_source
  join biz_project_task task on task.project_id=event_source.project_id
    and (
      event_source.event_comment=concat(
        case event_source.event_type
          when 'TASK_VOID' then '停用一次性任务 '
          else '重新启用一次性任务 '
        end,task.task_id)
      or event_source.event_comment=concat(
        case event_source.event_type
          when 'TASK_VOID' then '停用一次性任务 '
          else '重新启用一次性任务 '
        end,task.task_name)
    )
  where event_source.event_type in ('TASK_VOID','TASK_ENABLE')
    and event_source.subject_user_id is null
    and task.assignee_user_id is not null
  group by event_source.event_id
  having count(distinct task.assignee_user_id)=1
) task_subject on task_subject.event_id=event_record.event_id
left join sys_user subject_user on subject_user.user_id=task_subject.subject_user_id
set event_record.subject_user_id=task_subject.subject_user_id,
  event_record.subject_name=coalesce(nullif(trim(task_subject.subject_name),''),
    nullif(trim(subject_user.nick_name),''),nullif(trim(subject_user.user_name),'')),
  event_record.subject_account=nullif(trim(subject_user.user_name),'')
where event_record.subject_user_id is null;

update biz_project_event event_record
join (
  select event_source.event_id,min(routine.assignee_user_id) subject_user_id,
    max(routine.assignee_name) subject_name
  from biz_project_event event_source
  join biz_project_routine routine on routine.project_id=event_source.project_id
    and (
      event_source.event_comment=concat(
        case event_source.event_type
          when 'ROUTINE_VOID' then '停用持续工作 '
          else '重新启用持续工作 '
        end,routine.routine_id)
      or event_source.event_comment=concat(
        case event_source.event_type
          when 'ROUTINE_VOID' then '停用持续工作 '
          else '重新启用持续工作 '
        end,routine.routine_name)
    )
  where event_source.event_type in ('ROUTINE_VOID','ROUTINE_ENABLE')
    and event_source.subject_user_id is null
    and routine.assignee_user_id is not null
  group by event_source.event_id
  having count(distinct routine.assignee_user_id)=1
) routine_subject on routine_subject.event_id=event_record.event_id
left join sys_user subject_user on subject_user.user_id=routine_subject.subject_user_id
set event_record.subject_user_id=routine_subject.subject_user_id,
  event_record.subject_name=coalesce(nullif(trim(routine_subject.subject_name),''),
    nullif(trim(subject_user.nick_name),''),nullif(trim(subject_user.user_name),'')),
  event_record.subject_account=nullif(trim(subject_user.user_name),'')
where event_record.subject_user_id is null;
