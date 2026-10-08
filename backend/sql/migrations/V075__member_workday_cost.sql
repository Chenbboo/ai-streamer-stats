create table if not exists biz_project_member_cost_period (
 project_id bigint not null,user_id bigint not null,user_name varchar(100),member_role varchar(20),
 joined_date date not null,left_date date not null,
 primary key(project_id,user_id,joined_date,left_date)
) engine=InnoDB default charset=utf8mb4 comment='成员再次加入前保留的参与日期';
create table if not exists biz_project_member_day_cost (
 project_id bigint not null,user_id bigint not null,user_name varchar(100),biz_date date not null,
 amount decimal(20,2),currency varchar(3),pricing_status varchar(16) not null,issue varchar(300),
 rate_policy_id bigint,calendar_id bigint,basis_json longtext,update_time datetime,
 primary key(project_id,user_id,biz_date),key idx_member_day_rate(rate_policy_id)
) engine=InnoDB default charset=utf8mb4 comment='按成员参与工作日自动计算的人员成本';
update biz_project set cost_policy_version='MEMBER_DAYS_V1' where del_flag='0' and coalesce(accounting_state,'OPEN')<>'CLOSED';
-- Keep past workload records as history; remove the obsolete navigation entry.
update sys_menu set visible='1',status='1' where component='business/resources/index';
