-- Boss accounts always inherit the current owner-role menu set. Personal menu snapshots are
-- intended for staff and can otherwise hide pages added after the snapshot was created.
delete permission
from biz_staff_menu_permission permission
join sys_user_role user_role on user_role.user_id = permission.user_id
join sys_role role on role.role_id = user_role.role_id
where role.role_key = 'company_owner'
  and role.del_flag = '0';
