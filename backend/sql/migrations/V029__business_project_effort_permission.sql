-- 项目负责人只获得计划投入与周确认权限，不继承老板的项目决策权限。
insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,
  is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select 4017,'计划投入管理',4002,7,'#',null,null,'',1,0,'F','0','0',
  'business:project:allocation','#','admin',sysdate(),'项目负责人维护计划投入并确认实际投入'
where not exists(select 1 from sys_menu where menu_id=4017);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,4017 from sys_role r
where r.role_key in('company_owner','project_owner') and r.del_flag='0';
