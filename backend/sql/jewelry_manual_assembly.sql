-- 珠宝ERP：商品图片、商品类型和手工组装。
-- 可重复执行，不会清空采购、销售、库存和单据数据。

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_product' and column_name='product_type') = 0,
  "alter table jewelry_product add column product_type varchar(16) not null default 'FINISHED' comment 'FINISHED成品商品 PART散件商品 ACCESSORY配件商品 WELFARE福利商品' after product_name",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_product' and column_name='image_urls') = 0,
  "alter table jewelry_product add column image_urls text comment '商品实物图片，逗号分隔' after image_url",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document' and column_name='labor_fee') = 0,
  "alter table jewelry_document add column labor_fee decimal(18,2) not null default 0 comment '组装人工费总额' after risk_status",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document' and column_name='processing_fee') = 0,
  "alter table jewelry_document add column processing_fee decimal(18,2) not null default 0 comment '组装加工费总额' after labor_fee",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document' and column_name='other_fee') = 0,
  "alter table jewelry_document add column other_fee decimal(18,2) not null default 0 comment '组装其他费用总额' after processing_fee",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item' and column_name='item_role') = 0,
  "alter table jewelry_document_item add column item_role varchar(16) not null default 'NORMAL' comment 'NORMAL普通 COMPONENT散件 OUTPUT成品' after product_id",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document_item' and column_name='image_urls') = 0,
  "alter table jewelry_document_item add column image_urls text comment '采购或成品参考图片，逗号分隔' after product_name_snapshot",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 清理由未发布AI试验版本创建的空配置和表。
delete from sys_config where config_key like 'jewelry.ai.image.%';
delete from sys_role_menu where menu_id in (3116,3117);
delete from sys_menu where menu_id in (3116,3117);
drop table if exists jewelry_ai_design_result;
drop table if exists jewelry_ai_design_component;
drop table if exists jewelry_ai_design;

set @sql = if(
  (select count(*) from information_schema.columns
   where table_schema=database() and table_name='jewelry_document' and column_name='design_id') > 0,
  "alter table jewelry_document drop column design_id",
  'select 1'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

insert into sys_menu(
  menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark,menu_name_vi
)
values (3010,'组装管理',3000,7,'assembly','jewelry/assembly/index','','',1,0,'C','0','0',
  'jewelry:assembly:list','build','admin',sysdate(),'',null,'散件组装成品','Lắp ráp')
on duplicate key update menu_name=values(menu_name),path=values(path),component=values(component),
  perms=values(perms),icon=values(icon),remark=values(remark),menu_name_vi=values(menu_name_vi);

insert into sys_menu(
  menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark,menu_name_vi
)
values (3115,'新建组装单',3010,1,'#','','','',1,0,'F','0','0',
  'jewelry:assembly:add','#','admin',sysdate(),'',null,'','Tạo phiếu lắp ráp')
on duplicate key update menu_name=values(menu_name),perms=values(perms),menu_name_vi=values(menu_name_vi);

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r
join sys_menu m on m.menu_id=3010
where r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin') and r.del_flag='0';

insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r
join sys_menu m on m.menu_id=3115
where r.role_key in ('jewelry_maker','jewelry_admin') and r.del_flag='0';
