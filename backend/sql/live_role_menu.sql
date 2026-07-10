-- ----------------------------
-- AI 主播直播数据统计:角色 + 菜单初始化脚本
-- 在 ry-vue 库执行。脚本可重复执行(先删后插)。
-- ----------------------------

-- ========== 清理旧数据(便于重复执行) ==========
delete from sys_role_menu where role_id in (3, 4, 5);
delete from sys_role where role_id in (3, 4, 5);
delete from sys_menu where menu_id between 2000 and 2099;

-- ========== 菜单 ==========
-- 一级目录:直播数据管理
insert into sys_menu values('2000', '直播数据管理', '0', '4', 'live', null, '', '', 1, 0, 'M', '0', '0', '', 'chart', 'admin', sysdate(), '', null, 'AI主播直播数据统计');

-- 二级菜单:图片上传
insert into sys_menu values('2001', '图片上传', '2000', '1', 'upload', 'live/upload/index', '', '', 1, 0, 'C', '0', '0', 'live:upload:list', 'upload', 'admin', sysdate(), '', null, '直播数据截图上传');
-- 二级菜单:识别校正
insert into sys_menu values('2002', '识别校正', '2000', '2', 'review', 'live/review/index', '', '', 1, 0, 'C', '0', '0', 'live:review:list', 'edit', 'admin', sysdate(), '', null, 'AI识别结果人工校正');
-- 二级菜单:数据统计
insert into sys_menu values('2003', '数据统计', '2000', '3', 'stats', 'live/stats/index', '', '', 1, 0, 'C', '0', '0', 'live:stats:list', 'dashboard', 'admin', sysdate(), '', null, '直播数据统计看板');

-- 按钮权限:图片上传
insert into sys_menu values('2011', '上传图片', '2001', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'live:upload:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '删除图片', '2001', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'live:upload:remove', '#', 'admin', sysdate(), '', null, '');
-- 按钮权限:识别校正
insert into sys_menu values('2021', '校正数据', '2002', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'live:review:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2022', '确认入库', '2002', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'live:review:confirm','#', 'admin', sysdate(), '', null, '');
-- 按钮权限:数据统计
insert into sys_menu values('2031', '导出统计', '2003', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'live:stats:export',  '#', 'admin', sysdate(), '', null, '');

-- ========== 角色 ==========
insert into sys_role values('3', '主播',       'streamer',   3, 5, 1, 1, '0', '0', 'admin', sysdate(), '', null, '只能上传截图、查看自己的统计');
insert into sys_role values('4', '运营',       'operator',   4, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '上传、校正、查看全部统计');
insert into sys_role values('5', '直播管理员', 'live_admin', 5, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '直播数据模块全部权限');

-- ========== 角色-菜单绑定 ==========
-- 主播:目录 + 图片上传(含上传按钮) + 数据统计(仅查看,数据范围为仅本人)
insert into sys_role_menu values ('3', '2000');
insert into sys_role_menu values ('3', '2001');
insert into sys_role_menu values ('3', '2011');
insert into sys_role_menu values ('3', '2003');

-- 运营:全部菜单,除删除图片
insert into sys_role_menu values ('4', '2000');
insert into sys_role_menu values ('4', '2001');
insert into sys_role_menu values ('4', '2011');
insert into sys_role_menu values ('4', '2002');
insert into sys_role_menu values ('4', '2021');
insert into sys_role_menu values ('4', '2022');
insert into sys_role_menu values ('4', '2031');
insert into sys_role_menu values ('4', '2003');

-- 直播管理员:全部
insert into sys_role_menu values ('5', '2000');
insert into sys_role_menu values ('5', '2001');
insert into sys_role_menu values ('5', '2011');
insert into sys_role_menu values ('5', '2012');
insert into sys_role_menu values ('5', '2002');
insert into sys_role_menu values ('5', '2021');
insert into sys_role_menu values ('5', '2022');
insert into sys_role_menu values ('5', '2003');
insert into sys_role_menu values ('5', '2031');
