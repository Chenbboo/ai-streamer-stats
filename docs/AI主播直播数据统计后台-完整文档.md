# AI 主播直播数据统计后台 — 系统完整文档

> 最后更新：2026-07-07（接入硅基流动 Qwen3-VL 视觉模型、聊天内容识别、AI识别加载状态、统计看板数据源修复）

---

## 一、系统架构

```
浏览器 ──→ Vue3 前端 (:80) ──→ Spring Boot (:8080) ──→ MySQL 8.0 (ry-vue)
                                                    │
                                                    └──→ 硅基流动 API (Qwen3-VL-32B-Instruct)
                                                    └──→ Redis (:6379)
                                                    └──→ 文件存储 (uploads/)
```

| 组件 | 位置 | 端口 |
|------|------|------|
| MySQL 8.0 | localhost | 3306 |
| Redis | localhost | 6379 |
| Spring Boot 后端 | J:\codex-projects\ai-streamer-stats\backend | 8080 |
| Vue3 前端 | J:\codex-projects\ai-streamer-stats\frontend | 80 |
| AI 模型 | 硅基流动 API | HTTPS |

**访问地址**: `http://localhost`（admin / admin123）

**GitHub 仓库**:
- 后端: https://github.com/Chenbboo/ai-streamer-backend（feature/live-upload 分支）
- 前端: https://github.com/Chenbboo/ai-streamer-frontend（feature/live-upload 分支）

---

## 二、技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | RuoYi 3.9.2（Spring Boot 2.5.15 + Java 8） |
| 前端框架 | Vue 3.5 + Element Plus 2.13 + Vite |
| 数据库 | MySQL 8.0 + MyBatis |
| 缓存 | Redis（若依 session + 配置缓存） |
| 图表 | ECharts 5.6 |
| HTTP 客户端 | JDK 原生 HttpURLConnection |
| AI 模型 | 硅基流动 Qwen/Qwen3-VL-32B-Instruct（OpenAI 兼容格式） |
| 包管理 | Maven 3.9（后端）+ pnpm（前端） |

---

## 三、数据库（ry-vue 库）

### 连接信息
```
host: localhost:3306
user: root
password: 123456
database: ry-vue
characterEncoding: UTF-8
```

### 7 张业务表

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `live_streamer` | 主播信息 | streamer_id, user_id(关联sys_user), stage_name, tiktok_handle, status(0在职/1离职) |
| `live_customer` | 客户信息 | customer_id, nickname(唯一), badge, first_seen_date, last_seen_date, merged_into_id |
| `live_upload` | 上传记录 | upload_id, biz_date, streamer_id, upload_type(1打赏/2聊天/3汇报), file_path, raw_text, ai_status, ai_result(JSON) |
| `live_gift_record` | 打赏明细 | gift_id, biz_date, streamer_id, customer_id, xu, rank_no, source_upload_id |
| `live_chat_contact` | 聊天联系人 | contact_id, biz_date, streamer_id, customer_id, upload_id |
| `live_chat_message` | 聊天消息明细（预留） | — |
| `live_daily_report` | 每日汇报 | report_id, biz_date, streamer_id, total_xu, raw_text, upload_id |

### AI 识别状态码（live_upload.ai_status）

| 值 | 含义 |
|----|------|
| 0 | 待识别 |
| 1 | 已识别 |
| 2 | 已校正入库 |
| 3 | 识别失败 |
| 4 | 识别中 |

### sys_config 表（AI 配置，6 行）

| config_key | config_value | 说明 |
|---|---|---|
| `live.ai.enabled` | `true` | AI 开关 |
| `live.ai.provider` | `openai-compatible-chat` | API 格式 |
| `live.ai.apiKey` | `sk-pukbxg...` | 硅基流动 Key |
| `live.ai.baseUrl` | `https://api.siliconflow.cn/v1/chat/completions` | 端点 |
| `live.ai.model` | `Qwen/Qwen3-VL-32B-Instruct` | 视觉模型 |
| `live.ai.timeout` | `120` | 超时秒数 |

### sys_menu 表（直播模块菜单，menu_id 2000-2099）

