-- 主项目在新增子项目时正式分配额度。
-- 独立表避免继续扩张已接近 MySQL 单行上限的立项主表；拨款不另行生成支出。

create table if not exists biz_project_subproject_funding (
  proposal_id bigint not null comment '子项目立项申请ID',
  parent_project_id bigint not null comment '主项目ID',
  amount decimal(20,2) not null comment '分配给子项目的额度',
  reason varchar(500) not null comment '拨款用途和额度依据',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (proposal_id),
  key idx_subproject_funding_parent (parent_project_id)
) engine=InnoDB default charset=utf8mb4 comment='主项目子项目拨款';
