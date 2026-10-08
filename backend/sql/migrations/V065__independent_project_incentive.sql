-- P3: project indicators and HR reward approvals have independent lifecycles.
-- Back up and verify database() before applying. No historical money, actor, status or source ID is rewritten.
-- New plan publications explicitly write INDEPENDENT_V1; old/in-flight plans retain LEGACY_LINKED.
set @p3_sql=(select if(count(*)=0,
  'alter table biz_project_kpi_plan add column reward_policy_version varchar(24) not null default ''LEGACY_LINKED'' comment ''LEGACY_LINKED/INDEPENDENT_V1''',
  'select 1') from information_schema.columns where table_schema=database()
  and table_name='biz_project_kpi_plan' and column_name='reward_policy_version');
prepare p3_stmt from @p3_sql;
execute p3_stmt;
deallocate prepare p3_stmt;

create table if not exists biz_incentive_rule (
  rule_id bigint not null auto_increment,
  project_id bigint not null,
  rule_version int not null,
  rule_name varchar(100) not null,
  policy_version varchar(24) not null comment 'FIXED_V1: immutable fixed reward with optional minimum KPI score',
  amount decimal(20,2) not null,
  currency char(3) not null,
  min_score decimal(9,2) default null,
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/RETIRED',
  created_user_id bigint not null,
  created_user_name varchar(100) not null,
  reason varchar(500) not null,
  create_by varchar(64) default '',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key(rule_id),
  unique key uk_incentive_rule_version(project_id,rule_version)
) engine=InnoDB default charset=utf8mb4 comment='HR independent incentive rule versions';

create table if not exists biz_incentive_award (
  award_id bigint not null auto_increment,
  project_id bigint not null,
  company_dept_id bigint not null,
  rule_id bigint not null,
  rule_version int not null,
  rule_name varchar(100) not null,
  policy_version varchar(24) not null,
  settlement_id bigint default null comment 'Optional confirmed independent project KPI evidence; never legacy bonus source',
  score_snapshot decimal(9,2) default null,
  amount decimal(20,2) not null,
  currency char(3) not null,
  biz_date date not null,
  reason varchar(500) not null,
  request_key varchar(64) not null,
  status varchar(16) not null default 'DRAFT' comment 'DRAFT/SUBMITTED/RETURNED/APPROVED/CANCELED; never PAID',
  applicant_user_id bigint not null,
  applicant_user_name varchar(100) not null,
  approved_user_id bigint default null,
  approved_user_name varchar(100) default null,
  approved_time datetime default null,
  accounting_fact_id bigint default null,
  review_comment varchar(500) default null,
  version int not null default 0,
  create_by varchar(64) default '',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key(award_id),
  unique key uk_incentive_award_request(project_id,request_key),
  unique key uk_incentive_award_fact(accounting_fact_id),
  key idx_incentive_award_status(project_id,status),
  key idx_incentive_award_evidence(settlement_id,rule_id)
) engine=InnoDB default charset=utf8mb4 comment='HR reward approval; no invented allocations or payroll payment';

create table if not exists biz_incentive_event (
  event_id bigint not null auto_increment,
  project_id bigint not null,
  award_id bigint default null,
  event_type varchar(40) not null,
  from_status varchar(16) default null,
  to_status varchar(16) default null,
  operator_user_id bigint not null,
  operator_name varchar(100) not null,
  reason varchar(600) not null,
  create_time datetime not null default current_timestamp,
  primary key(event_id),
  key idx_incentive_event_project(project_id,event_id),
  key idx_incentive_event_award(award_id,event_id)
) engine=InnoDB default charset=utf8mb4 comment='Append-only incentive audit trail';

-- Menus/permissions are installed by the shared three-system navigation migration.
-- Rollback: stop new writes while retaining these tables and compatible readers; do not delete approved sources.
