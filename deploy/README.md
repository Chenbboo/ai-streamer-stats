# 生产发布手册

## 一次性前置条件

1. 在系统用户管理中创建 `wangfuzhang`，由管理员设置一次性密码；不要把密码写入 SQL、脚本或版本库。
2. 在服务器创建 `/opt/ai-streamer/business-ai.env`，参考 `business-ai.env.example`，填入至少 32 位随机 Token 密钥和生产 DeepSeek 密钥，并保留 `RUOYI_PROFILE=/opt/ai-streamer/uploads`。然后执行：

   ```bash
   chmod 600 /opt/ai-streamer/business-ai.env
   chown root:root /opt/ai-streamer/business-ai.env
   ```

3. 保留 `/opt/ai-streamer/application-prod.yml`，部署脚本会读取其中的生产数据库连接并在备份后把权限收紧为 `600`。上传文件目录位于发布版本目录之外，部署脚本会创建并持续复用 `/opt/ai-streamer/uploads`，发布和回滚不删除该目录。

## 本地生成发布包

发布提交完成且工作区干净后执行：

```powershell
pwsh -File deploy/prepare-release.ps1 -ReleaseId <当前提交哈希>
```

脚本会重新执行后端测试与打包、前端依赖安装、安全审计和生产构建，并生成：

```text
outputs/releases/release-<提交哈希>.tar.gz
```

## 上传但不部署

```powershell
scp -o ProxyCommand=none outputs/releases/release-<提交哈希>.tar.gz root@129.226.146.30:/opt/ai-streamer/releases/
ssh -o ProxyCommand=none root@129.226.146.30 "cd /opt/ai-streamer/releases && tar -xzf release-<提交哈希>.tar.gz"
```

## 正式部署

```powershell
ssh -t -o ProxyCommand=none root@129.226.146.30 "/opt/ai-streamer/releases/<提交哈希>/deploy-release.sh <提交哈希>"
```

部署脚本会依次执行：制品校验、账号与环境配置门禁、完整数据库/JAR/前端/配置备份、升级前身份校验、V010–V062、完整结构校验、机器门禁、身份校验和比对、上传目录准备、后端与前端切换、服务和接口健康检查。任何步骤失败都会恢复数据库、JAR、前端和 systemd 配置，并保留持久化上传文件。

## 上线后人工验收

- 管理员：登录、组织架构、员工、项目、财务页面。
- 江澜：本人项目详情、跨老板项目目录、AI 查询、证据来源、待确认操作。
- 王赋章：首次登录修改一次性密码，并验证与江澜之间的项目详情隔离。
- 普通员工：创建本人负责的立项申请，手动指定审批老板；指定老板一次审批后只生成一个 `ACTIVE` 项目。
- 归属老板：为项目发布 KPI 方案；负责人填报结算；老板确认后奖金只计入项目成本，未确认结算必须阻断项目关闭。
- 两位老板：分别验证本人公司人员成本可读写、另一家公司人员成本被服务端拒绝。
- 确认执行：生成一个可回收的临时部门，确认后删除；重复确认不得重复写入。
