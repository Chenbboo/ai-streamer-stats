$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $PID
$logDirectory = Join-Path $projectRoot "logs\checks\$runId"
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$stages = @()

function Invoke-Check {
    param([string]$Name, [string]$Directory, [string]$Command, [string[]]$Arguments)
    $started = [DateTime]::UtcNow
    $log = Join-Path $logDirectory "$Name.log"
    $code = 1
    Write-Host "`n=== $Name ==="
    Push-Location $Directory
    try {
        $executable = (Get-Command $Command -ErrorAction Stop).Source
        # Windows PowerShell treats native stderr as ErrorRecord, even for build warnings.
        $ErrorActionPreference = 'Continue'
        & $executable @Arguments 2>&1 | Tee-Object -FilePath $log | Out-Host
        $code = $LASTEXITCODE
    }
    catch {
        $_ | Out-String | Tee-Object -FilePath $log -Append | Out-Host
    }
    finally { Pop-Location }
    [pscustomobject]@{
        name = $Name
        exitCode = $code
        startedUtc = $started
        seconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 1)
        log = $log
    }
}

$backend = Join-Path $projectRoot 'backend'
$frontend = Join-Path $projectRoot 'frontend'
$stages += Invoke-Check 'backend' $backend 'mvn.cmd' @('-B', '-ntp', 'test')

# Only count reports written by this run; old reports must not turn a failed run green.
$totals = [ordered]@{ tests = 0; passed = 0; failures = 0; errors = 0; skipped = 0 }
$skipped = @()
$reportedModules = @()
try {
    $reports = @(Get-ChildItem "$backend\*\target\surefire-reports\TEST-*.xml" -File |
        Where-Object { $_.LastWriteTimeUtc -ge $stages[0].startedUtc })
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw -Encoding UTF8
        $suite = $xml.testsuite
        foreach ($key in @('tests', 'failures', 'errors', 'skipped')) {
            $totals[$key] += [int]$suite.GetAttribute($key)
        }
        $reportedModules += $report.Directory.Parent.Parent.Name
        foreach ($test in $suite.SelectNodes('testcase[skipped]')) {
            $skipped += [pscustomobject]@{
                test = "$($test.classname).$($test.name)"
                reason = $test.skipped.GetAttribute('message')
            }
        }
    }
    $totals.passed = $totals.tests - $totals.failures - $totals.errors - $totals.skipped
    foreach ($module in @('ruoyi-system', 'ruoyi-admin')) {
        if ($reportedModules -notcontains $module) { throw "No fresh test reports for $module." }
    }
    if ($totals.passed -eq 0 -or $totals.failures -gt 0 -or $totals.errors -gt 0) {
        throw 'Backend test reports contain failures, errors, or no passing tests.'
    }
}
catch {
    Write-Warning "Backend report validation failed: $_"
    $stages[0].exitCode = 1
}

# Run every stage even if an earlier check fails, so one run shows all failures.
$stages += Invoke-Check 'frontend' $frontend 'pnpm.cmd' @('test')
$stages += Invoke-Check 'i18n' $frontend 'pnpm.cmd' @('run', 'check:i18n')
$stages += Invoke-Check 'build' $frontend 'pnpm.cmd' @('run', 'build:prod')
# ERP currently has no Vietnamese acceptance requirement. Keep the translation
# audit visible, but do not block a release on its ERP findings.
$failed = @($stages | Where-Object { $_.exitCode -ne 0 -and $_.name -ne 'i18n' })
$summary = [ordered]@{
    success = ($failed.Count -eq 0)
    stages = $stages
    informationalStages = @('i18n')
    backend = $totals
    skippedBackendTests = $skipped
}
$summary | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $logDirectory 'summary.json') -Encoding UTF8
Write-Host "`n=== Check summary ==="
$stages | Select-Object name, exitCode, seconds | Format-Table -AutoSize | Out-Host
Write-Host "Backend: $($totals.passed) passed, $($totals.failures) failures, $($totals.errors) errors, $($totals.skipped) skipped."
if (($stages | Where-Object { $_.name -eq 'i18n' }).exitCode -ne 0) {
    Write-Host 'Translation audit has findings (informational; ERP Vietnamese is not a release requirement).'
}
foreach ($item in $skipped) { Write-Host "SKIPPED $($item.test): $($item.reason)" }
Write-Host "Reports: $logDirectory"
if ($failed.Count -gt 0) {
    Write-Host 'CHECK FAILED'
    exit 1
}
Write-Host 'CHECK PASSED (see skipped tests above; this is not a full API/browser acceptance run).'
exit 0
