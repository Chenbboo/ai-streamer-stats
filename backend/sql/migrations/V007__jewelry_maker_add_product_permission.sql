-- 制单员可以新增商品档案，但不授予修改既有商品档案或管理供应商的权限。
insert ignore into sys_role_menu(role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m on m.perms = 'jewelry:product:add'
where r.role_key = 'jewelry_maker'
  and r.del_flag = '0'
  and m.status = '0';
