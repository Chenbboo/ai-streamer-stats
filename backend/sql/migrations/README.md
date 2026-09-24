# 数据库迁移说明

### V130：制单员维护达人商品绑定

在 V129 之后执行 `V130__jewelry_maker_influencer_binding_permission.sql`，为珠宝ERP制单员授予达人商品绑定权限。制单员可新增、编辑和批量导入商品绑定，并维护预设搭售配置；不授予达人基本资料修改权限，审核员继续只读。脚本使用角色编码和权限标识定位记录，可重复执行。

### V129：越南公司部门

在 V128 之后执行 `V129__vietnam_company_departments.sql`，为越南meimaru公司（111）新增团播部、人事部、电商部。脚本只处理名称、层级和启用状态匹配的公司节点，已有未删除的同名部门不会重复创建，也不会调整现有部门或人员归属；可重复执行。

### V096：立项创建防重

V095 后执行 `V096__proposal_create_idempotency.sql`，为 `biz_project_proposal` 添加可空的 `create_request_key` 和申请人、创建标识的联合唯一索引。历史数据保留，脚本可重复执行；必须先迁移再发布后端。发布包及结构门禁已包含本版本。

### V095：上海公司部门

V094 后执行 `V095__shanghai_company_departments.sql`，在上海公司（110）下按顺序补充运营部、商务部、IT部、AI视频内容部、人事部、财务部、外部人员七个部门。执行前备份 `sys_dept`；已有未删除的同名部门（包括停用部门）保留，不重复创建，不改动人员归属、负责人和现有排序。公司节点名称沿用现有数据，兼容“上海美丸文化公司”和“上海每丸文化公司”。若目标公司缺失、停用或名称不匹配，脚本不插入记录，需核对目标组织后再执行。

前端默认展开集团，公司点击名称或箭头下拉展开部门，支持全部展开和全部折叠。发布包已包含 V094、V095；本地代码验证不代表目标数据库已应用迁移。

本目录保存不会主动删除业务表或业务数据的增量迁移。所有脚本都以当前代码实际依赖为准，并支持在同一数据库上重复执行。

