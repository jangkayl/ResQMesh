# Architecture boundary check. Scans Kotlin imports and fully-qualified references under the app main source set.
# Fails on any violation not listed in boundary-allowlist.txt and on stale allowlist entries.
param(
    [string]$RepositoryRoot = (Join-Path $PSScriptRoot '../..')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$base = 'edu.cit.resqmesh'
$srcRoot = Join-Path $root 'app/src/main/java/edu/cit/resqmesh'
$allowlistPath = Join-Path $PSScriptRoot 'boundary-allowlist.txt'

# Each rule: name, source directory prefix (relative to the package root), forbidden import prefixes.
$rules = @(
    @{ Name = 'core-no-feature-data'; Dir = 'core/'; Forbidden = @("$base.feature.", "$base.data.") }
    @{ Name = 'core-network-no-data-feature'; Dir = 'core/network/'; Forbidden = @("$base.data.", "$base.feature.") }
    @{ Name = 'feature-no-data'; Dir = 'feature/'; Forbidden = @("$base.data.") }
    @{ Name = 'data-no-feature'; Dir = 'data/'; Forbidden = @("$base.feature.") }
)

$found = @{}
$counts = @{}
foreach ($rule in $rules) { $counts[$rule.Name] = 0 }

$files = Get-ChildItem -LiteralPath $srcRoot -Recurse -File -Filter '*.kt'
foreach ($file in $files) {
    $rel = $file.FullName.Substring($srcRoot.Length + 1).Replace('\', '/')
    # Imports and fully-qualified references on any line except the package declaration.
    $imports = Select-String -LiteralPath $file.FullName -Pattern '(?<![\w.])com\.example\.testresqmesh(\.\w+)+' -AllMatches |
        Where-Object { $_.Line -notmatch '^\s*package\s' } |
        ForEach-Object { $_.Matches } | ForEach-Object { $_.Value } | Sort-Object -Unique
    foreach ($rule in $rules) {
        if (-not $rel.StartsWith($rule.Dir)) { continue }
        foreach ($import in $imports) {
            foreach ($prefix in $rule.Forbidden) {
                if ($import.StartsWith($prefix)) {
                    $key = "$rel -> $import"
                    if (-not $found.ContainsKey($key)) { $found[$key] = [System.Collections.Generic.List[string]]::new() }
                    $found[$key].Add($rule.Name)
                    $counts[$rule.Name]++
                    break
                }
            }
        }
    }
}

$allowed = @{}
if (Test-Path -LiteralPath $allowlistPath) {
    foreach ($line in Get-Content -LiteralPath $allowlistPath) {
        $entry = $line.Trim()
        if ($entry -eq '' -or $entry.StartsWith('#')) { continue }
        $allowed[$entry] = $true
    }
}

$errors = [System.Collections.Generic.List[string]]::new()
foreach ($key in $found.Keys) {
    if (-not $allowed.ContainsKey($key)) { $errors.Add("New boundary violation: $key [$($found[$key] -join ', ')]") }
}
foreach ($key in $allowed.Keys) {
    if (-not $found.ContainsKey($key)) { $errors.Add("Stale allowlist entry (remove it): $key") }
}

if ($errors.Count -gt 0) {
    $errors | Sort-Object | ForEach-Object { Write-Host $_ }
    Write-Host "Boundary check failed: $($errors.Count) problem(s)."
    exit 1
}

foreach ($rule in $rules) { Write-Host ("{0}: {1} allowlisted" -f $rule.Name, $counts[$rule.Name]) }
Write-Host 'Boundary checks passed.'
