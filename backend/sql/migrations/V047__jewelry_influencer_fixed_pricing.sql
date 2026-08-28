-- 达人/主播档案、达人商品固定价与价格历史。

create table if not exists jewelry_influencer (
  influencer_id bigint not null auto_increment comment '主键ID',
  influencer_code varchar(32) not null comment '达人编码',
  external_influencer_id varchar(64) default '' comment '达人ID',
  influencer_name varchar(128) not null comment '达人/主播名称',
  platform varchar(64) default '' comment '平台',
  platform_account varchar(128) default '' comment '平台账号',
  sales_channel varchar(64) default '' comment '默认销售渠道',
  last_sale_time datetime default null comment '最近销售入账时间',
  contact_phone varchar(32) default '' comment '联系电话',
  status char(1) not null default '0' comment '0启用 1停用',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (influencer_id),
  unique key uk_jewelry_influencer_code (influencer_code),
  key idx_jewelry_influencer_name (influencer_name),
  key idx_jewelry_influencer_platform (platform,platform_account)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP达人主播档案';

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_influencer' and column_name='external_influencer_id')=0,
  "alter table jewelry_influencer add column external_influencer_id varchar(64) default '' comment '达人ID' after influencer_code",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

create table if not exists jewelry_influencer_product_price (
  price_id bigint not null auto_increment comment '价格ID',
  influencer_id bigint not null comment '达人主键ID',
  product_id bigint not null comment '商品ID',
  fixed_unit_price decimal(18,4) not null comment '固定成交单价或待生效价格',
  price_status varchar(16) not null comment 'PENDING/PRICED',
  price_version int not null default 0 comment '正式价格版本，待生效为0',
  pending_source_document_id bigint default null comment '待生效来源销售单ID',
  price_source_document_id bigint default null comment '当前正式价格来源销售单ID',
  price_effective_time datetime default null comment '当前正式价格生效时间',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key (price_id),
  unique key uk_jewelry_influencer_product (influencer_id,product_id),
  key idx_jewelry_influencer_price_pending (pending_source_document_id),
  key idx_jewelry_influencer_product_status (influencer_id,price_status)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP达人商品固定价';

create table if not exists jewelry_influencer_price_history (
  history_id bigint not null auto_increment comment '历史ID',
  influencer_id bigint not null comment '达人ID',
  product_id bigint not null comment '商品ID',
  old_price decimal(18,4) default null comment '原固定价',
  new_price decimal(18,4) not null comment '新固定价',
  source_type varchar(24) not null comment 'FIRST_SALE/ADMIN_CHANGE',
  source_document_id bigint default null comment '来源销售单ID',
  price_version int not null comment '价格版本',
  change_reason varchar(500) not null comment '定价或改价原因',
  operator_user_id bigint not null comment '操作人ID',
  operator_name varchar(64) not null comment '操作人',
  create_time datetime not null,
  primary key (history_id),
  key idx_jewelry_influencer_price_history (influencer_id,product_id,history_id)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP达人固定价历史';

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_influencer_price_history' and column_name='product_id')=0,
  "alter table jewelry_influencer_price_history add column product_id bigint null comment '商品ID' after influencer_id",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='influencer_id')=0,
  "alter table jewelry_document add column influencer_id bigint null comment '达人ID' after external_no",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='influencer_price_snapshot')=0,
  "alter table jewelry_document_item add column influencer_price_snapshot decimal(18,4) null comment '达人商品固定价快照' after unit_price",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document_item' and column_name='influencer_price_version')=0,
  "alter table jewelry_document_item add column influencer_price_version int null comment '达人商品价格版本快照' after influencer_price_snapshot",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select character_maximum_length from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='influencer_name') < 128,
  "alter table jewelry_document modify column influencer_name varchar(128) default '' comment '达人或主播名称'",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='influencer_price_snapshot')=0,
  "alter table jewelry_document add column influencer_price_snapshot decimal(18,4) null comment '达人固定价快照' after influencer_name",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
    and table_name='jewelry_document' and column_name='influencer_price_version')=0,
  "alter table jewelry_document add column influencer_price_version int null comment '达人价格版本快照' after influencer_price_snapshot",
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 将旧版单据头价格快照下沉到销售明细，确保历史销售和关联退货仍按原价格追溯。
update jewelry_document_item item
join jewelry_document doc on doc.document_id=item.document_id
set item.influencer_price_snapshot=coalesce(item.influencer_price_snapshot,doc.influencer_price_snapshot,item.unit_price),
    item.influencer_price_version=coalesce(item.influencer_price_version,doc.influencer_price_version,0)
