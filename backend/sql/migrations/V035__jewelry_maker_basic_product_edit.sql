-- 制单员可直接修改已有商品的名称和图片，但不授予完整商品维护权限。
insert into sys_menu(
  menu_id,menu_name,parent_id,order_num,path,component,query,route_name,
  is_frame,is_cache,menu_type,visible,status,perms,icon,
  create_by,create_time,update_by,update_time,remark,menu_name_vi
) values (
  3118,'修改商品名称图片',3003,3,'#','','','',
  1,0,'F','0','0','jewelry:product:basic-edit','#',
  'admin',sysdate(),'',null,'仅允许修改已有商品名称和图片','Sửa tên và ảnh sản phẩm'
)
on duplicate key update
  menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  menu_type=values(menu_type),visible=values(visible),status=values(status),
  perms=values(perms),remark=values(remark),menu_name_vi=values(menu_name_vi);

-- 防止制单员意外获得可以修改类型、状态、费用等字段的完整编辑权限。
delete rm
from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id
join sys_menu m on m.menu_id=rm.menu_id
where r.role_key='jewelry_maker'
  and r.del_flag='0'
  and m.perms='jewelry:product:edit';

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r
join sys_menu m on m.perms='jewelry:product:basic-edit'
where r.role_key in ('jewelry_maker','jewelry_admin')
  and r.del_flag='0'
  and m.status='0';

delete rm
from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id
join sys_menu m on m.menu_id=rm.menu_id
where r.role_key='jewelry_reviewer'
  and r.del_flag='0'
  and m.perms='jewelry:product:basic-edit';
