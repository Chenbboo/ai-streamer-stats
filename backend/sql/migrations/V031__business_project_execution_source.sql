-- 项目执行系统关联：项目中心只管理项目，直播系统继续作为执行层和权威数据源。
-- 关系按有效期保留历史，不修改任何 live_ 业务表。

create table if not exists biz_project_relation (
  relation_id bigint not null auto_increment comment '关系ID',
  project_id bigint not null comment '项目ID',
  source_domain varchar(32) not null comment '来源业务域，如 LIVE',
  source_type varchar(32) not null comment '来源范围类型',
  source_id bigint null comment '来源范围ID；全业务范围可为空',
  relation_type varchar(32) not null comment '关系类型',
  effective_from date not null comment '生效日期',
  effective_to date null comment '失效日期',
  status char(1) not null default '0' comment '0有效 1失效',
  active_key varchar(191) null comment '有效来源范围唯一键，保证整个直播业务只归属一个有效项目；失效后置空',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (relation_id),
  unique key uk_biz_project_relation_active (active_key),
  key idx_biz_project_relation_project (project_id,status,effective_from,effective_to),
  key idx_biz_project_relation_source (source_domain,source_type,source_id,status)
) engine=InnoDB default charset=utf8mb4 comment='项目与执行系统关系';