where doc.doc_type='SALES_OUT' and doc.influencer_id is not null
  and coalesce(item.pricing_mode,'SEPARATE')<>'INCLUDED'
  and item.influencer_price_snapshot is null;

-- 已入账历史销售按“达人 + 商品”迁入最新有效价格；新库或无历史销售时该语句不产生数据。
insert ignore into jewelry_influencer_product_price(influencer_id,product_id,fixed_unit_price,price_status,
  price_version,pending_source_document_id,price_source_document_id,price_effective_time,
  create_by,create_time,update_by,update_time)
select distinct doc.influencer_id,item.product_id,item.influencer_price_snapshot,'PRICED',
  greatest(coalesce(item.influencer_price_version,0),1),null,doc.document_id,
  coalesce(doc.update_time,doc.create_time,sysdate()),'migration',sysdate(),'migration',sysdate()
from jewelry_document doc
join jewelry_document_item item on item.document_id=doc.document_id
where doc.doc_type='SALES_OUT' and doc.status in('POSTED','REVERSED') and doc.influencer_id is not null
  and coalesce(item.pricing_mode,'SEPARATE')<>'INCLUDED'
  and item.influencer_price_snapshot>0
  and not exists(
    select 1 from jewelry_document newer_doc
    join jewelry_document_item newer_item on newer_item.document_id=newer_doc.document_id
    where newer_doc.doc_type='SALES_OUT' and newer_doc.status in('POSTED','REVERSED')
      and newer_doc.influencer_id=doc.influencer_id and newer_item.product_id=item.product_id
      and coalesce(newer_item.pricing_mode,'SEPARATE')<>'INCLUDED'
      and newer_doc.document_id>doc.document_id
  );

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark,menu_name_vi)
select 3011,'达人/主播库',3000,5,'influencer','jewelry/influencer/index','','',1,0,'C','0','0',
  'jewelry:influencer:list','peoples','admin',sysdate(),'',null,'达人主播固定价档案','Danh sách KOL/Streamer'
where not exists(select 1 from sys_menu where menu_id=3011);

insert into sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark,menu_name_vi) values
(3119,'新增达人/主播',3011,1,'#','','','',1,0,'F','0','0','jewelry:influencer:add','#','admin',sysdate(),'',null,'','Thêm KOL/Streamer'),
(3120,'修改达人/主播',3011,2,'#','','','',1,0,'F','0','0','jewelry:influencer:edit','#','admin',sysdate(),'',null,'','Sửa KOL/Streamer'),
(3121,'修改达人商品固定价',3011,3,'#','','','',1,0,'F','0','0','jewelry:influencer:price','#','admin',sysdate(),'',null,'','Sửa giá cố định theo sản phẩm')
on duplicate key update menu_name=values(menu_name),parent_id=values(parent_id),perms=values(perms),menu_name_vi=values(menu_name_vi);

insert ignore into sys_role_menu(role_id,menu_id)
select role_id,3011 from sys_role where role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin') and del_flag='0';
insert ignore into sys_role_menu(role_id,menu_id)
select role_id,3119 from sys_role where role_key in ('jewelry_maker','jewelry_admin') and del_flag='0';
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in (3120,3121)
where r.role_key='jewelry_admin' and r.del_flag='0';
