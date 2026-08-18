-- 只读验证。missing_* 应为0，数据完整性检查也应为0。

select
  count(case when table_name='live_streamer' then 1 end)=0 as missing_live_streamer,
  count(case when table_name='live_customer' then 1 end)=0 as missing_live_customer,
  count(case when table_name='live_customer_alias' then 1 end)=0 as missing_live_customer_alias,
  count(case when table_name='live_upload' then 1 end)=0 as missing_live_upload,
  count(case when table_name='live_gift_record' then 1 end)=0 as missing_live_gift_record,
  count(case when table_name='live_chat_contact' then 1 end)=0 as missing_live_chat_contact,
  count(case when table_name='live_chat_message' then 1 end)=0 as missing_live_chat_message,
  count(case when table_name='live_follow_record' then 1 end)=0 as missing_live_follow_record,
  count(case when table_name='live_daily_report' then 1 end)=0 as missing_live_daily_report,
  count(case when table_name='live_kpi_config' then 1 end)=0 as missing_live_kpi_config,
  count(case when table_name='live_weiji_stats' then 1 end)=0 as missing_live_weiji_stats
from information_schema.tables
where table_schema=database() and table_name like 'live\_%';

select
  count(case when table_name='jewelry_staff' then 1 end)=0 as missing_jewelry_staff,
  count(case when table_name='jewelry_product' then 1 end)=0 as missing_jewelry_product,
  count(case when table_name='jewelry_supplier' then 1 end)=0 as missing_jewelry_supplier,
  count(case when table_name='jewelry_stock' then 1 end)=0 as missing_jewelry_stock,
  count(case when table_name='jewelry_document' then 1 end)=0 as missing_jewelry_document,
  count(case when table_name='jewelry_document_item' then 1 end)=0 as missing_jewelry_document_item,
  count(case when table_name='jewelry_approval' then 1 end)=0 as missing_jewelry_approval,
  count(case when table_name='jewelry_document_event' then 1 end)=0 as missing_jewelry_document_event,
  count(case when table_name='jewelry_stock_transaction' then 1 end)=0 as missing_jewelry_stock_transaction
from information_schema.tables
where table_schema=database() and table_name like 'jewelry\_%';

select
  count(case when table_name='biz_project' then 1 end)=0 as missing_biz_project,
  count(case when table_name='biz_project_member' then 1 end)=0 as missing_biz_project_member,
  count(case when table_name='biz_project_owner_history' then 1 end)=0 as missing_biz_project_owner_history,
  count(case when table_name='biz_project_milestone' then 1 end)=0 as missing_biz_project_milestone,
  count(case when table_name='biz_project_task' then 1 end)=0 as missing_biz_project_task,
  count(case when table_name='biz_project_risk' then 1 end)=0 as missing_biz_project_risk,
  count(case when table_name='biz_project_event' then 1 end)=0 as missing_biz_project_event
  ,count(case when table_name='biz_project_acceptance' then 1 end)=0 as missing_biz_project_acceptance,
  count(case when table_name='biz_project_kpi' then 1 end)=0 as missing_biz_project_kpi,
  count(case when table_name='biz_project_budget_history' then 1 end)=0 as missing_biz_project_budget_history,
  count(case when table_name='biz_staff_cost_policy' then 1 end)=0 as missing_biz_staff_cost_policy,
  count(case when table_name='biz_project_staff_allocation' then 1 end)=0 as missing_biz_project_staff_allocation
  ,count(case when table_name='biz_fact_category' then 1 end)=0 as missing_biz_fact_category,
  count(case when table_name='biz_operating_fact' then 1 end)=0 as missing_biz_operating_fact,
  count(case when table_name='biz_project_daily_result' then 1 end)=0 as missing_biz_project_daily_result,
  count(case when table_name='biz_project_daily_result_item' then 1 end)=0 as missing_biz_project_daily_result_item
from information_schema.tables
where table_schema=database() and table_name like 'biz\_%';

select
  count(case when column_name='initiator_user_id' then 1 end)=0 as missing_project_initiator_user_id,
  count(case when column_name='initiator_name' then 1 end)=0 as missing_project_initiator_name,
  count(case when column_name='company_dept_id' then 1 end)=0 as missing_project_company_dept_id
from information_schema.columns
where table_schema=database() and table_name='biz_project';

