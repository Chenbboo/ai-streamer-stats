# 老板端页面、接口与 AI 能力清单

> 盘点日期：2026-08-13
> 适用角色：江澜、王赋章等拥有老板工作台权限的账号
> 目标：以老板现有页面和后端接口为基准，明确哪些业务已经可以由 AI 完成，哪些仍需注册为 AI 能力。

## 1. 状态定义

| 标记 | 含义 |
|---|---|
| ✅ 已接入 | AI 已有可调用工具；读操作可直接执行，写操作必须经过确认单 |
| 🟡 部分接入 | AI 能完成其中一部分，但尚未覆盖页面上的全部操作或全部状态 |
| ⬜ 待接入 | 页面和后端接口已经存在，但尚未注册成 AI 工具 |
| ⚙️ AI 基础设施 | 供对话、历史、确认与拒绝使用，不是单独的经营业务能力 |

## 2. 总体盘点

| 范围 | 页面 | 后端接口数 | 当前 AI 情况 |
|---|---|---:|---|
| 老板工作台 | `/business/boss` | 3 个直接看板接口，复用项目状态接口 | 项目组合、待办、经营总览已接入；部分状态操作已接入 |
| 老板 AI 助理 | `/business/boss-ai` | 4 | 对话、历史、确认、拒绝完整可用 |
| 项目中心 | `/business/projects` | 39（含负责人/员工工作接口） | P0 已接入：项目资料、KPI、成员、任务、持续工作、成本、投入与完整状态流转 |
| 人员管理 | `/business/staff` | 8，另有 2 个成本政策接口位于项目控制器 | P0 已接入：查询、档案新增/编辑、职责、启停账号和成本政策；安全重置密码留到 P1 |
| 部门管理 | `/business/departments` | 6 | 查询、新增、编辑已接入；排序和删除待接入 |
| 每日收支 | `/business/accounting` | 9 | P0 已接入：总览、目录、草稿、确认、冲销、重算和结果明细 |
| 合计 | 6 个老板核心页面 | 66 | AI 基础架构与老板日常 P0 执行能力完成；P1 治理细节待补齐 |

## 3. 老板工作台能力

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查看本人项目总量、状态、逾期、高风险、待办 | `GET /business/boss/dashboard` | `business:boss:view` | ✅ | `project.portfolio.get`、`business.pending-decisions.get` | 保持 |
| 查看两位老板的项目名称和立项归属 | `GET /business/boss/project-directory` | `business:boss:view` | ✅ | `project.directory.get`；非本人项目只暴露安全目录字段 | 保持数据隔离 |
| 查看今日收入、业务成本、人员成本和盈亏 | `GET /business/accounting/boss-overview` | `business:boss:view` | ✅ | `business.operating.overview` | 保持确定性计算 |
| 查看待老板处理事项 | `GET /business/boss/dashboard` | `business:boss:view` | ✅ | AI 可汇总草稿、待计划审批、暂停和待验收项目 | 保持 |
| 草稿项目进入规划 | `POST /business/project/{id}/transition` | `business:project:manage` | ✅ | `boss_prepare_project_transition`，确认后执行 | 后续迁入通用 capability |
| 批准或退回负责人计划 | 同上 | `business:project:manage` | ✅ | `boss_project_plan_review` + `boss_prepare_plan_decision` | 后续迁入通用 capability |
| 恢复暂停项目 | 同上 | `business:project:manage` | ✅ | `boss_prepare_project_transition`，确认后执行 | 后续迁入通用 capability |
| 暂停、直接关闭、取消项目 | 同上 | `business:project:manage` | ✅ | `project.transition`，统一确认后执行 | 已完成 |
| 审核待验收项目并结项或退回 | `GET/PUT/POST` 验收相关接口 | `business:boss:view` / `business:project:manage` | ✅ | `boss_project_acceptance_review` + `boss_prepare_acceptance_decision` | 后续迁入通用 capability |

## 4. 项目中心能力

