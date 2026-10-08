-- 直播角色、菜单、权限和中越双语名称。可重复执行，不删除已有授权。

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '主播','streamer',3,5,1,1,'0','0','admin',sysdate(),'只能上传和查看自己的直播数据'
where not exists(select 1 from sys_role where role_key='streamer' and del_flag='0');

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '运营','operator',4,2,1,1,'0','0','admin',sysdate(),'上传、校正并查看全部直播数据'
where not exists(select 1 from sys_role where role_key='operator' and del_flag='0');

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '直播管理员','live_admin',5,1,1,1,'0','0','admin',sysdate(),'直播数据模块全部权限'
where not exists(select 1 from sys_role where role_key='live_admin' and del_flag='0');

update sys_role set role_name='主播' where role_key='streamer' and del_flag='0';
update sys_role set role_name='运营' where role_key='operator' and del_flag='0';
update sys_role set role_name='直播管理员' where role_key='live_admin' and del_flag='0';
update sys_role set role_name='珠宝ERP制单员' where role_key='jewelry_maker' and del_flag='0';
update sys_role set role_name='珠宝ERP审核员' where role_key='jewelry_reviewer' and del_flag='0';
update sys_role set role_name='珠宝ERP管理员' where role_key='jewelry_admin' and del_flag='0';

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(2000,'直播数据管理',0,4,'live',null,'','',1,0,'M','0','0','','chart','admin',sysdate(),'直播数据业务目录','Quản lý dữ liệu livestream'),
(2001,'图片上传',2000,1,'upload','live/upload/index','','',1,0,'C','0','0','live:upload:list','upload','admin',sysdate(),'直播资料上传','Tải ảnh lên'),
(2002,'识别校正',2000,2,'review','live/review/index','','',1,0,'C','0','0','live:review:list','edit','admin',sysdate(),'AI识别与人工校正','Kiểm duyệt nhận dạng'),
(2003,'数据统计',2000,3,'stats','live/stats/index','','',1,0,'C','0','0','live:stats:list','dashboard','admin',sysdate(),'直播运营统计','Thống kê dữ liệu'),
(2004,'主播管理',2000,4,'streamer','live/streamer/index','','',1,0,'C','0','0','live:streamer:list','peoples','admin',sysdate(),'主播账号与档案','Quản lý streamer'),
(2005,'主播数据列表',2000,5,'streamer-data','live/streamerData/index','','',1,0,'C','0','0','live:stats:list','list','admin',sysdate(),'主播每日与区间数据','Danh sách dữ liệu streamer'),
(2044,'KPI配置',2000,6,'kpi','live/kpi/index','','',1,0,'C','0','0','live:stats:list','edit','admin',sysdate(),'直播KPI配置','Cấu hình KPI'),
(2011,'上传图片',2001,1,'#','','','',1,0,'F','0','0','live:upload:add','#','admin',sysdate(),'','Tải dữ liệu lên'),
(2012,'删除图片',2001,2,'#','','','',1,0,'F','0','0','live:upload:remove','#','admin',sysdate(),'','Xóa dữ liệu'),
(2021,'校正数据',2002,1,'#','','','',1,0,'F','0','0','live:review:edit','#','admin',sysdate(),'','Hiệu chỉnh dữ liệu'),
(2022,'确认入库',2002,2,'#','','','',1,0,'F','0','0','live:review:confirm','#','admin',sysdate(),'','Xác nhận ghi dữ liệu'),
(2023,'合并客户',2002,3,'#','','','',1,0,'F','0','0','live:review:edit','#','admin',sysdate(),'','Hợp nhất khách hàng'),
(2031,'导出统计',2003,1,'#','','','',1,0,'F','0','0','live:stats:export','#','admin',sysdate(),'','Xuất thống kê'),
(2032,'主播卡片详情',2003,2,'#','','','',1,0,'F','0','0','live:stats:list','#','admin',sysdate(),'','Chi tiết streamer'),
(2033,'中高级用户',2003,3,'#','','','',1,0,'F','0','0','live:stats:list','#','admin',sysdate(),'','Khách hàng giá trị cao'),
(2034,'新增打赏用户',2003,4,'#','','','',1,0,'F','0','0','live:stats:list','#','admin',sysdate(),'','Khách tặng quà mới'),
(2041,'新增主播',2004,1,'#','','','',1,0,'F','0','0','live:streamer:add','#','admin',sysdate(),'','Thêm streamer'),
(2042,'修改主播',2004,2,'#','','','',1,0,'F','0','0','live:streamer:edit','#','admin',sysdate(),'','Sửa streamer'),
(2043,'删除主播',2004,3,'#','','','',1,0,'F','0','0','live:streamer:remove','#','admin',sysdate(),'','Ngừng streamer'),
(2045,'KPI查询',2044,1,'#','','','',1,0,'F','0','0','live:stats:list','#','admin',sysdate(),'','Xem KPI'),
(2046,'KPI新增',2044,2,'#','','','',1,0,'F','0','0','live:stats:add','#','admin',sysdate(),'','Thêm KPI'),
(2047,'KPI修改',2044,3,'#','','','',1,0,'F','0','0','live:stats:edit','#','admin',sysdate(),'','Sửa KPI'),
(2048,'KPI删除',2044,4,'#','','','',1,0,'F','0','0','live:stats:remove','#','admin',sysdate(),'','Xóa KPI')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),perms=values(perms),icon=values(icon),
  remark=values(remark),menu_name_vi=values(menu_name_vi);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
where r.role_key='streamer' and r.del_flag='0'
  and m.menu_id in (2000,2001,2003,2005,2011,2032,2033,2034);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
where r.role_key='operator' and r.del_flag='0'
  and m.menu_id in (2000,2001,2002,2003,2005,2011,2021,2022,2023,2031,2032,2033,2034,2044,2045);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
where r.role_key='live_admin' and r.del_flag='0' and m.menu_id between 2000 and 2048;
