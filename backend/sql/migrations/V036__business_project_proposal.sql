-- 项目立项申请：全员申请、申请人即负责人、指定老板一次审批后直接进入执行。
-- 可重复执行；历史正式项目保持原状态，不转换为申请。

create table if not exists biz_project_proposal (
  proposal_id bigint not null auto_increment comment '立项申请ID',
  proposal_no varchar(32) not null comment '立项申请编号',
  submission_version int not null default 0 comment '提交版本',
  project_name varchar(160) not null comment '拟建项目名称',
  applicant_user_id bigint not null comment '申请人账号ID，同时为初始项目负责人',
  applicant_name varchar(64) not null comment '申请人姓名快照',
  sponsor_owner_user_id bigint not null comment '审批及归属老板账号ID',
  sponsor_owner_name varchar(64) not null comment '审批及归属老板姓名快照',
  company_dept_id bigint not null comment '归属公司ID',
  parent_project_id bigint null comment '上级项目ID',
  project_type varchar(64) not null default 'GENERAL',
  accounting_mode varchar(16) not null default 'PROFIT',
  management_mode varchar(16) not null default 'SIMPLE',
  objective varchar(1000) not null comment '项目目标',
  application_reason varchar(2000) not null comment '申请理由',
  plan_start_date date not null,
  plan_end_date date not null,
  priority varchar(16) not null default 'MEDIUM',
  base_currency char(3) not null default 'CNY',
  budget_limit decimal(20,2) null,
  no_budget char(1) not null default '0' comment '1明确不设置预算',
  execution_source varchar(32) null,
  status varchar(16) not null default 'DRAFT' comment 'DRAFT/PENDING/RETURNED/WITHDRAWN/APPROVED',
  submitted_time datetime null,
  reviewed_user_id bigint null,
  reviewed_user_name varchar(64) null,
  reviewed_time datetime null,
  review_comment varchar(2000) null,
  created_project_id bigint null,
  version int not null default 0,
  del_flag char(1) not null default '0',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  remark varchar(500) null,
  primary key (proposal_id),
  unique key uk_biz_project_proposal_no (proposal_no),
  unique key uk_biz_project_proposal_project (created_project_id),
  key idx_biz_project_proposal_applicant (applicant_user_id,status,create_time),
  key idx_biz_project_proposal_sponsor (sponsor_owner_user_id,status,submitted_time)
) engine=InnoDB default charset=utf8mb4 comment='项目立项申请';

create table if not exists biz_project_proposal_event (
  event_id bigint not null auto_increment,
  proposal_id bigint not null,
  submission_version int not null default 0,
  event_type varchar(24) not null,
  from_status varchar(16) null,
  to_status varchar(16) null,
  operator_user_id bigint not null,
  operator_name varchar(64) not null,
  event_comment varchar(2000) null,
  snapshot_json longtext null,
  create_time datetime not null default current_timestamp,
  primary key (event_id),
  key idx_biz_project_proposal_event (proposal_id,event_id)
) engine=InnoDB default charset=utf8mb4 comment='立项申请不可删除事件';

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='source_proposal_id')=0,
  "alter table biz_project add column source_proposal_id bigint null comment '来源立项申请ID' after project_no",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='applicant_user_id')=0,
  "alter table biz_project add column applicant_user_id bigint null comment '原始申请人账号ID' after baseline_status",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='applicant_name')=0,
  "alter table biz_project add column applicant_name varchar(64) null comment '原始申请人姓名快照' after applicant_user_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='sponsor_owner_user_id')=0,
  "alter table biz_project add column sponsor_owner_user_id bigint null comment '审批及归属老板账号ID' after applicant_name",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='sponsor_owner_name')=0,
  "alter table biz_project add column sponsor_owner_name varchar(64) null comment '审批及归属老板姓名快照' after sponsor_owner_user_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 历史项目维持原状态和负责人；创建账号作为申请人快照，原立项老板作为归属老板。
