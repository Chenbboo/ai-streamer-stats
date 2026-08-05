-- 商品类型统一为四种固定类型，原“规格”字段直接替换为“精品/普通”规格类型。
-- 历史规格无法可靠映射为精品时统一归入“普通”。

update jewelry_product
set product_type = 'FINISHED'
where product_type is null
   or product_type = ''
   or product_type not in ('FINISHED', 'PART', 'ACCESSORY', 'WELFARE');

update jewelry_product
set specification = '普通'
where specification is null
   or specification not in ('精品', '普通');

alter table jewelry_product
  modify column product_type varchar(16) not null default 'FINISHED'
    comment 'FINISHED成品商品 PART散件商品 ACCESSORY配件商品 WELFARE福利商品',
  modify column specification varchar(16) not null default '普通'
    comment '规格类型：精品或普通';
