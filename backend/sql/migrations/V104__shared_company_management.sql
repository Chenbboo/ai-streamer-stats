-- Explicit grants: existing bosses jointly manage existing companies only.
-- A future company or boss receives no automatic grant. Re-running must not restore revoked grants.
create table if not exists biz_company_access (
 company_dept_id bigint not null, user_id bigint not null, capabilities varchar(255) not null default '',
 version int not null default 1, update_by varchar(64) not null default '',
 update_time datetime not null default current_timestamp,
 primary key(company_dept_id,user_id), key idx_company_access_user(user_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_company_access_event (
 event_id bigint not null auto_increment primary key, company_dept_id bigint not null, user_id bigint not null,
 capabilities varchar(255) not null, operator_id bigint not null, operator_name varchar(64) not null,
 reason varchar(500) not null, create_time datetime not null default current_timestamp,
 key idx_company_access_history(company_dept_id,user_id,event_id)
) engine=InnoDB default charset=utf8mb4;
-- The marker is permanent: repeat migrations never authorize new accounts/companies silently.
insert into biz_company_access(company_dept_id,user_id,capabilities,update_by)
select d.dept_id,u.user_id,
 concat('BUSINESS,STAFF,COST_READ,COST_WRITE,ATTENDANCE_READ,AUTHORIZE',
 case when d.leader_user_id=u.user_id then ',CUTOVER' else '' end), 'migration:V104'
from sys_dept d cross join sys_user u
where d.parent_id=100 and d.status='0' and d.del_flag='0' and u.status='0' and u.del_flag='0'
and exists(select 1 from sys_user_role ur join sys_role r on r.role_id=ur.role_id
 where ur.user_id=u.user_id and r.role_key='company_owner' and r.status='0' and r.del_flag='0')
and not exists(select 1 from biz_company_access_event where reason='BOOTSTRAP_V104')
on duplicate key update user_id=values(user_id);
insert into biz_company_access_event(company_dept_id,user_id,capabilities,operator_id,operator_name,reason)
select 0,0,'',0,'migration:V104','BOOTSTRAP_V104'
where not exists(select 1 from biz_company_access_event where reason='BOOTSTRAP_V104');
