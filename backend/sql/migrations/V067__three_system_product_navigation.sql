-- Three business systems on the existing platform. No business facts or user-role assignments are rewritten.

-- Apply after V064-V066; repeatable by stable route/perms identity. Preserve live/jewelry menus.

update sys_menu set menu_name='项目管理系统',menu_name_vi='Quản lý dự án',remark='项目启动、计划、执行、资源、项目指标与交付' where menu_id=4000 and path='business';

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '人力资源管理系统','Quản lý nhân sự',0,3,'hcm',null,'','Hcm',1,0,'M','0','0','','peoples','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=0 and path='hcm');

set @hcm=(select menu_id from sys_menu where parent_id=0 and path='hcm' limit 1);

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '财务与管理核算系统','Tài chính và kế toán quản trị',0,4,'finance',null,'','Finance',1,0,'M','0','0','','money','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=0 and path='finance');

set @finance=(select menu_id from sys_menu where parent_id=0 and path='finance' limit 1);

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '平台与集成','Nền tảng và tích hợp',0,5,'platform',null,'','Platform',1,0,'M','0','0','','system','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=0 and path='platform');

set @platform=(select menu_id from sys_menu where parent_id=0 and path='platform' limit 1);

update sys_menu set parent_id=@hcm where menu_id in(4004,4005);

update sys_menu set parent_id=@finance where menu_id in(4001,4006);

update sys_menu set parent_id=@platform where menu_id=4008;

update sys_menu set menu_name='经营报表',menu_name_vi='Báo cáo quản trị' where menu_id=4001;

update sys_menu set menu_name='项目核算与收支',menu_name_vi='Quyết toán và thu chi dự án' where menu_id=4006;

update sys_menu set menu_name='项目 KPI',menu_name_vi='KPI dự án',remark='项目成果指标；新方案奖励独立在人力资源办理' where menu_id=4010;

update sys_menu set menu_name='项目启动申请',menu_name_vi='Đề nghị khởi tạo dự án' where menu_id=4009;

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '资源与实际工作','Nguồn lực và công việc thực tế',4000,10,'resources','business/resources/index','','BusinessResources',1,0,'C','0','0','business:work:report','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=4000 and path='resources');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '奖金激励','Khuyến khích và thưởng',@hcm,10,'incentives','business/incentive/index','','BusinessIncentives',1,0,'C','0','0','business:incentive:list','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=@hcm and path='incentives');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '假勤查询','Tra cứu chấm công',@hcm,10,'attendance','business/attendance/index','','BusinessAttendance',1,0,'C','0','0','business:attendance:self','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=@hcm and path='attendance');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '用人成本政策','Chính sách chi phí nhân sự',@finance,10,'cost-policies','business/cost-policies/index','','BusinessCostPolicies',1,0,'C','0','0','business:staff:cost','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=@finance and path='cost-policies');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '飞书集成','Tích hợp Feishu',@platform,10,'feishu','business/feishu/index','','BusinessFeishu',1,0,'C','0','0','business:integration:feishu','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=@platform and path='feishu');

set @incentive=(select menu_id from sys_menu where parent_id=@hcm and path='incentives' limit 1);

set @attendance=(select menu_id from sys_menu where parent_id=@hcm and path='attendance' limit 1);

set @feishu=(select menu_id from sys_menu where parent_id=@platform and path='feishu' limit 1);

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '奖金规则维护','Quy tắc thưởng',@incentive,10,'#','','','',1,0,'F','0','0','business:incentive:rule','list','migration',sysdate() where not exists(select 1 from sys_menu where perms='business:incentive:rule');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '奖励申请','Đề nghị thưởng',@incentive,10,'#','','','',1,0,'F','0','0','business:incentive:apply','list','migration',sysdate() where not exists(select 1 from sys_menu where perms='business:incentive:apply');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '奖励核准','Phê duyệt thưởng',@incentive,10,'#','','','',1,0,'F','0','0','business:incentive:approve','list','migration',sysdate() where not exists(select 1 from sys_menu where perms='business:incentive:approve');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '组织假勤读取','Đọc chấm công tổ chức',@attendance,10,'#','','','',1,0,'F','0','0','business:attendance:read','list','migration',sysdate() where not exists(select 1 from sys_menu where perms='business:attendance:read');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '假勤验收与切换','Nghiệm thu và chuyển nguồn',@hcm,10,'#','','','',1,0,'F','0','0','business:attendance:cutover','list','migration',sysdate() where not exists(select 1 from sys_menu where perms='business:attendance:cutover');