### 4.1 项目基础与经营配置

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查询项目列表 | `GET /business/project/list` | `business:project:list` | ✅ | `project.directory.get` | 保持 |
| 查看项目完整详情 | `GET /business/project/{projectId}` | `business:project:list` | ✅ | `project.detail.get`，包含目标、计划、成员、任务、持续工作、KPI、风险和验收 | 保持 |
| 查询可选负责人/成员 | `GET /business/project/user-options` | 项目新增/成员/任务任一权限 | ✅ | `staff.directory.get` | 保持稳定用户 ID |
| 新建立项 | `POST /business/project` | `business:project:add` | ✅ | `project.create`；使用稳定负责人/公司 ID，生成完整确认单后创建 | 已迁入通用 capability |
| 查看和修改未确认的 AI 立项草稿 | AI 草稿工作流 | `business:project:add` | ✅ | `project.draft.get`、`project.draft.update` | 保持重新校验 |
| 编辑已经存在的项目基础资料 | `PUT /business/project` | `business:project:edit` | ✅ | `project.update`，仅覆盖明确修改字段 | 已完成 |
| 查看经营配置 | `GET /business/project/{projectId}/operating-config` | `business:project:list` | ✅ | `project.operating-config.get` | 已完成独立查询 |
| 调整预算并保留历史 | `PUT /business/project/{projectId}/budget` | `business:project:manage` | ✅ | `project.budget.update`，确认后执行 | 保持 |
| 新增/修改 KPI 版本 | `POST /business/project/kpi` | `business:project:manage` | ✅ | `project.kpi.save` | 已完成 |
| 停用 KPI | `DELETE /business/project/{projectId}/kpi/{kpiId}` | `business:project:manage` | ✅ | `project.kpi.retire` | 已完成 |
| 更换项目主负责人 | `PUT /business/project/{projectId}/owner` | `business:project:manage` | ✅ | `project.owner.change`，确认后执行 | 保持 |

### 4.2 成员、任务、持续工作与风险

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 新增/调整项目成员 | `POST /business/project/member` | `business:project:member` | ✅ | `project.member.save` | 已完成 |
| 移除项目成员 | `DELETE /business/project/{projectId}/member/{userId}` | `business:project:member` | ✅ | `project.member.remove` | 已完成 |
| 新增/修改里程碑 | `POST /business/project/milestone` | `business:project:task` | ⬜ | AI 目前只能读取 | P2；若业务继续弱化里程碑可暂缓 |
| 删除里程碑 | `DELETE /business/project/{projectId}/milestone/{milestoneId}` | `business:project:task` | ⬜ | — | P2 |
| 新增/修改一次性任务 | `POST /business/project/task` | `business:project:task` | ✅ | `project.task.save` | 已完成 |
| 删除一次性任务 | `DELETE /business/project/{projectId}/task/{taskId}` | `business:project:task` | ✅ | `project.task.remove` | 已完成 |
| 新增/修改持续工作 | `POST /business/project/routine` | `business:project:task` | ✅ | `project.routine.save` | 已完成 |
| 停用持续工作 | `DELETE /business/project/{projectId}/routine/{routineId}` | `business:project:task` | ✅ | `project.routine.retire` | 已完成 |
| 员工提交持续工作完成量 | `POST /business/project/routine-report` | `business:project:report` | ⬜ | 属于员工 AI 范围，不应由老板 AI 代填 | 留给员工 AI |
| 新增/修改项目风险 | `POST /business/project/risk` | `business:project:task` | ⬜ | AI 可读取风险，但不能写入 | P1，新增 `project.risk.save` |
| 关闭/删除风险 | `DELETE /business/project/{projectId}/risk/{riskId}` | `business:project:task` | ⬜ | 当前接口语义为删除/停用 | P1，建议明确“关闭”语义后接入 |

