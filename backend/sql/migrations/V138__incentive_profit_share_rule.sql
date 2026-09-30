-- Immutable profit-share rule snapshots. Historical fixed/KPI rules remain unchanged.
set @ddl = if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_incentive_rule' and column_name='after_tax_profit')=0,
  'alter table biz_incentive_rule add column after_tax_profit decimal(18,2) default null', 'select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl = if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_incentive_rule' and column_name='main_owner_bonus_rate')=0,
  'alter table biz_incentive_rule add column main_owner_bonus_rate decimal(7,4) default null', 'select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
set @ddl = if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_incentive_rule' and column_name='sponsor_owner_bonus_rate')=0,
  'alter table biz_incentive_rule add column sponsor_owner_bonus_rate decimal(7,4) default null', 'select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
-- Rollback: retain columns and published snapshots; disable new publication instead of deleting history.
