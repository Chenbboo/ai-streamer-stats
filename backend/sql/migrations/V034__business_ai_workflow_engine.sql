-- AI 持久化工作流：业务流程状态和结构化草稿以数据库为准，Redis 仅作缓存。
-- 同一会话同一时间只允许一个活动工作流；刷新页面、Redis 清空或后端重启后仍可继续。

create table if not exists biz_ai_workflow_instance (
  workflow_id bigint not null auto_increment comment '工作流实例ID',
  conversation_id bigint not null comment '所属AI会话',
  user_id bigint not null comment '所属用户',
  role_code varchar(32) not null comment '角色场景',
  workflow_code varchar(64) not null comment 'CREATE_PROJECT等稳定流程编码',
  workflow_status varchar(24) not null comment 'COLLECTING/READY/WAITING_CONFIRMATION/COMPLETED/CANCELED/EXPIRED',
  current_step varchar(64) null comment '当前业务步骤',
  draft_json longtext null comment '已收集的结构化字段',
  missing_fields_json longtext null comment '仍缺少的字段',
  bound_entities_json longtext null comment '已绑定的稳定实体ID',
  version_no int not null default 1 comment '乐观锁版本',
  action_request_id bigint null comment '生成的待确认动作',
  expire_time datetime null,
  create_time datetime not null default current_timestamp,
  update_time datetime null,
  completed_time datetime null,
  primary key (workflow_id),
  key idx_biz_ai_workflow_active (conversation_id,user_id,workflow_status,workflow_id),
  key idx_biz_ai_workflow_user (user_id,workflow_code,create_time)
) engine=InnoDB default charset=utf8mb4 comment='AI持久化工作流实例';

create table if not exists biz_ai_workflow_event (
  workflow_event_id bigint not null auto_increment,
  workflow_id bigint not null,
  conversation_id bigint not null,
  user_id bigint not null,
  event_type varchar(64) not null comment 'STARTED/FIELDS_MERGED/WAITING_CONFIRMATION/COMPLETED/CANCELED',
  before_json longtext null,
  after_json longtext null,
  message_id bigint null,
  create_time datetime not null default current_timestamp,
  primary key (workflow_event_id),
  key idx_biz_ai_workflow_event (workflow_id,workflow_event_id),
  key idx_biz_ai_workflow_event_conversation (conversation_id,workflow_event_id)
) engine=InnoDB default charset=utf8mb4 comment='AI工作流事件';
