param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('eureka-server','user-service','movie-service','showtime-service','booking-service','api-gateway')]
    [string]$Service,
    [ValidateSet('test','run')][string]$Action = 'run'
)
$ErrorActionPreference = 'Stop'
$java = Get-Command java -ErrorAction SilentlyContinue
if (!$java) { throw 'Java 17 is required. Install it and set JAVA_HOME or add java to PATH.' }
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch 'version "17\.') { throw "Java 17 is required; found: $javaVersion" }
$config = Join-Path $PSScriptRoot '.local\application-local.properties'
if (!(Test-Path $config)) { throw 'Missing .local/application-local.properties. See README.md.' }
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = ([System.Uri]$config).AbsoluteUri
Push-Location (Join-Path $PSScriptRoot $Service)
try {
    if ($Action -eq 'test') { & .\mvnw.cmd clean test }
    else { & .\mvnw.cmd spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
