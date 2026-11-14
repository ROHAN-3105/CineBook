$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$config = Join-Path $root '.local\application-local.properties'
if (!(Test-Path -LiteralPath $config)) { throw 'Missing .local/application-local.properties. See README.md.' }
$line = Get-Content $config | Where-Object { $_.StartsWith('spring.datasource.password=') }
if (!$line) { throw 'Local MySQL configuration is missing.' }
$mysql = Get-Command mysql -ErrorAction SilentlyContinue
$mysqlPath = if ($mysql) { $mysql.Source } else { 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
if (!(Test-Path -LiteralPath $mysqlPath)) { throw 'MySQL CLI was not found. Add mysql.exe to PATH.' }
$env:MYSQL_PWD = $line.Substring('spring.datasource.password='.Length)
try {
    Get-Content (Join-Path $PSScriptRoot 'sample-data.sql') -Raw | & $mysqlPath -u root -h 127.0.0.1 -D movie_ticketing --show-warnings
    if ($LASTEXITCODE -ne 0) { throw "MySQL seed failed ($LASTEXITCODE)" }
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