当前三系统本地版本至 V067。代码与迁移的已执行证据见 [P1–P4 总记录](../../../docs/P1-P4-三系统实施与验收记录.md)。本地应用记录不代表正式库已升级；旧脚本下述业务描述只代表各自引入时的语义，最终行为按对象版本和最新服务实现判断。

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
V035__jewelry_maker_basic_product_edit.sql
V036__business_project_proposal.sql
V037__business_project_kpi_bonus.sql
V038__business_staff_monthly_cost.sql
V039__bind_company_owners.sql
V040__jewelry_purchase_amount_precision.sql
V041__project_governance_modes.sql
V042__kpi_plan_soft_delete.sql
V043__business_task_progress_report.sql
V044__business_project_progress_report.sql
V045__open_ended_project_plan.sql
V046__staff_cost_policy_retirement.sql
V047__jewelry_influencer_fixed_pricing.sql
V048__jewelry_influencer_bundle_items.sql
V049__business_staff_leave_approval.sql
V050__staff_leave_request_collation.sql
V051__business_operations_integrity_hardening.sql
V052__business_project_deputy_permissions.sql
V053__preserve_unassigned_project_routines.sql
V054__normalize_business_attachment_urls.sql
V055__business_accounting_return_review.sql
V056__business_project_terminal_and_cost_integrity.sql
V057__owner_driven_project_business_plan.sql
V058__proposal_named_staff_cost_snapshot.sql
V059__proposal_daily_staff_cost_snapshot.sql
V060__project_owner_kpi_and_staff_cost_permissions.sql
V061__staff_personal_menu_permissions.sql
V062__project_kpi_automatic_sources.sql
V063__separate_project_delivery_accounting.sql
V064__project_actual_work_and_templates.sql
V065__independent_project_incentive.sql
V066__feishu_readonly_attendance.sql
V067__three_system_product_navigation.sql
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
V035__jewelry_maker_basic_product_edit.sql
V036__business_project_proposal.sql
V037__business_project_kpi_bonus.sql
V038__business_staff_monthly_cost.sql
V039__bind_company_owners.sql
V040__jewelry_purchase_amount_precision.sql
V041__project_governance_modes.sql
V042__kpi_plan_soft_delete.sql
V043__business_task_progress_report.sql
V044__business_project_progress_report.sql
V045__open_ended_project_plan.sql
V046__staff_cost_policy_retirement.sql
V047__jewelry_influencer_fixed_pricing.sql
V048__jewelry_influencer_bundle_items.sql
V049__business_staff_leave_approval.sql
V050__staff_leave_request_collation.sql
V051__business_operations_integrity_hardening.sql
V052__business_project_deputy_permissions.sql
V053__preserve_unassigned_project_routines.sql
V054__normalize_business_attachment_urls.sql
V055__business_accounting_return_review.sql
V056__business_project_terminal_and_cost_integrity.sql
V057__owner_driven_project_business_plan.sql
V058__proposal_named_staff_cost_snapshot.sql
V059__proposal_daily_staff_cost_snapshot.sql
V060__project_owner_kpi_and_staff_cost_permissions.sql
V061__staff_personal_menu_permissions.sql
V062__project_kpi_automatic_sources.sql
V063__separate_project_delivery_accounting.sql
V064__project_actual_work_and_templates.sql
V065__independent_project_incentive.sql
V066__feishu_readonly_attendance.sql
V067__three_system_product_navigation.sql
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
* `V035__jewelry_maker_basic_product_edit.sql`：制单员可直接维护商品名称与图片，完整商品属性仍仅由管理员修改。
* `V036__business_project_proposal.sql`：全员提交本人负责的立项申请，指定老板一次审批后创建正式项目并直接进入执行。
* `V037__business_project_kpi_bonus.sql`：老板发布项目 KPI 和人民币综合阶梯奖金，负责人填报结算，老板确认后立即形成项目奖金成本。
* `V038__business_staff_monthly_cost.sql`：人员内部成本统一按人民币月度金额维护，并保存中国 21.75 天、越南 26 天的折算快照。
* `V039__bind_company_owners.sql`：将上海美丸文化公司绑定江澜、越南 meimaru 公司绑定王赋章，用于组织管理和人员成本范围；不会覆盖立项申请手动指定的审批/归属老板。
* `V040__jewelry_purchase_amount_precision.sql`：扩展珠宝采购金额精度；属于珠宝 ERP 独立迁移，不改变项目经营、立项、KPI 或人员成本规则。
* `V041__project_governance_modes.sql`：将项目管理强度与结项方式解耦，并增加阶段验收记录。
* `V042__kpi_plan_soft_delete.sql`：KPI 方案和结算改为可审计作废。
* `V043__business_task_progress_report.sql`：保存一次性任务负责人的每日进度、完成说明和成果凭证。
* `V044__business_project_progress_report.sql`：保存项目主负责人每日填报的项目整体进度、完成说明和成果凭证；项目总进度不再从一次性任务推算。
* `V045__open_ended_project_plan.sql`：项目计划结束日期改为可选，支持持续经营且没有固定结束时间的项目。
* `V046__staff_cost_policy_retirement.sql`：人员成本版本增加作废人、时间和原因审计字段；未生效且未被引用的误录版本可受控删除。
* `V047__jewelry_influencer_fixed_pricing.sql`：新增达人固定售价、价格历史和销售单达人快照。
* `V048__jewelry_influencer_bundle_items.sql`：新增达人组合商品明细及相关销售校验结构。
* `V049__business_staff_leave_approval.sql`：负责人提交员工请假申请，归属老板审批通过后才生成按人员和日期全项目生效的正式请假记录。
* `V050__staff_leave_request_collation.sql`：统一请假申请表与项目经营表的字符排序规则，避免老板待办合并查询出现 `Illegal mix of collations`。
* `V051__business_operations_integrity_hardening.sql`：保留请假与撤销的独立审批轨迹，绑定请假来源，清理已退出成员的未完成执行数据，并标准化项目币种。
* `V052__business_project_deputy_permissions.sql`：新增项目副负责人角色，开放本人协管项目的项目中心和执行管理权限，保留主负责人专属提交边界。
* `V053__preserve_unassigned_project_routines.sql`：移除项目成员时仅解除持续工作负责人，保留工作和历史填报。
* `V054__normalize_business_attachment_urls.sql`：统一公司经营附件的 Web 路径分隔符。
* `V055__business_accounting_return_review.sql`：收支草稿支持老板填写原因后退回，并保留退回人和时间的审核轨迹。
* `V056__business_project_terminal_and_cost_integrity.sql`：修正累计成本快照，并统一修复历史结项/取消项目的结束日期、持续工作、人员投入、未完成任务和财务关账状态；历史请假与工作填报冲突仅输出诊断清单，由老板决定保留哪一侧记录。
* `V057__owner_driven_project_business_plan.sql`：新增结构化收入、支出、人员投入和量化目标测算；新项目由负责人确认后直接启动，负责人完成预算、KPI、经营事实、验收和结项闭环，历史审批与财务记录保持不变。
* `V058__proposal_named_staff_cost_snapshot.sql`：立项阶段直接选择具体人员，从人员管理同步当期月度内部成本并保存可追溯快照；原有人月和投入比例字段仅作历史兼容。
* `V059__proposal_daily_staff_cost_snapshot.sql`：立项人员投入保存标准工作天数和日成本快照；固定周期按日成本核算，不限期按月度成本核算。
* `V060__project_owner_kpi_and_staff_cost_permissions.sql`：项目负责人设置本人项目 KPI 与奖金；人员管理对负责人开放只读资料和独立成本维护权限，不开放档案及账号管理。
* `V061__staff_personal_menu_permissions.sql`：增加普通员工个人目录权限覆盖层；空表默认继承原角色权限，老板可按“不显示/仅查看/可维护”设置全部左侧目录，并保留操作级鉴权快照。
* `V062__project_kpi_automatic_sources.sql`：为 KPI 目标和方案快照增加自动取数来源引用，支持收入、业务成本、人员成本、经营结果、持续工作、任务和里程碑按考核周期自动统计。

