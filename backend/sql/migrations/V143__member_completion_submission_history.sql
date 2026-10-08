-- 执行填报的每日最新值仍用于进度及统计，每次提交另存不可覆盖的内容和附件快照。
-- 提交历史长期保存，至少三个月；不设置到期删除任务。
create table if not exists biz_project_completion_submission (
  submission_id bigint not null auto_increment,
  work_type varchar(16) not null,
  source_report_id bigint not null,
  source_version int not null,
  project_id bigint not null,
  project_name varchar(200) default null,
  work_name varchar(200) default null,
  member_user_id bigint default null,
  member_name varchar(64) default null,
  report_date date not null,
  report_details text,
  issue_reason text,
  evidence_urls text,
  actual_value decimal(20,4) default null,
  unit varchar(32) default null,
  target_mode varchar(16) default null,
  progress int default null,
  submitted_time datetime not null default current_timestamp,
  primary key (submission_id),
  unique key uk_completion_source_version (work_type,source_report_id,source_version),
  key idx_completion_project_date (project_id,report_date,submission_id)
) engine=InnoDB default charset=utf8mb4 comment='成员任务每次提交历史，内容附件至少保存三个月';

insert into biz_project_completion_submission
(work_type,source_report_id,source_version,project_id,project_name,work_name,
 member_user_id,member_name,report_date,report_details,issue_reason,evidence_urls,
 actual_value,unit,target_mode,submitted_time)
select 'ROUTINE',rr.report_id,coalesce(rr.version,0),rr.project_id,p.project_name,
 coalesce(r.routine_name,'持续工作'),rr.submitted_user_id,rr.submitted_user_name,rr.biz_date,
 rr.summary,rr.issue_reason,rr.evidence_urls,rr.actual_value,rr.unit,r.target_mode,
 coalesce(rr.update_time,rr.create_time,now())
from biz_project_routine_report rr join biz_project p on p.project_id=rr.project_id
left join biz_project_routine r on r.routine_id=rr.routine_id and r.project_id=rr.project_id
where rr.status='SUBMITTED' and not exists(select 1 from biz_project_completion_submission s
 where s.work_type='ROUTINE' and s.source_report_id=rr.report_id and s.source_version=coalesce(rr.version,0));

insert into biz_project_completion_submission
(work_type,source_report_id,source_version,project_id,project_name,work_name,
 member_user_id,member_name,report_date,report_details,evidence_urls,progress,submitted_time)
select 'TASK',tr.report_id,coalesce(tr.version,0),tr.project_id,p.project_name,
 coalesce(t.task_name,'一次性工作'),tr.submitted_user_id,tr.submitted_user_name,tr.biz_date,
 tr.completion_summary,tr.evidence_urls,tr.progress,coalesce(tr.update_time,tr.create_time,now())
from biz_project_task_report tr join biz_project p on p.project_id=tr.project_id
left join biz_project_task t on t.task_id=tr.task_id and t.project_id=tr.project_id
where not exists(select 1 from biz_project_completion_submission s
 where s.work_type='TASK' and s.source_report_id=tr.report_id and s.source_version=coalesce(tr.version,0));
