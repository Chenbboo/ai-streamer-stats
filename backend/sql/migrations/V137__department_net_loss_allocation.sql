-- Business departments provide direct project costs; IT provides its offset monthly loss.
-- Preserve the classification when a department is renamed or this migration is repeated.
set @source_sql=(select if(count(*)=0,
 'alter table sys_dept add column public_cost_source varchar(24) not null default ''STAFF_REMAINDER''',
 'select 1') from information_schema.columns where table_schema=database()
 and table_name='sys_dept' and column_name='public_cost_source');
set @source_new=(@source_sql!='select 1');
prepare source_stmt from @source_sql; execute source_stmt; deallocate prepare source_stmt;
update sys_dept department join sys_dept company on company.dept_id=department.parent_id and company.parent_id=100
 set department.public_cost_source=case when department.dept_name='IT部' then 'IT_NET_LOSS' else 'DIRECT_PROJECT' end
 where @source_new and department.del_flag='0' and department.dept_name in('运营部','商务部','AI视频内容部','IT部');

-- Transfer credits are kept separately even when a source project also bears ordinary expenses.
set @transfer_sql=(select if(count(*)=0,
 'alter table biz_public_expense_daily add column it_transfer_amount decimal(20,2) not null default 0',
 'select 1') from information_schema.columns where table_schema=database()
 and table_name='biz_public_expense_daily' and column_name='it_transfer_amount');
prepare transfer_stmt from @transfer_sql; execute transfer_stmt; deallocate prepare transfer_stmt;
