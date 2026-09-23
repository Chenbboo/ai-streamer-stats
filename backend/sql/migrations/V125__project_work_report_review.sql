-- 成员工作汇报由项目主负责人验收；已有汇报进入待验收。
alter table biz_project_work_report
  add column status varchar(16) not null default 'PENDING' comment 'PENDING/APPROVED/RETURNED',
  add column reviewed_user_id bigint default null,
  add column reviewed_user_name varchar(64) default null,
  add column review_comment varchar(500) default null,
  add column reviewed_time datetime default null;
