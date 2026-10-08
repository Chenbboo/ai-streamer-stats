-- 公司经营与其他业务模块存在相同的子路径（例如 staff）。
-- 为动态路由设置全局唯一名称，避免 Vue Router 后注册的菜单覆盖先注册的菜单并落入 404。

update sys_menu
set route_name=case menu_id
  when 4000 then 'Business'
  when 4001 then 'BusinessBoss'
  when 4002 then 'BusinessProjects'
  when 4003 then 'BusinessMyWorkbench'
  when 4004 then 'BusinessStaff'
  when 4005 then 'BusinessDepartments'
  else route_name
end,
update_by='admin',update_time=sysdate()
where menu_id between 4000 and 4005;
