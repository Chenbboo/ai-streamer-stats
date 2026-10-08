-- P4 飞书只读来源层；迁移不启用连接、不更改本地假勤、不触碰费用/投入/工资。
-- 备份并确认 database() 后按迁移顺序执行。凭据仅使用服务端环境变量。
create table if not exists biz_feishu_identity_scope (
 tenant_key varchar(128) collate utf8mb4_bin not null primary key
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_connection (
 connection_id bigint not null auto_increment primary key, company_dept_id bigint not null,
 tenant_key varchar(128) not null, source_timezone varchar(64) not null,
 state varchar(16) not null default 'PARALLEL', version int not null default 1,
 effective_date date null, last_complete_at datetime null, last_complete_run_id bigint null,
 running_run_id bigint null, lease_until datetime null, created_by bigint not null, created_at datetime not null,
 unique key uk_feishu_company(company_dept_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_mapping (
 mapping_id bigint not null auto_increment primary key, connection_id bigint not null,
 user_id bigint not null, external_user_id varchar(128) collate utf8mb4_bin not null,
 identity_type varchar(24) not null default 'employee_id', effective_from date not null, effective_to date null,
 status varchar(16) not null default 'CONFIRMED', created_by bigint not null, created_at datetime not null,
 retired_by bigint null, retired_at datetime null,
 key ix_feishu_external(connection_id,external_user_id,effective_from), key ix_feishu_local(connection_id,user_id,effective_from)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_sync_run (
 run_id bigint not null auto_increment primary key, connection_id bigint not null, mapping_version int not null,
 window_start date not null, window_end date not null, status varchar(16) not null,
 started_at datetime not null, finished_at datetime null, started_by bigint not null,
 received_count int not null default 0, rejected_count int not null default 0,
 completed_chunks int not null default 0, expected_chunks int not null default 0, error_code varchar(96) null,
 key ix_feishu_run(connection_id,run_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_sync_chunk (
 chunk_id bigint not null auto_increment primary key, run_id bigint not null, resource_type varchar(16) not null,
 business_date date not null, chunk_no int not null, scope_hash char(64) not null, status varchar(16) not null,
 received_count int not null default 0, error_code varchar(96) null, completed_at datetime not null,
 unique key uk_feishu_chunk(run_id,resource_type,business_date,chunk_no)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_observation (
 observation_id bigint not null auto_increment primary key, connection_id bigint not null, mapping_id bigint not null,
 user_id bigint not null, source_key char(64) collate utf8mb4_bin not null comment 'SHA256 of provider source identity', source_revision int not null,
 fingerprint char(64) not null, business_date date not null, source_timezone varchar(64) not null,
 kind varchar(24) not null, normalized_status varchar(24) not null, source_status varchar(64) not null, quality varchar(16) not null,
 source_duration_seconds bigint null, intervals_json text null, details_json text null, sync_run_id bigint not null,
 received_at datetime not null, last_seen_at datetime not null, last_seen_run_id bigint not null,
 is_current tinyint not null default 1, adapter_version varchar(64) not null,
 unique key uk_feishu_revision(connection_id,source_key,source_revision),
 key ix_feishu_current(connection_id,source_key,is_current), key ix_feishu_person(user_id,business_date),
 key ix_feishu_mapping(mapping_id,business_date)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_issue (
 issue_id bigint not null auto_increment primary key, connection_id bigint not null, run_id bigint null,
 observation_id bigint null, external_id_hash char(64) null, issue_code varchar(96) not null,
 status varchar(16) not null default 'OPEN', resolution varchar(1000) null, resolved_by bigint null,
 created_at datetime not null, resolved_at datetime null, key ix_feishu_issue(connection_id,status)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_validation (
 validation_id bigint not null auto_increment primary key, connection_id bigint not null, connection_version int not null,
 case_type varchar(24) not null, observation_id bigint null, issue_id bigint null, run_id bigint not null,
 expected_result varchar(1000) not null, source_evidence varchar(1000) not null, local_result varchar(1000) not null,
 passed tinyint not null, reason varchar(1000) not null, validated_by bigint not null, validated_at datetime not null,
 key ix_feishu_validation(connection_id,connection_version,case_type)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_audit (
 audit_id bigint not null auto_increment primary key, connection_id bigint not null, action_type varchar(32) not null,
 actor_id bigint not null, reference_id bigint null, reason varchar(1000) null, created_at datetime not null,
 key ix_feishu_audit(connection_id,audit_id)
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_feishu_reader_scope (
 company_dept_id bigint not null,user_id bigint not null,enabled tinyint not null default 1,
 granted_by bigint not null,updated_at datetime not null,primary key(company_dept_id,user_id)
) engine=InnoDB default charset=utf8mb4;
-- 页面菜单及角色分组由 V067 统一处理；不向技术角色自动授予业务假勤查看/切换权限。
