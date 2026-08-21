-- 项目级 KPI、综合阶梯奖金与立即计入项目经营成本。
-- 不创建个人 KPI、个人奖金或真实工资记录。
create table if not exists biz_project_kpi_plan (
  plan_id bigint not null auto_increment comment '方案ID',
  project_id bigint not null comment '项目ID',
  plan_version int not null comment '项目内方案版本',
  cycle_type varchar(16) not null comment 'MONTH/QUARTER/PROJECT',
  cycle_start date not null comment '考核开始日期',
  cycle_end date not null comment '考核结束日期',
  bonus_mode varchar(16) not null default 'LADDER' comment '第一阶段固定LADDER',
  currency char(3) not null default 'CNY' comment '第一阶段固定CNY',
  status varchar(16) not null default 'PUBLISHED' comment 'PUBLISHED/CLOSED',
  published_user_id bigint not null comment '发布人ID',
  published_user_name varchar(100) not null comment '发布人名称',
  published_time datetime not null default current_timestamp comment '发布时间',
  closed_time datetime default null comment '封账时间',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime not null default current_timestamp comment '创建时间',
  remark varchar(500) default null comment '方案说明',
  primary key(plan_id),
  unique key uk_project_kpi_plan_version(project_id,plan_version),
  key idx_project_kpi_plan_period(project_id,status,cycle_start,cycle_end)
) engine=InnoDB default charset=utf8mb4 comment='项目KPI发布方案';

create table if not exists biz_project_kpi_plan_item (
  item_id bigint not null auto_increment comment '快照明细ID',
  plan_id bigint not null comment '方案ID',
  kpi_id bigint not null comment '来源KPI版本ID',
  kpi_code varchar(64) not null comment '指标编码快照',
  kpi_name varchar(160) not null comment '指标名称快照',
  metric_type varchar(20) not null comment '指标类型快照',
  unit varchar(32) default null comment '单位快照',
  target_value decimal(20,4) not null comment '目标值快照',
  minimum_value decimal(20,4) default null comment '最低值快照',
  warning_value decimal(20,4) default null comment '预警值快照',
  challenge_value decimal(20,4) default null comment '挑战值快照',
  weight decimal(9,4) not null comment '权重快照',
  direction varchar(20) not null comment 'HIGHER_BETTER/LOWER_BETTER',
  aggregate_type varchar(20) not null comment '聚合方式快照',
  source_type varchar(20) not null comment '数据来源快照',
  sort_order int not null default 0 comment '显示顺序',
  primary key(item_id),
  unique key uk_kpi_plan_item_code(plan_id,kpi_code),
  key idx_kpi_plan_item(plan_id,sort_order,item_id)
) engine=InnoDB default charset=utf8mb4 comment='项目KPI目标发布快照';

create table if not exists biz_project_bonus_tier (
  tier_id bigint not null auto_increment comment '阶梯ID',
  plan_id bigint not null comment '方案ID',
  tier_name varchar(100) not null comment '阶梯名称',
  min_score decimal(9,2) not null comment '最低分，包含',
  max_score decimal(9,2) default null comment '最高分，不包含；空为无上限',
  bonus_amount decimal(20,2) not null comment '项目奖金人民币金额',
  sort_order int not null default 0 comment '顺序',
  primary key(tier_id),
  key idx_project_bonus_tier(plan_id,sort_order,tier_id)
) engine=InnoDB default charset=utf8mb4 comment='项目综合阶梯奖金快照';

