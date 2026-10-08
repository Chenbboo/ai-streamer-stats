-- 立项人员可在启动前编排本人项目与既有项目的投入比例；计划在项目启动事务中应用。
set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='biz_project_proposal_staffing' and column_name='allocation_plan_json')=0,
  "alter table biz_project_proposal_staffing add column allocation_plan_json longtext null comment '立项时跨项目投入调整计划' after input_quantity",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