* `V063__separate_project_delivery_accounting.sql`：新项目分离交付与核算，保存策略版本和独立核算状态。历史项目保持原交付流程；历史终态核算关闭。新增独立关账权限仅授予公司负责人角色，实际操作还须匹配项目归属。停写并备份后执行，可重复运行，不改历史金额和审批。

P1 发布前在 V062 目标库执行 [专项预检](./preflight_p1_delivery_accounting.sql)，核对菜单父级及异常 KPI／结束日期，保存金额摘要；V063 后复查相同摘要。

## P1–P4 配套升级与权限变化

| 脚本 | 变更及兼容要求 |
| --- | --- |
| V063 | 项目交付/核算分离，固定旧策略默认值，不重开历史关闭账 |
| V064 | 新增模板、日历、单位、基线、资源、工作及计价依据；新增 `rate_minutes_per_day`，月度天数允许日/小时费率填 null；已有金额和折算值保留 |
| V065 | 新 KPI 奖励轴为独立版本，存量默认 `LEGACY_LINKED`；独立奖励申请/核准/事件与唯一待确认成本来源，不重复生成旧奖金 |
| V066 | 飞书连接、有效身份映射、同步与来源修订、问题、授权、验收及审计；不启用连接、不修改本地假勤或人工成本 |
| V067 | 单平台三业务系统导航与共享集成目录，中越菜单；独立费率、奖励、假勤和技术角色；移除项目负责人默认原价权限 |
| V068 | 不限期项目人员可不限期参与；立项只维护参与方式和时间，预算按当前预算期间完整工作日估算 |
| V069 | 总额／每日／无上限控制、启动预算、重复收支及首月／稳定月测算；新项目保留按期间估算人员预算 |
| V070 | 项目动态保存被安排人员姓名、账号及稳定 ID 快照 |
| V071 | 一次性任务可恢复停用，任务和持续工作的每段执行区间保留 |
| V072 | 启停动态回填能够唯一匹配的执行人快照 |
| V073 | 项目总目标模式、持续工作的四种目标模式及每日目标版本表 |

已有 V062 库应先备份并保存专项预检与业务数据摘要，再按 V063 → V073 顺序升级；已有 V068 库执行 V069 → V073。前后端须配套。不要在 V067 之后单独重跑 V060 后停止，否则旧脚本会恢复项目负责人原价授权；完整迁移序列必须经过 V067 重新收敛权限，再顺序执行后续迁移。V069–V073 来自旧未提交备份的功能迁移，已重新编号，不可用旧版同编号文件覆盖现有 V061–V065。