create table if not exists biz_project_kpi_settlement (
  settlement_id bigint not null auto_increment comment '结算ID',
  plan_id bigint not null comment '方案ID',
  project_id bigint not null comment '项目ID',
  period_start date not null comment '结算开始日期快照',
  period_end date not null comment '结算结束日期快照',
  status varchar(16) not null default 'DRAFT' comment 'DRAFT/SUBMITTED/RETURNED/CONFIRMED',
  total_score decimal(9,2) default null comment '项目综合得分',
  bonus_amount decimal(20,2) default null comment '项目奖金池金额',
  currency char(3) not null default 'CNY' comment '币种',
  submitted_user_id bigint default null comment '提交人ID',
  submitted_user_name varchar(100) default null comment '提交人',
  submitted_time datetime default null comment '提交时间',
  reviewed_user_id bigint default null comment '确认或退回人ID',
  reviewed_user_name varchar(100) default null comment '确认或退回人',
  reviewed_time datetime default null comment '确认或退回时间',
  review_comment varchar(500) default null comment '审核意见',
  accounting_fact_id bigint default null comment '奖金经营事实ID',
  version int not null default 0 comment '乐观锁版本',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  primary key(settlement_id),
  unique key uk_project_kpi_settlement_plan(plan_id),
  key idx_project_kpi_settlement(project_id,status,period_end)
) engine=InnoDB default charset=utf8mb4 comment='项目KPI奖金结算';

create table if not exists biz_project_kpi_result (
  result_id bigint not null auto_increment comment '结果ID',
  settlement_id bigint not null comment '结算ID',
  plan_item_id bigint not null comment '方案指标快照ID',
  actual_value decimal(20,4) not null comment '实际值',
  completion_rate decimal(9,2) not null comment '完成率/单项得分',
  weighted_score decimal(9,2) not null comment '加权得分',
  result_note varchar(1000) not null comment '手工结果说明',
  attachment_urls varchar(4000) default null comment '凭证附件',
  input_user_id bigint not null comment '填报人ID',
  input_user_name varchar(100) not null comment '填报人',
  input_time datetime not null default current_timestamp comment '填报时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  primary key(result_id),
  unique key uk_project_kpi_result_item(settlement_id,plan_item_id),
  key idx_project_kpi_result_settlement(settlement_id)
) engine=InnoDB default charset=utf8mb4 comment='项目KPI结算结果';

insert into biz_fact_category(category_code,category_name,fact_kind,default_sign,unit_type,sort_order)
values('PROJECT_BONUS_COST','项目绩效奖金','COST',-1,'MONEY',165)
on duplicate key update category_name=values(category_name),fact_kind=values(fact_kind),
  default_sign=values(default_sign),unit_type=values(unit_type),sort_order=values(sort_order);

set @bonus_cost_column_sql=(select if(count(*)=0,
  'alter table biz_project_daily_result add column bonus_cost decimal(20,4) not null default 0 comment ''项目奖金成本'' after personnel_cost',
  'select 1')
  from information_schema.columns where table_schema=database()
    and table_name='biz_project_daily_result' and column_name='bonus_cost');
prepare bonus_cost_column_stmt from @bonus_cost_column_sql;
execute bonus_cost_column_stmt;
deallocate prepare bonus_cost_column_stmt;

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi) values
(4010,'项目KPI与奖金',4000,5,'kpi-bonus','business/kpi/index','','BusinessProjectKpiBonus',1,0,'C','0','0',
 'business:kpi:list','chart','admin',sysdate(),'项目级KPI、综合阶梯奖金和结算',''),
(4071,'查看项目KPI奖金',4010,1,'#','','','',1,0,'F','0','0','business:kpi:list','#','admin',sysdate(),'',''),
(4072,'设置及确认KPI奖金',4010,2,'#','','','',1,0,'F','0','0','business:kpi:manage','#','admin',sysdate(),'',''),
(4073,'填报项目KPI结果',4010,3,'#','','','',1,0,'F','0','0','business:kpi:settle','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),
  icon=values(icon),status=values(status),remark=values(remark),menu_name_vi=values(menu_name_vi);

update sys_menu set order_num=6 where menu_id=4004;
update sys_menu set order_num=7 where menu_id=4005;
update sys_menu set order_num=8 where menu_id=4006;

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4000,4010,4071,4072)
where r.role_key='company_owner' and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4000,4010,4071,4073)
where r.role_key='project_owner' and r.del_flag='0';
