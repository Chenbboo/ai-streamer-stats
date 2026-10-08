-- 老板经营配置：项目 KPI、预算调整历史、人员内部核算成本和项目分摊。
-- 本迁移只新增表，不改动直播、珠宝和原账号业务表。

create table if not exists biz_project_kpi (
  kpi_id bigint not null auto_increment comment 'KPI版本ID',
  project_id bigint not null comment '项目ID',
  kpi_code varchar(64) not null comment '稳定指标编码',
  kpi_name varchar(160) not null comment '指标名称',
  metric_type varchar(20) not null default 'COUNT' comment '指标类型',
  unit varchar(32) default null comment '单位',
  precision_scale int not null default 2 comment '小数位数',
  period_type varchar(20) not null default 'PROJECT' comment '统计周期',
  target_value decimal(20,4) not null comment '目标值',
  minimum_value decimal(20,4) default null comment '最低值',
  warning_value decimal(20,4) default null comment '预警值',
  challenge_value decimal(20,4) default null comment '挑战值',
  actual_value decimal(20,4) default null comment '当前实际值',
  weight decimal(9,4) not null default 0 comment '权重百分比',
  direction varchar(20) not null default 'HIGHER_BETTER' comment '考核方向',
  aggregate_type varchar(20) not null default 'SUM' comment '聚合方式',
  source_type varchar(20) not null default 'MANUAL' comment '数据来源',
  owner_user_id bigint default null comment '指标负责人',
  owner_name varchar(100) default null comment '指标负责人快照',
  effective_from date not null comment '生效日期',
  effective_to date default null comment '失效日期',
  target_version int not null default 1 comment '目标版本',
  status varchar(16) not null default 'CURRENT' comment 'CURRENT/RETIRED',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  remark varchar(500) default null comment '调整说明',
  primary key (kpi_id),
  unique key uk_project_kpi_version (project_id,kpi_code,target_version),
  key idx_project_kpi_current (project_id,status)
) engine=InnoDB default charset=utf8mb4 comment='项目KPI版本';

create table if not exists biz_project_budget_history (
  history_id bigint not null auto_increment comment '记录ID',
  project_id bigint not null comment '项目ID',
  from_amount decimal(20,2) default null comment '调整前金额',
  to_amount decimal(20,2) not null comment '调整后金额',
  currency char(3) not null comment '币种',
  budget_version int not null comment '预算版本',
  reason varchar(500) not null comment '调整原因',
  operator_user_id bigint not null comment '操作人ID',
  operator_name varchar(100) not null comment '操作人',
  effective_time datetime not null default current_timestamp comment '生效时间',
  primary key (history_id),
  key idx_budget_project (project_id,effective_time)
) engine=InnoDB default charset=utf8mb4 comment='项目预算调整历史';

create table if not exists biz_staff_cost_policy (
  policy_id bigint not null auto_increment comment '成本政策版本ID',
  user_id bigint not null comment '人员ID',
  cost_mode varchar(24) not null comment 'DAILY/HOURLY/MONTHLY/FIXED_PROJECT/FIXED_TASK/VARIABLE',
  unit_cost decimal(20,4) not null comment '内部核算单价',
  currency char(3) not null default 'CNY' comment '币种',
  effective_from date not null comment '生效日期',
  effective_to date default null comment '失效日期',
  policy_version int not null comment '政策版本',
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/VOID',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime not null default current_timestamp comment '创建时间',
  remark varchar(500) default null comment '政策说明',
  primary key (policy_id),
  unique key uk_staff_cost_version (user_id,policy_version),
  key idx_staff_cost_effective (user_id,status,effective_from,effective_to)
) engine=InnoDB default charset=utf8mb4 comment='人员内部核算成本政策';

create table if not exists biz_project_staff_allocation (
  allocation_id bigint not null auto_increment comment '分摊ID',
  project_id bigint not null comment '项目ID',
  user_id bigint not null comment '人员ID',
  user_name varchar(100) not null comment '人员名称快照',
  allocation_mode varchar(24) not null default 'PERCENTAGE' comment 'PERCENTAGE/HOURS/ATTENDANCE/FIXED_DAILY/PER_TASK',
  allocation_value decimal(20,4) not null comment '分摊参数',
  effective_from date not null comment '生效日期',
  effective_to date default null comment '失效日期',
  cost_policy_id bigint not null comment '引用的成本政策版本',
  exception_allowed char(1) not null default '0' comment '是否允许比例超100%',
  exception_reason varchar(500) default null comment '超额说明',
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE/VOID',
  version int not null default 0 comment '乐观锁版本',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  remark varchar(500) default null comment '备注',
  primary key (allocation_id),
  key idx_allocation_project (project_id,status,effective_from,effective_to),
  key idx_allocation_staff (user_id,status,effective_from,effective_to)
) engine=InnoDB default charset=utf8mb4 comment='项目人员内部成本分摊';

-- 为升级前已经填写预算的项目建立v1基线，避免首次调整时丢失来源。
insert into biz_project_budget_history(project_id,from_amount,to_amount,currency,budget_version,reason,
  operator_user_id,operator_name,effective_time)
select p.project_id,null,p.budget_limit,p.base_currency,1,'系统升级时建立预算基线',
  p.initiator_user_id,coalesce(p.initiator_name,p.create_by),coalesce(p.create_time,sysdate())
from biz_project p
where p.del_flag='0' and p.budget_limit is not null
  and not exists(select 1 from biz_project_budget_history h where h.project_id=p.project_id);
