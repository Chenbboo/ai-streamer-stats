-- 老板端共享公司部门管理菜单；复用现有 sys_dept 组织结构。

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4005,'部门管理',4000,4,'departments','business/department/index','','BusinessDepartments',1,0,'C','0','0','business:department:list','tree','admin',sysdate(),'老板共享管理公司组织架构',''),
(4031,'管理部门',4005,1,'#','','','',1,0,'F','0','0','business:department:manage','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),icon=values(icon),
  remark=values(remark),menu_name_vi=values(menu_name_vi);

update sys_menu set order_num=5 where menu_id=4003;

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r join sys_menu m on m.menu_id in (4005,4031)
where r.role_key='company_owner' and r.del_flag='0';
