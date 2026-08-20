-- 采购单价支持4位小数，采购行金额和采购总额同步保存4位小数。
-- 其他单据仍由应用层按2位小数写入，扩大字段精度不会改变其展示和计算口径。
alter table jewelry_document
  modify column total_amount decimal(20,4) not null default 0,
  modify column total_cost decimal(20,4) not null default 0;

alter table jewelry_document_item
  modify column amount decimal(20,4) not null default 0,
  modify column cost_amount decimal(20,4) not null default 0;
