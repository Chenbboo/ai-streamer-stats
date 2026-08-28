-- 部署后机器可判定门禁：只返回一个问题总数，必须为 0。
select sum(problem_rows) as total_problem_rows
from (
  select if(count(*)=2,0,1) problem_rows
  from sys_dept
  where del_flag='0' and status='0'
    and ((dept_id=110 and dept_name='上海美丸文化公司')
      or (dept_id=111 and dept_name='越南meimaru公司'))

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
  select count(*)
  from biz_project p
  where p.del_flag='0' and not exists (
    select 1 from sys_user_role ur
    join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
    where ur.user_id=p.main_owner_user_id
      and r.role_key in ('admin','company_owner','project_owner')
  )
) release_gate;
