-- Project deputies receive project-scoped execution-management permissions without inheriting
-- main-owner submission, allocation, accounting or closure authority.

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '项目副负责人','project_deputy',8,5,1,1,'0','0','admin',sysdate(),
  '按项目成员关系协管项目执行，不承担主负责人专属提交与经营决策'
where not exists(select 1 from sys_role where role_key='project_deputy' and del_flag='0');

update sys_role
set role_name='项目副负责人',role_sort=8,data_scope=5,status='0',
    remark='按项目成员关系协管项目执行，不承担主负责人专属提交与经营决策'
where role_key='project_deputy' and del_flag='0';

-- Shared project-center menus plus execution-management buttons. The service layer still limits
-- every operation to projects where the caller is an active DEPUTY member.
insert ignore into sys_role_menu(role_id,menu_id)
select role.role_id,menu.menu_id
from sys_role role
join sys_menu menu on menu.menu_id in(4000,4002,4012,4013,4014,4017)
where role.role_key='project_deputy' and role.del_flag='0';

-- Do not accidentally inherit owner-only submission/workbench permissions if this migration is rerun.
delete role_menu
from sys_role_menu role_menu
join sys_role role on role.role_id=role_menu.role_id
where role.role_key='project_deputy' and role.del_flag='0'
  and role_menu.menu_id in(4003,4015,4016,4051,4071,4072,4073);

-- Every deputy remains a normal project participant so the personal work schedule/reporting
-- permissions continue to come from project_user.
insert ignore into sys_user_role(user_id,role_id)
select distinct member.user_id,project_user_role.role_id
from biz_project_member member
join biz_project project on project.project_id=member.project_id and project.del_flag='0'
join sys_role project_user_role on project_user_role.role_key='project_user' and project_user_role.del_flag='0'
where member.member_role='DEPUTY' and member.status='0'
  and not exists(
    select 1 from sys_user_role privileged_user_role
    join sys_role privileged_role on privileged_role.role_id=privileged_user_role.role_id
    where privileged_user_role.user_id=member.user_id
      and privileged_role.role_key in('admin','company_owner') and privileged_role.del_flag='0'
  );

insert ignore into sys_user_role(user_id,role_id)
select distinct member.user_id,deputy_role.role_id
from biz_project_member member
join biz_project project on project.project_id=member.project_id and project.del_flag='0'
join sys_role deputy_role on deputy_role.role_key='project_deputy' and deputy_role.del_flag='0'
where member.member_role='DEPUTY' and member.status='0'
  and not exists(
    select 1 from sys_user_role privileged_user_role
    join sys_role privileged_role on privileged_role.role_id=privileged_user_role.role_id
    where privileged_user_role.user_id=member.user_id
      and privileged_role.role_key in('admin','company_owner') and privileged_role.del_flag='0'
  );

-- The deputy role is relationship-driven and must disappear after the user's final deputy
-- assignment is removed. Admins and company owners do not need the redundant role.
delete user_role
from sys_user_role user_role
join sys_role deputy_role on deputy_role.role_id=user_role.role_id
left join (
  select distinct member.user_id
  from biz_project_member member
  join biz_project project on project.project_id=member.project_id and project.del_flag='0'
  where member.member_role='DEPUTY' and member.status='0'
) active_deputy on active_deputy.user_id=user_role.user_id
where deputy_role.role_key='project_deputy' and deputy_role.del_flag='0'
  and active_deputy.user_id is null;

delete deputy_user_role
from sys_user_role deputy_user_role
join sys_role deputy_role on deputy_role.role_id=deputy_user_role.role_id
join sys_user_role privileged_user_role on privileged_user_role.user_id=deputy_user_role.user_id
join sys_role privileged_role on privileged_role.role_id=privileged_user_role.role_id
where deputy_role.role_key='project_deputy' and deputy_role.del_flag='0'
  and privileged_role.role_key in('admin','company_owner') and privileged_role.del_flag='0';
