-- Preserve active recurring work when its assignee leaves a project. The work remains visible and
-- can be reassigned, while all previously submitted routine reports stay attached to it.

alter table biz_project_routine
  modify column assignee_user_id bigint default null comment '当前负责人用户ID，空表示待重新分配',
  modify column assignee_name varchar(64) default null comment '当前负责人姓名快照，空表示待重新分配';

-- V051 retired orphaned routines because the assignee columns were not nullable. Restore only rows
-- changed by that migration; routines deliberately retired by users remain VOID.
update biz_project_routine routine
set routine.status='ACTIVE',routine.assignee_user_id=null,routine.assignee_name=null,
    routine.update_by='migration-v053',routine.update_time=sysdate(),routine.version=routine.version+1
where routine.status='VOID' and routine.update_by='migration-v051';

-- Restore routines hidden by the former member-removal implementation. Match the audit event and
-- its timestamp so manually retired routines are not revived accidentally.
update biz_project_routine routine
set routine.status='ACTIVE',routine.assignee_user_id=null,routine.assignee_name=null,
    routine.update_by='migration-v053',routine.update_time=sysdate(),routine.version=routine.version+1
where routine.status='VOID' and routine.assignee_user_id is not null
  and exists(
    select 1
    from biz_project_event event
    where event.project_id=routine.project_id and event.event_type='MEMBER_REMOVE'
      and event.event_comment like concat('移除账号ID ',routine.assignee_user_id,'；%停用持续工作%')
      and abs(timestampdiff(second,event.create_time,routine.update_time))<=2
  );
