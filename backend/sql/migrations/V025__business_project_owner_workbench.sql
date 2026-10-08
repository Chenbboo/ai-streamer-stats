-- 项目负责人工作台与“负责人提交、老板确认”的今日填报权限。
-- 仅调整菜单和角色权限，不修改既有账号、项目或经营数据。

update sys_menu
set menu_name='负责人工作台',path='owner-workbench',component='business/owner/index',
    route_name='BusinessOwnerWorkbench',order_num=2,perms='business:project:owner:view',
    icon='guide',remark='项目负责人查看本人负责项目并提交今日执行数据'
where menu_id=4003;

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4051,'项目今日填报',4003,1,'#','','','',1,0,'F','0','0','business:project:report','#','admin',sysdate(),
 '负责人和项目成员提交当日经营草稿，需老板确认后入账','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  perms=values(perms),remark=values(remark);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r join sys_menu m on m.menu_id in(4003,4051)
where r.role_key='project_user' and r.del_flag='0';

-- 老板工作台已包含老板个人待办，继续避免重复出现负责人工作台。
delete rm from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id and r.role_key='company_owner' and r.del_flag='0'
where rm.menu_id in(4003,4051);
