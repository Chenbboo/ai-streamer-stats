-- Company expenses are allocated twice but recognized once, when a complete month is settled.
create table if not exists biz_public_expense_policy (
 policy_id bigint not null auto_increment, company_dept_id bigint not null,
 name varchar(100) not null, category varchar(40) not null, period_type varchar(12) not null,
 amount decimal(20,2) not null, currency char(3) not null, start_month char(7) not null, end_month char(7) not null,
 estimated tinyint not null default 0, status varchar(12) not null default 'ACTIVE',
 remark varchar(500), attachment_urls varchar(4000), version int not null default 0,
 create_by varchar(64), create_time datetime not null default current_timestamp, update_by varchar(64), update_time datetime,
 primary key(policy_id), key idx_public_policy_company(company_dept_id,currency,start_month,end_month)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_month (
 bill_id bigint not null auto_increment, company_dept_id bigint not null, bill_month char(7) not null,
 currency char(3) not null, total_amount decimal(20,2) not null default 0, status varchar(12) not null default 'DRAFT',
 version int not null default 0, settled_time datetime, settled_user_id bigint, settled_user_name varchar(100),
 create_by varchar(64), create_time datetime not null default current_timestamp, update_time datetime,
 primary key(bill_id), unique key uk_public_month(company_dept_id,bill_month,currency)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_entry (
 entry_id bigint not null auto_increment, bill_id bigint not null, policy_id bigint not null, policy_version int not null,
 name varchar(100) not null, category varchar(40) not null, amount decimal(20,2) not null,
 estimated tinyint not null default 0, remark varchar(500), attachment_urls varchar(4000),
 primary key(entry_id), unique key uk_public_entry(bill_id,policy_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_owner (
 allocation_id bigint not null auto_increment, bill_id bigint not null, owner_user_id bigint not null,
 owner_name varchar(100) not null, percentage decimal(7,4) not null, amount decimal(20,2) not null,
 status varchar(12) not null default 'DRAFT', version int not null default 0, submitted_time datetime,
 primary key(allocation_id), unique key uk_public_owner(bill_id,owner_user_id), key idx_public_owner_user(owner_user_id,bill_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_project (
 project_allocation_id bigint not null auto_increment, allocation_id bigint not null,
 project_id bigint not null, project_name varchar(100) not null, percentage decimal(7,4) not null,
 amount decimal(20,2) not null, accounting_fact_id bigint,
 primary key(project_allocation_id), unique key uk_public_project(allocation_id,project_id), key idx_public_project(project_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_adjustment (
 adjustment_id bigint not null auto_increment, bill_id bigint not null, project_id bigint not null,
 amount decimal(20,2) not null, reason varchar(500) not null, request_key varchar(64) not null,
 accounting_fact_id bigint, operator_user_id bigint not null, operator_name varchar(100) not null,
 create_time datetime not null default current_timestamp,
 primary key(adjustment_id), unique key uk_public_adjustment_request(bill_id,request_key), key idx_public_adjustment_project(project_id,bill_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_public_expense_event (
 event_id bigint not null auto_increment, company_dept_id bigint not null, bill_id bigint,
 event_type varchar(24) not null, operator_user_id bigint not null, operator_name varchar(100) not null,
 reason varchar(500) not null, snapshot longtext not null, create_time datetime not null default current_timestamp,
 primary key(event_id), key idx_public_event(company_dept_id,bill_id,event_id)
) engine=InnoDB default charset=utf8mb4;
set @public_cost_sql=(select if(count(*)=0,
 'alter table biz_project_daily_result add column public_cost decimal(20,4) not null default 0 after personnel_cost','select 1')
 from information_schema.columns where table_schema=database() and table_name='biz_project_daily_result' and column_name='public_cost');
prepare public_cost_stmt from @public_cost_sql;
execute public_cost_stmt;
deallocate prepare public_cost_stmt;
insert into biz_fact_category(category_code,category_name,fact_kind,default_sign,unit_type,status,sort_order,remark)
values('COMPANY_PUBLIC_COST','公司公共费用','COST',-1,'MONEY','0',190,'公司公共费用月结及历史调整专用，禁止手工录入和冲销')
on duplicate key update category_name=values(category_name),fact_kind='COST',default_sign=-1,status='0';
set @public_finance=(select menu_id from sys_menu where parent_id=0 and path='finance' limit 1);
insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '公司公共费用','Chi phí chung công ty',@public_finance,12,'public-expenses','business/public-expenses/index','','BusinessPublicExpenses',1,0,'C','0','0','business:public-expense:list','money','migration',sysdate()
where @public_finance is not null and not exists(select 1 from sys_menu where perms='business:public-expense:list');
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms='business:public-expense:list'
where r.role_key='company_owner' and r.del_flag='0';
