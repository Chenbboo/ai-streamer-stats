-- Company operations hardening: preserve leave approval audit, bind generated leave rows to their request,
-- normalize collations/currency, and deactivate execution data that belongs to departed project members.

alter table biz_staff_leave
  convert to character set utf8mb4 collate utf8mb4_0900_ai_ci,
  add column source_request_id bigint default null comment 'approved leave request id' after recorded_project_id,
  add key idx_staff_leave_source_request (source_request_id);

alter table biz_staff_leave_request
  add column cancel_reviewed_user_id bigint default null after cancel_reason,
  add column cancel_reviewed_user_name varchar(100) default null after cancel_reviewed_user_id,
  add column cancel_reviewed_time datetime default null after cancel_reviewed_user_name,
  add column cancel_review_comment varchar(1000) default null after cancel_reviewed_time;

-- Older code wrote cancellation approval into the original review columns. Preserve that legacy evidence
-- in the dedicated cancellation review fields; the original approval cannot be reconstructed if it was overwritten.
update biz_staff_leave_request
set cancel_reviewed_user_id=reviewed_user_id,cancel_reviewed_user_name=reviewed_user_name,
    cancel_reviewed_time=reviewed_time,cancel_review_comment=review_comment
where status='CANCELED' and canceled_time is not null and cancel_reviewed_time is null
  and reviewed_time is not null and reviewed_time>=canceled_time;

update biz_staff_leave leave_record
set source_request_id=(
  select max(request_record.request_id)
  from biz_staff_leave_request request_record
  where request_record.user_id=leave_record.user_id
    and leave_record.leave_date between request_record.leave_start_date and request_record.leave_end_date
    and request_record.status in ('APPROVED','CANCEL_PENDING','CANCELED')
)
where leave_record.source_request_id is null;

update biz_project set base_currency='CNY'
where upper(trim(base_currency)) in ('人民币','RMB');

update biz_project_task task
left join biz_project_member member on member.project_id=task.project_id
  and member.user_id=task.assignee_user_id and member.status='0'
set task.assignee_user_id=null,task.assignee_name=null,task.update_by='migration-v049',task.update_time=sysdate(),task.version=task.version+1
where task.assignee_user_id is not null and task.status<>'DONE' and member.member_id is null;

update biz_project_routine routine
left join biz_project_member member on member.project_id=routine.project_id
  and member.user_id=routine.assignee_user_id and member.status='0'
set routine.status='VOID',routine.update_by='migration-v049',routine.update_time=sysdate(),routine.version=routine.version+1
where routine.status='ACTIVE' and member.member_id is null;

update biz_project_staff_allocation allocation
left join biz_project_member member on member.project_id=allocation.project_id
  and member.user_id=allocation.user_id and member.status='0'
set allocation.status=case when allocation.effective_from>curdate() then 'VOID' else allocation.status end,
    allocation.effective_to=case
      when allocation.effective_from>curdate() then allocation.effective_to
      when allocation.effective_to is null or allocation.effective_to>=curdate() then date_sub(curdate(),interval 1 day)
      else allocation.effective_to end,
    allocation.update_by='migration-v049',allocation.update_time=sysdate(),allocation.version=allocation.version+1
where allocation.status='ACTIVE' and member.member_id is null
  and (allocation.effective_from>curdate() or allocation.effective_to is null or allocation.effective_to>=curdate());
