@echo off
chcp 65001 >nul
set "RUOYI_PROFILE=%~dp0backend\uploads"
if not exist "%RUOYI_PROFILE%" mkdir "%RUOYI_PROFILE%"
set "BACKEND_PID="
for /f "delims=" %%P in ('powershell.exe -NoProfile -Command "$connection=Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue; if($connection){$connection[0].OwningProcess}"') do set "BACKEND_PID=%%P"
if defined BACKEND_PID (
  echo 后端已在 8080 端口运行 ^(PID %BACKEND_PID%^)，跳过重复打包和启动。
) else (
  echo 启动后端(新窗口)...
  start "ruoyi-backend" cmd /k "cd /d ""%~dp0backend"" && call mvn -pl ruoyi-admin -am -DskipTests clean package && java -jar ruoyi-admin\target\ruoyi-admin.jar"
)
echo 启动前端(新窗口)...
start "ruoyi-frontend" cmd /k "cd /d ""%~dp0frontend"" && pnpm dev"
echo.
echo 两个窗口都启动后:
echo   后端就绪标志: 窗口出现 "若依启动成功"
echo   前端就绪标志: 窗口出现 Local: http://localhost:80/
echo 然后浏览器打开 http://localhost:80  用 admin / admin123 登录
pause
