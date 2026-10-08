-- 公司项目经营第一阶段：只新增项目域表、角色和菜单，不修改直播或珠宝业务表。
-- 可重复执行，不删除已有业务数据。

create table if not exists biz_project (
  project_id bigint not null auto_increment comment '项目ID',
  project_no varchar(32) not null comment '项目编号',
  parent_id bigint null comment '父项目ID',
  project_name varchar(160) not null comment '项目名称',
  project_type varchar(64) not null default 'GENERAL' comment '项目类型',
  accounting_mode varchar(16) not null default 'PROFIT' comment '核算模式',
  objective varchar(1000) null comment '项目目标',
  status varchar(24) not null default 'DRAFT' comment '项目状态',
  baseline_status varchar(16) not null default 'DRAFT' comment '基线状态',
  initiator_user_id bigint not null comment '立项老板账号ID',
  initiator_name varchar(64) not null comment '立项老板姓名快照',
  main_owner_user_id bigint not null comment '主负责人账号ID',
  main_owner_name varchar(64) not null comment '主负责人姓名快照',
  plan_start_date date null comment '计划开始日期',
  plan_end_date date null comment '计划结束日期',
  actual_start_date date null comment '实际开始日期',
  actual_end_date date null comment '实际结束日期',
  priority varchar(16) not null default 'MEDIUM' comment '优先级',
  base_currency char(3) not null default 'CNY' comment '基础币种',
  budget_limit decimal(20,2) null comment '预算上限',
  baseline_version int not null default 0 comment '基线版本',
  version int not null default 0 comment '并发版本',
  del_flag char(1) not null default '0' comment '删除标志',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (project_id),
  unique key uk_biz_project_no (project_no),
  key idx_biz_project_parent (parent_id),
  key idx_biz_project_initiator_status (initiator_user_id,status,plan_end_date),
  key idx_biz_project_owner_status (main_owner_user_id,status),
  key idx_biz_project_status_dates (status,plan_end_date)
) engine=InnoDB default charset=utf8mb4 comment='项目主档';

create table if not exists biz_project_member (
  member_id bigint not null auto_increment comment '成员ID',
  project_id bigint not null comment '项目ID',
  user_id bigint not null comment '登录账号ID',
  user_name_snapshot varchar(64) not null comment '姓名快照',
  member_role varchar(16) not null default 'MEMBER' comment '项目身份',
  status char(1) not null default '0' comment '0有效1退出',
  joined_date date null,
  left_date date null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (member_id),
  unique key uk_biz_project_member (project_id,user_id),
  key idx_biz_project_member_user (user_id,status)
) engine=InnoDB default charset=utf8mb4 comment='项目成员';

create table if not exists biz_project_owner_history (
  history_id bigint not null auto_increment,
  project_id bigint not null,
  from_user_id bigint null,
  from_user_name varchar(64) null,
  to_user_id bigint not null,
  to_user_name varchar(64) not null,
  effective_time datetime not null,
  reason varchar(500) null,
  operator_user_id bigint not null,
  operator_name varchar(64) not null,
  create_time datetime not null default current_timestamp,
  primary key (history_id),
  key idx_biz_owner_history_project (project_id,effective_time)
) engine=InnoDB default charset=utf8mb4 comment='项目负责人变更历史';

create table if not exists biz_project_milestone (
  milestone_id bigint not null auto_increment,
  project_id bigint not null,
  milestone_name varchar(160) not null,
  plan_date date null,
  actual_date date null,
  weight decimal(9,4) not null default 0,
  status varchar(16) not null default 'PENDING',
  sort_order int not null default 0,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (milestone_id),
  key idx_biz_milestone_project (project_id,status,plan_date)
) engine=InnoDB default charset=utf8mb4 comment='项目里程碑';

create table if not exists biz_project_task (
  task_id bigint not null auto_increment,
  project_id bigint not null,
  parent_task_id bigint null,
  milestone_id bigint null,
  task_name varchar(200) not null,
  assignee_user_id bigint null,
  assignee_name varchar(64) null,
  status varchar(16) not null default 'TODO',
  progress int not null default 0,
  priority varchar(16) not null default 'MEDIUM',
  plan_start_date date null,
  due_date date null,
  actual_finish_time datetime null,
  version int not null default 0,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (task_id),
  key idx_biz_task_project (project_id,status,due_date),
  key idx_biz_task_assignee (assignee_user_id,status,due_date)
) engine=InnoDB default charset=utf8mb4 comment='项目任务';

create table if not exists biz_project_risk (
  risk_id bigint not null auto_increment,
  project_id bigint not null,
  risk_type varchar(32) not null default 'GENERAL',
  risk_title varchar(200) not null,
  severity varchar(16) not null default 'MEDIUM',
  probability varchar(16) not null default 'MEDIUM',
  owner_user_id bigint null,
  owner_name varchar(64) null,
  due_date date null,
  status varchar(16) not null default 'OPEN',
  response_plan varchar(1000) null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (risk_id),
  key idx_biz_risk_project (project_id,status,severity)
) engine=InnoDB default charset=utf8mb4 comment='项目风险';

