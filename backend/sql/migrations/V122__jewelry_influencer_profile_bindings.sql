-- Platform-specific creator numbers and reusable product terms.
create table if not exists jewelry_influencer_platform (
  platform_code varchar(8) not null primary key,
  platform_name varchar(64) not null,
  next_no bigint not null default 1,
  status char(1) not null default '0',
  unique key uk_jewelry_influencer_platform_name (platform_name)
) engine=InnoDB default charset=utf8mb4;

insert ignore into jewelry_influencer_platform(platform_code,platform_name,next_no) values
  ('DY','抖音',1),('TB','淘宝',1),('KS','快手',1),
  ('XHS','小红书',1),('SPH','视频号',1),('JD','京东',1),('QT','其他',1);

alter table jewelry_influencer add column platform_code varchar(8) default null after platform;

update jewelry_influencer i
left join jewelry_influencer_platform p on p.platform_name=i.platform
set i.platform_code=coalesce(p.platform_code,'QT')
where i.platform_code is null;

alter table jewelry_influencer_product_price
  add column commission_rate decimal(9,6) default null,
  add column platform_rate decimal(9,6) default null,
  add column tax_rate decimal(9,6) default null,
  add column pack_fee decimal(18,4) default null,
  add column ship_fee decimal(18,4) default null,
  add column cert_fee decimal(18,4) default null,
  add column binding_status char(1) not null default '0',
  add column binding_remark varchar(500) default null;

create table if not exists jewelry_influencer_bundle_config (
  config_id bigint not null auto_increment primary key,
  influencer_id bigint not null,
  main_product_id bigint not null,
  addon_product_id bigint not null,
  main_qty int not null,
  addon_qty int not null,
  pricing_mode varchar(16) not null,
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  unique key uk_jewelry_influencer_bundle_config (influencer_id,main_product_id,addon_product_id)
) engine=InnoDB default charset=utf8mb4;
