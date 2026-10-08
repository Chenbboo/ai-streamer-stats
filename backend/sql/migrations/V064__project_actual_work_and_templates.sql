-- P2: versioned templates, resource plans and confirmed actual work.
-- Run after V063 in a backed-up, verified target database. Existing projects and percentage amounts remain unchanged.
-- Every DDL is additive/re-runnable. Do not drop these tables when rolling back application writes.
set names utf8mb4;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project add column template_version varchar(32) not null default ''LEGACY_V1''','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project' and column_name='template_version');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project add column template_snapshot_json longtext null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project' and column_name='template_snapshot_json');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal add column template_version varchar(32) not null default ''LEGACY_V1''','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal' and column_name='template_version');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal add column template_snapshot_json longtext null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal' and column_name='template_snapshot_json');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal_staffing add column input_unit varchar(16) null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal_staffing' and column_name='input_unit');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal_staffing add column input_quantity decimal(16,4) null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal_staffing' and column_name='input_quantity');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal_staffing add column calendar_id bigint null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal_staffing' and column_name='calendar_id');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_project_proposal_staffing add column unit_policy_id bigint null','select 1') from information_schema.columns where table_schema=database() and table_name='biz_project_proposal_staffing' and column_name='unit_policy_id');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

set @p2_ddl=(select if(count(*)=0,'alter table biz_staff_cost_policy add column rate_minutes_per_day int not null default 480 comment ''Rate-day conversion snapshot; legacy formulas unchanged''','select 1') from information_schema.columns where table_schema=database() and table_name='biz_staff_cost_policy' and column_name='rate_minutes_per_day');
prepare p2_stmt from @p2_ddl;
execute p2_stmt;
deallocate prepare p2_stmt;

-- Nullable estimates distinguish unpriced from an explicitly estimated zero. Original values are retained.
-- Day/hour rates have no monthly denominator. Existing monthly denominators are retained exactly.
alter table biz_staff_cost_policy modify standard_work_days decimal(6,2) null default null;
alter table biz_project_proposal modify estimated_personnel_cost decimal(20,2) null default null;
alter table biz_project_proposal modify estimated_total_cost decimal(20,2) null default null;
alter table biz_project_proposal modify expected_profit decimal(20,2) null default null;
alter table biz_project_proposal modify break_even_revenue decimal(20,2) null default null;
alter table biz_project_proposal_staffing modify estimated_cost decimal(20,2) null;
alter table biz_project_proposal_staffing modify allocation_percent decimal(8,2) null;

create table if not exists biz_project_template_version (
 template_version varchar(32) primary key,template_name varchar(100) not null,
 authorization_mode varchar(32) not null,management_mode varchar(24) not null,close_method varchar(32) not null,
 snapshot_json longtext not null,status varchar(16) not null default 'ACTIVE',create_time datetime not null
) engine=InnoDB default charset=utf8mb4;
insert ignore into biz_project_template_version values
 ('LIGHT_V1','轻量协作 v1','SELF_AUTHORIZED','LIGHT','DIRECT','{"templateVersion":"LIGHT_V1","budgetRequired":false,"kpiRequired":false,"bonusRequired":false,"closeMethod":"DIRECT","changeAuthorization":"OWNER","defaultCalendarId":1,"defaultUnitPolicyId":1,"workSelfConfirmation":false}', 'ACTIVE',sysdate()),
 ('CONTROLLED_V1','受控交付 v1','MANUAL_APPROVAL','STANDARD','RESULT_ACCEPTANCE','{"templateVersion":"CONTROLLED_V1","budgetRequired":false,"kpiRequired":false,"bonusRequired":false,"closeMethod":"RESULT_ACCEPTANCE","changeAuthorization":"SPONSOR","defaultCalendarId":1,"defaultUnitPolicyId":1,"workSelfConfirmation":false}', 'ACTIVE',sysdate()),
 ('SERVICE_V1','持续服务 v1','SELF_AUTHORIZED','STANDARD','RESULT_ACCEPTANCE','{"templateVersion":"SERVICE_V1","budgetRequired":false,"kpiRequired":false,"bonusRequired":false,"finiteReviewWindowRequired":true,"changeAuthorization":"SPONSOR","defaultCalendarId":1,"defaultUnitPolicyId":1,"workSelfConfirmation":false}', 'ACTIVE',sysdate());

