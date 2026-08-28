-- 销售出库入账后，将组合中的搭售散件绑定到达人和主商品。

create table if not exists jewelry_influencer_bundle_item (
  bundle_item_id bigint not null auto_increment comment '绑定ID',
  influencer_id bigint not null comment '达人/主播ID',
  main_product_id bigint not null comment '组合主商品ID',
  addon_product_id bigint not null comment '搭售散件商品ID',
  main_qty int not null comment '来源组合主商品数量',
  addon_qty int not null comment '来源组合搭售商品数量',
  pricing_mode varchar(16) not null default 'INCLUDED' comment 'INCLUDED/SEPARATE',
  source_document_id bigint not null comment '最近来源销售出库单ID',
  last_sale_time datetime not null comment '最近销售入账时间',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key (bundle_item_id),
  unique key uk_jewelry_influencer_bundle_item (influencer_id,main_product_id,addon_product_id),
  key idx_jewelry_influencer_bundle_main (influencer_id,main_product_id),
  key idx_jewelry_influencer_bundle_source (source_document_id)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP达人搭售散件绑定';

-- 用已经入账且未红冲的历史销售组合初始化绑定；同一关系以最新销售单为准。
insert into jewelry_influencer_bundle_item(influencer_id,main_product_id,addon_product_id,
  main_qty,addon_qty,pricing_mode,source_document_id,last_sale_time,create_by,create_time,update_by,update_time)
select doc.influencer_id,main.product_id,addon.product_id,main.qty,addon.qty,
  coalesce(addon.pricing_mode,'INCLUDED'),doc.document_id,coalesce(doc.update_time,doc.create_time,sysdate()),
  'migration',sysdate(),'migration',sysdate()
from jewelry_document doc
join jewelry_document_item main on main.document_id=doc.document_id
  and main.sale_role='MAIN' and main.bundle_group_no is not null
join jewelry_document_item addon on addon.document_id=doc.document_id
  and addon.bundle_group_no=main.bundle_group_no and addon.sale_role='ADDON'
where doc.doc_type='SALES_OUT' and doc.status='POSTED' and doc.influencer_id is not null
  and not exists(
    select 1 from jewelry_document newer_doc
    join jewelry_document_item newer_main on newer_main.document_id=newer_doc.document_id
      and newer_main.sale_role='MAIN' and newer_main.product_id=main.product_id
    join jewelry_document_item newer_addon on newer_addon.document_id=newer_doc.document_id
      and newer_addon.bundle_group_no=newer_main.bundle_group_no
      and newer_addon.sale_role='ADDON' and newer_addon.product_id=addon.product_id
    where newer_doc.doc_type='SALES_OUT' and newer_doc.status='POSTED'
      and newer_doc.influencer_id=doc.influencer_id and newer_doc.document_id>doc.document_id
  )
on duplicate key update main_qty=values(main_qty),addon_qty=values(addon_qty),
  pricing_mode=values(pricing_mode),source_document_id=values(source_document_id),
  last_sale_time=values(last_sale_time),update_by=values(update_by),update_time=values(update_time);
