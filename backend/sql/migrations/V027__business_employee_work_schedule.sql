-- 普通员工“我的安排”与项目负责人工作台分层。
-- 普通项目成员只显示日/周/月个人安排；主负责人保留项目中心和负责人工作台。

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '项目负责人','project_owner',7,5,1,1,'0','0','admin',sysdate(),'项目负责人查看项目全局并维护计划'
where not exists(select 1 from sys_role where role_key='project_owner' and del_flag='0');

update sys_role set role_name='项目负责人',role_sort=7,remark='项目负责人查看项目全局并维护计划'
where role_key='project_owner' and del_flag='0';

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4007,'我的安排',4000,3,'work-schedule','business/work/index','','BusinessWorkSchedule',1,0,'C','0','0',
 'business:project:work:view','date','admin',sysdate(),'普通员工查看本人今日、本周和本月工作安排','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),
  icon=values(icon),remark=values(remark);

update sys_menu set order_num=4 where menu_id=4004;
update sys_menu set order_num=5 where menu_id=4005;
update sys_menu set order_num=6 where menu_id=4006;

-- 项目成员保留个人安排、完成量填报和本人一次性任务进度权限，不显示项目管理页面。
delete rm from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id and r.role_key='project_user' and r.del_flag='0'
where rm.menu_id in(4002,4003,4012,4013,4015);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4000,4007,4014,4051)
where r.role_key='project_user' and r.del_flag='0';

-- 项目负责人拥有项目中心和负责人工作台；同时保留项目参与角色以使用“我的安排”。
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
  on m.menu_id in(4000,4002,4003,4012,4013,4014,4015,4051)
where r.role_key='project_owner' and r.del_flag='0';

insert ignore into sys_user_role(user_id,role_id)
select distinct p.main_owner_user_id,r.role_id
from biz_project p join sys_user u on u.user_id=p.main_owner_user_id and u.del_flag='0'
join sys_role r on r.role_key='project_owner' and r.del_flag='0'
where p.del_flag='0'
  and not exists(
    select 1 from sys_user_role ur2 join sys_role r2 on r2.role_id=ur2.role_id
    where ur2.user_id=p.main_owner_user_id and r2.role_key in('admin','company_owner') and r2.del_flag='0'
  );