create table if not exists biz_work_calendar (
 calendar_id bigint primary key auto_increment,calendar_name varchar(100) not null,time_zone varchar(80) not null,
 working_weekdays varchar(20) not null,daily_minutes int not null,exceptions_json longtext null,
 effective_from date not null,effective_to date null,version int not null default 1,
 create_by varchar(64) not null,create_time datetime not null
) engine=InnoDB default charset=utf8mb4;
insert ignore into biz_work_calendar(calendar_id,calendar_name,time_zone,working_weekdays,daily_minutes,exceptions_json,effective_from,version,create_by,create_time)
 values(1,'标准工作周（可新增日历版本）','Asia/Shanghai','1,2,3,4,5',480,'[]','2000-01-01',1,'migration',sysdate());
create table if not exists biz_work_unit_policy (
 unit_policy_id bigint primary key auto_increment,policy_name varchar(100) not null,minutes_per_day int not null,
 effective_from date not null,effective_to date null,version int not null default 1,create_by varchar(64) not null,create_time datetime not null
) engine=InnoDB default charset=utf8mb4;
insert ignore into biz_work_unit_policy(unit_policy_id,policy_name,minutes_per_day,effective_from,version,create_by,create_time)
 values(1,'1人天=8小时（独立于日历容量）',480,'2000-01-01',1,'migration',sysdate());

