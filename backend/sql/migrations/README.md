# 数据库迁移说明

本目录保存不会主动删除业务表或业务数据的增量迁移。所有脚本都以当前代码实际依赖为准，并支持在同一数据库上重复执行。

## 执行前

1. 备份目标数据库。
2. 明确确认当前数据库：本地默认是 `ry-vue`，正式环境当前是 `ry_live`，禁止依赖脚本中的默认库名。
3. 确认连接字符集为 `utf8mb4`。
4. 兼容入口 `live_tables.sql` 只会转调本目录的安全基线迁移，不会删除业务表。
5. 在正式库执行 `preflight_business_upgrade.sql` 并保存输出；先在正式库副本完成一次全流程演练。

## 正式账号连续性

- 升级只复用现有 `sys_user`，不会导入一套替代账号，也不会修改用户名、密码、启停状态和已有业务角色。
- 执行迁移前后各运行一次 `preflight_business_upgrade.sql`；`identity_checksum`、`legacy_role_checksum` 及对应数量必须一致。
- 迁移只按原有 `role_key` 更新公司归属：珠宝角色进入上海公司，主播角色进入越南公司。
- 江澜优先复用本地 `jianglan`；正式环境没有该账号时复用现有 `GLY-jl`，并保留其珠宝管理员角色。
- 王赋章账号不得在 SQL 中保存生产密码。由系统管理员设置一次性密码创建 `wangfuzhang`，再重复执行 V017 绑定老板角色。
- 正式切换前必须分别使用原主播、珠宝制单、珠宝审核、珠宝管理员和系统管理员账号完成登录及权限回归。

## 现有数据库升级顺序

```text
V001__system_menu_i18n.sql
V002__live_schema_baseline.sql
V003__live_permissions_i18n.sql
V004__jewelry_maker_product_permission.sql
V005__jewelry_product_type_and_specification.sql
V006__jewelry_sales_bundle.sql
V007__jewelry_maker_add_product_permission.sql
V008__jewelry_sales_other_fees.sql
V009__jewelry_cost_adjustment_lock_index.sql
V010__business_project_foundation.sql
V011__business_project_boss_isolation.sql
V012__business_staff_management.sql
V013__business_department_management.sql
V014__business_two_company_organization.sql
V015__retire_default_departments.sql
V016__assign_staff_to_companies.sql
V017__bind_existing_owner_accounts.sql
V018__fix_business_route_names.sql
V019__bind_department_leader_user.sql
V020__business_staff_profile.sql
V021__business_project_acceptance.sql
V022__business_operating_configuration.sql
V023__business_daily_accounting.sql
V024__repair_jianglan_display_name.sql
V025__business_project_owner_workbench.sql
V026__business_recurring_work.sql
V027__business_employee_work_schedule.sql
V028__business_effort_confirmation.sql
V029__business_project_effort_permission.sql
V030__business_staff_leave.sql
V031__business_project_execution_source.sql
V032__business_ai_foundation.sql
V033__business_boss_ai_page.sql
V034__business_ai_workflow_engine.sql
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
V008__jewelry_sales_other_fees.sql
V009__jewelry_cost_adjustment_lock_index.sql
V010__business_project_foundation.sql
V011__business_project_boss_isolation.sql
V012__business_staff_management.sql
V013__business_department_management.sql
V014__business_two_company_organization.sql
V015__retire_default_departments.sql
V016__assign_staff_to_companies.sql
V017__bind_existing_owner_accounts.sql
V018__fix_business_route_names.sql
V019__bind_department_leader_user.sql
V020__business_staff_profile.sql
V021__business_project_acceptance.sql
V022__business_operating_configuration.sql
V023__business_daily_accounting.sql
V024__repair_jianglan_display_name.sql
V025__business_project_owner_workbench.sql
V026__business_recurring_work.sql
V027__business_employee_work_schedule.sql
V028__business_effort_confirmation.sql
V029__business_project_effort_permission.sql
V030__business_staff_leave.sql
V031__business_project_execution_source.sql
V032__business_ai_foundation.sql
V033__business_boss_ai_page.sql
V034__business_ai_workflow_engine.sql
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
- 项目经营域使用 `biz_` 表，通过适配器引用直播和珠宝事实，不反向修改原业务表。
- `biz_project_relation` 只记录项目与执行系统的有效期关系；直播原始明细仍由 `live_` 表维护。
- 老板 AI 只通过已有业务服务读取数据；所有会话、工具调用和待确认动作必须写入 `biz_ai_*` 审计链路。
- 老板 AI 使用独立菜单页面，老板工作台保持为经营看板；两处仍复用同一老板权限边界。
- 项目预算、KPI、人员内部核算成本和项目分摊均按版本或历史记录追溯，不允许静默覆盖。
* `V034__business_ai_workflow_engine.sql`：AI 持久化工作流实例与事件记录，支持多轮操作在刷新、重启和模型误判后继续。
