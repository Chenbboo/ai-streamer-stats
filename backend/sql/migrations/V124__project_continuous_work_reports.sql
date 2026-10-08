-- 独立保存持续工作的周期文字和附件汇报，不改变每日完成量。
create table if not exists biz_project_work_report (
  report_id bigint not null auto_increment,
  routine_id bigint not null,
  project_id bigint not null,
  frequency varchar(16) not null comment 'DAILY/WEEKLY/MONTHLY',
  period_start date not null,
  period_end date not null,
  content varchar(4000) default null,
  attachment_urls varchar(4000) default null,
  submitted_user_id bigint not null,
  submitted_user_name varchar(64) not null,
  create_time datetime not null default current_timestamp,
  primary key (report_id),
  key idx_work_report_project (project_id, create_time),
  key idx_work_report_routine (routine_id, period_start, frequency)
) engine=InnoDB default charset=utf8mb4 comment='持续工作文字与附件汇报';
