-- Append-only progress history. Previously overwritten daily revisions cannot be recovered.
alter table biz_project_progress_report drop index uk_biz_project_progress_day;
alter table biz_project_progress_report
  add column issues_risks varchar(2000) null,
  add column next_plan varchar(2000) null,
  add column sync_tasks boolean not null default false,
  add column sync_routines boolean not null default false,
  add column snapshot_json longtext null,
  add column parent_project_id bigint null,
  add column project_name_snapshot varchar(160) null;
alter table biz_project_progress_report
  add key idx_progress_project_version(project_id,report_id),
  add key idx_progress_parent_version(parent_project_id,report_id);
alter table biz_project add column progress_weight decimal(12,4) null comment '子项目进度权重；空值按1计算';
-- Link existing retained records to their project; do not invent missing historical snapshots.
update biz_project_progress_report r join biz_project p on p.project_id=r.project_id
set r.parent_project_id=p.parent_id,r.project_name_snapshot=p.project_name;
create table biz_project_progress_notification (
  notification_id bigint not null auto_increment primary key,
  report_id bigint not null,
  recipient_user_id bigint not null,
  read_time datetime null,
  create_time datetime not null default current_timestamp,
  unique key uk_progress_recipient(report_id,recipient_user_id),
  key idx_progress_inbox(recipient_user_id,read_time,notification_id)
) engine=InnoDB default charset=utf8mb4;
