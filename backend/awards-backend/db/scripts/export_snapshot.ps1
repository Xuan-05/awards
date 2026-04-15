param(
    [string]$Host = "127.0.0.1",
    [int]$Port = 3306,
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$User,
    [Parameter(Mandatory = $true)][string]$Password
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$outputDir = Join-Path $rootDir "snapshots\local"
if (!(Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outputFile = Join-Path $outputDir ("snapshot_{0}.sql" -f $timestamp)

$tables = @(
    "biz_award_record",
    "biz_team",
    "biz_team_member",
    "biz_team_teacher",
    "dict_competition",
    "dict_award_level",
    "sys_user",
    "sys_user_role",
    "sys_role"
)

$mysqldump = Get-Command "mysqldump" -ErrorAction SilentlyContinue
if (-not $mysqldump) {
    throw "mysqldump not found in PATH. Please install MySQL client tools first."
}

$args = @(
    "--host=$Host",
    "--port=$Port",
    "--user=$User",
    "--password=$Password",
    "--single-transaction",
    "--skip-lock-tables",
    "--set-gtid-purged=OFF",
    "--no-create-db",
    "--databases", $Database,
    "--tables"
) + $tables

Write-Host "Exporting snapshot to: $outputFile"
& $mysqldump.Source @args | Out-File -FilePath $outputFile -Encoding utf8
Write-Host "Done."
