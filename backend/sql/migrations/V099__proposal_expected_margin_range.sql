-- A small positive revenue can produce a loss percentage below -100000%.
-- Preserve four decimal places and existing values; do not cap the calculated margin.
set @proposal_margin_sql=(select if(numeric_precision-numeric_scale<26,
 'alter table biz_project_proposal modify column expected_margin decimal(30,4) null comment ''预计利润率百分比''',
 'select 1') from information_schema.columns where table_schema=database()
 and table_name='biz_project_proposal' and column_name='expected_margin');
prepare proposal_margin_stmt from @proposal_margin_sql;
execute proposal_margin_stmt;
deallocate prepare proposal_margin_stmt;
