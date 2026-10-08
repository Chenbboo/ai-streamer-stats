-- 系统菜单越南语字段。可重复执行，不删除现有菜单。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='sys_menu' and column_name='menu_name_vi') = 0,
  "alter table sys_menu add column menu_name_vi varchar(64) default '' comment '越南语菜单名称' after remark",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