### 4.3 人员投入、成本与请假

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查看人员成本政策 | `GET /business/staff/{staffUserId}/cost-policies` | `business:staff:list` | ✅ | `staff.cost-policy.get` | 已完成 |
| 设置人员成本政策 | `POST /business/staff/cost-policy` | `business:staff:manage` | ✅ | `staff.cost-policy.save`，确认后执行 | 已完成 |
| 新增/修改成员计划投入 | `POST /business/project/staff-allocation` | `business:project:allocation` | ✅ | `project.allocation.save` | 已完成 |
| 停用成员计划投入 | `DELETE /business/project/{projectId}/staff-allocation/{allocationId}` | `business:project:allocation` | ✅ | `project.allocation.retire` | 已完成 |
| 查看负责人工作台和投入状态 | `GET /business/owner/dashboard` | `business:project:owner:view` | 🟡 | `project.detail.get`/成员进度查询可看到一部分，但没有负责人工作台专用聚合工具 | 项目负责人 AI 阶段完善 |
| 确认成员实际投入 | `POST /business/owner/{projectId}/member/{memberUserId}/effort/confirm` | `business:project:allocation` | ⬜ | 负责人业务，不建议老板 AI 默认代办 | 留给负责人 AI |
| 退回成员实际投入 | `POST /business/owner/{projectId}/member/{memberUserId}/effort/return` | 同上 | ⬜ | 同上 | 留给负责人 AI |
| 登记/取消员工今日请假 | `POST/DELETE /business/owner/{projectId}/member/{memberUserId}/leave` | `business:project:allocation` | ⬜ | 请假会让该人员当日所有项目人员成本归零 | P1；老板可操作，负责人 AI 也需接入 |
| 负责人填写今日项目业务总花费 | `POST /business/accounting/project-daily-spend` | `business:project:report` | ⬜ | 不应由老板 AI 伪造负责人填报 | 留给负责人 AI |

## 5. 人员管理能力

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查询人员列表/筛选 | `GET /business/staff/list` | `business:staff:list` | 🟡 | `staff.directory.get` + `staff.profile.get` 可查稳定人员档案；批量完整档案筛选留到 P1 | P1 补批量查询 |
| 查询公司和部门选项 | `GET /business/staff/departments` | `business:staff:list` | ✅ | `department.directory.get` | 保持 |
| 查询人员稳定 ID | `GET /business/staff/options` | `business:staff:list` | ✅ | `staff.directory.get` | 保持 |
| 查看某人的负责/参与项目 | `GET /business/staff/{userId}/projects` | `business:staff:list` | ✅ | `staff.project.responsibilities` | 保持 |
| 新增人员账号和档案 | `POST /business/staff` | `business:staff:manage` | ✅ | `staff.create`；不在对话保存密码，创建后先停用 | 已完成 |
| 编辑人员档案、组织关系 | `PUT /business/staff` | `business:staff:manage` | ✅ | `staff.profile.update`，仅覆盖明确字段 | 已完成 |
| 启用/停用人员账号 | `PUT /business/staff/status` | `business:staff:manage` | ✅ | `staff.account.status.change`，确认后执行 | 保持 |
| 重置人员密码 | `PUT /business/staff/password` | `business:staff:manage` | ⬜ | 高敏感操作，必须确认且不得把密码写入对话历史 | P1，设计专用安全确认 |

## 6. 部门管理能力

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查看两家公司和部门树 | `GET /business/department/list` | `business:department:list` | ✅ | `department.directory.get` | 保持 |
| 查看部门人员明细/负责人候选 | `GET /business/department/staff` | `business:department:list` | ✅ | 目录能力可组合人员目录完成定位 | 可补独立成员明细查询 |
| 新增部门 | `POST /business/department` | `business:department:manage` | ✅ | `department.save`，确认后执行 | 保持 |
| 编辑部门名称、负责人、电话、邮箱、状态 | `PUT /business/department` | `business:department:manage` | ✅ | `department.save`，确认后执行 | 保持 |
| 调整部门排序 | `PUT /business/department/sort` | `business:department:manage` | ⬜ | — | P2，新增 `department.sort` |
| 删除部门 | `DELETE /business/department/{deptId}` | `business:department:manage` | ⬜ | 高风险，需展示影响人员并二次确认 | P1，新增 `department.remove` |

