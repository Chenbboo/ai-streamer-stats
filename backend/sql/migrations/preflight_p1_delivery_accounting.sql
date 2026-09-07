-- P1 专项只读预检：在已完成 V062 的目标库执行，核对 database() 并先备份。
-- 记录状态分布及金额摘要，迁移后应保持一致。不要以本机开发库代替生产验收。
select database() as target_database;
select status,count(*) as project_count from biz_project group by status;
select status,currency,count(*) as fact_count,sum(amount) as amount_sum
from biz_operating_fact group by status,currency;
select status,currency,count(*) as settlement_count,sum(bonus_amount) as bonus_sum
from biz_project_kpi_settlement group by status,currency;

-- 以下异常需逐项核查；不能靠忽略待办或重开历史账解决。
select project_id,project_no,status,actual_end_date
from biz_project where del_flag='0' and status in ('CLOSED','CANCELED') and actual_end_date is null;
select p.project_id,p.plan_id,p.status as plan_status
from biz_project_kpi_plan p
where p.status<>'VOIDED' and (p.status='DRAFT' or not exists(
    select 1 from biz_project_kpi_settlement s where s.plan_id=p.plan_id));

-- 新关账权限应挂在唯一的收支菜单下；若结果不是1，先核对菜单配置。
select count(*) as accounting_parent_menu_count from sys_menu
where perms='business:accounting:list' and menu_type='C';
