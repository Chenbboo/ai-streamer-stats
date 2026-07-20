-- 主播数据列表菜单，可重复执行。
delete from sys_role_menu where menu_id = 2005;
delete from sys_menu where menu_id = 2005;

insert into sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark, menu_name_vi
) values(
  2005, '主播数据列表', 2000, 4, 'streamer-data', 'live/streamerData/index', '', '',
  1, 0, 'C', '0', '0', 'live:stats:list', 'list',
  'admin', sysdate(), '', null, '按日期查看主播每日及月度累计数据', 'Danh sách dữ liệu streamer'
);

-- 继承“数据统计”的角色可见范围。
insert into sys_role_menu(role_id, menu_id)
select role_id, 2005 from (
  select distinct role_id from sys_role_menu where menu_id = 2003
) source_roles;