## 7. 每日收支能力

| 页面能力 | 接口 | 权限 | AI 状态 | AI 工具/说明 | 下一步 |
|---|---|---|---|---|---|
| 查询按公司、项目、日期汇总的收支 | `GET /business/accounting/dashboard` | `business:accounting:list` | ✅ | `accounting.result.list` 按项目和日期查询，`accounting.result.detail` 查逐项明细 | 已完成独立查询 |
| 查询老板今日经营总览 | `GET /business/accounting/boss-overview` | `business:boss:view` | ✅ | `business.operating.overview` | 保持 |
| 查询收支项目、公司、分类目录 | dashboard 返回的目录数据 | `business:accounting:list` | ✅ | `accounting.directory.get` | 保持，录入前先定位真实 ID |
| 录入项目收入/支出草稿 | `POST /business/accounting/fact` | `business:accounting:add` | ✅ | `accounting.fact.draft.create`，确认后写入草稿 | 保持 |
| 项目负责人录入经营事实 | `POST /business/accounting/project-fact` | `business:project:report` | ⬜ | 不属于老板默认代填范围 | 留给负责人 AI |
| 确认收支草稿 | `PUT /business/accounting/fact/{factId}/confirm` | `business:accounting:confirm` | ✅ | `accounting.fact.list` 定位后由 `accounting.fact.confirm` 确认执行 | 已完成 |
| 冲销已确认收支 | `POST /business/accounting/fact/{factId}/reverse` | `business:accounting:confirm` | ✅ | `accounting.fact.reverse`，强制原因与确认单 | 已完成 |
| 重新核算项目某日结果 | `POST /business/accounting/recalculate` | `business:accounting:recalculate` | ✅ | `accounting.project-day.recalculate` | 已完成 |
| 查看项目日结果及人员成本明细 | `GET /business/accounting/result/{resultId}` | `business:accounting:list` | ✅ | `accounting.result.detail` | 已完成 |

## 8. 老板 AI 基础设施

| 能力 | 接口 | 权限 | 状态 | 说明 |
|---|---|---|---|---|
| 发送自然语言请求 | `POST /business/ai/boss/chat` | `business:boss:view` | ⚙️ | 模型只看到当前登录账号有权使用的工具 |
| 读取对话历史 | `GET /business/ai/boss/conversation/{conversationId}` | `business:boss:view` | ⚙️ | 历史记录按账号隔离 |
| 确认 AI 操作 | `PUT /business/ai/boss/action/{actionRequestId}/confirm` | `business:boss:view` + 目标能力权限复核 | ⚙️ | 执行时重新读取当前权限；不是只依赖聊天时权限 |
| 拒绝 AI 操作 | `PUT /business/ai/boss/action/{actionRequestId}/reject` | `business:boss:view` | ⚙️ | 拒绝后业务数据不变 |

## 9. 当前已注册给模型的工具

### 9.1 通用能力注册表