select 'orphan_project_kpi',count(*) problem_rows
from biz_project_kpi k left join biz_project p on p.project_id=k.project_id and p.del_flag='0'
where p.project_id is null
union all
select 'orphan_budget_history',count(*)
from biz_project_budget_history h left join biz_project p on p.project_id=h.project_id and p.del_flag='0'
where p.project_id is null
union all
select 'orphan_staff_cost_policy',count(*)
from biz_staff_cost_policy c left join sys_user u on u.user_id=c.user_id and u.del_flag='0'
where u.user_id is null
union all
select 'orphan_project_staff_allocation',count(*)
from biz_project_staff_allocation a
left join biz_project p on p.project_id=a.project_id and p.del_flag='0'
left join sys_user u on u.user_id=a.user_id and u.del_flag='0'
left join biz_staff_cost_policy c on c.policy_id=a.cost_policy_id
where p.project_id is null or u.user_id is null or c.policy_id is null;

select 'orphan_operating_fact',count(*) problem_rows
from biz_operating_fact f
left join biz_project p on p.project_id=f.project_id and p.del_flag='0'
left join biz_fact_category c on c.category_id=f.category_id
left join sys_dept d on d.dept_id=f.company_dept_id and d.del_flag='0'
where p.project_id is null or c.category_id is null or d.dept_id is null
union all
select 'orphan_daily_result',count(*)
from biz_project_daily_result r
left join biz_project p on p.project_id=r.project_id and p.del_flag='0'
left join sys_dept d on d.dept_id=r.company_dept_id and d.del_flag='0'
where p.project_id is null or d.dept_id is null
union all
select 'orphan_daily_result_item',count(*)
from biz_project_daily_result_item i left join biz_project_daily_result r on r.result_id=i.result_id
where r.result_id is null;

select count(*)=0 as missing_project_initiator_status_index
from information_schema.statistics
where table_schema=database() and table_name='biz_project'
  and index_name='idx_biz_project_initiator_status';

select
  count(case when column_name='streamer_id' then 1 end)=0 as missing_customer_streamer_id
from information_schema.columns
where table_schema=database() and table_name='live_customer';

select
  count(case when table_name='jewelry_product' and column_name='product_type' then 1 end)=0
    as missing_jewelry_product_type,
  count(case when table_name='jewelry_product' and column_name='specification' then 1 end)=0
    as missing_jewelry_specification,
  count(case when table_name='jewelry_document' and column_name='actual_refund_amount' then 1 end)=0
    as missing_jewelry_actual_refund_amount,
  count(case when table_name='jewelry_document_item' and column_name='bundle_group_no' then 1 end)=0
    as missing_jewelry_bundle_group_no,
  count(case when table_name='jewelry_document_item' and column_name='sale_role' then 1 end)=0
    as missing_jewelry_sale_role,
  count(case when table_name='jewelry_document_item' and column_name='pricing_mode' then 1 end)=0
    as missing_jewelry_pricing_mode,
  count(case when table_name='jewelry_document_item' and column_name='product_type_snapshot' then 1 end)=0
    as missing_jewelry_product_type_snapshot,
  count(case when table_name='jewelry_document_item' and column_name='specification_snapshot' then 1 end)=0
    as missing_jewelry_specification_snapshot,
  count(case when table_name='jewelry_document_item' and column_name='other_fee1' then 1 end)=0
    as missing_jewelry_other_fee1,
  count(case when table_name='jewelry_document_item' and column_name='other_fee2' then 1 end)=0
    as missing_jewelry_other_fee2,
  count(case when table_name='jewelry_document_item' and column_name='other_fee3' then 1 end)=0
    as missing_jewelry_other_fee3
from information_schema.columns
where table_schema=database()
  and table_name in ('jewelry_product','jewelry_document','jewelry_document_item');

select
  count(*)=0 as missing_jewelry_cost_adjustment_lock_index
from information_schema.statistics
where table_schema=database()
  and table_name='jewelry_document_item'
  and index_name='idx_jewelry_item_product_document';

select 'invalid_jewelry_product_type' check_name, count(*) problem_rows
from jewelry_product
where product_type not in ('FINISHED','PART','ACCESSORY','WELFARE')
union all
select 'invalid_jewelry_specification', count(*)
from jewelry_product
where specification not in ('精品','普通');