| menu_id | 名称 | 路径 | 权限标识 |
|---------|------|------|----------|
| 2000 | 直播数据管理 | live | — |
| 2001 | 图片上传 | live/upload/index | live:upload:list |
| 2002 | 识别校正 | live/review/index | live:review:list |
| 2003 | 数据统计 | live/stats/index | live:stats:list |

### 3 个角色

| role_id | 角色 | role_key | 权限范围 |
|---------|------|----------|----------|
| 3 | 主播 | streamer | 上传自己的截图 + 查看自己的统计 |
| 4 | 运营 | operator | 上传、校正、查看全部统计 |
| 5 | 直播管理员 | live_admin | 全部权限（含删除） |

---

## 四、API 端点（14个）

### 上传模块（/live/upload）

| 方法 | 路径 | 权限 | 用途 |
|------|------|------|------|
| GET | /live/upload/list | live:upload:list | 明细列表 |
| GET | /live/upload/daily | live:upload:list | 按日汇总（完整性） |
| POST | /live/upload/img | live:upload:add | 批量上传截图（multipart） |
| POST | /live/upload/report | live:upload:add | 提交汇报文本 |
| DELETE | /live/upload/{ids} | live:upload:remove | 删除上传记录 |

### 主播模块（/live/streamer）

| 方法 | 路径 | 权限 | 用途 |
|------|------|------|------|
| GET | /live/streamer/listAll | — | 在职主播下拉列表 |

### 识别校正模块（/live/review）

| 方法 | 路径 | 权限 | 用途 |
|------|------|------|------|
| GET | /live/review/list | live:review:list | 待校正列表 |
| POST | /live/review/recognize/{id} | live:review:edit | 触发 AI 识别 |
| POST | /live/review/mock/{id} | live:review:edit | 模拟识别（测试用） |
| PUT | /live/review/result/{id} | live:review:edit | 保存校正结果 |
| POST | /live/review/confirm/{id} | live:review:confirm | 确认入库 |

### 统计模块（/live/stats）

| 方法 | 路径 | 权限 | 用途 |
|------|------|------|------|
| GET | /live/stats/weekly | live:stats:list | 周统计数据 |

---

## 五、前端弹窗设计

### 5.1 图片上传页（图片上传）

**每日提交表单**:

| 字段 | 组件 | 说明 |
|------|------|------|
| 业务日期 | el-date-picker | 必填，默认今天 |
| 主播 | el-select | 必填，数据来自 `/live/streamer/listAll` |
| 打赏榜截图 | el-upload (picture-card) | 多选，accept="image/*" |
| 聊天截图 | el-upload (picture-card) | 多选，accept="image/*" |
| 工作汇报 | el-input (textarea) | 如 "Zhenzhen Ngày 1/7 Tổng 27079" |

**按日汇总 Tab**:

| 列 | 说明 |
|----|------|
| 日期 | biz_date |
| 主播 | stage_name |
| 打赏榜截图 | 有→绿色tag显示张数，无→红色"未交" |
| 聊天截图 | 同上 |
| 工作汇报 | 有→绿色"已交"，无→红色"未交" |

**明细列表 Tab**:

| 列 | 说明 |
|----|------|
| 内容 | 图片缩略图（可预览大图）或汇报文本 |
| 日期 | biz_date |
| 主播 | stage_name |
| 类型 | 打赏榜截图/聊天截图/汇报文本 |
| 识别状态 | 待识别(灰)/已识别(黄)/已校正(绿)/识别失败(红) |
| 上传人 | upload_by_name |
| 上传时间 | create_time |
| 操作 | 删除按钮（live_admin 角色） |

### 5.2 识别校正页（识别校正）

**列表表格**:

| 列 | 说明 |
|----|------|
| 内容 | 图片缩略图或汇报文本 |
| 日期 | biz_date |
| 主播 | stage_name |
| 类型 | 打赏榜截图/聊天截图/汇报文本 |
| 识别状态 | 待识别/已识别/已校正/识别失败/识别中(带loading动画) |
| 识别摘要 | AI识别结果概览 |
| 操作 | AI识别 / 校正 / 确认入库 |

