# 数据库迁移说明

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
