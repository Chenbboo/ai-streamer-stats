-- 老板在人员管理中为普通员工设置个人目录权限。
-- 表为空时仍完全继承现有角色权限；迁移本身不改变任何人员当前权限。

create table if not exists biz_staff_menu_permission (
  user_id bigint not null comment '员工用户ID',
  menu_id bigint not null comment '菜单或操作ID',
  access_level varchar(16) not null comment 'HIDDEN/READ/MAINTAIN',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime comment '更新时间',
  primary key (user_id, menu_id),
  key idx_staff_menu_permission_menu (menu_id),
  constraint chk_staff_menu_access_level check (access_level in ('HIDDEN','READ','MAINTAIN'))
) engine=InnoDB comment='人员个人目录权限快照';
