-- 子项目拨款采用内部转拨口径：主项目确认支出，子项目确认收入。
insert into biz_fact_category(category_code,category_name,fact_kind,default_sign,unit_type,sort_order) values
('SUBPROJECT_FUNDING_COST','子项目拨款支出','COST',-1,'MONEY',155),
('SUBPROJECT_FUNDING_REVENUE','主项目拨款收入','REVENUE',1,'MONEY',45)
on duplicate key update category_name=values(category_name),fact_kind=values(fact_kind),
  default_sign=values(default_sign),unit_type=values(unit_type),sort_order=values(sort_order);
