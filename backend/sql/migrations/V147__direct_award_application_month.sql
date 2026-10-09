set @has_column := (select count(*) from information_schema.columns where table_schema=database()
and table_name='biz_incentive_award' and column_name='application_month');
set @sql := if(@has_column=0,'alter table biz_incentive_award add column application_month varchar(7) default null','select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
