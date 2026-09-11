-- 项目管理费在项目核算关闭时一次性确认为项目成本，付款记录不重复入账。
insert into biz_fact_category
  (category_code,category_name,fact_kind,default_sign,unit_type,status,sort_order,remark)
values
  ('PROJECT_MANAGEMENT_FEE','项目管理费','COST',-1,'MONEY','0',180,'项目核算关闭时确认的负责人管理费')
on duplicate key update
  category_name=values(category_name),fact_kind=values(fact_kind),default_sign=values(default_sign),
  unit_type=values(unit_type),status='0',sort_order=values(sort_order),remark=values(remark);

create table if not exists biz_project_management_fee (
  fee_id bigint not null auto_increment,
  project_id bigint not null,
  recipient_user_id bigint not null,
  recipient_user_name varchar(100) not null,
  calculation_mode varchar(20) not null,
  fixed_amount decimal(20,2) null,
  profit_rate decimal(7,4) null,
  minimum_profit decimal(20,2) not null default 0,
  cap_amount decimal(20,2) null,
  currency varchar(8) not null,
  status varchar(20) not null,
  config_reason varchar(500) not null,
  configured_user_id bigint not null,
  configured_user_name varchar(100) not null,
  configured_time datetime not null,
  basis_revenue decimal(20,2) null,
  basis_business_cost decimal(20,2) null,
  basis_personnel_cost decimal(20,2) null,
  basis_bonus_cost decimal(20,2) null,
  basis_adjustment decimal(20,2) null,
  pre_fee_profit decimal(20,2) null,
  settled_amount decimal(20,2) null,
  accounting_fact_id bigint null,
  settled_user_id bigint null,
  settled_user_name varchar(100) null,
  settled_time datetime null,
  version int not null default 0,
  create_time datetime not null default current_timestamp,
  update_time datetime null,
  primary key (fee_id),
  unique key uk_project_management_fee_project (project_id),
  key idx_project_management_fee_status (status,project_id)
) engine=InnoDB default charset=utf8mb4 comment='项目负责人管理费配置与结算';

create table if not exists biz_project_management_fee_payment (
  payment_id bigint not null auto_increment,
  fee_id bigint not null,
  project_id bigint not null,
  amount decimal(20,2) not null,
  paid_date date not null,
  method varchar(24) not null,
  reference_no varchar(100) not null,
  voucher varchar(1000) not null,
  reason varchar(500) not null,
  request_key varchar(64) not null,
  recorded_user_id bigint not null,
  recorded_user_name varchar(100) not null,
  create_time datetime not null default current_timestamp,
  primary key (payment_id),
  unique key uk_management_fee_payment_request (project_id,request_key),
  unique key uk_management_fee_payment_reference (fee_id,reference_no),
  key idx_management_fee_payment_fee (fee_id,payment_id)
) engine=InnoDB default charset=utf8mb4 comment='项目管理费付款凭证，不重复计入项目成本';

create table if not exists biz_project_management_fee_event (
  event_id bigint not null auto_increment,
  project_id bigint not null,
  fee_id bigint null,
  event_type varchar(24) not null,
  operator_user_id bigint not null,
  operator_name varchar(100) not null,
  reason varchar(500) not null,
  snapshot longtext not null,
  create_time datetime not null default current_timestamp,
  primary key (event_id),
  key idx_management_fee_event_project (project_id,event_id)
) engine=InnoDB default charset=utf8mb4 comment='项目管理费配置、结算与付款审计';
