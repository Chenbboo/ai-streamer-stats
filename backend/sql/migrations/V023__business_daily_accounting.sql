-- 公司每日收支、项目日结果与项目公司归属。仅新增经营层数据，不修改直播/珠宝事实表。

set @project_company_column_sql=(select if(count(*)=0,
  'alter table biz_project add column company_dept_id bigint default null comment ''归属公司部门ID'' after parent_id','select 1')
  from information_schema.columns where table_schema=database() and table_name='biz_project' and column_name='company_dept_id');
prepare project_company_column_stmt from @project_company_column_sql;
execute project_company_column_stmt;
deallocate prepare project_company_column_stmt;
set @project_company_index_sql=(select if(count(*)=0,
  'create index idx_biz_project_company on biz_project(company_dept_id,status)','select 1')
  from information_schema.statistics where table_schema=database() and table_name='biz_project' and index_name='idx_biz_project_company');
prepare project_company_index_stmt from @project_company_index_sql;
execute project_company_index_stmt;
deallocate prepare project_company_index_stmt;

-- 能从负责人组织明确判断的旧项目自动归属；老板/管理员直属集团的项目保留待人工选择。
update biz_project p
join sys_user u on u.user_id=p.main_owner_user_id and u.del_flag='0'
join sys_dept d on d.dept_id=u.dept_id and d.del_flag='0'
set p.company_dept_id=case
  when d.dept_id=110 or find_in_set(110,d.ancestors) then 110
  when d.dept_id=111 or find_in_set(111,d.ancestors) then 111
  else p.company_dept_id end
where p.company_dept_id is null;

create table if not exists biz_fact_category (
  category_id bigint not null auto_increment,
  category_code varchar(64) not null,
  category_name varchar(100) not null,
  fact_kind varchar(20) not null comment 'REVENUE/COST/ADJUSTMENT/VALUE',
  default_sign smallint not null default 1,
  unit_type varchar(20) not null default 'MONEY',
  status char(1) not null default '0',
  sort_order int not null default 0,
  remark varchar(500) default null,
  primary key(category_id), unique key uk_fact_category_code(category_code)
) engine=InnoDB default charset=utf8mb4 comment='经营事实类别';

insert into biz_fact_category(category_code,category_name,fact_kind,default_sign,unit_type,sort_order) values
('SALES_REVENUE','销售收入','REVENUE',1,'MONEY',10),('SERVICE_REVENUE','服务收入','REVENUE',1,'MONEY',20),
('LIVE_REVENUE','直播折算收入','REVENUE',1,'MONEY',30),('MILESTONE_REVENUE','合同里程碑收入','REVENUE',1,'MONEY',40),
('OTHER_REVENUE','其他收入','REVENUE',1,'MONEY',90),('PURCHASE_COST','商品及采购成本','COST',-1,'MONEY',110),
('PLATFORM_FEE','平台与渠道费用','COST',-1,'MONEY',120),('MARKETING_COST','投流与营销费用','COST',-1,'MONEY',130),
('LOGISTICS_COST','物流履约费用','COST',-1,'MONEY',140),('DIRECT_EXPENSE','项目直接费用','COST',-1,'MONEY',150),
('ADMIN_ALLOCATION','分摊管理费用','COST',-1,'MONEY',160),('ACCOUNTING_ADJUSTMENT','核算调整','ADJUSTMENT',1,'MONEY',210),
('VALUE_RESULT','非金额成果价值','VALUE',1,'SCORE',310)
on duplicate key update category_name=values(category_name),fact_kind=values(fact_kind),default_sign=values(default_sign),unit_type=values(unit_type),sort_order=values(sort_order);

create table if not exists biz_operating_fact (
  fact_id bigint not null auto_increment, project_id bigint not null, company_dept_id bigint not null,
  biz_date date not null, category_id bigint not null, category_code varchar(64) not null,
  category_name varchar(100) not null, fact_kind varchar(20) not null,
  amount decimal(20,4) default null, quantity decimal(20,4) default null, currency char(3) default null, unit varchar(32) default null,
  description varchar(500) not null, counterparty varchar(160) default null, attachment_urls varchar(4000) default null,
  source_domain varchar(32) not null default 'MANUAL', source_type varchar(32) not null default 'MANUAL',
  source_id varchar(100) default null, source_line_key varchar(100) default null,
  status varchar(16) not null default 'DRAFT', reversal_fact_id bigint default null,
  idempotency_key varchar(128) not null, version int not null default 0,
  confirmed_user_id bigint default null, confirmed_user_name varchar(100) default null, confirmed_time datetime default null,
  create_user_id bigint not null, create_by varchar(64) default '', create_time datetime not null default current_timestamp,
  update_by varchar(64) default '', update_time datetime default null, remark varchar(500) default null,
  primary key(fact_id), unique key uk_operating_fact_idempotency(idempotency_key),
  key idx_fact_project_date(project_id,biz_date,status), key idx_fact_company_date(company_dept_id,biz_date,status)
) engine=InnoDB default charset=utf8mb4 comment='可追溯经营事实';

create table if not exists biz_project_daily_result (
  result_id bigint not null auto_increment, project_id bigint not null, company_dept_id bigint not null,
  biz_date date not null, accounting_mode varchar(20) not null, formula_version_id bigint default null,
  revenue_amount decimal(20,4) not null default 0, cost_amount decimal(20,4) not null default 0,
  personnel_cost decimal(20,4) not null default 0, adjustment_amount decimal(20,4) not null default 0,
  profit_amount decimal(20,4) not null default 0, budget_spent decimal(20,4) not null default 0,
  value_score decimal(20,4) not null default 0, data_cutoff_time datetime not null,
  close_status varchar(16) not null default 'OPEN', result_version int not null, is_current char(1) not null default '1',
  calculation_detail varchar(2000) default null, create_by varchar(64) default '', create_time datetime not null default current_timestamp,
  primary key(result_id), unique key uk_project_daily_version(project_id,biz_date,result_version),
  key idx_daily_company(company_dept_id,biz_date,is_current), key idx_daily_project(project_id,biz_date,is_current)
) engine=InnoDB default charset=utf8mb4 comment='项目每日经营结果快照';

create table if not exists biz_project_daily_result_item (
  item_id bigint not null auto_increment, result_id bigint not null, component_code varchar(64) not null,
  component_name varchar(100) not null, amount decimal(20,4) default null, quantity decimal(20,4) default null,
  calculation_detail varchar(2000) default null, primary key(item_id), key idx_daily_item_result(result_id)
) engine=InnoDB default charset=utf8mb4 comment='项目每日结果分项';

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi) values
(4006,'每日收支',4000,5,'accounting','business/accounting/index','','BusinessAccounting',1,0,'C','0','0','business:accounting:list','money','admin',sysdate(),'老板每日收支和项目盈亏',''),
(4041,'录入收支',4006,1,'#','','','',1,0,'F','0','0','business:accounting:add','#','admin',sysdate(),'',''),
(4042,'确认收支',4006,2,'#','','','',1,0,'F','0','0','business:accounting:confirm','#','admin',sysdate(),'',''),
(4043,'重算日结果',4006,3,'#','','','',1,0,'F','0','0','business:accounting:recalculate','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),icon=values(icon),remark=values(remark);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4006,4041,4042,4043)
where r.role_key='company_owner' and r.del_flag='0';
