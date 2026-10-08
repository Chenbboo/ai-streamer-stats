-- 公司经营 AI 底座：会话、消息、运行、工具调用、待确认动作和审计。
-- AI 不直接修改业务表；未来的写操作必须先写入 action_request，再由当前登录人确认后调用既有业务服务。

create table if not exists biz_ai_conversation (
  conversation_id bigint not null auto_increment comment '会话ID',
  user_id bigint not null comment '会话所属用户',
  role_code varchar(32) not null comment '角色场景，如 BOSS',
  title varchar(120) not null comment '会话标题',
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/ARCHIVED',
  last_message_time datetime not null,
  create_time datetime not null default current_timestamp,
  update_time datetime null,
  primary key (conversation_id),
  key idx_biz_ai_conversation_user (user_id,role_code,status,last_message_time)
) engine=InnoDB default charset=utf8mb4 comment='AI会话';

create table if not exists biz_ai_message (
  message_id bigint not null auto_increment comment '消息ID',
  conversation_id bigint not null comment '会话ID',
  user_id bigint not null comment '消息归属用户',
  message_role varchar(16) not null comment 'USER/ASSISTANT/SYSTEM/TOOL',
  content longtext not null comment '消息正文',
  metadata_json longtext null comment '数据口径、来源和展示元数据',
  create_time datetime not null default current_timestamp,
  primary key (message_id),
  key idx_biz_ai_message_conversation (conversation_id,message_id),
  key idx_biz_ai_message_user (user_id,create_time)
) engine=InnoDB default charset=utf8mb4 comment='AI消息';

create table if not exists biz_ai_run (
  run_id bigint not null auto_increment comment '运行ID',
  trace_id varchar(64) not null comment '链路追踪ID',
  conversation_id bigint not null comment '会话ID',
  user_id bigint not null comment '执行用户',
  role_code varchar(32) not null comment '角色场景',
  request_message_id bigint not null comment '请求消息ID',
  response_message_id bigint null comment '响应消息ID',
  execution_mode varchar(32) not null comment 'SAFE_ROUTER/LLM_AGENT',
  status varchar(16) not null comment 'RUNNING/SUCCEEDED/FAILED',
  error_message varchar(1000) null,
  started_time datetime not null,
  finished_time datetime null,
  primary key (run_id),
  unique key uk_biz_ai_run_trace (trace_id),
  key idx_biz_ai_run_conversation (conversation_id,run_id),
  key idx_biz_ai_run_user (user_id,started_time)
) engine=InnoDB default charset=utf8mb4 comment='AI运行';

create table if not exists biz_ai_tool_call (
  tool_call_id bigint not null auto_increment comment '工具调用ID',
  run_id bigint not null comment '运行ID',
  conversation_id bigint not null comment '会话ID',
  user_id bigint not null comment '执行用户',
  tool_code varchar(64) not null comment '白名单工具编码',
  risk_level varchar(16) not null default 'READ_ONLY' comment 'READ_ONLY/CONFIRM_REQUIRED/PROHIBITED',
  input_json longtext null,
  output_json longtext null,
  status varchar(16) not null comment 'RUNNING/SUCCEEDED/FAILED',
  error_message varchar(1000) null,
  started_time datetime not null,
  finished_time datetime null,
  primary key (tool_call_id),
  key idx_biz_ai_tool_run (run_id,tool_call_id),
  key idx_biz_ai_tool_user (user_id,started_time)
) engine=InnoDB default charset=utf8mb4 comment='AI工具调用';

create table if not exists biz_ai_action_request (
  action_request_id bigint not null auto_increment comment '待确认动作ID',
  run_id bigint not null comment '来源运行ID',
  conversation_id bigint not null comment '来源会话ID',
  user_id bigint not null comment '发起用户',
  action_code varchar(64) not null comment '动作编码',
  risk_level varchar(16) not null default 'CONFIRM_REQUIRED',
  action_payload_json longtext not null comment '拟执行参数',
  confirmation_summary varchar(1000) not null comment '给用户确认的摘要',
  status varchar(16) not null default 'PENDING' comment 'PENDING/CONFIRMED/REJECTED/EXPIRED/EXECUTED/FAILED',
  confirmed_user_id bigint null,
  confirmed_time datetime null,
  executed_time datetime null,
  expire_time datetime null,
  result_json longtext null,
  create_time datetime not null default current_timestamp,
  update_time datetime null,
  primary key (action_request_id),
  key idx_biz_ai_action_user (user_id,status,create_time),
  key idx_biz_ai_action_run (run_id)
) engine=InnoDB default charset=utf8mb4 comment='AI待确认动作';

create table if not exists biz_ai_audit_log (
  audit_id bigint not null auto_increment comment '审计ID',
  trace_id varchar(64) not null comment '链路追踪ID',
  conversation_id bigint null,
  run_id bigint null,
  user_id bigint not null,
  user_name varchar(64) not null,
  role_code varchar(32) not null,
  event_type varchar(64) not null comment '事件类型',
  event_summary varchar(1000) not null,
  detail_json longtext null,
  create_time datetime not null default current_timestamp,
  primary key (audit_id),
  key idx_biz_ai_audit_trace (trace_id),
  key idx_biz_ai_audit_user (user_id,create_time),
  key idx_biz_ai_audit_run (run_id)
) engine=InnoDB default charset=utf8mb4 comment='AI审计日志';