| 工具代码 | 类型 | 权限 |
|---|---|---|
| `business.operating.overview` | 只读 | `business:boss:view` |
| `project.portfolio.get` | 只读 | `business:boss:view` |
| `project.directory.get` | 只读 | `business:project:list` |
| `project.detail.get` | 只读 | `business:project:list` |
| `staff.directory.get` | 只读 | `business:staff:list` |
| `staff.project.responsibilities` | 只读 | `business:staff:list` |
| `department.directory.get` | 只读 | `business:department:list` |
| `accounting.directory.get` | 只读 | `business:accounting:list` |
| `project.draft.get` | 只读 | `business:project:add` |
| `project.draft.update` | 草稿写入 | `business:project:add` |
| `project.budget.update` | 确认后执行 | `business:project:manage` |
| `project.owner.change` | 确认后执行 | `business:project:manage` |
| `staff.account.status.change` | 确认后执行 | `business:staff:manage` |
| `department.save` | 确认后执行 | `business:department:manage` |
| `accounting.fact.draft.create` | 确认后执行 | `business:accounting:add` |
| `project.operating-config.get` | 只读 | `business:project:list` |
| `project.update` | 确认后执行 | `business:project:edit` |
| `project.kpi.save` / `project.kpi.retire` | 确认后执行 | `business:project:manage` |
| `project.member.save` / `project.member.remove` | 确认后执行 | `business:project:member` |
| `project.task.save` / `project.task.remove` | 确认后执行 | `business:project:task` |
| `project.routine.save` / `project.routine.retire` | 确认后执行 | `business:project:task` |
| `project.allocation.save` / `project.allocation.retire` | 确认后执行 | `business:project:allocation` |
| `project.transition` | 确认后执行 | `business:project:manage` |
| `project.acceptance.review` | 只读验收材料、成果凭证与前置检查 | `business:boss:view` |
| `project.acceptance.decide` | 确认后验收结项或退回负责人 | `business:project:manage` |
| `staff.profile.get` / `staff.cost-policy.get` | 只读 | `business:staff:list` |
| `staff.create` / `staff.profile.update` / `staff.cost-policy.save` | 确认后执行 | `business:staff:manage` |
| `accounting.fact.list` / `accounting.result.detail` | 只读 | `business:accounting:list` |
| `accounting.fact.confirm` / `accounting.fact.reverse` | 确认后执行 | `business:accounting:confirm` |
| `accounting.project-day.recalculate` | 确认后执行 | `business:accounting:recalculate` |
| `project.create` | 确认后执行 | `business:project:add` |
| `project.plan.review` | 只读 | `business:boss:view` |
| `project.plan.decide` | 确认后执行 | `business:project:manage` |

### 9.2 旧工具退出情况

| 旧工具代码 | 当前状态 | 替代能力 |
|---|---|---|
| `boss_query_business` | 已从模型工具目录删除 | 经营、项目、待办、人员、预算和明细分别由独立 capability 接管 |
| `boss_prepare_project_transition` | 已从模型工具目录删除 | `project.transition` |
| `boss_project_acceptance_review` | 已从模型工具目录删除 | `project.acceptance.review` |
| `boss_prepare_acceptance_decision` | 已从模型工具目录删除 | `project.acceptance.decide` |

当前模型工具目录只由 `AiCapabilityRegistry` 生成，不再接受额外的 `boss_*` 聚合或关键词工具提供器。历史确认单仍可按已保存的 actionCode 完成，历史兼容不参与新消息路由。

### 9.3 老板查询能力映射

| 经营问题 | 模型可组合调用的能力 |
|---|---|
| 今天公司经营怎么样 | `business.operating.overview` |
| 全部项目有哪些、状态如何 | `project.portfolio.get` + `project.directory.get` |
| 有哪些事项需要老板处理 | `business.pending-decisions.get` |
| 公司现在有多少人、分布如何 | `staff.overview.get`；需要姓名时再用 `staff.directory.get` |
| 某个项目的目标、人员、任务和进度 | `project.directory.get` + `project.detail.get` |
| 某项目某段时间的经营结果 | `accounting.result.list` + `accounting.result.detail` |
| 某项目预算和内部核算配置 | `project.operating-config.get` |
| 某个人负责和参与哪些项目 | `staff.directory.get` + `staff.project.responsibilities` |

## 10. 下一轮接入顺序

### P0：老板日常必须能通过 AI 完成

> 状态：✅ 本轮已完成，相关能力已注册到统一 capability 工具层并复用现有业务服务。

1. 正式项目基础资料编辑。
2. KPI 新增、改版、停用。
3. 项目成员新增、调整、移除。
4. 持续工作和一次性任务新增、调整、停用。
5. 人员成本政策与项目投入比例设置。
6. 暂停、关闭、取消项目的完整状态操作。
7. 新增和编辑人员档案。
8. 收支草稿确认、冲销、重新核算、日结果明细。

### P1：补齐经营与治理细节

