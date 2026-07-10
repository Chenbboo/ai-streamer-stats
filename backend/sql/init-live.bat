@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================
echo  初始化直播数据模块(角色菜单 + 业务表 + 测试主播)
echo ============================================
set /p MYSQL_PWD=请输入 MySQL root 密码:

mysql -uroot ry-vue < live_role_menu.sql
if errorlevel 1 goto :fail
echo [1/3] 角色菜单 OK

mysql -uroot ry-vue < live_tables.sql
if errorlevel 1 goto :fail
echo [2/3] 业务表 OK

mysql -uroot ry-vue -e "insert into live_streamer(user_id, stage_name, tiktok_handle, status, create_by, create_time) select 1, 'Zhenzhen', '@zhenzhen', '0', 'admin', sysdate() from dual where not exists (select 1 from live_streamer where stage_name='Zhenzhen');"
if errorlevel 1 goto :fail
echo [3/3] 测试主播 Zhenzhen OK

echo.
echo 全部完成!接下来:重启后端 - 重新登录 admin - 直播数据管理 - 图片上传
goto :end

:fail
echo.
echo 执行失败!请确认 MySQL 在运行、mysql 命令在 PATH 中,把上面的报错发给 Claude
:end
pause
