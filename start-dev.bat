@echo off
chcp 65001 >nul
echo 启动后端(新窗口)...
start "ruoyi-backend" cmd /k "cd /d J:\codex-projects\ai-streamer-stats\backend && mvn spring-boot:run -pl ruoyi-admin -am -U"
echo 启动前端(新窗口)...
start "ruoyi-frontend" cmd /k "cd /d J:\codex-projects\ai-streamer-stats\frontend && pnpm dev"
echo.
echo 两个窗口都启动后:
echo   后端就绪标志: 窗口出现 "若依启动成功"
echo   前端就绪标志: 窗口出现 Local: http://localhost:80/
echo 然后浏览器打开 http://localhost:80  用 admin / admin123 登录
pause
