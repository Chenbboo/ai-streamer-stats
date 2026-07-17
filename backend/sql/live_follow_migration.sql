-- 关注关系截图识别结果：用于区分“已关注待回关”和真正未维护客户
create table if not exists live_follow_record (
  follow_id bigint(20) not null auto_increment,
  biz_date date not null,
  streamer_id bigint(20) not null,
  customer_id bigint(20) not null,
  upload_id bigint(20) default null,
  follow_status varchar(32) not null default 'pending',
  create_time datetime,
  update_time datetime,
  primary key (follow_id),
  unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id),
  key idx_date (biz_date),
  key idx_streamer_customer_date_status (streamer_id, customer_id, biz_date, follow_status)
) engine=innodb default charset=utf8mb4 comment='客户关注关系记录';
