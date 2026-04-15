param(
    [Parameter(Mandatory = $true)]
    [string]$Name
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$safeName = $Name.Trim().ToLower() -replace "[^a-z0-9_]+", "_"
$safeName = $safeName.Trim("_")
if ([string]::IsNullOrWhiteSpace($safeName)) {
    throw "Name is empty after sanitization. Please use letters/numbers/underscore."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dbDir = Split-Path -Parent $scriptDir
$migrationDir = Join-Path $dbDir "migration"

if (!(Test-Path $migrationDir)) {
    New-Item -ItemType Directory -Path $migrationDir | Out-Null
}

$datePart = Get-Date -Format "yyyyMMdd"
$fileName = "V${datePart}__${safeName}.sql"
$filePath = Join-Path $migrationDir $fileName

if (Test-Path $filePath) {
    throw "Migration already exists: $filePath"
}

$template = @"
-- Purpose:
--   Describe what data change this migration performs.
-- Rollback:
--   Provide rollback SQL if possible.

START TRANSACTION;

-- Example:
-- INSERT INTO dict_competition (competition_name, competition_short_name, category_id, enabled, sort_no, remark)
-- VALUES ('全国大学生XX竞赛', 'XX竞赛', 1, 1, 100, 'shared data');

COMMIT;
"@

$template | Out-File -FilePath $filePath -Encoding utf8NoBOM
Write-Host "Created migration: $filePath"
