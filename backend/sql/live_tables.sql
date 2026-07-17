-- ----------------------------
-- AI 涓绘挱鐩存挱鏁版嵁缁熻 鈥?涓氬姟琛?-- 鍦?ry-vue 搴撴墽琛屻€傚瓧绗﹂泦 utf8mb4(鏄电О鍚?emoji/闊╂枃/瓒婂崡鏂?銆?-- ----------------------------

-- 1. 涓绘挱鎵╁睍琛?鐧诲綍璐﹀彿澶嶇敤 sys_user,杩欓噷瀛樺钩鍙颁俊鎭?
drop table if exists live_streamer;
create table live_streamer (
  streamer_id     bigint(20)   not null auto_increment    comment '涓绘挱ID',
  user_id         bigint(20)   not null                   comment '鍏宠仈 sys_user.user_id',
  tiktok_handle   varchar(100) default ''                 comment 'TikTok 璐﹀彿(@handle)',
  stage_name      varchar(100) not null                   comment '鑹哄悕/姹囨姤鐢ㄥ悕(濡?Zhenzhen)',
  status          char(1)      default '0'                comment '鐘舵€?0鍦ㄨ亴 1绂昏亴)',
  create_by       varchar(64)  default '',
  create_time     datetime,
  update_by       varchar(64)  default '',
  update_time     datetime,
  remark          varchar(500) default null,
  primary key (streamer_id),
  unique key uk_user_id (user_id),
  key idx_stage_name (stage_name)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='涓绘挱淇℃伅琛?;

-- 2. 瀹㈡埛琛?韬唤浠ユ樀绉颁负鍑?鏀瑰悕闈犱汉宸ュ悎骞?
drop table if exists live_customer;
create table live_customer (
  customer_id     bigint(20)   not null auto_increment    comment '瀹㈡埛ID',
  nickname        varchar(200) not null                   comment '瀹㈡埛鏄电О(鎴浘璇嗗埆鍘熸枃)',
  profile_url     varchar(300) default ''                 comment 'TikTok 涓婚〉閾炬帴(浜哄伐鏍稿疄濉啓)',
  avatar_path     varchar(300) default ''                 comment '澶村儚灏忓浘璺緞(璇嗗埆鏃舵埅鍙?杈呭姪璁や汉)',
  badge           varchar(50)  default ''                 comment '绮変笣鍥㈠窘绔?濡?MWRM)',
  merged_into_id  bigint(20)   default null               comment '宸插悎骞跺埌鐨勫鎴稩D(null=姝ｅ父;闈瀗ull=姝ゆ樀绉版槸鍒悕)',
  first_seen_date date         default null               comment '棣栨鍑虹幇鏃ユ湡',
  last_seen_date  date         default null               comment '鏈€杩戝嚭鐜版棩鏈?,
  create_by       varchar(64)  default '',
  create_time     datetime,
  update_by       varchar(64)  default '',
  update_time     datetime,
  remark          varchar(500) default null               comment '杩愯惀澶囨敞(鍦板尯/娑堣垂鍔?鎬ф牸绛?',
  primary key (customer_id),
  unique key uk_nickname_streamer (nickname, streamer_id),
  key idx_merged (merged_into_id),
  key idx_streamer_id (streamer_id)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='瀹㈡埛琛?;

-- 3. 涓婁紶璁板綍琛?姣忓紶鎴浘/姣忔姹囨姤鏂囨湰涓€鏉?
drop table if exists live_upload;
create table live_upload (
  upload_id       bigint(20)   not null auto_increment    comment '涓婁紶ID',
  biz_date        date         not null                   comment '涓氬姟鏃ユ湡(鏁版嵁灞炰簬鍝竴澶?',
  streamer_id     bigint(20)   not null                   comment '涓绘挱ID',
  upload_type     char(1)      not null                   comment '绫诲瀷(1鎵撹祻姒滄埅鍥?2鑱婂ぉ鎴浘 3姹囨姤鏂囨湰)',
  file_path       varchar(300) default ''                 comment '鏂囦欢璺緞(绫诲瀷3涓虹┖)',
  raw_text        text         default null               comment '姹囨姤鍘熸枃(绫诲瀷3)',
  ai_status       char(1)      default '0'                comment 'AI璇嗗埆鐘舵€?0寰呰瘑鍒?1宸茶瘑鍒?2宸叉牎姝ｅ叆搴?3璇嗗埆澶辫触)',
  ai_result       json         default null               comment 'AI璇嗗埆鍘熷JSON(鐣欑棔,鏍℃瀵圭収鐢?',
  upload_by       bigint(20)   default null               comment '涓婁紶浜?sys_user.user_id',
  create_time     datetime,
  update_time     datetime,
  primary key (upload_id),
  key idx_date_streamer (biz_date, streamer_id),
  key idx_status (ai_status)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='涓婁紶璁板綍琛?;

-- 4. 鎵撹祻鏄庣粏琛?鎸夊ぉ,鍚屼竴瀹㈡埛褰撳ぉ澶氬紶鎴浘鍘婚噸鍚庝竴鏉?
drop table if exists live_gift_record;
create table live_gift_record (
  gift_id         bigint(20)   not null auto_increment    comment '璁板綍ID',
  biz_date        date         not null                   comment '鏃ユ湡',
  streamer_id     bigint(20)   not null                   comment '涓绘挱ID',
  customer_id     bigint(20)   not null                   comment '瀹㈡埛ID',
  xu              int          not null default 0         comment 'Xu 鏁?,
  rank_no         int          default null               comment '姒滃崟鎺掑悕',
  upload_id       bigint(20)   default null               comment '鏉ユ簮鎴浘',
  confirm_status  char(1)      default '0'                comment '鏍℃鐘舵€?0寰呮牎姝?1宸茬‘璁?',
  ai_confidence   char(1)      default '1'                comment 'AI缃俊搴?0浣?闇€閲嶇偣鏍稿 1姝ｅ父)',
  create_time     datetime,
  update_by       varchar(64)  default ''                 comment '鏍℃浜?,
  update_time     datetime,
  primary key (gift_id),
  unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id),
  key idx_date (biz_date),
  key idx_streamer_customer_date (streamer_id, customer_id, biz_date)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='鎵撹祻鏄庣粏琛?鏃ョ矑搴?';

-- 5. 鑱婂ぉ浜掑姩琛?涓€鏈?涓绘挱脳瀹㈡埛脳鏃ユ湡涓€鏉?璇佹槑褰撳ぉ璺熻繘杩?
drop table if exists live_chat_contact;
create table live_chat_contact (
  contact_id      bigint(20)   not null auto_increment    comment '璁板綍ID',
  biz_date        date         not null                   comment '鏃ユ湡',
  streamer_id     bigint(20)   not null                   comment '涓绘挱ID',
  customer_id     bigint(20)   not null                   comment '瀹㈡埛ID',
  upload_id       bigint(20)   default null               comment '鏉ユ簮鎴浘(璇佹嵁)',
  has_interaction tinyint(1)   default 0                  comment '客户是否有回复/互动',
  create_time     datetime,
  primary key (contact_id),
  unique key uk_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id),
  key idx_streamer_customer_date_interaction (streamer_id, customer_id, biz_date, has_interaction)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='鑱婂ぉ浜掑姩琛?鏃ョ矑搴?';

-- 6. 鑱婂ぉ娑堟伅琛?浜屾湡:閫愭潯娑堟伅,渚?AI 鏈堝害璇勪及;涓€鏈熷缓琛ㄤ笉鍐欏叆)
drop table if exists live_chat_message;
create table live_chat_message (
  msg_id          bigint(20)   not null auto_increment    comment '娑堟伅ID',
  biz_date        date         not null                   comment '鏃ユ湡',
  streamer_id     bigint(20)   not null                   comment '涓绘挱ID',
  customer_id     bigint(20)   not null                   comment '瀹㈡埛ID',
  sender          char(1)      not null                   comment '鍙戦€佹柟(1涓绘挱 2瀹㈡埛)',
  content_type    char(1)      default '1'                comment '鍐呭绫诲瀷(1鏂囧瓧 2璐村浘/琛ㄦ儏 3鍥剧墖 4璇煶 9鍏朵粬)',
  content         text         default null               comment '娑堟伅鏂囨湰(闈炴枃瀛楃被鍨嬪瓨鎻忚堪)',
  msg_time        varchar(50)  default ''                 comment '鎴浘涓婃樉绀虹殑鏃堕棿(鍘熸枃)',
  seq_no          int          default 0                  comment '瀵硅瘽鍐呴『搴忓彿',
  upload_id       bigint(20)   default null               comment '鏉ユ簮鎴浘',
  create_time     datetime,
  primary key (msg_id),
  key idx_date_streamer_customer (biz_date, streamer_id, customer_id),
  key idx_customer (customer_id)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='鑱婂ぉ娑堟伅琛?浜屾湡)';

-- 7. 姣忔棩姹囨姤琛?涓绘挱鑷姤,涓嶄笌鏄庣粏瀵硅处)
drop table if exists live_daily_report;
create table live_daily_report (
  report_id       bigint(20)   not null auto_increment    comment '姹囨姤ID',
  biz_date        date         not null                   comment '鏃ユ湡',
  streamer_id     bigint(20)   not null                   comment '涓绘挱ID',
  total_xu        int          not null default 0         comment '鑷姤鎬?Xu',
  raw_text        varchar(500) default ''                 comment '姹囨姤鍘熸枃',
  upload_id       bigint(20)   default null               comment '鏉ユ簮涓婁紶璁板綍',
  create_time     datetime,
  update_by       varchar(64)  default '',
  update_time     datetime,
  primary key (report_id),
  unique key uk_date_streamer (biz_date, streamer_id),
  key idx_date (biz_date)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='姣忔棩姹囨姤琛?;
