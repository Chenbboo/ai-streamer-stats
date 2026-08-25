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
  select count(*)
  from biz_project p
  where p.del_flag='0' and not exists (
    select 1 from sys_user_role ur
    join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
    where ur.user_id=p.main_owner_user_id
      and r.role_key in ('admin','company_owner','project_owner')
  )
) release_gate;
