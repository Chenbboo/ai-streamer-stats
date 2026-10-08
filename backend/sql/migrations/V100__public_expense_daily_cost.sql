-- Historical settled bills retain their immutable monthly recognition.
set @daily_mode_sql=(select if(count(*)=0,
 'alter table biz_public_expense_month add column recognition_mode varchar(24) not null default ''DAILY_V1''',
 'select 1') from information_schema.columns where table_schema=database()
 and table_name='biz_public_expense_month' and column_name='recognition_mode');
set @daily_mode_new=(@daily_mode_sql!='select 1');
prepare daily_mode_stmt from @daily_mode_sql;
execute daily_mode_stmt;
deallocate prepare daily_mode_stmt;
update biz_public_expense_month set recognition_mode='MONTHLY_V1' where @daily_mode_new and status='SETTLED';
create table if not exists biz_public_expense_daily (
 bill_id bigint not null,project_id bigint not null,biz_date date not null,
 amount decimal(20,2) not null,status varchar(16) not null,
 update_time datetime not null default current_timestamp on update current_timestamp,
 primary key(bill_id,project_id,biz_date),key idx_public_daily_project(project_id,biz_date)
) engine=InnoDB default charset=utf8mb4;
