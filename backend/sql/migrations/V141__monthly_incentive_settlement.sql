-- V141: New profit-share plans are immutable monthly snapshots. Do not relabel historical cumulative plans.
set @ddl = if((select count(*) from information_schema.columns where table_schema=database() and table_name='biz_incentive_rule' and column_name='settlement_month')=0,
  'alter table biz_incentive_rule add column settlement_month varchar(7) default null', 'select 1');
prepare stmt from @ddl; execute stmt; deallocate prepare stmt;
-- Awards, member allocations and payments inherit the month from their immutable source rule.
-- Rollback: keep this nullable column and historical snapshots; disable new monthly publication instead.
