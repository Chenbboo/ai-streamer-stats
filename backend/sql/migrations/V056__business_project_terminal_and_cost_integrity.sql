-- Project cost and terminal-state integrity repair.
-- This migration is intentionally deterministic: it repairs cumulative snapshots and terminal execution
-- records, but does not guess how to resolve historical leave/work conflicts that require a manager decision.

drop temporary table if exists tmp_v056_budget_spent;
create temporary table tmp_v056_budget_spent (
  result_id bigint not null primary key,
  budget_spent decimal(20,4) not null
) engine=InnoDB;

insert into tmp_v056_budget_spent(result_id,budget_spent)
select current_result.result_id,
  coalesce((select sum(fact.amount)
    from biz_operating_fact fact
    where fact.project_id=current_result.project_id
      and fact.biz_date<=current_result.biz_date
      and fact.fact_kind='COST' and fact.status in ('CONFIRMED','REVERSED')),0)
  + coalesce((select sum(snapshot.personnel_cost)
    from biz_project_daily_result snapshot
    where snapshot.project_id=current_result.project_id
      and snapshot.biz_date<=current_result.biz_date and snapshot.is_current='1'),0)
from biz_project_daily_result current_result
where current_result.is_current='1';

update biz_project_daily_result result_row
join tmp_v056_budget_spent repaired on repaired.result_id=result_row.result_id
set result_row.budget_spent=repaired.budget_spent;

drop temporary table if exists tmp_v056_budget_spent;

-- Terminal projects get one authoritative end date. Prefer the recorded terminal event date when available.
update biz_project project
set project.actual_end_date=coalesce(project.actual_end_date,
  (select date(max(event_record.create_time)) from biz_project_event event_record
    where event_record.project_id=project.project_id
      and event_record.event_type in ('CLOSE','CANCEL')),
  date(project.update_time),curdate())
where project.del_flag='0' and project.status in ('CLOSED','CANCELED')
  and project.actual_end_date is null;

update biz_project_routine routine
join biz_project project on project.project_id=routine.project_id and project.del_flag='0'
set routine.status='VOID',routine.update_by='migration-v056',routine.update_time=sysdate(),
  routine.version=routine.version+1
where project.status in ('CLOSED','CANCELED') and routine.status='ACTIVE';

update biz_project_staff_allocation allocation
join biz_project project on project.project_id=allocation.project_id and project.del_flag='0'
set allocation.status=case when allocation.effective_from>project.actual_end_date then 'VOID' else allocation.status end,
  allocation.effective_to=case
    when allocation.effective_from>project.actual_end_date then allocation.effective_to
    when allocation.effective_to is null or allocation.effective_to>project.actual_end_date then project.actual_end_date
    else allocation.effective_to end,
  allocation.update_by='migration-v056',allocation.update_time=sysdate(),allocation.version=allocation.version+1
where project.status in ('CLOSED','CANCELED') and allocation.status='ACTIVE'
  and (allocation.effective_to is null or allocation.effective_to>project.actual_end_date);

-- Unfinished tasks on an already terminal project are terminated, never presented as successfully completed.
update biz_project_task task
join biz_project project on project.project_id=task.project_id and project.del_flag='0'
set task.status='CANCELED',task.update_by='migration-v056',task.update_time=sysdate(),task.version=task.version+1
where project.status in ('CLOSED','CANCELED') and task.status not in ('DONE','CANCELED');

update biz_project_daily_result result_row
join biz_project project on project.project_id=result_row.project_id and project.del_flag='0'
set result_row.close_status='CLOSED'
where project.status in ('CLOSED','CANCELED') and result_row.is_current='1'
  and result_row.close_status<>'CLOSED';

-- Diagnostic only: rows returned here need a boss to decide whether the leave or the work record is valid.
select request_record.request_id,request_record.user_id,request_record.leave_start_date,
  request_record.leave_end_date,
  (select count(*) from biz_project_routine_report routine_report
    where routine_report.submitted_user_id=request_record.user_id
      and routine_report.biz_date between request_record.leave_start_date and request_record.leave_end_date) routine_report_count,
  (select count(*) from biz_project_task_report task_report
    where task_report.submitted_user_id=request_record.user_id
      and task_report.biz_date between request_record.leave_start_date and request_record.leave_end_date) task_report_count
from biz_staff_leave_request request_record
where request_record.status in ('APPROVED','CANCEL_PENDING')
having routine_report_count>0 or task_report_count>0;
