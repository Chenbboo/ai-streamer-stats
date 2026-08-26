-- 一次性任务由实际负责人按日填报进度、完成说明和成果凭证。

create table if not exists biz_project_task_report (
  report_id bigint not null auto_increment comment '填报ID',
  task_id bigint not null comment '一次性任务ID',
  project_id bigint not null comment '项目ID',
  biz_date date not null comment '填报日期',
  progress int not null comment '填报后任务总进度0-100',
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
  unique key uk_biz_task_report_day (task_id,biz_date),
  key idx_biz_task_report_project_day (project_id,biz_date),
  key idx_biz_task_report_submitter_day (submitted_user_id,biz_date)
) engine=InnoDB default charset=utf8mb4 comment='一次性任务完成填报';
