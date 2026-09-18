-- 商品资料维护无需审批；删除由接口校验零库存且从未使用。
insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,
  is_frame,is_cache,menu_type,visible,status,perms,icon,
  create_by,create_time,update_by,update_time,remark,menu_name_vi)
values(3122,'删除商品',3003,4,'#','','','',1,0,'F','0','0','jewelry:product:remove','#',
  'admin',sysdate(),'',null,'仅允许删除零库存且从未使用的商品','Xóa sản phẩm')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),
  order_num=values(order_num),menu_type=values(menu_type),visible=values(visible),
  status=values(status),perms=values(perms),remark=values(remark),menu_name_vi=values(menu_name_vi);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r
join sys_menu m on m.perms in ('jewelry:product:edit','jewelry:product:remove')
where r.role_key in ('jewelry_maker','jewelry_admin') and r.del_flag='0' and m.status='0';

-- 审核员仍只读商品档案。
delete rm from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id
join sys_menu m on m.menu_id=rm.menu_id
where r.role_key='jewelry_reviewer' and r.del_flag='0'
  and m.perms in ('jewelry:product:edit','jewelry:product:basic-edit','jewelry:product:remove');
