param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{7,40}$')]
    [string]$ReleaseId
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$migrationRoot = Join-Path $backendRoot 'sql\migrations'
$releaseRoot = Join-Path $repoRoot "outputs\releases\$ReleaseId"
$bundlePath = Join-Path $repoRoot "outputs\releases\release-$ReleaseId.tar.gz"

Push-Location $repoRoot
try {
    $head = (git rev-parse HEAD).Trim()
    if (-not $head.StartsWith($ReleaseId, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ReleaseId $ReleaseId does not match HEAD $head"
    }
    if (git status --porcelain) {
        throw 'Working tree must be clean before preparing a release.'
    }

    Push-Location $backendRoot
    try {
        mvn clean package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }

    Push-Location $frontendRoot
    try {
        $env:CI = 'true'
        pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        pnpm audit --prod --audit-level high --registry=https://registry.npmjs.org
        if ($LASTEXITCODE -ne 0) { throw 'Frontend production dependency audit failed.' }
        pnpm run build:prod
        if ($LASTEXITCODE -ne 0) { throw 'Frontend production build failed.' }
    } finally { Pop-Location }

    if (Test-Path -LiteralPath $releaseRoot) {
        throw "Release directory already exists: $releaseRoot"
    }
    if (Test-Path -LiteralPath $bundlePath) {
        throw "Release bundle already exists: $bundlePath"
    }

    New-Item -ItemType Directory -Path $releaseRoot | Out-Null
    $releaseMigrations = New-Item -ItemType Directory -Path (Join-Path $releaseRoot 'migrations')

    Copy-Item -LiteralPath (Join-Path $backendRoot 'ruoyi-admin\target\ruoyi-admin.jar') -Destination $releaseRoot
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'deploy-release.sh') -Destination $releaseRoot
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'release_gate.sql') -Destination $releaseMigrations.FullName
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ai-streamer-business-ai.conf') -Destination $releaseRoot
    Copy-Item -LiteralPath (Join-Path $migrationRoot 'preflight_business_upgrade.sql') -Destination $releaseMigrations.FullName
    Copy-Item -LiteralPath (Join-Path $migrationRoot 'verify_business_schema.sql') -Destination $releaseMigrations.FullName
    foreach ($version in 10..42) {
        $migration = @(Get-ChildItem -LiteralPath $migrationRoot -Filter ('V{0:D3}__*.sql' -f $version) -File)
        if ($migration.Count -ne 1) { throw "Expected exactly one V$('{0:D3}' -f $version) migration." }
        Copy-Item -LiteralPath $migration[0].FullName -Destination $releaseMigrations.FullName
    }

    tar -czf (Join-Path $releaseRoot 'frontend.tar.gz') -C (Join-Path $frontendRoot 'dist') .
    if ($LASTEXITCODE -ne 0) { throw 'Failed to package frontend.' }

    $manifestFiles = Get-ChildItem -LiteralPath $releaseRoot -Recurse -File |
        Where-Object { $_.Name -ne 'SHA256SUMS' } |
        Sort-Object FullName
    $manifest = foreach ($file in $manifestFiles) {
        $relative = $file.FullName.Substring($releaseRoot.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
    $manifestPath = Join-Path $releaseRoot 'SHA256SUMS'
    [System.IO.File]::WriteAllLines($manifestPath, $manifest, (New-Object System.Text.UTF8Encoding($false)))

    tar -czf $bundlePath -C (Split-Path $releaseRoot -Parent) $ReleaseId
    if ($LASTEXITCODE -ne 0) { throw 'Failed to package release bundle.' }

    Write-Output "RELEASE_READY=$ReleaseId"
    Write-Output "RELEASE_DIR=$releaseRoot"
    Write-Output "BUNDLE=$bundlePath"
} finally {
    Pop-Location
}