**识别摘要显示规则**:
- 打赏榜: "Ty Ko 15,500虚拟币、Fan 4,278虚拟币"
- 聊天: "cowboy19862(4条): Kid playing w filter lol 😝; Good night"
- 汇报: "总流水 27,079，可校正汇报文本"

**校正对话框（el-dialog, 860px）**:

顶部元信息: 日期 + 主播 + 类型

打赏榜校正:

| 列 | 组件 |
|----|------|
| 排名 | el-input-number |
| 客户昵称 | el-input |
| 标记/徽章 | el-input |
| 虚拟币 | el-input-number |
| 操作 | 删除行 |

底部: "增加客户" 按钮

聊天校正（按客户分组）:

每个客户一个卡片块:

| 字段 | 组件 |
|------|------|
| 客户昵称 | el-input |
| 标记/徽章 | el-input |
| 消息列表 | el-table（每行一条消息） |

消息列表列:

| 列 | 组件 |
|----|------|
| 发送方 | el-select（客户/主播） |
| 类型 | el-select（文本/视频/图片/语音） |
| 消息内容 | el-input |
| 操作 | 删除消息 |

底部: "添加消息" + "增加客户" 按钮

汇报校正:

| 字段 | 组件 |
|------|------|
| 总流水 | el-input-number |
| 汇报原文 | el-input (textarea) |

底部按钮: 取消 / 保存校正 / 保存并入库

### 5.3 数据统计页（数据统计）

**头部**:
- 标题: "WEEKLY REPORT · 主播运营看板"
- 副标题: "第N周内容情况"
- 日期范围: YYYY-MM-DD 至 YYYY-MM-DD
- 筛选: 日期范围 + 主播下拉

**概览胶囊（3个）**:
- 主播 N 位
- 总流水 N
- 私信客户 N

**01 · 主播本周表现**（主播卡片网格，3列）:

| 字段 | 说明 | 数据来源 |
|------|------|----------|
| 主播名 | stageName | live_streamer |
| 健康状态 | good(绿)/watch(黄)/risk(红) | 与上期对比 |
| 总流水 | 大数字 | live_gift_record SUM(xu) |
| 环比变化 | ▲/▼ 百分比 | vs 上周期 |
| 打赏识别 | giftXu | live_gift_record SUM(xu) |
| 打赏客户 | giftCustomers | live_gift_record COUNT(DISTINCT customer_id) |
| 私信客户 | chatCustomers | live_chat_contact COUNT(DISTINCT customer_id) |
| 日报天数 | reportDays | live_daily_report COUNT(*) |

**02 · 本周每日走势**（ECharts 折线图）:
- X 轴: 日期
- Y 轴: 流水金额
- 系列: 按主播拆分
- 数据来源: live_gift_record 按 biz_date + streamer_id 分组

**03 · 客户触达情况**（客户卡片网格，5列）:

| 字段 | 说明 |
|------|------|
| 主播名 | stageName |
| 月活打赏客户 | activeCustomers（打赏记录中去重客户数） |
| 已私信客户 | chatCustomers（聊天联系人去重客户数） |
| 中高价值客户 | highValueCustomers（xu >= 1000 的去重客户数） |
| 联系率 | contactRate = chatCustomers / activeCustomers × 100% |
| 警告标签 | 联系率 < 50% 显示红色"急需跟进" |

---

## 六、数据来源映射

### 主播卡片（GET /live/stats/weekly → cards）

| 字段 | 数据库字段 | SQL |
|------|-----------|-----|
| stageName | live_streamer.stage_name | 直接查 |
| totalXu | — | `SUM(live_gift_record.xu) WHERE biz_date BETWEEN begin AND end` |
| giftXu | — | 同 totalXu（统计口径以打赏记录为准） |
| giftCustomers | — | `COUNT(DISTINCT customer_id) FROM live_gift_record` |
| chatCustomers | — | `COUNT(DISTINCT customer_id) FROM live_chat_contact` |
| reportDays | — | `COUNT(*) FROM live_daily_report` |
| previousTotalXu | — | `SUM(live_gift_record.xu) WHERE biz_date BETWEEN 上期begin AND 上期end` |
| changeRate | — | `(totalXu - previousTotalXu) / previousTotalXu × 100` |
| health | — | totalXu >= previousTotalXu → "good"；差 > -30% → "watch"；差 <= -30% → "risk" |

