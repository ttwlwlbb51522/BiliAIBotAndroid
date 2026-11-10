param(
    [ValidateSet('major', 'minor', 'patch')]
    [string]$Part = 'patch'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root 'VERSION'
$gradleFile = Join-Path $root 'gradle.properties'

$current = (Get-Content $versionFile -Raw).Trim()
if ($current -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
    throw "VERSION 格式无效: $current"
}
$major = [int]$Matches[1]
$minor = [int]$Matches[2]
$patch = [int]$Matches[3]

switch ($Part) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    'patch' { $patch++ }
}
$next = "$major.$minor.$patch"

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($versionFile, "$next`n", $utf8)

$text = [System.IO.File]::ReadAllText($gradleFile, [System.Text.Encoding]::UTF8)
if ($text -notmatch '(?m)^APP_VERSION=.*$') {
    throw 'gradle.properties 缺少 APP_VERSION'
}
$text = [regex]::Replace($text, '(?m)^APP_VERSION=.*$', "APP_VERSION=$next")
if ($text -notmatch '(?m)^APP_VERSION_CODE=(\d+)$') {
    throw 'gradle.properties 缺少 APP_VERSION_CODE'
}
$code = [int]$Matches[1] + 1
$text = [regex]::Replace($text, '(?m)^APP_VERSION_CODE=\d+$', "APP_VERSION_CODE=$code")
[System.IO.File]::WriteAllText($gradleFile, $text, $utf8)

Write-Host "Version: $current -> $next"
Write-Host "APP_VERSION_CODE: $code"
Write-Host "Next: update CHANGELOG.md, build, commit, tag v$next"