select 'customer_missing_streamer' check_name, count(*) problem_rows
from live_customer c left join live_streamer s on s.streamer_id=c.streamer_id
where s.streamer_id is null
union all
select 'gift_orphan_customer', count(*)
from live_gift_record g left join live_customer c on c.customer_id=g.customer_id
where c.customer_id is null
union all
select 'gift_customer_streamer_mismatch', count(*)
from live_gift_record g join live_customer c on c.customer_id=g.customer_id
where g.streamer_id<>c.streamer_id
union all
select 'chat_orphan_customer', count(*)
from live_chat_contact x left join live_customer c on c.customer_id=x.customer_id
where c.customer_id is null
union all
select 'chat_customer_streamer_mismatch', count(*)
from live_chat_contact x join live_customer c on c.customer_id=x.customer_id
where x.streamer_id<>c.streamer_id
union all
select 'follow_orphan_customer', count(*)
from live_follow_record x left join live_customer c on c.customer_id=x.customer_id
where c.customer_id is null
union all
select 'follow_customer_streamer_mismatch', count(*)
from live_follow_record x join live_customer c on c.customer_id=x.customer_id
where x.streamer_id<>c.streamer_id
union all
select 'alias_orphan_customer', count(*)
from live_customer_alias a left join live_customer c on c.customer_id=a.customer_id
where c.customer_id is null
union all
select 'alias_customer_streamer_mismatch', count(*)
from live_customer_alias a join live_customer c on c.customer_id=a.customer_id
where a.streamer_id is null or a.streamer_id<>c.streamer_id
union all
select 'duplicate_active_customer_scope', count(*)
from (
  select nickname,streamer_id
  from live_customer
  where merged_into_id is null
  group by nickname,streamer_id
  having count(*)>1
) duplicate_scope;

select
  count(case when role_key='streamer' then 1 end)=0 as missing_streamer_role,
  count(case when role_key='operator' then 1 end)=0 as missing_operator_role,
  count(case when role_key='live_admin' then 1 end)=0 as missing_live_admin_role,
  count(case when role_key='jewelry_maker' then 1 end)=0 as missing_jewelry_maker_role,
  count(case when role_key='jewelry_reviewer' then 1 end)=0 as missing_jewelry_reviewer_role,
  count(case when role_key='jewelry_admin' then 1 end)=0 as missing_jewelry_admin_role
  ,count(case when role_key='company_owner' then 1 end)=0 as missing_company_owner_role
  ,count(case when role_key='project_user' then 1 end)=0 as missing_project_user_role
from sys_role
where del_flag='0' and role_key in
  ('streamer','operator','live_admin','jewelry_maker','jewelry_reviewer','jewelry_admin',
   'company_owner','project_user');

select 'project_owner_missing_member' check_name,count(*) problem_rows
from biz_project p
left join biz_project_member m on m.project_id=p.project_id
  and m.user_id=p.main_owner_user_id and m.status='0' and m.member_role='OWNER'
where p.del_flag='0' and m.member_id is null
union all
select 'project_orphan_parent',count(*)
from biz_project p left join biz_project parent on parent.project_id=p.parent_id and parent.del_flag='0'
where p.del_flag='0' and p.parent_id is not null and parent.project_id is null
union all
select 'project_orphan_member_user',count(*)
from biz_project_member m left join sys_user u on u.user_id=m.user_id and u.del_flag='0'
where m.status='0' and u.user_id is null
union all
select 'project_orphan_initiator_user',count(*)
from biz_project p left join sys_user u on u.user_id=p.initiator_user_id and u.del_flag='0'
where p.del_flag='0' and u.user_id is null
union all
select 'project_orphan_acceptance',count(*)
from biz_project_acceptance a left join biz_project p on p.project_id=a.project_id and p.del_flag='0'
where p.project_id is null;

select
  count(case when r.role_key='jewelry_maker' and m.perms='jewelry:product:add' then 1 end)=0
    as missing_jewelry_maker_product_add_permission,
  count(case when r.role_key='jewelry_maker' and m.perms='jewelry:product:edit' then 1 end)>0
    as jewelry_maker_product_edit_mismatch,
  count(case when r.role_key='jewelry_reviewer'
    and m.perms in ('jewelry:product:add','jewelry:product:edit') then 1 end)>0
    as jewelry_reviewer_product_write_mismatch,
  count(case when r.role_key='jewelry_admin' and m.perms='jewelry:product:add' then 1 end)=0
    as missing_jewelry_admin_product_add_permission,
  count(case when r.role_key='jewelry_admin' and m.perms='jewelry:product:edit' then 1 end)=0
    as missing_jewelry_admin_product_edit_permission
