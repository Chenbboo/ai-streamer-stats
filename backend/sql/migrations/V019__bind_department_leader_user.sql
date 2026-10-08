-- 部门负责人绑定真实系统账号；姓名、联系电话和邮箱保留为展示快照并由后端统一同步。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='sys_dept' and column_name='leader_user_id')=0,
  "alter table sys_dept add column leader_user_id bigint null comment '负责人账号ID' after order_num",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics
   where table_schema=database() and table_name='sys_dept' and index_name='idx_sys_dept_leader_user')=0,
  'alter table sys_dept add index idx_sys_dept_leader_user (leader_user_id)',
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 仅在负责人姓名能唯一匹配一个有效账号时回填，避免同名人员被错误绑定。
update sys_dept d
join (
  select nick_name,min(user_id) user_id
  from sys_user
  where del_flag='0' and nick_name is not null and trim(nick_name)<>''
  group by nick_name
  having count(*)=1
) matched on matched.nick_name=d.leader
join sys_user u on u.user_id=matched.user_id
set d.leader_user_id=u.user_id,
    d.leader=coalesce(nullif(u.nick_name,''),u.user_name),
    d.phone=coalesce(u.phonenumber,''),
    d.email=coalesce(u.email,'')
where d.leader_user_id is null and d.del_flag='0' and d.leader is not null and trim(d.leader)<>'';
