param(
    [string]$RepositoryRoot = (Join-Path $PSScriptRoot '..')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path

$required = @(
    'AGENTS.md'
    'README.md'
    'docs/architecture.md'
    'docs/status.md'
    'docs/validation.md'
    'docs/decisions.md'
    'docs/research.md'
    'docs/ui.md'
)

$wordLimits = @{
    'AGENTS.md' = 900
    'README.md' = 1700
    'docs/architecture.md' = 1500
    'docs/status.md' = 900
    'docs/validation.md' = 1500
    'docs/decisions.md' = 1200
    'docs/research.md' = 1200
    'docs/ui.md' = 900
}

$errors = [System.Collections.Generic.List[string]]::new()
$rows = foreach ($relativePath in $required) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $errors.Add("Missing required file: $relativePath")
        continue
    }

    $text = Get-Content -Raw -LiteralPath $path
    $wordCount = [regex]::Matches($text, '\S+').Count
    $limit = $wordLimits[$relativePath]
    if ($wordCount -gt $limit) {
        $errors.Add("$relativePath has $wordCount words; limit is $limit")
    }

    [pscustomobject]@{
        File = $relativePath
        Words = $wordCount
        Limit = $limit
    }
}

$activeMarkdown = @(
    Join-Path $root 'AGENTS.md'
    Join-Path $root 'README.md'
    Get-ChildItem -LiteralPath (Join-Path $root 'docs') -Recurse -File -Filter '*.md' |
        Select-Object -ExpandProperty FullName
)

foreach ($path in $activeMarkdown) {
    $text = Get-Content -Raw -LiteralPath $path
    $matches = [regex]::Matches($text, '!?(?:\[[^\]]*\])\(([^)]+)\)')
    foreach ($match in $matches) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(?:https?://|mailto:|#)') { continue }
        $target = ($target -split '#', 2)[0]
        if ([string]::IsNullOrWhiteSpace($target)) { continue }

        $decodedTarget = [Uri]::UnescapeDataString($target).Trim('<', '>')
        $resolvedTarget = Join-Path (Split-Path -Parent $path) $decodedTarget
        if (-not (Test-Path -LiteralPath $resolvedTarget)) {
            $relativeSource = [IO.Path]::GetRelativePath($root, $path)
            $errors.Add("Broken link in $relativeSource -> $target")
        }
    }
}

$ignoredDocs = & git -C $root check-ignore docs/status.md 2>$null
if ($LASTEXITCODE -eq 0 -and $ignoredDocs) {
    $errors.Add('Canonical docs are still ignored by Git.')
}

$rows | Format-Table -AutoSize

if ($errors.Count -gt 0) {
    Write-Error ($errors -join [Environment]::NewLine)
}

Write-Host 'Documentation checks passed.'
