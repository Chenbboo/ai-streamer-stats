-- 项目总进度由项目负责人独立按日填报，不再从一次性任务进度推算。

create table if not exists biz_project_progress_report (
  report_id bigint not null auto_increment comment '填报ID',
  project_id bigint not null comment '项目ID',
  biz_date date not null comment '填报日期',
  progress int not null comment '填报后项目总进度0-100',
  completion_summary varchar(2000) not null comment '实际完成情况',
  evidence_urls varchar(4000) not null comment '成果凭证URL',
  submitted_user_id bigint not null comment '填报人ID',
  submitted_user_name varchar(100) not null comment '填报人名称',
  version int not null default 0,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (report_id),
  unique key uk_biz_project_progress_day (project_id,biz_date),
  key idx_biz_project_progress_latest (project_id,biz_date,report_id),
  key idx_biz_project_progress_submitter_day (submitted_user_id,biz_date)
) engine=InnoDB default charset=utf8mb4 comment='项目每日完成进度填报';
