@echo off
chcp 65001 >nul
echo ==== 推送 backend ====
cd /d J:\codex-projects\ai-streamer-stats\backend
git remote remove mine 2>nul
git remote add mine https://github.com/Chenbboo/ai-streamer-backend.git
git push -u mine feature/live-upload
if errorlevel 1 goto :fail

echo ==== 推送 frontend ====
cd /d J:\codex-projects\ai-streamer-stats\frontend
git remote remove mine 2>nul
git remote add mine https://github.com/Chenbboo/ai-streamer-frontend.git
git push -u mine feature/live-upload
if errorlevel 1 goto :fail

echo.
echo 全部推送完成!
goto :end

:fail
echo.
echo 推送失败:请确认 GitHub 上已建好同名仓库,且本机 git 已登录(第一次推会弹浏览器授权)
:end
pause
