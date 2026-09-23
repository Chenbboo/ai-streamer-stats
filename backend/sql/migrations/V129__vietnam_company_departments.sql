-- Add the requested departments beneath the existing Vietnam company.
-- Preserve existing departments and staff assignments; safe to run again.
insert into sys_dept(parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time)
select company.dept_id, concat(company.ancestors, ',', company.dept_id), requested.dept_name,
       requested.order_num, '0', '0', 'migration-v129', sysdate()
from sys_dept company
cross join (
  select '团播部' as dept_name, 1 as order_num
  union all select '人事部', 2
  union all select '电商部', 3
) requested
where company.dept_id = 111 and company.parent_id = 100
  and company.dept_name = '越南meimaru公司'
  and company.del_flag = '0' and company.status = '0'
  and not exists (
    select 1 from sys_dept existing
    where existing.parent_id = company.dept_id
      and existing.dept_name = requested.dept_name and existing.del_flag = '0'
  );
