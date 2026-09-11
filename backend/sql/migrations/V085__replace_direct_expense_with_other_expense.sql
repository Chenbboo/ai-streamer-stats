-- 手工支出使用“其他费用”兜底；旧“项目直接费用”仅保留历史流水引用，不再允许新增。
insert into biz_fact_category
  (category_code,category_name,fact_kind,default_sign,unit_type,status,sort_order,remark)
values
  ('OTHER_EXPENSE','其他费用','COST',-1,'MONEY','0',190,'无法归入常用类别的其他项目支出')
on duplicate key update
  category_name=values(category_name),fact_kind=values(fact_kind),default_sign=values(default_sign),
  unit_type=values(unit_type),status='0',sort_order=values(sort_order),remark=values(remark);

update biz_fact_category
set status='1',remark='历史类别：已由其他费用替代'
where category_code='DIRECT_EXPENSE';