新增 `finance_cost_manager`、`hcm_incentive_operator`、`hcm_incentive_approver`、`attendance_reader`、`feishu_integrator` 只创建角色和菜单关系，不自动分配给现有员工。操作仍受公司、项目、申请人和核准责任约束。权限迁移后应刷新用户会话并按实际岗位验收；技术集成身份不得代替假勤切换或奖金业务核准。

2026-09-07 已在本机复制库重复验证 V064–V067 并应用 `localhost / ry-vue`，既有业务列哈希不变。最新证明为本地忽略文件 `logs/product_migration_verification_20260907_153949.json`，不提交仓库。测试库发现的首次基线冲突已通过应用创建逻辑修复，新标准项目及首快照同为 1；迁移不修改旧基线 0。真实飞书授权及六类样本尚待验收，本轮未推送或部署生产。

`verify_business_schema.sql` 在旧检查之后增加 P2–P4 表/字段、模板/基线、计价来源、奖励来源、飞书修订及导航权限检查。异常值用于阻断交接和核查，不能由校验脚本直接修数据。回退只暂停新增写入并保留新策略兼容及历史，不能删除新表、把确认工时还原为比例或恢复假勤自动双写。

最终本机完整校验共 70 组结果全部 0 异常，证明 `logs/product-schema-verification.json`。隔离 QA 库因刻意创建的合成账号/公司不具有完整员工档案或岗位配置，有 3 组既有资料完整性检查非零；新增 P1–P4 检查均无异常。该测试数据差异不应通过删除校验或批量修改真实员工来消除。


### V074：立项人员参与方式持久化

V073 后执行 `V074__proposal_staff_participation_mode.sql`，新增 `biz_project_proposal_staffing.participation_mode`，保存跟随项目、自定义时间和不限期参与方式。日期与项目一致的旧行恢复为跟随项目；有开始日但无结束日的其他旧行恢复为不限期；其他日期区间恢复为自定义。已有明确参与方式不会被覆盖。须先执行迁移再更新后端，脚本可重复执行。

### V075：成员工作日成本

执行 V075__member_workday_cost.sql 后更新后端。未关账项目改为成员参与工作日自动计价；历史投入记录及旧核算版本保留，已关账项目不重算。移除实际投入填报、比例配置与确认流程，休息日不计费。再次加入项目时保留此前参与区间。部署前备份项目、成员、核算结果及菜单表。

### V076：KPI 得分奖金档位

V075 后执行 `V076__kpi_score_incentive_tiers.sql`，为 KPI 奖金规则增加方案绑定与不可变得分档位。旧固定奖金规则和历史单据保持不变。

### V077：奖金分配与发放

V076 后执行 `V077__bonus_allocation_payment.sql`，新增个人奖金分配、明细、发放凭证和审计事件，并补齐奖金查看与发放权限。只记录人工发放凭证，不触发银行转账。

### V078：老板工作台菜单归属

V077 后执行 `V078__boss_workbench_project_navigation.sql`，将原“经营报表”更名为“老板工作台”，移至“项目管理系统”首项，保留菜单 ID 4001 及查看权限。已有该菜单的角色补齐项目父目录授权。前端同步将 `/finance/boss` 重定向到 `/business/boss`，保留查询参数；刷新页面重新加载菜单后生效。脚本可重复执行，执行前备份 `sys_menu`、`sys_role_menu`。

### V079：立项申请菜单名称

V078 后执行 `V079__proposal_menu_title.sql`，将菜单 4009 的中文名称由“项目启动申请”改为“立项申请”，路由和权限保持不变。脚本可重复执行，执行前备份该菜单记录；刷新页面重新加载菜单后生效。

### V080：人员成本设置菜单名称

V079 后执行 `V080__staff_cost_menu_title.sql`，统一菜单名称为“人员成本设置”，保留路由与权限。

### V081：历史成本保护与成员参与规则

先停写并完整备份，再执行 `V081__cost_history_and_participation.sql`，最后更新配套后端。脚本可重复执行：保留人员工作日成本、核算结果及分项快照；新增项目资源的参与方式和成员角色生效记录。只有与原立项人员日期完全匹配的资源行才恢复原参与方式，其余保留自定义，避免扩大成员参与范围。

