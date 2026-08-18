-- 老板端共享公司人员管理菜单；不开放系统用户角色和权限配置页面。

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4004,'人员管理',4000,3,'staff','business/staff/index','','BusinessStaff',1,0,'C','0','0','business:staff:list','peoples','admin',sysdate(),'老板共享管理公司人员',''),
(4021,'管理人员',4004,1,'#','','','',1,0,'F','0','0','business:staff:manage','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),icon=values(icon),
  remark=values(remark),menu_name_vi=values(menu_name_vi);

update sys_menu set order_num=4 where menu_id=4003;

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r join sys_menu m on m.menu_id in (4004,4021)
where r.role_key='company_owner' and r.del_flag='0';