### 概览胶囊（GET /live/stats/weekly → overview）

| 字段 | 说明 |
|------|------|
| streamerCount | `COUNT(*) FROM live_streamer WHERE status='0'` |
| totalXu | `SUM(cards[].totalXu)` |
| totalGiftXu | 同 totalXu |
| giftCustomers | `SUM(cards[].giftCustomers)` |
| chatCustomers | `SUM(cards[].chatCustomers)` |

### 趋势图（GET /live/stats/weekly → trend）

| 字段 | SQL |
|------|-----|
| bizDate | `DATE_FORMAT(biz_date, '%Y-%m-%d')` |
| streamerId | `live_gift_record.streamer_id` |
| stageName | `live_streamer.stage_name` |
| totalXu | `SUM(xu) GROUP BY biz_date, streamer_id` |

### 客户卡片（GET /live/stats/weekly → customerCards）

| 字段 | SQL |
|------|-----|
| activeCustomers | `COUNT(DISTINCT customer_id) FROM live_gift_record` |
| chatCustomers | `COUNT(DISTINCT customer_id) FROM live_chat_contact` |
| highValueCustomers | `COUNT(DISTINCT customer_id) FROM live_gift_record WHERE xu >= 1000` |
| contactRate | `chatCustomers / activeCustomers × 100` |

### 识别摘要（前端 resultSummary 函数）

| 类型 | 显示格式 | 数据来源 |
|------|----------|----------|
| 打赏榜 | "昵称 N虚拟币" | ai_result.items[].nickname + items[].xu |
| 聊天 | "昵称(N条): 内容预览" | ai_result.items[].nickname + items[].messages[].content |
| 汇报 | "总流水 N，可校正汇报文本" | ai_result.totalXu |

---

## 七、AI 识别方案

### 调用流程

```
用户点"AI识别" → LiveReviewController.recognize()
  → LiveUploadServiceImpl.recognizeUpload()
    → ConfigurableLiveRecognitionServiceImpl.recognize()
      → 读取 sys_config 获取 API 配置
      → 读取图片文件 → 转 base64 data URL
      → 构造 OpenAI Chat Completions 请求
      → POST 到 https://api.siliconflow.cn/v1/chat/completions
      → 解析返回 JSON → 存入 live_upload.ai_result
      → 更新 ai_status = 1（已识别）或 3（失败）
```

### Prompt 模板

**打赏榜截图**:
```
You are a live-stream operations OCR assistant. Extract structured data from the submitted screenshot.
Return valid JSON only, no markdown and no explanation.
Target schema: {"type":"gift","provider":"model","items":[{"rankNo":1,"nickname":"name","xu":1234,"badge":"","confidence":"normal"}]}
If a field is unclear, use empty string or 0 and set confidence to low.
```

**聊天截图**:
```
Target schema: {"type":"chat","provider":"model","items":[{"nickname":"name","messages":[{"sender":"customer","messageType":"text","content":"message text"}],"confidence":"normal"}]}
For chat screenshots: extract each visible message with its sender (customer or streamer).
Preserve the original language (Vietnamese/Korean/Chinese/emoji).
Group messages by customer nickname. Include ALL visible messages, do not summarize.
For text messages: set messageType='text', put the text in content.
For video/image/audio messages: look at the thumbnail, describe what you see in content (e.g. '[视频:主播在跳舞]').
Set messageType to 'video'/'image'/'audio' accordingly.
```

**汇报文本**:
```
Target schema: {"type":"report","provider":"model","totalXu":1234,"rawText":"original text"}
```

### 关键参数

