-- Employee leave is recorded once per person and day. It affects personnel cost in every project.
create table if not exists biz_staff_leave (
  leave_id bigint not null auto_increment comment 'leave record id',
  user_id bigint not null comment 'employee user id',
  user_name varchar(100) not null comment 'employee display name snapshot',
  leave_date date not null comment 'leave date',
  leave_type varchar(24) not null default 'LEAVE' comment 'leave type',
  reason varchar(500) not null comment 'leave reason',
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/CANCELED',
  recorded_project_id bigint not null comment 'project where the leave was recorded',
  recorded_user_id bigint not null comment 'operator user id',
  recorded_user_name varchar(100) not null comment 'operator name snapshot',
  version int not null default 0,
  create_by varchar(64) default '',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key (leave_id),
  unique key uk_staff_leave_day (user_id, leave_date),
  key idx_staff_leave_date_status (leave_date, status),
  key idx_staff_leave_project (recorded_project_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='employee daily leave record';
