-- 部署后机器可判定门禁：只返回一个问题总数，必须为 0。
select sum(problem_rows) as total_problem_rows
from (
  select if(count(*)=2,0,1) problem_rows
  from sys_dept
  where del_flag='0' and status='0' and dept_id in (110,111)

  union all
  select if(count(*)=2,0,1)
  from sys_user u
  join sys_user_role ur on ur.user_id=u.user_id
  join sys_role r on r.role_id=ur.role_id
    and r.role_key='company_owner' and r.del_flag='0'
  where u.del_flag='0' and u.user_name in ('GLY-jl','jianglan','wangfuzhang')

  union all
  select if(count(*)=1,0,1)
  from sys_menu
  where menu_id=4008 and perms='business:boss:view' and status='0'

  union all
  select if(count(*)=25,0,1)
  from information_schema.tables
  where table_schema=database() and table_name in (
    'biz_project','biz_project_member','biz_project_task','biz_project_risk',
    'biz_project_acceptance','biz_project_routine','biz_project_routine_report',
    'biz_project_effort_report','biz_staff_leave','biz_operating_fact',
    'biz_project_daily_result','biz_ai_conversation','biz_ai_message',
    'biz_ai_run','biz_ai_tool_call','biz_ai_action_request',
    'biz_ai_workflow_instance','biz_ai_workflow_event',
    'biz_project_proposal','biz_project_proposal_event',
    'biz_project_kpi_plan','biz_project_kpi_plan_item','biz_project_bonus_tier',
    'biz_project_kpi_settlement','biz_project_kpi_result'
  )

  union all
  select if(count(*)=1,0,1)
  from information_schema.tables
  where table_schema=database() and table_name='biz_project_stage_acceptance'

  union all
  select if(count(*)=1,0,1)
  from information_schema.tables
  where table_schema=database() and table_name='biz_project_task_report'

  union all
  select if(count(*)=1,0,1)
  from information_schema.tables
  where table_schema=database() and table_name='biz_project_progress_report'

  union all
  select if(count(*)=6,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='biz_project' and column_name in ('close_method','management_reason','acceptance_criteria'))
      or (table_name='biz_project_proposal' and column_name in ('close_method','management_reason','acceptance_criteria')))

  union all
  select if(count(*)=6,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='biz_project_kpi_plan' and column_name in ('voided_user_id','voided_user_name','voided_time'))
      or (table_name='biz_project_kpi_settlement' and column_name in ('voided_user_id','voided_user_name','voided_time')))

  union all
  select if(count(*)=4,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_staff_cost_policy'
    and column_name in ('voided_user_id','voided_user_name','voided_time','void_reason')

  union all
  select if(count(*)=4,0,1)
  from information_schema.tables
  where table_schema=database() and table_name in (
    'jewelry_influencer','jewelry_influencer_product_price',
    'jewelry_influencer_price_history','jewelry_influencer_bundle_item'
  )

  union all
  select if(count(*)=8,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='jewelry_influencer'
        and column_name in ('influencer_code','external_influencer_id','influencer_name'))
      or (table_name='jewelry_document'
        and column_name in ('influencer_id','influencer_price_snapshot','influencer_price_version'))
      or (table_name='jewelry_document_item'
        and column_name in ('influencer_price_snapshot','influencer_price_version')))

  union all
  select if(count(*)=1,0,1)
  from information_schema.tables
  where table_schema=database() and table_name='biz_staff_leave_request'

  union all
  select if(count(*)=5,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='biz_staff_leave' and column_name='source_request_id')
      or (table_name='biz_staff_leave_request' and column_name in (
        'cancel_reviewed_user_id','cancel_reviewed_user_name',
        'cancel_reviewed_time','cancel_review_comment'
      )))

  union all
  select if(count(*)=4,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_operating_fact'
    and column_name in ('returned_user_id','returned_user_name','returned_time','return_reason')

  union all
  select if(count(*)=2 and sum(is_nullable='YES')=2,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_routine'
    and column_name in ('assignee_user_id','assignee_name')

  union all
  select if(count(*)=1,0,1)
  from sys_role
  where role_key='project_deputy' and del_flag='0' and status='0'

  union all
  select if(count(*)=6,0,1)
  from sys_role_menu role_menu
  join sys_role role on role.role_id=role_menu.role_id
  where role.role_key='project_deputy' and role.del_flag='0'
    and role_menu.menu_id in (4000,4002,4012,4013,4014,4017)

  union all
  select count(*)
  from information_schema.columns
  where table_schema=database()
    and table_name in ('biz_staff_leave','biz_staff_leave_request')
    and collation_name is not null and collation_name<>'utf8mb4_0900_ai_ci'

  union all
  select count(*)
  from biz_project
  where del_flag='0' and base_currency not regexp '^[A-Z]{3}$'

  union all
  select count(*)
  from biz_project_task task
  left join biz_project_member member on member.project_id=task.project_id
    and member.user_id=task.assignee_user_id and member.status='0'
  where task.assignee_user_id is not null and task.status<>'DONE' and member.member_id is null

  union all
  select count(*)
  from biz_project_routine routine
  left join biz_project_member member on member.project_id=routine.project_id
    and member.user_id=routine.assignee_user_id and member.status='0'
  where routine.status='ACTIVE' and routine.assignee_user_id is not null and member.member_id is null

  union all
  select count(*)
  from biz_project_staff_allocation allocation
  left join biz_project_member member on member.project_id=allocation.project_id
    and member.user_id=allocation.user_id and member.status='0'
  where allocation.status='ACTIVE'
    and (allocation.effective_to is null or allocation.effective_to>=curdate())
    and member.member_id is null

  union all
  select count(*)
  from biz_project
  where del_flag='0' and status in ('CLOSED','CANCELED') and actual_end_date is null

  union all
  select count(*)
  from biz_project_routine routine
  join biz_project project on project.project_id=routine.project_id and project.del_flag='0'
  where project.status in ('CLOSED','CANCELED') and routine.status='ACTIVE'

  union all
  select count(*)
  from biz_project_task task
  join biz_project project on project.project_id=task.project_id and project.del_flag='0'
  where project.status in ('CLOSED','CANCELED') and task.status not in ('DONE','CANCELED')

  union all
  select count(*)
  from biz_project_daily_result result_row
  join biz_project project on project.project_id=result_row.project_id and project.del_flag='0'
  where project.status in ('CLOSED','CANCELED') and result_row.is_current='1'
    and result_row.close_status<>'CLOSED'

  union all
  select if(count(*)=17,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal'
    and column_name in (
      'revenue_model','estimated_revenue','estimated_external_cost','estimated_personnel_cost',
      'estimated_bonus_cost','estimated_tax_cost','contingency_cost','estimated_total_cost',
      'expected_profit','expected_margin','break_even_revenue','peak_cash_need',
      'planned_headcount','funding_plan','key_assumptions','risk_summary','stop_loss_rule'
    )

  union all
  select if(count(*)=4,0,1)
  from information_schema.tables
  where table_schema=database() and table_name in (
    'biz_project_proposal_revenue','biz_project_proposal_expense',
    'biz_project_proposal_staffing','biz_project_proposal_target'
  )

  union all
  select if(count(*)=8,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal_staffing'
    and column_name in (
      'user_id','user_name','cost_policy_id','cost_policy_version','monthly_cost_snapshot',
      'standard_work_days_snapshot','daily_cost_snapshot','cost_currency'
    )

  union all
  select if(count(*)=1,0,1)
  from sys_menu
  where menu_id=4022 and parent_id=4004 and perms='business:staff:cost' and status='0'

  union all
  select count(*)
  from sys_role role
  where role.role_key='project_owner' and role.del_flag='0'
    and (
      not exists(select 1 from sys_role_menu rm where rm.role_id=role.role_id and rm.menu_id=4004)
      or exists(select 1 from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id
        where rm.role_id=role.role_id and m.perms='business:staff:cost')
      or not exists(select 1 from sys_role_menu rm where rm.role_id=role.role_id and rm.menu_id=4072)
      or exists(select 1 from sys_role_menu rm where rm.role_id=role.role_id and rm.menu_id=4021)
    )

  union all
  select if(count(*)=1,0,1)
  from information_schema.tables
  where table_schema=database() and table_name='biz_staff_menu_permission'

  union all
  select count(*)
  from biz_staff_menu_permission permission
  left join sys_user user on user.user_id=permission.user_id and user.del_flag='0'
  left join sys_menu menu on menu.menu_id=permission.menu_id
  where permission.access_level not in ('HIDDEN','READ','MAINTAIN')
    or user.user_id is null or menu.menu_id is null

  union all
  select if(count(*)=2,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='biz_project_kpi' and column_name='source_ref_id')
      or (table_name='biz_project_kpi_plan_item' and column_name='source_ref_id'))

  union all
  select count(*)
  from biz_project p
  where p.del_flag='0' and not exists (
    select 1 from sys_user_role ur
    join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
    where ur.user_id=p.main_owner_user_id
      and r.role_key in ('admin','company_owner','project_owner')
  )
  union all
  select if(count(*)=2,0,1) from information_schema.tables where table_schema=database()
    and table_name in ('biz_project_work_period','biz_project_routine_daily_target')
  union all
  select if(count(*)=17,0,1) from information_schema.columns where table_schema=database() and (
    (table_name in ('biz_project','biz_project_proposal') and column_name in ('budget_mode','daily_budget_limit','budget_scope','startup_budget_limit','goal_mode'))
    or (table_name='biz_project_routine' and column_name='target_mode')
    or (table_name='biz_project_task' and column_name='active_status')
    or (table_name='biz_project_event' and column_name in ('subject_user_id','subject_name','subject_account'))
    or (table_name in ('biz_project_proposal_revenue','biz_project_proposal_expense') and column_name='occurrence_type'))
  union all
  select if(count(*)=1,0,1) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='participation_mode'
  union all
  select count(*) from (select routine_id,biz_date from biz_project_routine_daily_target
    where status='CURRENT' group by routine_id,biz_date having count(*)>1) duplicate_target
  union all
  select if(count(*)=3,0,1) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal'
    and column_name in ('sponsor_owner_user_id','company_dept_id','plan_start_date')
    and is_nullable='YES'
  union all
  select if(count(*)=1,0,1) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='create_request_key'
  union all
  select if(count(*)=2,0,1) from information_schema.statistics where table_schema=database()
    and table_name='biz_project_proposal' and index_name='uk_proposal_create_request' and non_unique=0
    and ((seq_in_index=1 and column_name='applicant_user_id') or (seq_in_index=2 and column_name='create_request_key'))

  union all
  select if(count(*)=11,0,1)
  from information_schema.tables
  where table_schema=database() and table_name in (
    'biz_public_expense_policy','biz_public_expense_month','biz_public_expense_entry',
    'biz_public_expense_owner','biz_public_expense_project','biz_public_expense_adjustment',
    'biz_public_expense_event','biz_public_expense_daily','biz_company_profit_tax',
    'biz_company_profit_tax_event','biz_project_profit_tax_snapshot'
  )

  union all
  select if(count(*)=2,0,1)
  from information_schema.columns
  where table_schema=database()
    and ((table_name='biz_project_daily_result' and column_name='public_cost')
      or (table_name='biz_public_expense_month' and column_name='recognition_mode'))

  union all
  select if(count(*)=1,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_public_expense_project'
    and column_name='project_name' and character_maximum_length>=160

  union all
  select if(count(*)=1,0,1)
  from information_schema.columns
  where table_schema=database() and table_name='biz_project_proposal'
    and column_name='expected_margin' and numeric_precision-numeric_scale>=26

  union all
  select if(count(*)=1,0,1)
  from biz_fact_category
  where category_code='COMPANY_PUBLIC_COST' and fact_kind='COST' and status='0'

  union all
  select if(count(*)=1,0,1)
  from sys_menu child join sys_menu parent on parent.menu_id=child.parent_id
  where parent.parent_id=0 and parent.path='finance' and child.menu_type='C'
    and child.status='0' and child.path='public-expenses'
    and child.component='business/public-expenses/index'
  union all
  select if(count(*)=1,0,1) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='supplier_return_date' and data_type='date'
  union all
  select if(count(*)=5,0,1) from information_schema.columns where table_schema=database()
    and ((table_name='biz_public_expense_month' and column_name in ('personnel_amount','personnel_snapshot'))
      or (table_name='biz_public_expense_owner' and column_name in ('cost_pool','dept_id','dept_name')))
  union all
  select if(count(*)=3,0,1) from information_schema.statistics where table_schema=database()
    and table_name='biz_public_expense_owner' and index_name='uk_public_owner_pool' and non_unique=0
    and ((seq_in_index=1 and column_name='bill_id') or (seq_in_index=2 and column_name='cost_pool')
      or (seq_in_index=3 and column_name='owner_user_id'))
  union all
  select if(count(*)=2,0,1) from information_schema.tables where table_schema=database()
    and table_name in ('biz_company_access','biz_company_access_event')
  union all
  select if(count(*)=2,0,1) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name in ('source_warehouse','target_warehouse')
    and data_type='varchar' and character_maximum_length>=100
  union all
  select if(count(*)=1,0,1) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='allocation_plan_json' and data_type='longtext'
  union all
  select if(count(*)=1,0,1) from information_schema.tables where table_schema=database()
    and table_name='biz_project_subproject_funding'
  union all
  select if(count(*)=2,0,1) from biz_fact_category
    where category_code in ('SUBPROJECT_FUNDING_COST','SUBPROJECT_FUNDING_REVENUE') and status='0'
  union all
  select count(*) from biz_staff_menu_permission permission
    where exists(select 1 from sys_user_role ur join sys_role role on role.role_id=ur.role_id
      where ur.user_id=permission.user_id and role.role_key='company_owner' and role.del_flag='0')
  union all
  select count(*) from sys_role r
    cross join (select 'jewelry:product:edit' perm union all select 'jewelry:product:remove') required_perm
    where r.role_key in ('jewelry_maker','jewelry_admin') and r.del_flag='0'
      and not exists(select 1 from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id
        where rm.role_id=r.role_id and m.perms=required_perm.perm and m.status='0')
  union all
  select count(*) from sys_role r join sys_role_menu rm on rm.role_id=r.role_id
    join sys_menu m on m.menu_id=rm.menu_id
    where r.role_key='jewelry_reviewer' and r.del_flag='0'
      and m.perms in ('jewelry:product:edit','jewelry:product:basic-edit','jewelry:product:remove')
  union all
  select if(count(*)=4,0,1) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item'
    and ((column_name='biz_date' and data_type='date')
      or (column_name='supplier_id' and data_type='bigint')
      or (column_name='supplier_name_snapshot' and data_type='varchar' and character_maximum_length>=128)
      or (column_name='sample_goods_no' and data_type='varchar' and character_maximum_length>=64))
  union all
  select if(count(*)=1,0,1) from information_schema.columns where table_schema=database()
    and table_name='biz_project_progress_report' and column_name='evidence_text'
    and data_type='varchar' and character_maximum_length>=2000
  union all
  select if(count(*)=1,0,1) from information_schema.tables where table_schema=database()
    and table_name='jewelry_supplier_return_mail_log'
) release_gate;
