-- Match the source project's 160-character name; preserve existing snapshots and amounts.
set @public_name_sql=(select if(character_maximum_length<160,
 'alter table biz_public_expense_project modify column project_name varchar(160) not null','select 1')
 from information_schema.columns where table_schema=database()
 and table_name='biz_public_expense_project' and column_name='project_name');
prepare public_name_stmt from @public_name_sql;
execute public_name_stmt;
deallocate prepare public_name_stmt;