| 参数 | 值 | 说明 |
|------|------|------|
| max_tokens | 8000 | 聊天内容需要更多 token |
| temperature | 0 | 确定性输出 |
| 前端超时 | 120 秒 | axios timeout |
| 后端超时 | 120 秒 | sys_config live.ai.timeout |
| 图片格式 | base64 data URL | 支持 png/jpeg/webp |

### 容错机制

- **JSON 截断修复**: 如果 AI 输出被 max_tokens 截断，autoCloseJson() 自动补全未闭合的括号
- **编码处理**: JDBC 使用 UTF-8 编码，支持 emoji 等 4 字节字符
- **状态回退**: 识别失败时状态设为 3，可重新触发

---

## 八、数据流完整链路

### 8.1 打赏榜识别入库

```
上传截图 → LiveUploadController.uploadImages()
  → 存文件到 uploads/live/{date}/{streamerId}/{uuid}.png
  → 写 live_upload 记录（ai_status=0, upload_type=1）

点"AI识别" → ConfigurableLiveRecognitionServiceImpl.recognize()
  → 读取图片 → base64 → 发送到硅基流动 API
  → 返回 JSON（items: [{rankNo, nickname, xu, badge}]）
  → 存入 ai_result（ai_status=1）

点"校正" → 运营编辑 AI 结果 → 保存

点"确认入库" → LiveUploadServiceImpl.confirmRecognize()
  → confirmGift():
    → 遍历 items:
      → insertCustomerIfAbsent（nickname 不存在则创建 live_customer）
      → upsertGiftRecord（写入 live_gift_record，同客户同天去重）
```

### 8.2 聊天截图识别入库

```
同上流程，confirmRecognize() 走 confirmChat() 分支:
  → 遍历 items:
    → insertCustomerIfAbsent
    → upsertChatContact（写入 live_chat_contact）
```

### 8.3 汇报文本入库

```
上传汇报 → LiveUploadController.submitReport()
  → 写 live_upload（upload_type=3, raw_text=汇报内容）

点"确认入库" → LiveUploadServiceImpl.confirmRecognize()
  → upsertDailyReport:
    → parseTotalXu(): 优先匹配 "Tổng/总" 后的数字，兜底取文本中最大数
    → 写入 live_daily_report（同主播同天去重）
```

---

## 九、文件存储

### 上传路径

```
J:/codex-projects/ai-streamer-stats/uploads/
  └── live/
      └── {2026-07-07/}
          └── {streamerId/}
              ├── uuid1.png
              ├── uuid2.jpg
              └── ...
```

### 配置（application.yml）

```yaml
ruoyi:
  profile: J:/codex-projects/ai-streamer-stats/uploads

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```

### 前端访问

图片通过 `{baseApi}/profile/live/{date}/{streamerId}/{file}` 访问，其中 `baseApi` 为后端地址。

---

## 十、启动方式

### 三个批处理脚本

| 脚本 | 用途 | 说明 |
|------|------|------|
| `backend/sql/init-live.bat` | 建表 | 执行 live_role_menu.sql + live_tables.sql + 插入测试主播 |
| `start-dev.bat` | 启动前后端 | 后端 `mvn spring-boot:run -pl ruoyi-admin -am -U`，前端 `pnpm dev` |
| `gh-create-and-push.bat` | 推送 GitHub | 创建仓库 + 推送 feature/live-upload 分支 |

### 手动启动

```bash
# 1. 启动 Redis
C:\Redis\redis-server.exe C:\Redis\redis.windows.conf

# 2. 启动后端（从 ruoyi-admin 目录）
cd J:\codex-projects\ai-streamer-stats\backend\ruoyi-admin
mvn spring-boot:run

# 3. 启动前端
cd J:\codex-projects\ai-streamer-stats\frontend
pnpm dev
```

### 依赖要求

- JDK 8
- Maven 3.9+
- Node.js + pnpm
- MySQL 8.0
- Redis（Windows 版，手动下载于 C:\Redis\）

---

## 十一、关键修复记录

