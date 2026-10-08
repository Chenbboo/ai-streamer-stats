-- 项目负责人驱动 KPI、奖金和人员成本；人员档案与账号管理仍保持只读。
-- 只调整菜单角色权限，不修改人员、项目、KPI、奖金或成本历史数据。

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,
  is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4022,'维护人员成本',4004,2,'#','','','',1,0,'F','0','0','business:staff:cost','#','admin',sysdate(),
 '仅允许查看人员资料并维护内部核算成本，不允许修改人员档案或账号','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  perms=values(perms),status=values(status),remark=values(remark);

update sys_menu
set remark='公司负责人可维护人员；项目负责人只读人员资料并维护内部核算成本'
where menu_id=4004;

-- 项目负责人可进入人员管理，并只获得独立的成本维护能力。
insert ignore into sys_role_menu(role_id,menu_id)
select role.role_id,menu.menu_id
from sys_role role join sys_menu menu on menu.menu_id in(4000,4004,4022)
where role.role_key='project_owner' and role.del_flag='0';

-- 避免项目负责人角色继承完整人员管理；老板或管理员自身角色权限不受影响。
delete role_menu from sys_role_menu role_menu
join sys_role role on role.role_id=role_menu.role_id
where role.role_key='project_owner' and role.del_flag='0' and role_menu.menu_id=4021;

-- 公司负责人继续使用原完整人员管理，同时补充独立成本权限供页面与 AI 共用。
insert ignore into sys_role_menu(role_id,menu_id)
select role.role_id,menu.menu_id
from sys_role role join sys_menu menu on menu.menu_id in(4000,4004,4022)
where role.role_key='company_owner' and role.del_flag='0';

-- 修复旧环境中遗漏的项目负责人 KPI 菜单权限；服务层仍限制为本人负责项目。
insert ignore into sys_role_menu(role_id,menu_id)
select role.role_id,menu.menu_id
from sys_role role join sys_menu menu on menu.menu_id in(4000,4002,4010,4071,4072,4073)
where role.role_key='project_owner' and role.del_flag='0';

update sys_menu
set remark='项目负责人设置项目KPI与奖金并完成结算；老板保留查看和修改能力'
where menu_id=4010;