create table if not exists biz_project_event (
  event_id bigint not null auto_increment,
  project_id bigint not null,
  event_type varchar(32) not null,
  from_status varchar(24) null,
  to_status varchar(24) null,
  operator_user_id bigint not null,
  operator_name varchar(64) not null,
  event_comment varchar(1000) null,
  create_time datetime not null default current_timestamp,
  primary key (event_id),
  key idx_biz_event_project (project_id,create_time)
) engine=InnoDB default charset=utf8mb4 comment='项目不可删除事件';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '老板','company_owner',1,1,1,1,'0','0','admin',sysdate(),'公司经营、立项和最终审批'
where not exists(select 1 from sys_role where role_key='company_owner' and del_flag='0');

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '项目参与人员','project_user',8,5,1,1,'0','0','admin',sysdate(),'按项目成员关系访问项目数据'
where not exists(select 1 from sys_role where role_key='project_user' and del_flag='0');

update sys_role set role_name='老板',remark='公司经营、立项和最终审批'
where role_key='company_owner' and del_flag='0';
update sys_role set role_name='项目参与人员',remark='按项目成员关系访问项目数据'
where role_key='project_user' and del_flag='0';

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4000,'公司经营',0,2,'business',null,'','Business',1,0,'M','0','0','','dashboard','admin',sysdate(),'项目经营管理目录',''),
(4001,'老板工作台',4000,1,'boss','business/boss/index','','BusinessBoss',1,0,'C','0','0','business:boss:view','dashboard','admin',sysdate(),'老板经营与项目组合工作台',''),
(4002,'项目中心',4000,2,'projects','business/project/index','','BusinessProjects',1,0,'C','0','0','business:project:list','tree-table','admin',sysdate(),'项目创建、规划和执行',''),
(4003,'我的工作台',4000,5,'my-workbench','business/my/index','','BusinessMyWorkbench',1,0,'C','0','0','business:project:list','user','admin',sysdate(),'个人跨项目待办',''),
(4004,'人员管理',4000,3,'staff','business/staff/index','','BusinessStaff',1,0,'C','0','0','business:staff:list','peoples','admin',sysdate(),'老板共享管理公司人员',''),
(4005,'部门管理',4000,4,'departments','business/department/index','','BusinessDepartments',1,0,'C','0','0','business:department:list','tree','admin',sysdate(),'老板共享管理公司组织架构',''),
(4011,'创建项目',4002,1,'#','','','',1,0,'F','0','0','business:project:add','#','admin',sysdate(),'',''),
(4012,'编辑项目',4002,2,'#','','','',1,0,'F','0','0','business:project:edit','#','admin',sysdate(),'',''),
(4013,'项目成员',4002,3,'#','','','',1,0,'F','0','0','business:project:member','#','admin',sysdate(),'',''),
(4014,'任务计划',4002,4,'#','','','',1,0,'F','0','0','business:project:task','#','admin',sysdate(),'',''),
(4015,'提交计划',4002,5,'#','','','',1,0,'F','0','0','business:project:submit','#','admin',sysdate(),'',''),
(4016,'项目决策',4002,6,'#','','','',1,0,'F','0','0','business:project:manage','#','admin',sysdate(),'',''),
(4021,'管理人员',4004,1,'#','','','',1,0,'F','0','0','business:staff:manage','#','admin',sysdate(),'',''),
(4031,'管理部门',4005,1,'#','','','',1,0,'F','0','0','business:department:manage','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
  path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),icon=values(icon),
  remark=values(remark),menu_name_vi=values(menu_name_vi);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
where r.role_key='company_owner' and r.del_flag='0'
  and (m.menu_id between 4000 and 4016 or m.menu_id in (4021,4031))
  and m.menu_id <> 4003;

-- 老板的个人待办已合并到老板工作台，不再显示独立“我的工作台”。
delete rm from sys_role_menu rm
join sys_role r on r.role_id=rm.role_id and r.role_key='company_owner' and r.del_flag='0'
where rm.menu_id=4003;

-- 老板权限覆盖项目参与人员权限，避免角色叠加后重新出现冗余菜单。
delete project_user_role from sys_user_role project_user_role
join sys_role project_role on project_role.role_id=project_user_role.role_id
  and project_role.role_key='project_user' and project_role.del_flag='0'
join sys_user_role owner_user_role on owner_user_role.user_id=project_user_role.user_id
join sys_role owner_role on owner_role.role_id=owner_user_role.role_id
  and owner_role.role_key='company_owner' and owner_role.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m
where r.role_key='project_user' and r.del_flag='0'
  and m.menu_id in (4000,4002,4003,4012,4013,4014,4015);
