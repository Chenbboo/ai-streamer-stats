@echo off
chcp 65001 >nul

where gh >nul 2>&1
if errorlevel 1 (
    echo 未安装 GitHub CLI。先执行: winget install GitHub.cli
    echo 装完重新打开命令行窗口再运行本脚本
    pause & exit /b 1
)

gh auth status >nul 2>&1
if errorlevel 1 (
    echo 尚未登录 GitHub CLI,先帮你发起登录(选 GitHub.com - HTTPS - 浏览器登录)
    gh auth login
    if errorlevel 1 ( echo 登录失败 & pause & exit /b 1 )
)

call :pushrepo J:\codex-projects\ai-streamer-stats\backend ai-streamer-backend
call :pushrepo J:\codex-projects\ai-streamer-stats\frontend ai-streamer-frontend

echo.
echo 全部完成!仓库地址:
echo   https://github.com/Chenbboo/ai-streamer-backend
echo   https://github.com/Chenbboo/ai-streamer-frontend
pause
exit /b 0

:pushrepo
echo ==== %2 ====
cd /d %1
gh repo view Chenbboo/%2 >nul 2>&1
if errorlevel 1 (
    echo 仓库不存在,创建并推送...
    git remote remove mine 2>nul
    gh repo create %2 --public --source . --remote mine --push
) else (
    echo 仓库已存在,直接推送...
    git remote remove mine 2>nul
    git remote add mine https://github.com/Chenbboo/%2.git
    git push -u mine feature/live-upload
)
if errorlevel 1 ( echo %2 推送失败,把上面报错发给 Claude & pause & exit /b 1 )
exit /b 0
