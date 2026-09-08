# Uses the same local services and environment overrides as start-dev.bat.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$env:RUOYI_PROFILE = Join-Path $projectRoot 'backend\uploads'
New-Item -ItemType Directory -Force -Path $env:RUOYI_PROFILE | Out-Null
# Optional Windows-user encrypted local integration settings, never stored in Git.
$feishuLocalPath = Join-Path $env:LOCALAPPDATA 'meimaru\feishu.local.json'
if (Test-Path -LiteralPath $feishuLocalPath) {
    $feishuLocal = Get-Content -LiteralPath $feishuLocalPath -Raw | ConvertFrom-Json
    if (-not $env:FEISHU_ATTENDANCE_ENABLED) { $env:FEISHU_ATTENDANCE_ENABLED = 'true' }
    if (-not $env:FEISHU_APP_ID) { $env:FEISHU_APP_ID = $feishuLocal.appId }
    if (-not $env:FEISHU_APP_SECRET) { $env:FEISHU_APP_SECRET = [System.Net.NetworkCredential]::new('', ($feishuLocal.appSecretDpapi | ConvertTo-SecureString)).Password }
    if (-not $env:FEISHU_TENANT_KEY) { $env:FEISHU_TENANT_KEY = $feishuLocal.tenantKey }
    if (-not $env:FEISHU_ATTENDANCE_POLL_ENABLED) { $env:FEISHU_ATTENDANCE_POLL_ENABLED = ([string][bool]$feishuLocal.pollEnabled).ToLowerInvariant() }
    if (-not $env:FEISHU_ATTENDANCE_POLL_LOOKBACK_DAYS -and $feishuLocal.pollLookbackDays) { $env:FEISHU_ATTENDANCE_POLL_LOOKBACK_DAYS = [string]$feishuLocal.pollLookbackDays }
}
function Test-LocalPort([int]$Port) {
    $connection = New-Object System.Net.Sockets.TcpClient
    try { $operation = $connection.ConnectAsync('127.0.0.1', $Port); return $operation.Wait(800) -and $connection.Connected }
    catch { return $false } finally { $connection.Dispose() }
}
if (-not (Test-LocalPort 8080)) {
    $artifact = Join-Path $projectRoot 'backend\ruoyi-admin\target\ruoyi-admin.jar'
    # Rebuild dependencies too: an existing executable jar may contain old module jars.
    Push-Location (Join-Path $projectRoot 'backend')
    try { & mvn -pl ruoyi-admin -am -DskipTests clean package; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' } }
    finally { Pop-Location }
    $backend = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', ('"{0}"' -f $artifact)) -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'dev-backend.out.log') -RedirectStandardError (Join-Path $logDirectory 'dev-backend.err.log') -PassThru
    Write-Output "Backend started: PID $($backend.Id). Logs: logs/dev-backend.out.log"
}
if (-not (Test-LocalPort 81)) {
    $env:BROWSER = 'none'
    $pnpmCommand = (Get-Command pnpm.cmd).Source
    $frontend = Start-Process -FilePath $env:ComSpec -ArgumentList ('/d /c ""{0}" dev"' -f $pnpmCommand) -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'dev-frontend.out.log') -RedirectStandardError (Join-Path $logDirectory 'dev-frontend.err.log') -PassThru
    Write-Output "Frontend started: PID $($frontend.Id). URL: http://localhost:81"
}
