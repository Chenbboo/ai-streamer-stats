-- One pending distribution per employee; all affected project owners confirm the same snapshot.
create table if not exists biz_staff_allocation_request (
  request_id bigint not null auto_increment primary key,
  user_id bigint not null,
  user_name varchar(100) not null,
  applicant_id bigint not null,
  applicant_name varchar(100) not null,
  effective_date date not null,
  reason varchar(500) not null,
  snapshot_json longtext not null,
  status varchar(20) not null default 'PENDING',
  pending_user_id bigint null,
  close_reason varchar(500) null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  unique key uk_pending_employee (pending_user_id),
  key idx_employee_requests (user_id,request_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_staff_allocation_review (
  request_id bigint not null,
  owner_user_id bigint not null,
  owner_name varchar(100) not null,
  project_id bigint not null,
  status varchar(20) not null default 'PENDING',
  comment varchar(500) null,
  reviewed_at datetime null,
  primary key (request_id,owner_user_id),
  key idx_owner_pending (owner_user_id,status)
) engine=InnoDB default charset=utf8mb4;
set @sql=(select if(count(*)=0,
  'alter table biz_project_staff_allocation add column confirmation_status varchar(20) not null default ''CONFIRMED''',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_staff_allocation' and column_name='confirmation_status');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
-- Only unassigned system placeholders are pending. Explicit zero allocations stay confirmed.
update biz_project_staff_allocation set confirmation_status='PENDING'
where status='ACTIVE' and allocation_value=0
  and remark in ('新增参与项目，待负责人重新分配投入权重','并行项目初始化为0%，待负责人重新分配');
-- Rebuild only derived zero-cost cache rows belonging to previously unconfirmed placeholders.
delete c from biz_project_member_day_cost c
join biz_project p on p.project_id=c.project_id and coalesce(p.accounting_state,'OPEN')<>'CLOSED'
join biz_project_staff_allocation a on a.project_id=c.project_id and a.user_id=c.user_id
  and a.status='ACTIVE' and a.confirmation_status='PENDING'
  and a.effective_from<=c.biz_date and (a.effective_to is null or a.effective_to>=c.biz_date)
where c.amount=0;
