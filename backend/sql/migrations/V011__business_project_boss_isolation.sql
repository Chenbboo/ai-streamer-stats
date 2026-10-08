-- 经营项目按立项老板隔离；脚本只新增归属字段并回填，可重复执行。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='biz_project'
     and column_name='initiator_user_id') = 0,
  "alter table biz_project add column initiator_user_id bigint null comment '立项老板账号ID' after baseline_status",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='biz_project'
     and column_name='initiator_name') = 0,
  "alter table biz_project add column initiator_name varchar(64) null comment '立项老板姓名快照' after initiator_user_id",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 历史项目按创建账号回填；找不到创建账号时归系统管理员，避免错误分配给任一老板。
update biz_project p
left join sys_user u on cast(u.user_name as binary)=cast(p.create_by as binary) and u.del_flag='0'
set p.initiator_user_id=coalesce(p.initiator_user_id,u.user_id,1),
    p.initiator_name=coalesce(nullif(p.initiator_name,''),nullif(u.nick_name,''),u.user_name,'系统管理员')
where p.initiator_user_id is null or p.initiator_name is null or p.initiator_name='';

alter table biz_project
  modify column initiator_user_id bigint not null comment '立项老板账号ID',
  modify column initiator_name varchar(64) not null comment '立项老板姓名快照';

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='biz_project'
     and index_name='idx_biz_project_initiator_status') = 0,
  'alter table biz_project add index idx_biz_project_initiator_status (initiator_user_id,status,plan_end_date)',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
