-- 直播数据模块结构基线。
-- 可用于空库初始化或现有数据库升级；不会删除业务表或业务数据。

create table if not exists live_streamer (
  streamer_id bigint not null auto_increment comment '主播ID',
  user_id bigint not null comment '关联sys_user.user_id',
  tiktok_handle varchar(100) default '' comment 'TikTok账号',
  stage_name varchar(100) not null comment '主播名称',
  status char(1) default '0' comment '0在职 1离职',
  create_by varchar(64) default '', create_time datetime,
  update_by varchar(64) default '', update_time datetime,
  remark varchar(500) default null,
  primary key (streamer_id),
  unique key uk_user_id (user_id),
  key idx_stage_name (stage_name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='主播信息表';

create table if not exists live_customer (
  customer_id bigint not null auto_increment comment '客户ID',
  streamer_id bigint not null comment '所属主播ID',
  nickname varchar(200) not null comment '客户昵称',
  profile_url varchar(300) default '', avatar_path varchar(300) default '',
  badge varchar(50) default '', merged_into_id bigint default null,
  first_seen_date date default null, last_seen_date date default null,
  create_by varchar(64) default '', create_time datetime,
  update_by varchar(64) default '', update_time datetime,
  remark varchar(500) default null,
  primary key (customer_id),
  unique key uk_nickname_streamer (nickname, streamer_id),
  key idx_merged (merged_into_id), key idx_streamer_id (streamer_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播客户表';

create table if not exists live_upload (
  upload_id bigint not null auto_increment comment '上传ID',
  biz_date date not null, streamer_id bigint not null,
  upload_type char(1) not null comment '1礼物榜 2聊天 3日报 4关注关系',
  file_path varchar(300) default '', raw_text text default null,
  ai_status char(1) default '0' comment '0待识别 1已识别 2已入库 3失败 4识别中',
  ai_result longtext default null comment 'AI识别原始结果',
  upload_by bigint default null, create_time datetime, update_time datetime,
  primary key (upload_id), key idx_date_streamer (biz_date, streamer_id), key idx_status (ai_status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播上传记录';

create table if not exists live_gift_record (
  gift_id bigint not null auto_increment, biz_date date not null,
  streamer_id bigint not null, customer_id bigint not null,
  xu int not null default 0, rank_no int default null, upload_id bigint default null,
  confirm_status char(1) default '0', ai_confidence char(1) default '1',
  create_time datetime, update_by varchar(64) default '', update_time datetime,
  primary key (gift_id),
  unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id), key idx_date (biz_date),
  key idx_streamer_customer_date (streamer_id, customer_id, biz_date)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户礼物明细';

create table if not exists live_chat_contact (
  contact_id bigint not null auto_increment, biz_date date not null,
  streamer_id bigint not null, customer_id bigint not null, upload_id bigint default null,
  has_interaction tinyint(1) default 0 comment '客户是否产生有效回复', create_time datetime,
  primary key (contact_id),
  unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id),
  key idx_streamer_customer_date_interaction (streamer_id, customer_id, biz_date, has_interaction)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户聊天联系记录';

create table if not exists live_chat_message (
  msg_id bigint not null auto_increment, biz_date date not null,
  streamer_id bigint not null, customer_id bigint not null,
  sender char(1) not null, content_type char(1) default '1', content text default null,
  msg_time varchar(50) default '', seq_no int default 0, upload_id bigint default null,
  create_time datetime, primary key (msg_id),
  key idx_date_streamer_customer (biz_date, streamer_id, customer_id), key idx_customer (customer_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='聊天消息明细预留表';

create table if not exists live_daily_report (
  report_id bigint not null auto_increment, biz_date date not null,
  streamer_id bigint not null, total_xu int not null default 0,
  raw_text varchar(500) default '', upload_id bigint default null,
  create_time datetime, update_by varchar(64) default '', update_time datetime,
  primary key (report_id), unique key uk_date_streamer (biz_date, streamer_id), key idx_date (biz_date)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='主播每日汇报';

create table if not exists live_follow_record (
  follow_id bigint not null auto_increment, biz_date date not null,
  streamer_id bigint not null, customer_id bigint not null, upload_id bigint default null,
  follow_status varchar(32) not null default 'pending', create_time datetime, update_time datetime,
  primary key (follow_id), unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id), key idx_date (biz_date),
  key idx_streamer_customer_date_status (streamer_id, customer_id, biz_date, follow_status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户关注关系记录';

create table if not exists live_customer_alias (
  alias_id bigint not null auto_increment, customer_id bigint not null,
  streamer_id bigint default null, nickname varchar(200) not null,
  source_type varchar(20) default 'manual', first_seen_date date default null,
  last_seen_date date default null, create_time datetime default current_timestamp,
  primary key (alias_id), key idx_customer_id (customer_id), key idx_nickname (nickname)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播客户历史昵称';

create table if not exists live_kpi_config (
  kpi_id bigint not null auto_increment, streamer_id bigint default null,
  kpi_year int not null, kpi_month int not null,
  gift_daily int default 10000, gift_monthly int default 260000,
  new_fan_daily int default 10, new_fan_monthly int default 260,
  chat_daily int default 5, chat_monthly int default 130,
  new_tip_daily int default 5000, new_tip_monthly int default 130000,
  create_by varchar(64) default '', create_time datetime,
  update_by varchar(64) default '', update_time datetime,
  primary key (kpi_id), unique key uk_streamer_month (streamer_id, kpi_year, kpi_month)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播KPI配置';

-- 兼容旧的维系统计快照。当前页面使用实时查询，新代码不再写入该表。
create table if not exists live_weiji_stats (
  id bigint not null auto_increment, stat_date date not null, streamer_id bigint not null,
  red int default 0, green int default 0, yellow int default 0, orange int default 0,
  total int default 0, create_time datetime default current_timestamp,
  primary key (id), unique key uk_date_streamer (stat_date, streamer_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='维系统计历史快照';

-- 旧库字段补齐。
set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
   and table_name='live_customer' and column_name='streamer_id') = 0,
  'alter table live_customer add column streamer_id bigint default null after customer_id', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
   and table_name='live_chat_contact' and column_name='has_interaction') = 0,
  'alter table live_chat_contact add column has_interaction tinyint(1) default 0 after upload_id', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.columns where table_schema=database()
   and table_name='live_customer_alias' and column_name='streamer_id') = 0,
  'alter table live_customer_alias add column streamer_id bigint default null after customer_id', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 从唯一能够确认所属主播的正式明细中回填旧客户。
update live_customer c
join (
  select customer_id, min(streamer_id) streamer_id
  from (
    select customer_id, streamer_id from live_gift_record
    union all select customer_id, streamer_id from live_chat_contact
    union all select customer_id, streamer_id from live_follow_record
  ) x
  group by customer_id having count(distinct streamer_id)=1
) scope on scope.customer_id=c.customer_id
set c.streamer_id=scope.streamer_id
where c.streamer_id is null;

update live_customer_alias a
join live_customer c on c.customer_id=a.customer_id
set a.streamer_id=c.streamer_id
where a.streamer_id is null;

-- 旧版全局昵称唯一约束会阻止不同主播拥有同名客户。
set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
   and table_name='live_customer' and index_name='uk_nickname') > 0,
  'alter table live_customer drop index uk_nickname', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
   and table_name='live_customer' and index_name='uk_nickname_streamer') = 0,
  'alter table live_customer add unique index uk_nickname_streamer(nickname,streamer_id)', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
   and table_name='live_gift_record' and index_name='idx_streamer_customer_date') = 0,
  'alter table live_gift_record add index idx_streamer_customer_date(streamer_id,customer_id,biz_date)', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  (select count(*) from information_schema.statistics where table_schema=database()
   and table_name='live_chat_contact' and index_name='idx_streamer_customer_date_interaction') = 0,
  'alter table live_chat_contact add index idx_streamer_customer_date_interaction(streamer_id,customer_id,biz_date,has_interaction)', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- JSON修复失败时仍需保留原始模型响应，因此统一为文本列。
set @sql = if(
  (select data_type from information_schema.columns where table_schema=database()
   and table_name='live_upload' and column_name='ai_result') = 'json',
  'alter table live_upload modify column ai_result longtext null comment ''AI识别原始结果''', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