from sys_role r
left join sys_role_menu rm on rm.role_id=r.role_id
left join sys_menu m on m.menu_id=rm.menu_id
where r.del_flag='0'
  and r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin');

select
  count(case when r.role_key='company_owner' and m.perms='business:staff:list' then 1 end)=0
    as missing_company_owner_staff_list,
  count(case when r.role_key='company_owner' and m.perms='business:staff:manage' then 1 end)=0
    as missing_company_owner_staff_manage,
  count(case when r.role_key='company_owner' and m.perms='business:department:list' then 1 end)=0
    as missing_company_owner_department_list,
  count(case when r.role_key='company_owner' and m.perms='business:department:manage' then 1 end)=0
    as missing_company_owner_department_manage
from sys_role r
left join sys_role_menu rm on rm.role_id=r.role_id
left join sys_menu m on m.menu_id=rm.menu_id
where r.del_flag='0' and r.role_key='company_owner';

select count(*) as business_route_name_mismatch
from sys_menu
where menu_id between 4000 and 4005
  and route_name<>case menu_id
    when 4000 then 'Business'
    when 4001 then 'BusinessBoss'
    when 4002 then 'BusinessProjects'
    when 4003 then 'BusinessOwnerWorkbench'
    when 4004 then 'BusinessStaff'
    when 4005 then 'BusinessDepartments'
  end;

select
  count(case when column_name='leader_user_id' then 1 end)=0 as missing_department_leader_user_id
from information_schema.columns
where table_schema=database() and table_name='sys_dept';

select count(*)=0 as missing_department_leader_user_index
from information_schema.statistics
where table_schema=database() and table_name='sys_dept'
  and index_name='idx_sys_dept_leader_user';

select 'department_orphan_leader_user' check_name,count(*) problem_rows
from sys_dept d
left join sys_user u on u.user_id=d.leader_user_id and u.del_flag='0'
where d.del_flag='0' and d.leader_user_id is not null and u.user_id is null
union all
select 'department_leader_snapshot_mismatch',count(*)
from sys_dept d
join sys_user u on u.user_id=d.leader_user_id and u.del_flag='0'
where d.del_flag='0' and (
  d.leader<>coalesce(nullif(u.nick_name,''),u.user_name)
  or coalesce(d.phone,'')<>coalesce(u.phonenumber,'')
  or coalesce(d.email,'')<>coalesce(u.email,'')
);

select count(*)=0 as missing_staff_profile_table
from information_schema.tables
where table_schema=database() and table_name='biz_staff_profile';

select 'missing_staff_profile' check_name,count(*) problem_rows
from sys_user u left join biz_staff_profile p on p.user_id=u.user_id
where u.del_flag='0' and p.user_id is null
union all
select 'invalid_staff_manager',count(*)
from biz_staff_profile p left join sys_user manager on manager.user_id=p.manager_user_id and manager.del_flag='0'
where p.manager_user_id is not null and (manager.user_id is null or p.manager_user_id=p.user_id);

select
  count(case when dept_id=100 and parent_id=0 and dept_name='美丸集团' then 1 end)=0
    as missing_meimaru_group_root,
  count(case when dept_id=110 and parent_id=100 and dept_name='上海美丸文化公司' then 1 end)=0
    as missing_shanghai_meimaru_company,
  count(case when dept_id=111 and parent_id=100 and dept_name='越南meimaru公司' then 1 end)=0
    as missing_vietnam_meimaru_company
from sys_dept
where del_flag='0' and dept_id in (100,110,111);

select count(*) as active_default_departments
from sys_dept
where dept_id between 101 and 109 and del_flag='0';

select count(*) as system_users_in_retired_departments
from sys_user
where dept_id between 101 and 109 and del_flag='0';

select 'jianglan_owner_binding_mismatch' check_name,
  if(count(*)=1,0,abs(count(*)-1)) problem_rows
from sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id and r.role_key='company_owner' and r.del_flag='0'
where u.del_flag='0' and u.user_name in ('jianglan','GLY-jl')
union all
select 'wangfuzhang_owner_binding_mismatch',
  if(count(*)=1,0,abs(count(*)-1))
from sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id and r.role_key='company_owner' and r.del_flag='0'
where u.del_flag='0' and u.user_name='wangfuzhang'
union all
select 'jianglan_display_name_encoding_error',count(*)
from sys_user u
where u.del_flag='0' and u.user_name in('jianglan','GLY-jl')
  and hex(u.nick_name)='C3A6C2B1C29FC3A6C2BEC29C'