update biz_project p
left join sys_user creator on cast(creator.user_name as binary)=cast(p.create_by as binary) and creator.del_flag='0'
set p.applicant_user_id=coalesce(p.applicant_user_id,creator.user_id,p.initiator_user_id),
    p.applicant_name=coalesce(nullif(p.applicant_name,''),nullif(creator.nick_name,''),creator.user_name,p.initiator_name),
    p.sponsor_owner_user_id=coalesce(p.sponsor_owner_user_id,p.initiator_user_id),
    p.sponsor_owner_name=coalesce(nullif(p.sponsor_owner_name,''),p.initiator_name)
where p.applicant_user_id is null or p.applicant_name is null
   or p.sponsor_owner_user_id is null or p.sponsor_owner_name is null;

set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
    and table_name='biz_project' and index_name='uk_biz_project_source_proposal')=0,
  'alter table biz_project add unique index uk_biz_project_source_proposal (source_proposal_id)',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
    and table_name='biz_project' and index_name='idx_biz_project_sponsor_status')=0,
  'alter table biz_project add index idx_biz_project_sponsor_status (sponsor_owner_user_id,status,plan_end_date)',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark)
select '公司人员','company_staff',9,5,1,1,'0','0','admin',sysdate(),'有效在职人员发起本人负责的立项申请'
where not exists(select 1 from sys_role where role_key='company_staff' and del_flag='0');

update sys_role set role_name='公司人员',remark='有效在职人员发起本人负责的立项申请'
where role_key='company_staff' and del_flag='0';

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,remark,menu_name_vi)
values
(4009,'立项申请',4000,2,'project-proposals','business/proposal/index','','BusinessProjectProposals',1,0,'C','0','0',
 'business:project:proposal:list','form','admin',sysdate(),'公司人员提交本人负责的立项申请',''),
(4061,'查看立项申请',4009,1,'#','','','',1,0,'F','0','0','business:project:proposal:list','#','admin',sysdate(),'',''),
(4062,'新建立项申请',4009,2,'#','','','',1,0,'F','0','0','business:project:proposal:add','#','admin',sysdate(),'',''),
(4063,'编辑立项申请',4009,3,'#','','','',1,0,'F','0','0','business:project:proposal:edit','#','admin',sysdate(),'',''),
(4064,'提交立项申请',4009,4,'#','','','',1,0,'F','0','0','business:project:proposal:submit','#','admin',sysdate(),'',''),
(4065,'审批立项申请',4009,5,'#','','','',1,0,'F','0','0','business:project:proposal:review','#','admin',sysdate(),'','')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),order_num=values(order_num),
 path=values(path),component=values(component),route_name=values(route_name),perms=values(perms),
 icon=values(icon),status=values(status),remark=values(remark),menu_name_vi=values(menu_name_vi);

update sys_menu set order_num=3 where menu_id=4008;
update sys_menu set order_num=4,remark='已批准项目的执行与经营管理' where menu_id=4002;
update sys_menu set order_num=5 where menu_id=4004;
update sys_menu set order_num=6 where menu_id=4005;
update sys_menu set order_num=7 where menu_id=4006;

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4000,4009,4061,4062,4063,4064)
where r.role_key='company_staff' and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in(4000,4009,4061,4062,4063,4064,4065)
where r.role_key='company_owner' and r.del_flag='0';

-- 当前所有有效账号获得公司人员身份；不移除任何既有业务角色。
insert ignore into sys_user_role(user_id,role_id)
select u.user_id,r.role_id from sys_user u
join sys_role r on r.role_key='company_staff' and r.del_flag='0'
left join biz_staff_profile profile on profile.user_id=u.user_id
where u.del_flag='0' and u.status='0' and coalesce(profile.employment_status,'ACTIVE')<>'LEFT';

-- 关闭直接创建正式项目的菜单权限。管理员也会在服务层被拒绝。
delete from sys_role_menu where menu_id=4011;
update sys_menu set status='1',remark='旧流程权限：正式项目只能由立项审批事务创建' where menu_id=4011;
