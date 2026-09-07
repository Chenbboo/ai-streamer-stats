# Uses the same local services and environment overrides as start-dev.bat.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$env:RUOYI_PROFILE = Join-Path $projectRoot 'backend\uploads'
New-Item -ItemType Directory -Force -Path $env:RUOYI_PROFILE | Out-Null
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