insert into sys_menu(menu_name,menu_name_vi,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '假勤接入验收','Nghiệm thu nguồn chấm công',@hcm,10,'attendance-cutover','business/feishu/index','','BusinessAttendanceCutover',1,0,'C','0','0','business:attendance:cutover','list','migration',sysdate() where not exists(select 1 from sys_menu where parent_id=@hcm and path='attendance-cutover');

-- The project role alone no longer conveys raw compensation/rate authority.

delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id join sys_menu m on m.menu_id=rm.menu_id where r.role_key='project_owner' and m.perms='business:staff:cost';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) select '财务成本管理员','finance_cost_manager',20,'1',1,1,'0','0','migration',sysdate(),'独立业务权限，仍受对象责任和公司范围校验' where not exists(select 1 from sys_role where role_key='finance_cost_manager' and del_flag='0');

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:staff:cost') where r.role_key='finance_cost_manager' and r.del_flag='0';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) select '奖金经办','hcm_incentive_operator',20,'1',1,1,'0','0','migration',sysdate(),'独立业务权限，仍受对象责任和公司范围校验' where not exists(select 1 from sys_role where role_key='hcm_incentive_operator' and del_flag='0');

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:incentive:list','business:incentive:apply') where r.role_key='hcm_incentive_operator' and r.del_flag='0';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) select '奖金核准人','hcm_incentive_approver',20,'1',1,1,'0','0','migration',sysdate(),'独立业务权限，仍受对象责任和公司范围校验' where not exists(select 1 from sys_role where role_key='hcm_incentive_approver' and del_flag='0');

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:incentive:list','business:incentive:approve','business:incentive:rule') where r.role_key='hcm_incentive_approver' and r.del_flag='0';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) select '假勤数据读取','attendance_reader',20,'1',1,1,'0','0','migration',sysdate(),'独立业务权限，仍受对象责任和公司范围校验' where not exists(select 1 from sys_role where role_key='attendance_reader' and del_flag='0');

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:attendance:self','business:attendance:read') where r.role_key='attendance_reader' and r.del_flag='0';

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) select '飞书集成管理员','feishu_integrator',20,'1',1,1,'0','0','migration',sysdate(),'独立业务权限，仍受对象责任和公司范围校验' where not exists(select 1 from sys_role where role_key='feishu_integrator' and del_flag='0');

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:integration:feishu') where r.role_key='feishu_integrator' and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:incentive:list','business:incentive:apply') where r.role_key in('project_owner','company_owner') and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:incentive:rule','business:incentive:approve','business:attendance:cutover') where r.role_key in('company_owner') and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:attendance:self') where r.role_key in('project_owner','project_user','project_deputy','company_owner') and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id) select distinct rm.role_id,target.menu_id from sys_role_menu rm join sys_menu source on source.menu_id=rm.menu_id join sys_menu target on target.perms=source.perms and target.menu_type='C' where target.component in('business/resources/index','business/cost-policies/index','business/feishu/index');

-- Add parent visibility only when the role has one of its children; no industry grants.

insert ignore into sys_role_menu(role_id,menu_id) select distinct rm.role_id,p.menu_id from sys_role_menu rm join sys_menu child on child.menu_id=rm.menu_id join sys_menu p on p.menu_id=child.parent_id where p.menu_id in(4000,@hcm,@finance,@platform,@incentive,@attendance,@feishu);

insert ignore into sys_role_menu(role_id,menu_id) select distinct rm.role_id,p.menu_id from sys_role_menu rm join sys_menu child on child.menu_id=rm.menu_id join sys_menu p on p.menu_id=child.parent_id where p.menu_id in(4000,@hcm,@finance,@platform,@incentive,@attendance,@feishu);

insert ignore into sys_role_menu(role_id,menu_id) select distinct rm.role_id,p.menu_id from sys_role_menu rm join sys_menu child on child.menu_id=rm.menu_id join sys_menu p on p.menu_id=child.parent_id where p.menu_id in(4000,@hcm,@finance,@platform,@incentive,@attendance,@feishu);

-- Repair earlier domain-role gaps and keep technical integration independent from business acceptance.
update sys_menu set parent_id=@hcm where perms='business:attendance:cutover' and menu_type='F';
delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id where r.role_key='company_owner' and rm.menu_id=@feishu;
delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id join sys_menu m on m.menu_id=rm.menu_id where r.role_key='finance_cost_manager' and m.perms='business:staff:list';
insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms in('business:project:proposal:list','business:project:proposal:add','business:project:proposal:edit','business:project:proposal:submit','business:project:work:view') where r.role_key='project_owner' and r.del_flag='0';

-- Business acceptance includes organization-scoped result reading; service still checks the actual company leader.
insert ignore into sys_role_menu(role_id,menu_id) select r.role_id,m.menu_id from sys_role r join sys_menu m on m.perms='business:attendance:read' where r.role_key='company_owner' and r.del_flag='0';
