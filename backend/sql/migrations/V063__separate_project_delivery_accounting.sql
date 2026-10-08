-- P1：交付与核算分离。先备份并确认 database()，仅在停写维护窗口执行。
-- 历史项目保持 LEGACY_V1；不重开历史账，不改金额/审批/账号/归属。
-- 新项目由应用显式写入 SEPARATED_V1。可重复执行，中断后可续跑。

set @p1_sql=(select if(count(*)=0,'alter table biz_project add column delivery_policy_version varchar(24) not null default ''LEGACY_V1'' comment ''交付规则版本''','select 1')
  from information_schema.columns where table_schema=database()
  and table_name='biz_project' and column_name='delivery_policy_version');
prepare p1_stmt from @p1_sql;
execute p1_stmt;
deallocate prepare p1_stmt;

set @p1_sql=(select if(count(*)=0,'alter table biz_project add column accounting_state varchar(16) null default null comment ''项目核算状态''','select 1')
  from information_schema.columns where table_schema=database()
  and table_name='biz_project' and column_name='accounting_state');
prepare p1_stmt from @p1_sql;
execute p1_stmt;
deallocate prepare p1_stmt;

set @p1_sql=(select if(count(*)=0,'alter table biz_project add column settlement_policy_version varchar(24) not null default ''LEGACY_COMPAT'' comment ''结算规则版本''','select 1')
  from information_schema.columns where table_schema=database()
  and table_name='biz_project' and column_name='settlement_policy_version');
prepare p1_stmt from @p1_sql;
execute p1_stmt;
deallocate prepare p1_stmt;

set @p1_sql=(select if(count(*)=0,'alter table biz_project add column cost_policy_version varchar(24) not null default ''PERCENTAGE_V1'' comment ''成本规则版本''','select 1')
  from information_schema.columns where table_schema=database()
  and table_name='biz_project' and column_name='cost_policy_version');
prepare p1_stmt from @p1_sql;
execute p1_stmt;
deallocate prepare p1_stmt;

update biz_project set accounting_state=case when status in ('CLOSED','CANCELED') then 'CLOSED' else 'OPEN' end
where accounting_state is null and delivery_policy_version='LEGACY_V1';
alter table biz_project modify accounting_state varchar(16) not null default 'OPEN' comment '项目核算状态';

-- 独立操作权限；只赋给既有公司负责人角色，实际项目归属还须服务层校验。
-- 动态分配菜单ID，避免覆盖用户已存在的菜单。
set @p1_close_menu=(select menu_id from sys_menu where perms='business:accounting:close' limit 1);
set @p1_parent=(select menu_id from sys_menu where perms='business:accounting:list' and menu_type='C' limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,
  is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
select '关闭项目核算',@p1_parent,9,'#','','','',1,0,'F','0','0','business:accounting:close','#',
  'admin',sysdate(),'项目交付结束、待办处理完毕后，由归属老板独立确认关闭核算','Đóng hạch toán dự án'
where @p1_close_menu is null and @p1_parent is not null;
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms='business:accounting:close'
where r.role_key='company_owner' and r.del_flag='0';
