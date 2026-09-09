-- V081 preserves existing accounting evidence before changing participation semantics.
create table if not exists biz_member_day_cost_pre_v079 like biz_project_member_day_cost;
insert ignore into biz_member_day_cost_pre_v079 select * from biz_project_member_day_cost;
create table if not exists biz_daily_result_pre_v079 like biz_project_daily_result;
insert ignore into biz_daily_result_pre_v079 select * from biz_project_daily_result;
create table if not exists biz_daily_result_item_pre_v079 like biz_project_daily_result_item;
insert ignore into biz_daily_result_item_pre_v079 select * from biz_project_daily_result_item;

set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_project_resource_assignment' and column_name='participation_mode')=0,
 'alter table biz_project_resource_assignment add participation_mode varchar(24) not null default ''CUSTOM''','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
-- Recover explicit intent from the original proposal; do not infer it from equal dates.
update biz_project_resource_assignment a
join biz_project p on p.project_id=a.project_id
join biz_project_proposal_staffing s on s.proposal_id=p.source_proposal_id and s.user_id=a.user_id
set a.participation_mode=s.participation_mode
where a.effective_from=s.plan_start_date and a.effective_to <=> s.plan_end_date
  and s.participation_mode in ('FOLLOW_PROJECT','UNLIMITED') and a.participation_mode='CUSTOM';

create table if not exists biz_project_member_role_period (
 project_id bigint not null,user_id bigint not null,effective_from date not null,
 member_role varchar(20) not null,operator_name varchar(64),created_at datetime not null,
 primary key(project_id,user_id,effective_from)
) engine=InnoDB default charset=utf8mb4;
insert ignore into biz_project_member_role_period(project_id,user_id,effective_from,member_role,operator_name,created_at)
select m.project_id,m.user_id,coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,curdate()),m.member_role,'migration',now()
from biz_project_member m join biz_project p on p.project_id=m.project_id;