配套服务保留已计价日期的金额与依据；尚未计价日期可在资料完备后补计。进行中项目不会因超过计划结束日自动停止计费，“跟随项目”随实际参与延续，自定义结束日期仍有效。角色调整从当天起生效，已核算历史保持不变。已关账项目不自动重算。

2026-09-08 已在本机备份库和 `localhost / ry-vue` 各重复验证两次。原 53 条已计价记录及所有旧核算版本金额保持不变；新补计 26 个成员工作日，42 个成员工作日因缺少有效成本待完善。证明见本地 `logs/cost-fix-data-verification.log`，具体修复与验收见 `docs/三系统问题修复与验收-2026-09-08.md`。回退时须保留快照、新参与方式和角色历史，不能删除新增已核算记录或盲目覆盖整库。

### V082：收支、交接及关账后调整

先完整备份，执行 `V082__business_flow_safeguards.sql`，再更新后端和前端。新增无支出确认、暂停计费期间、离职办理、关账后调整四张表；不改写既有表数据。旧客户端未传暂停安排时继续采用保留人员计费；收支旧客户端仍兼容，新表单使用稳定提交编号支持重试。

暂停释放从次日起停止新增成员工作日成本，恢复当日重新计费；已计价日期保持原值。离职办理以指定日期为计费截止日（含当天），须先交接负责项目和未完工作，生效时退出在执行项目并停用账号、撤销会话。未来申请由后台每分钟检查，条件变化导致失败时在办理记录显示原因，须处理交接后取消原申请并重新办理。

已关账项目通过独立调整单纠错，仅项目归属老板可审核，审核通过按当日记录经营结果差额。原流水、核算版本和关账状态保留。回退须保留上述记录，不能删除新表或直接恢复整库。

2026-09-09 本地升级前已完整备份；副本连续执行两次和本地执行后，原有 135 张表校验值一致。运行服务更新后再次检查收支、成员成本、日结果及其分项、项目、成员和人员档案七张核心表，仍与升级前一致。详见 `docs/业务流程修复与验收-2026-09-09.md`。

### V097：公司公共费用

备份后执行 `V097__company_public_expenses.sql`，再启用配套后端和前端。新增7张公共费用表、日结果 `public_cost` 列、专用成本类别及财务菜单。年度/月度费用通过负责人、项目两级分摊，月结记账一次，日均参考不入账。旧数据与历史结果保留，脚本可重复执行。公司老板按组织负责人权限管理，菜单授权后刷新会话。

项目月中结束交付时可保留实际结束日期并等待月结，完成公共费用月结后关闭核算。已月结月份通过调整记录纠错，已关账项目沿用关账后调整。回退须保留新账单和审计，不删除费用事实。详见 `docs/公司公共费用实施与验收.md`。

### V098：公共费用分摊项目名称

执行 V097 后，备份并执行 `V098__public_expense_project_name.sql`。将分摊记录的项目名称上限从 100 字扩至 160 字，与项目表一致，修复长名称项目保存分摊失败。脚本可重复执行，不改写既有分摊金额、历史名称及经营结果。配套后端同时排除离职人员和停用部门中的新分配候选人，历史已下发记录仍保留。

### V099：立项预计利润率范围

备份后执行 `V099__proposal_expected_margin_range.sql`，将立项预计利润率由 `decimal(9,4)` 扩为 `decimal(30,4)`。收入很小而成本较高时，负利润率可能低于 -100000%，原字段会使保存草稿和启动项目失败。新字段保留实际计算值和四位小数，不截断亏损率；零收入仍使用空利润率。脚本可重复执行，不改写既有立项记录，无须重启前后端服务。

### V100：公共费用按日计入成本

备份后执行 `V100__public_expense_daily_cost.sql`，再启动配套后端。新增每日费用表及月账计入方式；既有已月结账单保留月结口径，未月结和新账单采用按日计入。负责人提交后，按项目当月有效期间的自然日分摊，最后一天补齐尾差，仅已到日期进入经营成本和预算。服务启动后及每五分钟补齐到期日，重复执行不重复生成成本。月结确认暂估，差额调整重算对应日期；月结事实留作追溯，但不再额外扣除整月费用。已关账项目保留原记录。脚本可重复执行，回退程序前须妥善保留每日费用及核算版本。

### V101：公司税率与税后盈利

