-- 珠宝ERP第一阶段数据库、角色和菜单，可重复执行。
-- 业务表只在不存在时创建，不会清空已录入数据。

create table if not exists jewelry_staff (
  staff_id bigint not null auto_increment comment 'ERP人员ID',
  user_id bigint not null comment '关联sys_user.user_id',
  staff_no varchar(32) not null comment '员工编号',
  real_name varchar(64) not null comment '姓名',
  phone varchar(32) default '' comment '联系电话',
  status char(1) default '0' comment '状态（0启用 1停用）',
  joined_date date default null comment '加入日期',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (staff_id),
  unique key uk_jewelry_staff_user (user_id),
  unique key uk_jewelry_staff_no (staff_no)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP人员';

create table if not exists jewelry_product (
  product_id bigint not null auto_increment comment '商品ID',
  sku varchar(64) not null comment 'SKU编码',
  product_name varchar(128) not null comment '商品名称',
  product_type varchar(16) not null default 'FINISHED' comment 'FINISHED成品商品 PART散件商品 ACCESSORY配件商品 WELFARE福利商品',
  category varchar(64) default '' comment '商品分类',
  specification varchar(16) not null default '普通' comment '规格类型：精品或普通',
  image_url varchar(500) default '' comment '商品主图',
  unit varchar(16) default '件' comment '计量单位',
  default_pack_fee decimal(18,6) default 0 comment '默认包装费',
  default_ship_fee decimal(18,6) default 0 comment '默认物流费',
  default_cert_fee decimal(18,6) default 0 comment '默认鉴定费',
  warning_qty int default 5 comment '库存预警值',
  status char(1) default '0' comment '状态（0启用 1停用）',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (product_id),
  unique key uk_jewelry_product_sku (sku)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP商品档案';

create table if not exists jewelry_supplier (
  supplier_id bigint not null auto_increment comment '供应商ID',
  supplier_code varchar(32) not null comment '供应商编码',
  supplier_name varchar(128) not null comment '供应商名称',
  contact_name varchar(64) default '' comment '联系人',
  contact_phone varchar(32) default '' comment '联系电话',
  address varchar(255) default '' comment '地址',
  settlement_type varchar(64) default '' comment '结算方式',
  status char(1) default '0' comment '状态（0启用 1停用）',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (supplier_id),
  unique key uk_jewelry_supplier_code (supplier_code)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP供应商档案';

create table if not exists jewelry_stock (
  product_id bigint not null comment '商品ID',
  on_hand_qty int not null default 0 comment '账面库存',
  reserved_out_qty int not null default 0 comment '出库待审冻结',
  inspection_qty int not null default 0 comment '售后待检',
  inspection_reserved_qty int not null default 0 comment '待检处理冻结',
  defect_qty int not null default 0 comment '次品库存',
  defect_reserved_qty int not null default 0 comment '次品待审冻结',
  avg_cost decimal(18,6) not null default 0 comment '移动平均成本',
  inspection_cost_amount decimal(20,6) not null default 0 comment '待检成本金额',
  defect_cost_amount decimal(20,6) not null default 0 comment '次品成本金额',
  version int not null default 0 comment '并发版本',
  update_time datetime default null,
  primary key (product_id)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP库存汇总';

create table if not exists jewelry_document (
  document_id bigint not null auto_increment comment '单据ID',
  doc_no varchar(32) not null comment '单号',
  doc_type varchar(32) not null comment '单据类型',
  biz_date date not null comment '业务日期',
  status varchar(24) not null comment '状态',
  supplier_id bigint default null comment '供应商ID',
  supplier_name_snapshot varchar(128) default '' comment '供应商名称快照',
  sales_channel varchar(64) default '' comment '销售渠道',
  external_no varchar(64) default '' comment '平台或供应商单号',
  influencer_name varchar(64) default '' comment '达人或主播名称',
  platform_rate decimal(9,6) default 0 comment '平台扣点率',
  commission_rate decimal(9,6) default 0 comment '达人佣金率',
  tax_rate decimal(9,6) default 0 comment '税率',
  return_reason varchar(255) default '' comment '退货或调整原因',
  source_document_id bigint default null comment '原单或被红冲单ID',
  unlinked_reason varchar(255) default '' comment '未关联原单原因',
  actual_refund_amount decimal(20,2) default null comment '客户退货实际退款总额',
  total_qty int not null default 0,
  total_amount decimal(20,2) not null default 0,
  total_cost decimal(20,2) not null default 0,
  total_profit decimal(20,2) not null default 0,
  risk_status varchar(16) default 'NORMAL',
  creator_user_id bigint not null,
  creator_name varchar(64) not null,
  first_reviewer_user_id bigint default null,
  first_reviewer_name varchar(64) default '',
  second_reviewer_user_id bigint default null,
  second_reviewer_name varchar(64) default '',
  reject_user_id bigint default null,
  reject_user_name varchar(64) default '',
  reject_reason varchar(500) default '',
  version int not null default 0,
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (document_id),
  unique key uk_jewelry_document_no (doc_no),
  key idx_jewelry_document_status (status, doc_type, biz_date),
  key idx_jewelry_document_creator (creator_user_id, create_time),
  key idx_jewelry_document_source (source_document_id)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP业务单据';

create table if not exists jewelry_document_item (
  item_id bigint not null auto_increment comment '明细ID',
  document_id bigint not null comment '单据ID',
  product_id bigint not null comment '商品ID',
  item_role varchar(16) not null default 'NORMAL' comment '普通/组装投入/组装产出角色',
  source_item_id bigint default null comment '原销售或原单明细ID',
  bundle_group_no int default null comment '销售组合序号',
  sale_role varchar(16) not null default 'NORMAL' comment 'NORMAL独立销售 MAIN主商品 ADDON搭售商品',
  pricing_mode varchar(16) not null default 'SEPARATE' comment 'SEPARATE单独计价 INCLUDED包含在组合价',
  sku_snapshot varchar(64) not null,
  product_name_snapshot varchar(128) not null,
  product_type_snapshot varchar(16) default null comment '销售时商品类型快照',
  specification_snapshot varchar(16) default null comment '销售时规格类型快照',
  image_urls text comment '单据商品图片，逗号分隔',
  qty int not null default 0,
  good_qty int not null default 0,
  defect_qty int not null default 0,
  system_qty int default null,
  counted_qty int default null,
  adjustment_qty int not null default 0,
  unit_price decimal(18,6) not null default 0,
  unit_cost decimal(18,6) not null default 0,
  pack_fee decimal(18,6) not null default 0,
  ship_fee decimal(18,6) not null default 0,
  cert_fee decimal(18,6) not null default 0,
  other_fee1 decimal(18,6) not null default 0 comment '其他费用1/件',
  other_fee2 decimal(18,6) not null default 0 comment '其他费用2/件',
  other_fee3 decimal(18,6) not null default 0 comment '其他费用3/件',
  amount decimal(20,2) not null default 0,
  cost_amount decimal(20,2) not null default 0,
  profit_amount decimal(20,2) not null default 0,
  profit_rate decimal(9,6) not null default 0,
  line_reason varchar(255) default '',
  primary key (item_id),
  key idx_jewelry_document_product (document_id, product_id),
  key idx_jewelry_item_product_document (product_id, document_id),
  key idx_jewelry_item_bundle (document_id, bundle_group_no, sale_role),
  key idx_jewelry_item_source (source_item_id)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP单据明细';

create table if not exists jewelry_approval (
  approval_id bigint not null auto_increment,
  document_id bigint not null,
  approval_stage int not null comment '1一审 2复核',
  action varchar(16) not null comment 'PASS或REJECT',
  approver_user_id bigint not null,
  approver_name varchar(64) not null,
  approval_comment varchar(500) default '',
  create_time datetime not null,
  primary key (approval_id),
  key idx_jewelry_approval_stage (document_id, approval_stage),
  key idx_jewelry_approval_user (approver_user_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP审批记录';

-- 旧版脚本曾把“单据+审批阶段”设为唯一；驳回后重新提交会产生新一轮审批，需保留完整历史。
set @drop_approval_unique = (
  select if(count(*) > 0,
    'alter table jewelry_approval drop index uk_jewelry_approval_stage',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'jewelry_approval'
    and index_name = 'uk_jewelry_approval_stage'
);
prepare drop_approval_unique_stmt from @drop_approval_unique;
execute drop_approval_unique_stmt;
deallocate prepare drop_approval_unique_stmt;

set @add_approval_index = (
  select if(count(*) = 0,
    'alter table jewelry_approval add index idx_jewelry_approval_stage(document_id, approval_stage)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'jewelry_approval'
    and index_name = 'idx_jewelry_approval_stage'
);
prepare add_approval_index_stmt from @add_approval_index;
execute add_approval_index_stmt;
deallocate prepare add_approval_index_stmt;

create table if not exists jewelry_document_event (
  event_id bigint not null auto_increment,
  document_id bigint not null,
  event_type varchar(24) not null,
  from_status varchar(24) default '',
  to_status varchar(24) default '',
  operator_user_id bigint not null,
  operator_name varchar(64) not null,
  event_comment varchar(500) default '',
  create_time datetime not null,
  primary key (event_id),
  key idx_jewelry_event_document (document_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP单据事件';

create table if not exists jewelry_stock_transaction (
  transaction_id bigint not null auto_increment,
  document_id bigint not null,
  item_id bigint not null,
  product_id bigint not null,
  transaction_type varchar(32) not null,
  on_hand_change int not null default 0,
  reserved_change int not null default 0,
  inspection_change int not null default 0,
  inspection_reserved_change int not null default 0,
  defect_change int not null default 0,
  defect_reserved_change int not null default 0,
  cost_amount_change decimal(20,6) not null default 0,
  before_on_hand int not null default 0,
  after_on_hand int not null default 0,
  before_avg_cost decimal(18,6) not null default 0,
  after_avg_cost decimal(18,6) not null default 0,
  operator_user_id bigint not null,
  operator_name varchar(64) not null,
  create_time datetime not null,
  primary key (transaction_id),
  unique key uk_jewelry_transaction_item (document_id, item_id),
  key idx_jewelry_transaction_product (product_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='珠宝ERP库存流水';

-- 独立ERP角色。
insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '珠宝ERP库存时间预警天数', 'jewelry.stock.warning.days', '25', 'Y', 'admin', sysdate(),
  '库存达到该库龄后预警'
where not exists(select 1 from sys_config where config_key='jewelry.stock.warning.days');

insert into sys_role(role_name, role_key, role_sort, data_scope, menu_check_strictly,
  dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '珠宝ERP制单员', 'jewelry_maker', 30, '1', 1, 1, '0', '0', 'admin', sysdate(), '珠宝ERP制单角色'
where not exists(select 1 from sys_role where role_key = 'jewelry_maker' and del_flag = '0');

insert into sys_role(role_name, role_key, role_sort, data_scope, menu_check_strictly,
  dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '珠宝ERP审核员', 'jewelry_reviewer', 31, '1', 1, 1, '0', '0', 'admin', sysdate(), '珠宝ERP审核角色'
where not exists(select 1 from sys_role where role_key = 'jewelry_reviewer' and del_flag = '0');

insert into sys_role(role_name, role_key, role_sort, data_scope, menu_check_strictly,
  dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '珠宝ERP管理员', 'jewelry_admin', 32, '1', 1, 1, '0', '0', 'admin', sysdate(), '珠宝ERP管理角色'
where not exists(select 1 from sys_role where role_key = 'jewelry_admin' and del_flag = '0');

-- 清理旧临时菜单及ERP菜单权限。
delete from sys_role_menu where menu_id = 2006 or menu_id between 3000 and 3199;
delete from sys_menu where menu_id = 2006 or menu_id between 3000 and 3199;

insert into sys_menu(
  menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark, menu_name_vi
) values
(3000, '唐勃珠宝库存管理', 0, 5, 'jewelry', null, '', '', 1, 0, 'M', '0', '0', '', 'shopping', 'admin', sysdate(), '', null, '唐勃珠宝库存管理业务目录', 'Quản lý kho trang sức Tangbo'),
(3001, '人员管理', 3000, 1, 'staff', 'jewelry/staff/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:staff:list', 'peoples', 'admin', sysdate(), '', null, 'ERP人员管理', 'Nhân sự ERP'),
(3002, 'ERP概览', 3000, 2, 'overview', 'jewelry/overview/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:overview:list', 'dashboard', 'admin', sysdate(), '', null, '珠宝ERP概览', 'Tổng quan ERP'),
(3003, '商品档案', 3000, 3, 'product', 'jewelry/product/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:product:list', 'shopping', 'admin', sysdate(), '', null, '珠宝商品档案', 'Sản phẩm'),
(3004, '供应商档案', 3000, 4, 'supplier', 'jewelry/supplier/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:supplier:list', 'peoples', 'admin', sysdate(), '', null, '珠宝供应商档案', 'Nhà cung cấp'),
(3005, '库存台账', 3000, 5, 'stock', 'jewelry/stock/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:stock:list', 'table', 'admin', sysdate(), '', null, '珠宝库存台账', 'Sổ kho'),
(3006, '单据管理', 3000, 6, 'document', 'jewelry/document/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:document:list', 'form', 'admin', sysdate(), '', null, '珠宝业务单据', 'Chứng từ'),
(3007, '审批中心', 3000, 7, 'approval', 'jewelry/approval/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:approval:list', 'edit', 'admin', sysdate(), '', null, '珠宝单据审批', 'Phê duyệt'),
(3008, '毛利试算', 3000, 9, 'calculator', 'jewelry/calculator/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:calculator:list', 'money', 'admin', sysdate(), '', null, '珠宝毛利试算', 'Tính lợi nhuận'),
(3009, '库存盘点', 3000, 8, 'inventory', 'jewelry/inventory/index', '', '', 1, 0, 'C', '0', '0', 'jewelry:inventory:list', 'clipboard', 'admin', sysdate(), '', null, '珠宝库存盘点', 'Kiểm kê kho'),

(3101, '新增ERP人员', 3001, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:staff:add', '#', 'admin', sysdate(), '', null, '', 'Thêm nhân sự'),
(3102, '修改ERP人员', 3001, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:staff:edit', '#', 'admin', sysdate(), '', null, '', 'Sửa nhân sự'),
(3103, '新增商品', 3003, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:product:add', '#', 'admin', sysdate(), '', null, '', 'Thêm sản phẩm'),
(3104, '修改商品', 3003, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:product:edit', '#', 'admin', sysdate(), '', null, '', 'Sửa sản phẩm'),
(3105, '新增供应商', 3004, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:supplier:add', '#', 'admin', sysdate(), '', null, '', 'Thêm nhà cung cấp'),
(3106, '修改供应商', 3004, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:supplier:edit', '#', 'admin', sysdate(), '', null, '', 'Sửa nhà cung cấp'),
(3107, '新建单据', 3006, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:document:add', '#', 'admin', sysdate(), '', null, '', 'Tạo chứng từ'),
(3108, '修改单据', 3006, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:document:edit', '#', 'admin', sysdate(), '', null, '', 'Sửa chứng từ'),
(3109, '提交单据', 3006, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:document:submit', '#', 'admin', sysdate(), '', null, '', 'Gửi chứng từ'),
(3110, '撤回单据', 3006, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:document:withdraw', '#', 'admin', sysdate(), '', null, '', 'Rút chứng từ'),
(3111, '红冲单据', 3006, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:document:reverse', '#', 'admin', sysdate(), '', null, '', 'Đảo chứng từ'),
(3112, '通过审批', 3007, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:approval:approve', '#', 'admin', sysdate(), '', null, '', 'Duyệt'),
(3113, '驳回审批', 3007, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:approval:reject', '#', 'admin', sysdate(), '', null, '', 'Từ chối'),
(3114, '修改库存预警', 3005, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'jewelry:stock:config', '#', 'admin', sysdate(), '', null, '', 'Sửa cảnh báo tồn kho');

-- 制单员菜单权限。
insert ignore into sys_role_menu(role_id, menu_id)
select r.role_id, m.menu_id from sys_role r
join sys_menu m on m.menu_id in (3000,3002,3003,3004,3005,3006,3009,3103,3107,3108,3109,3110)
where r.role_key = 'jewelry_maker' and r.del_flag = '0';

-- 审核员菜单权限。
insert ignore into sys_role_menu(role_id, menu_id)
select r.role_id, m.menu_id from sys_role r
join sys_menu m on m.menu_id in (3000,3002,3003,3004,3005,3006,3007,3008,3009,3112,3113)
where r.role_key = 'jewelry_reviewer' and r.del_flag = '0';

-- ERP管理员菜单权限。
insert ignore into sys_role_menu(role_id, menu_id)
select r.role_id, m.menu_id from sys_role r
join sys_menu m on m.menu_id between 3000 and 3199
where r.role_key = 'jewelry_admin' and r.del_flag = '0';
