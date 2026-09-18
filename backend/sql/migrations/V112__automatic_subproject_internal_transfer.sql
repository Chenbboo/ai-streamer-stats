-- 子项目启动时自动生成内部拨款的双边经营事实。
-- 主项目记拨款支出，子项目记拨款收入；公司合并汇总抵销双方记录。
insert into biz_fact_category
  (category_code,category_name,fact_kind,default_sign,unit_type,sort_order,status)
values
  ('SUBPROJECT_FUNDING_COST','子项目拨款支出','COST',-1,'MONEY',155,'0'),
  ('SUBPROJECT_FUNDING_REVENUE','主项目拨款收入','REVENUE',1,'MONEY',45,'0')
on duplicate key update
  category_name=values(category_name),
  fact_kind=values(fact_kind),
  default_sign=values(default_sign),
  unit_type=values(unit_type),
  sort_order=values(sort_order),
  status='0';
