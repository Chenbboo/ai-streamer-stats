# 数据库迁移说明

本目录保存不会主动删除业务表或业务数据的增量迁移。所有脚本都以当前代码实际依赖为准，并支持在同一数据库上重复执行。

## 执行前

1. 备份目标数据库。
2. 确认当前数据库为 `ry-vue`。
3. 确认连接字符集为 `utf8mb4`。
4. 兼容入口 `live_tables.sql` 只会转调本目录的安全基线迁移，不会删除业务表。

## 现有数据库升级顺序

```text
V001__system_menu_i18n.sql
V002__live_schema_baseline.sql
V003__live_permissions_i18n.sql
V004__jewelry_maker_product_permission.sql
V005__jewelry_product_type_and_specification.sql
V006__jewelry_sales_bundle.sql
V007__jewelry_maker_add_product_permission.sql
../live_ai_config.sql
../jewelry_erp_menu.sql
../jewelry_manual_assembly.sql
```

`jewelry_erp_menu.sql` 和 `jewelry_manual_assembly.sql` 使用 `CREATE TABLE IF NOT EXISTS` 和字段存在性检查，可用于现有数据库升级。

## 空数据库初始化顺序

```text
../ry_20260417.sql
../quartz.sql
V001__system_menu_i18n.sql
V002__live_schema_baseline.sql
V003__live_permissions_i18n.sql
V004__jewelry_maker_product_permission.sql
V005__jewelry_product_type_and_specification.sql
V006__jewelry_sales_bundle.sql
V007__jewelry_maker_add_product_permission.sql
../live_ai_config.sql
../jewelry_erp_menu.sql
../jewelry_manual_assembly.sql
```

执行完成后运行 `verify_business_schema.sql`。所有 `missing_*` 列都应为 `0`，所有 `*_mismatch` 和 `orphan_*` 行数也应为 `0`。

## 设计约束

- 直播流水使用 `live_daily_report.total_xu`。
- 礼物和客户分析使用 `live_gift_record`。
- 聊天互动使用 `live_chat_contact`。
- 关注待回关使用 `live_follow_record`。
- `live_upload.ai_result` 使用 `LONGTEXT` 保存模型原始结果，应用层负责 JSON 校验和修复。
- 珠宝库存只能通过已审核单据和库存流水改变。
