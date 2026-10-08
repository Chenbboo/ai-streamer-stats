@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================
echo  安全升级直播数据模块(结构 + 权限 + AI配置)
echo ============================================
set /p MYSQL_PWD=请输入 MySQL root 密码:

mysql -uroot ry-vue < migrations\V001__system_menu_i18n.sql
if errorlevel 1 goto :fail
echo [1/4] 系统菜单多语言字段 OK

mysql -uroot ry-vue < migrations\V002__live_schema_baseline.sql
if errorlevel 1 goto :fail
echo [2/4] 直播业务表和索引 OK

mysql -uroot ry-vue < migrations\V003__live_permissions_i18n.sql
if errorlevel 1 goto :fail
echo [3/4] 角色菜单和中越双语名称 OK

mysql -uroot ry-vue < live_ai_config.sql
if errorlevel 1 goto :fail
echo [4/4] AI配置项 OK

echo.
echo 全部完成!未删除业务数据，也未创建测试主播。
echo 建议继续执行: mysql -uroot ry-vue ^< migrations\verify_business_schema.sql
goto :end

:fail
echo.
echo 执行失败!请确认 MySQL 在运行、mysql 命令在 PATH 中，并检查上方错误。
:end
pause