| 问题 | 原因 | 修复 |
|------|------|------|
| Maven 找不到 spring-boot 插件 | 从父目录运行，插件定义在 ruoyi-admin | 改为从 ruoyi-admin 目录运行，或加 `-am` 编译依赖模块 |
| Maven 依赖找不到 ruoyi-framework | 本地仓库缓存了失败记录 | 加 `-U` 强制刷新 |
| 后端启动连不上 Redis | 机器没装 Redis | 手动下载 Redis Windows 版到 C:\Redis\ |
| 后端启动连不上 Redis（Docker） | 若依默认 Docker Redis，本地没 Docker | 改用本地 Redis |
| 后端启动报错 "No plugin found for prefix 'spring-boot'" | 从父目录运行 | 从 ruoyi-admin 目录运行 |
| AI 识别报 "数据正在处理，请勿重复提交" | 若依防重复提交机制 + 10秒超时导致重试 | review.js 加 timeout: 120000 |
| AI 识别 10 秒超时 | axios 默认 10 秒，AI 识别需要更久 | review.js timeout 改 120 秒 |
| MySQL JSON 列报 "Invalid encoding in string" | AI 返回的 JSON 被 max_tokens 截断，末尾是不完整 Unicode | max_tokens 从 1200→4000→8000；加 autoCloseJson() 自动补全括号 |
| MySQL JSON 列编码报错 | JDBC characterEncoding=utf8（3字节），emoji 是 4 字节 | application-druid.yml 改 characterEncoding=UTF-8 |
| 统计 totalXu=0，trend 为空 | SQL 查的是 live_daily_report（没数据），应查 live_gift_record | selectStreamerCards/selectPreviousTotals/selectTrend 全部改为查 live_gift_record |
| AI 识别中无 loading 状态 | 点击按钮后前端没有反馈 | 加 recognizingId ref，按钮显示"识别中..."，状态列显示 loading 动画 |
| 聊天截图只识别客户消息 | prompt 没要求读取具体内容 | prompt 增加聊天内容提取指令，要求读取每条消息 |
| 主播发的视频消息无法识别内容 | 视频内容无法从截图读取 | prompt 增加"看缩略图描述内容"，标记 messageType=video |

---

## 十二、实施进度

1. ✅ 底座搭建：RuoYi-Vue3 3.9.2 跑通，前端 :80，后端 :8080，库 ry-vue
2. ✅ 角色菜单初始化：3 角色（主播/运营/管理员）+ 3 菜单（上传/校正/统计）+ 按钮权限
3. ✅ 业务表建表：7 张表（streamer/customer/upload/gift_record/chat_contact/chat_message/daily_report）
4. ✅ 图片上传模块：批量截图上传 + 汇报文本提交 + 按日汇总 + 明细列表 + 删除
5. ✅ AI 识别校正模块：AI 识别 + 人工校正 + 确认入库（Mock 模式）
6. ✅ 数据统计模块：主播卡片 + 每日走势 + 客户触达（ECharts）
7. ✅ GitHub 推送：两个仓库 feature/live-upload 分支
8. ✅ 接入真实 AI 模型：硅基流动 Qwen3-VL-32B-Instruct，OpenAI 兼容格式
9. ✅ 聊天内容识别：prompt 改为读取每条消息内容，区分 text/video/image/audio 类型
10. ✅ AI 识别加载状态：按钮 loading + 状态列"识别中"动画
11. ✅ JSON 编码修复：max_tokens 8000 + autoCloseJson + JDBC UTF-8
12. ✅ 统计看板数据源修复：totalXu/trend 从 live_gift_record 取数
13. ✅ 系统文档编写：完整文档 12 章节

---

## 十三、已知问题与待办

### 已知问题

| 问题 | 说明 |
|------|------|
| 客户昵称不唯一 | `live_customer.nickname` 有唯一约束，Fan 和 Fan↑ 被当成两个客户 |
| 打赏榜按场重置 | 一天多场直播会被误去重，需按场/批次区分 |
| AI 结果直接写客户表 | 应先暂存再人工确认，避免污染客户表 |
| 主播发视频消息无法读取内容 | 只能看缩略图描述，无法读取视频本身 |

