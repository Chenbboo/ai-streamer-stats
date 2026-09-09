-- Independent personal allocations and manual payment evidence. No cost facts are created.
create table if not exists biz_bonus_allocation (
 allocation_id bigint not null auto_increment, award_id bigint not null, project_id bigint not null,
 status varchar(16) not null, mode varchar(16) not null, reason varchar(500) not null,
 request_key varchar(64) not null, amount decimal(20,2) not null,
 created_user_id bigint not null, created_user_name varchar(100) not null,
 approved_user_id bigint null, approved_user_name varchar(100) null, approved_time datetime null,
 version int not null default 0, create_time datetime not null default current_timestamp,
 primary key(allocation_id), unique key uk_bonus_allocation_request(project_id,request_key),
 key idx_bonus_allocation_award(award_id,status)
) engine=InnoDB default charset=utf8mb4 comment='Personal bonus allocation batches';
create table if not exists biz_bonus_allocation_line (
 line_id bigint not null auto_increment, allocation_id bigint not null, user_id bigint not null,
 user_name varchar(100) not null, amount decimal(20,2) not null, percentage decimal(5,2) null,
 reason varchar(500) not null,
 primary key(line_id), unique key uk_bonus_allocation_person(allocation_id,user_id)
) engine=InnoDB default charset=utf8mb4 comment='Personal allocation snapshots';
create table if not exists biz_bonus_payment (
 payment_id bigint not null auto_increment, project_id bigint not null, line_id bigint not null,
 amount decimal(20,2) not null, paid_date date not null, method varchar(24) not null,
 reference_no varchar(100) not null, voucher varchar(1000) not null, reason varchar(500) not null,
 request_key varchar(64) not null, status varchar(16) not null default 'RECORDED',
 recorded_user_id bigint not null, recorded_user_name varchar(100) not null,
 create_time datetime not null default current_timestamp,
 primary key(payment_id), unique key uk_bonus_payment_request(project_id,request_key),
 unique key uk_bonus_payment_reference(line_id,reference_no)
) engine=InnoDB default charset=utf8mb4 comment='Manual payment evidence, no bank transfer';
create table if not exists biz_bonus_event (
 event_id bigint not null auto_increment, project_id bigint not null, allocation_id bigint not null,
 event_type varchar(24) not null, operator_user_id bigint not null, operator_name varchar(100) not null,
 reason varchar(500) not null, snapshot longtext not null, create_time datetime not null default current_timestamp,
 primary key(event_id), key idx_bonus_event_allocation(allocation_id,event_id)
) engine=InnoDB default charset=utf8mb4 comment='Append only allocation and payment audit';

set @bonus_menu=(select menu_id from sys_menu where perms='business:incentive:list' and menu_type='C' limit 1);
insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time)
select '登记奖金发放','Ghi nhận chi thưởng',@bonus_menu,10,'','F','0','0','business:incentive:pay','migration',sysdate()
where @bonus_menu is not null and not exists(select 1 from sys_menu where perms='business:incentive:pay');
insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select '奖金发放财务','bonus_payment_finance',21,'1',1,1,'0','0','migration',sysdate(),'仅登记本公司奖金实付，不授予奖金核准权限'
where not exists(select 1 from sys_role where role_key='bonus_payment_finance' and del_flag='0');
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:incentive:list','business:incentive:pay')
where r.role_key='bonus_payment_finance' and r.del_flag='0';
-- The project sponsor currently handles actual payout; controller permission plus sponsor scope are both checked.
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms='business:incentive:pay'
where r.role_key in('company_owner','hcm_incentive_approver') and r.del_flag='0';
-- Project members may see only their own approved allocations via the scoped distribution endpoint.
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms='business:incentive:list'
where r.role_key in('project_user','project_deputy') and r.del_flag='0';
insert ignore into sys_role_menu(role_id,menu_id)
select distinct rm.role_id,m.parent_id from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id
where m.menu_id=@bonus_menu and m.parent_id>0;