备份后执行 `V101__company_profit_tax.sql`，再启用配套服务。新增公司税率、设置审计及项目结算税率快照三张表，不改写既有经营事实。老板在老板工作台或项目核算与收支中按公司设置0%至100%的税率（最多四位小数）；未设置明确提示暂按0%。税基为扣完已入账的各项成本、奖金、公共费用及管理费后的项目累计正利润，亏损不计税；税额保留到分，税后盈利＝税前结果－税额。日税额按累计税额变化计算，跨日和跨月汇总不重复计税；已批准的关账后调整也参与当期税后计算。关闭核算时保存税率与最终结果，后续公司税率变更不改写该项目快照。历史已关账项目保存原结果及0税额，未擅自补扣历史税款。

### V102：珠宝采购单约定退货日期

`V102__jewelry_supplier_return_date.sql` 为珠宝采购单新增可空的 `supplier_return_date` 字段，历史记录留空，不改动库存、金额和审批状态。原本地文件编号 V097 与公共费用迁移重叠，合并后改为 V102；若曾在本地执行旧文件，保留字段和数据，脚本会检测已存在字段并跳过新增。发布时先备份数据库，按顺序执行迁移后再更新配套程序；本次合并不自动执行迁移。

### V104：两家公司共同管理

`V104__shared_company_management.sql` 新增公司授权和变更记录表。首次执行时，现有有效老板对现有有效公司获得经营、组织、成本查看/修改、考勤查询及授权管理权限；来源切换只授给原公司负责人，飞书技术配置需另行授权。新增公司或老板不会自动继承授权，重复执行不会恢复已撤销权限。

先完成 V103 公共人员费用迁移，再执行 V104 并更新后端。老板工作台新增“公司管理授权”，按公司设置，变更需填写原因并检查版本。公司授权与原有菜单权限同时生效；公司授权不能代替系统管理员权限。个人任务、申请人启动、禁止自审、支付防重、历史核算快照保持原流程。

## V105 仓库调货

新增单据头 `source_warehouse`、`target_warehouse` 字段，历史单据留空，迁移可重复执行。部署支持仓库调货的后端前必须先执行 `V105__jewelry_transfer_warehouses.sql`。单据仅从当前统一仓库调出，接收方另行采购入库，不引入分仓库存。

## V106 立项人员跨项目投入分配

执行 `V106__proposal_staff_allocation_plan.sql` 后再发布配套前后端。迁移为立项人员计划新增 `allocation_plan_json`，用于保存人员在本项目及其他有效项目间的投入分配和调整原因。项目启动时会再次检查版本；其他项目已变化时要求重新调整，涉及其他负责人时沿用投入调整确认流程。脚本可重复执行，不改写历史立项和既有项目投入记录。

## V107 老板菜单随角色管理

执行前备份数据库。`V107__company_owner_role_managed_menus.sql` 删除公司老板账号的个人菜单权限快照，配套程序改为读取当前角色菜单，避免旧快照隐藏新增页面。普通员工的个人菜单策略不变。脚本可重复执行；如需恢复已删除的老板个人菜单配置，须从迁移前备份恢复对应记录。

## V109 主项目向子项目拨款

执行 `V109__subproject_parent_funding.sql` 后再发布配套前后端。迁移新增独立的子项目拨款表，不扩张已接近 MySQL 单行上限的项目和立项主表。主项目负责人新增子项目时必须填写拨款额度和说明；草稿交接后额度即被保留，删除草稿自动释放，子项目启动后转为正式占用。子项目的人员预算与业务预算合计不得超过拨款额度。V109 本身只保存预算分配，最终核算规则由 V110–V112 承接。历史子项目额度保留为空，脚本可重复执行。

## V110–V112 子项目拨款自动内部转拨

必须按顺序执行 V110、V111、V112。V110 建立拨款核算类别；V111 清理过渡期自动预计收入并暂停专用类别；V112 启用最终自动内部转拨规则。子项目启动时系统自动生成主项目拨款支出和子项目拨款收入，两个事实使用稳定业务键防止重复入账，公司合并汇总时抵销。拨款专用类别不出现在手工录入选项中，也禁止通过接口重复录入；历史经营事实保留。

## V117 成品退供期限邮件去重记录

执行 `V117__jewelry_supplier_return_mail_log.sql` 后再启用邮件预警。每日最多登记一次发送尝试；SMTP 结果不明确时不自动重发，避免重复提醒。迁移不改写库存或历史单据，可重复执行。