### 待实现功能

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 客户合并 | 高 | 同一客户不同昵称去重，历史别名映射，人工合并 |
| 导出 Excel | 高 | 主播排名、客户 Top、打赏明细导出 |
| 多截图滚动拼接 | 中 | 同一场次多张截图自动去重合并 |
| 识别置信度高亮 | 中 | 低置信度字段在校正页高亮提示 |
| 客户头像相似度 | 低 | 新昵称出现时按头像提示疑似同一客户 |

---

## 十四、配置文件清单

| 文件 | 用途 |
|------|------|
| `backend/ruoyi-admin/src/main/resources/application.yml` | 主配置（端口、文件上传、Redis） |
| `backend/ruoyi-admin/src/main/resources/application-druid.yml` | 数据库连接（含 UTF-8 编码） |
| `backend/sql/live_role_menu.sql` | 角色菜单权限初始化（3角色+3菜单+按钮权限） |
| `backend/sql/live_tables.sql` | 7 张业务表建表语句 |
| `backend/sql/init-live.bat` | 一键初始化脚本 |
| `frontend/src/api/live/upload.js` | 上传 API 封装（6 个接口） |
| `frontend/src/api/live/review.js` | 校正 API 封装（5 个接口，AI 识别 120 秒超时） |
| `frontend/src/api/live/stats.js` | 统计 API 封装（1 个接口） |
| `frontend/src/views/live/upload/index.vue` | 上传页面（480 行，含移动端适配） |
| `frontend/src/views/live/review/index.vue` | 校正页面（520 行，含聊天内容编辑） |
| `frontend/src/views/live/stats/index.vue` | 统计页面（460 行，含 ECharts 趋势图） |

---

## 十五、代码文件清单（新增，约 2000 行）

### 后端（com.ruoyi.live 包）

```
ruoyi-system/src/main/java/com/ruoyi/live/
├── domain/
│   ├── LiveStreamer.java          (76行, 主播实体)
│   ├── LiveUpload.java            (190行, 上传记录+类型常量)
│   └── LiveDailySummary.java      (89行, 日汇总VO)
├── mapper/
│   ├── LiveStreamerMapper.java     (20行, 主播CRUD)
│   ├── LiveUploadMapper.java       (23行, 上传CRUD+业务表upsert)
│   └── LiveStatsMapper.java        (16行, 统计聚合)
├── service/
│   ├── ILiveStreamerService.java    (16行)
│   ├── ILiveUploadService.java      (24行, 含recognize/mock/confirm)
│   ├── ILiveRecognitionService.java (6行, recognize接口)
│   ├── ILiveStatsService.java       (6行, getWeeklyStats)
│   └── impl/
│       ├── LiveStreamerServiceImpl.java           (36行)
│       ├── LiveUploadServiceImpl.java             (120行, 识别/校正/入库逻辑)
│       ├── ConfigurableLiveRecognitionServiceImpl.java (400行, 真实AI识别)
│       ├── MockLiveRecognitionServiceImpl.java    (30行, Mock模式)
│       └── LiveStatsServiceImpl.java              (110行, 周统计聚合)

ruoyi-system/src/main/resources/mapper/live/
├── LiveStreamerMapper.xml    (66行)
├── LiveUploadMapper.xml      (130行, 含upsert业务表SQL)
└── LiveStatsMapper.xml       (100行, 统计聚合SQL)

ruoyi-admin/src/main/java/com/ruoyi/web/controller/live/
├── LiveStreamerController.java  (36行, GET /live/streamer/listAll)
├── LiveUploadController.java    (211行, 上传/列表/汇总/删除+主播权限)
├── LiveReviewController.java    (80行, AI识别/校正/确认)
└── LiveStatsController.java     (30行, 周统计)
```

### 前端

```
src/api/live/
├── upload.js    (54行, 6个接口)
├── review.js    (39行, 5个接口, AI识别120秒超时)
└── stats.js     (8行, 1个接口)

src/views/live/
├── upload/index.vue  (480行, 每日提交+按日汇总+明细列表+移动端适配)
├── review/index.vue  (520行, 识别列表+校正对话框+聊天内容编辑+loading状态)
└── stats/index.vue   (460行, 概览+主播卡片+ECharts趋势+客户触达)
```
