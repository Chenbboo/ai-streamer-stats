-- 项目治理模式重构：管理强度与结项方式解耦，并增加真实阶段验收记录。
-- 可重复执行；旧 DELIVERY 迁移为 STANDARD + RESULT_ACCEPTANCE，不自动升级为重点监管。

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='close_method')=0,
  "alter table biz_project_proposal add column close_method varchar(24) not null default 'DIRECT' comment 'DIRECT/RESULT_ACCEPTANCE/STAGED_ACCEPTANCE' after management_mode",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='management_reason')=0,
  "alter table biz_project_proposal add column management_reason varchar(1000) null comment '管理模式选择理由' after close_method",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='acceptance_criteria')=0,
  "alter table biz_project_proposal add column acceptance_criteria varchar(2000) null comment '成果或阶段验收标准' after management_reason",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='close_method')=0,
  "alter table biz_project add column close_method varchar(24) not null default 'DIRECT' comment 'DIRECT/RESULT_ACCEPTANCE/STAGED_ACCEPTANCE' after management_mode",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='management_reason')=0,
  "alter table biz_project add column management_reason varchar(1000) null comment '管理模式选择理由' after close_method",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='acceptance_criteria')=0,
  "alter table biz_project add column acceptance_criteria varchar(2000) null comment '成果或阶段验收标准' after management_reason",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

update biz_project_proposal
set close_method=case when management_mode='DELIVERY' then 'RESULT_ACCEPTANCE' else coalesce(nullif(close_method,''),'DIRECT') end,
    management_mode=case management_mode when 'SIMPLE' then 'LIGHT' when 'DELIVERY' then 'STANDARD' else management_mode end;

update biz_project
set close_method=case when management_mode='DELIVERY' then 'RESULT_ACCEPTANCE' else coalesce(nullif(close_method,''),'DIRECT') end,
    management_mode=case management_mode when 'SIMPLE' then 'LIGHT' when 'DELIVERY' then 'STANDARD' else management_mode end;

-- 统一里程碑“进行中”状态，兼容阶段验收功能开发期间写入的旧值。
update biz_project_milestone set status='DOING' where status='IN_PROGRESS';

alter table biz_project_proposal modify column management_mode varchar(24) not null default 'STANDARD'
  comment 'LIGHT/STANDARD/KEY_CONTROL';
alter table biz_project modify column management_mode varchar(24) not null default 'STANDARD'
  comment 'LIGHT/STANDARD/KEY_CONTROL';

create table if not exists biz_project_stage_acceptance (
  stage_acceptance_id bigint not null auto_increment comment '阶段验收记录ID',
  project_id bigint not null comment '项目ID',
  milestone_id bigint not null comment '作为验收阶段的里程碑ID',
  submission_version int not null comment '该阶段第几次提交',
  result_summary varchar(2000) not null comment '阶段结果摘要',
  deliverables varchar(4000) not null comment '阶段成果说明',
  attachment_urls varchar(4000) null comment '成果附件URL',
  submitted_user_id bigint not null,
  submitted_user_name varchar(64) not null,
  submitted_time datetime not null,
  review_status varchar(16) not null default 'PENDING' comment 'PENDING/APPROVED/RETURNED',
  reviewed_user_id bigint null,
  reviewed_user_name varchar(64) null,
  review_comment varchar(2000) null,
  reviewed_time datetime null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (stage_acceptance_id),
  unique key uk_biz_stage_acceptance_version (project_id,milestone_id,submission_version),
  key idx_biz_stage_acceptance_review (project_id,review_status,submitted_time),
  key idx_biz_stage_acceptance_milestone (milestone_id,review_status)
) engine=InnoDB default charset=utf8mb4 comment='项目阶段验收提交与评审历史';