1. 风险新增、更新、关闭和删除：✅ 已注册 `project.risk.save` / `project.risk.remove`。
2. 指定日期请假及取消请假：✅ 已注册 `project.member.leave.set`。
3. 部门删除确认：✅ 已注册 `department.remove`，业务服务继续校验根节点、子部门和在职人员。
4. 安全重置密码：✅ 已注册 `staff.password.reset`；明文只在本轮内存中转换为不可逆密文，确认单、工具审计和回答均不保存明文。人员完整档案查询继续补充字段覆盖。
5. 任意日期范围经营查询已完成；P1 转为查询结果声明核验和更细粒度经营指标。

### P2：可根据实际使用决定

1. 里程碑维护：✅ 已注册 `project.milestone.save` / `project.milestone.remove`。
2. 部门显示顺序调整：✅ 已注册 `department.sort.update`。
3. 老板页面中的低频辅助操作。

## 11. 验收原则

每新增一项 AI 能力必须同时满足：

1. 工具只对拥有对应页面/接口权限的登录账号可见。
2. 工具执行前由服务端再次校验权限和数据范围。
3. 查询结果来自业务服务或数据库事实，不允许模型自行计算关键金额和状态。
4. 写操作展示对象、字段变化和影响范围，并由老板确认后执行。
5. 删除、冲销、关闭、密码等高风险操作使用更严格的确认规则。
6. 操作写入审计记录，可追溯账号、时间、工具、参数和结果。
7. 江澜与王赋章分别测试本人项目详情隔离，同时保留跨老板项目名称目录可见。
8. 一旦目录或查询能力返回了稳定 ID，后续工具参数必须引用该结果；模型使用列表序号或虚构 ID 时，服务端拒绝该次调用并把候选值返回给模型自动纠正，不中断整次对话。

## 12. 模型优先路由边界

- DeepSeek 已启用时，新消息只由模型从当前登录账号有权使用的能力目录中选择工具；本地关键词不得改写模型选择的意图、实体或操作。
- 正在收集立项资料时，模型仅获得 `project.draft.get` 和 `project.draft.update`：自然语言理解由模型完成，后端重新校验草稿并生成确认单，模型不能直接创建正式项目。
- 模型规划必须选择当前账号可用的 Capability；未调用工具或返回的工具无法由通用能力层执行时，本次请求明确失败，不进入旧关键词流程。
- 模型规划失败时，本次请求明确失败，并保证不执行任何系统写操作；不得静默降级到关键词写入。
- `project.create`、`project.plan.review`、`project.plan.decide` 已迁入通用能力注册表；对应的 `boss_prepare_project_create`、`boss_project_plan_review`、`boss_prepare_plan_decision` 已从模型工具目录移除，新消息无法再调用旧入口。
- `boss_query_business` 及旧工具提供器已经删除；经营、项目、人员、待办、预算与成员职责由独立能力组合完成。
- 历史确认单的读取与确认执行仍保留兼容，避免升级后已有待确认记录失效；这部分只按明确的历史 actionCode 处理，不参与自然语言路由。
- 历史消息中的旧工具代码只用于还原旧卡片和审计记录，不得参与新消息路由。

## 13. 最终回答事实校验

- 老板 AI 通过能力工具取得数据后，最终文字必须经过服务端事实校验，模型提示词不再是唯一保障。
- 服务端从本轮已授权能力结果中提取金额、数量、日期以及项目、人员、公司和部门名称；回答中出现的新事实如果找不到本轮依据，不允许直接展示。
- 首次校验失败时，模型只拿本轮能力结果和违规项重写一次；重写后的文字再次经过同一套服务端校验。
- 重写仍不通过或模型校对暂不可用时，系统返回安全提示，引导老板查看原始依据或确认卡，不展示未经核验的结论。
- 校验状态写入消息元数据：`PASSED` 表示原回答通过，`REWRITTEN` 表示已自动纠正，`SAFE_FALLBACK` 表示已阻止不可靠回答。
- 普通寒暄等没有业务工具结果的对话不做事实限制；写操作的真实结果始终以系统确认卡、执行记录和审计日志为准。
