-- 将老板 AI 从老板工作台拆分为独立页面，沿用老板查看权限与审计链路。

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4008,'老板 AI 助理',4000,2,'boss-ai','business/ai/index','','BusinessBossAi',1,0,'C','0','0',
 'business:boss:view','chat','admin',sysdate(),'DeepSeek 驱动的老板经营助理独立页面','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),icon=values(icon),
  visible=values(visible),status=values(status),remark=values(remark),menu_name_vi=values(menu_name_vi);

update sys_menu set order_num=3 where menu_id=4002;
update sys_menu set order_num=4 where menu_id=4004;
update sys_menu set order_num=5 where menu_id=4005;
update sys_menu set order_num=6 where menu_id=4006;

insert ignore into sys_role_menu(role_id,menu_id)
select role_id,4008 from sys_role where role_key='company_owner' and del_flag='0';
