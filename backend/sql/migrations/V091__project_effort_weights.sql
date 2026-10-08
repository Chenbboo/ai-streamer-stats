-- Split one person's workday cost across concurrent projects instead of charging a full day to every project.
alter table biz_project_staff_allocation modify cost_policy_id bigint null comment '创建权重时的成本政策版本，可为空';

-- Preserve historical allocation periods and replace current/future periods with one complete 100% distribution.
update biz_project_staff_allocation a
join biz_project p on p.project_id=a.project_id and p.del_flag='0'
  and p.cost_policy_version='MEMBER_DAYS_V1' and coalesce(p.accounting_state,'OPEN')<>'CLOSED'
set a.effective_to=date_sub(current_date(),interval 1 day),a.update_by='system-weight-migration',
  a.update_time=sysdate(),a.version=a.version+1
where a.status='ACTIVE' and a.allocation_mode='PERCENTAGE'
  and a.effective_from<current_date()
  and (a.effective_to is null or a.effective_to>=current_date());

update biz_project_staff_allocation a
join biz_project p on p.project_id=a.project_id and p.del_flag='0'
  and p.cost_policy_version='MEMBER_DAYS_V1' and coalesce(p.accounting_state,'OPEN')<>'CLOSED'
set a.status='VOID',a.update_by='system-weight-migration',a.update_time=sysdate(),a.version=a.version+1
where a.status='ACTIVE' and a.allocation_mode='PERCENTAGE' and a.effective_from>=current_date();

insert into biz_project_staff_allocation(project_id,user_id,user_name,allocation_mode,allocation_value,
  effective_from,effective_to,cost_policy_id,exception_allowed,exception_reason,status,version,
  create_by,create_time,remark)
select p.project_id,m.user_id,m.user_name_snapshot,'PERCENTAGE',
  case when not exists(
    select 1 from biz_project_member earlier
    join biz_project earlier_project on earlier_project.project_id=earlier.project_id
      and earlier_project.del_flag='0' and earlier_project.status in ('PLANNING','ACTIVE','PAUSED','ACCEPTANCE')
      and earlier_project.cost_policy_version='MEMBER_DAYS_V1'
      and coalesce(earlier_project.accounting_state,'OPEN')<>'CLOSED'
    where earlier.user_id=m.user_id and earlier.status='0' and earlier.member_role in ('OWNER','DEPUTY','MEMBER')
      and (coalesce(earlier.joined_date,earlier_project.actual_start_date,earlier_project.plan_start_date,'1970-01-01')
            < coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,'1970-01-01')
        or (coalesce(earlier.joined_date,earlier_project.actual_start_date,earlier_project.plan_start_date,'1970-01-01')
            = coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,'1970-01-01')
          and earlier_project.project_id<p.project_id))
  ) then 100 else 0 end,
  greatest(current_date(),coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,current_date())),null,
  (select policy.policy_id from biz_staff_cost_policy policy
    where policy.user_id=m.user_id and policy.status='ACTIVE'
      and policy.effective_from<=greatest(current_date(),coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,current_date()))
      and (policy.effective_to is null or policy.effective_to>=greatest(current_date(),coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,current_date())))
    order by policy.effective_from desc,policy.policy_id desc limit 1),
  '0',null,'ACTIVE',0,'system-weight-migration',sysdate(),
  case when not exists(
    select 1 from biz_project_member earlier
    join biz_project earlier_project on earlier_project.project_id=earlier.project_id
      and earlier_project.del_flag='0' and earlier_project.status in ('PLANNING','ACTIVE','PAUSED','ACCEPTANCE')
      and earlier_project.cost_policy_version='MEMBER_DAYS_V1'
      and coalesce(earlier_project.accounting_state,'OPEN')<>'CLOSED'
    where earlier.user_id=m.user_id and earlier.status='0' and earlier.member_role in ('OWNER','DEPUTY','MEMBER')
      and (coalesce(earlier.joined_date,earlier_project.actual_start_date,earlier_project.plan_start_date,'1970-01-01')
            < coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,'1970-01-01')
        or (coalesce(earlier.joined_date,earlier_project.actual_start_date,earlier_project.plan_start_date,'1970-01-01')
            = coalesce(m.joined_date,p.actual_start_date,p.plan_start_date,'1970-01-01')
          and earlier_project.project_id<p.project_id))
  ) then '首次参与项目，系统默认投入权重100%' else '并行项目初始化为0%，待负责人重新分配' end
from biz_project_member m
join biz_project p on p.project_id=m.project_id and p.del_flag='0'
  and p.status in ('PLANNING','ACTIVE','PAUSED','ACCEPTANCE')
  and p.cost_policy_version='MEMBER_DAYS_V1' and coalesce(p.accounting_state,'OPEN')<>'CLOSED'
where m.status='0' and m.member_role in ('OWNER','DEPUTY','MEMBER');

-- Today's priced rows were created before project weights existed. Remove only that open-day cache;
-- the application rebuilds it from the new weights and preserves every earlier accounting snapshot.
delete cost from biz_project_member_day_cost cost
join biz_project p on p.project_id=cost.project_id and p.del_flag='0'
  and p.cost_policy_version='MEMBER_DAYS_V1' and coalesce(p.accounting_state,'OPEN')<>'CLOSED'
where cost.biz_date=current_date();
