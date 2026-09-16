-- Public personnel pool shares the existing monthly bill and settlement.
set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_public_expense_month' and column_name='personnel_amount')=0,'alter table biz_public_expense_month add column personnel_amount decimal(20,2) not null default 0','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_public_expense_month' and column_name='personnel_snapshot')=0,'alter table biz_public_expense_month add column personnel_snapshot longtext null','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_public_expense_owner' and column_name='cost_pool')=0,'alter table biz_public_expense_owner add column cost_pool varchar(16) not null default ''EXPENSE''','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_public_expense_owner' and column_name='dept_id')=0,'alter table biz_public_expense_owner add column dept_id bigint null','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl=if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_public_expense_owner' and column_name='dept_name')=0,'alter table biz_public_expense_owner add column dept_name varchar(100) null','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl=if((select count(*) from information_schema.statistics where table_schema=database() and table_name='biz_public_expense_owner' and index_name='uk_public_owner_pool')=0,'alter table biz_public_expense_owner drop index uk_public_owner, add unique key uk_public_owner_pool(bill_id,cost_pool,owner_user_id)','select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
