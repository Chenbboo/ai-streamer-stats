-- KPI score-based HR bonus rules. Existing FIXED_V1 rules and awards retain their values.
set @kpi_bonus_sql=(select if(count(*)=0,
  'alter table biz_incentive_rule add column kpi_plan_id bigint default null comment ''Bound independent KPI plan for SCORE_TIERS_V1''',
  'select 1') from information_schema.columns where table_schema=database()
  and table_name='biz_incentive_rule' and column_name='kpi_plan_id');
prepare kpi_bonus_stmt from @kpi_bonus_sql;
execute kpi_bonus_stmt;
deallocate prepare kpi_bonus_stmt;

create table if not exists biz_incentive_tier (
  rule_id bigint not null,
  sort_order int not null,
  min_score decimal(9,2) not null,
  max_score decimal(9,2) default null,
  amount decimal(20,2) not null,
  primary key(rule_id,sort_order)
) engine=InnoDB default charset=utf8mb4 comment='Immutable score intervals and bonus amounts for each HR rule version';

-- Rollback: keep the column and tier data; stop SCORE_TIERS_V1 writes until compatible code is restored.
