-- Repair existing installations without rerunning or editing the previously applied V067.
-- Reading still requires actual company leadership or an explicitly granted company scope.
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
  on m.perms in ('business:attendance:self','business:attendance:read')
where r.role_key='company_owner' and r.del_flag='0';

-- Include the attendance page and its HR navigation ancestors.
insert ignore into sys_role_menu(role_id,menu_id)
select distinct rm.role_id,p.menu_id from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id and r.role_key='company_owner' and r.del_flag='0'
join sys_menu child on child.menu_id=rm.menu_id
join sys_menu p on p.menu_id=child.parent_id
where child.perms in ('business:attendance:self','business:attendance:read');

insert ignore into sys_role_menu(role_id,menu_id)
select distinct rm.role_id,p.menu_id from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id and r.role_key='company_owner' and r.del_flag='0'
join sys_menu child on child.menu_id=rm.menu_id and child.component='business/attendance/index'
join sys_menu p on p.menu_id=child.parent_id;