create table if not exists biz_project_plan_baseline (
 baseline_id bigint primary key auto_increment,project_id bigint not null,baseline_version int not null,template_version varchar(32) not null,
 snapshot_json longtext not null,authorization_source varchar(32) not null,authorized_user_id bigint not null,
 authorized_user_name varchar(64) not null,create_time datetime not null,
 unique key uk_project_baseline(project_id,baseline_version)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_plan_change (
 change_id bigint primary key auto_increment,project_id bigint not null,base_version int not null,snapshot_json longtext not null,
 status varchar(16) not null,reason varchar(2000) not null,request_user_id bigint not null,request_user_name varchar(64) not null,
 reviewed_user_id bigint null,reviewed_user_name varchar(64) null,review_reason varchar(2000) null,reviewed_time datetime null,
 version int not null default 0,create_time datetime not null,index idx_change_pending(project_id,status)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_plan_forecast (
 forecast_id bigint primary key auto_increment,project_id bigint not null,project_version int not null,forecast_end_date date not null,
 forecast_cost decimal(20,2) null,reason varchar(2000) not null,create_user_id bigint not null,create_by varchar(64) not null,
 create_time datetime not null,index idx_forecast_project(project_id,forecast_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists biz_project_resource_assignment (
 assignment_id bigint primary key auto_increment,project_id bigint not null,user_id bigint not null,effective_from date not null,effective_to date not null,
 input_unit varchar(16) not null,input_quantity decimal(16,4) not null,planned_minutes int not null,
 calendar_id bigint not null,calendar_snapshot_json longtext not null,unit_policy_id bigint not null,unit_snapshot_json longtext not null,
 status varchar(16) not null default 'ACTIVE',version int not null default 0,reason varchar(2000) null,retire_reason varchar(2000) null,
 create_by varchar(64) not null,create_time datetime not null,update_by varchar(64) null,update_time datetime null,
 index idx_assignment_project(project_id,status),index idx_assignment_person(user_id,effective_from,effective_to)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_resource_day (
 assignment_id bigint not null,project_id bigint not null,user_id bigint not null,biz_date date not null,time_zone varchar(80) not null,
 planned_minutes int not null,capacity_minutes int not null,overload_reason varchar(2000) null,
 primary key(assignment_id,biz_date),index idx_plan_person_day(user_id,biz_date)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_person_day_lock (
 user_id bigint not null,biz_date date not null,primary key(user_id,biz_date)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_work_entry (
 entry_id bigint primary key auto_increment,logical_entry_id bigint null,parent_entry_id bigint null,
 project_id bigint not null,user_id bigint not null,biz_date date not null,time_zone varchar(80) not null,
 activity varchar(1000) not null,input_unit varchar(16) not null,input_quantity decimal(16,4) not null,work_minutes int not null,
 calendar_id bigint not null,calendar_snapshot_json longtext not null,unit_policy_id bigint not null,unit_snapshot_json longtext not null,
 minutes_per_day int not null,capacity_minutes int not null,source_key varchar(100) not null,
 status varchar(16) not null,is_current char(1) not null default '0',revision_no int not null,version int not null default 0,
 reason varchar(2000) null,submitted_user_id bigint null,confirmed_user_id bigint null,review_reason varchar(2000) null,
 create_user_id bigint not null,create_by varchar(64) not null,create_time datetime not null,update_by varchar(64) null,update_time datetime not null,
 unique key uk_work_source(project_id,source_key),index idx_work_logical(logical_entry_id,is_current),
 index idx_work_person_day(user_id,biz_date,status),index idx_work_project(project_id,status,is_current)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_work_audit (
 audit_id bigint primary key auto_increment,entry_id bigint not null,action varchar(32) not null,snapshot_json longtext not null,
 actor_id bigint not null,actor_name varchar(64) not null,reason varchar(2000) null,create_time datetime not null,index idx_work_audit(entry_id,audit_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_work_event (
 event_id bigint primary key auto_increment,entry_id bigint not null,project_id bigint not null,status varchar(16) not null default 'PENDING',
 attempts int not null default 0,last_error varchar(300) null,create_time datetime not null,update_time datetime null,
 unique key uk_work_event(entry_id),index idx_work_event_pending(status,event_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_work_cost (
 entry_id bigint primary key,project_id bigint not null,user_id bigint not null,biz_date date not null,pricing_status varchar(16) not null,
 amount decimal(20,2) null,currency varchar(3) not null,rate_policy_id bigint null,rate_policy_version int null,basis_json longtext not null,
 update_time datetime not null,index idx_work_cost_project(project_id,biz_date,pricing_status)
) engine=InnoDB default charset=utf8mb4;

-- Permissions inherit only existing project-domain roles. Service checks actual project responsibility.
set @p2_parent=(select menu_id from sys_menu where perms='business:project:list' and menu_type='C' limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,menu_name_vi)
select '实际工作填报',@p2_parent,20,'#','','','',1,0,'F','0','0','business:work:report','#','migration',sysdate(),'Ghi công việc thực tế' where @p2_parent is not null and not exists(select 1 from sys_menu where perms='business:work:report');
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,menu_name_vi)
select '实际工作确认',@p2_parent,20,'#','','','',1,0,'F','0','0','business:work:confirm','#','migration',sysdate(),'Xác nhận công việc' where @p2_parent is not null and not exists(select 1 from sys_menu where perms='business:work:confirm');
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,menu_name_vi)
select '项目资源安排',@p2_parent,20,'#','','','',1,0,'F','0','0','business:resource:edit','#','migration',sysdate(),'Lập kế hoạch nguồn lực' where @p2_parent is not null and not exists(select 1 from sys_menu where perms='business:resource:edit');
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,menu_name_vi)
select '资源政策配置',@p2_parent,20,'#','','','',1,0,'F','0','0','business:resource:config','#','migration',sysdate(),'Cấu hình nguồn lực' where @p2_parent is not null and not exists(select 1 from sys_menu where perms='business:resource:config');
insert ignore into sys_role_menu(role_id,menu_id)
select distinct rm.role_id,m.menu_id from sys_role_menu rm join sys_menu old on old.menu_id=rm.menu_id
join sys_menu m on m.perms='business:work:report'
where old.perms in('business:project:owner:view','business:project:work:view','business:project:effort','business:project:manage','business:project:task');
insert ignore into sys_role_menu(role_id,menu_id)
select distinct rm.role_id,m.menu_id from sys_role_menu rm join sys_menu old on old.menu_id=rm.menu_id
join sys_menu m on m.perms in('business:work:confirm','business:resource:edit')
where old.perms in('business:project:manage','business:project:owner:view');
