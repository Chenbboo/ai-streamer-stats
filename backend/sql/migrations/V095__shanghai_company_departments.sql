-- 上海公司下设七个部门；保留已有同名部门及所有人员归属，可重复执行。
insert into sys_dept(parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time)
select company.dept_id, concat(company.ancestors, ',', company.dept_id), requested.dept_name,
       requested.order_num, '0', '0', 'migration-v095', sysdate()
from sys_dept company
cross join (
  select '运营部' as dept_name, 1 as order_num
  union all select '商务部', 2
  union all select 'IT部', 3
  union all select 'AI视频内容部', 4
  union all select '人事部', 5
  union all select '财务部', 6
  union all select '外部人员', 7
) requested
where company.dept_id=110 and company.parent_id=100
  and company.dept_name in ('上海美丸文化公司', '上海每丸文化公司')
  and company.del_flag='0' and company.status='0'
  and not exists (
    select 1 from sys_dept existing
    where existing.parent_id=company.dept_id
      and existing.dept_name=requested.dept_name and existing.del_flag='0'
  );
