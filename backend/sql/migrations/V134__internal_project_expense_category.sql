-- 负责人花费表单新增内部项目支出；计入成本，并在工作台花费明细中单独展示。
insert into biz_fact_category
  (category_code,category_name,fact_kind,default_sign,unit_type,status,sort_order,remark)
values
  ('INTERNAL_PROJECT_COST','内部项目支出','COST',-1,'MONEY','0',180,'负责人填写的内部项目支出，不包含自动核算的人员成本、奖金及公共费用')
on duplicate key update
  category_name=values(category_name),fact_kind=values(fact_kind),default_sign=values(default_sign),
  unit_type=values(unit_type),status='0',sort_order=values(sort_order),remark=values(remark);
