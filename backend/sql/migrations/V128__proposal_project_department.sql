-- The proposal's department is chosen independently of the project owner's account.
-- Legacy rows remain nullable; this migration can be run again safely.
set @proposal_department_sql=(select if(count(*)=0,
  'alter table biz_project_proposal add column department_id bigint null comment ''所选项目归属部门'' after company_dept_id',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal' and column_name='department_id');
prepare proposal_department_stmt from @proposal_department_sql;
execute proposal_department_stmt;
deallocate prepare proposal_department_stmt;

set @project_department_sql=(select if(count(*)=0,
  'alter table biz_project add column department_id bigint null comment ''所选项目归属部门'' after company_dept_id',
  'select 1') from information_schema.columns where table_schema=database()
    and table_name='biz_project' and column_name='department_id');
prepare project_department_stmt from @project_department_sql;
execute project_department_stmt;
deallocate prepare project_department_stmt;
