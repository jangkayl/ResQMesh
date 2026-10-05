param(
    [string]$RepositoryRoot = (Join-Path $PSScriptRoot '..'),
    [string]$BaselinePath = 'app/build/structure-audit/baseline.json',
    [switch]$Capture,
    [switch]$ArchitectureOnly
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$source = 'app/src/main/java/com/example/testresqmesh'
$baselineFile = if ([IO.Path]::IsPathRooted($BaselinePath)) { $BaselinePath } else { Join-Path $root $BaselinePath }
if ($ArchitectureOnly) {
    if ($Capture) { throw 'ArchitectureOnly is read-only and cannot be combined with Capture.' }
    & python (Join-Path $PSScriptRoot 'check_network_refactor.py') --baseline $baselineFile --architecture-only
    if ($LASTEXITCODE -ne 0) { throw 'Architecture inspection failed.' }
    exit 0
}
$protectedRoots = @(
    "$source/core/di", "$source/core/domain", "$source/core/location",
    "$source/core/map", "$source/core/model", "$source/core/network",
    "$source/core/service", "$source/core/utils", "$source/data",
    'app/src/main/res', 'app/src/main/AndroidManifest.xml', 'app/schemas',
    'app/src/test/java/com/example/testresqmesh/core/network',
    'app/src/test/java/com/example/testresqmesh/data',
    'app/src/androidTest/java/com/example/testresqmesh/core/network',
    'app/src/androidTest/java/com/example/testresqmesh/data',
    'app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle.properties', 'gradle', 'gradlew', 'gradlew.bat', '.github/workflows'
)

function Get-RelativePath([string]$Path) {
    [IO.Path]::GetRelativePath($root, $Path).Replace('\', '/')
}

function Get-FileHashes {
    $hashes = @{}
    foreach ($relativePath in $protectedRoots) {
        $path = Join-Path $root $relativePath
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $files = if (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -Recurse -File
        } else { Get-Item -LiteralPath $path }
        foreach ($file in $files) {
            $hashes[(Get-RelativePath $file.FullName)] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        }
    }
    return $hashes
}

function Get-BodyHash([string]$Path) {
    # Import relocation is allowed; entry-point and ViewModel bodies are unchanged.
    $content = (Get-Content -LiteralPath $Path -Raw).Replace("`r`n", "`n")
    $content = [regex]::Replace($content, '(?m)^import [^\n]*\n', '')
    $bytes = [Text.Encoding]::UTF8.GetBytes($content)
    $digest = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($digest)
}

if ($Capture) {
    if (Test-Path -LiteralPath $baselineFile) { throw "Baseline already exists: $baselineFile. Choose a new path for a new change." }
    $originalFiles = @{}
    $untracked = & git -C $root -c core.quotepath=false ls-files --others --exclude-standard
    if ($LASTEXITCODE -ne 0) { throw 'Could not inventory untracked files.' }
    foreach ($relativePath in $untracked) {
        $path = Join-Path $root $relativePath
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $originalFiles[$relativePath] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        }
    }
    $bodyHashes = @{}
    $bodyFiles = @(Get-Item -LiteralPath (Join-Path $root "$source/MainActivity.kt"))
    $bodyFiles += Get-ChildItem -LiteralPath (Join-Path $root "$source/feature") -Recurse -File -Filter '*.kt' |
        Where-Object { $_.Directory.Name -eq 'viewmodel' }
    foreach ($file in $bodyFiles) { $bodyHashes[(Get-RelativePath $file.FullName)] = Get-BodyHash $file.FullName }
    $baseline = @{ Version = 1; Protected = Get-FileHashes; OriginalUntracked = $originalFiles; Bodies = $bodyHashes }
    New-Item -ItemType Directory -Path (Split-Path -Parent $baselineFile) -Force | Out-Null
    $baseline | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $baselineFile -Encoding utf8NoBOM
    Write-Host "Captured $($baseline.Protected.Count) protected files and $($originalFiles.Count) existing untracked files."
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile -PathType Leaf)) { throw "Missing baseline. Run with -Capture before editing: $baselineFile" }
$baseline = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json -AsHashtable
if ($baseline.Version -ne 1) { throw 'Unsupported structure baseline version.' }
$current = Get-FileHashes
$errors = [Collections.Generic.List[string]]::new()
foreach ($path in @($baseline.Protected.Keys) + @($current.Keys) | Sort-Object -Unique) {
    if ($baseline.Protected[$path] -ne $current[$path]) { $errors.Add("Protected file changed, added, or removed: $path") }
}
foreach ($path in $baseline.OriginalUntracked.Keys) {
    $file = Join-Path $root $path
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $baseline.OriginalUntracked[$path]) {
        $errors.Add("Existing untracked file changed or removed: $path")
    }
}
foreach ($path in $baseline.Bodies.Keys) {
    $file = Join-Path $root $path
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or (Get-BodyHash $file) -ne $baseline.Bodies[$path]) {
        $errors.Add("Entry-point or ViewModel body changed: $path")
    }
}
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $root $source) -Recurse -File -Filter '*.kt') {
    $package = Select-String -LiteralPath $file.FullName -Pattern '^package ([\w.]+)' | Select-Object -First 1
    if (-not $package) { $errors.Add("Missing package: $(Get-RelativePath $file.FullName)"); continue }
    $expected = $package.Matches[0].Groups[1].Value.Replace('.', '/')
    if (-not $file.Directory.FullName.Replace('\', '/').EndsWith("/$expected")) {
        $errors.Add("Package does not match folder: $(Get-RelativePath $file.FullName)")
    }
}
$featureImports = Get-ChildItem -LiteralPath (Join-Path $root "$source/core/ui") -Recurse -File -Filter '*.kt' |
    Select-String -Pattern '^import com\.example\.testresqmesh\.feature\.'
foreach ($match in $featureImports) { $errors.Add("Shared UI imports a feature: $(Get-RelativePath $match.Path):$($match.LineNumber)") }
if ($errors.Count) { throw ($errors -join [Environment]::NewLine) }
Write-Host "Structure checks passed: $($current.Count) protected files unchanged; original untracked files and entry-point/ViewModel bodies preserved; packages and shared UI boundaries valid."
