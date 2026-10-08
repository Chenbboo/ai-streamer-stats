-- 暂停成员在单个项目中的工作，保留成员身份、任务归属和每一次暂停/启动记录。
create table if not exists biz_project_member_work_pause (
  pause_id bigint not null auto_increment,
  project_id bigint not null,
  user_id bigint not null,
  paused_time datetime not null default current_timestamp,
  started_time datetime default null,
  paused_by varchar(64) not null,
  started_by varchar(64) default null,
  primary key (pause_id),
  key idx_member_work_pause (project_id,user_id,started_time,paused_time)
) engine=InnoDB default charset=utf8mb4 comment='项目成员暂停和启动工作记录';
