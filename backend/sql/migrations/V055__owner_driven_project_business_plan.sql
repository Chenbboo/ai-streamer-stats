-- 负责人驱动项目：新立项由负责人完成经营测算后直接启动；历史审批、项目和财务记录不改写。

set @proposal_plan_columns_sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='revenue_model')=0,
  "alter table biz_project_proposal
    add column revenue_model varchar(32) null comment '收入方式' after no_budget,
    add column estimated_revenue decimal(20,2) not null default 0 comment '基准预计收入',
    add column estimated_external_cost decimal(20,2) not null default 0 comment '预计外部支出',
    add column estimated_personnel_cost decimal(20,2) not null default 0 comment '预计人员成本',
    add column estimated_bonus_cost decimal(20,2) not null default 0 comment '预计项目奖金',
    add column estimated_tax_cost decimal(20,2) not null default 0 comment '预计税费平台费',
    add column contingency_cost decimal(20,2) not null default 0 comment '风险准备金',
    add column estimated_total_cost decimal(20,2) not null default 0 comment '预计总成本',
    add column expected_profit decimal(20,2) not null default 0 comment '预计利润',
    add column expected_margin decimal(9,4) null comment '预计利润率百分比',
    add column break_even_revenue decimal(20,2) null comment '盈亏平衡收入',
    add column peak_cash_need decimal(20,2) null comment '最大资金占用',
    add column planned_headcount int not null default 1 comment '计划人数',
    add column funding_plan varchar(1000) null comment '资金与回款安排',
    add column key_assumptions varchar(2000) null comment '关键假设',
    add column risk_summary varchar(2000) null comment '主要风险',
    add column stop_loss_rule varchar(1000) null comment '止损条件'",
  'select 1');
prepare proposal_plan_columns_stmt from @proposal_plan_columns_sql;
execute proposal_plan_columns_stmt;
deallocate prepare proposal_plan_columns_stmt;

create table if not exists biz_project_proposal_revenue (
  revenue_line_id bigint not null auto_increment,
  proposal_id bigint not null,
  scenario varchar(16) not null default 'BASE' comment 'CONSERVATIVE/BASE/OPTIMISTIC',
  revenue_type varchar(32) not null,
  item_name varchar(160) not null,
  unit_price decimal(20,4) null,
  quantity decimal(20,4) null,
  conversion_rate decimal(9,4) null,
  expected_amount decimal(20,2) not null,
  expected_date date null,
  assumption_text varchar(500) null,
  sort_order int not null default 1,
  create_time datetime not null default current_timestamp,
  primary key (revenue_line_id),
  key idx_biz_proposal_revenue (proposal_id,scenario,sort_order)
) engine=InnoDB default charset=utf8mb4 comment='立项收入测算明细';

create table if not exists biz_project_proposal_expense (
  expense_line_id bigint not null auto_increment,
  proposal_id bigint not null,
  expense_category varchar(32) not null,
  item_name varchar(160) not null,
  purpose varchar(500) not null,
  counterparty varchar(160) null,
  amount decimal(20,2) not null,
  occur_date date null,
  expense_type varchar(16) not null default 'ONE_TIME' comment 'ONE_TIME/RECURRING',
  has_quotation char(1) not null default '0',
  sort_order int not null default 1,
  create_time datetime not null default current_timestamp,
  primary key (expense_line_id),
  key idx_biz_proposal_expense (proposal_id,sort_order)
) engine=InnoDB default charset=utf8mb4 comment='立项支出计划明细';

create table if not exists biz_project_proposal_staffing (
  staffing_line_id bigint not null auto_increment,
  proposal_id bigint not null,
  role_name varchar(80) not null,
  headcount int not null,
  allocation_percent decimal(7,2) not null default 100,
  plan_start_date date null,
  plan_end_date date null,
  person_months decimal(12,2) null,
  estimated_cost decimal(20,2) not null,
  note varchar(500) null,
  sort_order int not null default 1,
  create_time datetime not null default current_timestamp,
  primary key (staffing_line_id),
  key idx_biz_proposal_staffing (proposal_id,sort_order)
) engine=InnoDB default charset=utf8mb4 comment='立项人员投入测算';

create table if not exists biz_project_proposal_target (
  target_line_id bigint not null auto_increment,
  proposal_id bigint not null,
  target_type varchar(24) not null comment 'FINANCIAL/RESULT/SCHEDULE/QUALITY/VALUE',
  target_name varchar(160) not null,
  target_value decimal(20,4) not null,
  unit varchar(32) not null,
  due_date date null,
  acceptance_evidence varchar(500) not null,
  weight decimal(7,2) null,
  sort_order int not null default 1,
  create_time datetime not null default current_timestamp,
  primary key (target_line_id),
  key idx_biz_proposal_target (proposal_id,sort_order)
) engine=InnoDB default charset=utf8mb4 comment='立项目标与验收指标';

-- 项目负责人可以设置目标、发布方案并完成结算；服务层仍按项目负责人关系校验。
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,4072 from sys_role r where r.role_key='project_owner' and r.del_flag='0';

update sys_role set remark='项目负责人自主完成计划、执行、核算、验收与结项'
where role_key='project_owner' and del_flag='0';
update sys_role set remark='查看公司经营全局，并对异常项目保留干预和修改能力'
where role_key='company_owner' and del_flag='0';
update sys_menu set menu_name='立项申请',remark='负责人完成收支、人员、目标和风险测算并自主启动'
where menu_id=4009;
update sys_menu set menu_name='启动项目',remark='负责人确认项目测算后直接启动，不进入老板审批队列'
where menu_id=4064;
update sys_menu set menu_name='历史立项处理',remark='仅处理升级前遗留的待审批立项'
where menu_id=4065;