## V118 样品与非样品共用 SKU

执行 `V118__jewelry_product_sample_sku_scope.sql` 后，同一 SKU 最多可有一个样品商品和一个非样品商品；成品、散件、配件、福利商品之间仍不能共用 SKU。库存、成本、单据与流水继续按各自 `product_id` 独立记录。迁移增加按商品类型生成的 `sku_scope` 和联合唯一索引，删除旧的全局 SKU 唯一索引；可重复执行。发布前备份数据库，并先执行迁移再启动新版后端。

## V119 KPI 实际值精度

执行 `V119__kpi_actual_unit_precision.sql` 后，`biz_project_kpi_result.actual_value` 扩大为 `decimal(24,8)`，以“万元”等单位填报的 KPI 在折算为元时仍可保留分位精度。迁移可重复执行；发布前备份数据库，并先执行迁移再启动新版后端。

## V120 项目删除审核

执行 `V120__project_deletion_approval.sql` 后再启用配套前后端。项目主负责人填写原因提交删除申请，项目在待审期间保持原状；系统管理员或项目归属公司的老板任一方审核通过后才软删除项目，审核记录与项目事件保留。驳回后可再次申请，主项目有在用子项目时不能申请或通过删除。迁移可重复执行。

## V121 项目删除审核结果通知

执行 `V121__project_deletion_result_notification.sql` 后再启用配套后端。删除申请审核通过或驳回时，在同一事务中给申请负责人写入一条站内通知；通知使用项目名称快照，项目软删除后仍可阅读。通知按本人隔离并可标记已读。迁移可重复执行，不补发历史审核结果。

## V124 持续工作汇报

执行 `V124__project_continuous_work_reports.sql` 后再启用配套后端。“我的安排”中的持续工作可提交每日、每周或每月文字及附件汇报；负责人工作台在项目完成量下方查看汇报。汇报独立保存，不影响每日完成量；迁移可重复执行。

## V125 工作汇报验收

在 V124 之后执行 `V125__project_work_report_review.sql`，为成员提交的工作汇报增加待验收、通过和退回状态，以及负责人和验收意见。已有汇报进入待验收，退回原因由成员在“我的安排”查看后可重新提交。

## V126 工作汇报退回通知

在 V125 之后执行 `V126__project_work_report_return_notification.sql`。负责人退回工作汇报时，同一事务给提交成员写入一条站内通知，成员可在顶部消息铃铛查看退回原因并进入“我的安排”重新提交。迁移会为已有退回汇报补发通知；可重复执行。

## V127 项目级主动工作汇报

在 V126 之后执行 `V127__project_level_work_reports.sql`。成员从“我的安排”的“主动汇报工作”选择已参与的项目，直接提交文字或附件汇报，无需选择持续工作。既有绑定持续工作的历史汇报保留；迁移可重复执行。

## V128 立项申请自主选择所属部门

在 V127 之后执行 `V128__proposal_project_department.sql`，再启动新版后端。新立项申请可从归属公司的部门中自主选择，所选部门随项目启动保存到正式项目，并用于财务报表；历史项目仍按原负责人部门显示。

## V122–V123、V131 达人商品条款与成本价

按顺序执行 V122、V123、V131 后再发布配套前后端。V122 增加达人商品的费率、履约费用和搭售配置；V123 保存销售明细的商品费率快照；V131 增加达人商品成本价，并以商品当前移动平均成本初始化已有绑定。新建销售出库单会带入绑定中的直播价、成本价、费率和履约费用，历史销售单继续使用原单据快照。

## V132 赠品商品独立 SKU

执行 `V132__jewelry_gift_product_sku_scope.sql` 后再启用赠品商品。赠品商品有独立 SKU 分组，可与成品商品、样品商品共用 SKU 和商品名称；同一 SKU 在赠品分组内仍只能有一条商品档案。现有样品与其他商品的 SKU 规则保持不变，库存和单据继续按商品 ID 分开记录。迁移可重复执行。

## V133 达人商品采购默认值

执行 `V133__jewelry_influencer_purchase_defaults.sql` 后再发布达人绑定录入功能。常用供应商和参考采购单价保存在达人商品绑定中，只用于采购入库时的建议值；供应商退货仍以原采购单为准。迁移可重复执行。