union all
select 'owner_outside_group_root',count(*)
from sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id and r.role_key='company_owner' and r.del_flag='0'
where u.del_flag='0' and u.dept_id<>100;

select
  coalesce(sum(case when r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin') and u.dept_id<>110 then 1 else 0 end),0)
    as jewelry_staff_company_mismatch,
  coalesce(sum(case when r.role_key='streamer' and u.dept_id<>111 then 1 else 0 end),0)
    as streamer_company_mismatch
from sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id
where u.del_flag='0' and r.del_flag='0'
  and r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin','streamer')
  and not exists (
    select 1
    from sys_user_role protected_ur
    join sys_role protected_r on protected_r.role_id=protected_ur.role_id
      and protected_r.del_flag='0'
      and protected_r.role_key in ('admin','company_owner')
    where protected_ur.user_id=u.user_id
  );

select count(*) as missing_live_menu_translation
from sys_menu
where menu_id between 2000 and 2048
  and (menu_name_vi is null or trim(menu_name_vi)='');

select count(*)=0 as missing_project_management_mode
from information_schema.columns
where table_schema=database() and table_name='biz_project' and column_name='management_mode';

select 'missing_project_routine_table' check_name,
  count(*)=0 problem_rows
from information_schema.tables
where table_schema=database() and table_name='biz_project_routine'
union all
select 'missing_project_routine_report_table',
  count(*)=0
from information_schema.tables
where table_schema=database() and table_name='biz_project_routine_report';

select 'missing_project_owner_role' check_name,count(*)=0 problem_rows
from sys_role where role_key='project_owner' and del_flag='0'
union all
select 'missing_employee_work_menu',count(*)=0
from sys_menu where menu_id=4007 and route_name='BusinessWorkSchedule'
  and perms='business:project:work:view' and status='0';

select 'ordinary_member_has_owner_workbench' check_name,count(*) problem_rows
from sys_role r join sys_role_menu rm on rm.role_id=r.role_id
where r.role_key='project_user' and r.del_flag='0' and rm.menu_id in(4002,4003)
union all
select 'project_owner_missing_role',count(*)
from biz_project p
where p.del_flag='0'
  and not exists(select 1 from sys_user_role ur join sys_role r on r.role_id=ur.role_id
    where ur.user_id=p.main_owner_user_id and r.role_key in('admin','company_owner','project_owner') and r.del_flag='0');

select 'missing_project_effort_report_table' check_name,
  count(*)=0 problem_rows
from information_schema.tables
where table_schema=database() and table_name='biz_project_effort_report';

select 'missing_project_effort_permission' check_name,count(*)=0 problem_rows
from sys_menu where menu_id=4017 and perms='business:project:allocation' and status='0'
union all
select 'project_owner_missing_effort_permission',count(*)
from sys_role r
where r.role_key='project_owner' and r.del_flag='0'
  and not exists(select 1 from sys_role_menu rm where rm.role_id=r.role_id and rm.menu_id=4017);

select 'missing_staff_leave_table' check_name,
  count(*)=0 problem_rows
from information_schema.tables
where table_schema=database() and table_name='biz_staff_leave';

select 'missing_project_relation_table' check_name,
  count(*)=0 problem_rows
from information_schema.tables
where table_schema=database() and table_name='biz_project_relation'
union all
select 'duplicate_active_project_execution_source',count(*)
from (
  select active_key from biz_project_relation
  where status='0' and active_key is not null
  group by active_key having count(*)>1
) duplicate_relation;

select 'missing_ai_conversation_table' check_name,count(*)=0 problem_rows
from information_schema.tables where table_schema=database() and table_name='biz_ai_conversation'
union all
select 'missing_ai_message_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_message'
union all
select 'missing_ai_run_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_run'
union all
select 'missing_ai_tool_call_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_tool_call'
union all
select 'missing_ai_action_request_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_action_request'
union all
select 'missing_ai_audit_log_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_audit_log'
union all
select 'missing_ai_workflow_instance_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_workflow_instance'
union all
select 'missing_ai_workflow_event_table',count(*)=0
from information_schema.tables where table_schema=database() and table_name='biz_ai_workflow_event'
union all
select 'missing_boss_ai_page_menu',count(*)=0
from sys_menu where menu_id=4008 and path='boss-ai' and component='business/ai/index'
  and route_name='BusinessBossAi' and perms='business:boss:view' and status='0';
